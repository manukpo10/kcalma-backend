package com.kcalma.suggestions;

import com.kcalma.food.analysis.AnalyzedDish;
import java.util.List;

/**
 * One meal option as returned by the model: a title, a one-line description, approximate prep
 * minutes, a one-line "why it fits you", and its DISHES in the SAME shape used by the food-analysis
 * flow ({@link AnalyzedDish}: name, grams, decomposed ingredients — a simple food is just one
 * dish with one ingredient). Deliberately has no "totals" field — {@code SuggestionService} always
 * derives totals from these dishes' resolved ingredients via {@code NutritionMath}, so there is
 * nothing here for the model to lie about.
 */
public record SuggestedMealOption(String title, String description, int prepMinutes, String why, List<AnalyzedDish> dishes) {}
