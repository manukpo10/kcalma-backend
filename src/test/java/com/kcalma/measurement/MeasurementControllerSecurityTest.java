package com.kcalma.measurement;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcalma.config.ClockConfig;
import com.kcalma.security.SecurityConfig;
import java.time.Instant;
import java.time.LocalDate;
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

/** Web/security slice test for the measurements endpoints, same owner-allowlist contract as {@code WeightControllerSecurityTest}. */
@WebMvcTest(MeasurementController.class)
@Import({SecurityConfig.class, ClockConfig.class})
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173",
    "app.timezone=America/Argentina/Buenos_Aires"
})
class MeasurementControllerSecurityTest {

    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String NON_OWNER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeasurementService measurementService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void noToken_list_returns401() throws Exception {
        mockMvc.perform(get("/api/measurements").param("from", "2026-09-01").param("to", "2026-09-25"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenFromNonOwner_list_returns403() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(NON_OWNER_ID));

        mockMvc.perform(get("/api/measurements")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-25"))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_list_returns200() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(measurementService.findRange(
                        UUID.fromString(OWNER_ID), LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-25")))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/measurements")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-25"))
                .andExpect(status().isOk());
    }

    @Test
    void noToken_upsert_returns401() throws Exception {
        mockMvc.perform(put("/api/measurements/2026-09-25")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"waistCm\": 80}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void owner_upsertWithNoFieldsAtAll_returns400AndNeverCallsService() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));

        mockMvc.perform(put("/api/measurements/2026-09-25")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(measurementService);
    }

    @Test
    void owner_deleteMissing_returns404() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(measurementService.delete(UUID.fromString(OWNER_ID), LocalDate.parse("2026-09-25"))).thenReturn(false);

        mockMvc.perform(delete("/api/measurements/2026-09-25").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());
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
