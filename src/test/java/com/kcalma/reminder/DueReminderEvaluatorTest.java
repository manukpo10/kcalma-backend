package com.kcalma.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import com.kcalma.food.MealType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Every SMART-skip and dedupe-KEY-shape rule {@link DueReminderEvaluator} decides, driven entirely
 * by fixed {@link LocalDateTime} values — no Spring context, no Docker, no mocks. Dedupe itself
 * (the DB side of "never double-send") is covered separately in {@code DatabaseIntegrationTest}.
 */
class DueReminderEvaluatorTest {

    // A Sunday immediately followed by a Monday -- computed, not hardcoded, so this test never
    // depends on correctly remembering what weekday some specific calendar date fell on.
    private static final LocalDate A_MONDAY = LocalDate.of(2026, 9, 28).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    private static final LocalDate THE_SUNDAY_BEFORE_IT = A_MONDAY.minusDays(1);

    private final DueReminderEvaluator evaluator = new DueReminderEvaluator();

    private static final DueReminderEvaluator.Signals NOTHING_LOGGED = new DueReminderEvaluator.Signals(Set.of(), 0, 2000, false);

    @Test
    void evaluate_masterSwitchDisabled_returnsNothingEvenWhenEveryLeafToggleWouldOtherwiseMatch() {
        ReminderSettingsData allEnabledButMaster = new ReminderSettingsData(
                false, allMealsAt("08:30"), new ReminderSettingsData.WaterSetting(true, 2, "08:00", "08:30"),
                new ReminderSettingsData.WeighInSetting(true, List.of("MON"), "08:30"));

        List<DueReminderEvaluator.Due> due =
                evaluator.evaluate(LocalDateTime.of(A_MONDAY, java.time.LocalTime.of(8, 30)), allEnabledButMaster, NOTHING_LOGGED);

        assertThat(due).isEmpty();
    }

    @Test
    void evaluate_mealEnabledTimeMatchesAndNotYetLogged_isDue() {
        ReminderSettingsData settings = onlyMeal("ALMUERZO", "13:00");

        List<DueReminderEvaluator.Due> due =
                evaluator.evaluate(LocalDateTime.of(A_MONDAY, java.time.LocalTime.of(13, 0)), settings, NOTHING_LOGGED);

        assertThat(due).hasSize(1);
        assertThat(due.get(0).reminderKey()).isEqualTo("MEAL_ALMUERZO");
        assertThat(due.get(0).url()).isEqualTo("/agregar?meal=ALMUERZO");
        assertThat(due.get(0).title()).isEqualTo("¿Qué almorzaste?");
    }

    @Test
    void evaluate_mealAlreadyLoggedToday_isSkippedEvenThoughTheTimeMatches() {
        ReminderSettingsData settings = onlyMeal("ALMUERZO", "13:00");
        DueReminderEvaluator.Signals alreadyAte = new DueReminderEvaluator.Signals(Set.of(MealType.ALMUERZO), 0, 2000, false);

        List<DueReminderEvaluator.Due> due = evaluator.evaluate(LocalDateTime.of(A_MONDAY, java.time.LocalTime.of(13, 0)), settings, alreadyAte);

        assertThat(due).isEmpty();
    }

    @Test
    void evaluate_mealDisabled_isSkippedEvenThoughTheTimeMatches() {
        ReminderSettingsData settings = new ReminderSettingsData(
                true, disabledMeals(), disabledWater(), disabledWeighIn()); // ALMUERZO left disabled at "13:00" inside disabledMeals()

        List<DueReminderEvaluator.Due> due = evaluator.evaluate(LocalDateTime.of(A_MONDAY, java.time.LocalTime.of(13, 0)), settings, NOTHING_LOGGED);

        assertThat(due).isEmpty();
    }

    @Test
    void evaluate_mealTimeOffByOneMinute_isNotDue() {
        ReminderSettingsData settings = onlyMeal("ALMUERZO", "13:00");

        List<DueReminderEvaluator.Due> due = evaluator.evaluate(LocalDateTime.of(A_MONDAY, java.time.LocalTime.of(13, 1)), settings, NOTHING_LOGGED);

        assertThat(due).isEmpty();
    }

    @Test
    void evaluate_waterAtTheFirstSlotOfTheWindow_isNeverDueBecauseTheProRataTargetThereIsZero() {
        ReminderSettingsData settings = onlyWater(2, "10:00", "20:00");

        List<DueReminderEvaluator.Due> due = evaluator.evaluate(LocalDateTime.of(A_MONDAY, java.time.LocalTime.of(10, 0)), settings, NOTHING_LOGGED);

        assertThat(due).isEmpty(); // 0 minutes elapsed in the window -> 0 ml expected -> 0 ml logged already satisfies it
    }

