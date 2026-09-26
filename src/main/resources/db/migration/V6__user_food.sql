-- Personal food library: priority (1) in com.kcalma.food.reference.FoodReferenceMatcher. Seeded
-- and refreshed whenever the user saves a food entry (see com.kcalma.food.FoodEntryService).
CREATE TABLE app.user_food (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL,
    normalized_name   TEXT NOT NULL,
    display_name      TEXT NOT NULL,
    kcal_per_100      NUMERIC(7, 2) NOT NULL,
    protein_per_100   NUMERIC(7, 2) NOT NULL,
    fat_per_100       NUMERIC(7, 2) NOT NULL,
    carbs_per_100     NUMERIC(7, 2) NOT NULL,
    fiber_per_100     NUMERIC(7, 2) NOT NULL,
    sugar_per_100     NUMERIC(7, 2) NOT NULL,
    sodium_mg_per_100 NUMERIC(8, 2) NOT NULL,
    source            VARCHAR(10) NOT NULL,
    fdc_id            BIGINT REFERENCES app.food_reference (fdc_id),
    use_count         INTEGER NOT NULL DEFAULT 1,
    last_used_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, normalized_name)
);

CREATE INDEX idx_user_food_user ON app.user_food (user_id);
CREATE INDEX idx_user_food_search_trgm ON app.user_food USING gin (normalized_name extensions.gin_trgm_ops);

-- No explicit REVOKE here either — see V4__enable_pg_trgm_and_food_reference.sql.
