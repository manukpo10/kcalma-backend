package com.kcalma.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.kcalma.water.WaterLog;
import com.kcalma.water.WaterLogRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
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
    "app.gemini.api-key=test-key"
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

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void flyway_migratesEveryVersionUpToV14Successfully() throws SQLException {
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
        assertThat(versions).contains("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14");
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
}
