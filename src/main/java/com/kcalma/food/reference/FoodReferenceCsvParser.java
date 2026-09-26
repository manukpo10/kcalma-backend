package com.kcalma.food.reference;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses one line of the compact, RFC4180-ish CSV bundled at {@code
 * src/main/resources/usda/food_reference.csv.gz} — fixed column order, no header row: fdc_id,
 * description, data_type, kcal, protein, fat, carbs, fiber, sugar, sodium_mg, search_name (see
 * {@code tools/usda/build_food_reference.py}, which must stay in sync with this column order).
 *
 * <p>Handles double-quoted fields with embedded commas (USDA descriptions routinely have them,
 * e.g. "Beef, ground, 80% lean, cooked") and doubled-quote escaping, but NOT embedded newlines
 * inside a single field — the generator never produces those, so a plain per-line reader is safe
 * here and keeps {@link db.migration.V5__LoadFoodReferenceData} a simple line-at-a-time streamer.
 */
public final class FoodReferenceCsvParser {

    private static final int COLUMN_COUNT = 11;

    private FoodReferenceCsvParser() {}

    public static FoodReferenceCsvRow parseLine(String line) {
        List<String> fields = splitCsvLine(line);
        if (fields.size() != COLUMN_COUNT) {
            throw new IllegalArgumentException(
                    "Expected " + COLUMN_COUNT + " columns, got " + fields.size() + ": " + line);
        }
        return new FoodReferenceCsvRow(
                Long.parseLong(fields.get(0)),
                fields.get(1),
                fields.get(2),
                new BigDecimal(fields.get(3)),
                new BigDecimal(fields.get(4)),
                new BigDecimal(fields.get(5)),
                new BigDecimal(fields.get(6)),
                new BigDecimal(fields.get(7)),
                new BigDecimal(fields.get(8)),
                new BigDecimal(fields.get(9)),
                fields.get(10));
    }

    /** Package-visible for direct unit testing of the quoting edge cases. */
    static List<String> splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
