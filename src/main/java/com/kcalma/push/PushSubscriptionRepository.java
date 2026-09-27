package com.kcalma.push;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserId(UUID userId);

    void deleteByUserIdAndEndpoint(UUID userId, String endpoint);

    /** Bulk-deletes every subscription a user ever registered — used by account deletion (via {@code PushSubscriptionService}). */
    void deleteByUserId(UUID userId);
}
