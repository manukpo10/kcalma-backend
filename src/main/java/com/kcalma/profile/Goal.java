package com.kcalma.profile;

/** Weight goal: drives the calorie adjustment off TDEE and the protein g/kg target. */
public enum Goal {
    LOSE(-0.20, 2.0),
    MAINTAIN(0.0, 1.6),
    GAIN(0.10, 1.8);

    private final double adjustmentPercent;
    private final double proteinGramsPerKg;

    Goal(double adjustmentPercent, double proteinGramsPerKg) {
        this.adjustmentPercent = adjustmentPercent;
        this.proteinGramsPerKg = proteinGramsPerKg;
    }

    public double adjustmentPercent() {
        return adjustmentPercent;
    }

    public double proteinGramsPerKg() {
        return proteinGramsPerKg;
    }
}
