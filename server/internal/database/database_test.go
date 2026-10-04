package database

import (
	"database/sql"
	"path/filepath"
	"testing"

	_ "modernc.org/sqlite"
)

func TestConnectAndMigrateSQLite(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_simsli.db")

	// 1. Initial connect & migrate
	db, err := Connect(dbPath)
	if err != nil {
		t.Fatalf("Failed to connect and migrate: %v", err)
	}
	defer db.Close()

	// Verify tables were created
	var count int
	row := db.QueryRow("SELECT COUNT(*) FROM users")
	if err := row.Scan(&count); err != nil {
		t.Fatalf("Failed to query users table: %v", err)
	}

	// Verify schema_migrations table
	var version uint64
	var dirty bool
	row = db.QueryRow("SELECT version, dirty FROM schema_migrations")
	if err := row.Scan(&version, &dirty); err != nil {
		t.Fatalf("Failed to query schema_migrations table: %v", err)
	}
	if version != 1 {
		t.Errorf("Expected migration version 1, got %d", version)
	}
	if dirty {
		t.Errorf("Expected migration dirty to be false, got true")
	}

	// 2. Re-connect & re-migrate should succeed idempotently
	db2, err := Connect(dbPath)
	if err != nil {
		t.Fatalf("Failed to re-connect and re-migrate: %v", err)
	}
	defer db2.Close()
}

func TestExistingDatabaseAdoption(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "existing_simsli.db")

	// Simulate an existing database created before golang-migrate was introduced
	rawDB, err := sql.Open("sqlite", dbPath)
	if err != nil {
		t.Fatalf("Failed to open raw sqlite: %v", err)
	}
	initialSQL := `
	CREATE TABLE users (
		id TEXT PRIMARY KEY,
		email TEXT UNIQUE NOT NULL,
		created_at TEXT NOT NULL,
		updated_at TEXT NOT NULL
	);
	INSERT INTO users (id, email, created_at, updated_at) VALUES ('u1', 'test@simsli.de', '2026-01-01', '2026-01-01');
	`
	if _, err := rawDB.Exec(initialSQL); err != nil {
		t.Fatalf("Failed to seed initial table: %v", err)
	}
	_ = rawDB.Close()

	// Now connect via Connect(), which runs golang-migrate
	db, err := Connect(dbPath)
	if err != nil {
		t.Fatalf("Failed to connect and adopt existing db: %v", err)
	}
	defer db.Close()

	// Ensure user row is preserved
	var email string
	if err := db.QueryRow("SELECT email FROM users WHERE id = 'u1'").Scan(&email); err != nil {
		t.Fatalf("Failed to query existing user: %v", err)
	}
	if email != "test@simsli.de" {
		t.Errorf("Expected email 'test@simsli.de', got %q", email)
	}

	// Ensure schema_migrations exists and is at version 1
	var version uint64
	var dirty bool
	if err := db.QueryRow("SELECT version, dirty FROM schema_migrations").Scan(&version, &dirty); err != nil {
		t.Fatalf("Failed to query schema_migrations: %v", err)
	}
	if version != 1 || dirty {
		t.Errorf("Expected version 1 (dirty: false), got version %d (dirty: %v)", version, dirty)
	}
}
