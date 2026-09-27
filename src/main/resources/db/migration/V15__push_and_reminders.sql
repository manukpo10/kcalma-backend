-- Sprint 3b: smart reminders via Web Push.
--
-- push_config: the VAPID (RFC 8292) EC P-256 key pair this server signs every push message with.
-- A true singleton -- CHECK (id = 1) makes a second row impossible at the DB level, not just by
-- convention (see com.kcalma.push.VapidKeyService, which generates and inserts this row itself on
-- first use if VAPID_PUBLIC_KEY/VAPID_PRIVATE_KEY aren't set and no row exists yet). Both keys are
-- base64url of the RAW wire encodings com.kcalma.push.EcKeys uses (65-byte uncompressed point /
-- 32-byte scalar), never PEM/DER -- private_key is never logged by application code, see
-- VapidKeyService's javadoc.
CREATE TABLE app.push_config (
    id          SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    public_key  TEXT NOT NULL,
    private_key TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- push_subscription: one row per browser PushSubscription the owner has granted. endpoint is
-- globally UNIQUE (it already encodes the push service + a per-subscription id), so
-- POST /api/push/subscriptions is an upsert keyed on it (see com.kcalma.push.PushSubscriptionService).
-- p256dh/auth are stored exactly as the browser sent them (base64url) -- decoded only at send time.
CREATE TABLE app.push_subscription (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    endpoint    TEXT NOT NULL,
    p256dh      TEXT NOT NULL,
    auth        TEXT NOT NULL,
    user_agent  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (endpoint)
);

CREATE INDEX idx_push_subscription_user ON app.push_subscription (user_id);

-- reminder_settings: one row per user, jsonb because the shape (which meals, water window, weigh-in
-- days) is read/written as one whole document by GET/PUT /api/reminders and never queried by its
-- internal fields -- see com.kcalma.reminder.ReminderSettingsData. Absent for a user who has never
-- called PUT: com.kcalma.reminder.ReminderSettingsService then serves ReminderSettingsData.defaults()
-- (master switch OFF) without writing a row, so "never configured" and "explicitly disabled" don't
-- need separate representations.
CREATE TABLE app.reminder_settings (
    user_id     UUID PRIMARY KEY,
    settings    JSONB NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- reminder_log: dedupe ledger so a restart or two overlapping @Scheduled runs never double-send
-- the same reminder occurrence. reminder_key is specific enough to identify ONE occurrence on ONE
-- day -- "MEAL_ALMUERZO" (once/day), "WEIGH_IN" (once/day), or "WATER_14:00" (one per configured
-- slot/day, since water reminds several times a day) -- so the composite primary key alone is the
-- dedupe guard: com.kcalma.reminder.ReminderLogRepository claims a slot with
-- INSERT ... ON CONFLICT (user_id, reminder_key, sent_on) DO NOTHING and only sends if that insert
-- actually landed a row.
CREATE TABLE app.reminder_log (
    user_id       UUID NOT NULL,
    reminder_key  VARCHAR(40) NOT NULL,
    sent_on       DATE NOT NULL,
    sent_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, reminder_key, sent_on)
);

-- No explicit REVOKE here -- see V4__enable_pg_trgm_and_food_reference.sql.
