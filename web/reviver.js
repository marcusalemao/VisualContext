/* VisualContext Manager — "Reviver o dia" (Marcus, 09/11; idea from Mindomo's presentation mode).
 * A day told in chapters, like stories: bars on top, one stop per screen (where, when, who, what was said, what was
 * kept), tap right / left to move, hold to pause, swipe down or X or the phone's Back to leave. Built only from what
 * is recorded; nothing is invented. Uses the Manager's globals: S, esc, isMe, nameKey, said, myPlaceAt, sameDay.
 */
(function () {
  const STEP_MS = 7000;
  const tm = d => new Date(d).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });
  const dayName = d => { const t = new Date(d), n = new Date(), y = new Date(n); y.setDate(y.getDate() - 1);
    return sameDay(t, n) ? "Hoje" : sameDay(t, y) ? "Ontem" : t.toLocaleDateString("pt-BR", { weekday: "long", day: "numeric", month: "long" }); };
  const dist = (a, b) => { if (!a || !b || a.lat == null || b.lat == null) return Infinity; const R = 6371000, p = Math.PI / 180, dl = (b.lat - a.lat) * p, dg = (b.lng - a.lng) * p,
    x = Math.sin(dl / 2) ** 2 + Math.cos(a.lat * p) * Math.cos(b.lat * p) * Math.sin(dg / 2) ** 2; return 2 * R * Math.asin(Math.sqrt(x)); };
  const placeOf = e => { const m = myPlaceAt && myPlaceAt(e.lat, e.lng); return m ? m.name : (e.place || "").split(" · ")[0] || "—"; };
  const face = p => p && (p.photo_url || p.thumb) ? `<img src="${esc(p.photo_url || (String(p.thumb).startsWith("data:") ? p.thumb : "data:image/jpeg;base64," + p.thumb))}" alt="">` : `<span>${esc((p && p.name || "?").slice(0, 1))}</span>`;

  /** The chapters of one day: an opening, one per stop (place + time span), the objects kept, the songs, a closing. */
  function chapters(day) {
    const on = d => d && sameDay(new Date(d), day);
    const enc = S.enc.filter(e => on(e.at) && !isMe(e.person_name)).sort((a, b) => new Date(a.at) - new Date(b.at));
    const things = S.things.filter(t => on(t.seen_at)).sort((a, b) => new Date(a.seen_at) - new Date(b.seen_at));
    const notes = S.notes.filter(n => on(n.created_at || n.at));
    const songs = notes.filter(n => n.kind === "musica"), papers = notes.filter(n => n.kind && n.kind !== "musica" && n.kind !== "ouvir_depois"), voice = notes.filter(n => !n.kind);
    if (!enc.length && !things.length && !notes.length) return [];
    const stops = [];
    for (const e of enc) {
      const key = placeOf(e), last = stops.at(-1);
      if (last && (last.key === key || dist(last, e) < 150)) { last.to = e.at; last.items.push(e); }
      else stops.push({ key, from: e.at, to: e.at, lat: e.lat, lng: e.lng, items: [e] });
    }
    for (let i = 1; i < stops.length; i++) {
      const a = stops[i - 1], b = stops[i], d = dist(a, b), h = (new Date(b.from) - new Date(a.to)) / 3.6e6;
      b.how = d === Infinity || d < 250 || h <= 0 ? null : (d / 1000 / h > 11 ? "🚗 de carro" : "🚶 a pé") + " · " + (d < 950 ? Math.round(d / 50) * 50 + " m" : (d / 1000).toFixed(1) + " km");
    }
    const people = [...new Set(enc.map(e => e.person_name).filter(Boolean))];
    const out = [];
    out.push({ kind: "open", html: `<div class="rv-k">✦ Reviver</div><h2>${esc(dayName(day))}</h2>
      <p class="rv-big">${stops.length} ${stops.length === 1 ? "lugar" : "lugares"} · ${people.length} ${people.length === 1 ? "pessoa" : "pessoas"}${things.length ? " · " + things.length + (things.length === 1 ? " objeto" : " objetos") : ""}${songs.length ? " · " + songs.length + (songs.length === 1 ? " música" : " músicas") : ""}</p>
      <p class="rv-dim">das ${esc(tm((enc[0] || things[0] || notes[0]).at || (things[0] || {}).seen_at || (notes[0] || {}).created_at))} às ${esc(tm((enc.at(-1) || things.at(-1) || notes.at(-1)).at || (things.at(-1) || {}).seen_at || (notes.at(-1) || {}).created_at))}</p>` });
    stops.forEach((s, i) => {
      const who = [...new Set(s.items.map(e => e.person_name).filter(Boolean))];
      const live = s.items.filter(e => e.capture_mode === "live");
      const lines = s.items.map(e => said(e)).filter(Boolean).slice(0, 3);
      const faces = who.map(n => { const p = S.people.find(x => nameKey(x.name) === nameKey(n)); return `<div class="rv-face">${face(p || { name: n })}<b>${esc(n.split(" ")[0])}</b></div>`; }).join("");
      out.push({ kind: "stop", lat: s.lat, lng: s.lng, html: `${s.how ? `<div class="rv-how">${esc(s.how)}</div>` : ""}<div class="rv-k">${i + 1} de ${stops.length} · ${esc(tm(s.from))}${s.to !== s.from ? "–" + esc(tm(s.to)) : ""}</div>
        <h2>⌖ ${esc(s.key)}</h2>
        ${faces ? `<div class="rv-faces">${faces}</div>` : live.length ? `<p class="rv-dim">✦ conversa com o assistente</p>` : ""}
        ${lines.length ? `<div class="rv-said">${lines.map(l => `<p>“${esc(l)}”</p>`).join("")}</div>` : ""}
        ${s.lat ? `<a class="rv-link" target="_blank" rel="noopener" href="https://www.google.com/maps/search/?api=1&query=${s.lat},${s.lng}">abrir no mapa ↗</a>` : ""}` });
    });
    if (things.length) out.push({ kind: "things", html: `<div class="rv-k">Onde você deixou as coisas</div><h2>◈ Objetos</h2>
      <div class="rv-things">${things.map(t => `<div class="rv-thing">${t.snapshot_url ? `<img src="${esc(t.snapshot_url)}" alt="">` : ""}<div><b>${esc(t.name)}</b><span>${esc((t.location_description || "").split(" · ")[0])} · ${esc(tm(t.seen_at))}</span></div></div>`).join("")}</div>` });
    if (songs.length || papers.length || voice.length) out.push({ kind: "notes", html: `<div class="rv-k">O que ficou guardado</div><h2>${songs.length ? "♪ Trilha e notas" : "▤ Notas"}</h2>
      <div class="rv-said">${songs.map(n => `<p>♪ ${esc((n.title || "").split(" · ")[0])}</p>`).join("")}${papers.map(n => `<p>▤ ${esc(n.title || n.kind)}</p>`).join("")}${voice.slice(0, 4).map(n => `<p>✎ ${esc(String(n.text).slice(0, 90))}</p>`).join("")}</div>` });
    out.push({ kind: "end", html: `<div class="rv-k">Fim</div><h2>${esc(dayName(day))}</h2><p class="rv-dim">Toque para rever ou feche.</p>
      <div class="rv-end"><button data-rv="again">↺ Rever</button><button data-rv="close">Fechar</button></div>` });
    return out;
  }

  let el = null, idx = 0, list = [], timer = null, startAt = 0, left = STEP_MS, paused = false;
  function paint() {
    const c = list[idx];
    el.querySelector(".rv-bars").innerHTML = list.map((_, i) => `<i><b style="width:${i < idx ? 100 : 0}%"></b></i>`).join("");
    el.querySelector(".rv-card").innerHTML = c.html;
    el.querySelector(".rv-card").setAttribute("aria-label", "capítulo " + (idx + 1) + " de " + list.length);
    run(STEP_MS);
  }
  function run(ms) {
    clearTimeout(timer); left = ms; startAt = performance.now(); paused = false;
    const bar = el.querySelectorAll(".rv-bars i b")[idx];
    if (bar) { bar.style.transition = "none"; bar.style.width = ((1 - ms / STEP_MS) * 100) + "%"; bar.offsetWidth; bar.style.transition = `width ${ms}ms linear`; bar.style.width = "100%"; }
    if (list[idx].kind !== "end") timer = setTimeout(next, ms);
  }
  function pause() { if (paused) return; paused = true; clearTimeout(timer); left -= performance.now() - startAt; const bar = el.querySelectorAll(".rv-bars i b")[idx]; if (bar) { bar.style.width = getComputedStyle(bar).width; bar.style.transition = "none"; } }
  function resume() { if (paused) run(Math.max(400, left)); }
  function next() { if (idx < list.length - 1) { idx++; paint(); } }
  function prev() { if (idx > 0) { idx--; paint(); } else paint(); }
  function close(fromBack) {
    clearTimeout(timer); if (!el) return; el.remove(); el = null; document.removeEventListener("keydown", keys);
    if (!fromBack && history.state && history.state.vc === "reviver") history.back();
  }
  function keys(e) { if (!el) return; if (e.key === "ArrowRight" || e.key === " ") { e.preventDefault(); next(); } else if (e.key === "ArrowLeft") prev(); else if (e.key === "Escape") close(); }

  window.reviverDia = function (day) {
    day = day ? new Date(day) : new Date();
    list = chapters(day);
    if (!list.length) { (window.toast || alert)("Nada registrado neste dia para reviver."); return; }
    idx = 0;
    el = document.createElement("div"); el.className = "rv"; el.setAttribute("role", "dialog"); el.setAttribute("aria-label", "Reviver " + dayName(day));
    el.innerHTML = `<div class="rv-bars"></div><button class="rv-x" aria-label="Fechar">✕</button><div class="rv-card" aria-live="polite"></div>
      <div class="rv-hint">toque à direita: próximo · à esquerda: anterior · segure: pausa</div>`;
    document.body.appendChild(el);
    try { history.pushState({ vc: "reviver" }, ""); } catch {}
    el.querySelector(".rv-x").onclick = () => close();
    let downAt = 0, y0 = 0;
    el.addEventListener("pointerdown", e => { if (e.target.closest("a,button")) return; downAt = performance.now(); y0 = e.clientY; pause(); });
    el.addEventListener("pointerup", e => {
      if (e.target.closest("a")) return;
      const b = e.target.closest("[data-rv]"); if (b) { if (b.dataset.rv === "again") { idx = 0; paint(); } else close(); return; }
      if (e.target.closest(".rv-x")) return;
      if (e.clientY - y0 > 90) { close(); return; }               // swipe down
      if (performance.now() - downAt > 350) { resume(); return; } // was a hold
      if (e.clientX < innerWidth * 0.33) prev(); else next();
    });
    document.addEventListener("keydown", keys);
    addEventListener("popstate", function pop() { removeEventListener("popstate", pop); if (el) close(true); });
    paint();
  };

  const css = document.createElement("style");
  css.textContent = `.rv{position:fixed;inset:0;z-index:9990;background:radial-gradient(circle at 50% 30%,rgba(0,255,136,.10),transparent 60%),#05080a;color:#fff;display:flex;flex-direction:column;padding:calc(10px + env(safe-area-inset-top)) 16px calc(16px + env(safe-area-inset-bottom));touch-action:none;user-select:none}
  .rv-bars{display:flex;gap:4px}.rv-bars i{flex:1;height:3px;border-radius:2px;background:rgba(255,255,255,.22);overflow:hidden}.rv-bars b{display:block;height:100%;width:0;background:var(--g,#00ff88)}
  .rv-x{position:absolute;top:calc(20px + env(safe-area-inset-top));right:12px;width:44px;height:44px;border-radius:50%;background:rgba(255,255,255,.08);color:#fff;font-size:18px}
  .rv-card{flex:1;display:flex;flex-direction:column;justify-content:center;gap:12px;max-width:560px;width:100%;margin:0 auto;animation:rvIn .45s ease}
  @keyframes rvIn{from{opacity:0;transform:translateY(14px) scale(.98)}to{opacity:1;transform:none}}
  .rv-k{font:600 12px var(--mono,monospace);letter-spacing:.12em;text-transform:uppercase;color:var(--g,#00ff88)}
  .rv h2{font-size:30px;line-height:1.15;margin:0}.rv-big{font-size:19px;margin:0}.rv-dim{color:rgba(255,255,255,.65);margin:0}
  .rv-how{align-self:flex-start;font-size:13px;color:rgba(255,255,255,.7);border:1px dashed rgba(255,255,255,.25);border-radius:99px;padding:4px 10px}
  .rv-faces{display:flex;gap:14px;flex-wrap:wrap}.rv-face{display:flex;flex-direction:column;align-items:center;gap:6px;font-size:13px}
  .rv-face img,.rv-face span{width:72px;height:72px;border-radius:50%;object-fit:cover;border:2px solid var(--g,#00ff88);display:grid;place-items:center;font-size:26px;background:#0f291e}
  .rv-said p{margin:0 0 8px;font-size:17px;line-height:1.45;color:rgba(255,255,255,.92)}
  .rv-link{color:var(--g,#00ff88);font-size:14px;align-self:flex-start}
  .rv-things{display:flex;flex-direction:column;gap:10px}.rv-thing{display:flex;gap:12px;align-items:center}.rv-thing img{width:84px;height:84px;object-fit:cover;border-radius:12px;border:1.5px solid #ffc85c}
  .rv-thing b{display:block;font-size:17px}.rv-thing span{font-size:14px;color:rgba(255,255,255,.7)}
  .rv-end{display:flex;gap:10px}.rv-end button{flex:1;min-height:48px;border-radius:14px;border:1.5px solid var(--g,#00ff88);color:#fff;font-weight:700;font-size:15px;background:transparent}.rv-end button:first-child{background:var(--g,#00ff88);color:#05080a}
  .rv-hint{text-align:center;font-size:11.5px;color:rgba(255,255,255,.45)}
  html.vc-rm .rv-card{animation:none}html.vc-rm .rv-bars b{transition:none!important}`;
  document.head.appendChild(css);
})();
