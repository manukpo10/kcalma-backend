package com.kcalma.food.reference;

import com.kcalma.food.FoodSource;
import com.kcalma.food.NutritionMath;
import java.util.List;

/**
 * One {@code AnalyzedDish} after every one of its ingredients has been resolved via {@link
 * FoodReferenceMatcher}. {@code totals}/{@code per100} are ALWAYS derived from the resolved
 * ingredients (never from the dish's own Gemini estimate) via {@link #aggregate}: totals = the sum
 * of each ingredient's own totals, per100 = totals / grams * 100 (see {@link
 * NutritionMath#per100FromTotals}). {@code source} follows {@link FoodSource#combine}.
 * {@code fdcId}/{@code matchedDescription} only carry through for a single-ingredient dish (a
 * simple food) — for a real multi-ingredient dish neither one is a single well-defined USDA row.
 */
public record ResolvedDish(
        String name,
        double grams,
        List<ResolvedFoodItem> ingredients,
        NutritionMath.Per100 per100,
        NutritionMath.Totals totals,
        FoodSource source,
        Long fdcId,
        String matchedDescription) {

    public static ResolvedDish aggregate(String name, double grams, List<ResolvedFoodItem> ingredients) {
        NutritionMath.Totals totals = ingredients.stream()
                .map(ingredient -> NutritionMath.totals(ingredient.per100(), ingredient.grams()))
                .reduce(NutritionMath.Totals.ZERO, NutritionMath.Totals::plus);
        NutritionMath.Per100 per100 = NutritionMath.per100FromTotals(totals, grams);
        FoodSource source = FoodSource.combine(ingredients.stream().map(ResolvedFoodItem::source).toList());

        boolean singleIngredient = ingredients.size() == 1;
        Long fdcId = singleIngredient ? ingredients.get(0).fdcId() : null;
        String matchedDescription = singleIngredient ? ingredients.get(0).matchedDescription() : null;

        return new ResolvedDish(name, grams, ingredients, per100, totals, source, fdcId, matchedDescription);
    }
}
