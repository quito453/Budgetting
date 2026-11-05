-- Valiu · Actualización 4: registrar gastos de Apple Pay automáticamente
-- Pega todo en Supabase → SQL Editor → New query → Run. Se puede ejecutar más de una vez.

-- 1) Clave personal de cada usuario para su iPhone. Solo guardamos su huella (hash), nunca la clave.
create table if not exists public.wallet_tokens (
    token_hash text primary key check (token_hash ~ '^[0-9a-f]{64}$'),
    user_id    uuid not null unique default auth.uid() references auth.users (id) on delete cascade,
    created_at timestamptz not null default now(),
    last_used  timestamptz
);
alter table public.wallet_tokens enable row level security;
grant select, insert, delete on public.wallet_tokens to authenticated;
drop policy if exists "wallet_tokens_select_own" on public.wallet_tokens;
drop policy if exists "wallet_tokens_insert_own" on public.wallet_tokens;
drop policy if exists "wallet_tokens_delete_own" on public.wallet_tokens;
create policy "wallet_tokens_select_own" on public.wallet_tokens for select to authenticated using ((select auth.uid()) = user_id);
create policy "wallet_tokens_insert_own" on public.wallet_tokens for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "wallet_tokens_delete_own" on public.wallet_tokens for delete to authenticated using ((select auth.uid()) = user_id);

-- 2) Bandeja de gastos que llegan del iPhone. La app los pasa a tus gastos y los borra de aquí.
create table if not exists public.wallet_inbox (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references auth.users (id) on delete cascade,
    amount     numeric(12, 2) not null check (amount > 0 and amount < 10000000),
    merchant   text check (merchant is null or length(merchant) <= 80),
    card       text check (card is null or length(card) <= 60),
    created_at timestamptz not null default now()
);
create index if not exists wallet_inbox_user_idx on public.wallet_inbox (user_id, created_at);
alter table public.wallet_inbox enable row level security;
grant select, delete on public.wallet_inbox to authenticated;
drop policy if exists "wallet_inbox_select_own" on public.wallet_inbox;
drop policy if exists "wallet_inbox_delete_own" on public.wallet_inbox;
create policy "wallet_inbox_select_own" on public.wallet_inbox for select to authenticated using ((select auth.uid()) = user_id);
create policy "wallet_inbox_delete_own" on public.wallet_inbox for delete to authenticated using ((select auth.uid()) = user_id);

-- 3) Avisar a la app en vivo cuando llega un gasto.
do $$ begin
    alter publication supabase_realtime add table public.wallet_inbox;
exception when duplicate_object then null; end $$;

-- 4) Borrar cuenta también borra esto (ya pasa solo por "on delete cascade"; se deja explícito).
create or replace function public.delete_my_account()
returns void language plpgsql security definer set search_path = '' as $$
declare uid uuid := auth.uid();
begin
    if uid is null then raise exception 'not authenticated'; end if;
    delete from public.wallet_inbox where user_id = uid;
    delete from public.wallet_tokens where user_id = uid;
    delete from public.push_subscriptions where user_id = uid;
    delete from public.app_state where user_id = uid;
    delete from auth.users where id = uid;
end; $$;
revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;

notify pgrst, 'reload schema';
