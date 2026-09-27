package com.kcalma.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Backed by env vars RATE_LIMIT_ENABLED, GEMINI_USER_PER_MINUTE, GEMINI_USER_PER_10MIN,
 * RATE_LIMIT_WINDOW_MINUTES, GEMINI_USER_PER_DAY (see application.yml). Governs {@link
 * GeminiRateLimiter}, shared across the three Gemini-backed endpoints (/api/food/analyze,
 * /api/food/analyze-text, /api/suggestions) and keyed per user (JWT {@code sub}).
 *
 * <p>{@code dailyRequests}' default was lowered from a single-owner-era 150 to 50: with open
 * registration, a handful of users now share one Supabase project's free Gemini quota (see {@code
 * GlobalRateLimitProperties} for the app-wide cap this sits underneath). {@code perMinuteRequests}
 * exists on top of that: {@code windowRequests} alone (20 per 10 minutes) lets one user burn
 * through the entire app-wide {@code GlobalRateLimitProperties#getPerMinute()} budget (10/min) by
 * itself in under a minute, starving every other user for the rest of that window -- 4/min keeps
 * one user's own ceiling comfortably below the shared global one. Admins ({@code OWNER_USER_IDS})
 * skip {@code dailyRequests} entirely (see {@link GeminiRateLimiter}) but never {@code
 * perMinuteRequests} or {@code windowRequests} — those two burst windows still apply to everyone,
 * admins included.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
@Validated
public class RateLimitProperties {

    private boolean enabled = true;

    private int perMinuteRequests = 4;

    private int windowRequests = 20;

    private int windowMinutes = 10;

    private int dailyRequests = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPerMinuteRequests() {
        return perMinuteRequests;
    }

    public void setPerMinuteRequests(int perMinuteRequests) {
        this.perMinuteRequests = perMinuteRequests;
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
