# ADR 0010: Per-household plans, config-driven limits, runtime server config

- **Status:** Accepted
- **Date:** 2026-09-26

## Context

The hosted free tier must be cost-bounded (FEATURE-SPEC BIZ-1: 2 users / 3 stores / 20 catalog items / 25 live list entries), while self-hosting must be unlimited and phone-home-free — the OSS promise depends on it. A single Supabase instance serves all households (second-instance-by-tier was rejected: double ops, per-household migration on upgrade). Two technical consequences follow: the app's Supabase URL/key can no longer live in build-time `BuildConfig` (that forecloses pointing a build at another server), and limit enforcement must tolerate a server that declares itself unlimited.

## Decision

We will run one Supabase instance for all households. Each household carries `plan` (`free`/`pro`) plus subscription validity (`status`, `current_period_end`), written by the billing webhook (v1.5); entitlement = `pro` plan **and** valid period. Limits are enforced **server-side** in RPCs/constraints, reading a config table — the hosted instance sets the free-tier limits, a self-hosted instance defaults to unlimited. The client pre-checks the same limits for friendly errors and to suppress the upsell where they don't apply. The server URL and anon key become local runtime settings; switching warns, wipes local data (ADR-0008), and starts fresh sign-in on the new server.

## Consequences

Easier: one deployment to operate; limits survive modified clients; self-host parity comes from config, not a fork; offline households stay unlimited and only meet limits at adoption (over-limit adoption is refused with local data untouched).

Harder / accepted trade-offs: every write RPC gains a limit check; adoption needs a pre-check path; runtime-config plumbing plus a switch-with-wipe flow; the payment provider is still open (OQ-1), so the webhook side ships with subscriptions (v1.5).
