-- Sprint 2b: favorite dishes. A favorite is a snapshot of a Dish (same shape as
-- com.kcalma.food.dto.AnalyzedDishResponse / app.food_entry) the user pinned for one-tap re-logging,
-- upserted by normalized name per user (see com.kcalma.favorites.FavoriteDishService) — same
-- upsert-by-normalized-name shape as app.user_food (V6__user_food.sql), just keyed to the whole
-- dish rather than one ingredient. mealType is an optional hint ("always log this as breakfast"),
-- not a requirement — a favorite can be saved with no particular meal in mind.
CREATE TABLE app.favorite_dish (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL,
    normalized_name   TEXT NOT NULL,
    meal_type         VARCHAR(20),
    name              TEXT NOT NULL,
    grams             NUMERIC(7, 2) NOT NULL,
    kcal_per_100      NUMERIC(7, 2) NOT NULL,
    protein_per_100   NUMERIC(7, 2) NOT NULL,
    fat_per_100       NUMERIC(7, 2) NOT NULL,
    carbs_per_100     NUMERIC(7, 2) NOT NULL,
    fiber_per_100     NUMERIC(7, 2) NOT NULL,
    sugar_per_100     NUMERIC(7, 2) NOT NULL,
    sodium_mg_per_100 NUMERIC(8, 2) NOT NULL,
    source            VARCHAR(10) NOT NULL,
    fdc_id            BIGINT REFERENCES app.food_reference (fdc_id),
    ingredients       jsonb,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, normalized_name),
    -- Same allowed values as app.food_entry.source (V9__food_entry_ingredients.sql) — a favorite is
    -- always a snapshot of a real dish, so its source vocabulary can never be wider than the one
    -- feeding it.
    CONSTRAINT favorite_dish_source_check CHECK (source IN ('PERSONAL', 'USDA', 'ESTIMATED', 'MANUAL', 'MIXED'))
);

CREATE INDEX idx_favorite_dish_user ON app.favorite_dish (user_id);

-- No explicit REVOKE here — see V4__enable_pg_trgm_and_food_reference.sql: the schema-level
-- REVOKE ALL ON SCHEMA app FROM anon/authenticated in V1__baseline.sql already covers it.
