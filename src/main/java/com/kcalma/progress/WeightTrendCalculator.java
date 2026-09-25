package com.kcalma.progress;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure domain math: turns raw, possibly-irregular daily weigh-ins into a smoothed trend line.
 * No Spring, no I/O — safe to unit test exhaustively, same philosophy as {@code NutritionMath}.
 *
 * <p><b>Method: exponential moving average (EMA), the "Hacker's Diet" approach (John Walker),
 * with a default smoothing factor alpha = 0.1.</b> That alpha reacts over roughly one to two
 * weeks of weigh-ins, which absorbs day-to-day water-weight noise while still following a real
 * change in a couple of weeks — the same tradeoff a 7-day moving average makes, but EMA needs
 * no warm-up window of missing history and updates with every new point.
 *
 * <p>The trend is seeded at the first weigh-in's raw weight ({@code trend[0] = weight[0]}); every
 * later point updates it with {@code trend[i] = trend[i-1] + alpha * (weight[i] - trend[i-1])}.
 * Weigh-ins are not required to land on consecutive calendar days: the gap since the previous
 * point is compounded into the smoothing factor ({@code alphaEffective = 1 - (1 - alpha)^gapDays}),
 * so a trend that goes a week without a log catches up proportionally at the next weigh-in
 * instead of reacting as if the missed days never happened.
 */
public final class WeightTrendCalculator {

    public static final double DEFAULT_ALPHA = 0.1;

    private final double alpha;

    public WeightTrendCalculator() {
        this(DEFAULT_ALPHA);
    }

    public WeightTrendCalculator(double alpha) {
        this.alpha = alpha;
    }

    /**
     * @param weighIns raw weigh-ins, already sorted by date ascending; at most one per date
     * @return one trend point per input point, same order — {@code weighIns.get(i).date()} always
     *     equals {@code result.get(i).date()}
     */
    public List<Point> smooth(List<Point> weighIns) {
        if (weighIns.isEmpty()) {
            return List.of();
        }

        List<Point> result = new ArrayList<>(weighIns.size());
        Point first = weighIns.get(0);
        double trend = first.weightKg();
        result.add(new Point(first.date(), trend));

        for (int i = 1; i < weighIns.size(); i++) {
            Point previous = weighIns.get(i - 1);
            Point current = weighIns.get(i);
            long gapDays = Math.max(1, ChronoUnit.DAYS.between(previous.date(), current.date()));
            double alphaEffective = 1 - Math.pow(1 - alpha, gapDays);
            trend = trend + alphaEffective * (current.weightKg() - trend);
            result.add(new Point(current.date(), trend));
        }

        return result;
    }

    /** One weigh-in or trend value on a given date. */
    public record Point(LocalDate date, double weightKg) {}
}
