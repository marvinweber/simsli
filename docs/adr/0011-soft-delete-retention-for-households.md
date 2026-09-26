# ADR 0011: Soft-delete retention for deleted households

- **Status:** Accepted
- **Date:** 2026-09-26

## Context

Deleting a shared household is destructive for *every* member, and deletion is exactly the operation most often done by accident or in anger. Hard-delete at confirm time makes recovery impossible. The app already has a proven pattern for deterministic, coordination-free lifecycle: timestamp-based TTL garbage collection (ADR-0005).

## Decision

We will implement household deletion as a **soft delete with 30-day retention**: the household and its entire graph — memberships included — are marked deleted and become invisible to every query; after 30 days a deterministic GC hard-deletes everything. Restore (v2 UI; manual via SQL/support until then) clears the flag, and because memberships were retained, a member's wiped device simply re-pulls on next sign-in — no re-invite. The GC mechanism itself (pg_cron vs lazy-on-access vs scheduled function) is deliberately open (FEATURE-SPEC OQ-4) and will get its own ADR.

## Consequences

Easier: accidental deletions are recoverable for a month; restore semantics fall out of "un-hide plus existing sync" with no extra reconciliation; consistency with the 24h-entry GC philosophy.

Harder / accepted trade-offs: a server-side scheduled mechanism is needed for the hard GC (the first real background job in the stack); deleted households occupy storage for 30 days; every query path must consistently exclude soft-deleted households (RLS does this centrally, which is why deletion is a flag and not row removal).
