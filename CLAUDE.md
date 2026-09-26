# CLAUDE.md

This file gives Claude context about the Simsli project so responses stay consistent across conversations.

**Product behavior** (features, rules, status) is defined in [`docs/FEATURE-SPEC.md`](docs/FEATURE-SPEC.md) — the single source of truth for what the app does. This file covers project setup and architecture context.

**When working on features:** read the FEATURE-SPEC entry (and its neighbors) before implementing — build to the spec, not to assumptions. Spec updates travel with the implementation: when a change ships or a behavior decision is made, update the feature's entry **and its status marker** (✅ implemented / 🚧 partial / 📋 planned / ❌ out of scope) in the same change. Feature descriptions elsewhere (README, commit messages, chat) are summaries only — on conflict, the spec wins. The *why* behind technical choices goes to `docs/adr/`.

**Architecture decisions (ADRs):** `docs/adr/NNNN-kebab-title.md`, sequential numbering, 0000 is the template. Write one when a technical choice has lasting consequences or a rejected alternative worth remembering (feature behavior belongs in FEATURE-SPEC.md, not ADRs). Accepted ADRs are immutable: changed thinking means a new ADR that supersedes the old one — only the old one's Status line may change. Keep them short: Context, Decision, Consequences with honest trade-offs.

---

## Project overview

**Simsli** (Simple Shared Shopping List) is an Android shopping list app with a core differentiator: items are assigned to one or more stores, so users can filter the list by store and see only what's relevant where they are.

Target audience: households of 2+ people who shop together or divide shopping tasks.

---

## Current status

Early development, pre-release. The app (offline + Supabase two-way sync + realtime) is functional on the emulator; local Supabase stack drives development.

---

## Key decisions (already made, don't re-open unless asked)

- **Platform**: Android only, Kotlin, Jetpack Compose with Material3 Expressive
- **Architecture**: MVVM + UDF, Repository pattern, offline-first
- **Local DB**: Room (SQLite)
- **Backend**: Supabase (self-hostable via Docker Compose, or managed at simsli.app)
- **Auth**: Supabase Auth — Magic Link + Email/Password; no OAuth providers
- **Realtime sync**: Supabase Realtime (Websocket), not polling
- **License**: AGPL v3 (`AGPL-3.0-or-later`; app + backend config in one mono-repo). The Simsli name/logo are reserved by the author, not licensed — see README's License section
- **Monetization**: Free tier (limited) + €1/month Pro on managed hosting; self-hosting always free
- **Name**: Simsli (may change, treat as placeholder if asked)

---

## Data model

```
Household        { id, name }
HouseholdMember  { householdId, userId, role }
Item             { id, householdId, name, notes, type, sortOrder }
Store            { id, householdId, name, sortOrder }
ItemStore        { itemId, storeId }          -- many-to-many
ListEntry        { id, householdId, itemId, quantity, unit, comment, done, completedAt }
InviteToken      { token, householdId, createdAt, expiresAt, usedAt }
```

### Item types
- `PERMANENT` — the normal case: lives in the catalog forever, re-added to the list whenever needed (milk, bread)
- `ONE_TIME` — rare: removed from the catalog together with its checked-off entry at garbage collection (birthday candles)
- `CHECKLIST` — obsolete under the entry lifecycle below; hidden in the UI, the enum value stays in the DB

### List entry lifecycle
`items` is the catalog; `list_entries` is strictly ephemeral — "what's on the list right now":

```
catalog item ──add──► active entry ──check──► done entry ──24h TTL──► gone
                      (done = false)          ("Recently checked",    (row deleted locally
                                               undo-able)             + on server by GC)
```

- One entry per (household, item) — enforced by a UNIQUE constraint. Adding an item that is already in "Recently checked" just moves it back to active.
- No "clear done": after 24h (`SyncContract.RECENTLY_CHECKED_TTL`) any device's garbage collection (in `SyncManager`) deletes the entry locally and on the server; the rule is deterministic, so devices converge without coordination.
- Entry-level data (quantity, unit, comment) lives on the entry, edited via the entry editor sheet; item metadata (name, type, stores, notes) is edited in the item detail screen.

### Sorting
`sortOrder` is a Float on Item (global order). Filter views show a subset but preserve relative order. Reordering updates only the affected items' sortOrder values (gap-based, no full re-index).

---

## Architecture layers

```
UI (Compose screens)
    │  events (user actions)
    ▼
ViewModel  (StateFlow<UiState>, UDF pattern)
    │
Repository  (single source of truth, exposes Flow)
    ├── LocalDataSource   (Room DAOs)
    └── RemoteDataSource  (Supabase-kt)
```

The Repository merges local and remote. UI and ViewModels never touch Room or Supabase directly.

Domain models (`domain/model/`) are plain Kotlin data classes with no Android or database annotations. Room entities and Supabase DTOs are separate classes with explicit mappers.

---

## Screens

Target structure: bottom app bar with three tabs — **Shopping List · Catalog · Settings** — with the Catalog tab containing **Items | Categories | Stores**, and household/member management in a Household screen reached from Settings. Full screen map with statuses: FEATURE-SPEC §3.9. Exists today: List tab (active entries, "Recently checked", fast-add sheet, entry editor, store filter chips), Catalog tab (items only so far — Categories/Stores tabs planned), Item detail (name, stores, type, notes), Settings as a pushed screen (household rename, invite/join by code, magic-link sign-in, "Sync now").

---

## Sync

Offline-first delta sync between Room and Supabase (`data/sync/SyncManager`), one serialized run per trigger:

1. `resolveHousehold` — adopts the offline household server-side via `create_household_with_owner` RPC on first sync
2. `flushOutbox` — pushes pending local writes (no payloads: reads current row state, last-write-wins)
3. `pullDeltas` — watermark-based (`updated_at`) pulls; `item_stores` full reconcile (no `updated_at` column)
4. `gcExpiredEntries` — removes done entries past the 24h TTL (see lifecycle above)

**Triggers**: sign-in / session restore, debounced push after every local write (`SyncScheduler.requestSync()` fired by the repositories' outbox hooks), Supabase Realtime events on `list_entries` + `items` (`RealtimeObserver` — realtime is only a *trigger*, the watermark delta pull is the transport and the backstop), and the manual "Sync now" button.

---

## Supabase setup

All backend logic lives in `supabase/`:
- `migrations/` — versioned SQL files, applied in order. Includes the RPCs `create_household_with_owner` and `accept_invite` (invite redemption); invite codes are generated client-side and inserted into `invite_tokens` via PostgREST
- `config.toml` — Supabase project config

Row Level Security (RLS) enforces that users only see data belonging to their household. No custom API server needed — the Android app talks directly to Supabase.

---

## Coding conventions

- Kotlin, no Java
- Coroutines + Flow throughout, no RxJava, no callbacks where avoidable
- `Result<T>` or `sealed class` for error handling, no exceptions for control flow
- StateFlow for UI state, SharedFlow for one-shot events (navigation, snackbars)
- Hilt for all dependency injection
- No business logic in Composables or ViewModels — use UseCases
- Room entities prefixed with `Db` (e.g. `DbItem`), Supabase DTOs suffixed with `Dto` (e.g. `ItemDto`), domain models unprefixed (e.g. `Item`)
- All database and network calls on `Dispatchers.IO`, injected via Hilt

---

## What's explicitly out of scope for v1

- Item photos
- Tags / categories
- Tandoor integration
- GPS reminders
- Barcode scanning
- iOS / multiplatform
- Web frontend
