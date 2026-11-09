/* VisualContext Manager — "Teia": the memory as a web of neurons (Marcus, 09/11), the picture in the logo.
 *
 * Rules (so it never becomes a ball of yarn):
 *  - one node in the middle (a day, a person, a place, a moment, an object) and only what touches it around;
 *  - distance = time (recent near, old far); line thickness = how often the two appeared together;
 *  - tapping a person / place / day brings it to the middle and the web rearranges; tapping a moment, note or
 *    object opens it; holding any node opens its page; a time scale (day … 10 years) limits what is drawn.
 * Pure SVG, no library. Uses the Manager's globals: S, items(), open(), esc(), norm(), isMe(), myPlaceAt(), said().
 */
(function () {
  const SCALES = [["day", "Dia", 1], ["week", "Semana", 7], ["month", "Mês", 31], ["year", "Ano", 366], ["decade", "10 anos", 3660]];
  const COLOR = { person: "#6fe3ff", place: "var(--g)", day: "#ffffff", memory: "var(--g2)", thing: "#ffc85c", music: "#e07bff" };
  const ICON = { person: "☺", place: "⌖", day: "▣", memory: "✦", thing: "◈", music: "♪" };
  const T = (window.vcTeia = window.vcTeia || { center: null, scale: "month", trail: [] });

  const dayKey = d => { const t = new Date(d); return isNaN(t) ? null : t.getFullYear() + "-" + String(t.getMonth() + 1).padStart(2, "0") + "-" + String(t.getDate()).padStart(2, "0"); };
  const dayLabel = k => { const t = new Date(k + "T12:00:00"); const now = new Date(); const y = new Date(now); y.setDate(y.getDate() - 1);
    if (dayKey(now) === k) return "Hoje"; if (dayKey(y) === k) return "Ontem";
    const wd = t.toLocaleDateString("pt-BR", { weekday: "short" }).replace(".", ""), mo = t.toLocaleDateString("pt-BR", { month: "short" }).replace(".", "");
    return `${wd} ${t.getDate()} ${mo}` + (t.getFullYear() !== now.getFullYear() ? " " + String(t.getFullYear()).slice(2) : ""); };
  const placeKey = e => { const m = myPlaceAt(e.lat, e.lng); return m ? { k: norm(m.name), name: m.name } : e.place ? { k: norm(e.place), name: e.place } : null; };
  const personOf = e => e.person_name && !isMe(e.person_name) ? e.person_name : null;
  const personNode = name => { const p = S.people.find(x => norm(x.name) === norm(name)); return { kind: "person", id: p ? p.id : "nm:" + norm(name), label: name, sub: p ? [p.relation, p.company].filter(Boolean).join(" · ") : "", ref: p }; };

  /** All the records as small facts: {kind, id, label, sub, date, person, place, day, ref} — the raw material of the edges. */
  function facts() {
    const out = [];
    const folded = typeof musicChats === "function" ? musicChats() : new Map();
    for (const e of S.enc) {
      if (folded.has(e.id)) continue; // only about a song: the song stands for it
      const pl = placeKey(e), who = personOf(e), live = e.capture_mode === "live";
      if (live && topicOf(e).k === "consulta") continue; // a look-up is not a memory
      out.push({ kind: "memory", id: e.id, label: live ? topicOf(e).t : (e.summary || (who ? "Encontro com " + who : (e.context || "Momento"))), sub: live ? "assistente" : who ? "com " + who.split(" ")[0] : "encontro",
        date: e.at, person: who, place: pl, day: dayKey(e.at), ref: e });
    }
    for (const n of S.notes) {
      if (["ligacao", "tarefa_feita"].includes(n.kind)) continue;
      const music = n.kind === "musica" || n.kind === "ouvir_depois";
      out.push({ kind: music ? "music" : "memory", id: n.id, label: n.title || n.text, sub: music ? "música" : (n.kind || "nota"), date: n.created_at || n.at, person: null, place: n.place ? { k: norm(n.place), name: n.place } : null, day: dayKey(n.created_at || n.at), ref: n, text: (n.title || "") + " " + (n.text || "") });
    }
    // An object's spot is where it was last SEEN, never where it is now (it may have moved): the label says when.
    const ago = d => { const m = Math.round((Date.now() - new Date(d)) / 6e4); return m < 1 ? "instantes" : m < 60 ? m + " min" : m < 1440 ? Math.round(m / 60) + " h" : Math.round(m / 1440) + " d"; };
    for (const t of S.things) out.push({ kind: "thing", id: t.id, label: t.name, sub: t.seen_at ? "visto há " + ago(t.seen_at) : "", date: t.seen_at, person: null, place: t.location_description ? { k: norm(t.location_description.split(" · ").pop()), name: t.location_description.split(" · ").pop() } : null, day: dayKey(t.seen_at), ref: t });
    return out;
  }
  const inScale = (f, now) => { const days = SCALES.find(s => s[0] === T.scale)[2]; return f.date && (now - new Date(f.date)) / 864e5 <= days; };

  // Identity of things that repeat: a song is its title (every time it was heard), an object is its name (every sighting).
  const songKey = t => norm(String(t || "").split(" · ")[0].split(" — ")[0]);
  const songLabel = t => String(t || "").split(" · ")[0].split(" — ")[0];
  const entOf = f => f.kind === "music" ? "music:song:" + songKey(f.label) : f.kind === "thing" ? "thing:thing:" + norm(f.label) : null;
  const NEAR_MS = 90 * 60_000; // "together": within an hour and a half, in the same place when both have one

  /**
   * The nodes around a centre, with weight (0.1–1) and recency (0 near … 1 far). Marcus, 09/11: not a timeline —
   * every centre reaches what happened AROUND it: the Bruno brings the songs heard with him and the places you went;
   * a song brings every time it played, where and with whom, and marks the first time.
   */
  function edges(center) {
    const now = Date.now(), all = facts(), fs = all.filter(f => inScale(f, now)), span = SCALES.find(s => s[0] === T.scale)[2] * 864e5;
    const acc = new Map(); // key → {node, n, newest, oldest}
    const add = (node, date) => { const k = node.kind + ":" + node.id; if (k === center.kind + ":" + center.id) return; const a = acc.get(k) || { node, n: 0, newest: 0, oldest: Infinity }; a.n++; const t = new Date(date || 0).getTime(); if (t > a.newest) a.newest = t; if (t < a.oldest) a.oldest = t; acc.set(k, a); };
    const dayNode = k => ({ kind: "day", id: k, label: dayLabel(k), sub: "" });
    const placeNode = p => ({ kind: "place", id: "pl:" + p.k, label: p.name, sub: "" });
    const songNode = f => ({ kind: "music", id: "song:" + songKey(f.label), label: songLabel(f.label), sub: "música", ref: f.ref });
    const thingNode = f => ({ kind: "thing", id: "thing:" + norm(f.label), label: f.label, sub: f.sub, ref: f.ref });
    const self = f => f.kind === "music" ? songNode(f) : f.kind === "thing" ? thingNode(f) : ({ kind: f.kind, id: f.id, label: f.label, sub: f.sub, ref: f.ref });
    const ckey = center.kind + ":" + center.id;
    // 1. The centre's own moments.
    const mine = fs.filter(f => {
      if (center.kind === "day") return f.day === center.id;
      if (center.kind === "person") return (f.person && norm(f.person) === norm(center.label)) || (f.text && norm(f.text).includes(norm(center.label)));
      if (center.kind === "place") return f.place && "pl:" + f.place.k === center.id;
      if (center.kind === "music" || center.kind === "thing") return entOf(f) === ckey;
      return f.kind === center.kind && f.id === center.id;
    });
    // 2. Everything that happened around each of them (same day for a day; ±90 min, same place, otherwise).
    const around = m => center.kind === "day" ? [m] : fs.filter(f => f !== m && f.date && m.date && Math.abs(new Date(f.date) - new Date(m.date)) <= NEAR_MS && (!f.place || !m.place || f.place.k === m.place.k));
    const seen = new Set();
    for (const m of mine) {
      if (m.day && center.kind !== "day") add(dayNode(m.day), m.date);
      if (m.place && center.kind !== "place") add(placeNode(m.place), m.date);
      if (m.person && center.kind !== "person") add(personNode(m.person), m.date);
      if (center.kind === "person" || center.kind === "place" || center.kind === "day") if (!seen.has(m)) { seen.add(m); if (m.kind !== "memory" || center.kind !== "day" || true) add(self(m), m.date); }
      for (const f of around(m)) {
        if (seen.has(f)) continue; seen.add(f);
        if (f.person && norm(f.person) !== norm(center.label)) add(personNode(f.person), f.date);
        if (f.place && center.kind !== "place" && !(m.place && f.place.k === m.place.k)) add(placeNode(f.place), f.date);
        if (f.kind === "music" || f.kind === "thing") add(self(f), f.date);
      }
    }
    // Links he drew himself.
    for (const m of manual()) { const a = m.fields.a, b = m.fields.b; if (nkey(a) === ckey) add({ ...b, sub: "ligado por você" }, m.at); else if (nkey(b) === ckey) add({ ...a, sub: "ligado por você" }, m.at); }
    // The first time (oldest record) of a song or a place gets a mark.
    const max = Math.max(1, ...[...acc.values()].map(a => a.n));
    const firstPlace = center.kind === "music" ? [...acc.values()].filter(a => a.node.kind === "place").sort((a, b) => a.oldest - b.oldest)[0] : null;
    if (firstPlace && mine.length > 1) firstPlace.node = { ...firstPlace.node, sub: "1ª vez aqui" };
    return [...acc.values()].map(a => ({ node: a.node, n: a.n, weight: Math.max(0.1, a.n / max), recency: Math.min(1, Math.max(0.05, (now - a.newest) / span)) }))
      .sort((a, b) => b.weight - a.weight || a.recency - b.recency);
  }

  function defaultCenter() {
    const fs = facts(); const today = dayKey(new Date());
    if (fs.some(f => f.day === today)) return { kind: "day", id: today, label: "Hoje", sub: "" };
    const last = fs.filter(f => f.day).sort((a, b) => new Date(b.date) - new Date(a.date))[0];
    return last ? { kind: "day", id: last.day, label: dayLabel(last.day), sub: "" } : { kind: "day", id: today, label: "Hoje", sub: "" };
  }

  const KIND_OF_CAT = { person: ["person"], place: ["place"], thing: ["thing"], memory: ["memory", "music"] };
  const kindOk = k => { const ks = KIND_OF_CAT[S.cat]; return !ks || ks.includes(k); };
  /** Two short lines instead of a cut word (Marcus, 09/11). */
  const wrap2 = (s, n = 16) => {
    const ws = String(s || "").split(/\s+/), out = [""];
    for (const w of ws) { const cur = out[out.length - 1]; if ((cur + " " + w).trim().length <= n || !cur) out[out.length - 1] = (cur + " " + w).trim(); else if (out.length < 2) out.push(w); else { out[1] = (out[1] + " " + w).trim(); } }
    return out.map((l, i) => l.length > n ? l.slice(0, n - 1) + "…" : l);
  };
  const tspans = (lines, x, y, size) => lines.map((l, i) => `<tspan x="${x}" dy="${i ? size + 2 : 0}">${esc(l)}</tspan>`).join("");

  /** Links he drew himself (drag one dot onto another), kept as notes of kind "ligacao". */
  const manual = () => S.notes.filter(n => n.kind === "ligacao" && n.fields && n.fields.a && n.fields.b);
  const nkey = n => n.kind + ":" + n.id;

  /** The search box and the category pills also steer the web (Marcus, 09/11): a name typed brings it to the middle. */
  function followSearch() {
    const q = S.filter; if (!q) { T.q = null; return; }
    if (T.q === q) return; T.q = q;
    const n = norm(q), hit = (a, b) => norm(a).includes(n) || (b && norm(b).includes(n));
    const p = S.people.find(x => hit(x.name, x.nickname));
    let c = p ? { kind: "person", id: p.id, label: p.name, sub: "", ref: p } : null;
    if (!c) { const f = facts().find(f => f.place && hit(f.place.name)); if (f) c = { kind: "place", id: "pl:" + f.place.k, label: f.place.name, sub: "" }; }
    if (!c) { const t = S.things.find(x => hit(x.name)); if (t) c = { kind: "thing", id: "thing:" + norm(t.name), label: t.name, sub: "" }; }
    if (!c) { const m = S.notes.find(x => (x.kind === "musica" || x.kind === "ouvir_depois") && hit(x.title)); if (m) c = { kind: "music", id: "song:" + songKey(m.title), label: songLabel(m.title).split(" — ")[0], sub: "", ref: m }; }
    if (c) { T.trail = []; T.center = c; }
  }

  window.teiaHtml = function () {
    followSearch();
    const chips = SCALES.map(([k, l]) => `<button class="pill ${T.scale === k ? "on" : ""}" data-tscale="${k}">${l}</button>`).join("");
    const back = T.center ? `<button class="pill" data-tall="1">← Rede inteira</button>` + (T.trail.length ? `<button class="pill" data-tback="1">← ${esc(T.trail[T.trail.length - 1].label)}</button>` : "") : "";
    const where = T.center ? `<span class="muted" style="font-size:12px;align-self:center">no centro: <b style="color:var(--t)">${esc(T.center.label)}</b></span>` : `<span class="muted" style="font-size:12px;align-self:center">tudo em volta de você</span>`;
    return `<div class="teia-top">${back}${where}${T.center && T.center.kind === "day" ? `<button class="pill on" data-reviver="${esc(T.center.id)}T12:00:00">▶ Reviver este dia</button>` : ""}</div>
      <div class="teia-top" style="margin-top:6px">${chips}</div>
      <div class="muted" style="display:flex;gap:12px;flex-wrap:wrap;font-size:11.5px;margin-top:8px">
        <span><i class="lg" style="background:${COLOR.person}"></i>pessoa</span><span><i class="lg" style="background:#00ff88"></i>lugar</span><span><i class="lg" style="background:${COLOR.thing}"></i>coisa</span><span><i class="lg" style="background:${COLOR.music}"></i>música</span><span><i class="lg" style="background:#8fdfb5"></i>lembrança</span><span><i class="lg" style="background:#fff"></i>dia</span></div>
      <div id="teia" class="teia" role="application" aria-label="Teia da memória"></div>
      <p class="muted" style="font-size:12px;margin:8px 0 0">Toque num ponto: ele vai para o centro, com tudo o que aconteceu em volta (lembrança abre). Segure: ações. Arraste um ponto até outro: liga os dois.</p>`;
  };

  window.teiaMount = function () {
    const box = document.getElementById("teia"); if (!box) return;
    const W = Math.max(300, box.clientWidth), H = Math.max(520, Math.round(W * 1.3)), cx = W / 2, cy = H / 2;
    let list, L = [], center;
    if (!T.center) {
      // The whole web around you.
      const now = Date.now(), fs = facts().filter(f => inScale(f, now));
      const nodes = new Map(), links = new Map();
      const node = (k, id, label, ref) => { const key = k + ":" + id; const n = nodes.get(key) || { key, kind: k, id, label, ref, n: 0 }; n.n++; nodes.set(key, n); return key; };
      for (const f of fs) {
        const ks = [];
        if (f.person) ks.push(node("person", (S.people.find(x => norm(x.name) === norm(f.person)) || {}).id || "nm:" + norm(f.person), f.person));
        if (f.place) ks.push(node("place", "pl:" + f.place.k, f.place.name));
        if (f.kind === "thing") ks.push(node("thing", "thing:" + norm(f.label), f.label, f.ref));
        if (f.kind === "music") ks.push(node("music", "song:" + songKey(f.label), songLabel(f.label).split(" — ")[0], f.ref));
        if (f.kind === "memory" && S.cat === "memory") ks.push(node("memory", f.id, f.label, f.ref));
        for (let i = 0; i < ks.length; i++) for (let j = i + 1; j < ks.length; j++) { const k = [ks[i], ks[j]].sort().join("|"); links.set(k, (links.get(k) || 0) + 1); }
      }
      for (const m of manual()) { const a = m.fields.a, b = m.fields.b; if (nodes.has(nkey(a)) && nodes.has(nkey(b))) { const k = [nkey(a), nkey(b)].sort().join("|"); links.set(k, (links.get(k) || 0) + 2); } }
      list = [...nodes.values()].filter(n => kindOk(n.kind)).sort((a, b) => b.n - a.n).slice(0, 160);
      const idx = new Map(list.map(n => [n.key, n]));
      L = [...links.entries()].map(([k, w]) => { const [a, b] = k.split("|"); return { a: idx.get(a), b: idx.get(b), w }; }).filter(l => l.a && l.b);
      center = { kind: "me", label: "você" };
    } else {
      // One thing in the middle and what touches it, in the same look (Marcus, 09/11: "esteticamente gosto muito mais do primeiro").
      const es = edges(T.center).filter(e => kindOk(e.node.kind)).slice(0, 40);
      list = es.map(e => ({ key: nkey(e.node), kind: e.node.kind, id: e.node.id, label: e.node.label, ref: e.node.ref, sub: e.node.sub, n: e.n, w: e.weight, rec: e.recency }));
      center = T.center;
    }
    if (!list.length) { box.innerHTML = `<div class="empty">${T.center ? "Nada ligado a “" + esc(T.center.label) + "” nesta janela de tempo." : "Nada nesta janela de tempo."} Experimente uma escala maior${S.cat !== "all" ? " ou o filtro Todos" : ""}.</div>`; return; }
    const max = Math.max(...list.map(n => n.n));
    let seed = 7; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
    list.forEach((n, i) => {
      const a = i * 2.39996 + rnd() * 0.3, r = T.center ? 70 + (n.rec ?? 0.5) * Math.min(W, H) * 0.36 : 40 + Math.sqrt(i + 1) * Math.min(W, H) * 0.045;
      n.x = cx + Math.cos(a) * r; n.y = cy + Math.sin(a) * r; n.r = 5 + 8 * Math.sqrt(n.n / max); n.vx = 0; n.vy = 0;
    });
    const CR = T.center ? 22 : 16;
    for (let it = 0; it < 240; it++) {
      const cool = 1 - it / 240;
      for (const n of list) { const pull = T.center ? 0.004 : 0.0025 * (0.5 + n.n / max); n.vx += (cx - n.x) * pull; n.vy += (cy - n.y) * pull; }
      for (let i = 0; i < list.length; i++) for (let j = i + 1; j < list.length; j++) {
        const a = list[i], b = list[j]; let dx = a.x - b.x, dy = a.y - b.y; const d2 = dx * dx + dy * dy || 0.01, min = a.r + b.r + (T.center ? 34 : 12);
        const f = Math.min(8, (T.center ? 3600 : 2400) / d2) + (d2 < min * min ? 3 : 0), d = Math.sqrt(d2); dx /= d; dy /= d;
        a.vx += dx * f; a.vy += dy * f; b.vx -= dx * f; b.vy -= dy * f;
      }
      for (const l of L) { const dx = l.b.x - l.a.x, dy = l.b.y - l.a.y, d = Math.hypot(dx, dy) || 1, f = (d - 95) * 0.006 * Math.min(3, l.w); l.a.vx += dx / d * f; l.a.vy += dy / d * f; l.b.vx -= dx / d * f; l.b.vy -= dy / d * f; }
      for (const n of list) { const dx = n.x - cx, dy = n.y - cy, d = Math.hypot(dx, dy) || 1, keep = CR + 64; if (d < keep) { n.vx += dx / d * 6; n.vy += dy / d * 6; } }
      for (const n of list) { n.x += n.vx * cool; n.y += n.vy * cool; n.vx *= 0.5; n.vy *= 0.5; n.x = Math.max(n.r + 30, Math.min(W - n.r - 30, n.x)); n.y = Math.max(n.r + 6, Math.min(H - n.r - 30, n.y)); }
    }
    const col = k => k === "me" ? "#a77bff" : COLOR[k] || "var(--g2)";
    let svg = `<svg viewBox="0 0 ${W} ${H}" width="${W}" height="${H}" style="display:block"><defs><filter id="glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="2.5"/></filter></defs>`;
    for (const n of list) {
      const once = T.center && n.n < 2, op = T.center ? (0.3 + 0.6 * (1 - (n.rec ?? 0.5))).toFixed(2) : (0.08 + 0.3 * n.n / max).toFixed(2), sw = T.center ? (once ? 1.2 : 1.4 + (n.w || 0) * 4) : 0.6 + 1.6 * n.n / max;
      const c = T.center ? col(n.kind) : "var(--g)";
      if (T.center && !once) svg += `<line x1="${cx}" y1="${cy}" x2="${n.x.toFixed(1)}" y2="${n.y.toFixed(1)}" stroke="${c}" stroke-opacity=".3" stroke-width="${(sw + 4).toFixed(1)}" filter="url(#glow)" class="syn glow"/>`;
      svg += `<line x1="${cx}" y1="${cy}" x2="${n.x.toFixed(1)}" y2="${n.y.toFixed(1)}" stroke="${c}" stroke-opacity="${op}" stroke-width="${sw.toFixed(1)}" ${once ? 'stroke-dasharray="3 5"' : ""} class="syn"/>`;
    }
    for (const l of L) svg += `<line x1="${l.a.x.toFixed(1)}" y1="${l.a.y.toFixed(1)}" x2="${l.b.x.toFixed(1)}" y2="${l.b.y.toFixed(1)}" stroke="#ffc85c" stroke-opacity="${Math.min(0.75, 0.25 + 0.15 * l.w).toFixed(2)}" stroke-width="${Math.min(2.5, 0.7 + 0.4 * l.w).toFixed(1)}" stroke-dasharray="2 3"/>`;
    // Names: every dot around a centre; the biggest ones in the whole web — never on top of each other.
    const boxes = [[cx - CR - 2, cy - CR - 2, cx + CR + 2, cy + CR + 2], [cx - 46, cy + CR, cx + 46, cy + CR + 30]], named = new Set();
    for (const n of (T.center ? list : list.slice(0, 24))) {
      const ls = wrap2(n.label), w = Math.max(...ls.map(l => l.length)) * 5.5, h = ls.length * 12.5;
      // Below the dot, or above it when below is taken.
      const tries = [[n.y + n.r + 12, [n.x - w / 2, n.y + n.r + 1, n.x + w / 2, n.y + n.r + 3 + h]], [n.y - n.r - 4 - (ls.length - 1) * 12.5, [n.x - w / 2, n.y - n.r - 3 - h, n.x + w / 2, n.y - n.r - 1]]];
      const ok = tries.find(([, b]) => !boxes.some(o => b[0] < o[2] && b[2] > o[0] && b[1] < o[3] && b[3] > o[1]));
      if (!ok) continue;
      boxes.push(ok[1]); named.add(n.key); n.lines = ls; n.ty = ok[0];
    }
    list.forEach((n, i) => {
      svg += `<g class="dot" role="button" tabindex="0" data-di="${i}" aria-label="${esc(n.label)}, ${n.n} ${n.n === 1 ? "vez" : "vezes"}">
        <circle cx="${n.x.toFixed(1)}" cy="${n.y.toFixed(1)}" r="${(n.r + 12).toFixed(1)}" fill="transparent"/>
        <circle class="pt" cx="${n.x.toFixed(1)}" cy="${n.y.toFixed(1)}" r="${n.r.toFixed(1)}" fill="${col(n.kind)}" fill-opacity=".92" stroke="#0b0f12" stroke-width="1.5"/>
        ${named.has(n.key) ? `<text x="${n.x.toFixed(1)}" y="${n.ty.toFixed(1)}" text-anchor="middle" font-size="10.5" fill="#fff" paint-order="stroke" stroke="#0b0f12" stroke-width="3">${tspans(n.lines, n.x.toFixed(1), 0, 10.5)}</text>` : ""}</g>`;
    });
    const cl = wrap2(center.label, 18);
    svg += `<g class="dot center" role="button" tabindex="0" data-center="1" aria-label="${esc(center.label)}"><circle class="pt" cx="${cx}" cy="${cy}" r="${CR}" fill="${col(center.kind)}" stroke="#fff" stroke-width="2"/>
      <text x="${cx}" y="${cy + CR + 15}" text-anchor="middle" font-size="12" font-weight="700" fill="#fff" paint-order="stroke" stroke="#0b0f12" stroke-width="3">${tspans(cl, cx, 0, 12)}</text></g>
      <line id="tdrag" x1="0" y1="0" x2="0" y2="0" stroke="var(--g)" stroke-width="2" stroke-dasharray="4 4" style="display:none;pointer-events:none"/></svg>`;
    box.innerHTML = svg + `<p class="muted" style="font-size:12px;margin:8px 12px">${list.length} pontos · tamanho = quantas vezes aparece · pontilhado amarelo = apareceram juntos ou você ligou.</p>`;
    requestAnimationFrame(() => box.querySelectorAll("line.syn").forEach(l => l.classList.add("in")));
    wire(box, list, center, cx, cy);
  };

  /** Tap = to the middle (a memory opens); hold = actions; drag onto another dot = link the two. */
  function wire(box, list, center, cx, cy) {
    const svg = box.querySelector("svg"), drag = svg.querySelector("#tdrag");
    const nodeOf = g => g.dataset.center ? (center.kind === "me" ? null : center) : (() => { const n = list[+g.dataset.di]; return { kind: n.kind, id: n.id, label: n.label, sub: n.sub || "", ref: n.ref }; })();
    const ptOf = g => g.dataset.center ? { x: cx, y: cy } : list[+g.dataset.di];
    const toSvg = e => { const m = svg.getScreenCTM().inverse(), p = svg.createSVGPoint(); p.x = e.clientX; p.y = e.clientY; return p.matrixTransform(m); };
    box.addEventListener("pointerdown", e => { if (!e.target.closest("g.dot") && !e.target.closest(".dact")) box.querySelectorAll(".dend").forEach(x => x.remove()); });
    box.querySelectorAll("g.dot").forEach(g => {
      let hold = null, held = false, start = null, dragging = false, over = null;
      g.addEventListener("pointerdown", e => {
        held = false; dragging = false; start = { x: e.clientX, y: e.clientY };
        try { g.setPointerCapture(e.pointerId); } catch {}
        hold = setTimeout(() => { held = true; const n = nodeOf(g); if (n) { const p = ptOf(g); showActions(box, { e: { node: n }, x: p.x, y: p.y }, cx, cy); } }, 550);
      });
      g.addEventListener("pointermove", e => {
        if (!start || held) return;
        if (!dragging && Math.hypot(e.clientX - start.x, e.clientY - start.y) > 10) { dragging = true; clearTimeout(hold); }
        if (!dragging) return;
        const p = ptOf(g), q = toSvg(e);
        Object.assign(drag.style, { display: "" }); drag.setAttribute("x1", p.x); drag.setAttribute("y1", p.y); drag.setAttribute("x2", q.x); drag.setAttribute("y2", q.y);
        const t = document.elementFromPoint(e.clientX, e.clientY)?.closest("g.dot");
        if (over && over !== t) over.classList.remove("hot"); over = t && t !== g ? t : null; if (over) over.classList.add("hot");
      });
      const end = () => { clearTimeout(hold); drag.style.display = "none"; if (over) over.classList.remove("hot"); start = null; };
      g.addEventListener("pointercancel", end);
      g.addEventListener("pointerup", () => {
        const wasDrag = dragging, target = over; end();
        if (held) return;
        if (wasDrag) { if (target) linkAsk(nodeOf(g), nodeOf(target)); return; }
        const n = nodeOf(g); if (!n) return;
        if (g.dataset.center || n.kind === "memory") openDetail(n); else pivot(n);
      });
      g.addEventListener("keydown", e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); const n = nodeOf(g); if (!n) return; if (e.shiftKey) { const p = ptOf(g); showActions(box, { e: { node: n }, x: p.x, y: p.y }, cx, cy); } else if (g.dataset.center || n.kind === "memory") openDetail(n); else pivot(n); } });
    });
  }
  function linkAsk(a, b) {
    if (!a || !b || nkey(a) === nkey(b)) return;
    sheet(`<h3>Ligar os dois?</h3><p class="sub">“${esc(a.label)}” e “${esc(b.label)}” passam a aparecer juntos na teia.</p>
      <div class="btns"><button class="btn pri" id="tlink">Ligar</button><button class="btn" type="button" data-x="1">Cancelar</button></div>`);
    document.getElementById("tlink").onclick = async () => {
      const slim = n => ({ kind: n.kind, id: n.id, label: n.label });
      try { await call("note", { kind: "ligacao", title: a.label + " ↔ " + b.label, text: "Ligação feita na teia", source: "manager", fields: { a: slim(a), b: slim(b) } }); S.notes = await call("list", { entity: "note" }); closeSheet(); render(); toast("Ligados"); }
      catch (e) { toast("Não deu: " + e.message); }
    };
  }

  /** Form tells the kind at a glance: person ○, place / thing ⬡, music ◇, moment ✦, day ▬. */
  function shape(k, x, y, c) {
    const hex = r => [0, 1, 2, 3, 4, 5].map(i => { const a = Math.PI / 3 * i; return (x + r * Math.cos(a)).toFixed(1) + "," + (y + r * Math.sin(a)).toFixed(1); }).join(" ");
    if (k === "person") return `<circle cx="${x}" cy="${y}" r="6" fill="none" stroke="${c}" stroke-width="1.8"/>`;
    if (k === "place") return `<polygon points="${hex(6.5)}" fill="none" stroke="${c}" stroke-width="1.8"/>`;
    if (k === "thing") return `<polygon points="${hex(6.5)}" fill="${c}" fill-opacity=".35" stroke="${c}" stroke-width="1.8"/>`;
    if (k === "music") return `<polygon points="${x},${y - 7} ${x + 6},${y} ${x},${y + 7} ${x - 6},${y}" fill="none" stroke="${c}" stroke-width="1.8"/>`;
    if (k === "day") return `<rect x="${x - 7}" y="${y - 3.5}" width="14" height="7" rx="3.5" fill="none" stroke="${c}" stroke-width="1.8"/>`;
    return `<text x="${x}" y="${y + 4}" text-anchor="middle" fill="${c}" font-size="11">✦</text>`;
  }

  /** Long press: short branches out of the node with what can be done right there (the "dendrites"). */
  function actionsFor(node) {
    const out = [];
    if (node.kind === "person") {
      const p = node.ref || S.people.find(x => x.id === node.id);
      const n = p && String(p.contact_whatsapp || p.contact_phone || "").replace(/\D/g, "");
      if (n) out.push(["💬", "Conversar", () => { location.href = /Android/i.test(navigator.userAgent) ? `intent://chat?phone=${n}&name=${encodeURIComponent(p.name)}#Intent;scheme=zap;package=io.alema.zap;S.browser_fallback_url=${encodeURIComponent("https://wa.me/" + n)};end` : "https://wa.me/" + n; }]);
      if (p) out.push(["✦", "Preparar", () => prepForm(p.id)]);
      out.push(["◉", "Centro", () => pivot(node)]);
      if (p) out.push(["≡", "Página", () => open("person", p.id)]);
    } else if (node.kind === "music") {
      const t = String(node.label).split(" · ")[0].replace(" — ", " ");
      out.push(["▶", "Spotify", () => window.open("https://open.spotify.com/search/" + encodeURIComponent(t), "_blank")]);
      const note = node.ref || S.notes.find(x => x.kind === "musica" && songKey(x.title) === String(node.id).replace(/^song:/, ""));
      if (note) out.push(["+", "Ouvir depois", () => listenLater(note.id)]);
      out.push(["◉", "Centro", () => pivot(node)]);
      if (note) out.push(["≡", "Página", () => open("memory", note.id)]);
    } else if (node.kind === "thing") {
      const t = S.things.filter(x => norm(x.name) === norm(node.label)).sort((a, b) => new Date(b.seen_at) - new Date(a.seen_at))[0];
      if (t) out.push(["📸", "Foto", () => open("thing", t.id)]);
      out.push(["◉", "Centro", () => pivot(node)]);
      if (t && t.lat) out.push(["📍", "Mapa", () => window.open(`https://www.google.com/maps/search/?api=1&query=${t.lat},${t.lng}`, "_blank")]);
    } else if (node.kind === "place") {
      out.push(["◉", "Centro", () => pivot(node)]);
      out.push(["≡", "Página", () => open("place", node.id)]);
    } else if (node.kind === "day") {
      out.push(["◉", "Centro", () => pivot(node)]);
    } else out.push(["≡", "Abrir", () => open("memory", node.id)]);
    return out;
  }
  function showActions(box, p, cx, cy) {
    box.querySelectorAll(".dend").forEach(x => x.remove());
    const acts = actionsFor(p.e.node); if (!acts.length) return;
    const svg = box.querySelector("svg"), NS = "http://www.w3.org/2000/svg";
    const W = svg.viewBox.baseVal.width, H = svg.viewBox.baseVal.height, spread = 0.95, R = 70;
    // Branches grow away from the centre; a node near the edge has no room there, so they grow back inwards.
    let out = Math.atan2(p.y - cy, p.x - cx);
    const fits = a => [-1, 0, 1].every(t => { const b = a + t * spread, x = p.x + Math.cos(b) * R, y = p.y + Math.sin(b) * R; return x > 28 && x < W - 28 && y > 24 && y < H - 34; });
    if (!fits(out)) out = [out + Math.PI / 2, out - Math.PI / 2, out + Math.PI].find(fits) ?? out + Math.PI;
    const g = document.createElementNS(NS, "g"); g.setAttribute("class", "dend");
    let html = "";
    acts.forEach(([ic, lb], i) => {
      const a = out + (acts.length === 1 ? 0 : (i / (acts.length - 1) - 0.5) * 2 * spread);
      const x = Math.min(W - 30, Math.max(30, p.x + Math.cos(a) * R)), y = Math.min(H - 30, Math.max(22, p.y + Math.sin(a) * R));
      const d = Math.hypot(x - p.x, y - p.y) || 1, sx = p.x + (x - p.x) / d * 28, sy = p.y + (y - p.y) / d * 24; // from the node's rim, not across its text
      html += `<line x1="${sx}" y1="${sy}" x2="${x}" y2="${y}" stroke="var(--g)" stroke-opacity=".6" stroke-width="1.5"/>
        <g class="dact" role="button" tabindex="0" data-ai="${i}" aria-label="${esc(lb)}" style="cursor:pointer"><circle cx="${x}" cy="${y}" r="17" fill="#0f291e" stroke="var(--g)" stroke-width="1.8"/>
        <text x="${x}" y="${y + 4.5}" text-anchor="middle" font-size="13" fill="#fff">${esc(ic)}</text>
        <text x="${x}" y="${y + 29}" text-anchor="middle" font-size="9.5" fill="var(--g)" paint-order="stroke" stroke="#0b0f12" stroke-width="3">${esc(lb)}</text></g>`;
    });
    g.innerHTML = html; svg.appendChild(g);
    g.querySelectorAll(".dact").forEach(b => {
      const run = e => { e.stopPropagation(); acts[+b.dataset.ai][2](); g.remove(); };
      b.addEventListener("click", run); b.addEventListener("keydown", e => { if (e.key === "Enter" || e.key === " ") run(e); });
    });
    g.querySelector(".dact").focus();
  }

  function pivot(node) { if (T.center) T.trail = [...T.trail, T.center].slice(-8); T.center = node; render(); }
  function openDetail(node) {
    if (node.kind === "person") { const p = node.ref || S.people.find(x => x.id === node.id); if (p) open("person", p.id); else pivot(node); }
    else if (node.kind === "place") open("place", node.id);
    else if (node.kind === "day") { /* a day has no page: it is the centre itself */ if (T.center.id !== node.id) pivot(node); }
    else if (node.kind === "thing") { const t = S.things.filter(x => norm(x.name) === norm(node.label)).sort((a, b) => new Date(b.seen_at) - new Date(a.seen_at))[0]; if (t) open("thing", t.id); }
    else if (node.kind === "music") { const n = node.ref || S.notes.find(x => x.kind === "musica" && songKey(x.title) === node.id.replace(/^song:/, "")); if (n) open("memory", n.id); }
    else open("memory", node.id);
  }
  window.teiaClick = function (d) {
    if (d.tscale) { T.scale = d.tscale; render(); return true; }
    if (d.tback) { const prev = T.trail.pop(); if (prev) T.center = prev; render(); return true; }
    if (d.tall) { T.center = null; T.trail = []; render(); return true; }
    return false;
  };
  /** The Manager can send something to the middle of the web (e.g. from a person's page). */
  window.teiaFocus = function (kind, id, label) { T.trail = []; T.center = { kind, id, label, sub: "" }; S.view = "teia"; render(); };

  const css = document.createElement("style");
  css.textContent = `.teia{margin-top:10px;border:1.5px solid var(--line2);border-radius:16px;background:radial-gradient(circle at 50% 50%,rgba(0,255,136,.05),transparent 60%),var(--s1);overflow:hidden}
  .teia svg{max-width:100%;height:auto}.teia g.node,.teia g.center{cursor:pointer;transition:transform .5s cubic-bezier(.2,.8,.2,1)}
  .teia g.node:hover rect,.teia g.node:focus-visible rect{stroke-width:2.5;filter:drop-shadow(0 0 6px rgba(0,255,136,.5))}.teia g:focus-visible{outline:none}
  .teia path.syn{opacity:0;transition:opacity .45s ease .2s}.teia path.syn.in{opacity:1}
  .teia path.syn.glow.in{animation:teiaPulse 3.2s ease-in-out infinite alternate}
  @keyframes teiaPulse{from{opacity:.55}to{opacity:1}}
  .teia g.center circle{animation:teiaCore 3.2s ease-in-out infinite alternate}
  @keyframes teiaCore{from{filter:drop-shadow(0 0 4px rgba(0,255,136,.35))}to{filter:drop-shadow(0 0 14px rgba(0,255,136,.7))}}
  .teia-top{display:flex;gap:6px;flex-wrap:wrap}
  .teia,.teia *{-webkit-user-select:none;user-select:none;-webkit-touch-callout:none;caret-color:transparent}
  .teia g.dot{cursor:pointer;touch-action:none;outline:none}.teia g.dot:focus{outline:none}
  .teia g.dot:focus-visible .pt,.teia g.dot:hover .pt,.teia g.dot.hot .pt{stroke:#fff;stroke-width:2.5;filter:drop-shadow(0 0 6px rgba(0,255,136,.7))}
  .teia line.syn{opacity:0;transition:opacity .45s ease .1s}.teia line.syn.in{opacity:1}.teia line.syn.glow.in{animation:teiaPulse 3.2s ease-in-out infinite alternate}
  .lg{display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:5px;vertical-align:middle}
  html.vc-rm .teia g,html.vc-rm .teia path{transition:none!important;animation:none!important}
  @media (prefers-reduced-motion:reduce){.teia g,.teia path,.teia circle{transition:none!important;animation:none!important}}`;
  document.head.appendChild(css);
})();
