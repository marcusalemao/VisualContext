// VisualContext — the one door the phone and the glasses use (replaces Base44's fcProxy).
// Auth: header x-vc-token (device token). Only its SHA-256 is stored, in public.devices.
// Same request shape as fcProxy ({ action, entity, ... }), so the apps switch by changing the address.
import { createClient } from "npm:@supabase/supabase-js@2.45.4";

const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type, x-vc-token",
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
async function listPeople(w: Who, limit = 1000) {
  const { data: people, error } = await mine(db.from("people").select("*"), w).order("updated_at", { ascending: false }).limit(limit);
  if (error) throw error;
  const ids = (people ?? []).map((p: any) => p.id);
  const { data: faces } = ids.length ? await db.from("faces").select("person_id, legacy").in("person_id", ids) : { data: [] };
  const legacy = new Map((faces ?? []).filter((f: any) => f.legacy).map((f: any) => [f.person_id, f.legacy]));
  const nFaces = new Map<string, number>();
  for (const f of faces ?? []) nFaces.set((f as any).person_id, (nFaces.get((f as any).person_id) ?? 0) + 1);
  return (people ?? []).map((p: any) => ({
    id: p.id, name: p.name, nickname: p.nickname, company: p.company, role: p.role, relation: p.relation,
    relation_context: p.relation_context, photo_url: p.photo_url, tags: p.tags, notes: p.notes,
    contact_email: p.contact_email, contact_phone: p.contact_phone, contact_whatsapp: p.contact_whatsapp, contact_linkedin: p.contact_linkedin,
    last_seen_date: p.last_seen_at, last_seen_context: p.last_seen_context, status: p.status, source: p.source,
    face_embedding: legacy.get(p.id) ?? null, faces: nFaces.get(p.id) ?? 0, created_at: p.created_at,
  }));
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "use POST" }, 405);
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
        if (!body.entity || /person/i.test(body.entity)) return json(await listPeople(w, Math.min(body.limit ?? 1000, 1000)));
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
        return json({ email, plan: acc?.plan, trial_ends_at: acc?.trial_ends_at, counts: { people: await count("people"), encounters: await count("encounters"), things: await count("things"), notes: await count("notes"), faces: await count("faces") } });
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
