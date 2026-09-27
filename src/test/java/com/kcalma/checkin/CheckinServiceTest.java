package com.kcalma.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kcalma.checkin.dto.CheckinResponse;
import com.kcalma.checkin.dto.CheckinWithTargetsResponse;
import com.kcalma.food.FoodEntry;
import com.kcalma.food.FoodEntryRepository;
import com.kcalma.food.FoodSource;
import com.kcalma.food.MealType;
import com.kcalma.profile.ActivityLevel;
import com.kcalma.profile.DietStyle;
import com.kcalma.profile.EnergySource;
import com.kcalma.profile.Goal;
import com.kcalma.profile.ProfileService;
import com.kcalma.profile.ProteinBasis;
import com.kcalma.profile.Sex;
import com.kcalma.profile.dto.NutritionTargetsResponse;
import com.kcalma.profile.dto.ProfileResponse;
import com.kcalma.profile.dto.ProfileWithTargetsResponse;
import com.kcalma.weight.WeightEntry;
import com.kcalma.weight.WeightEntryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for {@link CheckinService}. No Spring context — every repository/service dependency
 * is mocked, exactly the way {@code WeightEntryServiceTest} tests {@code WeightEntryService}.
 *
 * <p>The profile in every test (male, 30y, 180cm, 80kg, MODERATELY_ACTIVE, MAINTAIN) is the same
 * baseline {@code NutritionCalculatorTest} uses: BMR 1780 x 1.55 = formula TDEE 2759, and MAINTAIN
 * applies no adjustment, so target calories track whatever TDEE is active exactly.
 */
