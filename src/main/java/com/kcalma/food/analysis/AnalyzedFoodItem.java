package com.kcalma.food.analysis;

/** One food item detected in a photo, with its estimated portion and per-100g nutrition. */
public record AnalyzedFoodItem(
        String name,
        double grams,
        double kcalPer100,
        double proteinPer100,
        double fatPer100,
        double carbsPer100,
        double fiberPer100,
        double sugarPer100,
        double sodiumMgPer100) {}
