package com.kcalma.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kcalma.profile.ActivityLevel;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AdaptiveTdeeService} — specifically the "changing activity level resets to
 * FORMULA until the next accepted check-in" rule (sprint 3a spec), since the activity-level
 * comparison lives here, not in {@code ProfileService} (see {@code ProfileServiceAdaptiveTargetsTest}
 * for how the result of this class flows into the targets response).
 */
@ExtendWith(MockitoExtension.class)
class AdaptiveTdeeServiceTest {

    @Mock
    private TdeeCheckinRepository repository;

    private final UUID userId = UUID.randomUUID();

    @Test
    void findActive_noCheckinWasEverAccepted_isEmpty() {
        AdaptiveTdeeService service = new AdaptiveTdeeService(repository);
        when(repository.findFirstByUserIdAndStatusOrderByWeekStartDesc(userId, CheckinStatus.ACCEPTED)).thenReturn(Optional.empty());

        assertThat(service.findActive(userId, ActivityLevel.MODERATELY_ACTIVE)).isEmpty();
    }

    @Test
    void findActive_acceptedCheckinMatchesTheCurrentActivityLevel_isPresentWithItsAppliedTdeeAndWeekStart() {
        AdaptiveTdeeService service = new AdaptiveTdeeService(repository);
        TdeeCheckin accepted = acceptedCheckin(LocalDate.of(2026, 9, 7), 2600, ActivityLevel.MODERATELY_ACTIVE);
        when(repository.findFirstByUserIdAndStatusOrderByWeekStartDesc(userId, CheckinStatus.ACCEPTED))
                .thenReturn(Optional.of(accepted));

        Optional<AdaptiveTdeeService.ActiveAdaptiveTdee> active = service.findActive(userId, ActivityLevel.MODERATELY_ACTIVE);

        assertThat(active).isPresent();
        assertThat(active.get().tdeeKcal()).isEqualTo(2600);
        assertThat(active.get().since()).isEqualTo(LocalDate.of(2026, 9, 7));
    }

    @Test
    void findActive_activityLevelChangedSinceTheAcceptedWeek_resetsToEmpty() {
        AdaptiveTdeeService service = new AdaptiveTdeeService(repository);
        // Accepted while MODERATELY_ACTIVE -- the profile is now VERY_ACTIVE instead.
        TdeeCheckin accepted = acceptedCheckin(LocalDate.of(2026, 9, 7), 2600, ActivityLevel.MODERATELY_ACTIVE);
        when(repository.findFirstByUserIdAndStatusOrderByWeekStartDesc(userId, CheckinStatus.ACCEPTED))
                .thenReturn(Optional.of(accepted));

        Optional<AdaptiveTdeeService.ActiveAdaptiveTdee> active = service.findActive(userId, ActivityLevel.VERY_ACTIVE);

        assertThat(active).isEmpty();
    }

    private TdeeCheckin acceptedCheckin(LocalDate weekStart, int proposedTdee, ActivityLevel activityLevelAtAcceptance) {
        TdeeCheckin checkin = new TdeeCheckin(userId, weekStart);
        checkin.recompute(
                weekStart.minusDays(21), weekStart.minusDays(1), 2759,
                new TdeeAdaptationCalculator.Result(
                        CheckinStatus.PENDING, 15, 8, 2100, -0.5, 2500, proposedTdee, Confidence.MEDIUM, List.of()));
        checkin.accept(activityLevelAtAcceptance, OffsetDateTime.now());
        return checkin;
    }
}
