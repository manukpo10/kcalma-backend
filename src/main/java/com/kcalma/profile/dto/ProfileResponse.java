package com.kcalma.profile.dto;

import com.kcalma.profile.ActivityLevel;
import com.kcalma.profile.Goal;
import com.kcalma.profile.Sex;
import com.kcalma.profile.UserProfile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
