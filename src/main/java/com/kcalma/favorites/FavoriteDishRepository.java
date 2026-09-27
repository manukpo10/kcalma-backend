package com.kcalma.favorites;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteDishRepository extends JpaRepository<FavoriteDish, UUID> {

    /** Exact-name lookup driving the upsert-by-normalized-name-per-user rule (see {@code FavoriteDishService#upsert}). */
    Optional<FavoriteDish> findByUserIdAndNormalizedName(UUID userId, String normalizedName);

    /** Most-recently-favorited first — GET /api/favorites has no other ordering contract. */
    List<FavoriteDish> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Owner-scoped lookup: an id that exists but belongs to another user resolves empty, never a value. */
    Optional<FavoriteDish> findByIdAndUserId(UUID id, UUID userId);
}
