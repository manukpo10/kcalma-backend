package com.kcalma.food;

import com.kcalma.food.NutritionMath.Per100;
import java.math.BigDecimal;

/**
 * One resolved ingredient inside a saved {@link FoodEntry}'s {@code ingredients} column — the whole
 * list is persisted as a single {@code jsonb} value (see {@code V9__food_entry_ingredients.sql}
 * and {@code FoodEntry#ingredients}), so this type has no identity/id of its own, unlike {@link
 * com.kcalma.food.reference.UserFood}. Deliberately mirrors {@code
 * com.kcalma.food.dto.AnalyzedItemResponse} minus {@code matchedDescription}, which is a
 * preview-only UI detail never worth persisting.
 */
public record FoodEntryIngredient(
        String name,
        BigDecimal grams,
        BigDecimal kcalPer100,
        BigDecimal proteinPer100,
        BigDecimal fatPer100,
        BigDecimal carbsPer100,
        BigDecimal fiberPer100,
        BigDecimal sugarPer100,
        BigDecimal sodiumMgPer100,
        FoodSource source,
        Long fdcId) {

    public Per100 toPer100() {
        return new Per100(
                kcalPer100.doubleValue(),
                proteinPer100.doubleValue(),
                fatPer100.doubleValue(),
                carbsPer100.doubleValue(),
                fiberPer100.doubleValue(),
                sugarPer100.doubleValue(),
                sodiumMgPer100.doubleValue());
    }

    /** Copy with different grams — used to proportionally rescale a dish's stored breakdown. */
    public FoodEntryIngredient withGrams(BigDecimal newGrams) {
        return new FoodEntryIngredient(
                name, newGrams, kcalPer100, proteinPer100, fatPer100, carbsPer100, fiberPer100, sugarPer100,
                sodiumMgPer100, source, fdcId);
    }
}
