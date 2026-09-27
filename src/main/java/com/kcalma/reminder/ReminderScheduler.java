package com.kcalma.reminder;

import com.kcalma.food.FoodEntry;
import com.kcalma.food.FoodEntryRepository;
import com.kcalma.food.MealType;
import com.kcalma.profile.ProfileService;
import com.kcalma.push.PushDispatchService;
import com.kcalma.push.PushPayload;
import com.kcalma.water.WaterLog;
import com.kcalma.water.WaterLogRepository;
import com.kcalma.weight.WeightEntryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every minute (zoned to {@code app.timezone} via the shared {@link Clock} — see {@code
 * com.kcalma.config.ClockConfig}): for each user with a {@code reminder_settings} row, ask {@link
 * DueReminderEvaluator} what's due, claim each due occurrence's {@code app.reminder_log} dedupe
 * slot, and only actually send (via {@link PushDispatchService}) when that claim wins — see {@link
 * ReminderLogRepository#claim}'s javadoc for why claim-then-send (never the other order) is what
 * makes double-sending impossible across restarts or overlapping ticks.
 *
 * <p>{@code kcalma.reminders.scheduler-enabled} (env {@code REMINDERS_SCHEDULER_ENABLED}, default
 * {@code true}) is an operational kill switch: when {@code false}, {@link #tick} returns
 * immediately and sends nothing, but every {@code /api/push/**}/{@code /api/reminders} endpoint
 * keeps working normally. Two concrete reasons this exists rather than just not deploying the
 * feature: (1) running a local instance against the PRODUCTION database (e.g. to verify something)
 * must never send real reminders to the real subscriber; (2) it's a one-env-var-away kill switch on
 * Render with no redeploy, for whenever the sending logic itself needs to be paused without pulling
 * the whole app down.
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final boolean schedulerEnabled;
    private final Clock clock;
    private final ReminderSettingsRepository settingsRepository;
    private final ReminderLogRepository logRepository;
    private final FoodEntryRepository foodEntryRepository;
    private final WaterLogRepository waterLogRepository;
    private final WeightEntryRepository weightEntryRepository;
    private final ProfileService profileService;
    private final DueReminderEvaluator evaluator;
    private final PushDispatchService dispatchService;

    public ReminderScheduler(
            @Value("${kcalma.reminders.scheduler-enabled:true}") boolean schedulerEnabled,
            Clock clock,
            ReminderSettingsRepository settingsRepository,
            ReminderLogRepository logRepository,
            FoodEntryRepository foodEntryRepository,
            WaterLogRepository waterLogRepository,
            WeightEntryRepository weightEntryRepository,
            ProfileService profileService,
            DueReminderEvaluator evaluator,
            PushDispatchService dispatchService) {
        this.schedulerEnabled = schedulerEnabled;
        this.clock = clock;
        this.settingsRepository = settingsRepository;
        this.logRepository = logRepository;
        this.foodEntryRepository = foodEntryRepository;
        this.waterLogRepository = waterLogRepository;
        this.weightEntryRepository = weightEntryRepository;
        this.profileService = profileService;
        this.evaluator = evaluator;
        this.dispatchService = dispatchService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        if (!schedulerEnabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        for (ReminderSettings row : settingsRepository.findAll()) {
            try {
                processUser(row.getUserId(), now, today, row.getSettings());
            } catch (RuntimeException e) {
                // One user's bad data/transient failure must never stop the rest of this tick.
                log.warn("Reminder tick failed for one user; continuing with the others.", e);
            }
        }
    }

    private void processUser(UUID userId, LocalDateTime now, LocalDate today, ReminderSettingsData settings) {
        DueReminderEvaluator.Signals signals = gatherSignals(userId, today);
        List<DueReminderEvaluator.Due> due = evaluator.evaluate(now, settings, signals);
        for (DueReminderEvaluator.Due reminder : due) {
            if (logRepository.claim(userId, reminder.reminderKey(), today) == 1) {
                dispatchService.sendToUser(userId, new PushPayload(reminder.title(), reminder.body(), reminder.url()));
            }
            // claim() == 0 means some earlier call (a previous tick, or an overlapping one) already
            // sent this exact occurrence -- correctly skip, not an error.
        }
    }

    private DueReminderEvaluator.Signals gatherSignals(UUID userId, LocalDate today) {
        Set<MealType> mealsLoggedToday = EnumSet.noneOf(MealType.class);
        for (FoodEntry entry : foodEntryRepository.findByUserIdAndEntryDateOrderByCreatedAtAsc(userId, today)) {
            mealsLoggedToday.add(entry.getMealType());
        }

        int waterMlToday = waterLogRepository.findByUserIdAndEntryDate(userId, today).map(WaterLog::getMl).orElse(0);
        Integer waterTargetMl = profileService.findByUserId(userId).map(p -> p.targets().waterMl()).orElse(null);
        boolean weighedInToday = weightEntryRepository.findByUserIdAndEntryDate(userId, today).isPresent();

        return new DueReminderEvaluator.Signals(mealsLoggedToday, waterMlToday, waterTargetMl, weighedInToday);
    }
}
