package com.kcalma.suggestions;

import com.kcalma.day.DayService;
import com.kcalma.food.MealType;
import com.kcalma.food.NutritionMath;
import com.kcalma.food.analysis.AnalyzedFoodItem;
import com.kcalma.food.dto.AnalyzedItemResponse;
import com.kcalma.suggestions.dto.SuggestionOptionResponse;
import com.kcalma.suggestions.dto.SuggestionResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Composes the day's remaining nutrient budget (via {@link DayService} — the client-sent numbers
 * are never trusted) with the {@link MealSuggester} port's suggestions, then recomputes every
 * option's totals from its items with {@link NutritionMath}.
 *
 * <p>Deliberately NOT {@code @Transactional}: {@link DayService#getDay} opens its own short
 * read-only transaction, and the call to {@link MealSuggester} is a slow external HTTP request
 * (up to ~45s) that must never hold a database connection open — this app's connection pool has
 * only 3 connections (see application.yml), so holding one for a whole Gemini round trip would be
 * a real bottleneck.
 */
@Service
public class SuggestionService {

    private final DayService dayService;
    private final MealSuggester mealSuggester;

    public SuggestionService(DayService dayService, MealSuggester mealSuggester) {
        this.dayService = dayService;
        this.mealSuggester = mealSuggester;
    }

    public Optional<SuggestionResponse> suggest(UUID userId, LocalDate date, MealType mealType, String preferences) {
        return dayService.getDay(userId, date).map(day -> {
            SuggestionContext context = new SuggestionContext(mealType, day.remaining(), preferences);
            MealSuggestionResult result = mealSuggester.suggest(context);
            List<SuggestionOptionResponse> options =
                    result.options().stream().map(SuggestionService::toOptionResponse).toList();
            return new SuggestionResponse(day.remaining(), options, result.note());
        });
    }

    private static SuggestionOptionResponse toOptionResponse(SuggestedMealOption option) {
        List<AnalyzedItemResponse> items =
                option.items().stream().map(AnalyzedItemResponse::from).toList();
        NutritionMath.Totals totals = option.items().stream()
                .map(SuggestionService::toTotals)
                .reduce(NutritionMath.Totals.ZERO, NutritionMath.Totals::plus);
        return new SuggestionOptionResponse(option.title(), option.description(), option.prepMinutes(), option.why(), items, totals);
    }

    private static NutritionMath.Totals toTotals(AnalyzedFoodItem item) {
        NutritionMath.Per100 per100 = new NutritionMath.Per100(
                item.kcalPer100(),
                item.proteinPer100(),
                item.fatPer100(),
                item.carbsPer100(),
                item.fiberPer100(),
                item.sugarPer100(),
                item.sodiumMgPer100());
        return NutritionMath.totals(per100, item.grams());
    }
}
