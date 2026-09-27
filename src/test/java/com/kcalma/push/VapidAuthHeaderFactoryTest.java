package com.kcalma.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VapidAuthHeaderFactoryTest {

    private final VapidProperties properties = new VapidProperties();
    private KeyPair keyPair;
    private VapidKeyService keyService;
    private VapidAuthHeaderFactory factory;

    @BeforeEach
    void setUp() {
        keyPair = EcKeys.generateKeyPair();
        properties.setSubject("https://kcalma-frontend.vercel.app");
        properties.setPublicKey(EcKeys.toBase64Url(EcKeys.encodePublicKey((ECPublicKey) keyPair.getPublic())));
        properties.setPrivateKey(EcKeys.toBase64Url(EcKeys.encodePrivateKey((ECPrivateKey) keyPair.getPrivate())));
        keyService = new VapidKeyService(mock(PushConfigRepository.class), properties);
        factory = new VapidAuthHeaderFactory(keyService);
    }

    @Test
    void buildAuthorizationHeader_isASignedJwtScopedToTheEndpointsOriginWithTheConfiguredSubject() throws Exception {
        String header = factory.buildAuthorizationHeader("https://fcm.googleapis.com/fcm/send/abc123");

        assertThat(header).startsWith("vapid t=").contains(", k=" + keyService.getPublicKeyBase64Url());

        String jwtCompact = header.substring("vapid t=".length(), header.indexOf(", k="));
        SignedJWT jwt = SignedJWT.parse(jwtCompact);
        assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic())))
                .as("the JWT must verify against the SAME public key returned in k=")
                .isTrue();

        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(claims.getAudience()).containsExactly("https://fcm.googleapis.com");
        assertThat(claims.getSubject()).isEqualTo("https://kcalma-frontend.vercel.app");
        Instant expiry = claims.getExpirationTime().toInstant();
        assertThat(expiry).isAfter(Instant.now());
        assertThat(Duration.between(Instant.now(), expiry)).isLessThanOrEqualTo(Duration.ofHours(24)); // RFC 8292 hard cap
    }

    @Test
    void buildAuthorizationHeader_signatureFailsToVerifyAgainstADifferentPublicKey() throws Exception {
        String header = factory.buildAuthorizationHeader("https://updates.push.services.mozilla.com/wpush/v2/abc");
        String jwtCompact = header.substring("vapid t=".length(), header.indexOf(", k="));
        SignedJWT jwt = SignedJWT.parse(jwtCompact);

        KeyPair unrelated = EcKeys.generateKeyPair();
        assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) unrelated.getPublic()))).isFalse();
    }
}
