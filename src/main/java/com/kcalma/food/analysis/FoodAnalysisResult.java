package com.kcalma.food.analysis;

import java.util.List;

/**
 * Outcome of analyzing a plate photo. {@code note} is set (items may be empty) when the model
 * flags the image as not food, or is not confident enough to identify anything in it.
 */
public record FoodAnalysisResult(List<AnalyzedFoodItem> items, String note) {}
