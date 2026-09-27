package com.kcalma.checkin;

import com.kcalma.profile.ActivityLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Maps to app.tdee_checkin (V14__tdee_checkin.sql). One row per user per ISO week (see {@link
 * #weekStart}). {@link #status} PENDING/INSUFFICIENT_DATA are "open" — {@link #recompute} rewrites
 * every metric field in place on each GET while the week stays open; {@link #accept}/{@link
 * #dismiss} "close" it, after which {@code CheckinService} never calls {@code recompute} on this
 * row again (see that class for the open/closed distinction).
 */
@Entity
@Table(name = "tdee_checkin")
public class TdeeCheckin {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Always a Monday, zoned to {@code app.timezone} — see {@code CheckinService#currentWeekStart}. */
    @Column(name = "week_start", nullable = false, updatable = false)
    private LocalDate weekStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CheckinStatus status;

    @Column(name = "window_start", nullable = false)
    private LocalDate windowStart;

    @Column(name = "window_end", nullable = false)
    private LocalDate windowEnd;

    @Column(name = "complete_days")
    private Integer completeDays;

    @Column(name = "weigh_ins")
    private Integer weighIns;

    @Column(name = "avg_intake_kcal")
    private Integer avgIntakeKcal;

    @Column(name = "trend_change_kg", precision = 6, scale = 2)
    private BigDecimal trendChangeKg;

    @Column(name = "formula_tdee")
    private Integer formulaTdee;

    @Column(name = "estimated_tdee")
    private Integer estimatedTdee;

    @Column(name = "proposed_tdee")
    private Integer proposedTdee;

    /** Set only by {@link #accept} — the adaptive TDEE {@code AdaptiveTdeeService} then blends into targets. */
    @Column(name = "applied_tdee")
    private Integer appliedTdee;

    /** Set only by {@link #accept}, alongside {@link #appliedTdee} — see the migration's own comment for why. */
    @Enumerated(EnumType.STRING)
    @Column(name = "applied_activity_level", length = 30)
    private ActivityLevel appliedActivityLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", length = 10)
    private Confidence confidence;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    protected TdeeCheckin() {
        // JPA
    }

    public TdeeCheckin(UUID userId, LocalDate weekStart) {
        this.userId = userId;
        this.weekStart = weekStart;
        this.status = CheckinStatus.PENDING;
    }

    /**
     * Rewrites every metric field from a fresh {@link TdeeAdaptationCalculator} run — the only way
     * {@link #status} ever becomes {@code PENDING} or {@code INSUFFICIENT_DATA}. Never call this on
     * a row that {@link #isClosed()} — see {@code CheckinService#currentWeek}.
     */
    public void recompute(LocalDate windowStart, LocalDate windowEnd, int formulaTdee, TdeeAdaptationCalculator.Result result) {
        this.status = result.status();
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.completeDays = result.completeDays();
        this.weighIns = result.weighIns();
        this.avgIntakeKcal = result.avgIntakeKcal();
        this.trendChangeKg = toScaledBigDecimal(result.trendChangeKg());
        this.formulaTdee = formulaTdee;
        this.estimatedTdee = result.estimatedTdee();
        this.proposedTdee = result.proposedTdee();
        this.confidence = result.confidence();
    }

    /** Closes the week: {@link #proposedTdee} becomes the new adaptive TDEE (see {@link #appliedTdee}). */
    public void accept(ActivityLevel currentActivityLevel, OffsetDateTime decidedAt) {
        this.status = CheckinStatus.ACCEPTED;
        this.appliedTdee = this.proposedTdee;
        this.appliedActivityLevel = currentActivityLevel;
        this.decidedAt = decidedAt;
    }

    /** Closes the week without applying anything — targets keep whatever TDEE they were already using. */
    public void dismiss(OffsetDateTime decidedAt) {
        this.status = CheckinStatus.DISMISSED;
        this.decidedAt = decidedAt;
    }

    public boolean isClosed() {
        return status == CheckinStatus.ACCEPTED || status == CheckinStatus.DISMISSED;
    }

    public boolean isPending() {
        return status == CheckinStatus.PENDING;
    }

    private static BigDecimal toScaledBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public CheckinStatus getStatus() {
        return status;
    }

    public LocalDate getWindowStart() {
        return windowStart;
    }

    public LocalDate getWindowEnd() {
        return windowEnd;
    }

    public Integer getCompleteDays() {
        return completeDays;
    }

    public Integer getWeighIns() {
        return weighIns;
    }

    public Integer getAvgIntakeKcal() {
        return avgIntakeKcal;
    }

    public Double getTrendChangeKg() {
        return trendChangeKg == null ? null : trendChangeKg.doubleValue();
    }

    public Integer getFormulaTdee() {
        return formulaTdee;
    }

    public Integer getEstimatedTdee() {
        return estimatedTdee;
    }

    public Integer getProposedTdee() {
        return proposedTdee;
    }

    public Integer getAppliedTdee() {
        return appliedTdee;
    }

    public ActivityLevel getAppliedActivityLevel() {
        return appliedActivityLevel;
    }

    public Confidence getConfidence() {
        return confidence;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }
}
