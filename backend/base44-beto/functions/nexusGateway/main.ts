import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

/**
 * OpenAI-compatible SSE gateway for the Rokid Nexus "Assistant" plugin.
 *
 * Hard constraints discovered in testing (13/09/2026):
 *  - The app appends "/chat/completions" to the configured base URL;
 *    users configure the endpoint with a trailing "#" so the appended
 *    path lands inside the URL fragment and never reaches the server.
 *  - The app goes through the hub http_proxy, which BUFFERS the whole
 *    response: incremental SSE pings do NOT reach the app while it
 *    waits. Effectively the total request duration must stay well under
 *    the app's 120 s read timeout, ideally under ~60 s.
 *  - Turns run against a DEDICATED, LIGHT conversation (GatewayConfig,
 *    name == "nexus") so the agent is fast: heavy WhatsApp-context
 *    conversations make turns slow and the app times out.
 *  - If the turn exceeds MAX_TURN_WAIT_MS, we return a short fallback
 *    message instead of hanging; the real reply still lands in the
 *    dedicated conversation when the turn finishes.
 *
 * v5 (20/09/2026): camera snapshot capture. The request body is scanned
 * for image payloads in any shape (OpenAI image_url parts, data URLs,
 * bare base64 fields, image URLs). Every request logs an img-scan line
 * (diagnostics even when nothing is found). Found images are stashed in
 * the GatewayCapture entity and the agent message gets self-contained
 * OCR instructions so Beto can read text from the snapshot.
 *
 * v5.1 (20/09/2026): the endpoint now ACCEPTS PHOTOS directly, in three
 * shapes: (a) JSON with inline image parts, (b) multipart/form-data with
 * a file + optional text field, (c) raw binary bodies (Content-Type:
 * image/* or application/octet-stream). Photo-only requests get a
 * default OCR prompt. OCR instructions use python3 (jq is not
 * available in the agent sandbox).
 *
 * The API key pasted into the Nexus Custom provider is forwarded to the
 * Superagent API as the `api_key` header and never stored.
 */

const SUPERAGENT_BASE =
  'https://app.base44.com/api/agents/6a11083db49430b410a8c066';
const GATEWAY_NAME = 'nexus';
const MODEL_ID = 'beto';

const MAX_TURN_WAIT_MS = 15_000;
const PING_INTERVAL_MS = 5_000;
const FALLBACK_REPLY =
  'Recebi, mano! Te respondo no WhatsApp agora já 🕶️';

/**
 * Wire contract v2 envelope (WIRE_CONTRACT_GATEWAY_CORE.md):
 * additive fields so the downstream can become Core TS without changing
 * the Assistant side. Today they are logged and sent as headers; the
 * Base44 conversation API ignores them.
 */
const SCHEMA_VERSION = '1';
const CHANNEL = 'assistant';
const USER_REF = 'owner:marcus';

const CORS: Record<string, string> = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers':
    'Authorization, Content-Type, api_key, x-api-key',
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...CORS },
  });
}

/** SSE stream carrying a single error payload, OpenAI style. */
function sseError(message: string): Response {
  const body =
    `data: ${JSON.stringify({ error: { message } })}\n\n` +
    `data: [DONE]\n\n`;
  return new Response(body, {
    status: 200,
    headers: {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache',
      ...CORS,
    },
  });
}

const enc = new TextEncoder();
function dataLine(obj: unknown): Uint8Array {
  return enc.encode('data: ' + JSON.stringify(obj) + '\n\n');
}
function commentLine(text: string): Uint8Array {
  return enc.encode(': ' + text + '\n\n');
}
const doneLine = (): Uint8Array => enc.encode('data: [DONE]\n\n');

function chunk(
  id: string,
  created: number,
  model: string,
  delta: Record<string, unknown>,
  finishReason: string | null = null,
): unknown {
  return {
    id,
    object: 'chat.completion.chunk',
    created,
    model,
    choices: [{ index: 0, delta, finish_reason: finishReason }],
  };
}

