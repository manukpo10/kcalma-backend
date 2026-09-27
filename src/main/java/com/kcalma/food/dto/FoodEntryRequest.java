package com.kcalma.food.dto;

import com.kcalma.food.FoodSource;
import com.kcalma.food.MealType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One DISH inside a POST /api/food/entries batch — from a confirmed photo/text analysis or manual
 * add. {@code kcalPer100}..{@code sodiumMgPer100} are the dish's own derived per-100g values (see
 * {@code com.kcalma.food.reference.ResolvedDish}). {@code ingredients} is optional: a client that
 * doesn't send a breakdown yet gets a single synthetic ingredient equal to the dish itself (see
 * {@code FoodEntryService}), so every entry saved from here on always has one.
 */
public record FoodEntryRequest(
        @NotNull LocalDate entryDate,
        @NotNull MealType mealType,
        @NotBlank String name,
        @NotNull @DecimalMin(value = "0.1") @DecimalMax(value = "5000") BigDecimal grams,
        @NotNull @DecimalMin("0.0") @DecimalMax("900") BigDecimal kcalPer100,
        @NotNull @DecimalMin("0.0") @DecimalMax("100") BigDecimal proteinPer100,
        @NotNull @DecimalMin("0.0") @DecimalMax("100") BigDecimal fatPer100,
        @NotNull @DecimalMin("0.0") @DecimalMax("100") BigDecimal carbsPer100,
        @NotNull @DecimalMin("0.0") @DecimalMax("100") BigDecimal fiberPer100,
        @NotNull @DecimalMin("0.0") @DecimalMax("100") BigDecimal sugarPer100,
        @NotNull @DecimalMin("0.0") @DecimalMax("40000") BigDecimal sodiumMgPer100,
        @NotNull FoodSource source,
        Long fdcId,
        @Valid List<FoodEntryIngredientRequest> ingredients) {}
