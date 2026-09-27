package com.kcalma.push.dto;

/** {@code POST /api/push/test} — how many of the caller's subscriptions were actually sent to. */
public record TestPushResponse(int sent) {
}
