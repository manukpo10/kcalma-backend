package com.kcalma.checkin;

import com.kcalma.profile.ActivityLevel;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-feature read façade onto {@link TdeeCheckinRepository}, so {@code
 * com.kcalma.profile.ProfileService} can ask "is adaptive TDEE active for this user right now?"
 * without depending on the checkin feature's persistence details directly (package-by-feature:
 * cross-feature calls go service -&gt; service, never repo -&gt; repo).
 */
@Service
public class AdaptiveTdeeService {

    private final TdeeCheckinRepository repository;

    public AdaptiveTdeeService(TdeeCheckinRepository repository) {
        this.repository = repository;
    }

    /**
     * The active adaptive TDEE for a user, or empty when there is none — either because no week
     * has ever been accepted, or because the profile's activity level has changed since the most
     * recent acceptance ("changing activity level resets to FORMULA until the next accepted
     * check-in" — see {@link TdeeCheckin#getAppliedActivityLevel()}, snapshotted only on {@link
     * TdeeCheckin#accept}).
     *
     * @param currentActivityLevel the profile's activity level right now
     */
    @Transactional(readOnly = true)
    public Optional<ActiveAdaptiveTdee> findActive(UUID userId, ActivityLevel currentActivityLevel) {
        return repository
                .findFirstByUserIdAndStatusOrderByWeekStartDesc(userId, CheckinStatus.ACCEPTED)
                .filter(checkin -> checkin.getAppliedActivityLevel() == currentActivityLevel)
                .map(checkin -> new ActiveAdaptiveTdee(checkin.getAppliedTdee(), checkin.getWeekStart()));
    }

    /**
     * @param tdeeKcal the accepted adaptive TDEE, now standing in for the formula TDEE
     * @param since the accepted week's {@code weekStart} — see {@code
     *     NutritionTargetsResponse#adaptiveSince()}. Always the MOST RECENTLY accepted week
     *     currently backing targets, not necessarily the first time this user ever went adaptive —
     *     a fresh acceptance always moves it forward.
     */
    public record ActiveAdaptiveTdee(int tdeeKcal, LocalDate since) {}
}
