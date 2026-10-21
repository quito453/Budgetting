# Valiu · Activar recordatorios y "Eliminar mi cuenta"

Las claves privadas (llave VAPID privada y CRON_SECRET) **no** van en este repositorio. Guárdalas solo en Supabase.

## 1. Tablas y función de borrar cuenta
SQL Editor → New query → pega `supabase/update-1-cuenta-y-recordatorios.sql` → **Run**.

## 2. Extensiones
Database → Extensions → activa **pg_cron** y **pg_net**.

## 3. Función que envía los recordatorios
1. Edge Functions → **Deploy a new function** → **Via Editor**.
2. Nombre: `recordatorios`. Borra el ejemplo, pega `supabase/functions/recordatorios/index.ts` y toca **Deploy**.
3. En los ajustes de la función, desactiva **Verify JWT** (la protege la clave CRON_SECRET).
4. Edge Functions → **Secrets** → agrega:
   - `VAPID_PRIVATE_JWK` = la llave privada (JSON completo)
   - `VAPID_CONTACT` = `mailto:tu-correo@ejemplo.com`
   - `CRON_SECRET` = una clave larga al azar

## 4. Programarla todos los días a las 8:00 a. m.
Edita `supabase/update-2-programar-recordatorios.sql` con tu URL, tu clave pública y tu CRON_SECRET, pégalo en SQL Editor → **Run**.

## 5. Probar
- En la app: Perfil → **Activar recordatorios** → **Enviar notificación de prueba**.
- Sin esperar a las 8 a. m.: Edge Functions → recordatorios → pruébala con el encabezado `x-cron-secret` y `?dry=1` para ver qué enviaría.
