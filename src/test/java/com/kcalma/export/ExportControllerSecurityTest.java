package com.kcalma.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcalma.config.ClockConfig;
import com.kcalma.export.dto.ExportResponse;
import com.kcalma.security.SecurityConfig;
import java.time.Instant;
import java.time.OffsetDateTime;
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

/** Web/security slice test for GET /api/export, same open-registration contract as {@code WeightControllerSecurityTest}. */
@WebMvcTest(ExportController.class)
@Import({SecurityConfig.class, ClockConfig.class})
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173",
    "app.timezone=America/Argentina/Buenos_Aires"
})
class ExportControllerSecurityTest {

    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String NON_OWNER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExportService exportService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/export")).andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenFromNonOwner_returns200() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(NON_OWNER_ID));
        when(exportService.buildJson(UUID.fromString(NON_OWNER_ID)))
                .thenReturn(new ExportResponse(OffsetDateTime.now(), null, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of()));

        mockMvc.perform(get("/api/export").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isOk());
    }

    @Test
    void anonymousSupabaseToken_returns403() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(anonymousJwtFor(NON_OWNER_ID));

        mockMvc.perform(get("/api/export").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isForbidden());
    }

    @Test
    void owner_defaultFormat_returns200AsJsonAttachment() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(exportService.buildJson(UUID.fromString(OWNER_ID)))
                .thenReturn(new ExportResponse(OffsetDateTime.now(), null, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of()));

        MvcResult result = mockMvc.perform(get("/api/export").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("attachment").contains(".json");
    }

    @Test
    void owner_csvFormat_returns200AsZipAttachment() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(exportService.buildCsvZip(UUID.fromString(OWNER_ID))).thenReturn(new byte[] {1, 2, 3});

        MvcResult result = mockMvc.perform(get("/api/export").param("format", "csv").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("attachment").contains(".zip");
    }

    @Test
    void owner_invalidFormat_returns400() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));

        mockMvc.perform(get("/api/export").param("format", "xml").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest());
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
