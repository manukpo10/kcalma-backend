package com.kcalma.checkin;

import static org.assertj.core.api.Assertions.assertThat;

import com.kcalma.checkin.TdeeAdaptationCalculator.DailyIntake;
import com.kcalma.checkin.TdeeAdaptationCalculator.Input;
import com.kcalma.checkin.TdeeAdaptationCalculator.Result;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link TdeeAdaptationCalculator}. No Spring context.
 *
 * <p>{@link #WINDOW_START} / {@link #WINDOW_END} span exactly {@value
 * TdeeAdaptationCalculator#WINDOW_DAYS} days (Sep 5..Sep 25 inclusive), matching how {@code
 * CheckinService} always slices the window in production.
 */
class TdeeAdaptationCalculatorTest {

    private static final LocalDate WINDOW_START = LocalDate.of(2026, 9, 5);
    private static final LocalDate WINDOW_END = LocalDate.of(2026, 9, 25);

    private final TdeeAdaptationCalculator calculator = new TdeeAdaptationCalculator();

    @Test
    void evaluate_fewerThanTenCompleteDays_returnsInsufficientDataWithReason() {
        // 7 complete (2000/2000) + 14 incomplete (500/2000, well under the 50% threshold).
        List<DailyIntake> days = constantDays(7, 2000, 500, 2000);
        List<LocalDate> weighIns = every(WINDOW_START, 8, 2); // 8 points, span 14 -- this gate is fine.

        Result result = calculator.evaluate(new Input(days, weighIns, 80.0, 80.0, 2500));

        assertThat(result.status()).isEqualTo(CheckinStatus.INSUFFICIENT_DATA);
        assertThat(result.completeDays()).isEqualTo(7);
        assertThat(result.weighIns()).isEqualTo(8);
        assertThat(result.reasons()).containsExactly("Faltan días con registro completo de comidas: tenés 7 de los 10 necesarios en las últimas 21 días.");
        assertThat(result.avgIntakeKcal()).isNull();
        assertThat(result.trendChangeKg()).isNull();
        assertThat(result.estimatedTdee()).isNull();
        assertThat(result.proposedTdee()).isNull();
        assertThat(result.confidence()).isNull();
    }

    @Test
    void evaluate_fewerThanSixWeighIns_returnsInsufficientDataWithExactReasonText() {
        List<DailyIntake> days = constantDays(15, 2000, 500, 2000); // complete-days gate is fine.
        List<LocalDate> weighIns = every(WINDOW_START, 4, 2); // only 4 weigh-ins.

        Result result = calculator.evaluate(new Input(days, weighIns, 80.0, 80.0, 2500));

        assertThat(result.status()).isEqualTo(CheckinStatus.INSUFFICIENT_DATA);
        assertThat(result.weighIns()).isEqualTo(4);
        assertThat(result.reasons()).containsExactly("Faltan registros de peso: al menos 2 por semana.");
    }

    @Test
    void evaluate_sixWeighInsButSpanUnderFourteenDays_returnsInsufficientDataWithSpanReason() {
        List<DailyIntake> days = constantDays(15, 2000, 500, 2000);
        // 6 weigh-ins, but all within a 5-day span -- count is fine, span is not.
        List<LocalDate> weighIns = every(WINDOW_START, 6, 1);

        Result result = calculator.evaluate(new Input(days, weighIns, 80.0, 80.0, 2500));

        assertThat(result.status()).isEqualTo(CheckinStatus.INSUFFICIENT_DATA);
        assertThat(result.reasons()).containsExactly("Los registros de peso deben abarcar al menos 2 semanas.");
    }

