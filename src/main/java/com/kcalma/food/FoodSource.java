package com.kcalma.food;

import java.util.Collection;
import java.util.EnumSet;

/**
 * Where a food entry's nutrient values came from, in {@code com.kcalma.food.reference.FoodReferenceMatcher}'s
 * priority order: {@code PERSONAL} (matched the caller's own {@code app.user_food} library),
 * {@code USDA} (matched {@code app.food_reference}, USDA FoodData Central), {@code ESTIMATED}
 * (no confident match — Gemini's own per-100g estimate, used as-is), or {@code MANUAL} (the values
 * were typed by hand, e.g. via the manual-add flow — never resolved against either table).
 *
 * <p>{@code MIXED} is dish-level only (see {@link #combine}): a single ingredient is always one of
 * the four sources above, never {@code MIXED} itself — it only describes a dish whose resolved
 * ingredients don't all agree on where their values came from.
 */
public enum FoodSource {
    PERSONAL,
    USDA,
    ESTIMATED,
    MANUAL,
    MIXED;

    /**
     * Dish-level source rule (see {@code com.kcalma.food.reference.ResolvedDish}): every ingredient
     * shares one source → that source; a confident-but-different pair of {@code USDA}/{@code
     * PERSONAL} → {@code USDA} (both are "real" matched data, just from different tables); any other
     * disagreement (an {@code ESTIMATED} ingredient alongside a matched one, or a mix involving
     * {@code MANUAL}) → {@code MIXED}, since the dish is no longer uniformly trustworthy.
     *
     * @throws IllegalArgumentException if {@code sources} is empty — a dish always has at least one
     *     ingredient
     */
    public static FoodSource combine(Collection<FoodSource> sources) {
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("Cannot combine an empty set of sources — a dish needs at least one ingredient.");
        }
        EnumSet<FoodSource> distinct = EnumSet.copyOf(sources);
        if (distinct.size() == 1) {
            return distinct.iterator().next();
        }
        if (distinct.equals(EnumSet.of(USDA, PERSONAL))) {
            return USDA;
        }
        return MIXED;
    }
}
