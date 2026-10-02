-- Simsli — local development seed data
--
-- Applied automatically by `supabase db reset` (and on the first `supabase start`).
-- Only meant for the local Docker stack.
--
-- Signs-in-ready test accounts (magic link only — the app has no password
-- login yet, so no password hashes are seeded):
--   test1@simsli.de — household owner
--   test2@simsli.de — second member
-- Their magic-link mails land in the local mail UI (http://127.0.0.1:54324).
--
-- The on-device twin of this dataset (offline / signed-out use case) lives in
-- app/src/main/java/net/marvinweber/simsli/data/debug/DemoDataSeeder.kt —
-- keep the two in mind when changing either.
--
-- Fixed UUID scheme (deterministic, easy to reference in queries):
--   users      10000000-0000-0000-0000-0000000000NN
--   household  20000000-0000-0000-0000-000000000001
--   memberships 21000000-0000-0000-0000-0000000000NN
--   stores     30000000-0000-0000-0000-0000000000NN
--   items      40000000-0000-0000-0000-0000000000NN
--   entries    50000000-0000-0000-0000-0000000000NN
--   categories 60000000-0000-0000-0000-0000000000NN

-- ============================================================
-- Auth users (direct GoTrue insert; emails pre-confirmed)
-- ============================================================

-- instance_id must be the zero UUID: GoTrue's user lookup filters on it and
-- the column has no default, so omitting it yields NULL and logins fail.
insert into auth.users (
  instance_id, id, aud, role, email, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data,
  confirmation_token, recovery_token, email_change, email_change_token_new,
  created_at, updated_at
)
values
  ('00000000-0000-0000-0000-000000000000', '10000000-0000-0000-0000-000000000001',
   'authenticated', 'authenticated', 'test1@simsli.de',
   now(), '{"provider": "email", "providers": ["email"]}', '{}',
   '', '', '', '', now(), now()),
  ('00000000-0000-0000-0000-000000000000', '10000000-0000-0000-0000-000000000002',
   'authenticated', 'authenticated', 'test2@simsli.de',
   now(), '{"provider": "email", "providers": ["email"]}', '{}',
   '', '', '', '', now(), now())
on conflict (id) do nothing;

insert into auth.identities (
  user_id, provider_id, provider, identity_data, last_sign_in_at, created_at, updated_at
)
values
  ('10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'email',
   jsonb_build_object('sub', '10000000-0000-0000-0000-000000000001',
                      'email', 'test1@simsli.de', 'email_verified', true),
   now(), now(), now()),
  ('10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'email',
   jsonb_build_object('sub', '10000000-0000-0000-0000-000000000002',
                      'email', 'test2@simsli.de', 'email_verified', true),
   now(), now(), now())
on conflict do nothing;

-- ============================================================
-- Household + members
-- ============================================================

insert into public.households (id, name, created_at, updated_at)
values
  ('20000000-0000-0000-0000-000000000001', 'Testhaushalt',
   now() - interval '30 days', now() - interval '30 days')
on conflict (id) do nothing;

insert into public.household_members (id, household_id, user_id, role, joined_at, updated_at)
values
  ('21000000-0000-0000-0000-000000000001',
   '20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
   'owner', now() - interval '30 days', now() - interval '30 days'),
  ('21000000-0000-0000-0000-000000000002',
   '20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002',
   'member', now() - interval '29 days', now() - interval '29 days')
on conflict (id) do nothing;

-- ============================================================
-- Stores
-- ============================================================

insert into public.stores (id, household_id, name, sort_order, created_at, updated_at)
values
  ('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
   'REWE', 1.0, now() - interval '30 days', now() - interval '12 days'),
  ('30000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
   'Aldi', 2.0, now() - interval '30 days', now() - interval '12 days'),
  ('30000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000001',
   'DM',   3.0, now() - interval '30 days', now() - interval '12 days')
on conflict (id) do nothing;

-- ============================================================
-- Categories (global order; items can be uncategorized)
-- ============================================================

insert into public.categories (id, household_id, name, emoji, sort_order, created_at, updated_at)
values
  ('60000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
   'Obst & Gemüse', '🍎', 1.0, now() - interval '11 days', now() - interval '6 days'),
  ('60000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
   'Milchprodukte', '🥛', 2.0, now() - interval '11 days', now() - interval '6 days'),
  ('60000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000001',
   'Backwaren', '🥖', 3.0, now() - interval '11 days', now() - interval '6 days'),
  ('60000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000001',
   'Grundnahrungsmittel', '🍝', 4.0, now() - interval '11 days', now() - interval '6 days'),
  ('60000000-0000-0000-0000-000000000005', '20000000-0000-0000-0000-000000000001',
   'Haushalt', '🧻', 5.0, now() - interval '11 days', now() - interval '6 days'),
  ('60000000-0000-0000-0000-000000000006', '20000000-0000-0000-0000-000000000001',
   'Sonstiges', '📦', 6.0, now() - interval '11 days', now() - interval '6 days')
on conflict (id) do nothing;

-- ============================================================
-- Items (mostly PERMANENT; last two are ONE_TIME).
-- Geschenkpapier stays uncategorized on purpose — it exercises the implicit
-- "Uncategorized" group in the category grouping (LIST-6).
-- ============================================================

insert into public.items (id, household_id, name, notes, type, category_id, sort_order, created_at, updated_at)
values
  ('40000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
   'Milch',           null,                        'PERMANENT', '60000000-0000-0000-0000-000000000002', 1.0, now() - interval '20 days', now() - interval '1 day'),
  ('40000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
   'Brot',            null,                        'PERMANENT', '60000000-0000-0000-0000-000000000003', 2.0, now() - interval '20 days', now() - interval '2 days'),
  ('40000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000001',
   'Butter',          null,                        'PERMANENT', '60000000-0000-0000-0000-000000000002', 3.0, now() - interval '20 days', now() - interval '2 days'),
  ('40000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000001',
   'Haferdrink',      'Barista-Edition',           'PERMANENT', '60000000-0000-0000-0000-000000000002', 4.0, now() - interval '18 days', now() - interval '3 days'),
  ('40000000-0000-0000-0000-000000000005', '20000000-0000-0000-0000-000000000001',
   'Käse',            null,                        'PERMANENT', '60000000-0000-0000-0000-000000000002', 5.0, now() - interval '18 days', now() - interval '3 days'),
  ('40000000-0000-0000-0000-000000000006', '20000000-0000-0000-0000-000000000001',
   'Eier',            'Freilandhaltung',           'PERMANENT', '60000000-0000-0000-0000-000000000002', 6.0, now() - interval '18 days', now() - interval '4 days'),
  ('40000000-0000-0000-0000-000000000007', '20000000-0000-0000-0000-000000000001',
   'Äpfel',           null,                        'PERMANENT', '60000000-0000-0000-0000-000000000001', 7.0, now() - interval '15 days', now() - interval '4 days'),
  ('40000000-0000-0000-0000-000000000008', '20000000-0000-0000-0000-000000000001',
   'Bananen',         null,                        'PERMANENT', '60000000-0000-0000-0000-000000000001', 8.0, now() - interval '15 days', now() - interval '5 days'),
  ('40000000-0000-0000-0000-000000000009', '20000000-0000-0000-0000-000000000001',
   'Kaffee',          'Bohnen, dunkle Röstung',    'PERMANENT', '60000000-0000-0000-0000-000000000004', 9.0, now() - interval '15 days', now() - interval '5 days'),
  ('40000000-0000-0000-0000-000000000010', '20000000-0000-0000-0000-000000000001',
   'Nudeln',          null,                        'PERMANENT', '60000000-0000-0000-0000-000000000004', 10.0, now() - interval '14 days', now() - interval '6 days'),
  ('40000000-0000-0000-0000-000000000011', '20000000-0000-0000-0000-000000000001',
   'Tomatenpassata',  null,                        'PERMANENT', '60000000-0000-0000-0000-000000000004', 11.0, now() - interval '14 days', now() - interval '6 days'),
  ('40000000-0000-0000-0000-000000000012', '20000000-0000-0000-0000-000000000001',
   'Spülmittel',      null,                        'PERMANENT', '60000000-0000-0000-0000-000000000005', 12.0, now() - interval '10 days', now() - interval '7 days'),
  ('40000000-0000-0000-0000-000000000013', '20000000-0000-0000-0000-000000000001',
   'Toilettenpapier', 'dreilagig',                 'PERMANENT', '60000000-0000-0000-0000-000000000005', 13.0, now() - interval '10 days', now() - interval '7 days'),
  ('40000000-0000-0000-0000-000000000014', '20000000-0000-0000-0000-000000000001',
   'Geburtstagskerzen', null,                      'ONE_TIME',  '60000000-0000-0000-0000-000000000006', 14.0, now() - interval '8 days',  now() - interval '8 days'),
  ('40000000-0000-0000-0000-000000000015', '20000000-0000-0000-0000-000000000001',
   'Geschenkpapier',  null,                        'ONE_TIME',  null, 15.0, now() - interval '8 days',  now() - interval '8 days')
on conflict (id) do nothing;

-- ============================================================
-- Item ↔ store assignments (items without a row are available everywhere)
-- ============================================================

insert into public.item_stores (item_id, store_id, created_at)
values
  ('40000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', now() - interval '12 days'),  -- Milch → REWE
  ('40000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000002', now() - interval '12 days'),  -- Milch → Aldi
  ('40000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', now() - interval '12 days'),  -- Brot → REWE
  ('40000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000002', now() - interval '12 days'),  -- Brot → Aldi
  ('40000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000001', now() - interval '12 days'),  -- Butter → REWE
  ('40000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000002', now() - interval '12 days'),  -- Butter → Aldi
  ('40000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000001', now() - interval '12 days'),  -- Haferdrink → REWE
  ('40000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000001', now() - interval '12 days'),  -- Käse → REWE
  ('40000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000002', now() - interval '12 days'),  -- Käse → Aldi
  ('40000000-0000-0000-0000-000000000006', '30000000-0000-0000-0000-000000000001', now() - interval '12 days'),  -- Eier → REWE
  ('40000000-0000-0000-0000-000000000006', '30000000-0000-0000-0000-000000000002', now() - interval '12 days'),  -- Eier → Aldi
  ('40000000-0000-0000-0000-000000000007', '30000000-0000-0000-0000-000000000001', now() - interval '12 days'),  -- Äpfel → REWE
  ('40000000-0000-0000-0000-000000000007', '30000000-0000-0000-0000-000000000002', now() - interval '12 days'),  -- Äpfel → Aldi
  ('40000000-0000-0000-0000-000000000008', '30000000-0000-0000-0000-000000000002', now() - interval '12 days'),  -- Bananen → Aldi
  ('40000000-0000-0000-0000-000000000009', '30000000-0000-0000-0000-000000000001', now() - interval '12 days'),  -- Kaffee → REWE
  ('40000000-0000-0000-0000-000000000009', '30000000-0000-0000-0000-000000000002', now() - interval '12 days'),  -- Kaffee → Aldi
  ('40000000-0000-0000-0000-000000000010', '30000000-0000-0000-0000-000000000002', now() - interval '12 days'),  -- Nudeln → Aldi
  ('40000000-0000-0000-0000-000000000011', '30000000-0000-0000-0000-000000000001', now() - interval '12 days'),  -- Tomatenpassata → REWE
  ('40000000-0000-0000-0000-000000000011', '30000000-0000-0000-0000-000000000002', now() - interval '12 days'),  -- Tomatenpassata → Aldi
  ('40000000-0000-0000-0000-000000000012', '30000000-0000-0000-0000-000000000001', now() - interval '12 days'),  -- Spülmittel → REWE
  ('40000000-0000-0000-0000-000000000012', '30000000-0000-0000-0000-000000000003', now() - interval '12 days'),  -- Spülmittel → DM
  ('40000000-0000-0000-0000-000000000013', '30000000-0000-0000-0000-000000000002', now() - interval '12 days'),  -- Toilettenpapier → Aldi
  ('40000000-0000-0000-0000-000000000013', '30000000-0000-0000-0000-000000000003', now() - interval '12 days'),  -- Toilettenpapier → DM
  ('40000000-0000-0000-0000-000000000014', '30000000-0000-0000-0000-000000000003', now() - interval '8 days'),   -- Geburtstagskerzen → DM
  ('40000000-0000-0000-0000-000000000015', '30000000-0000-0000-0000-000000000003', now() - interval '8 days')    -- Geschenkpapier → DM
on conflict do nothing;

-- ============================================================
-- Store ↔ category aisle order
-- REWE: full explicit order (differs from the global one). Aldi: partial —
-- the rest falls back to global order, appended. DM: none — pure fallback.
-- ============================================================

insert into public.store_categories (store_id, category_id, sort_order, created_at)
values
  -- REWE: Milchprodukte, Backwaren, Obst & Gemüse, Grundnahrungsmittel, Haushalt
  ('30000000-0000-0000-0000-000000000001', '60000000-0000-0000-0000-000000000002', 1.0, now() - interval '11 days'),
  ('30000000-0000-0000-0000-000000000001', '60000000-0000-0000-0000-000000000003', 2.0, now() - interval '11 days'),
  ('30000000-0000-0000-0000-000000000001', '60000000-0000-0000-0000-000000000001', 3.0, now() - interval '11 days'),
  ('30000000-0000-0000-0000-000000000001', '60000000-0000-0000-0000-000000000004', 4.0, now() - interval '11 days'),
  ('30000000-0000-0000-0000-000000000001', '60000000-0000-0000-0000-000000000005', 5.0, now() - interval '11 days'),
  -- Aldi: Obst & Gemüse, Milchprodukte (Rest fällt auf globale Ordnung zurück)
  ('30000000-0000-0000-0000-000000000002', '60000000-0000-0000-0000-000000000001', 1.0, now() - interval '11 days'),
  ('30000000-0000-0000-0000-000000000002', '60000000-0000-0000-0000-000000000002', 2.0, now() - interval '11 days')
on conflict do nothing;

-- ============================================================
-- List entries: 8 active, 3 checked off recently ("Recently checked";
-- completed_at well within the 24 h TTL so the GC keeps them around)
-- ============================================================

insert into public.list_entries
  (id, household_id, item_id, quantity, unit, comment, done, completed_at, created_at, updated_at, created_by)
values
  -- active
  ('50000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
   '40000000-0000-0000-0000-000000000001', 2, 'Liter', null,
   false, null, now() - interval '1 day', now() - interval '1 day',
   '10000000-0000-0000-0000-000000000001'),
  ('50000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
   '40000000-0000-0000-0000-000000000002', 1, null, 'das mit den Körnern',
   false, null, now() - interval '1 day', now() - interval '1 day',
   '10000000-0000-0000-0000-000000000001'),
  ('50000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000001',
   '40000000-0000-0000-0000-000000000004', 2, 'Liter', null,
   false, null, now() - interval '2 days', now() - interval '2 days',
   '10000000-0000-0000-0000-000000000001'),
  ('50000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000001',
   '40000000-0000-0000-0000-000000000006', 1, 'Packung', null,
   false, null, now() - interval '2 days', now() - interval '2 days',
   '10000000-0000-0000-0000-000000000001'),
  ('50000000-0000-0000-0000-000000000005', '20000000-0000-0000-0000-000000000001',
   '40000000-0000-0000-0000-000000000009', 1, null, 'keine Pads',
   false, null, now() - interval '3 days', now() - interval '3 days',
   '10000000-0000-0000-0000-000000000001'),
  ('50000000-0000-0000-0000-000000000006', '20000000-0000-0000-0000-000000000001',
   '40000000-0000-0000-0000-000000000012', 1, null, null,
   false, null, now() - interval '3 days', now() - interval '3 days',
   '10000000-0000-0000-0000-000000000001'),
  ('50000000-0000-0000-0000-000000000007', '20000000-0000-0000-0000-000000000001',
   '40000000-0000-0000-0000-000000000007', 1, 'kg', 'säuerliche',
   false, null, now() - interval '4 days', now() - interval '4 days',
   '10000000-0000-0000-0000-000000000001'),
  ('50000000-0000-0000-0000-000000000008', '20000000-0000-0000-0000-000000000001',
   '40000000-0000-0000-0000-000000000013', 10, 'Rollen', null,
   false, null, now() - interval '4 days', now() - interval '4 days',
   '10000000-0000-0000-0000-000000000001'),
  -- recently checked (undo-able until the daily GC)
  ('50000000-0000-0000-0000-000000000009', '20000000-0000-0000-0000-000000000001',
   '40000000-0000-0000-0000-000000000003', 1, null, null,
   true, now() - interval '2 hours', now() - interval '5 days', now() - interval '2 hours',
   '10000000-0000-0000-0000-000000000001'),
  ('50000000-0000-0000-0000-000000000010', '20000000-0000-0000-0000-000000000001',
   '40000000-0000-0000-0000-000000000010', 2, 'Packung', null,
   true, now() - interval '3 hours', now() - interval '5 days', now() - interval '3 hours',
   '10000000-0000-0000-0000-000000000001'),
  ('50000000-0000-0000-0000-000000000011', '20000000-0000-0000-0000-000000000001',
   '40000000-0000-0000-0000-000000000005', 1, 'Stück', 'Gouda, mittelalt',
   true, now() - interval '2 hours', now() - interval '5 days', now() - interval '2 hours',
   '10000000-0000-0000-0000-000000000001')
on conflict (id) do nothing;
