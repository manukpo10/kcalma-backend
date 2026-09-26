package com.kcalma.food.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.kcalma.food.FoodSource;
import com.kcalma.food.NutritionMath;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ResolvedDish#aggregate} — dish totals/per100 are always derived from the
 * SUM of the resolved ingredients (never the dish's own raw Gemini estimate), and the dish-level
 * source follows {@link FoodSource#combine}.
 */
class ResolvedDishTest {

    @Test
    void aggregate_sumsIngredientTotalsAndDerivesDishPer100FromDishGrams() {
        // Milanesa (180 g): carne 120 g, pan rallado 30 g, huevo 15 g, aceite 15 g.
        ResolvedFoodItem carne = ingredient("carne", 120, new NutritionMath.Per100(250, 26, 15, 0, 0, 0, 60), FoodSource.USDA);
        ResolvedFoodItem panRallado =
                ingredient("pan rallado", 30, new NutritionMath.Per100(380, 13, 5, 70, 3, 4, 700), FoodSource.USDA);
        ResolvedFoodItem huevo = ingredient("huevo", 15, new NutritionMath.Per100(155, 13, 11, 1, 0, 1, 124), FoodSource.USDA);
        ResolvedFoodItem aceite = ingredient("aceite", 15, new NutritionMath.Per100(884, 0, 100, 0, 0, 0, 0), FoodSource.USDA);

        ResolvedDish dish = ResolvedDish.aggregate("Milanesa", 180, List.of(carne, panRallado, huevo, aceite));

        NutritionMath.Totals expectedTotals = NutritionMath.totals(carne.per100(), carne.grams())
                .plus(NutritionMath.totals(panRallado.per100(), panRallado.grams()))
                .plus(NutritionMath.totals(huevo.per100(), huevo.grams()))
                .plus(NutritionMath.totals(aceite.per100(), aceite.grams()));
        assertThat(dish.totals()).isEqualTo(expectedTotals);
        assertThat(dish.per100()).isEqualTo(NutritionMath.per100FromTotals(expectedTotals, 180));
        assertThat(dish.source()).isEqualTo(FoodSource.USDA);
        // Multi-ingredient dish: no single USDA row applies to the dish as a whole.
        assertThat(dish.fdcId()).isNull();
        assertThat(dish.matchedDescription()).isNull();
    }

    @Test
    void aggregate_singleIngredientDish_passesThroughFdcIdAndMatchedDescription() {
        ResolvedFoodItem banana = new ResolvedFoodItem(
                "banana", "banana, raw", 120, new NutritionMath.Per100(89, 1, 0, 23, 2, 12, 1), FoodSource.USDA, 1001L,
                "Banana, raw");

        ResolvedDish dish = ResolvedDish.aggregate("Banana", 120, List.of(banana));

        assertThat(dish.fdcId()).isEqualTo(1001L);
        assertThat(dish.matchedDescription()).isEqualTo("Banana, raw");
        assertThat(dish.source()).isEqualTo(FoodSource.USDA);
    }

    @Test
    void aggregate_mixedIngredientSources_dishSourceIsMixed() {
        ResolvedFoodItem matched =
                ingredient("carne", 120, new NutritionMath.Per100(250, 26, 15, 0, 0, 0, 60), FoodSource.USDA);
        ResolvedFoodItem estimated =
                ingredient("salsa casera", 40, new NutritionMath.Per100(90, 2, 3, 12, 1, 6, 300), FoodSource.ESTIMATED);

        ResolvedDish dish = ResolvedDish.aggregate("Milanesa con salsa", 160, List.of(matched, estimated));

        assertThat(dish.source()).isEqualTo(FoodSource.MIXED);
    }

    private static ResolvedFoodItem ingredient(String name, double grams, NutritionMath.Per100 per100, FoodSource source) {
        return new ResolvedFoodItem(name, name, grams, per100, source, null, null);
    }
}
