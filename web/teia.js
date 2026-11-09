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
  const T = (window.vcTeia = window.vcTeia || { center: null, scale: "week", trail: [] });

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
    for (const e of S.enc) {
      const pl = placeKey(e), who = personOf(e), live = e.capture_mode === "live";
      out.push({ kind: "memory", id: e.id, label: live ? (said(e) || "Conversa com o assistente") : who ? "Encontro com " + who : (e.context || "Momento"), sub: live ? "assistente" : "encontro",
        date: e.at, person: who, place: pl, day: dayKey(e.at), ref: e });
    }
    for (const n of S.notes) {
      const music = n.kind === "musica" || n.kind === "ouvir_depois";
      out.push({ kind: music ? "music" : "memory", id: n.id, label: n.title || n.text, sub: music ? "música" : (n.kind || "nota"), date: n.created_at || n.at, person: null, place: n.place ? { k: norm(n.place), name: n.place } : null, day: dayKey(n.created_at || n.at), ref: n, text: (n.title || "") + " " + (n.text || "") });
    }
    for (const t of S.things) out.push({ kind: "thing", id: t.id, label: t.name, sub: t.location_description || "", date: t.seen_at, person: null, place: t.location_description ? { k: norm(t.location_description.split(" · ").pop()), name: t.location_description.split(" · ").pop() } : null, day: dayKey(t.seen_at), ref: t });
    return out;
  }
  const inScale = (f, now) => { const days = SCALES.find(s => s[0] === T.scale)[2]; return f.date && (now - new Date(f.date)) / 864e5 <= days; };

  /** The nodes around a centre, with weight (0.1–1) and recency (0 near … 1 far). */
  function edges(center) {
    const now = Date.now(), fs = facts().filter(f => inScale(f, now)), span = SCALES.find(s => s[0] === T.scale)[2] * 864e5;
    const acc = new Map(); // key → {node, n, newest}
    const add = (node, date) => { const k = node.kind + ":" + node.id; const a = acc.get(k) || { node, n: 0, newest: 0 }; a.n++; const t = new Date(date || 0).getTime(); if (t > a.newest) a.newest = t; acc.set(k, a); };
    const dayNode = k => ({ kind: "day", id: k, label: dayLabel(k), sub: "" });
    const placeNode = p => ({ kind: "place", id: "pl:" + p.k, label: p.name, sub: "" });
    const self = f => ({ kind: f.kind, id: f.id, label: f.label, sub: f.sub, ref: f.ref });
    if (center.kind === "day") {
      for (const f of fs.filter(f => f.day === center.id)) { add(self(f), f.date); if (f.person) add(personNode(f.person), f.date); if (f.place) add(placeNode(f.place), f.date); }
    } else if (center.kind === "person") {
      const name = norm(center.label);
      for (const f of fs) {
        const hit = (f.person && norm(f.person) === name) || (f.text && norm(f.text).includes(name));
        if (!hit) continue;
        if (f.day) add(dayNode(f.day), f.date); if (f.place) add(placeNode(f.place), f.date); if (f.kind !== "memory" || !f.person) add(self(f), f.date);
      }
    } else if (center.kind === "place") {
      const k = center.id.replace(/^pl:/, "");
      for (const f of fs.filter(f => f.place && f.place.k === k)) { if (f.day) add(dayNode(f.day), f.date); if (f.person) add(personNode(f.person), f.date); if (f.kind === "thing" || f.kind === "music") add(self(f), f.date); }
    } else { // a moment, note or object: its person, place and day, and what else happened that day
      const f0 = fs.find(f => f.kind === center.kind && f.id === center.id) || facts().find(f => f.kind === center.kind && f.id === center.id);
      if (f0) {
        if (f0.person) add(personNode(f0.person), f0.date); if (f0.place) add(placeNode(f0.place), f0.date); if (f0.day) add(dayNode(f0.day), f0.date);
        for (const f of fs.filter(f => f.day === f0.day && !(f.kind === f0.kind && f.id === f0.id)).slice(0, 6)) add(self(f), f.date);
      }
    }
    const max = Math.max(1, ...[...acc.values()].map(a => a.n));
    return [...acc.values()].map(a => ({ node: a.node, n: a.n, weight: Math.max(0.1, a.n / max), recency: Math.min(1, Math.max(0.05, (now - a.newest) / span)) }))
      .sort((a, b) => b.weight - a.weight || a.recency - b.recency).slice(0, 12);
  }

  function defaultCenter() {
    const fs = facts(); const today = dayKey(new Date());
    if (fs.some(f => f.day === today)) return { kind: "day", id: today, label: "Hoje", sub: "" };
    const last = fs.filter(f => f.day).sort((a, b) => new Date(b.date) - new Date(a.date))[0];
    return last ? { kind: "day", id: last.day, label: dayLabel(last.day), sub: "" } : { kind: "day", id: today, label: "Hoje", sub: "" };
  }

  window.teiaHtml = function () {
    if (!T.center) T.center = defaultCenter();
    const chips = SCALES.map(([k, l]) => `<button class="pill ${T.scale === k ? "on" : ""}" data-tscale="${k}">${l}</button>`).join("");
    const trail = T.trail.length ? `<button class="pill" data-tback="1">← ${esc(T.trail[T.trail.length - 1].label)}</button>` : "";
    return `<div class="teia-top">${trail}<span class="muted" style="font-size:12px;align-self:center">${T.trail.map(x => esc(x.label) + " › ").join("")}<b style="color:var(--t)">${esc(T.center.label)}</b></span></div>
      <div class="teia-top" style="margin-top:6px">${chips}</div>
      <div id="teia" class="teia" role="application" aria-label="Teia da memória"></div>
      <p class="muted" style="font-size:12px;margin:8px 0 0">Toque: pessoa, lugar ou dia vem para o meio; lembrança ou coisa abre. Segure: abre a página. Perto = recente; linha grossa = aparecem juntos muitas vezes.</p>`;
  };

  window.teiaMount = function () {
    const box = document.getElementById("teia"); if (!box) return;
    const W = Math.max(300, box.clientWidth), H = Math.max(560, Math.round(W * 1.55));
    const NW = 86, NH = 42, CR = 42;
    const cx = W / 2, cy = H / 2, maxRx = cx - NW / 2 - 8, maxRy = cy - NH / 2 - 8;
    // Nothing may sit on the centre: the inner ring clears it with room for a node (on both axes); the outer ring is the box edge.
    const inner = Math.min(Math.max((CR + NH / 2 + 12) / maxRy, (CR + NW / 2 + 8) / maxRx), 0.85);
    const es = edges(T.center);
    const n = es.length;
    // Time sets the radius (recent near, old far). When everything is from the same moment (one day), the order
    // of recency still spreads them, so the rings read as "earlier today … later today".
    const rs = es.map(e => e.recency), lo = Math.min(...rs), hi = Math.max(...rs);
    const rel = r => hi - lo < 0.25 ? (hi === lo ? 0.5 : (r - lo) / (hi - lo)) : r;
    const pos = es.map((e, i) => {
      const a = -Math.PI / 2 + (2 * Math.PI * i) / Math.max(1, n);
      let f = inner + (1 - inner) * rel(e.recency);
      if (n > 5) f = i % 2 ? Math.max(inner, Math.min(f, inner + (1 - inner) * 0.35)) : Math.max(inner + (1 - inner) * 0.65, f); // two rings, neighbours apart
      return { e, x: cx + Math.cos(a) * maxRx * f, y: cy + Math.sin(a) * maxRy * f };
    });
    const col = k => COLOR[k] || "var(--g2)";
    const label = (s, max) => { s = String(s || ""); return s.length > max ? s.slice(0, max - 1) + "…" : s; };
    let svg = `<svg viewBox="0 0 ${W} ${H}" width="${W}" height="${H}" style="display:block">
      <ellipse cx="${cx}" cy="${cy}" rx="${maxRx * inner}" ry="${maxRy * inner}" fill="none" stroke="rgba(255,255,255,.06)"/>
      <ellipse cx="${cx}" cy="${cy}" rx="${maxRx}" ry="${maxRy}" fill="none" stroke="rgba(255,255,255,.05)" stroke-dasharray="6 6"/>`;
    for (const p of pos) svg += `<line class="syn" x1="${cx}" y1="${cy}" x2="${cx}" y2="${cy}" data-x2="${p.x.toFixed(1)}" data-y2="${p.y.toFixed(1)}" stroke="${col(p.e.node.kind)}" stroke-opacity="${(0.25 + 0.7 * (1 - p.e.recency)).toFixed(2)}" stroke-width="${(1 + p.e.weight * 5).toFixed(1)}" stroke-linecap="round"/>`;
    pos.forEach((p, i) => {
      const k = p.e.node.kind, w = NW, h = p.e.node.sub || p.e.n > 1 ? NH : 32;
      svg += `<g class="node" role="button" tabindex="0" data-ti="${i}" style="transform:translate(${cx}px,${cy}px)" aria-label="${esc(p.e.node.label)}${p.e.n > 1 ? ", " + p.e.n + " vezes" : ""}">
        <rect x="${-w / 2}" y="${-h / 2}" width="${w}" height="${h}" rx="12" fill="#0f1519" stroke="${col(k)}" stroke-opacity=".85" stroke-width="${k === "day" ? 1.5 : 1.5}"/>
        <text x="${-w / 2 + 8}" y="${p.e.node.sub || p.e.n > 1 ? -5 : 4}" fill="#fff" font-size="11" font-weight="600">${esc(ICON[k] + " " + label(p.e.node.label, 12))}</text>
        ${p.e.node.sub || p.e.n > 1 ? `<text x="${-w / 2 + 8}" y="11" fill="rgba(255,255,255,.6)" font-size="9.5">${esc(label(p.e.n > 1 ? p.e.n + "× " + (p.e.node.sub || "") : p.e.node.sub, 16))}</text>` : ""}
      </g>`;
    });
    const ck = T.center.kind;
    svg += `<g class="center" role="button" tabindex="0" data-tc="1" style="transform:translate(${cx}px,${cy}px)">
      <circle r="${CR}" fill="#0f291e" stroke="${col(ck)}" stroke-width="2.5"/>
      <text text-anchor="middle" y="${T.center.sub ? -2 : 4}" fill="#fff" font-size="12" font-weight="700">${esc(ICON[ck] + " " + label(T.center.label, 12))}</text>
      ${T.center.sub ? `<text text-anchor="middle" y="13" fill="var(--g)" font-size="9.5">${esc(label(T.center.sub, 18))}</text>` : ""}
    </g></svg>`;
    if (!n) svg = `<div class="empty">Nada ligado a “${esc(T.center.label)}” nesta janela de tempo. Experimente uma escala maior.</div>`;
    box.innerHTML = svg;
    // Grow out of the centre.
    requestAnimationFrame(() => requestAnimationFrame(() => {
      box.querySelectorAll("line.syn").forEach(l => { l.setAttribute("x2", l.dataset.x2); l.setAttribute("y2", l.dataset.y2); });
      box.querySelectorAll("g.node").forEach(g => { const p = pos[+g.dataset.ti]; g.style.transform = `translate(${p.x}px,${p.y}px)`; });
    }));
    // Tap / hold / keyboard.
    let hold = null, held = false;
    const act = (g, long) => {
      if (g.dataset.tc) { openDetail(T.center); return; }
      const node = pos[+g.dataset.ti].e.node;
      if (long) openDetail(node);
      else if (node.kind === "person" || node.kind === "place" || node.kind === "day") pivot(node);
      else openDetail(node);
    };
    box.querySelectorAll("g[role=button]").forEach(g => {
      g.addEventListener("pointerdown", () => { held = false; hold = setTimeout(() => { held = true; act(g, true); }, 550); });
      g.addEventListener("pointerup", () => { clearTimeout(hold); if (!held) act(g, false); });
      g.addEventListener("pointerleave", () => clearTimeout(hold));
      g.addEventListener("keydown", e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); act(g, e.shiftKey); } });
    });
  };
  function pivot(node) { T.trail = [...T.trail, T.center].slice(-8); T.center = node; render(); }
  function openDetail(node) {
    if (node.kind === "person") { const p = node.ref || S.people.find(x => x.id === node.id); if (p) open("person", p.id); else pivot(node); }
    else if (node.kind === "place") open("place", node.id);
    else if (node.kind === "day") { /* a day has no page: it is the centre itself */ if (T.center.id !== node.id) pivot(node); }
    else if (node.kind === "thing") open("thing", node.id);
    else open("memory", node.id);
  }
  window.teiaClick = function (d) {
    if (d.tscale) { T.scale = d.tscale; render(); return true; }
    if (d.tback) { const prev = T.trail.pop(); if (prev) T.center = prev; render(); return true; }
    return false;
  };
  /** The Manager can send something to the middle of the web (e.g. from a person's page). */
  window.teiaFocus = function (kind, id, label) { T.trail = []; T.center = { kind, id, label, sub: "" }; S.view = "teia"; render(); };

  const css = document.createElement("style");
  css.textContent = `.teia{margin-top:10px;border:1.5px solid var(--line2);border-radius:16px;background:radial-gradient(circle at 50% 50%,rgba(0,255,136,.05),transparent 60%),var(--s1);overflow:hidden}
  .teia svg{max-width:100%;height:auto}.teia g.node,.teia g.center{cursor:pointer;transition:transform .5s cubic-bezier(.2,.8,.2,1)}
  .teia g.node:hover rect,.teia g.node:focus-visible rect{stroke-width:2.5;filter:drop-shadow(0 0 6px rgba(0,255,136,.5))}.teia g:focus-visible{outline:none}
  .teia line.syn{transition:x2 .5s cubic-bezier(.2,.8,.2,1),y2 .5s cubic-bezier(.2,.8,.2,1)}
  .teia-top{display:flex;gap:6px;flex-wrap:wrap}
  html.vc-rm .teia g,html.vc-rm .teia line{transition:none!important}`;
  document.head.appendChild(css);
})();
