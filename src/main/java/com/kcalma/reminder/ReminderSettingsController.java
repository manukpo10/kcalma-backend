package com.kcalma.reminder;

import com.kcalma.reminder.dto.ReminderSettingsRequest;
import com.kcalma.reminder.dto.ReminderSettingsResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner-only (see {@code com.kcalma.security.SecurityConfig}'s blanket {@code /api/**} rule) — no extra security wiring needed here. */
@RestController
@RequestMapping("/api/reminders")
public class ReminderSettingsController {

    private final ReminderSettingsService service;

    public ReminderSettingsController(ReminderSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ReminderSettingsResponse> get(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ReminderSettingsResponse.from(service.get(userId)));
    }

    @PutMapping
    public ResponseEntity<ReminderSettingsResponse> update(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReminderSettingsRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        ReminderSettingsData updated = service.update(userId, request.toData());
        return ResponseEntity.ok(ReminderSettingsResponse.from(updated));
    }
}
