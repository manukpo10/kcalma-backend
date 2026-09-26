package com.kcalma.food.dto;

import com.kcalma.food.FoodEntryIngredient;
import com.kcalma.food.FoodSource;
import java.math.BigDecimal;

/** One ingredient inside a saved {@code FoodEntryResponse}'s {@code ingredients} breakdown. */
public record FoodEntryIngredientResponse(
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

    public static FoodEntryIngredientResponse from(FoodEntryIngredient ingredient) {
        return new FoodEntryIngredientResponse(
                ingredient.name(),
                ingredient.grams(),
                ingredient.kcalPer100(),
                ingredient.proteinPer100(),
                ingredient.fatPer100(),
                ingredient.carbsPer100(),
                ingredient.fiberPer100(),
                ingredient.sugarPer100(),
                ingredient.sodiumMgPer100(),
                ingredient.source(),
                ingredient.fdcId());
    }
}
