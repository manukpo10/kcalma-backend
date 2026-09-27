package com.kcalma.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kcalma.checkin.AdaptiveTdeeService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link ProfileService}'s age computation, with a {@link Clock} fixed to a
 * specific instant so the result never depends on the machine running the test or on wall-clock
 * drift. 23:30 in America/Argentina/Buenos_Aires is already 02:30 UTC the NEXT calendar day: a
 * birth date on that next day means Argentina's clock says the birthday hasn't happened yet this
 * year (age N), while a UTC clock at the exact same instant already says it has (age N+1).
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserProfileRepository repository;

    @Mock
    private AdaptiveTdeeService adaptiveTdeeService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void findByUserId_lateEveningInArgentinaTimezone_computesAgeFromArgentinaCalendarDayNotUtc() {
        Instant fixedInstant = LocalDateTime.of(2026, 9, 25, 23, 30)
                .atZone(ZoneId.of("America/Argentina/Buenos_Aires"))
                .toInstant();
        Clock argentinaClock = Clock.fixed(fixedInstant, ZoneId.of("America/Argentina/Buenos_Aires"));
        Clock utcClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
        // Birthday is the 26th: Argentina's calendar day at this instant is still the 25th (not
        // reached yet this year), while UTC's calendar day is already the 26th (reached today).
        UserProfile profile = profileWithBirthDate(LocalDate.of(1990, 9, 26));
        when(repository.findById(userId)).thenReturn(Optional.of(profile));

        int caloriesUsingArgentinaClock = new ProfileService(repository, adaptiveTdeeService, argentinaClock)
                .findByUserId(userId).orElseThrow().targets().calories();
        int caloriesUsingUtcClock = new ProfileService(repository, adaptiveTdeeService, utcClock)
                .findByUserId(userId).orElseThrow().targets().calories();

        // NutritionCalculator's BMR term is "- 5 * ageYears" (Mifflin-St Jeor) -- strictly lower
        // for one more year of age, and with this profile neither result hits the calorie floor.
        // Same real-world instant, different clock -> different age -> different (higher for
        // Argentina, whose "today" hasn't reached the birthday yet) calorie target.
        assertThat(caloriesUsingArgentinaClock).isGreaterThan(caloriesUsingUtcClock);
    }

    private static UserProfile profileWithBirthDate(LocalDate birthDate) {
        UserProfile profile = new UserProfile(UUID.randomUUID());
        profile.setSex(Sex.FEMALE);
        profile.setBirthDate(birthDate);
        profile.setHeightCm(165);
        profile.setWeightKg(new BigDecimal("72.00"));
        profile.setActivityLevel(ActivityLevel.SEDENTARY);
        profile.setGoal(Goal.MAINTAIN);
        return profile;
    }
}
