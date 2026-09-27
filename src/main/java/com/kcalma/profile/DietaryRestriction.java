package com.kcalma.profile;

/**
 * A hard constraint on suggested meals (see {@code GeminiMealSuggester}) — never a macro/calorie
 * input to {@link NutritionCalculator}. A profile can hold any combination of these (empty by
 * default). Persisted as a Postgres {@code text[]} (see {@code V10} migration and {@link
 * UserProfile#getDietaryRestrictions()}), so the exact enum names are also the values the
 * database-level CHECK constraint allows.
 */
public enum DietaryRestriction {
    VEGETARIAN,
    VEGAN,
    GLUTEN_FREE,
    LACTOSE_FREE
}
