# ADR 0006: Last-write-wins delta sync without payloads

- **Status:** Accepted
- **Date:** 2026-09-26 (recorded retroactively — decision predates this ADR)

## Context

Households are small (2–5 people), edit collisions are rare, and shopping-list rows have no hard merge semantics — merging "2 kg" and "3 kg" has no meaningful answer. CRDTs or field-level merge would add real complexity for a conflict rate that rounds to zero. Additionally, outbox pushes that carry the payload captured *at write time* would push stale state whenever the user edits again before the next sync.

## Decision

We will sync via watermark-based delta pulls (`updated_at`) and outbox pushes of *current row state* — no payload is captured at write time; the push reads the row fresh. Conflicts resolve by last-write-wins per row. Join tables without `updated_at` (`item_stores`, and future `store_categories`) reconcile by full-set comparison on every sync.

## Consequences

Easier: pushes can never send stale data; missed pulls self-heal because watermarks only advance; the whole sync pipeline is a short serial sequence (resolve → flush → pull → GC).

Harder / accepted trade-offs: a true simultaneous edit to the same row silently loses one side (accepted at household scale); full-set reconcile is O(n) per sync (trivial at household size); `updated_at` precision must match Postgres (the microsecond-precision watermark converter exists because millisecond truncation caused an infinite re-pull loop).
