package com.kcalma.suggestions.dto;

/**
 * Body for POST /api/suggestions. {@code date}/{@code mealType}/{@code preferences} are validated
 * in {@code SuggestionController} (blank / format / max length) rather than via Bean Validation,
 * so a 400 response carries the same kind of friendly Spanish message as the rest of the API
 * (see {@code AnalyzeTextRequest}).
 */
public record SuggestionRequest(String date, String mealType, String preferences) {}
