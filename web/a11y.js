/* VisualContext — accessibility panel. Always solid (no glass, no transparency), high contrast, keyboard friendly.
 * Options: text size, high contrast, less motion, and "baixa visão" (larger, bolder, highest contrast).
 * Saved per browser (localStorage). Pages only need: <script src="/a11y.js" defer></script>
 */
(function () {
  var KEY = "vc_a11y";
  var s = { size: 100, contrast: false, motion: false, low: false };
  try { var v = JSON.parse(localStorage.getItem(KEY) || "{}"); for (var k in v) s[k] = v[k]; } catch (e) {}
  var pt = (function () { try { var l = localStorage.getItem("vc_lang"); if (l) return l === "pt"; } catch (e) {} return /^pt/i.test(navigator.language || ""); })();
  var T = pt
    ? { open: "Acessibilidade", title: "Acessibilidade", size: "Tamanho do texto", contrast: "Alto contraste", motion: "Menos movimento", low: "Modo baixa visão (texto grande e forte)", reset: "Voltar ao padrão", close: "Fechar" }
    : { open: "Accessibility", title: "Accessibility", size: "Text size", contrast: "High contrast", motion: "Reduce motion", low: "Low-vision mode (large, bold text)", reset: "Reset", close: "Close" };

  var css = document.createElement("style");
  css.textContent =
    "html.vc-hc{--bg:#000!important;--s1:#000!important;--s2:#000!important;--t:#fff!important;--m:#e6e6e6!important;--line:#fff!important;--line2:#fff!important}" +
    "html.vc-hc body{background:#000!important;color:#fff!important}html.vc-hc a{color:#7dffbf!important;text-decoration:underline!important}" +
    "html.vc-hc *{text-shadow:none!important;box-shadow:none!important;backdrop-filter:none!important}" +
    "html.vc-rm *{animation:none!important;transition:none!important;scroll-behavior:auto!important}" +
    "html.vc-low body{font-weight:600!important;letter-spacing:.01em}html.vc-low p,html.vc-low li,html.vc-low small{line-height:1.6!important}" +
    "#vc-a11y-btn{position:fixed;right:12px;bottom:calc(12px + env(safe-area-inset-bottom));z-index:9997;width:52px;height:52px;border-radius:50%;background:#0d1217;color:#00ff88;border:2px solid #00ff88;font:700 18px system-ui;cursor:pointer}" +
    "#vc-a11y-btn:focus-visible,#vc-a11y button:focus-visible,#vc-a11y input:focus-visible{outline:3px solid #fff;outline-offset:2px}" +
    "#vc-a11y{position:fixed;right:12px;bottom:calc(74px + env(safe-area-inset-bottom));z-index:9999;width:min(340px,calc(100vw - 24px));background:#0d1217;color:#fff;border:2px solid #00ff88;border-radius:16px;padding:16px;font:16px/1.4 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;display:none}" +
    "#vc-a11y h2{margin:0 0 10px;font-size:18px;color:#00ff88}#vc-a11y label{display:flex;align-items:center;gap:10px;min-height:44px;cursor:pointer}" +
    "#vc-a11y input[type=checkbox]{width:22px;height:22px;accent-color:#00ff88}#vc-a11y .row{display:flex;align-items:center;justify-content:space-between;min-height:44px}" +
    "#vc-a11y .row button,#vc-a11y .end button{min-width:44px;min-height:44px;border-radius:10px;border:2px solid #8fdfb5;background:#121a20;color:#fff;font:700 16px system-ui;cursor:pointer}" +
    "#vc-a11y .end{display:flex;gap:8px;margin-top:10px}#vc-a11y .end button{flex:1}";
  document.head.appendChild(css);

  function apply() {
    var h = document.documentElement;
    var size = s.low ? Math.max(s.size, 125) : s.size;
    h.style.fontSize = size === 100 ? "" : size + "%";
    h.classList.toggle("vc-hc", s.contrast || s.low);
    h.classList.toggle("vc-rm", s.motion);
    h.classList.toggle("vc-low", s.low);
    try { localStorage.setItem(KEY, JSON.stringify(s)); } catch (e) {}
    var o = document.getElementById("vc-a11y-size"); if (o) o.textContent = size + "%";
  }

  function ui() {
    var b = document.createElement("button");
    b.id = "vc-a11y-btn"; b.type = "button"; b.setAttribute("aria-label", T.open); b.setAttribute("aria-expanded", "false"); b.setAttribute("aria-controls", "vc-a11y"); b.textContent = "Aa";
    var p = document.createElement("div");
    p.id = "vc-a11y"; p.setAttribute("role", "dialog"); p.setAttribute("aria-label", T.title);
    p.innerHTML = "<h2>" + T.title + "</h2>" +
      "<div class='row'><span>" + T.size + "</span><span style='display:flex;gap:8px;align-items:center'><button type='button' data-a='-' aria-label='A−'>A−</button><b id='vc-a11y-size' aria-live='polite'></b><button type='button' data-a='+' aria-label='A+'>A+</button></span></div>" +
      "<label><input type='checkbox' data-k='contrast'>" + T.contrast + "</label>" +
      "<label><input type='checkbox' data-k='motion'>" + T.motion + "</label>" +
      "<label><input type='checkbox' data-k='low'>" + T.low + "</label>" +
      "<div class='end'><button type='button' data-a='reset'>" + T.reset + "</button><button type='button' data-a='close'>" + T.close + "</button></div>";
    document.body.appendChild(b); document.body.appendChild(p);
    p.querySelectorAll("input[data-k]").forEach(function (i) { i.checked = !!s[i.dataset.k]; i.onchange = function () { s[i.dataset.k] = i.checked; apply(); }; });
    function show(on) { p.style.display = on ? "block" : "none"; b.setAttribute("aria-expanded", on ? "true" : "false"); if (on) p.querySelector("button").focus(); }
    b.onclick = function () { show(p.style.display !== "block"); };
    p.onclick = function (e) {
      var a = e.target.getAttribute && e.target.getAttribute("data-a"); if (!a) return;
      if (a === "+") s.size = Math.min(175, s.size + 10);
      if (a === "-") s.size = Math.max(90, s.size - 10);
      if (a === "reset") { s = { size: 100, contrast: false, motion: false, low: false }; p.querySelectorAll("input[data-k]").forEach(function (i) { i.checked = false; }); }
      if (a === "close") { show(false); b.focus(); return; }
      apply();
    };
    document.addEventListener("keydown", function (e) { if (e.key === "Escape" && p.style.display === "block") { show(false); b.focus(); } });
    apply();
  }
  apply();
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", ui); else ui();
})();
