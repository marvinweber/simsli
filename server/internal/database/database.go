package database

import (
	"context"
	"database/sql"
	"embed"
	"errors"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/golang-migrate/migrate/v4"
	migratedb "github.com/golang-migrate/migrate/v4/database"
	"github.com/golang-migrate/migrate/v4/database/pgx/v5"
	"github.com/golang-migrate/migrate/v4/database/sqlite"
	"github.com/golang-migrate/migrate/v4/source/iofs"
	_ "github.com/jackc/pgx/v5/stdlib"
	_ "modernc.org/sqlite"
)

//go:embed migrations/*.sql
var migrationsFS embed.FS

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

func (db *DB) Rebind(query string) string {
	if db.Driver != "pgx" {
		return query
	}
	var b strings.Builder
	idx := 1
	for i := 0; i < len(query); i++ {
		if query[i] == '?' {
			b.WriteString(fmt.Sprintf("$%d", idx))
			idx++
		} else {
			b.WriteByte(query[i])
		}
	}
	return b.String()
}

func (db *DB) Migrate() error {
	d, err := iofs.New(migrationsFS, "migrations")
	if err != nil {
		return fmt.Errorf("failed to create migration source driver: %w", err)
	}

	var driver migratedb.Driver
	if db.Driver == "pgx" {
		driver, err = pgx.WithInstance(db.DB, &pgx.Config{
			MultiStatementEnabled: true,
		})
	} else {
		driver, err = sqlite.WithInstance(db.DB, &sqlite.Config{})
	}
	if err != nil {
		return fmt.Errorf("failed to create migration database driver: %w", err)
	}

	m, err := migrate.NewWithInstance("iofs", d, db.Driver, driver)
	if err != nil {
		return fmt.Errorf("failed to initialize migration instance: %w", err)
	}

	if err := m.Up(); err != nil && !errors.Is(err, migrate.ErrNoChange) {
		return fmt.Errorf("failed to run migrations: %w", err)
	}

	version, dirty, err := m.Version()
	if err != nil && !errors.Is(err, migrate.ErrNilVersion) {
		log.Printf("[Database] Migrations applied (version check warning: %v)", err)
	} else {
		log.Printf("[Database] Schema at migration version %d (dirty: %v)", version, dirty)
	}

	return nil
}
