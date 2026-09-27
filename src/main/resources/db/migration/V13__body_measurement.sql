-- Sprint 2b: optional body measurements (waist/hip/chest/arm/thigh + body fat % + muscle mass),
-- at most one row per user per calendar day, same shape as app.weight_entry (V3__weight_entry.sql):
-- surrogate UUID id + UNIQUE (user_id, measured_on) rather than a composite PK, since (unlike
-- app.water_log) callers address rows by id too (no update-by-date-only requirement). Every column
-- is individually optional (a user might only track waist + body fat) — the "at least one field
-- required" rule lives in application code (com.kcalma.measurement.dto.UpsertMeasurementRequest),
-- since a plain CHECK can't express "at least one of these 7 columns is non-null" cleanly.
--
-- Range CHECKs mirror the Bean Validation bounds on UpsertMeasurementRequest exactly, same
-- defense-in-depth already used for user_profile.body_fat_pct (V10__richer_goals_and_protein_basis.sql)
-- — a value the API accepts can never be one the DB then rejects, or vice versa.
CREATE TABLE app.body_measurement (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    measured_on     DATE NOT NULL,
    waist_cm        NUMERIC(5, 2) CHECK (waist_cm IS NULL OR (waist_cm >= 30.0 AND waist_cm <= 250.0)),
    hip_cm          NUMERIC(5, 2) CHECK (hip_cm IS NULL OR (hip_cm >= 30.0 AND hip_cm <= 250.0)),
    chest_cm        NUMERIC(5, 2) CHECK (chest_cm IS NULL OR (chest_cm >= 30.0 AND chest_cm <= 250.0)),
    arm_cm          NUMERIC(5, 2) CHECK (arm_cm IS NULL OR (arm_cm >= 10.0 AND arm_cm <= 100.0)),
    thigh_cm        NUMERIC(5, 2) CHECK (thigh_cm IS NULL OR (thigh_cm >= 10.0 AND thigh_cm <= 150.0)),
    body_fat_pct    NUMERIC(4, 1) CHECK (body_fat_pct IS NULL OR (body_fat_pct >= 3.0 AND body_fat_pct <= 70.0)),
    muscle_mass_kg  NUMERIC(5, 2) CHECK (muscle_mass_kg IS NULL OR (muscle_mass_kg >= 5.0 AND muscle_mass_kg <= 150.0)),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, measured_on)
);

CREATE INDEX idx_body_measurement_user_date ON app.body_measurement (user_id, measured_on);

-- No explicit REVOKE here — see V4__enable_pg_trgm_and_food_reference.sql.
