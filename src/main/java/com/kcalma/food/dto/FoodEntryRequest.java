package com.kcalma.food.dto;

import com.kcalma.food.FoodSource;
import com.kcalma.food.MealType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** One item inside a POST /api/food/entries batch — from a confirmed photo analysis or manual add. */
public record FoodEntryRequest(
        @NotNull LocalDate entryDate,
        @NotNull MealType mealType,
        @NotBlank String name,
        @NotNull @DecimalMin(value = "0.1") @DecimalMax(value = "5000") BigDecimal grams,
        @NotNull @DecimalMin("0.0") BigDecimal kcalPer100,
        @NotNull @DecimalMin("0.0") BigDecimal proteinPer100,
        @NotNull @DecimalMin("0.0") BigDecimal fatPer100,
        @NotNull @DecimalMin("0.0") BigDecimal carbsPer100,
        @NotNull @DecimalMin("0.0") BigDecimal fiberPer100,
        @NotNull @DecimalMin("0.0") BigDecimal sugarPer100,
        @NotNull @DecimalMin("0.0") BigDecimal sodiumMgPer100,
        @NotNull FoodSource source) {}
