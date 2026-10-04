# ADR 0001: Use Supabase as the only backend

- **Status:** Superseded by [ADR-0015](0015-custom-go-backend.md)
- **Date:** 2026-09-21 (recorded retroactively — decision predates this ADR)

## Context

Simsli needs household data sharing: auth, a relational schema with per-household authorization, realtime updates, and delta sync — for a solo developer who wants to ship an Android app, not operate a backend. Two hard constraints from the project's footing: the app is AGPL, and self-hosting must remain free, so whatever backend is chosen cannot require proprietary infrastructure. Writing and maintaining a custom API server (deployment, auth, websocket scaling, security patching) is the main cost to avoid.

## Decision

We will use Supabase as the only backend: Postgres + PostgREST for data, Supabase Auth (magic link, later email/password) for identity, Supabase Realtime for change events. The Android app talks to it directly — there is no custom API server. All backend logic lives in versioned SQL migrations (`supabase/migrations/`): Row Level Security policies enforce household membership, and RPCs (`create_household_with_owner`, `accept_invite`) implement the operations that need transactional multi-table writes. Alternatives considered: a custom Kotlin/Ktor server (rejected: ongoing operational and security burden for logic that Postgres RLS expresses declaratively) and Firebase (rejected: not self-hostable, which breaks the free-self-hosting constraint).

## Consequences

Easier: no server code to deploy or patch; authorization is declarative RLS instead of hand-written checks on every endpoint; self-hosting is "run Supabase + apply migrations"; realtime and auth come free with the platform.

Harder / accepted trade-offs: logic is split between Kotlin (client) and SQL (server), so contributors need both; the migration history is a public contract — breaking changes need real migrations, not schema edits; RLS mistakes are silent and dangerous, so policies need review discipline; the platform's per-table realtime publication must be maintained manually; managed hosting (simsli.app) becomes a product cost, which the free-tier/Pro model exists to cover.
