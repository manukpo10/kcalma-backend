package com.kcalma.weight.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Body for PUT /api/weights/{date}. */
public record UpsertWeightRequest(
        @NotNull(message = "Falta el peso.")
                @DecimalMin(value = "30.0", message = "El peso debe ser al menos 30 kg.")
                @DecimalMax(value = "300.0", message = "El peso no puede superar los 300 kg.")
                BigDecimal weightKg) {}
