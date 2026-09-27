package com.kcalma.water;

import com.kcalma.water.dto.UpsertWaterRequest;
import com.kcalma.water.dto.WaterResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/water")
public class WaterController {

    private final WaterLogService service;
    private final Clock clock;

    public WaterController(WaterLogService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<WaterResponse> log(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpsertWaterRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        validateNotFuture(request.date());
        return ResponseEntity.ok(service.applyDelta(userId, request.date(), request.deltaMl()));
    }

    private void validateNotFuture(LocalDate date) {
        if (date.isAfter(LocalDate.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha no puede ser futura.");
        }
    }
}
