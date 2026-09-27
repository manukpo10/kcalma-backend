package com.kcalma.reminder;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ReminderLogRepository extends JpaRepository<ReminderLog, ReminderLogId> {

    /**
     * Atomically claims one (user, reminder, day) occurrence: an {@code INSERT ... ON CONFLICT DO
     * NOTHING} against the composite primary key, so a restart or two {@link ReminderScheduler}
     * ticks racing each other resolve at the database itself, not in application code. {@link
     * ReminderScheduler} calls this BEFORE sending, and only actually sends when it returns {@code
     * 1} (this call landed the row) rather than {@code 0} (some other call already had) — claim
     * first, send second, never the other way around.
     *
     * <p>{@code @Transactional} is required here, not optional: unlike {@code save}/{@code
     * delete} (inherited from {@code SimpleJpaRepository}, which is transactional at the class
     * level), a custom {@code @Modifying} query method has no transaction of its own unless one is
     * declared — without this, the {@code executeUpdate()} call fails outright with "No active
     * transaction for update or delete query" whenever the caller isn't already inside one.
     */
    @Transactional
    @Modifying
    @Query(
            value = "INSERT INTO app.reminder_log (user_id, reminder_key, sent_on) VALUES (:userId, :reminderKey, :sentOn) "
                    + "ON CONFLICT (user_id, reminder_key, sent_on) DO NOTHING",
            nativeQuery = true)
    int claim(@Param("userId") UUID userId, @Param("reminderKey") String reminderKey, @Param("sentOn") LocalDate sentOn);

    /** Bulk-deletes every dedupe-ledger row a user ever had — used by account deletion ({@code com.kcalma.account.AccountService}). */
    @Transactional
    @Modifying
    @Query("DELETE FROM ReminderLog r WHERE r.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
