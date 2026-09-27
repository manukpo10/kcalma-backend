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
 * <p>g/kg factors below come from a synthesis of Helms et al. 2014 (protein needs for lean,
 * resistance-trained individuals in a caloric deficit), Iraki et al. 2019 (bulking-phase protein
 * recommendations), Morton et al. 2018 (meta-analysis: ~1.6-2.2 g/kg/day maximizes resistance
 * training-induced gains), and the ISSN 2017 position stand (0.4-0.55 g/kg/meal, 1.4-2.0 g/kg/day
 * for exercising adults generally) — deliberately at the higher end within each source's range for
 * {@code LEAN_MASS}, since it's computed off the smallest (most conservative) mass of the three.
 */
public enum ProteinBasis {
    LEAN_MASS,
    ADJUSTED_WEIGHT,
    BODY_WEIGHT;

    /** g of protein per kg of this basis mass, by goal — see the class doc for the evidence behind these. */
    double gramsPerKg(Goal goal) {
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

    /** HIGH_PROTEIN adds +0.3 g/kg on top of {@link #gramsPerKg}, capped here (lower for the two heavier bases). */
    double highProteinCapGramsPerKg() {
        return this == LEAN_MASS ? 2.8 : 2.4;
    }
}
