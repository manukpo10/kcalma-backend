package com.kcalma.favorites;

import com.kcalma.favorites.dto.CreateFavoriteRequest;
import com.kcalma.favorites.dto.FavoriteResponse;
import com.kcalma.food.FoodEntry;
import com.kcalma.food.FoodEntryIngredient;
import com.kcalma.food.FoodEntryRepository;
import com.kcalma.food.FoodSource;
import com.kcalma.food.dto.FoodEntryIngredientRequest;
import com.kcalma.food.reference.NameNormalizer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FavoriteDishService {

    private final FavoriteDishRepository repository;
    private final FoodEntryRepository foodEntryRepository;

    public FavoriteDishService(FavoriteDishRepository repository, FoodEntryRepository foodEntryRepository) {
        this.repository = repository;
        this.foodEntryRepository = foodEntryRepository;
    }

    @Transactional(readOnly = true)
    public List<FavoriteResponse> findAll(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(FavoriteResponse::from).toList();
    }

    /** Upserts by normalized name per user — favoriting the same dish twice refreshes it in place instead of duplicating it. */
    @Transactional
    public FavoriteResponse upsert(UUID userId, CreateFavoriteRequest request) {
        Dish dish = resolveDish(userId, request);
        String normalizedName = NameNormalizer.normalize(dish.name());
        if (normalizedName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el nombre del plato.");
        }

        FavoriteDish favorite =
                repository.findByUserIdAndNormalizedName(userId, normalizedName).orElseGet(() -> new FavoriteDish(userId, normalizedName));
        favorite.applyDish(
                request.mealType(),
                dish.name(),
                dish.grams(),
                dish.kcalPer100(),
                dish.proteinPer100(),
                dish.fatPer100(),
                dish.carbsPer100(),
                dish.fiberPer100(),
                dish.sugarPer100(),
                dish.sodiumMgPer100(),
                dish.source(),
                dish.fdcId(),
                dish.ingredients());
        return FavoriteResponse.from(repository.save(favorite));
    }

    @Transactional
    public boolean delete(UUID userId, UUID id) {
        Optional<FavoriteDish> favorite = repository.findByIdAndUserId(id, userId);
        favorite.ifPresent(repository::delete);
        return favorite.isPresent();
    }

    /** Either an existing entry's own dish fields, or the Dish payload sent directly — see {@link CreateFavoriteRequest}. */
    private Dish resolveDish(UUID userId, CreateFavoriteRequest request) {
        if (request.isFromEntry()) {
            FoodEntry entry = foodEntryRepository
                    .findByIdAndUserId(request.entryId(), userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La comida indicada no existe."));
            return Dish.from(entry);
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el plato a guardar como favorito.");
        }
        if (request.grams() == null
                || request.kcalPer100() == null
                || request.proteinPer100() == null
                || request.fatPer100() == null
                || request.carbsPer100() == null
                || request.fiberPer100() == null
                || request.sugarPer100() == null
                || request.sodiumMgPer100() == null
                || request.source() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan datos del plato a guardar como favorito.");
        }
        return Dish.from(request);
    }

    /** The subset of Dish fields needed to build/refresh a {@link FavoriteDish}, whichever of the two input shapes it came from. */
    private record Dish(
            String name,
            BigDecimal grams,
            BigDecimal kcalPer100,
            BigDecimal proteinPer100,
            BigDecimal fatPer100,
            BigDecimal carbsPer100,
            BigDecimal fiberPer100,
            BigDecimal sugarPer100,
            BigDecimal sodiumMgPer100,
            FoodSource source,
            Long fdcId,
            List<FoodEntryIngredient> ingredients) {

        /** A legacy pre-V9 entry with no stored breakdown becomes a single ingredient equal to the dish itself — same fallback as {@code FoodEntryService#resolveIngredients}. */
        static Dish from(FoodEntry entry) {
            List<FoodEntryIngredient> ingredients = entry.getIngredients() != null
                    ? entry.getIngredients()
                    : List.of(new FoodEntryIngredient(
                            entry.getName(),
                            entry.getGrams(),
                            entry.getKcalPer100(),
                            entry.getProteinPer100(),
                            entry.getFatPer100(),
                            entry.getCarbsPer100(),
                            entry.getFiberPer100(),
                            entry.getSugarPer100(),
                            entry.getSodiumMgPer100(),
                            entry.getSource(),
                            entry.getFdcId()));
            return new Dish(
                    entry.getName(),
                    entry.getGrams(),
                    entry.getKcalPer100(),
                    entry.getProteinPer100(),
                    entry.getFatPer100(),
                    entry.getCarbsPer100(),
                    entry.getFiberPer100(),
                    entry.getSugarPer100(),
                    entry.getSodiumMgPer100(),
                    entry.getSource(),
                    entry.getFdcId(),
                    ingredients);
        }

        /** A client that doesn't send a breakdown yet gets a single synthetic ingredient equal to the dish itself. */
        static Dish from(CreateFavoriteRequest request) {
            List<FoodEntryIngredient> ingredients = request.ingredients() != null && !request.ingredients().isEmpty()
                    ? request.ingredients().stream().map(FoodEntryIngredientRequest::toIngredient).toList()
                    : List.of(new FoodEntryIngredient(
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
            return new Dish(
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
                    ingredients);
        }
    }
}
