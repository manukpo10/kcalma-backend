-- Mimics the parts of a fresh Supabase Postgres project this app's own migrations check for or
-- rely on, which a plain postgres:17 image doesn't have out of the box -- same file (kept in sync
-- by hand; it's a handful of lines) as src/test/resources/testcontainers/init.sql, used by the
-- DatabaseIntegrationTest Testcontainers suite:
--   * an "extensions" schema -- Supabase never installs extensions into "public" (see
--     V4__enable_pg_trgm_and_food_reference.sql, which installs pg_trgm there and every query
--     qualifies extensions.similarity(...)/extensions.gin_trgm_ops explicitly);
--   * the anon/authenticated roles Supabase's Data API uses -- V1__baseline.sql defensively
--     revokes app-schema access from them if they exist (a no-op on plain local Postgres
--     otherwise).
--
-- Runs once, on the volume's first initialization (the official postgres image only executes
-- /docker-entrypoint-initdb.d on an empty data directory) -- see README.md for how to reset it.
CREATE SCHEMA IF NOT EXISTS extensions;
CREATE ROLE anon;
CREATE ROLE authenticated;
