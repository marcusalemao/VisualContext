// VisualContext — public demo (vc.alema.io/demo). The Gemini key never reaches the browser:
// this function hands out single-use, short-lived Live tokens locked to one model and one configuration,
// answers memory questions for the demo's own (fictional, per-session) memory, and stores the short review.
// Limits: sessions per IP per day, sessions per day overall, questions per session. Optional Cloudflare Turnstile.
import { createClient } from "npm:@supabase/supabase-js@2.45.4";

const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
const KEY = Deno.env.get("GEMINI_DEMO_KEY") ?? "";
const TURNSTILE = Deno.env.get("TURNSTILE_SECRET") ?? "";
const MODEL = Deno.env.get("DEMO_LIVE_MODEL") ?? "models/gemini-3.8-live";
const TEXT_MODEL = Deno.env.get("DEMO_TEXT_MODEL") ?? "models/gemini-2.5-flash";
const PER_IP = Number(Deno.env.get("DEMO_PER_IP_DAY") ?? 4);
const PER_DAY = Number(Deno.env.get("DEMO_PER_DAY") ?? 150);
const ASKS = 25, MINUTES = 10;
const ALLOWED = ["https://vc.alema.io", "https://visualcontext.alema.io", "https://web-visualcontext-site.visualcontext.pages.dev", "https://visualcontext.pages.dev"];

const cors = (o: string | null) => ({
  "Access-Control-Allow-Origin": o && (ALLOWED.includes(o) || o.endsWith(".visualcontext.pages.dev") || o.startsWith("http://localhost")) ? o : ALLOWED[0],
  "Access-Control-Allow-Headers": "content-type", "Access-Control-Allow-Methods": "POST, OPTIONS", "Vary": "Origin",
});
async function sha(t: string) { const h = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(t)); return [...new Uint8Array(h)].map((b) => b.toString(16).padStart(2, "0")).join(""); }

const SYS = {
  pt: `Você é o assistente do VisualContext, numa DEMONSTRAÇÃO no navegador: a câmera do computador ou do celular faz o papel dos óculos.
Fale português do Brasil, curto (1 a 2 frases), como quem cochicha no ouvido. A memória desta demo é só desta sessão (ou dados fictícios de exemplo); diga isso se perguntarem.
Ferramentas: quem está na frente → quem_e. "Essa é a Ana" / "guarda o rosto" → guardar_pessoa. "Essas são minhas chaves" / "guarda onde parei a moto" → guardar_objeto (nunca vire nota).
"Onde deixei…?" → onde_deixei antes de olhar. Perguntas sobre o passado ("o que eu fiz", "quem encontrei", "o que anotei") → consultar_memoria e responda só com o que vier.
"Anota…" → guardar_nota. Para ver algo, você recebe imagens da câmera a cada segundo. Nunca invente nomes de pessoas.`,
  en: `You are the VisualContext assistant in a browser DEMO: the computer's or phone's camera stands in for the glasses.
Speak English, short (1 to 2 sentences), like a whisper in the ear. This demo's memory is only this session (or fictional sample data); say so if asked.
Tools: who is in front → quem_e. "This is Ana" / "remember this face" → guardar_pessoa. "These are my keys" / "remember where I parked" → guardar_objeto (never a note).
"Where did I leave…?" → onde_deixei before looking. Questions about the past ("what did I do", "who did I meet", "what did I note") → consultar_memoria and answer only from it.
"Note that…" → guardar_nota. You get camera frames every second. Never invent people's names.`,
};
const S = (d: string) => ({ type: "STRING", description: d });
const fn = (name: string, description: string, props?: Record<string, unknown>, req?: string[]) =>
  ({ name, description, ...(props ? { parameters: { type: "OBJECT", properties: props, ...(req ? { required: req } : {}) } } : {}) });
