package com.kcalma.checkin;

import com.kcalma.checkin.dto.CheckinHistoryEntryResponse;
import com.kcalma.checkin.dto.CheckinResponse;
import com.kcalma.checkin.dto.CheckinWithTargetsResponse;
import com.kcalma.food.FoodEntry;
import com.kcalma.food.FoodEntryRepository;
import com.kcalma.food.NutritionMath;
import com.kcalma.food.dto.FoodEntryResponse;
import com.kcalma.profile.NutritionCalculator;
import com.kcalma.profile.ProfileService;
import com.kcalma.profile.dto.NutritionTargetsResponse;
import com.kcalma.profile.dto.ProfileResponse;
import com.kcalma.progress.WeightTrendCalculator;
import com.kcalma.weight.WeightEntry;
import com.kcalma.weight.WeightEntryRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Orchestrates the weekly adaptive-TDEE check-in: resolves the current ISO week's row (creating or
 * recomputing it against the latest 21-day window — see {@link TdeeAdaptationCalculator} — while
 * it's still open), and handles accepting/dismissing it. Cross-feature reads go through {@code
 * ProfileService} (for the profile's own fields) and {@link AdaptiveTdeeService} (for whether
 * adaptive TDEE is currently active) rather than their repositories directly.
 */
@Service
public class CheckinService {

    private final ProfileService profileService;
    private final AdaptiveTdeeService adaptiveTdeeService;
    private final FoodEntryRepository foodEntryRepository;
    private final WeightEntryRepository weightEntryRepository;
    private final TdeeCheckinRepository checkinRepository;
    private final Clock clock;
    private final NutritionCalculator calculator = new NutritionCalculator();
    private final WeightTrendCalculator trendCalculator = new WeightTrendCalculator();
    private final TdeeAdaptationCalculator adaptationCalculator = new TdeeAdaptationCalculator();

    public CheckinService(
            ProfileService profileService,
            AdaptiveTdeeService adaptiveTdeeService,
            FoodEntryRepository foodEntryRepository,
            WeightEntryRepository weightEntryRepository,
            TdeeCheckinRepository checkinRepository,
            Clock clock) {
        this.profileService = profileService;
        this.adaptiveTdeeService = adaptiveTdeeService;
        this.foodEntryRepository = foodEntryRepository;
        this.weightEntryRepository = weightEntryRepository;
        this.checkinRepository = checkinRepository;
        this.clock = clock;
    }

    /**
     * A GET that can write: while the current week is still open (PENDING/INSUFFICIENT_DATA), this
     * recomputes and upserts it against the latest data every time it's called — the "MacroFactor
     * style" live-updating check-in card. The write is idempotent in effect (same underlying data
     * in -&gt; same recomputed row out) and has no side effects beyond that one row, so a
     * side-effecting GET is an acceptable, deliberate tradeoff here rather than adding a
     * client-triggered "refresh" endpoint nobody asked for.
     */
    @Transactional
    public Optional<CheckinResponse> getCurrentWeek(UUID userId) {
        Optional<ProfileResponse> profile = profile(userId);
        if (profile.isEmpty()) {
            return Optional.empty();
        }
        LiveTargets live = resolveLiveTargets(userId, profile.get());
        WeekState state = currentWeek(userId, live);
        return Optional.of(toResponse(state, live));
    }

    @Transactional
    public CheckinWithTargetsResponse accept(UUID userId) {
        ProfileResponse profile = requireProfile(userId);
        LiveTargets live = resolveLiveTargets(userId, profile);
        WeekState state = currentWeek(userId, live);
        TdeeCheckin checkin = state.checkin();
        if (!checkin.isPending()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay una propuesta pendiente para aceptar esta semana.");
        }

        checkin.accept(profile.activityLevel(), OffsetDateTime.now(clock));
        checkinRepository.save(checkin);

        // Re-resolved AFTER saving the acceptance: AdaptiveTdeeService now picks this week up, and
        // going through ProfileService's own path keeps this response's targets identical to what
        // GET /api/profile would show right after this call -- one source of truth, not two.
        NutritionTargetsResponse targets = profileService.findByUserId(userId).orElseThrow().targets();
        LiveTargets liveAfterAccept = resolveLiveTargets(userId, profile);
        return new CheckinWithTargetsResponse(toResponse(new WeekState(checkin, List.of()), liveAfterAccept), targets);
    }

