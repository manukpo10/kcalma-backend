package com.kcalma.checkin;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure domain service: the MacroFactor-style weekly adaptive-TDEE algorithm. No Spring, no I/O —
 * safe to unit test exhaustively, same philosophy as {@code NutritionCalculator}/{@code
 * WeightTrendCalculator}.
 *
 * <p>Pipeline, over a fixed {@value #WINDOW_DAYS}-day window ending yesterday (the caller resolves
 * the actual calendar dates — this class only ever sees pre-sliced data for that window):
 *
 * <ol>
 *   <li>Sufficiency gate: at least {@value #MIN_COMPLETE_DAYS} "complete" days (logged kcal &ge;
 *       {@value #COMPLETE_DAY_MIN_FRACTION_OF_TARGET} of that day's target — see {@link
 *       DailyIntake#isComplete}) AND at least {@value #MIN_WEIGH_INS} weigh-ins spanning at least
 *       {@value #MIN_WEIGH_IN_SPAN_DAYS} days inside the window. Either half failing -&gt; {@link
 *       CheckinStatus#INSUFFICIENT_DATA} with one Spanish reason per failed half, and nothing
 *       below is computed (every other {@link Result} field is {@code null}).
 *   <li>{@code avgIntakeKcal} = mean logged kcal of the complete days <b>only</b> — incomplete days
 *       are excluded entirely from the average, never treated as a zero-kcal day.
 *   <li>{@code trendChangeKg} = the EMA weight trend (see {@code WeightTrendCalculator}) at the
 *       window's end minus at its start. Both values are resolved by the caller: an accurate EMA
 *       needs the user's <em>full</em> weigh-in history (not just what's inside this window), so
 *       recomputing it here would require data this class deliberately doesn't take.
 *   <li>{@code estimatedTdee = avgIntakeKcal - trendChangeKg * 7700 / WINDOW_DAYS} — simple energy
 *       balance: eating {@code avgIntakeKcal} kcal/day while the trend moved {@code trendChangeKg}
 *       over the window implies a maintenance TDEE that many kcal/day higher (when losing weight)
 *       or lower (when gaining) than what was actually eaten.
 *   <li>{@code proposedTdee} = {@code currentTdee} nudged <b>halfway</b> towards {@code
 *       estimatedTdee}, clamped to a &plusmn;{@value #MAX_ADJUSTMENT_KCAL} kcal/day step and
 *       rounded to the nearest 10 kcal — a deliberately damped update so one noisy week can't
 *       swing the target far. {@code currentTdee} is resolved by the caller (the last accepted
 *       adaptive TDEE, or the formula TDEE if there is none yet — see {@code
 *       AdaptiveTdeeService}).
 * </ol>
 */
public final class TdeeAdaptationCalculator {

    public static final int WINDOW_DAYS = 21;
    public static final int MIN_COMPLETE_DAYS = 10;
    public static final int MIN_WEIGH_INS = 6;
    public static final int MIN_WEIGH_IN_SPAN_DAYS = 14;
    public static final double COMPLETE_DAY_MIN_FRACTION_OF_TARGET = 0.5;

    /** {@link Confidence#HIGH} bar — deliberately well above the {@link #MIN_COMPLETE_DAYS}/{@link #MIN_WEIGH_INS} gate. */
    static final int HIGH_COMPLETE_DAYS_FLOOR = 16;

    static final int HIGH_WEIGH_INS_FLOOR = 10;

    /** {@link Confidence#MEDIUM} floor — the midpoint between the minimum gate and the {@code HIGH} bar above. */
    static final int MEDIUM_COMPLETE_DAYS_FLOOR = 13;

    static final int MEDIUM_WEIGH_INS_FLOOR = 8;

    private static final double ADJUSTMENT_WEIGHT = 0.5;
    private static final double MAX_ADJUSTMENT_KCAL = 150.0;
    private static final double PROPOSAL_ROUNDING_STEP_KCAL = 10.0;

    /** Same energy/body-mass equivalence {@code NutritionCalculator} uses for the reverse computation. */
    private static final double KCAL_PER_KG_OF_BODY_MASS = 7700.0;

    private static final String REASON_INCOMPLETE_LOGGING =
            "Faltan días con registro completo de comidas: tenés %d de los %d necesarios en las últimas %d días.";
    private static final String REASON_TOO_FEW_WEIGH_INS = "Faltan registros de peso: al menos 2 por semana.";
    private static final String REASON_WEIGH_IN_SPAN_TOO_SHORT = "Los registros de peso deben abarcar al menos 2 semanas.";

    public Result evaluate(Input input) {
        Objects.requireNonNull(input, "input");

        int completeDays = (int) input.dailyIntakes().stream().filter(DailyIntake::isComplete).count();
        int weighIns = input.weighInDatesInWindow().size();
        long spanDays = weighInSpanDays(input.weighInDatesInWindow());

        List<String> reasons = new ArrayList<>();
        if (completeDays < MIN_COMPLETE_DAYS) {
            reasons.add(REASON_INCOMPLETE_LOGGING.formatted(completeDays, MIN_COMPLETE_DAYS, WINDOW_DAYS));
        }
        if (weighIns < MIN_WEIGH_INS) {
            reasons.add(REASON_TOO_FEW_WEIGH_INS);
        } else if (spanDays < MIN_WEIGH_IN_SPAN_DAYS) {
            reasons.add(REASON_WEIGH_IN_SPAN_TOO_SHORT);
        }
        if (!reasons.isEmpty()) {
            return Result.insufficient(completeDays, weighIns, reasons);
        }

        double avgIntakeKcal = input.dailyIntakes().stream()
                .filter(DailyIntake::isComplete)
                .mapToInt(DailyIntake::loggedKcal)
                .average()
                .orElseThrow();
        double trendChangeKg = input.trendAtWindowEndKg() - input.trendAtWindowStartKg();
        double estimatedTdee = avgIntakeKcal - trendChangeKg * KCAL_PER_KG_OF_BODY_MASS / WINDOW_DAYS;

        double rawAdjustment = (estimatedTdee - input.currentTdee()) * ADJUSTMENT_WEIGHT;
        double clampedAdjustment = clamp(rawAdjustment, -MAX_ADJUSTMENT_KCAL, MAX_ADJUSTMENT_KCAL);
        double proposedTdee = roundToStep(input.currentTdee() + clampedAdjustment, PROPOSAL_ROUNDING_STEP_KCAL);

        return new Result(
                CheckinStatus.PENDING,
                completeDays,
                weighIns,
                round(avgIntakeKcal),
                round2(trendChangeKg),
                round(estimatedTdee),
                round(proposedTdee),
                confidence(completeDays, weighIns),
                List.of());
    }

    private static Confidence confidence(int completeDays, int weighIns) {
        if (completeDays >= HIGH_COMPLETE_DAYS_FLOOR && weighIns >= HIGH_WEIGH_INS_FLOOR) {
            return Confidence.HIGH;
        }
        if (completeDays < MEDIUM_COMPLETE_DAYS_FLOOR || weighIns < MEDIUM_WEIGH_INS_FLOOR) {
            return Confidence.LOW;
        }
        return Confidence.MEDIUM;
    }

    /** 0 for zero or one weigh-in (nothing to span) — matches the caller's own count/span gate ordering (count checked first). */
    private static long weighInSpanDays(List<LocalDate> weighInDates) {
        if (weighInDates.size() < 2) {
            return 0;
        }
        LocalDate min = weighInDates.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate max = weighInDates.stream().max(LocalDate::compareTo).orElseThrow();
        return ChronoUnit.DAYS.between(min, max);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double roundToStep(double value, double step) {
        return Math.round(value / step) * step;
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** One day's logged-vs-target kcal inside the window; {@link #isComplete} is the sufficiency-gate predicate. */
    public record DailyIntake(LocalDate date, int loggedKcal, int targetKcal) {
        public boolean isComplete() {
            return loggedKcal >= targetKcal * COMPLETE_DAY_MIN_FRACTION_OF_TARGET;
        }
    }

    /**
     * @param dailyIntakes exactly one entry per calendar day in the window
     * @param weighInDatesInWindow distinct weigh-in dates inside the window — used only for the
     *     count/span sufficiency gate; the EMA trend values below need the user's full history, so
     *     they're resolved by the caller, never recomputed here
     * @param trendAtWindowStartKg the EMA trend value on the window's first day
     * @param trendAtWindowEndKg the EMA trend value on the window's last day
     * @param currentTdee the last accepted adaptive TDEE, or the formula TDEE if none
     */
    public record Input(
            List<DailyIntake> dailyIntakes,
            List<LocalDate> weighInDatesInWindow,
            double trendAtWindowStartKg,
            double trendAtWindowEndKg,
            double currentTdee) {}

    /**
     * {@code avgIntakeKcal}/{@code trendChangeKg}/{@code estimatedTdee}/{@code proposedTdee}/
     * {@code confidence} are {@code null} (and {@code reasons} non-empty) exactly when {@code
     * status == INSUFFICIENT_DATA}; {@code status} is otherwise always {@link
     * CheckinStatus#PENDING} — {@code ACCEPTED}/{@code DISMISSED} only ever come from the user
     * acting on a {@code PENDING} proposal (see {@code CheckinService}), never from this algorithm.
     */
    public record Result(
            CheckinStatus status,
            int completeDays,
            int weighIns,
            Integer avgIntakeKcal,
            Double trendChangeKg,
            Integer estimatedTdee,
            Integer proposedTdee,
            Confidence confidence,
            List<String> reasons) {

        static Result insufficient(int completeDays, int weighIns, List<String> reasons) {
            return new Result(
                    CheckinStatus.INSUFFICIENT_DATA, completeDays, weighIns, null, null, null, null, null, List.copyOf(reasons));
        }
    }
}
