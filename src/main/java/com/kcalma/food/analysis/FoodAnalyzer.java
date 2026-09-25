package com.kcalma.food.analysis;

/** Port: identifies food items and their estimated nutrition from a plate photo or a text description. */
public interface FoodAnalyzer {

    /**
     * @param imageBytes raw image bytes (JPEG/PNG/WebP)
     * @param mimeType the image's content type, e.g. "image/jpeg"
     * @throws FoodAnalysisException if the provider is unavailable, rate-limited, or returns
     *     something that cannot be interpreted
     */
    FoodAnalysisResult analyzePhoto(byte[] imageBytes, String mimeType);

    /**
     * @param description free-text description of a meal, in Spanish (e.g. "2 empanadas de carne
     *     y una ensalada chica")
     * @throws FoodAnalysisException if the provider is unavailable, rate-limited, or returns
     *     something that cannot be interpreted
     */
    FoodAnalysisResult analyzeDescription(String description);
}
