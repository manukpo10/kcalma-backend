package com.kcalma.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kcalma.profile.NutritionCalculator.Input;
import com.kcalma.profile.NutritionCalculator.NoteCode;
import com.kcalma.profile.NutritionCalculator.NutritionTargets;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link NutritionCalculator}. No Spring context.
 *
 * <p>Expected values below are computed by hand from the documented formula: BMR (Mifflin-St
 * Jeor) x activity factor -&gt; TDEE -&gt; weekly rate of change (%% of body weight, by goal x
 * pace; RECOMP fixes -10%% of TDEE instead) -&gt; daily calorie adjustment (capped at a 25%% TDEE
 * deficit) -&gt; calorie floor clamp -&gt; protein basis (lean mass if bodyFatPct is known, else
 * BMI-adjusted weight above BMI 30, else body weight) x g/kg (by goal x basis, +0.3 g/kg for
 * HIGH_PROTEIN, capped) -&gt; fat/carb split (by diet style) -&gt; fiber/sugar (overridden for
 * KETO) -&gt; sodium cap -&gt; water (35 ml/kg of body weight). See NutritionCalculator's own
 * class doc for the protein g/kg evidence (Helms 2014, Iraki 2019, Morton 2018, ISSN 2017).
 */
class NutritionCalculatorTest {

    private final NutritionCalculator calculator = new NutritionCalculator();

    @Test
    void maintainGoal_moderatelyActiveMale_matchesThePreSprint2aBaseline() {
        // BMR = 10*80 + 6.25*180 - 5*30 + 5 = 1780; TDEE = 1780*1.55 = 2759; MAINTAIN -> no adjustment.
        // No bodyFatPct and BMI (24.7) < 30 -> BODY_WEIGHT basis, MAINTAIN g/kg = 1.6 (same as before).
        Input input = new Input(
                Sex.MALE, 30, 180, 80, ActivityLevel.MODERATELY_ACTIVE, Goal.MAINTAIN, null, DietStyle.BALANCED, false, null);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(2759);
        assertThat(targets.floorApplied()).isFalse();
        assertThat(targets.proteinGrams()).isEqualTo(128);
        assertThat(targets.fatGrams()).isEqualTo(92); // 30% of kcal now, not 25% -- differs from the old baseline
        assertThat(targets.carbGrams()).isEqualTo(355);
        assertThat(targets.fiberGrams()).isEqualTo(39);
        assertThat(targets.sugarMaxGrams()).isEqualTo(69);
        assertThat(targets.sodiumMaxMg()).isEqualTo(2000);
        assertThat(targets.waterMl()).isEqualTo(2800);
        assertThat(targets.weeklyRateKg()).isEqualTo(0.0);
        assertThat(targets.dailyAdjustmentKcal()).isEqualTo(0.0);
        assertThat(targets.proteinBasis()).isEqualTo(ProteinBasis.BODY_WEIGHT);
        assertThat(targets.proteinBasisKg()).isEqualTo(80.0);
        assertThat(targets.leanMassKg()).isNull();
        assertThat(targets.notes()).isEmpty();
    }

    @Test
    void loseFatModerate_withKnownBodyFatPct_usesLeanMassBasisAndCapsTheDeficit() {
        // The sprint's own worked example: male, 34y, 168cm, 112.9kg, LIGHTLY_ACTIVE, LOSE_FAT
        // MODERATE, BALANCED, strength training yes, bodyFatPct 43.9.
        // BMR = 10*112.9 + 6.25*168 - 5*34 + 5 = 2014; TDEE = 2014*1.375 = 2769.25.
        // MODERATE loss = 0.75% of body weight/week -> -0.84675 kg/week -> -931.425 kcal/day, but
        // the 25% TDEE deficit cap is -692.3125 kcal/day, which is less of a deficit -> capped.
        Input input =
                new Input(Sex.MALE, 34, 168, 112.9, ActivityLevel.LIGHTLY_ACTIVE, Goal.LOSE_FAT, Pace.MODERATE, DietStyle.BALANCED, true, 43.9);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(2077);
        assertThat(targets.floorApplied()).isFalse();
        assertThat(targets.weeklyRateKg()).isEqualTo(-0.63);
        assertThat(targets.dailyAdjustmentKcal()).isEqualTo(-692.0);
        assertThat(targets.proteinBasis()).isEqualTo(ProteinBasis.LEAN_MASS);
        assertThat(targets.leanMassKg()).isEqualTo(63.34);
        assertThat(targets.proteinBasisKg()).isEqualTo(63.34);
        assertThat(targets.proteinGrams()).isEqualTo(152);
        assertThat(targets.fatGrams()).isEqualTo(69);
        assertThat(targets.carbGrams()).isEqualTo(211);
        assertThat(targets.fiberGrams()).isEqualTo(29);
        assertThat(targets.sugarMaxGrams()).isEqualTo(52);
        assertThat(targets.sodiumMaxMg()).isEqualTo(2000);
        assertThat(targets.waterMl()).isEqualTo(3952);
        assertThat(targets.notes()).extracting(NutritionCalculator.TargetNote::code).containsExactly(NoteCode.RATE_CAPPED);
    }

