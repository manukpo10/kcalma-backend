package com.kcalma.web;

import com.kcalma.ratelimit.RateLimitExceededException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single place every controller's exception funnels through, so the frontend (see
 * {@code kcalma-frontend/src/lib/api.ts}'s {@code friendlyErrorMessage}) can always read a Spanish
 * {@code message} field, regardless of which layer raised the error. {@code server.error.include-message:
 * never}/{@code include-binding-errors: never} (application.yml) back this up: if something ever
 * reaches Boot's own {@code /error} page instead of a handler here, it won't leak raw exception text.
 *
 * <p>{@link com.kcalma.food.analysis.FoodAnalysisExceptionHandler} stays separate and keeps
 * translating {@code FoodAnalysisException} to a 502 — it's a distinct, already-tested concern.
 *
 * <p>Every {@code @ExceptionHandler} here corresponds to something this API can actually throw:
 * bean-validation failures, the {@code ResponseStatusException}s controllers raise directly for
 * friendly domain errors (400s, 404-never-here, 413, 415), the new rate limiter's 429, and a small
 * set of standard Spring MVC exceptions that must keep returning their existing 4xx status instead
 * of falling into the generic 500 fallback below. Anything else — a real bug — hits {@link
 * #handleUnexpected} and is logged server-side with a request id, never with its raw message shown
 * to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String VALIDATION_MESSAGE = "Revisá los datos ingresados.";
    private static final String BAD_REQUEST_MESSAGE = "La solicitud no es válida.";
    private static final String UNSUPPORTED_MEDIA_TYPE_MESSAGE = "El tipo de contenido de la solicitud no es compatible.";
    private static final String METHOD_NOT_ALLOWED_MESSAGE = "El método HTTP utilizado no está permitido para este recurso.";
    private static final String RATE_LIMIT_MESSAGE = "Llegaste al límite de análisis por ahora. Probá de nuevo en unos minutos.";
    private static final String UNEXPECTED_MESSAGE = "Ocurrió un error inesperado.";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", VALIDATION_MESSAGE);
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /** Preserves the status and Spanish reason of every {@code ResponseStatusException} thrown across the app. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : BAD_REQUEST_MESSAGE;
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", message));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(Map.of("message", RATE_LIMIT_MESSAGE));
    }

    /** Malformed/unreadable JSON body on any {@code @RequestBody} — never echoes the parser's own message. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "El cuerpo de la solicitud no es válido."));
    }

    /** A required {@code @RequestParam} (e.g. the GET endpoints' {@code date}/{@code range}) was omitted. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "Falta un parámetro obligatorio en la solicitud."));
    }

    /** A {@code @RequestParam}/{@code @PathVariable} couldn't convert (e.g. a non-UUID id, a malformed date). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Uno de los parámetros de la solicitud no tiene un formato válido."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Map.of("message", UNSUPPORTED_MEDIA_TYPE_MESSAGE));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of("message", METHOD_NOT_ALLOWED_MESSAGE));
    }

    /** Last resort: anything not explicitly mapped above is a bug, not a user mistake — never shown to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        String requestId = UUID.randomUUID().toString();
        log.error("Unexpected error [requestId={}]", requestId, ex);
        return ResponseEntity.internalServerError().body(Map.of("message", UNEXPECTED_MESSAGE));
    }
}
