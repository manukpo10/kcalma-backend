package com.kcalma.favorites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kcalma.favorites.dto.CreateFavoriteRequest;
import com.kcalma.food.FoodEntry;
import com.kcalma.food.FoodEntryRepository;
import com.kcalma.food.FoodSource;
import com.kcalma.food.MealType;
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
import org.springframework.web.server.ResponseStatusException;

/** Unit tests for {@link FavoriteDishService}: the two input shapes ({@code entryId} vs. a raw Dish) and the upsert-by-normalized-name rule. */
@ExtendWith(MockitoExtension.class)
class FavoriteDishServiceTest {

    @Mock
    private FavoriteDishRepository repository;

    @Mock
    private FoodEntryRepository foodEntryRepository;

    private final UUID userId = UUID.randomUUID();

    @Test
    void upsert_fromEntryId_snapshotsTheEntrysOwnDishFields() {
        UUID entryId = UUID.randomUUID();
        FoodEntry entry = new FoodEntry(
                userId,
                LocalDate.of(2026, 9, 25),
                MealType.ALMUERZO,
                "Milanesa de carne",
                new BigDecimal("150.00"),
                new BigDecimal("250.00"),
                new BigDecimal("26.00"),
                new BigDecimal("15.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("70.00"),
                FoodSource.USDA,
                111L,
                null);
        when(foodEntryRepository.findByIdAndUserId(entryId, userId)).thenReturn(Optional.of(entry));
        when(repository.findByUserIdAndNormalizedName(userId, "milanesa de carne")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = newService().upsert(userId, new CreateFavoriteRequest(
                entryId, null, null, null, null, null, null, null, null, null, null, null, null, MealType.ALMUERZO));

        assertThat(response.dish().name()).isEqualTo("Milanesa de carne");
        assertThat(response.mealType()).isEqualTo(MealType.ALMUERZO);
        ArgumentCaptor<FavoriteDish> captor = ArgumentCaptor.forClass(FavoriteDish.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNormalizedName()).isEqualTo("milanesa de carne");
        assertThat(captor.getValue().getFdcId()).isEqualTo(111L);
    }

    @Test
    void upsert_fromEntryId_entryDoesNotBelongToCaller_throws400() {
        UUID entryId = UUID.randomUUID();
        when(foodEntryRepository.findByIdAndUserId(entryId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().upsert(userId, new CreateFavoriteRequest(
                        entryId, null, null, null, null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void upsert_fromDishPayload_savesWithSingleSyntheticIngredientWhenNoneSent() {
        when(repository.findByUserIdAndNormalizedName(userId, "tarta de jamon y queso")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CreateFavoriteRequest request = new CreateFavoriteRequest(
                null,
                "Tarta de jamón y queso",
                new BigDecimal("120"),
                new BigDecimal("280"),
                new BigDecimal("12"),
                new BigDecimal("18"),
                new BigDecimal("20"),
                new BigDecimal("1"),
                new BigDecimal("2"),
                new BigDecimal("450"),
                FoodSource.MANUAL,
                null,
                null,
                null);

        var response = newService().upsert(userId, request);

        assertThat(response.dish().ingredients()).hasSize(1);
        assertThat(response.dish().ingredients().get(0).name()).isEqualTo("Tarta de jamón y queso");
    }

    @Test
    void upsert_fromDishPayload_missingRequiredField_throws400() {
        CreateFavoriteRequest missingSource = new CreateFavoriteRequest(
                null,
                "Torta",
                new BigDecimal("100"),
                new BigDecimal("200"),
                new BigDecimal("5"),
                new BigDecimal("5"),
                new BigDecimal("5"),
                new BigDecimal("1"),
                new BigDecimal("1"),
                new BigDecimal("100"),
                null,
                null,
                null,
                null);

        assertThatThrownBy(() -> newService().upsert(userId, missingSource)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void upsert_existingNormalizedName_refreshesInPlaceInsteadOfInsertingANewRow() {
        FavoriteDish existing = new FavoriteDish(userId, "milanesa");
        when(repository.findByUserIdAndNormalizedName(userId, "milanesa")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CreateFavoriteRequest request = new CreateFavoriteRequest(
                null,
                "Milanesa",
                new BigDecimal("180"),
                new BigDecimal("260"),
                new BigDecimal("27"),
                new BigDecimal("16"),
                new BigDecimal("0"),
                new BigDecimal("0"),
                new BigDecimal("0"),
                new BigDecimal("75"),
                FoodSource.USDA,
                222L,
                null,
                MealType.CENA);

        var response = newService().upsert(userId, request);

        // Refreshed the SAME row in place — never a second, distinct FavoriteDish instance.
        org.mockito.Mockito.verify(repository).save(existing);
        assertThat(response.dish().grams()).isEqualByComparingTo("180");
        assertThat(response.mealType()).isEqualTo(MealType.CENA);
        assertThat(existing.getFdcId()).isEqualTo(222L);
    }

    @Test
    void delete_notOwnedByCaller_returnsFalse() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThat(newService().delete(userId, id)).isFalse();
    }

    @Test
    void delete_ownedByCaller_deletesAndReturnsTrue() {
        UUID id = UUID.randomUUID();
        FavoriteDish favorite = new FavoriteDish(userId, "milanesa");
        when(repository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(favorite));

        assertThat(newService().delete(userId, id)).isTrue();
        org.mockito.Mockito.verify(repository).delete(favorite);
    }

    @Test
    void findAll_mapsEveryFavoriteMostRecentFirst() {
        when(repository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        assertThat(newService().findAll(userId)).isEmpty();
        org.mockito.Mockito.verify(repository).findByUserIdOrderByCreatedAtDesc(userId);
    }

    private FavoriteDishService newService() {
        return new FavoriteDishService(repository, foodEntryRepository);
    }
}
