# ADR 0020: Derived effective store set for store groups

- **Status:** Accepted
- **Date:** 2026-10-11

## Context

STORE-7 adds optional store groups ("Supermarkt") above stores. Per item, users select
individual stores, whole groups (with per-store exclusions), or a mix. The central design
question: where does "which stores does this item belong to" live?

The payoff of groups is auto-propagation — a store added to a group must immediately count
for every item that selected the group. That rules out denormalizing group coverage into
concrete `item_stores` rows: it would need a per-item write cascade on every group edit and
would make the stored rows a stale copy of the truth.

A second, pre-existing gap had to close alongside: join-table rows (`item_stores`,
`store_categories`) had **no server-side deletion path**. The client deletes a row locally and
enqueues an outbox DELETE, but `flushOutbox` silently dropped the entry once the row was gone,
the server never learned, and the next pull's full reconcile re-inserted the "deleted" row.
Deletions resurrected; "No Store" was polluted by rows pointing at deleted stores.

## Decision

1. **Selection is stored, membership is derived.** The per-item truth is:
   `item_stores.membership ∈ {MEMBER, EXCLUDED, METADATA}` (per-store rows) plus
   `item_store_groups` (group selections). The effective set is computed —
   `MEMBER rows ∪ stores of selected groups − EXCLUDED rows` — in a pure domain function
   (`EffectiveStoreSet`), in the Room filter query, and never denormalized into tables.
2. **`membership` on the row, not a separate exclusion table.** INCLUDE/EXCLUDE are two
   values of one discriminator; METADATA (no membership, favorite/price only) anticipates
   STORE-5/6 and survives coverage loss dormant.
3. **Join-row deletions travel as composite-id lists** (`deleted_item_stores`,
   `deleted_store_categories`, `deleted_item_store_groups`) in the flush request; the server
   hard-deletes them household-scoped inside the flush transaction. There is no row left to
   push, so the key is the payload. This fixes the resurrection bug for all three tables.
4. **One reconcile write path per save** (`ItemStoreRepository.saveStoreSelection`): the
   item detail dialog submits the full selection; the repository diffs rows and group
   selections, preserving METADATA rows.

Rejected alternative: sending the authoritative full set and having the server diff-delete —
that breaks the flush's additive-LWW shape (a device that missed an unrelated deletion would
re-add the row on its next flush), while explicit deletion lists delete exactly what the
deleting device deleted.

## Consequences

- Group edits (add/rename/delete a member store) need no per-item writes anywhere; adding a
  store to a group updates every consumer on the next pull — or immediately, since the
  effective set is derived from live rows.
- Full reconcile stays unchanged in shape: the server returns all `item_store_groups` rows per
  pull; clients diff locally. No watermark needed (mirrors `item_stores`).
- Mixed-version households are safe: legacy clients send no `membership` (normalizes to
  `MEMBER` server-side, defaults client-side) and simply don't see groups.
- Filter queries gained correlated EXISTS subselects over three tables — fine at household
  data scale, but the "No Store" and store filters are no longer single-table lookups.
- The wear payload keeps its flat `item→storeIds` shape; the phone publishes the effective
  set, so the watch stays group-unaware.
