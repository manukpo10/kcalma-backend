package com.kcalma.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcalma.checkin.CheckinStatus;
import com.kcalma.checkin.Confidence;
import com.kcalma.checkin.TdeeAdaptationCalculator;
import com.kcalma.checkin.TdeeCheckin;
import com.kcalma.checkin.TdeeCheckinRepository;
import com.kcalma.favorites.FavoriteDish;
import com.kcalma.favorites.FavoriteDishRepository;
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
import com.kcalma.reminder.ReminderSettings;
import com.kcalma.reminder.ReminderSettingsData;
import com.kcalma.reminder.ReminderSettingsRepository;
import com.kcalma.weight.WeightEntry;
import com.kcalma.weight.WeightEntryRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The IDOR audit: two real, distinct users (A and B), each with their own JWT, exercised through
 * the full HTTP stack (real {@link SecurityConfig}, real controllers/services/repositories) against
 * a real, disposable Postgres via Testcontainers — proving B can never read, modify, or delete A's
 * data, and that a foreign id resolves 404 (never 403, so existence never leaks — see the
 * controllers' own {@code findByIdAndUserId} scoping this exercises end to end).
 *
 * <p>Deliberately excludes {@code /api/food/analyze}, {@code /api/food/analyze-text} and {@code
 * /api/suggestions}: none of the three take an id/date referencing another user's resource (they
 * only ever act on the caller's own derived context), so there is no IDOR surface to prove here,
 * and actually invoking them would require a real Gemini network call. Push subscription
 * cross-user behavior (the "shared device" re-claim) is covered in {@link DatabaseIntegrationTest}
 * instead, since verifying it needs the package-private {@code PushSubscriptionRepository}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=",
    "app.security.allowed-origins=http://localhost:5173",
    "app.gemini.api-key=test-key",
    "app.timezone=America/Argentina/Buenos_Aires",
    "kcalma.reminders.scheduler-enabled=false"
})
class CrossUserAccessControlTest {

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

    private static final String TOKEN_A = "token-user-a";
    private static final String TOKEN_B = "token-user-b";
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FoodEntryRepository foodEntryRepository;

    @Autowired
    private FavoriteDishRepository favoriteDishRepository;

    @Autowired
    private WeightEntryRepository weightEntryRepository;

    @Autowired
    private BodyMeasurementRepository measurementRepository;

    @Autowired
    private TdeeCheckinRepository checkinRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private ReminderSettingsRepository reminderSettingsRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setUpTwoUsers() {
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        when(jwtDecoder.decode(TOKEN_A)).thenReturn(jwtFor(userA));
        when(jwtDecoder.decode(TOKEN_B)).thenReturn(jwtFor(userB));
        userProfileRepository.save(minimalProfile(userA));
        userProfileRepository.save(minimalProfile(userB));
    }

    // ---- food entries: PATCH/DELETE by id, bulk delete by meal, copy, list, recent ----

    @Test
    void patchFoodEntry_ownedByAnotherUser_returns404AndNeverChangesIt() throws Exception {
        FoodEntry entryA = foodEntryRepository.save(milanesaFor(userA, TODAY));

        mockMvc.perform(patch("/api/food/entries/" + entryA.getId())
                        .header("Authorization", "Bearer " + TOKEN_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grams\": 500, \"mealType\": \"CENA\"}"))
                .andExpect(status().isNotFound());

        FoodEntry reloaded = foodEntryRepository.findById(entryA.getId()).orElseThrow();
        assertThat(reloaded.getGrams()).isEqualByComparingTo("120.00");
        assertThat(reloaded.getMealType()).isEqualTo(MealType.ALMUERZO);
    }

    @Test
    void deleteFoodEntry_ownedByAnotherUser_returns404AndLeavesItIntact() throws Exception {
        FoodEntry entryA = foodEntryRepository.save(milanesaFor(userA, TODAY));

        mockMvc.perform(delete("/api/food/entries/" + entryA.getId()).header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isNotFound());

        assertThat(foodEntryRepository.findById(entryA.getId())).isPresent();
    }

    @Test
    void bulkDeleteMealEntries_scopedToCaller_neverTouchesAnotherUsersMeal() throws Exception {
        FoodEntry entryA = foodEntryRepository.save(milanesaFor(userA, TODAY));

        // B has nothing on this date/meal -- the bulk delete is a no-op for B, always 204 regardless.
        mockMvc.perform(delete("/api/food/entries")
                        .header("Authorization", "Bearer " + TOKEN_B)
                        .param("date", TODAY.toString())
                        .param("mealType", "ALMUERZO"))
                .andExpect(status().isNoContent());

        assertThat(foodEntryRepository.findById(entryA.getId())).isPresent();
    }

