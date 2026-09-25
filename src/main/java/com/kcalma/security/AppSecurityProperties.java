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
 * <p>Bean Validation runs at startup binding time: an empty owner allowlist or a blank
 * Supabase URL fails application startup instead of silently opening/blocking every request.
 */
@ConfigurationProperties(prefix = "app.security")
@Validated
public class AppSecurityProperties {

    @NotBlank
    private String supabaseUrl;

    @NotEmpty
    private Set<String> ownerUserIds;

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
        this.ownerUserIds = ownerUserIds;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
