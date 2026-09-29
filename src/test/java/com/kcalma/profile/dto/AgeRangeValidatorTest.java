package com.kcalma.profile.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test for {@link AgeRangeValidator} — a {@link Clock} fixed to a specific instant so
 * "today" (and therefore every computed age) never depends on the machine running the test. The
 * annotation instance is read off a dummy element so {@code min}/{@code max} come from {@link
 * AgeRange}'s real defaults (18/100), exactly as Spring's {@code SpringConstraintValidatorFactory}
 * would build and {@code initialize()} this validator in the running app — see {@code
 * ProfileControllerValidationTest} for the same rule proven end to end through a real HTTP request.
 */
class AgeRangeValidatorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneId.of("UTC"));

    private final AgeRangeValidator validator = newInitializedValidator();

    @Test
    void isValid_exactlyEighteenYearsOld_isTrue() {
        assertThat(validator.isValid(LocalDate.of(2008, 9, 27), null)).isTrue();
    }

    @Test
    void isValid_oneDayShortOfEighteen_isFalse() {
        assertThat(validator.isValid(LocalDate.of(2008, 9, 28), null)).isFalse();
    }

    @Test
    void isValid_seventeenYearsOld_isFalse() {
        assertThat(validator.isValid(LocalDate.of(2009, 9, 27), null)).isFalse();
    }

    @Test
    void isValid_exactlyOneHundredYearsOld_isTrue() {
        assertThat(validator.isValid(LocalDate.of(1926, 9, 27), null)).isTrue();
    }

    @Test
    void isValid_oneHundredAndOneYearsOld_isFalse() {
        assertThat(validator.isValid(LocalDate.of(1925, 9, 27), null)).isFalse();
    }

    @Test
    void isValid_nullBirthDate_isTrueAndLeftToNotNull() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    private static final class AnnotatedDummy {
        @AgeRange
        LocalDate birthDate;
    }

    private static AgeRangeValidator newInitializedValidator() {
        try {
            AgeRangeValidator validator = new AgeRangeValidator(CLOCK);
            validator.initialize(AnnotatedDummy.class.getDeclaredField("birthDate").getAnnotation(AgeRange.class));
            return validator;
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e); // the field above is declared right here -- this can't happen
        }
    }
}
