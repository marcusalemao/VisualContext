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
    // An object's spot is where it was last SEEN, never where it is now (it may have moved): the label says when.
    const ago = d => { const m = Math.round((Date.now() - new Date(d)) / 6e4); return m < 1 ? "instantes" : m < 60 ? m + " min" : m < 1440 ? Math.round(m / 60) + " h" : Math.round(m / 1440) + " d"; };
    for (const t of S.things) out.push({ kind: "thing", id: t.id, label: t.name, sub: t.seen_at ? "visto há " + ago(t.seen_at) : "", date: t.seen_at, person: null, place: t.location_description ? { k: norm(t.location_description.split(" · ").pop()), name: t.location_description.split(" · ").pop() } : null, day: dayKey(t.seen_at), ref: t });
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
      .sort((a, b) => b.weight - a.weight || a.recency - b.recency);
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
    const mode = `<div class="seg" style="align-self:center"><button data-tmode="foco" class="${T.mode !== "rede" ? "on" : ""}">Foco</button><button data-tmode="rede" class="${T.mode === "rede" ? "on" : ""}">Rede inteira</button></div>`;
    const kindChip = T.kind && T.mode !== "rede" ? `<button class="pill on" data-tkind="1">✕ só ${({ person: "pessoas", place: "lugares", day: "dias", memory: "lembranças", thing: "coisas", music: "músicas" })[T.kind]}</button>` : "";
    const trail = T.trail.length ? `<button class="pill" data-tback="1">← ${esc(T.trail[T.trail.length - 1].label)}</button>` : "";
    return `<div class="teia-top">${trail}<span class="muted" style="font-size:12px;align-self:center">${T.trail.map(x => esc(x.label) + " › ").join("")}<b style="color:var(--t)">${esc(T.center.label)}</b></span></div>
      <div class="teia-top" style="margin-top:6px">${mode}${kindChip}</div>
      <div class="teia-top" style="margin-top:6px">${chips}</div>
      <div class="muted" style="display:flex;gap:12px;flex-wrap:wrap;font-size:11.5px;margin-top:8px">
        <span>○ pessoa</span><span>⬡ lugar</span><span>⬢ coisa</span><span>◇ música</span><span>✦ lembrança</span><span>▬ dia</span></div>
      <div id="teia" class="teia" role="application" aria-label="Teia da memória"></div>
      <p class="muted" style="font-size:12px;margin:8px 0 0">Toque: pessoa, lugar ou dia vem para o meio; lembrança, coisa ou música abre. Segure: ações ali mesmo. Perto = recente; linha grossa = sempre juntos; pontilhada = uma vez só.</p>`;
  };

  window.teiaMount = function () {
    const box = document.getElementById("teia"); if (!box) return;
    if (T.mode === "rede") { mountRede(box); return; }
    const W = Math.max(300, box.clientWidth), H = Math.max(560, Math.round(W * 1.55));
    const NW = 86, NH = 42, CR = 42;
    const cx = W / 2, cy = H / 2, maxRx = cx - NW / 2 - 8, maxRy = cy - NH / 2 - 8;
    // Nothing may sit on the centre: the inner ring clears it with room for a node (on both axes); the outer ring is the box edge.
    const inner = Math.min(Math.max((CR + NH / 2 + 12) / maxRy, (CR + NW / 2 + 8) / maxRx), 0.85);
    // Many connections (Mindomo idea, 09/11): one bubble per kind with its count; tapping it opens that kind only.
    const KIND_NAME = { person: "Pessoas", place: "Lugares", day: "Dias", memory: "Lembranças", thing: "Coisas", music: "Músicas" };
    let es = edges(T.center);
    if (T.kind) es = es.filter(e => e.node.kind === T.kind).slice(0, 12);
    else if (es.length > 9) {
      const by = new Map(); for (const e of es) { const a = by.get(e.node.kind) || []; a.push(e); by.set(e.node.kind, a); }
      es = [...by.entries()].map(([k, xs]) => xs.length === 1 ? xs[0] : ({ node: { kind: k, id: "hub:" + k, label: KIND_NAME[k] || k, sub: "abrir", hub: true }, n: xs.length,
        weight: Math.min(1, xs.length / 10 + 0.2), recency: Math.min(...xs.map(x => x.recency)) }));
    }
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
    // Recurring links are solid, thick and glow; a single meeting is a thin dotted line.
    svg += `<defs><filter id="glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="2.5"/></filter></defs>`;
    for (const p of pos) {
      const once = p.e.n < 2, op = (0.3 + 0.65 * (1 - p.e.recency)).toFixed(2), sw = once ? 1.4 : (1.5 + p.e.weight * 5).toFixed(1);
      // Curved like a nerve fibre (inspired by the Gemini sketch): the bend grows with the distance, always to the same side.
      const mx = (cx + p.x) / 2, my = (cy + p.y) / 2, dx = p.x - cx, dy = p.y - cy, bend = 0.12;
      const attrs = `d="M${cx},${cy} Q${(mx - dy * bend).toFixed(1)},${(my + dx * bend).toFixed(1)} ${p.x.toFixed(1)},${p.y.toFixed(1)}" fill="none" stroke="${col(p.e.node.kind)}" stroke-linecap="round"`;
      if (!once) svg += `<path class="syn glow" ${attrs} stroke-opacity=".35" stroke-width="${(+sw + 4).toFixed(1)}" filter="url(#glow)"/>`;
      svg += `<path class="syn" ${attrs} stroke-opacity="${op}" stroke-width="${sw}" ${once ? 'stroke-dasharray="3 5"' : ""}/>`;
    }
    pos.forEach((p, i) => {
      const k = p.e.node.kind, w = NW, h = p.e.node.sub || p.e.n > 1 ? NH : 32;
      svg += `<g class="node" role="button" tabindex="0" data-ti="${i}" style="transform:translate(${cx}px,${cy}px)" aria-label="${esc(p.e.node.label)}${p.e.n > 1 ? ", " + p.e.n + " vezes" : ""}">
        <rect x="${-w / 2}" y="${-h / 2}" width="${w}" height="${h}" rx="${k === "day" ? h / 2 : 12}" fill="#0f1519" stroke="${col(k)}" stroke-opacity=".85" stroke-width="1.5"/>
        ${shape(k, -w / 2 + 13, 0, col(k))}
        <text x="${-w / 2 + 24}" y="${p.e.node.sub || p.e.n > 1 ? -5 : 4}" fill="#fff" font-size="11" font-weight="600">${esc(label(p.e.node.label, 11))}</text>
        ${p.e.node.sub || p.e.n > 1 ? `<text x="${-w / 2 + 24}" y="11" fill="rgba(255,255,255,.6)" font-size="9.5">${esc(label(p.e.node.hub ? p.e.n + " · abrir" : p.e.n > 1 ? p.e.n + "× " + (p.e.node.sub || "") : p.e.node.sub, 18))}</text>` : ""}
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
      box.querySelectorAll("path.syn").forEach(l => l.classList.add("in"));
      box.querySelectorAll("g.node").forEach(g => { const p = pos[+g.dataset.ti]; g.style.transform = `translate(${p.x}px,${p.y}px)`; });
    }));
    // Tap / hold / keyboard.
    let hold = null, held = false;
    const act = (g, long) => {
      if (g.dataset.tc) { if (long) showActions(box, { e: { node: T.center }, x: cx, y: cy - 1 }, cx, cy + 1); else openDetail(T.center); return; }
      const p = pos[+g.dataset.ti], node = p.e.node;
      if (node.hub) { T.kind = node.kind; render(); return; }
      if (long) showActions(box, p, cx, cy);
      else if (node.kind === "person" || node.kind === "place" || node.kind === "day") pivot(node);
      else openDetail(node);
    };
    box.addEventListener("pointerdown", e => { if (!e.target.closest("g[role=button]") && !e.target.closest(".dact")) box.querySelectorAll(".dend").forEach(x => x.remove()); });
    box.querySelectorAll("g[role=button]").forEach(g => {
      g.addEventListener("pointerdown", () => { held = false; hold = setTimeout(() => { held = true; act(g, true); }, 550); });
      g.addEventListener("pointerup", () => { clearTimeout(hold); if (!held) act(g, false); });
      g.addEventListener("pointerleave", () => clearTimeout(hold));
      g.addEventListener("keydown", e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); act(g, e.shiftKey); } });
    });
  };
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
      out.push(["+", "Ouvir depois", () => listenLater(node.id)]);
      out.push(["≡", "Página", () => open("memory", node.id)]);
    } else if (node.kind === "thing") {
      out.push(["📸", "Foto", () => open("thing", node.id)]);
      const t = node.ref || S.things.find(x => x.id === node.id);
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

  /**
   * "Rede inteira" (from the first reference image, 09/11): everything in the time window as dots around you —
   * people, places, things, songs — sized by how often they appear, with dotted links between what appeared
   * together. Labels only for the biggest; tapping a dot shows its name, a second tap opens it in Foco.
   */
  function mountRede(box) {
    const now = Date.now(), fs = facts().filter(f => inScale(f, now));
    const nodes = new Map(), links = new Map();
    const node = (k, id, label, ref) => { const key = k + ":" + id; const n = nodes.get(key) || { key, kind: k, id, label, ref, n: 0 }; n.n++; nodes.set(key, n); return key; };
    for (const f of fs) {
      const ks = [];
      if (f.person) ks.push(node("person", (S.people.find(x => norm(x.name) === norm(f.person)) || {}).id || "nm:" + norm(f.person), f.person));
      if (f.place) ks.push(node("place", "pl:" + f.place.k, f.place.name));
      if (f.kind === "thing" || f.kind === "music") ks.push(node(f.kind, f.id, f.label, f.ref));
      for (let i = 0; i < ks.length; i++) for (let j = i + 1; j < ks.length; j++) { const k = [ks[i], ks[j]].sort().join("|"); links.set(k, (links.get(k) || 0) + 1); }
    }
    const list = [...nodes.values()].sort((a, b) => b.n - a.n).slice(0, 160);
    if (!list.length) { box.innerHTML = `<div class="empty">Nada nesta janela de tempo. Experimente uma escala maior.</div>`; return; }
    const W = Math.max(300, box.clientWidth), H = Math.max(560, Math.round(W * 1.4)), cx = W / 2, cy = H / 2;
    const max = Math.max(...list.map(n => n.n));
    // A small, deterministic force layout: every dot pulled to you, pushed from the others, held by its links.
    let seed = 7; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
    list.forEach((n, i) => { const a = i * 2.39996, r = 40 + Math.sqrt(i + 1) * Math.min(W, H) * 0.045; n.x = cx + Math.cos(a) * r; n.y = cy + Math.sin(a) * r; n.r = 4 + 8 * Math.sqrt(n.n / max); n.vx = 0; n.vy = 0; });
    const idx = new Map(list.map(n => [n.key, n]));
    const L = [...links.entries()].map(([k, w]) => { const [a, b] = k.split("|"); return { a: idx.get(a), b: idx.get(b), w }; }).filter(l => l.a && l.b);
    for (let it = 0; it < 220; it++) {
      const cool = 1 - it / 220;
      for (const n of list) { n.vx += (cx - n.x) * 0.0025 * (0.5 + n.n / max); n.vy += (cy - n.y) * 0.0025 * (0.5 + n.n / max); }
      for (let i = 0; i < list.length; i++) for (let j = i + 1; j < list.length; j++) {
        const a = list[i], b = list[j]; let dx = a.x - b.x, dy = a.y - b.y; let d2 = dx * dx + dy * dy || 0.01; const min = (a.r + b.r + 10);
        const f = Math.min(8, 2400 / d2) + (d2 < min * min ? 3 : 0); const d = Math.sqrt(d2); dx /= d; dy /= d;
        a.vx += dx * f; a.vy += dy * f; b.vx -= dx * f; b.vy -= dy * f;
      }
      for (const l of L) { const dx = l.b.x - l.a.x, dy = l.b.y - l.a.y, d = Math.hypot(dx, dy) || 1, f = (d - 95) * 0.006 * Math.min(3, l.w); l.a.vx += dx / d * f; l.a.vy += dy / d * f; l.b.vx -= dx / d * f; l.b.vy -= dy / d * f; }
      // keep clear of "you" in the middle
      for (const n of list) { const dx = n.x - cx, dy = n.y - cy, d = Math.hypot(dx, dy) || 1; if (d < 64) { n.vx += dx / d * 6; n.vy += dy / d * 6; } }
      for (const n of list) { n.x += n.vx * cool; n.y += n.vy * cool; n.vx *= 0.5; n.vy *= 0.5; n.x = Math.max(n.r + 4, Math.min(W - n.r - 4, n.x)); n.y = Math.max(n.r + 4, Math.min(H - n.r - 4, n.y)); }
    }
    const col = k => COLOR[k] || "var(--g2)";
    let svg = `<svg viewBox="0 0 ${W} ${H}" width="${W}" height="${H}" style="display:block">`;
    for (const n of list) svg += `<line x1="${cx}" y1="${cy}" x2="${n.x.toFixed(1)}" y2="${n.y.toFixed(1)}" stroke="var(--g)" stroke-opacity="${(0.08 + 0.3 * n.n / max).toFixed(2)}" stroke-width="${(0.6 + 1.6 * n.n / max).toFixed(1)}"/>`;
    for (const l of L) svg += `<line x1="${l.a.x.toFixed(1)}" y1="${l.a.y.toFixed(1)}" x2="${l.b.x.toFixed(1)}" y2="${l.b.y.toFixed(1)}" stroke="#ffc85c" stroke-opacity="${Math.min(0.75, 0.25 + 0.15 * l.w).toFixed(2)}" stroke-width="${Math.min(2.5, 0.7 + 0.4 * l.w).toFixed(1)}" stroke-dasharray="2 3"/>`;
    // Names for the biggest dots only, and never on top of each other or of "você".
    const big = new Set(), boxes = [[cx - 26, cy - 22, cx + 26, cy + 36]];
    for (const n of list.slice(0, 14)) {
      const t = n.label.length > 14 ? n.label.slice(0, 13) + "…" : n.label, w = t.length * 5.6, b = [n.x - w / 2, n.y + n.r + 1, n.x + w / 2, n.y + n.r + 14];
      if (boxes.some(o => b[0] < o[2] && b[2] > o[0] && b[1] < o[3] && b[3] > o[1])) continue;
      boxes.push(b); big.add(n.key);
    }
    list.forEach((n, i) => {
      svg += `<g class="dot" role="button" tabindex="0" data-di="${i}" aria-label="${esc(n.label)}, ${n.n} ${n.n === 1 ? "vez" : "vezes"}">
        <circle cx="${n.x.toFixed(1)}" cy="${n.y.toFixed(1)}" r="${n.r.toFixed(1)}" fill="${col(n.kind)}" fill-opacity=".9" stroke="#0b0f12" stroke-width="1.5"/>
        ${big.has(n.key) ? `<text x="${n.x.toFixed(1)}" y="${(n.y + n.r + 11).toFixed(1)}" text-anchor="middle" font-size="10" fill="#fff" paint-order="stroke" stroke="#0b0f12" stroke-width="3">${esc(n.label.length > 14 ? n.label.slice(0, 13) + "…" : n.label)}</text>` : ""}</g>`;
    });
    svg += `<g class="center"><circle cx="${cx}" cy="${cy}" r="16" fill="#a77bff" stroke="#fff" stroke-width="2"/><text x="${cx}" y="${cy + 30}" text-anchor="middle" font-size="11" font-weight="700" fill="#fff" paint-order="stroke" stroke="#0b0f12" stroke-width="3">você</text></g>
      <g id="rede-tip"></g></svg>`;
    box.innerHTML = svg + `<p class="muted" style="font-size:12px;margin:8px 12px">${list.length} pontos · tamanho = quantas vezes aparece · pontilhado amarelo = apareceram juntos. Toque num ponto para ver o nome; toque de novo para abrir.</p>`;
    let picked = null;
    const tip = box.querySelector("#rede-tip");
    box.querySelectorAll("g.dot").forEach(g => {
      const go = () => {
        const n = list[+g.dataset.di];
        if (picked === n.key) { pivot({ kind: n.kind, id: n.id, label: n.label, sub: "", ref: n.ref }); return; }
        picked = n.key;
        const w = Math.min(190, 16 + n.label.length * 6.3), x = Math.max(4, Math.min(W - w - 4, n.x - w / 2)), y = n.y - n.r - 34 < 4 ? n.y + n.r + 6 : n.y - n.r - 34;
        tip.innerHTML = `<rect x="${x}" y="${y}" width="${w}" height="26" rx="8" fill="#0f291e" stroke="${col(n.kind)}"/><text x="${x + 8}" y="${y + 17}" font-size="11.5" fill="#fff">${esc(n.label.slice(0, 26))} · ${n.n}×</text>`;
      };
      g.addEventListener("click", go); g.addEventListener("keydown", e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); go(); } });
    });
  }

  function pivot(node) { T.trail = [...T.trail, T.center].slice(-8); T.center = node; T.kind = null; T.mode = "foco"; render(); }
  function openDetail(node) {
    if (node.kind === "person") { const p = node.ref || S.people.find(x => x.id === node.id); if (p) open("person", p.id); else pivot(node); }
    else if (node.kind === "place") open("place", node.id);
    else if (node.kind === "day") { /* a day has no page: it is the centre itself */ if (T.center.id !== node.id) pivot(node); }
    else if (node.kind === "thing") open("thing", node.id);
    else open("memory", node.id);
  }
  window.teiaClick = function (d) {
    if (d.tscale) { T.scale = d.tscale; render(); return true; }
    if (d.tback) { const prev = T.trail.pop(); if (prev) T.center = prev; T.kind = null; render(); return true; }
    if (d.tkind) { T.kind = null; render(); return true; }
    if (d.tmode) { T.mode = d.tmode; render(); return true; }
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
  html.vc-rm .teia g,html.vc-rm .teia path{transition:none!important;animation:none!important}
  @media (prefers-reduced-motion:reduce){.teia g,.teia path,.teia circle{transition:none!important;animation:none!important}}`;
  document.head.appendChild(css);
})();
