package com.kcalma.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link GeminiGlobalRateLimiter}'s sliding-window algorithm — the app-wide cap
 * shared by every user, mirroring {@code GeminiRateLimiterTest} but with a single instance-wide
 * window instead of one per user.
 */
class GeminiGlobalRateLimiterTest {

    @Test
    void checkAndRecord_underBothLimits_neverThrows() {
        GeminiGlobalRateLimiter limiter =
                new GeminiGlobalRateLimiter(propertiesWith(5, 100), new MutableClock(Instant.parse("2026-09-25T12:00:00Z")));

        for (int i = 0; i < 5; i++) {
            limiter.checkAndRecord();
        }
    }

    @Test
    void checkAndRecord_perMinuteReached_rejectsTheNextOneWithRetryAfter() {
        GeminiGlobalRateLimiter limiter =
                new GeminiGlobalRateLimiter(propertiesWith(3, 100), new MutableClock(Instant.parse("2026-09-25T12:00:00Z")));

        limiter.checkAndRecord();
        limiter.checkAndRecord();
        limiter.checkAndRecord();

        assertThatThrownBy(limiter::checkAndRecord)
                .isInstanceOf(GlobalAiCapExceededException.class)
                .satisfies(ex -> assertThat(((GlobalAiCapExceededException) ex).getRetryAfterSeconds())
                        .isGreaterThan(0)
                        .isLessThanOrEqualTo(Duration.ofMinutes(1).getSeconds() + 1));
    }

    @Test
    void checkAndRecord_everyCallCountsRegardlessOfCaller() {
        // Unlike GeminiRateLimiter, this cap has no concept of "user" -- two different callers
        // still share the exact same budget.
        GeminiGlobalRateLimiter limiter =
                new GeminiGlobalRateLimiter(propertiesWith(1, 100), new MutableClock(Instant.parse("2026-09-25T12:00:00Z")));

        limiter.checkAndRecord(); // "user A"

        assertThatThrownBy(limiter::checkAndRecord).isInstanceOf(GlobalAiCapExceededException.class); // "user B" -- same shared budget
    }

    @Test
    void checkAndRecord_afterAMinuteElapses_allowsRequestsAgain() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-25T12:00:00Z"));
        GeminiGlobalRateLimiter limiter = new GeminiGlobalRateLimiter(propertiesWith(2, 100), clock);

        limiter.checkAndRecord();
        limiter.checkAndRecord();
        assertThatThrownBy(limiter::checkAndRecord).isInstanceOf(GlobalAiCapExceededException.class);

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));

        limiter.checkAndRecord(); // the 1-minute window has cleared -- must not throw
    }

    @Test
    void checkAndRecord_perDayReached_rejectsEvenWithTheMinuteWindowClear() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-25T12:00:00Z"));
        GeminiGlobalRateLimiter limiter = new GeminiGlobalRateLimiter(propertiesWith(100, 3), clock);

        for (int i = 0; i < 3; i++) {
            limiter.checkAndRecord();
            clock.advance(Duration.ofMinutes(2)); // clears the 1-minute window every time
        }

        assertThatThrownBy(limiter::checkAndRecord)
                .isInstanceOf(GlobalAiCapExceededException.class)
                .satisfies(ex -> assertThat(((GlobalAiCapExceededException) ex).getRetryAfterSeconds())
                        .isGreaterThan(0)
                        .isLessThanOrEqualTo(Duration.ofDays(1).getSeconds() + 1));
    }

    /** Operator "kill switch": a 0 (or misconfigured negative) cap must cleanly reject the very first call, never NPE on an empty log. */
    @Test
    void checkAndRecord_zeroPerMinuteCap_rejectsTheFirstEverCallWithoutError() {
        GeminiGlobalRateLimiter limiter =
                new GeminiGlobalRateLimiter(propertiesWith(0, 0), new MutableClock(Instant.parse("2026-09-25T12:00:00Z")));

        assertThatThrownBy(limiter::checkAndRecord)
                .isInstanceOf(GlobalAiCapExceededException.class)
                .satisfies(ex -> assertThat(((GlobalAiCapExceededException) ex).getRetryAfterSeconds()).isGreaterThan(0));
    }

    private static GlobalRateLimitProperties propertiesWith(int perMinute, int perDay) {
        GlobalRateLimitProperties properties = new GlobalRateLimitProperties();
        properties.setPerMinute(perMinute);
        properties.setPerDay(perDay);
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
            throw new UnsupportedOperationException("not needed by GeminiGlobalRateLimiter");
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
