-- Sprint 2b: water logging. One row per user per calendar day, keyed directly by (user_id,
-- entry_date) — unlike app.weight_entry/app.food_entry there's no independent identity worth a
-- surrogate id for a single running total per day (see com.kcalma.water.WaterLog, @IdClass).
-- ml is the day's running total (POST /api/water applies a signed delta and floors it at 0 in
-- application code — see com.kcalma.water.WaterLogService — never stored negative).
CREATE TABLE app.water_log (
    user_id     UUID NOT NULL,
    entry_date  DATE NOT NULL,
    ml          INTEGER NOT NULL CHECK (ml >= 0),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, entry_date)
);

-- No explicit REVOKE here — see V4__enable_pg_trgm_and_food_reference.sql.
