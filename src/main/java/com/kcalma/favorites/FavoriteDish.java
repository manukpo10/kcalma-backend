package com.kcalma.favorites;

import com.kcalma.food.FoodEntryIngredient;
import com.kcalma.food.FoodSource;
import com.kcalma.food.MealType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Maps to app.favorite_dish (V11__favorite_dish.sql): a Dish snapshot (same fields as {@code
 * com.kcalma.food.FoodEntry}, minus {@code entryDate}) the user pinned for one-tap re-logging.
 * Upserted by normalized name per user (see {@code FavoriteDishService}) — the same
 * upsert-by-normalized-name shape as {@code com.kcalma.food.reference.UserFood}, just keyed to the
 * whole dish rather than one ingredient. {@code mealType} is an optional hint, never required.
 */
@Entity
@Table(name = "favorite_dish")
public class FavoriteDish {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "normalized_name", nullable = false, updatable = false)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", length = 20)
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
    @Column(name = "source", nullable = false, length = 10)
    private FoodSource source;

    @Column(name = "fdc_id")
    private Long fdcId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ingredients")
    private List<FoodEntryIngredient> ingredients;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected FavoriteDish() {
        // JPA
    }

    public FavoriteDish(UUID userId, String normalizedName) {
        this.userId = userId;
        this.normalizedName = normalizedName;
    }

    /**
     * Refreshes every dish field at once (upsert re-save, either from a re-favorited entry or an
     * edited Dish payload) — kept as one method, same reasoning as {@code FoodEntry#setPer100}: the
     * fields can never drift out of sync mid-update.
     */
    public void applyDish(
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

    public String getNormalizedName() {
        return normalizedName;
    }

    public MealType getMealType() {
        return mealType;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getGrams() {
        return grams;
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

    /** {@code null} only if the dish snapshot came from a legacy pre-V9 {@code FoodEntry} with no breakdown of its own. */
    public List<FoodEntryIngredient> getIngredients() {
        return ingredients;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
