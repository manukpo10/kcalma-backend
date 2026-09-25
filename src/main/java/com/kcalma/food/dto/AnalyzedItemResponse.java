package com.kcalma.food.dto;

import com.kcalma.food.analysis.AnalyzedFoodItem;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** One detected item from POST /api/food/analyze — not persisted yet, grams/values are editable client-side. */
public record AnalyzedItemResponse(
        String name,
        BigDecimal grams,
        BigDecimal kcalPer100,
        BigDecimal proteinPer100,
        BigDecimal fatPer100,
        BigDecimal carbsPer100,
        BigDecimal fiberPer100,
        BigDecimal sugarPer100,
        BigDecimal sodiumMgPer100) {

    public static AnalyzedItemResponse from(AnalyzedFoodItem item) {
        return new AnalyzedItemResponse(
                item.name(),
                round(item.grams()),
                round(item.kcalPer100()),
                round(item.proteinPer100()),
                round(item.fatPer100()),
                round(item.carbsPer100()),
                round(item.fiberPer100()),
                round(item.sugarPer100()),
                round(item.sodiumMgPer100()));
    }

    private static BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
