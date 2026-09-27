package com.kcalma.push;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owner-scoped CRUD for {@link PushSubscription} rows — the {@code POST}/{@code DELETE /api/push/subscriptions} endpoints. */
@Service
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;

    public PushSubscriptionService(PushSubscriptionRepository repository) {
        this.repository = repository;
    }

    /** Upsert by {@code endpoint} (globally unique) — see {@link PushSubscription#update}. */
    @Transactional
    public void subscribe(UUID userId, String endpoint, String p256dh, String auth, String userAgent) {
        PushSubscription subscription =
                repository.findByEndpoint(endpoint).orElseGet(() -> new PushSubscription(userId, endpoint, p256dh, auth, userAgent));
        subscription.update(userId, p256dh, auth, userAgent);
        repository.save(subscription);
    }

    /** Scoped to {@code userId} as well as {@code endpoint}: never lets one owner delete another's row. */
    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        repository.deleteByUserIdAndEndpoint(userId, endpoint);
    }
}
