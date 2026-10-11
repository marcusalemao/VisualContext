/**
 * RokidLive — Módulo de Respostas por Voz / Q&A.
 *
 * POST { question? , item_name? , lat?, lng? }
 * Ex: "Onde deixei a minha chave?" → busca o último EpisodicMemory do item,
 * gera resposta falada curta e cria HUDNotification para o display óptico.
 */

const CORS: Record<string, string> = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, x-vc-token',
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...CORS },
  });
}

function normalize(s: string): string {
  return s.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
}

/** Extrai o nome do item da pergunta, casando com itens já registrados. */
function matchItem(question: string, itemNames: string[]): string | null {
  const q = normalize(question);
  // melhor correspondência por comprimento do nome
  let best: string | null = null;
  for (const name of itemNames) {
    const n = normalize(name);
    if (n.length >= 3 && q.includes(n)) {
      if (!best || n.length > normalize(best).length) best = name;
    }
  }
  return best;
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
    const question = String(body.question ?? '').slice(0, 300);
    let item_name = String(body.item_name ?? '').slice(0, 60);

    // catálogo de itens conhecidos
    const recent = await db.EpisodicMemory.list('-created_date', 100);
    const itemNames = [...new Set((recent ?? []).map((r: any) => r.item_name).filter(Boolean))];

    if (!item_name && question) item_name = matchItem(question, itemNames) ?? '';

    if (!item_name) {
      const answer = itemNames.length
        ? `Não achei esse item. Eu lembro de: ${itemNames.slice(0, 5).join(', ')}.`
        : 'Ainda não memorizei nenhum objeto hoje.';
      return json({ ok: true, answer, item: null, known_items: itemNames });
    }

    // busca o ÚLTIMO registro do item (filtro like + seleção do mais recente)
    const allMemories = await db.EpisodicMemory.list('-created_date', 200);
    const matches = (allMemories ?? []).filter((r: any) =>
      normalize(r.item_name).includes(normalize(item_name)));

    if (!matches || matches.length === 0) {
      const answer = `Não vi onde você deixou ${item_name} ainda. Ativa a câmera que eu memorizo.`;
      return json({ ok: true, answer, item: item_name, known_items: itemNames });
    }

    const m: any = matches[0];
    const time = m.captured_at ? new Date(m.captured_at).toLocaleString('pt-BR', {
      day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
    }) : '';

    const answer = `Você deixou ${m.item_name} ${m.location_description}. ${time ? `Por volta das ${time}.` : ''}`.trim();

    // notificação HUD (line_art_table: tabela blueprint do lembrete)
    const notif = await db.HUDNotification.create({
      title: `📍 ${m.item_name}`,
      content_type: 'line_art_table',
      payload: {
        rows: [
          ['item', m.item_name],
          ['local', m.location_description],
          ['hora', time],
        ],
        snapshot_url: m.snapshot_url ?? null,
      },
      displayed: false,
      created_at: new Date().toISOString(),
    });

    return json({
      ok: true,
      answer,
      item: m.item_name,
      memory: {
        location_description: m.location_description,
        captured_at: m.captured_at,
        lat: m.coordinates_lat ?? null,
        lng: m.coordinates_lng ?? null,
        snapshot_url: m.snapshot_url ?? null,
      },
      notification_id: notif?.id ?? null,
    });
  } catch (e) {
    return json({ ok: false, error: String(e) }, 500);
  }
});