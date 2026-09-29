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
 * deficit) -&gt; calorie floor clamp (deficit goals only, never above TDEE) -&gt; rate/adjustment
 * recomputed from the post-floor target -&gt; protein basis (lean mass if bodyFatPct is known, else
 * BMI-adjusted weight above BMI 30, else body weight) x g/kg (by goal x basis x strength training,
 * +0.3 g/kg for HIGH_PROTEIN, capped) -&gt; fat/carb split (by diet style) -&gt; fiber (floored at
 * 25 g) /sugar (total sugars, overridden for KETO) -&gt; sodium cap -&gt; water (35 ml/kg of the
 * same BMI-adjusted weight above BMI 30, else body weight; clamped to [1500, 3500] ml). See
 * NutritionCalculator's own class doc for the protein g/kg evidence (Helms 2014, Iraki 2019,
 * Morton 2018, ISSN 2017) and the untrained table's own evidence (see {@link ProteinBasis}).
 *
 * <p>Every profile below is a synthetic fixture invented for these tests -- none of them is a real
 * person's body data.
 */
class NutritionCalculatorTest {

    private final NutritionCalculator calculator = new NutritionCalculator();

    @Test
    void maintainGoal_moderatelyActiveMale_untrained_usesTheBaselineFormulaAndTheUntrainedProteinTable() {
        // BMR = 10*80 + 6.25*180 - 5*30 + 5 = 1780; TDEE = 1780*1.55 = 2759; MAINTAIN -> no
        // adjustment, and MAINTAIN never touches the calorie floor (see Goal#isDeficit).
        // No bodyFatPct and BMI (24.7) < 30 -> BODY_WEIGHT basis; strengthTraining=false -> the
        // untrained table's MAINTAIN g/kg is 1.2 (80 * 1.2 = 96), not the trained table's 1.6.
        Input input = new Input(
                Sex.MALE, 30, 180, 80, ActivityLevel.MODERATELY_ACTIVE, Goal.MAINTAIN, null, DietStyle.BALANCED, false, null);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(2759);
        assertThat(targets.floorApplied()).isFalse();
        assertThat(targets.proteinGrams()).isEqualTo(96);
        assertThat(targets.fatGrams()).isEqualTo(92); // 30% of kcal
        assertThat(targets.carbGrams()).isEqualTo(387);
        assertThat(targets.fiberGrams()).isEqualTo(39);
        assertThat(targets.sugarMaxGrams()).isEqualTo(124); // 18% of kcal / 4 (total sugars)
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
        // A synthetic profile: male, 40y, 175cm, 110kg, LIGHTLY_ACTIVE, LOSE_FAT MODERATE,
        // BALANCED, strength training yes, bodyFatPct 40 (BMI 35.9 -- deliberately obese so BOTH
        // the LEAN_MASS basis here and the BMI-adjusted basis in the next test are exercised).
        // BMR = 10*110 + 6.25*175 - 5*40 + 5 = 1998.75; TDEE = 1998.75*1.375 = 2748.28125.
        // MODERATE loss = 0.75% of body weight/week -> -0.825 kg/week -> -907.5 kcal/day, but the
        // 25% TDEE deficit cap is -687.0703125 kcal/day, which is less of a deficit -> capped.
        Input input =
                new Input(Sex.MALE, 40, 175, 110, ActivityLevel.LIGHTLY_ACTIVE, Goal.LOSE_FAT, Pace.MODERATE, DietStyle.BALANCED, true, 40.0);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(2061);
        assertThat(targets.floorApplied()).isFalse();
        assertThat(targets.weeklyRateKg()).isEqualTo(-0.62);
        assertThat(targets.dailyAdjustmentKcal()).isEqualTo(-687.0);
        assertThat(targets.proteinBasis()).isEqualTo(ProteinBasis.LEAN_MASS);
        assertThat(targets.leanMassKg()).isEqualTo(66.0);
        assertThat(targets.proteinBasisKg()).isEqualTo(66.0);
        assertThat(targets.proteinGrams()).isEqualTo(158);
        assertThat(targets.fatGrams()).isEqualTo(69);
        assertThat(targets.carbGrams()).isEqualTo(202);
        assertThat(targets.fiberGrams()).isEqualTo(29);
        assertThat(targets.sugarMaxGrams()).isEqualTo(93);
        assertThat(targets.sodiumMaxMg()).isEqualTo(2000);
        // BMI (35.9) >= 30 -> water uses the BMI-adjusted weight too, EVEN THOUGH bodyFatPct (and
        // therefore a LEAN_MASS protein basis) is known -- the two bases are chosen independently.
        assertThat(targets.waterMl()).isEqualTo(3148);
        assertThat(targets.notes()).extracting(NutritionCalculator.TargetNote::code).containsExactly(NoteCode.RATE_CAPPED);
    }

