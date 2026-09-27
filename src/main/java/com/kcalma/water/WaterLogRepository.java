package com.kcalma.water;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaterLogRepository extends JpaRepository<WaterLog, WaterLogId> {

    Optional<WaterLog> findByUserIdAndEntryDate(UUID userId, LocalDate entryDate);

    /** Every day a user ever logged water for, oldest first — used by GET /api/export. */
    List<WaterLog> findByUserIdOrderByEntryDateAsc(UUID userId);

    /** Bulk-deletes every day a user ever logged water for — used by account deletion ({@code com.kcalma.account.AccountService}). */
    @Modifying
    @Query("DELETE FROM WaterLog w WHERE w.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
