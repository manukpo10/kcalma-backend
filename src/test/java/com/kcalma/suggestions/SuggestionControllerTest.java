package com.kcalma.suggestions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcalma.food.FoodSource;
import com.kcalma.food.MealType;
import com.kcalma.food.NutritionMath;
import com.kcalma.food.analysis.AnalyzedFoodItem;
import com.kcalma.food.analysis.FoodAnalysisException;
import com.kcalma.food.dto.AnalyzedDishResponse;
import com.kcalma.food.reference.ResolvedDish;
import com.kcalma.food.reference.ResolvedFoodItem;
import com.kcalma.security.SecurityConfig;
import com.kcalma.suggestions.dto.SuggestionOptionResponse;
import com.kcalma.suggestions.dto.SuggestionResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
 * Web slice test for POST /api/suggestions: input validation (400, friendly Spanish messages),
 * the happy path, "no profile yet" (404, same convention as {@code DayController}), and
 * provider-failure translation (shared with the food-analysis endpoints via {@link
 * com.kcalma.food.analysis.FoodAnalysisExceptionHandler}). Same {@code @WebMvcTest} +
 * {@code SecurityConfig} setup as {@link SuggestionControllerSecurityTest}.
 */
@WebMvcTest(SuggestionController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173"
})
class SuggestionControllerTest {

    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SuggestionService suggestionService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void missingDate_returns400() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));

        MvcResult result = mockMvc.perform(authenticatedPost("{\"mealType\": \"CENA\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(reasonOf(result)).isEqualTo("Falta la fecha.");
    }

    @Test
    void invalidDate_returns400() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));

        MvcResult result = mockMvc.perform(authenticatedPost("{\"date\": \"not-a-date\", \"mealType\": \"CENA\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(reasonOf(result)).isEqualTo("La fecha no es válida.");
    }

    @Test
    void missingMealType_returns400() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));

        MvcResult result = mockMvc.perform(authenticatedPost("{\"date\": \"2026-09-25\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(reasonOf(result)).isEqualTo("Falta el tipo de comida.");
    }

    @Test
    void invalidMealType_returns400() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));

        MvcResult result = mockMvc.perform(authenticatedPost("{\"date\": \"2026-09-25\", \"mealType\": \"POSTRE\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(reasonOf(result)).isEqualTo("El tipo de comida no es válido.");
    }

    @Test
    void preferencesOver300Characters_returns400() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        String tooLong = "a".repeat(301);

        MvcResult result = mockMvc.perform(
                        authenticatedPost("{\"date\": \"2026-09-25\", \"mealType\": \"CENA\", \"preferences\": \"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(reasonOf(result)).isEqualTo("Las preferencias no pueden superar los 300 caracteres.");
    }

    @Test
    void blankPreferences_isTreatedAsNoPreferences() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(suggestionService.suggest(UUID.fromString(OWNER_ID), LocalDate.parse("2026-09-25"), MealType.CENA, null))
                .thenReturn(Optional.of(new SuggestionResponse(NutritionMath.Totals.ZERO, List.of(), null)));

        mockMvc.perform(authenticatedPost("{\"date\": \"2026-09-25\", \"mealType\": \"CENA\", \"preferences\": \"   \"}"))
                .andExpect(status().isOk());
    }

    @Test
    void validRequest_returns200WithRemainingOptionsAndTotals() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        NutritionMath.Totals remaining = new NutritionMath.Totals(600, 40, 20, 70, 8, 15, 500);
        AnalyzedFoodItem item =
                new AnalyzedFoodItem("milanesa de carne", "beef, ground, cooked", 150, 200, 25, 8, 6, 1, 0.5, 420);
        ResolvedFoodItem resolvedItem = new ResolvedFoodItem(
                item.name(),
                item.canonicalNameEn(),
                item.grams(),
                new NutritionMath.Per100(
                        item.kcalPer100(), item.proteinPer100(), item.fatPer100(), item.carbsPer100(),
                        item.fiberPer100(), item.sugarPer100(), item.sodiumMgPer100()),
                FoodSource.ESTIMATED,
                null,
                null);
        ResolvedDish resolvedDish = ResolvedDish.aggregate("milanesa de carne", 150, List.of(resolvedItem));
        SuggestionOptionResponse option = new SuggestionOptionResponse(
                "Milanesa al horno con ensalada",
                "Milanesa de carne al horno con ensalada mixta",
                25,
                "Alta en proteína para llegar a tu objetivo del día",
                List.of(AnalyzedDishResponse.from(resolvedDish)),
                resolvedDish.totals());
        when(suggestionService.suggest(UUID.fromString(OWNER_ID), LocalDate.parse("2026-09-25"), MealType.CENA, "tengo pollo"))
                .thenReturn(Optional.of(new SuggestionResponse(remaining, List.of(option), null)));

        mockMvc.perform(authenticatedPost(
                        "{\"date\": \"2026-09-25\", \"mealType\": \"CENA\", \"preferences\": \"tengo pollo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining.kcal").value(600))
                .andExpect(jsonPath("$.options[0].title").value("Milanesa al horno con ensalada"))
                .andExpect(jsonPath("$.options[0].prepMinutes").value(25))
                .andExpect(jsonPath("$.options[0].dishes[0].name").value("milanesa de carne"))
                .andExpect(jsonPath("$.options[0].dishes[0].ingredients[0].name").value("milanesa de carne"))
                .andExpect(jsonPath("$.options[0].totals.kcal").value(resolvedDish.totals().kcal()))
                .andExpect(jsonPath("$.note").doesNotExist());
    }

    @Test
    void noProfileYet_returns404() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(suggestionService.suggest(UUID.fromString(OWNER_ID), LocalDate.parse("2026-09-25"), MealType.CENA, null))
                .thenReturn(Optional.empty());

        mockMvc.perform(authenticatedPost("{\"date\": \"2026-09-25\", \"mealType\": \"CENA\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void providerFailure_returns502WithFriendlyMessage() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        when(suggestionService.suggest(UUID.fromString(OWNER_ID), LocalDate.parse("2026-09-25"), MealType.CENA, null))
                .thenThrow(new FoodAnalysisException("No se pudieron generar sugerencias. Probá de nuevo."));

        mockMvc.perform(authenticatedPost("{\"date\": \"2026-09-25\", \"mealType\": \"CENA\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("No se pudieron generar sugerencias. Probá de nuevo."));
    }

    private static String reasonOf(MvcResult result) {
        return ((ResponseStatusException) result.getResolvedException()).getReason();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticatedPost(
            String json) {
        return post("/api/suggestions")
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
