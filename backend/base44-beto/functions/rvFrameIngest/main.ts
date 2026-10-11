/**
 * RokidLive — Pipeline de visão (1 FPS).
 *
 * POST { frame_base64, timestamp?, lat?, lng?, audio_transcript_chunk?, snapshot_base64? }
 * snapshot_base64: thumbnail 160x120 JPEG do MESMO frame (para o registro de memória).
 *
 * v1.0.5 — Auth: x-vc-token (óculos/emulador) OU usuário Base44 logado. 401 caso contrário.
 * 1. Frame-differencing: descarta frames idênticos (câmera estática)
 *    via hash do payload + intervalo mínimo entre análises (protege o budget).
 * 2. Envia o frame ao Gemini 2.5 Flash (System Prompt de visão episódica)
 *    com as DynamicSkills ativas anexadas como regras de extração.
 * 3. Salva detecções em EpisodicMemory / PersonInteraction / HUDNotification.
 * 4. Registra custo em AIUsageLog (budget R$100/mês).
 */

const CORS: Record<string, string> = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, api_key, x-api-key, x-vc-token',
};

const MODEL = 'gemini-2.5-flash';
const MIN_ANALYSIS_INTERVAL_MS = 4000; // no máx. 1 análise Gemini a cada 4s
const COST_PER_MTOK_IN = 0.30; // USD
const COST_PER_MTOK_OUT = 2.50; // USD
const USD_TO_BRL = 5.4;

const SYSTEM_PROMPT = `Você é o núcleo de visão de um óculos AR. Analise a cena.
Se identificar objetos pessoais sendo pousados ou estacionados (como chaves, moto, carro, carteira), extraia o local exato com pontos de referência.
Se identificar uma pessoa interagindo, descreva as âncoras visuais (roupa, contexto) e resuma a interação.
Responda SEMPRE em JSON com esta estrutura:
{
  "objects": [{"item_name": "chaves", "category": "veiculo|objeto_pessoal|documento|outro", "location_description": "descrição semântica com pontos de referência", "action": "pousado|estacionado|retirado"}],
  "people": [{"temp_identifier": "Pessoa N", "visual_anchors": "roupa, óculos, acessórios", "conversation_summary": "tópicos e acordos"}]
}
Arrays vazios se nada for detectado. Seja extremamente conciso nas descrições.`;

// Estado em memória da instância (frame-differencing)
let lastFrameHash = '';
let lastAnalysisAt = 0;

function djb2(s: string): string {
  let h = 5381;
  for (let i = 0; i < s.length; i += 3) {
    // amostra o base64 para hash rápido
    h = ((h << 5) + h + s.charCodeAt(i)) >>> 0;
  }
  return h.toString(16);
}

/** Snapshot do registro: thumbnail do client (preferencial) ou frame pequeno.
 *  Entity fields têm limite de tamanho — nunca inline do frame 640x480 completo. */
