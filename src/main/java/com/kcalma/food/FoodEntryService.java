package com.kcalma.food;

import com.kcalma.food.dto.FoodEntryRequest;
import com.kcalma.food.dto.FoodEntryResponse;
import com.kcalma.food.dto.UpdateFoodEntryRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FoodEntryService {

    private final FoodEntryRepository repository;

    public FoodEntryService(FoodEntryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<FoodEntryResponse> saveAll(UUID userId, List<FoodEntryRequest> requests) {
        List<FoodEntry> entries = requests.stream().map(r -> toEntity(userId, r)).toList();
        return repository.saveAll(entries).stream().map(FoodEntryResponse::from).toList();
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
                request.source());
    }
}
