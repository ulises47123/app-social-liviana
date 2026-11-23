(async function () {
  function filas() { return [].slice.call(document.querySelectorAll('a[href*="/messages/"]')).filter(function (a) { return /\/t\/\d+/.test(a.getAttribute('href') || ''); }); }
  function etiqueta() { var t = document.querySelector('[role=textbox][contenteditable=true]'); return t ? t.getAttribute('aria-label') : null; }
  async function abrir(i) {
    var fs = filas(); var a = fs[i]; var antes = etiqueta(); var t0 = performance.now(); var id = (a.getAttribute('href').match(/\/t\/(\d+)/) || [])[1];
    a.click(); var r = null;
    await new Promise(function (ok) { var iv = setInterval(function () { var n = performance.now() - t0; var e = etiqueta();
      if (location.pathname.indexOf(id) >= 0 && e && e !== antes) { r = n; clearInterval(iv); ok(); } if (n > 30000) { clearInterval(iv); ok(); } }, 50); });
    await new Promise(function (ok) { setTimeout(ok, 3500); });
    return Math.round(r);
  }
  function estilo(on) { var s = document.getElementById('__sinanim'); if (on && !s) { s = document.createElement('style'); s.id = '__sinanim'; s.textContent = '*,*::before,*::after{animation:none!important;transition:none!important}'; document.head.appendChild(s); } if (!on && s) s.remove(); }
  var orden = [2, 8, 10, 11, 12, 2, 8, 10, 11, 12, 2, 8, 10, 11, 12, 2, 8, 10, 11, 12];
  var res = { normal: [], sin_animaciones: [] };
  await abrir(13);   // calentamiento
  for (var k = 0; k < orden.length; k++) {
    var sin = Math.floor(k / 5) % 2 === 1; estilo(sin);
    res[sin ? 'sin_animaciones' : 'normal'].push(await abrir(orden[k]));
  }
  estilo(false);
  return res;
})()
