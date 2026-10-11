// serveHUD — HUD Editor hospedado (FaceContext/VisualContext).
// O HTML vive em storage público; o wrapper busca uma vez e cacheia em memória.
// O HTML já embute o tokenizador x-vc-token (vcToken) logo após a linha do PROXY.

const HTML_URL = "https://base44.app/api/apps/6a11083db49430b410a8c066/files/mp/public/6a11083db49430b410a8c066/f9196c126_hud-editor.html";

let _html = "";

Deno.serve(async(req)=>{
  const h={"Access-Control-Allow-Origin":"*","Content-Type":"text/html; charset=utf-8","Cache-Control":"no-cache"};
  if(req.method==="OPTIONS")return new Response(null,{status:204,headers:h});
  try{
    if(!_html){
      const r=await fetch(HTML_URL);
      if(!r.ok)return new Response("erro ao carregar o HUD editor (storage HTTP "+r.status+")",{status:502,headers:{"Content-Type":"text/plain; charset=utf-8"}});
      _html=await r.text();
    }
    return new Response(_html,{status:200,headers:h});
  }catch(e:any){
    return new Response("erro ao carregar o HUD editor: "+(e?.message||e),{status:502,headers:{"Content-Type":"text/plain; charset=utf-8"}});
  }
});