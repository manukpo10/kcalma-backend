package com.kcalma.food.reference;

import com.kcalma.food.FoodSource;
import com.kcalma.food.NutritionMath;
import com.kcalma.food.analysis.AnalyzedDish;
import com.kcalma.food.analysis.AnalyzedFoodItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves each Gemini-analyzed item's real nutrient values, in priority order: (1) the caller's
 * own {@code app.user_food} library, by normalized Spanish name — exact match, then a
 * high-similarity trigram fallback; (2) {@code app.food_reference} (USDA FoodData Central) by
 * trigram similarity on the English {@code canonicalNameEn}; (3) Gemini's own per-100g estimate,
 * used as-is. Applied to the photo/text analyze flows and to meal suggestions alike — see {@code
 * FoodController} and {@code SuggestionService}. Totals are still always derived from the
 * resolved per-100g values via {@link NutritionMath}, never stored/computed here.
 *
 * <p>Every dish is decomposed into ingredients before it ever reaches this class (see {@link
 * AnalyzedDish}) — {@link #resolveDish}/{@link #resolveDishes} resolve each ingredient
 * independently via {@link #resolve} and then aggregate the dish's own totals/per100/source from
 * those resolved ingredients (see {@link ResolvedDish#aggregate}).
 */
@Service
public class FoodReferenceMatcher {

    /**
     * Stricter threshold for the personal library: a false positive here silently overwrites the
     * item with the WRONG saved food's numbers, so a fuzzy match needs to be quite confident.
     */
    static final double PERSONAL_SIMILARITY_THRESHOLD = 0.45;

    /**
     * Looser threshold for the USDA reference: the query is an AI-translated English name being
     * matched against real USDA descriptions, so exact wording is less likely.
     */
    static final double REFERENCE_SIMILARITY_THRESHOLD = 0.3;

    private final UserFoodRepository userFoodRepository;
    private final FoodReferenceRepository foodReferenceRepository;

    public FoodReferenceMatcher(UserFoodRepository userFoodRepository, FoodReferenceRepository foodReferenceRepository) {
        this.userFoodRepository = userFoodRepository;
        this.foodReferenceRepository = foodReferenceRepository;
    }

    /**
     * Read-only: every query below is a lookup, never a write. Wrapping the whole per-item loop in
     * one transaction (rather than each repository call opening its own) keeps a single item's
     * 2-3 sequential lookups on one connection instead of round-tripping the pool per query.
     */
    @Transactional(readOnly = true)
    public List<ResolvedFoodItem> resolve(UUID userId, List<AnalyzedFoodItem> items) {
        return items.stream().map(item -> resolveOne(userId, item)).toList();
    }

    /** Resolves every one of a dish's ingredients, then aggregates the dish's own totals/per100/source. */
    @Transactional(readOnly = true)
    public ResolvedDish resolveDish(UUID userId, AnalyzedDish dish) {
        List<ResolvedFoodItem> ingredients = resolve(userId, dish.ingredients());
        return ResolvedDish.aggregate(dish.name(), dish.grams(), ingredients);
    }

    /** {@link #resolveDish} for every dish in an analysis/suggestion result. */
    @Transactional(readOnly = true)
    public List<ResolvedDish> resolveDishes(UUID userId, List<AnalyzedDish> dishes) {
        return dishes.stream().map(dish -> resolveDish(userId, dish)).toList();
    }

    private ResolvedFoodItem resolveOne(UUID userId, AnalyzedFoodItem item) {
        Optional<ResolvedFoodItem> personal = matchPersonalLibrary(userId, item);
        if (personal.isPresent()) {
            return personal.get();
        }

        Optional<ResolvedFoodItem> reference = matchFoodReference(item);
        if (reference.isPresent()) {
            return reference.get();
        }

        return estimateFrom(item);
    }

    private Optional<ResolvedFoodItem> matchPersonalLibrary(UUID userId, AnalyzedFoodItem item) {
        String normalizedName = NameNormalizer.normalize(item.name());
        if (normalizedName.isBlank()) {
            return Optional.empty();
        }
        return userFoodRepository
                .findByUserIdAndNormalizedName(userId, normalizedName)
                .or(() -> userFoodRepository.findBestFuzzyMatch(userId, normalizedName, PERSONAL_SIMILARITY_THRESHOLD))
                .map(food -> new ResolvedFoodItem(
                        item.name(),
                        item.canonicalNameEn(),
                        item.grams(),
                        food.toPer100(),
                        FoodSource.PERSONAL,
                        food.getFdcId(),
                        food.getDisplayName()));
    }

    private Optional<ResolvedFoodItem> matchFoodReference(AnalyzedFoodItem item) {
        String canonicalQuery = NameNormalizer.normalizeCanonical(item.canonicalNameEn());
        if (canonicalQuery.isBlank()) {
            return Optional.empty();
        }
        boolean preferRaw = !NameNormalizer.mentionsCookingMethod(item.canonicalNameEn());
        return foodReferenceRepository
                .findBestMatch(canonicalQuery, REFERENCE_SIMILARITY_THRESHOLD, preferRaw)
                .map(ref -> new ResolvedFoodItem(
                        item.name(),
                        item.canonicalNameEn(),
                        item.grams(),
                        new NutritionMath.Per100(
                                ref.getKcalPer100().doubleValue(),
                                ref.getProteinPer100().doubleValue(),
                                ref.getFatPer100().doubleValue(),
                                ref.getCarbsPer100().doubleValue(),
                                ref.getFiberPer100().doubleValue(),
                                ref.getSugarPer100().doubleValue(),
                                ref.getSodiumMgPer100().doubleValue()),
                        FoodSource.USDA,
                        ref.getFdcId(),
                        ref.getDescription()));
    }

    private ResolvedFoodItem estimateFrom(AnalyzedFoodItem item) {
        NutritionMath.Per100 estimate = new NutritionMath.Per100(
                item.kcalPer100(),
                item.proteinPer100(),
                item.fatPer100(),
                item.carbsPer100(),
                item.fiberPer100(),
                item.sugarPer100(),
                item.sodiumMgPer100());
        return new ResolvedFoodItem(
                item.name(), item.canonicalNameEn(), item.grams(), estimate, FoodSource.ESTIMATED, null, null);
    }
}
