package com.kcalma.weight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kcalma.profile.ActivityLevel;
import com.kcalma.profile.Goal;
import com.kcalma.profile.Sex;
import com.kcalma.profile.UserProfile;
import com.kcalma.profile.UserProfileRepository;
import com.kcalma.weight.dto.UpsertWeightRequest;
import com.kcalma.weight.dto.UpsertWeightResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link WeightEntryService}. No Spring context — repositories are mocked, exactly
 * the way {@code FoodEntryServiceTest} tests {@code FoodEntryService}.
 *
 * <p>Every upsert/delete recomputes the EMA trend (see {@code WeightTrendCalculator}, alpha =
 * 0.1) across ALL of the user's weigh-ins and stores the value AT THE LATEST DATE into {@code
 * user_profile.weight_kg} — never the raw latest weigh-in, and never gated on "was the edited
 * date the latest one", since editing any point reflows the whole EMA chain.
 */
@ExtendWith(MockitoExtension.class)
class WeightEntryServiceTest {

    @Mock
    private WeightEntryRepository repository;

    @Mock
    private UserProfileRepository profileRepository;

    private final UUID userId = UUID.randomUUID();

    @Test
    void upsert_firstEverWeighIn_trendEqualsTheRawValue() {
        WeightEntryService service = new WeightEntryService(repository, profileRepository);
        LocalDate date = LocalDate.of(2026, 9, 25);
        UserProfile profile = profileFor(userId);

        when(repository.findByUserIdAndEntryDate(userId, date)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findByUserIdOrderByEntryDateAsc(userId))
                .thenReturn(List.of(new WeightEntry(userId, date, new BigDecimal("70.50"))));
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

        UpsertWeightResponse response = service.upsert(userId, date, new UpsertWeightRequest(new BigDecimal("70.50")));

        assertThat(response.targetsUpdated()).isTrue();
        assertThat(response.entry().weightKg()).isEqualByComparingTo("70.50");
        assertThat(profile.getWeightKg()).isEqualByComparingTo("70.5");
    }

    @Test
    void upsert_thirdConsecutiveWeighIn_storesTheEmaTrendAtLatestNotTheRawValue() {
        WeightEntryService service = new WeightEntryService(repository, profileRepository);
        LocalDate day1 = LocalDate.of(2026, 9, 23);
        LocalDate day2 = LocalDate.of(2026, 9, 24);
        LocalDate day3 = LocalDate.of(2026, 9, 25);
        UserProfile profile = profileFor(userId);

        when(repository.findByUserIdAndEntryDate(userId, day3)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findByUserIdOrderByEntryDateAsc(userId))
                .thenReturn(List.of(
                        new WeightEntry(userId, day1, new BigDecimal("80.00")),
                        new WeightEntry(userId, day2, new BigDecimal("79.00")),
                        new WeightEntry(userId, day3, new BigDecimal("78.00"))));
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

        service.upsert(userId, day3, new UpsertWeightRequest(new BigDecimal("78.00")));

        // trend0=80.0; trend1=80+0.1*(79-80)=79.9; trend2=79.9+0.1*(78-79.9)=79.71 -> rounds to 79.7,
        // clearly different from both the raw latest value (78.00) and the previous behavior.
        assertThat(profile.getWeightKg()).isEqualByComparingTo("79.7");
    }

