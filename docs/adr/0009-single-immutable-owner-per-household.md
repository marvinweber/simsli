# ADR 0009: Single immutable Owner per household

- **Status:** Accepted
- **Date:** 2026-09-26

## Context

Household subscriptions (Simsli Pro, FEATURE-SPEC BIZ-3) attach to *one person and one household*: the paying user must be structurally irremovable, or a member could remove the payer and detach the subscription. The previously considered multi-owner model with an outcome guard (≥1 owner must remain, anyone may demote anyone else) is incompatible with that: it has no notion of an unremovable person. It also made billing accountability ambiguous (who pays when every owner is equal?).

## Decision

We will give each household **exactly one Owner**, immutable: nobody — not the Owner themselves — can demote, remove, or otherwise affect them, and the Owner cannot leave. Household management (rename, invite, remove, role changes) is Owner-only; the **Admin role (v1.5)** wields those rights over everyone *except* the Owner. Ownership transfer is v2+; until then the Owner's only exits are deleting the household (blocked while subscribed) or staying.

## Consequences

Easier: RLS/RPC guards are single-row checks ("is owner") with no quorum logic; billing has one accountable person per household; the untouchable-Owner rule is trivial to state and enforce.

Harder / accepted trade-offs: no self-service step-down — an Owner who wants out must delete the household or wait for transfer (v2+); deletion-blocked-while-subscribed requires a cancel flow; the earlier outcome-guard flexibility (multi-owner step-down) is gone by design.
