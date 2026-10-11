Deno.serve(async (req) => {
  try {
    const { createClientFromRequest } = await import('npm:@base44/sdk@0.8.31');
    const base44 = createClientFromRequest(req);

    const body = await req.json().catch(() => ({}));
    const app_version = String(body.app_version ?? 'unknown').slice(0, 20);
    const device = String(body.device ?? 'unknown').slice(0, 120);
    const crash_log = String(body.crash_log ?? '').slice(0, 60000);

    if (!crash_log) {
      return Response.json({ ok: false, error: 'crash_log vazio' }, { status: 400 });
    }

    await base44.asServiceRole.entities.RokidCrashLog.create({
      app_version,
      device,
      crash_log,
    });

    return Response.json({ ok: true });
  } catch (e) {
    return Response.json({ ok: false, error: String(e) }, { status: 500 });
  }
});