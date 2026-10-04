# Simsli Server (Go)

A high-performance, resource-efficient backend for Simsli written in Go.

- **Footprint:** ~15 MB RAM, single static binary, zero CGO dependencies.
- **Dual Database Support:** Embedded SQLite (default for single-container self-hosting) or PostgreSQL (for multi-node horizontal cloud scaling).
- **Passwordless Magic-Link Auth:** Zero-SMTP fallback with QR code display in the admin dashboard.
- **Admin Dashboard:** Private dashboard on port 8081 for managing users, households, live connections, and pending magic links.
- **Realtime Sync:** WebSocket invalidation engine with PostgreSQL `LISTEN / NOTIFY` support for cross-instance sync.

---

## Quickstart

### Local Development

1. **Run directly with SQLite:**
   ```bash
   cd server
   go run ./cmd/simsli
   ```

2. **Access the endpoints:**
   - **Public Mobile API:** `http://localhost:8080/api/v1/server-info`
   - **Admin Dashboard:** `http://localhost:8081`

3. **Run with PostgreSQL (Cloud Mode simulation):**
   ```bash
   SIMSLI_SERVER_MODE=cloud \
   SIMSLI_DATABASE_URL="postgres://postgres:postgres@localhost:5432/simsli?sslmode=disable" \
   go run ./cmd/simsli
   ```

---

## Configuration

All configuration is handled via environment variables:

| Variable | Default | Description |
|---|---|---|
| `SIMSLI_PORT` | `8080` | Port for the public mobile app API |
| `SIMSLI_ADMIN_PORT` | `8081` | Port for the private admin dashboard |
| `SIMSLI_SERVER_MODE` | `self_hosted` | `self_hosted` (unlimited, no billing) or `cloud` (enforces BIZ-1 limits) |
| `SIMSLI_REGISTRATION_OPEN` | `false` | When `false`, only invited users or admin-created accounts can sign up |
| `SIMSLI_EMAIL_ALLOWLIST` | `""` | Comma-separated list of allowed emails or wildcards (`*@family.de`) |
| `SIMSLI_DATABASE_URL` | `sqlite://data/simsli.db` | Connection string (`sqlite://...` or `postgres://...`) |
| `SIMSLI_PUBLIC_URL` | `http://localhost:8080` | Base public URL used in magic links |
| `SIMSLI_ADMIN_KEY` | `""` | Optional secret key for securing admin dashboard access |
| `SIMSLI_JWT_SECRET` | auto-generated | Secret used to sign JWT access tokens |
| `SIMSLI_SMTP_HOST` | `""` | Optional SMTP server for sending magic link emails |
