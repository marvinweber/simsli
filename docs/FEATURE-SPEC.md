# Simsli — Feature Spec

Single source of truth for **what Simsli does** (behavior). Technical *why* lives in `docs/adr/`, project setup in `CLAUDE.md`.
Changes to this file are changes to product behavior — discuss before editing.

**Status legend**
- ✅ implemented (works today)
- 🚧 partial (exists with a known gap, noted)
- 📋 **v0.1** — working test build (core list, sync, sharing)
- 📋 **v0.5** — pre-release feature wave (dashboard)
- 📋 **v1** — public release (limits, server switch, export, polish)
- 📋 **v1.5** — Simsli Pro subscriptions + admin role
- 🔭 **v2+** — later milestones
- ❌ out of scope (deliberately not planned)

Milestone tags are assignments, not promises — moving a feature between milestones is a one-line edit.

## Progress overview

| Area                     | ✅ done                 | 🚧 partial           | 📋 v0.1                | 📋 v0.5        | 📋 v1        | 📋 v1.5      | 🔭 v2+    |
|:-------------------------|:-----------------------|:---------------------|:-----------------------|:---------------|:-------------|:-------------|:----------|
| 3.1 Auth (AUTH)          | AUTH-1, AUTH-2, AUTH-3 | —                    | —                      | —              | AUTH-4       | —            | —         |
| 3.2 Household (HH)       | HH-1, HH-2, HH-4, HH-5 | HH-3                 | HH-9                   | —              | HH-6, HH-7   | —            | HH-8      |
| 3.3 Catalog items (ITEM) | ITEM-1, ITEM-2         | ITEM-4               | ITEM-3, ITEM-5, ITEM-6 | —              | —            | —            | —         |
| 3.4 Shopping list (LIST) | LIST-1–7               | —                    | —                      | —              | LIST-8       | —            | —         |
| 3.5 Stores (STORE)       | STORE-1–3              | —                    | STORE-4, STORE-5, STORE-6 | —           | —            | —            | —         |
| 3.6 Categories (CAT)     | CAT-1, CAT-2, CAT-3   | —                    | CAT-4                  | —              | —            | —            | —         |
| 3.7 Sync (SYNC)          | SYNC-1–4               | —                    | —                      | —              | SYNC-5       | —            | —         |
| 3.8 Device data (DATA)   | DATA-1, DATA-3         | —                    | DATA-2                 | —              | DATA-4       | —            | DATA-5    |
| 3.9 Screens (SCREENS)    | SCREENS-1, SCREENS-4, SCREENS-6 | SCREENS-2, SCREENS-3 | SCREENS-5, SCREENS-9 | SCREENS-8   | —            | —            | SCREENS-7 |
| 3.10 Localization (I18N) | —                      | —                    | I18N-1                 | —              | —            | —            | —         |
| 3.11 Business (BIZ)      | BIZ-5, BIZ-6           | —                    | —                      | —              | BIZ-1        | BIZ-2, BIZ-3 | —         |
| 3.12 Dashboard (DASH)    | —                      | —                    | —                      | DASH-1, DASH-2 | —            | —            | —         |

Each feature appears once, under its current status; extensions tagged to later milestones (e.g. HH-2 admin role → v1.5, STORE-1 drag & drop → v1, LIST-5 favorite toggle → v0.1) stay tracked in the §3 entry. DATA-2 ships per path (removal with HH-5 in v0.1, leave/delete with HH-6/7 in v1). BIZ-4 is unlisted — its decision is open (OQ-1).

Last updated: 2026-10-05

---

## 1. Product definition

Simsli is a shared shopping list for households of 2+ people. Items live in a per-household catalog, get assigned to stores, and the store-filtered list is grouped by category in each store's aisle order — the app answers "what do I need *here*, in which order".

Free forever: offline use, self-hosted unlimited. Hosted: free tier with limits, per-household **Simsli Pro** subscription lifts them (§3.11).

## 2. Concepts

