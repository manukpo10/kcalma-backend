package com.kcalma.water;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcalma.config.ClockConfig;
import com.kcalma.security.SecurityConfig;
import com.kcalma.water.dto.WaterResponse;
import java.time.Instant;
import java.time.LocalDate;
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

/** Web/security slice test for POST /api/water, same open-registration contract as {@code WeightControllerSecurityTest}. */
@WebMvcTest(WaterController.class)
@Import({SecurityConfig.class, ClockConfig.class})
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173",
    "app.timezone=America/Argentina/Buenos_Aires"
})
class WaterControllerSecurityTest {

    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String NON_OWNER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WaterLogService waterLogService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/water").contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-09-25\",\"deltaMl\":250}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenFromNonOwner_returns200() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(NON_OWNER_ID));
        when(waterLogService.applyDelta(UUID.fromString(NON_OWNER_ID), LocalDate.parse("2026-09-25"), 250))
                .thenReturn(new WaterResponse(LocalDate.parse("2026-09-25"), 250, 2500));

        mockMvc.perform(post("/api/water")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-25\",\"deltaMl\":250}"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousSupabaseToken_returns403() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(anonymousJwtFor(NON_OWNER_ID));

        mockMvc.perform(post("/api/water")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-25\",\"deltaMl\":250}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_returns200() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(waterLogService.applyDelta(UUID.fromString(OWNER_ID), LocalDate.parse("2026-09-25"), 250))
                .thenReturn(new WaterResponse(LocalDate.parse("2026-09-25"), 250, 2500));

        mockMvc.perform(post("/api/water")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-25\",\"deltaMl\":250}"))
                .andExpect(status().isOk());
    }

    @Test
    void futureDate_returns400AndNeverCallsService() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));

        mockMvc.perform(post("/api/water")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2099-01-01\",\"deltaMl\":250}"))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(waterLogService);
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

    private static Jwt anonymousJwtFor(String subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(TOKEN)
                .header("alg", "ES256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("aud", "authenticated")
                .claim("is_anonymous", true)
                .build();
    }
}
