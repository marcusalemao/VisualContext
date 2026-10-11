import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

/**
 * lcSyncToManager — Sincroniza registros do app do Beto (Superagent) para o
 * app "Live Companion" (FaceContext Manager, id 6aa30727d5a6019696c81dee).
 *
 * Chamado por workflows com gatilho de entidade (create/update/delete) e,
 * em modo backfill, replica registros pré-existentes.
 *
 * Mapeamentos (origem -> destino):
 *   FaceContextPerson    -> Person
 *   FaceContextEncounter -> Encounter
 *   EpisodicMemory       -> EpisodicMemory
 *   PersonInteraction    -> PersonInteraction
 *   DynamicSkill         -> DynamicSkill
 *   HUDNotification      -> HUDNotification
 *
 * O vínculo origem<->destino fica na entidade SyncMap do app do Beto.
 */

const MANAGER_APP = '6aa30727d5a6019696c81dee';
const API = `https://app.base44.com/api/apps/${MANAGER_APP}/entities`;

const ENTITY_MAP: Record<string, string> = {
  FaceContextPerson: 'Person',
  FaceContextEncounter: 'Encounter',
  EpisodicMemory: 'EpisodicMemory',
  PersonInteraction: 'PersonInteraction',
  DynamicSkill: 'DynamicSkill',
  HUDNotification: 'HUDNotification',
};

// Campos aceitos em cada entidade de destino (remove campos extras da origem)
const FIELDS: Record<string, string[]> = {
  Person: ['name', 'nickname', 'company', 'role', 'relation', 'relation_context', 'photo_url',
    'contact_whatsapp', 'contact_email', 'contact_phone', 'contact_linkedin',
    'last_seen_date', 'last_seen_context', 'notes', 'status', 'face_embedding'],
  Encounter: ['person_id', 'person_name', 'date', 'location', 'context',
    'speech_captured', 'ocr_captured', 'capture_mode', 'notes'],
  EpisodicMemory: ['item_name', 'category', 'location_description', 'audio_context',
    'coordinates_lat', 'coordinates_lng', 'snapshot_url', 'captured_at'],
  PersonInteraction: ['temp_identifier', 'assigned_name', 'visual_anchors',
    'conversation_summary', 'start_time', 'end_time'],
  DynamicSkill: ['skill_name', 'trigger_instruction', 'extraction_schema',
    'action_type', 'is_active'],
  HUDNotification: ['title', 'content_type', 'payload', 'displayed', 'created_at'],
};

function pick(data: Record<string, unknown> | null, fields: string[]): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  if (!data) return out;
  for (const f of fields) {
    if (data[f] !== undefined && data[f] !== null) out[f] = data[f];
  }
  return out;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

