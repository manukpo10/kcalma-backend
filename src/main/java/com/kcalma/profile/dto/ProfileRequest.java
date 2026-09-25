package com.kcalma.profile.dto;

import com.kcalma.profile.ActivityLevel;
import com.kcalma.profile.Goal;
import com.kcalma.profile.Sex;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import java.math.BigDecimal;
import java.time.LocalDate;

/** PUT /api/profile request body. */
public record ProfileRequest(
        @NotNull Sex sex,
        @NotNull @Past LocalDate birthDate,
        @NotNull @Min(50) @Max(250) Integer heightCm,
        @NotNull @DecimalMin("20.0") @DecimalMax("500.0") BigDecimal weightKg,
        @NotNull ActivityLevel activityLevel,
        @NotNull Goal goal) {}
