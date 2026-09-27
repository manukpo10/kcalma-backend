package com.kcalma.measurement.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/**
 * Body for PUT /api/measurements/{date}. Every field is individually optional, but at least one
 * must be present — see {@link #hasAnyValue()} and {@code MeasurementController#validateHasAnyValue}
 * for that cross-field rule. Range bounds mirror the CHECK constraints on app.body_measurement
 * (V13__body_measurement.sql) exactly.
 */
public record UpsertMeasurementRequest(
        @DecimalMin(value = "30.0", message = "El contorno de cintura debe ser al menos 30 cm.")
                @DecimalMax(value = "250.0", message = "El contorno de cintura no puede superar los 250 cm.")
                BigDecimal waistCm,
        @DecimalMin(value = "30.0", message = "El contorno de cadera debe ser al menos 30 cm.")
                @DecimalMax(value = "250.0", message = "El contorno de cadera no puede superar los 250 cm.")
                BigDecimal hipCm,
        @DecimalMin(value = "30.0", message = "El contorno de pecho debe ser al menos 30 cm.")
                @DecimalMax(value = "250.0", message = "El contorno de pecho no puede superar los 250 cm.")
                BigDecimal chestCm,
        @DecimalMin(value = "10.0", message = "El contorno de brazo debe ser al menos 10 cm.")
                @DecimalMax(value = "100.0", message = "El contorno de brazo no puede superar los 100 cm.")
                BigDecimal armCm,
        @DecimalMin(value = "10.0", message = "El contorno de muslo debe ser al menos 10 cm.")
                @DecimalMax(value = "150.0", message = "El contorno de muslo no puede superar los 150 cm.")
                BigDecimal thighCm,
        @DecimalMin(value = "3.0", message = "El porcentaje de grasa corporal debe ser al menos 3.")
                @DecimalMax(value = "70.0", message = "El porcentaje de grasa corporal no puede superar 70.")
                BigDecimal bodyFatPct,
        @DecimalMin(value = "5.0", message = "La masa muscular debe ser al menos 5 kg.")
                @DecimalMax(value = "150.0", message = "La masa muscular no puede superar los 150 kg.")
                BigDecimal muscleMassKg) {

    public boolean hasAnyValue() {
        return waistCm != null
                || hipCm != null
                || chestCm != null
                || armCm != null
                || thighCm != null
                || bodyFatPct != null
                || muscleMassKg != null;
    }
}
