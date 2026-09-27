package com.kcalma.push;

import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owner-scoped CRUD for {@link PushSubscription} rows — the {@code POST}/{@code DELETE /api/push/subscriptions} endpoints. */
@Service
public class PushSubscriptionService {

    /**
     * {@code endpoint} isn't a secret -- it can leak via logs, a shared device, or a copy-pasted
     * bug report -- so a request that merely knows someone else's endpoint must not be enough to
     * take their subscription over. See {@link #subscribe}.
     */
    static final String OWNERSHIP_CONFLICT_MESSAGE = "Esta suscripción de notificaciones pertenece a otra cuenta.";

    private final PushSubscriptionRepository repository;

    public PushSubscriptionService(PushSubscriptionRepository repository) {
        this.repository = repository;
    }

    /**
     * Upsert by {@code endpoint} (globally unique) — see {@link PushSubscription#update}.
     *
     * <p>When {@code endpoint} already belongs to a DIFFERENT user, reassigning it is only allowed
     * as proof of possession: {@code p256dh} AND {@code auth} must match exactly what that row
     * already has stored. That's what happens when the same browser genuinely re-subscribes on a
     * shared device (the push service can reissue the same endpoint, but the browser's own key pair
     * behind it doesn't change). If either key differs, {@code endpoint} is being replayed by
     * someone who doesn't actually hold the original subscription -- reject with 409 and leave the
     * existing row untouched, rather than silently handing it to the caller.
     *
     * @throws ResponseStatusException 409 ({@link #OWNERSHIP_CONFLICT_MESSAGE}) if {@code endpoint}
     *     belongs to another user and {@code p256dh}/{@code auth} don't match that row's stored
     *     values
     */
    @Transactional
    public void subscribe(UUID userId, String endpoint, String p256dh, String auth, String userAgent) {
        Optional<PushSubscription> existing = repository.findByEndpoint(endpoint);
        if (existing.isPresent()) {
            PushSubscription subscription = existing.get();
            boolean sameOwner = subscription.getUserId().equals(userId);
            boolean provesPossession = subscription.getP256dh().equals(p256dh) && subscription.getAuth().equals(auth);
            if (!sameOwner && !provesPossession) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, OWNERSHIP_CONFLICT_MESSAGE);
            }
            subscription.update(userId, p256dh, auth, userAgent);
            repository.save(subscription);
            return;
        }
        repository.save(new PushSubscription(userId, endpoint, p256dh, auth, userAgent));
    }

    /** Scoped to {@code userId} as well as {@code endpoint}: never lets one owner delete another's row. */
    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        repository.deleteByUserIdAndEndpoint(userId, endpoint);
    }

    /**
     * Deletes every subscription for {@code userId} — used by account deletion ({@code
     * com.kcalma.account.AccountService}), which lives outside this package and so can't reach the
     * package-private {@link PushSubscriptionRepository} directly.
     */
    @Transactional
    public void deleteAllForUser(UUID userId) {
        repository.deleteByUserId(userId);
    }
}
