package com.kcalma.water;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaterLogRepository extends JpaRepository<WaterLog, WaterLogId> {

    Optional<WaterLog> findByUserIdAndEntryDate(UUID userId, LocalDate entryDate);

    /** Every day a user ever logged water for, oldest first — used by GET /api/export. */
    List<WaterLog> findByUserIdOrderByEntryDateAsc(UUID userId);
}
