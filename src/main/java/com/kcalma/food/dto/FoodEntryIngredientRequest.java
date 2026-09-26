package com.kcalma.food.dto;

import com.kcalma.food.FoodEntryIngredient;
import com.kcalma.food.FoodSource;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * One ingredient inside a POST /api/food/entries dish, or inside the optional {@code ingredients}
 * PATCH edit on a saved entry (see {@link UpdateFoodEntryRequest}) — the already-resolved
 * per-100g values/source/fdcId, same shape persisted in {@code food_entry.ingredients} (see
 * {@link FoodEntryIngredient}).
 */
public record FoodEntryIngredientRequest(
        @NotBlank String name,
        @NotNull @DecimalMin(value = "0.1") @DecimalMax(value = "5000") BigDecimal grams,
        @NotNull @DecimalMin("0.0") BigDecimal kcalPer100,
        @NotNull @DecimalMin("0.0") BigDecimal proteinPer100,
        @NotNull @DecimalMin("0.0") BigDecimal fatPer100,
        @NotNull @DecimalMin("0.0") BigDecimal carbsPer100,
        @NotNull @DecimalMin("0.0") BigDecimal fiberPer100,
        @NotNull @DecimalMin("0.0") BigDecimal sugarPer100,
        @NotNull @DecimalMin("0.0") BigDecimal sodiumMgPer100,
        @NotNull FoodSource source,
        Long fdcId) {

    public FoodEntryIngredient toIngredient() {
        return new FoodEntryIngredient(
                name, grams, kcalPer100, proteinPer100, fatPer100, carbsPer100, fiberPer100, sugarPer100,
                sodiumMgPer100, source, fdcId);
    }
}
