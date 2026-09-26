package com.kcalma.food.reference;

import java.math.BigDecimal;

/**
 * One parsed row of the compact food_reference CSV bundled at
 * {@code src/main/resources/usda/food_reference.csv.gz} — see {@code tools/usda/build_food_reference.py}
 * for the generator and {@link FoodReferenceCsvParser} for the reader.
 */
public record FoodReferenceCsvRow(
        long fdcId,
        String description,
        String dataType,
        BigDecimal kcalPer100,
        BigDecimal proteinPer100,
        BigDecimal fatPer100,
        BigDecimal carbsPer100,
        BigDecimal fiberPer100,
        BigDecimal sugarPer100,
        BigDecimal sodiumMgPer100,
        String searchName) {}
