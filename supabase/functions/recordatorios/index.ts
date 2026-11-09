// Valiu · Edge Function "recordatorios"
// Runs once a day (pg_cron). For every user with reminders on, finds what is due in 3 days / tomorrow / today /
// was due yesterday (cards with their statement, plus fixed bills, loan installments and cards by their payment day)
// and sends a notification:
//   - Web Push to browsers and home-screen apps (push_subscriptions)
//   - FCM to the Android app and APNs to the iPhone app (native_push_tokens)
// Secrets (Edge Functions → Secrets): VAPID_PRIVATE_JWK, VAPID_CONTACT, CRON_SECRET,
//   and for the store apps: FCM_SERVICE_ACCOUNT (the Firebase JSON key), APNS_KEY_P8, APNS_KEY_ID, APNS_TEAM_ID,
//   APNS_BUNDLE_ID (com.valiugt.app) and APNS_ENV ("production", or "sandbox" while testing from Xcode).
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
    billIn: (n: number, what: string) => n === 0 ? `Hoy vence ${what}.` : n === 1 ? `Mañana vence ${what}.` : n < 0 ? `Ayer venció ${what}.` : `En ${n} días vence ${what}.`,
    what: (kind: string, name: string) => kind === "loan" ? `la cuota de ${name}` : kind === "cc" ? `tu tarjeta ${name}` : `tu pago de ${name}`,
    approx: "aprox.",
    recapTitle: (m: string) => `Valiu · Tu resumen de ${m}`,
    recapBody: (m: string) => `Ya está listo tu resumen de ${m}: cuánto gastaste, ahorraste y abonaste. Abre Valiu para verlo; el mes nuevo empieza en cero.`,
  },
  en: {
    title: "Valiu · Payment reminder",
    in: (n: number, name: string) => n === 0 ? `Your ${name} card is due today.` : n === 1 ? `Your ${name} card is due tomorrow.` : n < 0 ? `Your ${name} card was due yesterday: pay at least the minimum today.` : `Your ${name} card is due in ${n} days.`,
    amounts: (min: string, full: string) => `Minimum ${min} · In full ${full}.`,
    tip: "Paying in full avoids interest.",
    billIn: (n: number, what: string) => n === 0 ? `${what} is due today.` : n === 1 ? `${what} is due tomorrow.` : n < 0 ? `${what} was due yesterday.` : `${what} is due in ${n} days.`,
    what: (kind: string, name: string) => kind === "loan" ? `The ${name} installment` : kind === "cc" ? `Your ${name} card` : `Your ${name} payment`,
    approx: "approx.",
    recapTitle: (m: string) => `Valiu · Your ${m} recap`,
    recapBody: (m: string) => `Your ${m} recap is ready: what you spent, saved and paid off. Open Valiu to see it; the new month starts from zero.`,
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
  const out: { title: string; body: string; tag: string; url: string }[] = [];
  // On the 1st: the previous month's recap is ready (the app builds it and starts the month from zero).
  if (today.endsWith("-01") && ((data?.items ?? []).length || (data?.expenses ?? []).length)) {
    const [y, m] = today.split("-").map(Number);
    const prev = new Date(Date.UTC(y, m - 2, 15));
    const name = new Intl.DateTimeFormat(lang === "en" ? "en-US" : "es-GT", { month: "long", timeZone: "UTC" }).format(prev);
    out.push({ title: L.recapTitle(name), body: L.recapBody(name), tag: `valiu-recap-${prev.toISOString().slice(0, 7)}`, url: "./" });
  }
  // deno-lint-ignore no-explicit-any
  return out.concat((data?.items ?? []).filter((i: any) => i?.stmt?.due && !i.paid && !i.completed && (i.stmt.full ?? 0) > 0)
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
    })))
    .concat(billReminders(data, today, days, lang, L));
}

