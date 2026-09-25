package com.kcalma.food.dto;

import com.kcalma.food.MealType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Body for PATCH /api/food/entries/{id}: the only two fields a saved entry may change. */
public record UpdateFoodEntryRequest(
        @NotNull @DecimalMin(value = "0.1") @DecimalMax(value = "5000") BigDecimal grams, @NotNull MealType mealType) {}
