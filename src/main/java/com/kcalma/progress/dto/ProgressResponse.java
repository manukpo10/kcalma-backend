package com.kcalma.progress.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Body for GET /api/progress: weight trend + range stats + nutrition history in one payload. */
public record ProgressResponse(
        String range, LocalDate from, LocalDate to, List<WeightPoint> weights, Stats stats, Nutrition nutrition) {

    public record WeightPoint(LocalDate date, BigDecimal weightKg, double trendKg) {}

    /**
     * {@code startKg}/{@code trendKg} are the smoothed trend (not the raw weigh-in) at the start
     * of the range and at the latest weigh-in, respectively — {@code changeKg} is their
     * difference, so a single noisy day never swings it. {@code currentKg} is the latest *raw*
     * weigh-in, shown for reference next to the trend. Any field is {@code null} when it can't be
     * computed yet (no weigh-ins, no weigh-in inside the selected range, no goal set, etc.).
     */
    public record Stats(
            Double startKg,
            Double currentKg,
            Double trendKg,
            Double changeKg,
            Double weeklyRateKg,
            BigDecimal goalWeightKg,
            LocalDate projectedGoalDate,
            Double progressPct) {}

    /**
     * {@code avgKcal}/{@code avgProteinG}/{@code adherencePct} are averaged over logged days only
     * — a day with no entries is excluded, not treated as zero intake, so a gap in logging never
     * masquerades as adherence data. All three are {@code null} when no day in range was logged.
     */
    public record Nutrition(
            List<NutritionDay> days, Integer avgKcal, Integer avgProteinG, Double adherencePct, int loggedStreakDays) {}

    /** targetKcal/targetProteinG are always today's current targets — see ProgressService for why. */
    public record NutritionDay(LocalDate date, int kcal, int targetKcal, int proteinG, int targetProteinG, boolean logged) {}
}
