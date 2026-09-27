package com.kcalma.reminder.dto;

import com.kcalma.reminder.ReminderSettingsData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code PUT /api/reminders} request body — a full replace, so the client always sends the
 * complete shape {@code GET} gave it. Per-field format ({@code "HH:mm"}, weekday codes) is
 * validated here structurally; the one rule that can't be expressed on a {@code Map}'s key set —
 * {@code meals} needing exactly the four reminder-eligible meal types — is checked in {@code
 * ReminderSettingsService#update}.
 */
public record ReminderSettingsRequest(
        @NotNull Boolean enabled,
        @NotEmpty Map<@NotBlank String, @NotNull @Valid MealSettingRequest> meals,
        @NotNull @Valid WaterSettingRequest water,
        @NotNull @Valid WeighInSettingRequest weighIn) {

    static final String TIME_PATTERN = "^([01]\\d|2[0-3]):[0-5]\\d$";
    private static final String WEEKDAY_PATTERN = "MON|TUE|WED|THU|FRI|SAT|SUN";

    public record MealSettingRequest(@NotNull Boolean enabled, @NotBlank @Pattern(regexp = TIME_PATTERN) String time) {
        ReminderSettingsData.MealSetting toData() {
            return new ReminderSettingsData.MealSetting(enabled, time);
        }
    }

    public record WaterSettingRequest(
            @NotNull Boolean enabled,
            @Min(1) @Max(12) int everyHours,
            @NotBlank @Pattern(regexp = TIME_PATTERN) String from,
            @NotBlank @Pattern(regexp = TIME_PATTERN) String to) {
        ReminderSettingsData.WaterSetting toData() {
            return new ReminderSettingsData.WaterSetting(enabled, everyHours, from, to);
        }
    }

    public record WeighInSettingRequest(
            @NotNull Boolean enabled,
            @NotEmpty List<@Pattern(regexp = WEEKDAY_PATTERN) String> days,
            @NotBlank @Pattern(regexp = TIME_PATTERN) String time) {
        ReminderSettingsData.WeighInSetting toData() {
            return new ReminderSettingsData.WeighInSetting(enabled, days, time);
        }
    }

    public ReminderSettingsData toData() {
        Map<String, ReminderSettingsData.MealSetting> mealData = new LinkedHashMap<>();
        meals.forEach((key, value) -> mealData.put(key, value.toData()));
        return new ReminderSettingsData(enabled, mealData, water.toData(), weighIn.toData());
    }
}