    @Test
    void loseFatModerate_withoutBodyFatPct_fallsBackToBmiAdjustedWeightBasis() {
        // Same profile as above but bodyFatPct unknown: BMI = 112.9/1.68^2 = 40.0 >= 30, so the
        // basis is ADJUSTED_WEIGHT (ref = 25*1.68^2 = 70.56; adjusted = 70.56 + 0.4*(112.9-70.56) =
        // 87.496), not raw body weight. Calories/rate are unaffected by the protein basis choice.
        Input input =
                new Input(Sex.MALE, 34, 168, 112.9, ActivityLevel.LIGHTLY_ACTIVE, Goal.LOSE_FAT, Pace.MODERATE, DietStyle.BALANCED, true, null);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(2077);
        assertThat(targets.weeklyRateKg()).isEqualTo(-0.63);
        assertThat(targets.dailyAdjustmentKcal()).isEqualTo(-692.0);
        assertThat(targets.proteinBasis()).isEqualTo(ProteinBasis.ADJUSTED_WEIGHT);
        assertThat(targets.leanMassKg()).isNull();
        assertThat(targets.proteinBasisKg()).isEqualTo(87.5);
        assertThat(targets.proteinGrams()).isEqualTo(175);
        assertThat(targets.fatGrams()).isEqualTo(69);
        assertThat(targets.carbGrams()).isEqualTo(188);
        assertThat(targets.notes()).extracting(NutritionCalculator.TargetNote::code).containsExactly(NoteCode.RATE_CAPPED);
    }

    @Test
    void recompWithoutStrengthTraining_usesTenPercentDeficitAndRecommendsTraining() {
        // BMR = 10*65 + 6.25*165 - 5*28 - 161 = 1380.25; TDEE = 1380.25*1.55 = 2139.3875.
        // RECOMP always uses a fixed -10% of TDEE (never a pace) -> -213.93875 kcal/day, well under
        // the 25% cap, so it is NOT rate-capped -- but RECOMP without strength training gets a note.
        Input input = new Input(Sex.FEMALE, 28, 165, 65, ActivityLevel.MODERATELY_ACTIVE, Goal.RECOMP, null, DietStyle.BALANCED, false, null);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(1925);
        assertThat(targets.floorApplied()).isFalse();
        assertThat(targets.weeklyRateKg()).isEqualTo(-0.19);
        assertThat(targets.dailyAdjustmentKcal()).isEqualTo(-214.0);
        assertThat(targets.proteinBasis()).isEqualTo(ProteinBasis.BODY_WEIGHT);
        assertThat(targets.proteinBasisKg()).isEqualTo(65.0);
        assertThat(targets.proteinGrams()).isEqualTo(143);
        assertThat(targets.fatGrams()).isEqualTo(64);
        assertThat(targets.carbGrams()).isEqualTo(194);
        assertThat(targets.fiberGrams()).isEqualTo(27);
        assertThat(targets.sugarMaxGrams()).isEqualTo(48);
        assertThat(targets.waterMl()).isEqualTo(2275);
        assertThat(targets.notes()).extracting(NutritionCalculator.TargetNote::code)
                .containsExactly(NoteCode.STRENGTH_TRAINING_RECOMMENDED);
    }

