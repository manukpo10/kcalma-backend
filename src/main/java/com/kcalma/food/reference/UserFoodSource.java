package com.kcalma.food.reference;

/**
 * Where a personal-library ({@code app.user_food}) row's nutrient values originally came from.
 * Distinct from {@link com.kcalma.food.FoodSource}: that enum describes one logged {@code
 * food_entry}'s provenance (and includes {@code PERSONAL} for "matched the library"); this one
 * describes the library row itself, which by definition is never "matched from the library".
 */
public enum UserFoodSource {
    USDA,
    ESTIMATED,
    USER
}
