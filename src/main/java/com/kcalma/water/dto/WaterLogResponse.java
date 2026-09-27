package com.kcalma.water.dto;

import com.kcalma.water.WaterLog;
import java.time.LocalDate;

/** One historical day's water total — used by GET /api/export, never by POST /api/water (see {@link WaterResponse} for that). */
public record WaterLogResponse(LocalDate date, int ml) {

    public static WaterLogResponse from(WaterLog log) {
        return new WaterLogResponse(log.getEntryDate(), log.getMl());
    }
}
