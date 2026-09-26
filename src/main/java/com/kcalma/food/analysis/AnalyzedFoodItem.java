package com.kcalma.food.analysis;

/**
 * One food item detected in a photo/text description (or suggested for a meal), with its
 * estimated portion and per-100g nutrition. {@code canonicalNameEn} is Gemini's own best-effort
 * USDA-style English name (e.g. "strawberries, raw", "beef, ground, 80% lean, cooked") — used by
 * {@code com.kcalma.food.reference.FoodReferenceMatcher} to look up {@code app.food_reference};
 * the per-100g fields here remain a FALLBACK estimate for when no confident match is found.
 */
public record AnalyzedFoodItem(
        String name,
        String canonicalNameEn,
        double grams,
        double kcalPer100,
        double proteinPer100,
        double fatPer100,
        double carbsPer100,
        double fiberPer100,
        double sugarPer100,
        double sodiumMgPer100) {}
