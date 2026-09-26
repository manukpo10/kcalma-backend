package com.kcalma.food;

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
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Maps to app.food_entry (V2__food_entry.sql). One row per logged DISH — {@code grams}/{@code
 * kcalPer100}..{@code sodiumMgPer100}/{@code source}/{@code fdcId} are always the dish's own
 * derived values (see {@code FoodEntryService}), and {@code ingredients} (V9__food_entry_ingredients.sql,
 * nullable) is its resolved breakdown. Entries logged before V9 shipped have {@code ingredients ==
 * null} and must keep working exactly as before — every read path treats {@code null} as "no
 * breakdown to show", never as an error.
 */
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

    /**
     * Dish-level source (may be {@code MIXED} — see {@link FoodSource#combine}). No longer
     * {@code updatable = false}: editing a dish's ingredients via PATCH (see {@code
     * FoodEntryService#update}) can change which source the recomputed dish aggregate falls under.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private FoodSource source;

    /**
     * USDA FoodData Central id this entry was matched against, or {@code null} (PERSONAL match with
     * no USDA origin, ESTIMATED, MANUAL, MIXED, or any multi-ingredient dish). No longer
     * {@code updatable = false} — see {@link #source}.
     */
    @Column(name = "fdc_id")
    private Long fdcId;

    /**
     * The resolved ingredient breakdown (see {@link FoodEntryIngredient}), persisted as a single
     * {@code jsonb} value (V9__food_entry_ingredients.sql). {@code null} for entries logged before
     * V9 — everything reading this column must treat that as "no breakdown", not an error.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ingredients")
    private List<FoodEntryIngredient> ingredients;

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
            Long fdcId,
            List<FoodEntryIngredient> ingredients) {
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
        this.ingredients = ingredients;
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

    /**
     * PATCH-editable: replaces every per-100g field at once from a freshly recomputed dish
     * aggregate (see {@code FoodEntryService#applyIngredientEdit}) — kept as one method rather than
     * 7 setters so the fields can never drift out of sync with each other mid-update.
     */
    public void setPer100(NutritionMath.Per100 per100) {
        this.kcalPer100 = bd(per100.kcal());
        this.proteinPer100 = bd(per100.protein());
        this.fatPer100 = bd(per100.fat());
        this.carbsPer100 = bd(per100.carbs());
        this.fiberPer100 = bd(per100.fiber());
        this.sugarPer100 = bd(per100.sugar());
        this.sodiumMgPer100 = bd(per100.sodiumMg());
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public FoodSource getSource() {
        return source;
    }

    /** PATCH-editable: see {@link #source}. */
    public void setSource(FoodSource source) {
        this.source = source;
    }

    public Long getFdcId() {
        return fdcId;
    }

    /** PATCH-editable: see {@link #fdcId}. */
    public void setFdcId(Long fdcId) {
        this.fdcId = fdcId;
    }

    /** {@code null} for any entry logged before V9 shipped — never an empty list for those rows. */
    public List<FoodEntryIngredient> getIngredients() {
        return ingredients;
    }

    /** PATCH-editable: see {@link #ingredients}. */
    public void setIngredients(List<FoodEntryIngredient> ingredients) {
        this.ingredients = ingredients;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
