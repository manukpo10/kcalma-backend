package com.kcalma.push;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Implements RFC 8291 (Message Encryption for Web Push, {@code aes128gcm}) using only the JDK's
 * own {@code java.security}/{@code javax.crypto} providers.
 *
 * <p><b>Why hand-rolled instead of a library:</b> the historical standard, {@code
 * nl.martijndwars:web-push} (now hosted at {@code web-push-libs/webpush-java}), still frames
 * itself as "a Web Push library for Java 8", carries 40+ open issues, and pulls in BouncyCastle as
 * a JCE provider just for EC/AES-GCM primitives the JDK has natively supported since Java 8 — that
 * combination didn't clear the bar for "reliable" for a security-sensitive path in a low-traffic,
 * single-maintainer app. The one actively modern alternative found (Java-21-native, zero runtime
 * deps), {@code com.the13haven:push2u-core}, is version 0.3.0 with a single GitHub star — too
 * unproven to trust with private key handling. RFC 8291 is small, has a fully worked test vector
 * (Appendix A) this class is tested against byte-for-byte (see {@code WebPushEncryptorTest}), and
 * Java 21's {@code SunEC} provider already natively supports every primitive it needs (P-256
 * ECDH, HMAC-SHA256, AES-128-GCM) — so this stays a from-spec implementation instead of a new
 * third-party dependency.
 *
 * <p>Algorithm (RFC 8291 §3.4 combined with RFC 8188's {@code aes128gcm} content coding):
 * <pre>
 *   ecdh_secret      = ECDH(as_private, ua_public)                                   -- as = this app server's per-message EPHEMERAL key pair, ua = the subscriber's p256dh
 *   prk_key          = HMAC-SHA256(key = auth_secret,           data = ecdh_secret)   -- HKDF-Extract
 *   key_info         = "WebPush: info" || 0x00 || ua_public(65) || as_public(65)
 *   ikm              = HMAC-SHA256(key = prk_key, data = key_info || 0x01)[0:32]      -- HKDF-Expand (1 block, since 32 == HMAC-SHA256's own output size)
 *   prk              = HMAC-SHA256(key = salt,     data = ikm)                        -- HKDF-Extract, salt is random per message
 *   cek              = HMAC-SHA256(key = prk, data = "Content-Encoding: aes128gcm" || 0x00 || 0x01)[0:16]
 *   nonce            = HMAC-SHA256(key = prk, data = "Content-Encoding: nonce"     || 0x00 || 0x01)[0:12]
 *   record           = AES-128-GCM(key = cek, iv = nonce, plaintext || 0x02)          -- 0x02: last (and only) record's padding delimiter, RFC 8188 §2
 *   body             = salt(16) || record_size(4, big-endian) || len(as_public)(1) || as_public || record
 * </pre>
 *
 * The VAPID key pair ({@link VapidKeyService}) is completely unrelated to the {@code as} key pair
 * here: VAPID signs the {@code Authorization} header's JWT (RFC 8292); this class generates a
 * brand-new, single-use EC key pair for every message, as RFC 8291 requires.
 */
@Component
class WebPushEncryptor {

    static final int RECORD_SIZE = 4096;
    private static final byte PADDING_DELIMITER = 0x02;

    private static final byte[] KEY_INFO_PREFIX = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);

    private final SecureRandom random = new SecureRandom();

    /** Encrypts {@code plaintext} for one subscriber, generating a fresh ephemeral key pair and salt. */
    byte[] encrypt(ECPublicKey subscriberPublicKey, byte[] authSecret, byte[] plaintext) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return encrypt(subscriberPublicKey, authSecret, plaintext, EcKeys.generateKeyPair(), salt);
    }

    /**
     * Same as {@link #encrypt(ECPublicKey, byte[], byte[])} but with the per-message ephemeral key
     * pair and salt supplied explicitly, so the RFC 8291 Appendix A vector (fixed keys/salt) can
     * drive this method directly in tests instead of only the randomized public entry point.
     */
    byte[] encrypt(ECPublicKey subscriberPublicKey, byte[] authSecret, byte[] plaintext, KeyPair ephemeral, byte[] salt) {
        Intermediates values = computeIntermediates(subscriberPublicKey, authSecret, ephemeral, salt);
        byte[] senderPublicBytes = EcKeys.encodePublicKey((ECPublicKey) ephemeral.getPublic());

        byte[] padded = concat(plaintext, new byte[] {PADDING_DELIMITER});
        byte[] record = aesGcmEncrypt(values.cek(), values.nonce(), padded);

        ByteBuffer body = ByteBuffer.allocate(16 + 4 + 1 + senderPublicBytes.length + record.length);
        body.put(salt);
        body.putInt(RECORD_SIZE);
        body.put((byte) senderPublicBytes.length);
        body.put(senderPublicBytes);
        body.put(record);
        return body.array();
    }

    /** Every HKDF-chain intermediate value, exposed package-private so tests can check each stage against RFC 8291 Appendix A. */
    record Intermediates(byte[] ecdhSecret, byte[] prkKey, byte[] ikm, byte[] prk, byte[] cek, byte[] nonce) {
    }

    Intermediates computeIntermediates(ECPublicKey subscriberPublicKey, byte[] authSecret, KeyPair ephemeral, byte[] salt) {
        byte[] ecdhSecret = ecdh((ECPrivateKey) ephemeral.getPrivate(), subscriberPublicKey);

        byte[] uaPublicBytes = EcKeys.encodePublicKey(subscriberPublicKey);
        byte[] asPublicBytes = EcKeys.encodePublicKey((ECPublicKey) ephemeral.getPublic());
        byte[] keyInfo = concat(KEY_INFO_PREFIX, uaPublicBytes, asPublicBytes);

        byte[] prkKey = hmacSha256(authSecret, ecdhSecret);
        byte[] ikm = hkdfExpand(prkKey, keyInfo, 32);

        byte[] prk = hmacSha256(salt, ikm);
        byte[] cek = hkdfExpand(prk, CEK_INFO, 16);
        byte[] nonce = hkdfExpand(prk, NONCE_INFO, 12);

        return new Intermediates(ecdhSecret, prkKey, ikm, prk, cek, nonce);
    }

    private byte[] ecdh(ECPrivateKey privateKey, ECPublicKey publicKey) {
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(privateKey);
            agreement.doPhase(publicKey, true);
            return agreement.generateSecret();
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Could not compute the ECDH shared secret — invalid subscription key?", e);
        }
    }

    /** HKDF-Expand (RFC 5869 §2.3) for an output no longer than the hash size: a single HMAC block, truncated. */
    private byte[] hkdfExpand(byte[] prk, byte[] info, int length) {
        byte[] block = hmacSha256(prk, concat(info, new byte[] {0x01}));
        byte[] output = new byte[length];
        System.arraycopy(block, 0, output, 0, length);
        return output;
    }

    /** HMAC-SHA256 doubling as HKDF-Extract (RFC 5869 §2.2): {@code key} is the "salt" argument there, never empty for this class's two call sites. */
    private byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available on this JVM.", e);
        }
    }

    private byte[] aesGcmEncrypt(byte[] key, byte[] nonce, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return cipher.doFinal(plaintext);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("AES-128-GCM is not available on this JVM.", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unexpected AES-GCM encryption failure.", e);
        }
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
