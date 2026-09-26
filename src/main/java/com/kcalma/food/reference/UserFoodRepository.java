package com.kcalma.food.reference;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserFoodRepository extends JpaRepository<UserFood, UUID> {

    /** Exact-name lookup — priority (1a) in {@link FoodReferenceMatcher}. */
    Optional<UserFood> findByUserIdAndNormalizedName(UUID userId, String normalizedName);

    /**
     * High-similarity fallback for the same user — priority (1b) in {@link FoodReferenceMatcher},
     * only consulted when the exact lookup above misses. See {@link FoodReferenceRepository} for
     * why {@code extensions.similarity} is qualified explicitly.
     */
    @Query(
            value =
                    """
                    SELECT * FROM app.user_food
                    WHERE user_id = :userId AND extensions.similarity(normalized_name, :query) >= :threshold
                    ORDER BY extensions.similarity(normalized_name, :query) DESC
                    LIMIT 1
                    """,
            nativeQuery = true)
    Optional<UserFood> findBestFuzzyMatch(
            @Param("userId") UUID userId, @Param("query") String query, @Param("threshold") double threshold);
}
