// fcMessages — busca emails recentes de/para um contato pelo nome ou email
// GET ?name=João&email=joao@x.com&limit=5

import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });

  try {
    const base44 = createClientFromRequest(req);
    const url = new URL(req.url);
    const name  = url.searchParams.get("name") || "";
    const email = url.searchParams.get("email") || "";
    const limit = parseInt(url.searchParams.get("limit") || "8");

    if (!name && !email) {
      return Response.json({ error: "name or email required" }, { status: 400, headers: CORS });
    }

    // Usa o conector Gmail do Base44
    const gmailToken = await base44.connectors.gmail.getToken();

    // Monta query de busca Gmail
    const query = email
      ? `(from:${email} OR to:${email})`
      : `(from:"${name}" OR to:"${name}" OR subject:"${name}")`;

    const searchUrl = `https://gmail.googleapis.com/gmail/v1/users/me/messages?q=${encodeURIComponent(query)}&maxResults=${limit}`;
    const searchRes = await fetch(searchUrl, {
      headers: { Authorization: `Bearer ${gmailToken}` }
    });
    const searchData = await searchRes.json();
    const messages = searchData.messages || [];

    if (!messages.length) {
      return Response.json({ messages: [], source: "gmail" }, { headers: CORS });
    }

    // Busca detalhes de cada mensagem (apenas headers + snippet)
    const details = await Promise.all(
      messages.slice(0, limit).map(async (m: { id: string }) => {
        const mRes = await fetch(
          `https://gmail.googleapis.com/gmail/v1/users/me/messages/${m.id}?format=metadata&metadataHeaders=Subject&metadataHeaders=From&metadataHeaders=To&metadataHeaders=Date`,
          { headers: { Authorization: `Bearer ${gmailToken}` } }
        );
        const mData = await mRes.json();
        const headers: { name: string; value: string }[] = mData.payload?.headers || [];
        const get = (n: string) => headers.find(h => h.name === n)?.value || "";
        return {
          id: m.id,
          subject: get("Subject") || "(sem assunto)",
          from: get("From"),
          to: get("To"),
          date: get("Date"),
          snippet: mData.snippet || "",
          channel: "gmail",
        };
      })
    );

    return Response.json({ messages: details, source: "gmail" }, { headers: CORS });

  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err);
    return Response.json({ error: msg }, { status: 500, headers: CORS });
  }
});
