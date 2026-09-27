package com.kcalma.checkin;

/**
 * How much to trust a week's {@code proposedTdee} — driven by how much data backed it, on top of
 * the minimum {@value TdeeAdaptationCalculator#MIN_COMPLETE_DAYS}-complete-day /
 * {@value TdeeAdaptationCalculator#MIN_WEIGH_INS}-weigh-in gate that must already hold for a
 * proposal to exist at all (see {@link TdeeAdaptationCalculator}). {@code HIGH}'s own, higher bar
 * is documented on {@link TdeeAdaptationCalculator}; {@code MEDIUM}/{@code LOW} split the
 * remaining range roughly at the midpoint between the minimum gate and that bar.
 */
public enum Confidence {
    HIGH,
    MEDIUM,
    LOW
}
