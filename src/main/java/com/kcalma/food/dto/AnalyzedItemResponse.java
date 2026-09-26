package com.kcalma.food.dto;

import com.kcalma.food.FoodSource;
import com.kcalma.food.analysis.AnalyzedFoodItem;
import com.kcalma.food.reference.ResolvedFoodItem;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One detected item from POST /api/food/analyze, POST /api/food/analyze-text, or POST
 * /api/suggestions — not persisted yet, grams are editable client-side. {@code source}/{@code
 * fdcId}/{@code matchedDescription} come from {@code com.kcalma.food.reference.FoodReferenceMatcher}
 * and tell the UI where the nutrient values below came from (see {@link #from(ResolvedFoodItem)}).
 */
public record AnalyzedItemResponse(
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
        Long fdcId,
        String matchedDescription) {

    /** Raw, unresolved item — {@code source} defaults to {@code ESTIMATED} (no match attempted). */
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
                round(item.sodiumMgPer100()),
                FoodSource.ESTIMATED,
                null,
                null);
    }

    /** Item resolved against the personal library/USDA reference — the normal, matched path. */
    public static AnalyzedItemResponse from(ResolvedFoodItem item) {
        return new AnalyzedItemResponse(
                item.name(),
                round(item.grams()),
                round(item.per100().kcal()),
                round(item.per100().protein()),
                round(item.per100().fat()),
                round(item.per100().carbs()),
                round(item.per100().fiber()),
                round(item.per100().sugar()),
                round(item.per100().sodiumMg()),
                item.source(),
                item.fdcId(),
                item.matchedDescription());
    }

    private static BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
