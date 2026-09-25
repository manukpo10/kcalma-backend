package com.kcalma.suggestions;

import com.kcalma.food.analysis.FoodAnalysisException;

/** Port: suggests meal options that fit what's left of the user's day, for a given meal type. */
public interface MealSuggester {

    /**
     * @param context the remaining nutrient budget, meal type, and optional user preferences
     * @throws FoodAnalysisException if the provider is unavailable, rate-limited, or returns
     *     something that cannot be interpreted
     */
    MealSuggestionResult suggest(SuggestionContext context);
}
