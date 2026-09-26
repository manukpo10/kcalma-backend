package com.kcalma.food.analysis;

import java.util.List;

/**
 * Outcome of analyzing a plate photo/description: the DISHES identified, each already decomposed
 * into ingredients (see {@link AnalyzedDish}). {@code note} is set (dishes may be empty) when the
 * model flags the image/text as not food, or is not confident enough to identify anything in it.
 */
public record FoodAnalysisResult(List<AnalyzedDish> dishes, String note) {}
