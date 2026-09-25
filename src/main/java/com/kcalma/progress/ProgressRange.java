package com.kcalma.progress;

import java.time.LocalDate;

/** Range selector for GET /api/progress. */
public enum ProgressRange {
    ONE_MONTH("1M"),
    THREE_MONTHS("3M"),
    ONE_YEAR("1Y"),
    ALL("ALL");

    private final String queryValue;

    ProgressRange(String queryValue) {
        this.queryValue = queryValue;
    }

    public String queryValue() {
        return queryValue;
    }

    /** @throws IllegalArgumentException if value isn't one of "1M", "3M", "1Y", "ALL" */
    public static ProgressRange fromQueryValue(String value) {
        for (ProgressRange range : values()) {
            if (range.queryValue.equals(value)) {
                return range;
            }
        }
        throw new IllegalArgumentException("Unknown range: " + value);
    }

    /** Inclusive start date for this range, ending at {@code to}. ALL starts at the earliest data available. */
    public LocalDate from(LocalDate to, LocalDate earliestDataDate) {
        return switch (this) {
            case ONE_MONTH -> to.minusMonths(1);
            case THREE_MONTHS -> to.minusMonths(3);
            case ONE_YEAR -> to.minusYears(1);
            case ALL -> earliestDataDate;
        };
    }
}
