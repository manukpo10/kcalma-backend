package com.kcalma.checkin.dto;

import com.kcalma.profile.dto.NutritionTargetsResponse;

/** Body for POST /api/checkin/accept: {"checkin": {...}, "targets": {...}} — mirrors ProfileWithTargetsResponse. */
public record CheckinWithTargetsResponse(CheckinResponse checkin, NutritionTargetsResponse targets) {}
