package com.kcalma.weight;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeightEntryRepository extends JpaRepository<WeightEntry, UUID> {

    Optional<WeightEntry> findByUserIdAndEntryDate(UUID userId, LocalDate entryDate);

    List<WeightEntry> findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(UUID userId, LocalDate from, LocalDate to);

    /** All of a user's weigh-ins up to (and including) a date, oldest first — the input the trend is computed from. */
    List<WeightEntry> findByUserIdAndEntryDateLessThanEqualOrderByEntryDateAsc(UUID userId, LocalDate to);

    /** Every one of a user's weigh-ins, oldest first — the full series {@code WeightTrendCalculator} smooths (see WeightEntryService). */
    List<WeightEntry> findByUserIdOrderByEntryDateAsc(UUID userId);
}
