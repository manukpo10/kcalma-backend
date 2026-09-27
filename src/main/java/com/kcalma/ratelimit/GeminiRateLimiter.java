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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-memory, per-user rate limiter shared across the three Gemini-backed endpoints
 * (/api/food/analyze, /api/food/analyze-text, /api/suggestions) — a call to any one of them counts
 * against the same quota. Three windows apply, any one can reject a request:
 *
 * <ul>
 *   <li>a per-minute sliding window ({@code app.rate-limit.per-minute-requests}, env {@code
 *       GEMINI_USER_PER_MINUTE}, default 4 per 60s) — keeps one user's own ceiling comfortably
 *       below {@link GlobalRateLimitProperties#getPerMinute()} (10/min shared by everyone), so a
 *       single account can no longer starve the whole app-wide cap by itself; applies to EVERY
 *       user, admins included;
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
 * three independent windows sharing one counter is simple enough to keep dependency-free. Thread
 * safety is per-user (synchronized per {@link RequestWindow} instance), so users never contend
 * with each other; process-local only (not shared across instances), which is fine for this app's
 * single-instance Render deployment.
 *
 * <p>Also the single call site every Gemini endpoint goes through for the app-WIDE cap: after the
 * per-user check passes (or is disabled), this delegates to {@link GeminiGlobalRateLimiter} unless
 * {@code userId} is an admin, who skips the global cap entirely (same admin check as the per-user
 * daily window above). Controllers call {@link #checkAndRecord(String)} once and never need to know
 * the global cap or the admin allowlist exist.
 *
 * <p>{@link #windowsByUser} gains one entry per distinct user ever seen and never shrinks on its
 * own (a user's own window is only pruned, not removed, lazily on their next call) — with open
 * registration that's an unbounded, ever-growing map over the process's lifetime. {@link
 * #evictInactiveUsers()} sweeps it on a schedule (see {@code SchedulingConfig}) and drops anyone
 * idle for the last 24h; it's also exposed package-private so tests can trigger it deterministically
 * against a fixed {@link Clock} instead of waiting on the real schedule.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class GeminiRateLimiter {

    private static final Duration MINUTE_WINDOW = Duration.ofMinutes(1);
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
     * own per-minute and short burst windows (always enforced, unless {@code
     * app.rate-limit.enabled} is false) and daily window (skipped for admins), then — again skipped
     * for admins — the app-wide shared cap.
     *
     * @throws RateLimitExceededException if {@code userId}'s own per-minute or short window is
     *     exhausted, or (non-admins only) their own daily window is; carries how many seconds until
     *     the oldest request in the exceeded window ages out
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

    /**
     * Drops every user whose {@link RequestWindow} has seen no activity in the last 24h (the same
     * window as {@link #DAILY_WINDOW} — reused rather than adding a second magic duration). Without
     * this, {@link #windowsByUser} would keep one entry per distinct user forever: with open
     * registration, that's an unbounded map over the app's lifetime, since a window is otherwise
     * only ever pruned (not removed) lazily on that same user's next call.
     *
     * <p>Runs hourly (see the {@code @Scheduled} annotation) rather than on every access, so normal
     * request handling never pays for the sweep — a 24h-idle threshold has no need to be checked
     * more often than that. {@code ConcurrentHashMap.values().removeIf(...)} is safe to run
     * concurrently with other threads' {@code computeIfAbsent}/reads (weakly-consistent iteration,
     * per {@link ConcurrentHashMap}'s own contract), so no extra locking is needed here. Package-private
     * (rather than the {@code @Scheduled} method being private) so tests can call it directly against
     * a fixed {@link Clock} instead of waiting on the real schedule.
     */
    @Scheduled(cron = "0 0 * * * *") // top of every hour
    void evictInactiveUsers() {
        Instant cutoff = clock.instant().minus(DAILY_WINDOW);
        windowsByUser.values().removeIf(window -> window.idleSince(cutoff));
    }

    /** Test seam: how many distinct users {@link #windowsByUser} currently holds a window for. */
    int trackedUserCount() {
        return windowsByUser.size();
    }

    /** Per-user sliding-window request log. All access is synchronized on the instance itself. */
    private static final class RequestWindow {

        private final Deque<Instant> timestamps = new ArrayDeque<>();

        /** Readable via {@link #idleSince}; {@code null} only for an instance that has never been recorded against yet. */
        private Instant lastAccess;

        /**
         * @param skipDailyCap true for admins: the daily window is still pruned/maintained (so the
         *     burst-window count above stays accurate) but never itself rejects the call.
         */
        synchronized void checkAndRecord(Instant now, RateLimitProperties properties, boolean skipDailyCap) {
            lastAccess = now;

            Instant dailyCutoff = now.minus(DAILY_WINDOW);
            for (Iterator<Instant> it = timestamps.iterator(); it.hasNext(); ) {
                if (it.next().isBefore(dailyCutoff)) {
                    it.remove();
                } else {
                    break; // timestamps is insertion-ordered (ascending) -> nothing older remains
                }
            }

            Instant minuteCutoff = now.minus(MINUTE_WINDOW);
            Duration windowDuration = Duration.ofMinutes(properties.getWindowMinutes());
            Instant windowCutoff = now.minus(windowDuration);

            Instant oldestInMinute = null;
            int countInMinute = 0;
            Instant oldestInWindow = null;
            int countInWindow = 0;
            for (Instant timestamp : timestamps) {
                if (!timestamp.isBefore(minuteCutoff)) {
                    countInMinute++;
                    if (oldestInMinute == null) {
                        oldestInMinute = timestamp;
                    }
                }
                if (!timestamp.isBefore(windowCutoff)) {
                    countInWindow++;
                    if (oldestInWindow == null) {
                        oldestInWindow = timestamp;
                    }
                }
            }
            // The per-minute and short burst windows apply to every user, admins included -- never
            // skipped. Checked tightest window first, though in practice only one of the two ever
            // actually trips for a given call pattern.
            if (countInMinute >= properties.getPerMinuteRequests()) {
                throw retryAfter(now, oldestInMinute.plus(MINUTE_WINDOW));
            }
            if (countInWindow >= properties.getWindowRequests()) {
                throw retryAfter(now, oldestInWindow.plus(windowDuration));
            }

            if (!skipDailyCap && timestamps.size() >= properties.getDailyRequests()) {
                throw retryAfter(now, timestamps.peekFirst().plus(DAILY_WINDOW));
            }

            timestamps.addLast(now);
        }

        /**
         * @return true if this window's last recorded activity is at or before {@code cutoff} —
         *     including a brand new window that hasn't recorded anything yet ({@code lastAccess ==
         *     null}), which only happens for the instant between {@link
         *     ConcurrentHashMap#computeIfAbsent} inserting it and {@link #checkAndRecord} first
         *     running on it; evicting it in that narrow race is harmless; the next call for that
         *     user just creates a fresh window.
         */
        synchronized boolean idleSince(Instant cutoff) {
            return lastAccess == null || !lastAccess.isAfter(cutoff);
        }

        private static RateLimitExceededException retryAfter(Instant now, Instant resetAt) {
            long seconds = Duration.between(now, resetAt).getSeconds() + 1;
            return new RateLimitExceededException(Math.max(seconds, 1));
        }
    }
}
