package com.kcalma.ratelimit;

/**
 * Thrown when the whole app's shared Gemini usage cap is exhausted — either {@link
 * GeminiGlobalRateLimiter} itself rejected the call, or Gemini's own API answered 429 (its quota,
 * not ours, is exhausted). Both cases are indistinguishable to the caller and get the same
 * friendly response (see {@code com.kcalma.web.GlobalExceptionHandler}): the app-wide budget is
 * gone for now, distinct from {@link RateLimitExceededException} (one user's own quota).
 */
public class GlobalAiCapExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public GlobalAiCapExceededException(long retryAfterSeconds) {
        super("Global Gemini usage cap exceeded, retry after " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
