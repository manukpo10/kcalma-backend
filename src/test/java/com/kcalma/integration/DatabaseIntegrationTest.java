package com.kcalma.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kcalma.account.AccountService;
import com.kcalma.checkin.CheckinStatus;
import com.kcalma.checkin.Confidence;
import com.kcalma.checkin.TdeeAdaptationCalculator;
import com.kcalma.checkin.TdeeCheckin;
import com.kcalma.checkin.TdeeCheckinRepository;
import com.kcalma.favorites.FavoriteDish;
import com.kcalma.favorites.FavoriteDishRepository;
import com.kcalma.food.FoodEntry;
import com.kcalma.food.FoodEntryIngredient;
import com.kcalma.food.FoodEntryRepository;
import com.kcalma.food.FoodSource;
import com.kcalma.food.MealType;
import com.kcalma.food.NutritionMath;
import com.kcalma.food.reference.FoodReference;
import com.kcalma.food.reference.FoodReferenceRepository;
import com.kcalma.food.reference.UserFood;
import com.kcalma.food.reference.UserFoodRepository;
import com.kcalma.food.reference.UserFoodSource;
import com.kcalma.measurement.BodyMeasurement;
import com.kcalma.measurement.BodyMeasurementRepository;
import com.kcalma.profile.ActivityLevel;
import com.kcalma.profile.DietStyle;
import com.kcalma.profile.DietaryRestriction;
import com.kcalma.profile.Goal;
import com.kcalma.profile.Pace;
import com.kcalma.profile.Sex;
import com.kcalma.profile.UserProfile;
import com.kcalma.profile.UserProfileRepository;
import com.kcalma.push.PushSubscriptionService;
import com.kcalma.push.VapidKeyService;
import com.kcalma.reminder.ReminderLogRepository;
import com.kcalma.reminder.ReminderSettings;
import com.kcalma.reminder.ReminderSettingsData;
import com.kcalma.reminder.ReminderSettingsRepository;
import com.kcalma.water.WaterLog;
import com.kcalma.water.WaterLogRepository;
import com.kcalma.weight.WeightEntry;
import com.kcalma.weight.WeightEntryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Real-database integration test: boots the full Spring context (Flyway included) against a
 * clean, disposable Postgres 17 container — running every migration V1..V10 from scratch — then
 * round-trips JPA against the tables those migrations create.
 *
 * <p>{@code src/test/resources/testcontainers/init.sql} mimics the parts of a fresh Supabase
 * project this app's migrations check for (the {@code extensions} schema, the {@code
 * anon}/{@code authenticated} roles) that a plain {@code postgres:17} image doesn't have.
 *
 * <p>{@code @Testcontainers(disabledWithoutDocker = true)}: on a machine without Docker running,
 * this class's tests are simply skipped (not failed) — the rest of the suite still passes. See
 * the sprint's smoke-test task for the fallback when Docker genuinely isn't available at all.
 */
