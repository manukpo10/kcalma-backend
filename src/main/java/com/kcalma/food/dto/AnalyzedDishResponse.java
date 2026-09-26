package com.kcalma.food.dto;

import com.kcalma.food.FoodSource;
import com.kcalma.food.NutritionMath;
import com.kcalma.food.reference.ResolvedDish;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * One detected DISH from POST /api/food/analyze, POST /api/food/analyze-text, or POST
 * /api/suggestions — not persisted yet, grams are editable client-side (both the dish's own
 * stepper and each ingredient's). {@code kcalPer100}..{@code sodiumMgPer100} and {@code totals} are
 * always derived from the resolved {@code ingredients} (see {@link ResolvedDish#aggregate}), never
 * a value of their own. {@code source} follows {@link FoodSource#combine}; {@code fdcId}/{@code
 * matchedDescription} are only set when the dish is a single ingredient (a simple food).
 */
public record AnalyzedDishResponse(
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
        Long fdcId,
        String matchedDescription,
        List<AnalyzedItemResponse> ingredients) {

    public static AnalyzedDishResponse from(ResolvedDish dish) {
        NutritionMath.Per100 per100 = dish.per100();
        List<AnalyzedItemResponse> ingredients =
                dish.ingredients().stream().map(AnalyzedItemResponse::from).toList();
        return new AnalyzedDishResponse(
                dish.name(),
                round(dish.grams()),
                round(per100.kcal()),
                round(per100.protein()),
                round(per100.fat()),
                round(per100.carbs()),
                round(per100.fiber()),
                round(per100.sugar()),
                round(per100.sodiumMg()),
                dish.totals(),
                dish.source(),
                dish.fdcId(),
                dish.matchedDescription(),
                ingredients);
    }

    private static BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
