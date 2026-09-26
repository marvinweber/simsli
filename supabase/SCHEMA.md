# Simsli — Database Schema

## Entity overview

```
auth.users (Supabase built-in)
    │
    ├──< household_members >──── households
    │                                │
    │                                ├──< stores
    │                                │
    │                                ├──< items >──< item_stores >── stores
    │                                │
    │                                └──< list_entries ──── items
    │
    └──── invite_tokens ──── households
```

## Tables

### `households`
Top-level container. Everything belongs to a household.

| Column | Type | Notes |
|---|---|---|
| id | uuid PK | client-generated, enables offline creation |
| name | text | e.g. "Schmidt household" |
| created_at | timestamptz | |
| updated_at | timestamptz | auto-updated via trigger |
| deleted_at | timestamptz | soft delete |

---

### `household_members`
Maps users to households. One user can be in multiple households.

| Column | Type | Notes |
|---|---|---|
| id | uuid PK | |
| household_id | uuid FK → households | |
| user_id | uuid FK → auth.users | |
| role | enum (owner, member) | owner can manage members |
| joined_at | timestamptz | |
| updated_at | timestamptz | |

Unique constraint: `(household_id, user_id)`

---

### `invite_tokens`
Single-use tokens for household invitations. Valid 24 hours.

| Column | Type | Notes |
|---|---|---|
| token | text PK | short random string, e.g. 8 alphanumeric chars |
| household_id | uuid FK → households | |
| created_by | uuid FK → auth.users | |
| created_at | timestamptz | |
| expires_at | timestamptz | default: now() + 24h |
| used_at | timestamptz | null = unused |
| used_by | uuid FK → auth.users | null = unused |

---

### `stores`
Stores (supermarkets, shops) that belong to a household.

| Column | Type | Notes |
|---|---|---|
| id | uuid PK | client-generated |
| household_id | uuid FK → households | |
| name | text | e.g. "REWE", "Aldi", "Pharmacy" |
| sort_order | float8 | display order in filter bar |
| created_at | timestamptz | |
| updated_at | timestamptz | |
| deleted_at | timestamptz | soft delete |

---

### `items`
Reusable items. Represent things you buy, not a specific purchase.

| Column | Type | Notes |
|---|---|---|
| id | uuid PK | client-generated |
| household_id | uuid FK → households | |
| name | text | e.g. "Milk", "Bread" |
| notes | text | persistent note on the item |
| type | enum | PERMANENT, ONE_TIME, CHECKLIST |
| sort_order | float8 | global order across all items in the household |
| created_at | timestamptz | |
| updated_at | timestamptz | |
| deleted_at | timestamptz | soft delete |

**Item types:**
- `PERMANENT` — stays on list after check-off; done flag reset on "clear done"
- `ONE_TIME` — item and list entry are deleted after check-off
- `CHECKLIST` — done flag reset automatically before next shopping trip (v2)

**Sorting:** `sort_order` is a float to allow gap-based reordering (LexoRank-lite). Inserting between two items picks the midpoint. No full re-index needed.

---

### `item_stores`
Many-to-many: which stores carry which items.

| Column | Type | Notes |
|---|---|---|
| item_id | uuid FK → items | |
| store_id | uuid FK → stores | |
| created_at | timestamptz | |

PK: `(item_id, store_id)`

An item with **no rows** in this table is considered available at all stores (shows up regardless of store filter).

---

### `list_entries`
The active shopping list. One entry per item per household.

| Column | Type | Notes |
|---|---|---|
| id | uuid PK | client-generated |
| household_id | uuid FK → households | denormalized for RLS + index efficiency |
| item_id | uuid FK → items | |
| quantity | float8 | optional |
| unit | text | free text in v1: "kg", "pcs", "ml", etc. |
| comment | text | one-time per-purchase note, cleared on check-off |
| done | boolean | false = still needed, true = in trolley / bought |
| completed_at | timestamptz | when done was set to true |
| created_at | timestamptz | |
| updated_at | timestamptz | |
| created_by | uuid FK → auth.users | who added this to the list |

Unique constraint: `(household_id, item_id)` — each item appears at most once on the active list.

---

## RLS summary

All tables use Row Level Security. Access is determined by `household_members`.

| Operation | Allowed for |
|---|---|
| SELECT any household data | Members of that household |
| INSERT/UPDATE/DELETE | Members of that household |
| Create a household | Anyone (authenticated) |
| Read an invite token | Anyone (needed to join before auth) |

Two helper functions handle the common check:
- `is_household_member(uuid)` — used in most policies
- `is_household_owner(uuid)` — used for member management

---

## Key functions

### `create_household_with_owner(id, name)`
Creates a household and adds the calling user as owner atomically.
Used when syncing a locally-created (offline) household to the backend.

### `accept_invite(token)`
Validates a token, marks it used, adds the calling user to the household.
Returns the `household_id` so the app can download the household data.

---

## Realtime subscriptions

The following tables publish changes via `supabase_realtime`:

- `items` — item additions, edits, deletions
- `stores` — store additions, edits, deletions
- `item_stores` — store assignment changes
- `list_entries` — check-offs, new items added to list, comment changes
- `household_members` — new members joining

The Android app subscribes filtered by `household_id` to avoid receiving
changes from other households.

---

## Delta sync (offline → online)

When the app comes back online, it fetches rows where
`updated_at > last_sync_timestamp` for each table.
Indexes on `(household_id, updated_at)` make this efficient.

Soft deletes (`deleted_at`) ensure that deletions made while offline
propagate correctly — a hard delete would leave no trace for other clients.
