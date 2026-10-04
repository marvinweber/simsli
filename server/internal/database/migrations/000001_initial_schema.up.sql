CREATE TABLE IF NOT EXISTS users (
	id TEXT PRIMARY KEY,
	email TEXT UNIQUE NOT NULL,
	created_at TEXT NOT NULL,
	updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS households (
	id TEXT PRIMARY KEY,
	name TEXT NOT NULL,
	plan TEXT NOT NULL DEFAULT 'free',
	status TEXT NOT NULL DEFAULT 'active',
	current_period_end TEXT,
	created_at TEXT NOT NULL,
	updated_at TEXT NOT NULL,
	deleted_at TEXT
);

CREATE TABLE IF NOT EXISTS household_members (
	id TEXT PRIMARY KEY,
	household_id TEXT NOT NULL REFERENCES households(id) ON DELETE CASCADE,
	user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	role TEXT NOT NULL DEFAULT 'member',
	joined_at TEXT NOT NULL,
	updated_at TEXT NOT NULL,
	UNIQUE(household_id, user_id)
);

CREATE TABLE IF NOT EXISTS stores (
	id TEXT PRIMARY KEY,
	household_id TEXT NOT NULL REFERENCES households(id) ON DELETE CASCADE,
	name TEXT NOT NULL,
	sort_order REAL NOT NULL DEFAULT 0,
	created_at TEXT NOT NULL,
	updated_at TEXT NOT NULL,
	deleted_at TEXT
);

CREATE TABLE IF NOT EXISTS categories (
	id TEXT PRIMARY KEY,
	household_id TEXT NOT NULL REFERENCES households(id) ON DELETE CASCADE,
	name TEXT NOT NULL,
	emoji TEXT NOT NULL DEFAULT '',
	sort_order REAL NOT NULL DEFAULT 0,
	created_at TEXT NOT NULL,
	updated_at TEXT NOT NULL,
	deleted_at TEXT
);

CREATE TABLE IF NOT EXISTS store_categories (
	store_id TEXT NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
	category_id TEXT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
	sort_order REAL NOT NULL DEFAULT 0,
	PRIMARY KEY(store_id, category_id)
);

CREATE TABLE IF NOT EXISTS items (
	id TEXT PRIMARY KEY,
	household_id TEXT NOT NULL REFERENCES households(id) ON DELETE CASCADE,
	category_id TEXT REFERENCES categories(id) ON DELETE SET NULL,
	name TEXT NOT NULL,
	notes TEXT NOT NULL DEFAULT '',
	type TEXT NOT NULL DEFAULT 'PERMANENT',
	default_unit TEXT,
	sort_order REAL NOT NULL DEFAULT 0,
	created_at TEXT NOT NULL,
	updated_at TEXT NOT NULL,
	deleted_at TEXT
);

CREATE TABLE IF NOT EXISTS item_stores (
	item_id TEXT NOT NULL REFERENCES items(id) ON DELETE CASCADE,
	store_id TEXT NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
	created_at TEXT NOT NULL,
	PRIMARY KEY(item_id, store_id)
);

CREATE TABLE IF NOT EXISTS list_entries (
	id TEXT PRIMARY KEY,
	household_id TEXT NOT NULL REFERENCES households(id) ON DELETE CASCADE,
	item_id TEXT NOT NULL REFERENCES items(id) ON DELETE CASCADE,
	quantity REAL,
	unit TEXT,
	comment TEXT,
	done INTEGER NOT NULL DEFAULT 0,
	completed_at TEXT,
	created_by TEXT REFERENCES users(id) ON DELETE SET NULL,
	created_at TEXT NOT NULL,
	updated_at TEXT NOT NULL,
	UNIQUE(household_id, item_id)
);

CREATE TABLE IF NOT EXISTS invite_tokens (
	token TEXT PRIMARY KEY,
	household_id TEXT NOT NULL REFERENCES households(id) ON DELETE CASCADE,
	created_by TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	created_at TEXT NOT NULL,
	expires_at TEXT NOT NULL,
	used_at TEXT,
	used_by TEXT REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS magic_links (
	token TEXT PRIMARY KEY,
	email TEXT NOT NULL,
	created_at TEXT NOT NULL,
	expires_at TEXT NOT NULL,
	used_at TEXT
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
	token_hash TEXT PRIMARY KEY,
	user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
	expires_at TEXT NOT NULL,
	revoked_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_stores_household_updated ON stores(household_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_categories_household_updated ON categories(household_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_items_household_updated ON items(household_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_list_entries_household_updated ON list_entries(household_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_household_members_user ON household_members(user_id);
