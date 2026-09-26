package com.kcalma.food;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** Maps to app.food_entry (V2__food_entry.sql). One row per logged food item. */
@Entity
@Table(name = "food_entry")
public class FoodEntry {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 20)
    private MealType mealType;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "grams", nullable = false, precision = 7, scale = 2)
    private BigDecimal grams;

    @Column(name = "kcal_per_100", nullable = false, precision = 7, scale = 2)
    private BigDecimal kcalPer100;

    @Column(name = "protein_per_100", nullable = false, precision = 7, scale = 2)
    private BigDecimal proteinPer100;

    @Column(name = "fat_per_100", nullable = false, precision = 7, scale = 2)
    private BigDecimal fatPer100;

    @Column(name = "carbs_per_100", nullable = false, precision = 7, scale = 2)
    private BigDecimal carbsPer100;

    @Column(name = "fiber_per_100", nullable = false, precision = 7, scale = 2)
    private BigDecimal fiberPer100;

    @Column(name = "sugar_per_100", nullable = false, precision = 7, scale = 2)
    private BigDecimal sugarPer100;

    @Column(name = "sodium_mg_per_100", nullable = false, precision = 8, scale = 2)
    private BigDecimal sodiumMgPer100;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10, updatable = false)
    private FoodSource source;

    /** USDA FoodData Central id this entry was matched against, or {@code null} (PERSONAL match with no USDA origin, ESTIMATED, or MANUAL). */
    @Column(name = "fdc_id", updatable = false)
    private Long fdcId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected FoodEntry() {
        // JPA
    }

    public FoodEntry(
            UUID userId,
            LocalDate entryDate,
            MealType mealType,
            String name,
            BigDecimal grams,
            BigDecimal kcalPer100,
            BigDecimal proteinPer100,
            BigDecimal fatPer100,
            BigDecimal carbsPer100,
            BigDecimal fiberPer100,
            BigDecimal sugarPer100,
            BigDecimal sodiumMgPer100,
            FoodSource source,
            Long fdcId) {
        this.userId = userId;
        this.entryDate = entryDate;
        this.mealType = mealType;
        this.name = name;
        this.grams = grams;
        this.kcalPer100 = kcalPer100;
        this.proteinPer100 = proteinPer100;
        this.fatPer100 = fatPer100;
        this.carbsPer100 = carbsPer100;
        this.fiberPer100 = fiberPer100;
        this.sugarPer100 = sugarPer100;
        this.sodiumMgPer100 = sodiumMgPer100;
        this.source = source;
        this.fdcId = fdcId;
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

    public MealType getMealType() {
        return mealType;
    }

    /** PATCH-editable: which meal this item is grouped under. */
    public void setMealType(MealType mealType) {
        this.mealType = mealType;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getGrams() {
        return grams;
    }

    /** PATCH-editable: the portion size: totals are re-derived from this, never stored. */
    public void setGrams(BigDecimal grams) {
        this.grams = grams;
    }

    public BigDecimal getKcalPer100() {
        return kcalPer100;
    }

    public BigDecimal getProteinPer100() {
        return proteinPer100;
    }

    public BigDecimal getFatPer100() {
        return fatPer100;
    }

    public BigDecimal getCarbsPer100() {
        return carbsPer100;
    }

    public BigDecimal getFiberPer100() {
        return fiberPer100;
    }

    public BigDecimal getSugarPer100() {
        return sugarPer100;
    }

    public BigDecimal getSodiumMgPer100() {
        return sodiumMgPer100;
    }

    public FoodSource getSource() {
        return source;
    }

    public Long getFdcId() {
        return fdcId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
