-- Simsli — Categories (CAT-1, CAT-2) and per-store category order (STORE-4)
-- Migration: 0002_categories
--
--   - categories: per-household grouping (name + emoji), global sort_order,
--     delta-synced via updated_at like stores/items
--   - store_categories: per-store aisle order for categories — join table
--     without updated_at, reconciled by full set comparison (like item_stores)
--   - items.category_id: zero or one category per item; null = uncategorized
--
-- Note on deletes: items/category deletes are soft (deleted_at), so the FK's
-- `on delete set null` only covers hard deletes (e.g. retention GC). The
-- regular "delete category → items become uncategorized" rule (CAT-3) is
-- applied by the clients when soft-deleting.

-- ============================================================
-- Categories
-- ============================================================

create table public.categories (
  id            uuid primary key default uuid_generate_v4(),
  household_id  uuid not null references public.households(id) on delete cascade,
  name          text not null,
  emoji         text,
  sort_order    float8 not null default 0,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  deleted_at    timestamptz
);

comment on table public.categories is
  'Per-household category (name + emoji) with a global sort order.
   Items have zero or one category; uncategorized items group implicitly.';

-- Items point at their category; null = uncategorized.
alter table public.items
  add column category_id uuid references public.categories(id) on delete set null;


-- ============================================================
-- Store ↔ Category aisle order
-- ============================================================

create table public.store_categories (
  store_id      uuid not null references public.stores(id) on delete cascade,
  category_id   uuid not null references public.categories(id) on delete cascade,
  sort_order    float8 not null default 0,
  created_at    timestamptz not null default now(),

  primary key (store_id, category_id)
);

comment on table public.store_categories is
  'Explicit per-store category ordering (the store''s aisle order).
   Categories without a row here fall back to the global order,
   appended after the explicit ones.';

-- ============================================================
-- updated_at trigger (via moddatetime extension)
-- ============================================================

create trigger set_updated_at_categories
  before update on public.categories
  for each row execute function moddatetime(updated_at);


-- ============================================================
-- Indexes
-- ============================================================

create index idx_categories_household      on public.categories(household_id) where deleted_at is null;
create index idx_categories_updated        on public.categories(household_id, updated_at);
create index idx_items_category            on public.items(category_id);
create index idx_store_categories_store    on public.store_categories(store_id);
create index idx_store_categories_category on public.store_categories(category_id);


-- ============================================================
-- Row Level Security
-- ============================================================

alter table public.categories      enable row level security;
alter table public.store_categories enable row level security;

-- categories (same shape as stores)
create policy "Members can view categories"
  on public.categories for select
  using (public.is_household_member(household_id));

create policy "Members can manage categories"
  on public.categories for all
  using (public.is_household_member(household_id));

-- store_categories (via the store's household — same shape as item_stores)
create policy "Members can view store-category orderings"
  on public.store_categories for select
  using (
    exists (
      select 1 from public.stores s
      where s.id = store_id
        and public.is_household_member(s.household_id)
    )
  );

create policy "Members can manage store-category orderings"
  on public.store_categories for all
  using (
    exists (
      select 1 from public.stores s
      where s.id = store_id
        and public.is_household_member(s.household_id)
    )
  );


-- ============================================================
-- Realtime
-- ============================================================
-- stores is already in the publication (0001); the app subscribes to its
-- changes from now on alongside the new tables.

alter publication supabase_realtime add table public.categories;
alter publication supabase_realtime add table public.store_categories;
