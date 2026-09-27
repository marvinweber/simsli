# Simsli

**Simple Shared Shopping List**

A store-aware, household shopping list for Android. Add items once, assign them to stores, and see exactly what to buy wherever you are — in the order the store is laid out.

> **What Simsli does** — features, rules, and what's implemented vs. planned — is defined in [`docs/FEATURE-SPEC.md`](docs/FEATURE-SPEC.md), the single source of truth for product behavior. This README only gives the short version; on any conflict, the spec wins.

---

## What makes Simsli different

Most shopping list apps are just lists. Simsli is store-aware: every item knows where it can be bought (and, via categories, in which aisle). Standing in REWE, you filter by REWE and see exactly what belongs there, grouped and ordered the way you walk the store. The underlying list stays the same — filters are just lenses.

## The short version

- **Catalog + ephemeral list** — items live in a household catalog; list entries are strictly "what's on the list right now". Checking off keeps the entry for 24h ("Recently checked", undo-able), then a deterministic garbage collection removes it on every device — no "clear done" button.
- **Store-aware** — items can belong to multiple stores; the store filter is the main lens. Items without a store assignment show in every store view, so nothing gets missed at the shop.
- **Shared household** — owners invite via a single-use code; changes propagate in real time while the app is open and via delta sync otherwise. Every device can check off, add, and edit.
- **Offline first** — fully usable signed out; the offline household is adopted server-side on first sign-in, so no work is lost.
- **Categories** *(planned)* — per-household categories with emoji icons, ordered globally and per store, so the list reads in aisle order.

Implemented/planned status for every feature (and everything deliberately out of scope — photos, tags, unit conversions, GPS reminders, barcode scanning, iOS/web, push) lives in the spec's status markers.

## Architecture

```
simsli/
├── app/                            # Android app (Kotlin, Jetpack Compose)
│   └── src/main/java/net/marvinweber/simsli/
│       ├── data/
│       │   ├── local/              # Room database, DAOs, entities, mappers
│       │   ├── remote/             # Supabase access, DTOs, mappers
│       │   ├── repository/         # Single source of truth (Flow-based)
│       │   └── sync/               # SyncManager, RealtimeObserver, outbox
│       ├── domain/model/           # Pure Kotlin models
│       ├── ui/
│       │   ├── theme/              # Material 3 theme
│       │   ├── navigation/         # Routes + NavHost
│       │   └── screens/            # home/, list/, catalog/, item/, stores/, settings/
│       └── di/                     # Hilt modules
├── docs/
│   ├── FEATURE-SPEC.md             # What the app does (features & rules)
│   └── adr/                        # Architecture decision records (why)
├── supabase/
│   ├── migrations/                 # SQL schema, RLS policies, RPCs
│   ├── seed.sql                    # local test data (auto-applied by `supabase db reset`)
│   └── config.toml
├── LICENSE                         # AGPL v3
└── README.md
```

Technical *decisions* — why Supabase, why offline-first, why realtime is only a trigger — are recorded as short ADRs in [`docs/adr/`](docs/adr/), following [`0000-adr-template.md`](docs/adr/0000-adr-template.md). Accepted ADRs are never edited; changed thinking results in a new ADR that supersedes the old one.

## Tech stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| DI | Hilt |
| Local DB | Room (offline-first) |
| Sync | supabase-kt (watermark delta sync, outbox pushes) |
| Realtime | Supabase Realtime — events trigger delta pulls, pulls are the transport |
| Auth | Supabase Auth (Magic Link) |
| Async | Kotlin Coroutines + Flow |

---

## Backend options

### Managed (simsli.app) — planned
Hosted backend with a free tier; Pro at €1/month. Self-hosting remains fully free. Not live yet.

### Self-hosted
Simsli talks directly to Supabase — no custom API server. Run your own instance (Docker Compose or the Supabase CLI), apply `supabase/migrations/`, and point the app at it via `supabase.url` in `local.properties`.

### Offline only
Works today: no account needed, all data stays on device. Sharing and sync are unavailable.

---

## Local development

Run the bundled Supabase stack locally:

```bash
supabase start          # API :54321, Studio :54323, Mailpit (dev inbox) :54324
supabase db reset       # rebuild the local database from migrations
supabase status         # prints URLs and API keys
```

Put the API key into `local.properties` (never committed — `supabase status` prints it):

```
supabase.anon.key=<publishable / anon key>
```

The app defaults to the emulator's host alias (`http://10.0.2.2:54321`); override with `supabase.url` in the same file.

### Emulator: make 127.0.0.1 links work

Magic-link emails and other locally generated links point at `127.0.0.1` — inside an emulator that's the emulator itself, not your machine. Forward the ports once per emulator boot:

```bash
adb reverse tcp:54321 tcp:54321   # API / auth verify links
adb reverse tcp:54324 tcp:54324   # Mailpit inbox at http://127.0.0.1:54324
```

(No `adb` on your PATH? It comes with Android Studio: `~/Library/Android/sdk/platform-tools/adb`.)

With the reverse in place: request a magic link in the app (Settings), open the mail from Mailpit in the emulator's browser, click the link — it redirects back into the app via `simsli://auth` and sync starts.

### Test data

`supabase/seed.sql` is applied automatically on `supabase db reset` and gives you a ready-to-debug setup:

- **Accounts** `test1@simsli.de` (household owner) and `test2@simsli.de` (second member) — magic-link sign-in only; their mails land in Mailpit like any other
- **Household "Testhaushalt"** — stores REWE/Aldi/DM, 15 catalog items (incl. 2 one-time), 11 list entries (8 active, 3 recently checked)

`supabase db reset` always brings the database back to this state.

For the **offline / signed-out** use case, debug builds have Settings → Debug → **Seed demo data**: it wipes the local database and inserts the same dataset directly into Room, deliberately bypassing the sync outbox — the data stays on device until something real triggers a sync. Signing in afterwards adopts the demo household server-side and uploads it (the offline-adoption path); seeding while signed in merges the demo rows into the server household. Release builds don't contain this action.

**Keeping the seeds current:** these are two hand-maintained twins of one dataset — `supabase/seed.sql` (server) and `DemoDataSeeder.kt` (`app/src/main/java/net/marvinweber/simsli/data/debug/DemoDataSeeder.kt`, device). Every change to the data model — a new migration, a Room entity, a domain model field, a sync-contract addition — is the trigger to ask: *do the seeds need the same change?* If they drift, the demo setup breaks silently: rows that no longer apply cleanly, or test scenarios that no longer cover what the schema now does.

---

## Household sharing

1. First launch creates your household automatically
2. Settings → Household → **Invite someone** → share the 8-character code (single use, valid 24 h)
3. The other person signs in (magic link) and enters the code via **Join household**
4. Their device adopts the household — items, stores, and list entries are shared from then on

---

## License

AGPL v3 (`AGPL-3.0-or-later`). See [LICENSE](LICENSE).

You may use, study, modify, and redistribute Simsli — self-hosting for any purpose, personal or commercial, is explicitly fine. If you run Simsli (modified or not) as a network service, you must offer its complete source code to that service's users (AGPL §13).

The **Simsli** name, logo, and branding are **not** licensed under AGPL and remain reserved by the author — forks and hosted deployments are welcome under their own name, but not as "Simsli".
