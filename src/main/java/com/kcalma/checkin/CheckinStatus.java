package com.kcalma.checkin;

/**
 * Lifecycle of one week's adaptive-TDEE proposal (see {@link TdeeAdaptationCalculator} for how
 * {@code PENDING}/{@code INSUFFICIENT_DATA} are decided). {@code PENDING} and {@code
 * INSUFFICIENT_DATA} are "open" — recomputed against the latest data on every {@code GET
 * /api/checkin} — until the user accepts or dismisses the week, which "closes" it: the row then
 * becomes a frozen snapshot (see {@code CheckinService#currentWeek}).
 */
public enum CheckinStatus {
    PENDING,
    ACCEPTED,
    DISMISSED,
    INSUFFICIENT_DATA
}
