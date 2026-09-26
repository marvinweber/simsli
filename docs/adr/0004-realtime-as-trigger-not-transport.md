# ADR 0004: Realtime as trigger, not transport

- **Status:** Accepted
- **Date:** 2026-09-26

## Context

Supabase Realtime streams postgres changes over a websocket and can carry payloads. Using those payloads as the sync transport would create a second write path into Room alongside the delta pull — duplicating resolution logic, racing with outbox pushes, and needing its own reconnect/backfill handling. Meanwhile the watermark delta pull (ADR-0006) is already a reliable transport that self-heals anything missed while offline.

## Decision

We will use Realtime events only as *triggers*: postgres changes on `list_entries` and `items` call `SyncScheduler.requestSync()`, which is debounced; the watermark delta pull does the actual work and remains the backstop for everything Realtime might have missed. The channel lifecycle keys on the household id only (via `distinctUntilChanged`), never on household-row content — unrelated writes to the households table must not tear down and rejoin the channel.

## Consequences

Easier: one code path applies every remote change (the pull); disconnects self-heal on the next pull; a broken websocket degrades gracefully instead of losing data.

Harder / accepted trade-offs: remote changes land at debounce latency (~500 ms) instead of instantly from payloads — imperceptible for a shopping list; the channel must be kept from reacting to its own sync-induced writes (the household-id keying exists because of a real infinite-loop bug caused by exactly that).
