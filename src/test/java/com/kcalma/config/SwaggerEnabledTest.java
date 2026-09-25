package com.kcalma.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcalma.food.FoodController;
import com.kcalma.food.FoodEntryService;
import com.kcalma.food.analysis.FoodAnalyzer;
import com.kcalma.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.MultipleOpenApiSupportConfiguration;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * With {@code SWAGGER_ENABLED=true}, both the docs JSON and the Swagger UI page must be reachable
 * without a token. See {@link SwaggerDisabledTest} for the (default) flag-off case.
 *
 * <p>{@code @WebMvcTest} only auto-configures Boot's own web-layer beans, not third-party
 * auto-configuration like springdoc's — so the configuration classes that actually register the
 * {@code /v3/api-docs} and {@code /swagger-ui/**} controllers are imported explicitly here,
 * mirroring springdoc-openapi-starter-{common,webmvc-api,webmvc-ui}'s own
 * {@code AutoConfiguration.imports} files.
 */
@WebMvcTest(FoodController.class)
@Import({
    SecurityConfig.class,
    OpenApiConfig.class,
    SpringDocConfiguration.class,
    SpringDocWebMvcConfiguration.class,
    MultipleOpenApiSupportConfiguration.class,
    SwaggerConfig.class,
})
@EnableConfigurationProperties({SpringDocConfigProperties.class, SwaggerUiConfigProperties.class, SwaggerUiOAuthProperties.class})
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173",
    "SWAGGER_ENABLED=true",
    "springdoc.api-docs.enabled=true",
    "springdoc.swagger-ui.enabled=true"
})
class SwaggerEnabledTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FoodAnalyzer foodAnalyzer;

    @MockitoBean
    private FoodEntryService foodEntryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void apiDocs_reachableWithoutToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