- **Household** — the sharing boundary. All data belongs to one household. Server-side a user *could* be in many; the app binds to exactly one (HH-8).
- **Owner** — exactly one per household, immutable until transfer exists (v2+). The only person who can subscribe the household or delete it. Cannot be affected by other members in any way.
- **Member** — manages all content (items, entries, stores, categories), cannot manage the household itself.
- **Admin** *(v1.5)* — member-level plus household management (rename, invites, member management) — but can never touch the Owner: no demotion, no removal, no role change against them.
- **Catalog item** — permanent metadata: name, type, stores, category, default unit, notes. Lives until deleted.
- **List entry** — ephemeral "on the list right now" row referencing an item. Holds quantity, unit, comment, done state. One per item per household.
- **Store** — a physical shop. Items can be assigned to several stores.
- **Category** — per-household grouping (name + emoji). Ordered globally; optionally re-ordered per store.
- **Invite code** — 8-char single-use code, valid 24h.
- **Plan** — per-household: `free` or `pro`, plus subscription validity state (BIZ-0). Limits are derived from it.
- **Backup** — full-fidelity JSON export of a household (DATA-4). Import is future (DATA-5).
- **Favorite filter** — shared toggle (filter bar LIST-5, dashboard DASH-1/DASH-2): **ON**, an item counts for a store only if that store is favorited for it; an item with **no favorites at all** counts for all its stores. **OFF**, favorites are ignored entirely.
- **Currency** — per household, display-only: chosen from a preset list, its symbol is shown next to store prices. No conversion, no calculations, ever.

## 3. Features

### 3.1 Auth & device (AUTH)

- **AUTH-1 Magic-link sign-in ✅** — email → OTP mail → `simsli://auth` deep link (or HTTP landing page with automatic redirect and fallback "Open App" button). Session persists via JWT and replays at app start, triggering an initial sync.
  - *Token security & storage note:* Magic link tokens are high-entropy (32-byte) strings valid for 15 minutes and single-use. Currently, tokens are stored raw in the database to allow display in the admin dashboard (convenient for zero-SMTP / local development & family LAN self-hosting). For production cloud deployments, tokens should be stored as SHA-256 hashes to prevent token exposure in database dumps/leaks, revealing the raw token only once at creation time if needed.
