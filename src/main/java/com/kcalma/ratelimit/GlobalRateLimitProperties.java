package com.kcalma.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Backed by env vars GEMINI_GLOBAL_PER_MINUTE, GEMINI_GLOBAL_PER_DAY (see application.yml).
 * Governs {@link GeminiGlobalRateLimiter} — one shared quota for every user combined, on top of
 * {@link RateLimitProperties}'s per-user quota. Exists because sign-up is now open: a single
 * Supabase project's free-tier Gemini quota must survive many users, not just the one owner it
 * used to serve.
 */
@ConfigurationProperties(prefix = "app.global-rate-limit")
@Validated
public class GlobalRateLimitProperties {

    private int perMinute = 10;

    private int perDay = 500;

    public int getPerMinute() {
        return perMinute;
    }

    public void setPerMinute(int perMinute) {
        this.perMinute = perMinute;
    }

    public int getPerDay() {
        return perDay;
    }

    public void setPerDay(int perDay) {
        this.perDay = perDay;
    }
}