const TOOLS = [
  fn("quem_e", "Who is in front of the user (face recognition in this session's memory)."),
  fn("guardar_pessoa", "Remember the face in front with the name the user said.", { nome: S("name") }, ["nome"]),
  fn("guardar_objeto", "Remember an object of the user with a photo and where it is.", { nome: S("object, short"), onde: S("where, if said or visible") }, ["nome"]),
  fn("onde_deixei", "Where the user left an object, from memory.", { objeto: S("object") }, ["objeto"]),
  fn("consultar_memoria", "Read the user's memory (encounters, people, objects, notes) to answer a question about the past.", { pergunta: S("the question") }, ["pergunta"]),
  fn("guardar_nota", "Save a short note.", { texto: S("note") }, ["texto"]),
];
function config(lang: "pt" | "en") {
  return {
    responseModalities: ["AUDIO"],
    speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: lang === "pt" ? "Kore" : "Aoede" } } },
    systemInstruction: { parts: [{ text: SYS[lang] }] },
    tools: [{ functionDeclarations: TOOLS }],
    inputAudioTranscription: {}, outputAudioTranscription: {},
    contextWindowCompression: { slidingWindow: {} },
  };
}

async function liveToken(lang: "pt" | "en") {
  const now = Date.now();
  const base = { uses: 1, expireTime: new Date(now + (MINUTES + 2) * 60_000).toISOString(), newSessionExpireTime: new Date(now + 90_000).toISOString() };
  const ask = (constraints: unknown) => fetch("https://generativelanguage.googleapis.com/v1beta/auth_tokens", {
    method: "POST", headers: { "content-type": "application/json", "x-goog-api-key": KEY }, body: JSON.stringify({ ...base, liveConnectConstraints: constraints }) });
  // Lock the whole configuration; if the API refuses some field, lock at least the model.
  let r = await ask({ model: MODEL, config: config(lang) });
  if (!r.ok) r = await ask({ model: MODEL, config: { responseModalities: ["AUDIO"] } });
  const j = await r.json().catch(() => ({}));
  if (!r.ok || !j.name) throw new Error("token " + r.status + " " + (j.error?.message ?? ""));
  return j.name as string;
}

