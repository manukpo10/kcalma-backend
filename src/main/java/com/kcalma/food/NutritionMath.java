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

    /**
     * The inverse of {@link #totals}: derives a per-100g breakdown from a known total and portion
     * size — per100 = totals / grams * 100. Used to derive a DISH's own per-100g values from the
     * sum of its resolved ingredients' totals (see {@code com.kcalma.food.reference.ResolvedDish}),
     * so a dish's macros are always backed by real ingredient-level data, never guessed directly.
     * {@code grams <= 0} returns all-zero per100 rather than dividing by zero (defensive — grams is
     * validated positive everywhere this is reachable from user input).
     */
    public static Per100 per100FromTotals(Totals totals, double grams) {
        if (grams <= 0) {
            return new Per100(0, 0, 0, 0, 0, 0, 0);
        }
        double factor = 100.0 / grams;
        return new Per100(
                totals.kcal() * factor,
                totals.protein() * factor,
                totals.fat() * factor,
                totals.carbs() * factor,
                totals.fiber() * factor,
                totals.sugar() * factor,
                totals.sodiumMg() * factor);
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }
}
