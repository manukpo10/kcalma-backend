package com.kcalma.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.kcalma.food.FoodController;
import com.kcalma.food.FoodEntryService;
import com.kcalma.food.analysis.FoodAnalyzer;
import com.kcalma.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * With {@code SWAGGER_ENABLED} unset (defaults to {@code false}), the API docs must not be
 * publicly reachable: {@code springdoc.api-docs.enabled} follows the same flag (application.yml),
 * so springdoc's own controller isn't even registered — and {@link SecurityConfig} doesn't permit
 * the path either way, so an anonymous request never gets more than the default "authenticated"
 * rule (401) or a 404 once springdoc's controller is absent. See {@link SwaggerEnabledTest} for
 * the flag-on case.
 */
@WebMvcTest(FoodController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173"
})
class SwaggerDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FoodAnalyzer foodAnalyzer;

    @MockitoBean
    private FoodEntryService foodEntryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void apiDocs_notPubliclyReachable() throws Exception {
        int status = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 404);
    }
}
