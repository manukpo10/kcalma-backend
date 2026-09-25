package com.kcalma.food.analysis;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates provider failures into a friendly 502 instead of a bare 500. */
@RestControllerAdvice
public class FoodAnalysisExceptionHandler {

    @ExceptionHandler(FoodAnalysisException.class)
    public ResponseEntity<Map<String, String>> handle(FoodAnalysisException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", ex.getMessage()));
    }
}
