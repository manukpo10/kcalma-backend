package com.kcalma.checkin;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TdeeCheckinRepository extends JpaRepository<TdeeCheckin, UUID> {

    /** The one row for a given ISO week (see {@link TdeeCheckin#getWeekStart()}) — at most one per (user, week). */
    Optional<TdeeCheckin> findByUserIdAndWeekStart(UUID userId, LocalDate weekStart);

    /** The most recently decided week in a given state — used by {@code AdaptiveTdeeService} to find the active adaptive TDEE. */
    Optional<TdeeCheckin> findFirstByUserIdAndStatusOrderByWeekStartDesc(UUID userId, CheckinStatus status);

    /** Every past (strictly before {@code weekStart}) week, most recent first — the source list for GET /api/checkin/history. */
    List<TdeeCheckin> findByUserIdAndWeekStartLessThanOrderByWeekStartDesc(UUID userId, LocalDate weekStart);

    /** Bulk-deletes every check-in week a user ever had — used by account deletion ({@code com.kcalma.account.AccountService}). */
    @Modifying
    @Query("DELETE FROM TdeeCheckin t WHERE t.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
