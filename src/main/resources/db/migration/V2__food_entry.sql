-- Food log: one row per logged food item (from a photo analysis or manual entry).
-- Nutrient columns are stored per 100 g; totals are computed at read time as
-- per100 * grams / 100 (see com.kcalma.food.NutritionMath), never stored redundantly.
CREATE TABLE app.food_entry (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL,
    entry_date        DATE NOT NULL,
    meal_type         VARCHAR(20) NOT NULL,
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
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_food_entry_user_date ON app.food_entry (user_id, entry_date);
