package com.kcalma.push;

/** The JSON web push notification payload itself (encrypted before it ever leaves the server — see {@link WebPushEncryptor}). */
public record PushPayload(String title, String body, String url) {
}