Deno.serve(async (req) => {
  const o = req.headers.get("origin"); const h = { ...cors(o), "Content-Type": "application/json" };
  const out = (b: unknown, s = 200) => new Response(JSON.stringify(b), { status: s, headers: h });
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors(o) });
  if (req.method !== "POST") return out({ error: "POST" }, 405);
  let b: any = {}; try { b = await req.json(); } catch { return out({ error: "json" }, 400); }
  const ip = req.headers.get("cf-connecting-ip") ?? req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "0";
  const ipHash = await sha("vc-demo:" + ip);
  const lang: "pt" | "en" = b.lang === "pt" ? "pt" : "en";
  const day = new Date(Date.now() - 86_400_000).toISOString();
  try {
    if (b.action === "status") return out({ on: !!KEY, turnstile: !!TURNSTILE, minutes: MINUTES });
    if (b.action === "start") {
      if (!KEY) return out({ error: "demo_off" }, 503);
      if (TURNSTILE) {
        const f = new FormData(); f.append("secret", TURNSTILE); f.append("response", String(b.turnstile ?? "")); f.append("remoteip", ip);
        const v = await (await fetch("https://challenges.cloudflare.com/turnstile/v0/siteverify", { method: "POST", body: f })).json();
        if (!v.success) return out({ error: "human" }, 403);
      }
      const { count: mine } = await db.from("demo_sessions").select("id", { count: "exact", head: true }).eq("ip_hash", ipHash).gte("created_at", day);
      if ((mine ?? 0) >= PER_IP) return out({ error: "limit_ip" }, 429);
      const { count: all } = await db.from("demo_sessions").select("id", { count: "exact", head: true }).gte("created_at", day);
      if ((all ?? 0) >= PER_DAY) return out({ error: "limit_day" }, 429);
      const token = await liveToken(lang);
      const { data, error } = await db.from("demo_sessions").insert({ ip_hash: ipHash, lang }).select("id").single();
      if (error) throw error;
      return out({ session: data.id, token, model: MODEL, setup: { model: MODEL, ...config(lang) }, minutes: MINUTES });
    }
    if (b.action === "renew") { // the Live connection dropped mid-session: a new single-use token, same session, inside its 10 minutes
      const { data: s } = await db.from("demo_sessions").select("id, created_at, ended_at, asks").eq("id", b.session).maybeSingle();
      if (!s || s.ended_at || Date.now() - new Date(s.created_at).getTime() > MINUTES * 60_000) return out({ error: "ended" }, 403);
      if (s.asks >= ASKS) return out({ error: "limit_ask" }, 429);
      await db.from("demo_sessions").update({ asks: s.asks + 4 }).eq("id", s.id); // a reconnection costs like four questions
      return out({ token: await liveToken(lang), model: MODEL, setup: { model: MODEL, ...config(lang) } });
    }
    if (b.action === "ask") { // a question typed in the demo's Manager, answered from the memory the page sends (fictional/session only)
      if (!KEY) return out({ error: "demo_off" }, 503);
      const { data: s } = await db.from("demo_sessions").select("id, created_at, asks").eq("id", b.session).maybeSingle();
      if (!s || Date.now() - new Date(s.created_at).getTime() > (MINUTES + 20) * 60_000) return out({ error: "ended" }, 403);
      if (s.asks >= ASKS) return out({ error: "limit_ask" }, 429);
      await db.from("demo_sessions").update({ asks: s.asks + 1 }).eq("id", s.id);
      const mem = JSON.stringify(b.memory ?? []).slice(0, 20_000), q = String(b.question ?? "").slice(0, 300);
      const prompt = (lang === "pt"
        ? `Você responde pela memória pessoal do usuário (demo do VisualContext). Agora: ${b.now ?? new Date().toISOString()}.\nPergunta: "${q}"\nMemória (JSON):\n${mem}\nResponda em português, 1 a 3 frases concretas (nomes, lugares, horas). Só use o que está na memória; se não houver, diga que não há registro.`
        : `You answer from the user's personal memory (VisualContext demo). Now: ${b.now ?? new Date().toISOString()}.\nQuestion: "${q}"\nMemory (JSON):\n${mem}\nAnswer in English, 1 to 3 concrete sentences (names, places, times). Only use what is in memory; if nothing, say there is no record.`);
      const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/${TEXT_MODEL}:generateContent`, { method: "POST", headers: { "content-type": "application/json", "x-goog-api-key": KEY },
        body: JSON.stringify({ contents: [{ parts: [{ text: prompt }] }], generationConfig: { temperature: 0.2, maxOutputTokens: 400 } }) });
      const j = await r.json().catch(() => ({}));
      if (!r.ok) return out({ error: "ai", detail: j.error?.message?.slice(0, 120) }, 502);
      return out({ answer: (j.candidates?.[0]?.content?.parts ?? []).filter((p: any) => p.text && !p.thought).map((p: any) => p.text).join("").trim() });
    }
    if (b.action === "end") { if (b.session) await db.from("demo_sessions").update({ ended_at: new Date().toISOString() }).eq("id", b.session).is("ended_at", null); return out({ ok: true }); }
    if (b.action === "feedback") {
      const wu = ["sim", "talvez", "nao"].includes(b.would_use) ? b.would_use : null;
      const rating = Number.isInteger(b.rating) && b.rating >= 1 && b.rating <= 5 ? b.rating : null;
      if (!rating && !wu && !b.comment) return out({ error: "empty" }, 400);
      await db.from("demo_feedback").insert({ session_id: b.session ?? null, rating, would_use: wu, comment: b.comment ? String(b.comment).slice(0, 2000) : null, lang });
      return out({ ok: true });
    }
    return out({ error: "action" }, 400);
  } catch (e) {
    console.error(e);
    return out({ error: "server", detail: String((e as any)?.message ?? e).slice(0, 160) }, 500);
  }
});
