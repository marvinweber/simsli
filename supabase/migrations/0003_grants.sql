-- Simsli — Role grants for authenticated and anon roles
-- Migration: 0003_grants
--
-- Grants required privileges to 'authenticated' and 'anon' roles on schema public.
-- In recent Supabase releases, new tables are not automatically granted to the Data API roles.
-- Explicit grants are required so that PostgREST queries pass table-level checks
-- before Row Level Security (RLS) policies are evaluated.

-- 1. Schema usage
grant usage on schema public to anon, authenticated;

-- 2. Table permissions for authenticated users
grant select, insert, update, delete on all tables in schema public to authenticated;

-- 3. Table permissions for anonymous users (needed for invite token lookup before sign-in)
grant select on table public.invite_tokens to anon;

-- 4. Sequences (if any sequences are used or created in future)
grant usage, select on all sequences in schema public to authenticated, anon;

-- 5. Function execution permissions
grant execute on all functions in schema public to authenticated, anon;

-- 6. Set default privileges so future tables, sequences, and functions receive these grants automatically
alter default privileges in schema public grant select, insert, update, delete on tables to authenticated;
alter default privileges in schema public grant select on tables to anon;
alter default privileges in schema public grant usage, select on sequences to authenticated, anon;
alter default privileges in schema public grant execute on functions to authenticated, anon;
