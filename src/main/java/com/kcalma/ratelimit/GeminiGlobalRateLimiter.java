package com.kcalma.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * In-memory cap shared by every user combined (not per-user — see {@link GeminiRateLimiter} for
 * that), on top of the three Gemini-backed endpoints' existing per-user quota. Two sliding windows
 * apply, either one can reject a call: a per-minute burst window ({@code
 * app.global-rate-limit.per-minute}, default 10) and a daily window ({@code
 * app.global-rate-limit.per-day}, default 400 — see {@link GlobalRateLimitProperties} for why
 * that's lower than Google's raw quota).
 *
 * <p>Same hand-rolled sliding-window-log shape as {@link GeminiRateLimiter}'s {@code
 * RequestWindow}, just a single instance-wide window instead of one per user — thread safety is a
 * plain {@code synchronized} method on this instance, and state is process-local (fine for this
 * app's single-instance Render deployment).
 */
@Component
@EnableConfigurationProperties(GlobalRateLimitProperties.class)
public class GeminiGlobalRateLimiter {

    private static final Duration DAILY_WINDOW = Duration.ofDays(1);
    private static final Duration MINUTE_WINDOW = Duration.ofMinutes(1);

    private final GlobalRateLimitProperties properties;
    private final Clock clock;
    private final Deque<Instant> timestamps = new ArrayDeque<>();

    public GeminiGlobalRateLimiter(GlobalRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Records one call against the shared budget if under both quotas.
     *
     * @throws GlobalAiCapExceededException if either the per-minute or the daily window is
     *     exhausted; carries how many seconds until the oldest call in the exceeded window ages out
     */
    public synchronized void checkAndRecord() {
        Instant now = clock.instant();
        Instant dailyCutoff = now.minus(DAILY_WINDOW);
        for (Iterator<Instant> it = timestamps.iterator(); it.hasNext(); ) {
            if (it.next().isBefore(dailyCutoff)) {
                it.remove();
            } else {
                break; // timestamps is insertion-ordered (ascending) -> nothing older remains
            }
        }

        Instant minuteCutoff = now.minus(MINUTE_WINDOW);
        Instant oldestInMinute = null;
        int countInMinute = 0;
        for (Instant timestamp : timestamps) {
            if (!timestamp.isBefore(minuteCutoff)) {
                countInMinute++;
                if (oldestInMinute == null) {
                    oldestInMinute = timestamp;
                }
            }
        }
        // oldestInMinute/peekFirst() can be null here when the configured cap is 0 (or negative) --
        // an operator-set "kill switch" with nothing recorded yet to measure from -- in which case
        // there is no real "oldest call" to wait out, so retry-after simply counts a full window
        // from now.
        if (countInMinute >= properties.getPerMinute()) {
            throw retryAfter(now, (oldestInMinute != null ? oldestInMinute : now).plus(MINUTE_WINDOW));
        }

        if (timestamps.size() >= properties.getPerDay()) {
            Instant oldestOverall = timestamps.isEmpty() ? now : timestamps.peekFirst();
            throw retryAfter(now, oldestOverall.plus(DAILY_WINDOW));
        }

        timestamps.addLast(now);
    }

    private static GlobalAiCapExceededException retryAfter(Instant now, Instant resetAt) {
        long seconds = Duration.between(now, resetAt).getSeconds() + 1;
        return new GlobalAiCapExceededException(Math.max(seconds, 1));
    }
}
