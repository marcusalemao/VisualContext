// VisualContext — the one door the phone and the glasses use (replaces Base44's fcProxy).
// Auth: header x-vc-token (device token). Only its SHA-256 is stored, in public.devices.
// Same request shape as fcProxy ({ action, entity, ... }), so the apps switch by changing the address.
import { createClient } from "npm:@supabase/supabase-js@2.45.4";

const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type, x-vc-token, authorization, apikey",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (b: unknown, s = 200) => new Response(JSON.stringify(b), { status: s, headers: { ...cors, "Content-Type": "application/json" } });

async function sha256(t: string) {
  const h = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(t));
  return [...new Uint8Array(h)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

type Who = { owner: string | null; email: string | null };
/** Rows of this device's owner (claimed after first login, or still by e-mail). */
const mine = (q: any, w: Who) => (w.owner ? q.eq("owner", w.owner) : q.is("owner", null).eq("owner_email", w.email));
const stamp = (w: Who) => ({ owner: w.owner, owner_email: w.email });
const vec = (a: unknown, n: number) => {
  const v = typeof a === "string" ? JSON.parse(a) : a;
  return Array.isArray(v) && v.length === n && v.every((x) => typeof x === "number") ? `[${v.join(",")}]` : null;
};

/** People in the shape the apps already read (FaceContextPerson). */
async function listPeople(w: Who, limit = 1000, thumbs = false) {
  const { data: people, error } = await mine(db.from("people").select("*"), w).order("updated_at", { ascending: false }).limit(limit);
  if (error) throw error;
  const ids = (people ?? []).map((p: any) => p.id);
  const { data: faces } = ids.length ? await db.from("faces").select(thumbs ? "person_id, legacy, thumb, last_seen_at" : "person_id, legacy, last_seen_at").in("person_id", ids).order("last_seen_at", { ascending: false }) : { data: [] };
  const legacy = new Map((faces ?? []).filter((f: any) => f.legacy).map((f: any) => [f.person_id, f.legacy]));
  const nFaces = new Map<string, number>();
  const thumb = new Map<string, string>(); // newest face crop per person: the Manager shows it when there is no photo_url
  for (const f of faces ?? []) { nFaces.set((f as any).person_id, (nFaces.get((f as any).person_id) ?? 0) + 1); if ((f as any).thumb && !thumb.has((f as any).person_id)) thumb.set((f as any).person_id, (f as any).thumb); }
  return (people ?? []).map((p: any) => ({
    id: p.id, name: p.name, nickname: p.nickname, company: p.company, role: p.role, relation: p.relation,
    relation_context: p.relation_context, photo_url: p.photo_url, tags: p.tags, notes: p.notes,
    contact_email: p.contact_email, contact_phone: p.contact_phone, contact_whatsapp: p.contact_whatsapp, contact_linkedin: p.contact_linkedin,
    last_seen_date: p.last_seen_at, last_seen_context: p.last_seen_context, status: p.status, source: p.source,
    face_embedding: legacy.get(p.id) ?? null, faces: nFaces.get(p.id) ?? 0, thumb: thumb.get(p.id) ?? null, created_at: p.created_at,
  }));
}

// ---- Beta testers (Marcus, 09/11): only invited e-mails get in; the founder's keys work for them through the server,
// within limits per tier that he can change per person in the Manager.
const FOUNDER = "marcusdemagalhaes@gmail.com";
const TIERS: Record<string, { days: number; calls_day: number; live_min_day: number; budget_usd: number; own_keys: boolean }> = {
  beta: { days: 5, calls_day: 150, live_min_day: 20, budget_usd: 3, own_keys: false },
  super: { days: 30, calls_day: 1000, live_min_day: 90, budget_usd: 25, own_keys: true },
};
const LIVE_USD_PER_MIN = 0.03; // only when a session reports no tokens (old app versions)
// Live API list prices, US$ per 1M tokens (ai.google.dev/gemini-api/docs/pricing, 10/11/2026: gemini-3.8-live and 3.1-flash-live).
const LIVE_PRICE = { in: { TEXT: 0.75, IMAGE: 1.0, VIDEO: 1.0, AUDIO: 3.0 } as Record<string, number>, out: { TEXT: 4.5, AUDIO: 12.0 } as Record<string, number> };
function liveCost(u: any) {
  let c = 0;
  for (const [k, v] of Object.entries(u?.in ?? {})) c += Number(v) * (LIVE_PRICE.in[k] ?? 3.0) / 1e6;
  for (const [k, v] of Object.entries(u?.out ?? {})) c += Number(v) * (LIVE_PRICE.out[k] ?? 12.0) / 1e6;
  return c;
}
const today = () => new Date(Date.now() - 3 * 3600_000).toISOString().slice(0, 10);
// Admins (Marcus, 10/11): people he names to run the testers dashboard with him. No limits, no tester data of their own.
const isAdmin = async (email: string | null) => { if (!email) return false; if (email === FOUNDER) return true; const { data } = await db.from("accounts").select("plan").eq("owner_email", email).maybeSingle(); return data?.plan === "founder" || data?.plan === "admin"; };
async function roleOf(email: string | null) {
  if (!email) return null;
  const { data: acc } = await db.from("accounts").select("plan").eq("owner_email", email).maybeSingle();
  if (acc?.plan === "founder") return { role: "founder" as const };
  if (acc?.plan === "admin") return { role: "admin" as const };
  const { data: inv } = await db.from("invites").select("*").eq("email", email).maybeSingle();
  if (!inv || !inv.active) return null;
  const limits = { ...(TIERS[inv.tier] ?? TIERS.beta), ...(inv.limits ?? {}) };
  const { data: rows } = await db.from("usage").select("day, calls, live_seconds, cost_usd").eq("owner_email", email);
  const t = (rows ?? []).find((r: any) => r.day === today()) ?? { calls: 0, live_seconds: 0, cost_usd: 0 };
  const spent = (rows ?? []).reduce((a: number, r: any) => a + Number(r.cost_usd), 0);
  const daysLeft = inv.accepted_at ? Math.max(0, Math.ceil(limits.days - (Date.now() - new Date(inv.accepted_at).getTime()) / 86400_000)) : limits.days;
  return { role: inv.tier as "beta" | "super", limits, today: t, spent: Math.round(spent * 100) / 100, days_left: daysLeft, accepted_at: inv.accepted_at };
}

// Invite codes (Marcus, 09/11): approval gives a code that goes with the download link; typed in the app or the
// Manager it signs in without waiting for an e-mail. 14 days, up to 3 devices (phone, browser, a reinstall).
const DOWNLOAD = "https://vc.alema.io/test-download/";
const MANAGER = "https://web-visualcontext-site.visualcontext.pages.dev/manager";
const CODE_DAYS = 14, CODE_USES = 3;
const normCode = (c: string) => { const n = String(c ?? "").toUpperCase().replace(/[^A-Z0-9]/g, ""); return n.length === 10 && n.startsWith("VC") ? n.slice(2) : n; };
async function newCode(email: string) {
  const abc = "23456789ABCDEFGHJKMNPQRSTUVWXYZ", r = new Uint8Array(8); crypto.getRandomValues(r);
  const raw = [...r].map((x) => abc[x % abc.length]).join("");
  await db.from("invites").update({ invite_code: raw, code_expires: new Date(Date.now() + CODE_DAYS * 86400_000).toISOString(), code_uses: 0 }).eq("email", email);
  return "VC-" + raw.slice(0, 4) + "-" + raw.slice(4);
}
const showCode = (raw: string | null) => raw ? "VC-" + raw.slice(0, 4) + "-" + raw.slice(4) : null;
function inviteMail(name: string | null, email: string, code: string, lang = "pt") {
  const first = (name ?? "").trim().split(/\s+/)[0];
  if (lang === "en") return { subject: `Your VisualContext invite code: ${code}`, html: inviteHtml(name, code, "en"), text: `Hi${first ? " " + first : ""}!\n\nYou're in the VisualContext beta.\n\n1. Install the app on your Android phone: ${DOWNLOAD}\n2. Open it and tap "Tenho um código de convite" (I have an invite code). Your code: ${code}\n3. The Manager, to see everything in your browser (same code): ${MANAGER}\n\nThe code works for ${CODE_DAYS} days. The test plan is on the download page. The green bubble in the app sends feedback: tell me everything, good and bad.\n\nThanks!\nMarcus` };
  return { subject: `Seu código de convite do VisualContext: ${code}`, html: inviteHtml(name, code, "pt"), text: `Oi${first ? ", " + first : ""}!\n\nVocê entrou no beta do VisualContext.\n\n1. Instale o app no celular Android: ${DOWNLOAD}\n2. Abra e toque em "Tenho um código de convite". Seu código: ${code}\n3. O Manager, para ver tudo no navegador (mesmo código): ${MANAGER}\n\nO código vale ${CODE_DAYS} dias. O roteiro do teste está na página de download. O balão verde no app manda feedback: conte tudo, bom e ruim.\n\nObrigado!\nMarcus` };
}
// The invite as an e-mail page (Marcus, 10/11): big code in its own box (selected with one tap where the client allows),
// a download button, and the code in the subject too, so Gmail can offer "Copiar código". Plain text stays as fallback.
const esc = (s: string) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" } as any)[c]);
function inviteHtml(name: string | null, code: string, lang = "pt") {
  const en = lang === "en", first = esc((name ?? "").trim().split(/\s+/)[0]);
  const t = en
    ? { hi: `Hi${first ? " " + first : ""}!`, in: "You're in the VisualContext beta.", code: "Your invite code", tap: "Tap the code to select it, then copy.", s1: "Install the app on your Android phone", btn: "Download the app", s2: 'Open it and tap <b>"Tenho um código de convite"</b> (I have an invite code), then type the code above.', s3: "To see everything in your browser, open the Manager and use the same code:", valid: `The code works for ${CODE_DAYS} days, on up to ${CODE_USES} devices. The test plan is on the download page. The green bubble in the app sends feedback: tell me everything, good and bad.`, bye: "Thanks!" }
    : { hi: `Oi${first ? ", " + first : ""}!`, in: "Você entrou no beta do VisualContext.", code: "Seu código de convite", tap: "Toque no código para selecionar e copiar.", s1: "Instale o app no seu celular Android", btn: "Baixar o app", s2: 'Abra e toque em <b>"Tenho um código de convite"</b>, depois digite o código acima.', s3: "Para ver tudo no navegador, abra o Manager e use o mesmo código:", valid: `O código vale ${CODE_DAYS} dias, em até ${CODE_USES} aparelhos. O roteiro do teste está na página de download. O balão verde no app manda feedback: conte tudo, bom e ruim.`, bye: "Obrigado!" };
  return `<!doctype html><html><body style="margin:0;background:#07090c;padding:24px 12px;font-family:Arial,Helvetica,sans-serif;color:#e8f0ec">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center">
<table role="presentation" width="100%" style="max-width:520px;background:#0d1217;border:1px solid #1f2a24;border-radius:16px" cellpadding="0" cellspacing="0"><tr><td style="padding:28px 24px">
<p style="margin:0 0 4px;font-size:12px;letter-spacing:2px;color:#00ff88;font-weight:bold">VISUALCONTEXT · BETA</p>
<h1 style="margin:0 0 6px;font-size:24px;color:#ffffff">${t.hi}</h1>
<p style="margin:0 0 22px;font-size:16px;color:#8fdfb5">${t.in}</p>
<p style="margin:0 0 8px;font-size:13px;color:#9aaaa2">${t.code}</p>
<div style="background:#07090c;border:2px solid #00ff88;border-radius:12px;padding:16px;text-align:center">
<span style="font-family:'Courier New',Courier,monospace;font-size:30px;font-weight:bold;letter-spacing:4px;color:#00ff88;-webkit-user-select:all;user-select:all">${esc(code)}</span></div>
<p style="margin:8px 0 24px;font-size:12px;color:#9aaaa2">${t.tap}</p>
<p style="margin:0 0 10px;font-size:15px"><b style="color:#00ff88">1.</b> ${t.s1}</p>
<a href="${DOWNLOAD}" style="display:inline-block;background:#00ff88;color:#07090c;text-decoration:none;font-weight:bold;font-size:16px;padding:14px 26px;border-radius:12px;margin:0 0 20px">${t.btn}</a>
<p style="margin:0 0 18px;font-size:15px"><b style="color:#00ff88">2.</b> ${t.s2}</p>
<p style="margin:0 0 6px;font-size:15px"><b style="color:#00ff88">3.</b> ${t.s3}</p>
<p style="margin:0 0 22px;font-size:14px"><a href="${MANAGER}" style="color:#00ff88">${MANAGER.replace("https://", "")}</a></p>
<p style="margin:0 0 18px;font-size:13px;color:#9aaaa2;line-height:1.5">${t.valid}</p>
<p style="margin:0;font-size:15px">${t.bye}<br>Marcus · <a href="https://alema.io" style="color:#00ff88">alema.io</a></p>
</td></tr></table></td></tr></table></body></html>`;
}
async function sendMail(to: string, m: { subject: string; text: string; html?: string }) {
  const rk = Deno.env.get("RESEND_API_KEY"); if (!rk) return false;
  const r = await fetch("https://api.resend.com/emails", { method: "POST", headers: { Authorization: `Bearer ${rk}`, "Content-Type": "application/json" },
    body: JSON.stringify({ from: Deno.env.get("INVITE_FROM") ?? Deno.env.get("FEEDBACK_FROM") ?? "VisualContext <convite@alema.io>", to: [to], reply_to: Deno.env.get("FEEDBACK_TO") ?? undefined, ...m }) }).catch(() => null);
  return !!r?.ok;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "use POST" }, 405);
  // Login (07/11): the user signs in with an e-mail code (Supabase Auth); this turns that session into a
  // device code for this phone or browser, so nobody ever types a "vc_" code.
  const bearer = (req.headers.get("authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (bearer && !req.headers.get("x-vc-token")) {
    let b: any = {};
    try { b = await req.json(); } catch { /* empty */ }
    if (b.action !== "register_device") return json({ error: "action" }, 400);
    const { data: u, error: ue } = await db.auth.getUser(bearer);
    if (ue || !u?.user?.email) return json({ error: "login" }, 401);
    const raw = new Uint8Array(30); crypto.getRandomValues(raw);
    const abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    const tok = "vc_" + [...raw].map((x) => abc[x % abc.length]).join("");
    const email = u.user.email.toLowerCase();
    const who = await roleOf(email);
    if (!who && email !== FOUNDER) return json({ error: "convite", message: "O VisualContext está em teste fechado: só entra quem foi convidado." }, 403);
    if (who && who.role !== "founder" && who.role !== "admin") {
      await db.from("invites").update({ accepted_at: new Date().toISOString() }).eq("email", email).is("accepted_at", null);
    }
    const { error: de } = await db.from("devices").insert({ owner: u.user.id, owner_email: email, name: String(b.name ?? "aparelho").slice(0, 60), token_hash: await sha256(tok) });
    if (de) return json({ error: String(de.message ?? de) }, 500);
    // Rows saved before this login (by e-mail) become this account's.
    for (const t of ["people", "faces", "encounters", "things", "notes", "skills"]) await db.from(t).update({ owner: u.user.id }).is("owner", null).eq("owner_email", email);
    await db.from("devices").update({ owner: u.user.id }).is("owner", null).eq("owner_email", email);
    const { data: acc } = await db.from("accounts").select("owner_email").eq("owner_email", email).maybeSingle();
    if (!acc) await db.from("accounts").insert({ owner_email: email, owner: u.user.id, ...(who && who.role !== "founder" && who.role !== "admin" ? { plan: who.role } : {}) }); else await db.from("accounts").update({ owner: u.user.id }).eq("owner_email", email);
    return json({ token: tok, email });
  }
  // Public door (Marcus, 09/11): the /convite page asks to join the beta. No token; the request waits for his approval.
  if (!req.headers.get("x-vc-token") && !bearer) {
    let b: any = {};
    try { b = await req.json(); } catch { /* empty */ }
    if (b.action === "redeem_code") {
      const raw = normCode(b.code);
      if (raw.length !== 8) return json({ error: "Confira o código: VC-XXXX-XXXX." }, 400);
      const { data: inv } = await db.from("invites").select("*").eq("invite_code", raw).maybeSingle();
      if (!inv || !inv.active) return json({ error: "Código inválido." }, 403);
      if (inv.code_expires && new Date(inv.code_expires).getTime() < Date.now()) return json({ error: "Código vencido. Peça outro ao Marcus." }, 403);
      if ((inv.code_uses ?? 0) >= CODE_USES) return json({ error: `Este código já foi usado em ${CODE_USES} aparelhos. Peça outro ao Marcus.` }, 403);
      const email = String(inv.email).toLowerCase();
      const r = new Uint8Array(30); crypto.getRandomValues(r);
      const abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
      const tok = "vc_" + [...r].map((x) => abc[x % abc.length]).join("");
      // Same account as an e-mail login, when there was one (rows are read by owner then).
      const { data: acc } = await db.from("accounts").select("owner").eq("owner_email", email).maybeSingle();
      const owner = acc?.owner ?? null;
      const { error: de } = await db.from("devices").insert({ owner, owner_email: email, name: String(b.name ?? "aparelho").slice(0, 60) + " (convite)", token_hash: await sha256(tok) });
      if (de) return json({ error: "Não deu para entrar agora." }, 500);
      await db.from("invites").update({ code_uses: (inv.code_uses ?? 0) + 1, ...(inv.accepted_at ? {} : { accepted_at: new Date().toISOString() }) }).eq("email", email);
      if (!acc) await db.from("accounts").insert({ owner_email: email, plan: inv.tier });
      return json({ token: tok, email });
    }
    if (b.action !== "beta_request") return json({ error: "token" }, 401);
    if (b.site) return json({ ok: true }); // the hidden field only robots fill
    const name = String(b.name ?? "").trim().slice(0, 120), email = String(b.email ?? "").trim().toLowerCase().slice(0, 200);
    if (name.length < 2 || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return json({ error: "Escreva seu nome e um e-mail válido." }, 400);
    const { data: had } = await db.from("beta_requests").select("status").eq("email", email).maybeSingle();
    if (had) return json({ ok: true, again: true, status: had.status });
    const rec = { name, email, phone: String(b.phone ?? "").slice(0, 80) || null, glasses: String(b.glasses ?? "").slice(0, 80) || null, why: String(b.why ?? "").slice(0, 1000) || null, lang: String(b.lang ?? "pt").slice(0, 5) };
    const { error } = await db.from("beta_requests").insert(rec);
    if (error) return json({ error: "Não deu para enviar agora. Tente de novo em instantes." }, 500);
    const rk = Deno.env.get("RESEND_API_KEY"), to = Deno.env.get("FEEDBACK_TO");
    if (rk && to) await fetch("https://api.resend.com/emails", { method: "POST", headers: { Authorization: `Bearer ${rk}`, "Content-Type": "application/json" },
      body: JSON.stringify({ from: Deno.env.get("FEEDBACK_FROM") ?? "VisualContext <feedback@alema.io>", to: [to], reply_to: email,
        subject: `[VisualContext] Pedido de beta: ${name}`, text: `${name} <${email}>\nCelular: ${rec.phone ?? "-"}\nÓculos: ${rec.glasses ?? "-"}\n\n${rec.why ?? ""}\n\nAprovar: Manager → Ajustes → Testadores` }) }).catch(() => {});
    return json({ ok: true });
  }
  const token = req.headers.get("x-vc-token") ?? "";
  if (!/^vc_[A-Za-z0-9]{16,128}$/.test(token)) return json({ error: "token" }, 401);
  const { data: dev } = await db.from("devices").select("id, owner, owner_email, revoked_at").eq("token_hash", await sha256(token)).maybeSingle();
  if (!dev || dev.revoked_at) return json({ error: "token" }, 401);
  await db.from("devices").update({ last_used_at: new Date().toISOString() }).eq("id", dev.id);
  const w: Who = { owner: dev.owner, email: dev.owner_email };

  let body: any;
  try { body = await req.json(); } catch { return json({ error: "json" }, 400); }
  try {
    switch (body.action) {
      case "list": {
        if (!body.entity || /person/i.test(body.entity)) return json(await listPeople(w, Math.min(body.limit ?? 1000, 1000), !!body.thumbs));
        const table = /encounter/i.test(body.entity) ? "encounters" : /thing|episodic/i.test(body.entity) ? "things" : /note/i.test(body.entity) ? "notes" : null;
        if (!table) return json({ error: "entity" }, 400);
        const col = table === "encounters" ? "at" : table === "things" ? "seen_at" : "at";
        const { data, error } = await mine(db.from(table).select("*"), w).order(col, { ascending: false }).limit(Math.min(body.limit ?? 500, 2000));
        if (error) throw error;
        return json(data);
      }
      case "faces": { // ArcFace signatures, so a new phone or the glasses can recognise without re-teaching
        const { data, error } = await mine(db.from("faces").select("id, person_id, arc, description, last_seen_at, prev_seen_at, people(name)"), w).not("arc", "is", null);
        if (error) throw error;
        return json(data);
      }
      case "enroll_face": {
        const arc = vec(body.arc, 512);
        if (!arc) return json({ error: "arc must be 512 numbers" }, 400);
        let personId: string | null = body.person_id ?? null;
        if (!personId && body.name) {
          const { data: hit } = await mine(db.from("people").select("id").ilike("name", body.name), w).limit(1);
          personId = hit?.[0]?.id ?? null;
          if (!personId) {
            const { data: p, error } = await db.from("people").insert({ ...stamp(w), name: body.name, source: "glasses" }).select("id").single();
            if (error) throw error;
            personId = p.id;
          }
        }
        const { data: f, error } = await db.from("faces").insert({ ...stamp(w), person_id: personId, arc, thumb: body.thumb ?? null, description: body.description ?? null, source: body.source ?? "glasses", last_seen_at: new Date().toISOString() }).select("id").single();
        if (error) throw error;
        return json({ face_id: f.id, person_id: personId });
      }
      case "seen": {
        if (!body.person_id) return json({ error: "person_id" }, 400);
        await mine(db.from("people").update({ last_seen_at: body.when ?? new Date().toISOString(), last_seen_context: body.context ?? null }), w).eq("id", body.person_id);
        return json({ ok: true });
      }
      case "note": {
        if (!body.text) return json({ error: "text" }, 400);
        const { error } = await db.from("notes").insert({ ...stamp(w), text: String(body.text).slice(0, 20000), source: body.source ?? "voice",
          kind: body.kind ?? null, title: body.title ?? null, fields: body.fields ?? null, sensitive: !!body.sensitive, place: body.place ?? null });
        if (error) throw error;
        return json({ ok: true });
      }
      case "feedback": { // a message to the maker, from the Manager's "Meu uso" page (Marcus, 09/11): no e-mail client opens
        const text = String(body.text ?? "").trim();
        if (text.length < 2) return json({ error: "text" }, 400);
        // The moment of the feedback (Marcus, 10/11): where the person was and up to 3 frames of the screen / lens.
        const shots = (Array.isArray(body.shots) ? body.shots : []).filter((x: unknown) => typeof x === "string" && /^data:image\/(jpeg|png|webp);base64,/.test(x) && (x as string).length < 400_000).slice(0, 3);
        const { error } = await db.from("feedback").insert({ owner: w.owner, owner_email: w.email, text: text.slice(0, 5000), app: String(body.app ?? "").slice(0, 40), usage: body.usage ?? null,
          context: body.context && typeof body.context === "object" ? body.context : null, shots: shots.length ? shots : null });
        if (error) throw error;
        // Also to Marcus's work e-mail (09/11: "quem vai receber sou eu, no meu e-mail profissional"), once Resend is set.
        const rk = Deno.env.get("RESEND_API_KEY"), to = Deno.env.get("FEEDBACK_TO");
        if (rk && to) {
          const where = body.usage?.where ? String(body.usage.where) : "";
          await fetch("https://api.resend.com/emails", { method: "POST", headers: { Authorization: `Bearer ${rk}`, "Content-Type": "application/json" },
            body: JSON.stringify({ from: Deno.env.get("FEEDBACK_FROM") ?? "VisualContext <feedback@alema.io>", to: [to], reply_to: w.email ?? undefined,
              subject: `[VisualContext] ${text.slice(0, 70)}`, text: `${text}\n\nDe: ${w.email}\nApp: ${body.app ?? ""}\nOnde: ${where}` }) }).catch(() => {});
        }
        return json({ ok: true });
      }
      case "feedback_list": { // only the maker reads them
        if (!(await isAdmin(w.email))) return json({ error: "forbidden" }, 403);
        const { data, error } = await db.from("feedback").select("id, owner_email, text, app, usage, context, shots, at").order("at", { ascending: false }).limit(body.limit ? Math.min(Number(body.limit), 200) : 100);
        if (error) throw error;
        return json(data ?? []);
      }
      case "identify_music": { // ACRCloud on the server (Marcus, 09/11): nobody has to make an ACRCloud account; the keys live here
        const host = Deno.env.get("ACR_HOST"), key = Deno.env.get("ACR_KEY"), secret = Deno.env.get("ACR_SECRET");
        if (!host || !key || !secret) return json({ error: "acr not configured" }, 503);
        const b64 = String(body.audio ?? "");
        if (b64.length < 1000 || b64.length > 2_000_000) return json({ error: "audio" }, 400);
        const sample = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
        const ts = Math.floor(Date.now() / 1000).toString();
        const k = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-1" }, false, ["sign"]);
        const sig = btoa(String.fromCharCode(...new Uint8Array(await crypto.subtle.sign("HMAC", k, new TextEncoder().encode(`POST\n/v1/identify\n${key}\naudio\n1\n${ts}`)))));
        const f = new FormData();
        f.append("access_key", key); f.append("data_type", "audio"); f.append("signature_version", "1"); f.append("signature", sig); f.append("timestamp", ts);
        f.append("sample_bytes", String(sample.length)); f.append("sample", new Blob([sample], { type: "audio/wav" }), "a.wav");
        const r = await fetch(`https://${host}/v1/identify`, { method: "POST", body: f });
        const j = await r.json().catch(() => ({}));
        return json(j); // the phone reads status.code and metadata.music[0], as if it had asked ACRCloud itself
      }
      case "live_token": { // a one-use Gemini Live token for a tester (the key stays here)
        const who = await roleOf(w.email); const key = Deno.env.get("GEMINI_KEY");
        if (!who) return json({ error: "convite" }, 403);
        if (!key) return json({ error: "Gemini do app ainda não configurado" }, 503);
        if (who.role !== "founder" && who.role !== "admin") {
          const l: any = who.limits, t: any = who.today;
          if (who.days_left <= 0) return json({ error: `Seu período de teste (${l.days} dias) terminou.` }, 429);
          if (t.live_seconds >= l.live_min_day * 60) return json({ error: `Você já usou os ${l.live_min_day} min de assistente de hoje.` }, 429);
          if (who.spent >= l.budget_usd) return json({ error: "O crédito do seu teste acabou. Fale com o Marcus." }, 429);
        }
        const model = String(body.model ?? Deno.env.get("LIVE_MODEL") ?? "models/gemini-3.8-live");
        const now = Date.now();
        const r = await fetch("https://generativelanguage.googleapis.com/v1beta/auth_tokens", { method: "POST", headers: { "x-goog-api-key": key, "Content-Type": "application/json" },
          body: JSON.stringify({ uses: 1, expireTime: new Date(now + 30 * 60_000).toISOString(), newSessionExpireTime: new Date(now + 2 * 60_000).toISOString() }) });
        const j = await r.json().catch(() => ({}));
        if (!r.ok || !j.name) return json({ error: "não consegui abrir o assistente", detail: j?.error?.message ?? r.status }, 502);
        return json({ token: j.name, model, minutes_left: who.role === "founder" || who.role === "admin" ? null : Math.max(0, Math.floor(((who.limits as any).live_min_day * 60 - (who.today as any).live_seconds) / 60)) });
      }
      case "live_used": { // the app reports how long the assistant was open and the tokens Google counted (10/11)
        const sec = Math.max(0, Math.min(4 * 3600, Math.round(Number(body.seconds) || 0)));
        const u = body.usage && typeof body.usage === "object" ? body.usage : null;
        const measured = !!u && Object.values({ ...(u.in ?? {}), ...(u.out ?? {}) }).some((v) => Number(v) > 0);
        const cost = measured ? liveCost(u) : sec / 60 * LIVE_USD_PER_MIN;
        if (w.email && (sec || measured)) {
          await db.from("live_sessions").insert({ owner_email: w.email, seconds: sec, model: String(body.model ?? "").slice(0, 80) || null, tokens: u, cost_usd: cost, measured });
          const tin = measured ? Object.values(u.in ?? {}).reduce((a: number, v: any) => a + Number(v), 0) : 0, tout = measured ? Object.values(u.out ?? {}).reduce((a: number, v: any) => a + Number(v), 0) : 0;
          await db.rpc("usage_add", { p_email: w.email, p_calls: 0, p_in: tin, p_out: tout, p_live: sec, p_cost: cost });
        }
        return json({ ok: true, cost_usd: Math.round(cost * 10000) / 10000, measured });
      }
      case "identify_audd": { // AudD with the app's key (testers have none)
        const k = Deno.env.get("AUDD_KEY"); if (!k) return json({ error: "audd not configured" }, 503);
        const b64 = String(body.audio ?? ""); if (b64.length < 1000 || b64.length > 2_000_000) return json({ error: "audio" }, 400);
        const f = new FormData(); f.append("api_token", k); f.append("return", "spotify,apple_music");
        f.append("file", new Blob([Uint8Array.from(atob(b64), (c) => c.charCodeAt(0))], { type: "audio/wav" }), "a.wav");
        const r = await fetch("https://api.audd.io/", { method: "POST", body: f });
        return json(await r.json().catch(() => ({ status: "error" })));
      }
      // ---- Admin (founder only): invitations and limits per person
      case "invite_list": {
        if (!(await isAdmin(w.email))) return json({ error: "forbidden" }, 403);
        const { data: inv } = await db.from("invites").select("*").order("invited_at", { ascending: false });
        const { data: use } = await db.from("usage").select("owner_email, day, calls, live_seconds, cost_usd");
        const out = (inv ?? []).map((i: any) => {
          const u = (use ?? []).filter((x: any) => x.owner_email === i.email);
          return { ...i, invite_code: showCode(i.invite_code), defaults: TIERS[i.tier] ?? TIERS.beta, spent: Math.round(u.reduce((a: number, x: any) => a + Number(x.cost_usd), 0) * 100) / 100,
            calls: u.reduce((a: number, x: any) => a + x.calls, 0), live_min: Math.round(u.reduce((a: number, x: any) => a + x.live_seconds, 0) / 60), last_day: u.map((x: any) => x.day).sort().pop() ?? null };
        });
        const { data: reqs } = await db.from("beta_requests").select("*").eq("status", "pending").order("created_at", { ascending: true });
        return json({ invites: out, tiers: TIERS, requests: reqs ?? [] });
      }
      case "invite_save": {
        if (!(await isAdmin(w.email))) return json({ error: "forbidden" }, 403);
        const email = String(body.email ?? "").trim().toLowerCase();
        if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return json({ error: "email" }, 400);
        const tier = body.tier === "super" ? "super" : "beta";
        const limits: Record<string, number> = {};
        for (const k of ["days", "calls_day", "live_min_day", "budget_usd"]) if (body.limits && body.limits[k] !== undefined && body.limits[k] !== "" && body.limits[k] !== null) limits[k] = Number(body.limits[k]);
        const { error } = await db.from("invites").upsert({ email, tier, limits, note: body.note ?? null, active: body.active !== false }, { onConflict: "email" });
        if (error) throw error;
        const { data: cur } = await db.from("invites").select("invite_code, code_expires").eq("email", email).maybeSingle();
        const code = cur?.invite_code && new Date(cur.code_expires).getTime() > Date.now() ? showCode(cur.invite_code) : await newCode(email);
        return json({ ok: true, email, code });
      }
      case "request_decide": { // approve as beta / super, or decline, a request from /convite
        if (!(await isAdmin(w.email))) return json({ error: "forbidden" }, 403);
        const email = String(body.email ?? "").toLowerCase();
        const { data: r } = await db.from("beta_requests").select("*").eq("email", email).maybeSingle();
        if (!r) return json({ error: "not found" }, 404);
        if (body.decision === "beta" || body.decision === "super") {
          const { error } = await db.from("invites").upsert({ email, tier: body.decision, limits: {}, note: `pedido pelo site: ${r.name}${r.glasses ? " · " + r.glasses : ""}`, active: true }, { onConflict: "email" });
          if (error) throw error;
          await db.from("beta_requests").update({ status: "approved", tier: body.decision, decided_at: new Date().toISOString() }).eq("email", email);
          const code = await newCode(email);
          const mailed = await sendMail(email, inviteMail(r.name, email, code, r.lang));
          if (mailed) await db.from("invites").update({ notified_at: new Date().toISOString(), notified_how: "email" }).eq("email", email);
          return json({ ok: true, name: r.name, code, mailed });
        }
        // Back to standby, or declined: no access (an approved person loses it at once).
        const status = body.decision === "pending" ? "pending" : "declined";
        await db.from("beta_requests").update({ status, tier: null, decided_at: status === "pending" ? null : new Date().toISOString() }).eq("email", email);
        await db.from("invites").update({ active: false, invite_code: null }).eq("email", email);
        await db.from("devices").update({ revoked_at: new Date().toISOString() }).eq("owner_email", email).is("revoked_at", null);
        return json({ ok: true, name: r.name, status });
      }
      case "admin_board": { // the testers dashboard (Marcus, 09/11): every request and invite, with its status
        if (!(await isAdmin(w.email))) return json({ error: "forbidden" }, 403);
        const [{ data: reqs }, { data: inv }, { data: use }, { data: devs }] = await Promise.all([
          db.from("beta_requests").select("*").order("created_at", { ascending: false }),
          db.from("invites").select("*"),
          db.from("usage").select("owner_email, day, calls, live_seconds, cost_usd"),
          db.from("devices").select("owner_email, name, last_used_at, revoked_at"),
        ]);
        const byInv = new Map((inv ?? []).map((i: any) => [i.email, i]));
        const emails = [...new Set([...(reqs ?? []).map((r: any) => r.email), ...(inv ?? []).map((i: any) => i.email)])];
        const rows = emails.filter((e) => e !== FOUNDER).map((e) => {
          const r: any = (reqs ?? []).find((x: any) => x.email === e) ?? null, i: any = byInv.get(e) ?? null;
          const u = (use ?? []).filter((x: any) => x.owner_email === e), d = (devs ?? []).filter((x: any) => x.owner_email === e && !x.revoked_at);
          const status = i?.active ? "approved" : r?.status === "declined" ? "declined" : r ? (r.status === "approved" ? "off" : "pending") : "off";
          return { email: e, name: r?.name ?? null, phone: r?.phone ?? null, glasses: r?.glasses ?? null, lang: r?.lang ?? "pt", asked_at: r?.created_at ?? null, from_site: !!r,
            status, tier: i?.tier ?? r?.tier ?? null, limits: { ...(TIERS[i?.tier ?? "beta"] ?? TIERS.beta), ...(i?.limits ?? {}) }, custom: i?.limits ?? {}, note: i?.note ?? null,
            code: i?.active ? showCode(i.invite_code) : null, code_expires: i?.code_expires ?? null, code_uses: i?.code_uses ?? 0, accepted_at: i?.accepted_at ?? null,
            notified_at: i?.notified_at ?? null, notified_how: i?.notified_how ?? null, decided_at: r?.decided_at ?? null,
            spent: Math.round(u.reduce((a: number, x: any) => a + Number(x.cost_usd), 0) * 100) / 100, calls: u.reduce((a: number, x: any) => a + x.calls, 0),
            live_min: Math.round(u.reduce((a: number, x: any) => a + x.live_seconds, 0) / 60), last_day: u.map((x: any) => x.day).sort().pop() ?? null,
            devices: d.map((x: any) => ({ name: x.name, last_used_at: x.last_used_at })) };
        });
        // What the voice assistant really costs (measured sessions only), for everyone and for the founder alone.
        const { data: ls } = await db.from("live_sessions").select("owner_email, seconds, cost_usd, measured, tokens, at").eq("measured", true).order("at", { ascending: false }).limit(2000);
        const sum = (xs: any[]) => { const sec = xs.reduce((a, x) => a + x.seconds, 0), usd = xs.reduce((a, x) => a + Number(x.cost_usd), 0);
          const tok: Record<string, number> = {}; for (const x of xs) for (const side of ["in", "out"]) for (const [k, v] of Object.entries(x.tokens?.[side] ?? {})) tok[side + ":" + k] = (tok[side + ":" + k] ?? 0) + Number(v);
          return { sessions: xs.length, minutes: Math.round(sec / 6) / 10, usd: Math.round(usd * 1000) / 1000, usd_per_min: sec ? Math.round(usd / (sec / 60) * 1000) / 1000 : null, tokens: tok }; };
        const live = { all: sum(ls ?? []), founder: sum((ls ?? []).filter((x: any) => x.owner_email === FOUNDER)), testers: sum((ls ?? []).filter((x: any) => x.owner_email !== FOUNDER)) };
        for (const r of rows as any[]) { const mine = (ls ?? []).filter((x: any) => x.owner_email === r.email); r.live_real = mine.length ? sum(mine) : null; }
        return json({ rows, tiers: TIERS, mail: !!Deno.env.get("RESEND_API_KEY"), download: DOWNLOAD, live });
      }
      case "admin_list": {
        if (!(await isAdmin(w.email))) return json({ error: "forbidden" }, 403);
        const { data } = await db.from("accounts").select("owner_email, plan").in("plan", ["founder", "admin"]);
        return json({ admins: [{ email: FOUNDER, plan: "founder" }, ...(data ?? []).filter((a: any) => a.owner_email !== FOUNDER).map((a: any) => ({ email: a.owner_email, plan: a.plan }))], me: w.email, founder: w.email === FOUNDER });
      }
      case "admin_set": { // only the founder names or removes admins
        if (w.email !== FOUNDER) return json({ error: "forbidden" }, 403);
        const email = String(body.email ?? "").trim().toLowerCase();
        if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email) || email === FOUNDER) return json({ error: "email" }, 400);
        if (body.on === false) { await db.from("accounts").update({ plan: "trial" }).eq("owner_email", email).eq("plan", "admin"); await db.from("devices").update({ revoked_at: new Date().toISOString() }).eq("owner_email", email).is("revoked_at", null); }
        else await db.from("accounts").upsert({ owner_email: email, plan: "admin" }, { onConflict: "owner_email" });
        return json({ ok: true });
      }
      case "mark_notified": { // the founder copied the answer to send it himself
        if (!(await isAdmin(w.email))) return json({ error: "forbidden" }, 403);
        await db.from("invites").update({ notified_at: new Date().toISOString(), notified_how: String(body.how ?? "copiado").slice(0, 20) }).eq("email", String(body.email ?? "").toLowerCase());
        return json({ ok: true });
      }
      case "invite_code": { // a fresh code for someone already invited (the old one stops working)
        if (!(await isAdmin(w.email))) return json({ error: "forbidden" }, 403);
        const email = String(body.email ?? "").toLowerCase();
        const { data: i } = await db.from("invites").select("email").eq("email", email).maybeSingle();
        if (!i) return json({ error: "not found" }, 404);
        const code = await newCode(email);
        let mailed = false;
        if (body.send) { const { data: r } = await db.from("beta_requests").select("name, lang").eq("email", email).maybeSingle(); mailed = await sendMail(email, inviteMail(r?.name ?? null, email, code, r?.lang)); }
        await db.from("invites").update(mailed ? { notified_at: new Date().toISOString(), notified_how: "email" } : { notified_at: null, notified_how: null }).eq("email", email);
        return json({ ok: true, code, mailed });
      }
      case "invite_remove": {
        if (!(await isAdmin(w.email))) return json({ error: "forbidden" }, 403);
        await db.from("invites").update({ active: false }).eq("email", String(body.email ?? "").toLowerCase());
        await db.from("devices").update({ revoked_at: new Date().toISOString() }).eq("owner_email", String(body.email ?? "").toLowerCase());
        return json({ ok: true });
      }
      case "update_note": { // the state of a commitment or a task (sugerido → na_agenda / ignorado), from the Manager or the glasses
        if (!body.id || typeof body.fields !== "object" || !body.fields) return json({ error: "id/fields" }, 400);
        const { data: cur } = await mine(db.from("notes").select("fields"), w).eq("id", body.id).maybeSingle();
        if (!cur) return json({ error: "not found" }, 404);
        const { error } = await mine(db.from("notes").update({ fields: { ...(cur.fields ?? {}), ...body.fields } }), w).eq("id", body.id);
        if (error) throw error;
        return json({ ok: true });
      }
      case "moment": { // a free conversation with the assistant (Gemini Live), or any moment: text only, never audio
        if (!body.transcript && !body.summary) return json({ error: "transcript" }, 400);
        let personId: string | null = null;
        if (body.person_name) { const { data: hit } = await mine(db.from("people").select("id").ilike("name", body.person_name), w).limit(1); personId = hit?.[0]?.id ?? null; }
        const { data, error } = await db.from("encounters").insert({ ...stamp(w), person_id: personId, person_name: body.person_name ?? null, at: body.at ?? new Date().toISOString(),
          place: body.place ?? null, lat: body.lat ?? null, lng: body.lng ?? null, context: body.context ?? "conversa com o assistente",
          transcript: String(body.transcript ?? "").slice(0, 20000), summary: body.summary ?? null, capture_mode: body.capture_mode ?? "live" }).select("id").single();
        if (error) throw error;
        return json({ id: data.id });
      }
      // ---- Things (Marcus, 07/11): "essas são minhas chaves" → an object with a snapshot and where it was; "onde deixei" reads it back
      case "thing": {
        if (!body.name) return json({ error: "name" }, 400);
        const { data, error } = await db.from("things").insert({ ...stamp(w), name: String(body.name).slice(0, 80), category: body.category ?? null, location_description: body.location_description ?? null,
          lat: body.lat ?? null, lng: body.lng ?? null, snapshot_url: body.snapshot ? "data:image/jpeg;base64," + String(body.snapshot).slice(0, 120_000) : null,
          audio_context: body.audio_context ?? null, seen_at: body.at ?? new Date().toISOString() }).select("id").single();
        if (error) throw error;
        return json({ id: data.id });
      }
      case "find_thing": { // latest sightings of an object by name ("chaves", "óculos de sol")
        const q = String(body.name ?? "").trim(); if (!q) return json({ error: "name" }, 400);
        const { data, error } = await mine(db.from("things").select("id, name, category, location_description, lat, lng, seen_at, audio_context, snapshot_url"), w).ilike("name", `%${q}%`).order("seen_at", { ascending: false }).limit(3);
        if (error) throw error;
        return json(data);
      }
      // ---- My places (Marcus, 08/11): "Casa", "Trabalho", "Pop Vegan" — names the user gave to spots, with a radius.
      // GPS indoors is off by tens of metres (he lives at the back of a building and the phone says the next street).
      case "places": {
        const { data, error } = await mine(db.from("my_places").select("id, name, kind, lat, lng, radius_m, address"), w).order("created_at");
        if (error) throw error;
        return json(data);
      }
      case "save_place": {
        const name = String(body.name ?? "").trim().slice(0, 60);
        if (!name || typeof body.lat !== "number" || typeof body.lng !== "number") return json({ error: "name/lat/lng" }, 400);
        const kind = ["casa", "trabalho", "frequente", "outro"].includes(body.kind) ? body.kind : "outro";
        const rec = { ...stamp(w), name, kind, lat: body.lat, lng: body.lng, radius_m: Math.max(40, Math.min(500, Number(body.radius_m) || 120)), address: body.address ?? null, updated_at: new Date().toISOString() };
        // Same name (or the one home / work) is updated, never duplicated.
        let q = mine(db.from("my_places").select("id"), w);
        q = kind === "casa" || kind === "trabalho" ? q.eq("kind", kind) : q.ilike("name", name);
        const { data: ex } = await q.limit(1);
        const { data, error } = ex?.length ? await db.from("my_places").update(rec).eq("id", ex[0].id).select("id").single() : await db.from("my_places").insert(rec).select("id").single();
        if (error) throw error;
        return json({ id: data.id, name });
      }
      case "delete_place": {
        if (!body.id) return json({ error: "id" }, 400);
        const { data, error } = await mine(db.from("my_places").delete(), w).eq("id", body.id).select("id");
        if (error) throw error;
        return json({ deleted: data?.length ?? 0 });
      }
      case "update_moment": { // the Manager fills a missing summary
        if (!body.id) return json({ error: "id" }, 400);
        const patch: Record<string, unknown> = {};
        if (typeof body.summary === "string") patch.summary = body.summary.slice(0, 300);
        if (typeof body.place === "string") patch.place = body.place.slice(0, 200);
        if (!Object.keys(patch).length) return json({ error: "fields" }, 400);
        const { data, error } = await mine(db.from("encounters").update(patch), w).eq("id", body.id).select("id");
        if (error) throw error;
        return json({ updated: data?.length ?? 0 });
      }
      // ---- Skills (spec 1r): learned recipes, a closed menu of steps, never code ----
      case "skills": {
        const { data, error } = await mine(db.from("skills").select("*"), w).order("updated_at", { ascending: false });
        if (error) throw error;
        return json(data);
      }
      case "save_skill": {
        const k = body.skill ?? {};
        if (!k.slug || !k.name || !Array.isArray(k.steps)) return json({ error: "skill" }, 400);
        const rec = { ...stamp(w), slug: String(k.slug).slice(0, 48), name: String(k.name).slice(0, 80), description: k.description ?? null, triggers: k.triggers ?? [],
          params: k.params ?? {}, steps: k.steps, enabled: k.enabled ?? true, updated_at: new Date().toISOString() };
        const { data: ex } = await mine(db.from("skills").select("id"), w).eq("slug", rec.slug).limit(1);
        const q = ex?.length ? db.from("skills").update(rec).eq("id", ex[0].id) : db.from("skills").insert(rec);
        const { error } = await q;
        if (error) throw error;
        return json({ saved: rec.slug });
      }
      case "delete_skill": {
        if (!body.slug) return json({ error: "slug" }, 400);
        const { data, error } = await mine(db.from("skills").delete(), w).eq("slug", body.slug).select("id");
        if (error) throw error;
        return json({ deleted: data?.length ?? 0 });
      }
      // ---- Manager ----
      case "me": { // who is this, which plan, how much is stored (the Manager's header)
        const email = w.email;
        let { data: acc } = await db.from("accounts").select("*").eq("owner_email", email).maybeSingle();
        if (!acc) ({ data: acc } = await db.from("accounts").insert({ owner_email: email }).select("*").single());
        const count = async (t: string) => (await mine(db.from(t).select("id", { count: "exact", head: true }), w)).count ?? 0;
        const who = await roleOf(email);
        return json({ email, plan: acc?.plan, role: who?.role ?? null, tester: who && who.role !== "founder" && who.role !== "admin" ? who : null, admin: await isAdmin(email), trial_ends_at: acc?.trial_ends_at, me_name: acc?.me_name ?? null, counts: { people: await count("people"), encounters: await count("encounters"), things: await count("things"), notes: await count("notes"), faces: await count("faces") } });
      }
      case "set_me": { // the wearer's own name (Marcus, 07/11): never an encounter, marked "você" everywhere
        const name = String(body.name ?? "").trim().slice(0, 120);
        await db.from("accounts").upsert({ owner_email: w.email, owner: w.owner, me_name: name || null }, { onConflict: "owner_email" });
        return json({ me_name: name || null });
      }
      case "delete_row": { // a thing, a note or a moment, from the Manager (confirmed in the page)
        const table = ({ thing: "things", note: "notes", encounter: "encounters" } as Record<string, string>)[body.kind];
        if (!table || !body.id) return json({ error: "kind/id" }, 400);
        const { data, error } = await mine(db.from(table).delete(), w).eq("id", body.id).select("id");
        if (error) throw error;
        return json({ deleted: data?.length ?? 0 });
      }
      case "update_person": {
        const allowed = ["name", "nickname", "company", "role", "relation", "relation_context", "photo_url", "tags", "contact_whatsapp", "contact_email", "contact_phone", "contact_linkedin", "notes", "status", "last_seen_context"];
        const patch: Record<string, unknown> = {};
        for (const k of allowed) if (body.fields && k in body.fields) patch[k] = body.fields[k];
        if (!body.id || !Object.keys(patch).length) return json({ error: "id/fields" }, 400);
        const { data, error } = await mine(db.from("people").update(patch), w).eq("id", body.id).select("id");
        if (error) throw error;
        return json({ updated: data?.length ?? 0 });
      }
      case "delete_person": {
        if (!body.id) return json({ error: "id" }, 400);
        const { data, error } = await mine(db.from("people").delete(), w).eq("id", body.id).select("id");
        if (error) throw error;
        return json({ deleted: data?.length ?? 0 });
      }
      case "delete_people": { // several at once (Manager select mode); their faces and notes go with them, encounters keep the name
        const ids = Array.isArray(body.ids) ? body.ids.filter((x: unknown) => typeof x === "string").slice(0, 500) : [];
        if (!ids.length) return json({ error: "ids" }, 400);
        const { data, error } = await mine(db.from("people").delete(), w).in("id", ids).select("id");
        if (error) throw error;
        return json({ deleted: data?.length ?? 0 });
      }
      case "merge_people": { // keep ← drop: faces and encounters move, empty fields are filled, tags joined, notes appended
        if (!body.keep || !body.drop || body.keep === body.drop) return json({ error: "keep/drop" }, 400);
        const { data: two } = await mine(db.from("people").select("*"), w).in("id", [body.keep, body.drop]);
        const keep = two?.find((p: any) => p.id === body.keep), drop = two?.find((p: any) => p.id === body.drop);
        if (!keep || !drop) return json({ error: "not found" }, 404);
        const fill: Record<string, unknown> = {};
        for (const k of ["nickname", "company", "role", "relation", "relation_context", "photo_url", "contact_whatsapp", "contact_email", "contact_phone", "contact_linkedin", "last_seen_context"]) if (!keep[k] && drop[k]) fill[k] = drop[k];
        fill.tags = Array.from(new Set([...(keep.tags ?? []), ...(drop.tags ?? [])]));
        if (drop.notes) fill.notes = [keep.notes, drop.notes].filter(Boolean).join("\n");
        if (drop.last_seen_at && (!keep.last_seen_at || drop.last_seen_at > keep.last_seen_at)) { fill.last_seen_at = drop.last_seen_at; fill.last_seen_context = drop.last_seen_context; }
        await db.from("people").update(fill).eq("id", keep.id);
        await db.from("faces").update({ person_id: keep.id }).eq("person_id", drop.id);
        await db.from("encounters").update({ person_id: keep.id, person_name: keep.name }).eq("person_id", drop.id);
        await db.from("people").delete().eq("id", drop.id);
        return json({ merged: true, keep: keep.id });
      }
      case "add_people": { // import from contacts/LinkedIn/CSV: same name = fill empty fields, never duplicate
        const rows = (body.people ?? []) as any[];
        const { data: have } = await mine(db.from("people").select("id, name, company, role, contact_email, contact_phone, contact_linkedin, photo_url"), w);
        const byName = new Map((have ?? []).map((p: any) => [String(p.name).trim().toLowerCase(), p]));
        let added = 0, filled = 0;
        for (const r of rows) {
          const name = String(r?.name ?? "").trim();
          if (!name) continue;
          const fields = { company: r.company || null, role: r.role || null, contact_email: r.contact_email || null, contact_phone: r.contact_phone || null, contact_linkedin: r.contact_linkedin || null, photo_url: r.photo_url || null };
          const ex: any = byName.get(name.toLowerCase());
          if (ex) {
            const fill: Record<string, unknown> = {};
            for (const [k, v] of Object.entries(fields)) if (v && !ex[k]) fill[k] = v;
            if (Object.keys(fill).length) { await db.from("people").update(fill).eq("id", ex.id); filled++; }
          } else {
            const { data: p, error } = await db.from("people").insert({ ...stamp(w), name, ...fields, source: r.source ?? "import", tags: r.tags ?? [] }).select("*").single();
            if (error) throw error;
            byName.set(name.toLowerCase(), p); added++;
          }
        }
        return json({ added, filled });
      }
      case "import_people": { // one-off migration from Base44 (FaceContextPerson records); re-runnable (upsert by legacy_id)
        const rows = (body.people ?? []) as any[];
        let n = 0, faces = 0;
        for (const p of rows) {
          if (!p?.id || !p?.name) continue;
          const rec = {
            ...stamp(w), legacy_id: String(p.id), name: p.name, nickname: p.nickname ?? null, company: p.company ?? null, role: p.role ?? null,
            relation: p.relation ?? null, relation_context: p.relation_context ?? null, photo_url: p.photo_url ?? null, tags: p.tags ?? [],
            contact_whatsapp: p.contact_whatsapp ?? null, contact_email: p.contact_email ?? null, contact_phone: p.contact_phone ?? null, contact_linkedin: p.contact_linkedin ?? null,
            last_seen_at: p.last_seen_date || null, last_seen_context: p.last_seen_context ?? null, notes: p.notes ?? null,
            custom: p.custom_fields ?? {}, status: p.status ?? "active", source: p.source ?? "base44", created_at: p.created_date ?? new Date().toISOString(),
          };
          const { data: up, error } = await db.from("people").upsert(rec, { onConflict: "legacy_id" }).select("id").single();
          if (error) throw error;
          n++;
          const legacy = vec(p.face_embedding, 128);
          if (legacy) {
            const { data: has } = await db.from("faces").select("id").eq("person_id", up.id).not("legacy", "is", null).limit(1);
            if (!has?.length) { await db.from("faces").insert({ ...stamp(w), person_id: up.id, legacy, source: "base44" }); faces++; }
          }
        }
        return json({ people: n, legacy_faces: faces });
      }
      case "import_encounters": {
        const rows = (body.encounters ?? []) as any[];
        const { data: ppl } = await mine(db.from("people").select("id, legacy_id"), w);
        const byLegacy = new Map((ppl ?? []).map((p: any) => [p.legacy_id, p.id]));
        let n = 0;
        for (const e of rows) {
          if (!e?.id) continue;
          const { error } = await db.from("encounters").upsert({
            ...stamp(w), legacy_id: String(e.id), person_id: byLegacy.get(e.person_id) ?? null, person_name: e.person_name ?? null,
            at: e.date ?? e.created_date ?? new Date().toISOString(), place: e.location ?? null, context: e.context ?? null,
            transcript: e.speech_captured ?? null, ocr: e.ocr_captured ?? null, capture_mode: e.capture_mode ?? null, summary: e.notes ?? null,
          }, { onConflict: "legacy_id" });
          if (error) throw error;
          n++;
        }
        return json({ encounters: n });
      }
      case "import_things": {
        const rows = (body.things ?? []) as any[];
        let n = 0;
        for (const t of rows) {
          if (!t?.id || !t?.item_name) continue;
          const { error } = await db.from("things").upsert({
            ...stamp(w), legacy_id: String(t.id), name: t.item_name, category: t.category ?? null, location_description: t.location_description ?? null,
            lat: t.coordinates_lat ?? null, lng: t.coordinates_lng ?? null, snapshot_url: t.snapshot_url ?? null, audio_context: t.audio_context ?? null,
            seen_at: t.captured_at ?? t.created_date ?? new Date().toISOString(),
          }, { onConflict: "legacy_id" });
          if (error) throw error;
          n++;
        }
        return json({ things: n });
      }
      default:
        return json({ error: "action" }, 400);
    }
  } catch (e) {
    console.error(e);
    return json({ error: "server", detail: String((e as any)?.message ?? e).slice(0, 200) }, 500);
  }
});
