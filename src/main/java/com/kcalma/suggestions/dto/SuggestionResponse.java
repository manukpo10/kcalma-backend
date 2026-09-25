package com.kcalma.suggestions.dto;

import com.kcalma.food.NutritionMath;
import java.util.List;

/** Body for POST /api/suggestions: what's left of the day, 3 (or fewer) options, and an optional note. */
public record SuggestionResponse(NutritionMath.Totals remaining, List<SuggestionOptionResponse> options, String note) {}