function buildSnapshot(body: any, frame_base64: string): string {
  let snap = String(body.snapshot_base64 ?? '');
  const m = snap.match(/^data:image\/[a-zA-Z+]+;base64,(.+)$/);
  if (m) snap = m[1];
  if (snap && snap.length <= 8000) return `data:image/jpeg;base64,${snap}`;
  if (!snap && frame_base64.length <= 8000) return `data:image/jpeg;base64,${frame_base64}`;
  return ''; // sem snapshot — registro guarda só a descrição semântica
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...CORS },
  });
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS });

  // ── Auth: x-vc-token (óculos/emulador) OU usuário Base44 logado ──
  const DEVICE_TOKEN = Deno.env.get('VC_DEVICE_TOKEN') || '';
  const headerToken = req.headers.get('x-vc-token') || '';
  if (!(DEVICE_TOKEN && headerToken === DEVICE_TOKEN)) {
    let userOk = false;
    try {
      const { createClientFromRequest } = await import('npm:@base44/sdk@0.8.31');
      const b44 = createClientFromRequest(req);
      await b44.entities.EpisodicMemory.list('-created_date', 1, 0);
      userOk = true;
    } catch { /* anônimo */ }
    if (!userOk) return json({ ok: false, error: 'unauthorized' }, 401);
  }

  try {
    const { createClientFromRequest } = await import('npm:@base44/sdk@0.8.31');
    const base44 = createClientFromRequest(req);
    const db = base44.asServiceRole.entities;

    const body = await req.json().catch(() => ({}));
    let frame_base64 = String(body.frame_base64 ?? '');
    // aceita tanto base64 puro quanto data URL do browser (data:image/jpeg;base64,...)
    const b64Match = frame_base64.match(/^data:image\/[a-zA-Z+]+;base64,(.+)$/);
    if (b64Match) frame_base64 = b64Match[1];
    if (!frame_base64) return json({ ok: false, error: 'frame_base64 obrigatório' }, 400);

    // ---- 1. Frame-differencing ----
    const hash = djb2(frame_base64);
    const now = Date.now();
    const staticFrame = hash === lastFrameHash;
    const tooSoon = now - lastAnalysisAt < MIN_ANALYSIS_INTERVAL_MS;
    lastFrameHash = hash;
    if (staticFrame || tooSoon) {
      return json({
        ok: true,
        analyzed: false,
        reason: staticFrame ? 'static_frame' : 'rate_limited',
        ts: now,
      });
    }
    lastAnalysisAt = now;

    // ---- 2. DynamicSkills ativas ----
    let skills: Array<Record<string, unknown>> = [];
    try {
      skills = await db.DynamicSkill.filter({ is_active: true }, '-created_date', 20);
    } catch { /* entidade pode estar vazia */ }

    let prompt = SYSTEM_PROMPT;
    if (skills.length > 0) {
      const skillRules = skills
        .map((s: any) => `- Skill "${s.skill_name}" (ação: ${s.action_type}): ${s.trigger_instruction}`)
        .join('\n');
      prompt += `\n\nAlém disso, aplique estas regras de extração aprendidas do usuário:\n${skillRules}\nInclua os resultados no JSON numa chave extra "skills": [{"skill_name": "...", "data": {...}}].`;
    }

    // ---- 3. Chamada Gemini ----
    const apiKey = Deno.env.get('GEMINI_API_KEY') ?? '';
    if (!apiKey) return json({ ok: false, error: 'GEMINI_API_KEY ausente' }, 500);

    const geminiBody = {
      systemInstruction: { parts: [{ text: prompt }] },
      contents: [{
        role: 'user',
        parts: [
          { text: 'Analise este frame da câmera do óculos.' },
          { inline_data: { mime_type: 'image/jpeg', data: frame_base64 } },
        ],
      }],
      generationConfig: { responseMimeType: 'application/json', temperature: 0.1 },
    };

    const t0 = Date.now();
    const gRes = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-goog-api-key': apiKey },
        body: JSON.stringify(geminiBody),
      },
    );
    if (!gRes.ok) {
      const errText = await gRes.text();
      return json({ ok: false, error: 'gemini_error', detail: errText.slice(0, 300) }, 502);
    }
    const gData = await gRes.json();
    const inTok = gData?.usageMetadata?.promptTokenCount ?? 0;
    const outTok = gData?.usageMetadata?.candidatesTokenCount ?? 0;
    const latency = Date.now() - t0;

    let parsed: any = { objects: [], people: [], skills: [] };
    try {
      const text = gData?.candidates?.[0]?.content?.parts?.[0]?.text ?? '{}';
      parsed = JSON.parse(text);
    } catch { /* responde JSON inválido → ignora */ }

    // ---- 4. Persistência ----
    const captured_at = body.timestamp ?? new Date().toISOString();
    let savedObjects = 0;
    const savedItems: string[] = [];
    for (const obj of (parsed.objects ?? [])) {
      if (!obj?.item_name || (obj.action !== 'pousado' && obj.action !== 'estacionado')) continue;
      savedItems.push(String(obj.item_name));
      await db.EpisodicMemory.create({
        item_name: String(obj.item_name).slice(0, 60),
        category: ['veiculo', 'objeto_pessoal', 'documento', 'outro'].includes(obj.category)
          ? obj.category : 'outro',
        location_description: String(obj.location_description ?? '').slice(0, 1000),
        coordinates_lat: typeof body.lat === 'number' ? body.lat : undefined,
        coordinates_lng: typeof body.lng === 'number' ? body.lng : undefined,
        snapshot_url: buildSnapshot(body, frame_base64),
        captured_at,
      });
      savedObjects++;
    }

    let savedPeople = 0;
    const peopleToday = await db.PersonInteraction.list('-created_date', 1);
    const nextPersonNum = (peopleToday?.length ?? 0) + 1;
    for (const p of (parsed.people ?? [])) {
      if (!p?.visual_anchors && !p?.conversation_summary) continue;
      await db.PersonInteraction.create({
        temp_identifier: String(p.temp_identifier ?? `Pessoa ${nextPersonNum}`).slice(0, 40),
        visual_anchors: String(p.visual_anchors ?? '').slice(0, 400),
        conversation_summary: String(p.conversation_summary ?? '').slice(0, 1000),
        start_time: captured_at,
        end_time: captured_at,
      });
      savedPeople++;
    }

    let savedSkillAlerts = 0;
    for (const sk of (parsed.skills ?? [])) {
      if (!sk?.skill_name) continue;
      await db.HUDNotification.create({
        title: String(sk.skill_name).slice(0, 60),
        content_type: 'alert',
        payload: { data: sk.data ?? {}, rule: 'dynamic_skill' },
        displayed: false,
        created_at: new Date().toISOString(),
      });
      savedSkillAlerts++;
    }

    // ---- 5. Log de custo (budget tracker) ----
    const cost_usd = (inTok / 1e6) * COST_PER_MTOK_IN + (outTok / 1e6) * COST_PER_MTOK_OUT;
    try {
      await db.AIUsageLog.create({
        action: 'rvFrameIngest',
        model: MODEL,
        input_tokens: inTok,
        output_tokens: outTok,
        cost_usd,
        cost_brl: cost_usd * USD_TO_BRL,
        success: true,
        month_key: new Date().toISOString().slice(0, 7),
      });
    } catch { /* log não bloqueia o pipeline */ }

    // confirmação instantânea no HUD (ideia do gateway Python do Gemini):
    // texto curto em MAIÚSCULAS, até ~5 palavras
    const hud_alert = savedItems.length > 0
      ? `SALVO: ${savedItems.map((i) => i.toUpperCase()).join(', ').slice(0, 40)}`
      : null;

    return json({
      ok: true,
      analyzed: true,
      latency_ms: latency,
      hud_alert,
      detections: {
        objects: savedObjects,
        people: savedPeople,
        skill_alerts: savedSkillAlerts,
      },
      raw: { objects: parsed.objects?.length ?? 0, people: parsed.people?.length ?? 0 },
    });
  } catch (e) {
    return json({ ok: false, error: String(e) }, 500);
  }
});