// Everything else in Payments: fixed bills, loan installments and cards without a statement, by their day of the month.
// Weekly recurring charges and items already paid this month are skipped.
// deno-lint-ignore no-explicit-any
function billReminders(data: any, today: string, days: number[], lang: string, L: any) {
  const [y, m] = today.split("-").map(Number);
  const iso = (yy: number, mm: number, d: number) => {
    const last = new Date(Date.UTC(yy, mm, 0)).getUTCDate();
    return `${yy}-${String(mm).padStart(2, "0")}-${String(Math.min(d, last)).padStart(2, "0")}`;
  };
  // deno-lint-ignore no-explicit-any
  return (data?.items ?? []).filter((i: any) => i && !i.paid && !i.completed && !i.stmt?.due && (i.amount ?? 0) > 0 && i.date >= 1 && i.date <= 31 && i.rec !== "week" && i.rec !== "biweek")
    // deno-lint-ignore no-explicit-any
    .map((i: any) => {
      // This month's day, or next month's when "in 3 days" crosses the end of the month.
      let n = daysBetween(today, iso(y, m, i.date));
      if (n < -1) n = daysBetween(today, iso(m === 12 ? y + 1 : y, m === 12 ? 1 : m + 1, i.date));
      return { i, n };
    })
    // deno-lint-ignore no-explicit-any
    .filter(({ n }: any) => days.includes(n))
    // deno-lint-ignore no-explicit-any
    .map(({ i, n }: any) => {
      const kind = i.type === "debt" ? (i.subtype === "loan" ? "loan" : "cc") : "fixed";
      const amt = money(i.amount, data.currencyCode, lang) + (i.approx ? ` ${L.approx}` : "");
      return { title: L.title, body: `${L.billIn(n, L.what(kind, i.name))} ${amt}${i.approx ? "" : "."}`, tag: `valiu-${i.id}-${today.slice(0, 7)}-${i.date}`, url: "./" };
    });
}

// Only send to the real push services (defense in depth: the database also checks this).
const PUSH_HOST = /^(fcm\.googleapis\.com|android\.googleapis\.com|[a-z0-9.-]+\.push\.apple\.com|updates\.push\.services\.mozilla\.com|[a-z0-9.-]+\.notify\.windows\.com)$/;
function validEndpoint(u: string): boolean {
  try { const x = new URL(u); return x.protocol === "https:" && PUSH_HOST.test(x.hostname); } catch { return false; }
}
function sameSecret(a: string | null, b: string | undefined): boolean {
  if (!a || !b || a.length !== b.length) return false;
  let r = 0;
  for (let k = 0; k < a.length; k++) r |= a.charCodeAt(k) ^ b.charCodeAt(k);
  return r === 0;
}

// ---------- store apps ----------
const b64url = (buf: ArrayBuffer | Uint8Array | string) => {
  const bytes = typeof buf === "string" ? new TextEncoder().encode(buf) : buf instanceof Uint8Array ? buf : new Uint8Array(buf);
  let s = ""; for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};
function pemToDer(pem: string): ArrayBuffer {
  const b = atob(pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, ""));
  return Uint8Array.from(b, (c) => c.charCodeAt(0)).buffer;
}
async function signJWT(header: object, claims: object, pem: string, alg: "RS256" | "ES256"): Promise<string> {
  const key = await crypto.subtle.importKey("pkcs8", pemToDer(pem),
    alg === "RS256" ? { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" } : { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"]);
  const input = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(claims))}`;
  const sig = await crypto.subtle.sign(alg === "RS256" ? { name: "RSASSA-PKCS1-v1_5" } : { name: "ECDSA", hash: "SHA-256" }, key, new TextEncoder().encode(input));
  return `${input}.${b64url(sig)}`;
}
let fcmCache: { token: string; project: string } | null = null;
async function fcmAuth() {
  if (fcmCache) return fcmCache;
  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT"); if (!raw) return null;
  const sa = JSON.parse(raw), now = Math.floor(Date.now() / 1000);
  const jwt = await signJWT({ alg: "RS256", typ: "JWT" }, { iss: sa.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging", aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3300 }, sa.private_key, "RS256");
  const r = await fetch("https://oauth2.googleapis.com/token", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}` });
  const j = await r.json(); if (!j.access_token) throw new Error("fcm auth");
  return (fcmCache = { token: j.access_token, project: sa.project_id });
}
let apnsJwt: string | null = null;
async function apnsAuth() {
  if (apnsJwt) return apnsJwt;
  const p8 = Deno.env.get("APNS_KEY_P8"), kid = Deno.env.get("APNS_KEY_ID"), iss = Deno.env.get("APNS_TEAM_ID");
  if (!p8 || !kid || !iss) return null;
  return (apnsJwt = await signJWT({ alg: "ES256", kid }, { iss, iat: Math.floor(Date.now() / 1000) }, p8, "ES256"));
}
type Msg = { title: string; body: string; tag: string; url: string };
// "gone" means the phone uninstalled the app or the token expired: delete it.
async function sendNative(platform: string, token: string, m: Msg): Promise<"ok" | "gone" | "fail" | "off"> {
  if (platform === "android") {
    const a = await fcmAuth(); if (!a) return "off";
    const r = await fetch(`https://fcm.googleapis.com/v1/projects/${a.project}/messages:send`, {
      method: "POST", headers: { Authorization: `Bearer ${a.token}`, "Content-Type": "application/json" },
      body: JSON.stringify({ message: { token, notification: { title: m.title, body: m.body }, data: { url: m.url },
        android: { priority: "high", ttl: "43200s", notification: { tag: m.tag.slice(0, 64), color: "#1e8150" } } } }),
    });
    if (r.ok) return "ok";
    const t = await r.text();
    return r.status === 404 || /UNREGISTERED|INVALID_ARGUMENT/.test(t) ? "gone" : "fail";
  }
  const jwt = await apnsAuth(); if (!jwt) return "off";
  const host = Deno.env.get("APNS_ENV") === "sandbox" ? "api.sandbox.push.apple.com" : "api.push.apple.com";
  if (!/^[0-9a-f]{64,200}$/i.test(token)) return "gone";
  const r = await fetch(`https://${host}/3/device/${token}`, {
    method: "POST",
    headers: { authorization: `bearer ${jwt}`, "apns-topic": Deno.env.get("APNS_BUNDLE_ID") ?? "com.valiugt.app", "apns-push-type": "alert",
      "apns-priority": "10", "apns-expiration": String(Math.floor(Date.now() / 1000) + 43200), "apns-collapse-id": m.tag.slice(0, 60) },
    body: JSON.stringify({ aps: { alert: { title: m.title, body: m.body }, sound: "default" }, url: m.url }),
  });
  if (r.ok) return "ok";
  const t = await r.text();
  return r.status === 410 || /BadDeviceToken|Unregistered|DeviceTokenNotForTopic/.test(t) ? "gone" : "fail";
}

