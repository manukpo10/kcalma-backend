package com.kcalma.reminder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcalma.security.SecurityConfig;
import java.time.Instant;
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

@WebMvcTest(ReminderSettingsController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173"
})
class ReminderSettingsControllerSecurityTest {

    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String NON_OWNER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String TOKEN = "valid-token";

    private static final String VALID_PUT_BODY =
            """
            {
              "enabled": true,
              "meals": {
                "DESAYUNO": {"enabled": true, "time": "08:30"},
                "ALMUERZO": {"enabled": true, "time": "13:00"},
                "MERIENDA": {"enabled": true, "time": "17:00"},
                "CENA": {"enabled": true, "time": "21:00"}
              },
              "water": {"enabled": true, "everyHours": 2, "from": "10:00", "to": "20:00"},
              "weighIn": {"enabled": true, "days": ["MON", "THU"], "time": "08:00"}
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReminderSettingsService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/reminders")).andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenFromNonOwner_returns403() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(NON_OWNER_ID));

        mockMvc.perform(get("/api/reminders").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isForbidden());
    }

    @Test
    void owner_get_returns200WithTheDefaultsWhenNothingWasEverSaved() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(service.get(UUID.fromString(OWNER_ID))).thenReturn(ReminderSettingsData.defaults());

        mockMvc.perform(get("/api/reminders").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.meals.DESAYUNO.time").value("08:30"))
                .andExpect(jsonPath("$.meals.ALMUERZO.time").value("13:00"))
                .andExpect(jsonPath("$.water.everyHours").value(2))
                .andExpect(jsonPath("$.water.from").value("10:00"))
                .andExpect(jsonPath("$.weighIn.days[0]").value("MON"))
                .andExpect(jsonPath("$.weighIn.time").value("08:00"));
    }

    @Test
    void owner_put_validBody_returns200AndForwardsTheParsedSettings() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(service.update(eq(UUID.fromString(OWNER_ID)), any())).thenAnswer(invocation -> invocation.getArgument(1));

        mockMvc.perform(put("/api/reminders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PUT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.meals.CENA.time").value("21:00"));
    }

    @Test
    void put_invalidTimeFormat_returns400BeforeReachingTheService() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        String body = VALID_PUT_BODY.replace("\"time\": \"08:30\"", "\"time\": \"8:30am\"");

        mockMvc.perform(put("/api/reminders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_invalidWeekdayCode_returns400() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        String body = VALID_PUT_BODY.replace("\"MON\", \"THU\"", "\"MONDAY\", \"THU\"");

        mockMvc.perform(put("/api/reminders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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
}
