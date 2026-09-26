package com.kcalma.food.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Maps to app.food_reference (V4__enable_pg_trgm_and_food_reference.sql): read-only USDA
 * FoodData Central reference data (SR Legacy + Foundation Foods + FNDDS survey foods), loaded by
 * the {@code V5__LoadFoodReferenceData} Flyway Java migration — never written through JPA.
 */
@Entity
@Table(name = "food_reference")
public class FoodReference {

    @Id
    @Column(name = "fdc_id", updatable = false, nullable = false)
    private Long fdcId;

    @Column(name = "description", nullable = false, updatable = false)
    private String description;

    @Column(name = "data_type", nullable = false, updatable = false, length = 20)
    private String dataType;

    @Column(name = "kcal_per_100", nullable = false, updatable = false, precision = 7, scale = 2)
    private BigDecimal kcalPer100;

    @Column(name = "protein_per_100", nullable = false, updatable = false, precision = 7, scale = 2)
    private BigDecimal proteinPer100;

    @Column(name = "fat_per_100", nullable = false, updatable = false, precision = 7, scale = 2)
    private BigDecimal fatPer100;

    @Column(name = "carbs_per_100", nullable = false, updatable = false, precision = 7, scale = 2)
    private BigDecimal carbsPer100;

    @Column(name = "fiber_per_100", nullable = false, updatable = false, precision = 7, scale = 2)
    private BigDecimal fiberPer100;

    @Column(name = "sugar_per_100", nullable = false, updatable = false, precision = 7, scale = 2)
    private BigDecimal sugarPer100;

    @Column(name = "sodium_mg_per_100", nullable = false, updatable = false, precision = 8, scale = 2)
    private BigDecimal sodiumMgPer100;

    @Column(name = "search_name", nullable = false, updatable = false)
    private String searchName;

    protected FoodReference() {
        // JPA
    }

    /**
     * Package-visible: lets tests build an instance directly. In production this entity is only
     * ever hydrated by Hibernate from {@link FoodReferenceRepository}'s native query rows — it is
     * never constructed or persisted by application code (see {@code V5__LoadFoodReferenceData}).
     */
    FoodReference(
            Long fdcId,
            String description,
            String dataType,
            BigDecimal kcalPer100,
            BigDecimal proteinPer100,
            BigDecimal fatPer100,
            BigDecimal carbsPer100,
            BigDecimal fiberPer100,
            BigDecimal sugarPer100,
            BigDecimal sodiumMgPer100,
            String searchName) {
        this.fdcId = fdcId;
        this.description = description;
        this.dataType = dataType;
        this.kcalPer100 = kcalPer100;
        this.proteinPer100 = proteinPer100;
        this.fatPer100 = fatPer100;
        this.carbsPer100 = carbsPer100;
        this.fiberPer100 = fiberPer100;
        this.sugarPer100 = sugarPer100;
        this.sodiumMgPer100 = sodiumMgPer100;
        this.searchName = searchName;
    }

    public Long getFdcId() {
        return fdcId;
    }

    public String getDescription() {
        return description;
    }

    public String getDataType() {
        return dataType;
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

    public String getSearchName() {
        return searchName;
    }
}
