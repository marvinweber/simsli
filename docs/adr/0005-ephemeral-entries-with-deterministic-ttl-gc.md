# ADR 0005: Ephemeral entries with deterministic TTL garbage collection

- **Status:** Accepted
- **Date:** 2026-09-26

## Context

A shared list needs checked-off items to disappear, but "clear done" buttons require coordination and leave differently-configured households in different states — and users want an undo window after accidentally checking something off. The catalog/entry split (items live forever, entries are "on the list right now") invites entries to be strictly ephemeral.

## Decision

We will make list entries strictly ephemeral with a fixed 24-hour TTL: checking an entry marks it done (`completed_at`), it stays visible under "Recently checked" and is undoable; after the TTL, *any* device's garbage collection deletes it locally and on the server. The rule is deterministic — based only on timestamps — so uncoordinated devices converge on identical deletions without any coordination. ONE_TIME items retire together with their last entry.

## Consequences

Easier: no "clear done" UI, no GC coordination between devices, undo is free for the whole TTL window; the catalog stays clean because ONE_TIME items can't accumulate.

Harder / accepted trade-offs: done rows linger for 24h (storage is trivial); the TTL is a constant (`SyncContract.RECENTLY_CHECKED_TTL`), not configurable; GC correctness relies on server-provided timestamps, not device clocks.