    @Test
    void evaluate_waterAtALaterSlotBelowTheProRataTarget_isDue() {
        ReminderSettingsData settings = onlyWater(2, "10:00", "20:00"); // target 2000ml over 10h -> by 14:00 (4h in) expect 800ml
        DueReminderEvaluator.Signals behindPace = new DueReminderEvaluator.Signals(Set.of(), 100, 2000, false);

        List<DueReminderEvaluator.Due> due = evaluator.evaluate(LocalDateTime.of(A_MONDAY, java.time.LocalTime.of(14, 0)), settings, behindPace);

        assertThat(due).hasSize(1);
        assertThat(due.get(0).reminderKey()).isEqualTo("WATER_14:00");
        assertThat(due.get(0).url()).isEqualTo("/");
    }

    @Test
    void evaluate_waterAtALaterSlotAlreadyAtTheProRataTarget_isSkipped() {
        ReminderSettingsData settings = onlyWater(2, "10:00", "20:00"); // by 14:00 expect 800ml
        DueReminderEvaluator.Signals onPace = new DueReminderEvaluator.Signals(Set.of(), 900, 2000, false);

        List<DueReminderEvaluator.Due> due = evaluator.evaluate(LocalDateTime.of(A_MONDAY, java.time.LocalTime.of(14, 0)), settings, onPace);

        assertThat(due).isEmpty();
    }

    @Test
    void evaluate_waterOutsideTheFromToWindow_isNotDueEvenIfOtherwiseASlotMinute() {
        ReminderSettingsData settings = onlyWater(2, "10:00", "20:00");
        DueReminderEvaluator.Signals behindPace = new DueReminderEvaluator.Signals(Set.of(), 0, 2000, false);

        List<DueReminderEvaluator.Due> due = evaluator.evaluate(LocalDateTime.of(A_MONDAY, java.time.LocalTime.of(22, 0)), settings, behindPace);

        assertThat(due).isEmpty();
    }

    @Test
    void evaluate_waterNotOnAnEveryHoursSlotBoundary_isNotDue() {
        ReminderSettingsData settings = onlyWater(2, "10:00", "20:00"); // slots: 10,12,14,16,18,20 -- 11:00 isn't one
        DueReminderEvaluator.Signals behindPace = new DueReminderEvaluator.Signals(Set.of(), 0, 2000, false);

        List<DueReminderEvaluator.Due> due = evaluator.evaluate(LocalDateTime.of(A_MONDAY, java.time.LocalTime.of(11, 0)), settings, behindPace);

        assertThat(due).isEmpty();
    }

    @Test
    void evaluate_weighInOnAConfiguredDayAtTheConfiguredTimeAndNotYetLogged_isDue() {
        ReminderSettingsData settings = onlyWeighIn(List.of("MON", "THU"), "08:00");

        List<DueReminderEvaluator.Due> due = evaluator.evaluate(LocalDateTime.of(A_MONDAY, java.time.LocalTime.of(8, 0)), settings, NOTHING_LOGGED);

        assertThat(due).hasSize(1);
        assertThat(due.get(0).reminderKey()).isEqualTo("WEIGH_IN");
        assertThat(due.get(0).url()).isEqualTo("/progreso");
    }

    @Test
    void evaluate_weighInOnANonConfiguredDay_isNotDue() {
        ReminderSettingsData settings = onlyWeighIn(List.of("MON", "THU"), "08:00");
        LocalDate aTuesday = A_MONDAY.plusDays(1);

        List<DueReminderEvaluator.Due> due = evaluator.evaluate(LocalDateTime.of(aTuesday, java.time.LocalTime.of(8, 0)), settings, NOTHING_LOGGED);

        assertThat(due).isEmpty();
    }

    @Test
    void evaluate_weighInAlreadyLoggedToday_isSkipped() {
        ReminderSettingsData settings = onlyWeighIn(List.of("MON"), "08:00");
        DueReminderEvaluator.Signals alreadyWeighedIn = new DueReminderEvaluator.Signals(Set.of(), 0, 2000, true);

        List<DueReminderEvaluator.Due> due = evaluator.evaluate(LocalDateTime.of(A_MONDAY, java.time.LocalTime.of(8, 0)), settings, alreadyWeighedIn);

        assertThat(due).isEmpty();
    }

