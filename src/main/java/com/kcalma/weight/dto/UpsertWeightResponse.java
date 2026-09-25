package com.kcalma.weight.dto;

/**
 * Body for PUT /api/weights/{date}. {@code targetsUpdated} tells the UI whether this write was
 * the user's most recent weigh-in and therefore refreshed {@code user_profile.weight_kg} (and so
 * the daily nutrition targets) — see {@link com.kcalma.weight.WeightEntryService#upsert}.
 */
public record UpsertWeightResponse(WeightEntryResponse entry, boolean targetsUpdated) {}