    @Test
    void upsert_editingAnOlderDateThanTheLatest_stillRecomputesAndUpdatesProfileWeight() {
        WeightEntryService service = new WeightEntryService(repository, profileRepository);
        // One day apart -> gapDays=1 -> alphaEffective is the plain 0.1 alpha (no gap compounding),
        // which keeps the expected trend value a simple hand-checkable number.
        LocalDate olderDate = LocalDate.of(2026, 9, 24);
        LocalDate latestDate = LocalDate.of(2026, 9, 25);
        UserProfile profile = profileFor(userId);

        when(repository.findByUserIdAndEntryDate(userId, olderDate))
                .thenReturn(Optional.of(new WeightEntry(userId, olderDate, new BigDecimal("80.00"))));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // The edit (80.00 -> 82.00) reflows the whole EMA chain, even though `olderDate` isn't latest.
        when(repository.findByUserIdOrderByEntryDateAsc(userId))
                .thenReturn(List.of(
                        new WeightEntry(userId, olderDate, new BigDecimal("82.00")),
                        new WeightEntry(userId, latestDate, new BigDecimal("79.00"))));
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

        UpsertWeightResponse response =
                service.upsert(userId, olderDate, new UpsertWeightRequest(new BigDecimal("82.00")));

        // trend0=82.0; trend1=82.0+0.1*(79.0-82.0)=81.7 -- neither 80.0 (old raw) nor 79.0 (latest raw).
        assertThat(response.targetsUpdated()).isTrue();
        assertThat(profile.getWeightKg()).isEqualByComparingTo("81.7");
    }

    @Test
    void upsert_noProfileExists_doesNotClaimTargetsUpdated() {
        WeightEntryService service = new WeightEntryService(repository, profileRepository);
        LocalDate date = LocalDate.of(2026, 9, 25);

        when(repository.findByUserIdAndEntryDate(userId, date)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findByUserIdOrderByEntryDateAsc(userId))
                .thenReturn(List.of(new WeightEntry(userId, date, new BigDecimal("70.50"))));
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());

        UpsertWeightResponse response = service.upsert(userId, date, new UpsertWeightRequest(new BigDecimal("70.50")));

        assertThat(response.targetsUpdated()).isFalse();
    }

    @Test
    void delete_latestEntry_recomputesTrendFromTheRemainingWeighIns() {
        WeightEntryService service = new WeightEntryService(repository, profileRepository);
        LocalDate day1 = LocalDate.of(2026, 9, 23);
        LocalDate day2 = LocalDate.of(2026, 9, 24);
        LocalDate day3 = LocalDate.of(2026, 9, 25);
        UserProfile profile = profileFor(userId);
        WeightEntry latest = new WeightEntry(userId, day3, new BigDecimal("78.00"));

        when(repository.findByUserIdAndEntryDate(userId, day3)).thenReturn(Optional.of(latest));
        // After the delete, only day1/day2 remain.
        when(repository.findByUserIdOrderByEntryDateAsc(userId))
                .thenReturn(List.of(
                        new WeightEntry(userId, day1, new BigDecimal("80.00")),
                        new WeightEntry(userId, day2, new BigDecimal("79.00"))));
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

        boolean deleted = service.delete(userId, day3);

        assertThat(deleted).isTrue();
        verify(repository).delete(latest);
        // trend0=80.0; trend1=80+0.1*(79-80)=79.9 -> the new latest (day2) trend.
        assertThat(profile.getWeightKg()).isEqualByComparingTo("79.9");
    }

    @Test
    void delete_theOnlyRemainingEntry_leavesProfileWeightUntouched() {
        WeightEntryService service = new WeightEntryService(repository, profileRepository);
        LocalDate date = LocalDate.of(2026, 9, 25);
        WeightEntry entry = new WeightEntry(userId, date, new BigDecimal("70.00"));
        when(repository.findByUserIdAndEntryDate(userId, date)).thenReturn(Optional.of(entry));
        when(repository.findByUserIdOrderByEntryDateAsc(userId)).thenReturn(List.of());

        boolean deleted = service.delete(userId, date);

        assertThat(deleted).isTrue();
        verify(repository).delete(entry);
        verify(profileRepository, never()).findById(any());
    }

    @Test
    void delete_missingEntry_returnsFalseWithoutDeletingOrRecomputing() {
        WeightEntryService service = new WeightEntryService(repository, profileRepository);
        LocalDate date = LocalDate.of(2026, 9, 25);
        when(repository.findByUserIdAndEntryDate(userId, date)).thenReturn(Optional.empty());

        boolean deleted = service.delete(userId, date);

        assertThat(deleted).isFalse();
        verify(repository, never()).delete(any());
        verify(repository, never()).findByUserIdOrderByEntryDateAsc(any());
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
