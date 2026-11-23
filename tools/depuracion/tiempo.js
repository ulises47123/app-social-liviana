(async function () {
  var INDICES = __INDICES__;
  function filas() { return [].slice.call(document.querySelectorAll('a[href*="/messages/"]')).filter(function (a) { return /\/t\/\d+/.test(a.getAttribute('href') || ''); }); }
  function etiqueta() { var t = document.querySelector('[role=textbox][contenteditable=true]'); return t ? t.getAttribute('aria-label') : null; }
  var largas = [];
  var po = new PerformanceObserver(function (l) { l.getEntries().forEach(function (e) { largas.push([Math.round(e.startTime), Math.round(e.duration)]); }); });
  try { po.observe({ entryTypes: ['longtask'] }); } catch (e) { }
  var res = [];
  for (var k = 0; k < INDICES.length; k++) {
    var fs = filas(); var a = fs[Math.min(INDICES[k], fs.length - 1)];
    var antes = etiqueta(); var t0 = performance.now(); var id = (a.getAttribute('href').match(/\/t\/(\d+)/) || [])[1];
    a.click();
    var tUrl = null, tCaja = null;
    await new Promise(function (ok) {
      var iv = setInterval(function () {
        var n = performance.now() - t0;
        if (tUrl === null && location.pathname.indexOf(id) >= 0) tUrl = n;
        var e = etiqueta();
        if (tUrl !== null && e && e !== antes) { tCaja = n; clearInterval(iv); ok(); }
        if (n > 60000) { clearInterval(iv); ok(); }
      }, 50);
    });
    await new Promise(function (ok) { setTimeout(ok, 2500); });
    var bloqueo = largas.filter(function (x) { return x[0] >= t0; }).reduce(function (s, x) { return s + x[1]; }, 0);
    var peor = largas.filter(function (x) { return x[0] >= t0; }).reduce(function (s, x) { return Math.max(s, x[1]); }, 0);
    res.push({ fila: INDICES[k], id: id, url_ms: Math.round(tUrl), lista_ms: tCaja === null ? null : Math.round(tCaja), tareas_largas_ms: bloqueo, peor_tarea_ms: peor, quien: etiqueta() });
  }
  po.disconnect();
  return res;
})()
