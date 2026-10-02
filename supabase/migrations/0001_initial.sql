-- Simsli — Supabase Schema
-- Migration: 0001_initial
-- Apply with: supabase db push  (or supabase migration up)
--
-- Design principles:
--   - UUIDs everywhere (client-generated, so offline creation works)
--   - updated_at on every table (used for delta sync)
--   - soft-delete via deleted_at (so offline clients can sync deletions)
--   - RLS enforces household isolation — no app-level filtering needed
--   - A user can belong to multiple households, but each household
--     is independent (no cross-household data)

-- ============================================================
-- Extensions
-- ============================================================

create extension if not exists "uuid-ossp" with schema extensions;
create extension if not exists "moddatetime" with schema extensions;   -- auto-updates updated_at


-- ============================================================
-- Households
-- ============================================================

create table public.households (
  id           uuid primary key default gen_random_uuid(),
  name         text not null default 'My household',
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  deleted_at   timestamptz
);

comment on table public.households is
  'A household is the top-level container. All data belongs to a household.';


-- ============================================================
-- Household members
-- ============================================================

create type public.member_role as enum ('owner', 'member');

create table public.household_members (
  id            uuid primary key default gen_random_uuid(),
  household_id  uuid not null references public.households(id) on delete cascade,
  user_id       uuid not null references auth.users(id) on delete cascade,
  role          public.member_role not null default 'member',
  joined_at     timestamptz not null default now(),
  updated_at    timestamptz not null default now(),

  unique (household_id, user_id)
);

comment on table public.household_members is
  'Maps Supabase auth users to households. One user can be in multiple households.';


-- ============================================================
-- Invite tokens
-- ============================================================

create table public.invite_tokens (
  token         text primary key,                          -- short random string, e.g. 8 chars
  household_id  uuid not null references public.households(id) on delete cascade,
  created_by    uuid not null references auth.users(id),
  created_at    timestamptz not null default now(),
  expires_at    timestamptz not null default now() + interval '24 hours',
  used_at       timestamptz,
  used_by       uuid references auth.users(id)
);

comment on table public.invite_tokens is
  'Single-use invite tokens. Expire after 24 hours.';


-- ============================================================
-- Stores
-- ============================================================

create table public.stores (
  id            uuid primary key default gen_random_uuid(),
  household_id  uuid not null references public.households(id) on delete cascade,
  name          text not null,
  sort_order    float8 not null default 0,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  deleted_at    timestamptz
);

comment on table public.stores is
  'Stores (supermarkets, shops) belonging to a household.
   Items can be assigned to one or more stores.';


-- ============================================================
-- Items
-- ============================================================

create type public.item_type as enum (
  'PERMANENT',    -- stays on list after purchase; done flag is reset on "clear done"
  'ONE_TIME',     -- removed from list entirely after check-off
  'CHECKLIST'     -- done flag reset automatically before each shopping trip (future)
);

create table public.items (
  id            uuid primary key default gen_random_uuid(),
  household_id  uuid not null references public.households(id) on delete cascade,
  name          text not null,
  notes         text,
  type          public.item_type not null default 'PERMANENT',
  sort_order    float8 not null default 0,   -- global order across all items in household
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  deleted_at    timestamptz
);

comment on table public.items is
  'Reusable items that can appear on the shopping list.
   sort_order is global — filter views are subsets in the same order.';


-- ============================================================
-- Item ↔ Store assignments (many-to-many)
-- ============================================================

create table public.item_stores (
  item_id       uuid not null references public.items(id) on delete cascade,
  store_id      uuid not null references public.stores(id) on delete cascade,
  created_at    timestamptz not null default now(),

  primary key (item_id, store_id)
);

comment on table public.item_stores is
  'Which stores carry which items.
   An item with no rows here is considered available at all stores.';


-- ============================================================
-- List entries
-- ============================================================

create table public.list_entries (
  id            uuid primary key default gen_random_uuid(),
  household_id  uuid not null references public.households(id) on delete cascade,
  item_id       uuid not null references public.items(id) on delete cascade,
  quantity      float8,
  unit          text,                         -- e.g. 'kg', 'pcs', 'ml' — free text in v1
  comment       text,                         -- one-time per-purchase comment
  done          boolean not null default false,
  completed_at  timestamptz,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  created_by    uuid references auth.users(id),

  -- Each item can only appear once on the active list
  unique (household_id, item_id)
);

comment on table public.list_entries is
  'The active shopping list. One entry per item per household.
   comment is a one-time note for this purchase, cleared on check-off.
   For ONE_TIME items, the entry (and the item) is deleted after check-off.';


-- ============================================================
-- updated_at triggers (via moddatetime extension)
-- ============================================================

create trigger set_updated_at_households
  before update on public.households
  for each row execute function extensions.moddatetime(updated_at);

create trigger set_updated_at_household_members
  before update on public.household_members
  for each row execute function extensions.moddatetime(updated_at);

create trigger set_updated_at_stores
  before update on public.stores
  for each row execute function extensions.moddatetime(updated_at);

create trigger set_updated_at_items
  before update on public.items
  for each row execute function extensions.moddatetime(updated_at);

create trigger set_updated_at_list_entries
  before update on public.list_entries
  for each row execute function extensions.moddatetime(updated_at);


-- ============================================================
-- Indexes
-- ============================================================

-- Household membership lookups (most queries start here)
create index idx_household_members_user    on public.household_members(user_id);
create index idx_household_members_hh      on public.household_members(household_id);

