import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

const ENTITIES: Record<string, string> = {
  FaceContextPerson:     'FaceContextPerson',
  FaceContextEncounter:  'FaceContextEncounter',
  FaceContextHUDLayout:  'FaceContextHUDLayout',
  AIUsageLog:            'AIUsageLog',
  persons:    'FaceContextPerson',
  encounters: 'FaceContextEncounter',
  layouts:    'FaceContextHUDLayout',
};

const GEMINI_API_KEY = Deno.env.get("VC_GEMINI_API_KEY") || "";
const GEMINI_MODEL   = "gemini-flash-latest";
const GEMINI_URL     = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`;

// ── Preços Gemini Flash (por 1M tokens, em USD) ──
const PRICE_INPUT_PER_M  = 0.30;
const PRICE_OUTPUT_PER_M = 2.50;
const USD_TO_BRL         = 5.70;   // atualizar periodicamente
const BUDGET_USD         = 18.00;  // ~R$100 convertido
const ALERT_THRESHOLD    = 0.80;   // alerta em 80% do budget

// ── Auth do dispositivo (emulador/óculos sem login) + CORS origins oficiais ──
const DEVICE_TOKEN = Deno.env.get("VC_DEVICE_TOKEN") || "";
const ALLOWED_ORIGINS = ["https://fc.alema.io", "https://visualcontext.alema.io", "https://raw.githack.com"];
const RATE_LIMIT_PER_MIN = 60;

// Rate limit simples por janela de 1 min (por instância, best-effort)
const rateBuckets = new Map<string, { count: number; reset: number }>();
function rateLimit(key: string): boolean {
  const now = Date.now();
  const b = rateBuckets.get(key);
  if (!b || now > b.reset) {
    rateBuckets.set(key, { count: 1, reset: now + 60000 });
    return true;
  }
  b.count++;
  return b.count <= RATE_LIMIT_PER_MIN;
}

// ── Estimativa de tokens (sem chamar API de contagem) ──
function estimateTokens(text: string): number {
  // ~1 token por 4 chars em português/inglês
  return Math.ceil(text.length / 4);
}

function calcCost(inputTokens: number, outputTokens: number) {
  const usd = (inputTokens / 1_000_000) * PRICE_INPUT_PER_M
            + (outputTokens / 1_000_000) * PRICE_OUTPUT_PER_M;
  return { usd, brl: usd * USD_TO_BRL };
}

// ── Caching simples: evita resumo duplicado da mesma transcrição ──
const recentCache = new Map<string, string>();

function hashString(s: string): string {
  // djb2 em base36 — hash do texto INTEIRO
  let h = 5381;
  for (let i = 0; i < s.length; i++) h = ((h << 5) + h + s.charCodeAt(i)) >>> 0;
  return h.toString(36);
}

function cacheKey(text: string, personName = "", lang = ""): string {
  // hash do texto inteiro + contexto (pessoa e idioma), evita resposta velha
  // quando o emulador repete frases de correcao ou o estado da pessoa mudou
  return hashString(text) + "|" + hashString(personName + "|" + lang);
}

// ── Prompt de sistema do analyze_encounter ({lang} é substituído pelo lang da entrada) ──
const ANALYZE_SYSTEM_PROMPT = `You process the transcript of ONE in-person encounter between the USER (wearing smart glasses) and ONE other person in front of them. Return ONLY valid JSON matching the schema. Write every text field in {lang}.

