-- Sprint 3a: adaptive TDEE weekly check-in (MacroFactor-style). One row per user per ISO week
-- (week_start is always a Monday, zoned to app.timezone -- see com.kcalma.checkin.CheckinService).
-- A week is "open" while status is PENDING or INSUFFICIENT_DATA -- recomputed against the latest
-- 21-day rolling window on every GET /api/checkin -- and "closed" once the user accepts or
-- dismisses it: the row then becomes a frozen snapshot, never recomputed again (see
-- CheckinService#currentWeek).
--
-- applied_tdee/applied_activity_level are set together, only on accept: applied_tdee becomes the
-- new "current" adaptive TDEE that com.kcalma.profile.ProfileService blends into targets instead
-- of the Mifflin x activity formula (see com.kcalma.checkin.AdaptiveTdeeService), and
-- applied_activity_level snapshots the profile's activity level at that moment so a later change
-- to it can be detected -- "changing activity level resets targets to FORMULA until the next
-- accepted check-in" needs something to compare against, since app.user_profile itself keeps no
-- history of past activity levels.
--
-- Every metric column is nullable: an INSUFFICIENT_DATA week still stores the window/complete_days
-- /weigh_ins/formula_tdee it found (useful in GET /api/checkin/history), but never an estimate,
-- proposal or confidence, since the algorithm never computes those without enough data (see
-- com.kcalma.checkin.TdeeAdaptationCalculator). window_start/window_end are the exception -- the
-- rolling window itself is always computable from "today" alone, regardless of data sufficiency.
CREATE TABLE app.tdee_checkin (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL,
    week_start              DATE NOT NULL,
    status                  VARCHAR(20) NOT NULL
                                CHECK (status IN ('PENDING', 'ACCEPTED', 'DISMISSED', 'INSUFFICIENT_DATA')),
    window_start            DATE NOT NULL,
    window_end              DATE NOT NULL,
    complete_days           INTEGER,
    weigh_ins               INTEGER,
    avg_intake_kcal         INTEGER,
    trend_change_kg         NUMERIC(6, 2),
    formula_tdee            INTEGER,
    estimated_tdee          INTEGER,
    proposed_tdee           INTEGER,
    applied_tdee            INTEGER,
    applied_activity_level  VARCHAR(30),
    confidence              VARCHAR(10) CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW')),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at              TIMESTAMPTZ,
    UNIQUE (user_id, week_start)
);

CREATE INDEX idx_tdee_checkin_user_week ON app.tdee_checkin (user_id, week_start);

-- No explicit REVOKE here -- see V4__enable_pg_trgm_and_food_reference.sql.
