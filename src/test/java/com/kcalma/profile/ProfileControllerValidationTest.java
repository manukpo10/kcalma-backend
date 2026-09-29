package com.kcalma.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcalma.config.ClockConfig;
import com.kcalma.profile.dto.NutritionTargetsResponse;
import com.kcalma.profile.dto.ProfileResponse;
import com.kcalma.profile.dto.ProfileWithTargetsResponse;
import com.kcalma.security.SecurityConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web slice test for PUT /api/profile's minimum-age bean-validation contract end to end (see
 * {@code com.kcalma.profile.dto.AgeRange}/{@code AgeRangeValidator}): a birth date outside
 * [18, 100] years must produce {@link com.kcalma.web.GlobalExceptionHandler}'s consistent 400 body
 * — a generic Spanish {@code message} plus a {@code fieldErrors} map keyed by {@code birthDate} —
 * exactly like {@code FoodControllerEntryValidationTest} proves for food entries. Same {@code
 * @WebMvcTest} + {@code SecurityConfig} + {@code ClockConfig} setup as {@code
 * WeightControllerValidationTest}: {@code AgeRangeValidator} is Spring-managed (constructor
 * injection of the real, {@code app.timezone}-zoned {@code Clock}), so {@code ClockConfig} must be
 * in the slice for the constraint to even construct.
 *
 * <p>Birth dates are computed relative to {@code LocalDate.now(...)} in Argentina's zone (like
 * {@code WeightControllerValidationTest}'s future-date test) rather than hardcoded, since {@code
 * ClockConfig} wires a real system clock here, not a fixed test one.
 */
@WebMvcTest(ProfileController.class)
@Import({SecurityConfig.class, ClockConfig.class})
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173",
    "app.timezone=America/Argentina/Buenos_Aires"
})
class ProfileControllerValidationTest {

    private static final ZoneId APP_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String TOKEN = "valid-token";
    private static final String AGE_RANGE_MESSAGE = "Kcalma es para personas de entre 18 y 100 años.";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void put_ageSeventeen_returns400WithBirthDateFieldError() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        LocalDate birthDate = LocalDate.now(APP_ZONE).minusYears(17);

        mockMvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(birthDate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Revisá los datos ingresados."))
                .andExpect(jsonPath("$.fieldErrors.birthDate").value(AGE_RANGE_MESSAGE));
    }

    @Test
    void put_ageOneHundredAndOne_returns400WithBirthDateFieldError() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        LocalDate birthDate = LocalDate.now(APP_ZONE).minusYears(101);

        mockMvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(birthDate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.birthDate").value(AGE_RANGE_MESSAGE));
    }

    @Test
    void put_ageEighteen_isNotRejectedForBirthDate() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(profileService.upsert(any(), any())).thenReturn(sampleResponse());
        LocalDate birthDate = LocalDate.now(APP_ZONE).minusYears(18);

        mockMvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(birthDate)))
                .andExpect(status().isOk());
    }

    private static String requestBody(LocalDate birthDate) {
        return """
                {
                  "sex": "FEMALE",
                  "birthDate": "%s",
                  "heightCm": 165,
                  "weightKg": 60,
                  "activityLevel": "SEDENTARY",
                  "goal": "MAINTAIN"
                }
                """.formatted(birthDate);
    }

    private static Jwt jwtFor(String subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(TOKEN)
                .header("alg", "ES256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("aud", "authenticated")
                .build();
    }

    private static ProfileWithTargetsResponse sampleResponse() {
        UUID id = UUID.fromString(OWNER_ID);
        OffsetDateTime now = OffsetDateTime.now();
        ProfileResponse profile = new ProfileResponse(
                id, Sex.FEMALE, LocalDate.of(1990, 1, 1), 165, new BigDecimal("60.00"),
                ActivityLevel.SEDENTARY, Goal.MAINTAIN, new BigDecimal("55.00"), null, DietStyle.BALANCED, List.of(), false, null, null,
                now, now);
        NutritionTargetsResponse targets = new NutritionTargetsResponse(
                1800, false, 96, 60, 180, 25, 45, 2000, 2100, 0.0, 0.0, ProteinBasis.BODY_WEIGHT, 60.0, null, List.of(),
                EnergySource.FORMULA, null);
        return new ProfileWithTargetsResponse(profile, targets);
    }
}