    @Test
    void evaluate_exactlyAtTheSufficiencyThresholds_isPendingNotInsufficient() {
        // Exactly 10 complete days, exactly 6 weigh-ins spanning exactly 14 days -- inclusive (>=) boundaries.
        List<DailyIntake> days = constantDays(10, 2000, 500, 2000);
        List<LocalDate> weighIns = List.of(
                WINDOW_START, WINDOW_START.plusDays(3), WINDOW_START.plusDays(6),
                WINDOW_START.plusDays(9), WINDOW_START.plusDays(12), WINDOW_START.plusDays(14));

        Result result = calculator.evaluate(new Input(days, weighIns, 80.0, 80.0, 2500));

        assertThat(result.status()).isEqualTo(CheckinStatus.PENDING);
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void evaluate_incompleteDaysAreExcludedFromTheAverageNotCountedAsZero() {
        // 10 complete days at 2000 kcal, 11 incomplete days at 100 kcal (well under 50% of 2000).
        // Including the incomplete days would drag the average down to ~1005 -- excluding them
        // (the correct behavior) keeps it at exactly 2000.
        List<DailyIntake> days = constantDays(10, 2000, 100, 2000);
        List<LocalDate> weighIns = every(WINDOW_START, 6, 3); // 6 points, span 15 -- sufficient.

        Result result = calculator.evaluate(new Input(days, weighIns, 80.0, 80.0, 2000));

        assertThat(result.status()).isEqualTo(CheckinStatus.PENDING);
        assertThat(result.avgIntakeKcal()).isEqualTo(2000);
        // trend is flat (80.0 -> 80.0) here, so estimatedTdee collapses to avgIntakeKcal exactly.
        assertThat(result.estimatedTdee()).isEqualTo(2000);
    }

    @Test
    void evaluate_workedExample_matchesTheSprintSpecNumbers() {
        // The sprint's own worked example: 21 days, avg intake 2100 kcal, trend -0.9 kg, formula
        // (== current, no prior accepted week) TDEE 2770.
        // estimatedTdee = 2100 - (-0.9 * 7700 / 21) = 2100 + 330 = 2430.
        // rawAdjustment = (2430 - 2770) * 0.5 = -170 -> clamped to -150 (exceeds the cap).
        // proposedTdee = 2770 - 150 = 2620 (already a multiple of 10).
        List<DailyIntake> days = constantDays(21, 2100, 0, 2100);
        List<LocalDate> weighIns = every(WINDOW_START, 10, 2); // 10 points, span 18 -- comfortably sufficient.

        Result result = calculator.evaluate(new Input(days, weighIns, 81.9, 81.0, 2770));

        assertThat(result.status()).isEqualTo(CheckinStatus.PENDING);
        assertThat(result.avgIntakeKcal()).isEqualTo(2100);
        assertThat(result.trendChangeKg()).isEqualTo(-0.9);
        assertThat(result.estimatedTdee()).isEqualTo(2430);
        assertThat(result.proposedTdee()).isEqualTo(2620); // the -150 floor was exercised.
    }

    @Test
    void evaluate_clampsThePositiveAdjustmentAtPlus150() {
        // estimatedTdee ends up 2486.67 (rounds to 2487); raw adjustment vs. currentTdee=2000 would
        // be +243.5 -- clamped to +150 -- proposedTdee = 2150 (already a multiple of 10).
        List<DailyIntake> days = constantDays(21, 3000, 0, 3000);
        List<LocalDate> weighIns = every(WINDOW_START, 10, 2);

        Result result = calculator.evaluate(new Input(days, weighIns, 79.6, 81.0, 2000)); // +1.4 kg over the window

        assertThat(result.estimatedTdee()).isEqualTo(2487);
        assertThat(result.proposedTdee()).isEqualTo(2150);
    }

    @Test
    void evaluate_roundsProposedTdeeUpToTheNearestTen() {
        // estimatedTdee = avgIntake = 2114 (flat trend); adjustment vs currentTdee=2000 is
        // (2114-2000)*0.5 = 57 (unclamped) -> raw proposedTdee 2057 -> rounds UP to 2060.
        List<DailyIntake> days = constantDays(21, 2114, 0, 2114);
        List<LocalDate> weighIns = every(WINDOW_START, 10, 2);

        Result result = calculator.evaluate(new Input(days, weighIns, 80.0, 80.0, 2000));

        assertThat(result.proposedTdee()).isEqualTo(2060);
    }

    @Test
    void evaluate_roundsProposedTdeeDownToTheNearestTen() {
        // estimatedTdee = avgIntake = 2086 (flat trend); adjustment vs currentTdee=2000 is
        // (2086-2000)*0.5 = 43 (unclamped) -> raw proposedTdee 2043 -> rounds DOWN to 2040.
        List<DailyIntake> days = constantDays(21, 2086, 0, 2086);
        List<LocalDate> weighIns = every(WINDOW_START, 10, 2);

        Result result = calculator.evaluate(new Input(days, weighIns, 80.0, 80.0, 2000));

        assertThat(result.proposedTdee()).isEqualTo(2040);
    }

    @Test
    void evaluate_confidenceHigh_atOrAboveBothHighThresholds() {
        List<DailyIntake> days = constantDays(16, 2100, 0, 2100);
        List<LocalDate> weighIns = every(WINDOW_START, 10, 2);

        Result result = calculator.evaluate(new Input(days, weighIns, 80.0, 80.0, 2500));

        assertThat(result.confidence()).isEqualTo(Confidence.HIGH);
    }

    @Test
    void evaluate_confidenceMedium_betweenTheMinimumGateAndTheHighBar() {
        List<DailyIntake> days = constantDays(13, 2100, 0, 2100);
        List<LocalDate> weighIns = every(WINDOW_START, 8, 2);

        Result result = calculator.evaluate(new Input(days, weighIns, 80.0, 80.0, 2500));

        assertThat(result.confidence()).isEqualTo(Confidence.MEDIUM);
    }

    @Test
    void evaluate_confidenceLow_rightAtTheMinimumSufficiencyGate() {
        List<DailyIntake> days = constantDays(10, 2100, 500, 2100);
        List<LocalDate> weighIns = List.of(
                WINDOW_START, WINDOW_START.plusDays(3), WINDOW_START.plusDays(6),
                WINDOW_START.plusDays(9), WINDOW_START.plusDays(12), WINDOW_START.plusDays(14));

        Result result = calculator.evaluate(new Input(days, weighIns, 80.0, 80.0, 2500));

        assertThat(result.confidence()).isEqualTo(Confidence.LOW);
    }

    @Test
    void evaluate_confidenceLow_whenWeighInsAreLowEvenIfCompleteDaysAreHigh() {
        // Confidence is the AND of both HIGH floors and the OR of both LOW floors -- 20 complete
        // days alone can't rescue a LOW confidence driven by too few weigh-ins (7 < the 8 floor).
        List<DailyIntake> days = constantDays(20, 2100, 0, 2100);
        List<LocalDate> weighIns = every(WINDOW_START, 7, 3); // 7 points (>= 6), span 18 (>= 14) -- the gate is fine, only the count is low.

        Result result = calculator.evaluate(new Input(days, weighIns, 80.0, 80.0, 2500));

        assertThat(result.confidence()).isEqualTo(Confidence.LOW);
    }

    /** {@code completeCount} days at {@code completeKcal}/{@code targetKcal}, then the rest of the 21-day window at {@code incompleteKcal}. */
    private static List<DailyIntake> constantDays(int completeCount, int completeKcal, int incompleteKcal, int targetKcal) {
        List<DailyIntake> days = new ArrayList<>();
        for (int i = 0; i < TdeeAdaptationCalculator.WINDOW_DAYS; i++) {
            int kcal = i < completeCount ? completeKcal : incompleteKcal;
            days.add(new DailyIntake(WINDOW_START.plusDays(i), kcal, targetKcal));
        }
        return days;
    }

    private static List<LocalDate> every(LocalDate start, int count, int stepDays) {
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            dates.add(start.plusDays((long) i * stepDays));
        }
        return dates;
    }
}
