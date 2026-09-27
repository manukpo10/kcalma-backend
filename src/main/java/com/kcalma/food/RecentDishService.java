package com.kcalma.food;

import com.kcalma.food.dto.RecentDishResponse;
import com.kcalma.food.reference.NameNormalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Powers GET /api/food/recent: the caller's own dishes from the last {@value #LOOKBACK_DAYS} days,
 * collapsed to one row per normalized name (see {@link NameNormalizer#normalize}) so "milanesa"
 * logged five times shows once, ranked by how often it comes back rather than by raw recency alone.
 */
@Service
public class RecentDishService {

    /** "the last 60 days" — inclusive of today, so the window is exactly 60 calendar days wide. */
    private static final int LOOKBACK_DAYS = 60;

    private static final int DEFAULT_LIMIT = 20;

    private final FoodEntryRepository repository;
    private final Clock clock;

    public RecentDishService(FoodEntryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RecentDishResponse> findRecent(UUID userId, Integer limit) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(LOOKBACK_DAYS - 1L);
        List<FoodEntry> entries = repository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, from, today);

        Map<String, List<FoodEntry>> byNormalizedName = entries.stream()
                .collect(Collectors.groupingBy(entry -> NameNormalizer.normalize(entry.getName()), LinkedHashMap::new, Collectors.toList()));

        int effectiveLimit = (limit == null || limit <= 0) ? DEFAULT_LIMIT : limit;

        return byNormalizedName.values().stream()
                .map(RecentDishService::toResponse)
                .sorted(Comparator.comparingInt(RecentDishResponse::timesLogged)
                        .reversed()
                        .thenComparing(Comparator.comparing(RecentDishResponse::lastLoggedOn).reversed()))
                .limit(effectiveLimit)
                .toList();
    }

    /** The dish's own fields (name, grams, per-100g...) always come from the MOST RECENT entry in the group — "grams = last used". */
    private static RecentDishResponse toResponse(List<FoodEntry> group) {
        FoodEntry latest = group.stream()
                .max(Comparator.comparing(FoodEntry::getEntryDate).thenComparing(FoodEntry::getCreatedAt))
                .orElseThrow();
        return RecentDishResponse.from(latest, group.size());
    }
}
