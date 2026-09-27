package com.kcalma.profile.dto;

import com.kcalma.profile.NutritionCalculator;
import com.kcalma.profile.ProteinBasis;
import java.util.List;

public record NutritionTargetsResponse(
        int calories,
        boolean floorApplied,
        int proteinGrams,
        int fatGrams,
        int carbGrams,
        int fiberGrams,
        int sugarMaxGrams,
        int sodiumMaxMg,
        int waterMl,
        double weeklyRateKg,
        double dailyAdjustmentKcal,
        ProteinBasis proteinBasis,
        double proteinBasisKg,
        Double leanMassKg,
        List<NoteResponse> notes) {

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
                targets.waterMl(),
                targets.weeklyRateKg(),
                targets.dailyAdjustmentKcal(),
                targets.proteinBasis(),
                targets.proteinBasisKg(),
                targets.leanMassKg(),
                targets.notes().stream().map(NoteResponse::from).toList());
    }

    /** One {@code {code, message}} entry; {@code code} is a {@code NutritionCalculator.NoteCode} name. */
    public record NoteResponse(String code, String message) {
        public static NoteResponse from(NutritionCalculator.TargetNote note) {
            return new NoteResponse(note.code().name(), note.message());
        }
    }
}
