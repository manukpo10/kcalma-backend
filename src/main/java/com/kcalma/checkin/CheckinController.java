package com.kcalma.checkin;

import com.kcalma.checkin.dto.CheckinHistoryEntryResponse;
import com.kcalma.checkin.dto.CheckinResponse;
import com.kcalma.checkin.dto.CheckinWithTargetsResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkin")
public class CheckinController {

    private final CheckinService checkinService;

    public CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    @GetMapping
    public ResponseEntity<CheckinResponse> get(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return checkinService.getCurrentWeek(userId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/accept")
    public ResponseEntity<CheckinWithTargetsResponse> accept(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(checkinService.accept(userId));
    }

    @PostMapping("/dismiss")
    public ResponseEntity<CheckinResponse> dismiss(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(checkinService.dismiss(userId));
    }

    @GetMapping("/history")
    public ResponseEntity<List<CheckinHistoryEntryResponse>> history(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "12") int limit) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(checkinService.history(userId, limit));
    }
}
