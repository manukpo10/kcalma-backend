package com.kcalma.profile;

/**
 * How aggressively to pursue a {@link Goal} that has a direction (everything except {@code
 * RECOMP}/{@code MAINTAIN} — see {@link Goal#requiresPace()}), expressed as a weekly rate of
 * change that is a percentage of body weight (see {@link NutritionCalculator}).
 */
public enum Pace {
    SLOW,
    MODERATE,
    FAST
}
