package com.kcalma.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.kcalma.food.dto.FoodEntryIngredientRequest;
import com.kcalma.food.dto.FoodEntryRequest;
import com.kcalma.food.dto.FoodEntryResponse;
import com.kcalma.food.dto.UpdateFoodEntryRequest;
import com.kcalma.food.reference.UserFood;
import com.kcalma.food.reference.UserFoodRepository;
import com.kcalma.food.reference.UserFoodSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link FoodEntryService}: the delete-a-whole-meal path (no real-DB/{@code
 * @DataJpaTest} test infrastructure in this project, so behavior is verified the same way as the
 * rest of the service layer — asserting exactly what was forwarded to a mocked repository); the
 * save -> per-INGREDIENT {@code app.user_food} upsert (a dish with no breakdown defaults to a
 * single ingredient equal to itself); and the PATCH ingredients-edit/plain-grams-scaling split.
 */
@ExtendWith(MockitoExtension.class)
class FoodEntryServiceTest {

    @Mock
    private FoodEntryRepository repository;

    @Mock
    private UserFoodRepository userFoodRepository;

    private final UUID userId = UUID.randomUUID();

    @Test
    void deleteAllByMeal_delegatesToASingleRepositoryDeleteScopedToUserDateAndMeal() {
        FoodEntryService service = newService();
        UUID otherUserId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 25);

        service.deleteAllByMeal(userId, date, MealType.DESAYUNO);