    @Test
    void loseFatModerate_withoutBodyFatPct_fallsBackToBmiAdjustedWeightBasis() {
        // Same synthetic profile as above but bodyFatPct unknown: BMI = 110/1.75^2 = 35.92 >= 30, so
        // the basis is ADJUSTED_WEIGHT (ref = 25*1.75^2 = 76.5625; adjusted = 76.5625 +
        // 0.4*(110-76.5625) = 89.9375), not raw body weight. Calories/rate are unaffected by the
        // protein basis choice.
        Input input =
                new Input(Sex.MALE, 40, 175, 110, ActivityLevel.LIGHTLY_ACTIVE, Goal.LOSE_FAT, Pace.MODERATE, DietStyle.BALANCED, true, null);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(2061);
        assertThat(targets.weeklyRateKg()).isEqualTo(-0.62);
        assertThat(targets.dailyAdjustmentKcal()).isEqualTo(-687.0);
        assertThat(targets.proteinBasis()).isEqualTo(ProteinBasis.ADJUSTED_WEIGHT);
        assertThat(targets.leanMassKg()).isNull();
        assertThat(targets.proteinBasisKg()).isEqualTo(89.94);
        assertThat(targets.proteinGrams()).isEqualTo(180);
        assertThat(targets.fatGrams()).isEqualTo(69);
        assertThat(targets.carbGrams()).isEqualTo(181);
        // Same water target as the bodyFatPct-known test above -- water's BMI>=30 check never looks
        // at bodyFatPct at all, only height/weight.
        assertThat(targets.waterMl()).isEqualTo(3148);
        assertThat(targets.notes()).extracting(NutritionCalculator.TargetNote::code).containsExactly(NoteCode.RATE_CAPPED);
    }

    @Test
    void recompWithoutStrengthTraining_usesTenPercentDeficitAndTheUntrainedProteinTable() {
        // BMR = 10*65 + 6.25*165 - 5*28 - 161 = 1380.25; TDEE = 1380.25*1.55 = 2139.3875.
        // RECOMP always uses a fixed -10% of TDEE (never a pace) -> -213.93875 kcal/day, well under
        // the 25% cap (not rate-capped) and well above the female floor (not floor-applied) --
        // RECOMP without strength training gets a note, and its untrained BODY_WEIGHT g/kg is 1.6.
        Input input = new Input(Sex.FEMALE, 28, 165, 65, ActivityLevel.MODERATELY_ACTIVE, Goal.RECOMP, null, DietStyle.BALANCED, false, null);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(1925);
        assertThat(targets.floorApplied()).isFalse();
        assertThat(targets.weeklyRateKg()).isEqualTo(-0.19);
        assertThat(targets.dailyAdjustmentKcal()).isEqualTo(-214.0);
        assertThat(targets.proteinBasis()).isEqualTo(ProteinBasis.BODY_WEIGHT);
        assertThat(targets.proteinBasisKg()).isEqualTo(65.0);
        assertThat(targets.proteinGrams()).isEqualTo(104); // 65 * 1.6 (untrained), not 65 * 2.2 (trained)
        assertThat(targets.fatGrams()).isEqualTo(64);
        assertThat(targets.carbGrams()).isEqualTo(233);
        assertThat(targets.fiberGrams()).isEqualTo(27);
        assertThat(targets.sugarMaxGrams()).isEqualTo(87);
        assertThat(targets.waterMl()).isEqualTo(2275);
        assertThat(targets.notes()).extracting(NutritionCalculator.TargetNote::code)
                .containsExactly(NoteCode.STRENGTH_TRAINING_RECOMMENDED);
    }

