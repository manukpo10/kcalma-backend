package com.kcalma.food.dto;

import com.kcalma.food.analysis.FoodAnalysisResult;
import java.util.List;

/** Body for POST /api/food/analyze. {@code note} is set (items may be empty) when the photo isn't food. */
public record FoodAnalysisResponse(List<AnalyzedItemResponse> items, String note) {

    public static FoodAnalysisResponse from(FoodAnalysisResult result) {
        return new FoodAnalysisResponse(
                result.items().stream().map(AnalyzedItemResponse::from).toList(), result.note());
    }
}
