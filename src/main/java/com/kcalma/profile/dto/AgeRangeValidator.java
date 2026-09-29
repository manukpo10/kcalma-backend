package com.kcalma.profile.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;

/**
 * Backs {@link AgeRange}. Spring-managed (constructor injection, like every other bean in this
 * app — see {@code LocalValidatorFactoryBean}'s {@code SpringConstraintValidatorFactory}), so the
 * injected, {@code app.timezone}-zoned {@link Clock} is used for "today" instead of the system
 * default. A {@code null} birth date is not this validator's concern — {@code @NotNull} on the
 * same field already covers it, so {@link #isValid} passes it through.
 */
class AgeRangeValidator implements ConstraintValidator<AgeRange, LocalDate> {

    private final Clock clock;

    private int min;
    private int max;

    AgeRangeValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void initialize(AgeRange constraintAnnotation) {
        this.min = constraintAnnotation.min();
        this.max = constraintAnnotation.max();
    }

    @Override
    public boolean isValid(LocalDate birthDate, ConstraintValidatorContext context) {
        if (birthDate == null) {
            return true;
        }
        int age = Period.between(birthDate, LocalDate.now(clock)).getYears();
        return age >= min && age <= max;
    }
}
