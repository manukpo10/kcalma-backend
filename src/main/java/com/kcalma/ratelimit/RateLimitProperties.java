package com.kcalma.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Backed by env vars RATE_LIMIT_ENABLED, RATE_LIMIT_WINDOW_REQUESTS, RATE_LIMIT_WINDOW_MINUTES,
 * RATE_LIMIT_DAILY_REQUESTS (see application.yml). Governs {@link GeminiRateLimiter}, shared
 * across the three Gemini-backed endpoints (/api/food/analyze, /api/food/analyze-text,
 * /api/suggestions) and keyed per user (JWT {@code sub}).
 */
@ConfigurationProperties(prefix = "app.rate-limit")
@Validated
public class RateLimitProperties {

    private boolean enabled = true;

    private int windowRequests = 20;

    private int windowMinutes = 10;

    private int dailyRequests = 150;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getWindowRequests() {
        return windowRequests;
    }

    public void setWindowRequests(int windowRequests) {
        this.windowRequests = windowRequests;
    }

    public int getWindowMinutes() {
        return windowMinutes;
    }

    public void setWindowMinutes(int windowMinutes) {
        this.windowMinutes = windowMinutes;
    }

    public int getDailyRequests() {
        return dailyRequests;
    }

    public void setDailyRequests(int dailyRequests) {
        this.dailyRequests = dailyRequests;
    }
}
