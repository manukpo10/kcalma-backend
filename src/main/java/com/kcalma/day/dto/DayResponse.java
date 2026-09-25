package com.kcalma.day.dto;

import com.kcalma.food.MealType;
import com.kcalma.food.NutritionMath;
import com.kcalma.food.dto.FoodEntryResponse;
import com.kcalma.profile.dto.NutritionTargetsResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Body for GET /api/day: today's targets vs. what was actually logged. */
public record DayResponse(
        LocalDate date,
        NutritionTargetsResponse targets,
        NutritionMath.Totals consumed,
        NutritionMath.Totals remaining,
        Exceeded exceeded,
        Map<MealType, List<FoodEntryResponse>> meals) {

    public record Exceeded(boolean kcal, boolean sugar, boolean sodium) {}
}
