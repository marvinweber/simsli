# ADR 0019: Server-stamped `updated_at` for flushed rows

- **Status:** Accepted
- **Date:** 2026-10-10

## Context

Delta pulls select rows with `updated_at > since`, where `since` is the watermark from the previous pull — and watermarks are issued in **server time** (`server_time` on every delta response). Flushed rows, however, carried the **client-stamped** `updated_at` of the sending device. That mixes two clocks on the two sides of the comparison:

- A device with a clock behind the server writes timestamps that can land at or below another device's watermark. That device never pulls the row — the write is invisible to it forever, with no recovery path short of wiping sync state.
- A stale outbox push (device offline while another device changes the row, then flushing old state) resets the server row's `updated_at` backwards to the stale client value — hiding the change (and any revert of it) from every device already past that point.

This produced a real field report: a device showed entries as unticked that were ticked on the server, while later rows (fresh timestamps) synced fine. Client clocks are not fixable server-side (skew is logged, not controlled), so the invariant must not depend on them.

## Decision

The server stamps `updated_at` with its own clock on every flush — the client-supplied `updated_at` is never stored (`Repository.Flush` normalizes stores/categories/items/list_entries to the flush's server time; soft deletes were already server-stamped). Client-stamped `created_at` and `completed_at` are kept: they are display and GC data, not delta-filter criteria.

With this, both operands of the delta comparison share one monotonic clock: every write is stamped after every previously issued watermark, so no write can slip below the horizon. LWW-by-arrival (ADR 0006) is unchanged; timestamps stop being a conflict-resolution input and become purely a delta-visibility cursor. Devices' own flushes echo back once on their next pull (idempotent REPLACE) — as they already did whenever client clocks were roughly correct.

## Consequences

- The reported drift class is structurally impossible, not merely mitigated; the client's "Full resync" (SYNC-6) remains the recovery hatch for pre-fix drift and belt-and-braces.
- Client edit timestamps no longer order writes on the server. Arrival order ≈ causal order for this sync design (debounced pushes), which is the semantics LWW already had in practice — the client-stamped ordering it pretended to provide was never reliable.
- A device whose clock is wrong no longer corrupts sync visibility at all; the clock-skew warning in the client's pull log loses its sync-correctness role (kept for diagnostics).
- Self-hosted servers need this server version for the guarantee; older clients work unchanged (they simply have their timestamps overridden). Downgrading the server after upgrading reintroduces the bug class.