/**
 * Streams the answer as OpenAI-style SSE chunks while sending comment
 * pings (ignored by the Nexus parser, but they keep direct clients
 * alive). Capped at maxWaitMs so the buffered proxy never stalls the
 * app past its 120 s timeout.
 */
function streamAnswer(
  task: Promise<{ ok: true; reply: string } | { ok: false; error: string }>,
  model: string,
  opts: { maxWaitMs?: number; pingMs?: number; fallback?: string } = {},
): Response {
  const {
    maxWaitMs = MAX_TURN_WAIT_MS,
    pingMs = PING_INTERVAL_MS,
    fallback = FALLBACK_REPLY,
  } = opts;
  const id = 'chatcmpl-beto-' + Date.now();
  const created = Math.floor(Date.now() / 1000);

  // Never let `task` reject unhandled after the stream stopped listening.
  const safe = task.then(
    (r) => r,
    (e) => ({ ok: false as const, error: e?.message || String(e) }),
  );

  const stream = new ReadableStream({
    async start(controller) {
      const startedAt = Date.now();
      // Opening chunk (role only) — establishes the stream immediately.
      controller.enqueue(dataLine(chunk(id, created, model, { role: 'assistant' })));

      for (;;) {
        const remaining = maxWaitMs - (Date.now() - startedAt);
        if (remaining <= 0) {
          controller.enqueue(
            dataLine(chunk(id, created, model, { role: 'assistant', content: fallback })),
          );
          controller.enqueue(dataLine(chunk(id, created, model, {}, 'stop')));
          controller.enqueue(doneLine());
          console.log('fallback returned after', Date.now() - startedAt, 'ms');
          break;
        }
        const won = await Promise.race([
          safe,
          new Promise((resolve) =>
            setTimeout(() => resolve(null), Math.min(pingMs, remaining)),
          ),
        ]);
        if (won) {
          const content = won.ok ? won.reply : 'Erro no gateway: ' + won.error;
          controller.enqueue(
            dataLine(chunk(id, created, model, { role: 'assistant', content })),
          );
          controller.enqueue(dataLine(chunk(id, created, model, {}, 'stop')));
          controller.enqueue(doneLine());
          break;
        }
        // Keep-alive ping: an SSE comment line the client ignores.
        controller.enqueue(commentLine('beto thinking'));
      }
      controller.close();
    },
  });

  return new Response(stream, {
    status: 200,
    headers: {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      'X-Accel-Buffering': 'no',
      ...CORS,
    },
  });
}

/** Extract the caller's API key from any of the usual places. */
function extractKey(req: Request, body: Record<string, unknown>): string | null {
  const h = req.headers;
  const auth = h.get('authorization');
  if (auth && auth.toLowerCase().startsWith('bearer ')) {
    return auth.slice(7).trim();
  }
  const direct = h.get('api_key') || h.get('x-api-key');
  if (direct) return direct.trim();
  const url = new URL(req.url);
  const qp = url.searchParams.get('api_key');
  if (qp) return qp.trim();
  if (typeof body.api_key === 'string' && body.api_key.trim()) {
    return body.api_key.trim();
  }
  return null;
}

/** Last user message text from an OpenAI-style messages array. */
function lastUserText(messages: unknown): string | null {
  if (!Array.isArray(messages)) return null;
  for (let i = messages.length - 1; i >= 0; i--) {
    const m = messages[i] as Record<string, unknown> | null;
    if (!m || m.role !== 'user') continue;
    const c = m.content;
    if (typeof c === 'string' && c.trim()) return c;
    if (Array.isArray(c)) {
      const text = c
        .filter((p) => p && typeof (p as Record<string, unknown>).text === 'string')
        .map((p) => (p as Record<string, unknown>).text as string)
        .join('\n');
      if (text.trim()) return text;
    }
  }
  return null;
}

/** ArrayBuffer -> base64 in chunks (no stack overflow on big payloads). */
function bufToB64(buf: ArrayBuffer): string {
  const bytes = new Uint8Array(buf);
  let bin = '';
  const CH = 0x8000;
  for (let i = 0; i < bytes.length; i += CH) {
    bin += String.fromCharCode(...bytes.subarray(i, i + CH));
  }
  return btoa(bin);
}

