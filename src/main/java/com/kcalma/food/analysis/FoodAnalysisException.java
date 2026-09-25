package com.kcalma.food.analysis;

/** The photo-analysis provider failed, rate-limited, or returned something unusable. Message is user-facing Spanish. */
public class FoodAnalysisException extends RuntimeException {

    public FoodAnalysisException(String message) {
        super(message);
    }

    public FoodAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
