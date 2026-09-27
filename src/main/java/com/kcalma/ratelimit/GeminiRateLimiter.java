package com.kcalma.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * In-memory, per-user rate limiter shared across the three Gemini-backed endpoints
 * (/api/food/analyze, /api/food/analyze-text, /api/suggestions) — a call to any one of them counts
 * against the same quota. Two windows apply, either one can reject a request:
 *
 * <ul>
 *   <li>a short sliding window ({@code app.rate-limit.window-requests} per {@code
 *       app.rate-limit.window-minutes}, default 20 per 10 minutes) — protects against bursts;
 *   <li>a daily sliding window ({@code app.rate-limit.daily-requests}, default 150 per 24h) —
 *       protects the free-tier Gemini quota over a whole day.
 * </ul>
 *
 * <p>Implementation: a sliding-window log per user (a deque of request timestamps), pruned to the
 * daily window on every call. Deliberately a plain, hand-rolled structure rather than Bucket4j —
 * two independent windows sharing one counter is simple enough to keep dependency-free. Thread
 * safety is per-user (synchronized per {@link RequestWindow} instance), so users never contend
 * with each other; process-local only (not shared across instances), which is fine for this app's
 * single-instance Render deployment.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class GeminiRateLimiter {

    private static final Duration DAILY_WINDOW = Duration.ofDays(1);

    private final RateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, RequestWindow> windowsByUser = new ConcurrentHashMap<>();

    public GeminiRateLimiter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Records one request for {@code userId} if under both quotas.
     *
     * @throws RateLimitExceededException if either the short or the daily window is exhausted;
     *     carries how many seconds until the oldest request in the exceeded window ages out
     */
    public void checkAndRecord(String userId) {
        if (!properties.isEnabled()) {
            return;
        }
        RequestWindow window = windowsByUser.computeIfAbsent(userId, key -> new RequestWindow());
        window.checkAndRecord(clock.instant(), properties);
    }

    /** Per-user sliding-window request log. All access is synchronized on the instance itself. */
    private static final class RequestWindow {

        private final Deque<Instant> timestamps = new ArrayDeque<>();

        synchronized void checkAndRecord(Instant now, RateLimitProperties properties) {
            Instant dailyCutoff = now.minus(DAILY_WINDOW);
            for (Iterator<Instant> it = timestamps.iterator(); it.hasNext(); ) {
                if (it.next().isBefore(dailyCutoff)) {
                    it.remove();
                } else {
                    break; // timestamps is insertion-ordered (ascending) -> nothing older remains
                }
            }

            Duration windowDuration = Duration.ofMinutes(properties.getWindowMinutes());
            Instant windowCutoff = now.minus(windowDuration);

            Instant oldestInWindow = null;
            int countInWindow = 0;
            for (Instant timestamp : timestamps) {
                if (!timestamp.isBefore(windowCutoff)) {
                    countInWindow++;
                    if (oldestInWindow == null) {
                        oldestInWindow = timestamp;
                    }
                }
            }
            if (countInWindow >= properties.getWindowRequests()) {
                throw retryAfter(now, oldestInWindow.plus(windowDuration));
            }

            if (timestamps.size() >= properties.getDailyRequests()) {
                throw retryAfter(now, timestamps.peekFirst().plus(DAILY_WINDOW));
            }

            timestamps.addLast(now);
        }

        private static RateLimitExceededException retryAfter(Instant now, Instant resetAt) {
            long seconds = Duration.between(now, resetAt).getSeconds() + 1;
            return new RateLimitExceededException(Math.max(seconds, 1));
        }
    }
}
