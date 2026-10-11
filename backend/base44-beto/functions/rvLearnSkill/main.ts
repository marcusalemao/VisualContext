/**
 * RokidLive — DynamicSkill Engine (v1.1 — auth).
 *
 * POST { instruction }
 * Comando de voz "Aprenda a [instrução]" → a IA analisa, cria entrada em
 * DynamicSkill com o schema de extração e a ação associada.
 *
 * Também suporta:
 * POST { action: "list" }                      → lista skills
 * POST { action: "toggle", id, is_active }     → ativa/desativa
 * POST { action: "delete", id }                → remove skill
 *
 * v1.1 — Auth: x-vc-token (óculos/emulador) OU usuário Base44 logado. 401 caso contrário.
 */

const CORS: Record<string, string> = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, api_key, x-api-key, x-vc-token',
};

const MODEL = 'gemini-2.5-flash';

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...CORS },
  });
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS });

  try {
    const { createClientFromRequest } = await import('npm:@base44/sdk@0.8.31');
    const base44 = createClientFromRequest(req);
    // ── Auth: x-vc-token (óculos/emulador) OU usuário Base44 logado (Manager) ──
    const DEVICE_TOKEN = Deno.env.get('VC_DEVICE_TOKEN') || '';
    const token = req.headers.get('x-vc-token') || '';
    let authorized = !!(DEVICE_TOKEN && token === DEVICE_TOKEN);
    if (!authorized) {
      try { await base44.entities.EpisodicMemory.list('-created_date', 1, 0); authorized = true; } catch (_) {}
    }
    if (!authorized) return Response.json({ error: 'unauthorized' }, { status: 401, headers: CORS });

    const db = base44.asServiceRole.entities;

    const body = await req.json().catch(() => ({}));
    const action = String(body.action ?? 'learn');

    // ---- Gestão (dashboard) ----
    if (action === 'list') {
      const skills = await db.DynamicSkill.list('-created_date', 100);
      return json({ ok: true, skills });
    }
    if (action === 'toggle' && body.id) {
      await db.DynamicSkill.update(body.id, { is_active: Boolean(body.is_active) });
      return json({ ok: true });
    }
    if (action === 'delete' && body.id) {
      await db.DynamicSkill.delete(body.id);
      return json({ ok: true });
    }

    // ---- Aprender nova skill ----
    const instruction = String(body.instruction ?? '').slice(0, 1000);
    if (!instruction) return json({ ok: false, error: 'instruction obrigatória' }, 400);

    const apiKey = Deno.env.get('GEMINI_API_KEY') ?? '';
    if (!apiKey) return json({ ok: false, error: 'GEMINI_API_KEY ausente' }, 500);

    const prompt = `O usuário de um óculos AR pediu para APRENDER uma nova regra de detecção visual:
"${instruction}"

Converta isso em uma skill estruturada. Responda SEMPRE em JSON:
{
  "skill_name": "nome_curto_snake_case",
  "trigger_instruction": "instrução de detecção clara para o modelo de visão (o que deve ativar a regra na cena)",
  "extraction_schema": { "campo1": "o que extrair", "campo2": "..." },
  "action_type": "salvar_nota | alerta_hud | adicionar_tarefa"
}`;

    const gRes = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-goog-api-key': apiKey },
        body: JSON.stringify({
          contents: [{ role: 'user', parts: [{ text: prompt }] }],
          generationConfig: { responseMimeType: 'application/json', temperature: 0.2 },
        }),
      },
    );
    if (!gRes.ok) {
      return json({ ok: false, error: 'gemini_error', detail: (await gRes.text()).slice(0, 300) }, 502);
    }
    const gData = await gRes.json();
    let parsed: any;
    try {
      parsed = JSON.parse(gData?.candidates?.[0]?.content?.parts?.[0]?.text ?? '{}');
    } catch {
      return json({ ok: false, error: 'resposta Gemini inválida' }, 502);
    }

    if (!parsed?.trigger_instruction) {
      return json({ ok: false, error: 'não foi possível estruturar a skill' }, 422);
    }

    const skill = await db.DynamicSkill.create({
      skill_name: String(parsed.skill_name ?? 'skill').slice(0, 80),
      trigger_instruction: String(parsed.trigger_instruction).slice(0, 1000),
      extraction_schema: parsed.extraction_schema ?? {},
      action_type: String(parsed.action_type ?? 'alerta_hud').slice(0, 60),
      is_active: true,
    });

    // custo (text-only, barato)
    const inTok = gData?.usageMetadata?.promptTokenCount ?? 0;
    const outTok = gData?.usageMetadata?.candidatesTokenCount ?? 0;
    const cost_usd = (inTok / 1e6) * 0.30 + (outTok / 1e6) * 2.50;
    try {
      await db.AIUsageLog.create({
        action: 'rvLearnSkill',
        model: MODEL,
        input_tokens: inTok,
        output_tokens: outTok,
        cost_usd,
        cost_brl: cost_usd * 5.4,
        success: true,
        month_key: new Date().toISOString().slice(0, 7),
      });
    } catch { /* não bloqueia */ }

    return json({
      ok: true,
      learned: true,
      skill,
      confirmation: `Anotado! Quando eu ver ${String(parsed.trigger_instruction).slice(0, 80)}... eu te aviso.`,
    });
  } catch (e) {
    return json({ ok: false, error: String(e) }, 500);
  }
});