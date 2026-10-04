# Migration Plan: Supabase to Custom Go Backend

> **Status:** 📋 Planned (target: around v1-ish)  
> **Pre-implementation requirement:** When work on this migration starts, **re-verify the entire plan against [`docs/FEATURE-SPEC.md`](FEATURE-SPEC.md)**. Check for newly implemented features, schema changes (e.g. categories, subscription webhooks, soft-delete retention), or behavioral decisions added since this document was written, and adjust the plan accordingly.

---

## 1. Executive Summary & Goals

Simsli currently uses Supabase as its backend ([ADR-0001](adr/0001-use-supabase-as-the-only-backend.md)). While Supabase enabled fast prototyping without custom server code, it presents significant friction for self-hosting: the official stack requires 10+ Docker containers and ~1–1.5 GB of RAM.

This migration plan outlines the transition to a **dedicated, lightweight Go backend** designed to fulfill Simsli's three core identity pillars:

1. **Fully Open Source:** AGPL v3 mono-repo with zero proprietary infrastructure dependencies.
2. **First-Class, Painless Self-Hosting for Geeks:** A single Docker container (`ghcr.io/marvinweber/simsli:latest`), idling at **~15 MB RAM**, using embedded SQLite, zero required configuration, and optional SMTP.
3. **European Cloud-Hosted Tier (`simsli.app`):** Stateless, horizontally-scalable serverless containers (e.g. Scaleway / Koyeb in Frankfurt/Paris) backed by managed PostgreSQL, with built-in GDPR compliance and low operational cost.

Because the Android app is strictly offline-first (Room is the only source of truth for UI and ViewModels), **~95% of the Android codebase remains untouched**. Only the remote networking layer (~500 lines of code) is swapped.

---

## 2. Core Architecture Blueprint

The Go backend is compiled into a single static binary capable of running in two execution modes based on configuration:

```
                            ┌──────────────────────────────────────┐
                            │         Simsli Backend (Go)          │
                            │  REST API + WebSocket Invalidation   │
                            └──────────────────┬───────────────────┘
                                               │
                    ┌──────────────────────────┴──────────────────────────┐
                    ▼                                                     ▼
      [ Mode 1: Self-Hosted ]                               [ Mode 2: Hosted Cloud ]
            (Default)                                     (DATABASE_URL=postgres://...)
   • Embedded SQLite (/data/simsli.db)                   • Managed PostgreSQL (Scaleway/Neon)
   • 1 single Docker container                           • Stateless, horizontally scalable
   • In-memory WebSocket pub/sub                         • Inter-node pub/sub via LISTEN/NOTIFY
   • ~15 MB RAM total                                    • Scale-to-zero serverless ready
```

### Mode 1: Self-Hosted (Home Server / NAS / Raspberry Pi)
- **Database:** Embedded SQLite in WAL (Write-Ahead Logging) mode. Everything lives in `/data/simsli.db`.
- **Deployment:** A single container with two isolated listening ports:
  ```bash
  docker run -d \
    --name simsli \
    -p 8080:8080 \               # Public API (mobile app)
    -p 127.0.0.1:8081:8081 \     # Private Admin Dashboard (host/LAN only)
    -v simsli_data:/data \
    ghcr.io/marvinweber/simsli:latest
  ```
- **Realtime:** In-memory Go channels broadcast invalidation pings to connected WebSockets.
- **Auth & Access Control:**
  - **Magic-Link-Only Auth:** Consistent with the app's secure passwordless architecture.
  - **Zero-SMTP Dashboard Fallback:** If SMTP is unconfigured, pending magic links are logged to console and displayed live in the Admin Dashboard with **Copy Link** and **QR Code** buttons for instant phone scanning.
  - **`REGISTRATION_OPEN` (default `false`):** Controls whether anyone can sign up or if registration is closed/admin-created only.
  - **`EMAIL_ALLOWLIST`:** Restricts registration/logins to specified addresses or wildcards (`*@family.de`).
- **Billing & Limits:** Bypassed completely via `UnlimitedBillingService`. All self-hosted households are unlimited by default with zero phone-home.

### Mode 2: Hosted Cloud (`simsli.app`)
- **Database:** Managed PostgreSQL (e.g. Scaleway Managed PostgreSQL or Neon in Frankfurt).
- **Stateless Horizontal Scaling:** Instances run behind a load balancer (Scaleway Serverless Containers, Koyeb, or Cloud Run).
- **Inter-Node Realtime via PostgreSQL `LISTEN / NOTIFY`:**
  - When Instance A processes a mutation for Household `X`, it executes `NOTIFY simsli_events, '{"household_id": "X"}'`.
  - All running instances (A, B, C...) listen to the PostgreSQL channel and broadcast the WebSocket ping to their locally connected clients.
  - **Zero extra infrastructure:** No Redis, Kafka, or RabbitMQ cluster required.
- **Billing & Limits (`CloudBillingService`):**
  - Enabled via `BILLING_ENABLED=true`.
  - Enforces free-tier household limits (BIZ-1) and handles payment webhooks (Google Play Billing / Stripe).
  - Client queries `GET /api/v1/server-info`: if `billing_enabled` is false, Android suppresses all Pro upgrade UI.

