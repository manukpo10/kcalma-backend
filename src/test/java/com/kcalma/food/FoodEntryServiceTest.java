package com.kcalma.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.kcalma.food.dto.FoodEntryRequest;
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
 * rest of the service layer — asserting exactly what was forwarded to a mocked repository), and
 * the save -> {@code app.user_food} upsert: create on first save, bump use_count/refresh values on
 * a repeat, and never let a {@code PERSONAL}-sourced save downgrade the library row's own original
 * source (see {@code com.kcalma.food.reference.FoodReferenceMatcher}).
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
    void saveAll_newNormalizedName_createsAUserFoodRowSourcedFromTheEntry() {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(userFoodRepository.findByUserIdAndNormalizedName(userId, "milanesa de carne")).thenReturn(Optional.empty());

        newService().saveAll(userId, List.of(request("Milanesa de carne", FoodSource.USDA, 111L)));

        ArgumentCaptor<UserFood> captor = ArgumentCaptor.forClass(UserFood.class);
        verify(userFoodRepository).save(captor.capture());
        UserFood saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getNormalizedName()).isEqualTo("milanesa de carne");
        assertThat(saved.getDisplayName()).isEqualTo("Milanesa de carne");
        assertThat(saved.getSource()).isEqualTo(UserFoodSource.USDA);
        assertThat(saved.getFdcId()).isEqualTo(111L);
        assertThat(saved.getUseCount()).isEqualTo(1);
    }

    @Test
    void saveAll_manualEntry_createsAUserFoodRowSourcedAsUser() {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(userFoodRepository.findByUserIdAndNormalizedName(any(), any())).thenReturn(Optional.empty());

        newService().saveAll(userId, List.of(request("Torta casera", FoodSource.MANUAL, null)));

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

        newService().saveAll(userId, List.of(request("Milanesa de carne", FoodSource.USDA, 222L)));

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
        newService().saveAll(userId, List.of(request("Milanesa de carne", FoodSource.PERSONAL, null)));

        assertThat(existing.getSource()).isEqualTo(UserFoodSource.USER);
        assertThat(existing.getUseCount()).isEqualTo(2);
    }

    @Test
    void saveAll_blankName_neverTouchesTheLibrary() {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        newService().saveAll(userId, List.of(request("   ", FoodSource.MANUAL, null)));

        verifyNoMoreInteractions(userFoodRepository);
    }

    private FoodEntryService newService() {
        return new FoodEntryService(repository, userFoodRepository);
    }

    private static FoodEntryRequest request(String name, FoodSource source, Long fdcId) {
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
                fdcId);
    }
}
