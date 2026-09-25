package com.kcalma.food;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodEntryRepository extends JpaRepository<FoodEntry, UUID> {

    List<FoodEntry> findByUserIdAndEntryDateOrderByCreatedAtAsc(UUID userId, LocalDate entryDate);

    /** Every entry in a date range, oldest first — used to build the progress screen's nutrition history. */
    List<FoodEntry> findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(UUID userId, LocalDate from, LocalDate to);

    /** Earliest logged date for a user — used to bound the "ALL" range on the progress screen. */
    Optional<FoodEntry> findFirstByUserIdOrderByEntryDateAsc(UUID userId);

    /** Owner-scoped lookup: an id that exists but belongs to another user resolves empty, never a value. */
    Optional<FoodEntry> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Bulk-deletes every entry for one user's meal on one day as a single SQL statement (not a
     * fetch-then-remove-each-entity loop), so wiping a full meal stays one round trip to the DB.
     */
    @Modifying
    @Query("DELETE FROM FoodEntry f WHERE f.userId = :userId AND f.entryDate = :entryDate AND f.mealType = :mealType")
    void deleteByUserIdAndEntryDateAndMealType(
            @Param("userId") UUID userId, @Param("entryDate") LocalDate entryDate, @Param("mealType") MealType mealType);
}
