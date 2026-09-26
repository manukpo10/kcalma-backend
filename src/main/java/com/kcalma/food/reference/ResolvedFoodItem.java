package com.kcalma.food.reference;

import com.kcalma.food.FoodSource;
import com.kcalma.food.NutritionMath;

/**
 * One {@code AnalyzedFoodItem} after {@link FoodReferenceMatcher} has resolved its nutrient values
 * — against the caller's personal library, the USDA reference table, or (as a last resort)
 * Gemini's own per-100g estimate. {@code fdcId}/{@code matchedDescription} are {@code null} unless
 * {@code source} is {@code PERSONAL} (with a library row that itself came from a USDA match) or
 * {@code USDA}.
 */
public record ResolvedFoodItem(
        String name,
        String canonicalNameEn,
        double grams,
        NutritionMath.Per100 per100,
        FoodSource source,
        Long fdcId,
        String matchedDescription) {}
