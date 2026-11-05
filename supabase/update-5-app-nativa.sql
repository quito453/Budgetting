-- Valiu · Actualización 5: recordatorios en la app de iPhone y Android (App Store / Google Play)
-- Pega todo en Supabase → SQL Editor → New query → Run. Se puede ejecutar más de una vez.

-- Cada teléfono con la app instalada guarda aquí su "dirección" para recibir notificaciones.
create table if not exists public.native_push_tokens (
    token      text primary key check (length(token) between 20 and 4096),
    user_id    uuid not null default auth.uid() references auth.users (id) on delete cascade,
    platform   text not null check (platform in ('ios', 'android')),
    lang       text not null default 'es' check (lang in ('es', 'en')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index if not exists native_push_tokens_user_idx on public.native_push_tokens (user_id);
alter table public.native_push_tokens enable row level security;
grant select, insert, update, delete on public.native_push_tokens to authenticated;
drop policy if exists "npt_select_own" on public.native_push_tokens;
drop policy if exists "npt_insert_own" on public.native_push_tokens;
drop policy if exists "npt_update_own" on public.native_push_tokens;
drop policy if exists "npt_delete_own" on public.native_push_tokens;
create policy "npt_select_own" on public.native_push_tokens for select to authenticated using ((select auth.uid()) = user_id);
create policy "npt_insert_own" on public.native_push_tokens for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "npt_update_own" on public.native_push_tokens for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "npt_delete_own" on public.native_push_tokens for delete to authenticated using ((select auth.uid()) = user_id);

notify pgrst, 'reload schema';
