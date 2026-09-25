package com.kcalma.progress;

import com.kcalma.food.FoodEntry;
import com.kcalma.food.FoodEntryRepository;
import com.kcalma.food.NutritionMath;
import com.kcalma.food.dto.FoodEntryResponse;
import com.kcalma.profile.ProfileService;
import com.kcalma.profile.dto.NutritionTargetsResponse;
import com.kcalma.profile.dto.ProfileWithTargetsResponse;
import com.kcalma.progress.dto.ProgressResponse;
import com.kcalma.progress.dto.ProgressResponse.NutritionDay;
import com.kcalma.progress.dto.ProgressResponse.Stats;
import com.kcalma.progress.dto.ProgressResponse.WeightPoint;
import com.kcalma.weight.WeightEntry;
import com.kcalma.weight.WeightEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composes weight history, food history and the profile's current targets into the "Progreso"
 * dashboard payload (mirrors {@code DayService}'s role for the day screen).
 *
 * <p><b>Nutrition-target simplification:</b> every day in {@code nutrition.days} is compared
 * against TODAY's targets, not whatever the targets were on that historical day. The API has no
 * history of past profile/target changes, and re-deriving one would mean snapshotting targets on
 * every profile edit — out of scope for this feature. In practice targets rarely change, so this
 * is a reasonable simplification, but it does mean a recent goal/weight change will slightly
 * skew adherence for older days.
 */
@Service
public class ProgressService {

    /** Weekly rate window: prefer up to 28 days of trend, but need at least 14 to be meaningful. */
    private static final int RATE_WINDOW_MAX_DAYS = 28;

    private static final int RATE_WINDOW_MIN_DAYS = 14;

    /** A day counts as "adherent" when kcal lands within this fraction of the target. */
    private static final double ADHERENCE_TOLERANCE = 0.10;

    private final ProfileService profileService;
    private final WeightEntryRepository weightEntryRepository;
    private final FoodEntryRepository foodEntryRepository;
    private final WeightTrendCalculator trendCalculator = new WeightTrendCalculator();
    private final LinearRegression regression = new LinearRegression();
    private final GoalProjectionCalculator projectionCalculator = new GoalProjectionCalculator();

    public ProgressService(
            ProfileService profileService, WeightEntryRepository weightEntryRepository, FoodEntryRepository foodEntryRepository) {
        this.profileService = profileService;
        this.weightEntryRepository = weightEntryRepository;
        this.foodEntryRepository = foodEntryRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ProgressResponse> getProgress(UUID userId, ProgressRange range) {
        return profileService.findByUserId(userId).map(profile -> build(userId, range, LocalDate.now(), profile));
    }

    private ProgressResponse build(UUID userId, ProgressRange range, LocalDate today, ProfileWithTargetsResponse profileWithTargets) {
        List<WeightEntry> allWeights =
                weightEntryRepository.findByUserIdAndEntryDateLessThanEqualOrderByEntryDateAsc(userId, today);
        List<WeightTrendCalculator.Point> rawPoints = allWeights.stream()
                .map(w -> new WeightTrendCalculator.Point(w.getEntryDate(), w.getWeightKg().doubleValue()))
                .toList();
        List<WeightTrendCalculator.Point> trendSeries = trendCalculator.smooth(rawPoints);

        LocalDate from = range.from(today, earliestDataDate(userId, today, allWeights));
        if (from.isAfter(today)) {
            from = today;
        }

        List<WeightPoint> weightPoints = new ArrayList<>();
        Integer firstIndexInRange = null;
        for (int i = 0; i < allWeights.size(); i++) {
            LocalDate date = allWeights.get(i).getEntryDate();
            if (date.isBefore(from) || date.isAfter(today)) {
                continue;
            }
            if (firstIndexInRange == null) {
                firstIndexInRange = i;
            }
            weightPoints.add(new WeightPoint(date, allWeights.get(i).getWeightKg(), round2(trendSeries.get(i).weightKg())));
        }

        Double latestTrendKg = trendSeries.isEmpty() ? null : round2(trendSeries.get(trendSeries.size() - 1).weightKg());
        Double latestRawKg = allWeights.isEmpty() ? null : allWeights.get(allWeights.size() - 1).getWeightKg().doubleValue();
        Double startTrendKg = firstIndexInRange == null ? null : round2(trendSeries.get(firstIndexInRange).weightKg());
        Double changeKg = startTrendKg != null && latestTrendKg != null ? round2(latestTrendKg - startTrendKg) : null;
        Double weeklyRateKg = computeWeeklyRateKg(trendSeries);

        BigDecimal goalWeightKg = profileWithTargets.profile().goalWeightKg();
        LocalDate projectedGoalDate = latestTrendKg != null && goalWeightKg != null
                ? projectionCalculator.project(latestTrendKg, goalWeightKg.doubleValue(), weeklyRateKg, today).orElse(null)
                : null;
        Double progressPct = computeProgressPct(startTrendKg, latestTrendKg, goalWeightKg);

        Stats stats = new Stats(startTrendKg, latestRawKg, latestTrendKg, changeKg, weeklyRateKg, goalWeightKg, projectedGoalDate, progressPct);
        ProgressResponse.Nutrition nutrition = buildNutrition(userId, from, today, profileWithTargets.targets());

        return new ProgressResponse(range.queryValue(), from, today, weightPoints, stats, nutrition);
    }

    private LocalDate earliestDataDate(UUID userId, LocalDate today, List<WeightEntry> allWeights) {
        Optional<LocalDate> earliestWeight = allWeights.isEmpty() ? Optional.empty() : Optional.of(allWeights.get(0).getEntryDate());
        Optional<LocalDate> earliestFood =
                foodEntryRepository.findFirstByUserIdOrderByEntryDateAsc(userId).map(FoodEntry::getEntryDate);

        return Stream.of(earliestWeight, earliestFood).flatMap(Optional::stream).min(LocalDate::compareTo).orElse(today);
    }

    /** OLS slope of the trend's last 14-28 days, in kg/week; null when there isn't enough recent history. */
    private Double computeWeeklyRateKg(List<WeightTrendCalculator.Point> trendSeries) {
        if (trendSeries.size() < 2) {
            return null;
        }
        LocalDate lastDate = trendSeries.get(trendSeries.size() - 1).date();
        LocalDate windowStart = lastDate.minusDays(RATE_WINDOW_MAX_DAYS - 1L);
        List<WeightTrendCalculator.Point> window =
                trendSeries.stream().filter(p -> !p.date().isBefore(windowStart)).toList();
        if (window.size() < 2) {
            return null;
        }
        LocalDate windowFirstDate = window.get(0).date();
        long spanDays = ChronoUnit.DAYS.between(windowFirstDate, window.get(window.size() - 1).date());
        if (spanDays < RATE_WINDOW_MIN_DAYS) {
            return null;
        }
        List<LinearRegression.Point> regressionPoints = window.stream()
                .map(p -> new LinearRegression.Point(ChronoUnit.DAYS.between(windowFirstDate, p.date()), p.weightKg()))
                .toList();
        return round2(regression.slope(regressionPoints) * 7);
    }

    /** % of the way from startKg to goalWeightKg that currentKg has covered, clamped to [0, 100]. */
    private Double computeProgressPct(Double startKg, Double currentKg, BigDecimal goalWeightKg) {
        if (startKg == null || currentKg == null || goalWeightKg == null) {
            return null;
        }
        double goal = goalWeightKg.doubleValue();
        double denominator = startKg - goal;
        if (denominator == 0) {
            return 100.0;
        }
        double pct = (startKg - currentKg) / denominator * 100.0;
        return round2(Math.max(0.0, Math.min(100.0, pct)));
    }

    private ProgressResponse.Nutrition buildNutrition(UUID userId, LocalDate from, LocalDate to, NutritionTargetsResponse targets) {
        List<FoodEntryResponse> entries = foodEntryRepository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, from, to)
                .stream()
                .map(FoodEntryResponse::from)
                .toList();
        Map<LocalDate, List<FoodEntryResponse>> byDate = entries.stream().collect(Collectors.groupingBy(FoodEntryResponse::entryDate));

        List<NutritionDay> days = new ArrayList<>();
        long kcalSum = 0;
        long proteinSum = 0;
        int loggedCount = 0;
        int adherentCount = 0;

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<FoodEntryResponse> dayEntries = byDate.getOrDefault(date, List.of());
            boolean logged = !dayEntries.isEmpty();
            NutritionMath.Totals totals =
                    dayEntries.stream().map(FoodEntryResponse::totals).reduce(NutritionMath.Totals.ZERO, NutritionMath.Totals::plus);

            days.add(new NutritionDay(date, totals.kcal(), targets.calories(), totals.protein(), targets.proteinGrams(), logged));

            if (logged) {
                loggedCount++;
                kcalSum += totals.kcal();
                proteinSum += totals.protein();
                if (Math.abs(totals.kcal() - targets.calories()) <= ADHERENCE_TOLERANCE * targets.calories()) {
                    adherentCount++;
                }
            }
        }

        Integer avgKcal = loggedCount == 0 ? null : (int) Math.round((double) kcalSum / loggedCount);
        Integer avgProteinG = loggedCount == 0 ? null : (int) Math.round((double) proteinSum / loggedCount);
        Double adherencePct = loggedCount == 0 ? null : round2(adherentCount * 100.0 / loggedCount);
        int loggedStreakDays = computeLoggedStreakDays(byDate, from, to);

        return new ProgressResponse.Nutrition(days, avgKcal, avgProteinG, adherencePct, loggedStreakDays);
    }

    /** Consecutive logged days walking back from {@code to}, capped at {@code from}. */
    private int computeLoggedStreakDays(Map<LocalDate, List<FoodEntryResponse>> byDate, LocalDate from, LocalDate to) {
        int streak = 0;
        for (LocalDate date = to; !date.isBefore(from); date = date.minusDays(1)) {
            List<FoodEntryResponse> dayEntries = byDate.get(date);
            if (dayEntries == null || dayEntries.isEmpty()) {
                break;
            }
            streak++;
        }
        return streak;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