---

## 3. Backend Specification

### 3.1 Technology Stack (Go)
- **Router / HTTP:** `net/http` with `chi` (lightweight, zero-allocation, idiomatic).
- **Database Access:** `sqlc` (type-safe Go code generated from SQL queries) or `bun` / `pgx`. Queries written to be compatible with both PostgreSQL and SQLite.
- **WebSockets:** `coder/websocket` (high performance, minimal memory per connection: ~2 KB per Goroutine).
- **Auth:** Standard JWT (`golang-jwt/jwt`) with HMAC-SHA256 or Ed25519 signing + Argon2id password hashing.
- **Email:** Transactional email sender interface:
  - SMTP driver (for self-hosters).
  - API driver (e.g. Resend / Postmark for hosted `simsli.app`).
  - Dev/Mock driver (prints magic link to console or sends to local Mailpit).

### 3.2 Database Schema & Compatibility
Tables mirrored from [`supabase/SCHEMA.md`](../supabase/SCHEMA.md):
- `households` (id, name, plan, status, current_period_end, created_at, updated_at, deleted_at)
- `household_members` (id, household_id, user_id, role, joined_at, updated_at)
- `stores` (id, household_id, name, sort_order, created_at, updated_at, deleted_at)
- `items` (id, household_id, category_id, name, notes, type, default_unit, sort_order, created_at, updated_at, deleted_at)
- `item_stores` (item_id, store_id, created_at)
- `categories` (id, household_id, name, emoji, sort_order, created_at, updated_at, deleted_at)
- `store_categories` (store_id, category_id, sort_order)
- `list_entries` (id, household_id, item_id, quantity, unit, comment, done, completed_at, created_by, created_at, updated_at)
- `invite_tokens` (token, household_id, created_by, created_at, expires_at, used_at, used_by)
- `users` (id, email, password_hash, created_at, updated_at)
- `refresh_tokens` (id, user_id, token_hash, expires_at, revoked_at)
- `email_verification_tokens` (id, email, token_hash, expires_at, used_at)

### 3.3 API Endpoints
All endpoints return JSON and require a `Bearer <access_token>` header (except public auth endpoints).

#### Authentication (`/api/v1/auth`)
- `POST /magic-link` — Request magic link email (body: `email`).
- `POST /verify` — Exchange magic-link token for JWT pair (body: `token`). Returns `{ access_token, refresh_token, user }`.
- `POST /register` & `POST /login` — Optional email/password auth for self-hosters.
- `POST /refresh` — Refresh expired access token using refresh token.
- `POST /logout` — Revoke refresh token.

#### Sync & Deltas (`/api/v1/sync`)
- `GET /deltas?since=<timestamp>&household_id=<id>` — Returns all rows modified or soft-deleted since `<timestamp>` across all tables for the household, plus full join-table snapshots (`item_stores`, `store_categories`).
- `POST /flush` — Accepts batches of local mutations from the client outbox (upserts and deletes) within a single transaction.

#### Household Operations (`/api/v1/households`)
- `POST /` — Adopt / create household (replaces `create_household_with_owner` RPC).
- `GET /members` — List members of the household.
- `DELETE /members/:user_id` — Remove member (owner only).
- `POST /invites` — Create invite token.
- `POST /invites/accept` — Accept invite (replaces `accept_invite` RPC; handles household cleanup and transfer atomically).
- `DELETE /` — Soft-delete household (owner only, 30-day retention).

#### Realtime WebSocket (`/api/v1/realtime`)
- `GET /ws?token=<jwt>&household_id=<id>` — Upgrades to WebSocket.
- The server pushes a lightweight invalidation message whenever any write occurs in that household:
  ```json
  { "event": "sync", "household_id": "uuid" }
  ```
- The client receives the ping and schedules a watermark delta pull via `SyncScheduler`. Realtime remains strictly a trigger, not the data transport ([ADR-0004](adr/0004-realtime-as-trigger-not-transport.md)).

### 3.4 Embedded Admin Dashboard (Port 8081)
The backend binary serves a dedicated private web UI on a secondary port (default `:8081`), completely isolated from the public API (`:8080`). All assets (HTML templates, CSS, JS) are embedded via `//go:embed` (zero external dependencies).

- **Network Security:** Docker exposes `-p 127.0.0.1:8081:8081` so the dashboard is only reachable via localhost, SSH port forwarding, or a secure private VPN (Tailscale/WireGuard).
- **Features:**
  - **Live Pending Logins (Zero-SMTP Mode):** Real-time table of recent magic link requests with **[Copy Link]** and **[Show QR Code]** buttons for instant camera sign-in without email infrastructure.
  - **User & Household Management:** Create users directly, inspect households, assign roles, view member counts.
  - **Telemetry & Health:** Memory allocation gauge (verifying ~15 MB RAM), active Goroutines, DB connection pool health, and active WebSocket count.
  - **Operational Tools:** Manual GC trigger, invite token revocation, and server config management.

