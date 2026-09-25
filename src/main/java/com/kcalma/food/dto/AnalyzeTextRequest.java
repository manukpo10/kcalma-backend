package com.kcalma.food.dto;

/**
 * Body for POST /api/food/analyze-text. {@code description} is validated in {@code
 * FoodController} (blank / max length) rather than via Bean Validation, so the 400 response
 * carries the same kind of friendly Spanish message as the photo endpoint's own checks.
 */
public record AnalyzeTextRequest(String description) {}
