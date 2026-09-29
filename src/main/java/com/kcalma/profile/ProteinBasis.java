package com.kcalma.profile;

/**
 * Which body mass a profile's protein target is computed per kilogram of — chosen by {@link
 * NutritionCalculator} from what the profile knows about body composition (see {@code
 * NutritionCalculator#proteinBasis}):
 *
 * <ul>
 *   <li>{@code LEAN_MASS}: {@code bodyFatPct} is known — the most accurate basis, since protein
 *       needs track muscle, not fat mass.
 *   <li>{@code ADJUSTED_WEIGHT}: {@code bodyFatPct} is unknown but BMI &ge; 30 — using raw body
 *       weight would overstate the need (a lot of that weight is very unlikely to be lean mass),
 *       so a metabolically-adjusted weight between "ideal" and actual is used instead.
 *   <li>{@code BODY_WEIGHT}: neither of the above — falls back to raw body weight.
 * </ul>
 *
 * <p>g/kg factors below depend on whether the profile trains with weights ({@code
 * strengthTraining}):
 *
 * <ul>
 *   <li>Resistance-trained ({@code strengthTraining = true}): a synthesis of Helms et al. 2014
 *       (protein needs for lean, resistance-trained individuals in a caloric deficit), Iraki et
 *       al. 2019 (bulking-phase protein recommendations), Morton et al. 2018 (meta-analysis:
 *       ~1.6-2.2 g/kg/day maximizes resistance training-induced gains), and the ISSN 2017 position
 *       stand (0.4-0.55 g/kg/meal, 1.4-2.0 g/kg/day for exercising adults generally) —
 *       deliberately at the higher end within each source's range for {@code LEAN_MASS}, since
 *       it's computed off the smallest (most conservative) mass of the three.
 *   <li>Untrained ({@code strengthTraining = false}): meaningfully lower across the board — roughly
 *       1.2-1.6 g/kg for untrained adults losing weight, above the general 0.8 g/kg RDA (an energy
 *       deficit still risks lean-mass loss that extra protein partly offsets) but below the trained
 *       tables above, which assume a resistance-training stimulus is actually present to direct the
 *       surplus/deficit into muscle rather than fat/lean tissue drifting either way on its own.
 * </ul>
 */
public enum ProteinBasis {
    LEAN_MASS,
    ADJUSTED_WEIGHT,
    BODY_WEIGHT;

    /**
     * g of protein per kg of this basis mass, by goal and whether the profile trains with weights —
     * see the class doc for the evidence behind both tables. {@code ADJUSTED_WEIGHT} and {@code
     * BODY_WEIGHT} share the same untrained numbers (unlike the trained table, where they differ).
     */
    double gramsPerKg(Goal goal, boolean strengthTraining) {
        return strengthTraining ? trainedGramsPerKg(goal) : untrainedGramsPerKg(goal);
    }

    private double trainedGramsPerKg(Goal goal) {
        return switch (this) {
            case LEAN_MASS -> switch (goal) {
                case LOSE_FAT -> 2.4;
                case LOSE_WEIGHT -> 2.0;
                case RECOMP -> 2.4;
                case MAINTAIN -> 1.8;
                case BUILD_MUSCLE -> 2.2;
                case GAIN_WEIGHT -> 1.8;
            };
            case ADJUSTED_WEIGHT -> switch (goal) {
                case LOSE_FAT -> 2.0;
                case LOSE_WEIGHT -> 1.6;
                case RECOMP -> 2.0;
                case MAINTAIN -> 1.4;
                case BUILD_MUSCLE -> 1.8;
                case GAIN_WEIGHT -> 1.4;
            };
            case BODY_WEIGHT -> switch (goal) {
                case LOSE_FAT -> 2.2;
                case LOSE_WEIGHT -> 1.8;
                case RECOMP -> 2.2;
                case MAINTAIN -> 1.6;
                case BUILD_MUSCLE -> 2.0;
                case GAIN_WEIGHT -> 1.6;
            };
        };
    }

    private double untrainedGramsPerKg(Goal goal) {
        return switch (this) {
            case LEAN_MASS -> switch (goal) {
                case LOSE_FAT -> 2.0;
                case LOSE_WEIGHT -> 1.8;
                case RECOMP -> 2.0;
                case MAINTAIN -> 1.5;
                case BUILD_MUSCLE -> 2.0;
                case GAIN_WEIGHT -> 1.5;
            };
            case ADJUSTED_WEIGHT, BODY_WEIGHT -> switch (goal) {
                case LOSE_FAT -> 1.6;
                case LOSE_WEIGHT -> 1.4;
                case RECOMP -> 1.6;
                case MAINTAIN -> 1.2;
                case BUILD_MUSCLE -> 1.6;
                case GAIN_WEIGHT -> 1.2;
            };
        };
    }

    /** HIGH_PROTEIN adds +0.3 g/kg on top of {@link #gramsPerKg}, capped here (lower for the two heavier bases). */
    double highProteinCapGramsPerKg() {
        return this == LEAN_MASS ? 2.8 : 2.4;
    }
}
