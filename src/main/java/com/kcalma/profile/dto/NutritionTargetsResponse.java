package com.kcalma.profile.dto;

import com.kcalma.profile.NutritionCalculator;

public record NutritionTargetsResponse(
        int calories,
        boolean floorApplied,
        int proteinGrams,
        int fatGrams,
        int carbGrams,
        int fiberGrams,
        int sugarMaxGrams,
        int sodiumMaxMg,
        int waterMl) {

    public static NutritionTargetsResponse from(NutritionCalculator.NutritionTargets targets) {
        return new NutritionTargetsResponse(
                targets.calories(),
                targets.floorApplied(),
                targets.proteinGrams(),
                targets.fatGrams(),
                targets.carbGrams(),
                targets.fiberGrams(),
                targets.sugarMaxGrams(),
                targets.sodiumMaxMg(),
                targets.waterMl());
    }
}
