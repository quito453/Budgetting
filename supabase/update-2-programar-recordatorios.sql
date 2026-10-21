-- Valiu · Actualización 2: enviar los recordatorios todos los días a las 8:00 a. m. (Guatemala = 14:00 UTC)
-- Antes: activa las extensiones pg_cron y pg_net (Database → Extensions) y despliega la función "recordatorios".
-- Reemplaza los 3 valores entre comillas y luego pega todo en SQL Editor → Run.

select vault.create_secret('https://TU-PROYECTO.supabase.co', 'valiu_project_url');
select vault.create_secret('TU_CLAVE_PUBLICA_sb_publishable', 'valiu_publishable_key');
select vault.create_secret('TU_CRON_SECRET', 'valiu_cron_secret');

select cron.schedule(
    'valiu-recordatorios-diarios',
    '0 14 * * *',
    $job$
    select net.http_post(
        url := (select decrypted_secret from vault.decrypted_secrets where name = 'valiu_project_url') || '/functions/v1/recordatorios',
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'apikey', (select decrypted_secret from vault.decrypted_secrets where name = 'valiu_publishable_key'),
            'x-cron-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'valiu_cron_secret')
        ),
        body := '{}'::jsonb,
        timeout_milliseconds := 20000
    ) as request_id;
    $job$
);
