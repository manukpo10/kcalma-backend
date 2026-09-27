package com.kcalma.account;

import com.kcalma.checkin.TdeeCheckinRepository;
import com.kcalma.favorites.FavoriteDishRepository;
import com.kcalma.food.FoodEntryRepository;
import com.kcalma.food.reference.UserFoodRepository;
import com.kcalma.measurement.BodyMeasurementRepository;
import com.kcalma.profile.UserProfileRepository;
import com.kcalma.push.PushSubscriptionService;
import com.kcalma.reminder.ReminderLogRepository;
import com.kcalma.reminder.ReminderSettingsRepository;
import com.kcalma.water.WaterLogRepository;
import com.kcalma.weight.WeightEntryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Powers {@code DELETE /api/account}: permanently erases every one of the caller's own rows —
 * across all 11 per-user tables (see each field's table below) plus, when it exists, their
 * Supabase {@code auth.users} row (see {@link AuthUserGateway}) — in a single transaction, so a
 * failure partway through leaves nothing deleted rather than an inconsistent half-deleted account.
 *
 * <p>Every repository call here is itself a bulk {@code DELETE ... WHERE user_id = :userId} (or,
 * for the two tables keyed directly by {@code user_id}, a scoped {@code findById}/{@code delete}),
 * never a raw id — so this can never touch another user's data, and calling it twice (or on a user
 * with no data at all) is a harmless no-op the second time.
 */
@Service
public class AccountService {

    private final UserProfileRepository userProfileRepository;
    private final FoodEntryRepository foodEntryRepository;
    private final WeightEntryRepository weightEntryRepository;
    private final UserFoodRepository userFoodRepository;
    private final FavoriteDishRepository favoriteDishRepository;
    private final WaterLogRepository waterLogRepository;
    private final BodyMeasurementRepository bodyMeasurementRepository;
    private final TdeeCheckinRepository tdeeCheckinRepository;
    private final PushSubscriptionService pushSubscriptionService;
    private final ReminderSettingsRepository reminderSettingsRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final AuthUserGateway authUserGateway;

    public AccountService(
            UserProfileRepository userProfileRepository,
            FoodEntryRepository foodEntryRepository,
            WeightEntryRepository weightEntryRepository,
            UserFoodRepository userFoodRepository,
            FavoriteDishRepository favoriteDishRepository,
            WaterLogRepository waterLogRepository,
            BodyMeasurementRepository bodyMeasurementRepository,
            TdeeCheckinRepository tdeeCheckinRepository,
            PushSubscriptionService pushSubscriptionService,
            ReminderSettingsRepository reminderSettingsRepository,
            ReminderLogRepository reminderLogRepository,
            AuthUserGateway authUserGateway) {
        this.userProfileRepository = userProfileRepository;
        this.foodEntryRepository = foodEntryRepository;
        this.weightEntryRepository = weightEntryRepository;
        this.userFoodRepository = userFoodRepository;
        this.favoriteDishRepository = favoriteDishRepository;
        this.waterLogRepository = waterLogRepository;
        this.bodyMeasurementRepository = bodyMeasurementRepository;
        this.tdeeCheckinRepository = tdeeCheckinRepository;
        this.pushSubscriptionService = pushSubscriptionService;
        this.reminderSettingsRepository = reminderSettingsRepository;
        this.reminderLogRepository = reminderLogRepository;
        this.authUserGateway = authUserGateway;
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        // app.reminder_log, app.reminder_settings
        reminderLogRepository.deleteByUserId(userId);
        reminderSettingsRepository.findById(userId).ifPresent(reminderSettingsRepository::delete);
        // app.push_subscription
        pushSubscriptionService.deleteAllForUser(userId);
        // app.tdee_checkin
        tdeeCheckinRepository.deleteByUserId(userId);
        // app.body_measurement
        bodyMeasurementRepository.deleteByUserId(userId);
        // app.water_log
        waterLogRepository.deleteByUserId(userId);
        // app.favorite_dish
        favoriteDishRepository.deleteByUserId(userId);
        // app.user_food
        userFoodRepository.deleteByUserId(userId);
        // app.weight_entry
        weightEntryRepository.deleteByUserId(userId);
        // app.food_entry
        foodEntryRepository.deleteByUserId(userId);
        // app.user_profile
        userProfileRepository.findById(userId).ifPresent(userProfileRepository::delete);
        // auth.users (Supabase only -- skipped with a log line where it doesn't exist)
        authUserGateway.deleteIfPresent(userId);
    }
}
