package com.kcalma.profile.dto;

import com.kcalma.profile.ActivityLevel;
import com.kcalma.profile.DietStyle;
import com.kcalma.profile.DietaryRestriction;
import com.kcalma.profile.Goal;
import com.kcalma.profile.Pace;
import com.kcalma.profile.Sex;
import com.kcalma.profile.UserProfile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProfileResponse(
        UUID userId,
        Sex sex,
        LocalDate birthDate,
        Integer heightCm,
        BigDecimal weightKg,
        ActivityLevel activityLevel,
        Goal goal,
        BigDecimal goalWeightKg,
        Pace pace,
        DietStyle dietStyle,
        List<DietaryRestriction> dietaryRestrictions,
        boolean strengthTraining,
        BigDecimal bodyFatPct,
        LocalDate bodyFatMeasuredOn,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ProfileResponse from(UserProfile profile) {
        return new ProfileResponse(
                profile.getUserId(),
                profile.getSex(),
                profile.getBirthDate(),
                profile.getHeightCm(),
                profile.getWeightKg(),
                profile.getActivityLevel(),
                profile.getGoal(),
                profile.getGoalWeightKg(),
                profile.getPace(),
                profile.getDietStyle(),
                profile.getDietaryRestrictions(),
                profile.isStrengthTraining(),
                profile.getBodyFatPct(),
                profile.getBodyFatMeasuredOn(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
