ALTER TABLE users DROP COLUMN last_seen_at;
ALTER TABLE users DROP COLUMN last_app_version;

DROP TABLE IF EXISTS household_monthly_stats;
