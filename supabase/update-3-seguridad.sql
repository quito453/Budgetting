-- Valiu · Actualización 3: refuerzos de seguridad
-- Pega todo en Supabase → SQL Editor → New query → Run. Se puede ejecutar más de una vez.

-- 1) Solo direcciones reales de los servicios de notificación (Apple, Google, Mozilla, Microsoft).
--    Evita que alguien registre una dirección cualquiera para que nuestro servidor le envíe solicitudes.
alter table public.push_subscriptions drop constraint if exists push_endpoint_valido;
alter table public.push_subscriptions add constraint push_endpoint_valido check (
    endpoint ~ '^https://(fcm\.googleapis\.com|android\.googleapis\.com|[a-z0-9.-]+\.push\.apple\.com|updates\.push\.services\.mozilla\.com|[a-z0-9.-]+\.notify\.windows\.com)/'
    and length(endpoint) < 1000 and length(p256dh) < 200 and length(auth) < 100
    and lang in ('es', 'en') and (ua is null or length(ua) <= 200)
) not valid;

-- 2) Máximo 10 dispositivos con recordatorios por persona.
create or replace function public.push_limit() returns trigger language plpgsql security definer set search_path = '' as $$
begin
    if (select count(*) from public.push_subscriptions where user_id = new.user_id and endpoint <> new.endpoint) >= 10 then
        raise exception 'too many devices';
    end if;
    return new;
end; $$;
revoke all on function public.push_limit() from public, anon, authenticated;
drop trigger if exists push_limit on public.push_subscriptions;
create trigger push_limit before insert on public.push_subscriptions for each row execute function public.push_limit();

-- 3) Tamaño máximo de los datos de cada persona (2 MB). Una cuenta normal usa mucho menos.
alter table public.app_state drop constraint if exists app_state_tamano;
alter table public.app_state add constraint app_state_tamano check (octet_length(data::text) < 2000000) not valid;

-- 4) Nadie sin sesión puede usar la función de borrar cuenta (ya era así; se confirma).
revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;

notify pgrst, 'reload schema';
