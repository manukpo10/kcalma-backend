package com.kcalma.profile;

import com.kcalma.checkin.AdaptiveTdeeService;
import com.kcalma.checkin.AdaptiveTdeeService.ActiveAdaptiveTdee;
import com.kcalma.profile.dto.NutritionTargetsResponse;
import com.kcalma.profile.dto.ProfileRequest;
import com.kcalma.profile.dto.ProfileResponse;
import com.kcalma.profile.dto.ProfileWithTargetsResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final UserProfileRepository repository;
    private final AdaptiveTdeeService adaptiveTdeeService;
    private final Clock clock;
    private final NutritionCalculator calculator = new NutritionCalculator();

    public ProfileService(UserProfileRepository repository, AdaptiveTdeeService adaptiveTdeeService, Clock clock) {
        this.repository = repository;
        this.adaptiveTdeeService = adaptiveTdeeService;
        this.clock = clock;
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
        // "must be null/ignored for RECOMP and MAINTAIN" (see ProfileController#validatePace for the
        // opposite, required case) -- silently drop whatever the client sent rather than rejecting it.
        profile.setPace(request.goal().requiresPace() ? request.pace() : null);
        profile.setDietStyle(request.dietStyle() != null ? request.dietStyle() : DietStyle.BALANCED);
        profile.setDietaryRestrictions(request.dietaryRestrictions() != null ? request.dietaryRestrictions() : List.of());
        profile.setStrengthTraining(request.strengthTraining() != null && request.strengthTraining());
        profile.setBodyFatPct(request.bodyFatPct());
        profile.setBodyFatMeasuredOn(request.bodyFatMeasuredOn());
        UserProfile saved = repository.save(profile);
        return toResponse(saved);
    }

    private ProfileWithTargetsResponse toResponse(UserProfile profile) {
        int ageYears = Period.between(profile.getBirthDate(), LocalDate.now(clock)).getYears();
        var input = new NutritionCalculator.Input(
                profile.getSex(),
                ageYears,
                profile.getHeightCm(),
                profile.getWeightKg().doubleValue(),
                profile.getActivityLevel(),
                profile.getGoal(),
                profile.getPace(),
                profile.getDietStyle(),
                profile.isStrengthTraining(),
                profile.getBodyFatPct() != null ? profile.getBodyFatPct().doubleValue() : null);

        // Sprint 3a: an accepted adaptive TDEE (still matching the CURRENT activity level -- see
        // AdaptiveTdeeService) takes over from the formula as calculate()'s base TDEE; changing
        // activity level silently falls back to FORMULA until the next accepted check-in.
        Optional<ActiveAdaptiveTdee> active = adaptiveTdeeService.findActive(profile.getUserId(), profile.getActivityLevel());
        NutritionCalculator.NutritionTargets targets =
                active.map(a -> calculator.calculate(input, a.tdeeKcal())).orElseGet(() -> calculator.calculate(input));
        EnergySource energySource = active.isPresent() ? EnergySource.ADAPTIVE : EnergySource.FORMULA;
        LocalDate adaptiveSince = active.map(ActiveAdaptiveTdee::since).orElse(null);

        return new ProfileWithTargetsResponse(
                ProfileResponse.from(profile), NutritionTargetsResponse.from(targets, energySource, adaptiveSince));
    }
}
