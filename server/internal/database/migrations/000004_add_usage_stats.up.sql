-- Aggregate usage metrics: app version + last-seen on the user row (no
-- device-level tracking), and per-household monthly activity counters.

ALTER TABLE users ADD COLUMN last_seen_at TEXT;
ALTER TABLE users ADD COLUMN last_app_version TEXT;

CREATE TABLE IF NOT EXISTS household_monthly_stats (
	household_id TEXT NOT NULL REFERENCES households(id) ON DELETE CASCADE,
	year INTEGER NOT NULL,
	month INTEGER NOT NULL,
	items_checked INTEGER NOT NULL DEFAULT 0,
	items_added INTEGER NOT NULL DEFAULT 0,
	updated_at TEXT NOT NULL,
	PRIMARY KEY(household_id, year, month)
);
