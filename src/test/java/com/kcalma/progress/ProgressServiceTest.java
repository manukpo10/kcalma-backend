package com.kcalma.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.kcalma.food.FoodEntry;
import com.kcalma.food.FoodEntryRepository;
import com.kcalma.food.FoodSource;
import com.kcalma.food.MealType;
import com.kcalma.profile.ActivityLevel;
import com.kcalma.profile.Goal;
import com.kcalma.profile.ProfileService;
import com.kcalma.profile.Sex;
import com.kcalma.profile.dto.NutritionTargetsResponse;
import com.kcalma.profile.dto.ProfileResponse;
import com.kcalma.profile.dto.ProfileWithTargetsResponse;
import com.kcalma.progress.dto.ProgressResponse;
import com.kcalma.weight.WeightEntry;
import com.kcalma.weight.WeightEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link ProgressService}: verifies it wires the pure calculators and the two
 * repositories together correctly. The calculators' own math (EMA, regression, projection) is
 * exhaustively hand-verified in their own test classes — this class only checks composition and
 * the edge cases specific to assembling the dashboard payload (no data, adherence, streaks).
 */
@ExtendWith(MockitoExtension.class)
class ProgressServiceTest {

    @Mock
    private ProfileService profileService;

    @Mock
    private WeightEntryRepository weightEntryRepository;

    @Mock
    private FoodEntryRepository foodEntryRepository;

    private final UUID userId = UUID.randomUUID();

    @Test
    void getProgress_noProfile_returnsEmpty() {
        ProgressService service = new ProgressService(profileService, weightEntryRepository, foodEntryRepository);
        when(profileService.findByUserId(userId)).thenReturn(Optional.empty());

        assertThat(service.getProgress(userId, ProgressRange.ONE_MONTH)).isEmpty();
    }

    @Test
    void getProgress_noWeightOrFoodData_statsAreNullButGoalAndZeroFilledNutritionStillReturn() {
        ProgressService service = new ProgressService(profileService, weightEntryRepository, foodEntryRepository);
        when(profileService.findByUserId(userId)).thenReturn(Optional.of(profileWithGoal(new BigDecimal("65.00"))));
        when(weightEntryRepository.findByUserIdAndEntryDateLessThanEqualOrderByEntryDateAsc(eq(userId), any()))
                .thenReturn(List.of());
        when(foodEntryRepository.findFirstByUserIdOrderByEntryDateAsc(userId)).thenReturn(Optional.empty());
        when(foodEntryRepository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(eq(userId), any(), any()))
                .thenReturn(List.of());

        ProgressResponse response = service.getProgress(userId, ProgressRange.ONE_MONTH).orElseThrow();

        assertThat(response.weights()).isEmpty();
        assertThat(response.stats().startKg()).isNull();
        assertThat(response.stats().currentKg()).isNull();
        assertThat(response.stats().trendKg()).isNull();
        assertThat(response.stats().changeKg()).isNull();
        assertThat(response.stats().weeklyRateKg()).isNull();
        assertThat(response.stats().projectedGoalDate()).isNull();
        assertThat(response.stats().progressPct()).isNull();
        assertThat(response.stats().goalWeightKg()).isEqualByComparingTo("65.00");

        long expectedDays = ChronoUnit.DAYS.between(response.from(), response.to()) + 1;
        assertThat(response.nutrition().days()).hasSize((int) expectedDays);
        assertThat(response.nutrition().days()).allSatisfy(day -> {
            assertThat(day.logged()).isFalse();
            assertThat(day.kcal()).isZero();
            assertThat(day.targetKcal()).isEqualTo(2000);
        });
        assertThat(response.nutrition().avgKcal()).isNull();
        assertThat(response.nutrition().adherencePct()).isNull();
        assertThat(response.nutrition().loggedStreakDays()).isZero();
    }

