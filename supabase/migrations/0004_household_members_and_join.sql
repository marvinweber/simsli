-- Simsli — Migration 0004: Household members management, owner-only invites, and joining logic
--
-- 1. Invite tokens: only household owners can create invite tokens (HH-2 / HH-3)
-- 2. RPC get_household_members: lets members view fellow members with email
-- 3. RPC remove_household_member: lets owner remove non-owner members (HH-5)
-- 4. RPC accept_invite: deletes or leaves caller's current household when joining a new one

-- 1. Update invite token creation policy to owner-only
drop policy if exists "Members can create invite tokens" on public.invite_tokens;

create policy "Owners can create invite tokens"
  on public.invite_tokens for insert
  with check (public.is_household_owner(household_id));

-- 2. get_household_members RPC
create or replace function public.get_household_members(p_household_id uuid)
returns table (
  id uuid,
  household_id uuid,
  user_id uuid,
  role public.member_role,
  email text,
  name text,
  joined_at timestamptz
)
language plpgsql security definer
as $$
begin
  if not public.is_household_member(p_household_id) then
    raise exception 'Not a member of this household';
  end if;

  return query
  select
    hm.id,
    hm.household_id,
    hm.user_id,
    hm.role,
    u.email::text,
    coalesce(u.raw_user_meta_data->>'full_name', u.raw_user_meta_data->>'name')::text as name,
    hm.joined_at
  from public.household_members hm
  left join auth.users u on u.id = hm.user_id
  where hm.household_id = p_household_id
  order by (hm.role = 'owner') desc, hm.joined_at asc;
end;
$$;

-- 3. remove_household_member RPC
create or replace function public.remove_household_member(p_household_id uuid, p_user_id uuid)
returns void
language plpgsql security definer
as $$
declare
  v_role public.member_role;
begin
  if not public.is_household_owner(p_household_id) then
    raise exception 'Only owners can remove members';
  end if;

  if p_user_id = auth.uid() then
    raise exception 'Owner cannot remove themselves from the household';
  end if;

  select role into v_role
  from public.household_members
  where household_id = p_household_id and user_id = p_user_id;

  if v_role is null then
    raise exception 'Member not found';
  end if;

  if v_role = 'owner' then
    raise exception 'Cannot remove an owner';
  end if;

  delete from public.household_members
  where household_id = p_household_id and user_id = p_user_id;
end;
$$;

-- 4. accept_invite RPC (updated to delete/leave current household on join)
create or replace function public.accept_invite(p_token text)
returns uuid   -- returns new household_id on success
language plpgsql security definer
as $$
declare
  v_target_household_id uuid;
  v_membership record;
  v_other_members int;
begin
  -- Validate token
  select household_id into v_target_household_id
  from public.invite_tokens
  where token = p_token
    and used_at is null
    and expires_at > now();

  if v_target_household_id is null then
    raise exception 'Invalid or expired invite token';
  end if;

  -- Check if already in target household
  if exists (
    select 1 from public.household_members
    where household_id = v_target_household_id and user_id = auth.uid()
  ) then
    raise exception 'You are already a member of this household';
  end if;

  -- Handle caller's existing memberships:
  -- If caller owns a household:
  --   If it has other members -> forbid join.
  --   If caller is the only member -> delete that household completely (cascades).
  -- If caller is a non-owner member -> remove caller from that household.
  for v_membership in
    select household_id, role
    from public.household_members
    where user_id = auth.uid()
  loop
    if v_membership.role = 'owner' then
      select count(*) into v_other_members
      from public.household_members
      where household_id = v_membership.household_id and user_id != auth.uid();

      if v_other_members > 0 then
        raise exception 'You cannot join another household as you are a member of a non empty household';
      else
        delete from public.households where id = v_membership.household_id;
      end if;
    else
      delete from public.household_members
      where household_id = v_membership.household_id and user_id = auth.uid();
    end if;
  end loop;

  -- Mark token as used
  update public.invite_tokens
  set used_at = now(), used_by = auth.uid()
  where token = p_token;

  -- Add user to the new household
  insert into public.household_members (household_id, user_id, role)
  values (v_target_household_id, auth.uid(), 'member');

  return v_target_household_id;
end;
$$;

-- Permissions
grant execute on function public.get_household_members(uuid) to authenticated;
grant execute on function public.remove_household_member(uuid, uuid) to authenticated;
grant execute on function public.accept_invite(text) to authenticated;