    @Transactional
    public CheckinResponse dismiss(UUID userId) {
        ProfileResponse profile = requireProfile(userId);
        LiveTargets live = resolveLiveTargets(userId, profile);
        WeekState state = currentWeek(userId, live);
        TdeeCheckin checkin = state.checkin();
        if (!checkin.isPending()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay una propuesta pendiente para descartar esta semana.");
        }

        checkin.dismiss(OffsetDateTime.now(clock));
        checkinRepository.save(checkin);
        return toResponse(new WeekState(checkin, List.of()), live);
    }

    /** Strictly-past weeks only (the current one is GET /api/checkin's job) — most recent first, capped to a sane range. */
    @Transactional(readOnly = true)
    public List<CheckinHistoryEntryResponse> history(UUID userId, int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 52));
        return checkinRepository.findByUserIdAndWeekStartLessThanOrderByWeekStartDesc(userId, currentWeekStart()).stream()
                .limit(cappedLimit)
                .map(CheckinHistoryEntryResponse::from)
                .toList();
    }

    /** Fetches (or creates) this week's row. Recomputes it against fresh data unless it's already closed — see {@link TdeeCheckin#isClosed()}. */
    private WeekState currentWeek(UUID userId, LiveTargets live) {
        LocalDate weekStart = currentWeekStart();
        TdeeCheckin checkin =
                checkinRepository.findByUserIdAndWeekStart(userId, weekStart).orElseGet(() -> new TdeeCheckin(userId, weekStart));
        if (checkin.isClosed()) {
            return new WeekState(checkin, List.of());
        }

        LocalDate windowEnd = LocalDate.now(clock).minusDays(1);
        LocalDate windowStart = windowEnd.minusDays(TdeeAdaptationCalculator.WINDOW_DAYS - 1L);

        List<TdeeAdaptationCalculator.DailyIntake> dailyIntakes = dailyIntakes(userId, windowStart, windowEnd, live.currentTargetKcal());

        List<LocalDate> weighInDatesInWindow = weightEntryRepository
                .findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, windowStart, windowEnd)
                .stream()
                .map(WeightEntry::getEntryDate)
                .toList();
        // The EMA needs the user's FULL history up to the window end for an accurate trend value at
        // any date inside it -- never just the in-window weigh-ins (same rule WeightEntryService/
        // ProgressService already follow for this exact calculator).
        List<WeightTrendCalculator.Point> trendSeries = trendCalculator.smooth(
                weightEntryRepository.findByUserIdAndEntryDateLessThanEqualOrderByEntryDateAsc(userId, windowEnd).stream()
                        .map(w -> new WeightTrendCalculator.Point(w.getEntryDate(), w.getWeightKg().doubleValue()))
                        .toList());

        TdeeAdaptationCalculator.Result result = adaptationCalculator.evaluate(new TdeeAdaptationCalculator.Input(
                dailyIntakes,
                weighInDatesInWindow,
                trendAtOrBefore(trendSeries, windowStart),
                trendAtOrBefore(trendSeries, windowEnd),
                live.currentTdee()));

        checkin.recompute(windowStart, windowEnd, live.formulaTdee(), result);
        TdeeCheckin saved = checkinRepository.save(checkin);
        return new WeekState(saved, result.reasons());
    }

    private CheckinResponse toResponse(WeekState state, LiveTargets live) {
        TdeeCheckin checkin = state.checkin();
        if (checkin.getStatus() == CheckinStatus.INSUFFICIENT_DATA) {
            return CheckinResponse.insufficientData(checkin, state.reasons());
        }
        int currentTdee = (int) Math.round(live.currentTdee());
        int proposedTargetKcal = calculator.calculate(live.input(), checkin.getProposedTdee()).calories();
        return CheckinResponse.of(checkin, currentTdee, live.currentTargetKcal(), proposedTargetKcal);
    }

    /** {@code currentTdee}/{@code currentTargetKcal} are always resolved live, even for an already-closed week — see {@code CheckinResponse}. */
    private LiveTargets resolveLiveTargets(UUID userId, ProfileResponse profile) {
        NutritionCalculator.Input input = toInput(profile);
        int formulaTdee = round(calculator.tdee(input));
        double currentTdee = adaptiveTdeeService
                .findActive(userId, profile.activityLevel())
                .map(active -> (double) active.tdeeKcal())
                .orElse((double) formulaTdee);
        int currentTargetKcal = calculator.calculate(input, currentTdee).calories();
        return new LiveTargets(input, formulaTdee, currentTdee, currentTargetKcal);
    }

    private NutritionCalculator.Input toInput(ProfileResponse profile) {
        int ageYears = Period.between(profile.birthDate(), LocalDate.now(clock)).getYears();
        return new NutritionCalculator.Input(
                profile.sex(),
                ageYears,
                profile.heightCm(),
                profile.weightKg().doubleValue(),
                profile.activityLevel(),
                profile.goal(),
                profile.pace(),
                profile.dietStyle(),
                profile.strengthTraining(),
                profile.bodyFatPct() != null ? profile.bodyFatPct().doubleValue() : null);
    }

    /** One {@link TdeeAdaptationCalculator.DailyIntake} per calendar day in the window — logged kcal vs. today's live target (see {@link #resolveLiveTargets}). */
    private List<TdeeAdaptationCalculator.DailyIntake> dailyIntakes(UUID userId, LocalDate windowStart, LocalDate windowEnd, int targetKcal) {
        List<FoodEntry> entries = foodEntryRepository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, windowStart, windowEnd);
        Map<LocalDate, List<FoodEntryResponse>> byDate =
                entries.stream().map(FoodEntryResponse::from).collect(Collectors.groupingBy(FoodEntryResponse::entryDate));

        List<TdeeAdaptationCalculator.DailyIntake> days = new ArrayList<>();
        for (LocalDate date = windowStart; !date.isAfter(windowEnd); date = date.plusDays(1)) {
            int loggedKcal = byDate.getOrDefault(date, List.of()).stream()
                    .map(FoodEntryResponse::totals)
                    .mapToInt(NutritionMath.Totals::kcal)
                    .sum();
            days.add(new TdeeAdaptationCalculator.DailyIntake(date, loggedKcal, targetKcal));
        }
        return days;
    }

    /** The EMA trend value as of the latest weigh-in on or before {@code date}; falls back to the earliest known point if none exists yet. */
    private static double trendAtOrBefore(List<WeightTrendCalculator.Point> series, LocalDate date) {
        if (series.isEmpty()) {
            return 0.0;
        }
        WeightTrendCalculator.Point latestAtOrBefore = null;
        for (WeightTrendCalculator.Point point : series) {
            if (point.date().isAfter(date)) {
                break;
            }
            latestAtOrBefore = point;
        }
        return latestAtOrBefore != null ? latestAtOrBefore.weightKg() : series.get(0).weightKg();
    }

    private Optional<ProfileResponse> profile(UUID userId) {
        return profileService.findByUserId(userId).map(profileWithTargets -> profileWithTargets.profile());
    }

    private ProfileResponse requireProfile(UUID userId) {
        return profile(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe un perfil para este usuario."));
    }

    /** Always a Monday, zoned to {@code app.timezone} via the injected {@link Clock} — see {@code TdeeCheckin#getWeekStart()}. */
    private LocalDate currentWeekStart() {
        return LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }

    /** This week's row plus the reasons a fresh {@link TdeeAdaptationCalculator} run found (empty unless just recomputed as INSUFFICIENT_DATA — see {@link #currentWeek}). */
    private record WeekState(TdeeCheckin checkin, List<String> reasons) {}

    /** The profile-derived numbers every response needs, resolved once per call and reused — see {@link #resolveLiveTargets}. */
    private record LiveTargets(NutritionCalculator.Input input, int formulaTdee, double currentTdee, int currentTargetKcal) {}
}
