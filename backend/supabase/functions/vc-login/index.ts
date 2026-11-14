// VisualContext — "send me my code again" (Marcus, 14/11/2026).
// The Manager's e-mail door. An approved e-mail gets its access code again, through Resend, from the same domain as the
// approval e-mail. A pending, declined or unknown e-mail is told so (which also catches typos). The founder and admins
// keep the Supabase e-mail login (state "login"). Public: no token, no JWT; it only ever mails an address that is
// already on the approved list, and no more than one e-mail per address every 2 minutes.
// Small on purpose: it does not touch the big `vc` function. Invite codes: same table, same format (VC-XXXX-XXXX).
import { createClient } from "npm:@supabase/supabase-js@2.45.4";

const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type, authorization, apikey",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (b: unknown, s = 200) => new Response(JSON.stringify(b), { status: s, headers: { ...cors, "Content-Type": "application/json" } });

const FOUNDER = "marcusdemagalhaes@gmail.com";
const DOWNLOAD = "https://vc.alema.io/test-download/";
const MANAGER = "https://vc.alema.io/manager";
const CODE_DAYS = 14, CODE_USES = 3;
const flat = (code: string) => code.replace(/-/g, "");
const showCode = (raw: string) => "VC-" + raw.slice(0, 4) + "-" + raw.slice(4);
const esc = (s: string) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" } as Record<string, string>)[c]);

async function newCode(email: string) {
  const abc = "23456789ABCDEFGHJKMNPQRSTUVWXYZ", r = new Uint8Array(8); crypto.getRandomValues(r);
  const raw = [...r].map((x) => abc[x % abc.length]).join("");
  await db.from("invites").update({ invite_code: raw, code_expires: new Date(Date.now() + CODE_DAYS * 86400_000).toISOString(), code_uses: 0 }).eq("email", email);
  return showCode(raw);
}

const T: Record<string, Record<string, string>> = {
  pt: { subj: "Seu código de acesso do VisualContext", hi: "Oi", line: "Você pediu o seu código de novo. Aqui está:", valid: `Vale ${CODE_DAYS} dias, em até ${CODE_USES} aparelhos. Ele é seu: não precisa compartilhar.`, s1: "Instale o app no celular Android", s1d: "Abra o botão abaixo no celular (ou escaneie o link por lá) e baixe o app.", btn: "Baixar o app e ver o roteiro", s2: "Entre com o código", s2d: "Abra o app, toque em “Tenho um código de convite” e digite o código acima.", s3: "Depois, no seu navegador", s3d: "O Manager abre no Chrome, no celular ou no computador, e mostra o que o app guardou. Só faz sentido depois das primeiras gravações, e usa o mesmo código.", mgr: "Abrir o Manager", ign: "Se não foi você, ignore este e-mail.", bye: "Marcus" },
  en: { subj: "Your VisualContext access code", hi: "Hi", line: "You asked for your code again. Here it is:", valid: `It works for ${CODE_DAYS} days, on up to ${CODE_USES} devices. It's yours: no need to share it.`, s1: "Install the app on your Android phone", s1d: "Open the button below on your phone and download the app.", btn: "Get the app and the test plan", s2: "Sign in with the code", s2d: "Open the app, tap “Tenho um código de convite” (I have an invite code) and type the code above.", s3: "Later, in your browser", s3d: "The Manager opens in Chrome, on your phone or computer, and shows what the app saved. It only makes sense after your first recordings, and it uses the same code.", mgr: "Open the Manager", ign: "If this wasn't you, just ignore this e-mail.", bye: "Marcus" },
  de: { subj: "Dein Zugangscode für VisualContext", hi: "Hallo", line: "Du hast deinen Code noch einmal angefordert. Hier ist er:", valid: `Er gilt ${CODE_DAYS} Tage, auf bis zu ${CODE_USES} Geräten. Er gehört dir: bitte nicht weitergeben.`, s1: "Installiere die App auf deinem Android-Handy", s1d: "Öffne den Button unten auf dem Handy und lade die App herunter.", btn: "App holen und Testplan ansehen", s2: "Mit dem Code anmelden", s2d: "Öffne die App, tippe auf „Tenho um código de convite“ (Ich habe einen Einladungscode) und gib den Code oben ein.", s3: "Später im Browser", s3d: "Der Manager öffnet sich in Chrome, auf dem Handy oder am Computer, und zeigt, was die App gespeichert hat. Er ergibt erst nach den ersten Aufnahmen Sinn und nutzt denselben Code.", mgr: "Manager öffnen", ign: "Warst du das nicht, ignoriere diese E-Mail einfach.", bye: "Marcus" },
};

