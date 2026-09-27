package com.kcalma.day;

import com.kcalma.day.dto.DayResponse;
import com.kcalma.food.FoodEntry;
import com.kcalma.food.FoodEntryRepository;
import com.kcalma.food.MealType;
import com.kcalma.food.NutritionMath;
import com.kcalma.food.dto.FoodEntryResponse;
import com.kcalma.profile.ProfileService;
import com.kcalma.profile.dto.NutritionTargetsResponse;
import com.kcalma.water.WaterLog;
import com.kcalma.water.WaterLogRepository;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Composes the profile's targets with the day's logged food entries (and water) into one dashboard payload. */
@Service
public class DayService {

    private final ProfileService profileService;
    private final FoodEntryRepository foodEntryRepository;
    private final WaterLogRepository waterLogRepository;

    public DayService(ProfileService profileService, FoodEntryRepository foodEntryRepository, WaterLogRepository waterLogRepository) {
        this.profileService = profileService;
        this.foodEntryRepository = foodEntryRepository;
        this.waterLogRepository = waterLogRepository;
    }

    @Transactional(readOnly = true)
    public Optional<DayResponse> getDay(UUID userId, LocalDate date) {
        return profileService.findByUserId(userId).map(profileWithTargets -> {
            NutritionTargetsResponse targets = profileWithTargets.targets();

            List<FoodEntry> entries = foodEntryRepository.findByUserIdAndEntryDateOrderByCreatedAtAsc(userId, date);
            List<FoodEntryResponse> entryResponses = entries.stream().map(FoodEntryResponse::from).toList();

            Map<MealType, List<FoodEntryResponse>> meals = new EnumMap<>(MealType.class);
            for (MealType mealType : MealType.values()) {
                meals.put(mealType, List.of());
            }
            meals.putAll(entryResponses.stream().collect(Collectors.groupingBy(FoodEntryResponse::mealType)));

            NutritionMath.Totals consumed = entryResponses.stream()
                    .map(FoodEntryResponse::totals)
                    .reduce(NutritionMath.Totals.ZERO, NutritionMath.Totals::plus);

            NutritionMath.Totals targetsAsTotals = new NutritionMath.Totals(
                    targets.calories(),
                    targets.proteinGrams(),
                    targets.fatGrams(),
                    targets.carbGrams(),
                    targets.fiberGrams(),
                    targets.sugarMaxGrams(),
                    targets.sodiumMaxMg());
            NutritionMath.Totals remaining = targetsAsTotals.minus(consumed);

            DayResponse.Exceeded exceeded = new DayResponse.Exceeded(
                    consumed.kcal() > targets.calories(),
                    consumed.sugar() > targets.sugarMaxGrams(),
                    consumed.sodiumMg() > targets.sodiumMaxMg());

            int consumedMl = waterLogRepository.findByUserIdAndEntryDate(userId, date).map(WaterLog::getMl).orElse(0);
            DayResponse.Water water = new DayResponse.Water(consumedMl, targets.waterMl());

            return new DayResponse(date, targets, consumed, remaining, exceeded, meals, water);
        });
    }
}
