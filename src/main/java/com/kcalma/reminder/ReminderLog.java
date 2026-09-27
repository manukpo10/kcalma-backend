package com.kcalma.reminder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Maps to app.reminder_log (V15__push_and_reminders.sql) — a read-only view onto rows {@link
 * ReminderLogRepository#claim} inserts; nothing in the app ever updates or deletes one. See that
 * repository's javadoc for the dedupe mechanics this entity's composite key exists to support.
 */
@Entity
@Table(name = "reminder_log")
@IdClass(ReminderLogId.class)
public class ReminderLog {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Id
    @Column(name = "reminder_key", updatable = false, nullable = false, length = 40)
    private String reminderKey;

    @Id
    @Column(name = "sent_on", updatable = false, nullable = false)
    private LocalDate sentOn;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private OffsetDateTime sentAt;

    protected ReminderLog() {
        // JPA
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

    public OffsetDateTime getSentAt() {
        return sentAt;
    }
}
