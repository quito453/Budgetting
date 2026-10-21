// Valiu · Edge Function "recordatorios"
// Runs once a day (pg_cron). For every user with reminders on, finds statement credit cards that are
// due in 3 days / tomorrow / today / were due yesterday, and sends a Web Push notification.
// Secrets needed (Edge Functions → Secrets): VAPID_PRIVATE_JWK, VAPID_CONTACT, CRON_SECRET.
import { buildPushHTTPRequest } from "npm:@pushforge/builder@2.0.5";
import { createClient } from "npm:@supabase/supabase-js@2.112.4";

const TZ = "America/Guatemala";
const DEFAULT_DAYS = [3, 1, 0];
const TEXT = {
  es: {
    title: "Valiu · Recordatorio de pago",
    in: (n: number, name: string) => n === 0 ? `Hoy vence tu tarjeta ${name}.` : n === 1 ? `Mañana vence tu tarjeta ${name}.` : n < 0 ? `Tu tarjeta ${name} venció ayer: paga al menos el mínimo hoy.` : `Tu tarjeta ${name} vence en ${n} días.`,
    amounts: (min: string, full: string) => `Mínimo ${min} · Contado ${full}.`,
    tip: "Pagar el total evita intereses.",
  },
  en: {
    title: "Valiu · Payment reminder",
    in: (n: number, name: string) => n === 0 ? `Your ${name} card is due today.` : n === 1 ? `Your ${name} card is due tomorrow.` : n < 0 ? `Your ${name} card was due yesterday: pay at least the minimum today.` : `Your ${name} card is due in ${n} days.`,
    amounts: (min: string, full: string) => `Minimum ${min} · In full ${full}.`,
    tip: "Paying in full avoids interest.",
  },
};

function todayISO(): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: TZ, year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
}
function daysBetween(a: string, b: string): number {
  return Math.round((Date.parse(b + "T00:00:00Z") - Date.parse(a + "T00:00:00Z")) / 86400000);
}
function money(n: number, code: string, lang: string): string {
  try { return new Intl.NumberFormat(lang === "en" ? "en-US" : "es-GT", { style: "currency", currency: code || "GTQ", maximumFractionDigits: 0 }).format(n); }
  catch { return String(Math.round(n)); }
}

// deno-lint-ignore no-explicit-any
export function remindersFor(data: any, today: string) {
  const notify = data?.notify ?? {};
  if (notify.on === false) return [];
  const days: number[] = Array.isArray(notify.days) && notify.days.length ? notify.days : DEFAULT_DAYS;
  const lang = data?.lang === "en" ? "en" : "es";
  const L = TEXT[lang];
  // deno-lint-ignore no-explicit-any
  return (data?.items ?? []).filter((i: any) => i?.stmt?.due && !i.paid && !i.completed && (i.stmt.full ?? 0) > 0)
    // deno-lint-ignore no-explicit-any
    .map((i: any) => ({ i, n: daysBetween(today, i.stmt.due) }))
    // deno-lint-ignore no-explicit-any
    .filter(({ n }: any) => days.includes(n))
    // deno-lint-ignore no-explicit-any
    .map(({ i, n }: any) => ({
      title: L.title,
      body: `${L.in(n, i.name)} ${L.amounts(money(Math.min(i.stmt.min || i.stmt.full, i.stmt.full), data.currencyCode, lang), money(i.stmt.full, data.currencyCode, lang))} ${n >= 0 ? L.tip : ""}`.trim(),
      tag: `valiu-${i.id}-${i.stmt.due}`,
      url: "./",
    }));
}

Deno.serve(async (req) => {
  if (!Deno.env.get("CRON_SECRET") || req.headers.get("x-cron-secret") !== Deno.env.get("CRON_SECRET")) {
    return new Response("forbidden", { status: 403 });
  }
  const dryRun = new URL(req.url).searchParams.get("dry") === "1";
  const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY")!, {
    auth: { persistSession: false },
  });
  const privateJWK = Deno.env.get("VAPID_PRIVATE_JWK")!;
  const adminContact = Deno.env.get("VAPID_CONTACT") ?? "mailto:soporte@valiu.app";

  const { data: subs, error: e1 } = await sb.from("push_subscriptions").select("endpoint, p256dh, auth, user_id");
  if (e1) return Response.json({ error: e1.message }, { status: 500 });
  const userIds = [...new Set((subs ?? []).map((s) => s.user_id))];
  if (!userIds.length) return Response.json({ users: 0, sent: 0 });

  const { data: rows, error: e2 } = await sb.from("app_state").select("user_id, data").in("user_id", userIds);
  if (e2) return Response.json({ error: e2.message }, { status: 500 });

  const today = todayISO();
  let sent = 0, removed = 0, failed = 0;
  const preview: unknown[] = [];
  for (const row of rows ?? []) {
    const msgs = remindersFor(row.data, today);
    if (!msgs.length) continue;
    for (const sub of (subs ?? []).filter((s) => s.user_id === row.user_id)) {
      for (const payload of msgs) {
        if (dryRun) { preview.push({ user: row.user_id, ...payload }); continue; }
        try {
          const { endpoint, headers, body } = await buildPushHTTPRequest({
            privateJWK,
            subscription: { endpoint: sub.endpoint, keys: { p256dh: sub.p256dh, auth: sub.auth } },
            message: { payload, adminContact, options: { ttl: 43200, urgency: "high", topic: payload.tag.slice(0, 32).replace(/[^A-Za-z0-9_-]/g, "") } },
          });
          const res = await fetch(endpoint, { method: "POST", headers, body });
          if (res.status === 404 || res.status === 410) {
            await sb.from("push_subscriptions").delete().eq("endpoint", sub.endpoint);
            removed++;
          } else if (res.ok) sent++;
          else failed++;
        } catch (_e) { failed++; }
      }
    }
  }
  return Response.json({ date: today, users: (rows ?? []).length, sent, removed, failed, ...(dryRun ? { preview } : {}) });
});
