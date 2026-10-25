#!/usr/bin/env python3
"""Glasscast panel: switches for glasscast.sh, in the browser.

Everything still runs through glasscast.sh; this only starts and stops its
commands. Python standard library only, nothing to install.
Opens http://127.0.0.1:8765 (this Mac only; nothing is reachable from the network).

    ./glasscast.sh ui            or double-click "Glasscast.command"
"""
import json
import os
import signal
import subprocess
import sys
import threading
import time
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.path.join(HERE, "glasscast.sh")
DIR = os.environ.get("GLASSCAST_DIR", HERE)
PORT = int(os.environ.get("GLASSCAST_PORT", "8765"))

# Each switch is one glasscast.sh command that keeps running until switched off.
SWITCHES = [
    # id, group, label, help, command
    ("lens", "glasses", "Lente (verde)", "A tela como a lente mostra: só verde, em intensidades", ["lens"]),
    ("watch", "glasses", "Capturas para o Claude", "Um print do HUD a cada 3 s em watch/latest.png", ["watch", "3"]),
    ("camera", "glasses", "Câmera dos óculos", "O que você está olhando", ["camera"]),
    ("record", "glasses", "Gravar o HUD", "Vídeo em videos/ até desligar. Não usa a câmera", ["record", "teste"]),
    ("fotos", "glasses", "Linha do tempo das fotos", "Hora em que cada foto nasce nos óculos e chega ao celular (vai junto no log do Zap)", ["fotos"]),
    ("phone", "phone", "Celular na tela", "Espelha o celular", ["phone"]),
    ("phonewatch", "phone", "Capturas do celular", "Um print a cada 5 s em watch/phone-latest.png", ["phone-watch", "5"]),
    ("log", "phone", "Log do Zap", "logs/zap-latest.log, para o Claude ler", ["log"]),
]
running = {}  # id -> Popen
lock = threading.Lock()


def run_script(*args, timeout=20):
    try:
        out = subprocess.run(["bash", SCRIPT, *args], capture_output=True, text=True, timeout=timeout,
                             env={**os.environ, "GLASSCAST_DIR": DIR})
        return (out.stdout + out.stderr).strip()
    except subprocess.TimeoutExpired:
        return "demorou demais"


def alive(sid):
    p = running.get(sid)
    return p is not None and p.poll() is None


def start(sid):
    cmd = next(s[4] for s in SWITCHES if s[0] == sid)
    if alive(sid):
        return
    log = open(os.path.join(DIR, "logs", f"panel-{sid}.txt"), "w")
    # Own process group, so switching off also stops adb/ffplay/scrcpy it started.
    running[sid] = subprocess.Popen(["bash", SCRIPT, *cmd], stdout=log, stderr=subprocess.STDOUT,
                                    stdin=subprocess.DEVNULL, start_new_session=True,
                                    env={**os.environ, "GLASSCAST_DIR": DIR})


def stop(sid):
    p = running.pop(sid, None)
    if p and p.poll() is None:
        try:
            os.killpg(p.pid, signal.SIGINT)  # lets "record" finish the file
            p.wait(timeout=4)
        except Exception:
            try:
                os.killpg(p.pid, signal.SIGKILL)
            except Exception:
                pass


devices_cache = {"glasses": ["none", ""], "phone": ["none", ""], "unauthorized": False, "checking": True}


def check_devices():
    # adb can hang on a loose cable; that must never freeze the page, so this runs on its own.
    while True:
        lines = run_script("status", timeout=10).splitlines()
        dev = {"glasses": ["none", ""], "phone": ["none", ""], "unauthorized": False, "checking": False}
        for line in lines:
            parts = line.split()
            if len(parts) >= 2 and parts[0] in ("glasses", "phone"):
                dev[parts[0]] = [parts[1], parts[2] if len(parts) > 2 else ""]
            elif parts and parts[0] == "unauthorized":
                dev["unauthorized"] = True
        devices_cache.clear()
        devices_cache.update(dev)
        time.sleep(3)


