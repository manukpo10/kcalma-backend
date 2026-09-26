package com.kcalma.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcalma.food.analysis.AnalyzedDish;
import com.kcalma.food.analysis.AnalyzedFoodItem;
import com.kcalma.food.analysis.FoodAnalysisException;
import com.kcalma.food.analysis.FoodAnalysisResult;
import com.kcalma.food.analysis.FoodAnalyzer;
import com.kcalma.food.reference.FoodReferenceMatcher;
import com.kcalma.food.reference.ResolvedDish;
import com.kcalma.food.reference.ResolvedFoodItem;
import com.kcalma.security.SecurityConfig;
import java.time.Instant;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

/**
 * Web slice test for POST /api/food/analyze-text: blank/too-long description validation (400,
 * friendly Spanish message), the happy path, provider-failure translation (shared with the photo
 * endpoint via {@link com.kcalma.food.analysis.FoodAnalysisExceptionHandler}), and the same
 * owner-allowlist security contract as the rest of {@link FoodController}. Same {@code
 * @WebMvcTest} + {@code SecurityConfig} setup as {@link FoodControllerSecurityTest} and {@link
 * FoodControllerImageValidationTest}.
 */
@WebMvcTest(FoodController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173"
})
class FoodControllerTextAnalysisTest {

    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String NON_OWNER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FoodAnalyzer foodAnalyzer;

    @MockitoBean
    private FoodEntryService foodEntryService;

    @MockitoBean
    private FoodReferenceMatcher foodReferenceMatcher;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    /**
     * {@code @WebMvcTest} runs {@code MockMvc} without a real servlet container, so a
     * {@link ResponseStatusException}'s reason never reaches the response body the way it does in
     * production (there, it's forwarded to {@code BasicErrorController} and rendered thanks to
     * {@code server.error.include-message: always} in application.yml). The reason is asserted
     * directly off the resolved exception instead, which is what actually carries the Spanish
     * message end to end.
     */
    @Test
    void blankDescription_returns400AndNeverCallsAnalyzer() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));

        MvcResult result = mockMvc.perform(authenticatedPost("{\"description\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResolvedException()).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) result.getResolvedException()).getReason())
                .isEqualTo("Contanos qué comiste.");
        verifyNoInteractions(foodAnalyzer);
    }

    @Test
    void descriptionOver500Characters_returns400AndNeverCallsAnalyzer() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        String tooLong = "a".repeat(501);

        MvcResult result = mockMvc.perform(authenticatedPost("{\"description\": \"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResolvedException()).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) result.getResolvedException()).getReason())
                .isEqualTo("La descripción no puede superar los 500 caracteres.");
        verifyNoInteractions(foodAnalyzer);
    }

    @Test
    void validDescription_returns200WithAnalyzedDishes() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        AnalyzedFoodItem item =
                new AnalyzedFoodItem("empanada de carne", "empanada, beef, baked", 90, 250, 9, 12, 22, 1.5, 1, 380);
        AnalyzedDish dish = new AnalyzedDish("empanada de carne", 90, List.of(item));
        when(foodAnalyzer.analyzeDescription(anyString())).thenReturn(new FoodAnalysisResult(List.of(dish), null));
        ResolvedFoodItem resolvedItem = new ResolvedFoodItem(
                item.name(),
                item.canonicalNameEn(),
                item.grams(),
                new com.kcalma.food.NutritionMath.Per100(
                        item.kcalPer100(),
                        item.proteinPer100(),
                        item.fatPer100(),
                        item.carbsPer100(),
                        item.fiberPer100(),
                        item.sugarPer100(),
                        item.sodiumMgPer100()),
                FoodSource.ESTIMATED,
                null,
                null);
        when(foodReferenceMatcher.resolveDishes(eq(UUID.fromString(OWNER_ID)), eq(List.of(dish))))
                .thenReturn(List.of(ResolvedDish.aggregate(dish.name(), dish.grams(), List.of(resolvedItem))));

        mockMvc.perform(authenticatedPost("{\"description\": \"2 empanadas de carne\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dishes[0].name").value("empanada de carne"))
                .andExpect(jsonPath("$.dishes[0].grams").value(90.0))
                .andExpect(jsonPath("$.dishes[0].source").value("ESTIMATED"))
                .andExpect(jsonPath("$.dishes[0].ingredients[0].name").value("empanada de carne"))
                .andExpect(jsonPath("$.note").doesNotExist());
    }

    @Test
    void providerFailure_returns502WithFriendlyMessage() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(foodAnalyzer.analyzeDescription(anyString()))
                .thenThrow(new FoodAnalysisException("El servicio de análisis no está disponible en este momento."));

        mockMvc.perform(authenticatedPost("{\"description\": \"un plato de fideos con tuco\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("El servicio de análisis no está disponible en este momento."));
    }

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/food/analyze-text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"un mate cocido\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(foodAnalyzer);
    }

    @Test
    void validTokenFromNonOwner_returns403() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(NON_OWNER_ID));

        mockMvc.perform(authenticatedPost("{\"description\": \"un mate cocido\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(foodAnalyzer);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticatedPost(
            String json) {
        return post("/api/food/analyze-text")
                .header("Authorization", "Bearer " + TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
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
