# ADR 0015: Custom Go backend with dual SQLite/Postgres and embedded admin dashboard

- **Status:** Accepted
- **Date:** 2026-10-04

## Context

ADR 0001 chose Supabase as the sole backend to minimize operational overhead. In practice, self-hosting Supabase requires orchestrating a 10+ container Docker Compose stack (PostgreSQL, PostgREST, GoTrue, Realtime, Kong, Studio, Inbucket, Storage) consuming 1.5–2 GB RAM. For target users self-hosting on homelabs, Raspberry Pis, or low-cost VPS instances, this operational footprint is excessive for a shared shopping list.

Furthermore, the Supabase Android Kotlin SDK added significant build overhead and transitive dependencies. We need a lightweight single-binary backend that can run effortlessly on embedded SQLite (~15 MB RAM) for self-hosters or scale to PostgreSQL for managed cloud hosting, while retaining offline-first watermark sync, WebSocket realtime invalidation (ADR 0004), and zero-SMTP magic-link onboarding.

## Decision

We will replace Supabase with an in-house Go backend (`simsli-server`) located in `server/`.

1. **Architecture & Transport:** A compiled Go service offering standard HTTP/REST endpoints for JWT authentication, household management, and batched atomic sync (flush and delta pulls). Realtime invalidation uses a lightweight WebSocket hub (trigger-only, preserving ADR 0004).
2. **Dual Database Support:** An abstraction layer supporting both embedded SQLite (default with WAL mode, single-writer safety, zero external database setup) and PostgreSQL (for concurrent multi-tenant cloud offering).
3. **Embedded Administration:** An integrated web dashboard served on a distinct administrative port (e.g. `:8081`) providing live system telemetry, user management, demo data seeding, and an interactive database table browser with household filtering.
4. **Zero-SMTP Onboarding:** In addition to SMTP delivery, generated magic links are exposed via the admin dashboard with copy-to-clipboard and QR codes, allowing frictionless LAN authentication.
5. **App Migration:** The Android app talks directly to `simsli-server` using standard OkHttp and kotlinx.serialization, eliminating all Supabase SDK dependencies.

## Consequences

- **Easier:** Self-hosting is reduced to running a single binary or ~15 MB container instead of a multi-container stack; memory usage drops from ~2 GB to <15 MB; local development requires no running Docker daemon; app and server versions are synchronized with automated compatibility handshake.
- **Harder / accepted trade-offs:** We now maintain server-side business logic, SQL migrations, JWT token lifecycle, and WebSocket connection infrastructure; magic-link tokens are currently stored in plaintext in the database to enable display in the admin dashboard for zero-SMTP setups (must be migrated to SHA-256 hashing for public cloud deployments); migrating existing Supabase production instances requires a one-time schema/data migration.
