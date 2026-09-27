package com.kcalma.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Validates {@link WebPushEncryptor} two independent ways, deliberately without reusing its
 * private HKDF/AES-GCM code, so a bug shared between production and test code can't cancel out:
 *
 * <ol>
 *   <li>{@code rfc8291Vector_...}: every HKDF-chain intermediate value this class computes for the
 *       RFC 8291 Appendix A worked example must equal the RFC's own published values, byte for
 *       byte. This is the strongest check — it's independently-authored ground truth, not
 *       something this test derives itself.
 *   <li>{@code decrypt(...)} below: a from-scratch RFC 8188 {@code aes128gcm} receiver
 *       implementation, written directly against the RFC rather than by calling into {@link
 *       WebPushEncryptor}. Round-tripping the production encryptor's output through it (both for
 *       the fixed RFC vector and for freshly-generated random keys) proves the two independent
 *       implementations agree — including on the parts (final AES-GCM ciphertext bytes, header
 *       layout) the first check alone wouldn't cover.
 * </ol>
 */
class WebPushEncryptorTest {

    // RFC 8291 Appendix A, "Push Message Encryption Example" (fetched from
    // https://www.rfc-editor.org/rfc/rfc8291.html on 2026-09-26).
    private static final String UA_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    private static final String AS_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";

    private static final String ECDH_SECRET = "kyrL1jIIOHEzg3sM2ZWRHDRB62YACZhhSlknJ672kSs";
    private static final String PRK_KEY = "Snr3JMxaHVDXHWJn5wdC52WjpCtd2EIEGBykDcZW32k";
    private static final String IKM = "S4lYMb_L0FxCeq0WhDx813KgSYqU26kOyzWUdsXYyrg";
    private static final String PRK = "09_eUZGrsvxChDCGRCdkLiDXrReGOEVeSCdCcPBSJSc";
    private static final String CEK = "oIhVW04MRdy2XN9CiKLxTg";
    private static final String NONCE = "4h_95klXJ5E_qnoN";

    private final WebPushEncryptor encryptor = new WebPushEncryptor();

    @Test
    void rfc8291Vector_everyHkdfIntermediateMatchesThePublishedExample() {
        ECPublicKey uaPublic = EcKeys.decodePublicKey(EcKeys.fromBase64Url(UA_PUBLIC));
        KeyPair asKeyPair = new KeyPair(
                EcKeys.decodePublicKey(EcKeys.fromBase64Url(AS_PUBLIC)), EcKeys.decodePrivateKey(EcKeys.fromBase64Url(AS_PRIVATE)));
        byte[] authSecret = EcKeys.fromBase64Url(AUTH_SECRET);
        byte[] salt = EcKeys.fromBase64Url(SALT);

        WebPushEncryptor.Intermediates values = encryptor.computeIntermediates(uaPublic, authSecret, asKeyPair, salt);

        assertThat(EcKeys.toBase64Url(values.ecdhSecret())).isEqualTo(ECDH_SECRET);
        assertThat(EcKeys.toBase64Url(values.prkKey())).isEqualTo(PRK_KEY);
        assertThat(EcKeys.toBase64Url(values.ikm())).isEqualTo(IKM);
        assertThat(EcKeys.toBase64Url(values.prk())).isEqualTo(PRK);
        assertThat(EcKeys.toBase64Url(values.cek())).isEqualTo(CEK);
        assertThat(EcKeys.toBase64Url(values.nonce())).isEqualTo(NONCE);
    }

