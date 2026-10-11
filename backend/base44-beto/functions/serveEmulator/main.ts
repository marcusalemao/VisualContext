Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: { "Access-Control-Allow-Origin": "*" } });
  }
  const upstream = await fetch(
    "https://base44.app/api/apps/6a11083db49430b410a8c066/files/mp/public/6a11083db49430b410a8c066/65e3c4ad1_index.html"
  );
  const html = await upstream.text();
  return new Response(html, {
    status: 200,
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Access-Control-Allow-Origin": "*",
      "Cache-Control": "no-cache",
      "Permissions-Policy": "camera=*, microphone=*",
    },
  });
});