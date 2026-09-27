package com.kcalma.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link GeminiRateLimiter}'s sliding-window algorithm: the short burst window, the
 * daily window, and the Retry-After computation — all driven by a mutable test {@link Clock} so
 * "time passing" is deterministic instead of sleeping in the test.
 */
class GeminiRateLimiterTest {

    private static final String USER = "11111111-1111-1111-1111-111111111111";

    @Test
    void checkAndRecord_underBothLimits_neverThrows() {
        GeminiRateLimiter limiter = new GeminiRateLimiter(
                propertiesWith(5, 10, 100), new MutableClock(Instant.parse("2026-09-25T12:00:00Z")));

        for (int i = 0; i < 5; i++) {
            limiter.checkAndRecord(USER);
        }
    }

    @Test
    void checkAndRecord_windowRequestsReached_rejectsTheNextOneWithRetryAfter() {
        GeminiRateLimiter limiter = new GeminiRateLimiter(
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
        GeminiRateLimiter limiter = new GeminiRateLimiter(
                propertiesWith(1, 10, 100), new MutableClock(Instant.parse("2026-09-25T12:00:00Z")));

        limiter.checkAndRecord("user-a");

        assertThatThrownBy(() -> limiter.checkAndRecord("user-a")).isInstanceOf(RateLimitExceededException.class);
        limiter.checkAndRecord("user-b"); // a different user has its own quota -- must not throw
    }

    @Test
    void checkAndRecord_afterWindowElapses_allowsRequestsAgain() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-25T12:00:00Z"));
        GeminiRateLimiter limiter = new GeminiRateLimiter(propertiesWith(2, 10, 100), clock);

        limiter.checkAndRecord(USER);
        limiter.checkAndRecord(USER);
        assertThatThrownBy(() -> limiter.checkAndRecord(USER)).isInstanceOf(RateLimitExceededException.class);

        clock.advance(Duration.ofMinutes(10).plusSeconds(1));

        limiter.checkAndRecord(USER); // the 10-minute window has cleared -- must not throw
    }

    @Test
    void checkAndRecord_dailyRequestsReached_rejectsEvenWithTheShortWindowClear() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-25T12:00:00Z"));
        GeminiRateLimiter limiter = new GeminiRateLimiter(propertiesWith(100, 1, 3), clock);

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
        GeminiRateLimiter limiter = new GeminiRateLimiter(properties, new MutableClock(Instant.parse("2026-09-25T12:00:00Z")));

        for (int i = 0; i < 10; i++) {
            limiter.checkAndRecord(USER);
        }
    }

    private static RateLimitProperties propertiesWith(int windowRequests, int windowMinutes, int dailyRequests) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setWindowRequests(windowRequests);
        properties.setWindowMinutes(windowMinutes);
        properties.setDailyRequests(dailyRequests);
        return properties;
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
