// Valiu · Edge Function "gasto": receives a purchase from the iPhone Shortcuts automation (Wallet → Transaction)
// and drops it in the user's inbox. Deploy with JWT verification OFF: the personal key in ?t= is the auth.
// Secrets used (already set by Supabase): SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY (or SUPABASE_SECRET_KEY).
import { createClient } from "npm:@supabase/supabase-js@2";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "content-type, apikey, authorization, x-valiu-token",
};
const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });

async function sha256Hex(s: string): Promise<string> {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

// "Q45.00", "45,50 Q", "$1,234.56", "1.234,56", 45 → number
export function parseAmount(v: unknown): number {
  if (typeof v === "number") return Math.abs(v);
  let s = String(v ?? "").replace(/[^\d.,-]/g, "");
  if (!s) return NaN;
  const lastDot = s.lastIndexOf("."), lastComma = s.lastIndexOf(",");
  if (lastDot >= 0 && lastComma >= 0) {
    const dec = lastDot > lastComma ? "." : ",";
    s = s.split(dec === "." ? "," : ".").join("").replace(dec, ".");
  } else if (lastComma >= 0) {
    s = /,\d{1,2}$/.test(s) ? s.replace(/\./g, "").replace(",", ".") : s.replace(/,/g, "");
  } else if ((s.match(/\./g) || []).length > 1) {
    s = s.replace(/\./g, "");
  }
  return Math.abs(parseFloat(s));
}
const clean = (v: unknown, max: number) => {
  const s = String(v ?? "").replace(/[\u0000-\u001f<>]/g, "").trim().slice(0, max);
  return s || null;
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json(405, { ok: false, error: "use POST" });

  const url = new URL(req.url);
  const token = url.searchParams.get("t") || req.headers.get("x-valiu-token") || "";
  if (!/^[A-Za-z0-9_-]{30,80}$/.test(token)) return json(401, { ok: false, error: "missing or invalid key" });

  let body: Record<string, unknown> = {};
  try {
    const text = await req.text();
    if (text.length > 4000) return json(413, { ok: false, error: "too large" });
    body = text ? JSON.parse(text) : {};
  } catch {
    return json(400, { ok: false, error: "body must be JSON" });
  }

  const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY")!, {
    auth: { persistSession: false },
  });
  const hash = await sha256Hex(token);
  const { data: owner } = await sb.from("wallet_tokens").select("user_id").eq("token_hash", hash).maybeSingle();
  if (!owner) return json(401, { ok: false, error: "unknown key" });
  await sb.from("wallet_tokens").update({ last_used: new Date().toISOString() }).eq("token_hash", hash);

  if (body.test === true || url.searchParams.get("test") === "1") return json(200, { ok: true, test: true });

  const amount = parseAmount(body.amount);
  if (!(amount > 0 && amount < 10_000_000)) return json(400, { ok: false, error: "invalid amount" });

  const since = new Date(Date.now() - 86_400_000).toISOString();
  const { count } = await sb.from("wallet_inbox").select("id", { count: "exact", head: true }).eq("user_id", owner.user_id).gte("created_at", since);
  if ((count ?? 0) >= 150) return json(429, { ok: false, error: "too many today" });

  const { error } = await sb.from("wallet_inbox").insert({
    user_id: owner.user_id,
    amount: Math.round(amount * 100) / 100,
    merchant: clean(body.merchant, 80),
    card: clean(body.card, 60),
  });
  if (error) return json(500, { ok: false, error: "could not save" });
  return json(200, { ok: true });
});