// MOCK (the default): SecurityConfig's filterChain() bean needs the HttpSecurity bean that only
// Spring Security's web auto-configuration provides, which WebEnvironment.NONE would skip.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173",
    "app.gemini.api-key=test-key",
    // This class isn't testing scheduling behavior (see ReminderSchedulerTest for that) and its
    // container is torn down right after the last @Test method -- without this, a tick that fires
    // during/after teardown logs a harmless but noisy "connection is closed" error.
    "kcalma.reminders.scheduler-enabled=false"
})
class DatabaseIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"))
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("testcontainers/init.sql"), "/docker-entrypoint-initdb.d/01-init.sql");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private FoodEntryRepository foodEntryRepository;

    @Autowired
    private UserFoodRepository userFoodRepository;

    @Autowired
    private FoodReferenceRepository foodReferenceRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private FavoriteDishRepository favoriteDishRepository;

    @Autowired
    private WaterLogRepository waterLogRepository;

    @Autowired
    private BodyMeasurementRepository measurementRepository;

    @Autowired
    private TdeeCheckinRepository checkinRepository;

    @Autowired
    private VapidKeyService vapidKeyService;

    @Autowired
    private PushSubscriptionService pushSubscriptionService;

    @Autowired
    private ReminderSettingsRepository reminderSettingsRepository;

    @Autowired
    private ReminderLogRepository reminderLogRepository;

    @Autowired
    private WeightEntryRepository weightEntryRepository;

    @Autowired
    private AccountService accountService;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void flyway_migratesEveryVersionUpToV15Successfully() throws SQLException {
        List<String> versions = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT version, success FROM app.flyway_schema_history ORDER BY installed_rank")) {
            while (resultSet.next()) {
                assertThat(resultSet.getBoolean("success")).isTrue();
                versions.add(resultSet.getString("version"));
            }
        }
        assertThat(versions).contains("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15");
    }

    @Test
    void userProfile_roundTripsSprint2aFieldsThroughTheRealDatabase() {
        UserProfile profile = new UserProfile(UUID.randomUUID());
        profile.setSex(Sex.FEMALE);
        profile.setBirthDate(LocalDate.of(1990, 5, 20));
        profile.setHeightCm(168);
        profile.setWeightKg(new BigDecimal("70.00"));
        profile.setActivityLevel(ActivityLevel.LIGHTLY_ACTIVE);
        profile.setGoal(Goal.LOSE_FAT);
        profile.setPace(Pace.MODERATE);
        profile.setDietStyle(DietStyle.HIGH_PROTEIN);
        profile.setDietaryRestrictions(List.of(DietaryRestriction.VEGETARIAN, DietaryRestriction.GLUTEN_FREE));
        profile.setStrengthTraining(true);
        profile.setBodyFatPct(new BigDecimal("28.5"));
        profile.setBodyFatMeasuredOn(LocalDate.of(2026, 9, 1));

        UserProfile saved = userProfileRepository.saveAndFlush(profile);
        entityManager.clear(); // force the next read to actually hit the DB, not the session cache

        UserProfile reloaded = userProfileRepository.findById(saved.getUserId()).orElseThrow();
        assertThat(reloaded.getGoal()).isEqualTo(Goal.LOSE_FAT);
        assertThat(reloaded.getPace()).isEqualTo(Pace.MODERATE);
        assertThat(reloaded.getDietStyle()).isEqualTo(DietStyle.HIGH_PROTEIN);
        assertThat(reloaded.getDietaryRestrictions()).containsExactly(DietaryRestriction.VEGETARIAN, DietaryRestriction.GLUTEN_FREE);
        assertThat(reloaded.isStrengthTraining()).isTrue();
        assertThat(reloaded.getBodyFatPct()).isEqualByComparingTo("28.5");
        assertThat(reloaded.getBodyFatMeasuredOn()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void userProfile_newRowDefaultsDietStyleAndDietaryRestrictionsAndStrengthTrainingWhenUnset() {
        UserProfile profile = new UserProfile(UUID.randomUUID());
        profile.setSex(Sex.MALE);
        profile.setBirthDate(LocalDate.of(1985, 3, 10));
        profile.setHeightCm(180);
        profile.setWeightKg(new BigDecimal("82.00"));
        profile.setActivityLevel(ActivityLevel.SEDENTARY);
        profile.setGoal(Goal.MAINTAIN);
        // pace/dietStyle/dietaryRestrictions/strengthTraining/bodyFat* deliberately left untouched.

        UserProfile saved = userProfileRepository.saveAndFlush(profile);
        entityManager.clear();

        UserProfile reloaded = userProfileRepository.findById(saved.getUserId()).orElseThrow();
        assertThat(reloaded.getPace()).isNull();
        assertThat(reloaded.getDietStyle()).isEqualTo(DietStyle.BALANCED);
        assertThat(reloaded.getDietaryRestrictions()).isEmpty();
        assertThat(reloaded.isStrengthTraining()).isFalse();
        assertThat(reloaded.getBodyFatPct()).isNull();
        assertThat(reloaded.getBodyFatMeasuredOn()).isNull();
    }

    /**
     * V10 (Flyway target) turns a legacy pre-V10 row (goal {@code 'LOSE'}, none of the new columns)
     * into a valid post-V10 one, with no manual backfill. Uses its own throwaway container/database
     * so the row can genuinely be inserted BEFORE V10 runs — the shared {@link #POSTGRES} container
     * above is migrated to the latest version by Spring's own Flyway run before any {@code @Test}
     * method here executes, so it can never be caught mid-migration.
     */
    @Test
    void v10Migration_migratesALegacyLoseGoalRowAndBackfillsTheNewColumnsWithSafeDefaults() throws SQLException {
        try (PostgreSQLContainer<?> legacyPostgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"))
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("testcontainers/init.sql"), "/docker-entrypoint-initdb.d/01-init.sql")) {
            legacyPostgres.start();
            String url = legacyPostgres.getJdbcUrl();
            String user = legacyPostgres.getUsername();
            String password = legacyPostgres.getPassword();

            Flyway.configure()
                    .dataSource(url, user, password)
                    .schemas("app")
                    .target(MigrationVersion.fromVersion("9"))
                    .load()
                    .migrate();

            UUID legacyUserId = UUID.randomUUID();
            try (Connection connection = DriverManager.getConnection(url, user, password);
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        """
                        INSERT INTO app.user_profile (user_id, sex, birth_date, height_cm, weight_kg, activity_level, goal)
                        VALUES ('%s', 'FEMALE', '1990-01-01', 165, 70.00, 'SEDENTARY', 'LOSE')
                        """
                                .formatted(legacyUserId));
            }

            Flyway.configure().dataSource(url, user, password).schemas("app").load().migrate();

            try (Connection connection = DriverManager.getConnection(url, user, password);
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery(
                            "SELECT goal, pace, diet_style, dietary_restrictions, strength_training, body_fat_pct "
                                    + "FROM app.user_profile WHERE user_id = '"
                                    + legacyUserId + "'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("goal")).isEqualTo("LOSE_WEIGHT");
                assertThat(resultSet.getString("pace")).isEqualTo("MODERATE");
                assertThat(resultSet.getString("diet_style")).isEqualTo("BALANCED");
                Array restrictions = resultSet.getArray("dietary_restrictions");
                assertThat((Object[]) restrictions.getArray()).isEmpty();
                assertThat(resultSet.getBoolean("strength_training")).isFalse();
                assertThat(resultSet.getObject("body_fat_pct")).isNull();
            }
        }
    }

    @Test
    void foodEntry_roundTripsJsonbIngredientsThroughTheRealDatabase() {
        UUID userId = UUID.randomUUID();
        // fdcId is null throughout -- food_entry.fdc_id/food_entry's own ingredients jsonb have no
        // FK of their own, but a non-null fdc_id here WOULD need a real row in food_reference
        // (see food_entry_fdc_id_fkey); null is exactly what an ESTIMATED/no-match item persists.
        List<FoodEntryIngredient> ingredients = List.of(new FoodEntryIngredient(
                "Carne",
                new BigDecimal("120.00"),
                new BigDecimal("250.00"),
                new BigDecimal("26.00"),
                new BigDecimal("15.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("70.00"),
                FoodSource.USDA,
                null));
        FoodEntry entry = new FoodEntry(
                userId,
                LocalDate.of(2026, 9, 25),
                MealType.ALMUERZO,
                "Milanesa",
                new BigDecimal("120.00"),
                new BigDecimal("250.00"),
                new BigDecimal("26.00"),
                new BigDecimal("15.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("70.00"),
                FoodSource.USDA,
                null,
                ingredients);

        FoodEntry saved = foodEntryRepository.saveAndFlush(entry);
        entityManager.clear(); // force the next read to actually hit the DB, not the session cache

        FoodEntry reloaded = foodEntryRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getIngredients()).hasSize(1);
        assertThat(reloaded.getIngredients().get(0).name()).isEqualTo("Carne");
        assertThat(reloaded.getIngredients().get(0).source()).isEqualTo(FoodSource.USDA);
        assertThat(reloaded.getIngredients().get(0).grams()).isEqualByComparingTo("120.00");
    }

    @Test
    void foodEntry_sourceCheckConstraint_allowsMixedButRejectsGarbage() {
        FoodEntry mixedEntry = new FoodEntry(
                UUID.randomUUID(),
                LocalDate.of(2026, 9, 25),
                MealType.CENA,
                "Plato mixto",
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                new BigDecimal("5.00"),
                new BigDecimal("5.00"),
                new BigDecimal("5.00"),
                new BigDecimal("1.00"),
                new BigDecimal("1.00"),
                new BigDecimal("50.00"),
                FoodSource.MIXED,
                null,
                null);
        assertThat(foodEntryRepository.saveAndFlush(mixedEntry).getId()).isNotNull();

        // Bypasses the Java enum entirely -- proves the DB-level CHECK constraint itself rejects
        // a value the enum could never even produce, independent of any application-layer check.
        assertThatThrownBy(() -> {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        """
                        INSERT INTO app.food_entry
                            (user_id, entry_date, meal_type, name, grams, kcal_per_100, protein_per_100,
                             fat_per_100, carbs_per_100, fiber_per_100, sugar_per_100, sodium_mg_per_100, source)
                        VALUES
                            ('%s', '2026-09-25', 'CENA', 'Garbage', 100, 100, 5, 5, 5, 1, 1, 50, 'GARBAGE')
                        """
                                .formatted(UUID.randomUUID()));
            }
        })
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("food_entry_source_check");
    }

    @Test
    void userFood_upsertByNormalizedName_bumpsUseCountInsteadOfDuplicating() {
        UUID userId = UUID.randomUUID();
        NutritionMath.Per100 initial = new NutritionMath.Per100(250, 26, 15, 0, 0, 0, 70);
        UserFood created =
                userFoodRepository.saveAndFlush(new UserFood(userId, "milanesa", "Milanesa", initial, UserFoodSource.USDA, null));

        UserFood existing = userFoodRepository.findByUserIdAndNormalizedName(userId, "milanesa").orElseThrow();
        existing.recordUse("Milanesa", new NutritionMath.Per100(260, 27, 16, 0, 0, 0, 75), UserFoodSource.USDA, null);
        userFoodRepository.saveAndFlush(existing);
        entityManager.clear();

        List<UserFood> rowsForUser = userFoodRepository.findAll().stream()
                .filter(u -> u.getUserId().equals(userId))
                .toList();
        assertThat(rowsForUser).hasSize(1); // the UNIQUE (user_id, normalized_name) constraint held -- no duplicate row
        UserFood reloaded = rowsForUser.get(0);
        assertThat(reloaded.getId()).isEqualTo(created.getId());
        assertThat(reloaded.getUseCount()).isEqualTo(2);
    }

    @Test
    void foodReference_trigramQuery_findsACloseMatchAboveTheThreshold() throws SQLException {
        // A deliberately made-up search term (never a real USDA description) inserted directly,
        // so the trigram match is deterministic and can't tie/collide with a real row V5 loaded.
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    """
                    INSERT INTO app.food_reference
                        (fdc_id, description, data_type, kcal_per_100, protein_per_100, fat_per_100, carbs_per_100,
                         fiber_per_100, sugar_per_100, sodium_mg_per_100, search_name)
                    VALUES
                        (900000001, 'Kcalma integration test fixture, raw', 'sr_legacy_food', 32, 0.7, 0.3, 7.7, 2, 4.9, 1,
                         'kcalma integration test fixture, raw')
                    ON CONFLICT (fdc_id) DO NOTHING
                    """);
        }

        Optional<FoodReference> match = foodReferenceRepository.findBestMatch("kcalma integration test fixtur, raw", 0.3, true);

        assertThat(match).isPresent();
        assertThat(match.orElseThrow().getFdcId()).isEqualTo(900000001L);
    }

    @Test
    void favoriteDish_roundTripsJsonbIngredientsThroughTheRealDatabase() {
        UUID userId = UUID.randomUUID();
        List<FoodEntryIngredient> ingredients = List.of(new FoodEntryIngredient(
                "Carne",
                new BigDecimal("120.00"),
                new BigDecimal("250.00"),
                new BigDecimal("26.00"),
                new BigDecimal("15.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("70.00"),
                FoodSource.USDA,
                null));
        FavoriteDish favorite = new FavoriteDish(userId, "milanesa");
        favorite.applyDish(
                MealType.ALMUERZO,
                "Milanesa",
                new BigDecimal("120.00"),
                new BigDecimal("250.00"),
                new BigDecimal("26.00"),
                new BigDecimal("15.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("70.00"),
                FoodSource.USDA,
                null,
                ingredients);

        FavoriteDish saved = favoriteDishRepository.saveAndFlush(favorite);
        entityManager.clear();

        FavoriteDish reloaded = favoriteDishRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getMealType()).isEqualTo(MealType.ALMUERZO);
        assertThat(reloaded.getIngredients()).hasSize(1);
        assertThat(reloaded.getIngredients().get(0).name()).isEqualTo("Carne");
    }

    @Test
    void favoriteDish_uniqueUserAndNormalizedNameConstraint_rejectsADuplicate() {
        UUID userId = UUID.randomUUID();
        FavoriteDish first = new FavoriteDish(userId, "milanesa");
        first.applyDish(
                null,
                "Milanesa",
                new BigDecimal("120.00"),
                new BigDecimal("250.00"),
                new BigDecimal("26.00"),
                new BigDecimal("15.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("70.00"),
                FoodSource.MANUAL,
                null,
                null);
        favoriteDishRepository.saveAndFlush(first);

        assertThatThrownBy(() -> {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        """
                        INSERT INTO app.favorite_dish
                            (user_id, normalized_name, name, grams, kcal_per_100, protein_per_100,
                             fat_per_100, carbs_per_100, fiber_per_100, sugar_per_100, sodium_mg_per_100, source)
                        VALUES
                            ('%s', 'milanesa', 'Milanesa otra vez', 100, 100, 5, 5, 5, 1, 1, 50, 'MANUAL')
                        """
                                .formatted(userId));
            }
        }).isInstanceOf(SQLException.class);
    }

    @Test
    void waterLog_roundTripsCompositePrimaryKeyThroughTheRealDatabase() {
        UUID userId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 25);
        waterLogRepository.saveAndFlush(new WaterLog(userId, date, 750));
        entityManager.clear();

        WaterLog reloaded = waterLogRepository.findByUserIdAndEntryDate(userId, date).orElseThrow();
        assertThat(reloaded.getMl()).isEqualTo(750);

        // Bypasses the Java layer entirely -- proves the DB-level CHECK constraint itself rejects
        // a negative total, independent of WaterLogService's own floor-at-0 application logic.
        assertThatThrownBy(() -> {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO app.water_log (user_id, entry_date, ml) VALUES ('%s', '2026-09-26', -1)"
                                .formatted(UUID.randomUUID()));
            }
        }).isInstanceOf(SQLException.class);
    }

    @Test
    void bodyMeasurement_roundTripsAndUniqueConstraintThroughTheRealDatabase() {
        UUID userId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 25);
        BodyMeasurement measurement = new BodyMeasurement(userId, date);
        measurement.setWaistCm(new BigDecimal("82.50"));
        measurement.setBodyFatPct(new BigDecimal("18.5"));
        measurementRepository.saveAndFlush(measurement);
        entityManager.clear();

        BodyMeasurement reloaded = measurementRepository.findByUserIdAndMeasuredOn(userId, date).orElseThrow();
        assertThat(reloaded.getWaistCm()).isEqualByComparingTo("82.50");
        assertThat(reloaded.getBodyFatPct()).isEqualByComparingTo("18.5");

        assertThatThrownBy(() -> {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO app.body_measurement (user_id, measured_on, waist_cm) VALUES ('%s', '%s', 90)"
                                .formatted(userId, date));
            }
        }).isInstanceOf(SQLException.class);
    }

    @Test
    void tdeeCheckin_roundTripsThroughAcceptAndTheUniqueConstraintThroughTheRealDatabase() {
        UUID userId = UUID.randomUUID();
        LocalDate weekStart = LocalDate.of(2026, 9, 21);
        TdeeCheckin checkin = new TdeeCheckin(userId, weekStart);
        checkin.recompute(
                LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 20), 2759,
                new TdeeAdaptationCalculator.Result(
                        CheckinStatus.PENDING, 15, 8, 2100, -0.9, 2430, 2620, Confidence.MEDIUM, List.of()));
        checkinRepository.saveAndFlush(checkin);
        entityManager.clear(); // force the next read to actually hit the DB, not the session cache

        TdeeCheckin reloaded = checkinRepository.findByUserIdAndWeekStart(userId, weekStart).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CheckinStatus.PENDING);
        assertThat(reloaded.getCompleteDays()).isEqualTo(15);
        assertThat(reloaded.getWeighIns()).isEqualTo(8);
        assertThat(reloaded.getAvgIntakeKcal()).isEqualTo(2100);
        assertThat(reloaded.getTrendChangeKg()).isEqualTo(-0.9);
        assertThat(reloaded.getFormulaTdee()).isEqualTo(2759);
        assertThat(reloaded.getEstimatedTdee()).isEqualTo(2430);
        assertThat(reloaded.getProposedTdee()).isEqualTo(2620);
        assertThat(reloaded.getConfidence()).isEqualTo(Confidence.MEDIUM);
        assertThat(reloaded.getAppliedTdee()).isNull();
        assertThat(reloaded.getDecidedAt()).isNull();

        reloaded.accept(ActivityLevel.MODERATELY_ACTIVE, OffsetDateTime.now());
        checkinRepository.saveAndFlush(reloaded);
        entityManager.clear();

        TdeeCheckin accepted = checkinRepository.findByUserIdAndWeekStart(userId, weekStart).orElseThrow();
        assertThat(accepted.getStatus()).isEqualTo(CheckinStatus.ACCEPTED);
        assertThat(accepted.getAppliedTdee()).isEqualTo(2620);
        assertThat(accepted.getAppliedActivityLevel()).isEqualTo(ActivityLevel.MODERATELY_ACTIVE);
        assertThat(accepted.getDecidedAt()).isNotNull();
        assertThat(checkinRepository.findFirstByUserIdAndStatusOrderByWeekStartDesc(userId, CheckinStatus.ACCEPTED))
                .map(TdeeCheckin::getId)
                .contains(accepted.getId());

        // UNIQUE (user_id, week_start): bypasses the Java layer entirely -- proves the DB-level
        // constraint itself rejects a second row for the same user+week, independent of the
        // findByUserIdAndWeekStart-then-update path CheckinService always takes in practice.
        assertThatThrownBy(() -> {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO app.tdee_checkin (user_id, week_start, status, window_start, window_end) "
                                + "VALUES ('%s', '%s', 'PENDING', '2026-08-31', '2026-09-20')"
                                        .formatted(userId, weekStart));
            }
        }).isInstanceOf(SQLException.class);
    }

    @Test
    void tdeeCheckin_statusCheckConstraint_rejectsGarbage() {
        assertThatThrownBy(() -> {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO app.tdee_checkin (user_id, week_start, status, window_start, window_end) "
                                + "VALUES ('%s', '2026-09-21', 'GARBAGE', '2026-08-31', '2026-09-20')"
                                        .formatted(UUID.randomUUID()));
            }
        })
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("tdee_checkin_status_check");
    }

    /**
     * Goes through {@link VapidKeyService} (public) rather than the package-private {@code
     * PushConfigRepository} directly -- proves the same thing (the entity maps cleanly onto
     * app.push_config and the CHECK (id = 1) singleton insert succeeds against real Postgres)
     * without reaching past the feature's own encapsulation boundary from a different package.
     */
    @Test
    void pushConfig_isGeneratedOnceAndPersistedAcrossRepeatedResolutionThroughTheRealDatabase() throws SQLException {
        String firstResolution = vapidKeyService.getPublicKeyBase64Url();
        String secondResolution = vapidKeyService.getPublicKeyBase64Url();

        assertThat(firstResolution).isEqualTo(secondResolution);
        assertThat(java.util.Base64.getUrlDecoder().decode(firstResolution)).hasSize(65); // 0x04 || X(32) || Y(32)

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM app.push_config")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1); // exactly the one singleton row, never a second
        }
    }

    /** Same reasoning as {@link #pushConfig_isGeneratedOnceAndPersistedAcrossRepeatedResolutionThroughTheRealDatabase}: through the public {@link PushSubscriptionService}, not the package-private repository. */
    @Test
    void pushSubscription_upsertByEndpoint_roundTripsAndReplacesRatherThanDuplicatingThroughTheRealDatabase() throws SQLException {
        UUID userId = UUID.randomUUID();
        String endpoint = "https://push.example/" + UUID.randomUUID();

        pushSubscriptionService.subscribe(userId, endpoint, "p256dh-v1", "auth-v1", "UA-v1");
        assertRowCountForEndpoint(endpoint, 1);
        assertP256dhForEndpoint(endpoint, "p256dh-v1");

        pushSubscriptionService.subscribe(userId, endpoint, "p256dh-v2", "auth-v2", "UA-v2"); // re-subscribe, same endpoint
        assertRowCountForEndpoint(endpoint, 1); // still one row -- upserted, not duplicated
        assertP256dhForEndpoint(endpoint, "p256dh-v2");

        pushSubscriptionService.unsubscribe(userId, endpoint);
        assertRowCountForEndpoint(endpoint, 0);
    }

    private void assertRowCountForEndpoint(String endpoint, int expected) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                java.sql.PreparedStatement statement =
                        connection.prepareStatement("SELECT count(*) FROM app.push_subscription WHERE endpoint = ?")) {
            statement.setString(1, endpoint);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(expected);
            }
        }
    }

    private void assertP256dhForEndpoint(String endpoint, String expectedP256dh) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                java.sql.PreparedStatement statement =
                        connection.prepareStatement("SELECT p256dh FROM app.push_subscription WHERE endpoint = ?")) {
            statement.setString(1, endpoint);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("p256dh")).isEqualTo(expectedP256dh);
            }
        }
    }

    /**
     * The one design decision this test exists specifically to de-risk: {@code
     * ReminderSettingsData.meals} is a {@code Map<String, MealSetting>} rather than an enum-keyed
     * map (see that class's javadoc) precisely so this jsonb round trip has no enum-key ambiguity
     * to get subtly wrong. Proves it does not.
     */
    @Test
    void reminderSettings_roundTripsTheStringKeyedJsonbMealsMapThroughTheRealDatabase() {
        UUID userId = UUID.randomUUID();
        ReminderSettingsData data = ReminderSettingsData.defaults();
        reminderSettingsRepository.saveAndFlush(new ReminderSettings(userId, data));
        entityManager.clear(); // force the next read to actually hit the DB, not the session cache

        ReminderSettings reloaded = reminderSettingsRepository.findById(userId).orElseThrow();
        assertThat(reloaded.getSettings()).isEqualTo(data);
        assertThat(reloaded.getSettings().meals().get("ALMUERZO").time()).isEqualTo("13:00");
        assertThat(reloaded.getSettings().water().everyHours()).isEqualTo(2);
        assertThat(reloaded.getSettings().weighIn().days()).containsExactly("MON", "THU");
    }

    /**
     * {@link ReminderLogRepository#claim} is the whole dedupe mechanism {@code ReminderScheduler}
     * relies on to never double-send — this proves the {@code ON CONFLICT DO NOTHING} it compiles
     * to actually behaves atomically against real Postgres, not just that the Java compiles.
     */
    @Test
    void reminderLog_claimDedupesTheSameOccurrenceButAllowsDifferentKeysOrDays() {
        UUID userId = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 9, 28);

        assertThat(reminderLogRepository.claim(userId, "MEAL_ALMUERZO", day)).isEqualTo(1); // first claim wins
        assertThat(reminderLogRepository.claim(userId, "MEAL_ALMUERZO", day)).isEqualTo(0); // same occurrence again -- loses

        assertThat(reminderLogRepository.claim(userId, "WATER_14:00", day)).isEqualTo(1); // different key, same day -- independent
        assertThat(reminderLogRepository.claim(userId, "MEAL_ALMUERZO", day.plusDays(1))).isEqualTo(1); // same key, next day -- independent
    }

    /**
     * Shared-device re-subscribe (see {@code PushSubscriptionService#subscribe}): {@code endpoint}
     * is globally unique, so a second browser subscription for the SAME endpoint from a DIFFERENT
     * user moves ownership rather than duplicating or failing -- but only as proof of possession,
     * i.e. the SAME browser keys re-subscribing on a shared device. This is the intended multi-user
     * behavior, not a bug (see the DELETE /api/account work's IDOR audit). The mismatched-keys case
     * (a hijack attempt, not a real shared device) is covered by {@link
     * #pushSubscription_reSubscribeFromAnotherUserWithMismatchedKeys_rejectsWithConflictThroughTheRealDatabase}.
     */
    @Test
    void pushSubscription_reSubscribeFromAnotherUserWithMatchingKeys_movesOwnershipToTheNewUserThroughTheRealDatabase() throws SQLException {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        String endpoint = "https://push.example/shared-device/" + UUID.randomUUID();

        pushSubscriptionService.subscribe(userA, endpoint, "shared-p256dh", "shared-auth", "UA-a");
        assertOwnerForEndpoint(endpoint, userA);

        // Same physical device/browser re-subscribing -- same p256dh/auth keys, different user logged in.
        pushSubscriptionService.subscribe(userB, endpoint, "shared-p256dh", "shared-auth", "UA-b");

        assertRowCountForEndpoint(endpoint, 1); // still one row -- moved, never duplicated
        assertOwnerForEndpoint(endpoint, userB);
        assertP256dhForEndpoint(endpoint, "shared-p256dh");
    }

    /**
     * The hijack case {@code PushSubscriptionService#subscribe} now guards against: knowing another
     * user's {@code endpoint} alone (e.g. leaked via logs) is not proof of possession -- without the
     * matching {@code p256dh}/{@code auth} keys, the reassignment must be rejected with 409 and the
     * victim's row must survive completely untouched.
     */
    @Test
    void pushSubscription_reSubscribeFromAnotherUserWithMismatchedKeys_rejectsWithConflictThroughTheRealDatabase() throws SQLException {
        UUID victim = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        String endpoint = "https://push.example/hijack-attempt/" + UUID.randomUUID();

        pushSubscriptionService.subscribe(victim, endpoint, "victim-p256dh", "victim-auth", "UA-victim");
        assertOwnerForEndpoint(endpoint, victim);

        assertThatThrownBy(() ->
                        pushSubscriptionService.subscribe(attacker, endpoint, "attacker-p256dh", "attacker-auth", "UA-attacker"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertRowCountForEndpoint(endpoint, 1); // untouched -- no duplicate, no takeover
        assertOwnerForEndpoint(endpoint, victim);
        assertP256dhForEndpoint(endpoint, "victim-p256dh");
    }

    /**
     * The centerpiece of {@code DELETE /api/account} ({@link AccountService}): seeds BOTH users
     * across every one of the 11 per-user tables plus a minimal {@code auth.users} (this
     * container's own migrations never create Supabase's real one — see {@code
     * com.kcalma.account.AuthUserGateway}), deletes user A's account, then proves every one of A's
     * rows — app-schema AND {@code auth.users} — is gone while every one of B's survives untouched.
     */
    @Test
    void accountService_deleteAccount_removesEveryTableRowAndTheAuthUserForOneUser_leavingAnotherUserIntact() throws SQLException {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 9, 25);
        LocalDate weekStart = LocalDate.of(2026, 9, 21);

        createAuthUsersTableIfMissing();
        insertAuthUser(userA, "user-a@example.test");
        insertAuthUser(userB, "user-b@example.test");

        seedOneRowPerTableForAccountDeletion(userA, today, weekStart, "milanesa-a");
        seedOneRowPerTableForAccountDeletion(userB, today, weekStart, "milanesa-b");

        accountService.deleteAccount(userA);

        assertThat(userProfileRepository.findById(userA)).isEmpty();
        assertThat(userProfileRepository.findById(userB)).isPresent();

        assertThat(foodEntryRepository.findByUserIdAndEntryDateOrderByCreatedAtAsc(userA, today)).isEmpty();
        assertThat(foodEntryRepository.findByUserIdAndEntryDateOrderByCreatedAtAsc(userB, today)).hasSize(1);

        assertThat(weightEntryRepository.findByUserIdOrderByEntryDateAsc(userA)).isEmpty();
        assertThat(weightEntryRepository.findByUserIdOrderByEntryDateAsc(userB)).hasSize(1);

        assertThat(userFoodRepository.findByUserIdAndNormalizedName(userA, "milanesa-a")).isEmpty();
        assertThat(userFoodRepository.findByUserIdAndNormalizedName(userB, "milanesa-b")).isPresent();

        assertThat(favoriteDishRepository.findByUserIdOrderByCreatedAtDesc(userA)).isEmpty();
        assertThat(favoriteDishRepository.findByUserIdOrderByCreatedAtDesc(userB)).hasSize(1);

        assertThat(waterLogRepository.findByUserIdOrderByEntryDateAsc(userA)).isEmpty();
        assertThat(waterLogRepository.findByUserIdOrderByEntryDateAsc(userB)).hasSize(1);

        assertThat(measurementRepository.findByUserIdOrderByMeasuredOnAsc(userA)).isEmpty();
        assertThat(measurementRepository.findByUserIdOrderByMeasuredOnAsc(userB)).hasSize(1);

        assertThat(checkinRepository.findByUserIdAndWeekStart(userA, weekStart)).isEmpty();
        assertThat(checkinRepository.findByUserIdAndWeekStart(userB, weekStart)).isPresent();

        assertRowCountForEndpoint(accountDeletionEndpointFor(userA), 0);
        assertRowCountForEndpoint(accountDeletionEndpointFor(userB), 1);

        assertThat(reminderSettingsRepository.findById(userA)).isEmpty();
        assertThat(reminderSettingsRepository.findById(userB)).isPresent();

        // No repository read beyond claim() exists for reminder_log (see ReminderLogRepository) --
        // re-claiming the exact same occurrence lands (1) where the row is gone, and loses (0) where
        // it's still there.
        assertThat(reminderLogRepository.claim(userA, "MEAL_ALMUERZO", today)).isEqualTo(1);
        assertThat(reminderLogRepository.claim(userB, "MEAL_ALMUERZO", today)).isEqualTo(0);

        assertThat(authUserExists(userA)).isFalse();
        assertThat(authUserExists(userB)).isTrue();
    }

    /** Every one of the 11 per-user tables gets exactly one row for {@code userId}, all dated/keyed consistently. */
    private void seedOneRowPerTableForAccountDeletion(UUID userId, LocalDate today, LocalDate weekStart, String normalizedName) {
        UserProfile profile = new UserProfile(userId);
        profile.setSex(Sex.FEMALE);
        profile.setBirthDate(LocalDate.of(1990, 1, 1));
        profile.setHeightCm(165);
        profile.setWeightKg(new BigDecimal("65.00"));
        profile.setActivityLevel(ActivityLevel.LIGHTLY_ACTIVE);
        profile.setGoal(Goal.MAINTAIN);
        userProfileRepository.save(profile);

        foodEntryRepository.save(new FoodEntry(
                userId, today, MealType.ALMUERZO, normalizedName, new BigDecimal("100.00"), new BigDecimal("200.00"),
                new BigDecimal("10.00"), new BigDecimal("5.00"), new BigDecimal("20.00"), new BigDecimal("2.00"),
                new BigDecimal("1.00"), new BigDecimal("50.00"), FoodSource.MANUAL, null, null));

        weightEntryRepository.save(new WeightEntry(userId, today, new BigDecimal("65.00")));

        userFoodRepository.save(new UserFood(
                userId, normalizedName, normalizedName, new NutritionMath.Per100(200, 10, 5, 20, 2, 1, 50), UserFoodSource.USER, null));

        FavoriteDish favorite = new FavoriteDish(userId, normalizedName);
        favorite.applyDish(
                MealType.ALMUERZO, normalizedName, new BigDecimal("100.00"), new BigDecimal("200.00"), new BigDecimal("10.00"),
                new BigDecimal("5.00"), new BigDecimal("20.00"), new BigDecimal("2.00"), new BigDecimal("1.00"),
                new BigDecimal("50.00"), FoodSource.MANUAL, null, null);
        favoriteDishRepository.save(favorite);

        waterLogRepository.save(new WaterLog(userId, today, 500));

        BodyMeasurement measurement = new BodyMeasurement(userId, today);
        measurement.setWaistCm(new BigDecimal("80.00"));
        measurementRepository.save(measurement);

        TdeeCheckin checkin = new TdeeCheckin(userId, weekStart);
        checkin.recompute(
                weekStart.minusDays(21), weekStart.minusDays(1), 2500,
                new TdeeAdaptationCalculator.Result(CheckinStatus.PENDING, 15, 8, 2100, -0.5, 2500, 2500, Confidence.MEDIUM, List.of()));
        checkinRepository.save(checkin);

        pushSubscriptionService.subscribe(userId, accountDeletionEndpointFor(userId), "p256dh", "auth-key", "UA");

        reminderSettingsRepository.save(new ReminderSettings(userId, ReminderSettingsData.defaults()));

        reminderLogRepository.claim(userId, "MEAL_ALMUERZO", today);
    }

    private static String accountDeletionEndpointFor(UUID userId) {
        return "https://push.example/account-deletion/" + userId;
    }

    private void createAuthUsersTableIfMissing() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE SCHEMA IF NOT EXISTS auth");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS auth.users (id uuid PRIMARY KEY, email text)");
        }
    }

    private void insertAuthUser(UUID id, String email) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                java.sql.PreparedStatement statement =
                        connection.prepareStatement("INSERT INTO auth.users (id, email) VALUES (?, ?)")) {
            statement.setObject(1, id);
            statement.setString(2, email);
            statement.executeUpdate();
        }
    }

    private boolean authUserExists(UUID id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                java.sql.PreparedStatement statement =
                        connection.prepareStatement("SELECT 1 FROM auth.users WHERE id = ?")) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void assertOwnerForEndpoint(String endpoint, UUID expectedUserId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                java.sql.PreparedStatement statement =
                        connection.prepareStatement("SELECT user_id FROM app.push_subscription WHERE endpoint = ?")) {
            statement.setString(1, endpoint);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getObject("user_id", UUID.class)).isEqualTo(expectedUserId);
            }
        }
    }
}
