package com.kcalma.food.analysis;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Backed by env vars GEMINI_API_KEY, GEMINI_MODEL, see application.yml. {@code apiKey} has no
 * default and fails application startup if missing, same convention as {@code AppSecurityProperties}.
 */
@ConfigurationProperties(prefix = "app.gemini")
@Validated
public class GeminiProperties {

    @NotBlank
    private String apiKey;

    @NotBlank
    private String model;

    @NotBlank
    private String baseUrl;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
