package com.kcalma.water;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Maps to app.water_log (V12__water_log.sql): the day's running water total in milliliters, one
 * row per user per calendar day, keyed directly by (user_id, entry_date) — see {@link WaterLogId}.
 * {@code ml} is always &gt;= 0 (DB CHECK, mirrored in {@code WaterLogService#applyDelta}, which
 * floors a negative delta at 0 rather than storing it).
 */
@Entity
@Table(name = "water_log")
@IdClass(WaterLogId.class)
public class WaterLog {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Id
    @Column(name = "entry_date", updatable = false, nullable = false)
    private LocalDate entryDate;

    @Column(name = "ml", nullable = false)
    private int ml;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected WaterLog() {
        // JPA
    }

    public WaterLog(UUID userId, LocalDate entryDate, int ml) {
        this.userId = userId;
        this.entryDate = entryDate;
        this.ml = ml;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public int getMl() {
        return ml;
    }

    /** The only editable field — see {@code WaterLogService#applyDelta}. */
    public void setMl(int ml) {
        this.ml = ml;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
