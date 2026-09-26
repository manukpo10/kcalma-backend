package com.kcalma.food;

import com.kcalma.food.dto.FoodEntryIngredientRequest;
import com.kcalma.food.dto.FoodEntryRequest;
import com.kcalma.food.dto.FoodEntryResponse;
import com.kcalma.food.dto.UpdateFoodEntryRequest;
import com.kcalma.food.reference.NameNormalizer;
import com.kcalma.food.reference.UserFood;
import com.kcalma.food.reference.UserFoodRepository;
import com.kcalma.food.reference.UserFoodSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FoodEntryService {

    private final FoodEntryRepository repository;
    private final UserFoodRepository userFoodRepository;

    public FoodEntryService(FoodEntryRepository repository, UserFoodRepository userFoodRepository) {
        this.repository = repository;
        this.userFoodRepository = userFoodRepository;
    }

    @Transactional
    public List<FoodEntryResponse> saveAll(UUID userId, List<FoodEntryRequest> requests) {
        List<FoodEntry> entries = requests.stream().map(r -> toEntity(userId, r)).toList();
        List<FoodEntry> saved = repository.saveAll(entries);
        saved.forEach(this::upsertUserFood);
        return saved.stream().map(FoodEntryResponse::from).toList();
    }

    /**
     * Personal-library upsert (priority 1 of {@code FoodReferenceMatcher}): every saved INGREDIENT
     * is cached under its own normalized name — not the dish's name — so logging "milanesa con
     * puré" today resolves "carne"/"pan rallado"/"papa" individually the next time any of them
     * shows up in a different dish. Every entry always has at least one ingredient (see {@link
     * #resolveIngredients}), so this never has nothing to do.
     */
    private void upsertUserFood(FoodEntry entry) {
        List<FoodEntryIngredient> ingredients = entry.getIngredients();
        if (ingredients == null) {
            return;
        }
        ingredients.forEach(ingredient -> upsertUserFood(entry.getUserId(), ingredient));
    }

    private void upsertUserFood(UUID userId, FoodEntryIngredient ingredient) {
        String normalizedName = NameNormalizer.normalize(ingredient.name());
        if (normalizedName.isBlank()) {
            return;
        }
        NutritionMath.Per100 per100 = ingredient.toPer100();
        UserFoodSource source = toUserFoodSource(ingredient.source());

        userFoodRepository
                .findByUserIdAndNormalizedName(userId, normalizedName)
                .ifPresentOrElse(
                        existing -> existing.recordUse(
                                ingredient.name(),
                                per100,
                                // PERSONAL means THIS save matched the library, not that the library
                                // itself was just re-sourced from USDA — keep its original source.
                                ingredient.source() == FoodSource.PERSONAL ? existing.getSource() : source,
                                ingredient.fdcId()),
                        () -> userFoodRepository.save(new UserFood(
                                userId, normalizedName, ingredient.name(), per100, source, ingredient.fdcId())));
    }

    /**
     * The library only ever remembers WHERE a value first came from, not "matched my own
     * library" — {@code PERSONAL} can't happen on the create path (a match implies a row already
     * exists) and is mapped defensively rather than left to throw. {@code MIXED} is a dish-level
     * aggregate that can never be a single ingredient's own source either (see {@link
     * FoodSource#combine}) — mapped defensively to {@code ESTIMATED} for the same reason.
     */
    private static UserFoodSource toUserFoodSource(FoodSource source) {
        return switch (source) {
            case USDA, PERSONAL -> UserFoodSource.USDA;
            case ESTIMATED, MIXED -> UserFoodSource.ESTIMATED;
            case MANUAL -> UserFoodSource.USER;
        };
    }

    @Transactional(readOnly = true)
    public List<FoodEntryResponse> findByDate(UUID userId, LocalDate date) {
        return repository.findByUserIdAndEntryDateOrderByCreatedAtAsc(userId, date).stream()
                .map(FoodEntryResponse::from)
                .toList();
    }

    @Transactional
    public Optional<FoodEntryResponse> update(UUID userId, UUID id, UpdateFoodEntryRequest request) {
        return repository.findByIdAndUserId(id, userId).map(entry -> {
            entry.setMealType(request.mealType());
            if (request.ingredients() != null) {
                applyIngredientEdit(entry, request.ingredients());
            } else {
                scaleIngredients(entry, request.grams());
                entry.setGrams(request.grams());
            }
            return FoodEntryResponse.from(entry);
        });
    }

    /**
     * The ingredients themselves changed (grams edited and/or one removed): the dish's own grams,
     * per-100g, and source are no longer whatever was last saved — they're recomputed FROM this
     * ingredient set, exactly like the initial resolve (see {@code ResolvedDish#aggregate}), so a
     * dish can never end up showing stale totals or a stale {@code SourceBadge} for a breakdown it
     * no longer matches.
     */
    private void applyIngredientEdit(FoodEntry entry, List<FoodEntryIngredientRequest> requested) {
        List<FoodEntryIngredient> ingredients = requested.stream().map(FoodEntryIngredientRequest::toIngredient).toList();

        BigDecimal grams = ingredients.stream().map(FoodEntryIngredient::grams).reduce(BigDecimal.ZERO, BigDecimal::add);
        NutritionMath.Totals totals = ingredients.stream()
                .map(ingredient -> NutritionMath.totals(ingredient.toPer100(), ingredient.grams().doubleValue()))
                .reduce(NutritionMath.Totals.ZERO, NutritionMath.Totals::plus);
        NutritionMath.Per100 per100 = NutritionMath.per100FromTotals(totals, grams.doubleValue());
        FoodSource source = FoodSource.combine(ingredients.stream().map(FoodEntryIngredient::source).toList());
        Long fdcId = ingredients.size() == 1 ? ingredients.get(0).fdcId() : null;

        entry.setGrams(grams);
        entry.setPer100(per100);
        entry.setSource(source);
        entry.setFdcId(fdcId);
        entry.setIngredients(ingredients);
    }

    /**
     * A plain dish-grams change (the ingredients section itself wasn't touched): per-100g values
     * are invariant under uniform scaling, so only each stored ingredient's OWN grams needs to
     * move, by the same ratio as the dish's — keeps the breakdown internally consistent for the
     * next time it's viewed/edited. A legacy entry with no stored ingredients has nothing to scale.
     */
    private void scaleIngredients(FoodEntry entry, BigDecimal newGrams) {
        List<FoodEntryIngredient> current = entry.getIngredients();
        BigDecimal oldGrams = entry.getGrams();
        if (current == null || oldGrams == null || oldGrams.signum() == 0) {
            return;
        }
        BigDecimal ratio = newGrams.divide(oldGrams, 6, RoundingMode.HALF_UP);
        List<FoodEntryIngredient> scaled = current.stream()
                .map(ingredient -> ingredient.withGrams(ingredient.grams().multiply(ratio).setScale(1, RoundingMode.HALF_UP)))
                .toList();
        entry.setIngredients(scaled);
    }

    @Transactional
    public boolean delete(UUID userId, UUID id) {
        Optional<FoodEntry> entry = repository.findByIdAndUserId(id, userId);
        entry.ifPresent(repository::delete);
        return entry.isPresent();
    }

    /** Wipes every entry of one meal/day for the caller in a single delete — a no-op if none matched. */
    @Transactional
    public void deleteAllByMeal(UUID userId, LocalDate date, MealType mealType) {
        repository.deleteByUserIdAndEntryDateAndMealType(userId, date, mealType);
    }

    private FoodEntry toEntity(UUID userId, FoodEntryRequest request) {
        return new FoodEntry(
                userId,
                request.entryDate(),
                request.mealType(),
                request.name(),
                request.grams(),
                request.kcalPer100(),
                request.proteinPer100(),
                request.fatPer100(),
                request.carbsPer100(),
                request.fiberPer100(),
                request.sugarPer100(),
                request.sodiumMgPer100(),
                request.source(),
                request.fdcId(),
                resolveIngredients(request));
    }

    /**
     * Defensive fallback for any client that doesn't yet send a breakdown: a dish with no
     * ingredients becomes a single ingredient equal to the dish itself, so every entry saved from
     * here on always has a non-empty breakdown to show/edit later.
     */
    private List<FoodEntryIngredient> resolveIngredients(FoodEntryRequest request) {
        if (request.ingredients() != null && !request.ingredients().isEmpty()) {
            return request.ingredients().stream().map(FoodEntryIngredientRequest::toIngredient).toList();
        }
        return List.of(new FoodEntryIngredient(
                request.name(),
                request.grams(),
                request.kcalPer100(),
                request.proteinPer100(),
                request.fatPer100(),
                request.carbsPer100(),
                request.fiberPer100(),
                request.sugarPer100(),
                request.sodiumMgPer100(),
                request.source(),
                request.fdcId()));
    }
}
