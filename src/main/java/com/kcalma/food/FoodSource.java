package com.kcalma.food;

/**
 * Where a food entry's nutrient values came from, in {@code com.kcalma.food.reference.FoodReferenceMatcher}'s
 * priority order: {@code PERSONAL} (matched the caller's own {@code app.user_food} library),
 * {@code USDA} (matched {@code app.food_reference}, USDA FoodData Central), {@code ESTIMATED}
 * (no confident match — Gemini's own per-100g estimate, used as-is), or {@code MANUAL} (the values
 * were typed by hand, e.g. via the manual-add flow — never resolved against either table).
 */
public enum FoodSource {
    PERSONAL,
    USDA,
    ESTIMATED,
    MANUAL
}
