// ============================================================
//  Architect Pro · conexión con Supabase
//  Pega aquí los datos de tu proyecto (Supabase → botón Connect, o Settings → API Keys).
//  La "publishable key" (sb_publishable_...) es pública por diseño: la seguridad
//  de los datos la dan las reglas (RLS) del archivo supabase/schema.sql.
//  NUNCA pegues aquí la clave "secret" (sb_secret_...) ni "service_role".
// ============================================================
window.APP_CONFIG = {
    supabaseUrl: 'https://fbcmeqsxrvusnpcfwufu.supabase.co',
    supabaseAnonKey: 'sb_publishable_ez7eeVi31Vqa-maHfhYoRA_2Z1vCM31',
    // Llave pública para notificaciones (la privada va solo en Supabase → Edge Functions → Secrets)
    vapidPublicKey: 'BGI4aCyLzVW9O9POAsVfgqEEorcD9dv9SXXY9v2d1tlYZ2-QxtbXDGF0la63VUXZqCF_PhoPj3iTb5M7jl04_5Y'
};
