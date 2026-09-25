package com.kcalma.food;

import static org.assertj.core.api.Assertions.assertThat;

import com.kcalma.food.NutritionMath.Per100;
import com.kcalma.food.NutritionMath.Totals;
import org.junit.jupiter.api.Test;

/** Pure unit tests for {@link NutritionMath} — the single place totals = per100 * grams / 100 is computed. */
class NutritionMathTest {

    @Test
    void totals_scalesPer100ValuesByGramsOverOneHundred() {
        Per100 per100 = new Per100(200, 20, 10, 25, 4, 8, 300);

        Totals totals = NutritionMath.totals(per100, 150);

        assertThat(totals.kcal()).isEqualTo(300);
        assertThat(totals.protein()).isEqualTo(30);
        assertThat(totals.fat()).isEqualTo(15);
        assertThat(totals.carbs()).isEqualTo(38); // 37.5 -> rounds to 38
        assertThat(totals.fiber()).isEqualTo(6);
        assertThat(totals.sugar()).isEqualTo(12);
        assertThat(totals.sodiumMg()).isEqualTo(450);
    }

    @Test
    void totals_hundredGrams_equalsPer100Values() {
        Per100 per100 = new Per100(157, 12, 8, 20, 3, 5, 210);

        Totals totals = NutritionMath.totals(per100, 100);

        assertThat(totals).isEqualTo(new Totals(157, 12, 8, 20, 3, 5, 210));
    }

    @Test
    void totals_zeroGrams_isZero() {
        Per100 per100 = new Per100(500, 40, 30, 10, 5, 2, 900);

        Totals totals = NutritionMath.totals(per100, 0);

        assertThat(totals).isEqualTo(Totals.ZERO);
    }

    @Test
    void plus_sumsEachField() {
        Totals a = new Totals(100, 10, 5, 15, 2, 3, 200);
        Totals b = new Totals(50, 5, 2, 8, 1, 1, 100);

        assertThat(a.plus(b)).isEqualTo(new Totals(150, 15, 7, 23, 3, 4, 300));
    }

    @Test
    void minus_subtractsEachField_andCanGoNegativeWhenExceeded() {
        Totals target = new Totals(2000, 100, 70, 250, 30, 50, 2000);
        Totals consumed = new Totals(2200, 90, 80, 260, 20, 60, 1800);

        Totals remaining = target.minus(consumed);

        assertThat(remaining).isEqualTo(new Totals(-200, 10, -10, -10, 10, -10, 200));
    }
}
