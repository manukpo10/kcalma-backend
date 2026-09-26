package com.kcalma.suggestions.dto;

import com.kcalma.food.NutritionMath;
import com.kcalma.food.dto.AnalyzedDishResponse;
import java.util.List;

/**
 * One suggested meal option: a title, a one-line description, approximate prep minutes, a
 * one-line "por qué te sirve", its dishes (same shape as the food-analysis results — see {@link
 * AnalyzedDishResponse}), and totals always computed server-side from those dishes' resolved
 * ingredients via {@link NutritionMath} — never taken from whatever the model might have claimed.
 */
public record SuggestionOptionResponse(
        String title,
        String description,
        int prepMinutes,
        String why,
        List<AnalyzedDishResponse> dishes,
        NutritionMath.Totals totals) {}
