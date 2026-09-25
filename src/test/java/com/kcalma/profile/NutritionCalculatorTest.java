package com.kcalma.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.kcalma.profile.NutritionCalculator.Input;
import com.kcalma.profile.NutritionCalculator.NutritionTargets;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link NutritionCalculator}. No Spring context.
 *
 * Expected values below are computed by hand from the documented formula:
 * BMR (Mifflin-St Jeor) x activity factor -> goal adjustment -> calorie floor clamp
 * -> protein (g/kg by goal) -> fat (25% of kcal, floor 0.6 g/kg) -> carbs (remainder, floor 0)
 * -> fiber (14 g / 1000 kcal) -> free sugar cap (10% of kcal) -> sodium cap (2000 mg)
 * -> water (35 ml/kg).
 */
class NutritionCalculatorTest {

    private final NutritionCalculator calculator = new NutritionCalculator();

    @Test
    void maintainGoal_moderatelyActiveMale_computesTargetsAboveFloor() {
        // BMR = 10*80 + 6.25*180 - 5*30 + 5 = 1780; TDEE = 1780*1.55 = 2759; maintain -> no adjustment
        Input input = new Input(Sex.MALE, 30, 180, 80, ActivityLevel.MODERATELY_ACTIVE, Goal.MAINTAIN);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(2759);
        assertThat(targets.floorApplied()).isFalse();
        assertThat(targets.proteinGrams()).isEqualTo(128);
        assertThat(targets.fatGrams()).isEqualTo(77);
        assertThat(targets.carbGrams()).isEqualTo(389);
        assertThat(targets.fiberGrams()).isEqualTo(39);
        assertThat(targets.sugarMaxGrams()).isEqualTo(69);
        assertThat(targets.sodiumMaxMg()).isEqualTo(2000);
        assertThat(targets.waterMl()).isEqualTo(2800);
    }

    @Test
    void loseGoal_sedentarySmallFemale_hitsCalorieFloor() {
        // BMR = 10*45 + 6.25*150 - 5*45 - 161 = 1001.5; TDEE = 1201.8; lose(-20%) = 961.44 -> clamps to 1200 floor
        Input input = new Input(Sex.FEMALE, 45, 150, 45, ActivityLevel.SEDENTARY, Goal.LOSE);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(1200);
        assertThat(targets.floorApplied()).isTrue();
        assertThat(targets.proteinGrams()).isEqualTo(90);
        assertThat(targets.fatGrams()).isEqualTo(33);
        assertThat(targets.carbGrams()).isEqualTo(135);
        assertThat(targets.fiberGrams()).isEqualTo(17);
        assertThat(targets.sugarMaxGrams()).isEqualTo(30);
        assertThat(targets.sodiumMaxMg()).isEqualTo(2000);
        assertThat(targets.waterMl()).isEqualTo(1575);
    }

    @Test
    void loseGoal_heavyMale_fatGramsFloorOverridesPercentOfCalories() {
        // target = 1774.8 kcal (above male floor of 1500, so floorApplied is false here);
        // 25% of kcal / 9 = 49.3g fat, but 0.6 g/kg * 100kg = 60g -> the g/kg floor wins
        Input input = new Input(Sex.MALE, 50, 175, 100, ActivityLevel.SEDENTARY, Goal.LOSE);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(1775);
        assertThat(targets.floorApplied()).isFalse();
        assertThat(targets.proteinGrams()).isEqualTo(200);
        assertThat(targets.fatGrams()).isEqualTo(60);
        assertThat(targets.carbGrams()).isEqualTo(109);
        assertThat(targets.fiberGrams()).isEqualTo(25);
        assertThat(targets.sugarMaxGrams()).isEqualTo(44);
        assertThat(targets.sodiumMaxMg()).isEqualTo(2000);
        assertThat(targets.waterMl()).isEqualTo(3500);
    }

    @Test
    void loseGoal_veryHeavyFemale_carbGramsClampToZeroWhenProteinAndFatExceedTarget() {
        // protein (300g=1200kcal) + fat (90g=810kcal) = 2010kcal > 1957.44kcal target -> carbs clamp to 0, never negative
        Input input = new Input(Sex.FEMALE, 60, 160, 150, ActivityLevel.SEDENTARY, Goal.LOSE);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(1957);
        assertThat(targets.floorApplied()).isFalse();
        assertThat(targets.proteinGrams()).isEqualTo(300);
        assertThat(targets.fatGrams()).isEqualTo(90);
        assertThat(targets.carbGrams()).isEqualTo(0);
        assertThat(targets.fiberGrams()).isEqualTo(27);
        assertThat(targets.sugarMaxGrams()).isEqualTo(49);
        assertThat(targets.sodiumMaxMg()).isEqualTo(2000);
        assertThat(targets.waterMl()).isEqualTo(5250);
    }

    @Test
    void gainGoal_veryActiveMale_appliesPositiveAdjustmentAndHigherProteinPerKg() {
        // BMR = 1692.5; TDEE = 1692.5*1.725 = 2919.5625; gain(+10%) = 3211.51875
        Input input = new Input(Sex.MALE, 25, 178, 70, ActivityLevel.VERY_ACTIVE, Goal.GAIN);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(3212);
        assertThat(targets.floorApplied()).isFalse();
        assertThat(targets.proteinGrams()).isEqualTo(126);
        assertThat(targets.fatGrams()).isEqualTo(89);
        assertThat(targets.carbGrams()).isEqualTo(476);
        assertThat(targets.fiberGrams()).isEqualTo(45);
        assertThat(targets.sugarMaxGrams()).isEqualTo(80);
        assertThat(targets.sodiumMaxMg()).isEqualTo(2000);
        assertThat(targets.waterMl()).isEqualTo(2450);
    }

    @Test
    void activityLevels_haveDocumentedPalFactors() {
        assertThat(ActivityLevel.SEDENTARY.factor()).isEqualTo(1.2);
        assertThat(ActivityLevel.LIGHTLY_ACTIVE.factor()).isEqualTo(1.375);
        assertThat(ActivityLevel.MODERATELY_ACTIVE.factor()).isEqualTo(1.55);
        assertThat(ActivityLevel.VERY_ACTIVE.factor()).isEqualTo(1.725);
        assertThat(ActivityLevel.EXTRA_ACTIVE.factor()).isEqualTo(1.9);
    }

    @Test
    void goals_haveDocumentedAdjustmentsAndProteinPerKg() {
        assertThat(Goal.LOSE.adjustmentPercent()).isEqualTo(-0.20);
        assertThat(Goal.LOSE.proteinGramsPerKg()).isEqualTo(2.0);
        assertThat(Goal.MAINTAIN.adjustmentPercent()).isEqualTo(0.0);
        assertThat(Goal.MAINTAIN.proteinGramsPerKg()).isEqualTo(1.6);
        assertThat(Goal.GAIN.adjustmentPercent()).isEqualTo(0.10);
        assertThat(Goal.GAIN.proteinGramsPerKg()).isEqualTo(1.8);
    }
}