    @Test
    void evaluate_aroundMidnight_theMinuteBeforeAndTheMinuteAfterDoNotBothFireTheSundayOnlyReminder() {
        // weigh-in configured for Sunday only, at 23:59 -- the last minute of that Sunday.
        ReminderSettingsData settings = onlyWeighIn(List.of("SUN"), "23:59");

        List<DueReminderEvaluator.Due> lastMinuteOfSunday =
                evaluator.evaluate(LocalDateTime.of(THE_SUNDAY_BEFORE_IT, java.time.LocalTime.of(23, 59)), settings, NOTHING_LOGGED);
        assertThat(lastMinuteOfSunday).hasSize(1); // still Sunday, time matches -> due

        List<DueReminderEvaluator.Due> oneMinuteLater =
                evaluator.evaluate(LocalDateTime.of(A_MONDAY, java.time.LocalTime.of(0, 0)), settings, NOTHING_LOGGED);
        assertThat(oneMinuteLater).isEmpty(); // now Monday 00:00 -- both the day AND the time moved on, so it must not fire again
    }

    @Test
    void evaluate_aroundMidnight_theWeekdayUsedForTheDaysCheckFlipsExactlyAtMidnightForTheSameTimeOfDay() {
        // Isolates the day-of-week computation itself: SAME configured time (00:00) evaluated on
        // the two calendar days either side of midnight -- only the date argument differs.
        ReminderSettingsData settings = onlyWeighIn(List.of("SUN"), "00:00");

        List<DueReminderEvaluator.Due> sundayMidnight =
                evaluator.evaluate(LocalDateTime.of(THE_SUNDAY_BEFORE_IT, java.time.LocalTime.of(0, 0)), settings, NOTHING_LOGGED);
        assertThat(sundayMidnight).hasSize(1); // Sunday 00:00 -- SUN is configured -> due

        List<DueReminderEvaluator.Due> mondayMidnightOneDayLater =
                evaluator.evaluate(LocalDateTime.of(A_MONDAY, java.time.LocalTime.of(0, 0)), settings, NOTHING_LOGGED);
        assertThat(mondayMidnightOneDayLater).isEmpty(); // Monday 00:00, same time-of-day -- MON is not configured -> not due
    }

    @Test
    void evaluate_multipleCategoriesDueInTheSameMinute_returnsAllOfThem() {
        Map<String, ReminderSettingsData.MealSetting> meals = allMealsAt("08:00");
        ReminderSettingsData settings =
                new ReminderSettingsData(true, meals, disabledWater(), new ReminderSettingsData.WeighInSetting(true, List.of("MON"), "08:00"));

        List<DueReminderEvaluator.Due> due = evaluator.evaluate(LocalDateTime.of(A_MONDAY, java.time.LocalTime.of(8, 0)), settings, NOTHING_LOGGED);

        // 4 meals all at 08:00 + weigh-in at 08:00 -- water is disabled here, its own pro-rata
        // logic is covered by the dedicated evaluate_water* tests above.
        assertThat(due).extracting(DueReminderEvaluator.Due::reminderKey)
                .containsExactlyInAnyOrder("MEAL_DESAYUNO", "MEAL_ALMUERZO", "MEAL_MERIENDA", "MEAL_CENA", "WEIGH_IN");
    }

    private ReminderSettingsData onlyMeal(String mealKey, String time) {
        Map<String, ReminderSettingsData.MealSetting> meals = disabledMeals();
        meals.put(mealKey, new ReminderSettingsData.MealSetting(true, time));
        return new ReminderSettingsData(true, meals, disabledWater(), disabledWeighIn());
    }

    private ReminderSettingsData onlyWater(int everyHours, String from, String to) {
        return new ReminderSettingsData(true, disabledMeals(), new ReminderSettingsData.WaterSetting(true, everyHours, from, to), disabledWeighIn());
    }

    private ReminderSettingsData onlyWeighIn(List<String> days, String time) {
        return new ReminderSettingsData(true, disabledMeals(), disabledWater(), new ReminderSettingsData.WeighInSetting(true, days, time));
    }

    private Map<String, ReminderSettingsData.MealSetting> disabledMeals() {
        Map<String, ReminderSettingsData.MealSetting> meals = new LinkedHashMap<>();
        for (String key : ReminderSettingsData.REMINDABLE_MEALS) {
            meals.put(key, new ReminderSettingsData.MealSetting(false, "13:00"));
        }
        return meals;
    }

    private Map<String, ReminderSettingsData.MealSetting> allMealsAt(String time) {
        Map<String, ReminderSettingsData.MealSetting> meals = new LinkedHashMap<>();
        for (String key : ReminderSettingsData.REMINDABLE_MEALS) {
            meals.put(key, new ReminderSettingsData.MealSetting(true, time));
        }
        return meals;
    }

    private ReminderSettingsData.WaterSetting disabledWater() {
        return new ReminderSettingsData.WaterSetting(false, 2, "10:00", "20:00");
    }

    private ReminderSettingsData.WeighInSetting disabledWeighIn() {
        return new ReminderSettingsData.WeighInSetting(false, List.of("MON"), "08:00");
    }
}
