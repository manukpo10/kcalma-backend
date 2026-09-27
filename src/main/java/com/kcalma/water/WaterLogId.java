package com.kcalma.water;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code @IdClass} for {@link WaterLog}: app.water_log's primary key is the composite (user_id,
 * entry_date) itself (V12__water_log.sql), not a surrogate id — there's exactly one row per user
 * per day and nothing else ever needs to address it. A plain class (not a record): JPA's
 * {@code @IdClass} contract wants a public no-arg constructor, which this gives it directly rather
 * than relying on record/Hibernate version-specific support for records as id classes.
 */
public class WaterLogId implements Serializable {

    private UUID userId;
    private LocalDate entryDate;

    public WaterLogId() {
        // JPA
    }

    public WaterLogId(UUID userId, LocalDate entryDate) {
        this.userId = userId;
        this.entryDate = entryDate;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WaterLogId other)) {
            return false;
        }
        return Objects.equals(userId, other.userId) && Objects.equals(entryDate, other.entryDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, entryDate);
    }
}
