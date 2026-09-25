package com.kcalma.food;

import com.kcalma.food.ImageFormatDetector.ImageFormat;
import com.kcalma.food.analysis.FoodAnalysisResult;
import com.kcalma.food.analysis.FoodPhotoAnalyzer;
import com.kcalma.food.dto.FoodAnalysisResponse;
import com.kcalma.food.dto.FoodEntryResponse;
import com.kcalma.food.dto.SaveFoodEntriesRequest;
import com.kcalma.food.dto.UpdateFoodEntryRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
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

    private final FoodPhotoAnalyzer analyzer;
    private final FoodEntryService entryService;

    public FoodController(FoodPhotoAnalyzer analyzer, FoodEntryService entryService) {
        this.analyzer = analyzer;
        this.entryService = entryService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FoodAnalysisResponse> analyze(@RequestParam("image") MultipartFile image) {
        validatePresenceAndSize(image);
        byte[] imageBytes = readBytes(image);
        ImageFormat format = detectFormat(imageBytes);
        FoodAnalysisResult result = analyzer.analyze(imageBytes, format.mimeType());
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

    private void validatePresenceAndSize(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta la imagen.");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "La imagen no puede superar los 6 MB.");
        }
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
