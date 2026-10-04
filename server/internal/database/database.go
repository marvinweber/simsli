package database

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	"time"

	_ "github.com/jackc/pgx/v5/stdlib"
	_ "modernc.org/sqlite"
)

type DB struct {
	*sql.DB
	Driver string
}

func Connect(dbURL string) (*DB, error) {
	var driver, dsn string

	if strings.HasPrefix(dbURL, "postgres://") || strings.HasPrefix(dbURL, "postgresql://") {
		driver = "pgx"
		dsn = dbURL
	} else {
		driver = "sqlite"
		dsn = strings.TrimPrefix(dbURL, "sqlite://")
		if dsn == "" {
			dsn = "data/simsli.db"
		}

		// Ensure parent directory exists for SQLite file
		dir := filepath.Dir(dsn)
		if dir != "" && dir != "." {
			if err := os.MkdirAll(dir, 0755); err != nil {
				return nil, fmt.Errorf("failed to create db directory %s: %w", dir, err)
			}
		}
	}

	sqlDB, err := sql.Open(driver, dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to open database: %w", err)
	}

	// Connection pool tuning
	if driver == "sqlite" {
		sqlDB.SetMaxOpenConns(1) // SQLite performs best with single writer
		sqlDB.SetMaxIdleConns(1)

		// Set WAL mode and busy timeout
		pragmas := []string{
			"PRAGMA journal_mode = WAL;",
			"PRAGMA busy_timeout = 5000;",
			"PRAGMA foreign_keys = ON;",
			"PRAGMA synchronous = NORMAL;",
		}
		for _, pragma := range pragmas {
			if _, err := sqlDB.Exec(pragma); err != nil {
				return nil, fmt.Errorf("failed to execute pragma %q: %w", pragma, err)
			}
		}
	} else {
		sqlDB.SetMaxOpenConns(25)
		sqlDB.SetMaxIdleConns(10)
		sqlDB.SetConnMaxLifetime(10 * time.Minute)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := sqlDB.PingContext(ctx); err != nil {
		return nil, fmt.Errorf("failed to ping database: %w", err)
	}

	db := &DB{
		DB:     sqlDB,
		Driver: driver,
	}

	if err := db.Migrate(); err != nil {
		return nil, fmt.Errorf("failed to run migrations: %w", err)
	}

	log.Printf("[Database] Connected successfully (driver: %s)", driver)
	return db, nil
}

func (db *DB) Migrate() error {
	schema := `
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
	`

	_, err := db.Exec(schema)
	return err
}
