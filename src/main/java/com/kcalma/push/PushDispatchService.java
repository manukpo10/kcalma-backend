package com.kcalma.push;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Sends one {@link PushPayload} to every one of a user's subscriptions and prunes the ones the
 * push service reports as gone (404/410) — the one piece of logic shared by {@code POST
 * /api/push/test} and {@code com.kcalma.reminder.ReminderScheduler}, so "send + clean up" is
 * never duplicated between them.
 */
@Service
public class PushDispatchService {

    private final PushSubscriptionRepository repository;
    private final WebPushSender sender;
    private final ObjectMapper objectMapper = new ObjectMapper();

    PushDispatchService(PushSubscriptionRepository repository, WebPushSender sender) {
        this.repository = repository;
        this.sender = sender;
    }

    /**
     * {@code com.kcalma.reminder.ReminderScheduler} checks this BEFORE claiming a reminder's dedupe
     * slot: a user with no subscriptions has nothing to send to, so the claim must not be taken at
     * all — otherwise a later tick (e.g. right after they finally grant permission) would find the
     * slot already claimed and skip it for the rest of the day.
     */
    @Transactional(readOnly = true)
    public boolean hasSubscriptions(UUID userId) {
        return !repository.findByUserId(userId).isEmpty();
    }

    /** @return how many of the user's subscriptions were actually sent to (excludes gone/failed ones). */
    @Transactional
    public int sendToUser(UUID userId, PushPayload payload) {
        return dispatchToUser(userId, payload).sent();
    }

    /**
     * Same delivery as {@link #sendToUser}, plus whether the occurrence is worth retrying on a
     * later tick — see {@link DispatchOutcome#shouldRetryLater()}.
     */
    @Transactional
    public DispatchOutcome dispatchToUser(UUID userId, PushPayload payload) {
        List<PushSubscription> subscriptions = repository.findByUserId(userId);
        if (subscriptions.isEmpty()) {
            return new DispatchOutcome(0, false);
        }

        byte[] payloadBytes = objectMapper.writeValueAsBytes(payload);
        int sent = 0;
        boolean anyTransientFailure = false;
        for (PushSubscription subscription : subscriptions) {
            if (!PushEndpointPolicy.isAllowed(subscription.getEndpoint())) {
                // Defense in depth: a row that predates the subscribe-time allowlist (or was written
                // some other way) must never be POSTed to -- drop it instead of calling the sender.
                repository.delete(subscription);
                continue;
            }
            WebPushSender.Result result = sender.send(subscription, payloadBytes);
            if (result == WebPushSender.Result.SENT) {
                sent++;
            } else if (result == WebPushSender.Result.GONE) {
                repository.delete(subscription);
            } else {
                anyTransientFailure = true;
            }
        }
        return new DispatchOutcome(sent, anyTransientFailure);
    }

    /**
     * {@code sent == 0}: nobody actually received the push. When that's because at least one
     * subscription failed transiently (5xx/timeout/429 — never GONE, which is a permanent delete,
     * not a failure to retry), {@link #shouldRetryLater()} tells {@code ReminderScheduler} to
     * release its dedupe claim so a tick later in the catch-up window tries again, instead of the
     * occurrence being silently lost for the day.
     */
    public record DispatchOutcome(int sent, boolean anyTransientFailure) {
        public boolean shouldRetryLater() {
            return sent == 0 && anyTransientFailure;
        }
    }
}