### 3.5 Clean Architectural Separation: The `BillingService` Interface
To allow the same codebase to run in both self-hosted and cloud environments without leaking billing complexity or commercial prompts to self-hosters:

```go
type BillingService interface {
    GetPlan(ctx context.Context, householdID string) HouseholdPlan
    CheckLimit(ctx context.Context, householdID string, resource ResourceType) error
    HandleWebhook(ctx context.Context, provider string, payload []byte) error
}
```

- **`UnlimitedBillingService` (Self-Hosted default):**
  - Enabled when `BILLING_ENABLED=false`.
  - `CheckLimit()` always returns `nil` (all households are strictly unlimited).
  - Webhook endpoints are disabled.
  - `GET /api/v1/server-info` reports `billing_enabled: false`, causing the Android client to dynamically hide all Pro upgrade UI, badges, and subscription menus.
- **`CloudBillingService` (Hosted `simsli.app`):**
  - Enabled when `BILLING_ENABLED=true`.
  - Connects to payment provider APIs (Google Play Billing / Stripe) and handles webhooks.
  - Enforces the free-tier household limits defined in BIZ-1.

---

## 4. Android Client Changes

Because the app is built around the Repository pattern and Room DAOs, the changes are isolated to the remote datasource and authentication implementation:

| Current File | Action | Replacement |
|---|---|---|
| `data/remote/SupabaseRemoteDataSource.kt` | **Replace** | `data/remote/SimsliRemoteDataSource.kt` using Ktor Client or Retrofit/OkHttp to call `/api/v1/*`. |
| `data/repository/impl/AuthRepositoryImpl.kt` | **Replace** | Implements `AuthRepository` via `/api/v1/auth/*`, storing JWT access/refresh tokens in encrypted `DataStore`. |
| `data/sync/RealtimeObserver.kt` | **Replace** | Connects to `/api/v1/realtime/ws` using standard WebSocket client, firing `SyncScheduler.requestSync()` on incoming events. |
| `di/SupabaseModule.kt` | **Replace** | `di/NetworkModule.kt` providing configured `HttpClient` with auth interceptor (auto-token-refresh). |
| `data/sync/SyncManager.kt` | **Keep (95%)** | Minor tweaks: replace direct PostgREST calls with calls to `SimsliRemoteDataSource`. Logic for outbox flush, delta pulls, watermark updates, and GC remains unchanged. |
| `ui/` & `domain/` | **No change** | ViewModels, Compose screens, UseCases, and Domain models are completely insulated from backend changes. |

---

## 5. Developer Tooling & Testing Setup

For local backend development, provide a simple `deploy/dev/docker-compose.yml`:
- **Postgres:** For testing PostgreSQL mode locally.
- **Mailpit:** Local mock SMTP server with web UI (`http://localhost:8025`) to catch magic-link emails.
- **pgweb:** Single-binary browser-based database viewer (`http://localhost:8081`) for modern, fast database inspection without heavy desktop tools.

For free remote testing:
- **Database:** [Neon.tech](https://neon.tech/) (free serverless Postgres) or existing Supabase project as raw Postgres.
- **App Hosting:** [Koyeb](https://www.koyeb.com/) (Frankfurt edge, free 24/7 nano container) or [Scaleway](https://www.scaleway.com/) Serverless Containers.

---

## 6. Phased Implementation Roadmap

When work begins, proceed in these phases on a dedicated branch (`feature/custom-backend`):

```
Phase 0: Pre-flight Verification
  └── Re-read docs/FEATURE-SPEC.md and confirm all new feature additions since plan creation.

Phase 1: Go Backend Scaffolding
  ├── Project structure in server/ (go.mod, cmd/server, internal/...)
  ├── Database migrations (SQL) compatible with PostgreSQL and SQLite
  └── Dockerfile (multi-arch linux/amd64 and linux/arm64, ~15MB scratch/alpine image)

Phase 2: Auth & Session Management
  ├── User registration, magic link generation & verification
  ├── JWT issuance, refresh token rotation, and password fallback
  └── Email sender integration (SMTP + Resend + Console logger)

Phase 3: Core API & Sync Engine
  ├── Household & membership endpoints (create, invite, accept, remove)
  ├── Outbox flush endpoint (batch upsert/delete)
  └── Delta sync endpoint (watermark timestamp filtering)

Phase 4: Realtime Invalidation
  ├── In-memory WebSocket hub (for SQLite / single-instance mode)
  └── PostgreSQL LISTEN / NOTIFY bridge (for multi-instance cloud mode)

Phase 5: Android Remote Layer Swap
  ├── NetworkModule (HTTP client, JWT auth header interceptor, token refresh)
  ├── SimsliRemoteDataSource implementation
  └── RealtimeObserver WebSocket swap

Phase 6: Verification & Cutover
  ├── End-to-end sync verification between two devices
  ├── Benchmark memory footprint (<20MB RAM target verified)
  ├── Write ADR superseding ADR-0001
  └── Update FEATURE-SPEC.md status to ✅
```