/** Sniff the image MIME type from magic bytes. */
function sniffMime(buf: ArrayBuffer): string {
  const b = new Uint8Array(buf);
  if (b[0] === 0x89 && b[1] === 0x50) return 'image/png';
  if (b[0] === 0xFF && b[1] === 0xD8) return 'image/jpeg';
  const head = new TextDecoder().decode(b.slice(0, 16));
  if (head.startsWith('RIFF')) return 'image/webp';
  if (head.startsWith('GIF8')) return 'image/gif';
  if (head.startsWith('<svg')) return 'image/svg+xml';
  return 'application/octet-stream';
}

/** Maximum base64 length we are willing to stash (~6.5 MB decoded). */
const MAX_STASH_B64 = 9_000_000;

interface FoundImage {
  path: string;
  mime: string | null;
  base64: string | null; // set for inline base64 payloads
  url: string | null;   // set for remote image URLs
}

const B64_RE = /^[A-Za-z0-9+/]{500,}={0,2}$/;
const DATAURL_RE = /^data:(image\/[a-zA-Z0-9.+-]+);base64,([A-Za-z0-9+/]+={0,2})$/;

/**
 * v5: Recursively walks the request JSON looking for camera snapshot
 * payloads in any shape: OpenAI-style image_url content parts, data
 * URLs, bare base64 fields with image-ish keys, or plain image URLs.
 * Diagnostic-first: the gateway logs what it sees even when nothing
 * is found, so we learn the Assistant's real wire format.
 */
function findImageData(
  node: unknown,
  path = '$',
  out: FoundImage[] = [],
  depth = 0,
): FoundImage[] {
  if (depth > 14 || node === null || typeof node !== 'object') return out;
  if (Array.isArray(node)) {
    node.forEach((item, i) => findImageData(item, `${path}[${i}]`, out, depth + 1));
    return out;
  }
  const obj = node as Record<string, unknown>;
  const t = typeof obj.type === 'string' ? obj.type : '';
  const iu = obj.image_url as Record<string, unknown> | undefined;
  if (t === 'image_url' && iu && typeof iu.url === 'string') {
    const m = iu.url.match(DATAURL_RE);
    out.push({
      path: `${path}.image_url.url`,
      mime: m ? m[1] : null,
      base64: m ? m[2] : null,
      url: m ? null : (iu.url.startsWith('http') ? iu.url : null),
    });
  }
  for (const [key, val] of Object.entries(obj)) {
    const p = `${path}.${key}`;
    if (typeof val === 'string') {
      const m = val.match(DATAURL_RE);
      if (m) {
        out.push({ path: p, mime: m[1], base64: m[2], url: null });
        continue;
      }
      if (/image|photo|snapshot|frame|picture|camera|jpeg|jpg|png/i.test(key)) {
        if (val.startsWith('http') && /\.(jpe?g|png|webp)|image|photo|snapshot/i.test(val)) {
          out.push({ path: p, mime: null, base64: null, url: val });
        } else if (B64_RE.test(val)) {
          out.push({ path: p, mime: null, base64: val, url: null });
        }
      }
    } else if (val && typeof val === 'object') {
      findImageData(val, p, out, depth + 1);
    }
  }
  return out;
}

/** Recursively find the LAST assistant-looking message in any JSON shape. */
function findAssistantReply(node: unknown, depth = 0): string | null {
  if (depth > 12 || node === null || typeof node !== 'object') return null;
  let found: string | null = null;
  if (Array.isArray(node)) {
    for (const item of node) {
      const r = findAssistantReply(item, depth + 1);
      if (r !== null) found = r;
    }
    return found;
  }
  const obj = node as Record<string, unknown>;
  const role = obj.role;
  const content = obj.content;
  const text = obj.text;
  const hasContent =
    (typeof content === 'string' && content.trim()) ||
    (typeof text === 'string' && text.trim());
  if (
    hasContent &&
    (role === 'assistant' ||
      role === 'ai' ||
      (role === undefined &&
        typeof obj.id === 'string' &&
        (obj.sender_type === 'ai' || obj.sender === 'assistant' || obj.sender_type === 'agent')))
  ) {
    found = typeof content === 'string' ? content : (text as string);
  }
  for (const key of Object.keys(obj)) {
    const r = findAssistantReply(obj[key], depth + 1);
    if (r !== null) found = r;
  }
  return found;
}