@ExtendWith(MockitoExtension.class)
class CheckinServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final int FORMULA_TDEE = 2759;

    // "Now" = Sunday 2026-09-27 -- the ISO week it belongs to starts Monday 2026-09-21.
    private static final Clock CLOCK =
            Clock.fixed(argentinaInstant(2026, 9, 27, 12, 0), ZoneId.of("America/Argentina/Buenos_Aires"));

    @Mock
    private ProfileService profileService;

    @Mock
    private AdaptiveTdeeService adaptiveTdeeService;

    @Mock
    private FoodEntryRepository foodEntryRepository;

    @Mock
    private WeightEntryRepository weightEntryRepository;

    @Mock
    private TdeeCheckinRepository checkinRepository;

    @Test
    void getCurrentWeek_noProfileYet_isEmpty() {
        CheckinService service = newService(CLOCK);
        when(profileService.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.getCurrentWeek(USER_ID)).isEmpty();
        verifyNoInteractions(checkinRepository);
    }

    @Test
    void getCurrentWeek_weekStartFollowsTheZonedClockNotUtc() {
        // Same real-world instant, two zones: Argentina's calendar day is still Sunday 9/27 (this
        // week starts Monday 9/21); UTC's is already Monday 9/28 (a NEW week, starting on itself).
        Clock argentinaClock = Clock.fixed(argentinaInstant(2026, 9, 27, 23, 30), ZoneId.of("America/Argentina/Buenos_Aires"));
        Clock utcClock = Clock.fixed(argentinaInstant(2026, 9, 27, 23, 30), ZoneId.of("UTC"));
        stubNoProfileDataForAnyWindow();

        LocalDate weekStartViaArgentina = newService(argentinaClock).getCurrentWeek(USER_ID).orElseThrow().weekStart();
        LocalDate weekStartViaUtc = newService(utcClock).getCurrentWeek(USER_ID).orElseThrow().weekStart();

        assertThat(weekStartViaArgentina).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(weekStartViaUtc).isEqualTo(LocalDate.of(2026, 9, 28));
    }

    @Test
    void getCurrentWeek_notEnoughDataYet_returnsInsufficientDataWithReasonsAndNullNumbers() {
        stubNoProfileDataForAnyWindow();

        CheckinResponse response = newService(CLOCK).getCurrentWeek(USER_ID).orElseThrow();

        assertThat(response.status()).isEqualTo(CheckinStatus.INSUFFICIENT_DATA);
        assertThat(response.weekStart()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(response.windowEnd()).isEqualTo(LocalDate.of(2026, 9, 26)); // yesterday
        assertThat(response.windowStart()).isEqualTo(LocalDate.of(2026, 9, 6)); // 21 days back from windowEnd
        assertThat(response.reasons()).isNotEmpty();
        assertThat(response.completeDays()).isNull();
        assertThat(response.estimatedTdee()).isNull();
        assertThat(response.proposedTdee()).isNull();
    }

    @Test
    void getCurrentWeek_alreadyClosedWeek_returnsTheFrozenRowWithoutTouchingFoodOrWeightData() {
        TdeeCheckin closed = new TdeeCheckin(USER_ID, LocalDate.of(2026, 9, 21));
        closed.recompute(
                LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 20), FORMULA_TDEE,
                new TdeeAdaptationCalculator.Result(
                        CheckinStatus.PENDING, 15, 8, 2600, -0.3, 2700, 2780, Confidence.MEDIUM, List.of()));
        closed.accept(ActivityLevel.MODERATELY_ACTIVE, OffsetDateTime.now(CLOCK));
        when(checkinRepository.findByUserIdAndWeekStart(USER_ID, LocalDate.of(2026, 9, 21))).thenReturn(Optional.of(closed));
        when(profileService.findByUserId(USER_ID)).thenReturn(Optional.of(profileWithDummyTargets()));
        when(adaptiveTdeeService.findActive(USER_ID, ActivityLevel.MODERATELY_ACTIVE)).thenReturn(Optional.empty());

        CheckinResponse response = newService(CLOCK).getCurrentWeek(USER_ID).orElseThrow();

        assertThat(response.status()).isEqualTo(CheckinStatus.ACCEPTED);
        assertThat(response.proposedTdee()).isEqualTo(2780); // straight from the frozen row
        assertThat(response.estimatedTdee()).isEqualTo(2700);
        verifyNoInteractions(foodEntryRepository, weightEntryRepository);
        verify(checkinRepository, never()).save(any());
    }

    @Test
    void accept_pendingWeek_closesItAndAppliesTheProposedTdee() {
        stubSufficientDataForANewPendingWeek();
        ArgumentCaptor<TdeeCheckin> savedCaptor = ArgumentCaptor.forClass(TdeeCheckin.class);
        when(checkinRepository.save(savedCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        CheckinWithTargetsResponse response = newService(CLOCK).accept(USER_ID);

        TdeeCheckin saved = savedCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(CheckinStatus.ACCEPTED);
        assertThat(saved.getAppliedTdee()).isEqualTo(saved.getProposedTdee());
        assertThat(saved.getAppliedActivityLevel()).isEqualTo(ActivityLevel.MODERATELY_ACTIVE);
        assertThat(saved.getDecidedAt()).isNotNull();
        assertThat(response.checkin().status()).isEqualTo(CheckinStatus.ACCEPTED);
        assertThat(response.targets()).isSameAs(DUMMY_TARGETS);
    }

    @Test
    void accept_weekAlreadyClosed_throwsBadRequestAndNeverSavesAgain() {
        TdeeCheckin dismissed = new TdeeCheckin(USER_ID, LocalDate.of(2026, 9, 21));
        dismissed.recompute(
                LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 20), FORMULA_TDEE,
                new TdeeAdaptationCalculator.Result(
                        CheckinStatus.PENDING, 15, 8, 2600, -0.3, 2700, 2780, Confidence.MEDIUM, List.of()));
        dismissed.dismiss(OffsetDateTime.now(CLOCK));
        when(checkinRepository.findByUserIdAndWeekStart(USER_ID, LocalDate.of(2026, 9, 21))).thenReturn(Optional.of(dismissed));
        when(profileService.findByUserId(USER_ID)).thenReturn(Optional.of(profileWithDummyTargets()));
        when(adaptiveTdeeService.findActive(USER_ID, ActivityLevel.MODERATELY_ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService(CLOCK).accept(USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("pendiente");
        verify(checkinRepository, never()).save(any());
    }

    @Test
    void dismiss_pendingWeek_closesItWithoutApplyingAnything() {
        stubSufficientDataForANewPendingWeek();
        ArgumentCaptor<TdeeCheckin> savedCaptor = ArgumentCaptor.forClass(TdeeCheckin.class);
        when(checkinRepository.save(savedCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        CheckinResponse response = newService(CLOCK).dismiss(USER_ID);

        TdeeCheckin saved = savedCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(CheckinStatus.DISMISSED);
        assertThat(saved.getAppliedTdee()).isNull();
        assertThat(saved.getDecidedAt()).isNotNull();
        assertThat(response.status()).isEqualTo(CheckinStatus.DISMISSED);
    }

    @Test
    void history_excludesTheCurrentWeekAndRespectsTheLimit() {
        TdeeCheckin week1 = new TdeeCheckin(USER_ID, LocalDate.of(2026, 9, 7));
        TdeeCheckin week2 = new TdeeCheckin(USER_ID, LocalDate.of(2026, 9, 14));
        when(checkinRepository.findByUserIdAndWeekStartLessThanOrderByWeekStartDesc(USER_ID, LocalDate.of(2026, 9, 21)))
                .thenReturn(List.of(week2, week1)); // most recent first, as the repository method promises

        var history = newService(CLOCK).history(USER_ID, 1);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).weekStart()).isEqualTo(LocalDate.of(2026, 9, 14));
        verify(checkinRepository).findByUserIdAndWeekStartLessThanOrderByWeekStartDesc(USER_ID, LocalDate.of(2026, 9, 21));
    }

    // --- fixtures ---

    private static final NutritionTargetsResponse DUMMY_TARGETS = new NutritionTargetsResponse(
            FORMULA_TDEE, false, 128, 92, 355, 39, 69, 2000, 2800, 0.0, 0.0, ProteinBasis.BODY_WEIGHT, 80.0, null, List.of(),
            EnergySource.FORMULA, null);

    private CheckinService newService(Clock clock) {
        return new CheckinService(profileService, adaptiveTdeeService, foodEntryRepository, weightEntryRepository, checkinRepository, clock);
    }

    private ProfileWithTargetsResponse profileWithDummyTargets() {
        return new ProfileWithTargetsResponse(profileResponse(), DUMMY_TARGETS);
    }

    private ProfileResponse profileResponse() {
        OffsetDateTime now = OffsetDateTime.now(CLOCK);
        return new ProfileResponse(
                USER_ID, Sex.MALE, LocalDate.of(1996, 9, 25), 180, new BigDecimal("80.00"), ActivityLevel.MODERATELY_ACTIVE,
                Goal.MAINTAIN, null, null, DietStyle.BALANCED, List.of(), false, null, null, now, now);
    }

    /** No food/weight data anywhere -- always resolves to INSUFFICIENT_DATA, regardless of exactly which window the clock computes. */
    private void stubNoProfileDataForAnyWindow() {
        when(profileService.findByUserId(USER_ID)).thenReturn(Optional.of(profileWithDummyTargets()));
        when(adaptiveTdeeService.findActive(eq(USER_ID), any())).thenReturn(Optional.empty());
        when(foodEntryRepository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(weightEntryRepository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(weightEntryRepository.findByUserIdAndEntryDateLessThanEqualOrderByEntryDateAsc(eq(USER_ID), any())).thenReturn(List.of());
        when(checkinRepository.findByUserIdAndWeekStart(eq(USER_ID), any())).thenReturn(Optional.empty());
        when(checkinRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /**
     * Window is fixed by {@link #CLOCK}: windowEnd = 2026-09-26 (yesterday), windowStart =
     * 2026-09-06. 10 complete days (2800 kcal >= 50% of the 2759 formula target) and 6 weigh-ins
     * spanning 15 days -- comfortably past both sufficiency gates, so the week resolves PENDING.
     */
    private void stubSufficientDataForANewPendingWeek() {
        when(profileService.findByUserId(USER_ID)).thenReturn(Optional.of(profileWithDummyTargets()));
        when(adaptiveTdeeService.findActive(USER_ID, ActivityLevel.MODERATELY_ACTIVE)).thenReturn(Optional.empty());
        when(checkinRepository.findByUserIdAndWeekStart(USER_ID, LocalDate.of(2026, 9, 21))).thenReturn(Optional.empty());

        List<FoodEntry> entries = new ArrayList<>();
        LocalDate windowStart = LocalDate.of(2026, 9, 6);
        for (int i = 0; i < 10; i++) {
            entries.add(entryWithKcal(windowStart.plusDays(i), 2800));
        }
        when(foodEntryRepository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(USER_ID, windowStart, LocalDate.of(2026, 9, 26)))
                .thenReturn(entries);

        List<WeightEntry> weighIns = List.of(
                new WeightEntry(USER_ID, windowStart, new BigDecimal("80.00")),
                new WeightEntry(USER_ID, windowStart.plusDays(3), new BigDecimal("80.00")),
                new WeightEntry(USER_ID, windowStart.plusDays(6), new BigDecimal("80.00")),
                new WeightEntry(USER_ID, windowStart.plusDays(9), new BigDecimal("80.00")),
                new WeightEntry(USER_ID, windowStart.plusDays(12), new BigDecimal("80.00")),
                new WeightEntry(USER_ID, windowStart.plusDays(15), new BigDecimal("80.00")));
        when(weightEntryRepository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(USER_ID, windowStart, LocalDate.of(2026, 9, 26)))
                .thenReturn(weighIns);
        when(weightEntryRepository.findByUserIdAndEntryDateLessThanEqualOrderByEntryDateAsc(USER_ID, LocalDate.of(2026, 9, 26)))
                .thenReturn(weighIns);
    }

    private static FoodEntry entryWithKcal(LocalDate date, int kcal) {
        return new FoodEntry(
                USER_ID, date, MealType.ALMUERZO, "Test", new BigDecimal("100.00"), new BigDecimal(kcal),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                FoodSource.MANUAL, null, null);
    }

    private static java.time.Instant argentinaInstant(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.of("America/Argentina/Buenos_Aires")).toInstant();
    }
}
