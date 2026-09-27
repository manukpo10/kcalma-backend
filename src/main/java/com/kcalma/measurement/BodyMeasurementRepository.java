package com.kcalma.measurement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BodyMeasurementRepository extends JpaRepository<BodyMeasurement, UUID> {

    Optional<BodyMeasurement> findByUserIdAndMeasuredOn(UUID userId, LocalDate measuredOn);

    List<BodyMeasurement> findByUserIdAndMeasuredOnBetweenOrderByMeasuredOnAsc(UUID userId, LocalDate from, LocalDate to);

    /** Every measurement a user ever logged, oldest first — used by GET /api/export. */
    List<BodyMeasurement> findByUserIdOrderByMeasuredOnAsc(UUID userId);

    /** The most recent row (any date) that has a body-fat reading — drives the profile propagation/fallback in {@code MeasurementService}. */
    Optional<BodyMeasurement> findFirstByUserIdAndBodyFatPctIsNotNullOrderByMeasuredOnDesc(UUID userId);
}