function mail(name: string | null, code: string, lang?: string | null) {
  const t = T[lang === "en" || lang === "de" ? lang : "pt"], first = (name ?? "").trim().split(/\s+/)[0], c = flat(code);
  const page = `${DOWNLOAD}#c=${c}`, sep = lang === "en" || lang === "de" ? " " : ", ";
  const step = (n: number, h: string, d: string) => `<tr><td valign="top" style="padding:0 12px 14px 0;width:26px"><div style="width:26px;height:26px;border-radius:13px;background:#0b8a52;color:#ffffff;font-weight:bold;font-size:14px;line-height:26px;text-align:center">${n}</div></td><td style="padding:0 0 14px;font-size:14px;line-height:1.45"><b style="color:#fff">${esc(h)}</b><br><span style="color:#b9c7bf">${esc(d)}</span>`;
  return {
    subject: `${t.subj}: ${c}`,
    text: `${t.hi}${first ? sep + first : ""}!\n\n${t.line}\n\n${c}\n\n${t.valid}\n\n1. ${t.s1}\n   ${t.s1d}\n   ${page}\n\n2. ${t.s2}\n   ${t.s2d}\n\n3. ${t.s3}\n   ${t.s3d}\n   ${MANAGER}\n\n${t.ign}\n${t.bye}`,
    html: `<!doctype html><html><head><meta charset="utf-8"><meta name="color-scheme" content="dark light"><meta name="supported-color-schemes" content="dark light"></head><body style="margin:0;background:#07090c;padding:24px 12px;font-family:Arial,Helvetica,sans-serif;color:#e8f0ec">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center">
<table role="presentation" width="100%" style="max-width:520px;background:#0d1217;border:1px solid #1f2a24;border-radius:16px" cellpadding="0" cellspacing="0"><tr><td style="padding:28px 24px">
<p style="margin:0 0 4px;font-size:12px;letter-spacing:2px;color:#00ff88;font-weight:bold">VISUALCONTEXT · BETA</p>
<h1 style="margin:0 0 14px;font-size:22px;color:#ffffff">${esc(t.hi)}${first ? sep + esc(first) : ""}!</h1>
<p style="margin:0 0 14px;font-size:16px;color:#8fdfb5">${esc(t.line)}</p>
<div style="background:#07090c;border:2px solid #00ff88;border-radius:12px;padding:16px;text-align:center"><span style="font-family:'Courier New',Courier,monospace;font-size:30px;font-weight:bold;letter-spacing:4px;color:#00ff88;-webkit-user-select:all;user-select:all">${esc(c)}</span></div>
<p style="margin:10px 0 22px;font-size:13px;color:#9aaaa2">${esc(t.valid)}</p>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0">
${step(1, t.s1, t.s1d)}<br><a href="${page}" style="display:inline-block;background:#0b8a52;color:#ffffff;text-decoration:none;font-weight:bold;font-size:16px;padding:13px 24px;border-radius:12px;margin-top:10px;border:2px solid #00ff88">${esc(t.btn)}</a></td></tr>
${step(2, t.s2, t.s2d)}</td></tr>
${step(3, t.s3, t.s3d)}<br><a href="${MANAGER}" style="color:#00ff88">${esc(t.mgr)}</a></td></tr>
</table>
<p style="margin:8px 0 0;font-size:12px;color:#9aaaa2">${esc(t.ign)}<br>${esc(t.bye)} · <a href="https://alema.io" style="color:#00ff88">alema.io</a></p>
</td></tr></table></td></tr></table></body></html>`,
  };
}

async function sendMail(to: string, m: { subject: string; text: string; html: string }) {
  const rk = Deno.env.get("RESEND_API_KEY"); if (!rk) { console.error("vc-login: RESEND_API_KEY is not set"); return false; }
  const r = await fetch("https://api.resend.com/emails", { method: "POST", headers: { Authorization: `Bearer ${rk}`, "Content-Type": "application/json" },
    body: JSON.stringify({ from: Deno.env.get("INVITE_FROM") ?? Deno.env.get("FEEDBACK_FROM") ?? "VisualContext <convite@alema.io>", to: [to], reply_to: Deno.env.get("FEEDBACK_TO") ?? undefined, ...m }) }).catch((e) => { console.error("vc-login: fetch", String(e)); return null; });
  if (!r?.ok) console.error("vc-login: Resend refused", r?.status, r ? (await r.text().catch(() => "")).slice(0, 300) : "no response");
  return !!r?.ok;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "use POST" }, 405);
  let b: any = {};
  try { b = await req.json(); } catch { /* empty */ }
  const email = String(b.email ?? "").trim().toLowerCase().slice(0, 200);
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return json({ error: "email" }, 400);
  if (email === FOUNDER) return json({ ok: true, state: "login" });
  const { data: acc } = await db.from("accounts").select("plan").eq("owner_email", email).maybeSingle();
  if (acc?.plan === "founder" || acc?.plan === "admin") return json({ ok: true, state: "login" });
  const { data: inv } = await db.from("invites").select("active, invite_code, code_expires, notified_at, notified_how").eq("email", email).maybeSingle();
  if (!inv || !inv.active) {
    const { data: rq } = await db.from("beta_requests").select("status").eq("email", email).maybeSingle();
    return json({ ok: true, state: rq?.status === "pending" ? "pending" : rq?.status === "declined" ? "declined" : "none" });
  }
  if (inv.notified_how === "email" && inv.notified_at && Date.now() - new Date(inv.notified_at).getTime() < 120_000) return json({ ok: true, state: "sent", throttled: true });
  const code = inv.invite_code && inv.code_expires && new Date(inv.code_expires).getTime() > Date.now() ? showCode(inv.invite_code) : await newCode(email);
  const { data: r } = await db.from("beta_requests").select("name, lang").eq("email", email).maybeSingle();
  const ok = await sendMail(email, mail(r?.name ?? null, code, r?.lang));
  if (!ok) return json({ ok: true, state: "mail_failed" });
  await db.from("invites").update({ notified_at: new Date().toISOString(), notified_how: "email" }).eq("email", email);
  return json({ ok: true, state: "sent" });
});