    @Test
    void buildMuscleFast_highProtein_withStrengthTraining_boostsProteinWithoutHittingTheLeanMassCap() {
        // BMR = 10*75 + 6.25*180 - 5*25 + 5 = 1755; TDEE = 1755*1.725 = 3027.375.
        // BUILD_MUSCLE FAST = +0.5% of body weight/week -> +0.375 kg/week -> +412.5 kcal/day
        // (surpluses are never capped). bodyFatPct known (15%) -> LEAN_MASS = 75*0.85 = 63.75.
        // Base g/kg for BUILD_MUSCLE on LEAN_MASS is 2.2; HIGH_PROTEIN adds 0.3 -> 2.5, under the
        // 2.8 LEAN_MASS cap. Strength training is already true -> no recommendation note.
        Input input = new Input(
                Sex.MALE, 25, 180, 75, ActivityLevel.VERY_ACTIVE, Goal.BUILD_MUSCLE, Pace.FAST, DietStyle.HIGH_PROTEIN, true, 15.0);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(3440);
        assertThat(targets.weeklyRateKg()).isEqualTo(0.38);
        assertThat(targets.dailyAdjustmentKcal()).isEqualTo(413.0);
        assertThat(targets.proteinBasis()).isEqualTo(ProteinBasis.LEAN_MASS);
        assertThat(targets.leanMassKg()).isEqualTo(63.75);
        assertThat(targets.proteinBasisKg()).isEqualTo(63.75);
        assertThat(targets.proteinGrams()).isEqualTo(159); // 63.75 * 2.5
        assertThat(targets.fatGrams()).isEqualTo(115);
        assertThat(targets.carbGrams()).isEqualTo(443);
        assertThat(targets.fiberGrams()).isEqualTo(48);
        assertThat(targets.sugarMaxGrams()).isEqualTo(86);
        assertThat(targets.waterMl()).isEqualTo(2625);
        assertThat(targets.notes()).isEmpty();
    }

    @Test
    void loseFatSlow_highProtein_onBodyWeightBasis_capsTheProteinBonusAndHitsTheFatFloor() {
        // BMR = 10*70 + 6.25*160 - 5*35 - 161 = 1364; TDEE = 1364*1.2 = 1636.8; BMI = 27.3 < 30 and
        // no bodyFatPct -> BODY_WEIGHT basis (70 kg). Base g/kg for LOSE_FAT on BODY_WEIGHT is 2.2;
        // HIGH_PROTEIN's +0.3 would be 2.5, but the cap for a non-LEAN_MASS basis is 2.4 -> capped.
        // Fat: 30% of kcal is 41.73 g, but the 0.6 g/kg-of-basis floor is 42 g -> the floor wins.
        Input input = new Input(Sex.FEMALE, 35, 160, 70, ActivityLevel.SEDENTARY, Goal.LOSE_FAT, Pace.SLOW, DietStyle.HIGH_PROTEIN, false, null);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(1252);
        assertThat(targets.floorApplied()).isFalse();
        assertThat(targets.weeklyRateKg()).isEqualTo(-0.35);
        assertThat(targets.dailyAdjustmentKcal()).isEqualTo(-385.0);
        assertThat(targets.proteinBasis()).isEqualTo(ProteinBasis.BODY_WEIGHT);
        assertThat(targets.proteinGrams()).isEqualTo(168); // 70 * 2.4 (capped from 2.5)
        assertThat(targets.fatGrams()).isEqualTo(42); // the 0.6 g/kg floor, not 30% of kcal
        assertThat(targets.carbGrams()).isEqualTo(50);
        assertThat(targets.notes()).isEmpty();
    }

