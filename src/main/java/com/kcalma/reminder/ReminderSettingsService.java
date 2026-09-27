package com.kcalma.reminder;

import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** {@code GET}/{@code PUT /api/reminders} — see {@link ReminderSettingsData} for the persisted shape. */
@Service
public class ReminderSettingsService {

    private static final Set<String> REQUIRED_MEAL_KEYS = Set.copyOf(ReminderSettingsData.REMINDABLE_MEALS);

    private final ReminderSettingsRepository repository;

    public ReminderSettingsService(ReminderSettingsRepository repository) {
        this.repository = repository;
    }

    /** Never persists a row just for a read — an absent row and a saved "everything off" row mean the same thing. */
    @Transactional(readOnly = true)
    public ReminderSettingsData get(UUID userId) {
        return repository.findById(userId).map(ReminderSettings::getSettings).orElseGet(ReminderSettingsData::defaults);
    }

    @Transactional
    public ReminderSettingsData update(UUID userId, ReminderSettingsData data) {
        validate(data);
        ReminderSettings entity = repository.findById(userId).orElseGet(() -> new ReminderSettings(userId, data));
        entity.setSettings(data);
        repository.save(entity);
        return data;
    }

    /**
     * Structural per-field format ({@code @Pattern} on {@code dto.ReminderSettingsRequest}) is
     * already enforced before this runs — what's left is the one rule that can't be expressed
     * declaratively on a {@code Map}'s key set: {@code meals} must have exactly the four reminder-
     * eligible meal types, no more, no fewer.
     */
    private void validate(ReminderSettingsData data) {
        if (!data.meals().keySet().equals(REQUIRED_MEAL_KEYS)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "meals debe tener exactamente las claves " + String.join(", ", ReminderSettingsData.REMINDABLE_MEALS) + ".");
        }
    }
}
