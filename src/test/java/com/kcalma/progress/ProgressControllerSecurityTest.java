package com.kcalma.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcalma.progress.dto.ProgressResponse;
import com.kcalma.security.SecurityConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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

/**
 * Web/security slice test for GET /api/progress, same owner-allowlist contract as
 * {@code DayControllerSecurityTest}: no token -> 401, non-owner -> 403, owner -> 200. Also covers
 * the one controller-level validation: an unknown {@code range} value -> 400 Spanish message.
 */
@WebMvcTest(ProgressController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173"
})
class ProgressControllerSecurityTest {

    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String NON_OWNER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProgressService progressService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/progress").param("range", "1M")).andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenFromNonOwner_returns403() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(NON_OWNER_ID));

        mockMvc.perform(get("/api/progress").header("Authorization", "Bearer " + TOKEN).param("range", "1M"))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_returns200() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(progressService.getProgress(UUID.fromString(OWNER_ID), ProgressRange.ONE_MONTH))
                .thenReturn(Optional.of(sampleResponse()));

        mockMvc.perform(get("/api/progress").header("Authorization", "Bearer " + TOKEN).param("range", "1M"))
                .andExpect(status().isOk());
    }

    @Test
    void owner_noProfileYet_returns404() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(progressService.getProgress(UUID.fromString(OWNER_ID), ProgressRange.ALL)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/progress").header("Authorization", "Bearer " + TOKEN).param("range", "ALL"))
                .andExpect(status().isNotFound());
    }

    @Test
    void owner_unknownRange_returns400WithSpanishMessage() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));

        org.springframework.test.web.servlet.MvcResult result = mockMvc
                .perform(get("/api/progress").header("Authorization", "Bearer " + TOKEN).param("range", "6M"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getErrorMessage()).isEqualTo("El rango no es válido. Usá 1M, 3M, 1Y o ALL.");
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

    private static ProgressResponse sampleResponse() {
        LocalDate to = LocalDate.of(2026, 9, 25);
        LocalDate from = to.minusMonths(1);
        ProgressResponse.Stats stats = new ProgressResponse.Stats(null, null, null, null, null, null, null, null);
        ProgressResponse.Nutrition nutrition = new ProgressResponse.Nutrition(List.of(), null, null, null, 0);
        return new ProgressResponse("1M", from, to, List.of(), stats, nutrition);
    }
}
