package com.kcalma.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.kcalma.security.AppSecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link GeminiRateLimiter}'s sliding-window algorithm: the short burst window, the
 * daily window, and the Retry-After computation — all driven by a mutable test {@link Clock} so
 * "time passing" is deterministic instead of sleeping in the test. Also covers the orchestration
 * this class now does on top of that: delegating to {@link GeminiGlobalRateLimiter} unless the
 * caller is an admin (see {@link AppSecurityProperties#isAdmin(String)}).
 */
class GeminiRateLimiterTest {

    private static final String USER = "11111111-1111-1111-1111-111111111111";

    @Test
    void checkAndRecord_underBothLimits_neverThrows() {
        GeminiRateLimiter limiter = limiterWith(
                propertiesWith(5, 10, 100), new MutableClock(Instant.parse("2026-09-25T12:00:00Z")));

        for (int i = 0; i < 5; i++) {
            limiter.checkAndRecord(USER);
        }
    }

    @Test
    void checkAndRecord_windowRequestsReached_rejectsTheNextOneWithRetryAfter() {
        GeminiRateLimiter limiter = limiterWith(
                propertiesWith(3, 10, 100), new MutableClock(Instant.parse("2026-09-25T12:00:00Z")));

        limiter.checkAndRecord(USER);
        limiter.checkAndRecord(USER);
        limiter.checkAndRecord(USER);

        assertThatThrownBy(() -> limiter.checkAndRecord(USER))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> assertThat(((RateLimitExceededException) ex).getRetryAfterSeconds())
                        .isGreaterThan(0)
                        .isLessThanOrEqualTo(Duration.ofMinutes(10).getSeconds() + 1));
    }

    @Test
    void checkAndRecord_differentUsers_haveIndependentQuotas() {
        GeminiRateLimiter limiter = limiterWith(
                propertiesWith(1, 10, 100), new MutableClock(Instant.parse("2026-09-25T12:00:00Z")));

        limiter.checkAndRecord("user-a");

        assertThatThrownBy(() -> limiter.checkAndRecord("user-a")).isInstanceOf(RateLimitExceededException.class);
        limiter.checkAndRecord("user-b"); // a different user has its own quota -- must not throw
    }

    @Test
    void checkAndRecord_afterWindowElapses_allowsRequestsAgain() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-25T12:00:00Z"));
        GeminiRateLimiter limiter = limiterWith(propertiesWith(2, 10, 100), clock);

        limiter.checkAndRecord(USER);
        limiter.checkAndRecord(USER);
        assertThatThrownBy(() -> limiter.checkAndRecord(USER)).isInstanceOf(RateLimitExceededException.class);

        clock.advance(Duration.ofMinutes(10).plusSeconds(1));

        limiter.checkAndRecord(USER); // the 10-minute window has cleared -- must not throw
    }

    @Test
    void checkAndRecord_dailyRequestsReached_rejectsEvenWithTheShortWindowClear() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-25T12:00:00Z"));
        GeminiRateLimiter limiter = limiterWith(propertiesWith(100, 1, 3), clock);

        for (int i = 0; i < 3; i++) {
            limiter.checkAndRecord(USER);
            clock.advance(Duration.ofMinutes(2)); // clears the 1-minute short window every time
        }

        assertThatThrownBy(() -> limiter.checkAndRecord(USER)).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void checkAndRecord_disabled_neverThrowsRegardlessOfVolume() {
        RateLimitProperties properties = propertiesWith(1, 10, 1);
        properties.setEnabled(false);
        GeminiRateLimiter limiter = limiterWith(properties, new MutableClock(Instant.parse("2026-09-25T12:00:00Z")));

        for (int i = 0; i < 10; i++) {
            limiter.checkAndRecord(USER);
        }
    }

    @Test
    void checkAndRecord_nonAdmin_delegatesToTheGlobalLimiter() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-25T12:00:00Z"));
        GeminiGlobalRateLimiter globalRateLimiter = mock(GeminiGlobalRateLimiter.class);
        GeminiRateLimiter limiter =
                new GeminiRateLimiter(propertiesWith(100, 10, 1000), globalRateLimiter, adminsOf(), clock);

        limiter.checkAndRecord(USER);

        verify(globalRateLimiter, times(1)).checkAndRecord();
    }

    @Test
    void checkAndRecord_admin_skipsTheGlobalLimiterEvenWhenItWouldReject() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-25T12:00:00Z"));
        GeminiGlobalRateLimiter exhaustedGlobalLimiter = new GeminiGlobalRateLimiter(globalPropertiesWith(0, 0), clock);
        GeminiRateLimiter limiter =
                new GeminiRateLimiter(propertiesWith(100, 10, 1000), exhaustedGlobalLimiter, adminsOf(USER), clock);

        // The global limiter above rejects EVERY call (0/0 quota) -- an admin must never reach it.
        limiter.checkAndRecord(USER);
        limiter.checkAndRecord(USER);
    }

    @Test
    void checkAndRecord_nonAdmin_globalCapExceeded_propagatesGlobalAiCapExceededException() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-25T12:00:00Z"));
        GeminiGlobalRateLimiter exhaustedGlobalLimiter = new GeminiGlobalRateLimiter(globalPropertiesWith(0, 0), clock);
        GeminiRateLimiter limiter =
                new GeminiRateLimiter(propertiesWith(100, 10, 1000), exhaustedGlobalLimiter, adminsOf(), clock);

        assertThatThrownBy(() -> limiter.checkAndRecord(USER)).isInstanceOf(GlobalAiCapExceededException.class);
    }

    @Test
    void checkAndRecord_admin_skipsOwnDailyCapButStillGetsRecordedForTheBurstWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-25T12:00:00Z"));
        GeminiGlobalRateLimiter globalRateLimiter = new GeminiGlobalRateLimiter(globalPropertiesWith(1_000_000, 1_000_000), clock);
        // dailyRequests = 2 -- a non-admin would be rejected on the 3rd call (see the sibling
        // non-admin daily test above); this admin sails through it.
        GeminiRateLimiter limiter = new GeminiRateLimiter(propertiesWith(100, 1, 2), globalRateLimiter, adminsOf(USER), clock);

        limiter.checkAndRecord(USER);
        limiter.checkAndRecord(USER);
        limiter.checkAndRecord(USER);
    }

    @Test
    void checkAndRecord_admin_stillRejectedByTheirOwnBurstWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-25T12:00:00Z"));
        GeminiGlobalRateLimiter globalRateLimiter = new GeminiGlobalRateLimiter(globalPropertiesWith(1_000_000, 1_000_000), clock);
        // windowRequests = 2, dailyRequests effectively unlimited -- only the burst window can reject here.
        GeminiRateLimiter limiter =
                new GeminiRateLimiter(propertiesWith(2, 10, 1_000_000), globalRateLimiter, adminsOf(USER), clock);

        limiter.checkAndRecord(USER);
        limiter.checkAndRecord(USER);

        assertThatThrownBy(() -> limiter.checkAndRecord(USER)).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void checkAndRecord_ownLimitExceeded_neverReachesTheGlobalLimiter() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-25T12:00:00Z"));
        GeminiGlobalRateLimiter globalRateLimiter = mock(GeminiGlobalRateLimiter.class);
        GeminiRateLimiter limiter = new GeminiRateLimiter(propertiesWith(1, 10, 100), globalRateLimiter, adminsOf(), clock);

        limiter.checkAndRecord(USER);
        assertThatThrownBy(() -> limiter.checkAndRecord(USER)).isInstanceOf(RateLimitExceededException.class);

        // Only the first, successful call reached the global limiter -- the second never got past
        // this user's own exhausted window.
        verify(globalRateLimiter, times(1)).checkAndRecord();
    }

    private static RateLimitProperties propertiesWith(int windowRequests, int windowMinutes, int dailyRequests) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setWindowRequests(windowRequests);
        properties.setWindowMinutes(windowMinutes);
        properties.setDailyRequests(dailyRequests);
        return properties;
    }

    private static GlobalRateLimitProperties globalPropertiesWith(int perMinute, int perDay) {
        GlobalRateLimitProperties properties = new GlobalRateLimitProperties();
        properties.setPerMinute(perMinute);
        properties.setPerDay(perDay);
        return properties;
    }

    private static AppSecurityProperties adminsOf(String... admins) {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setOwnerUserIds(Set.of(admins));
        return properties;
    }

    /** A generously-quota'd global limiter that never trips during these per-user-focused tests, plus no admins. */
    private static GeminiRateLimiter limiterWith(RateLimitProperties properties, Clock clock) {
        return new GeminiRateLimiter(properties, new GeminiGlobalRateLimiter(globalPropertiesWith(1_000_000, 1_000_000), clock), adminsOf(), clock);
    }

    /** A {@link Clock} whose {@link #instant()} can be advanced on demand — deterministic "time passing" for tests. */
    private static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("not needed by GeminiRateLimiter");
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
