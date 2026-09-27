package com.kcalma.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link WebPushSender} is mocked throughout, per the sprint requirement ("404/410 cleanup (mock the sender)"). */
@ExtendWith(MockitoExtension.class)
class PushDispatchServiceTest {

    @Mock
    private PushSubscriptionRepository repository;

    @Mock
    private WebPushSender sender;

    @InjectMocks
    private PushDispatchService dispatchService;

    private static final PushPayload PAYLOAD = new PushPayload("Kcalma", "hola", "/");

    @Test
    void sendToUser_noSubscriptions_returnsZeroAndNeverCallsTheSender() {
        UUID userId = UUID.randomUUID();
        when(repository.findByUserId(userId)).thenReturn(List.of());

        int sent = dispatchService.sendToUser(userId, PAYLOAD);

        assertThat(sent).isZero();
        verifyNoInteractions(sender);
    }

    @Test
    void sendToUser_senderReportsGone_deletesTheSubscriptionAndDoesNotCountIt() {
        UUID userId = UUID.randomUUID();
        PushSubscription subscription = subscriptionFor(userId);
        when(repository.findByUserId(userId)).thenReturn(List.of(subscription));
        when(sender.send(eq(subscription), any())).thenReturn(WebPushSender.Result.GONE);

        int sent = dispatchService.sendToUser(userId, PAYLOAD);

        assertThat(sent).isZero();
        verify(repository).delete(subscription);
    }

    @Test
    void sendToUser_mixOfOutcomes_countsOnlySentAndDeletesOnlyTheGoneOne() {
        UUID userId = UUID.randomUUID();
        PushSubscription sent = subscriptionFor(userId);
        PushSubscription gone = subscriptionFor(userId);
        PushSubscription failed = subscriptionFor(userId);
        when(repository.findByUserId(userId)).thenReturn(List.of(sent, gone, failed));
        when(sender.send(eq(sent), any())).thenReturn(WebPushSender.Result.SENT);
        when(sender.send(eq(gone), any())).thenReturn(WebPushSender.Result.GONE);
        when(sender.send(eq(failed), any())).thenReturn(WebPushSender.Result.FAILED);

        int sentCount = dispatchService.sendToUser(userId, PAYLOAD);

        assertThat(sentCount).isEqualTo(1);
        verify(repository).delete(gone);
        verify(repository, never()).delete(sent);
        verify(repository, never()).delete(failed);
    }

    private static PushSubscription subscriptionFor(UUID userId) {
        return new PushSubscription(userId, "https://push.example/" + UUID.randomUUID(), "p256dh", "auth", "UA");
    }
}
