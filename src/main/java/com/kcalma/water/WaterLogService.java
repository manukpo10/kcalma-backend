package com.kcalma.water;

import com.kcalma.profile.ProfileService;
import com.kcalma.water.dto.WaterResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaterLogService {

    private final WaterLogRepository repository;
    private final ProfileService profileService;

    public WaterLogService(WaterLogRepository repository, ProfileService profileService) {
        this.repository = repository;
        this.profileService = profileService;
    }

    /**
     * Applies a signed delta to the day's running total, floored at 0 — the day's total can never
     * go negative even if the client sends more "undo" deltas than there was water logged.
     */
    @Transactional
    public WaterResponse applyDelta(UUID userId, LocalDate date, int deltaMl) {
        WaterLog log = repository.findByUserIdAndEntryDate(userId, date).orElseGet(() -> new WaterLog(userId, date, 0));
        int newTotal = Math.max(0, log.getMl() + deltaMl);
        log.setMl(newTotal);
        WaterLog saved = repository.save(log);
        return new WaterResponse(date, saved.getMl(), targetMlFor(userId));
    }

    /** Used by {@code com.kcalma.day.DayService} to compose GET /api/day's {@code water} block. */
    @Transactional(readOnly = true)
    public int consumedMl(UUID userId, LocalDate date) {
        return repository.findByUserIdAndEntryDate(userId, date).map(WaterLog::getMl).orElse(0);
    }

    /** Same 35 ml/kg target {@code NutritionCalculator} already derives for the profile — no profile yet means no target (0), not an error. */
    private int targetMlFor(UUID userId) {
        return profileService.findByUserId(userId).map(profile -> profile.targets().waterMl()).orElse(0);
    }
}
