package com.kcalma.food.dto;

import com.kcalma.food.analysis.FoodAnalysisResult;
import com.kcalma.food.reference.ResolvedFoodItem;
import java.util.List;

/**
 * Body for POST /api/food/analyze and POST /api/food/analyze-text. {@code note} is set (items
 * may be empty) when the photo/description isn't food.
 */
public record FoodAnalysisResponse(List<AnalyzedItemResponse> items, String note) {

    /** Raw analyzer output, with no reference-matching applied (source defaults to ESTIMATED). */
    public static FoodAnalysisResponse from(FoodAnalysisResult result) {
        return new FoodAnalysisResponse(
                result.items().stream().map(AnalyzedItemResponse::from).toList(), result.note());
    }

    /** The normal path: items already resolved against the personal library/USDA reference. */
    public static FoodAnalysisResponse fromResolved(List<ResolvedFoodItem> items, String note) {
        return new FoodAnalysisResponse(items.stream().map(AnalyzedItemResponse::from).toList(), note);
    }
}
