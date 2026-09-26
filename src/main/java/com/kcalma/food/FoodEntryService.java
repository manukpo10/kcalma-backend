package com.kcalma.food;

import com.kcalma.food.dto.FoodEntryRequest;
import com.kcalma.food.dto.FoodEntryResponse;
import com.kcalma.food.dto.UpdateFoodEntryRequest;
import com.kcalma.food.reference.NameNormalizer;
import com.kcalma.food.reference.UserFood;
import com.kcalma.food.reference.UserFoodRepository;
import com.kcalma.food.reference.UserFoodSource;
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
     * Personal-library upsert (priority 1 of {@code FoodReferenceMatcher}): every saved item is
     * cached under the caller's own normalized name, so logging the same food again — by name,
     * from a photo, from text, or typed by hand — resolves to these exact values next time.
     */
    private void upsertUserFood(FoodEntry entry) {
        String normalizedName = NameNormalizer.normalize(entry.getName());
        if (normalizedName.isBlank()) {
            return;
        }
        NutritionMath.Per100 per100 = new NutritionMath.Per100(
                entry.getKcalPer100().doubleValue(),
                entry.getProteinPer100().doubleValue(),
                entry.getFatPer100().doubleValue(),
                entry.getCarbsPer100().doubleValue(),
                entry.getFiberPer100().doubleValue(),
                entry.getSugarPer100().doubleValue(),
                entry.getSodiumMgPer100().doubleValue());
        UserFoodSource source = toUserFoodSource(entry.getSource());

        userFoodRepository
                .findByUserIdAndNormalizedName(entry.getUserId(), normalizedName)
                .ifPresentOrElse(
                        existing -> existing.recordUse(
                                entry.getName(),
                                per100,
                                // PERSONAL means THIS save matched the library, not that the library
                                // itself was just re-sourced from USDA — keep its original source.
                                entry.getSource() == FoodSource.PERSONAL ? existing.getSource() : source,
                                entry.getFdcId()),
                        () -> userFoodRepository.save(new UserFood(
                                entry.getUserId(), normalizedName, entry.getName(), per100, source, entry.getFdcId())));
    }

    /**
     * The library only ever remembers WHERE a value first came from, not "matched my own
     * library" — {@code PERSONAL} can't happen on the create path (a match implies a row already
     * exists) and is mapped defensively rather than left to throw.
     */
    private static UserFoodSource toUserFoodSource(FoodSource source) {
        return switch (source) {
            case USDA, PERSONAL -> UserFoodSource.USDA;
            case ESTIMATED -> UserFoodSource.ESTIMATED;
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
            entry.setGrams(request.grams());
            entry.setMealType(request.mealType());
            return FoodEntryResponse.from(entry);
        });
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
                request.fdcId());
    }
}