    @Test
    void copyMeal_fromAnotherUsersDate_returns400_cannotPullTheirMealIntoOwnLog() throws Exception {
        foodEntryRepository.save(milanesaFor(userA, TODAY));

        mockMvc.perform(post("/api/food/entries/copy")
                        .header("Authorization", "Bearer " + TOKEN_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromDate\": \"" + TODAY + "\", \"toDate\": \"" + TODAY.plusDays(1) + "\", \"mealType\": \"ALMUERZO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No hay comidas para copiar en ese momento."));
    }

    @Test
    void listFoodEntries_asAnotherUser_neverIncludesTheOwnersEntries() throws Exception {
        foodEntryRepository.save(milanesaFor(userA, TODAY));

        mockMvc.perform(get("/api/food/entries").header("Authorization", "Bearer " + TOKEN_B).param("date", TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void recentDishes_asAnotherUser_neverIncludesTheOwnersDishes() throws Exception {
        foodEntryRepository.save(milanesaFor(userA, TODAY));

        mockMvc.perform(get("/api/food/recent").header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ---- favorites: DELETE by id, list ----

    @Test
    void deleteFavorite_ownedByAnotherUser_returns404AndLeavesItIntact() throws Exception {
        FavoriteDish favoriteA = favoriteDishRepository.save(favoriteFor(userA));

        mockMvc.perform(delete("/api/favorites/" + favoriteA.getId()).header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isNotFound());

        assertThat(favoriteDishRepository.findById(favoriteA.getId())).isPresent();
    }

    @Test
    void listFavorites_asAnotherUser_neverIncludesTheOwnersFavorite() throws Exception {
        favoriteDishRepository.save(favoriteFor(userA));

        mockMvc.perform(get("/api/favorites").header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ---- weights: DELETE by date, list ----

    @Test
    void deleteWeight_dateOnlyOwnedByAnotherUser_returns404AndLeavesItIntact() throws Exception {
        weightEntryRepository.save(new WeightEntry(userA, TODAY, new BigDecimal("70.00")));

        mockMvc.perform(delete("/api/weights/" + TODAY).header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isNotFound());

        assertThat(weightEntryRepository.findByUserIdAndEntryDate(userA, TODAY)).isPresent();
    }

    @Test
    void listWeights_asAnotherUser_neverIncludesTheOwnersEntry() throws Exception {
        weightEntryRepository.save(new WeightEntry(userA, TODAY, new BigDecimal("70.00")));

        mockMvc.perform(get("/api/weights")
                        .header("Authorization", "Bearer " + TOKEN_B)
                        .param("from", TODAY.minusDays(7).toString())
                        .param("to", TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ---- measurements: DELETE by date, list ----

    @Test
    void deleteMeasurement_dateOnlyOwnedByAnotherUser_returns404AndLeavesItIntact() throws Exception {
        BodyMeasurement measurementA = new BodyMeasurement(userA, TODAY);
        measurementA.setWaistCm(new BigDecimal("80.00"));
        measurementRepository.save(measurementA);

        mockMvc.perform(delete("/api/measurements/" + TODAY).header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isNotFound());

        assertThat(measurementRepository.findByUserIdAndMeasuredOn(userA, TODAY)).isPresent();
    }

    @Test
    void listMeasurements_asAnotherUser_neverIncludesTheOwnersEntry() throws Exception {
        BodyMeasurement measurementA = new BodyMeasurement(userA, TODAY);
        measurementA.setWaistCm(new BigDecimal("80.00"));
        measurementRepository.save(measurementA);

        mockMvc.perform(get("/api/measurements")
                        .header("Authorization", "Bearer " + TOKEN_B)
                        .param("from", TODAY.minusDays(7).toString())
                        .param("to", TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ---- check-in: accept/dismiss never mutate another user's week ----

    @Test
    void acceptCheckin_asAnotherUser_neverMutatesTheOwnersPendingWeek() throws Exception {
        LocalDate weekStart = currentWeekStart();
        TdeeCheckin pendingForA = checkinRepository.save(pendingCheckinFor(userA, weekStart));
        assertThat(pendingForA.getStatus()).isEqualTo(CheckinStatus.PENDING);

        mockMvc.perform(post("/api/checkin/accept").header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isBadRequest()); // B has no pending proposal of their own

        TdeeCheckin reloaded = checkinRepository.findByUserIdAndWeekStart(userA, weekStart).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CheckinStatus.PENDING);
        assertThat(reloaded.getAppliedTdee()).isNull();
    }

    @Test
    void dismissCheckin_asAnotherUser_neverMutatesTheOwnersPendingWeek() throws Exception {
        LocalDate weekStart = currentWeekStart();
        checkinRepository.save(pendingCheckinFor(userA, weekStart));

        mockMvc.perform(post("/api/checkin/dismiss").header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isBadRequest()); // B has no pending proposal of their own

        TdeeCheckin reloaded = checkinRepository.findByUserIdAndWeekStart(userA, weekStart).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CheckinStatus.PENDING);
        assertThat(reloaded.getDecidedAt()).isNull();
    }

    // ---- reminders: GET never leaks another user's saved settings ----

    @Test
    void getReminderSettings_asAnotherUser_returnsDefaultsNeverTheOwnersSavedSettings() throws Exception {
        ReminderSettingsData defaults = ReminderSettingsData.defaults();
        ReminderSettingsData customizedForA = new ReminderSettingsData(true, defaults.meals(), defaults.water(), defaults.weighIn());
        reminderSettingsRepository.save(new ReminderSettings(userA, customizedForA));

        mockMvc.perform(get("/api/reminders").header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    // ---- export: never includes another user's records ----

    @Test
    void export_asAnotherUser_neverIncludesTheOwnersRecords() throws Exception {
        foodEntryRepository.save(milanesaFor(userA, TODAY));
        weightEntryRepository.save(new WeightEntry(userA, TODAY, new BigDecimal("70.00")));

        mockMvc.perform(get("/api/export").header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foodEntries").isArray())
                .andExpect(jsonPath("$.foodEntries").isEmpty())
                .andExpect(jsonPath("$.weights").isArray())
                .andExpect(jsonPath("$.weights").isEmpty());
    }

    // ---- day: never reflects another user's logged food ----

    @Test
    void day_asAnotherUser_neverReflectsTheOwnersLoggedFood() throws Exception {
        foodEntryRepository.save(milanesaFor(userA, TODAY));

        mockMvc.perform(get("/api/day").header("Authorization", "Bearer " + TOKEN_B).param("date", TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumed.kcal").value(0))
                .andExpect(jsonPath("$.meals.ALMUERZO").isArray())
                .andExpect(jsonPath("$.meals.ALMUERZO").isEmpty());
    }

    // ---- progress: never reflects another user's weigh-ins ----

    @Test
    void progress_asAnotherUser_neverReflectsTheOwnersWeighIns() throws Exception {
        weightEntryRepository.save(new WeightEntry(userA, TODAY, new BigDecimal("70.00")));

        mockMvc.perform(get("/api/progress").header("Authorization", "Bearer " + TOKEN_B).param("range", "1M"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weights").isArray())
                .andExpect(jsonPath("$.weights").isEmpty());
    }

    /** A real PENDING week (window_start/window_end are NOT NULL — see V14__tdee_checkin.sql) — {@link TdeeCheckin}'s bare constructor alone isn't a persistable row. */
    private static TdeeCheckin pendingCheckinFor(UUID userId, LocalDate weekStart) {
        TdeeCheckin checkin = new TdeeCheckin(userId, weekStart);
        checkin.recompute(
                weekStart.minusDays(21), weekStart.minusDays(1), 2500,
                new TdeeAdaptationCalculator.Result(CheckinStatus.PENDING, 15, 8, 2100, -0.5, 2500, 2500, Confidence.MEDIUM, List.of()));
        return checkin;
    }

    private static FoodEntry milanesaFor(UUID userId, LocalDate date) {
        return new FoodEntry(
                userId, date, MealType.ALMUERZO, "Milanesa", new BigDecimal("120.00"), new BigDecimal("250.00"),
                new BigDecimal("26.00"), new BigDecimal("15.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("70.00"), FoodSource.MANUAL, null, null);
    }

    private static FavoriteDish favoriteFor(UUID userId) {
        FavoriteDish favorite = new FavoriteDish(userId, "milanesa");
        favorite.applyDish(
                MealType.ALMUERZO, "Milanesa", new BigDecimal("120.00"), new BigDecimal("250.00"), new BigDecimal("26.00"),
                new BigDecimal("15.00"), new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("70.00"), FoodSource.MANUAL, null, null);
        return favorite;
    }

    private static UserProfile minimalProfile(UUID userId) {
        UserProfile profile = new UserProfile(userId);
        profile.setSex(Sex.FEMALE);
        profile.setBirthDate(LocalDate.of(1990, 1, 1));
        profile.setHeightCm(165);
        profile.setWeightKg(new BigDecimal("65.00"));
        profile.setActivityLevel(ActivityLevel.LIGHTLY_ACTIVE);
        profile.setGoal(Goal.MAINTAIN);
        return profile;
    }

    /**
     * Same rule as {@code CheckinService#currentWeekStart}: always a Monday, zoned to {@code
     * app.timezone} (see {@code ClockConfig}) -- computed from the real current date (this test
     * uses the real {@link java.time.Clock} bean, not a fixed one) so it always matches whatever
     * week {@code POST /api/checkin/accept}/{@code dismiss} actually look up, regardless of what
     * day this suite runs on.
     */
    private static LocalDate currentWeekStart() {
        return LocalDate.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires")).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static Jwt jwtFor(UUID subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token-for-" + subject)
                .header("alg", "ES256")
                .subject(subject.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("aud", "authenticated")
                .build();
    }
}
