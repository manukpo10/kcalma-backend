package com.kcalma.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Maps to app.user_profile (V1__baseline.sql, extended by V3/V10). Primary key is the Supabase auth user id (JWT sub). */
@Entity
@Table(name = "user_profile")
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false, length = 10)
    private Sex sex;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "height_cm", nullable = false)
    private Integer heightCm;

    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", nullable = false, length = 30)
    private ActivityLevel activityLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal", nullable = false, length = 20)
    private Goal goal;

    /** Optional target weight set from the profile screen; null means no goal set yet. */
    @Column(name = "goal_weight_kg", precision = 5, scale = 2)
    private BigDecimal goalWeightKg;

    /** Required for every goal except RECOMP/MAINTAIN (see {@link Goal#requiresPace()}); null otherwise. */
    @Enumerated(EnumType.STRING)
    @Column(name = "pace", length = 10)
    private Pace pace;

    @Enumerated(EnumType.STRING)
    @Column(name = "diet_style", nullable = false, length = 20)
    private DietStyle dietStyle = DietStyle.BALANCED;

    /**
     * Persisted as a Postgres {@code text[]} (V10 migration) — Hibernate maps enum lists to JSON by
     * default, but a CHECK constraint on a plain array's elements is far simpler than one on JSON
     * array elements (Postgres CHECK constraints can't contain subqueries), so this field stores the
     * raw enum names and {@link #getDietaryRestrictions()}/{@link #setDietaryRestrictions(List)}
     * convert at the boundary.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "dietary_restrictions", nullable = false, columnDefinition = "text[]")
    private List<String> dietaryRestrictions = new ArrayList<>();

    @Column(name = "strength_training", nullable = false)
    private boolean strengthTraining = false;

    /** Body fat % (3-70) if the user knows it — drives the LEAN_MASS protein basis when present. */
    @Column(name = "body_fat_pct", precision = 4, scale = 1)
    private BigDecimal bodyFatPct;

    @Column(name = "body_fat_measured_on")
    private LocalDate bodyFatMeasuredOn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserProfile() {
        // JPA
    }

    public UserProfile(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Sex getSex() {
        return sex;
    }

    public void setSex(Sex sex) {
        this.sex = sex;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public Integer getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(Integer heightCm) {
        this.heightCm = heightCm;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public ActivityLevel getActivityLevel() {
        return activityLevel;
    }

    public void setActivityLevel(ActivityLevel activityLevel) {
        this.activityLevel = activityLevel;
    }

    public Goal getGoal() {
        return goal;
    }

    public void setGoal(Goal goal) {
        this.goal = goal;
    }

    public BigDecimal getGoalWeightKg() {
        return goalWeightKg;
    }

    public void setGoalWeightKg(BigDecimal goalWeightKg) {
        this.goalWeightKg = goalWeightKg;
    }

    public Pace getPace() {
        return pace;
    }

    public void setPace(Pace pace) {
        this.pace = pace;
    }

    public DietStyle getDietStyle() {
        return dietStyle;
    }

    public void setDietStyle(DietStyle dietStyle) {
        this.dietStyle = dietStyle;
    }

    public List<DietaryRestriction> getDietaryRestrictions() {
        return dietaryRestrictions.stream().map(DietaryRestriction::valueOf).toList();
    }

    public void setDietaryRestrictions(List<DietaryRestriction> dietaryRestrictions) {
        this.dietaryRestrictions =
                dietaryRestrictions.stream().map(DietaryRestriction::name).collect(Collectors.toCollection(ArrayList::new));
    }

    public boolean isStrengthTraining() {
        return strengthTraining;
    }

    public void setStrengthTraining(boolean strengthTraining) {
        this.strengthTraining = strengthTraining;
    }

    public BigDecimal getBodyFatPct() {
        return bodyFatPct;
    }

    public void setBodyFatPct(BigDecimal bodyFatPct) {
        this.bodyFatPct = bodyFatPct;
    }

    public LocalDate getBodyFatMeasuredOn() {
        return bodyFatMeasuredOn;
    }

    public void setBodyFatMeasuredOn(LocalDate bodyFatMeasuredOn) {
        this.bodyFatMeasuredOn = bodyFatMeasuredOn;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
