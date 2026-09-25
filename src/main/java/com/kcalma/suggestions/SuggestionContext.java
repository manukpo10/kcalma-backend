package com.kcalma.suggestions;

import com.kcalma.food.MealType;
import com.kcalma.food.NutritionMath;

/**
 * Input to {@link MealSuggester}: what's left of the day's budget (kcal/protein/fat/carbs/fiber,
 * plus sugar and sodium "room" — i.e. {@code targets.minus(consumed)}, computed server-side by
 * {@code DayService}, never trusted from the client), the meal type to suggest for, and the
 * user's optional free-text preferences.
 */
public record SuggestionContext(MealType mealType, NutritionMath.Totals remaining, String preferences) {}
