package com.kcalma.food.dto;

import com.kcalma.food.FoodEntry;
import com.kcalma.food.FoodSource;
import com.kcalma.food.MealType;
import com.kcalma.food.NutritionMath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FoodEntryResponse(
        UUID id,
        LocalDate entryDate,
        MealType mealType,
        String name,
        BigDecimal grams,
        BigDecimal kcalPer100,
        BigDecimal proteinPer100,
        BigDecimal fatPer100,
        BigDecimal carbsPer100,
        BigDecimal fiberPer100,
        BigDecimal sugarPer100,
        BigDecimal sodiumMgPer100,
        NutritionMath.Totals totals,
        FoodSource source,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static FoodEntryResponse from(FoodEntry entry) {
        NutritionMath.Per100 per100 = new NutritionMath.Per100(
                entry.getKcalPer100().doubleValue(),
                entry.getProteinPer100().doubleValue(),
                entry.getFatPer100().doubleValue(),
                entry.getCarbsPer100().doubleValue(),
                entry.getFiberPer100().doubleValue(),
                entry.getSugarPer100().doubleValue(),
                entry.getSodiumMgPer100().doubleValue());
        NutritionMath.Totals totals = NutritionMath.totals(per100, entry.getGrams().doubleValue());

        return new FoodEntryResponse(
                entry.getId(),
                entry.getEntryDate(),
                entry.getMealType(),
                entry.getName(),
                entry.getGrams(),
                entry.getKcalPer100(),
                entry.getProteinPer100(),
                entry.getFatPer100(),
                entry.getCarbsPer100(),
                entry.getFiberPer100(),
                entry.getSugarPer100(),
                entry.getSodiumMgPer100(),
                totals,
                entry.getSource(),
                entry.getCreatedAt(),
                entry.getUpdatedAt());
    }
}
