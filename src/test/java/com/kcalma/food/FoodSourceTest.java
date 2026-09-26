package com.kcalma.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link FoodSource#combine} — the dish-level source aggregation rule. */
class FoodSourceTest {

    @Test
    void combine_allIngredientsShareOneSource_returnsThatSource() {
        assertThat(FoodSource.combine(List.of(FoodSource.PERSONAL, FoodSource.PERSONAL))).isEqualTo(FoodSource.PERSONAL);
        assertThat(FoodSource.combine(List.of(FoodSource.USDA, FoodSource.USDA, FoodSource.USDA))).isEqualTo(FoodSource.USDA);
        assertThat(FoodSource.combine(List.of(FoodSource.ESTIMATED))).isEqualTo(FoodSource.ESTIMATED);
        assertThat(FoodSource.combine(List.of(FoodSource.MANUAL))).isEqualTo(FoodSource.MANUAL);
    }

    @Test
    void combine_usdaAndPersonalOnly_returnsUsda() {
        assertThat(FoodSource.combine(List.of(FoodSource.USDA, FoodSource.PERSONAL))).isEqualTo(FoodSource.USDA);
        assertThat(FoodSource.combine(List.of(FoodSource.PERSONAL, FoodSource.USDA, FoodSource.PERSONAL)))
                .isEqualTo(FoodSource.USDA);
    }

    @Test
    void combine_estimatedMixedWithAnythingElse_returnsMixed() {
        assertThat(FoodSource.combine(List.of(FoodSource.ESTIMATED, FoodSource.USDA))).isEqualTo(FoodSource.MIXED);
        assertThat(FoodSource.combine(List.of(FoodSource.PERSONAL, FoodSource.ESTIMATED))).isEqualTo(FoodSource.MIXED);
        assertThat(FoodSource.combine(List.of(FoodSource.USDA, FoodSource.PERSONAL, FoodSource.ESTIMATED)))
                .isEqualTo(FoodSource.MIXED);
    }

    @Test
    void combine_manualMixedWithAnythingElse_returnsMixed() {
        assertThat(FoodSource.combine(List.of(FoodSource.MANUAL, FoodSource.USDA))).isEqualTo(FoodSource.MIXED);
    }

    @Test
    void combine_emptyCollection_throws() {
        assertThatThrownBy(() -> FoodSource.combine(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
