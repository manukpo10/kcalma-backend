package com.kcalma.profile.dto;

/** Body for GET/PUT /api/profile: {"profile": {...}, "targets": {...}}. */
public record ProfileWithTargetsResponse(ProfileResponse profile, NutritionTargetsResponse targets) {}
