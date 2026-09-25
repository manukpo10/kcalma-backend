package com.kcalma.weight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcalma.security.SecurityConfig;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Web slice test for the weight endpoints' 400 validation paths — weight out of the 30-300 kg
 * range, a future date, and an inverted from/to range — all with Spanish messages, none of them
 * ever reaching {@link WeightEntryService}. Same {@code @WebMvcTest} + {@code SecurityConfig}
 * setup as {@code FoodControllerImageValidationTest}.
 *
 * <p>Bean-Validation failures ({@code @Valid} body) resolve to {@link MethodArgumentNotValidException}
 * — its field message is asserted directly off the resolved exception's binding result, since
 * MockMvc's slice-test environment doesn't render an HTTP error body to assert against (no
 * {@code BasicErrorController} in play). Manually-thrown {@link ResponseStatusException}s (date
 * checks) carry their Spanish reason as the response's error message instead.
 */
@WebMvcTest(WeightController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173"
})
class WeightControllerValidationTest {

    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String TOKEN = "valid-token";
    private static final String TODAY = "2026-09-25";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WeightEntryService weightEntryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void upsert_weightBelowMinimum_returns400WithSpanishFieldMessage() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));

        MvcResult result = mockMvc.perform(put("/api/weights/" + TODAY)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 29.9}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(weightFieldMessage(result)).isEqualTo("El peso debe ser al menos 30 kg.");
        verifyNoInteractions(weightEntryService);
    }

    @Test
    void upsert_weightAboveMaximum_returns400WithSpanishFieldMessage() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));

        MvcResult result = mockMvc.perform(put("/api/weights/" + TODAY)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 300.1}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(weightFieldMessage(result)).isEqualTo("El peso no puede superar los 300 kg.");
        verifyNoInteractions(weightEntryService);
    }

    @Test
    void upsert_missingWeight_returns400() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));

        mockMvc.perform(put("/api/weights/" + TODAY)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(weightEntryService);
    }

    @Test
    void upsert_futureDate_returns400WithSpanishMessage() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        String futureDate = LocalDate.now().plusDays(1).toString();

        MvcResult result = mockMvc.perform(put("/api/weights/" + futureDate)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 70.0}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getErrorMessage()).isEqualTo("La fecha no puede ser futura.");
        verifyNoInteractions(weightEntryService);
    }

    @Test
    void list_fromAfterTo_returns400WithSpanishMessage() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));

        MvcResult result = mockMvc.perform(get("/api/weights")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("from", "2026-09-25")
                        .param("to", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getErrorMessage()).isEqualTo("La fecha de inicio no puede ser posterior a la de fin.");
        verifyNoInteractions(weightEntryService);
    }

    private static String weightFieldMessage(MvcResult result) {
        Exception resolved = result.getResolvedException();
        assertThat(resolved).isInstanceOf(MethodArgumentNotValidException.class);
        MethodArgumentNotValidException manv = (MethodArgumentNotValidException) resolved;
        var fieldError = manv.getBindingResult().getFieldError("weightKg");
        assertThat(fieldError).isNotNull();
        return fieldError.getDefaultMessage();
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
