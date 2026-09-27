package com.kcalma.push.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code DELETE /api/push/subscriptions} body. */
public record UnsubscribeRequest(@NotBlank String endpoint) {
}
