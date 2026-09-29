package com.kcalma.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure domain service: derives daily nutrition targets from onboarding inputs.
 * No Spring, no I/O — safe to unit test exhaustively without a container.
 *
 * <p>Pipeline: Mifflin-St Jeor BMR x activity factor -&gt; TDEE -&gt; weekly rate of change (%% of
 * body weight, by goal x pace; RECOMP uses a fixed -10%% of TDEE instead) -&gt; daily calorie
 * adjustment (capped at a 25%% TDEE deficit) -&gt; calorie floor clamp, deficit goals only, never
 * above TDEE (see {@link Goal#isDeficit()}) -&gt; the rate/adjustment are recomputed against
 * whatever calorie target the floor actually left, so display and the goal-date projection never
 * show a rate the calories don't back up -&gt; protein basis (lean mass, BMI-adjusted weight, or
 * body weight) x g/kg (by goal x basis x whether the profile trains with weights, +0.3 g/kg for
 * HIGH_PROTEIN) -&gt; fat/carb split (by diet style) -&gt; fiber (floored at 25 g) /sugar (overridden
 * for KETO) -&gt; sodium cap -&gt; water (35 ml/kg of the same BMI-adjusted weight above BMI 30, else
 * body weight; clamped to a safe range).
 *
 * <p>Evidence behind the protein g/kg tables (see {@link ProteinBasis}): Helms et al. 2014, Iraki
 * et al. 2019, Morton et al. 2018, and the ISSN 2017 position stand for the resistance-trained
 * tables; see {@link ProteinBasis} for the untrained (non-strength-training) table's own evidence.
 */
public final class NutritionCalculator {

    private static final double FAT_PERCENT_OF_CALORIES = 0.30;
    private static final double MIN_FAT_GRAMS_PER_KG_BASIS = 0.6;
    private static final double LOW_CARB_PERCENT_OF_CALORIES = 0.25;
    private static final double LOW_CARB_MAX_GRAMS = 130.0;
    private static final double KETO_CARB_GRAMS = 30.0;
    private static final double KETO_FIBER_GRAMS = 20.0;
    private static final double KETO_SUGAR_MAX_PERCENT_OF_CALORIES = 0.05;
    private static final double FIBER_GRAMS_PER_1000_KCAL = 14.0;
    private static final double MIN_FIBER_GRAMS = 25.0;
    /**
     * Total sugars (USDA {@code sugars, total}) as consumed/logged by this app — not "free" or
     * "added" sugars (WHO's own, stricter 10%-of-calories guidance is about added sugars only, a
     * quantity this app has no reliable way to isolate from a logged food's total sugar content).
     * 18% of calories / 4 kcal-per-gram mirrors the EU reference intake of 90 g per 2000 kcal.
     */
    private static final double TOTAL_SUGAR_MAX_PERCENT_OF_CALORIES = 0.18;
    private static final int SODIUM_MAX_MG = 2000;
    private static final double WATER_ML_PER_KG = 35.0;
    private static final double WATER_ML_MIN = 1500.0;
    private static final double WATER_ML_MAX = 3500.0;
    private static final double FEMALE_CALORIE_FLOOR = 1200.0;
    private static final double MALE_CALORIE_FLOOR = 1500.0;
    private static final double DEFICIT_CAP_PERCENT_OF_TDEE = 0.25;
    private static final double RECOMP_DEFICIT_PERCENT_OF_TDEE = 0.10;
    private static final double HIGH_PROTEIN_BONUS_GRAMS_PER_KG = 0.3;
    private static final double BMI_ADJUSTED_WEIGHT_THRESHOLD = 30.0;
    private static final double REFERENCE_BMI_FOR_ADJUSTED_WEIGHT = 25.0;
    private static final double ADJUSTED_WEIGHT_LEAN_FRACTION = 0.4;

    /** Energy per kg of body-mass change, used both ways between {@code weeklyRateKg} and {@code dailyAdjustmentKcal}. */
    private static final double KCAL_PER_KG_OF_BODY_MASS = 7700.0;

    public NutritionTargets calculate(Input input) {
        return calculate(input, tdee(input));
    }

    /**
     * Same pipeline as {@link #calculate(Input)}, but with an explicit TDEE in place of BMR x
     * activity — every other rule (goal/pace adjustment, the 25% deficit cap, the floor,
     * protein/macros) stays identical. Used by the adaptive check-in ({@code
     * com.kcalma.checkin.CheckinService} via {@code com.kcalma.checkin.AdaptiveTdeeService}) once
     * a week's adaptive TDEE has been accepted, so targets blend in that smoothed real-world
     * number instead of the formula's estimate.
     */
    public NutritionTargets calculate(Input input, double tdee) {
        Objects.requireNonNull(input.dietStyle(), "dietStyle");
        if (input.goal().requiresPace() && input.pace() == null) {
            throw new IllegalArgumentException("pace is required for goal " + input.goal());
        }

        RateResult rate = weeklyRate(input.goal(), input.pace(), input.weightKg(), tdee);
        double rateAdjustedCalories = tdee + rate.dailyAdjustmentKcal();

        // Fix 1: the safety floor only makes sense for goals that actually run a deficit (RECOMP's
        // fixed -10% included) -- MAINTAIN and the gain goals never clamp up to it. Even for a
        // deficit goal, the floor itself is never allowed to sit above TDEE: when someone's TDEE is
        // already below the sex-based floor, there's no safe deficit to apply at all, so the target
        // falls back to TDEE (maintenance) with DEFICIT_NOT_POSSIBLE instead of FLOOR_APPLIED.
        boolean floorApplied = false;
        boolean deficitNotPossible = false;
        double targetCalories;
        if (input.goal().isDeficit()) {
            double sexFloor = input.sex() == Sex.FEMALE ? FEMALE_CALORIE_FLOOR : MALE_CALORIE_FLOOR;
            double floor = Math.min(sexFloor, tdee);
            targetCalories = Math.max(rateAdjustedCalories, floor);
            deficitNotPossible = tdee < sexFloor;
            floorApplied = !deficitNotPossible && rateAdjustedCalories < floor;
        } else {
            targetCalories = rateAdjustedCalories;
        }

        // Fix 2: once the floor (if any) has settled the actual calorie target, the rate/adjustment
        // are recomputed FROM that target rather than reused from the pre-floor pace math -- so a
        // profile whose calories barely moved (because the floor caught almost all of the requested
        // deficit) shows a rate that barely moved either, instead of the bigger one the pace alone
        // would have implied. Both fields feed display and GoalProjectionCalculator's projection.
        double dailyAdjustmentKcal = targetCalories - tdee;
        double weeklyRateKg = dailyAdjustmentKcal * 7 / KCAL_PER_KG_OF_BODY_MASS;

        ProteinResult protein = proteinBasis(input);
        double proteinFactor = protein.basis().gramsPerKg(input.goal(), input.strengthTraining());
        if (input.dietStyle() == DietStyle.HIGH_PROTEIN) {
            proteinFactor = Math.min(proteinFactor + HIGH_PROTEIN_BONUS_GRAMS_PER_KG, protein.basis().highProteinCapGramsPerKg());
        }
        double proteinGrams = protein.basisKg() * proteinFactor;
        double proteinCalories = proteinGrams * 4;

        MacroSplit macros = macroSplit(input.dietStyle(), targetCalories, proteinCalories, protein.basisKg());
        double waterMl = waterMl(input.weightKg(), input.heightCm());

        List<TargetNote> notes = new ArrayList<>();
        if (deficitNotPossible) {
            notes.add(new TargetNote(
                    NoteCode.DEFICIT_NOT_POSSIBLE,
                    "Tu gasto estimado es menor al mínimo seguro, así que no se aplica déficit. Sumá actividad física o"
                            + " consultá a un profesional."));
        } else if (floorApplied) {
            notes.add(new TargetNote(NoteCode.FLOOR_APPLIED, "Se aplicó un piso calórico mínimo de seguridad."));
        }
        if (rate.capped()) {
            notes.add(new TargetNote(
                    NoteCode.RATE_CAPPED, "Se limitó el ritmo para no superar un déficit del 25% del gasto calórico total."));
        }
        if (input.goal().benefitsFromStrengthTraining() && !input.strengthTraining()) {
            notes.add(new TargetNote(
                    NoteCode.STRENGTH_TRAINING_RECOMMENDED, "Se recomienda sumar entrenamiento de fuerza para este objetivo."));
        }
        if (macros.ketoFiber()) {
            notes.add(new TargetNote(NoteCode.KETO_FIBER, "En dieta cetogénica se prioriza un mínimo de fibra de 20 g por día."));
        }

        return new NutritionTargets(
                round(targetCalories),
                floorApplied,
                round(proteinGrams),
                round(macros.fatGrams()),
                round(macros.carbGrams()),
                round(macros.fiberGrams()),
                round(macros.sugarMaxGrams()),
                SODIUM_MAX_MG,
                round(waterMl),
                round2(weeklyRateKg),
                round(dailyAdjustmentKcal),
                protein.basis(),
                round2(protein.basisKg()),
                protein.leanMassKg() == null ? null : round2(protein.leanMassKg()),
                List.copyOf(notes));
    }

    private double bmr(Input input) {
        double base = 10 * input.weightKg() + 6.25 * input.heightCm() - 5 * input.ageYears();
        return input.sex() == Sex.MALE ? base + 5 : base - 161;
    }

    /**
     * BMR (Mifflin-St Jeor) x activity factor — the baseline "formula" TDEE, before any goal/pace
     * adjustment. Exposed publicly (unlike {@link #bmr}) for the adaptive check-in ({@code
     * com.kcalma.checkin.CheckinService}), which needs it both as the {@code formulaTdee} it
     * reports and as the {@code currentTdee} fallback when the user has no accepted adaptive week
     * yet — see {@link #calculate(Input, double)}.
     */
    public double tdee(Input input) {
        return bmr(input) * input.activityLevel().factor();
    }

    /**
     * Weekly rate of change (kg/week, signed: negative = loss) and the daily calorie adjustment it
     * implies, capped so the adjustment is never more of a deficit than {@value
     * #DEFICIT_CAP_PERCENT_OF_TDEE} of TDEE (surpluses are never capped). This is the PRE-floor
     * request implied by the goal/pace alone — see {@link #calculate(Input, double)} for how the
     * floor can still shrink it further before it's ever shown to the user.
     */
    private RateResult weeklyRate(Goal goal, Pace pace, double weightKg, double tdee) {
        double weeklyRateKg;
        double dailyAdjustmentKcal;
        if (goal == Goal.MAINTAIN) {
            weeklyRateKg = 0;
            dailyAdjustmentKcal = 0;
        } else if (goal == Goal.RECOMP) {
            dailyAdjustmentKcal = -RECOMP_DEFICIT_PERCENT_OF_TDEE * tdee;
            weeklyRateKg = dailyAdjustmentKcal * 7 / KCAL_PER_KG_OF_BODY_MASS;
        } else {
            double percentOfBodyWeight = weeklyRatePercent(goal, pace);
            weeklyRateKg = (goal.isWeightLoss() ? -percentOfBodyWeight : percentOfBodyWeight) * weightKg;
            dailyAdjustmentKcal = weeklyRateKg * KCAL_PER_KG_OF_BODY_MASS / 7;
        }

        double maxDeficitKcal = -DEFICIT_CAP_PERCENT_OF_TDEE * tdee;
        if (dailyAdjustmentKcal < maxDeficitKcal) {
            dailyAdjustmentKcal = maxDeficitKcal;
            weeklyRateKg = dailyAdjustmentKcal * 7 / KCAL_PER_KG_OF_BODY_MASS;
            return new RateResult(weeklyRateKg, dailyAdjustmentKcal, true);
        }
        return new RateResult(weeklyRateKg, dailyAdjustmentKcal, false);
    }

    /** % of body weight moved per week, by goal x pace. LOSE_FAT and LOSE_WEIGHT share the same loss pace table. */
    private double weeklyRatePercent(Goal goal, Pace pace) {
        return switch (goal) {
            case LOSE_FAT, LOSE_WEIGHT -> switch (pace) {
                case SLOW -> 0.005;
                case MODERATE -> 0.0075;
                case FAST -> 0.01;
            };
            case BUILD_MUSCLE -> switch (pace) {
                case SLOW -> 0.0025;
                case MODERATE -> 0.0035;
                case FAST -> 0.005;
            };
            case GAIN_WEIGHT -> switch (pace) {
                case SLOW -> 0.005;
                case MODERATE -> 0.0075;
                case FAST -> 0.01;
            };
            case RECOMP, MAINTAIN -> throw new IllegalStateException(goal + " does not use a pace-based weekly rate");
        };
    }

    /** LEAN_MASS when bodyFatPct is known; else ADJUSTED_WEIGHT above the BMI 30 threshold; else BODY_WEIGHT. */
    private ProteinResult proteinBasis(Input input) {
        double heightM = input.heightCm() / 100.0;
        double bmi = input.weightKg() / (heightM * heightM);

        if (input.bodyFatPct() != null) {
            double leanMassKg = input.weightKg() * (1 - input.bodyFatPct() / 100.0);
            return new ProteinResult(ProteinBasis.LEAN_MASS, leanMassKg, leanMassKg);
        }
        if (bmi >= BMI_ADJUSTED_WEIGHT_THRESHOLD) {
            return new ProteinResult(ProteinBasis.ADJUSTED_WEIGHT, adjustedWeightKg(input.weightKg(), heightM), null);
        }
        return new ProteinResult(ProteinBasis.BODY_WEIGHT, input.weightKg(), null);
    }

    /**
     * Water target: 35 ml/kg of the same BMI-adjusted weight {@link #proteinBasis} uses for
     * ADJUSTED_WEIGHT once BMI &ge; 30 -- independently of whether {@code bodyFatPct} is known (so
     * even a LEAN_MASS-basis profile's water still switches to the adjusted weight above that BMI),
     * since raw body weight would overstate hydration needs the same way it would overstate protein
     * needs. Falls back to raw body weight below that threshold. Clamped to a safe [{@value
     * #WATER_ML_MIN}, {@value #WATER_ML_MAX}] ml range either way.
     */
    private double waterMl(double weightKg, double heightCm) {
        double heightM = heightCm / 100.0;
        double bmi = weightKg / (heightM * heightM);
        double basisKg = bmi >= BMI_ADJUSTED_WEIGHT_THRESHOLD ? adjustedWeightKg(weightKg, heightM) : weightKg;
        return clamp(basisKg * WATER_ML_PER_KG, WATER_ML_MIN, WATER_ML_MAX);
    }

    /** Shared by {@link #proteinBasis} and {@link #waterMl}: a metabolically-adjusted weight between "ideal" (BMI 25) and actual. */
    private static double adjustedWeightKg(double weightKg, double heightM) {
        double referenceWeightKg = REFERENCE_BMI_FOR_ADJUSTED_WEIGHT * heightM * heightM;
        return referenceWeightKg + ADJUSTED_WEIGHT_LEAN_FRACTION * (weightKg - referenceWeightKg);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Fat/carb split (and fiber/sugar, overridden for KETO) once protein is already fixed. */
    private MacroSplit macroSplit(DietStyle dietStyle, double targetCalories, double proteinCalories, double proteinBasisKg) {
        return switch (dietStyle) {
            case BALANCED, HIGH_PROTEIN -> {
                double fatFromPercent = (targetCalories * FAT_PERCENT_OF_CALORIES) / 9;
                double fatMinFromBasis = proteinBasisKg * MIN_FAT_GRAMS_PER_KG_BASIS;
                double fatGrams = Math.max(fatFromPercent, fatMinFromBasis);
                double carbCalories = Math.max(targetCalories - proteinCalories - fatGrams * 9, 0);
                yield new MacroSplit(
                        fatGrams,
                        carbCalories / 4,
                        Math.max(FIBER_GRAMS_PER_1000_KCAL * (targetCalories / 1000.0), MIN_FIBER_GRAMS),
                        (targetCalories * TOTAL_SUGAR_MAX_PERCENT_OF_CALORIES) / 4,
                        false);
            }
            case LOW_CARB -> {
                double carbGrams = Math.min((targetCalories * LOW_CARB_PERCENT_OF_CALORIES) / 4, LOW_CARB_MAX_GRAMS);
                double fatCalories = Math.max(targetCalories - proteinCalories - carbGrams * 4, 0);
                yield new MacroSplit(
                        fatCalories / 9,
                        carbGrams,
                        Math.max(FIBER_GRAMS_PER_1000_KCAL * (targetCalories / 1000.0), MIN_FIBER_GRAMS),
                        (targetCalories * TOTAL_SUGAR_MAX_PERCENT_OF_CALORIES) / 4,
                        false);
            }
            case KETO -> {
                double fatCalories = Math.max(targetCalories - proteinCalories - KETO_CARB_GRAMS * 4, 0);
                yield new MacroSplit(
                        fatCalories / 9,
                        KETO_CARB_GRAMS,
                        KETO_FIBER_GRAMS,
                        (targetCalories * KETO_SUGAR_MAX_PERCENT_OF_CALORIES) / 4,
                        true);
            }
        };
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record RateResult(double weeklyRateKg, double dailyAdjustmentKcal, boolean capped) {}

    private record ProteinResult(ProteinBasis basis, double basisKg, Double leanMassKg) {}

    private record MacroSplit(double fatGrams, double carbGrams, double fiberGrams, double sugarMaxGrams, boolean ketoFiber) {}

    /** Onboarding inputs needed to derive targets. Age is precomputed by the caller (no clock here). */
    public record Input(
            Sex sex,
            int ageYears,
            double heightCm,
            double weightKg,
            ActivityLevel activityLevel,
            Goal goal,
            Pace pace,
            DietStyle dietStyle,
            boolean strengthTraining,
            Double bodyFatPct) {}

    /** Derived daily targets. Gram/kcal amounts are rounded to the nearest whole unit for display. */
    public record NutritionTargets(
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
            List<TargetNote> notes) {}

    /** One user-facing (Spanish) note about how a target was derived — see {@link NoteCode} for the fixed set. */
    public record TargetNote(NoteCode code, String message) {}

    public enum NoteCode {
        FLOOR_APPLIED,
        /** A deficit goal whose TDEE is already at/under the sex-based safety floor: target falls back to TDEE instead. */
        DEFICIT_NOT_POSSIBLE,
        RATE_CAPPED,
        STRENGTH_TRAINING_RECOMMENDED,
        KETO_FIBER
    }
}