    @Test
    void buildMuscleFast_highProtein_withStrengthTraining_boostsProteinWithoutHittingTheLeanMassCap() {
        // BMR = 10*75 + 6.25*180 - 5*25 + 5 = 1755; TDEE = 1755*1.725 = 3027.375.
        // BUILD_MUSCLE FAST = +0.5% of body weight/week -> +0.375 kg/week -> +412.5 kcal/day
        // (surpluses are never capped). bodyFatPct known (15%) -> LEAN_MASS = 75*0.85 = 63.75.
        // Base g/kg for BUILD_MUSCLE on LEAN_MASS (trained) is 2.2; HIGH_PROTEIN adds 0.3 -> 2.5,
        // under the 2.8 LEAN_MASS cap. Strength training is already true -> no recommendation note.
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
        assertThat(targets.sugarMaxGrams()).isEqualTo(155);
        assertThat(targets.waterMl()).isEqualTo(2625);
        assertThat(targets.notes()).isEmpty();
    }

    @Test
    void loseFatSlow_highProtein_onBodyWeightBasis_capsTheProteinBonusAndHitsTheFatFloor() {
        // BMR = 10*70 + 6.25*160 - 5*35 - 161 = 1364; TDEE = 1364*1.2 = 1636.8; BMI = 27.3 < 30 and
        // no bodyFatPct -> BODY_WEIGHT basis (70 kg). strengthTraining=true (trained table) so this
        // test stays isolated from the untrained-protein rule: base g/kg for LOSE_FAT on BODY_WEIGHT
        // is 2.2; HIGH_PROTEIN's +0.3 would be 2.5, but the cap for a non-LEAN_MASS basis is 2.4 ->
        // capped. Fat: 30% of kcal is 41.73 g, but the 0.6 g/kg-of-basis floor is 42 g -> floor wins.
        Input input = new Input(Sex.FEMALE, 35, 160, 70, ActivityLevel.SEDENTARY, Goal.LOSE_FAT, Pace.SLOW, DietStyle.HIGH_PROTEIN, true, null);

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
        assertThat(targets.fatGrams()).isEqualTo(213); // remainder after protein (untrained, 108g) and carbs (130g)
        assertThat(targets.fiberGrams()).isEqualTo(40); // fiber/sugar are unchanged (not overridden) for LOW_CARB
        assertThat(targets.sugarMaxGrams()).isEqualTo(129);
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
        assertThat(targets.fatGrams()).isEqualTo(76);
    }

    @Test
    void dietStyleKeto_fixesCarbsAndOverridesFiberAndSugar() {
        // BMR = 10*85 + 6.25*170 - 5*45 + 5 = 1692.5; TDEE = 1692.5*1.375 = 2327.1875; MAINTAIN.
        // KETO always fixes carbs at 30g, fiber at 20g (not 14g/1000kcal), and sugar max at 5% of
        // kcal (not the 18% total-sugars formula the other diet styles use) -- and always adds the
        // KETO_FIBER note.
        Input input = new Input(Sex.MALE, 45, 170, 85, ActivityLevel.LIGHTLY_ACTIVE, Goal.MAINTAIN, null, DietStyle.KETO, false, null);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(2327);
        assertThat(targets.carbGrams()).isEqualTo(30);
        assertThat(targets.fatGrams()).isEqualTo(200);
        assertThat(targets.fiberGrams()).isEqualTo(20);
        assertThat(targets.sugarMaxGrams()).isEqualTo(29);
        assertThat(targets.notes()).extracting(NutritionCalculator.TargetNote::code).containsExactly(NoteCode.KETO_FIBER);
    }

