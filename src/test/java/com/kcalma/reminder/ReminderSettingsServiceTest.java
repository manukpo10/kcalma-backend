package com.kcalma.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReminderSettingsServiceTest {

    @Mock
    private ReminderSettingsRepository repository;

    @InjectMocks
    private ReminderSettingsService service;

    @Test
    void get_noRowForTheUser_returnsDefaultsWithoutPersistingAnything() {
        UUID userId = UUID.randomUUID();
        when(repository.findById(userId)).thenReturn(Optional.empty());

        ReminderSettingsData result = service.get(userId);

        assertThat(result).isEqualTo(ReminderSettingsData.defaults());
        verify(repository, never()).save(any());
    }

    @Test
    void get_existingRow_returnsItsPersistedSettingsInsteadOfDefaults() {
        UUID userId = UUID.randomUUID();
        ReminderSettingsData persisted = new ReminderSettingsData(
                true, ReminderSettingsData.defaults().meals(), ReminderSettingsData.defaults().water(), ReminderSettingsData.defaults().weighIn());
        when(repository.findById(userId)).thenReturn(Optional.of(new ReminderSettings(userId, persisted)));

        assertThat(service.get(userId)).isEqualTo(persisted);
    }

    @Test
    void update_validData_persistsItAndReturnsItUnchanged() {
        UUID userId = UUID.randomUUID();
        when(repository.findById(userId)).thenReturn(Optional.empty());
        ReminderSettingsData data = ReminderSettingsData.defaults();

        ReminderSettingsData result = service.update(userId, data);

        assertThat(result).isEqualTo(data);
        ArgumentCaptor<ReminderSettings> captor = ArgumentCaptor.forClass(ReminderSettings.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getSettings()).isEqualTo(data);
    }

    @Test
    void update_existingRow_overwritesItsSettingsRatherThanCreatingASecondRow() {
        UUID userId = UUID.randomUUID();
        ReminderSettings existing = new ReminderSettings(userId, ReminderSettingsData.defaults());
        when(repository.findById(userId)).thenReturn(Optional.of(existing));
        ReminderSettingsData updatedData = new ReminderSettingsData(
                true, ReminderSettingsData.defaults().meals(), ReminderSettingsData.defaults().water(), ReminderSettingsData.defaults().weighIn());

        service.update(userId, updatedData);

        verify(repository).save(existing);
        assertThat(existing.getSettings()).isEqualTo(updatedData);
    }

    @Test
    void update_mealsMissingARequiredKey_rejectsWith400BeforeTouchingTheRepository() {
        UUID userId = UUID.randomUUID();
        Map<String, ReminderSettingsData.MealSetting> incompleteMeals = new LinkedHashMap<>(ReminderSettingsData.defaults().meals());
        incompleteMeals.remove("CENA");
        ReminderSettingsData invalid =
                new ReminderSettingsData(true, incompleteMeals, ReminderSettingsData.defaults().water(), ReminderSettingsData.defaults().weighIn());

        assertThatThrownBy(() -> service.update(userId, invalid))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
        verifyNoInteractions(repository);
    }

    @Test
    void update_mealsWithAnExtraUnknownKey_rejectsWith400() {
        UUID userId = UUID.randomUUID();
        Map<String, ReminderSettingsData.MealSetting> extraMeals = new LinkedHashMap<>(ReminderSettingsData.defaults().meals());
        extraMeals.put("SNACK", new ReminderSettingsData.MealSetting(true, "11:00"));
        ReminderSettingsData invalid =
                new ReminderSettingsData(true, extraMeals, ReminderSettingsData.defaults().water(), ReminderSettingsData.defaults().weighIn());

        assertThatThrownBy(() -> service.update(userId, invalid)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }
}
