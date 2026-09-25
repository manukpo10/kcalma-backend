package com.kcalma.weight;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** Maps to app.weight_entry (V3__weight_entry.sql). At most one row per user per calendar day. */
@Entity
@Table(name = "weight_entry")
public class WeightEntry {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "entry_date", nullable = false, updatable = false)
    private LocalDate entryDate;

    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightKg;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected WeightEntry() {
        // JPA
    }

    public WeightEntry(UUID userId, LocalDate entryDate, BigDecimal weightKg) {
        this.userId = userId;
        this.entryDate = entryDate;
        this.weightKg = weightKg;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    /** The only editable field: upserting a date again overwrites the weigh-in, never the date. */
    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
