-- Weight tracking: at most one row per user per calendar day. Trend (EMA), weekly rate
-- (regression) and goal projection are all derived at read time (see com.kcalma.progress),
-- never stored redundantly — same philosophy as app.food_entry's per-100g totals.
-- No extra REVOKE here: the schema-level "REVOKE ALL ON SCHEMA app FROM anon/authenticated"
-- in V1__baseline.sql already covers every table created afterwards (V2__food_entry.sql
-- relied on the same thing), so a new table needs no per-table grant/revoke of its own.
CREATE TABLE app.weight_entry (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    entry_date  DATE NOT NULL,
    weight_kg   NUMERIC(5, 2) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, entry_date)
);

CREATE INDEX idx_weight_entry_user_date ON app.weight_entry (user_id, entry_date);

-- Optional target weight, set from the profile screen; NULL means "no goal set yet".
ALTER TABLE app.user_profile ADD COLUMN goal_weight_kg NUMERIC(5, 2);
