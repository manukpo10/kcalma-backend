package com.kcalma.food.reference;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodReferenceRepository extends JpaRepository<FoodReference, Long> {

    /**
     * Best trigram match for {@code query} (an already-normalized English canonical name) at or
     * above {@code threshold}, preferring Foundation Foods/SR Legacy over FNDDS survey foods, and
     * preferring a "raw" description unless {@code preferRaw} is false (the caller sets this to
     * {@code false} when the item's own canonical name already names a cooking method — see
     * {@link NameNormalizer#mentionsCookingMethod}).
     *
     * <p>{@code extensions.similarity}/{@code extensions.gin_trgm_ops} are qualified explicitly
     * (pg_trgm lives in the {@code extensions} schema on Supabase, never on the app's own session
     * search_path — see V4__enable_pg_trgm_and_food_reference.sql).
     */
    @Query(
            value =
                    """
                    SELECT * FROM app.food_reference
                    WHERE extensions.similarity(search_name, :query) >= :threshold
                    ORDER BY
                        extensions.similarity(search_name, :query) DESC,
                        CASE data_type
                            WHEN 'foundation_food' THEN 0
                            WHEN 'sr_legacy_food' THEN 1
                            ELSE 2
                        END,
                        CASE
                            WHEN :preferRaw AND description ILIKE '%, raw%' THEN 0
                            WHEN NOT :preferRaw AND description NOT ILIKE '%, raw%' THEN 0
                            ELSE 1
                        END
                    LIMIT 1
                    """,
            nativeQuery = true)
    Optional<FoodReference> findBestMatch(
            @Param("query") String query, @Param("threshold") double threshold, @Param("preferRaw") boolean preferRaw);
}
