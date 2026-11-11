// VisualContext — Gemini through the server, for beta testers (Marcus, 09/11): the key never goes to their phone.
// POST/GET /functions/v1/gem/v1beta/<path>  (same path and body as generativelanguage.googleapis.com)
// Auth: x-vc-token (device token). Founder, beta and super testers only; each call counts against the tester's limits.
import { createClient } from "npm:@supabase/supabase-js@2.45.4";

const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "content-type, x-vc-token, authorization, apikey", "Access-Control-Allow-Methods": "GET, POST, OPTIONS" };
const json = (b: unknown, s = 200) => new Response(JSON.stringify(b), { status: s, headers: { ...cors, "Content-Type": "application/json" } });
const sha256 = async (t: string) => [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(t)))].map((b) => b.toString(16).padStart(2, "0")).join("");

// Rough prices (US$ per 1M tokens) to turn usage into money; good enough to stop a budget, not an invoice.
const PRICE = { in: 0.75, out: 3.75 }; // gemini-3.8-flash list price until 31/12/2026 (doubles in 2027); cheaper models cost less, so this errs high
export const TIERS: Record<string, { days: number; calls_day: number; live_min_day: number; budget_usd: number; own_keys: boolean }> = {
  beta: { days: 5, calls_day: 150, live_min_day: 20, budget_usd: 3, own_keys: false },
  super: { days: 30, calls_day: 1000, live_min_day: 90, budget_usd: 25, own_keys: true },
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  const key = Deno.env.get("GEMINI_KEY");
  if (!key) return json({ error: { code: 503, message: "Gemini do app ainda não configurado" } }, 503);
  const token = req.headers.get("x-vc-token") ?? "";
  if (!/^vc_[A-Za-z0-9]{16,128}$/.test(token)) return json({ error: { code: 401, message: "token" } }, 401);
  const { data: dev } = await db.from("devices").select("owner_email, revoked_at").eq("token_hash", await sha256(token)).maybeSingle();
  if (!dev || dev.revoked_at) return json({ error: { code: 401, message: "token" } }, 401);
  const email = String(dev.owner_email).toLowerCase();

  // Who may spend, and how much is left today / in total.
  const { data: acc } = await db.from("accounts").select("plan").eq("owner_email", email).maybeSingle();
  const founder = acc?.plan === "founder" || acc?.plan === "admin"; // admins (10/11) are not limited
  if (!founder) {
    const { data: inv } = await db.from("invites").select("*").eq("email", email).maybeSingle();
    if (!inv || !inv.active) return json({ error: { code: 403, message: "convite inativo" } }, 403);
    const lim = { ...TIERS[inv.tier] ?? TIERS.beta, ...(inv.limits ?? {}) };
    if (inv.accepted_at && Date.now() - new Date(inv.accepted_at).getTime() > lim.days * 86400_000) return json({ error: { code: 429, message: `Seu período de teste (${lim.days} dias) terminou.` } }, 429);
    const { data: rows } = await db.from("usage").select("day, calls, cost_usd").eq("owner_email", email);
    const today = new Date(Date.now() - 3 * 3600_000).toISOString().slice(0, 10);
    const t = (rows ?? []).find((r: any) => r.day === today);
    const spent = (rows ?? []).reduce((a: number, r: any) => a + Number(r.cost_usd), 0);
    if ((t?.calls ?? 0) >= lim.calls_day) return json({ error: { code: 429, message: `Limite de hoje (${lim.calls_day} pedidos) atingido. Amanhã volta.` } }, 429);
    if (spent >= lim.budget_usd) return json({ error: { code: 429, message: "O crédito do seu teste acabou. Fale com o Marcus." } }, 429);
  }

  const url = new URL(req.url);
  const path = url.pathname.replace(/^.*\/gem\//, ""); // v1beta/models/…:generateContent
  if (!path.startsWith("v1beta/models")) return json({ error: { code: 400, message: "path" } }, 400); // models list and generateContent only
  const r = await fetch(`https://generativelanguage.googleapis.com/${path}${url.search}`, {
    method: req.method, headers: { "x-goog-api-key": key, "Content-Type": "application/json" }, body: req.method === "POST" ? await req.text() : undefined,
  });
  const text = await r.text();
  if (req.method === "POST" && r.ok) {
    let tin = 0, tout = 0;
    try { const u = JSON.parse(text).usageMetadata ?? {}; tin = u.promptTokenCount ?? 0; tout = (u.candidatesTokenCount ?? 0) + (u.thoughtsTokenCount ?? 0); } catch { /* not JSON */ }
    await db.rpc("usage_add", { p_email: email, p_calls: 1, p_in: tin, p_out: tout, p_live: 0, p_cost: (tin * PRICE.in + tout * PRICE.out) / 1e6 });
  }
  return new Response(text, { status: r.status, headers: { ...cors, "Content-Type": "application/json" } });
});
