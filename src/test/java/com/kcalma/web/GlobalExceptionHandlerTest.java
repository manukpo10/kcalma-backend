package com.kcalma.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.kcalma.ratelimit.RateLimitExceededException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Direct unit test (no Spring context — every handler method here is a pure function of its
 * exception argument) for {@link GlobalExceptionHandler}'s status codes, headers and Spanish
 * messages. {@link MethodArgumentNotValidException}'s {@code fieldErrors} shape is covered
 * end-to-end instead via a real {@code @Valid} request in {@code FoodControllerEntryValidationTest},
 * since it needs a real {@code BindingResult} that's more realistic to produce through MockMvc
 * than to hand-construct here.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleResponseStatus_preservesStatusAndReason() {
        ResponseStatusException ex =
                new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "La imagen no puede superar los 6 MB.");

        ResponseEntity<Map<String, String>> response = handler.handleResponseStatus(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).containsEntry("message", "La imagen no puede superar los 6 MB.");
    }

    @Test
    void handleResponseStatus_withNoReason_fallsBackToGenericMessage() {
        ResponseEntity<Map<String, String>> response = handler.handleResponseStatus(new ResponseStatusException(HttpStatus.BAD_REQUEST));

        assertThat(response.getBody()).containsEntry("message", "La solicitud no es válida.");
    }

    @Test
    void handleRateLimit_returns429WithRetryAfterHeaderAndSpanishMessage() {
        ResponseEntity<Map<String, String>> response = handler.handleRateLimit(new RateLimitExceededException(42));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("42");
        assertThat(response.getBody())
                .containsEntry("message", "Llegaste al límite de análisis por ahora. Probá de nuevo en unos minutos.");
    }

    @Test
    void handleMissingParam_returns400WithGenericSpanishMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.handleMissingParam(new MissingServletRequestParameterException("date", "LocalDate"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "Falta un parámetro obligatorio en la solicitud.");
    }

    @Test
    void handleMethodNotAllowed_returns405WithGenericSpanishMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.handleMethodNotAllowed(new HttpRequestMethodNotSupportedException("PATCH"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody())
                .containsEntry("message", "El método HTTP utilizado no está permitido para este recurso.");
    }

    @Test
    void handleUnexpected_returns500WithGenericMessageAndNeverLeaksTheRealOne() {
        RuntimeException ex = new RuntimeException("credenciales invalidas: password=supersecreto");

        ResponseEntity<Map<String, String>> response = handler.handleUnexpected(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("message", "Ocurrió un error inesperado.");
        assertThat(response.getBody().values()).noneMatch(v -> v.contains("supersecreto"));
    }
}
