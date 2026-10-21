-- Architect Pro · base de datos
-- Pega todo este archivo en Supabase → SQL Editor → New query → Run.
-- Crea una fila privada por usuario con sus datos de la app.

create table if not exists public.app_state (
    user_id    uuid primary key default auth.uid() references auth.users (id) on delete cascade,
    data       jsonb  not null,
    updated_at bigint not null,
    modified   timestamptz not null default now()
);

-- Cada persona solo puede ver y cambiar su propia fila.
alter table public.app_state enable row level security;

drop policy if exists "app_state_select_own" on public.app_state;
drop policy if exists "app_state_insert_own" on public.app_state;
drop policy if exists "app_state_update_own" on public.app_state;
drop policy if exists "app_state_delete_own" on public.app_state;

create policy "app_state_select_own" on public.app_state
    for select to authenticated using ((select auth.uid()) = user_id);
create policy "app_state_insert_own" on public.app_state
    for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "app_state_update_own" on public.app_state
    for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "app_state_delete_own" on public.app_state
    for delete to authenticated using ((select auth.uid()) = user_id);

-- Actualización en vivo entre dispositivos (opcional pero recomendado).
do $$ begin
    alter publication supabase_realtime add table public.app_state;
exception when duplicate_object then null; end $$;
