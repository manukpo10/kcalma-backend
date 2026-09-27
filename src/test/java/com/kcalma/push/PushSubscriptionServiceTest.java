package com.kcalma.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

    @Mock
    private PushSubscriptionRepository repository;

    @InjectMocks
    private PushSubscriptionService service;

    @Test
    void subscribe_newEndpoint_savesANewSubscriptionForTheCaller() {
        UUID userId = UUID.randomUUID();
        when(repository.findByEndpoint("https://push.example/abc")).thenReturn(Optional.empty());

        service.subscribe(userId, "https://push.example/abc", "p256dh", "auth", "UA");

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getEndpoint()).isEqualTo("https://push.example/abc");
        assertThat(captor.getValue().getP256dh()).isEqualTo("p256dh");
    }

    @Test
    void subscribe_sameOwnerReSubscribing_upsertsInPlaceInsteadOfCreatingADuplicateRow() {
        UUID userId = UUID.randomUUID();
        PushSubscription existing = new PushSubscription(userId, "https://push.example/abc", "old-p256dh", "old-auth", "old-UA");
        when(repository.findByEndpoint("https://push.example/abc")).thenReturn(Optional.of(existing));

        service.subscribe(userId, "https://push.example/abc", "new-p256dh", "new-auth", "new-UA");

        verify(repository).save(existing);
        assertThat(existing.getUserId()).isEqualTo(userId);
        assertThat(existing.getP256dh()).isEqualTo("new-p256dh");
        assertThat(existing.getAuth()).isEqualTo("new-auth");
        assertThat(existing.getUserAgent()).isEqualTo("new-UA");
    }

    /** Shared-device re-subscribe: a different user, but the SAME browser (same keys) -- proof of possession, so ownership legitimately moves. */
    @Test
    void subscribe_differentOwnerWithMatchingKeys_reassignsOwnershipInPlace() {
        UUID originalOwner = UUID.randomUUID();
        UUID newOwner = UUID.randomUUID();
        PushSubscription existing = new PushSubscription(originalOwner, "https://push.example/abc", "shared-p256dh", "shared-auth", "old-UA");
        when(repository.findByEndpoint("https://push.example/abc")).thenReturn(Optional.of(existing));

        service.subscribe(newOwner, "https://push.example/abc", "shared-p256dh", "shared-auth", "new-UA");

        verify(repository).save(existing);
        assertThat(existing.getUserId()).isEqualTo(newOwner);
        assertThat(existing.getP256dh()).isEqualTo("shared-p256dh");
        assertThat(existing.getAuth()).isEqualTo("shared-auth");
        assertThat(existing.getUserAgent()).isEqualTo("new-UA");
    }

    /** Hijack attempt: a different user, and keys that DON'T match the stored subscription -- no proof of possession, so it's rejected instead of stolen. */
    @Test
    void subscribe_differentOwnerWithMismatchedKeys_rejectsWithConflictAndLeavesTheRowUntouched() {
        UUID originalOwner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        PushSubscription existing = new PushSubscription(originalOwner, "https://push.example/abc", "victim-p256dh", "victim-auth", "victim-UA");
        when(repository.findByEndpoint("https://push.example/abc")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.subscribe(attacker, "https://push.example/abc", "attacker-p256dh", "attacker-auth", "attacker-UA"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) ex;
                    assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(responseStatusException.getReason()).isEqualTo(PushSubscriptionService.OWNERSHIP_CONFLICT_MESSAGE);
                });

        verify(repository, never()).save(any());
        assertThat(existing.getUserId()).isEqualTo(originalOwner);
        assertThat(existing.getP256dh()).isEqualTo("victim-p256dh");
        assertThat(existing.getAuth()).isEqualTo("victim-auth");
    }

    @Test
    void unsubscribe_delegatesToTheOwnerScopedDeleteQuery() {
        UUID userId = UUID.randomUUID();

        service.unsubscribe(userId, "https://push.example/abc");

        verify(repository).deleteByUserIdAndEndpoint(userId, "https://push.example/abc");
    }
}