- **AUTH-2 Sign out ✅** — revokes the session and **wipes all local app data** (Room + outbox), leaving a clean device. The wipe is serialized against sync runs (an in-flight pull can't repopulate the wiped data), and happens even when the server revoke fails (e.g. offline): the device is signed out regardless, only the refresh-token revocation is then left to expire server-side. Unsynced offline writes are lost; that is accepted (DATA-3).
- **AUTH-3 Offline-first use ✅** — fully usable signed out: local household with a random UUID, everything works, unlimited. Signing in later adopts the offline household server-side (user becomes Owner) — subject to the adoption limit check (BIZ-1).
- **AUTH-4 Email/password sign-in 📋 v1** — decided as part of auth scope (magic link + email/password), not yet built.

### 3.2 Household & membership (HH)

- **HH-1 Household creation ✅** — implicit: offline create or auto "My household" on first sync; explicit rename ✅ (rename rights: Owner only; Admins v1.5). Household emoji icon 📋 v0.1 — selected via the ADR-0014 emoji picker, same edit rights as rename (Owner; Admins join in v1.5).
- **HH-2 Roles & guards ✅/📋** — **exactly one Owner per household**, immutable: nobody can demote, remove, or otherwise affect the Owner, and the Owner cannot leave (their exits: delete the household — HH-7 — or ownership transfer, 🔭 v2+). Members manage all content; household management (rename, invite, remove, role changes) is Owner-only. Invite creation is owner-only via RLS policy (`0004_household_members_and_join.sql`) and UI guard ✅. **Admin role 📋 v1.5**: members promoted by the Owner gain household-management rights over all *other* members — never over the Owner. Owner-guard RPC checks for admins 📋 v1.5.
- **HH-3 Invite 🚧** — Owner generates an 8-char code (A–Z/2–9, no 0/O/1/I/L), single-use, 24h expiry, shown with copy-to-clipboard ✅. Owner-only creation enforced server & UI ✅. Expired/used codes fail with a clear error ✅. **Deep link 📋 v0.1** — the code is also shareable as `simsli://invite/<code>`; opening it asks for sign-in first if needed, then jumps straight into the join flow (HH-4).
- **HH-4 Join by code ✅** — signed-in user enters a code → `accept_invite` → membership created in joined household. Guarded: owners of non-empty households cannot join another household (UI + RPC guard) ✅. Joining shows warning that current household will be deleted. Joining deletes caller's current household on server (if sole owner) or leaves it (if member), and wipes local device data before adopting the joined household ✅.
- **HH-5 Remove member ✅** — Owner removes a member via Household members screen (`remove_household_member` RPC). Owner cannot remove self or another owner. Removed member's device wipes household data on next sync detection (DATA-2) ✅. Admins gain this right in v1.5 (HH-2).
- **HH-6 Leave household 📋 v1** — members can leave on their own (new server policy). **The Owner cannot leave** (HH-2). Leaving wipes the device (DATA-2).
- **HH-7 Delete household 📋 v1** — **only the Owner** may delete, and **never while the household has an active subscription** (cancel first, BIZ-3). Requires typing the household name to confirm. Server-side: **soft delete with 30-day retention** — household and data (memberships included) become invisible to all queries; the Owner can restore within 30 days (no restore UI in v1 — manual via SQL/support; restore UI v2 🔭). After 30 days a deterministic GC hard-deletes everything (mechanism open, OQ-4). The deleting device wipes immediately; other members' devices wipe on next sync detection (DATA-2).
- **HH-8 Single-household app ❌→🔭 v2** — the app binds to the user's first membership; no household switcher in v1 (the v2 bottom bar will need one somewhere). The server schema/RPCs stay multi-household-general so v2 can add switching without backend rework (sync watermarks are per-table — re-keying per household is part of that v2 work).
- **HH-9 Currency 📋 v0.1** — per household, chosen from a preset list of common currencies (picker rows shown as "EUR €", "USD $", …), default EUR. Display-only: the symbol is rendered next to store prices (STORE-6). No conversion, no calculations. Edit rights as rename (Owner; Admins join in v1.5).

### 3.3 Catalog items (ITEM)

- **ITEM-1 Create/edit ✅** — name, notes, links (catalog column storing URLs and fetched page titles; notes field automatically extracts pasted/typed URLs into interactive link chips with native app icon or direct favicon, background title fetching, chip removal, and "+ Add link" dialog), type, category (CAT-2 ✅), store assignments, (📋 v0.1: default unit ITEM-5).
- **ITEM-2 Item types ✅** — `PERMANENT` (normal case, lives in catalog forever) and `ONE_TIME` (retired together with its checked-off entry at GC). Legacy `CHECKLIST` stays hidden in UI/DB.
- **ITEM-3 Duplicate-name warning 📋 v0.1** — creating (or renaming to) a name that already exists in the household (case-insensitive) asks "already exists — add anyway?".
- **ITEM-4 Delete item ✅** — soft delete in repository. Deleting an item removes its active entries in `list_entries` and store assignments in `item_stores` (and enqueues outbox deletions). UI entry point in item detail top bar and bottom action with confirmation dialog.
- **ITEM-5 Default unit 📋 v0.1** — optional unit preset on the item, preselected when adding to the list, changeable per entry.
- **ITEM-6 Reference size 📋 v0.1** — optional free-text reference size per item ("1 l", "500 g"), edited at the top of the Store Prices screen (STORE-6). Purely informational — displayed wherever the item's prices are shown ("1,09 € / 1 l"); never translated, no unit logic. Working assumption: a user prices one item in the same reference size across stores.

### 3.4 Shopping list (LIST)

- **LIST-1 Add to list ✅** — one entry per item per household (UNIQUE). Adding an item that is in "Recently checked" moves it back to active; details entered with the add are applied, blank fields keep the entry's previous values.
- **LIST-2 Fast-add ✅** — the list FAB (sits in the bottom filter bar, LIST-5) or home screen app shortcut ("Add item") opens the quick-add sheet directly. **Search state**: auto-focused search field; with text entered, up to **5 catalog suggestions** (prefix matches first) plus a permanent **NEW "‹query›"** row at the top (distinct icon) that creates from the query — always available, even alongside an exact match. **Selected state**: tapping any suggestion dismisses the keyboard and shows a compact form (quantity, unit, comment) under the selection, which stays as a header with ✕ back to search. Suggestions whose item already has an entry (active or recently checked) open that entry's editor instead. New items get a **"Save ‹name› to Catalog" checkbox, default off**: off = `ONE_TIME` (removed together with its checked-off entry at GC), checked = `PERMANENT`; an (i) explains both in plain words. Buttons: **Add** (add + reset to search state, sheet stays open for rapid-fire adds) and **Add & Close**.
- **LIST-3 Check off ✅** — ticking or swiping an active item in either direction shows the completion animation before moving to "Recently checked" (flat section at the bottom, undo-able); a 10s snackbar with a "Revert" action allows immediate undo. A "Clear" button in the "Recently checked" section header allows manually clearing recently checked items immediately; otherwise after **24h** (deterministic TTL, no coordination) any device's GC deletes the entry locally and on the server; ONE_TIME items retire with it.
- **LIST-4 Entry editor ✅** — shared bottom sheet: quantity (optional number), unit (preset list: pcs/Stk., g, kg, ml, l + free-text custom; defaults from item per ITEM-5 📋 v0.1), comment, and read-only catalog note (if present on the item). Recognized links in comments or catalog notes are automatically extracted and rendered as interactive chips (with app icon for native handlers or web favicon). On the shopping list, rows show the item name alongside a note indicator icon (when a catalog note exists) and a link chain icon (when links are detected), with quantity/unit + comment combined on line 2 ("100g ‧ spicy"). On the list it edits an existing entry (**Save** / **Remove from list**); the same sheet opens from the Items tab's per-row **+** to add a catalog item to the list with details (**Save** / **Cancel**).
- **LIST-5 Filter bar ✅** — a floating, rounded bar in the navigation bar's container color, inset from the screen edges and the bottom navigation bar with equal 8 dp margins, structured as `(+) (filters…) (✕)`. On the left, a static 48 dp **+** FAB opens the quick-add sheet; in the middle, a horizontally scrollable row holds filter chips that **compose**: a **store chip** and a **category chip**. Each chip opens a dropdown menu (trailing ▲): the store menu lists every store, **No Store** (items without any store assignment), and **No Filter**; the category menu every category, **Uncategorized** (items without any category), and **No Filter**. While a concrete selection is active the chip shows its name (categories with their emoji) and a leading ✓; menu rows show the active option as ✓, stores as 🏬, "No Store"/"Uncategorized" as ⊘, "No Filter" as a struck-through funnel. A store view shows only items assigned to that store — items without any store assignment are excluded from store views; **No Store** shows only items without any assignment. On the right, a static **✕** reset icon button appears whenever any filter is active, clearing all filter dimensions at once. 📋 v0.1: a star toggle applies the favorite filter (§2) to the selected store — active only while a concrete store is selected.
- **LIST-6 Category grouping ✅** — the list is always grouped by category (v1 has exactly one view): store-filtered → that store's category order, unordered categories appended in global order (STORE-4); unfiltered → global order. Uncategorized items form an implicit group at the end. "Recently checked" stays a flat section after all groups.
- **LIST-7 Ordering ✅** — within a group, items follow the catalog's global `sortOrder`. No drag & drop for items.
- **LIST-8 Shopping mode 📋 v1** — full-screen focused check-off view, available when a store filter is active: large touch targets, one tap to check, checked items collapse immediately (no "Recently checked" section here). Exit via back or "Done shopping" — exiting changes nothing (the TTL GC is the cleanup, LIST-3).

### 3.5 Stores (STORE)

- **STORE-1 Store management ✅/📋** — create, rename, delete ✅; managed in the Catalog tab's **Stores** tab (SCREENS-2, 📋 v0.1 move). Reorder: repository support ✅, drag & drop UI 📋 v1.
- **STORE-2 Item↔store assignment ✅** — many-to-many, edited in item detail.
- **STORE-3 Delete store ✅** — its item assignments are removed (cascade; local reconcile follows); **items are preserved** (unassigned for that store — visible unfiltered and under the "No Store" filter, LIST-5).
- **STORE-4 Per-store category order 📋 v0.1** — store edit has a checklist of the household's categories; checked categories can be ordered (drag) → `store_categories` rows. Unchecked categories fall back to global order, appended after the explicit ones (LIST-6).
- **STORE-5 Favorite stores 📋 v0.1** — among an item's assigned stores, any subset can be favorited ("where I usually buy this"). Item detail store chips gain a trailing star: chip body assigns/unassigns, star toggles the favorite (only active while assigned; unassigning a favorited store clears the favorite). Consumers: the LIST-5 star toggle and the dashboard's favorite filter (DASH-1/DASH-2); nothing else derives from favorites.
- **STORE-6 Store prices 📋 v0.1** — optional price per assigned store. Item detail gains a **"Store prices"** entry → pushed screen: reference size input (ITEM-6) at the top, then one row per assigned store with a price field (empty = unpriced). Prices render with the household currency symbol (HH-9) and locale-aware number formatting; no totals and no mapping against entry quantity/unit — comparing stores for one item is the whole job.

### 3.6 Categories (CAT)

- **CAT-1 Category entity ✅** — per household: name + emoji icon (selected via AndroidX EmojiPickerView, ADR-0014) + global `sortOrder`. Global order is user-draggable.
- **CAT-2 Item assignment ✅** — an item has **zero or one** category; selected via single-choice filter chips in the item detail screen; null = "Uncategorized" (implicit group, always last, not a real category).
- **CAT-3 Management ✅** — the Catalog tab's **Categories** tab (SCREENS-2): create, rename, icon, delete, drag global order. Deleting a category sets its items to uncategorized (nothing else breaks).
- **CAT-4 Setup presets 📋 v0.1** — offered during household setup (SCREENS-5) to apply wholesale: Obst & Gemüse/Produce 🍎, Backwaren/Bakery 🥖, Milchprodukte/Dairy 🥛, Fleisch & Fisch/Meat & Fish 🥩, Grundnahrungsmittel/Pantry 🍝, Tiefkühl/Frozen ❄️, Getränke/Drinks 🧃, Snacks/Snacks 🍫, Haushalt/Household 🧻, Drogerie/Personal Care 🧴, Sonstiges/Other 📦. Editable afterwards like any category.

### 3.7 Sync (SYNC)

- **SYNC-1 Triggers ✅** — sign-in/session restore; debounced (500 ms) requests after every local write; Realtime WebSocket invalidation events on `list_entries`, `items`, `stores`, `categories`, `store_categories` (foreground only: connection active while app is in foreground, closed on backgrounding/close) ✅; manual "Sync now".
- **SYNC-2 Pipeline ✅** — resolveHousehold → flushOutbox (push current row state) → watermark delta pulls (`updated_at`, microsecond precision) → GC. Join tables without `updated_at` (`item_stores` ✅, `store_categories` ✅) reconcile by full set comparison.
- **SYNC-3 Conflict resolution ✅** — last write wins per row (no payloads; the current local row state is pushed).
- **SYNC-4 Realtime-as-trigger ✅** — realtime events only request a sync; the watermark pull is transport and backstop.
- **SYNC-5 Offline/pending indicator 📋 v1** — subtle, honest indicator when the device is offline or has unsynced writes (outbox pending); clears when sync completes. No modal nagging.

### 3.8 Device data & backups (DATA)

- **DATA-1 Sign-out wipe ✅** — see AUTH-2.
- **DATA-2 Membership-end wipe 📋** — leaving (HH-6, v1), being removed (HH-5, v0.1), the household being deleted (HH-7, v1), or losing RLS access any other way wipes household data on that device. Each path ships with its trigger.
- **DATA-3 Unsynced writes are lost on any wipe ✅ (accepted)** — documented consequence of the wipe policy; sync runs continuously (debounced + realtime), so the realistic loss window is small.
- **DATA-4 Export (JSON backup) 📋 v1** — any member can export the complete household data at any time — online or offline, signed in or not. Format: one **version-tagged JSON file** with full fidelity (items, stores, categories, list entries incl. state, all relations intact) — the format a future import (DATA-5) consumes. Shared via the Android share sheet.
- **DATA-5 Import 🔭 v2** — if built, it **always creates a new household** (never merges) and must **re-generate every UUID** (server PKs are global; the original household still holds the exported rows) and strip membership/user references. Requires v2 multi-household (HH-8) or a refined replace-in-place rule — semantics open until then (OQ-4).

### 3.9 Navigation & screens (SCREENS)

- **SCREENS-1 Bottom navigation ✅/📋** — bottom app bar with three tabs: **Shopping List · Catalog · Settings**. Settings is promoted from a pushed screen to a tab. Tab/sub-tab UI state (scroll position, open dialogs) survives tab switches. 📋 v0.1: the Settings tab is renamed to the household's name (SCREENS-4); 📋 v0.5: **Dashboard** is inserted as the second tab (four tabs, SCREENS-8).
- **SCREENS-2 Catalog tabs 🚧** — the Catalog tab is a tabbed view: **Items | Categories | Stores**. Stores management moved here from its own pushed screen ✅; Categories management (CAT-3) with create, rename, emoji, delete, and drag order ✅. In Items, each row's **+** opens the entry details sheet (LIST-4) to add the item to the list with quantity/unit/comment; items already on the list show a check in the same position.
- **SCREENS-3 Household screen 🚧 v0.1 (minimal) / 📋 v1 (full)** — reached from Settings: member list with roles, remove member (HH-5) ✅. Leave (HH-6), delete household (HH-7), household rename to move here in v1.
- **SCREENS-4 Settings tab ✅/📋** — tab with Material 3 Expressive cards: Account (magic-link sign-in; signed-in state with confirmation dialog before data-wiping sign-out), Household (UUID display with tap-to-copy, rename, household members screen navigation, owner-only invite token generation with copy & system share sheet, join by code with non-empty owner guard and deletion warning dialog; 📋 v0.1 adds emoji icon (HH-1) and currency (HH-9) editing), Sync ("Sync now" manual trigger, conditionally shown when signed in), About (branding, app version), and Developer options (seed demo data, debug builds only). Data (DATA-4) and Server (BIZ-5) arrive with those v1 features. 📋 v0.1: the tab is renamed from "Settings" to the **household's name** (single line, ellipsized) and its icon becomes the **household emoji** (fallback: person icon when unset); long-press will open the household switcher (🔭 v2, HH-8).
- **SCREENS-5 Onboarding 📋 v0.1** — minimal first-launch flow: welcome → sign-in (**skippable** — offline stays first-class) → household setup: name + apply category presets (CAT-4). Fine-tuning (stores, per-store order) happens in the management screens afterwards.
- **SCREENS-6 Existing screens ✅** — List tab (active entries in compact modern rows with indented dividers and swipe-to-complete, recently checked, fast-add sheet, entry editor, floating filter bar with the add FAB above the bottom nav, LIST-5), Item detail (create/edit metadata with double-tap protection), onboarding placeholder (replaced by SCREENS-5).
- **SCREENS-7 Appearance 🔭 v2** — manual theme override (Light/Dark/System) in Settings. Until then the app follows the system (✅ current behavior, M3 dynamic color).
- **SCREENS-8 Dashboard tab 📋 v0.5** — inserted as the second tab (SCREENS-1), hosting DASH-1 and DASH-2 with the star icon toggle (favorite filter, §2). Widgets recompute live from the local DB — offline-first: rendering never requires a sync.
- **SCREENS-9 Top app bar & overflow menu ✅** — Simsli logo (the sync-indicator slot and its animation are preserved) + screen title **left-aligned**, avoiding layout shifts from differing title lengths; this makes room for a trailing ⋮ overflow on Shopping List and Catalog (and Dashboard from v0.5). First entry: **"Refresh"** — requests a sync (same mechanism as "Sync now", which stays in Settings). Shown signed out as well, where it is an honest local no-op.

### 3.10 Localization (I18N)

- **I18N-1 EN default + DE overlay 📋 v0.1** — all UI strings live in string resources: `values/` (English, default/fallback) + `values-de/` (German). No hardcoded UI strings. User-entered content (item names, category names, custom units) is never translated; built-in content (unit presets, category presets, messages) ships in both languages. Numbers and dates render locale-aware.

### 3.11 Business model: limits, subscriptions, hosting (BIZ)

**Model overview** — offline use: free, unlimited, forever. Self-hosted: free, unlimited, forever (point the app at your own server). Hosted (simsli.app): free tier with limits; a per-household **Simsli Pro** subscription lifts all limits. One backend instance/cluster serves all households; each household carries `plan` (`free`/`pro`) plus subscription validity (`status`, `current_period_end`), written by the billing webhook — entitlement = plan `pro` **and** valid period (BIZ-3). **Backend architecture:** Powered by an in-house Go backend (`simsli-server`, ADR 0015) supporting embedded SQLite for single-container self-hosting (~15 MB RAM) and PostgreSQL for scalable cloud hosting, featuring atomic batched sync, WebSocket invalidation, and an embedded admin dashboard.

- **BIZ-1 Free-tier limits 📋 v1** — a free hosted household: max **2 users**, **3 stores**, **20 catalog items**, **25 live list entries** (active + recently-checked combined; the 24h GC frees space). Enforced **server-side** (RPC/constraint checks reading a server config; a self-hosted instance defaults to unlimited) **and pre-checked client-side** for friendly errors and to show the upsell. Offline households are never limited; adoption of an over-limit offline household on sign-in is **refused** with a clear message (trim and retry, or self-host) — local data is untouched. Joining at the 2-user cap is refused (HH-4).
- **BIZ-2 Lapse / fits-or-readonly 📋 v1.5** — when a subscription ends, the household reverts to `free`. If it fits the free limits, it keeps working as a free household. If it exceeds them: **read-only** — everything visible, no edits/adds/invites until trimmed below the limits or resubscribed. No data is deleted, ever.
- **BIZ-3 Subscribe (Simsli Pro) 📋 v1.5** — the **Owner** subscribes *their household* (per-household subscription, not per-user). Active subscription: all limits lifted. The Owner cannot delete the household while subscribed (cancel first, HH-7). Cancellation runs to period end, then BIZ-2 applies.
- **BIZ-4 Payment stack — open (OQ-1)** — provider-agnostic until subscriptions are built. Note: distributing via Google Play forces Play Billing for in-app subscriptions. Billing webhooks will be handled server-side.
- **BIZ-5 Server switch ✅** — Settings → custom server URL. Shows real-time connection status, server latency, and server version vs app version with compatibility check. Local data is retained if switching to another instance of the same household or adopting an offline household; sign-out cleanly wipes if desired (AUTH-2).
- **BIZ-6 Self-host promise ✅ (policy)** — a self-hosted instance is unlimited by default, requires no account with simsli.app, and phones home to nothing. The OSS guarantee: AGPL, full feature set self-hosted. Delivered as a single-container Go binary (`simsli-server`) with embedded SQLite (~15 MB RAM), zero-SMTP login links/QRs, telemetry, and database table browsing in the admin dashboard (ADR 0015).

### 3.12 Dashboard (DASH)

Computed over the **active list entries** (recently checked excluded) and independent of the List tab's store filter; recalculated live from the local DB.

- **DASH-1 Stores to visit 📋 v0.5** — the minimum set of stores that covers the list. Per item, candidate stores: favorite filter off → all assigned stores; on → favorites if any, else all assigned stores (§2). Deterministic greedy set cover (repeatedly take the store covering the most yet-uncovered items, tie → store sort order) so all devices converge without coordination. Items without any store assignment are counted as uncovered.
- **DASH-2 Cheapest split 📋 v0.5** — per item with at least one priced store (STORE-6): the cheapest store + price, reference size appended if set (tie → store order); favorite filter on restricts competitors to favorites per §2. Items without any price are listed under "no price" (honest empty state until prices are filled in). **No totals, ever** — entry quantity/unit is never mapped against the reference size.

## 4. Data model delta (what this spec adds)

Server (new migrations):
- `categories` (id, household_id, name, emoji, sort_order, timestamps) — watermark-pulled
- `store_categories` (store_id, category_id, sort_order) — full-reconcile, like `item_stores`
- `items.category_id` (nullable), `items.default_unit` (nullable)
- `items.reference_size` (nullable free text)
- `item_stores.favorite` (bool, default false), `item_stores.price` (nullable) — full-reconcile mechanism unchanged
- `households.icon` (nullable emoji), `households.currency` (code, default EUR) — synced with the household row
- RLS/RPC: invite insert policy → Owner-only (v0.1); `leave_household` policy (member deletes own membership, v1); household deletion = soft delete via `deleted_at` + 30-day retention GC (v1, mechanism open OQ-4); owner-guard checks for admin role changes (v1.5)
- Plan & entitlements (v1/v1.5): `households.plan`, subscription state (status, period end), server config table (limits; self-host default unlimited), limit checks in RPCs
- Billing webhook (v1.5): first Edge Function writing plan/validity

Local (Room): mirror tables/entities for the above (watermarks + reconcile unchanged in shape); runtime server settings (URL/key) in local storage (BIZ-5).

## 5. Out of scope (deliberately not planned)

❌ Item photos · tags (categories cover the need) · unit **conversions** · GPS/store reminders · barcode scanning · iOS/web · **push notifications** 🔭 v2/v3 (FCM + Edge Function is the path) · customizable list views 🔭 (one good view first) · item drag & drop reordering · **ownership transfer** 🔭 v2/v3 (until then the Owner's only exits are HH-7 deletion or staying) · household switcher UI 🔭 v2 (HH-8) · import 🔭 v2 (DATA-5) · restore-household UI 🔭 v2 (retention ships v1, HH-7) · price totals / basket optimization beyond the two dashboard widgets · price/quantity math (entry units are never mapped against reference sizes) · currency conversion · price history · favorites influencing anything beyond the favorite filter (LIST-5 toggle, DASH-1/DASH-2).

## 6. Open questions

1. **Payment stack & distribution** — Play Billing vs Stripe; decides BIZ-4 and whether Play Store distribution happens at all.
2. **Limits-before-subscriptions window** — if v1 launches before v1.5, early hosted households hit limits with no purchase path. Acceptable for a short window; otherwise move BIZ-1 to v1.5 (launch unlimited-free as a promo).
3. **Import semantics** — v2: new-household-plus-switcher (needs HH-8 v2) vs replace-in-place; decided when multi-household is designed.
4. **Retention GC mechanism** — how the 30-day household hard-delete runs server-side (pg_cron vs lazy-on-access vs scheduled function).
5. **Supabase tier trigger** — confirmed intent: free tier for testing, paid tier at first real load/paying user (BIZ-0); revisit costs when the userbase exists.
6. **App name & icon** — "Simsli" is a placeholder.
