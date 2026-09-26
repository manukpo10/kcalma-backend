package com.kcalma.weight;

import com.kcalma.profile.UserProfile;
import com.kcalma.profile.UserProfileRepository;
import com.kcalma.progress.WeightTrendCalculator;
import com.kcalma.weight.dto.UpsertWeightRequest;
import com.kcalma.weight.dto.UpsertWeightResponse;
import com.kcalma.weight.dto.WeightEntryResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeightEntryService {

    private final WeightEntryRepository repository;
    private final UserProfileRepository profileRepository;
    private final WeightTrendCalculator trendCalculator = new WeightTrendCalculator();

    public WeightEntryService(WeightEntryRepository repository, UserProfileRepository profileRepository) {
        this.repository = repository;
        this.profileRepository = profileRepository;
    }

    /**
     * Upserts the weigh-in for one day, then refreshes {@code user_profile.weight_kg} from the
     * EMA trend (see {@link #recomputeProfileWeightFromTrend}) so the daily nutrition targets are
     * always derived from the smoothed trend, never a single noisy weigh-in — even when the edited
     * date isn't the latest one, since changing any point reflows the whole EMA chain.
     */
    @Transactional
    public UpsertWeightResponse upsert(UUID userId, LocalDate date, UpsertWeightRequest request) {
        WeightEntry entry = repository
                .findByUserIdAndEntryDate(userId, date)
                .orElseGet(() -> new WeightEntry(userId, date, request.weightKg()));
        entry.setWeightKg(request.weightKg());
        WeightEntry saved = repository.save(entry);

        boolean targetsUpdated = recomputeProfileWeightFromTrend(userId);

        return new UpsertWeightResponse(WeightEntryResponse.from(saved), targetsUpdated);
    }

    @Transactional
    public boolean delete(UUID userId, LocalDate date) {
        Optional<WeightEntry> entry = repository.findByUserIdAndEntryDate(userId, date);
        entry.ifPresent(repository::delete);
        if (entry.isPresent()) {
            recomputeProfileWeightFromTrend(userId);
        }
        return entry.isPresent();
    }

    @Transactional(readOnly = true)
    public List<WeightEntryResponse> findRange(UUID userId, LocalDate from, LocalDate to) {
        return repository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, from, to).stream()
                .map(WeightEntryResponse::from)
                .toList();
    }

    /**
     * Recomputes the {@link WeightTrendCalculator} EMA trend across every one of the user's
     * weigh-ins and stores the value AT THE LATEST DATE into {@code user_profile.weight_kg}
     * (rounded to 0.1 kg) — never the raw latest weigh-in. A lone weigh-in has a trend equal to
     * its own raw value (the EMA seeds {@code trend[0] = weight[0]}), so "first weigh-in -> trend =
     * raw" falls out of {@link WeightTrendCalculator#smooth} without any special-casing here.
     *
     * <p>No-op when the user has no profile yet, or no weigh-ins remain (e.g. the one just deleted
     * was the last one) — there's nothing sensible to store in either case.
     *
     * @return true if {@code user_profile.weight_kg} was updated
     */
    private boolean recomputeProfileWeightFromTrend(UUID userId) {
        List<WeightEntry> allWeighIns = repository.findByUserIdOrderByEntryDateAsc(userId);
        if (allWeighIns.isEmpty()) {
            return false;
        }
        Optional<UserProfile> profile = profileRepository.findById(userId);
        if (profile.isEmpty()) {
            return false;
        }

        List<WeightTrendCalculator.Point> points = allWeighIns.stream()
                .map(w -> new WeightTrendCalculator.Point(w.getEntryDate(), w.getWeightKg().doubleValue()))
                .toList();
        List<WeightTrendCalculator.Point> trend = trendCalculator.smooth(points);
        double latestTrendKg = trend.get(trend.size() - 1).weightKg();

        profile.get().setWeightKg(BigDecimal.valueOf(latestTrendKg).setScale(1, RoundingMode.HALF_UP));
        return true;
    }
}
