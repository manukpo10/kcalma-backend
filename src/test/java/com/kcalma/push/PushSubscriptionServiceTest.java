package com.kcalma.push;

import static org.assertj.core.api.Assertions.assertThat;
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
    void subscribe_existingEndpoint_upsertsInPlaceInsteadOfCreatingADuplicateRow() {
        UUID originalOwner = UUID.randomUUID();
        UUID newOwner = UUID.randomUUID();
        PushSubscription existing = new PushSubscription(originalOwner, "https://push.example/abc", "old-p256dh", "old-auth", "old-UA");
        when(repository.findByEndpoint("https://push.example/abc")).thenReturn(Optional.of(existing));

        service.subscribe(newOwner, "https://push.example/abc", "new-p256dh", "new-auth", "new-UA");

        verify(repository).save(existing);
        assertThat(existing.getUserId()).isEqualTo(newOwner);
        assertThat(existing.getP256dh()).isEqualTo("new-p256dh");
        assertThat(existing.getAuth()).isEqualTo("new-auth");
        assertThat(existing.getUserAgent()).isEqualTo("new-UA");
    }

    @Test
    void unsubscribe_delegatesToTheOwnerScopedDeleteQuery() {
        UUID userId = UUID.randomUUID();

        service.unsubscribe(userId, "https://push.example/abc");

        verify(repository).deleteByUserIdAndEndpoint(userId, "https://push.example/abc");
    }
}
