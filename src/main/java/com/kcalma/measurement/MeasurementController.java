package com.kcalma.measurement;

import com.kcalma.measurement.dto.MeasurementResponse;
import com.kcalma.measurement.dto.UpsertMeasurementRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/measurements")
public class MeasurementController {

    private final MeasurementService service;
    private final Clock clock;

    public MeasurementController(MeasurementService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PutMapping("/{date}")
    public ResponseEntity<MeasurementResponse> upsert(
            @AuthenticationPrincipal Jwt jwt, @PathVariable LocalDate date, @Valid @RequestBody UpsertMeasurementRequest request) {
        validateHasAnyValue(request);
        validateNotFuture(date);
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(service.upsert(userId, date, request));
    }

    @GetMapping
    public ResponseEntity<List<MeasurementResponse>> list(
            @AuthenticationPrincipal Jwt jwt, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        UUID userId = UUID.fromString(jwt.getSubject());
        validateRange(from, to);
        return ResponseEntity.ok(service.findRange(userId, from, to));
    }

    @DeleteMapping("/{date}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable LocalDate date) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return service.delete(userId, date) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private void validateHasAnyValue(UpsertMeasurementRequest request) {
        if (!request.hasAnyValue()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresá al menos una medida.");
        }
    }

    private void validateNotFuture(LocalDate date) {
        if (date.isAfter(LocalDate.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha no puede ser futura.");
        }
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de inicio no puede ser posterior a la de fin.");
        }
    }
}