    @Test
    void loseWeightFast_smallSedentaryFemale_hitsBothTheRateCapAndTheCalorieFloor() {
        // BMR = 10*45 + 6.25*150 - 5*45 - 161 = 1001.5; TDEE = 1001.5*1.2 = 1201.8.
        // FAST loss = 1% of body weight/week -> -0.45 kg/week -> -495 kcal/day, but the 25% TDEE
        // cap is -300.45 -> capped; TDEE + that adjustment (901.35) is still below the 1200 female
        // floor -> floor applied too (tdee itself, 1201.8, is still just above the floor, so this
        // is FLOOR_APPLIED, not DEFICIT_NOT_POSSIBLE -- see that separate test below).
        // strengthTraining=true (trained table) keeps this isolated from the untrained-protein rule.
        // Once the floor clamps calories to 1200, the rate is recomputed FROM that 1200 rather than
        // reused from the pre-floor pace math: the real adjustment is only 1200-1201.8 = -1.8
        // kcal/day (~0 kg/week), nowhere near the -300 kcal/-0.27 kg/week the capped pace implied.
        Input input = new Input(Sex.FEMALE, 45, 150, 45, ActivityLevel.SEDENTARY, Goal.LOSE_WEIGHT, Pace.FAST, DietStyle.BALANCED, true, null);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(1200);
        assertThat(targets.floorApplied()).isTrue();
        assertThat(targets.weeklyRateKg()).isEqualTo(0.0);
        assertThat(targets.dailyAdjustmentKcal()).isEqualTo(-2.0);
        assertThat(targets.proteinGrams()).isEqualTo(81);
        assertThat(targets.fatGrams()).isEqualTo(40);
        assertThat(targets.carbGrams()).isEqualTo(129);
        assertThat(targets.fiberGrams()).isEqualTo(25); // the new 25g floor, not 14g/1000kcal of 1200 (16.8)
        assertThat(targets.sugarMaxGrams()).isEqualTo(54);
        assertThat(targets.waterMl()).isEqualTo(1575);
        assertThat(targets.notes()).extracting(NutritionCalculator.TargetNote::code)
                .containsExactly(NoteCode.FLOOR_APPLIED, NoteCode.RATE_CAPPED);
    }

    @Test
    void loseWeight_tdeeBelowSexFloor_targetFallsBackToTdeeWithDeficitNotPossibleNote() {
        // A 70-year-old woman, 152cm, 50kg, sedentary: BMR = 10*50 + 6.25*152 - 5*70 - 161 = 939;
        // TDEE = 939*1.2 = 1126.8 -- already BELOW the 1200 female floor, so there is no safe
        // deficit to apply at all. The target falls back to TDEE itself (never forced UP to the
        // floor and never above TDEE either), and the note is DEFICIT_NOT_POSSIBLE, not
        // FLOOR_APPLIED. MODERATE pace's own pre-floor deficit also exceeds the 25% cap, so
        // RATE_CAPPED still fires alongside it.
        Input input = new Input(
                Sex.FEMALE, 70, 152, 50, ActivityLevel.SEDENTARY, Goal.LOSE_WEIGHT, Pace.MODERATE, DietStyle.BALANCED, false, null);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(1127); // == TDEE (rounded), not the 1200 floor
        assertThat(targets.floorApplied()).isFalse();
        assertThat(targets.dailyAdjustmentKcal()).isEqualTo(0.0);
        assertThat(targets.weeklyRateKg()).isEqualTo(0.0);
        assertThat(targets.notes()).extracting(NutritionCalculator.TargetNote::code)
                .containsExactly(NoteCode.DEFICIT_NOT_POSSIBLE, NoteCode.RATE_CAPPED);
    }

