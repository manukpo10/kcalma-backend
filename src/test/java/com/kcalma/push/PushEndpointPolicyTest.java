package com.kcalma.push;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Every accept/reject case the SSRF review for Sprint 3b called out explicitly. */
class PushEndpointPolicyTest {

    @Test
    void isAllowed_acceptsApple() {
        assertThat(PushEndpointPolicy.isAllowed("https://web.push.apple.com/subscribe/abc123")).isTrue();
    }

    @Test
    void isAllowed_acceptsFcm() {
        assertThat(PushEndpointPolicy.isAllowed("https://fcm.googleapis.com/fcm/send/abc123")).isTrue();
    }

    @Test
    void isAllowed_acceptsMozillaSubdomain() {
        assertThat(PushEndpointPolicy.isAllowed("https://updates.push.services.mozilla.com/wpush/v2/abc123")).isTrue();
    }

    @Test
    void isAllowed_acceptsWindowsNotificationSubdomain() {
        assertThat(PushEndpointPolicy.isAllowed("https://wns2-par02p.notify.windows.com/w/?token=abc123")).isTrue();
    }

    @Test
    void isAllowed_acceptsPortExplicitly443() {
        assertThat(PushEndpointPolicy.isAllowed("https://fcm.googleapis.com:443/fcm/send/abc123")).isTrue();
    }

    @Test
    void isAllowed_rejectsHttp() {
        assertThat(PushEndpointPolicy.isAllowed("http://fcm.googleapis.com/fcm/send/abc123")).isFalse();
    }

    @Test
    void isAllowed_rejectsAnIpLiteralHost() {
        assertThat(PushEndpointPolicy.isAllowed("https://169.254.169.254/latest/meta-data")).isFalse();
    }

    @Test
    void isAllowed_rejectsLocalhost() {
        assertThat(PushEndpointPolicy.isAllowed("https://localhost/x")).isFalse();
    }

    @Test
    void isAllowed_rejectsAnAllowedNameUsedAsASuffixOfADifferentDomain() {
        assertThat(PushEndpointPolicy.isAllowed("https://fcm.googleapis.com.evil.com/x")).isFalse();
    }

    @Test
    void isAllowed_rejectsAnAllowedNameSmuggledInAsUserinfo() {
        assertThat(PushEndpointPolicy.isAllowed("https://fcm.googleapis.com@evil.com/x")).isFalse();
    }

    @Test
    void isAllowed_rejectsANonStandardPort() {
        assertThat(PushEndpointPolicy.isAllowed("https://fcm.googleapis.com:8443/fcm/send/abc123")).isFalse();
    }

    @Test
    void isAllowed_rejectsAMalformedUri() {
        assertThat(PushEndpointPolicy.isAllowed("not a uri")).isFalse();
    }

    @Test
    void isAllowed_rejectsAnUnrelatedHttpsHost() {
        assertThat(PushEndpointPolicy.isAllowed("https://evil.example/collect")).isFalse();
    }
}
