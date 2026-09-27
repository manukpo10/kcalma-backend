package com.kcalma.push;

import java.net.URI;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends one already-JSON-serialized payload to one subscription: encrypts it (RFC 8291, see
 * {@link WebPushEncryptor}), attaches the VAPID {@code Authorization} header (RFC 8292, see
 * {@link VapidAuthHeaderFactory}), and POSTs it to the subscription's push-service endpoint.
 * Never logs the payload, the subscription's keys, or the VAPID private key — failures log only
 * the status code and the endpoint's host.
 */
@Component
class WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

    /** web push services drop a message after this long anyway; matches it so we don't hold a connection open pointlessly. */
    private static final String TTL_SECONDS = String.valueOf(Duration.ofHours(24).toSeconds());

    enum Result {
        SENT,
        /** 404/410: the push service says this subscription no longer exists — caller should delete it. */
        GONE,
        FAILED
    }

    private final RestClient restClient;
    private final WebPushEncryptor encryptor;
    private final VapidAuthHeaderFactory authHeaderFactory;

    WebPushSender(RestClient.Builder restClientBuilder, WebPushEncryptor encryptor, VapidAuthHeaderFactory authHeaderFactory) {
        this.encryptor = encryptor;
        this.authHeaderFactory = authHeaderFactory;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    Result send(PushSubscription subscription, byte[] payloadJson) {
        try {
            ECPublicKey subscriberKey = EcKeys.decodePublicKey(EcKeys.fromBase64Url(subscription.getP256dh()));
            byte[] authSecret = EcKeys.fromBase64Url(subscription.getAuth());
            byte[] body = encryptor.encrypt(subscriberKey, authSecret, payloadJson);
            String authorization = authHeaderFactory.buildAuthorizationHeader(subscription.getEndpoint());

            restClient
                    .post()
                    .uri(URI.create(subscription.getEndpoint()))
                    .header("Authorization", authorization)
                    .header("Content-Encoding", "aes128gcm")
                    .header("TTL", TTL_SECONDS)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return Result.SENT;
        } catch (HttpStatusCodeException e) {
            Result result = classify(e.getStatusCode());
            if (result == Result.FAILED) {
                log.warn("Web Push send failed with status {} for endpoint host {}", e.getStatusCode().value(), hostOf(subscription));
            }
            return result;
        } catch (ResourceAccessException e) {
            log.warn("Web Push send timed out or could not connect to endpoint host {}", hostOf(subscription));
            return Result.FAILED;
        } catch (RestClientException | IllegalArgumentException | IllegalStateException e) {
            log.warn("Unexpected Web Push send failure for endpoint host {}", hostOf(subscription), e);
            return Result.FAILED;
        }
    }

    /** 404 Not Found / 410 Gone both mean "this subscription is dead" per the Web Push protocol (RFC 8030 §7). */
    static Result classify(HttpStatusCode status) {
        int value = status.value();
        return (value == 404 || value == 410) ? Result.GONE : Result.FAILED;
    }

    private String hostOf(PushSubscription subscription) {
        try {
            return URI.create(subscription.getEndpoint()).getHost();
        } catch (IllegalArgumentException e) {
            return "unknown";
        }
    }
}
