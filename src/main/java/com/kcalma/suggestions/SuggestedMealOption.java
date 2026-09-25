package com.kcalma.suggestions;

import com.kcalma.food.analysis.AnalyzedFoodItem;
import java.util.List;

/**
 * One meal option as returned by the model: a title, a one-line description, approximate prep
 * minutes, a one-line "why it fits you", and its items in the SAME shape used by the food-analysis
 * flow ({@link AnalyzedFoodItem}: name, grams, per-100g nutrients). Deliberately has no "totals"
 * field — {@code SuggestionService} always derives totals from these items via {@code
 * NutritionMath}, so there is nothing here for the model to lie about.
 */
public record SuggestedMealOption(String title, String description, int prepMinutes, String why, List<AnalyzedFoodItem> items) {}
