# ADR 0003: Single-household app on a multi-household server

- **Status:** Accepted
- **Date:** 2026-09-26 (recorded retroactively — decision predates this ADR)

## Context

The server-side model is naturally multi-household: `household_members` maps many users to many households, and RLS authorizes per membership. The v1 product, however, is one household per user. Building the app for true multi-household now would require per-household sync watermarks (today's are per-table), a household switcher UI, and multi-channel realtime — significant machinery before the core list even ships.

## Decision

We will bind the v1 app to the user's first membership and expose no household switching. The server stays general — no schema or RPC assumes app-side household count — so v2 can add switching without backend rework. Joining a second household in v1 (invite flow) deliberately remaps local data onto the joined household.

## Consequences

Easier: one household flows through every DAO query, the realtime observer, and the UI; the join-by-code flow works with zero extra concepts.

Harder / accepted trade-offs: per-table watermarks are a known v2 migration (re-key per household); a v1 user who joins a second household abandons their local household's separate identity; import (FEATURE-SPEC DATA-5) is blocked until v2 multi-household or a replace-in-place rule exists.
