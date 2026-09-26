package com.kcalma.food.dto;

import com.kcalma.food.reference.ResolvedDish;
import java.util.List;

/**
 * Body for POST /api/food/analyze and POST /api/food/analyze-text: the detected DISHES, each
 * already resolved (per-ingredient) against the personal library/USDA reference — see {@link
 * AnalyzedDishResponse}. {@code note} is set (dishes may be empty) when the photo/description
 * isn't food.
 */
public record FoodAnalysisResponse(List<AnalyzedDishResponse> dishes, String note) {

    public static FoodAnalysisResponse fromResolved(List<ResolvedDish> dishes, String note) {
        return new FoodAnalysisResponse(dishes.stream().map(AnalyzedDishResponse::from).toList(), note);
    }
}
