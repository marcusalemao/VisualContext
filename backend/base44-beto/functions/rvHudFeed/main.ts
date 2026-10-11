/**
 * RokidLive — Feed do HUD.
 *
 * POST { action: "poll", limit? }   → devolve notificações pendentes (displayed=false)
 *                                     e as marca como exibidas.
 * POST { action: "history", limit? } → histórico sem marcar.
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
    const action = String(body.action ?? 'poll');
    const limit = Math.min(Number(body.limit ?? 10), 50);

    if (action === 'history') {
      const items = await db.HUDNotification.list('-created_date', limit);
      return json({ ok: true, notifications: items ?? [] });
    }

    // poll: pendentes
    const recentN = await db.HUDNotification.list('-created_date', Math.max(limit * 5, 50));
    const pending = (recentN ?? []).filter((n: any) => n.displayed === false).slice(0, limit);

    const notifications = pending ?? [];
    for (const n of notifications) {
      await db.HUDNotification.update(n.id, { displayed: true });
    }

    return json({
      ok: true,
      count: notifications.length,
      notifications: notifications.map((n: any) => ({
        id: n.id,
        title: n.title,
        content_type: n.content_type,
        payload: n.payload,
        created_at: n.created_at,
      })),
    });
  } catch (e) {
    return json({ ok: false, error: String(e) }, 500);
  }
});