def status():
    dev = dict(devices_cache)
    lines = []
    for line in lines:
        parts = line.split()
        if len(parts) >= 2 and parts[0] in ("glasses", "phone"):
            dev[parts[0]] = [parts[1], parts[2] if len(parts) > 2 else ""]
        elif parts and parts[0] == "unauthorized":
            dev["unauthorized"] = True
    switches = []
    for sid, group, label, hint, _ in SWITCHES:
        last = ""
        p = running.get(sid)
        failed = p is not None and p.poll() not in (None, 0)
        # The last line only while running, or when it stopped by itself with an error.
        if alive(sid) or failed:
            try:
                with open(os.path.join(DIR, "logs", f"panel-{sid}.txt")) as f:
                    tail = [l.strip() for l in f.read().splitlines() if l.strip()]
                    last = tail[-1][-160:] if tail else ""
            except OSError:
                pass
        switches.append({"id": sid, "group": group, "label": label, "hint": hint,
                         "on": alive(sid), "failed": failed, "last": last})
    return {"devices": dev, "switches": switches, "folder": DIR}


PAGE = r"""<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Glasscast</title>
<style>
:root{--bg:#0b0f0c;--card:#121a15;--line:#1f2b23;--text:#e6f2ea;--dim:#8fa597;--green:#4dff8c;--warn:#ffb454;--bad:#ff6b6b}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:15px/1.4 -apple-system,BlinkMacSystemFont,"Helvetica Neue",sans-serif}
main{max-width:760px;margin:0 auto;padding:24px 16px 48px}
h1{font-size:22px;margin:0 0 4px}.sub{color:var(--dim);margin:0 0 20px}
.devices{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:20px}
.dev{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:14px}
.dev b{display:block;font-size:13px;color:var(--dim);font-weight:500;margin-bottom:4px}
.st{font-size:16px}.st.usb{color:var(--green)}.st.wifi{color:var(--warn)}.st.none{color:var(--bad)}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:20px}
h2{font-size:13px;letter-spacing:.06em;text-transform:uppercase;color:var(--dim);margin:0 0 8px}
.sw{display:flex;gap:12px;align-items:flex-start;background:var(--card);border:1px solid var(--line);border-radius:12px;padding:12px;margin-bottom:8px;cursor:pointer}
.sw .t{flex:1}.sw .h{color:var(--dim);font-size:13px}.sw.fail .l{color:var(--bad)}.sw .l{color:var(--dim);font:11px ui-monospace,monospace;margin-top:4px;word-break:break-all}
.tg{width:42px;height:24px;border-radius:12px;background:#2a3830;position:relative;flex:none;margin-top:2px;transition:.15s}
.tg:after{content:"";position:absolute;top:3px;left:3px;width:18px;height:18px;border-radius:50%;background:#cfe;transition:.15s}
.sw.on .tg{background:var(--green)}.sw.on .tg:after{left:21px;background:#0b0f0c}
.row{display:flex;gap:8px;flex-wrap:wrap;margin-top:8px}
button,input{font:inherit;border-radius:10px;border:1px solid var(--line);background:var(--card);color:var(--text);padding:9px 12px}
button{cursor:pointer}button.primary{background:var(--green);color:#0b0f0c;border-color:var(--green);font-weight:600}
input{flex:1;min-width:200px}.msg{color:var(--dim);font-size:13px;margin-top:8px;white-space:pre-wrap}
.box{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:14px;margin-top:20px}
.foot{color:var(--dim);font-size:12px;margin-top:20px;word-break:break-all}
@media(max-width:640px){.grid,.devices{grid-template-columns:1fr}}
</style></head><body><main>
<h1>Glasscast</h1><p class="sub">Óculos e celular no Mac, pelo cabo ou pelo Wi-Fi. Só para desenvolvimento.</p>
<div class="devices">
  <div class="dev"><b>Óculos</b><div id="g" class="st none">procurando…</div></div>
  <div class="dev"><b>Celular</b><div id="p" class="st none">procurando…</div></div>
</div>
<div class="grid">
  <section><h2>Óculos</h2><div id="glasses"></div>
    <div class="row"><button onclick="act('snap')">Print do HUD agora</button></div></section>
  <section><h2>Celular</h2><div id="phone"></div>
    <div class="row"><button onclick="act('phone-snap')">Print do celular agora</button></div></section>
</div>
<div class="box"><h2>Óculos pelo Wi-Fi (sem cabo)</h2>
  <div class="row"><button onclick="act('find')">Procurar óculos</button></div>
  <div class="row"><input id="addr" placeholder="O que o Wireless ADB mostra: 192.168.0.31:40701 969774">
  <button class="primary" onclick="wifi()">Conectar</button></div>
  <div id="msg" class="msg"></div></div>
<div class="foot" id="folder"></div>
</main><script>
const label={usb:"pelo cabo ✓",wifi:"pelo Wi-Fi",none:"não encontrado"};
async function refresh(){
  try{const s=await (await fetch("/status")).json();
    for(const k of ["g","p"]){const d=s.devices[k=="g"?"glasses":"phone"];const el=document.getElementById(k);
      el.className="st "+d[0];el.textContent=(s.devices.checking?"procurando…":label[d[0]])+(d[1]?"  ·  "+d[1]:"");}
    if(s.devices.unauthorized){const p=document.getElementById("p");p.className="st wifi";p.textContent="aceite \"Permitir depuração USB?\" no celular";}
    for(const g of ["glasses","phone"]){const box=document.getElementById(g);box.innerHTML="";
      s.switches.filter(w=>w.group==g).forEach(w=>{const d=document.createElement("div");d.className="sw"+(w.on?" on":"")+(w.failed?" fail":"");d.dataset.id=w.id;
        d.innerHTML=`<div class="tg"></div><div class="t"><div>${w.label}</div><div class="h">${w.hint}</div>${w.last?`<div class="l"></div>`:""}</div>`;
        if(w.last)d.querySelector(".l").textContent=w.last;
        d.onclick=()=>toggle(w.id,!w.on);box.appendChild(d);});}
    document.getElementById("folder").textContent="Pasta: "+s.folder;
  }catch(e){document.getElementById("g").textContent="o painel parou: rode de novo o Glasscast.command";}
}
async function toggle(id,on){document.querySelectorAll(".sw").forEach(el=>{if(el.dataset.id==id)el.classList.toggle("on",on)});
  await fetch("/switch",{method:"POST",body:JSON.stringify({id,on})});setTimeout(refresh,700);}
async function act(cmd,args=[]){const m=document.getElementById("msg");m.textContent="…";
  const r=await (await fetch("/run",{method:"POST",body:JSON.stringify({cmd,args})})).json();m.textContent=r.out||"ok";refresh();}
function wifi(){const v=document.getElementById("addr").value.trim().replace(/^adb (pair|connect) /,"").split(/\s+/);
  if(!v[0])return; act(v[1]?"pair":"connect",v[1]?[v[0],v[1]]:[v[0]]);}
refresh();setInterval(refresh,3000);
</script></body></html>"""

