package com.kcalma.measurement;

import com.kcalma.measurement.dto.MeasurementResponse;
import com.kcalma.measurement.dto.UpsertMeasurementRequest;
import com.kcalma.profile.UserProfile;
import com.kcalma.profile.UserProfileRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeasurementService {

    private final BodyMeasurementRepository repository;
    private final UserProfileRepository profileRepository;

    public MeasurementService(BodyMeasurementRepository repository, UserProfileRepository profileRepository) {
        this.repository = repository;
        this.profileRepository = profileRepository;
    }

    @Transactional
    public MeasurementResponse upsert(UUID userId, LocalDate date, UpsertMeasurementRequest request) {
        BodyMeasurement measurement = repository.findByUserIdAndMeasuredOn(userId, date).orElseGet(() -> new BodyMeasurement(userId, date));
        measurement.setWaistCm(request.waistCm());
        measurement.setHipCm(request.hipCm());
        measurement.setChestCm(request.chestCm());
        measurement.setArmCm(request.armCm());
        measurement.setThighCm(request.thighCm());
        measurement.setBodyFatPct(request.bodyFatPct());
        measurement.setMuscleMassKg(request.muscleMassKg());
        BodyMeasurement saved = repository.save(measurement);

        recomputeProfileBodyFatFromLatestMeasurement(userId);

        return MeasurementResponse.from(saved);
    }

    @Transactional
    public boolean delete(UUID userId, LocalDate date) {
        Optional<BodyMeasurement> existing = repository.findByUserIdAndMeasuredOn(userId, date);
        if (existing.isEmpty()) {
            return false;
        }
        repository.delete(existing.get());
        recomputeProfileBodyFatFromLatestMeasurement(userId);
        return true;
    }

    @Transactional(readOnly = true)
    public List<MeasurementResponse> findRange(UUID userId, LocalDate from, LocalDate to) {
        return repository.findByUserIdAndMeasuredOnBetweenOrderByMeasuredOnAsc(userId, from, to).stream()
                .map(MeasurementResponse::from)
                .toList();
    }

    /**
     * Re-syncs {@code user_profile.body_fat_pct}/{@code body_fat_measured_on} from whichever
     * measurement is now the latest ({@code measured_on} DESC) one carrying a body-fat reading —
     * called unconditionally after every upsert AND every delete, which correctly implements both
     * halves of the sprint contract with one rule instead of two:
     *
     * <ul>
     *   <li>upserting/editing a row that now has the latest body-fat reading -&gt; the profile picks
     *       it up (the "update from the latest one that has it" case);
     *   <li>deleting that row, or clearing its bodyFatPct via a later PUT that omits it -&gt; this
     *       query naturally excludes it and falls back to whichever is now latest, or is empty and
     *       the profile is left exactly as it was ("leaves the profile value if none" — this method
     *       never nulls out an existing profile value on an empty result).
     * </ul>
     *
     * <p>Re-applying the same latest measurement when nothing relevant changed (e.g. deleting an
     * OLDER row while a newer body-fat reading already feeds the profile) is a harmless no-op: the
     * query still returns that same newer row, so the profile is set to the values it already had.
     */
    private void recomputeProfileBodyFatFromLatestMeasurement(UUID userId) {
        Optional<BodyMeasurement> latestWithBodyFat = repository.findFirstByUserIdAndBodyFatPctIsNotNullOrderByMeasuredOnDesc(userId);
        if (latestWithBodyFat.isEmpty()) {
            return;
        }
        Optional<UserProfile> profile = profileRepository.findById(userId);
        if (profile.isEmpty()) {
            return;
        }
        BodyMeasurement latest = latestWithBodyFat.get();
        profile.get().setBodyFatPct(latest.getBodyFatPct());
        profile.get().setBodyFatMeasuredOn(latest.getMeasuredOn());
    }
}
