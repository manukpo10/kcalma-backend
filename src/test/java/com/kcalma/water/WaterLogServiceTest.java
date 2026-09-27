package com.kcalma.water;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kcalma.profile.ProfileService;
import com.kcalma.profile.ProteinBasis;
import com.kcalma.profile.dto.NutritionTargetsResponse;
import com.kcalma.profile.dto.ProfileResponse;
import com.kcalma.profile.dto.ProfileWithTargetsResponse;
import com.kcalma.water.dto.WaterResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link WaterLogService}: the signed-delta upsert, the floor-at-0 rule, and the profile-derived target. */
@ExtendWith(MockitoExtension.class)
class WaterLogServiceTest {

    @Mock
    private WaterLogRepository repository;

    @Mock
    private ProfileService profileService;

    private final UUID userId = UUID.randomUUID();
    private final LocalDate date = LocalDate.of(2026, 9, 25);

    @Test
    void applyDelta_firstLogOfTheDay_startsFromZero() {
        when(repository.findByUserIdAndEntryDate(userId, date)).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(profileService.findByUserId(userId)).thenReturn(Optional.of(profileWithWaterTarget(2500)));

        WaterResponse response = newService().applyDelta(userId, date, 250);

        assertThat(response.totalMl()).isEqualTo(250);
        assertThat(response.targetMl()).isEqualTo(2500);
        assertThat(response.date()).isEqualTo(date);
    }

    @Test
    void applyDelta_positiveDeltaOnExistingTotal_adds() {
        when(repository.findByUserIdAndEntryDate(userId, date)).thenReturn(Optional.of(new WaterLog(userId, date, 500)));
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(profileService.findByUserId(userId)).thenReturn(Optional.of(profileWithWaterTarget(2500)));

        WaterResponse response = newService().applyDelta(userId, date, 250);

        assertThat(response.totalMl()).isEqualTo(750);
    }

    @Test
    void applyDelta_negativeDeltaLargerThanCurrentTotal_floorsAtZeroNeverGoesNegative() {
        when(repository.findByUserIdAndEntryDate(userId, date)).thenReturn(Optional.of(new WaterLog(userId, date, 200)));
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(profileService.findByUserId(userId)).thenReturn(Optional.of(profileWithWaterTarget(2500)));

        WaterResponse response = newService().applyDelta(userId, date, -2000);

        assertThat(response.totalMl()).isEqualTo(0);
    }

    @Test
    void applyDelta_negativeDeltaSmallerThanCurrentTotal_subtractsNormally() {
        when(repository.findByUserIdAndEntryDate(userId, date)).thenReturn(Optional.of(new WaterLog(userId, date, 800)));
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(profileService.findByUserId(userId)).thenReturn(Optional.of(profileWithWaterTarget(2500)));

        WaterResponse response = newService().applyDelta(userId, date, -300);

        assertThat(response.totalMl()).isEqualTo(500);
    }

    @Test
    void applyDelta_noProfileYet_targetMlIsZeroNotAnError() {
        when(repository.findByUserIdAndEntryDate(userId, date)).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(profileService.findByUserId(userId)).thenReturn(Optional.empty());

        WaterResponse response = newService().applyDelta(userId, date, 100);

        assertThat(response.targetMl()).isEqualTo(0);
    }

    @Test
    void consumedMl_noRowForThatDay_returnsZero() {
        when(repository.findByUserIdAndEntryDate(userId, date)).thenReturn(Optional.empty());

        assertThat(newService().consumedMl(userId, date)).isEqualTo(0);
    }

    private WaterLogService newService() {
        return new WaterLogService(repository, profileService);
    }

    private static ProfileWithTargetsResponse profileWithWaterTarget(int waterMl) {
        NutritionTargetsResponse targets = new NutritionTargetsResponse(
                2000, false, 120, 65, 220, 28, 50, 2000, waterMl, 0.0, 0.0, ProteinBasis.BODY_WEIGHT, 80.0, null, List.of());
        ProfileResponse profile = new ProfileResponse(
                UUID.randomUUID(),
                com.kcalma.profile.Sex.FEMALE,
                LocalDate.of(1990, 1, 1),
                165,
                new java.math.BigDecimal("65.00"),
                com.kcalma.profile.ActivityLevel.SEDENTARY,
                com.kcalma.profile.Goal.MAINTAIN,
                null,
                null,
                com.kcalma.profile.DietStyle.BALANCED,
                List.of(),
                false,
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now());
        return new ProfileWithTargetsResponse(profile, targets);
    }
}
