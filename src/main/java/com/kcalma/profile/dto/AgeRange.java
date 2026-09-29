package com.kcalma.profile.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a {@code LocalDate} birth date yields an age within [{@link #min}, {@link #max}]
 * years, computed against the app's own zoned {@code Clock} (see {@code ClockConfig}) — never the
 * system default clock/zone, so this stays consistent with every other age/date computation in the
 * domain (see {@code ProfileService}/{@code CheckinService}). Kcalma's formulas (Mifflin-St Jeor,
 * the protein g/kg tables) are only validated for adults roughly 18-100 years old; outside that
 * range the targets this API would compute aren't clinically meaningful. See {@link
 * AgeRangeValidator}.
 */
@Documented
@Constraint(validatedBy = AgeRangeValidator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface AgeRange {

    String message() default "Kcalma es para personas de entre 18 y 100 años.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int min() default 18;

    int max() default 100;
}
