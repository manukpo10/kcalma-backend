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

    /** @return how many of the user's subscriptions were actually sent to (excludes gone/failed ones). */
    @Transactional
    public int sendToUser(UUID userId, PushPayload payload) {
        List<PushSubscription> subscriptions = repository.findByUserId(userId);
        if (subscriptions.isEmpty()) {
            return 0;
        }

        byte[] payloadBytes = objectMapper.writeValueAsBytes(payload);
        int sent = 0;
        for (PushSubscription subscription : subscriptions) {
            WebPushSender.Result result = sender.send(subscription, payloadBytes);
            if (result == WebPushSender.Result.SENT) {
                sent++;
            } else if (result == WebPushSender.Result.GONE) {
                repository.delete(subscription);
            }
        }
        return sent;
    }
}
