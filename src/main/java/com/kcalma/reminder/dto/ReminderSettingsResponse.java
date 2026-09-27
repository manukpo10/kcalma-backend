package com.kcalma.reminder.dto;

import com.kcalma.reminder.ReminderSettingsData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code GET}/{@code PUT /api/reminders} response body. */
public record ReminderSettingsResponse(
        boolean enabled, Map<String, MealSettingResponse> meals, WaterSettingResponse water, WeighInSettingResponse weighIn) {

    public record MealSettingResponse(boolean enabled, String time) {
        static MealSettingResponse from(ReminderSettingsData.MealSetting data) {
            return new MealSettingResponse(data.enabled(), data.time());
        }
    }

    public record WaterSettingResponse(boolean enabled, int everyHours, String from, String to) {
        static WaterSettingResponse from(ReminderSettingsData.WaterSetting data) {
            return new WaterSettingResponse(data.enabled(), data.everyHours(), data.from(), data.to());
        }
    }

    public record WeighInSettingResponse(boolean enabled, List<String> days, String time) {
        static WeighInSettingResponse from(ReminderSettingsData.WeighInSetting data) {
            return new WeighInSettingResponse(data.enabled(), data.days(), data.time());
        }
    }

    public static ReminderSettingsResponse from(ReminderSettingsData data) {
        Map<String, MealSettingResponse> meals = new LinkedHashMap<>();
        data.meals().forEach((key, value) -> meals.put(key, MealSettingResponse.from(value)));
        return new ReminderSettingsResponse(
                data.enabled(), meals, WaterSettingResponse.from(data.water()), WeighInSettingResponse.from(data.weighIn()));
    }
}
