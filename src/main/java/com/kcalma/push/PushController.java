package com.kcalma.push;

import com.kcalma.push.dto.PublicKeyResponse;
import com.kcalma.push.dto.SubscribeRequest;
import com.kcalma.push.dto.TestPushResponse;
import com.kcalma.push.dto.UnsubscribeRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Open to any authenticated user (see {@code com.kcalma.security.SecurityConfig}'s blanket {@code /api/**} rule) — no extra security wiring needed here. */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private final VapidKeyService vapidKeyService;
    private final PushSubscriptionService subscriptionService;
    private final PushDispatchService dispatchService;

    public PushController(VapidKeyService vapidKeyService, PushSubscriptionService subscriptionService, PushDispatchService dispatchService) {
        this.vapidKeyService = vapidKeyService;
        this.subscriptionService = subscriptionService;
        this.dispatchService = dispatchService;
    }

    @GetMapping("/public-key")
    public ResponseEntity<PublicKeyResponse> publicKey() {
        return ResponseEntity.ok(new PublicKeyResponse(vapidKeyService.getPublicKeyBase64Url()));
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SubscribeRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        validateEndpoint(request.endpoint());
        subscriptionService.subscribe(userId, request.endpoint(), request.keys().p256dh(), request.keys().auth(), request.userAgent());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * SSRF guard: only a real browser push service's endpoint may ever be stored, since {@link
     * WebPushSender} later POSTs straight to it -- see {@link PushEndpointPolicy} for the allowlist.
     */
    private void validateEndpoint(String endpoint) {
        if (!PushEndpointPolicy.isAllowed(endpoint)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Endpoint de notificaciones no válido.");
        }
    }

    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UnsubscribeRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        subscriptionService.unsubscribe(userId, request.endpoint());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    public ResponseEntity<TestPushResponse> test(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        int sent = dispatchService.sendToUser(
                userId,
                new PushPayload("Kcalma · Notificación de prueba", "Si ves esto, las notificaciones funcionan bien.", "/"));
        return ResponseEntity.ok(new TestPushResponse(sent));
    }
}
