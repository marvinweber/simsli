# ADR 0002: Offline-first with Room as the source of truth

- **Status:** Accepted
- **Date:** 2026-09-26 (recorded retroactively — decision predates this ADR)

## Context

Shopping happens in basements, cold stores, and dead zones; network latency must never delay adding "milk". At the same time the product is a *shared* list across devices. The backend is Supabase (ADR-0001), reachable only when online. The app therefore needs a local store that is fully functional by itself and a story for reconciling with the server later.

## Decision

We will make Room the single source of truth: the UI reads exclusively from Room (via repository Flows), every write lands locally first and reaches Supabase asynchronously through an outbox. Supabase holds replicated state, not the primary copy. Synchronization is a separate subsystem (`data/sync/`) that can fail, retry, and run behind a debounce without ever blocking the UI.

## Consequences

Easier: the UI is instant and fully functional offline; sync failures never surface as user-facing write errors; the repository layer gives one stable API regardless of connectivity.

Harder / accepted trade-offs: two schema definitions (Room entities + Supabase DTOs) with explicit mappers; sync bookkeeping is real machinery (outbox, watermarks, reconciles); the system is eventually consistent — concurrent changes resolve by last-write-wins (ADR-0006) rather than being impossible.
