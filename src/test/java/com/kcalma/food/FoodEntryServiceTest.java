package com.kcalma.food;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link FoodEntryService}'s delete-a-whole-meal path. The project has no
 * real-DB/{@code @DataJpaTest} test infrastructure, so the "only the matching user+date+meal rows
 * are removed" contract is verified the way the rest of the service is: by asserting the exact
 * scope forwarded to the single repository call, with no other repository interaction (i.e. no
 * fetch-all-then-filter, no per-row loop).
 */
@ExtendWith(MockitoExtension.class)
class FoodEntryServiceTest {

    @Mock
    private FoodEntryRepository repository;

    @Test
    void deleteAllByMeal_delegatesToASingleRepositoryDeleteScopedToUserDateAndMeal() {
        FoodEntryService service = new FoodEntryService(repository);
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 25);

        service.deleteAllByMeal(userId, date, MealType.DESAYUNO);

        verify(repository).deleteByUserIdAndEntryDateAndMealType(userId, date, MealType.DESAYUNO);
        verifyNoMoreInteractions(repository);
        // Sanity check on the scope itself: a different user/date/meal is a different call.
        verify(repository, org.mockito.Mockito.never())
                .deleteByUserIdAndEntryDateAndMealType(otherUserId, date, MealType.DESAYUNO);
        verify(repository, org.mockito.Mockito.never())
                .deleteByUserIdAndEntryDateAndMealType(userId, date, MealType.ALMUERZO);
        verify(repository, org.mockito.Mockito.never())
                .deleteByUserIdAndEntryDateAndMealType(userId, date.plusDays(1), MealType.DESAYUNO);
    }
}
