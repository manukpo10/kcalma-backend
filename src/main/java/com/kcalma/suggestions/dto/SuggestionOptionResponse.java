package com.kcalma.suggestions.dto;

import com.kcalma.food.NutritionMath;
import com.kcalma.food.dto.AnalyzedItemResponse;
import java.util.List;

/**
 * One suggested meal option: a title, a one-line description, approximate prep minutes, a
 * one-line "por qué te sirve", its items (same shape as the food-analysis results), and totals
 * always computed server-side from those items via {@link NutritionMath} — never taken from
 * whatever the model might have claimed.
 */
public record SuggestionOptionResponse(
        String title,
        String description,
        int prepMinutes,
        String why,
        List<AnalyzedItemResponse> items,
        NutritionMath.Totals totals) {}
