-- STORE-7: store groups. A store belongs to at most one group or none;
-- per-item store selection stores group selections in item_store_groups and
-- derives membership (see ADR 0020).

CREATE TABLE IF NOT EXISTS store_groups (
	id TEXT PRIMARY KEY,
	household_id TEXT NOT NULL REFERENCES households(id) ON DELETE CASCADE,
	name TEXT NOT NULL,
	sort_order REAL NOT NULL DEFAULT 0,
	created_at TEXT NOT NULL,
	updated_at TEXT NOT NULL,
	deleted_at TEXT
);

ALTER TABLE stores ADD COLUMN group_id TEXT REFERENCES store_groups(id) ON DELETE SET NULL;
ALTER TABLE item_stores ADD COLUMN membership TEXT NOT NULL DEFAULT 'MEMBER';

CREATE TABLE IF NOT EXISTS item_store_groups (
	item_id TEXT NOT NULL REFERENCES items(id) ON DELETE CASCADE,
	group_id TEXT NOT NULL REFERENCES store_groups(id) ON DELETE CASCADE,
	created_at TEXT NOT NULL,
	PRIMARY KEY(item_id, group_id)
);

CREATE INDEX IF NOT EXISTS idx_store_groups_household_updated ON store_groups(household_id, updated_at);
