package com.kcalma.food;

/**
 * Pure domain math: the single place nutrient totals are derived from per-100g values.
 * No Spring, no I/O — safe to unit test exhaustively. Every feature that needs a food
 * item's totals (analyze preview, saved entries, the day summary) must go through this.
 */
public final class NutritionMath {

    private NutritionMath() {}

    /** Nutrient values per 100 g of a food item. */
    public record Per100(
            double kcal, double protein, double fat, double carbs, double fiber, double sugar, double sodiumMg) {}

    /** Nutrient amounts for an actual portion (or a sum of portions), rounded to whole units for display. */
    public record Totals(int kcal, int protein, int fat, int carbs, int fiber, int sugar, int sodiumMg) {

        public static final Totals ZERO = new Totals(0, 0, 0, 0, 0, 0, 0);

        public Totals plus(Totals other) {
            return new Totals(
                    kcal + other.kcal,
                    protein + other.protein,
                    fat + other.fat,
                    carbs + other.carbs,
                    fiber + other.fiber,
                    sugar + other.sugar,
                    sodiumMg + other.sodiumMg);
        }

        public Totals minus(Totals other) {
            return new Totals(
                    kcal - other.kcal,
                    protein - other.protein,
                    fat - other.fat,
                    carbs - other.carbs,
                    fiber - other.fiber,
                    sugar - other.sugar,
                    sodiumMg - other.sodiumMg);
        }
    }

    /** totals = per100 * grams / 100, rounded to the nearest whole unit. */
    public static Totals totals(Per100 per100, double grams) {
        double factor = grams / 100.0;
        return new Totals(
                round(per100.kcal() * factor),
                round(per100.protein() * factor),
                round(per100.fat() * factor),
                round(per100.carbs() * factor),
                round(per100.fiber() * factor),
                round(per100.sugar() * factor),
                round(per100.sodiumMg() * factor));
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }
}
