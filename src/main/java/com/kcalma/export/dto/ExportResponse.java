package com.kcalma.export.dto;

import com.kcalma.food.dto.FoodEntryResponse;
import com.kcalma.measurement.dto.MeasurementResponse;
import com.kcalma.profile.dto.ProfileResponse;
import com.kcalma.water.dto.WaterLogResponse;
import com.kcalma.weight.dto.WeightEntryResponse;
import java.time.OffsetDateTime;
import java.util.List;

/** Body for GET /api/export?format=json: every one of the caller's own records, in one payload. */
public record ExportResponse(
        OffsetDateTime exportedAt,
        ProfileResponse profile,
        List<FoodEntryResponse> foodEntries,
        List<WeightEntryResponse> weights,
        List<WaterLogResponse> water,
        List<MeasurementResponse> measurements) {}
