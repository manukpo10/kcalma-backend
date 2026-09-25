package com.kcalma.food;

import com.kcalma.food.ImageFormatDetector.ImageFormat;
import com.kcalma.food.analysis.FoodAnalysisResult;
import com.kcalma.food.analysis.FoodAnalyzer;
import com.kcalma.food.dto.AnalyzeTextRequest;
import com.kcalma.food.dto.FoodAnalysisResponse;
import com.kcalma.food.dto.FoodEntryResponse;
import com.kcalma.food.dto.SaveFoodEntriesRequest;
import com.kcalma.food.dto.UpdateFoodEntryRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/food")
public class FoodController {

    private static final long MAX_IMAGE_BYTES = 6L * 1024 * 1024;
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private final FoodAnalyzer analyzer;
    private final FoodEntryService entryService;

    public FoodController(FoodAnalyzer analyzer, FoodEntryService entryService) {
        this.analyzer = analyzer;
        this.entryService = entryService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FoodAnalysisResponse> analyze(@RequestParam("image") MultipartFile image) {
        validatePresenceAndSize(image);
        byte[] imageBytes = readBytes(image);
        ImageFormat format = detectFormat(imageBytes);
        FoodAnalysisResult result = analyzer.analyzePhoto(imageBytes, format.mimeType());
        return ResponseEntity.ok(FoodAnalysisResponse.from(result));
    }

    @PostMapping("/analyze-text")
    public ResponseEntity<FoodAnalysisResponse> analyzeText(@RequestBody(required = false) AnalyzeTextRequest request) {
        String description = validateDescription(request == null ? null : request.description());
        FoodAnalysisResult result = analyzer.analyzeDescription(description);
        return ResponseEntity.ok(FoodAnalysisResponse.from(result));
    }

    @PostMapping("/entries")
    public ResponseEntity<List<FoodEntryResponse>> saveEntries(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SaveFoodEntriesRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(entryService.saveAll(userId, request.entries()));
    }

    @GetMapping("/entries")
    public ResponseEntity<List<FoodEntryResponse>> listEntries(
            @AuthenticationPrincipal Jwt jwt, @RequestParam LocalDate date) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(entryService.findByDate(userId, date));
    }

    @PatchMapping("/entries/{id}")
    public ResponseEntity<FoodEntryResponse> updateEntry(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody UpdateFoodEntryRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return entryService
                .update(userId, id, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/entries/{id}")
    public ResponseEntity<Void> deleteEntry(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return entryService.delete(userId, id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** Wipes an entire meal (e.g. every "Desayuno" item on one day) for the caller in one shot. */
    @DeleteMapping("/entries")
    public ResponseEntity<Void> deleteMealEntries(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String mealType) {
        UUID userId = UUID.fromString(jwt.getSubject());
        LocalDate parsedDate = validateDate(date);
        MealType parsedMealType = validateMealType(mealType);
        entryService.deleteAllByMeal(userId, parsedDate, parsedMealType);
        return ResponseEntity.noContent().build();
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

    private void validatePresenceAndSize(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta la imagen.");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "La imagen no puede superar los 6 MB.");
        }
    }

    private String validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contanos qué comiste.");
        }
        String trimmed = description.strip();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La descripción no puede superar los " + MAX_DESCRIPTION_LENGTH + " caracteres.");
        }
        return trimmed;
    }

    /**
     * Identifies the image format from its bytes instead of the client-supplied Content-Type
     * header, which is trivial to spoof (e.g. a script uploading arbitrary bytes tagged as
     * "image/jpeg"). The MIME type sent to the analyzer is derived from this detection, never
     * from the header.
     */
    private ImageFormat detectFormat(byte[] imageBytes) {
        return ImageFormatDetector.detect(imageBytes)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "El formato de la imagen no es compatible. Los formatos permitidos son JPEG, PNG, WebP y HEIC."));
    }

    private byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la imagen recibida.", e);
        }
    }
}
