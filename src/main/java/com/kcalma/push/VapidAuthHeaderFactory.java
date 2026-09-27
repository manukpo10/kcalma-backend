package com.kcalma.push;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.security.interfaces.ECPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Component;

/**
 * Builds the {@code Authorization: vapid t=<JWT>, k=<public key>} header RFC 8292 requires on
 * every Web Push request. Signing uses Nimbus JOSE+JWT's {@link ECDSASigner} — already a
 * compile-time dependency here via {@code spring-security-oauth2-jose} (see {@code
 * com.kcalma.security.SecurityConfig}'s {@code NimbusJwtDecoder}) — rather than hand-rolled ES256,
 * unlike {@link WebPushEncryptor}'s payload encryption: signing a JWT is exactly what a mature,
 * already-present JOSE library is for, so there's no reason to reimplement it.
 */
@Component
class VapidAuthHeaderFactory {

    /** RFC 8292 caps this at 24h; a comfortable margin under that, re-signed fresh on every call. */
    private static final Duration EXPIRY = Duration.ofHours(12);

    private final VapidKeyService keyService;

    VapidAuthHeaderFactory(VapidKeyService keyService) {
        this.keyService = keyService;
    }

    /** {@code endpoint} is the subscriber's full push-service URL; the JWT audience is just its origin. */
    String buildAuthorizationHeader(String endpoint) {
        String audience = originOf(endpoint);
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience(audience)
                .subject(keyService.getSubject())
                .expirationTime(Date.from(now.plus(EXPIRY)))
                .issueTime(Date.from(now))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType.JWT).build(), claims);
        try {
            jwt.sign(new ECDSASigner((ECPrivateKey) keyService.resolveKeyPair().getPrivate()));
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign the VAPID JWT.", e);
        }
        return "vapid t=" + jwt.serialize() + ", k=" + keyService.getPublicKeyBase64Url();
    }

    private String originOf(String endpoint) {
        URI uri = URI.create(endpoint);
        return uri.getScheme() + "://" + uri.getAuthority();
    }
}
