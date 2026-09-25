package com.kcalma.profile;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcalma.profile.dto.NutritionTargetsResponse;
import com.kcalma.profile.dto.ProfileResponse;
import com.kcalma.profile.dto.ProfileWithTargetsResponse;
import com.kcalma.security.SecurityConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
 * Fast web/security slice test: real {@link SecurityConfig} filter chain, mocked {@link JwtDecoder}
 * so no network call ever happens. Proves the three-way owner-allowlist contract:
 * no token -&gt; 401, valid token from a non-owner -&gt; 403, owner -&gt; 200.
 */
@WebMvcTest(ProfileController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173"
})
class ProfileControllerSecurityTest {

    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String NON_OWNER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenFromNonOwner_returns403() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(NON_OWNER_ID));

        mockMvc.perform(get("/api/profile").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_returns200() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(profileService.findByUserId(UUID.fromString(OWNER_ID)))
                .thenReturn(Optional.of(sampleResponse()));

        mockMvc.perform(get("/api/profile").header("Authorization", "Bearer " + TOKEN))
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

    private static ProfileWithTargetsResponse sampleResponse() {
        UUID id = UUID.fromString(OWNER_ID);
        OffsetDateTime now = OffsetDateTime.now();
        ProfileResponse profile = new ProfileResponse(
                id, Sex.FEMALE, LocalDate.of(1990, 1, 1), 165, new BigDecimal("60.00"),
                ActivityLevel.SEDENTARY, Goal.MAINTAIN, now, now);
        NutritionTargetsResponse targets =
                new NutritionTargetsResponse(1800, false, 96, 60, 180, 25, 45, 2000, 2100);
        return new ProfileWithTargetsResponse(profile, targets);
    }
}