    @Test
    void getProgress_threeConsecutiveWeighIns_computesTrendChangeAndSkipsRateForShortSpan() {
        ProgressService service = new ProgressService(profileService, weightEntryRepository, foodEntryRepository);
        LocalDate today = LocalDate.now();
        List<WeightEntry> weighIns = List.of(
                new WeightEntry(userId, today.minusDays(2), new BigDecimal("80.00")),
                new WeightEntry(userId, today.minusDays(1), new BigDecimal("79.00")),
                new WeightEntry(userId, today, new BigDecimal("78.00")));
        when(profileService.findByUserId(userId)).thenReturn(Optional.of(profileWithGoal(null)));
        when(weightEntryRepository.findByUserIdAndEntryDateLessThanEqualOrderByEntryDateAsc(eq(userId), any()))
                .thenReturn(weighIns);
        when(foodEntryRepository.findFirstByUserIdOrderByEntryDateAsc(userId)).thenReturn(Optional.empty());
        when(foodEntryRepository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(eq(userId), any(), any()))
                .thenReturn(List.of());

        ProgressResponse response = service.getProgress(userId, ProgressRange.ONE_MONTH).orElseThrow();

        // trend0=80.0; trend1=80+0.1*(79-80)=79.9; trend2=79.9+0.1*(78-79.9)=79.71
        assertThat(response.weights()).hasSize(3);
        assertThat(response.stats().currentKg()).isCloseTo(78.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(response.stats().trendKg()).isCloseTo(79.71, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(response.stats().startKg()).isCloseTo(80.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(response.stats().changeKg()).isCloseTo(-0.29, org.assertj.core.data.Offset.offset(0.0001));
        // Only 2 days of span -> below the 14-day minimum for a weekly rate.
        assertThat(response.stats().weeklyRateKg()).isNull();
        assertThat(response.stats().projectedGoalDate()).isNull();
    }

    @Test
    void getProgress_fourteenDaysOfSteadyLoss_withGoalSet_producesRateAndProjection() {
        ProgressService service = new ProgressService(profileService, weightEntryRepository, foodEntryRepository);
        LocalDate today = LocalDate.now();
        // 15 points spanning exactly 14 days (today-14 .. today) -> meets the 14-day rate minimum.
        List<WeightEntry> weighIns = new java.util.ArrayList<>();
        for (int i = 14; i >= 0; i--) {
            BigDecimal weight = new BigDecimal("80.0").subtract(new BigDecimal("0.1").multiply(new BigDecimal(14 - i)));
            weighIns.add(new WeightEntry(userId, today.minusDays(i), weight.setScale(2, java.math.RoundingMode.HALF_UP)));
        }
        when(profileService.findByUserId(userId)).thenReturn(Optional.of(profileWithGoal(new BigDecimal("70.00"))));
        when(weightEntryRepository.findByUserIdAndEntryDateLessThanEqualOrderByEntryDateAsc(eq(userId), any()))
                .thenReturn(weighIns);
        when(foodEntryRepository.findFirstByUserIdOrderByEntryDateAsc(userId)).thenReturn(Optional.empty());
        when(foodEntryRepository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(eq(userId), any(), any()))
                .thenReturn(List.of());

        ProgressResponse response = service.getProgress(userId, ProgressRange.ONE_MONTH).orElseThrow();

        assertThat(response.stats().weeklyRateKg()).isNotNull().isLessThan(0.0);
        assertThat(response.stats().projectedGoalDate()).isNotNull().isAfter(today);
        assertThat(response.stats().progressPct()).isNotNull();
        assertThat(response.stats().progressPct()).isBetween(0.0, 100.0);
    }

    @Test
    void getProgress_nutritionDays_averagesAdherenceAndStreakOnlyCountLoggedDays() {
        ProgressService service = new ProgressService(profileService, weightEntryRepository, foodEntryRepository);
        LocalDate today = LocalDate.now();
        LocalDate twoDaysAgo = today.minusDays(2);
        // Within the +/-10% band around the 2000 kcal target (1800-2200).
        FoodEntry withinTarget = new FoodEntry(
                userId, twoDaysAgo, MealType.ALMUERZO, "Comida", new BigDecimal("100.00"), new BigDecimal("2050.00"),
                new BigDecimal("150.00"), new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), FoodSource.MANUAL);
        // Outside the band.
        FoodEntry outsideTarget = new FoodEntry(
                userId, today, MealType.CENA, "Comida", new BigDecimal("100.00"), new BigDecimal("1500.00"),
                new BigDecimal("100.00"), new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), FoodSource.MANUAL);
        when(profileService.findByUserId(userId)).thenReturn(Optional.of(profileWithGoal(null)));
        when(weightEntryRepository.findByUserIdAndEntryDateLessThanEqualOrderByEntryDateAsc(eq(userId), any()))
                .thenReturn(List.of());
        when(foodEntryRepository.findFirstByUserIdOrderByEntryDateAsc(userId)).thenReturn(Optional.empty());
        when(foodEntryRepository.findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(eq(userId), any(), any()))
                .thenReturn(List.of(withinTarget, outsideTarget));

        ProgressResponse response = service.getProgress(userId, ProgressRange.ONE_MONTH).orElseThrow();

        assertThat(response.nutrition().avgKcal()).isEqualTo(1775); // (2050+1500)/2
        assertThat(response.nutrition().avgProteinG()).isEqualTo(125); // (150+100)/2
        assertThat(response.nutrition().adherencePct()).isEqualTo(50.0); // 1 of 2 logged days within band
        // "today" is logged but "yesterday" (in between) is not -> streak of exactly 1.
        assertThat(response.nutrition().loggedStreakDays()).isEqualTo(1);
    }

    private static ProfileWithTargetsResponse profileWithGoal(BigDecimal goalWeightKg) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        ProfileResponse profile = new ProfileResponse(
                id, Sex.FEMALE, LocalDate.of(1990, 1, 1), 165, new BigDecimal("72.00"), ActivityLevel.SEDENTARY,
                Goal.LOSE, goalWeightKg, now, now);
        NutritionTargetsResponse targets = new NutritionTargetsResponse(2000, false, 120, 60, 200, 28, 50, 2000, 2500);
        return new ProfileWithTargetsResponse(profile, targets);
    }
}