    @Test
    void maintain_sameLowTdeeProfile_alsoTargetsTdeeButNeverTouchesTheFloorLogicAtAll() {
        // Same 70-year-old woman as above, MAINTAIN instead of LOSE_WEIGHT: MAINTAIN isn't a
        // deficit goal (see Goal#isDeficit), so it never even evaluates the floor -- it just lands
        // on the same TDEE-based target, with no note at all (not even DEFICIT_NOT_POSSIBLE).
        Input input = new Input(
                Sex.FEMALE, 70, 152, 50, ActivityLevel.SEDENTARY, Goal.MAINTAIN, null, DietStyle.BALANCED, false, null);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(1127);
        assertThat(targets.floorApplied()).isFalse();
        assertThat(targets.notes()).isEmpty();
    }

    @Test
    void loseFatFast_ketoSmallDeficitAfterFloor_recomputesTheRateFromTheActualTarget() {
        // A 55-year-old woman, 158cm, 70kg, sedentary, LOSE_FAT FAST, KETO: BMR = 10*70 + 6.25*158 -
        // 5*55 - 161 = 1251.5; TDEE = 1251.5*1.2 = 1501.8. FAST loss (1% of body weight/week) wants
        // -770 kcal/day, but the 25% TDEE cap limits the pre-floor request to -375.45 first, and
        // THEN the 1200 female floor clamps calories to 1200 -- a much smaller effective deficit
        // than either the pace or the cap alone implied. The displayed rate reflects that smaller,
        // ACTUAL deficit (~-0.27 kg/week off the 1200-vs-1501.8 gap), not the bigger one the capped
        // pace alone would have shown.
        Input input = new Input(Sex.FEMALE, 55, 158, 70, ActivityLevel.SEDENTARY, Goal.LOSE_FAT, Pace.FAST, DietStyle.KETO, true, null);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.calories()).isEqualTo(1200);
        assertThat(targets.floorApplied()).isTrue();
        assertThat(targets.dailyAdjustmentKcal()).isEqualTo(-302.0);
        assertThat(targets.weeklyRateKg()).isEqualTo(-0.27);
        assertThat(targets.notes()).extracting(NutritionCalculator.TargetNote::code)
                .containsExactly(NoteCode.FLOOR_APPLIED, NoteCode.RATE_CAPPED, NoteCode.KETO_FIBER);
    }

    @Test
    void proteinGramsPerKg_bodyWeightBasis_trainedUsesTheHigherTableThanUntrained() {
        // Same profile as the very first MAINTAIN test (BODY_WEIGHT basis, 80kg): strengthTraining
        // is the ONLY thing that differs between these two calls, proving it alone switches which
        // g/kg table applies -- 1.6 g/kg trained vs. 1.2 g/kg untrained for MAINTAIN.
        Input trained = new Input(
                Sex.MALE, 30, 180, 80, ActivityLevel.MODERATELY_ACTIVE, Goal.MAINTAIN, null, DietStyle.BALANCED, true, null);
        Input untrained = new Input(
                Sex.MALE, 30, 180, 80, ActivityLevel.MODERATELY_ACTIVE, Goal.MAINTAIN, null, DietStyle.BALANCED, false, null);

        assertThat(calculator.calculate(trained).proteinGrams()).isEqualTo(128); // 80 * 1.6 (trained)
        assertThat(calculator.calculate(untrained).proteinGrams()).isEqualTo(96); // 80 * 1.2 (untrained)
    }

    @Test
    void proteinGramsPerKg_leanMassBasis_trainedUsesTheHigherTableThanUntrained() {
        // Same profile as the BUILD_MUSCLE worked example (LEAN_MASS basis, 63.75kg). The untrained
        // case also earns STRENGTH_TRAINING_RECOMMENDED (BUILD_MUSCLE benefits from it); the
        // trained case does not.
        Input trained = new Input(
                Sex.MALE, 25, 180, 75, ActivityLevel.VERY_ACTIVE, Goal.BUILD_MUSCLE, Pace.FAST, DietStyle.BALANCED, true, 15.0);
        Input untrained = new Input(
                Sex.MALE, 25, 180, 75, ActivityLevel.VERY_ACTIVE, Goal.BUILD_MUSCLE, Pace.FAST, DietStyle.BALANCED, false, 15.0);

        NutritionTargets trainedTargets = calculator.calculate(trained);
        NutritionTargets untrainedTargets = calculator.calculate(untrained);

        assertThat(trainedTargets.proteinGrams()).isEqualTo(140); // 63.75 * 2.2 (trained)
        assertThat(trainedTargets.notes()).isEmpty();
        assertThat(untrainedTargets.proteinGrams()).isEqualTo(128); // 63.75 * 2.0 (untrained)
        assertThat(untrainedTargets.notes()).extracting(NutritionCalculator.TargetNote::code)
                .containsExactly(NoteCode.STRENGTH_TRAINING_RECOMMENDED);
    }

    @Test
    void waterMl_belowMinimum_clampsToFifteenHundred() {
        // 40kg at 150cm: BMI 17.8 (< 30, so raw body weight is the basis) * 35 ml/kg = 1400 ml,
        // below the 1500 ml safety minimum -> clamped up.
        Input input = new Input(Sex.FEMALE, 30, 150, 40, ActivityLevel.SEDENTARY, Goal.MAINTAIN, null, DietStyle.BALANCED, false, null);

        assertThat(calculator.calculate(input).waterMl()).isEqualTo(1500);
    }

    @Test
    void waterMl_aboveMaximum_clampsToThirtyFiveHundred() {
        // 110kg at 200cm: BMI 27.5 (< 30, so still raw body weight, not the BMI-adjusted one) * 35
        // ml/kg = 3850 ml, above the 3500 ml safety maximum -> clamped down.
        Input input = new Input(Sex.MALE, 30, 200, 110, ActivityLevel.SEDENTARY, Goal.MAINTAIN, null, DietStyle.BALANCED, false, null);

        assertThat(calculator.calculate(input).waterMl()).isEqualTo(3500);
    }

    @Test
    void fiberGrams_lowCalorieTarget_flowsToTheTwentyFiveGramFloor() {
        // A 60-year-old woman, 150cm, 45kg, sedentary, MAINTAIN: BMR = 10*45 + 6.25*150 - 5*60 -
        // 161 = 926.5; TDEE = 926.5*1.2 = 1111.8. 14 g/1000kcal of 1111.8 kcal is only ~15.6g, well
        // under the new 25g floor -- and sugar at this same low target is 18% of kcal / 4 = 50g.
        Input input = new Input(Sex.FEMALE, 60, 150, 45, ActivityLevel.SEDENTARY, Goal.MAINTAIN, null, DietStyle.BALANCED, false, null);

        NutritionTargets targets = calculator.calculate(input);

        assertThat(targets.fiberGrams()).isEqualTo(25);
        assertThat(targets.sugarMaxGrams()).isEqualTo(50);
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
        assertThat(targets.proteinGrams()).isEqualTo(84); // 70 * 1.2 (untrained), not 70 * 1.6 (trained)
        assertThat(targets.fatGrams()).isEqualTo(117);
        assertThat(targets.carbGrams()).isEqualTo(528);
        assertThat(targets.fiberGrams()).isEqualTo(49);
        assertThat(targets.sugarMaxGrams()).isEqualTo(157);
        assertThat(targets.waterMl()).isEqualTo(2450);
        assertThat(targets.notes()).isEmpty();
    }

    @Test
    void carbGramsClampToZero_whenProteinAndFatAlreadyExceedTheCappedFloorCalories() {
        // An intentionally extreme combination (high lean mass on a very restricted, floor-adjacent
        // calorie target) to exercise the Math.max(..., 0) clamp: protein (924 kcal) + the fat
        // g/kg-of-basis floor (462 kcal) already add up to more than the 1278.225 kcal target, so
        // carbs must clamp to 0 rather than go negative. strengthTraining=true (trained table) keeps
        // this isolated from the untrained-protein rule, which would otherwise leave room for carbs.
        Input input = new Input(Sex.FEMALE, 55, 145, 95, ActivityLevel.SEDENTARY, Goal.LOSE_FAT, Pace.FAST, DietStyle.HIGH_PROTEIN, true, 10.0);

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

    @Test
    void goal_isDeficitMatchesTheGoalsThatRunACalorieDeficit() {
        assertThat(Goal.LOSE_FAT.isDeficit()).isTrue();
        assertThat(Goal.LOSE_WEIGHT.isDeficit()).isTrue();
        assertThat(Goal.RECOMP.isDeficit()).isTrue();
        assertThat(Goal.MAINTAIN.isDeficit()).isFalse();
        assertThat(Goal.BUILD_MUSCLE.isDeficit()).isFalse();
        assertThat(Goal.GAIN_WEIGHT.isDeficit()).isFalse();
    }

    // --- Sprint 3a: the explicit-TDEE overload the adaptive check-in blends in (see
    // com.kcalma.checkin.CheckinService / AdaptiveTdeeService) instead of BMR x activity. ---

    @Test
    void tdee_moderatelyActiveMale_matchesBmrTimesActivityFactor() {
        // Same profile as the very first test: BMR = 1780, activity factor 1.55 -> TDEE = 2759.
        Input input = new Input(
                Sex.MALE, 30, 180, 80, ActivityLevel.MODERATELY_ACTIVE, Goal.MAINTAIN, null, DietStyle.BALANCED, false, null);

        assertThat(calculator.tdee(input)).isEqualTo(2759.0);
    }

    @Test
    void calculate_withExplicitTdee_matchesTheSingleArgOverload_whenGivenItsOwnFormulaTdee() {
        Input input = new Input(
                Sex.MALE, 40, 175, 110, ActivityLevel.LIGHTLY_ACTIVE, Goal.LOSE_FAT, Pace.MODERATE, DietStyle.BALANCED, true, 40.0);

        NutritionTargets viaFormula = calculator.calculate(input);
        NutritionTargets viaExplicitTdee = calculator.calculate(input, calculator.tdee(input));

        assertThat(viaExplicitTdee).isEqualTo(viaFormula);
    }

    @Test
    void calculate_withExplicitTdee_usesItInsteadOfTheFormulaForAMaintainGoal() {
        // MAINTAIN has no goal/pace adjustment of its own, so calories should track the override
        // (2400) exactly -- not the formula's 2759 -- proving the pipeline used it as the base.
        Input input = new Input(
                Sex.MALE, 30, 180, 80, ActivityLevel.MODERATELY_ACTIVE, Goal.MAINTAIN, null, DietStyle.BALANCED, false, null);

        NutritionTargets targets = calculator.calculate(input, 2400.0);

        assertThat(targets.calories()).isEqualTo(2400);
        assertThat(targets.floorApplied()).isFalse();
    }

    @Test
    void calculate_withExplicitTdee_computesTheDeficitCapRelativeToTheOverrideNotTheFormula() {
        // FAST loss is a fixed 1% of body weight/week regardless of TDEE: 80kg -> -0.8 kg/week ->
        // -880 kcal/day, uncapped. With the FORMULA tdee (2759) the 25% cap (-689.75) would already
        // bind; with this override (2000) instead the cap is tighter still (-500), and the
        // resulting calories (2000 - 500 = 1500) prove the cap used the override, not the 2759. The
        // override (2000) also sits above the male floor (1500), so the floor itself never binds.
        Input input = new Input(
                Sex.MALE, 30, 180, 80, ActivityLevel.MODERATELY_ACTIVE, Goal.LOSE_WEIGHT, Pace.FAST, DietStyle.BALANCED, false, null);

        NutritionTargets targets = calculator.calculate(input, 2000.0);

        assertThat(targets.dailyAdjustmentKcal()).isEqualTo(-500.0);
        assertThat(targets.calories()).isEqualTo(1500);
        assertThat(targets.notes()).extracting(NutritionCalculator.TargetNote::code).containsExactly(NoteCode.RATE_CAPPED);
    }
}
