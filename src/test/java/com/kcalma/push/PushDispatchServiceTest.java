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

    @Test
    void hasSubscriptions_noRows_returnsFalse() {
        UUID userId = UUID.randomUUID();
        when(repository.findByUserId(userId)).thenReturn(List.of());

        assertThat(dispatchService.hasSubscriptions(userId)).isFalse();
    }

    @Test
    void hasSubscriptions_atLeastOneRow_returnsTrue() {
        UUID userId = UUID.randomUUID();
        when(repository.findByUserId(userId)).thenReturn(List.of(subscriptionFor(userId)));

        assertThat(dispatchService.hasSubscriptions(userId)).isTrue();
    }

    /** {@code ReminderScheduler} releases its dedupe claim on exactly this outcome — see {@code PushDispatchService.DispatchOutcome}. */
    @Test
    void dispatchToUser_everySubscriptionFailsTransiently_reportsShouldRetryLater() {
        UUID userId = UUID.randomUUID();
        PushSubscription subscription = subscriptionFor(userId);
        when(repository.findByUserId(userId)).thenReturn(List.of(subscription));
        when(sender.send(eq(subscription), any())).thenReturn(WebPushSender.Result.FAILED);

        PushDispatchService.DispatchOutcome outcome = dispatchService.dispatchToUser(userId, PAYLOAD);

        assertThat(outcome.sent()).isZero();
        assertThat(outcome.anyTransientFailure()).isTrue();
        assertThat(outcome.shouldRetryLater()).isTrue();
        verify(repository, never()).delete(subscription);
    }

    @Test
    void dispatchToUser_partialSuccessWithATransientFailure_doesNotRecommendRetry() {
        UUID userId = UUID.randomUUID();
        PushSubscription sent = subscriptionFor(userId);
        PushSubscription failed = subscriptionFor(userId);
        when(repository.findByUserId(userId)).thenReturn(List.of(sent, failed));
        when(sender.send(eq(sent), any())).thenReturn(WebPushSender.Result.SENT);
        when(sender.send(eq(failed), any())).thenReturn(WebPushSender.Result.FAILED);

        PushDispatchService.DispatchOutcome outcome = dispatchService.dispatchToUser(userId, PAYLOAD);

        assertThat(outcome.sent()).isEqualTo(1);
        assertThat(outcome.shouldRetryLater()).isFalse(); // partial success -- keep the claim, don't resend to the ones that worked
    }

    /** GONE is a permanent delete, not a transient failure -- it must never trigger a claim-release retry. */
    @Test
    void dispatchToUser_onlyGoneSubscriptions_doesNotRecommendRetry() {
        UUID userId = UUID.randomUUID();
        PushSubscription gone = subscriptionFor(userId);
        when(repository.findByUserId(userId)).thenReturn(List.of(gone));
        when(sender.send(eq(gone), any())).thenReturn(WebPushSender.Result.GONE);

        PushDispatchService.DispatchOutcome outcome = dispatchService.dispatchToUser(userId, PAYLOAD);

        assertThat(outcome.sent()).isZero();
        assertThat(outcome.shouldRetryLater()).isFalse();
    }

    /** SSRF defense in depth (see {@code PushEndpointPolicy}): a stored row that wouldn't pass subscribe-time validation today must never be POSTed to. */
    @Test
    void dispatchToUser_endpointFailsPolicy_deletesTheRowAndNeverCallsTheSender() {
        UUID userId = UUID.randomUUID();
        PushSubscription invalid = new PushSubscription(userId, "https://evil.example/collect", "p256dh", "auth", "UA");
        when(repository.findByUserId(userId)).thenReturn(List.of(invalid));

        PushDispatchService.DispatchOutcome outcome = dispatchService.dispatchToUser(userId, PAYLOAD);

        assertThat(outcome.sent()).isZero();
        assertThat(outcome.shouldRetryLater()).isFalse(); // dropped, not a transient failure -- retrying would never help
        verify(repository).delete(invalid);
        verifyNoInteractions(sender);
    }

    /** A syntactically-valid, {@code PushEndpointPolicy}-allowed endpoint — see that class for why an arbitrary host is now rejected. */
    private static PushSubscription subscriptionFor(UUID userId) {
        return new PushSubscription(userId, "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID(), "p256dh", "auth", "UA");
    }
}
