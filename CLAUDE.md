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

Target structure: bottom app bar with three tabs — **Shopping List · Catalog · Settings** — with the Catalog tab containing **Items | Categories | Stores**, and household/member management in a Household screen reached from Settings. Full screen map with statuses: FEATURE-SPEC §3.9. Exists today: the three-tab bottom bar; List tab (active entries, "Recently checked", fast-add sheet, entry editor, store filter chips), Catalog tab (tabbed Items | Categories | Stores — Categories is still an honest placeholder until CAT-*), Item detail as a pushed screen (name, stores, type, notes), Settings tab (household rename, invite/join by code, magic-link sign-in, "Sync now"). Household screen still pending.

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
- `seed.sql` — local test data, applied automatically by `supabase db reset`. Seeds the magic-link accounts `test1@simsli.de` (owner) + `test2@simsli.de` (member) and a populated household ("Testhaushalt": 3 stores, 15 items, 11 entries incl. 3 recently checked). Their magic-link mails land in the local mail UI
- `config.toml` — Supabase project config

Row Level Security (RLS) enforces that users only see data belonging to their household. No custom API server needed — the Android app talks directly to Supabase.

**On-device test data:** debug builds get a Settings → Debug → "Seed demo data" action (`data/debug/DemoDataSeeder`) that wipes the local DB and inserts the same dataset via DAOs, deliberately bypassing the outbox — for the offline / signed-out use case. Signing in afterwards still adopts the demo household and uploads it (offline-adoption path); seeding while signed in merges the demo rows into the server household. Release builds don't contain it (`BuildConfig.DEBUG` gate). The two datasets mirror each other — change them together.

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

## Emulator debugging & verification

The working recipe for verifying app behavior end-to-end without hand-testing:

- **Build/install**: `./gradlew :app:assembleDebug` (if `java` isn't on the PATH, point `JAVA_HOME` at a JDK — Android Studio's bundled JBR works), then `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Clean slate: `adb shell pm clear net.marvinweber.simsli`.
- **Drive the UI via adb**: `adb shell uiautomator dump /sdcard/ui.xml`, parse the dump for `text`/`bounds`, tap element centers with `input tap`. Layouts shift (keyboard, status messages, dialogs) — **always re-dump before tapping; never reuse coordinates from an earlier dump**. Browser/webview content may not expose text nodes at all.
- **adb IME quirks**: `input text` can drop or reorder characters. Dump the field to see what actually landed — a dropped char is a tooling artifact, not an app bug; **reordered or interleaved text is a real bug**.
- **App logs**: `adb logcat -d -s SimsliSync:V SimsliAuth:V` — sync runs, pulls, household resolution, deep-link sign-in results.
- **Backend inspection**: `docker exec supabase_db_simsli psql -U postgres -d postgres -c "<sql>"` (container names get the `<project-dir>` suffix, so they hold for any checkout as `simsli`). Colima is the Docker runtime on this machine (`colima start` if the daemon is down), then `supabase start`. Mailpit's REST API (`http://127.0.0.1:54324/api/v1/messages`, then `/api/v1/message/{ID}`) reads magic-link mails without a browser — the mail body needs un-escaping (`=\r\n`, `=3D`) before extracting URLs.
- **Sign-in without the browser**: POST `/auth/v1/otp` (`apikey` header, `{"email": ..., "create_user": false}`) for a seeded account → fetch the verify URL from Mailpit's API → follow it with redirects disabled → take the `Location` (`simsli://auth#access_token=...`) and fire it with the URI quoted **for the device shell**: `adb shell "am start -a android.intent.action.VIEW -d '<that uri>'"`. Quoting on the host side alone is not enough — the device shell splits the URI at the first `&`, so the app receives a fragment missing `refresh_token`/`expires_in` and the auth client silently discards it (sign-in looks like a no-op; the `SimsliAuth` "Handling auth deep link" log line shows the truncated URI). The verify URL is single-use — following it twice yields `#error=access_denied`. Chrome tends to swallow the `simsli://` redirect; firing the deep link directly exercises the app's `handleDeepLink` path deterministically.

---

## What's explicitly out of scope for v1

- Item photos
- Tags / categories
- Tandoor integration
- GPS reminders
- Barcode scanning
- iOS / multiplatform
- Web frontend