1. NAME OF THE PERSON IN FRONT
- Introductions are short and informal in every language. Brazil: "Marcos, prazer", "sou a Mariana", "prazer, Ana" (rarely "meu nome é"). English: "I'm Mike", "nice to meet you, Sarah", "you must be Sarah". Spanish: "soy Carlos", "mucho gusto, Ana". Japanese/Chinese/Korean: family name first, honorifics (-san, -ssi, 先生) are not part of the name; people may be called by title ("Manager Kim"). A name read from a business card in the transcript or OCR is the strongest evidence.
- "prazer" is a strong marker that an introduction just happened; the name next to it is the person in front.
- The USER may name the person on purpose: "ah, então você é o Marcelo?", "deixa eu gravar você aqui, Marcelo Campos", "esse é o meu amigo Rafael Souza", "Karin, né?".
- Corrections: the LAST valid name wins ("Gabriela… perdão, Gabriela não, você é a Beatriz" → Beatriz). Spelling confirmations ("Patrícia com C") fix the spelling.
- Nickname: "pode me chamar de Dani, mas é Daniele" → name "Daniele", alias "Dani".
- THIRD PARTIES — VERY IMPORTANT: never use the name of someone who is only mentioned ("o João me disse", "você conhece a Carolina?", "sua mãe, a Karin", public figures). A name right after a family/relationship word (mãe, irmã, amigo, chefe…) belongs to someone else, unless it is "esse é o meu amigo X" presenting the person in front.
- Transcripts have no reliable punctuation or capitalisation. Judge by meaning.
- If unsure, return name = null. Never invent a name. name_evidence must be a literal quote from the transcript.

2. CARD
- tags: 2–3 short lowercase words (relationship, place, interest). Never "desconhecido/desconhecida", never the person's name.
- hook: the single most memorable thing about THIS encounter, a theme, a funny situation, a phrase. Max ~8 words, no name. In Portuguese, phrase it like "a fofoca do relógio falso" so it reads well after the visual description.
- context: 1–2 sentences on what this encounter was about. Not a literal transcript.
- dossier: ~4 short lines about who this person is to the user. Facts about the OTHER person ONLY: anything the USER says about themselves ("eu trabalho na X", "minha filha…", "eu moro em…") must NEVER enter this dossier. Merge with the previous dossier (given in input): keep durable facts, add new ones (job changes, tastes, life events), drop trivia.
- actions: concrete follow-ups the USER committed to, if any. Promises count as actions even when phrased casually: "vou te mandar o contato do pessoal da Latam", "te mando o orçamento por email", "I'll send you the deck" → actions.

- Never infer nationality, ethnicity or culture of the person. Formality comes only from how they introduce themselves (titles, business card, surname-only). If the evidence gives family-name-first, fill given_name and family_name.

