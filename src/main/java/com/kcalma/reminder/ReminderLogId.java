package com.kcalma.reminder;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code @IdClass} for {@link ReminderLog}: same reasoning as {@code com.kcalma.water.WaterLogId}
 * — a plain class (not a record) because JPA's {@code @IdClass} contract wants a public no-arg
 * constructor.
 */
public class ReminderLogId implements Serializable {

    private UUID userId;
    private String reminderKey;
    private LocalDate sentOn;

    public ReminderLogId() {
        // JPA
    }

    public ReminderLogId(UUID userId, String reminderKey, LocalDate sentOn) {
        this.userId = userId;
        this.reminderKey = reminderKey;
        this.sentOn = sentOn;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getReminderKey() {
        return reminderKey;
    }

    public LocalDate getSentOn() {
        return sentOn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReminderLogId other)) {
            return false;
        }
        return Objects.equals(userId, other.userId) && Objects.equals(reminderKey, other.reminderKey) && Objects.equals(sentOn, other.sentOn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, reminderKey, sentOn);
    }
}
