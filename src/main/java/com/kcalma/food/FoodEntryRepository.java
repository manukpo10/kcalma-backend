package com.kcalma.food;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodEntryRepository extends JpaRepository<FoodEntry, UUID> {

    List<FoodEntry> findByUserIdAndEntryDateOrderByCreatedAtAsc(UUID userId, LocalDate entryDate);

    /** Owner-scoped lookup: an id that exists but belongs to another user resolves empty, never a value. */
    Optional<FoodEntry> findByIdAndUserId(UUID id, UUID userId);
}
