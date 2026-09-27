package com.kcalma.reminder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The whole {@code GET}/{@code PUT /api/reminders} document, persisted as a single {@code jsonb}
 * value in {@code app.reminder_settings.settings} (V15__push_and_reminders.sql). {@code
 * com.kcalma.reminder.dto.ReminderSettingsRequest}/{@code ReminderSettingsResponse} carry the same
 * shape across HTTP (with Bean Validation on the request side) — same separation as every other
 * feature in this codebase (e.g. {@code ProfileRequest}/{@code ProfileResponse}).
 *
 * <p>{@code meals} is keyed by {@link com.kcalma.food.MealType} name — {@code "DESAYUNO"},
 * {@code "ALMUERZO"}, {@code "MERIENDA"}, {@code "CENA"} (never {@code "SNACK"}: nobody gets
 * reminded to snack) — as a plain {@code Map<String, MealSetting>} rather than an enum-keyed map,
 * so Jackson's (de)serialization of the jsonb column has no enum-key ambiguity to get subtly wrong
 * (the wire shape is identical either way; see {@code DatabaseIntegrationTest}'s round-trip test).
 *
 * <p>The master {@code enabled} switch defaults OFF, but every reminder TYPE underneath it
 * defaults ON: turning the master switch on is meant to immediately activate a sensible full set
 * of reminders, not leave the user having to also flip four more toggles just to get one meal
 * reminder. {@link ReminderScheduler} always checks the master switch first, so "disabled overall"
 * genuinely means nothing fires, regardless of the leaf values below.
 */
public record ReminderSettingsData(boolean enabled, Map<String, MealSetting> meals, WaterSetting water, WeighInSetting weighIn) {

    /** The only meal types worth a reminder — {@code MealType.SNACK} is deliberately excluded. */
    public static final List<String> REMINDABLE_MEALS = List.of("DESAYUNO", "ALMUERZO", "MERIENDA", "CENA");

    public static final Set<String> VALID_WEEKDAY_CODES =
            Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    public record MealSetting(boolean enabled, String time) {
    }

    public record WaterSetting(boolean enabled, int everyHours, String from, String to) {
    }

    public record WeighInSetting(boolean enabled, List<String> days, String time) {
    }

    public static ReminderSettingsData defaults() {
        Map<String, MealSetting> meals = new LinkedHashMap<>();
        meals.put("DESAYUNO", new MealSetting(true, "08:30"));
        meals.put("ALMUERZO", new MealSetting(true, "13:00"));
        meals.put("MERIENDA", new MealSetting(true, "17:00"));
        meals.put("CENA", new MealSetting(true, "21:00"));
        return new ReminderSettingsData(
                false, meals, new WaterSetting(true, 2, "10:00", "20:00"), new WeighInSetting(true, List.of("MON", "THU"), "08:00"));
    }
}
