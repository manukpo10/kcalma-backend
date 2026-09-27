package com.kcalma.favorites;

import com.kcalma.favorites.dto.CreateFavoriteRequest;
import com.kcalma.favorites.dto.FavoriteResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteDishService service;

    public FavoriteController(FavoriteDishService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<FavoriteResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(service.findAll(userId));
    }

    @PostMapping
    public ResponseEntity<FavoriteResponse> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateFavoriteRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upsert(userId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return service.delete(userId, id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
