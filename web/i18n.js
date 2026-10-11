/* VisualContext — shared language layer (all pages on the same site share the choice).
 *
 * Rule (Marcus, 10/10/2026):
 *   1. The user's choice (set in the app) wins.
 *   2. Otherwise the phone's system language — if it is Portuguese, Portuguese.
 *   3. Anything else, or unknown → English.
 * Supported today: en, pt. Add a language = add its dictionary; nothing else changes.
 *
 * Usage in a page:
 *   VC_I18N.add({ en:{ key:'Text {name}' }, pt:{ key:'Texto {name}' } });
 *   VC_I18N.t('key', { name:'Ana' })
 *   <span data-i18n="key"></span>   → filled by VC_I18N.apply()
 *   window.addEventListener('vc-lang', fn)  → fired when the language changes
 */
(function () {
  var KEY = 'vc_lang';
  var SUPPORTED = ['en', 'pt'];
  var FALLBACK = 'en';
  var dicts = { en: {}, pt: {} };

  function stored() {
    try { var s = localStorage.getItem(KEY); return SUPPORTED.indexOf(s) >= 0 ? s : null; }
    catch (e) { return null; }
  }
  function systemLang() {
    var primary = (navigator.languages && navigator.languages[0]) || navigator.language || '';
    var base = String(primary).slice(0, 2).toLowerCase();
    return SUPPORTED.indexOf(base) >= 0 ? base : FALLBACK;
  }

  var I = {
    lang: stored() || systemLang(),
    supported: SUPPORTED.slice(),
    isUserChoice: function () { return !!stored(); },
    systemLang: systemLang,
    locale: function () { return I.lang === 'pt' ? 'pt-BR' : 'en-US'; },
    add: function (d) {
      Object.keys(d).forEach(function (l) {
        dicts[l] = dicts[l] || {};
        Object.keys(d[l]).forEach(function (k) { dicts[l][k] = d[l][k]; });
      });
    },
    t: function (key, vars) {
      var s = (dicts[I.lang] && key in dicts[I.lang]) ? dicts[I.lang][key]
            : (key in dicts[FALLBACK]) ? dicts[FALLBACK][key] : key;
      if (vars) Object.keys(vars).forEach(function (k) { s = s.split('{' + k + '}').join(vars[k]); });
      return s;
    },
    apply: function (root) {
      root = root || document;
      root.querySelectorAll('[data-i18n]').forEach(function (el) { el.textContent = I.t(el.getAttribute('data-i18n')); });
      root.querySelectorAll('[data-i18n-title]').forEach(function (el) { el.title = I.t(el.getAttribute('data-i18n-title')); });
      document.documentElement.lang = I.locale();
    },
    set: function (l) {   // l = 'en' | 'pt' | 'system'
      if (l === 'system') { try { localStorage.removeItem(KEY); } catch (e) {} l = systemLang(); }
      else { if (SUPPORTED.indexOf(l) < 0) l = FALLBACK; try { localStorage.setItem(KEY, l); } catch (e) {} }
      I.lang = l;
      I.apply();
      window.dispatchEvent(new CustomEvent('vc-lang', { detail: l }));
    }
  };

  // another tab/page changed the language → follow it
  window.addEventListener('storage', function (e) {
    if (e.key !== KEY) return;
    var l = stored() || systemLang();
    if (l !== I.lang) { I.lang = l; I.apply(); window.dispatchEvent(new CustomEvent('vc-lang', { detail: l })); }
  });

  window.VC_I18N = I;
})();
