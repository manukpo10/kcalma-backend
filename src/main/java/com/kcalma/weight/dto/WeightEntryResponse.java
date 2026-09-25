package com.kcalma.weight.dto;

import com.kcalma.weight.WeightEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WeightEntryResponse(
        UUID id, LocalDate entryDate, BigDecimal weightKg, OffsetDateTime createdAt, OffsetDateTime updatedAt) {

    public static WeightEntryResponse from(WeightEntry entry) {
        return new WeightEntryResponse(
                entry.getId(), entry.getEntryDate(), entry.getWeightKg(), entry.getCreatedAt(), entry.getUpdatedAt());
    }
}
