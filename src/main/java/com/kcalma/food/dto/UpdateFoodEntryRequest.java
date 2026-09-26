package com.kcalma.food.dto;

import com.kcalma.food.MealType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * Body for PATCH /api/food/entries/{id}. {@code ingredients} is optional:
 *
 * <ul>
 *   <li>absent ({@code null}) — a plain grams change: the server scales every stored ingredient's
 *       grams proportionally to the new dish grams (per-100g values don't change — they're
 *       invariant under uniform scaling);
 *   <li>present — the edited breakdown (grams changed and/or an ingredient removed): the server
 *       recomputes the dish's own grams/per-100g/source FROM these ingredients, ignoring whatever
 *       {@code grams} was sent alongside them.
 * </ul>
 */
public record UpdateFoodEntryRequest(
        @NotNull @DecimalMin(value = "0.1") @DecimalMax(value = "5000") BigDecimal grams,
        @NotNull MealType mealType,
        @Valid List<FoodEntryIngredientRequest> ingredients) {}