    @Test
    void dietStyleLowCarb_gramCapBinds_whenTwentyFivePercentOfCaloriesWouldExceedOneHundredThirtyGrams() {
        // BMR = 10*90 + 6.25*175 - 5*30 + 5 = 1848.75; TDEE = 1848.75*1.55 = 2865.5625; MAINTAIN.
        // 25% of kcal / 4 = 179.1 g > 130 g -> the 130 g cap wins, not the percentage.
        Input input = new Input(Sex.MALE, 30, 175, 90, ActivityLevel.MODERATELY_ACTIVE, Goal.MAINTAIN, null, DietStyle.LOW_CARB, false, null);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(2866);
        assertThat(targets.carbGrams()).isEqualTo(130);
        assertThat(targets.fatGrams()).isEqualTo(197); // remainder after protein (144g) and carbs (130g)
        assertThat(targets.fiberGrams()).isEqualTo(40); // fiber/sugar are unchanged (not overridden) for LOW_CARB
        assertThat(targets.sugarMaxGrams()).isEqualTo(72);
        assertThat(targets.notes()).isEmpty();
    }

    @Test
    void dietStyleLowCarb_percentageWins_whenTwentyFivePercentOfCaloriesIsUnderOneHundredThirtyGrams() {
        // BMR = 10*50 + 6.25*150 - 5*50 - 161 = 1026.5; TDEE = 1026.5*1.2 = 1231.8; MAINTAIN.
        // 25% of kcal / 4 = 76.99 g < 130 g -> the percentage wins this time.
        Input input = new Input(Sex.FEMALE, 50, 150, 50, ActivityLevel.SEDENTARY, Goal.MAINTAIN, null, DietStyle.LOW_CARB, false, null);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(1232);
        assertThat(targets.carbGrams()).isEqualTo(77);
        assertThat(targets.fatGrams()).isEqualTo(67);
    }

    @Test
    void dietStyleKeto_fixesCarbsAndOverridesFiberAndSugar() {
        // BMR = 10*85 + 6.25*170 - 5*45 + 5 = 1692.5; TDEE = 1692.5*1.375 = 2327.1875; MAINTAIN.
        // KETO always fixes carbs at 30g, fiber at 20g (not 14g/1000kcal), and sugar max at 5% of
        // kcal (not 10%) -- and always adds the KETO_FIBER note.
        Input input = new Input(Sex.MALE, 45, 170, 85, ActivityLevel.LIGHTLY_ACTIVE, Goal.MAINTAIN, null, DietStyle.KETO, false, null);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(2327);
        assertThat(targets.carbGrams()).isEqualTo(30);
        assertThat(targets.fatGrams()).isEqualTo(185);
        assertThat(targets.fiberGrams()).isEqualTo(20);
        assertThat(targets.sugarMaxGrams()).isEqualTo(29);
        assertThat(targets.notes()).extracting(NutritionCalculator.TargetNote::code).containsExactly(NoteCode.KETO_FIBER);
    }

    @Test
    void loseWeightFast_smallSedentaryFemale_hitsBothTheRateCapAndTheCalorieFloor() {
        // BMR = 10*45 + 6.25*150 - 5*45 - 161 = 1001.5; TDEE = 1001.5*1.2 = 1201.8.
        // FAST loss = 1% of body weight/week -> -0.45 kg/week -> -495 kcal/day, but the 25% TDEE
        // cap is -300.45 -> capped; TDEE + that adjustment (901.35) is still below the 1200 female
        // floor -> floor applied too. Both notes fire together, floor always winning on calories.
        Input input = new Input(Sex.FEMALE, 45, 150, 45, ActivityLevel.SEDENTARY, Goal.LOSE_WEIGHT, Pace.FAST, DietStyle.BALANCED, false, null);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(1200);
        assertThat(targets.floorApplied()).isTrue();
        assertThat(targets.weeklyRateKg()).isEqualTo(-0.27);
        assertThat(targets.dailyAdjustmentKcal()).isEqualTo(-300.0);
        assertThat(targets.proteinGrams()).isEqualTo(81);
        assertThat(targets.fatGrams()).isEqualTo(40);
        assertThat(targets.carbGrams()).isEqualTo(129);
        assertThat(targets.fiberGrams()).isEqualTo(17);
        assertThat(targets.sugarMaxGrams()).isEqualTo(30);
        assertThat(targets.waterMl()).isEqualTo(1575);
        assertThat(targets.notes()).extracting(NutritionCalculator.TargetNote::code)
                .containsExactly(NoteCode.FLOOR_APPLIED, NoteCode.RATE_CAPPED);
    }

