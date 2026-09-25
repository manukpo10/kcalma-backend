-- Kcalma baseline schema: dedicated "app" schema, not exposed via the Supabase Data API.
CREATE SCHEMA IF NOT EXISTS app;
COMMENT ON SCHEMA app IS 'Kcalma application schema (not exposed to the Supabase Data API)';

-- Lock the schema down from Supabase's Data API roles if they exist on this project.
-- No-op on a plain local/dev Postgres where those roles are never created.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE 'REVOKE ALL ON SCHEMA app FROM anon';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        EXECUTE 'REVOKE ALL ON SCHEMA app FROM authenticated';
    END IF;
END
$$;

CREATE TABLE app.user_profile (
    user_id         UUID PRIMARY KEY,
    sex             VARCHAR(10) NOT NULL,
    birth_date      DATE NOT NULL,
    height_cm       INTEGER NOT NULL,
    weight_kg       NUMERIC(5, 2) NOT NULL,
    activity_level  VARCHAR(30) NOT NULL,
    goal            VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
