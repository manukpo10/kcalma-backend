package com.kcalma.weight;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcalma.security.SecurityConfig;
import com.kcalma.weight.dto.UpsertWeightResponse;
import com.kcalma.weight.dto.WeightEntryResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
 * Web/security slice test for the weight endpoints, same owner-allowlist contract as
 * {@code FoodControllerSecurityTest}: no token -> 401, non-owner -> 403, owner -> 200.
 */
@WebMvcTest(WeightController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173"
})
class WeightControllerSecurityTest {

    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String NON_OWNER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WeightEntryService weightEntryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/weights").param("from", "2026-09-01").param("to", "2026-09-25"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenFromNonOwner_returns403() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(NON_OWNER_ID));

        mockMvc.perform(get("/api/weights")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-25"))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_list_returns200() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(weightEntryService.findRange(
                        java.util.UUID.fromString(OWNER_ID), LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-25")))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/weights")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-25"))
                .andExpect(status().isOk());
    }

    @Test
    void owner_upsert_returns200() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        java.util.UUID ownerId = java.util.UUID.fromString(OWNER_ID);
        LocalDate date = LocalDate.parse("2026-09-25");
        WeightEntryResponse entry = new WeightEntryResponse(
                java.util.UUID.randomUUID(), date, new BigDecimal("70.50"), java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now());
        when(weightEntryService.upsert(org.mockito.ArgumentMatchers.eq(ownerId), org.mockito.ArgumentMatchers.eq(date), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new UpsertWeightResponse(entry, true));

        mockMvc.perform(put("/api/weights/2026-09-25")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 70.50}"))
                .andExpect(status().isOk());
    }

    @Test
    void owner_delete_returns204() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(weightEntryService.delete(java.util.UUID.fromString(OWNER_ID), LocalDate.parse("2026-09-25")))
                .thenReturn(true);

        mockMvc.perform(delete("/api/weights/2026-09-25").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNoContent());
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
