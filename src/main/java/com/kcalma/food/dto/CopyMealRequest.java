package com.kcalma.food.dto;

import com.kcalma.food.MealType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Body for POST /api/food/entries/copy: re-logs one meal/day onto another day, under the same meal type. */
public record CopyMealRequest(@NotNull LocalDate fromDate, @NotNull LocalDate toDate, @NotNull MealType mealType) {}
