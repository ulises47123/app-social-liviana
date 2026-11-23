(function () {
  var o = { url: location.href, ancho: innerWidth, alto: innerHeight, dpr: devicePixelRatio, nodos: document.getElementsByTagName('*').length };
  o.etiquetas = [].slice.call(document.querySelectorAll('[role=main],[role=grid],[role=navigation],[role=textbox],[role=complementary],[role=banner]'))
    .map(function (e) { var r = e.getBoundingClientRect(); return e.getAttribute('role') + ' | ' + (e.getAttribute('aria-label') || '') + ' | ' + Math.round(r.width) + 'x' + Math.round(r.height) + ' @' + Math.round(r.left) + ',' + Math.round(r.top) + ' hijos=' + e.getElementsByTagName('*').length; });
  o.filas = [].slice.call(document.querySelectorAll('a[href*="/messages/"]')).filter(function (a) { return /\/t\/\d+/.test(a.getAttribute('href') || ''); })
    .slice(0, 4).map(function (a) { var r = a.getBoundingClientRect(); return (a.innerText || '').replace(/\n/g, ' / ').slice(0, 50) + ' | ' + Math.round(r.width) + 'x' + Math.round(r.height) + ' @' + Math.round(r.left) + ',' + Math.round(r.top); });
  o.video = document.querySelectorAll('video').length; o.img = document.images.length; o.iframes = document.querySelectorAll('iframe').length;
  o.animaciones = document.getAnimations ? document.getAnimations().length : -1;
  o.sl = !!window.__sl;
  return o;
})()
