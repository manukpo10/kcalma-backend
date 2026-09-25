package com.kcalma.progress;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Pure domain math: projects a goal-weight completion date from the current trend weight and a
 * recent weekly rate of change. No Spring, no I/O — safe to unit test exhaustively.
 *
 * <p>Deliberately conservative — returns {@link Optional#empty()} (meaning: "don't show a date")
 * rather than a misleading one whenever:
 * <ul>
 *   <li>the trend is already within {@value #AT_GOAL_THRESHOLD_KG} kg of the goal ("already there");
 *   <li>there is no reliable weekly rate yet (fewer than two recent weigh-ins);
 *   <li>the rate moves <em>away</em> from the goal (e.g. still gaining while the goal is to lose);
 *   <li>the projected date is further out than {@value #MAX_PROJECTION_DAYS} days — an "absurd"
 *       date that a near-zero rate would otherwise produce.
 * </ul>
 */
public final class GoalProjectionCalculator {

    static final double AT_GOAL_THRESHOLD_KG = 0.1;
    static final long MAX_PROJECTION_DAYS = 730;

    /**
     * @param currentTrendKg the latest smoothed trend weight
     * @param goalWeightKg the target weight
     * @param weeklyRateKg the recent trend's rate of change in kg/week, or {@code null} if not
     *     enough history exists to compute one
     * @param today the date to project forward from
     */
    public Optional<LocalDate> project(double currentTrendKg, double goalWeightKg, Double weeklyRateKg, LocalDate today) {
        double remainingKg = goalWeightKg - currentTrendKg;
        if (Math.abs(remainingKg) <= AT_GOAL_THRESHOLD_KG) {
            return Optional.empty();
        }
        if (weeklyRateKg == null || weeklyRateKg == 0) {
            return Optional.empty();
        }
        if (Math.signum(remainingKg) != Math.signum(weeklyRateKg)) {
            return Optional.empty(); // moving away from the goal
        }

        double ratePerDay = weeklyRateKg / 7.0;
        double daysToGoal = remainingKg / ratePerDay; // same sign on both sides -> positive

        if (!Double.isFinite(daysToGoal) || daysToGoal <= 0 || daysToGoal > MAX_PROJECTION_DAYS) {
            return Optional.empty();
        }

        return Optional.of(today.plusDays((long) Math.ceil(daysToGoal)));
    }
}
