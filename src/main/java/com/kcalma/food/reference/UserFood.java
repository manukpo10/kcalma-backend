package com.kcalma.food.reference;

import com.kcalma.food.NutritionMath;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Maps to app.user_food (V6__user_food.sql): the caller's personal food library — first priority
 * in {@link FoodReferenceMatcher}. Seeded/refreshed whenever a food entry is saved (see {@code
 * com.kcalma.food.FoodEntryService}).
 */
@Entity
@Table(name = "user_food")
public class UserFood {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "normalized_name", nullable = false, updatable = false)
    private String normalizedName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

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
    private UserFoodSource source;

    @Column(name = "fdc_id")
    private Long fdcId;

    @Column(name = "use_count", nullable = false)
    private int useCount;

    @Column(name = "last_used_at", nullable = false)
    private OffsetDateTime lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserFood() {
        // JPA
    }

    public UserFood(
            UUID userId,
            String normalizedName,
            String displayName,
            NutritionMath.Per100 per100,
            UserFoodSource source,
            Long fdcId) {
        this.userId = userId;
        this.normalizedName = normalizedName;
        this.useCount = 0;
        recordUse(displayName, per100, source, fdcId);
    }

    /** Bumps use_count/last_used_at and refreshes the cached values to the ones just used. */
    public final void recordUse(String displayName, NutritionMath.Per100 per100, UserFoodSource source, Long fdcId) {
        this.displayName = displayName;
        this.kcalPer100 = bd(per100.kcal());
        this.proteinPer100 = bd(per100.protein());
        this.fatPer100 = bd(per100.fat());
        this.carbsPer100 = bd(per100.carbs());
        this.fiberPer100 = bd(per100.fiber());
        this.sugarPer100 = bd(per100.sugar());
        this.sodiumMgPer100 = bd(per100.sodiumMg());
        this.source = source;
        this.fdcId = fdcId;
        this.useCount++;
        this.lastUsedAt = OffsetDateTime.now();
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
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

    public String getDisplayName() {
        return displayName;
    }

    public NutritionMath.Per100 toPer100() {
        return new NutritionMath.Per100(
                kcalPer100.doubleValue(),
                proteinPer100.doubleValue(),
                fatPer100.doubleValue(),
                carbsPer100.doubleValue(),
                fiberPer100.doubleValue(),
                sugarPer100.doubleValue(),
                sodiumMgPer100.doubleValue());
    }

    public UserFoodSource getSource() {
        return source;
    }

    public Long getFdcId() {
        return fdcId;
    }

    public int getUseCount() {
        return useCount;
    }

    public OffsetDateTime getLastUsedAt() {
        return lastUsedAt;
    }
}
