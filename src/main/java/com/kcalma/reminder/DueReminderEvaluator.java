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
import org.springframework.stereotype.Component;

/**
 * The pure "what's due right now, and should it actually be sent" decision — deliberately taking
 * plain values ({@link Signals}) instead of repositories, so every SMART-skip rule can be unit
 * tested with a fixed {@link LocalDateTime} and no Spring/mocking at all. {@link ReminderScheduler}
 * is the only caller: it gathers {@link Signals} from the real repositories, calls {@link
 * #evaluate}, then separately claims the {@code app.reminder_log} dedupe slot for each {@link Due}
 * before actually sending — this class knows nothing about dedupe or persistence.
 */
@Component
public class DueReminderEvaluator {

    /** Everything about "today" this class needs to decide what's due, gathered by the caller. */
    public record Signals(Set<MealType> mealsLoggedToday, int waterMlToday, Integer waterTargetMl, boolean weighInLoggedToday) {
    }

    /** One reminder that should be sent right now. {@code reminderKey} is the {@code app.reminder_log} dedupe key. */
    public record Due(String reminderKey, String title, String body, String url) {
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
            if (mealSetting == null || !mealSetting.enabled() || !matchesMinute(mealSetting.time(), nowTime)) {
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
        if (from == null || to == null || nowTime.isBefore(from) || nowTime.isAfter(to) || water.everyHours() <= 0) {
            return;
        }
        long elapsedMinutes = Duration.between(from, nowTime).toMinutes();
        if (elapsedMinutes % (water.everyHours() * 60L) != 0) {
            return; // not one of the configured slots (e.g. every 2h from 10:00 -> 10:00,12:00,...)
        }

        long windowMinutes = Duration.between(from, to).toMinutes();
        double elapsedFraction = windowMinutes <= 0 ? 1.0 : Math.min(1.0, elapsedMinutes / (double) windowMinutes);
        int expectedMlByNow = (int) Math.round(signals.waterTargetMl() * elapsedFraction);
        if (signals.waterMlToday() >= expectedMlByNow) {
            return; // SMART skip: already at or ahead of the pro-rata target for this hour
        }

        due.add(new Due("WATER_" + nowTime, "¡Hidratate!", "Todavía no tomaste suficiente agua hoy.", "/"));
    }

    private void checkWeighIn(
            List<Due> due, LocalDateTime now, LocalTime nowTime, ReminderSettingsData.WeighInSetting weighIn, Signals signals) {
        if (weighIn == null || !weighIn.enabled() || !matchesMinute(weighIn.time(), nowTime)) {
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

    private boolean matchesMinute(String hhmm, LocalTime nowTime) {
        LocalTime configured = parseTimeOrNull(hhmm);
        return configured != null && configured.equals(nowTime);
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
