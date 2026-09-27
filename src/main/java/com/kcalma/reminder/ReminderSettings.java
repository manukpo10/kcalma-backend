package com.kcalma.reminder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Maps to app.reminder_settings (V15__push_and_reminders.sql): one row per user, only once they've
 * called {@code PUT /api/reminders} at least once (see {@code ReminderSettingsService#get} for how
 * an absent row and a persisted "everything off" row both just mean "nothing to send").
 */
@Entity
@Table(name = "reminder_settings")
public class ReminderSettings {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", nullable = false)
    private ReminderSettingsData settings;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ReminderSettings() {
        // JPA
    }

    public ReminderSettings(UUID userId, ReminderSettingsData settings) {
        this.userId = userId;
        this.settings = settings;
    }

    public UUID getUserId() {
        return userId;
    }

    public ReminderSettingsData getSettings() {
        return settings;
    }

    public void setSettings(ReminderSettingsData settings) {
        this.settings = settings;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
