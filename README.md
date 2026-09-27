# Kcalma API

Spring Boot backend for Kcalma: food logging with photo/text analysis (Gemini), weight tracking
with an EMA trend, daily nutrition targets, and meal suggestions. Java 21, Spring Boot 4, Hibernate
7, Flyway, Postgres (Supabase in production).

## Requirements

- JDK 21
- Docker Desktop (for the local database and the Testcontainers integration test — both are
  optional but recommended; see below)

## Configuration

Copy `env.example` to `.env` and fill in real values. `.env` is gitignored — never commit it.

**The committed `.env` on this project points at the production Supabase database.** For anything
other than a quick one-off check against production, run against the local database instead (see
below) by overriding `DB_URL`/`DB_USER`/`DB_PASSWORD` — this is much harder to do by accident than
it sounds, since the override only lasts for the single command it's prefixed to.

## Running locally against a local database (recommended)

`docker-compose.yml` starts a disposable Postgres 17 on `localhost:5433` (not 5432, so it doesn't
clash with a Postgres already installed on your machine) with the same `extensions` schema /
`anon`/`authenticated` roles Supabase has and this app's migrations check for
(`docker/init/01-init-extensions-and-roles.sql`).

```bash
docker compose up -d
```

Then run the app with `DB_URL`/`DB_USER`/`DB_PASSWORD` overriding whatever `.env` has (every other
`.env` value — `SUPABASE_URL`, `GEMINI_API_KEY`, etc. — is still used normally):

```bash
# bash / zsh
DB_URL="jdbc:postgresql://localhost:5433/kcalma" DB_USER=kcalma DB_PASSWORD=kcalma \
  ./mvnw spring-boot:run
```

```powershell
# PowerShell
$env:DB_URL="jdbc:postgresql://localhost:5433/kcalma"; $env:DB_USER="kcalma"; $env:DB_PASSWORD="kcalma"
./mvnw spring-boot:run
```

Flyway runs every migration from scratch against the fresh container on first startup. To reset
the local database entirely (re-run every migration from zero): `docker compose down -v` (the `-v`
drops the named volume) then `docker compose up -d` again.

Stop it with `docker compose down` (keeps the data) or `docker compose down -v` (also deletes it).

## Tests

```bash
./mvnw test
```

Most tests are plain unit tests or Spring web slices (`@WebMvcTest`) and need nothing extra.
`DatabaseIntegrationTest` additionally runs every Flyway migration and round-trips JPA against a
real, disposable Postgres 17 container (via Testcontainers) — it needs Docker running, but is
annotated `@Testcontainers(disabledWithoutDocker = true)`, so the rest of the suite still passes
on a machine without Docker; that one test class is simply skipped instead of failing.

## Database backups

`.github/workflows/db-backup.yml` runs a daily `pg_dump` of the production Supabase database
(schema `app` only) and uploads it as a workflow artifact, retained for 30 days. It also runs
on-demand from the Actions tab (`workflow_dispatch`).

**Setup (repository owner, one-time):** add a repository secret named `SUPABASE_DB_URL` with the
Supabase **session pooler** connection string, including the password — Dashboard → Connect →
ORMs/JDBC → "Session pooler" (port 5432; the same kind of URL `DB_URL` in `.env` uses, just with
its own dedicated secret rather than reusing that one). The workflow never echoes this value.

**Restoring a backup:**

1. Download the artifact from the workflow run (Actions tab → the run → Artifacts) and unzip it —
   you'll get a single `.dump` file (custom-format `pg_dump` output).
2. Restore it into a database with `pg_restore` (install the matching `postgresql-client-17` if
   you don't have it):

   ```bash
   pg_restore --dbname="<target-connection-string>" --schema=app --no-owner --no-privileges --clean <file>.dump
   ```

   - Point `--dbname` at a **throwaway/local** database first if you're not certain about the
     restore — `--clean` drops the existing `app` schema's objects before recreating them.
   - `--no-owner --no-privileges` avoids failing on role names that don't exist in the target
     database (mirrors the flags `pg_dump` was run with).
3. Verify the row counts you expect are there, then Flyway will happily continue from that state —
   `flyway_schema_history` is part of schema `app` and is included in the dump.
