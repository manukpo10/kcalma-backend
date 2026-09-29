package com.kcalma.profile.dto;

import com.kcalma.profile.ActivityLevel;
import com.kcalma.profile.DietStyle;
import com.kcalma.profile.DietaryRestriction;
import com.kcalma.profile.Goal;
import com.kcalma.profile.Pace;
import com.kcalma.profile.Sex;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * PUT /api/profile request body. {@code pace}/{@code dietStyle}/{@code dietaryRestrictions}/{@code
 * strengthTraining} default (respectively: null when not required, BALANCED, empty, false) in
 * {@code ProfileService} when omitted — see {@code ProfileController#validatePace} for the one
 * cross-field rule (pace required/ignored depending on {@code goal}) that can't be a format-only
 * annotation here.
 */
public record ProfileRequest(
        @NotNull Sex sex,
        @NotNull @Past @AgeRange LocalDate birthDate,
        @NotNull @Min(50) @Max(250) Integer heightCm,
        @NotNull @DecimalMin("20.0") @DecimalMax("500.0") BigDecimal weightKg,
        @NotNull ActivityLevel activityLevel,
        @NotNull Goal goal,
        @DecimalMin(value = "30.0", message = "El peso objetivo debe ser al menos 30 kg.")
                @DecimalMax(value = "300.0", message = "El peso objetivo no puede superar los 300 kg.")
                BigDecimal goalWeightKg,
        Pace pace,
        DietStyle dietStyle,
        List<DietaryRestriction> dietaryRestrictions,
        Boolean strengthTraining,
        @DecimalMin(value = "3.0", message = "El porcentaje de grasa corporal debe ser al menos 3.")
                @DecimalMax(value = "70.0", message = "El porcentaje de grasa corporal no puede superar 70.")
                BigDecimal bodyFatPct,
        @PastOrPresent(message = "La fecha de medición de grasa corporal no puede ser futura.") LocalDate bodyFatMeasuredOn) {}
