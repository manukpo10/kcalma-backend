package com.kcalma.profile;

/**
 * Which TDEE a profile's daily targets are currently derived from (see {@code
 * NutritionTargetsResponse}). {@code FORMULA} is Mifflin-St Jeor x activity (the only option
 * before sprint 3a); {@code ADAPTIVE} is the last accepted weekly check-in's TDEE (see {@code
 * com.kcalma.checkin.AdaptiveTdeeService}), which stays active until the user changes their
 * activity level (that resets to {@code FORMULA} until the next accepted check-in) or the profile
 * simply has no accepted check-in yet.
 */
public enum EnergySource {
    FORMULA,
    ADAPTIVE
}
