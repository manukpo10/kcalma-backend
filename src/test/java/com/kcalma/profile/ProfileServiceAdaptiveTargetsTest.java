package com.kcalma.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kcalma.checkin.AdaptiveTdeeService;
import com.kcalma.checkin.AdaptiveTdeeService.ActiveAdaptiveTdee;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for how {@link ProfileService} wires {@link AdaptiveTdeeService}'s answer into the
 * targets response (sprint 3a: {@code energySource}/{@code adaptiveSince}). The activity-level
 * reset rule itself is {@link AdaptiveTdeeService}'s own responsibility — see {@code
 * AdaptiveTdeeServiceTest} — this class only checks that {@code ProfileService} reacts correctly
 * to whatever that service reports.
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceAdaptiveTargetsTest {

    // Fixed at 2026-09-25 so a 1996-09-25 birth date is exactly 30 years old (no timezone edge
    // case here -- ProfileServiceTest already covers that separately).
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneId.of("UTC"));

    @Mock
    private UserProfileRepository repository;

    @Mock
    private AdaptiveTdeeService adaptiveTdeeService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void findByUserId_noActiveAdaptiveTdee_usesFormulaEnergySourceAndTheFormulaCalories() {
        when(repository.findById(userId)).thenReturn(Optional.of(moderatelyActiveMaintainProfile()));
        when(adaptiveTdeeService.findActive(userId, ActivityLevel.MODERATELY_ACTIVE)).thenReturn(Optional.empty());

        var targets = new ProfileService(repository, adaptiveTdeeService, CLOCK)
                .findByUserId(userId).orElseThrow().targets();

        // BMR = 10*80 + 6.25*180 - 5*30 + 5 = 1780; TDEE = 1780*1.55 = 2759; MAINTAIN -> no adjustment.
        assertThat(targets.calories()).isEqualTo(2759);
        assertThat(targets.energySource()).isEqualTo(EnergySource.FORMULA);
        assertThat(targets.adaptiveSince()).isNull();
    }

    @Test
    void findByUserId_activeAdaptiveTdee_usesAdaptiveEnergySourceAndItsTdeeInsteadOfTheFormula() {
        LocalDate acceptedSince = LocalDate.of(2026, 9, 7);
        when(repository.findById(userId)).thenReturn(Optional.of(moderatelyActiveMaintainProfile()));
        when(adaptiveTdeeService.findActive(userId, ActivityLevel.MODERATELY_ACTIVE))
                .thenReturn(Optional.of(new ActiveAdaptiveTdee(2600, acceptedSince)));

        var targets = new ProfileService(repository, adaptiveTdeeService, CLOCK)
                .findByUserId(userId).orElseThrow().targets();

        // MAINTAIN has no adjustment of its own, so calories track the adaptive TDEE (2600)
        // exactly, not the formula's 2759 -- proving the override was actually used.
        assertThat(targets.calories()).isEqualTo(2600);
        assertThat(targets.energySource()).isEqualTo(EnergySource.ADAPTIVE);
        assertThat(targets.adaptiveSince()).isEqualTo(acceptedSince);
    }

    private UserProfile moderatelyActiveMaintainProfile() {
        UserProfile profile = new UserProfile(userId);
        profile.setSex(Sex.MALE);
        profile.setBirthDate(LocalDate.of(1996, 9, 25));
        profile.setHeightCm(180);
        profile.setWeightKg(new BigDecimal("80.00"));
        profile.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);
        profile.setGoal(Goal.MAINTAIN);
        return profile;
    }
}
