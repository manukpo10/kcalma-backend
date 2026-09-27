package com.kcalma.food.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.kcalma.food.FoodEntry;
import com.kcalma.food.MealType;
import java.time.LocalDate;

/**
 * Body for one entry of GET /api/food/recent: {@code {...Dish (grams = last used), lastMealType,
 * timesLogged, lastLoggedOn}} — see {@code com.kcalma.food.RecentDishService} for how a user's
 * distinct-by-normalized-name, last-60-days dishes are ranked. {@code dish} is {@code
 * @JsonUnwrapped} for the same reason as {@code com.kcalma.favorites.dto.FavoriteResponse}: the
 * exact Dish shape the review step already uses, reused rather than re-invented.
 */
public record RecentDishResponse(MealType lastMealType, int timesLogged, LocalDate lastLoggedOn, @JsonUnwrapped AnalyzedDishResponse dish) {

    /** {@code latest} is the most-recently-logged entry among every entry sharing this dish's normalized name. */
    public static RecentDishResponse from(FoodEntry latest, int timesLogged) {
        return new RecentDishResponse(latest.getMealType(), timesLogged, latest.getEntryDate(), AnalyzedDishResponse.from(latest));
    }
}
