# ADR 0012: Categories over tags — single assignment, per-store ordering

- **Status:** Accepted
- **Date:** 2026-09-26

## Context

The store-filtered list must read in aisle order ("what do I need *here*, in which order"), which needs groupable, orderable item metadata. Alternatives considered: free-form **tags** (no grouping/ordering semantics, unbounded per item, order undefined when an item has several); **multiple categories per item** (expressive, but items duplicate across groups and management is heavier); **curated Material icon sets** (consistent rendering, but a picker to build and a set to maintain). The sync layer already has a proven pattern for per-pair ordering joins (ADR-0006's full-set reconcile).

## Decision

We will model **per-household categories**: name + free emoji icon (no picker — the keyboard is the picker) + globally draggable `sortOrder`. An item has **zero or one** category; null is the implicit "Uncategorized" group, always last. Per-store ordering lives in a `store_categories` join (same shape as `item_stores`, full-set reconcile per sync); categories without a store-specific entry fall back to global order, appended after the explicit ones. One always-grouped list view in v1.

## Consequences

Easier: grouping is unambiguous — every item appears exactly once per view; the store-order join reuses the `item_stores` machinery unchanged; zero icon-picker UI.

Harder / accepted trade-offs: a category cannot express "also fits there" (accepted — groceries have one obvious aisle); the preset set offered at setup must be maintained bilingually (I18N-1); grouping is mandatory in v1, so view customization stays a v2+ idea rather than an escape hatch.
