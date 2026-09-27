package com.kcalma.food.dto;

import com.kcalma.food.FoodEntry;
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

    /**
     * A saved {@link FoodEntry} re-shown in Dish form (recent dishes — see {@code
     * com.kcalma.food.RecentDishService}). Unlike {@link #from(ResolvedDish)}, the per-100g/{@code
     * totals} fields are the entry's own already-rounded, already-persisted values, not a fresh
     * ingredient aggregation — {@code matchedDescription} is never persisted on a {@link
     * com.kcalma.food.FoodEntry} (preview-only elsewhere), so it's always {@code null} here.
     * {@code ingredients} stays {@code null} for any entry logged before V9 shipped (see {@link
     * com.kcalma.food.FoodEntry#getIngredients()}), same "no breakdown" contract all the way through.
     */
    public static AnalyzedDishResponse from(FoodEntry entry) {
        NutritionMath.Per100 per100 = new NutritionMath.Per100(
                entry.getKcalPer100().doubleValue(),
                entry.getProteinPer100().doubleValue(),
                entry.getFatPer100().doubleValue(),
                entry.getCarbsPer100().doubleValue(),
                entry.getFiberPer100().doubleValue(),
                entry.getSugarPer100().doubleValue(),
                entry.getSodiumMgPer100().doubleValue());
        NutritionMath.Totals totals = NutritionMath.totals(per100, entry.getGrams().doubleValue());
        List<AnalyzedItemResponse> ingredients =
                entry.getIngredients() == null ? null : entry.getIngredients().stream().map(AnalyzedItemResponse::from).toList();
        return new AnalyzedDishResponse(
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
                entry.getFdcId(),
                null,
                ingredients);
    }

    private static BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
