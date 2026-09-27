package com.kcalma.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Backed by env vars SUPABASE_URL, OWNER_USER_IDS, ALLOWED_ORIGINS (see application.yml).
 * {@code ownerUserIds} and {@code allowedOrigins} bind from comma-separated env values.
 *
 * <p>Kcalma is multi-user with open registration: every valid, non-anonymous Supabase JWT is let
 * in by {@link SecurityConfig} regardless of whether its subject appears here. {@code
 * OWNER_USER_IDS} keeps its original env var name for backward compatibility, but its meaning is
 * now "admins" — see {@link #isAdmin(String)} — and it is optional: an app with no admins
 * configured still runs, it just has no one who skips the global Gemini usage cap (see {@code
 * com.kcalma.ratelimit.GeminiGlobalRateLimiter}).
 *
 * <p>Bean Validation runs at startup binding time: a blank Supabase URL fails application startup
 * instead of silently misconfiguring the JWT decoder. The admin allowlist has no such requirement
 * anymore — an empty set is a normal, supported configuration.
 */
@ConfigurationProperties(prefix = "app.security")
@Validated
public class AppSecurityProperties {

    @NotBlank
    private String supabaseUrl;

    /** Historically "owners"; now the admin allowlist. Never null — defaults to empty when unset. */
    private Set<String> ownerUserIds = Set.of();

    @NotEmpty
    private List<String> allowedOrigins;

    public String getSupabaseUrl() {
        return supabaseUrl;
    }

    public void setSupabaseUrl(String supabaseUrl) {
        this.supabaseUrl = supabaseUrl;
    }

    public Set<String> getOwnerUserIds() {
        return ownerUserIds;
    }

    public void setOwnerUserIds(Set<String> ownerUserIds) {
        this.ownerUserIds = ownerUserIds != null ? ownerUserIds : Set.of();
    }

    /** True when {@code userId} (a JWT {@code sub}) is in the admin allowlist ({@code OWNER_USER_IDS}). */
    public boolean isAdmin(String userId) {
        return userId != null && ownerUserIds.contains(userId);
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
