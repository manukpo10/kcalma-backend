package com.kcalma.progress;

import com.kcalma.progress.dto.ProgressResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @GetMapping
    public ResponseEntity<ProgressResponse> get(@AuthenticationPrincipal Jwt jwt, @RequestParam String range) {
        UUID userId = UUID.fromString(jwt.getSubject());
        ProgressRange parsedRange = validateRange(range);
        return progressService
                .getProgress(userId, parsedRange)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ProgressRange validateRange(String range) {
        try {
            return ProgressRange.fromQueryValue(range);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rango no es válido. Usá 1M, 3M, 1Y o ALL.");
        }
    }
}
