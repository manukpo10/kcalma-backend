package com.kcalma.suggestions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kcalma.day.DayService;
import com.kcalma.day.dto.DayResponse;
import com.kcalma.food.MealType;
import com.kcalma.food.NutritionMath;
import com.kcalma.food.analysis.AnalyzedFoodItem;
import com.kcalma.profile.dto.NutritionTargetsResponse;
import com.kcalma.suggestions.dto.SuggestionOptionResponse;
import com.kcalma.suggestions.dto.SuggestionResponse;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link SuggestionService}: reuses {@link DayService} for the remaining budget
 * (never trusts client-sent numbers), forwards it plus meal type/preferences to the {@link
 * MealSuggester} port, and computes each option's totals from its items via {@link
 * NutritionMath} — ignoring anything the model might have claimed as a total, since the port's
 * own data model ({@link SuggestedMealOption}) has no such field to begin with.
 */
@ExtendWith(MockitoExtension.class)
class SuggestionServiceTest {

    @Mock
    private DayService dayService;

    @Mock
    private MealSuggester mealSuggester;

    private final UUID userId = UUID.randomUUID();
    private final LocalDate date = LocalDate.of(2026, 9, 25);

    @Test
    void suggest_whenNoProfileForUser_returnsEmptyAndNeverCallsThePort() {
        when(dayService.getDay(userId, date)).thenReturn(Optional.empty());

        Optional<SuggestionResponse> result = newService().suggest(userId, date, MealType.CENA, "sin carne");

        assertThat(result).isEmpty();
        verifyNoInteractions(mealSuggester);
    }

    @Test
    void suggest_passesTheDaysRemainingBudgetMealTypeAndPreferencesToThePort() {
        NutritionMath.Totals remaining = new NutritionMath.Totals(600, 40, 20, 70, 8, 15, 500);
        when(dayService.getDay(userId, date)).thenReturn(Optional.of(sampleDay(remaining)));
        when(mealSuggester.suggest(any())).thenReturn(new MealSuggestionResult(List.of(), null));

        newService().suggest(userId, date, MealType.CENA, "tengo pollo y arroz");

        ArgumentCaptor<SuggestionContext> captor = ArgumentCaptor.forClass(SuggestionContext.class);
        verify(mealSuggester).suggest(captor.capture());
        assertThat(captor.getValue().mealType()).isEqualTo(MealType.CENA);
        assertThat(captor.getValue().remaining()).isEqualTo(remaining);
        assertThat(captor.getValue().preferences()).isEqualTo("tengo pollo y arroz");
    }

    @Test
    void suggest_computesEachOptionsTotalsFromItsItemsViaNutritionMath() {
        NutritionMath.Totals remaining = new NutritionMath.Totals(600, 40, 20, 70, 8, 15, 500);
        when(dayService.getDay(userId, date)).thenReturn(Optional.of(sampleDay(remaining)));

        AnalyzedFoodItem itemA = new AnalyzedFoodItem("milanesa de carne", 150, 200, 25, 8, 6, 1, 0.5, 420);
        AnalyzedFoodItem itemB = new AnalyzedFoodItem("ensalada mixta", 120, 40, 1.5, 2, 4, 2, 1, 50);
        SuggestedMealOption option =
                new SuggestedMealOption("Milanesa con ensalada", "Milanesa al horno", 25, "Alta en proteína", List.of(itemA, itemB));
        when(mealSuggester.suggest(any())).thenReturn(new MealSuggestionResult(List.of(option), null));

        Optional<SuggestionResponse> result = newService().suggest(userId, date, MealType.CENA, null);

        NutritionMath.Totals expectedTotals = NutritionMath.totals(
                        new NutritionMath.Per100(200, 25, 8, 6, 1, 0.5, 420), 150)
                .plus(NutritionMath.totals(new NutritionMath.Per100(40, 1.5, 2, 4, 2, 1, 50), 120));

        assertThat(result).isPresent();
        SuggestionOptionResponse optionResponse = result.get().options().get(0);
        assertThat(optionResponse.totals()).isEqualTo(expectedTotals);
        assertThat(optionResponse.title()).isEqualTo("Milanesa con ensalada");
        assertThat(optionResponse.items()).hasSize(2);
    }

    @Test
    void suggest_returnsTheDaysRemainingBudgetAndThePortsNote() {
        NutritionMath.Totals remaining = new NutritionMath.Totals(-50, 10, 5, 5, 2, 3, 100);
        when(dayService.getDay(userId, date)).thenReturn(Optional.of(sampleDay(remaining)));
        when(mealSuggester.suggest(any()))
                .thenReturn(new MealSuggestionResult(List.of(), "Ya superaste tu objetivo de calorías de hoy."));

        Optional<SuggestionResponse> result = newService().suggest(userId, date, MealType.CENA, null);

        assertThat(result).isPresent();
        assertThat(result.get().remaining()).isEqualTo(remaining);
        assertThat(result.get().note()).isEqualTo("Ya superaste tu objetivo de calorías de hoy.");
        assertThat(result.get().options()).isEmpty();
    }

    private SuggestionService newService() {
        return new SuggestionService(dayService, mealSuggester);
    }

    private static DayResponse sampleDay(NutritionMath.Totals remaining) {
        NutritionTargetsResponse targets = new NutritionTargetsResponse(2000, false, 120, 65, 220, 28, 50, 2000, 2500);
        NutritionMath.Totals consumed = NutritionMath.Totals.ZERO;
        Map<MealType, List<com.kcalma.food.dto.FoodEntryResponse>> meals = new EnumMap<>(MealType.class);
        for (MealType mealType : MealType.values()) {
            meals.put(mealType, List.of());
        }
        return new DayResponse(
                LocalDate.of(2026, 9, 25),
                targets,
                consumed,
                remaining,
                new DayResponse.Exceeded(remaining.kcal() < 0, false, false),
                meals);
    }
}
