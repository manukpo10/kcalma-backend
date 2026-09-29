package com.kcalma.profile;

/**
 * The user's weight/body-composition objective. Drives the weekly rate of change (see {@link
 * Pace}), the calorie adjustment off TDEE, and the protein g/kg target (see {@link
 * NutritionCalculator}).
 *
 * <p>{@code LOSE_WEIGHT} and {@code GAIN_WEIGHT} are the simple, direction-only goals kept from
 * before this sprint (V10 migration maps the old {@code LOSE}/{@code GAIN} values onto these).
 * {@code LOSE_FAT} is a more aggressive cut (higher protein to preserve lean mass while dieting
 * harder); {@code BUILD_MUSCLE} is a controlled surplus aimed at muscle gain rather than plain
 * weight gain; {@code RECOMP} targets a small fixed deficit (see {@link NutritionCalculator}) to
 * lose fat and build muscle at roughly the same time, at a slower pace than either alone — so it
 * has no {@link Pace} of its own.
 */
public enum Goal {
    LOSE_FAT,
    LOSE_WEIGHT,
    RECOMP,
    MAINTAIN,
    BUILD_MUSCLE,
    GAIN_WEIGHT;

    /** RECOMP has its own fixed rate (-10% of TDEE) and MAINTAIN has none — every other goal needs one. */
    public boolean requiresPace() {
        return this != RECOMP && this != MAINTAIN;
    }

    /**
     * Whether this goal runs a calorie deficit — the gate for {@code NutritionCalculator}'s safety
     * floor (see its class doc): LOSE_FAT/LOSE_WEIGHT (pace-based) and RECOMP (its own fixed -10%
     * of TDEE) all do; MAINTAIN and the gain goals never do, so they never clamp up to the floor.
     */
    public boolean isDeficit() {
        return isWeightLoss() || this == RECOMP;
    }

    /** LOSE_FAT/LOSE_WEIGHT move the weekly rate (and its calorie adjustment) in the negative direction. */
    public boolean isWeightLoss() {
        return this == LOSE_FAT || this == LOSE_WEIGHT;
    }

    /** BUILD_MUSCLE/GAIN_WEIGHT move the weekly rate (and its calorie adjustment) in the positive direction. */
    public boolean isWeightGain() {
        return this == BUILD_MUSCLE || this == GAIN_WEIGHT;
    }

    /** BUILD_MUSCLE and RECOMP both rely on a training stimulus to direct the surplus/deficit into muscle. */
    public boolean benefitsFromStrengthTraining() {
        return this == BUILD_MUSCLE || this == RECOMP;
    }
}
