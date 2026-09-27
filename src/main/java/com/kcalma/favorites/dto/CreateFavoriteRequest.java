package com.kcalma.favorites.dto;

import com.kcalma.food.FoodSource;
import com.kcalma.food.MealType;
import com.kcalma.food.dto.FoodEntryIngredientRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Body for POST /api/favorites: EITHER {@code entryId} (favorite an already-logged food entry as
 * it stands) OR a Dish's own fields ({@code name}..{@code fdcId}/{@code ingredients}) — see {@code
 * FavoriteDishService#resolveDish} for the cross-field "exactly one of the two, and the Dish shape
 * is complete when used" rule, which can't be expressed as plain Bean Validation annotations since
 * every Dish field must stay optional here to keep the {@code entryId} shape valid too.
 * {@code mealType} is always optional, valid with either shape.
 */
public record CreateFavoriteRequest(
        UUID entryId,
        String name,
        @DecimalMin("0.1") @DecimalMax("5000") BigDecimal grams,
        @DecimalMin("0.0") @DecimalMax("900") BigDecimal kcalPer100,
        @DecimalMin("0.0") @DecimalMax("100") BigDecimal proteinPer100,
        @DecimalMin("0.0") @DecimalMax("100") BigDecimal fatPer100,
        @DecimalMin("0.0") @DecimalMax("100") BigDecimal carbsPer100,
        @DecimalMin("0.0") @DecimalMax("100") BigDecimal fiberPer100,
        @DecimalMin("0.0") @DecimalMax("100") BigDecimal sugarPer100,
        @DecimalMin("0.0") @DecimalMax("40000") BigDecimal sodiumMgPer100,
        FoodSource source,
        Long fdcId,
        @Valid List<FoodEntryIngredientRequest> ingredients,
        MealType mealType) {

    public boolean isFromEntry() {
        return entryId != null;
    }
}
