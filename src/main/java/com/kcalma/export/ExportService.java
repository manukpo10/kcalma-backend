package com.kcalma.export;

import com.kcalma.export.dto.ExportResponse;
import com.kcalma.food.FoodEntry;
import com.kcalma.food.FoodEntryRepository;
import com.kcalma.food.dto.FoodEntryResponse;
import com.kcalma.measurement.BodyMeasurement;
import com.kcalma.measurement.BodyMeasurementRepository;
import com.kcalma.measurement.dto.MeasurementResponse;
import com.kcalma.profile.UserProfileRepository;
import com.kcalma.profile.dto.ProfileResponse;
import com.kcalma.water.WaterLog;
import com.kcalma.water.WaterLogRepository;
import com.kcalma.water.dto.WaterLogResponse;
import com.kcalma.weight.WeightEntry;
import com.kcalma.weight.WeightEntryRepository;
import com.kcalma.weight.dto.WeightEntryResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Powers GET /api/export: every one of the caller's own records, as one JSON payload or a zip of four CSVs. */
@Service
public class ExportService {

    private final UserProfileRepository profileRepository;
    private final FoodEntryRepository foodEntryRepository;
    private final WeightEntryRepository weightEntryRepository;
    private final WaterLogRepository waterLogRepository;
    private final BodyMeasurementRepository measurementRepository;
    private final Clock clock;

    public ExportService(
            UserProfileRepository profileRepository,
            FoodEntryRepository foodEntryRepository,
            WeightEntryRepository weightEntryRepository,
            WaterLogRepository waterLogRepository,
            BodyMeasurementRepository measurementRepository,
            Clock clock) {
        this.profileRepository = profileRepository;
        this.foodEntryRepository = foodEntryRepository;
        this.weightEntryRepository = weightEntryRepository;
        this.waterLogRepository = waterLogRepository;
        this.measurementRepository = measurementRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ExportResponse buildJson(UUID userId) {
        return new ExportResponse(
                OffsetDateTime.now(clock),
                profileRepository.findById(userId).map(ProfileResponse::from).orElse(null),
                foodEntryRepository.findByUserIdOrderByEntryDateAscCreatedAtAsc(userId).stream()
                        .map(FoodEntryResponse::from)
                        .toList(),
                weightEntryRepository.findByUserIdOrderByEntryDateAsc(userId).stream().map(WeightEntryResponse::from).toList(),
                waterLogRepository.findByUserIdOrderByEntryDateAsc(userId).stream().map(WaterLogResponse::from).toList(),
                measurementRepository.findByUserIdOrderByMeasuredOnAsc(userId).stream().map(MeasurementResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public byte[] buildCsvZip(UUID userId) {
        List<FoodEntry> foodEntries = foodEntryRepository.findByUserIdOrderByEntryDateAscCreatedAtAsc(userId);
        List<WeightEntry> weights = weightEntryRepository.findByUserIdOrderByEntryDateAsc(userId);
        List<WaterLog> water = waterLogRepository.findByUserIdOrderByEntryDateAsc(userId);
        List<BodyMeasurement> measurements = measurementRepository.findByUserIdOrderByMeasuredOnAsc(userId);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            writeCsvEntry(zip, "food_entries.csv", foodEntriesCsv(foodEntries));
            writeCsvEntry(zip, "weights.csv", weightsCsv(weights));
            writeCsvEntry(zip, "water.csv", waterCsv(water));
            writeCsvEntry(zip, "measurements.csv", measurementsCsv(measurements));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el archivo de exportación.", e);
        }
        return buffer.toByteArray();
    }

    private void writeCsvEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String foodEntriesCsv(List<FoodEntry> entries) {
        StringBuilder csv = new StringBuilder(row(
                "entryDate", "mealType", "name", "grams", "kcalPer100", "proteinPer100", "fatPer100", "carbsPer100",
                "fiberPer100", "sugarPer100", "sodiumMgPer100", "source", "fdcId"));
        for (FoodEntry entry : entries) {
            csv.append(row(
                    entry.getEntryDate(),
                    entry.getMealType(),
                    entry.getName(),
                    entry.getGrams(),
                    entry.getKcalPer100(),
                    entry.getProteinPer100(),
                    entry.getFatPer100(),
                    entry.getCarbsPer100(),
                    entry.getFiberPer100(),
                    entry.getSugarPer100(),
                    entry.getSodiumMgPer100(),
                    entry.getSource(),
                    entry.getFdcId()));
        }
        return csv.toString();
    }

    private String weightsCsv(List<WeightEntry> weights) {
        StringBuilder csv = new StringBuilder(row("entryDate", "weightKg"));
        for (WeightEntry weight : weights) {
            csv.append(row(weight.getEntryDate(), weight.getWeightKg()));
        }
        return csv.toString();
    }

    private String waterCsv(List<WaterLog> water) {
        StringBuilder csv = new StringBuilder(row("date", "ml"));
        for (WaterLog log : water) {
            csv.append(row(log.getEntryDate(), log.getMl()));
        }
        return csv.toString();
    }

    private String measurementsCsv(List<BodyMeasurement> measurements) {
        StringBuilder csv =
                new StringBuilder(row("measuredOn", "waistCm", "hipCm", "chestCm", "armCm", "thighCm", "bodyFatPct", "muscleMassKg"));
        for (BodyMeasurement measurement : measurements) {
            csv.append(row(
                    measurement.getMeasuredOn(),
                    measurement.getWaistCm(),
                    measurement.getHipCm(),
                    measurement.getChestCm(),
                    measurement.getArmCm(),
                    measurement.getThighCm(),
                    measurement.getBodyFatPct(),
                    measurement.getMuscleMassKg()));
        }
        return csv.toString();
    }

    private static String row(Object... values) {
        return Arrays.stream(values).map(ExportService::csvField).collect(Collectors.joining(",")) + "\n";
    }

    /** {@code null} becomes an empty field; a value containing a comma/quote/newline is quoted with internal quotes doubled (RFC 4180). */
    private static String csvField(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