Schema:
{"name": string|null, "given_name": string|null, "family_name": string|null, "formal": boolean, "name_confidence": number, "name_evidence": string|null, "alias": string|null,
 "tags": string[], "hook": string, "context": string, "dossier": string, "actions": string[]}`;

async function callGemini(prompt: string, maxTokens = 150, opts: { systemInstruction?: string; responseJson?: boolean; disableThinking?: boolean } = {}): Promise<{ text: string; inputTokens: number; outputTokens: number }> {
  const genBody: any = {
    contents: [{ role: "user", parts: [{ text: prompt }] }],
    generationConfig: { maxOutputTokens: maxTokens, temperature: 0.4 }
  };
  if (opts.systemInstruction) genBody.systemInstruction = { parts: [{ text: opts.systemInstruction }] };
  if (opts.responseJson) genBody.generationConfig.responseMimeType = "application/json";
  if (opts.disableThinking) genBody.generationConfig.thinkingConfig = { thinkingBudget: 0 };

  const res = await fetch(GEMINI_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-goog-api-key": GEMINI_API_KEY
    },
    body: JSON.stringify(genBody)
  });
  const d = await res.json();
  if (d.error) throw new Error(d.error.message || "Gemini error");

  const text = d?.candidates?.[0]?.content?.parts?.[0]?.text?.trim() || "";
  // Usa contagem real se disponível, senão estima
  const inputTokens  = d?.usageMetadata?.promptTokenCount     || estimateTokens(prompt);
  const outputTokens = d?.usageMetadata?.candidatesTokenCount || estimateTokens(text);

  return { text, inputTokens, outputTokens };
}

async function detectLang(text: string): Promise<string> {
  if (!text || text.length < 10) return "pt";
  // Heurística rápida antes de chamar a API (economiza tokens)
  const ptWords = /\b(que|você|para|com|uma|não|mais|por|isso|como|mas)\b/i;
  const enWords = /\b(the|and|for|you|that|with|this|from|have|are)\b/i;
  if (ptWords.test(text)) return "pt";
  if (enWords.test(text)) return "en";
  // Só chama Gemini se realmente não dá pra detectar localmente
  const r = await callGemini(
    `Language code only (ISO 639-1, 2 chars): "${text.substring(0,100)}"`, 3, { disableThinking: true }
  );
  return r.text.replace(/[^a-z]/g,"").substring(0,2) || "pt";
}

// ── Verifica budget mensal e dispara alerta ──
async function checkBudgetAlert(db: any, newCostUsd: number) {
  const monthKey = new Date().toISOString().substring(0, 7);
  const allLogs = await db["AIUsageLog"].list({ limit: 500 });
  const logs = (allLogs || []).filter((l: any) => l.month_key === monthKey);
  const totalUsd = (logs || []).reduce((sum: number, l: any) => sum + (l.cost_usd || 0), 0) + newCostUsd;
  const pct = (totalUsd / BUDGET_USD) * 100;

  return {
    month_total_usd: totalUsd,
    month_total_brl: totalUsd * USD_TO_BRL,
    budget_pct: Math.round(pct),
    alert: pct >= ALERT_THRESHOLD * 100,
    exhausted: totalUsd >= BUDGET_USD
  };
}

// ── Log de gasto (mesmo registro do ai_summary) ──
function logUsage(db: Record<string, any>, action: string, inputTokens: number, outputTokens: number, personName: string) {
  const { usd, brl } = calcCost(inputTokens, outputTokens);
  const monthKey = new Date().toISOString().substring(0, 7);
  db["AIUsageLog"].create({
    action: action, model: GEMINI_MODEL,
    input_tokens: inputTokens, output_tokens: outputTokens,
    cost_usd: usd, cost_brl: brl,
    person_name: personName, success: true, month_key: monthKey
  }).catch(() => {});
  return { usd, brl };
}

Deno.serve(async (req) => {
  // ── CORS: só libera os origins oficiais; preflight aceita o header x-vc-token ──
  const baseCors: Record<string, string> = {
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, x-vc-token",
    "Vary": "Origin",
  };
  const origin = req.headers.get("Origin") || "";
  if (ALLOWED_ORIGINS.includes(origin)) baseCors["Access-Control-Allow-Origin"] = origin;
  const CORS = baseCors;
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });

  try {
    const base44 = createClientFromRequest(req);
    const url    = new URL(req.url);
    const id     = url.searchParams.get("id");
    const entityParam = url.searchParams.get("entity") || "FaceContextPerson";
    const entityName  = ENTITIES[entityParam] || "FaceContextPerson";

    // Proxy para entidades genéricas
    const db: Record<string, any> = {};
    for (const key of Object.keys(ENTITIES)) {
      db[key] = base44.asServiceRole.entities[ENTITIES[key]];
    }
    const entityDb = db[entityParam] || db["FaceContextPerson"];

    // ── Auth: x-vc-token (emulador/óculos) OU usuário Base44 logado (Manager) ──
    const token = req.headers.get("x-vc-token") || "";
    if (DEVICE_TOKEN && token === DEVICE_TOKEN) {
      if (!rateLimit("device")) {
        return Response.json({ error: "rate_limited", retry_after_s: 60 }, { status: 429, headers: CORS });
      }
    } else {
      // qualquer outra origem: exige usuário Base44 logado
      let userOk = false;
      try {
        await base44.entities.FaceContextHUDLayout.list("-created_date", 1, 0);
        userOk = true;
      } catch { /* sem usuário logado */ }
      if (!userOk) {
        return Response.json({ error: "unauthorized" }, { status: 401, headers: CORS });
      }
    }

    // ── GET ──
    if (req.method === "GET") {
      // Rota especial: /usage — retorna resumo de gastos
      if (url.searchParams.get("action") === "usage") {
        const monthKey = url.searchParams.get("month") || new Date().toISOString().substring(0, 7);
        const allLogs2 = await db["AIUsageLog"].list({ limit: 500 });
        const logs = (allLogs2 || []).filter((l: any) => l.month_key === monthKey);
        const totalUsd = (logs || []).reduce((s: number, l: any) => s + (l.cost_usd || 0), 0);
        const totalCalls = (logs || []).length;
        const byAction: Record<string, number> = {};
        for (const l of (logs || [])) {
          byAction[l.action] = (byAction[l.action] || 0) + 1;
        }
        return Response.json({
          month: monthKey,
          total_calls: totalCalls,
          total_usd:   +totalUsd.toFixed(4),
          total_brl:   +(totalUsd * USD_TO_BRL).toFixed(2),
          budget_usd:  BUDGET_USD,
          budget_pct:  Math.round((totalUsd / BUDGET_USD) * 100),
          budget_remaining_brl: +((BUDGET_USD - totalUsd) * USD_TO_BRL).toFixed(2),
          by_action:   byAction,
          model:       GEMINI_MODEL
        }, { headers: CORS });
      }

      if (id) return Response.json(await entityDb.get(id), { headers: CORS });
      // list() retorna todos os registros (filter({}) não funciona no SDK)
      const all: unknown[] = [];
      let skip = 0;
      while (true) {
        const batch = await entityDb.list("-created_date", 100, skip);   // SDK: list(sort, limit, skip)
        if (!batch || batch.length === 0) break;
        all.push(...batch);
        if (batch.length < 100) break;
        skip += 100;
      }
      return Response.json(all, { headers: CORS });
    }

    // ── POST ──
    if (req.method === "POST") {
      const body = await req.json().catch(() => ({}));

      // ── ai_summary ──
      if (body.action === "ai_summary") {
        const transcript: string = body.transcript  || "";
        const personName: string = body.person_name || "desconhecido";
        const userLang: string   = body.lang        || "";

        if (!transcript.trim()) {
          return Response.json({ summary: "Encontro registrado", lang: "pt", cost_usd: 0 }, { headers: CORS });
        }

        // Cache: evita chamar Gemini para a mesma conversa
        const ck = cacheKey(transcript, personName, userLang);
        if (recentCache.has(ck)) {
          return Response.json({ summary: recentCache.get(ck), lang: userLang||"pt", cost_usd: 0, cached: true }, { headers: CORS });
        }

        // Trunca input pra economizar tokens (máx 600 chars ~ 150 tokens)
        const truncated = transcript.length > 600 ? transcript.substring(0, 600) + "…" : transcript;

        const lang = userLang || await detectLang(truncated);
        const langNames: Record<string,string> = {
          pt:"português", en:"English", es:"español", fr:"français",
          de:"Deutsch", ja:"日本語", zh:"中文", it:"italiano"
        };
        const langLabel = langNames[lang] || lang;

        // Prompt enxuto (menos tokens)
        const prompt =
          `Assistente de memória. Responda em ${langLabel}. ` +
          `Resuma em UMA frase natural e completa sobre o que foi dito (sem verbosidade).\n` +
          `Pessoa: ${personName}\n` +
          `"${truncated}"\nResumo:`;

        try {
          const { text, inputTokens, outputTokens } = await callGemini(prompt, 200, { disableThinking: true });
          const summary = text.replace(/^["'`]|["'`]$/g,"").substring(0, 160);
          const { usd } = logUsage(db, "ai_summary", inputTokens, outputTokens, personName);

          // Cache por 5 min
          recentCache.set(ck, summary);
          setTimeout(() => recentCache.delete(ck), 5 * 60 * 1000);

          // Verifica budget (assíncrono, não bloqueia resposta)
          const budgetInfo = await checkBudgetAlert(db, usd);

          return Response.json({
            summary: summary || truncated.substring(0,80),
            lang, model: GEMINI_MODEL,
            cost_usd: +usd.toFixed(6),
            ...(budgetInfo.alert ? {
              budget_alert: true,
              budget_pct: budgetInfo.budget_pct,
              budget_remaining_brl: budgetInfo.budget_remaining_brl
            } : {})
          }, { headers: CORS });
        } catch (e: any) {
          return Response.json({ summary: transcript.substring(0,80), lang: "pt", model: "fallback", error: e.message }, { headers: CORS });
        }
      }

      // ── analyze_encounter ──
      if (body.action === "analyze_encounter") {
        const transcript: string = body.transcript || "";
        const lang: string = body.lang || "pt";

        if (!transcript.trim()) {
          return Response.json({ error: "empty_transcript" }, { headers: CORS });
        }

        // Entrada inteira (sem "action") como mensagem do usuário
        const { action, ...analyzeInput } = body;
        const systemInstruction = ANALYZE_SYSTEM_PROMPT.replace("{lang}", lang);

        try {
          const { text, inputTokens, outputTokens } = await callGemini(
            JSON.stringify(analyzeInput),
            1500,
            { systemInstruction, responseJson: true, disableThinking: true }
          );
          logUsage(db, "analyze_encounter", inputTokens, outputTokens, analyzeInput?.person?.name || "");

          let parsed: any;
          try {
            parsed = JSON.parse(text);
          } catch {
            return Response.json({ error: "invalid_model_json", raw: text }, { headers: CORS });
          }
          return Response.json(parsed, { headers: CORS });
        } catch (e: any) {
          return Response.json({ error: e.message }, { headers: CORS });
        }
      }

      // ── ai_detect_name ──
      if (body.action === "ai_detect_name") {
        const text: string = body.text || "";
        if (!text.trim()) return Response.json({ name: null }, { headers: CORS });

        // Tenta regex primeiro (zero custo)
        const regexPatterns = [
          /(?:me chamo|meu nome é|sou (?:a |o |))([\w\s]{2,25})/i,
          /(?:pode me chamar de|me chamam de)\s+([\w\s]{2,20})/i,
          /(?:i(?:'m| am)|my name(?:'s| is))\s+([\w\s]{2,25})/i,
          /(?:soy|me llamo)\s+([\w\s]{2,25})/i,
        ];
        for (const rx of regexPatterns) {
          const m = text.match(rx);
          if (m?.[1]) return Response.json({ name: m[1].trim(), source: "regex" }, { headers: CORS });
        }

        // Só chama Gemini se regex falhar
        const prompt = `Nome próprio de pessoa nesta frase (só o nome, ou "null"):\n"${text.substring(0,200)}"`;
        try {
          const { text: result, inputTokens, outputTokens } = await callGemini(prompt, 15, { disableThinking: true });
          const name = result.replace(/[^a-zA-ZÀ-ú\s]/g,"").trim();
          logUsage(db, "ai_detect_name", inputTokens, outputTokens, "");
          return Response.json({ name: name && name !== "null" ? name : null, source: "gemini" }, { headers: CORS });
        } catch (e: any) {
          return Response.json({ name: null, error: e.message }, { headers: CORS });
        }
      }

      // ── list (emulator/Manager contract): POST {action:"list", entity, limit} → array ──
      if (body.action === "list") {
        const listDb = db[body.entity] || entityDb;
        const max = Math.min(Math.max(+body.limit || 200, 1), 1000);
        const out: unknown[] = [];
        let skip = 0;
        while (out.length < max) {
          const batch = await listDb.list("-created_date", Math.min(100, max - out.length), skip);
          if (!batch || batch.length === 0) break;
          out.push(...batch);
          if (batch.length < 100) break;
          skip += batch.length;
        }
        return Response.json(out, { headers: CORS });
      }
      // Never create a record from a request that carries an unknown "action"
      if (typeof body.action === "string") {
        return Response.json({ error: "unknown_action", action: body.action }, { status: 400, headers: CORS });
      }

      // ── CRUD normal ──
      const created = await entityDb.create(body);
      return Response.json(created, { status: 201, headers: CORS });
    }

    // ── PUT ──
    if (req.method === "PUT") {
      if (!id) return Response.json({ error: "id required" }, { status: 400, headers: CORS });
      const updated = await entityDb.update(id, await req.json().catch(() => ({})));
      return Response.json(updated, { headers: CORS });
    }

    // ── DELETE ──
    if (req.method === "DELETE") {
      if (!id) return Response.json({ error: "id required" }, { status: 400, headers: CORS });
      await entityDb.delete(id);
      return Response.json({ ok: true }, { headers: CORS });
    }

    return Response.json({ error: "method_not_supported" }, { status: 405, headers: CORS });
  } catch (error: any) {
    return Response.json({ error: error.message }, { status: 500, headers: CORS });
  }
});