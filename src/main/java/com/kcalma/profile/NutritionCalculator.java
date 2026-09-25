package com.kcalma.profile;

/**
 * Pure domain service: derives daily nutrition targets from onboarding inputs.
 * No Spring, no I/O — safe to unit test exhaustively without a container.
 *
 * Pipeline: Mifflin-St Jeor BMR x activity factor -&gt; goal adjustment -&gt; calorie floor clamp
 * -&gt; protein (g/kg by goal) -&gt; fat (25% of kcal, floor 0.6 g/kg) -&gt; carbs (remainder, floor 0)
 * -&gt; fiber (14 g / 1000 kcal) -&gt; free sugar cap (10% of kcal) -&gt; sodium cap (2000 mg)
 * -&gt; water (35 ml/kg).
 */
public final class NutritionCalculator {

    private static final double FAT_PERCENT_OF_CALORIES = 0.25;
    private static final double MIN_FAT_GRAMS_PER_KG = 0.6;
    private static final double FIBER_GRAMS_PER_1000_KCAL = 14.0;
    private static final double FREE_SUGAR_MAX_PERCENT_OF_CALORIES = 0.10;
    private static final int SODIUM_MAX_MG = 2000;
    private static final double WATER_ML_PER_KG = 35.0;
    private static final double FEMALE_CALORIE_FLOOR = 1200.0;
    private static final double MALE_CALORIE_FLOOR = 1500.0;

    public NutritionTargets calculate(Input input) {
        double bmr = bmr(input);
        double tdee = bmr * input.activityLevel().factor();
        double goalCalories = tdee * (1 + input.goal().adjustmentPercent());

        double floor = input.sex() == Sex.FEMALE ? FEMALE_CALORIE_FLOOR : MALE_CALORIE_FLOOR;
        boolean floorApplied = goalCalories < floor;
        double targetCalories = Math.max(goalCalories, floor);

        double proteinGrams = input.weightKg() * input.goal().proteinGramsPerKg();
        double proteinCalories = proteinGrams * 4;

        double fatFromPercent = (targetCalories * FAT_PERCENT_OF_CALORIES) / 9;
        double fatMinFromWeight = input.weightKg() * MIN_FAT_GRAMS_PER_KG;
        double fatGrams = Math.max(fatFromPercent, fatMinFromWeight);
        double fatCalories = fatGrams * 9;

        double carbCalories = Math.max(targetCalories - proteinCalories - fatCalories, 0);
        double carbGrams = carbCalories / 4;

        double fiberGrams = FIBER_GRAMS_PER_1000_KCAL * (targetCalories / 1000.0);
        double sugarMaxGrams = (targetCalories * FREE_SUGAR_MAX_PERCENT_OF_CALORIES) / 4;
        double waterMl = input.weightKg() * WATER_ML_PER_KG;

        return new NutritionTargets(
                round(targetCalories),
                floorApplied,
                round(proteinGrams),
                round(fatGrams),
                round(carbGrams),
                round(fiberGrams),
                round(sugarMaxGrams),
                SODIUM_MAX_MG,
                round(waterMl));
    }

    private double bmr(Input input) {
        double base = 10 * input.weightKg() + 6.25 * input.heightCm() - 5 * input.ageYears();
        return input.sex() == Sex.MALE ? base + 5 : base - 161;
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }

    /** Onboarding inputs needed to derive targets. Age is precomputed by the caller (no clock here). */
    public record Input(
            Sex sex,
            int ageYears,
            double heightCm,
            double weightKg,
            ActivityLevel activityLevel,
            Goal goal) {}

    /** Derived daily targets. All amounts are rounded to the nearest whole unit for display. */
    public record NutritionTargets(
            int calories,
            boolean floorApplied,
            int proteinGrams,
            int fatGrams,
            int carbGrams,
            int fiberGrams,
            int sugarMaxGrams,
            int sodiumMaxMg,
            int waterMl) {}
}
