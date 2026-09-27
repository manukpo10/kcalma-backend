package com.kcalma.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kcalma.checkin.TdeeCheckinRepository;
import com.kcalma.favorites.FavoriteDishRepository;
import com.kcalma.food.FoodEntryRepository;
import com.kcalma.food.reference.UserFoodRepository;
import com.kcalma.measurement.BodyMeasurementRepository;
import com.kcalma.profile.UserProfileRepository;
import com.kcalma.push.PushSubscriptionService;
import com.kcalma.reminder.ReminderLogRepository;
import com.kcalma.reminder.ReminderSettings;
import com.kcalma.reminder.ReminderSettingsData;
import com.kcalma.reminder.ReminderSettingsRepository;
import com.kcalma.water.WaterLogRepository;
import com.kcalma.weight.WeightEntryRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link AccountService}'s orchestration: every collaborator is mocked, so this
 * proves WHICH calls {@code deleteAccount} makes (every one of the 11 tables, plus the Supabase
 * auth-user bridge), not that a real database actually deletes rows — see {@code
 * com.kcalma.integration.DatabaseIntegrationTest} for that end-to-end proof.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private FoodEntryRepository foodEntryRepository;

    @Mock
    private WeightEntryRepository weightEntryRepository;

    @Mock
    private UserFoodRepository userFoodRepository;

    @Mock
    private FavoriteDishRepository favoriteDishRepository;

    @Mock
    private WaterLogRepository waterLogRepository;

    @Mock
    private BodyMeasurementRepository bodyMeasurementRepository;

    @Mock
    private TdeeCheckinRepository tdeeCheckinRepository;

    @Mock
    private PushSubscriptionService pushSubscriptionService;

    @Mock
    private ReminderSettingsRepository reminderSettingsRepository;

    @Mock
    private ReminderLogRepository reminderLogRepository;

    @Mock
    private AuthUserGateway authUserGateway;

    @InjectMocks
    private AccountService accountService;

    @Test
    void deleteAccount_bulkDeletesEveryMultiRowTableForExactlyThatUser() {
        accountService.deleteAccount(USER_ID);

        verify(reminderLogRepository).deleteByUserId(USER_ID);
        verify(pushSubscriptionService).deleteAllForUser(USER_ID);
        verify(tdeeCheckinRepository).deleteByUserId(USER_ID);
        verify(bodyMeasurementRepository).deleteByUserId(USER_ID);
        verify(waterLogRepository).deleteByUserId(USER_ID);
        verify(favoriteDishRepository).deleteByUserId(USER_ID);
        verify(userFoodRepository).deleteByUserId(USER_ID);
        verify(weightEntryRepository).deleteByUserId(USER_ID);
        verify(foodEntryRepository).deleteByUserId(USER_ID);
    }

    @Test
    void deleteAccount_singleRowTables_deletesOnlyWhenARowExists() {
        ReminderSettings existingSettings = new ReminderSettings(USER_ID, ReminderSettingsData.defaults());
        when(reminderSettingsRepository.findById(USER_ID)).thenReturn(Optional.of(existingSettings));
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        accountService.deleteAccount(USER_ID);

        verify(reminderSettingsRepository).delete(existingSettings);
        verify(userProfileRepository, never()).delete(any());
    }

    @Test
    void deleteAccount_alwaysAsksTheAuthUserGatewayToDeleteTheSupabaseUser() {
        accountService.deleteAccount(USER_ID);

        verify(authUserGateway).deleteIfPresent(USER_ID);
    }
}
