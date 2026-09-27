package com.kcalma.food.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.kcalma.food.FoodSource;
import com.kcalma.food.MealType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Bean-validation test for the absurd-value bounds on {@link FoodEntryRequest} and {@link
 * FoodEntryIngredientRequest}: kcal (&lt;= 900/100g), protein/fat/carbs/fiber/sugar (&lt;= 100
 * g/100g), sodium (&lt;= 40000 mg/100g) and grams (&lt;= 5000) all have a realistic ceiling, so a
 * garbage value from a malfunctioning client (or a Gemini hallucination passed straight through)
 * fails {@code @Valid} with a 400, instead of reaching {@code NutritionMath}/the database and
 * risking a 500 or a numeric overflow (columns are {@code NUMERIC(7,2)}/{@code NUMERIC(8,2)}).
 */
class FoodEntryRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static final BigDecimal GRAMS = new BigDecimal("150");
    private static final BigDecimal KCAL = new BigDecimal("200");
    private static final BigDecimal PROTEIN = new BigDecimal("20");
    private static final BigDecimal FAT = new BigDecimal("10");
    private static final BigDecimal CARBS = new BigDecimal("15");
    private static final BigDecimal FIBER = new BigDecimal("2");
    private static final BigDecimal SUGAR = new BigDecimal("1");
    private static final BigDecimal SODIUM = new BigDecimal("300");

    @Test
    void request_withRealisticValues_hasNoViolations() {
        assertThat(VALIDATOR.validate(request(GRAMS, KCAL, PROTEIN, FAT, CARBS, FIBER, SUGAR, SODIUM))).isEmpty();
    }

    @Test
    void request_gramsOver5000_isRejected() {
        assertThat(fieldsOf(request(new BigDecimal("5000.1"), KCAL, PROTEIN, FAT, CARBS, FIBER, SUGAR, SODIUM)))
                .contains("grams");
    }

    @Test
    void request_kcalPer100Over900_isRejected() {
        assertThat(fieldsOf(request(GRAMS, new BigDecimal("900.1"), PROTEIN, FAT, CARBS, FIBER, SUGAR, SODIUM)))
                .contains("kcalPer100");
    }

    @Test
    void request_kcalPer100AtExactly900_isAccepted() {
        assertThat(VALIDATOR.validate(request(GRAMS, new BigDecimal("900"), PROTEIN, FAT, CARBS, FIBER, SUGAR, SODIUM)))
                .isEmpty();
    }

    @Test
    void request_proteinPer100Over100_isRejected() {
        assertThat(fieldsOf(request(GRAMS, KCAL, new BigDecimal("100.1"), FAT, CARBS, FIBER, SUGAR, SODIUM)))
                .contains("proteinPer100");
    }

    @Test
    void request_fatPer100Over100_isRejected() {
        assertThat(fieldsOf(request(GRAMS, KCAL, PROTEIN, new BigDecimal("100.1"), CARBS, FIBER, SUGAR, SODIUM)))
                .contains("fatPer100");
    }

    @Test
    void request_carbsPer100Over100_isRejected() {
        assertThat(fieldsOf(request(GRAMS, KCAL, PROTEIN, FAT, new BigDecimal("100.1"), FIBER, SUGAR, SODIUM)))
                .contains("carbsPer100");
    }

    @Test
    void request_fiberPer100Over100_isRejected() {
        assertThat(fieldsOf(request(GRAMS, KCAL, PROTEIN, FAT, CARBS, new BigDecimal("100.1"), SUGAR, SODIUM)))
                .contains("fiberPer100");
    }

    @Test
    void request_sugarPer100Over100_isRejected() {
        assertThat(fieldsOf(request(GRAMS, KCAL, PROTEIN, FAT, CARBS, FIBER, new BigDecimal("100.1"), SODIUM)))
                .contains("sugarPer100");
    }

    @Test
    void request_sodiumMgPer100Over40000_isRejected() {
        assertThat(fieldsOf(request(GRAMS, KCAL, PROTEIN, FAT, CARBS, FIBER, SUGAR, new BigDecimal("40000.1"))))
                .contains("sodiumMgPer100");
    }

    @Test
    void request_sodiumMgPer100AtExactly40000_isAccepted() {
        assertThat(VALIDATOR.validate(request(GRAMS, KCAL, PROTEIN, FAT, CARBS, FIBER, SUGAR, new BigDecimal("40000"))))
                .isEmpty();
    }

    @Test
    void ingredient_withRealisticValues_hasNoViolations() {
        assertThat(VALIDATOR.validate(ingredient(GRAMS, KCAL, PROTEIN, FAT, CARBS, FIBER, SUGAR, SODIUM))).isEmpty();
    }

    @Test
    void ingredient_kcalPer100Over900_isRejected() {
        assertThat(fieldsOf(ingredient(GRAMS, new BigDecimal("900.1"), PROTEIN, FAT, CARBS, FIBER, SUGAR, SODIUM)))
                .contains("kcalPer100");
    }

    @Test
    void ingredient_proteinPer100Over100_isRejected() {
        assertThat(fieldsOf(ingredient(GRAMS, KCAL, new BigDecimal("100.1"), FAT, CARBS, FIBER, SUGAR, SODIUM)))
                .contains("proteinPer100");
    }

    @Test
    void ingredient_sodiumMgPer100Over40000_isRejected() {
        assertThat(fieldsOf(ingredient(GRAMS, KCAL, PROTEIN, FAT, CARBS, FIBER, SUGAR, new BigDecimal("40000.1"))))
                .contains("sodiumMgPer100");
    }

    @Test
    void ingredient_gramsOver5000_isRejected() {
        assertThat(fieldsOf(ingredient(new BigDecimal("5000.1"), KCAL, PROTEIN, FAT, CARBS, FIBER, SUGAR, SODIUM)))
                .contains("grams");
    }

    private static FoodEntryRequest request(
            BigDecimal grams,
            BigDecimal kcal,
            BigDecimal protein,
            BigDecimal fat,
            BigDecimal carbs,
            BigDecimal fiber,
            BigDecimal sugar,
            BigDecimal sodium) {
        return new FoodEntryRequest(
                LocalDate.of(2026, 9, 25),
                MealType.ALMUERZO,
                "Milanesa",
                grams,
                kcal,
                protein,
                fat,
                carbs,
                fiber,
                sugar,
                sodium,
                FoodSource.MANUAL,
                null,
                null);
    }

    private static FoodEntryIngredientRequest ingredient(
            BigDecimal grams,
            BigDecimal kcal,
            BigDecimal protein,
            BigDecimal fat,
            BigDecimal carbs,
            BigDecimal fiber,
            BigDecimal sugar,
            BigDecimal sodium) {
        return new FoodEntryIngredientRequest(
                "Carne", grams, kcal, protein, fat, carbs, fiber, sugar, sodium, FoodSource.MANUAL, null);
    }

    private static <T> Set<String> fieldsOf(T value) {
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(value);
        assertThat(violations).isNotEmpty();
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }
}