        verify(repository).deleteByUserIdAndEntryDateAndMealType(userId, date, MealType.DESAYUNO);
        verifyNoMoreInteractions(repository);
        // Sanity check on the scope itself: a different user/date/meal is a different call.
        verify(repository, never()).deleteByUserIdAndEntryDateAndMealType(otherUserId, date, MealType.DESAYUNO);
        verify(repository, never()).deleteByUserIdAndEntryDateAndMealType(userId, date, MealType.ALMUERZO);
        verify(repository, never())
                .deleteByUserIdAndEntryDateAndMealType(userId, date.plusDays(1), MealType.DESAYUNO);
    }

    @Test
    void saveAll_noIngredientsInRequest_defaultsToASingleIngredientEqualToTheDishAndUpsertsIt() {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(userFoodRepository.findByUserIdAndNormalizedName(userId, "milanesa de carne")).thenReturn(Optional.empty());

        List<FoodEntryResponse> saved =
                newService().saveAll(userId, List.of(request("Milanesa de carne", FoodSource.USDA, 111L, null)));

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).ingredients()).hasSize(1);
        assertThat(saved.get(0).ingredients().get(0).name()).isEqualTo("Milanesa de carne");
        assertThat(saved.get(0).ingredients().get(0).fdcId()).isEqualTo(111L);

        ArgumentCaptor<UserFood> captor = ArgumentCaptor.forClass(UserFood.class);
        verify(userFoodRepository).save(captor.capture());
        UserFood savedFood = captor.getValue();
        assertThat(savedFood.getUserId()).isEqualTo(userId);
        assertThat(savedFood.getNormalizedName()).isEqualTo("milanesa de carne");
        assertThat(savedFood.getDisplayName()).isEqualTo("Milanesa de carne");
        assertThat(savedFood.getSource()).isEqualTo(UserFoodSource.USDA);
        assertThat(savedFood.getFdcId()).isEqualTo(111L);
        assertThat(savedFood.getUseCount()).isEqualTo(1);
    }

    @Test
    void saveAll_dishWithMultipleIngredients_upsertsEachIngredientSeparately() {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(userFoodRepository.findByUserIdAndNormalizedName(any(), any())).thenReturn(Optional.empty());

        FoodEntryIngredientRequest carne = ingredient("Carne", FoodSource.USDA, 222L);
        FoodEntryIngredientRequest panRallado = ingredient("Pan rallado", FoodSource.ESTIMATED, null);
        newService().saveAll(userId, List.of(request("Milanesa con puré", FoodSource.MIXED, null, List.of(carne, panRallado))));

        ArgumentCaptor<UserFood> captor = ArgumentCaptor.forClass(UserFood.class);
        verify(userFoodRepository, times(2)).save(captor.capture());
        List<UserFood> savedFoods = captor.getAllValues();
        assertThat(savedFoods).extracting(UserFood::getNormalizedName).containsExactlyInAnyOrder("carne", "pan rallado");
        assertThat(savedFoods)
                .extracting(UserFood::getSource)
                .containsExactlyInAnyOrder(UserFoodSource.USDA, UserFoodSource.ESTIMATED);
    }

    @Test
    void saveAll_manualEntry_createsAUserFoodRowSourcedAsUser() {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(userFoodRepository.findByUserIdAndNormalizedName(any(), any())).thenReturn(Optional.empty());

        newService().saveAll(userId, List.of(request("Torta casera", FoodSource.MANUAL, null, null)));

        ArgumentCaptor<UserFood> captor = ArgumentCaptor.forClass(UserFood.class);
        verify(userFoodRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(UserFoodSource.USER);
        assertThat(captor.getValue().getFdcId()).isNull();
    }

    @Test
    void saveAll_existingNormalizedName_bumpsUseCountAndRefreshesValuesInsteadOfInsertingANewRow() {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        UserFood existing = new UserFood(
                userId,
                "milanesa de carne",
                "Milanesa de carne",
                new NutritionMath.Per100(200, 20, 10, 5, 1, 1, 300),
                UserFoodSource.ESTIMATED,
                null);
        when(userFoodRepository.findByUserIdAndNormalizedName(userId, "milanesa de carne"))
                .thenReturn(Optional.of(existing));

        newService().saveAll(userId, List.of(request("Milanesa de carne", FoodSource.USDA, 222L, null)));

        verify(userFoodRepository, never()).save(any());
        assertThat(existing.getUseCount()).isEqualTo(2);
        assertThat(existing.getSource()).isEqualTo(UserFoodSource.USDA);
        assertThat(existing.getFdcId()).isEqualTo(222L);
    }

    @Test
    void saveAll_personalMatch_keepsTheLibraryRowsOwnOriginalSourceInstead() {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        UserFood existing = new UserFood(
                userId,
                "milanesa de carne",
                "Milanesa de carne",
                new NutritionMath.Per100(200, 20, 10, 5, 1, 1, 300),
                UserFoodSource.USER,
                null);
        when(userFoodRepository.findByUserIdAndNormalizedName(userId, "milanesa de carne"))
                .thenReturn(Optional.of(existing));

        // source=PERSONAL means the matcher already resolved this item FROM the library itself —
        // the library's own record of where the numbers first came from must not change.
        newService().saveAll(userId, List.of(request("Milanesa de carne", FoodSource.PERSONAL, null, null)));

        assertThat(existing.getSource()).isEqualTo(UserFoodSource.USER);
        assertThat(existing.getUseCount()).isEqualTo(2);
    }

    @Test
    void saveAll_blankDishNameAndNoIngredients_neverTouchesTheLibrary() {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        newService().saveAll(userId, List.of(request("   ", FoodSource.MANUAL, null, null)));

        verifyNoMoreInteractions(userFoodRepository);
    }

    @Test
    void saveAll_oneIngredientHasABlankName_thatOneIsSkippedButOthersStillUpsert() {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(userFoodRepository.findByUserIdAndNormalizedName(any(), any())).thenReturn(Optional.empty());

        FoodEntryIngredientRequest blank = ingredient("   ", FoodSource.ESTIMATED, null);
        FoodEntryIngredientRequest carne = ingredient("Carne", FoodSource.USDA, null);
        newService().saveAll(userId, List.of(request("Milanesa", FoodSource.MIXED, null, List.of(blank, carne))));

        verify(userFoodRepository, times(1)).save(any());
    }

    @Test
    void update_noIngredients_scalesStoredIngredientsProportionallyAndKeepsPer100SourceAndFdcId() {
        FoodEntry entry = new FoodEntry(
                userId,
                LocalDate.of(2026, 9, 25),
                MealType.ALMUERZO,
                "Milanesa con puré",
                new BigDecimal("200"),
                new BigDecimal("220.00"),
                new BigDecimal("18.00"),
                new BigDecimal("9.00"),
                new BigDecimal("20.00"),
                new BigDecimal("2.00"),
                new BigDecimal("3.00"),
                new BigDecimal("350.00"),
                FoodSource.MIXED,
                null,
                List.of(
                        ingredientEntity("Carne", "150", FoodSource.USDA, 111L),
                        ingredientEntity("Puré de papas", "50", FoodSource.ESTIMATED, null)));
        when(repository.findByIdAndUserId(any(), eq(userId))).thenReturn(Optional.of(entry));

        UUID id = UUID.randomUUID();
        Optional<FoodEntryResponse> result =
                newService().update(userId, id, new UpdateFoodEntryRequest(new BigDecimal("300"), MealType.CENA, null));

        assertThat(result).isPresent();
        assertThat(result.get().mealType()).isEqualTo(MealType.CENA);
        assertThat(result.get().grams()).isEqualByComparingTo("300");
        // Dish per-100g/source/fdcId are untouched by a plain grams change.
        assertThat(result.get().kcalPer100()).isEqualByComparingTo("220.00");
        assertThat(result.get().source()).isEqualTo(FoodSource.MIXED);
        // Ratio 300/200 = 1.5 -> ingredient grams scale from 150/50 to 225/75.
        assertThat(result.get().ingredients()).hasSize(2);
        assertThat(result.get().ingredients().get(0).grams()).isEqualByComparingTo("225.0");
        assertThat(result.get().ingredients().get(1).grams()).isEqualByComparingTo("75.0");
        assertThat(result.get().ingredients().get(0).source()).isEqualTo(FoodSource.USDA);
        assertThat(result.get().ingredients().get(0).fdcId()).isEqualTo(111L);
    }

    @Test
    void update_noIngredients_legacyEntryWithNoStoredIngredients_stillUpdatesGramsWithoutError() {
        FoodEntry entry = new FoodEntry(
                userId,
                LocalDate.of(2026, 9, 25),
                MealType.ALMUERZO,
                "Entrada vieja",
                new BigDecimal("150"),
                new BigDecimal("100.00"),
                new BigDecimal("5.00"),
                new BigDecimal("2.00"),
                new BigDecimal("10.00"),
                new BigDecimal("1.00"),
                new BigDecimal("1.00"),
                new BigDecimal("100.00"),
                FoodSource.ESTIMATED,
                null,
                null);
        when(repository.findByIdAndUserId(any(), eq(userId))).thenReturn(Optional.of(entry));

        Optional<FoodEntryResponse> result = newService()
                .update(userId, UUID.randomUUID(), new UpdateFoodEntryRequest(new BigDecimal("180"), MealType.CENA, null));

        assertThat(result).isPresent();
        assertThat(result.get().grams()).isEqualByComparingTo("180");
        assertThat(result.get().ingredients()).isNull();
    }

    @Test
    void update_ingredientsProvided_recomputesGramsPer100SourceAndFdcIdFromThem() {
        FoodEntry entry = new FoodEntry(
                userId,
                LocalDate.of(2026, 9, 25),
                MealType.ALMUERZO,
                "Milanesa con puré",
                new BigDecimal("200"),
                new BigDecimal("220.00"),
                new BigDecimal("18.00"),
                new BigDecimal("9.00"),
                new BigDecimal("20.00"),
                new BigDecimal("2.00"),
                new BigDecimal("3.00"),
                new BigDecimal("350.00"),
                FoodSource.MIXED,
                null,
                List.of(
                        ingredientEntity("Carne", "150", FoodSource.USDA, 111L),
                        ingredientEntity("Puré de papas", "50", FoodSource.ESTIMATED, null)));
        when(repository.findByIdAndUserId(any(), eq(userId))).thenReturn(Optional.of(entry));

        // The user removed "Puré de papas" in the ingredients editor — one USDA ingredient remains.
        FoodEntryIngredientRequest onlyIngredient = new FoodEntryIngredientRequest(
                "Carne",
                new BigDecimal("150"),
                new BigDecimal("250"),
                new BigDecimal("26"),
                new BigDecimal("15"),
                new BigDecimal("0"),
                new BigDecimal("0"),
                new BigDecimal("0"),
                new BigDecimal("60"),
                FoodSource.USDA,
                111L);
        NutritionMath.Totals expectedTotals = NutritionMath.totals(
                new NutritionMath.Per100(250, 26, 15, 0, 0, 0, 60), 150);
        NutritionMath.Per100 expectedPer100 = NutritionMath.per100FromTotals(expectedTotals, 150);

        Optional<FoodEntryResponse> result = newService()
                .update(
                        userId,
                        UUID.randomUUID(),
                        new UpdateFoodEntryRequest(new BigDecimal("999"), MealType.ALMUERZO, List.of(onlyIngredient)));

        assertThat(result).isPresent();
        FoodEntryResponse response = result.get();
        // The dish's own grams comes from the SUM of the given ingredients, not the (stale) 999 sent alongside them.
        assertThat(response.grams()).isEqualByComparingTo("150");
        assertThat(response.kcalPer100().doubleValue()).isEqualTo(expectedPer100.kcal());
        assertThat(response.source()).isEqualTo(FoodSource.USDA);
        assertThat(response.fdcId()).isEqualTo(111L);
        assertThat(response.ingredients()).hasSize(1);
        assertThat(response.totals().kcal()).isEqualTo(expectedTotals.kcal());
    }

    @Test
    void update_ingredientsProvided_mixedSourcesAcrossIngredients_dishSourceIsMixed() {
        FoodEntry entry = new FoodEntry(
                userId,
                LocalDate.of(2026, 9, 25),
                MealType.ALMUERZO,
                "Milanesa con puré",
                new BigDecimal("200"),
                new BigDecimal("220.00"),
                new BigDecimal("18.00"),
                new BigDecimal("9.00"),
                new BigDecimal("20.00"),
                new BigDecimal("2.00"),
                new BigDecimal("3.00"),
                new BigDecimal("350.00"),
                FoodSource.USDA,
                111L,
                List.of(ingredientEntity("Carne", "200", FoodSource.USDA, 111L)));
        when(repository.findByIdAndUserId(any(), eq(userId))).thenReturn(Optional.of(entry));

        FoodEntryIngredientRequest carne = new FoodEntryIngredientRequest(
                "Carne", new BigDecimal("150"), new BigDecimal("250"), new BigDecimal("26"), new BigDecimal("15"),
                new BigDecimal("0"), new BigDecimal("0"), new BigDecimal("0"), new BigDecimal("60"), FoodSource.USDA, 111L);
        FoodEntryIngredientRequest saborizante = new FoodEntryIngredientRequest(
                "Aderezo casero", new BigDecimal("20"), new BigDecimal("90"), new BigDecimal("1"), new BigDecimal("8"),
                new BigDecimal("3"), new BigDecimal("0"), new BigDecimal("2"), new BigDecimal("200"), FoodSource.ESTIMATED, null);

        Optional<FoodEntryResponse> result = newService()
                .update(
                        userId,
                        UUID.randomUUID(),
                        new UpdateFoodEntryRequest(new BigDecimal("170"), MealType.ALMUERZO, List.of(carne, saborizante)));

        assertThat(result).isPresent();
        assertThat(result.get().source()).isEqualTo(FoodSource.MIXED);
        // A dish backed by more than one ingredient has no single well-defined USDA row.
        assertThat(result.get().fdcId()).isNull();
        assertThat(result.get().grams()).isEqualByComparingTo("170");
    }

    private FoodEntryService newService() {
        return new FoodEntryService(repository, userFoodRepository);
    }

    private static FoodEntryIngredient ingredientEntity(String name, String grams, FoodSource source, Long fdcId) {
        return new FoodEntryIngredient(
                name,
                new BigDecimal(grams),
                new BigDecimal("200.00"),
                new BigDecimal("20.00"),
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                new BigDecimal("2.00"),
                new BigDecimal("3.00"),
                new BigDecimal("300.00"),
                source,
                fdcId);
    }

    private static FoodEntryIngredientRequest ingredient(String name, FoodSource source, Long fdcId) {
        return new FoodEntryIngredientRequest(
                name,
                new BigDecimal("100"),
                new BigDecimal("200"),
                new BigDecimal("20"),
                new BigDecimal("10"),
                new BigDecimal("20"),
                new BigDecimal("2"),
                new BigDecimal("3"),
                new BigDecimal("300"),
                source,
                fdcId);
    }

    private static FoodEntryRequest request(
            String name, FoodSource source, Long fdcId, List<FoodEntryIngredientRequest> ingredients) {
        return new FoodEntryRequest(
                LocalDate.of(2026, 9, 25),
                MealType.ALMUERZO,
                name,
                new BigDecimal("150"),
                new BigDecimal("200"),
                new BigDecimal("20"),
                new BigDecimal("10"),
                new BigDecimal("5"),
                new BigDecimal("1"),
                new BigDecimal("1"),
                new BigDecimal("300"),
                source,
                fdcId,
                ingredients);
    }
}
