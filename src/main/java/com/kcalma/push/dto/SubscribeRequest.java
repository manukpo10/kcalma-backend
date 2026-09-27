package com.kcalma.push.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code POST /api/push/subscriptions} body — the browser's own {@code PushSubscription.toJSON()} shape, plus an optional {@code userAgent}. */
public record SubscribeRequest(@NotBlank String endpoint, @NotNull @Valid Keys keys, String userAgent) {

    public record Keys(@NotBlank String p256dh, @NotBlank String auth) {
    }
}
