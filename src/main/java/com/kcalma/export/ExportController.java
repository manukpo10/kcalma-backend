package com.kcalma.export;

import com.kcalma.export.dto.ExportResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private static final MediaType APPLICATION_ZIP = MediaType.valueOf("application/zip");

    private final ExportService exportService;
    private final Clock clock;

    public ExportController(ExportService exportService, Clock clock) {
        this.exportService = exportService;
        this.clock = clock;
    }

    /**
     * {@code Object} (not {@code ExportResponse}/{@code byte[]} specifically): the body is either a
     * plain record, serialized to JSON the exact same way every other endpoint in this API already
     * is (no manually-injected {@code ObjectMapper} needed — Spring MVC picks the converter from the
     * runtime body type and the declared content type), or raw zip bytes.
     */
    @GetMapping
    public ResponseEntity<Object> export(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "json") String format) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String today = LocalDate.now(clock).toString();

        if (format.equalsIgnoreCase("csv")) {
            byte[] zip = exportService.buildCsvZip(userId);
            return respond(zip, APPLICATION_ZIP, "kcalma-export-" + today + ".zip");
        }
        if (!format.equalsIgnoreCase("json")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El formato debe ser 'json' o 'csv'.");
        }
        ExportResponse json = exportService.buildJson(userId);
        return respond(json, MediaType.APPLICATION_JSON, "kcalma-export-" + today + ".json");
    }

    private ResponseEntity<Object> respond(Object body, MediaType contentType, String filename) {
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
