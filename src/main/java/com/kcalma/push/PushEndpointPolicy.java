package com.kcalma.push;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * The SSRF allowlist for {@code POST /api/push/subscriptions}: a browser's own {@code
 * PushSubscription.endpoint} only ever points at one of a handful of push services (each vendor
 * runs its own, and the URL comes straight from the browser API, never from anything a user
 * types), so anything else stored there is almost certainly an attempt to make this server issue
 * an outbound POST somewhere it shouldn't -- an internal service, a cloud metadata endpoint, or an
 * attacker's own listener. {@link PushController#subscribe} calls {@link #isAllowed} before a new
 * subscription is ever persisted; {@link PushDispatchService#dispatchToUser} calls it again as
 * defense in depth right before every send, in case a stored row predates this check.
 */
final class PushEndpointPolicy {

    private static final Set<String> ALLOWED_HOSTS =
            Set.of("fcm.googleapis.com", "push.services.mozilla.com", "push.apple.com", "notify.windows.com");

    private PushEndpointPolicy() {
    }

    /**
     * {@code true} only for an {@code https} URL with no userinfo, no port (or explicitly 443),
     * and a host that either exactly matches one of {@link #ALLOWED_HOSTS} or is a subdomain of
     * one, matched on a dot boundary so {@code fcm.googleapis.com.evil.com} is rejected while
     * {@code updates.push.services.mozilla.com} is accepted.
     */
    static boolean isAllowed(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            return false;
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        if (uri.getRawUserInfo() != null) {
            return false; // e.g. https://fcm.googleapis.com@evil.com/x -- host below would be evil.com anyway
        }
        int port = uri.getPort();
        if (port != -1 && port != 443) {
            return false;
        }

        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        for (String allowedHost : ALLOWED_HOSTS) {
            if (lowerHost.equals(allowedHost) || lowerHost.endsWith("." + allowedHost)) {
                return true;
            }
        }
        return false;
    }
}