-- Items by household, excluding soft-deleted
create index idx_items_household           on public.items(household_id) where deleted_at is null;

-- Delta sync: fetch everything changed since last sync
create index idx_items_updated             on public.items(household_id, updated_at);
create index idx_stores_updated            on public.stores(household_id, updated_at);
create index idx_list_entries_updated      on public.list_entries(household_id, updated_at);

-- List entries by household
create index idx_list_entries_household    on public.list_entries(household_id);

-- Item store assignments
create index idx_item_stores_store         on public.item_stores(store_id);


-- ============================================================
-- Row Level Security
-- ============================================================
-- All tables are locked down. Users can only see/modify data
-- that belongs to a household they are a member of.

alter table public.households          enable row level security;
alter table public.household_members   enable row level security;
alter table public.invite_tokens       enable row level security;
alter table public.stores              enable row level security;
alter table public.items               enable row level security;
alter table public.item_stores         enable row level security;
alter table public.list_entries        enable row level security;

-- Helper function: is the current user a member of the given household?
create or replace function public.is_household_member(hh_id uuid)
returns boolean
language sql security definer stable
as $$
  select exists (
    select 1 from public.household_members
    where household_id = hh_id
      and user_id = auth.uid()
  );
$$;

-- Helper function: is the current user the owner of the given household?
create or replace function public.is_household_owner(hh_id uuid)
returns boolean
language sql security definer stable
as $$
  select exists (
    select 1 from public.household_members
    where household_id = hh_id
      and user_id = auth.uid()
      and role = 'owner'
  );
$$;

-- households
create policy "Members can view their household"
  on public.households for select
  using (public.is_household_member(id));

create policy "Members can update their household name"
  on public.households for update
  using (public.is_household_member(id));

create policy "Anyone can create a household"
  on public.households for insert
  with check (true);  -- user must then add themselves as owner (see function below)

-- household_members
create policy "Members can view membership of their households"
  on public.household_members for select
  using (public.is_household_member(household_id));

create policy "Owners can add members"
  on public.household_members for insert
  with check (public.is_household_owner(household_id) or user_id = auth.uid());

create policy "Owners can remove members"
  on public.household_members for delete
  using (public.is_household_owner(household_id) or user_id = auth.uid());

-- invite_tokens
create policy "Members can view invite tokens for their household"
  on public.invite_tokens for select
  using (public.is_household_member(household_id));

create policy "Members can create invite tokens"
  on public.invite_tokens for insert
  with check (public.is_household_member(household_id));

create policy "Token can be read by anyone (for joining)"
  on public.invite_tokens for select
  using (true);  -- needed so unauthenticated users can look up a token before signing in

-- stores
create policy "Members can view stores"
  on public.stores for select
  using (public.is_household_member(household_id));

create policy "Members can manage stores"
  on public.stores for all
  using (public.is_household_member(household_id));

-- items
create policy "Members can view items"
  on public.items for select
  using (public.is_household_member(household_id));

create policy "Members can manage items"
  on public.items for all
  using (public.is_household_member(household_id));

-- item_stores
create policy "Members can view item-store assignments"
  on public.item_stores for select
  using (
    exists (
      select 1 from public.items i
      where i.id = item_id
        and public.is_household_member(i.household_id)
    )
  );

create policy "Members can manage item-store assignments"
  on public.item_stores for all
  using (
    exists (
      select 1 from public.items i
      where i.id = item_id
        and public.is_household_member(i.household_id)
    )
  );

-- list_entries
create policy "Members can view list entries"
  on public.list_entries for select
  using (public.is_household_member(household_id));

create policy "Members can manage list entries"
  on public.list_entries for all
  using (public.is_household_member(household_id));


-- ============================================================
-- Edge function: create_household_with_owner
-- (called on first sync of an offline household)
--
-- Creates household + adds calling user as owner in one
-- transaction. Bypasses RLS via security definer.
-- ============================================================

create or replace function public.create_household_with_owner(
  p_id    uuid,
  p_name  text
)
returns void
language plpgsql security definer
as $$
begin
  insert into public.households (id, name)
  values (p_id, p_name)
  on conflict (id) do nothing;

  insert into public.household_members (household_id, user_id, role)
  values (p_id, auth.uid(), 'owner')
  on conflict (household_id, user_id) do nothing;
end;
$$;


-- ============================================================
-- Edge function: accept_invite
-- (called when a user opens an invite link)
-- ============================================================

create or replace function public.accept_invite(p_token text)
returns uuid   -- returns household_id on success
language plpgsql security definer
as $$
declare
  v_household_id uuid;
begin
  select household_id into v_household_id
  from public.invite_tokens
  where token = p_token
    and used_at is null
    and expires_at > now();

  if v_household_id is null then
    raise exception 'Invalid or expired invite token';
  end if;

  -- Mark token as used
  update public.invite_tokens
  set used_at = now(), used_by = auth.uid()
  where token = p_token;

  -- Add user to household (idempotent)
  insert into public.household_members (household_id, user_id, role)
  values (v_household_id, auth.uid(), 'member')
  on conflict (household_id, user_id) do nothing;

  return v_household_id;
end;
$$;


-- ============================================================
-- Realtime
-- ============================================================
-- Enable realtime publications for tables that need live sync.
-- The Android app subscribes to changes for its household_id.

alter publication supabase_realtime add table public.items;
alter publication supabase_realtime add table public.stores;
alter publication supabase_realtime add table public.item_stores;
alter publication supabase_realtime add table public.list_entries;
alter publication supabase_realtime add table public.household_members;
