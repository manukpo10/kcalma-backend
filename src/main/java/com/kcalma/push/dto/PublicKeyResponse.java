package com.kcalma.push.dto;

/** {@code GET /api/push/public-key} — the VAPID public key, base64url of the uncompressed P-256 point, ready for {@code applicationServerKey}. */
public record PublicKeyResponse(String publicKey) {
}
