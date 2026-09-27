package com.kcalma.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Backed by env vars RATE_LIMIT_ENABLED, GEMINI_USER_PER_10MIN, RATE_LIMIT_WINDOW_MINUTES,
 * GEMINI_USER_PER_DAY (see application.yml). Governs {@link GeminiRateLimiter}, shared across the
 * three Gemini-backed endpoints (/api/food/analyze, /api/food/analyze-text, /api/suggestions) and
 * keyed per user (JWT {@code sub}).
 *
 * <p>{@code dailyRequests}' default was lowered from a single-owner-era 150 to 50: with open
 * registration, a handful of users now share one Supabase project's free Gemini quota (see {@code
 * GlobalRateLimitProperties} for the app-wide cap this sits underneath). Admins ({@code
 * OWNER_USER_IDS}) skip {@code dailyRequests} entirely (see {@link GeminiRateLimiter}) but never
 * {@code windowRequests} — the short burst window still applies to everyone, admins included.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
@Validated
public class RateLimitProperties {

    private boolean enabled = true;

    private int windowRequests = 20;

    private int windowMinutes = 10;

    private int dailyRequests = 50;

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
