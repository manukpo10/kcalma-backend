package com.kcalma.water.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Body for POST /api/water: a signed delta applied to the day's running total (see {@code WaterLogService#applyDelta}). */
public record UpsertWaterRequest(
        @NotNull LocalDate date,
        @NotNull(message = "Falta la cantidad de agua.")
                @Min(value = -2000, message = "El cambio no puede ser menor a -2000 ml.")
                @Max(value = 2000, message = "El cambio no puede superar los 2000 ml.")
                Integer deltaMl) {}
