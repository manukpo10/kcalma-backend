package com.kcalma.weight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kcalma.profile.Goal;
import com.kcalma.profile.Sex;
import com.kcalma.profile.UserProfile;
import com.kcalma.profile.UserProfileRepository;
import com.kcalma.profile.ActivityLevel;
import com.kcalma.weight.dto.UpsertWeightRequest;
import com.kcalma.weight.dto.UpsertWeightResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link WeightEntryService}. No Spring context — repositories are mocked, exactly
 * the way {@code FoodEntryServiceTest} tests {@code FoodEntryService}.
 */
@ExtendWith(MockitoExtension.class)
class WeightEntryServiceTest {

    @Mock
    private WeightEntryRepository repository;

    @Mock
    private UserProfileRepository profileRepository;

    private final UUID userId = UUID.randomUUID();

    @Test
    void upsert_newDateIsTheOnlyEntry_isMostRecentAndUpdatesProfileWeight() {
        WeightEntryService service = new WeightEntryService(repository, profileRepository);
        LocalDate date = LocalDate.of(2026, 9, 25);
        UserProfile profile = profileFor(userId);

        when(repository.findByUserIdAndEntryDate(userId, date)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findFirstByUserIdOrderByEntryDateDesc(userId))
                .thenReturn(Optional.of(new WeightEntry(userId, date, new BigDecimal("70.50"))));
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

        UpsertWeightResponse response = service.upsert(userId, date, new UpsertWeightRequest(new BigDecimal("70.50")));

        assertThat(response.targetsUpdated()).isTrue();
        assertThat(response.entry().weightKg()).isEqualByComparingTo("70.50");
        assertThat(response.entry().entryDate()).isEqualTo(date);
        assertThat(profile.getWeightKg()).isEqualByComparingTo("70.50");
    }

    @Test
    void upsert_editingAnOlderDateThanTheLatestEntry_doesNotUpdateProfileWeight() {
        WeightEntryService service = new WeightEntryService(repository, profileRepository);
        LocalDate editedDate = LocalDate.of(2026, 9, 10);
        LocalDate latestDate = LocalDate.of(2026, 9, 25);

        when(repository.findByUserIdAndEntryDate(userId, editedDate))
                .thenReturn(Optional.of(new WeightEntry(userId, editedDate, new BigDecimal("71.00"))));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findFirstByUserIdOrderByEntryDateDesc(userId))
                .thenReturn(Optional.of(new WeightEntry(userId, latestDate, new BigDecimal("69.00"))));

        UpsertWeightResponse response = service.upsert(userId, editedDate, new UpsertWeightRequest(new BigDecimal("70.20")));

        assertThat(response.targetsUpdated()).isFalse();
        assertThat(response.entry().weightKg()).isEqualByComparingTo("70.20");
        verify(profileRepository, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void upsert_mostRecentButNoProfileExists_doesNotClaimTargetsUpdated() {
        WeightEntryService service = new WeightEntryService(repository, profileRepository);
        LocalDate date = LocalDate.of(2026, 9, 25);

        when(repository.findByUserIdAndEntryDate(userId, date)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findFirstByUserIdOrderByEntryDateDesc(userId))
                .thenReturn(Optional.of(new WeightEntry(userId, date, new BigDecimal("70.50"))));
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());

        UpsertWeightResponse response = service.upsert(userId, date, new UpsertWeightRequest(new BigDecimal("70.50")));

        assertThat(response.targetsUpdated()).isFalse();
    }

    @Test
    void delete_existingEntry_deletesItAndReturnsTrue() {
        WeightEntryService service = new WeightEntryService(repository, profileRepository);
        LocalDate date = LocalDate.of(2026, 9, 25);
        WeightEntry entry = new WeightEntry(userId, date, new BigDecimal("70.00"));
        when(repository.findByUserIdAndEntryDate(userId, date)).thenReturn(Optional.of(entry));

        boolean deleted = service.delete(userId, date);

        assertThat(deleted).isTrue();
        verify(repository).delete(entry);
    }

    @Test
    void delete_missingEntry_returnsFalseWithoutDeleting() {
        WeightEntryService service = new WeightEntryService(repository, profileRepository);
        LocalDate date = LocalDate.of(2026, 9, 25);
        when(repository.findByUserIdAndEntryDate(userId, date)).thenReturn(Optional.empty());

        boolean deleted = service.delete(userId, date);

        assertThat(deleted).isFalse();
        verify(repository, org.mockito.Mockito.never()).delete(any());
    }

    private static UserProfile profileFor(UUID userId) {
        UserProfile profile = new UserProfile(userId);
        profile.setSex(Sex.FEMALE);
        profile.setBirthDate(LocalDate.of(1990, 1, 1));
        profile.setHeightCm(165);
        profile.setWeightKg(new BigDecimal("72.00"));
        profile.setActivityLevel(ActivityLevel.SEDENTARY);
        profile.setGoal(Goal.MAINTAIN);
        return profile;
    }
}
