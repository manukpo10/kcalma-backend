package com.kcalma.profile;

import com.kcalma.profile.dto.ProfileRequest;
import com.kcalma.profile.dto.ProfileWithTargetsResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<ProfileWithTargetsResponse> get(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return profileService.findByUserId(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping
    public ResponseEntity<ProfileWithTargetsResponse> put(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest request) {
        validatePace(request);
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(profileService.upsert(userId, request));
    }

    /**
     * {@code pace} is required for every goal except RECOMP/MAINTAIN (see {@link
     * Goal#requiresPace()}) -- a cross-field rule, so it can't be a plain Bean Validation annotation
     * on {@link ProfileRequest}. The opposite case (a stray {@code pace} sent for RECOMP/MAINTAIN) is
     * not an error: {@code ProfileService} just ignores it.
     */
    private void validatePace(ProfileRequest request) {
        if (request.goal().requiresPace() && request.pace() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ritmo (pace) es obligatorio para este objetivo.");
        }
    }
}
