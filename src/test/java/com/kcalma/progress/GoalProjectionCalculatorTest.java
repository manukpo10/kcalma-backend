package com.kcalma.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Pure unit tests for {@link GoalProjectionCalculator}. No Spring context. */
class GoalProjectionCalculatorTest {

    private final GoalProjectionCalculator calculator = new GoalProjectionCalculator();
    private final LocalDate today = LocalDate.of(2026, 9, 25);

    @Test
    void project_noRateYet_returnsEmpty() {
        // Covers both "no data" and "1 point" upstream: ProgressService only has a weeklyRateKg
        // once at least two weigh-ins exist, so null is exactly what it passes in those cases.
        assertThat(calculator.project(75.0, 70.0, null, today)).isEmpty();
    }

    @Test
    void project_alreadyAtGoal_returnsEmpty() {
        assertThat(calculator.project(70.05, 70.0, -0.5, today)).isEmpty();
    }

    @Test
    void project_exactlyAtGoal_returnsEmpty() {
        assertThat(calculator.project(70.0, 70.0, -0.5, today)).isEmpty();
    }

    @Test
    void project_movingAwayFromGoal_returnsEmpty() {
        // Goal is to lose (remaining = 70-75 = -5) but the rate is positive (gaining).
        assertThat(calculator.project(75.0, 70.0, 0.3, today)).isEmpty();
    }

    @Test
    void project_zeroRate_returnsEmpty() {
        assertThat(calculator.project(75.0, 70.0, 0.0, today)).isEmpty();
    }

    @Test
    void project_normalLossTrajectory_returnsDateAtExpectedDayCount() {
        // remaining = -5.0 kg, ratePerDay = -0.5/7 -> daysToGoal = 5.0 / (0.5/7) = 70 days
        Optional<LocalDate> result = calculator.project(75.0, 70.0, -0.5, today);

        assertThat(result).isPresent();
        assertThat(ChronoUnit.DAYS.between(today, result.get())).isEqualTo(70);
    }

    @Test
    void project_normalGainTrajectory_returnsDateAtExpectedDayCount() {
        // remaining = 66-60 = 6.0 kg, ratePerDay = 0.4/7 -> daysToGoal = 6.0 / (0.4/7) = 105 days
        Optional<LocalDate> result = calculator.project(60.0, 66.0, 0.4, today);

        assertThat(result).isPresent();
        assertThat(ChronoUnit.DAYS.between(today, result.get())).isEqualTo(105);
    }

    @Test
    void project_rateTooSmall_producesAnAbsurdlyFarDate_returnsEmpty() {
        // remaining = -5.0, ratePerDay = -0.02/7 -> daysToGoal = 5.0 / (0.02/7) = 1750 days (> 730 cap)
        assertThat(calculator.project(75.0, 70.0, -0.02, today)).isEmpty();
    }
}
