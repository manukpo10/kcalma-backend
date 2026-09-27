package com.kcalma.profile.dto;

import com.kcalma.profile.EnergySource;
import com.kcalma.profile.NutritionCalculator;
import com.kcalma.profile.ProteinBasis;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code energySource}/{@code adaptiveSince} (sprint 3a, additive) say whether {@link #calories}
 * and the rest of these targets came from the Mifflin-St Jeor formula or from an accepted weekly
 * check-in — see {@link EnergySource} and {@code com.kcalma.checkin.AdaptiveTdeeService}.
 */
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
        List<NoteResponse> notes,
        EnergySource energySource,
        LocalDate adaptiveSince) {

    public static NutritionTargetsResponse from(
            NutritionCalculator.NutritionTargets targets, EnergySource energySource, LocalDate adaptiveSince) {
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
                targets.notes().stream().map(NoteResponse::from).toList(),
                energySource,
                adaptiveSince);
    }

    /** One {@code {code, message}} entry; {@code code} is a {@code NutritionCalculator.NoteCode} name. */
    public record NoteResponse(String code, String message) {
        public static NoteResponse from(NutritionCalculator.TargetNote note) {
            return new NoteResponse(note.code().name(), note.message());
        }
    }
}
