package com.kcalma.food;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcalma.food.analysis.FoodAnalyzer;
import com.kcalma.food.reference.FoodReferenceMatcher;
import com.kcalma.ratelimit.GeminiRateLimiter;
import com.kcalma.security.SecurityConfig;
import java.time.Instant;
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
 * Web slice test for POST /api/food/entries' bean-validation contract end to end: an absurd
 * per-100g value (see {@link com.kcalma.food.dto.FoodEntryRequestValidationTest} for the bounds
 * themselves) must produce {@link com.kcalma.web.GlobalExceptionHandler}'s consistent 400 body —
 * a generic Spanish {@code message} plus a {@code fieldErrors} map keyed by the violating field —
 * never a 500, and never call {@link FoodEntryService}. Same {@code @WebMvcTest} + {@code
 * SecurityConfig} setup as {@link FoodControllerSecurityTest}.
 */
@WebMvcTest(FoodController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173"
})
class FoodControllerEntryValidationTest {

    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FoodAnalyzer foodAnalyzer;

    @MockitoBean
    private FoodEntryService foodEntryService;

    @MockitoBean
    private FoodReferenceMatcher foodReferenceMatcher;

    @MockitoBean
    private GeminiRateLimiter geminiRateLimiter;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void saveEntries_kcalPer100Over900_returns400WithFieldErrorAndNeverCallsService() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        String body =
                """
                {"entries": [{
                    "entryDate": "2026-09-25", "mealType": "ALMUERZO", "name": "Torta gigante", "grams": 100,
                    "kcalPer100": 999, "proteinPer100": 5, "fatPer100": 5, "carbsPer100": 5,
                    "fiberPer100": 1, "sugarPer100": 1, "sodiumMgPer100": 100, "source": "MANUAL"
                }]}
                """;

        mockMvc.perform(post("/api/food/entries")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Revisá los datos ingresados."))
                .andExpect(jsonPath("$.fieldErrors['entries[0].kcalPer100']").exists());

        verifyNoInteractions(foodEntryService);
    }

    @Test
    void saveEntries_gramsOver5000_returns400WithFieldError() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        String body =
                """
                {"entries": [{
                    "entryDate": "2026-09-25", "mealType": "ALMUERZO", "name": "Torta gigante", "grams": 6000,
                    "kcalPer100": 200, "proteinPer100": 5, "fatPer100": 5, "carbsPer100": 5,
                    "fiberPer100": 1, "sugarPer100": 1, "sodiumMgPer100": 100, "source": "MANUAL"
                }]}
                """;

        mockMvc.perform(post("/api/food/entries")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['entries[0].grams']").exists());

        verifyNoInteractions(foodEntryService);
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
