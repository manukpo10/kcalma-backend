package com.kcalma.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kcalma.food.dto.RecentDishResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link RecentDishService}: distinct-by-normalized-name collapsing, the
 * timesLogged-desc/lastLoggedOn-desc ranking, the default/explicit limit, and the exact 60-day
 * lookback window handed to the repository.
 */
@ExtendWith(MockitoExtension.class)
class RecentDishServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

    @Mock
    private FoodEntryRepository repository;

    private final UUID userId = UUID.randomUUID();

    @Test
    void findRecent_queriesExactlyTheLast60DaysInclusiveOfToday() {
        when(repository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(eq(userId), org.mockito.ArgumentMatchers.any(), eq(TODAY)))
                .thenReturn(List.of());

        newService().findRecent(userId, null);

        // 60 calendar days inclusive of today (2026-09-25) starts 2026-07-28.
        verify(repository).findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, LocalDate.of(2026, 7, 28), TODAY);
    }

    @Test
    void findRecent_sameDishLoggedTwiceWithDifferentCasingAndSpacing_collapsesToOneEntryWithTimesLoggedTwo() {
        FoodEntry first = entry(LocalDate.of(2026, 9, 1), "Milanesa", MealType.ALMUERZO);
        FoodEntry second = entry(LocalDate.of(2026, 9, 10), "  milanesa  ", MealType.CENA);
        when(repository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(eq(userId), org.mockito.ArgumentMatchers.any(), eq(TODAY)))
                .thenReturn(List.of(first, second));

        List<RecentDishResponse> recent = newService().findRecent(userId, null);

        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).timesLogged()).isEqualTo(2);
        // The dish's own fields (name, grams, mealType...) come from the MOST RECENT entry in the group.
        assertThat(recent.get(0).dish().name()).isEqualTo("  milanesa  ");
        assertThat(recent.get(0).lastMealType()).isEqualTo(MealType.CENA);
        assertThat(recent.get(0).lastLoggedOn()).isEqualTo(LocalDate.of(2026, 9, 10));
    }

    @Test
    void findRecent_ranksByTimesLoggedDescendingBeforeRecency() {
        FoodEntry loggedOnceRecently = entry(LocalDate.of(2026, 9, 24), "Ensalada", MealType.CENA);
        FoodEntry loggedThreeTimesOlder1 = entry(LocalDate.of(2026, 8, 1), "Pollo", MealType.ALMUERZO);
        FoodEntry loggedThreeTimesOlder2 = entry(LocalDate.of(2026, 8, 5), "Pollo", MealType.ALMUERZO);
        FoodEntry loggedThreeTimesOlder3 = entry(LocalDate.of(2026, 8, 10), "Pollo", MealType.ALMUERZO);
        when(repository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(eq(userId), org.mockito.ArgumentMatchers.any(), eq(TODAY)))
                .thenReturn(List.of(loggedOnceRecently, loggedThreeTimesOlder1, loggedThreeTimesOlder2, loggedThreeTimesOlder3));

        List<RecentDishResponse> recent = newService().findRecent(userId, null);

        assertThat(recent).hasSize(2);
        // "Pollo" (3x) outranks "Ensalada" (1x, even though it's the more recent one).
        assertThat(recent.get(0).dish().name()).isEqualTo("Pollo");
        assertThat(recent.get(0).timesLogged()).isEqualTo(3);
        assertThat(recent.get(1).dish().name()).isEqualTo("Ensalada");
    }

    @Test
    void findRecent_tiedTimesLogged_ranksByLastLoggedOnDescending() {
        FoodEntry olderDish = entry(LocalDate.of(2026, 9, 1), "Sopa", MealType.CENA);
        FoodEntry newerDish = entry(LocalDate.of(2026, 9, 20), "Tarta", MealType.CENA);
        when(repository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(eq(userId), org.mockito.ArgumentMatchers.any(), eq(TODAY)))
                .thenReturn(List.of(olderDish, newerDish));

        List<RecentDishResponse> recent = newService().findRecent(userId, null);

        assertThat(recent).extracting(r -> r.dish().name()).containsExactly("Tarta", "Sopa");
    }

    @Test
    void findRecent_explicitLimit_trimsToThatManyResults() {
        FoodEntry a = entry(LocalDate.of(2026, 9, 1), "A", MealType.CENA);
        FoodEntry b = entry(LocalDate.of(2026, 9, 2), "B", MealType.CENA);
        FoodEntry c = entry(LocalDate.of(2026, 9, 3), "C", MealType.CENA);
        when(repository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(eq(userId), org.mockito.ArgumentMatchers.any(), eq(TODAY)))
                .thenReturn(List.of(a, b, c));

        assertThat(newService().findRecent(userId, 2)).hasSize(2);
    }

    @Test
    void findRecent_nullOrNonPositiveLimit_defaultsToTwenty() {
        when(repository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(eq(userId), org.mockito.ArgumentMatchers.any(), eq(TODAY)))
                .thenReturn(threeAndTwentyFiveMoreDistinctDishes());

        assertThat(newService().findRecent(userId, null)).hasSize(20);
        assertThat(newService().findRecent(userId, 0)).hasSize(20);
        assertThat(newService().findRecent(userId, -5)).hasSize(20);
    }

    private RecentDishService newService() {
        return new RecentDishService(repository, CLOCK);
    }

    private static List<FoodEntry> threeAndTwentyFiveMoreDistinctDishes() {
        return java.util.stream.IntStream.range(0, 25)
                .mapToObj(i -> entry(LocalDate.of(2026, 9, 1).plusDays(i), "Dish " + i, MealType.SNACK))
                .toList();
    }

    private static FoodEntry entry(LocalDate date, String name, MealType mealType) {
        return new FoodEntry(
                UUID.randomUUID(),
                date,
                mealType,
                name,
                new BigDecimal("150.00"),
                new BigDecimal("200.00"),
                new BigDecimal("20.00"),
                new BigDecimal("10.00"),
                new BigDecimal("5.00"),
                new BigDecimal("1.00"),
                new BigDecimal("1.00"),
                new BigDecimal("300.00"),
                FoodSource.MANUAL,
                null,
                null);
    }
}
