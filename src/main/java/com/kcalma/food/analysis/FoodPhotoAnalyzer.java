package com.kcalma.food.analysis;

/** Port: identifies food items and their estimated nutrition from a plate photo. */
public interface FoodPhotoAnalyzer {

    /**
     * @param imageBytes raw image bytes (JPEG/PNG/WebP)
     * @param mimeType the image's content type, e.g. "image/jpeg"
     * @throws FoodAnalysisException if the provider is unavailable, rate-limited, or returns
     *     something that cannot be interpreted
     */
    FoodAnalysisResult analyze(byte[] imageBytes, String mimeType);
}
