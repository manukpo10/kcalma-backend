package com.kcalma.push;

import jakarta.annotation.PostConstruct;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Resolves the one VAPID (RFC 8292) EC P-256 key pair this server signs every push message with.
 *
 * <p>Resolution order, decided once and cached for the process lifetime (the key pair never
 * changes while running — see {@link #resolveKeyPair()}):
 *
 * <ol>
 *   <li>{@code VAPID_PUBLIC_KEY} + {@code VAPID_PRIVATE_KEY} env vars, if both are set — lets an
 *       operator pin a specific key pair (e.g. carried over from another deployment) without a DB
 *       write.
 *   <li>Otherwise, the single row in {@code app.push_config}, if one already exists.
 *   <li>Otherwise, generate a fresh key pair and persist it — every future startup then falls into
 *       branch 2. {@code DataIntegrityViolationException} on that insert (another instance won the
 *       race — see {@code PushConfig}'s {@code CHECK (id = 1)}) is treated as a cue to simply
 *       re-read the row the other instance just wrote, not an error.
 * </ol>
 *
 * <p>The private key is never logged: it flows straight from {@link EcKeys}/{@link PushConfig}
 * into an in-memory {@link ECPrivateKey}, and every log statement in this class names only which
 * branch above was taken.
 */
@Service
@EnableConfigurationProperties(VapidProperties.class)
public class VapidKeyService {

    private final PushConfigRepository repository;
    private final VapidProperties properties;
    private volatile KeyPair cached;

    public VapidKeyService(PushConfigRepository repository, VapidProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Resolves (and, the very first time, generates + persists) the key pair eagerly at startup
     * rather than lazily on the first push — a malformed {@code VAPID_PUBLIC_KEY}/{@code
     * VAPID_PRIVATE_KEY} env var then fails application startup immediately instead of the first
     * time someone happens to send a reminder, and an operator can confirm "the key pair exists"
     * right after deploying without needing to trigger a send first.
     */
    @PostConstruct
    void resolveAtStartup() {
        resolveKeyPair();
    }

    public KeyPair resolveKeyPair() {
        KeyPair local = cached;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cached == null) {
                cached = doResolve();
            }
            return cached;
        }
    }

    public String getPublicKeyBase64Url() {
        return EcKeys.toBase64Url(EcKeys.encodePublicKey((ECPublicKey) resolveKeyPair().getPublic()));
    }

    public String getSubject() {
        return properties.getSubject();
    }

    /**
     * No {@code @Transactional} here on purpose: {@code repository.findById}/{@code
     * saveAndFlush} are each already transactional on their own (every {@code SimpleJpaRepository}
     * method is), and a self-invoked {@code private} method couldn't be advised by Spring's proxy
     * anyway (self-invocation never goes through the proxy — see the well-known AOP pitfall). The
     * race between the read and the write is handled explicitly below instead of by wrapping both
     * in one DB transaction.
     */
    private KeyPair doResolve() {
        if (isSet(properties.getPublicKey()) && isSet(properties.getPrivateKey())) {
            return new KeyPair(
                    EcKeys.decodePublicKey(EcKeys.fromBase64Url(properties.getPublicKey())),
                    EcKeys.decodePrivateKey(EcKeys.fromBase64Url(properties.getPrivateKey())));
        }

        Optional<PushConfig> existing = repository.findById(PushConfig.SINGLETON_ID);
        if (existing.isPresent()) {
            return toKeyPair(existing.get());
        }

        KeyPair generated = EcKeys.generateKeyPair();
        PushConfig config = new PushConfig(
                EcKeys.toBase64Url(EcKeys.encodePublicKey((ECPublicKey) generated.getPublic())),
                EcKeys.toBase64Url(EcKeys.encodePrivateKey((ECPrivateKey) generated.getPrivate())));
        try {
            repository.saveAndFlush(config);
            return generated;
        } catch (DataIntegrityViolationException raceLost) {
            // Another instance generated and committed the singleton row first -- use that one.
            return repository.findById(PushConfig.SINGLETON_ID).map(this::toKeyPair).orElseThrow(() -> raceLost);
        }
    }

    private KeyPair toKeyPair(PushConfig config) {
        return new KeyPair(
                EcKeys.decodePublicKey(EcKeys.fromBase64Url(config.getPublicKey())),
                EcKeys.decodePrivateKey(EcKeys.fromBase64Url(config.getPrivateKey())));
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
