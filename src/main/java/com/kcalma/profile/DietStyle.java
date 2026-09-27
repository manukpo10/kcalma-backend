package com.kcalma.profile;

/**
 * Macro distribution style layered on top of the calorie/protein targets {@link
 * NutritionCalculator} already derives from {@link Goal}/{@link Pace} — this only changes how the
 * remaining calories split between fat and carbs (and, for {@code KETO}, fiber/sugar), never the
 * calorie or protein target itself. Also passed to {@code GeminiMealSuggester} as a hard
 * constraint on suggested meals. Defaults to {@code BALANCED} when not set.
 */
public enum DietStyle {
    BALANCED,
    HIGH_PROTEIN,
    LOW_CARB,
    KETO
}
