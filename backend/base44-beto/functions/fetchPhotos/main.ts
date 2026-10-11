import { createClient } from "npm:@base44/sdk@0.8.31";

async function md5hex(str: string): Promise<string> {
  try {
    const buf = await crypto.subtle.digest("MD5", new TextEncoder().encode(str));
    return Array.from(new Uint8Array(buf)).map((b: number) => b.toString(16).padStart(2, "0")).join("");
  } catch { return ""; }
}

async function urlExists(url: string): Promise<boolean> {
  try {
    const r = await fetch(url, { method: "HEAD", signal: AbortSignal.timeout(4000) });
    return r.ok;
  } catch { return false; }
}

async function findPhoto(name: string, email?: string): Promise<{ url: string; src: string }> {
  if (email) {
    try {
      const hash = await md5hex(email.trim().toLowerCase());
      if (hash) {
        const url = `https://www.gravatar.com/avatar/${hash}?d=404&s=200`;
        if (await urlExists(url)) return { url, src: "gravatar" };
      }
    } catch {}
  }

  if (email) {
    try {
      const domain = email.split("@")[1];
      if (domain && !["gmail.com","hotmail.com","yahoo.com","outlook.com","icloud.com"].includes(domain)) {
        const url = `https://logo.clearbit.com/${domain}?size=200`;
        if (await urlExists(url)) return { url, src: "clearbit" };
      }
    } catch {}
  }

  const nm = encodeURIComponent(name || "?");
  return {
    url: `https://ui-avatars.com/api/?name=${nm}&background=0a2a18&color=00ff88&size=200&bold=true&format=png`,
    src: "ui-avatars"
  };
}

Deno.serve(async (req: Request) => {
  const app = createClient({ appId: "6a11083db49430b410a8c066" });

  const persons = await app.asServiceRole.entities.FaceContextPerson.list();
  const sem = (persons as any[]).filter((p: any) => !p.photo_url || !p.photo_url.startsWith("http"));

  let updated = 0, errors = 0;
  const log: string[] = [];

  for (const p of sem as any[]) {
    try {
      const { url, src } = await findPhoto(p.name, p.contact_email || undefined);
      await app.asServiceRole.entities.FaceContextPerson.update(p.id, { photo_url: url });
      log.push(`✅ ${p.name} → ${src}`);
      updated++;
      await new Promise(r => setTimeout(r, 150));
    } catch (e: any) {
      errors++;
      log.push(`❌ ${p.name}: ${e.message}`);
    }
  }

  return Response.json({ total: (persons as any[]).length, sem_foto: sem.length, updated, errors, log });
});
