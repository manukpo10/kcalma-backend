package com.kcalma.reminder;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderSettingsRepository extends JpaRepository<ReminderSettings, UUID> {
}
