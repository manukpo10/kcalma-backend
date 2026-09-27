package com.kcalma.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Backed by env vars GEMINI_GLOBAL_PER_MINUTE, GEMINI_GLOBAL_PER_DAY (see application.yml).
 * Governs {@link GeminiGlobalRateLimiter} — one shared quota for every user combined, on top of
 * {@link RateLimitProperties}'s per-user quota. Exists because sign-up is now open: a single
 * Supabase project's free-tier Gemini quota must survive many users, not just the one owner it
 * used to serve.
 *
 * <p>{@code perDay}'s default was lowered from 500 to 400: Google's real per-project quota (~500
 * requests/day on the free tier at the time of writing) is a hard ceiling shared with anything else
 * hitting the same Gemini project, so operators should set this to roughly 80% of the actual RPD
 * shown for the project in Google AI Studio, not the raw quota number itself -- leaving headroom
 * for quota drift, other callers on the same project, and admins' own unmetered usage.
 */
@ConfigurationProperties(prefix = "app.global-rate-limit")
@Validated
public class GlobalRateLimitProperties {

    private int perMinute = 10;

    private int perDay = 400;

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
