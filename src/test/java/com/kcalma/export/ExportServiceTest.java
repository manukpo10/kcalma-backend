package com.kcalma.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kcalma.export.dto.ExportResponse;
import com.kcalma.food.FoodEntry;
import com.kcalma.food.FoodEntryRepository;
import com.kcalma.food.FoodSource;
import com.kcalma.food.MealType;
import com.kcalma.measurement.BodyMeasurement;
import com.kcalma.measurement.BodyMeasurementRepository;
import com.kcalma.profile.ActivityLevel;
import com.kcalma.profile.Goal;
import com.kcalma.profile.Sex;
import com.kcalma.profile.UserProfile;
import com.kcalma.profile.UserProfileRepository;
import com.kcalma.water.WaterLog;
import com.kcalma.water.WaterLogRepository;
import com.kcalma.weight.WeightEntry;
import com.kcalma.weight.WeightEntryRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link ExportService}: the JSON payload's shape and the CSV/zip's exact entries and header rows. */
@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));

    @Mock
    private UserProfileRepository profileRepository;

    @Mock
    private FoodEntryRepository foodEntryRepository;

    @Mock
    private WeightEntryRepository weightEntryRepository;

    @Mock
    private WaterLogRepository waterLogRepository;

    @Mock
    private BodyMeasurementRepository measurementRepository;

    private final UUID userId = UUID.randomUUID();

    @Test
    void buildJson_includesExportedAtProfileAndEveryDataset() {
        UserProfile profile = new UserProfile(userId);
        profile.setSex(Sex.FEMALE);
        profile.setBirthDate(LocalDate.of(1990, 1, 1));
        profile.setHeightCm(165);
        profile.setWeightKg(new BigDecimal("65.00"));
        profile.setActivityLevel(ActivityLevel.SEDENTARY);
        profile.setGoal(Goal.MAINTAIN);
        FoodEntry entry = foodEntry();
        WeightEntry weight = new WeightEntry(userId, LocalDate.of(2026, 9, 20), new BigDecimal("65.00"));
        WaterLog water = new WaterLog(userId, LocalDate.of(2026, 9, 20), 1500);
        BodyMeasurement measurement = new BodyMeasurement(userId, LocalDate.of(2026, 9, 20));
        measurement.setWaistCm(new BigDecimal("80.00"));

        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(foodEntryRepository.findByUserIdOrderByEntryDateAscCreatedAtAsc(userId)).thenReturn(List.of(entry));
        when(weightEntryRepository.findByUserIdOrderByEntryDateAsc(userId)).thenReturn(List.of(weight));
        when(waterLogRepository.findByUserIdOrderByEntryDateAsc(userId)).thenReturn(List.of(water));
        when(measurementRepository.findByUserIdOrderByMeasuredOnAsc(userId)).thenReturn(List.of(measurement));

        ExportResponse export = newService().buildJson(userId);

        assertThat(export.exportedAt()).isEqualTo(java.time.OffsetDateTime.now(CLOCK));
        assertThat(export.profile().userId()).isEqualTo(userId);
        assertThat(export.foodEntries()).hasSize(1);
        assertThat(export.foodEntries().get(0).name()).isEqualTo("Milanesa");
        assertThat(export.weights()).hasSize(1);
        assertThat(export.weights().get(0).weightKg()).isEqualByComparingTo("65.00");
        assertThat(export.water()).hasSize(1);
        assertThat(export.water().get(0).ml()).isEqualTo(1500);
        assertThat(export.measurements()).hasSize(1);
        assertThat(export.measurements().get(0).waistCm()).isEqualByComparingTo("80.00");
    }

    @Test
    void buildJson_noProfileYet_profileFieldIsNullNotAnError() {
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());
        when(foodEntryRepository.findByUserIdOrderByEntryDateAscCreatedAtAsc(userId)).thenReturn(List.of());
        when(weightEntryRepository.findByUserIdOrderByEntryDateAsc(userId)).thenReturn(List.of());
        when(waterLogRepository.findByUserIdOrderByEntryDateAsc(userId)).thenReturn(List.of());
        when(measurementRepository.findByUserIdOrderByMeasuredOnAsc(userId)).thenReturn(List.of());

        ExportResponse export = newService().buildJson(userId);

        assertThat(export.profile()).isNull();
    }

    @Test
    void buildCsvZip_producesAllFourNamedEntriesWithHeaderAndDataRows() throws IOException {
        when(foodEntryRepository.findByUserIdOrderByEntryDateAscCreatedAtAsc(userId)).thenReturn(List.of(foodEntry()));
        when(weightEntryRepository.findByUserIdOrderByEntryDateAsc(userId))
                .thenReturn(List.of(new WeightEntry(userId, LocalDate.of(2026, 9, 20), new BigDecimal("65.00"))));
        when(waterLogRepository.findByUserIdOrderByEntryDateAsc(userId))
                .thenReturn(List.of(new WaterLog(userId, LocalDate.of(2026, 9, 20), 1500)));
        when(measurementRepository.findByUserIdOrderByMeasuredOnAsc(userId)).thenReturn(List.of());

        byte[] zip = newService().buildCsvZip(userId);

        Map<String, String> entries = readZipEntries(zip);
        assertThat(entries).containsOnlyKeys("food_entries.csv", "weights.csv", "water.csv", "measurements.csv");
        assertThat(entries.get("food_entries.csv"))
                .startsWith("entryDate,mealType,name,grams,kcalPer100")
                .contains("Milanesa");
        assertThat(entries.get("weights.csv")).isEqualTo("entryDate,weightKg\n2026-09-20,65.00\n");
        assertThat(entries.get("water.csv")).isEqualTo("date,ml\n2026-09-20,1500\n");
        // No measurements logged -- header row only, no trailing garbage.
        assertThat(entries.get("measurements.csv"))
                .isEqualTo("measuredOn,waistCm,hipCm,chestCm,armCm,thighCm,bodyFatPct,muscleMassKg\n");
    }

    private ExportService newService() {
        return new ExportService(profileRepository, foodEntryRepository, weightEntryRepository, waterLogRepository, measurementRepository, CLOCK);
    }

    private FoodEntry foodEntry() {
        return new FoodEntry(
                userId,
                LocalDate.of(2026, 9, 20),
                MealType.ALMUERZO,
                "Milanesa",
                new BigDecimal("150.00"),
                new BigDecimal("250.00"),
                new BigDecimal("26.00"),
                new BigDecimal("15.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("70.00"),
                FoodSource.USDA,
                null,
                null);
    }

    private static Map<String, String> readZipEntries(byte[] zip) throws IOException {
        Map<String, String> entries = new java.util.LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return entries;
    }
}
