-- Sprint 2a: richer goals (objective + pace + diet style + restrictions + strength training) and
-- protein based on lean mass/adjusted weight/body weight (see com.kcalma.profile.NutritionCalculator).
-- Backward compatible: every row that existed before this migration must still be valid after it,
-- with no manual backfill required.

-- 1) Expand the goal vocabulary. The old LOSE/GAIN become the more specific LOSE_WEIGHT/GAIN_WEIGHT
--    (LOSE_FAT, RECOMP and BUILD_MUSCLE are new); MAINTAIN is unchanged. Update the data BEFORE
--    adding the CHECK constraint, same order V7/V8 used for app.food_entry.source.
UPDATE app.user_profile SET goal = 'LOSE_WEIGHT' WHERE goal = 'LOSE';
UPDATE app.user_profile SET goal = 'GAIN_WEIGHT' WHERE goal = 'GAIN';

ALTER TABLE app.user_profile
    ADD CONSTRAINT user_profile_goal_check
    CHECK (goal IN ('LOSE_FAT', 'LOSE_WEIGHT', 'RECOMP', 'MAINTAIN', 'BUILD_MUSCLE', 'GAIN_WEIGHT'));

-- 2) Pace: application-required for every goal except RECOMP/MAINTAIN (see Goal#requiresPace()).
--    Every pre-existing row is LOSE_WEIGHT/GAIN_WEIGHT or MAINTAIN (the only goals that existed
--    before) -- backfill MODERATE for the two that now require one so no legacy row is left in a
--    state the application would reject if it were ever re-submitted unchanged.
ALTER TABLE app.user_profile ADD COLUMN pace VARCHAR(10);

UPDATE app.user_profile SET pace = 'MODERATE' WHERE goal IN ('LOSE_WEIGHT', 'GAIN_WEIGHT');

ALTER TABLE app.user_profile
    ADD CONSTRAINT user_profile_pace_check
    CHECK (pace IS NULL OR pace IN ('SLOW', 'MODERATE', 'FAST'));

-- 3) Diet style: every existing row keeps eating however it already does -> BALANCED (the "no
--    special macro split" style) is a safe, non-lossy default that needs no backfill logic.
ALTER TABLE app.user_profile ADD COLUMN diet_style VARCHAR(20) NOT NULL DEFAULT 'BALANCED';

ALTER TABLE app.user_profile
    ADD CONSTRAINT user_profile_diet_style_check
    CHECK (diet_style IN ('BALANCED', 'HIGH_PROTEIN', 'LOW_CARB', 'KETO'));

-- 4) Dietary restrictions: a real Postgres array (not jsonb) specifically so its elements can be
--    validated with a plain CHECK -- Postgres CHECK constraints cannot contain subqueries, which
--    rules out validating jsonb array elements (e.g. via jsonb_array_elements_text) here. The `<@`
--    (contained by) operator needs no subquery at all.
ALTER TABLE app.user_profile ADD COLUMN dietary_restrictions TEXT[] NOT NULL DEFAULT '{}';

ALTER TABLE app.user_profile
    ADD CONSTRAINT user_profile_dietary_restrictions_check
    CHECK (dietary_restrictions <@ ARRAY['VEGETARIAN', 'VEGAN', 'GLUTEN_FREE', 'LACTOSE_FREE']::text[]);

-- 5) Strength training: unknown for every pre-existing row -> default to false (the more
--    conservative assumption -- NutritionCalculator only uses this to ADD a recommendation note,
--    never to change a target, so a false negative here costs nothing but a missed tip).
ALTER TABLE app.user_profile ADD COLUMN strength_training BOOLEAN NOT NULL DEFAULT false;

-- 6) Body fat %: optional everywhere, so it's simply unknown (NULL) for every pre-existing row --
--    NutritionCalculator falls back to the BMI-adjusted-weight/body-weight protein basis for those.
ALTER TABLE app.user_profile ADD COLUMN body_fat_pct NUMERIC(4, 1);

ALTER TABLE app.user_profile
    ADD CONSTRAINT user_profile_body_fat_pct_check
    CHECK (body_fat_pct IS NULL OR (body_fat_pct >= 3.0 AND body_fat_pct <= 70.0));

ALTER TABLE app.user_profile ADD COLUMN body_fat_measured_on DATE;
