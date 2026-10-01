# Category cluster — working slicing (TEMPORARY)

> **This file is a temporary working plan.** Delete it once the cluster is fully
> shipped (all slices done, FEATURE-SPEC.md updated). Product behavior lives in
> FEATURE-SPEC.md (§3.6 CAT-*, LIST-6, STORE-4); this file only tracks the
> implementation order.

Slices are **self-contained**: each leaves the app fully working, nothing
half-broken in between. 4 and 5 are mutually independent (without
`store_categories` rows, per-store grouping falls back to global order — the
spec-defined behavior).

## Slice 1 — CAT-1 + CAT-2 data & sync (no UI) ✅ shipped 2026-09-27

- Supabase migration: `categories` (id, household_id, name, emoji, sort_order,
  timestamps), `store_categories` (store_id, category_id, sort_order),
  `items.category_id` (nullable); RLS policies mirroring existing tables;
  realtime enabled on `categories` + `store_categories` + `stores` (SYNC-1 v0.1)
- Room: `DbCategory`, `DbStoreCategory` entities + DAOs, `DbItem.categoryId`,
  schema version bump + migration, mappers
- Sync: watermarks for `categories`; `store_categories` full-reconcile (like
  `item_stores`); outbox paths for both; flushOutbox support
- App behavior after this slice: unchanged (category data syncs but nothing
  reads/writes it yet)

## Slice 2 — CAT-3 Categories management tab ✅ shipped 2026-10-01

- Categories tab replaces the placeholder in the Catalog tab:
  create / rename / emoji / delete / drag global order
- Delete sets affected items to uncategorized

## Slice 3 — CAT-2 item assignment UI ✅ shipped 2026-10-01

- Category picker in item detail (zero or one category; null = Uncategorized)

## Slice 4 — LIST-6 list grouping ✅ shipped 2026-10-01

- Store-filtered → that store's category order (fallback: global order),
  unfiltered → global order; Uncategorized implicit group last; "Recently
  checked" stays flat after all groups

## Slice 5 — STORE-4 per-store category order

- Store edit gains a category checklist; checked categories drag-orderable →
  `store_categories`; unchecked fall back to global order, appended after

## Slice 6 — CAT-4 setup presets

- Preset list (Obst & Gemüse/Produce 🍎 … Sonstiges/Other 📦) applied
  wholesale; later reused by SCREENS-5 onboarding
