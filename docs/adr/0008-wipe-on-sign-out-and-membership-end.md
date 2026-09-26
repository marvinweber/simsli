# ADR 0008: Wipe on sign-out and membership end

- **Status:** Accepted
- **Date:** 2026-09-26

## Context

Originally local data survived sign-out. That design allowed a second account signing in on the same device to *silently adopt* the previous household (the resolution logic saw local data + no membership and claimed it), and left ghost outbox entries that would push under the wrong session. The app is a cache of shared household data — not a private store — and the wipe policy is what keeps account switches and membership ends clean.

## Decision

We will wipe all local app data (Room + outbox) on sign-out, and likewise whenever membership ends: leaving, being removed, the household being deleted, or losing RLS access any other way. Losing unsynced offline writes is explicitly accepted: sync runs continuously (debounced + realtime), and sign-out/leave are deliberate acts. Household deletion keeps server-side data for 30 days (FEATURE-SPEC HH-7), so a wipe there is recoverable at the source.

## Consequences

Easier: devices are stateless with respect to accounts — no cross-account data leaks, no silent household adoption, clean multi-account testing on a single device; the adoption branch only runs for genuine first syncs.

Harder / accepted trade-offs: signing back in re-pulls everything (a full sync, fine at household scale); a user who signs out while offline loses unsynced writes (documented, FEATURE-SPEC DATA-3); the wipe must be implemented (currently a gap, AUTH-2 🚧).
