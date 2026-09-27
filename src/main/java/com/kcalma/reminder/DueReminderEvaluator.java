package com.kcalma.reminder;

import com.kcalma.food.MealType;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The pure "what's due right now, and should it actually be sent" decision — deliberately taking
 * plain values ({@link Signals}) instead of repositories, so every SMART-skip rule can be unit
 * tested with a fixed {@link LocalDateTime} and no Spring/mocking at all. {@link ReminderScheduler}
 * is the only caller: it gathers {@link Signals} from the real repositories, calls {@link
 * #evaluate}, then separately claims the {@code app.reminder_log} dedupe slot for each {@link Due}
 * before actually sending — this class knows nothing about dedupe or persistence.
 *
 * <p>Every slot (a meal's/weigh-in's {@code HH:mm}, or one of water's every-{@code N}-hours slots)
 * is due not just at the exact minute it's configured for, but for {@code catchUpMinutes} after it
 * too — {@code slot <= now < slot + catchUpMinutes} — so a restart or a slow tick near a due minute
 * doesn't lose that occurrence for the rest of the day. The window never crosses midnight: {@link
 * Duration#between(java.time.temporal.Temporal, java.time.temporal.Temporal)} on two bare {@link
 * LocalTime}s (no date attached) is a plain subtraction of nanosecond-of-day, never a modulo-24h
 * wraparound, so a slot late at night (e.g. {@code 23:55}) yields a negative, out-of-window elapsed
 * time as soon as {@code now} rolls over into the next calendar day. Water's dedupe key is built
 * from the SLOT's time, not {@code now}'s — e.g. still {@code WATER_14:00} whether this fires
 * exactly at 14:00 or is caught up nine minutes later — so every tick inside one catch-up window
 * dedupes to the same {@code app.reminder_log} occurrence instead of claiming a fresh one each
 * minute.
 */
@Component
public class DueReminderEvaluator {

    /** Everything about "today" this class needs to decide what's due, gathered by the caller. */
    public record Signals(Set<MealType> mealsLoggedToday, int waterMlToday, Integer waterTargetMl, boolean weighInLoggedToday) {
    }

    /** One reminder that should be sent right now. {@code reminderKey} is the {@code app.reminder_log} dedupe key. */
    public record Due(String reminderKey, String title, String body, String url) {
    }

    private final int catchUpMinutes;

    public DueReminderEvaluator(@Value("${kcalma.reminders.catch-up-minutes:10}") int catchUpMinutes) {
        this.catchUpMinutes = catchUpMinutes;
    }

    public List<Due> evaluate(LocalDateTime now, ReminderSettingsData settings, Signals signals) {
        List<Due> due = new ArrayList<>();
        if (settings == null || !settings.enabled()) {
            return due; // master switch off -- nothing fires, regardless of the leaf toggles below
        }

        LocalTime nowTime = now.toLocalTime().withSecond(0).withNano(0);
        checkMeals(due, nowTime, settings, signals);
        checkWater(due, nowTime, settings.water(), signals);
        checkWeighIn(due, now, nowTime, settings.weighIn(), signals);
        return due;
    }

    private void checkMeals(List<Due> due, LocalTime nowTime, ReminderSettingsData settings, Signals signals) {
        for (String mealKey : ReminderSettingsData.REMINDABLE_MEALS) {
            ReminderSettingsData.MealSetting mealSetting = settings.meals().get(mealKey);
            if (mealSetting == null || !mealSetting.enabled() || !isWithinCatchUpWindow(mealSetting.time(), nowTime)) {
                continue;
            }
            if (signals.mealsLoggedToday().contains(MealType.valueOf(mealKey))) {
                continue; // SMART skip: already logged something for this meal today
            }
            due.add(new Due("MEAL_" + mealKey, mealTitle(mealKey), "Registrá tu comida en Kcalma.", "/agregar?meal=" + mealKey));
        }
    }

    private void checkWater(List<Due> due, LocalTime nowTime, ReminderSettingsData.WaterSetting water, Signals signals) {
        if (water == null || !water.enabled() || signals.waterTargetMl() == null || signals.waterTargetMl() <= 0) {
            return;
        }
        LocalTime from = parseTimeOrNull(water.from());
        LocalTime to = parseTimeOrNull(water.to());
        if (from == null || to == null || water.everyHours() <= 0 || nowTime.isBefore(from)) {
            return;
        }

        long slotSizeMinutes = water.everyHours() * 60L;
        long elapsedMinutes = Duration.between(from, nowTime).toMinutes();
        long sinceLastSlot = elapsedMinutes % slotSizeMinutes;
        if (sinceLastSlot >= catchUpMinutes) {
            return; // now isn't within catch-up range of any configured slot (e.g. every 2h from 10:00 -> 10:00,12:00,...)
        }
        // The slot THIS catch-up window belongs to -- e.g. 14:00 whether now is 14:00 or 14:09 -- so
        // the dedupe key below and the pro-rata target it feeds stay anchored to the slot, not to
        // whichever minute inside the window actually happened to fire.
        LocalTime slot = nowTime.minusMinutes(sinceLastSlot);
        if (slot.isAfter(to)) {
            return; // this slot is past the configured window
        }

        long windowMinutes = Duration.between(from, to).toMinutes();
        long slotElapsedMinutes = elapsedMinutes - sinceLastSlot;
        double elapsedFraction = windowMinutes <= 0 ? 1.0 : Math.min(1.0, slotElapsedMinutes / (double) windowMinutes);
        int expectedMlByNow = (int) Math.round(signals.waterTargetMl() * elapsedFraction);
        if (signals.waterMlToday() >= expectedMlByNow) {
            return; // SMART skip: already at or ahead of the pro-rata target for this slot
        }

        due.add(new Due("WATER_" + slot, "¡Hidratate!", "Todavía no tomaste suficiente agua hoy.", "/"));
    }

    private void checkWeighIn(
            List<Due> due, LocalDateTime now, LocalTime nowTime, ReminderSettingsData.WeighInSetting weighIn, Signals signals) {
        if (weighIn == null || !weighIn.enabled() || !isWithinCatchUpWindow(weighIn.time(), nowTime)) {
            return;
        }
        if (!weighIn.days().contains(weekdayCode(now.getDayOfWeek()))) {
            return;
        }
        if (signals.weighInLoggedToday()) {
            return; // SMART skip: already weighed in today
        }
        due.add(new Due("WEIGH_IN", "Pesate hoy", "Todavía no registraste tu peso de hoy.", "/progreso"));
    }

    /** {@code DayOfWeek.MONDAY.name().substring(0, 3)} == {@code "MON"} — true for all seven values, no lookup table needed. */
    private String weekdayCode(DayOfWeek dayOfWeek) {
        return dayOfWeek.name().substring(0, 3);
    }

    /** {@code slot <= nowTime < slot + catchUpMinutes} — see the class javadoc for why this never crosses midnight. */
    private boolean isWithinCatchUpWindow(String hhmm, LocalTime nowTime) {
        LocalTime configured = parseTimeOrNull(hhmm);
        if (configured == null) {
            return false;
        }
        long elapsedMinutes = Duration.between(configured, nowTime).toMinutes();
        return elapsedMinutes >= 0 && elapsedMinutes < catchUpMinutes;
    }

    /** Never throws: a malformed persisted time (there shouldn't be one — see {@code dto.ReminderSettingsRequest}'s {@code @Pattern}) just never matches. */
    private LocalTime parseTimeOrNull(String hhmm) {
        try {
            return hhmm == null ? null : LocalTime.parse(hhmm);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String mealTitle(String mealKey) {
        return switch (mealKey) {
            case "DESAYUNO" -> "¿Qué desayunaste?";
            case "ALMUERZO" -> "¿Qué almorzaste?";
            case "MERIENDA" -> "¿Qué merendaste?";
            case "CENA" -> "¿Qué cenaste?";
            default -> throw new IllegalStateException("Unknown reminder-eligible meal: " + mealKey);
        };
    }
}