Deno.serve(async (req) => {
  const base44 = createClientFromRequest(req);
  const apiKey = Deno.env.get('BASE44_API_KEY') ?? '';
  const restHeaders: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(apiKey ? { Authorization: `Bearer ${apiKey}` } : {}),
  };

  try {
    const body = await req.json().catch(() => ({})) as {
      mode?: string;
      entity_name?: string;
      event_type?: string;
      entity_id?: string;
      data?: Record<string, unknown> | null;
    };

    // ---------- BACKFILL: replica registros pré-existentes ----------
    if (body.mode === 'backfill') {
      const report: Record<string, { existing: number; synced: number; failed: number }> = {};
      for (const [src, dst] of Object.entries(ENTITY_MAP)) {
        const maps = await base44.asServiceRole.entities.SyncMap.filter(
          { source_entity: src }, '-created_date', 500,
        );
        const syncedIds = new Set((maps ?? []).map((m: { source_id?: string }) => m.source_id));
        const rows = await base44.asServiceRole.entities[src].list('-created_date', 500);
        const rep = { existing: 0, synced: 0, failed: 0 };
        for (const row of rows ?? []) {
          if (syncedIds.has(row.id)) continue;
          rep.existing++;
          const payload = pick(row, FIELDS[dst]);
          if (Object.keys(payload).length === 0) continue;
          const r = await fetch(`${API}/${dst}`, {
            method: 'POST', headers: restHeaders, body: JSON.stringify(payload),
          });
          if (!r.ok) { rep.failed++; continue; }
          const created = await r.json() as { id: string };
          await base44.asServiceRole.entities.SyncMap.create({
            source_entity: src, source_id: row.id,
            target_app: MANAGER_APP, target_entity: dst, target_id: created.id,
          });
          rep.synced++;
        }
        report[src] = rep;
      }
      return json({ ok: true, mode: 'backfill', report });
    }

    // ---------- SYNC DE EVENTO (create/update/delete) ----------
    const { entity_name, event_type, entity_id, data } = body;
    if (!entity_name || !event_type || !entity_id) {
      return json({ ok: false, error: 'entity_name, event_type e entity_id são obrigatórios' }, 400);
    }
    const dst = ENTITY_MAP[entity_name];
    if (!dst) return json({ ok: true, skipped: true, reason: 'entidade fora do escopo do sync' });

    // Mapa origem -> destino
    const maps = await base44.asServiceRole.entities.SyncMap.filter(
      { source_entity: entity_name, source_id: entity_id }, '-created_date', 5,
    );
    let map = (maps ?? [])[0] as { id: string; target_id: string } | undefined;

    // Registro grande demais no trigger: busca pelo SDK
    let record = data;
    if (!record && event_type !== 'delete') {
      try {
        record = await base44.asServiceRole.entities[entity_name].get(entity_id);
      } catch { record = null; }
    }

    // CREATE
    if (event_type === 'create' && !map) {
      if (!record) return json({ ok: false, error: 'sem dados para sincronizar' }, 400);
      const payload = pick(record, FIELDS[dst]);
      if (entity_name === 'FaceContextEncounter' && payload.person_id) {
        const pm = await base44.asServiceRole.entities.SyncMap.filter(
          { source_entity: 'FaceContextPerson', source_id: String(payload.person_id) }, '-created_date', 5,
        );
        const link = (pm ?? [])[0] as { target_id?: string } | undefined;
        if (link?.target_id) payload.person_id = link.target_id;
      }
      const r = await fetch(`${API}/${dst}`, {
        method: 'POST', headers: restHeaders, body: JSON.stringify(payload),
      });
      if (!r.ok) return json({ ok: false, error: `create falhou: ${await r.text()}` }, 502);
      const created = await r.json() as { id: string };
      await base44.asServiceRole.entities.SyncMap.create({
        source_entity: entity_name, source_id: entity_id,
        target_app: MANAGER_APP, target_entity: dst, target_id: created.id,
      });
      return json({ ok: true, action: 'create', target_id: created.id });
    }

    // UPDATE (com ou sem mapa — sem mapa, trata como create)
    if (event_type === 'update') {
      if (!record) return json({ ok: false, error: 'sem dados para sincronizar' }, 400);
      const payload = pick(record, FIELDS[dst]);
      if (entity_name === 'FaceContextEncounter' && payload.person_id) {
        const pm = await base44.asServiceRole.entities.SyncMap.filter(
          { source_entity: 'FaceContextPerson', source_id: String(payload.person_id) }, '-created_date', 5,
        );
        const link = (pm ?? [])[0] as { target_id?: string } | undefined;
        if (link?.target_id) payload.person_id = link.target_id;
      }
      if (!map) {
        const r = await fetch(`${API}/${dst}`, {
          method: 'POST', headers: restHeaders, body: JSON.stringify(payload),
        });
        if (!r.ok) return json({ ok: false, error: `upsert-create falhou: ${await r.text()}` }, 502);
        const created = await r.json() as { id: string };
        await base44.asServiceRole.entities.SyncMap.create({
          source_entity: entity_name, source_id: entity_id,
          target_app: MANAGER_APP, target_entity: dst, target_id: created.id,
        });
        return json({ ok: true, action: 'create-from-update', target_id: created.id });
      }
      const r = await fetch(`${API}/${dst}/${map.target_id}`, {
        method: 'PUT', headers: restHeaders, body: JSON.stringify(payload),
      });
      if (!r.ok) return json({ ok: false, error: `update falhou: ${await r.text()}` }, 502);
      return json({ ok: true, action: 'update', target_id: map.target_id });
    }

    // DELETE
    if (event_type === 'delete') {
      if (!map) return json({ ok: true, action: 'noop', reason: 'sem mapa de sync' });
      const r = await fetch(`${API}/${dst}/${map.target_id}`, {
        method: 'DELETE', headers: restHeaders,
      });
      await base44.asServiceRole.entities.SyncMap.delete(map.id);
      if (!r.ok) return json({ ok: false, error: `delete falhou: ${await r.text()}` }, 502);
      return json({ ok: true, action: 'delete', target_id: map.target_id });
    }

    return json({ ok: true, action: 'noop', reason: `evento ${event_type} sem tratamento` });
  } catch (e) {
    return json({ ok: false, error: String(e) }, 500);
  }
});