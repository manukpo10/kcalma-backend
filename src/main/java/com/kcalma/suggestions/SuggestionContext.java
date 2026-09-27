package com.kcalma.suggestions;

import com.kcalma.food.MealType;
import com.kcalma.food.NutritionMath;
import com.kcalma.profile.DietStyle;
import com.kcalma.profile.DietaryRestriction;
import java.util.List;

/**
 * Input to {@link MealSuggester}: what's left of the day's budget (kcal/protein/fat/carbs/fiber,
 * plus sugar and sodium "room" — i.e. {@code targets.minus(consumed)}, computed server-side by
 * {@code DayService}, never trusted from the client), the meal type to suggest for, the user's
 * optional free-text preferences, and the profile's {@code dietStyle}/{@code dietaryRestrictions}
 * — passed as hard constraints, never as something the model may relax.
 */
public record SuggestionContext(
        MealType mealType,
        NutritionMath.Totals remaining,
        String preferences,
        DietStyle dietStyle,
        List<DietaryRestriction> dietaryRestrictions) {}
