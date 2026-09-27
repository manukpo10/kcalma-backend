package com.kcalma.measurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kcalma.measurement.dto.MeasurementResponse;
import com.kcalma.measurement.dto.UpsertMeasurementRequest;
import com.kcalma.profile.ActivityLevel;
import com.kcalma.profile.Goal;
import com.kcalma.profile.Sex;
import com.kcalma.profile.UserProfile;
import com.kcalma.profile.UserProfileRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link MeasurementService}: the upsert/delete round trip and the body-fat -> profile propagation rule. */
@ExtendWith(MockitoExtension.class)
class MeasurementServiceTest {

    @Mock
    private BodyMeasurementRepository repository;

    @Mock
    private UserProfileRepository profileRepository;

    private final UUID userId = UUID.randomUUID();

    @Test
    void upsert_newRowWithBodyFatAndItsTheLatest_updatesProfileBodyFatAndMeasuredOn() {
        LocalDate date = LocalDate.of(2026, 9, 25);
        UpsertMeasurementRequest request = new UpsertMeasurementRequest(
                new BigDecimal("80"), null, null, null, null, new BigDecimal("18.5"), null);
        BodyMeasurement saved = new BodyMeasurement(userId, date);
        saved.setBodyFatPct(new BigDecimal("18.5"));
        UserProfile profile = profileFor(userId);

        when(repository.findByUserIdAndMeasuredOn(userId, date)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findFirstByUserIdAndBodyFatPctIsNotNullOrderByMeasuredOnDesc(userId)).thenReturn(Optional.of(saved));
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

        MeasurementResponse response = newService().upsert(userId, date, request);

        assertThat(response.bodyFatPct()).isEqualByComparingTo("18.5");
        assertThat(profile.getBodyFatPct()).isEqualByComparingTo("18.5");
        assertThat(profile.getBodyFatMeasuredOn()).isEqualTo(date);
    }

    @Test
    void upsert_bodyFatOnAnOlderDateThanAnExistingNewerReading_profileKeepsPointingAtTheNewerOne() {
        LocalDate olderDate = LocalDate.of(2026, 8, 1);
        UpsertMeasurementRequest request = new UpsertMeasurementRequest(null, null, null, null, null, new BigDecimal("22.0"), null);
        BodyMeasurement newerReading = new BodyMeasurement(userId, LocalDate.of(2026, 9, 20));
        newerReading.setBodyFatPct(new BigDecimal("19.0"));
        UserProfile profile = profileFor(userId);

        when(repository.findByUserIdAndMeasuredOn(userId, olderDate)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // The just-saved OLDER row is not the latest-with-body-fat -- the newer one still is.
        when(repository.findFirstByUserIdAndBodyFatPctIsNotNullOrderByMeasuredOnDesc(userId)).thenReturn(Optional.of(newerReading));
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

        newService().upsert(userId, olderDate, request);

        assertThat(profile.getBodyFatPct()).isEqualByComparingTo("19.0");
        assertThat(profile.getBodyFatMeasuredOn()).isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    void upsert_noBodyFatValueAnywhereYet_leavesProfileUntouched() {
        LocalDate date = LocalDate.of(2026, 9, 25);
        UpsertMeasurementRequest request = new UpsertMeasurementRequest(new BigDecimal("80"), null, null, null, null, null, null);
        when(repository.findByUserIdAndMeasuredOn(userId, date)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findFirstByUserIdAndBodyFatPctIsNotNullOrderByMeasuredOnDesc(userId)).thenReturn(Optional.empty());

        newService().upsert(userId, date, request);

        org.mockito.Mockito.verify(profileRepository, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void delete_theRowFeedingTheProfile_fallsBackToThePreviousOneThatHasBodyFat() {
        LocalDate latestDate = LocalDate.of(2026, 9, 25);
        BodyMeasurement latest = new BodyMeasurement(userId, latestDate);
        latest.setBodyFatPct(new BigDecimal("18.0"));
        BodyMeasurement previous = new BodyMeasurement(userId, LocalDate.of(2026, 8, 1));
        previous.setBodyFatPct(new BigDecimal("21.0"));
        UserProfile profile = profileFor(userId);

        when(repository.findByUserIdAndMeasuredOn(userId, latestDate)).thenReturn(Optional.of(latest));
        // After the delete, only the older row with body fat remains.
        when(repository.findFirstByUserIdAndBodyFatPctIsNotNullOrderByMeasuredOnDesc(userId)).thenReturn(Optional.of(previous));
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

        boolean deleted = newService().delete(userId, latestDate);

        assertThat(deleted).isTrue();
        org.mockito.Mockito.verify(repository).delete(latest);
        assertThat(profile.getBodyFatPct()).isEqualByComparingTo("21.0");
        assertThat(profile.getBodyFatMeasuredOn()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    void delete_theOnlyRowWithBodyFat_leavesTheProfileValueUntouchedInsteadOfClearingIt() {
        LocalDate date = LocalDate.of(2026, 9, 25);
        BodyMeasurement onlyOne = new BodyMeasurement(userId, date);
        onlyOne.setBodyFatPct(new BigDecimal("18.0"));
        UserProfile profile = profileFor(userId);
        profile.setBodyFatPct(new BigDecimal("18.0"));
        profile.setBodyFatMeasuredOn(date);

        when(repository.findByUserIdAndMeasuredOn(userId, date)).thenReturn(Optional.of(onlyOne));
        when(repository.findFirstByUserIdAndBodyFatPctIsNotNullOrderByMeasuredOnDesc(userId)).thenReturn(Optional.empty());

        boolean deleted = newService().delete(userId, date);

        assertThat(deleted).isTrue();
        // No fallback measurement exists -- the profile keeps whatever it already had, untouched.
        org.mockito.Mockito.verify(profileRepository, org.mockito.Mockito.never()).findById(any());
        assertThat(profile.getBodyFatPct()).isEqualByComparingTo("18.0");
    }

    @Test
    void delete_missingRow_returnsFalseWithoutTouchingTheProfile() {
        LocalDate date = LocalDate.of(2026, 9, 25);
        when(repository.findByUserIdAndMeasuredOn(userId, date)).thenReturn(Optional.empty());

        assertThat(newService().delete(userId, date)).isFalse();
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).delete(any());
    }

    private MeasurementService newService() {
        return new MeasurementService(repository, profileRepository);
    }

    private static UserProfile profileFor(UUID userId) {
        UserProfile profile = new UserProfile(userId);
        profile.setSex(Sex.FEMALE);
        profile.setBirthDate(LocalDate.of(1990, 1, 1));
        profile.setHeightCm(165);
        profile.setWeightKg(new BigDecimal("65.00"));
        profile.setActivityLevel(ActivityLevel.SEDENTARY);
        profile.setGoal(Goal.MAINTAIN);
        return profile;
    }
}
