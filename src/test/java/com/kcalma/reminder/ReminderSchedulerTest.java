package com.kcalma.reminder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kcalma.food.FoodEntryRepository;
import com.kcalma.profile.ProfileService;
import com.kcalma.push.PushDispatchService;
import com.kcalma.push.PushPayload;
import com.kcalma.water.WaterLogRepository;
import com.kcalma.weight.WeightEntryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link DueReminderEvaluator} is mocked here on purpose: its own SMART-skip rules are already
 * exhaustively covered by {@code DueReminderEvaluatorTest}. What this class tests is everything
 * {@link ReminderScheduler} itself is responsible for — the operational kill switch, and the
 * claim-before-dispatch ordering that makes dedupe actually work.
 */
@ExtendWith(MockitoExtension.class)
class ReminderSchedulerTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    @Mock
    private ReminderSettingsRepository settingsRepository;

    @Mock
    private ReminderLogRepository logRepository;

    @Mock
    private FoodEntryRepository foodEntryRepository;

    @Mock
    private WaterLogRepository waterLogRepository;

    @Mock
    private WeightEntryRepository weightEntryRepository;

    @Mock
    private ProfileService profileService;

    @Mock
    private DueReminderEvaluator evaluator;

    @Mock
    private PushDispatchService dispatchService;

    @Test
    void tick_schedulerDisabled_sendsNothingAndNeverTouchesAnyCollaborator() {
        ReminderScheduler scheduler = schedulerWith(false, fixedClockAt(LocalTime.of(13, 0)));

        scheduler.tick();

        verifyNoInteractions(
                settingsRepository, logRepository, foodEntryRepository, waterLogRepository, weightEntryRepository, profileService,
                evaluator, dispatchService);
    }

    @Test
    void tick_dueReminderClaimedSuccessfully_dispatchesExactlyThatPushPayload() {
        UUID userId = UUID.randomUUID();
        ReminderSettingsData settings = ReminderSettingsData.defaults();
        stubEmptySignalsFor(userId, settings);
        DueReminderEvaluator.Due due = new DueReminderEvaluator.Due("MEAL_ALMUERZO", "¿Qué almorzaste?", "body", "/agregar?meal=ALMUERZO");
        when(evaluator.evaluate(any(), eq(settings), any())).thenReturn(List.of(due));
        when(logRepository.claim(userId, "MEAL_ALMUERZO", TODAY)).thenReturn(1);

        schedulerWith(true, fixedClockAt(LocalTime.of(13, 0))).tick();

        verify(dispatchService)
                .sendToUser(
                        eq(userId),
                        argThat((PushPayload payload) -> payload.title().equals("¿Qué almorzaste?") && payload.url().equals("/agregar?meal=ALMUERZO")));
    }

    @Test
    void tick_claimLosesTheRace_neverDispatches() {
        UUID userId = UUID.randomUUID();
        ReminderSettingsData settings = ReminderSettingsData.defaults();
        stubEmptySignalsFor(userId, settings);
        DueReminderEvaluator.Due due = new DueReminderEvaluator.Due("WEIGH_IN", "t", "b", "/progreso");
        when(evaluator.evaluate(any(), eq(settings), any())).thenReturn(List.of(due));
        when(logRepository.claim(userId, "WEIGH_IN", TODAY)).thenReturn(0); // some other call already claimed it

        schedulerWith(true, fixedClockAt(LocalTime.of(8, 0))).tick();

        verify(dispatchService, never()).sendToUser(any(), any());
    }

    @Test
    void tick_evaluatorReturnsNothingDue_neverCallsClaimOrDispatch() {
        UUID userId = UUID.randomUUID();
        ReminderSettingsData settings = ReminderSettingsData.defaults();
        stubEmptySignalsFor(userId, settings);
        when(evaluator.evaluate(any(), eq(settings), any())).thenReturn(List.of());

        schedulerWith(true, fixedClockAt(LocalTime.of(3, 0))).tick();

        verifyNoInteractions(logRepository, dispatchService);
    }

    @Test
    void tick_oneUsersSignalGatheringThrows_stillProcessesTheRemainingUser() {
        UUID brokenUser = UUID.randomUUID();
        UUID healthyUser = UUID.randomUUID();
        ReminderSettingsData settings = ReminderSettingsData.defaults();
        when(settingsRepository.findAll())
                .thenReturn(List.of(new ReminderSettings(brokenUser, settings), new ReminderSettings(healthyUser, settings)));
        when(foodEntryRepository.findByUserIdAndEntryDateOrderByCreatedAtAsc(brokenUser, TODAY))
                .thenThrow(new RuntimeException("boom"));
        stubEmptySignalsExceptFindAllFor(healthyUser);
        DueReminderEvaluator.Due due = new DueReminderEvaluator.Due("WEIGH_IN", "t", "b", "/progreso");
        when(evaluator.evaluate(any(), eq(settings), any())).thenReturn(List.of(due));
        when(logRepository.claim(healthyUser, "WEIGH_IN", TODAY)).thenReturn(1);

        schedulerWith(true, fixedClockAt(LocalTime.of(8, 0))).tick();

        verify(dispatchService).sendToUser(eq(healthyUser), any());
    }

    private void stubEmptySignalsFor(UUID userId, ReminderSettingsData settings) {
        when(settingsRepository.findAll()).thenReturn(List.of(new ReminderSettings(userId, settings)));
        stubEmptySignalsExceptFindAllFor(userId);
    }

    /** Same as {@link #stubEmptySignalsFor} but leaves {@code settingsRepository.findAll()} alone, for tests that need to stub it themselves (e.g. with more than one user). */
    private void stubEmptySignalsExceptFindAllFor(UUID userId) {
        when(foodEntryRepository.findByUserIdAndEntryDateOrderByCreatedAtAsc(userId, TODAY)).thenReturn(List.of());
        when(waterLogRepository.findByUserIdAndEntryDate(userId, TODAY)).thenReturn(Optional.empty());
        when(weightEntryRepository.findByUserIdAndEntryDate(userId, TODAY)).thenReturn(Optional.empty());
        when(profileService.findByUserId(userId)).thenReturn(Optional.empty());
    }

    private ReminderScheduler schedulerWith(boolean enabled, Clock clock) {
        return new ReminderScheduler(
                enabled, clock, settingsRepository, logRepository, foodEntryRepository, waterLogRepository, weightEntryRepository,
                profileService, evaluator, dispatchService);
    }

    private Clock fixedClockAt(LocalTime time) {
        return Clock.fixed(ZonedDateTime.of(TODAY, time, ZONE).toInstant(), ZONE);
    }
}
