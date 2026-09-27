package com.kcalma.checkin.dto;

import com.kcalma.checkin.CheckinStatus;
import com.kcalma.checkin.Confidence;
import com.kcalma.checkin.TdeeCheckin;
import java.time.LocalDate;
import java.util.List;

/** Body for GET /api/checkin (and the checkin half of accept/dismiss's own responses). */
public record CheckinResponse(
        LocalDate weekStart,
        CheckinStatus status,
        LocalDate windowStart,
        LocalDate windowEnd,
        Integer completeDays,
        Integer weighIns,
        Integer avgIntakeKcal,
        Double trendChangeKg,
        Integer formulaTdee,
        Integer currentTdee,
        Integer estimatedTdee,
        Integer proposedTdee,
        Integer currentTargetKcal,
        Integer proposedTargetKcal,
        Confidence confidence,
        List<String> reasons) {

    /** {@code INSUFFICIENT_DATA}: every number is {@code null} per the API contract — only the window and reasons are shown. */
    public static CheckinResponse insufficientData(TdeeCheckin checkin, List<String> reasons) {
        return new CheckinResponse(
                checkin.getWeekStart(),
                CheckinStatus.INSUFFICIENT_DATA,
                checkin.getWindowStart(),
                checkin.getWindowEnd(),
                null, null, null, null, null, null, null, null, null, null, null,
                List.copyOf(reasons));
    }

    /**
     * @param currentTdee resolved live (last accepted adaptive TDEE, or the formula TDEE) — never
     *     frozen on the row, since "current" always means right now, even for an already-closed week
     * @param currentTargetKcal today's target calories at {@code currentTdee}
     * @param proposedTargetKcal what target calories would become at {@code checkin.getProposedTdee()}
     */
    public static CheckinResponse of(TdeeCheckin checkin, int currentTdee, int currentTargetKcal, int proposedTargetKcal) {
        return new CheckinResponse(
                checkin.getWeekStart(),
                checkin.getStatus(),
                checkin.getWindowStart(),
                checkin.getWindowEnd(),
                checkin.getCompleteDays(),
                checkin.getWeighIns(),
                checkin.getAvgIntakeKcal(),
                checkin.getTrendChangeKg(),
                checkin.getFormulaTdee(),
                currentTdee,
                checkin.getEstimatedTdee(),
                checkin.getProposedTdee(),
                currentTargetKcal,
                proposedTargetKcal,
                checkin.getConfidence(),
                List.of());
    }
}
