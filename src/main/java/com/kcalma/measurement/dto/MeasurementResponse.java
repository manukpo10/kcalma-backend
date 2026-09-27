package com.kcalma.measurement.dto;

import com.kcalma.measurement.BodyMeasurement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record MeasurementResponse(
        LocalDate measuredOn,
        BigDecimal waistCm,
        BigDecimal hipCm,
        BigDecimal chestCm,
        BigDecimal armCm,
        BigDecimal thighCm,
        BigDecimal bodyFatPct,
        BigDecimal muscleMassKg,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static MeasurementResponse from(BodyMeasurement measurement) {
        return new MeasurementResponse(
                measurement.getMeasuredOn(),
                measurement.getWaistCm(),
                measurement.getHipCm(),
                measurement.getChestCm(),
                measurement.getArmCm(),
                measurement.getThighCm(),
                measurement.getBodyFatPct(),
                measurement.getMuscleMassKg(),
                measurement.getCreatedAt(),
                measurement.getUpdatedAt());
    }
}
