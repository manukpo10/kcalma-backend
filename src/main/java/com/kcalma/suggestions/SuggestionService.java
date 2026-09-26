package com.kcalma.suggestions;

import com.kcalma.day.DayService;
import com.kcalma.food.MealType;
import com.kcalma.food.NutritionMath;
import com.kcalma.food.dto.AnalyzedDishResponse;
import com.kcalma.food.reference.FoodReferenceMatcher;
import com.kcalma.food.reference.ResolvedDish;
import com.kcalma.suggestions.dto.SuggestionOptionResponse;
import com.kcalma.suggestions.dto.SuggestionResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Composes the day's remaining nutrient budget (via {@link DayService} — the client-sent numbers
 * are never trusted) with the {@link MealSuggester} port's suggestions, resolves every suggested
 * item against the personal library/USDA reference via {@link FoodReferenceMatcher} (same as the
 * photo/text analyze flows), then recomputes every option's totals from the RESOLVED values with
 * {@link NutritionMath} — so a suggestion's totals are just as real as a logged entry's.
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
    private final FoodReferenceMatcher referenceMatcher;

    public SuggestionService(DayService dayService, MealSuggester mealSuggester, FoodReferenceMatcher referenceMatcher) {
        this.dayService = dayService;
        this.mealSuggester = mealSuggester;
        this.referenceMatcher = referenceMatcher;
    }

    public Optional<SuggestionResponse> suggest(UUID userId, LocalDate date, MealType mealType, String preferences) {
        return dayService.getDay(userId, date).map(day -> {
            SuggestionContext context = new SuggestionContext(mealType, day.remaining(), preferences);
            MealSuggestionResult result = mealSuggester.suggest(context);
            List<SuggestionOptionResponse> options = result.options().stream()
                    .map(option -> toOptionResponse(userId, option))
                    .toList();
            return new SuggestionResponse(day.remaining(), options, result.note());
        });
    }

    private SuggestionOptionResponse toOptionResponse(UUID userId, SuggestedMealOption option) {
        List<ResolvedDish> resolvedDishes = referenceMatcher.resolveDishes(userId, option.dishes());
        List<AnalyzedDishResponse> dishes = resolvedDishes.stream().map(AnalyzedDishResponse::from).toList();
        NutritionMath.Totals totals = resolvedDishes.stream()
                .map(ResolvedDish::totals)
                .reduce(NutritionMath.Totals.ZERO, NutritionMath.Totals::plus);
        return new SuggestionOptionResponse(option.title(), option.description(), option.prepMinutes(), option.why(), dishes, totals);
    }
}
