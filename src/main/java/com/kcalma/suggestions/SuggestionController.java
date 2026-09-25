package com.kcalma.suggestions;

import com.kcalma.food.MealType;
import com.kcalma.suggestions.dto.SuggestionRequest;
import com.kcalma.suggestions.dto.SuggestionResponse;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
@RequestMapping("/api")
public class SuggestionController {

    private static final int MAX_PREFERENCES_LENGTH = 300;

    private final SuggestionService suggestionService;

    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @PostMapping("/suggestions")
    public ResponseEntity<SuggestionResponse> suggest(
            @AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) SuggestionRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        LocalDate date = validateDate(request == null ? null : request.date());
        MealType mealType = validateMealType(request == null ? null : request.mealType());
        String preferences = validatePreferences(request == null ? null : request.preferences());

        return suggestionService
                .suggest(userId, date, mealType, preferences)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private LocalDate validateDate(String date) {
        if (date == null || date.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta la fecha.");
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha no es válida.");
        }
    }

    private MealType validateMealType(String mealType) {
        if (mealType == null || mealType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el tipo de comida.");
        }
        try {
            return MealType.valueOf(mealType);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tipo de comida no es válido.");
        }
    }

    /** Optional field: blank/missing collapses to {@code null} (no preferences), never a 400. */
    private String validatePreferences(String preferences) {
        if (preferences == null) {
            return null;
        }
        String trimmed = preferences.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_PREFERENCES_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Las preferencias no pueden superar los " + MAX_PREFERENCES_LENGTH + " caracteres.");
        }
        return trimmed;
    }
}
