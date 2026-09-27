package com.kcalma.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Maps to app.push_config (V15__push_and_reminders.sql): the VAPID EC P-256 key pair, generated
 * once and persisted here when neither {@code VAPID_PUBLIC_KEY} nor {@code VAPID_PRIVATE_KEY} is
 * set (see {@link VapidKeyService}). A true singleton — the table's own {@code CHECK (id = 1)}
 * makes a second row impossible at the DB level, not just by convention.
 *
 * <p>{@code publicKey}/{@code privateKey} are base64url of the raw wire encodings {@link EcKeys}
 * uses (65-byte uncompressed point / 32-byte scalar), not PEM/DER. Nothing here overrides {@code
 * toString()} — the default {@code Object} one prints only the class name and identity hash, so a
 * stray {@code log.debug("{}", pushConfig)} anywhere could never leak {@code privateKey}.
 */
@Entity
@Table(name = "push_config")
public class PushConfig {

    public static final short SINGLETON_ID = 1;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private short id = SINGLETON_ID;

    @Column(name = "public_key", nullable = false, updatable = false)
    private String publicKey;

    @Column(name = "private_key", nullable = false, updatable = false)
    private String privateKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected PushConfig() {
        // JPA
    }

    PushConfig(String publicKey, String privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public short getId() {
        return id;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
