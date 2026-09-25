package com.kcalma.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for Swagger UI. Only reachable when {@code SWAGGER_ENABLED=true} — see
 * application.yml ({@code springdoc.api-docs.enabled}/{@code springdoc.swagger-ui.enabled}) and
 * {@code SecurityConfig}, which gate springdoc's own registration and its exposure through the
 * security filter chain behind the same flag.
 *
 * <p>Declares a bearer JWT scheme so "Authorize" in Swagger UI accepts a Supabase access token
 * for the owner-only {@code /api/**} endpoints, which is effectively the app's whole documented
 * surface (the only other route, {@code /actuator/health}, isn't part of the OpenAPI docs).
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI kcalmaOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Kcalma API").version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
