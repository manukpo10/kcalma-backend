package com.kcalma.favorites.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.kcalma.favorites.FavoriteDish;
import com.kcalma.food.MealType;
import com.kcalma.food.NutritionMath;
import com.kcalma.food.dto.AnalyzedDishResponse;
import com.kcalma.food.dto.AnalyzedItemResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Body for one entry of GET /api/favorites, and for the created favorite from POST /api/favorites:
 * {@code {id, mealType, createdAt, ...Dish}}. {@code dish} is {@code @JsonUnwrapped} so its fields
 * (name, grams, per-100g values, source, fdcId, ingredients...) land flat alongside {@code id}/
 * {@code mealType}/{@code createdAt} — the exact same Dish shape {@code AnalyzedDishResponse}
 * already gives the review step (POST /api/food/analyze): the sprint contract is to reuse that
 * DTO, not invent a new one.
 */
public record FavoriteResponse(UUID id, MealType mealType, OffsetDateTime createdAt, @JsonUnwrapped AnalyzedDishResponse dish) {

    public static FavoriteResponse from(FavoriteDish favorite) {
        return new FavoriteResponse(favorite.getId(), favorite.getMealType(), favorite.getCreatedAt(), toDish(favorite));
    }

    /**
     * Builds the Dish view directly from a persisted, already-rounded {@link FavoriteDish} —
     * deliberately not an {@code AnalyzedDishResponse.from(FavoriteDish)} overload: that would make
     * the foundational {@code com.kcalma.food.dto} package depend on this feature package, backwards
     * from every other cross-feature dependency in this codebase (features depend on {@code food},
     * never the other way around).
     */
    private static AnalyzedDishResponse toDish(FavoriteDish favorite) {
        NutritionMath.Per100 per100 = new NutritionMath.Per100(
                favorite.getKcalPer100().doubleValue(),
                favorite.getProteinPer100().doubleValue(),
                favorite.getFatPer100().doubleValue(),
                favorite.getCarbsPer100().doubleValue(),
                favorite.getFiberPer100().doubleValue(),
                favorite.getSugarPer100().doubleValue(),
                favorite.getSodiumMgPer100().doubleValue());
        NutritionMath.Totals totals = NutritionMath.totals(per100, favorite.getGrams().doubleValue());
        List<AnalyzedItemResponse> ingredients = favorite.getIngredients() == null
                ? null
                : favorite.getIngredients().stream().map(AnalyzedItemResponse::from).toList();
        return new AnalyzedDishResponse(
                favorite.getName(),
                favorite.getGrams(),
                favorite.getKcalPer100(),
                favorite.getProteinPer100(),
                favorite.getFatPer100(),
                favorite.getCarbsPer100(),
                favorite.getFiberPer100(),
                favorite.getSugarPer100(),
                favorite.getSodiumMgPer100(),
                totals,
                favorite.getSource(),
                favorite.getFdcId(),
                null,
                ingredients);
    }
}
