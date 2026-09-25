package com.kcalma.food;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcalma.food.analysis.FoodAnalyzer;
import com.kcalma.security.SecurityConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

/**
 * Web slice test for DELETE /api/food/entries?date=&amp;mealType= — the delete-a-whole-meal
 * endpoint: missing/invalid query param validation (400, Spanish message), the happy path (204,
 * always — even when nothing matched), and the same owner-allowlist security contract as the rest
 * of {@link FoodController}. Same {@code @WebMvcTest} + {@code SecurityConfig} setup as
 * {@link FoodControllerSecurityTest} and {@link FoodControllerTextAnalysisTest}; see the latter for
 * why the {@link ResponseStatusException} reason is asserted directly instead of via the response
 * body in this slice.
 */
@WebMvcTest(FoodController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173"
})
class FoodControllerDeleteMealTest {

    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String NON_OWNER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FoodAnalyzer foodAnalyzer;

    @MockitoBean
    private FoodEntryService foodEntryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void missingDate_returns400AndNeverCallsService() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));

        MvcResult result = mockMvc.perform(authenticatedDelete().param("mealType", "DESAYUNO"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertReason(result, "Falta la fecha.");
        verifyNoInteractions(foodEntryService);
    }

    @Test
    void invalidDate_returns400AndNeverCallsService() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));

        MvcResult result = mockMvc.perform(authenticatedDelete().param("date", "not-a-date").param("mealType", "DESAYUNO"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertReason(result, "La fecha no es válida.");
        verifyNoInteractions(foodEntryService);
    }

    @Test
    void missingMealType_returns400AndNeverCallsService() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));

        MvcResult result = mockMvc.perform(authenticatedDelete().param("date", "2026-09-25"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertReason(result, "Falta el tipo de comida.");
        verifyNoInteractions(foodEntryService);
    }

    @Test
    void invalidMealType_returns400AndNeverCallsService() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));

        MvcResult result = mockMvc.perform(
                        authenticatedDelete().param("date", "2026-09-25").param("mealType", "BRUNCH"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertReason(result, "El tipo de comida no es válido.");
        verifyNoInteractions(foodEntryService);
    }

    @Test
    void validRequest_returns204AndDeletesScopedToOwnerDateAndMeal() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));

        mockMvc.perform(authenticatedDelete().param("date", "2026-09-25").param("mealType", "DESAYUNO"))
                .andExpect(status().isNoContent());

        verify(foodEntryService)
                .deleteAllByMeal(UUID.fromString(OWNER_ID), LocalDate.parse("2026-09-25"), MealType.DESAYUNO);
    }

    @Test
    void validRequestWhenNothingMatches_stillReturns204() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        // deleteAllByMeal returns void and is a no-op when nothing matched — no stubbing needed;
        // the point of this test is that the controller doesn't special-case "nothing deleted".

        mockMvc.perform(authenticatedDelete().param("date", "2026-09-25").param("mealType", "CENA"))
                .andExpect(status().isNoContent());
    }

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/food/entries").param("date", "2026-09-25").param("mealType", "DESAYUNO"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(foodEntryService);
    }

    @Test
    void validTokenFromNonOwner_returns403() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(NON_OWNER_ID));

        mockMvc.perform(authenticatedDelete().param("date", "2026-09-25").param("mealType", "DESAYUNO"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(foodEntryService);
    }

    private static void assertReason(MvcResult result, String expectedReason) {
        org.assertj.core.api.Assertions.assertThat(result.getResolvedException())
                .isInstanceOf(ResponseStatusException.class);
        org.assertj.core.api.Assertions.assertThat(((ResponseStatusException) result.getResolvedException()).getReason())
                .isEqualTo(expectedReason);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticatedDelete() {
        return delete("/api/food/entries").header("Authorization", "Bearer " + TOKEN);
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
}
