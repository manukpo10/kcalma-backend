package com.kcalma.checkin.dto;

import com.kcalma.checkin.CheckinStatus;
import com.kcalma.checkin.TdeeCheckin;
import java.time.LocalDate;

/** One entry of GET /api/checkin/history — a strictly-past week, frozen as of when it closed (or last recomputed, if never closed). */
public record CheckinHistoryEntryResponse(
        LocalDate weekStart,
        CheckinStatus status,
        Integer estimatedTdee,
        Integer appliedTdee,
        Integer avgIntakeKcal,
        Double trendChangeKg) {

    public static CheckinHistoryEntryResponse from(TdeeCheckin checkin) {
        return new CheckinHistoryEntryResponse(
                checkin.getWeekStart(),
                checkin.getStatus(),
                checkin.getEstimatedTdee(),
                checkin.getAppliedTdee(),
                checkin.getAvgIntakeKcal(),
                checkin.getTrendChangeKg());
    }
}
