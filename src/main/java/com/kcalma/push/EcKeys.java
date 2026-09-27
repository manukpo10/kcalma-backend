package com.kcalma.push;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidParameterSpecException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

/**
 * EC P-256 (secp256r1) key generation and the raw wire encodings Web Push actually uses on the
 * network — never PKCS8/X509 DER. Everything here uses only the JDK's own {@code java.security}
 * providers (SunEC): no BouncyCastle, no third-party crypto library (see {@link WebPushEncryptor}'s
 * javadoc for why). Backs both VAPID key storage ({@code app.push_config}) and RFC 8291 message
 * encryption (parsing a subscription's {@code p256dh} and building the ephemeral sender key).
 *
 * <ul>
 *   <li>Public key wire format: the SEC1 "uncompressed point" — {@code 0x04 || X(32) || Y(32)}, 65
 *       bytes total. This is exactly what a browser's {@code PushSubscription.getKey('p256dh')}
 *       returns and what {@code applicationServerKey} expects, so it's also how {@code
 *       app.push_config.public_key} is stored (base64url of these same 65 bytes).
 *   <li>Private key wire format: the raw 32-byte big-endian scalar ({@code S}) — no ASN.1
 *       wrapper. Simple to persist as a single base64url column and simple to reconstruct.
 * </ul>
 */
final class EcKeys {

    /** Field/point size for secp256r1 — every coordinate and the raw scalar are exactly this long. */
    static final int FIELD_BYTES = 32;

    /** {@code 0x04} prefix || X || Y. */
    static final int UNCOMPRESSED_POINT_BYTES = 1 + 2 * FIELD_BYTES;

    private static final byte UNCOMPRESSED_POINT_TAG = 0x04;

    private static final ECParameterSpec P256_PARAMS = loadP256Params();

    private EcKeys() {
    }

    static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            // The JDK's own "EC"/secp256r1 support (SunEC) is always present on a standard JVM;
            // this can only fail if the JVM itself is misconfigured, not from any user input.
            throw new IllegalStateException("EC P-256 key generation is not available on this JVM.", e);
        }
    }

    static byte[] encodePublicKey(ECPublicKey publicKey) {
        ECPoint point = publicKey.getW();
        byte[] encoded = new byte[UNCOMPRESSED_POINT_BYTES];
        encoded[0] = UNCOMPRESSED_POINT_TAG;
        writeFixedLength(point.getAffineX(), encoded, 1);
        writeFixedLength(point.getAffineY(), encoded, 1 + FIELD_BYTES);
        return encoded;
    }

    static byte[] encodePrivateKey(ECPrivateKey privateKey) {
        byte[] encoded = new byte[FIELD_BYTES];
        writeFixedLength(privateKey.getS(), encoded, 0);
        return encoded;
    }

    static ECPublicKey decodePublicKey(byte[] uncompressedPoint) {
        if (uncompressedPoint.length != UNCOMPRESSED_POINT_BYTES || uncompressedPoint[0] != UNCOMPRESSED_POINT_TAG) {
            throw new IllegalArgumentException(
                    "Expected a 65-byte uncompressed EC point (0x04 || X || Y), got " + uncompressedPoint.length + " bytes.");
        }
        BigInteger x = unsigned(uncompressedPoint, 1, FIELD_BYTES);
        BigInteger y = unsigned(uncompressedPoint, 1 + FIELD_BYTES, FIELD_BYTES);
        try {
            KeyFactory factory = KeyFactory.getInstance("EC");
            return (ECPublicKey) factory.generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256_PARAMS));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid EC public key point.", e);
        }
    }

    static ECPrivateKey decodePrivateKey(byte[] rawScalar) {
        if (rawScalar.length != FIELD_BYTES) {
            throw new IllegalArgumentException("Expected a 32-byte EC private key scalar, got " + rawScalar.length + " bytes.");
        }
        BigInteger s = unsigned(rawScalar, 0, FIELD_BYTES);
        try {
            KeyFactory factory = KeyFactory.getInstance("EC");
            return (ECPrivateKey) factory.generatePrivate(new ECPrivateKeySpec(s, P256_PARAMS));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid EC private key scalar.", e);
        }
    }

    static String toBase64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static byte[] fromBase64Url(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static BigInteger unsigned(byte[] source, int offset, int length) {
        byte[] slice = new byte[length];
        System.arraycopy(source, offset, slice, 0, length);
        return new BigInteger(1, slice);
    }

    /** Writes {@code value} into {@code destination[offset..offset+FIELD_BYTES)} as big-endian, zero-padded. */
    private static void writeFixedLength(BigInteger value, byte[] destination, int offset) {
        byte[] raw = value.toByteArray(); // two's-complement; may have a leading 0x00 sign byte or be short
        if (raw.length == FIELD_BYTES) {
            System.arraycopy(raw, 0, destination, offset, FIELD_BYTES);
        } else if (raw.length > FIELD_BYTES) {
            // Only ever one extra leading 0x00 sign byte for a positive value in range — drop it.
            System.arraycopy(raw, raw.length - FIELD_BYTES, destination, offset, FIELD_BYTES);
        } else {
            System.arraycopy(raw, 0, destination, offset + (FIELD_BYTES - raw.length), raw.length);
        }
    }

    private static ECParameterSpec loadP256Params() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (NoSuchAlgorithmException | InvalidParameterSpecException e) {
            // Same reasoning as generateKeyPair(): SunEC's secp256r1 support is a JVM constant, not
            // something user input can ever affect.
            throw new IllegalStateException("EC P-256 parameters are not available on this JVM.", e);
        }
    }
}