    @Test
    void rfc8291Vector_encryptThenIndependentlyDecryptedRecoversThePlaintext() throws Exception {
        ECPublicKey uaPublic = EcKeys.decodePublicKey(EcKeys.fromBase64Url(UA_PUBLIC));
        ECPrivateKey uaPrivate = EcKeys.decodePrivateKey(EcKeys.fromBase64Url(UA_PRIVATE));
        KeyPair asKeyPair = new KeyPair(
                EcKeys.decodePublicKey(EcKeys.fromBase64Url(AS_PUBLIC)), EcKeys.decodePrivateKey(EcKeys.fromBase64Url(AS_PRIVATE)));
        byte[] authSecret = EcKeys.fromBase64Url(AUTH_SECRET);
        byte[] salt = EcKeys.fromBase64Url(SALT);

        byte[] body = encryptor.encrypt(uaPublic, authSecret, PLAINTEXT.getBytes(StandardCharsets.UTF_8), asKeyPair, salt);

        assertThat(decrypt(uaPrivate, uaPublic, authSecret, body)).isEqualTo(PLAINTEXT.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void randomKeys_encryptThenIndependentlyDecryptedRoundTripsAnArbitraryPayload() throws Exception {
        KeyPair subscriber = EcKeys.generateKeyPair();
        byte[] authSecret = new byte[16];
        new java.security.SecureRandom().nextBytes(authSecret);
        String payload = "{\"title\":\"Kcalma\",\"body\":\"¿Qué almorzaste?\",\"url\":\"/agregar?meal=ALMUERZO\"}";

        byte[] body = encryptor.encrypt(
                (ECPublicKey) subscriber.getPublic(), authSecret, payload.getBytes(StandardCharsets.UTF_8));

        byte[] recovered = decrypt((ECPrivateKey) subscriber.getPrivate(), (ECPublicKey) subscriber.getPublic(), authSecret, body);
        assertThat(new String(recovered, StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    @Test
    void encrypt_headerCarriesTheRandomSaltAndTheEphemeralSenderKeyAtA4096RecordSize() {
        KeyPair subscriber = EcKeys.generateKeyPair();
        byte[] authSecret = new byte[16];

        byte[] body = encryptor.encrypt((ECPublicKey) subscriber.getPublic(), authSecret, "hi".getBytes(StandardCharsets.UTF_8));
        ByteBuffer header = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        header.get(salt);
        int recordSize = header.getInt();
        int keyIdLen = header.get() & 0xFF;

        assertThat(recordSize).isEqualTo(WebPushEncryptor.RECORD_SIZE);
        assertThat(keyIdLen).isEqualTo(EcKeys.UNCOMPRESSED_POINT_BYTES);
    }

    /**
     * A from-scratch RFC 8188 {@code aes128gcm} receiver, written independently of {@link
     * WebPushEncryptor}'s own HKDF/AES-GCM code (see class javadoc) — this is the "browser/push
     * service" side of the exchange, not code under test.
     */
    private static byte[] decrypt(ECPrivateKey receiverPrivate, ECPublicKey receiverPublic, byte[] authSecret, byte[] body)
            throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buffer.get(salt);
        buffer.getInt(); // record size -- unused by a single-record decrypt
        int keyIdLength = buffer.get() & 0xFF;
        byte[] senderPublicBytes = new byte[keyIdLength];
        buffer.get(senderPublicBytes);
        byte[] record = new byte[buffer.remaining()];
        buffer.get(record);

        ECPublicKey senderPublic = EcKeys.decodePublicKey(senderPublicBytes);
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(receiverPrivate);
        agreement.doPhase(senderPublic, true);
        byte[] ecdhSecret = agreement.generateSecret();

        byte[] receiverPublicBytes = EcKeys.encodePublicKey(receiverPublic);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), receiverPublicBytes, senderPublicBytes);

        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] ikm = hkdfExpand(prkKey, keyInfo, 32);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = hkdfExpand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdfExpand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(record);

        int end = padded.length;
        while (end > 0 && padded[end - 1] == 0x00) {
            end--; // strip optional trailing zero padding
        }
        // end-1 is now the 0x02 "last record" delimiter (RFC 8188 §2) -- strip that too.
        return Arrays.copyOfRange(padded, 0, end - 1);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws Exception {
        byte[] block = hmac(prk, concat(info, new byte[] {0x01}));
        return Arrays.copyOf(block, length);
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] result = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }
}
