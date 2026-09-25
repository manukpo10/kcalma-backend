package com.kcalma.suggestions;

import java.util.List;

/**
 * Outcome of asking for meal suggestions. {@code note} is set (options may still be present) when
 * e.g. the day already met or exceeded its calorie target and the model flags that in a short
 * message.
 */
public record MealSuggestionResult(List<SuggestedMealOption> options, String note) {}
