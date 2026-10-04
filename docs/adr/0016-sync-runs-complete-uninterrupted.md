# 0016 — Sync runs complete uninterrupted

## Status

Accepted

## Context

Sync runs are triggered by debounced pokes (`SyncScheduler.requestSync`): local writes,
realtime events, sign-in, manual "Sync now". The realtime server broadcasts every flush
to **all** household devices — including the flusher itself — so a sync run regularly
receives a new poke while it is still executing. The debounced collector originally used
`collectLatest`, which **cancels the in-flight run** when the next poke arrives.

A cancelled run can die at any suspension point with different damage:

- between the server-side flush and the local outbox delete → the entry stays queued and
  is re-pushed by the next run;
- inside `pullDeltas`, after the deltas response but before the watermark upsert → the
  watermark never advances, so every pull returns the same rows.

With two devices online the broadcasts feed each device's pokes, cancelled runs re-push
rows, each re-push broadcasts again — a self-sustaining sync loop observed in production
(frozen `since` watermark, identical delta responses, `POST /flush` every second on both
devices). The loop was intermittent because it only sustains while cancellations keep
landing mid-run.

## Decision

A started sync run always runs to completion. The debounced collector keeps
`collectLatest` for burst collapse, but wraps the run in `withContext(NonCancellable)`.
Pokes arriving during a run simply produce one follow-up run; runs are serialized by the
sync mutex and are cheap when there is nothing to do (one `households/mine` + one empty
delta pull).

## Consequences

- Outbox deletes and watermark advances are never skipped by cancellation — devices
  converge instead of feeding a loop.
- A hung network call (OkHttp default timeouts apply) delays subsequent runs instead of
  being abandoned; acceptable at current request sizes.
- The flush echo still costs one extra no-op run per realtime event; suppressing it
  (sender exclusion or client-side dedup window) remains an option if that overhead
  ever matters.