/** Recursively find a conversation id in any JSON shape. */
function findConversationId(node: unknown, depth = 0): string | null {
  if (depth > 8 || node === null || typeof node !== 'object') return null;
  if (Array.isArray(node)) {
    for (const item of node) {
      const r = findConversationId(item, depth + 1);
      if (r) return r;
    }
    return null;
  }
  const obj = node as Record<string, unknown>;
  for (const key of ['id', '_id', 'conversation_id']) {
    const v = obj[key];
    if (typeof v === 'string' && v.trim()) return v.trim();
  }
  for (const key of Object.keys(obj)) {
    const r = findConversationId(obj[key], depth + 1);
    if (r) return r;
  }
  return null;
}

/** Trim long replies so the glasses HUD (which has no scroll) can show them. */
function fitForDisplay(text: string): string {
  const MAX_CHARS = 300;
  if (text.length <= MAX_CHARS) return text;
  const cut = text.slice(0, MAX_CHARS);
  let end = -1;
  for (const stop of ['. ', '! ', '? ', '.\n', '!\n', '?\n', '\n']) {
    const i = cut.lastIndexOf(stop);
    if (i > end) end = i;
  }
  const trimmed = end > 120 ? cut.slice(0, end + 1) : cut.slice(0, cut.lastIndexOf(' ') > 60 ? cut.lastIndexOf(' ') : MAX_CHARS);
  return trimmed.trim() + ' …';
}

/** v2 envelope as headers — additive, ignored by the current downstream. */
function envelopeHeaders(requestId: string): Record<string, string> {
  return {
    'x-request-id': requestId,
    'x-schema-version': SCHEMA_VERSION,
    'x-channel': CHANNEL,
    'x-user-ref': USER_REF,
  };
}

