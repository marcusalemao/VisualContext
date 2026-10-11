/**
 * fcRecognize — endpoint de reconhecimento facial
 *
 * POST /fcRecognize
 * Body: { "descriptor": [128 floats] }   ← embedding gerado pelo face-api no cliente
 *   OU: { "image_b64": "..." }            ← JPEG base64 (APK Android)
 *
 * Retorna:
 *   { match: true,  person: { id, name, role, company, relation, relation_context, last_seen_date, last_seen_context, photo_url }, score: 94 }
 *   { match: false, score: 0 }
 *
 * Lógica:
 *   1. Busca todos os FaceContextPerson com face_embedding salvo
 *   2. Compara via distância euclidiana (mesmo algoritmo do face-api.js)
 *   3. Threshold: 0.44 (igual ao emulador)
 *
 * Nota sobre image_b64:
 *   O backend Deno não tem acesso a GPU/TFLite para extrair embedding de JPEG.
 *   Quando recebe image_b64, retorna { needs_client_embedding: true } para
 *   que o cliente (emulador/APK) extraia o descriptor e reenvie.
 *   No futuro: integrar com API externa de face recognition (ex: AWS Rekognition).
 */

import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

const THRESHOLD = 0.44;

function euclideanDistance(a: number[], b: number[]): number {
  if (a.length !== b.length) return 999;
  let sum = 0;
  for (let i = 0; i < a.length; i++) {
    const diff = a[i] - b[i];
    sum += diff * diff;
  }
  return Math.sqrt(sum);
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: CORS });
  }

  if (req.method !== "POST") {
    return Response.json({ error: "POST only" }, { status: 405, headers: CORS });
  }

  try {
    const body = await req.json().catch(() => ({}));

    // ── Modo image_b64 (APK) — extração de embedding não disponível no servidor ──
    if (body.image_b64 && !body.descriptor) {
      return Response.json(
        { needs_client_embedding: true, message: "Send face descriptor instead of raw JPEG" },
        { status: 200, headers: CORS }
      );
    }

    // ── Modo descriptor (emulador + APK futuro) ──
    const descriptor: number[] = body.descriptor;
    if (!descriptor || !Array.isArray(descriptor) || descriptor.length < 64) {
      return Response.json(
        { error: "descriptor required (array of 128 floats)" },
        { status: 400, headers: CORS }
      );
    }

    // Busca todos os perfis com embedding salvo
    const base44 = createClientFromRequest(req);
    const db = base44.asServiceRole.entities.FaceContextPerson;

    const all: any[] = [];
    let skip = 0;
    while (true) {
      const batch = await db.filter({}, { limit: 100, skip });
      if (!batch || batch.length === 0) break;
      all.push(...batch);
      if (batch.length < 100) break;
      skip += 100;
    }

    const withEmbedding = all.filter((p: any) => p.face_embedding && p.status !== "Pendente");

    if (withEmbedding.length === 0) {
      return Response.json(
        { match: false, score: 0, reason: "no_embeddings", total_profiles: all.length },
        { headers: CORS }
      );
    }

    // Compara descriptor recebido com cada embedding salvo
    let bestPerson: any = null;
    let bestDist = 999;

    for (const person of withEmbedding) {
      try {
        let saved: number[];
        // face_embedding pode ser string JSON ou array direto
        if (typeof person.face_embedding === "string") {
          saved = JSON.parse(person.face_embedding);
        } else {
          saved = person.face_embedding;
        }

        if (!Array.isArray(saved) || saved.length < 64) continue;

        const dist = euclideanDistance(descriptor, saved);
        if (dist < bestDist) {
          bestDist = dist;
          bestPerson = person;
        }
      } catch (e) {
        // embedding corrompido — ignora
        continue;
      }
    }

    if (bestDist <= THRESHOLD && bestPerson) {
      const score = Math.round((1 - bestDist) * 100);
      return Response.json({
        match: true,
        score,
        distance: parseFloat(bestDist.toFixed(4)),
        person: {
          id:                 bestPerson.id,
          name:               bestPerson.name,
          role:               bestPerson.role               || "",
          company:            bestPerson.company            || "",
          relation:           bestPerson.relation           || "",
          relation_context:   bestPerson.relation_context   || "",
          last_seen_date:     bestPerson.last_seen_date     || "",
          last_seen_context:  bestPerson.last_seen_context  || "",
          photo_url:          bestPerson.photo_url          || "",
          tags:               bestPerson.tags               || "",
          notes:              bestPerson.notes              || "",
        }
      }, { headers: CORS });
    }

    // Nenhum match acima do threshold
    return Response.json({
      match: false,
      score: bestDist < 999 ? Math.round((1 - bestDist) * 100) : 0,
      distance: parseFloat(bestDist.toFixed(4)),
      closest: bestPerson ? bestPerson.name : null,
    }, { headers: CORS });

  } catch (e: any) {
    return Response.json(
      { error: e.message },
      { status: 500, headers: CORS }
    );
  }
});