Deno.serve(async (req) => {
  if (!sameSecret(req.headers.get("x-cron-secret"), Deno.env.get("CRON_SECRET"))) {
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
  // The store apps' table may not exist yet (update 5 not run): then only web reminders go out.
  const { data: natives } = await sb.from("native_push_tokens").select("token, platform, user_id");
  const userIds = [...new Set([...(subs ?? []), ...(natives ?? [])].map((s) => s.user_id))];
  if (!userIds.length) return Response.json({ users: 0, sent: 0 });

  const { data: rows, error: e2 } = await sb.from("app_state").select("user_id, data").in("user_id", userIds);
  if (e2) return Response.json({ error: e2.message }, { status: 500 });

  const today = todayISO();
  let sent = 0, removed = 0, failed = 0;
  const preview: unknown[] = [];
  for (const row of rows ?? []) {
    const msgs = remindersFor(row.data, today);
    if (!msgs.length) continue;
    for (const sub of (subs ?? []).filter((s) => s.user_id === row.user_id && validEndpoint(s.endpoint))) {
      for (const payload of msgs) {
        if (dryRun) { preview.push({ user: row.user_id, ...payload }); continue; }
        try {
          const { endpoint, headers, body } = await buildPushHTTPRequest({
            privateJWK,
            subscription: { endpoint: sub.endpoint, keys: { p256dh: sub.p256dh, auth: sub.auth } },
            message: { payload, adminContact, options: { ttl: 43200, urgency: "high", topic: payload.tag.slice(0, 32).replace(/[^A-Za-z0-9_-]/g, "") } },
          });
          const res = await fetch(endpoint, { method: "POST", headers, body, redirect: "manual" });
          if (res.status === 404 || res.status === 410) {
            await sb.from("push_subscriptions").delete().eq("endpoint", sub.endpoint);
            removed++;
          } else if (res.ok) sent++;
          else failed++;
        } catch (_e) { failed++; }
      }
    }
    for (const n of (natives ?? []).filter((x) => x.user_id === row.user_id)) {
      for (const payload of msgs) {
        if (dryRun) { preview.push({ user: row.user_id, app: n.platform, ...payload }); continue; }
        try {
          const r = await sendNative(n.platform, n.token, payload);
          if (r === "ok") sent++;
          else if (r === "gone") { await sb.from("native_push_tokens").delete().eq("token", n.token); removed++; }
          else if (r === "fail") failed++;
        } catch (_e) { failed++; }
      }
    }
  }
  return Response.json({ date: today, users: (rows ?? []).length, sent, removed, failed, ...(dryRun ? { preview } : {}) });
});