# Only these one-shot commands can be run from the page, never anything else.
ONE_SHOT = {"snap", "phone-snap", "find", "pair", "connect"}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def send(self, code, body, kind="application/json"):
        data = body.encode() if isinstance(body, str) else body
        self.send_response(code)
        self.send_header("Content-Type", kind + "; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def body(self):
        n = int(self.headers.get("Content-Length") or 0)
        try:
            return json.loads(self.rfile.read(n) or b"{}")
        except ValueError:
            return {}

    def same_origin(self):
        # A web page elsewhere can't drive this panel: requests must come from it.
        origin = self.headers.get("Origin")
        return origin in (None, f"http://127.0.0.1:{PORT}", f"http://localhost:{PORT}")

    def do_GET(self):
        if self.path == "/":
            self.send(200, PAGE, "text/html")
        elif self.path == "/status":
            self.send(200, json.dumps(status()))
        else:
            self.send(404, "{}")

    def do_POST(self):
        if not self.same_origin():
            return self.send(403, "{}")
        b = self.body()
        if self.path == "/switch":
            sid = b.get("id")
            if sid not in {s[0] for s in SWITCHES}:
                return self.send(400, "{}")
            with lock:
                start(sid) if b.get("on") else stop(sid)
            self.send(200, "{}")
        elif self.path == "/run":
            cmd, args = b.get("cmd"), [str(a) for a in (b.get("args") or [])][:2]
            if cmd not in ONE_SHOT:
                return self.send(400, "{}")
            out = run_script(cmd, *args, timeout=40)
            self.send(200, json.dumps({"out": out[-800:]}))
        else:
            self.send(404, "{}")


def main():
    for sub in ("logs", "watch", "shots", "videos"):
        os.makedirs(os.path.join(DIR, sub), exist_ok=True)
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    url = f"http://127.0.0.1:{PORT}"
    print(f"Glasscast: painel em {url}")
    print("Para fechar: clique nesta janela e aperte Control + C (desliga tudo).")

    def bye(*_):
        for sid in list(running):
            stop(sid)
        os._exit(0)

    signal.signal(signal.SIGINT, bye)
    signal.signal(signal.SIGTERM, bye)
    threading.Thread(target=check_devices, daemon=True).start()
    threading.Timer(0.8, lambda: webbrowser.open(url)).start()
    server.serve_forever()


if __name__ == "__main__":
    main()
