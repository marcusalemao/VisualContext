/* VisualContext — Microsoft Clarity, only after consent (same rule as alema.io: solid notice, "Pode / Não", nothing loads before).
 *
 * Fill CLARITY_ID with the project ID from clarity.microsoft.com (Settings → Overview). Empty = Clarity never loads.
 * Pages call vcTrack("demo_start") etc.; those calls are dropped when there is no consent.
 * Masking: anything with data-clarity-mask="true" (camera, photos, typed text) is never recorded.
 */
(function () {
  var CLARITY_ID = ""; // ← Marcus: paste the Clarity project ID here
  var APP_VERSION = "0.17.21";
  var KEY = "vc_consent_clarity";

  function get() { try { return localStorage.getItem(KEY); } catch (e) { return null; } }
  function put(v) { try { localStorage.setItem(KEY, v); } catch (e) {} }
  var pt = /^pt/i.test((document.documentElement.lang || navigator.language || ""));
  try { var l = localStorage.getItem("vc_lang"); if (l) pt = l === "pt"; } catch (e) {}

  function load() {
    if (!CLARITY_ID || window.clarity) return;
    (function (c, l, a, r, i, t, y) {
      c[a] = c[a] || function () { (c[a].q = c[a].q || []).push(arguments); };
      t = l.createElement(r); t.async = 1; t.src = "https://www.clarity.ms/tag/" + i;
      y = l.getElementsByTagName(r)[0]; y.parentNode.insertBefore(t, y);
    })(window, document, "clarity", "script", CLARITY_ID);
    window.clarity("set", "app_version", APP_VERSION);
    window.clarity("set", "page", location.pathname);
  }

  window.vcTrack = function (name, tags) {
    if (!window.clarity) return;
    try { window.clarity("event", name); if (tags) Object.keys(tags).forEach(function (k) { window.clarity("set", k, String(tags[k])); }); } catch (e) {}
  };

  function banner() {
    var b = document.createElement("div");
    b.id = "vc-consent";
    b.setAttribute("role", "dialog");
    b.setAttribute("aria-label", pt ? "Medição de uso" : "Usage measurement");
    b.style.cssText = "position:fixed;left:12px;right:12px;bottom:12px;z-index:9998;max-width:560px;margin:0 auto;background:#0d1217;color:#e8f0ec;border:2px solid #00ff88;border-radius:16px;padding:16px 18px;font:15px/1.45 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;box-shadow:0 8px 30px #000";
    b.innerHTML = (pt
      ? "<b style='display:block;margin-bottom:4px'>Posso medir como você usa esta página?</b>Uso o Microsoft Clarity para ver onde as pessoas clicam e onde travam. A câmera e o que você digita nunca são gravados."
      : "<b style='display:block;margin-bottom:4px'>May I measure how you use this page?</b>I use Microsoft Clarity to see where people click and get stuck. Your camera and what you type are never recorded.") +
      "<div style='display:flex;gap:10px;margin-top:12px'>" +
      "<button id='vc-c-yes' style='flex:1;min-height:44px;border-radius:12px;border:0;background:#00ff88;color:#07090c;font-weight:700;font-size:15px'>" + (pt ? "Pode" : "Yes") + "</button>" +
      "<button id='vc-c-no' style='flex:1;min-height:44px;border-radius:12px;border:2px solid #8fdfb5;background:#0d1217;color:#e8f0ec;font-weight:700;font-size:15px'>" + (pt ? "Não" : "No") + "</button></div>";
    document.body.appendChild(b);
    document.getElementById("vc-c-yes").onclick = function () { put("yes"); b.remove(); load(); };
    document.getElementById("vc-c-no").onclick = function () { put("no"); b.remove(); };
  }

  function init() {
    if (!CLARITY_ID) return;            // nothing to ask until there is a project
    var c = get();
    if (c === "yes") load();
    else if (c !== "no") banner();
  }
  window.vcConsentReset = function () { put(""); init(); };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init); else init();
})();
