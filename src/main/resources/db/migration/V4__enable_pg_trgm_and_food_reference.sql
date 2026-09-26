-- Trigram similarity backs the USDA food-reference lookup (see
-- com.kcalma.food.reference.FoodReferenceMatcher). On Supabase, extensions live in their own
-- "extensions" schema, never "public" — every pg_trgm operator/function is qualified explicitly
-- at the call site (extensions.similarity(...), extensions.gin_trgm_ops) instead of relying on
-- search_path, since the app's runtime DB session is never guaranteed to have "extensions" on it.
create extension if not exists pg_trgm with schema extensions;

-- Read-only USDA reference data (SR Legacy + Foundation Foods + FNDDS survey foods), loaded by
-- the V5__LoadFoodReferenceData Flyway Java migration from the gzipped CSV bundled at
-- src/main/resources/usda/food_reference.csv.gz. See tools/usda/build_food_reference.py to
-- regenerate that file from a fresh FoodData Central download.
CREATE TABLE app.food_reference (
    fdc_id            BIGINT PRIMARY KEY,
    description       TEXT NOT NULL,
    data_type         VARCHAR(20) NOT NULL,
    kcal_per_100      NUMERIC(7, 2) NOT NULL,
    protein_per_100   NUMERIC(7, 2) NOT NULL,
    fat_per_100       NUMERIC(7, 2) NOT NULL,
    carbs_per_100     NUMERIC(7, 2) NOT NULL,
    fiber_per_100     NUMERIC(7, 2) NOT NULL,
    sugar_per_100     NUMERIC(7, 2) NOT NULL,
    sodium_mg_per_100 NUMERIC(8, 2) NOT NULL,
    search_name       TEXT NOT NULL
);

CREATE INDEX idx_food_reference_search_trgm ON app.food_reference USING gin (search_name extensions.gin_trgm_ops);

-- No explicit REVOKE here: the schema-level "REVOKE ALL ON SCHEMA app FROM anon/authenticated" in
-- V1__baseline.sql already covers every table created afterwards (same reasoning as
-- V3__weight_entry.sql).
