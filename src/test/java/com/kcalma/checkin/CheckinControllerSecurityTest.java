package com.kcalma.checkin;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcalma.checkin.dto.CheckinResponse;
import com.kcalma.checkin.dto.CheckinWithTargetsResponse;
import com.kcalma.config.ClockConfig;
import com.kcalma.profile.EnergySource;
import com.kcalma.profile.ProteinBasis;
import com.kcalma.profile.dto.NutritionTargetsResponse;
import com.kcalma.security.SecurityConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
 * Web/security slice test for the check-in endpoints, same open-registration contract as {@code
 * WeightControllerSecurityTest}: no token -&gt; 401, anonymous Supabase user -&gt; 403, any other
 * authenticated user -&gt; 200.
 */
@WebMvcTest(CheckinController.class)
@Import({SecurityConfig.class, ClockConfig.class})
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173",
    "app.timezone=America/Argentina/Buenos_Aires"
})
class CheckinControllerSecurityTest {

    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String NON_OWNER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckinService checkinService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/checkin")).andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenFromNonOwner_returns200() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(NON_OWNER_ID));
        when(checkinService.getCurrentWeek(UUID.fromString(NON_OWNER_ID))).thenReturn(java.util.Optional.of(sampleCheckin()));

        mockMvc.perform(get("/api/checkin").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isOk());
    }

    @Test
    void anonymousSupabaseToken_returns403() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(anonymousJwtFor(NON_OWNER_ID));

        mockMvc.perform(get("/api/checkin").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isForbidden());
    }

    @Test
    void owner_get_returns200() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(checkinService.getCurrentWeek(UUID.fromString(OWNER_ID))).thenReturn(java.util.Optional.of(sampleCheckin()));

        mockMvc.perform(get("/api/checkin").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isOk());
    }

    @Test
    void owner_accept_returns200() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(checkinService.accept(UUID.fromString(OWNER_ID))).thenReturn(new CheckinWithTargetsResponse(sampleCheckin(), sampleTargets()));

        mockMvc.perform(post("/api/checkin/accept").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isOk());
    }

    @Test
    void owner_dismiss_returns200() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(checkinService.dismiss(UUID.fromString(OWNER_ID))).thenReturn(sampleCheckin());

        mockMvc.perform(post("/api/checkin/dismiss").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isOk());
    }

    @Test
    void owner_history_returns200() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(checkinService.history(UUID.fromString(OWNER_ID), 12)).thenReturn(List.of());

        mockMvc.perform(get("/api/checkin/history").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isOk());
    }

    private static CheckinResponse sampleCheckin() {
        return new CheckinResponse(
                LocalDate.of(2026, 9, 21), CheckinStatus.PENDING, LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 26),
                15, 8, 2100, -0.5, 2759, 2759, 2500, 2620, 2759, 2620, Confidence.MEDIUM, List.of());
    }

    private static NutritionTargetsResponse sampleTargets() {
        return new NutritionTargetsResponse(
                2620, false, 128, 92, 355, 39, 69, 2000, 2800, 0.0, 0.0, ProteinBasis.BODY_WEIGHT, 80.0, null, List.of(),
                EnergySource.ADAPTIVE, LocalDate.of(2026, 9, 21));
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