    @Test
    void gainWeightModerate_veryActiveMale_usesItsOwnPaceTableNotBuildMuscles() {
        // BMR = 10*70 + 6.25*178 - 5*25 + 5 = 1692.5; TDEE = 1692.5*1.725 = 2919.5625.
        // GAIN_WEIGHT MODERATE = 0.75% of body weight/week (BUILD_MUSCLE's MODERATE is 0.35%, a
        // different table) -> +0.525 kg/week -> +577.5 kcal/day, never capped (it's a surplus).
        Input input = new Input(Sex.MALE, 25, 178, 70, ActivityLevel.VERY_ACTIVE, Goal.GAIN_WEIGHT, Pace.MODERATE, DietStyle.BALANCED, false, null);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(3497);
        assertThat(targets.floorApplied()).isFalse();
        assertThat(targets.weeklyRateKg()).isEqualTo(0.53);
        assertThat(targets.dailyAdjustmentKcal()).isEqualTo(578.0);
        assertThat(targets.proteinGrams()).isEqualTo(112);
        assertThat(targets.fatGrams()).isEqualTo(117);
        assertThat(targets.carbGrams()).isEqualTo(500);
        assertThat(targets.fiberGrams()).isEqualTo(49);
        assertThat(targets.sugarMaxGrams()).isEqualTo(87);
        assertThat(targets.waterMl()).isEqualTo(2450);
        assertThat(targets.notes()).isEmpty();
    }

    @Test
    void carbGramsClampToZero_whenProteinAndFatAlreadyExceedTheCappedFloorCalories() {
        // An intentionally extreme combination (high lean mass on a very restricted, floor-adjacent
        // calorie target) to exercise the Math.max(..., 0) clamp: protein (923.4 kcal) + the fat
        // g/kg-of-basis floor (461.7 kcal) already add up to more than the 1278.225 kcal target, so
        // carbs must clamp to 0 rather than go negative.
        Input input = new Input(Sex.FEMALE, 55, 145, 95, ActivityLevel.SEDENTARY, Goal.LOSE_FAT, Pace.FAST, DietStyle.HIGH_PROTEIN, false, 10.0);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(1278);
        assertThat(targets.carbGrams()).isEqualTo(0);
        assertThat(targets.proteinGrams()).isEqualTo(231);
        assertThat(targets.fatGrams()).isEqualTo(51);
    }

    @Test
    void calculate_paceRequiredButMissing_throws() {
        Input input = new Input(Sex.MALE, 30, 180, 80, ActivityLevel.SEDENTARY, Goal.LOSE_WEIGHT, null, DietStyle.BALANCED, false, null);

        assertThatThrownBy(() -> calculator.calculate(input)).isInstanceOf(IllegalArgumentException.class);
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
    void goal_requiresPaceForEveryGoalExceptRecompAndMaintain() {
        assertThat(Goal.LOSE_FAT.requiresPace()).isTrue();
        assertThat(Goal.LOSE_WEIGHT.requiresPace()).isTrue();
        assertThat(Goal.BUILD_MUSCLE.requiresPace()).isTrue();
        assertThat(Goal.GAIN_WEIGHT.requiresPace()).isTrue();
        assertThat(Goal.RECOMP.requiresPace()).isFalse();
        assertThat(Goal.MAINTAIN.requiresPace()).isFalse();
    }

    @Test
    void goal_directionAndStrengthTrainingHelpersMatchTheSpec() {
        assertThat(Goal.LOSE_FAT.isWeightLoss()).isTrue();
        assertThat(Goal.LOSE_WEIGHT.isWeightLoss()).isTrue();
        assertThat(Goal.BUILD_MUSCLE.isWeightGain()).isTrue();
        assertThat(Goal.GAIN_WEIGHT.isWeightGain()).isTrue();
        assertThat(Goal.BUILD_MUSCLE.benefitsFromStrengthTraining()).isTrue();
        assertThat(Goal.RECOMP.benefitsFromStrengthTraining()).isTrue();
        assertThat(Goal.LOSE_FAT.benefitsFromStrengthTraining()).isFalse();
        assertThat(Goal.MAINTAIN.benefitsFromStrengthTraining()).isFalse();
    }
}
