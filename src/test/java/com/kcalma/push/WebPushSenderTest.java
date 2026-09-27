package com.kcalma.push;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;

/**
 * {@link WebPushSender#send} itself always makes a real HTTP call, so it's exercised end to end
 * only by the smoke test (a real subscription is needed to hit a real push service). What IS unit
 * tested here is the pure decision this class makes from a response status — the part {@code
 * PushDispatchServiceTest} then relies on by mocking this whole class (see requirement: "404/410
 * cleanup (mock the sender)").
 */
class WebPushSenderTest {

    @Test
    void classify_404And410AreGone() {
        assertThat(WebPushSender.classify(HttpStatusCode.valueOf(404))).isEqualTo(WebPushSender.Result.GONE);
        assertThat(WebPushSender.classify(HttpStatusCode.valueOf(410))).isEqualTo(WebPushSender.Result.GONE);
    }

    @Test
    void classify_everyOtherErrorStatusIsFailedNotGone() {
        assertThat(WebPushSender.classify(HttpStatusCode.valueOf(400))).isEqualTo(WebPushSender.Result.FAILED);
        assertThat(WebPushSender.classify(HttpStatusCode.valueOf(413))).isEqualTo(WebPushSender.Result.FAILED);
        assertThat(WebPushSender.classify(HttpStatusCode.valueOf(429))).isEqualTo(WebPushSender.Result.FAILED);
        assertThat(WebPushSender.classify(HttpStatusCode.valueOf(500))).isEqualTo(WebPushSender.Result.FAILED);
        assertThat(WebPushSender.classify(HttpStatusCode.valueOf(503))).isEqualTo(WebPushSender.Result.FAILED);
    }
}
