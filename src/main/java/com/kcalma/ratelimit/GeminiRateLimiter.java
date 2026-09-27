package com.kcalma.ratelimit;

import com.kcalma.security.AppSecurityProperties;
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
 *   <li>a short sliding window ({@code app.rate-limit.window-requests}, env {@code
 *       GEMINI_USER_PER_10MIN}, per {@code app.rate-limit.window-minutes}, default 20 per 10
 *       minutes) — protects against bursts, and applies to EVERY user, admins included;
 *   <li>a daily sliding window ({@code app.rate-limit.daily-requests}, env {@code
 *       GEMINI_USER_PER_DAY}, default 50 per 24h) — protects one project's free-tier Gemini quota
 *       being shared by every user now that sign-up is open; admins skip this one (see {@link
 *       AppSecurityProperties#isAdmin(String)}).
 * </ul>
 *
 * <p>Implementation: a sliding-window log per user (a deque of request timestamps), pruned to the
 * daily window on every call. Deliberately a plain, hand-rolled structure rather than Bucket4j —
 * two independent windows sharing one counter is simple enough to keep dependency-free. Thread
 * safety is per-user (synchronized per {@link RequestWindow} instance), so users never contend
 * with each other; process-local only (not shared across instances), which is fine for this app's
 * single-instance Render deployment.
 *
 * <p>Also the single call site every Gemini endpoint goes through for the app-WIDE cap: after the
 * per-user check passes (or is disabled), this delegates to {@link GeminiGlobalRateLimiter} unless
 * {@code userId} is an admin, who skips the global cap entirely (same admin check as the per-user
 * daily window above). Controllers call {@link #checkAndRecord(String)} once and never need to know
 * the global cap or the admin allowlist exist.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class GeminiRateLimiter {

    private static final Duration DAILY_WINDOW = Duration.ofDays(1);

    private final RateLimitProperties properties;
    private final GeminiGlobalRateLimiter globalRateLimiter;
    private final AppSecurityProperties securityProperties;
    private final Clock clock;
    private final ConcurrentHashMap<String, RequestWindow> windowsByUser = new ConcurrentHashMap<>();

    public GeminiRateLimiter(
            RateLimitProperties properties,
            GeminiGlobalRateLimiter globalRateLimiter,
            AppSecurityProperties securityProperties,
            Clock clock) {
        this.properties = properties;
        this.globalRateLimiter = globalRateLimiter;
        this.securityProperties = securityProperties;
        this.clock = clock;
    }

    /**
     * Records one request for {@code userId} if under every applicable quota: first this user's
     * own short burst window (always enforced, unless {@code app.rate-limit.enabled} is false) and
     * daily window (skipped for admins), then — again skipped for admins — the app-wide shared cap.
     *
     * @throws RateLimitExceededException if {@code userId}'s own short window is exhausted, or
     *     (non-admins only) their own daily window is; carries how many seconds until the oldest
     *     request in the exceeded window ages out
     * @throws GlobalAiCapExceededException if the app-wide shared cap is exhausted and {@code
     *     userId} is not an admin
     */
    public void checkAndRecord(String userId) {
        boolean admin = securityProperties.isAdmin(userId);
        if (properties.isEnabled()) {
            RequestWindow window = windowsByUser.computeIfAbsent(userId, key -> new RequestWindow());
            window.checkAndRecord(clock.instant(), properties, admin);
        }
        if (!admin) {
            globalRateLimiter.checkAndRecord();
        }
    }

    /** Per-user sliding-window request log. All access is synchronized on the instance itself. */
    private static final class RequestWindow {

        private final Deque<Instant> timestamps = new ArrayDeque<>();

        /**
         * @param skipDailyCap true for admins: the daily window is still pruned/maintained (so the
         *     burst-window count above stays accurate) but never itself rejects the call.
         */
        synchronized void checkAndRecord(Instant now, RateLimitProperties properties, boolean skipDailyCap) {
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
            // The short burst window applies to every user, admins included -- never skipped.
            if (countInWindow >= properties.getWindowRequests()) {
                throw retryAfter(now, oldestInWindow.plus(windowDuration));
            }

            if (!skipDailyCap && timestamps.size() >= properties.getDailyRequests()) {
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
