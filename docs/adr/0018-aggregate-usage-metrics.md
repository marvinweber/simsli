# ADR 0018: Aggregate usage metrics without device-level tracking

- **Status:** Accepted
- **Date:** 2026-10-08

## Context

The managed server needs a minimal operational picture: which app versions are in use (to judge the blast radius before raising `SIMSLI_MIN_APP_VERSION` or killing a release) and whether households actually use the app (checked/added items). The app already sends `X-Simsli-App-Version` with every request. Any solution must stay compatible with Simsli's privacy positioning: no third-party analytics, no device-level tracking, negligible storage and request cost.

## Decision

Usage metrics are recorded server-side, aggregated, and never device-level:

1. **App version on the user row:** `users` gets `last_seen_at` and `last_app_version` (nullable columns). The value is written by `TouchUserVersion` on token refresh and magic-link verify, throttled by the UPDATE's WHERE clause: a write only lands when the version changed or the previous `last_seen_at` is older than one hour — steady-state refresh traffic (every 15 min during active use) writes nothing. Refresh is reactive in the client (only after a 401), so dormant installs never update; the distribution reflects active users.
2. **No version history / snapshots table:** only the current distribution is needed (Play Store Console covers rollout curves); decided against a time-series table.
3. **Per-household monthly activity:** `household_monthly_stats (household_id, year, month, items_checked, items_added)` is incremented inside the same transaction as the sync flush by diffing each incoming list entry against its stored row: check = +1 checked, un-check (undo) = −1, new entry = +1 added (and +1 checked when it arrives already done). The checked column is floored at zero. Counts are approximate (last-write-wins, offline adoption) — good enough for a "feeling" metric, never billing-grade.
4. **No device table:** the `X-Simsli-Device-Id` header stays log-correlation-only; it is not persisted.
5. **Surfacing:** admin dashboard shows version distribution, the last 12 months of activity, and a "seen in the last 24 h" user count.

## Consequences

- **Easier:** release/version decisions rest on real data; activity trends visible at a glance; storage is bounded (one row per user + one row per household-month); no extra client traffic — existing headers carry everything.
- **Harder / accepted trade-offs:** activity counts are best-effort (concurrent flushes of the same entry can race, floor-at-zero can mask rare over-decrements); `last_seen_at`-based activity undercounts (reactive refresh); uninstall/removal leaves a stale user row (retention follows the user account, not this metric). PRIVACY.md documents the metrics honestly.
