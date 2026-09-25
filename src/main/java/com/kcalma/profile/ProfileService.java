package com.kcalma.profile;

import com.kcalma.profile.dto.NutritionTargetsResponse;
import com.kcalma.profile.dto.ProfileRequest;
import com.kcalma.profile.dto.ProfileResponse;
import com.kcalma.profile.dto.ProfileWithTargetsResponse;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final UserProfileRepository repository;
    private final NutritionCalculator calculator = new NutritionCalculator();

    public ProfileService(UserProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<ProfileWithTargetsResponse> findByUserId(UUID userId) {
        return repository.findById(userId).map(this::toResponse);
    }

    @Transactional
    public ProfileWithTargetsResponse upsert(UUID userId, ProfileRequest request) {
        UserProfile profile = repository.findById(userId).orElseGet(() -> new UserProfile(userId));
        profile.setSex(request.sex());
        profile.setBirthDate(request.birthDate());
        profile.setHeightCm(request.heightCm());
        profile.setWeightKg(request.weightKg());
        profile.setActivityLevel(request.activityLevel());
        profile.setGoal(request.goal());
        profile.setGoalWeightKg(request.goalWeightKg());
        UserProfile saved = repository.save(profile);
        return toResponse(saved);
    }

    private ProfileWithTargetsResponse toResponse(UserProfile profile) {
        int ageYears = Period.between(profile.getBirthDate(), LocalDate.now()).getYears();
        var input = new NutritionCalculator.Input(
                profile.getSex(),
                ageYears,
                profile.getHeightCm(),
                profile.getWeightKg().doubleValue(),
                profile.getActivityLevel(),
                profile.getGoal());
        NutritionCalculator.NutritionTargets targets = calculator.calculate(input);
        return new ProfileWithTargetsResponse(ProfileResponse.from(profile), NutritionTargetsResponse.from(targets));
    }
}
