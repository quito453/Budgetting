-- Valiu · Actualización 1: borrar cuenta desde la app + suscripciones a recordatorios
-- Pega todo en Supabase → SQL Editor → New query → Run.

-- 1) Dispositivos que aceptaron recordatorios (una fila por navegador/celular)
create table if not exists public.push_subscriptions (
    endpoint   text primary key,
    user_id    uuid not null default auth.uid() references auth.users (id) on delete cascade,
    p256dh     text not null,
    auth       text not null,
    lang       text not null default 'es',
    ua         text,
    created_at timestamptz not null default now()
);
create index if not exists push_subscriptions_user_idx on public.push_subscriptions (user_id);
alter table public.push_subscriptions enable row level security;

drop policy if exists "push_select_own" on public.push_subscriptions;
drop policy if exists "push_insert_own" on public.push_subscriptions;
drop policy if exists "push_update_own" on public.push_subscriptions;
drop policy if exists "push_delete_own" on public.push_subscriptions;
create policy "push_select_own" on public.push_subscriptions for select to authenticated using ((select auth.uid()) = user_id);
create policy "push_insert_own" on public.push_subscriptions for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "push_update_own" on public.push_subscriptions for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "push_delete_own" on public.push_subscriptions for delete to authenticated using ((select auth.uid()) = user_id);

-- 2) Borrar mi cuenta: solo puede borrarse a sí mismo quien tiene la sesión iniciada
create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare uid uuid := auth.uid();
begin
    if uid is null then
        raise exception 'not authenticated';
    end if;
    delete from public.push_subscriptions where user_id = uid;
    delete from public.app_state where user_id = uid;
    delete from auth.users where id = uid;
end;
$$;
revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
