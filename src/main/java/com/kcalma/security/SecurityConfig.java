package com.kcalma.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless resource-server security: Supabase-issued ES256 JWTs only. Open registration — any
 * authenticated Supabase user from this project is let in, since Supabase sign-ups stay behind
 * the frontend's own gate. The admission check is an authorization decision (not a token
 * validator) so a missing/invalid/expired token gets 401, while a structurally valid JWT for a
 * Supabase anonymous sign-in ({@code is_anonymous: true}) gets 403 — anonymous guests never get a
 * user account worth persisting data against. {@code OWNER_USER_IDS} (see {@link
 * AppSecurityProperties}) no longer gates access at all; it now marks admins, used only to skip
 * the global Gemini usage cap.
 */
@Configuration
@EnableConfigurationProperties(AppSecurityProperties.class)
public class SecurityConfig {

    private static final String AUDIENCE = "authenticated";

    private final AppSecurityProperties properties;
    private final boolean swaggerEnabled;
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    public SecurityConfig(AppSecurityProperties properties, @Value("${SWAGGER_ENABLED:false}") boolean swaggerEnabled) {
        this.properties = properties;
        this.swaggerEnabled = swaggerEnabled;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    // springdoc's own controllers aren't even registered when SWAGGER_ENABLED is
                    // false (see application.yml), so this only ever widens access when the docs
                    // truly exist; leaving the matcher out otherwise means these paths fall back
                    // to the default "authenticated" rule below (401 with no token) on top of the
                    // 404 springdoc itself would already give.
                    if (swaggerEnabled) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }
                    auth.requestMatchers("/api/**").access(authenticatedNonAnonymous());
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder())));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        String jwkSetUri = properties.getSupabaseUrl() + "/auth/v1/.well-known/jwks.json";
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();

        String issuer = properties.getSupabaseUrl() + "/auth/v1";
        OAuth2TokenValidator<Jwt> withIssuerAndTimestamps = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> withAudience = new JwtClaimValidator<List<String>>(
                "aud", aud -> aud != null && aud.contains(AUDIENCE));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuerAndTimestamps, withAudience));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Missing/invalid/expired token -&gt; Spring Security never authenticates it -&gt; {@code
     * trustResolver.isAnonymous} sees the framework's own anonymous placeholder authentication -&gt;
     * entry point -&gt; 401. A structurally valid JWT for a Supabase ANONYMOUS sign-in (distinct
     * concept: {@code is_anonymous: true} in the token's own claims, not Spring Security's
     * anonymous-authentication placeholder) -&gt; authenticated but not granted -&gt; access-denied
     * handler -&gt; 403. Any other authenticated Supabase user -&gt; granted -&gt; 2xx.
     */
    private AuthorizationManager<RequestAuthorizationContext> authenticatedNonAnonymous() {
        return (authenticationSupplier, context) -> {
            Authentication authentication = authenticationSupplier.get();
            boolean granted = !trustResolver.isAnonymous(authentication)
                    && authentication instanceof JwtAuthenticationToken jwtAuth
                    && authentication.isAuthenticated()
                    && !isSupabaseAnonymousUser(jwtAuth.getToken());
            return new AuthorizationDecision(granted);
        };
    }

    /**
     * Supabase stamps every anonymous sign-in's JWT with a top-level {@code is_anonymous: true}
     * claim (the same one RLS policies check via {@code auth.jwt() ->> 'is_anonymous'}); a regular
     * user's token either omits the claim or carries it as {@code false}.
     */
    private static boolean isSupabaseAnonymousUser(Jwt token) {
        return Boolean.TRUE.equals(token.getClaimAsBoolean("is_anonymous"));
    }
}
