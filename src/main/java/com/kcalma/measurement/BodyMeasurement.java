package com.kcalma.measurement;

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

/**
 * Maps to app.body_measurement (V13__body_measurement.sql): optional body measurements (waist,
 * hip, chest, arm, thigh, body fat %, muscle mass), at most one row per user per calendar day —
 * same shape as {@code com.kcalma.weight.WeightEntry}. Every measurement field is individually
 * optional; "at least one required" is enforced in {@code MeasurementController}, not here (a
 * plain entity/DB CHECK can't express it cleanly against 7 independent nullable columns).
 */
@Entity
@Table(name = "body_measurement")
public class BodyMeasurement {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "measured_on", nullable = false, updatable = false)
    private LocalDate measuredOn;

    @Column(name = "waist_cm", precision = 5, scale = 2)
    private BigDecimal waistCm;

    @Column(name = "hip_cm", precision = 5, scale = 2)
    private BigDecimal hipCm;

    @Column(name = "chest_cm", precision = 5, scale = 2)
    private BigDecimal chestCm;

    @Column(name = "arm_cm", precision = 5, scale = 2)
    private BigDecimal armCm;

    @Column(name = "thigh_cm", precision = 5, scale = 2)
    private BigDecimal thighCm;

    @Column(name = "body_fat_pct", precision = 4, scale = 1)
    private BigDecimal bodyFatPct;

    @Column(name = "muscle_mass_kg", precision = 5, scale = 2)
    private BigDecimal muscleMassKg;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected BodyMeasurement() {
        // JPA
    }

    public BodyMeasurement(UUID userId, LocalDate measuredOn) {
        this.userId = userId;
        this.measuredOn = measuredOn;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getMeasuredOn() {
        return measuredOn;
    }

    public BigDecimal getWaistCm() {
        return waistCm;
    }

    public void setWaistCm(BigDecimal waistCm) {
        this.waistCm = waistCm;
    }

    public BigDecimal getHipCm() {
        return hipCm;
    }

    public void setHipCm(BigDecimal hipCm) {
        this.hipCm = hipCm;
    }

    public BigDecimal getChestCm() {
        return chestCm;
    }

    public void setChestCm(BigDecimal chestCm) {
        this.chestCm = chestCm;
    }

    public BigDecimal getArmCm() {
        return armCm;
    }

    public void setArmCm(BigDecimal armCm) {
        this.armCm = armCm;
    }

    public BigDecimal getThighCm() {
        return thighCm;
    }

    public void setThighCm(BigDecimal thighCm) {
        this.thighCm = thighCm;
    }

    public BigDecimal getBodyFatPct() {
        return bodyFatPct;
    }

    public void setBodyFatPct(BigDecimal bodyFatPct) {
        this.bodyFatPct = bodyFatPct;
    }

    public BigDecimal getMuscleMassKg() {
        return muscleMassKg;
    }

    public void setMuscleMassKg(BigDecimal muscleMassKg) {
        this.muscleMassKg = muscleMassKg;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
