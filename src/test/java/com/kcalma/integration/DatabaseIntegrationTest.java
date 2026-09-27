package com.kcalma.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
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
 * clean, disposable Postgres 17 container — running every migration V1..V9 from scratch — then
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

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void flyway_migratesEveryVersionUpToV9Successfully() throws SQLException {
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
        assertThat(versions).contains("1", "2", "3", "4", "5", "6", "7", "8", "9");
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
}