async function superagentFetch(
  path: string,
  apiKey: string,
  init: { method: string; body?: unknown; headers?: Record<string, string> },
): Promise<{ ok: boolean; status: number; data: unknown; raw: string }> {
  const resp = await fetch(SUPERAGENT_BASE + path, {
    method: init.method,
    headers: {
      'api_key': apiKey,
      'Content-Type': 'application/json',
      ...(init.headers || {}),
    },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  });
  const raw = await resp.text();
  let data: unknown = null;
  try {
    data = raw ? JSON.parse(raw) : null;
  } catch {
    data = null;
  }
  return { ok: resp.ok, status: resp.status, data, raw };
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { status: 204, headers: CORS });
  }

  // OpenAI-compatible model listing (harmless if some client probes it).
  if (req.method === 'GET') {
    return json({
      object: 'list',
      data: [{ id: MODEL_ID, object: 'model', owned_by: 'beto' }],
    });
  }

  if (req.method !== 'POST') {
    return json({ error: { message: 'Use POST.' } }, 405);
  }

  // v5.1: accept photos in three shapes: JSON (OpenAI-style messages with
  // inline image parts), multipart/form-data (file + optional text field),
  // and raw binary bodies (Content-Type: image/* or octet-stream).
  const ct = (req.headers.get('content-type') || '').toLowerCase();

  let body: Record<string, unknown> = {};
  let formText: string | null = null;
  const binaryImages: FoundImage[] = [];

  if (ct.includes('multipart/form-data')) {
    try {
      const form = await req.formData();
      for (const [k, v] of form.entries()) {
        if (typeof v === 'string') {
          if (/^(text|message|prompt|question|q|input)$/i.test(k) && v.trim()) {
            formText = v;
          }
          continue;
        }
        const f = v as File;
        if (f && typeof f.arrayBuffer === 'function') {
          const buf = await f.arrayBuffer();
          if (buf.byteLength > 0) {
            binaryImages.push({
              path: '$form.' + k,
              mime: f.type || sniffMime(buf),
              base64: bufToB64(buf),
              url: null,
            });
          }
        }
      }
    } catch {
      return sseError('Invalid multipart body.');
    }
  } else if (ct.startsWith('image/') || ct === 'application/octet-stream') {
    const buf = await req.arrayBuffer();
    if (buf.byteLength === 0) return sseError('Empty image body.');
    binaryImages.push({
      path: '$raw',
      mime: ct.startsWith('image/') ? ct : sniffMime(buf),
      base64: bufToB64(buf),
      url: null,
    });
  } else {
    try {
      body = await req.json();
    } catch {
      return sseError('Invalid JSON body.');
    }
  }

  // Plain JSON ping for our own debugging.
  if (body.ping === true) {
    return json({ pong: true, gateway: 'nexusGateway', model: MODEL_ID, version: 'v5.1-oculos-cam' });
  }

  // Streaming/keep-alive self-test: pings for ~10 s, then a pong reply.
  if (body.pingStream === true) {
    return streamAnswer(
      (async () => {
        await new Promise((r) => setTimeout(r, 10_000));
        return { ok: true as const, reply: 'pong-stream: heartbeat e streaming funcionando.' };
      })(),
      MODEL_ID,
      { maxWaitMs: 20_000, pingMs: 2_000, fallback: 'ping-stream timeout' },
    );
  }

  // v2 envelope: generate the request_id here (the Assistant never sends one).
  const requestId = crypto.randomUUID();
  console.log('request_id', requestId, 'schema', SCHEMA_VERSION, 'channel', CHANNEL);

  const apiKey = extractKey(req, body);
  if (!apiKey) {
    return sseError(
      'Missing API key. Paste the Superagent API key into the Custom provider\'s API key field.',
    );
  }

  // v5.1: inline-image scan runs BEFORE the text check so a photo-only
  // request still triggers a full turn with a default OCR prompt.
  const images = [...binaryImages, ...findImageData(body)];

  const userText = lastUserText(body.messages) ||
    formText ||
    (images.length > 0 ? 'Leia o texto que tiver nessa imagem.' : null);
  if (!userText) {
    return sseError('No user message found in the request.');
  }

  const modelId = (typeof body.model === 'string' && body.model) || MODEL_ID;

  // The full agent turn: resolve/create the DEDICATED nexus conversation
  // (kept separate and light so turns are fast), send the message, wait
  // for Beto's reply. Runs while the SSE pings flow.
  const task = (async (): Promise<{ ok: true; reply: string } | { ok: false; error: string }> => {
    const turnStartedAt = Date.now();
    const base44 = createClientFromRequest(req);

    // v5: images were scanned up-front (v5.1) so photo-only requests work.
    const stash = images.filter((i) => i.base64);
    const urls = images.filter((i) => i.url).map((i) => i.url as string);
    console.log(
      'img-scan:',
      images.length === 0
        ? 'none'
        : `found ${images.length} (paths: ${images.slice(0, 3).map((i) => i.path).join(' | ')})`,
    );
    let captureNote = '';
    if (stash.length > 0) {
      const img = stash.sort((a, b) => (b.base64?.length ?? 0) - (a.base64?.length ?? 0))[0];
      const b64 = img.base64 ?? '';
      if (b64.length > MAX_STASH_B64) {
        console.log('img-scan: too large to stash:', b64.length, 'b64 chars');
      } else {
        try {
          const rec = await base44.asServiceRole.entities.GatewayCapture.create({
            request_id: requestId,
            mime: img.mime ?? 'image/jpeg',
            size_bytes: Math.floor((b64.length * 3) / 4),
            data_base64: b64,
            source_path: img.path,
            user_text: userText.slice(0, 500),
          });
          const cid = (rec as unknown as Record<string, unknown>).id as string;
          console.log('img-scan: stashed GatewayCapture', cid, 'from', img.path);
          captureNote = '\n\n📷 [gateway v5] A c\u00e2mera dos \u00f3culos mandou um snapshot junto com o pedido ' +
            '(entidade GatewayCapture, id ' + cid + '). Se o pedido for LER TEXTO da imagem: ' +
            'baixe e rode OCR em bash: ' +
            'curl -s -H "Authorization: Bearer $BASE44_SERVICE_TOKEN" ' +
            '"https://app.base44.com/api/apps/6a11083db49430b410a8c066/entities/GatewayCapture/' + cid + '" ' +
            '| python3 -c "import sys,json,base64; open(\u0027/tmp/cap.png\u0027,\u0027wb\u0027).write(base64.b64decode(json.load(sys.stdin)[\u0027data_base64\u0027]))" ' +
            '&& tesseract /tmp/cap.png - -l por+eng. ' +
            'Responda ao usu\u00e1rio o que ele pediu (curto, sem links, m\u00e1ximo 300 caracteres). ' +
            'Se tesseract n\u00e3o existir, tente instal\u00e1-lo (apt com espelhos https). ' +
            'Depois marque processed=true nessa entidade.';
        } catch (e) {
          console.log('img-scan: GatewayCapture stash failed:', e?.message || e);
        }
      }
    }
    if (captureNote === '' && urls.length > 0) {
      captureNote =
        '\n\n📷 [gateway v5] Snapshot em URL: ' + urls[0] +
        ' \u2014 baixe com curl e rode OCR (tesseract por+eng) se o pedido for ler texto.';
    }

    // 1) Resolve the dedicated Nexus conversation.
    let conversationId: string | null = null;
    try {
      const recs = await base44.asServiceRole.entities.GatewayConfig.list();
      const rec = (recs || []).find((r: Record<string, unknown>) =>
        r.name === GATEWAY_NAME
      );
      if (rec && typeof rec.conversation_id === 'string' && rec.conversation_id) {
        conversationId = rec.conversation_id;
      }
    } catch (e) {
      console.log('GatewayConfig list failed:', e?.message || e);
    }

    // 2) Create a fresh conversation if we don't have one yet.
    if (!conversationId) {
      const createResp = await superagentFetch('/conversations', apiKey, {
        method: 'POST',
        body: {},
        headers: envelopeHeaders(requestId),
      });
      if (!createResp.ok) {
        return {
          ok: false,
          error:
            'Could not create conversation on the Superagent API (HTTP ' +
            createResp.status + '): ' + createResp.raw.slice(0, 200),
        };
      }
      conversationId = findConversationId(createResp.data);
      if (!conversationId) {
        return { ok: false, error: 'Superagent API created a conversation but no id was found.' };
      }
      try {
        await base44.asServiceRole.entities.GatewayConfig.create({
          name: GATEWAY_NAME,
          conversation_id: conversationId,
          notes: 'Dedicated conversation for the Rokid Nexus Assistant plugin.',
        });
      } catch (e) {
        console.log('GatewayConfig create failed:', e?.message || e);
      }
      console.log('created fresh nexus conversation:', conversationId);
    }

    // 3) Send the user message; the Superagent API runs the full agent
    //    turn and returns Beto's reply when it completes.
    const sendResp = await superagentFetch(
      '/conversations/' + conversationId + '/messages',
      apiKey,
      {
        method: 'POST',
        body: { content: '🕶️ [óculos] ' + userText + captureNote },
        headers: envelopeHeaders(requestId),
      },
    );
    if (!sendResp.ok) {
      return {
        ok: false,
        error:
          'Superagent API rejected the message (HTTP ' + sendResp.status + '): ' +
          sendResp.raw.slice(0, 200),
      };
    }
    const rawReply = findAssistantReply(sendResp.data);
    console.log('agent turn took', Date.now() - turnStartedAt, 'ms');
    if (!rawReply) {
      return { ok: false, error: 'A resposta do agente não voltou a tempo.' };
    }
    return { ok: true, reply: fitForDisplay(rawReply) };
  })();

  return streamAnswer(task, modelId);
});