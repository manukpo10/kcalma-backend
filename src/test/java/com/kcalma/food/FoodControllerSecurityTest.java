package com.kcalma.food;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcalma.food.analysis.FoodPhotoAnalyzer;
import com.kcalma.security.SecurityConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web/security slice test for the food-log endpoints, same owner-allowlist contract as
 * {@code ProfileControllerSecurityTest}: no token -> 401, non-owner -> 403, owner -> 200.
 * Exercised against GET /api/food/entries since the chain-level {@code /api/**} rule in
 * {@link SecurityConfig} — not per-controller logic — is what's under test.
 */
@WebMvcTest(FoodController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173"
})
class FoodControllerSecurityTest {

    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String NON_OWNER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FoodPhotoAnalyzer foodPhotoAnalyzer;

    @MockitoBean
    private FoodEntryService foodEntryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/food/entries").param("date", "2026-09-25"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenFromNonOwner_returns403() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(NON_OWNER_ID));

        mockMvc.perform(get("/api/food/entries").header("Authorization", "Bearer " + TOKEN).param("date", "2026-09-25"))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_returns200() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(foodEntryService.findByDate(java.util.UUID.fromString(OWNER_ID), LocalDate.parse("2026-09-25")))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/food/entries").header("Authorization", "Bearer " + TOKEN).param("date", "2026-09-25"))
                .andExpect(status().isOk());
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
