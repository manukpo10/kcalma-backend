package com.kcalma.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Maps to app.push_subscription (V15__push_and_reminders.sql): one row per browser {@code
 * PushSubscription} the owner has granted. {@code endpoint} is globally unique (it already
 * encodes the push service + a per-subscription id), so {@code POST /api/push/subscriptions} is
 * an upsert keyed on it — see {@link PushSubscriptionService#subscribe}. {@code p256dh}/{@code
 * auth} are stored exactly as the browser sent them (base64url); {@link WebPushSender} decodes
 * them at send time via {@link EcKeys}.
 */
@Entity
@Table(name = "push_subscription")
public class PushSubscription {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "endpoint", nullable = false)
    private String endpoint;

    @Column(name = "p256dh", nullable = false)
    private String p256dh;

    @Column(name = "auth", nullable = false)
    private String auth;

    @Column(name = "user_agent")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PushSubscription() {
        // JPA
    }

    PushSubscription(UUID userId, String endpoint, String p256dh, String auth, String userAgent) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.userAgent = userAgent;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Re-establishes every mutable field on a {@code POST /api/push/subscriptions} upsert,
     * including {@code userId}. Unconditional -- it doesn't itself check who's allowed to claim this
     * row; {@link PushSubscriptionService#subscribe} decides that (proof of possession when the
     * caller isn't already the owner) before ever calling this.
     */
    void update(UUID userId, String p256dh, String auth, String userAgent) {
        this.userId = userId;
        this.p256dh = p256dh;
        this.auth = auth;
        this.userAgent = userAgent;
    }
}
