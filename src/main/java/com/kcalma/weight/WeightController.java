package com.kcalma.weight;

import com.kcalma.weight.dto.UpsertWeightRequest;
import com.kcalma.weight.dto.UpsertWeightResponse;
import com.kcalma.weight.dto.WeightEntryResponse;
import jakarta.validation.Valid;
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
@RequestMapping("/api/weights")
public class WeightController {

    private final WeightEntryService entryService;

    public WeightController(WeightEntryService entryService) {
        this.entryService = entryService;
    }

    @PutMapping("/{date}")
    public ResponseEntity<UpsertWeightResponse> upsert(
            @AuthenticationPrincipal Jwt jwt, @PathVariable LocalDate date, @Valid @RequestBody UpsertWeightRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        validateNotFuture(date);
        return ResponseEntity.ok(entryService.upsert(userId, date, request));
    }

    @DeleteMapping("/{date}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable LocalDate date) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return entryService.delete(userId, date) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping
    public ResponseEntity<List<WeightEntryResponse>> list(
            @AuthenticationPrincipal Jwt jwt, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        UUID userId = UUID.fromString(jwt.getSubject());
        validateRange(from, to);
        return ResponseEntity.ok(entryService.findRange(userId, from, to));
    }

    private void validateNotFuture(LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha no puede ser futura.");
        }
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de inicio no puede ser posterior a la de fin.");
        }
    }
}
