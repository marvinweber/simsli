# ADR 0007: Client-generated single-use invite codes

- **Status:** Accepted
- **Date:** 2026-09-26

## Context

Household invites need a shareable, human-transcribable secret. The `invite_tokens` table supports client-generated tokens (insert policy + server-side `expires_at` default), and the codebase has no Edge Functions — adding a server-side generator would introduce the first one for marginal benefit. Codes are shared by copy/paste or reading aloud, so the alphabet must survive transcription.

## Decision

We will generate invite codes on the client: 8 characters from an unambiguous alphabet (A–Z minus I/L/O, plus 2–9 — no 0/O/1/I/L), `SecureRandom`, inserted via PostgREST with timestamps left to server defaults. Redemption is server-enforced by the `accept_invite` RPC (single-use, 24-hour expiry, `security definer`), so the client cannot weaken the single-use guarantee. Creation is owner-only by policy (FEATURE-SPEC HH-2/HH-3).

## Consequences

Easier: zero Edge Function surface; expiry and single-use are server-side facts, not client promises; codes are speakable and typeable.

Harder / accepted trade-offs: unused codes linger 24h before expiry (no delete policy on the table); a PK collision surfaces as a failed insert (probability ~1 in 31⁸ — acceptable, no retry logic).
