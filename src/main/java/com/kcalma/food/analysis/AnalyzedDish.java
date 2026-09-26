package com.kcalma.food.analysis;

import java.util.List;

/**
 * One prepared dish detected in a photo/text description (or suggested for a meal): a Spanish
 * display name, its total portion size as eaten, and the {@link AnalyzedFoodItem} ingredients it
 * decomposes into (2-8 for a prepared dish, INCLUDING likely hidden ones such as cooking oil,
 * butter, sugar, or breading; exactly one, equal to the dish itself, for a simple food like a piece
 * of fruit or a yogurt). {@code grams} is the dish's own estimate of its as-eaten weight — the
 * ingredients' grams approximate it but are not forced to sum to it exactly, the same way a real
 * recipe's raw ingredients don't sum to exactly the cooked plate's weight.
 */
public record AnalyzedDish(String name, double grams, List<AnalyzedFoodItem> ingredients) {}
