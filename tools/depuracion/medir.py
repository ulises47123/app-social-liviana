"""Mide cuánto tarda Face en abrir la lista de chats y una conversación (CDP por adb forward)."""
import json, sys, time, urllib.request
from collections import defaultdict
from websockets.sync.client import connect

PUERTO = sys.argv[1] if len(sys.argv) > 1 else "9333"
ABRIR_LISTA = "--sin-lista" not in sys.argv

pag = json.load(urllib.request.urlopen(f"http://127.0.0.1:{PUERTO}/json"))[0]
ws = connect(pag["webSocketDebuggerUrl"], max_size=None, open_timeout=20)
_id = 0
red = {}          # requestId -> dict
eventos = []


def enviar(metodo, **params):
    global _id
    _id += 1
    ws.send(json.dumps({"id": _id, "method": metodo, "params": params}))
    return _id


def procesar(m):
    met = m.get("method")
    p = m.get("params", {})
    if met == "Network.requestWillBeSent":
        red[p["requestId"]] = {"url": p["request"]["url"], "tipo": p.get("type", "?"), "t": time.time(), "bytes": 0}
    elif met == "Network.loadingFinished":
        r = red.get(p["requestId"])
        if r:
            r["bytes"] = p.get("encodedDataLength", 0)
            r["fin"] = time.time()
    elif met == "Network.responseReceived":
        r = red.get(p["requestId"])
        if r:
            r["cache"] = p["response"].get("fromDiskCache", False)
            r["estado"] = p["response"].get("status")
    elif met:
        eventos.append((time.time(), met))


def llamar(metodo, espera=30, **params):
    i = enviar(metodo, **params)
    fin = time.time() + espera
    while time.time() < fin:
        try:
            m = json.loads(ws.recv(timeout=1))
        except TimeoutError:
            continue
        if m.get("id") == i:
            return m.get("result", m)
        procesar(m)
    return {}


def bombear(seg):
    fin = time.time() + seg
    while time.time() < fin:
        try:
            procesar(json.loads(ws.recv(timeout=0.2)))
        except TimeoutError:
            pass


def js(expr, espera=30):
    r = llamar("Runtime.evaluate", espera=espera, expression=expr, returnByValue=True)
    return r.get("result", {}).get("value")


def metricas():
    m = llamar("Performance.getMetrics").get("metrics", [])
    return {x["name"]: x["value"] for x in m}


def esperar(expr, tope, cada=0.4):
    t0 = time.time()
    while time.time() - t0 < tope:
        try:
            if js(expr, espera=10):
                return time.time() - t0
        except Exception:
            pass
        bombear(cada)
    return None


def resumen_red(desde, titulo):
    sel = [r for r in red.values() if r["t"] >= desde]
    por = defaultdict(lambda: [0, 0])
    for r in sel:
        por[r["tipo"]][0] += 1
        por[r["tipo"]][1] += r["bytes"]
    tot = sum(r["bytes"] for r in sel)
    cache = sum(1 for r in sel if r.get("cache"))
    print(f"  [{titulo}] pedidos={len(sel)} (de caché={cache}) bajado={tot/1024:.0f} KB")
    for k, (n, b) in sorted(por.items(), key=lambda x: -x[1][1]):
        print(f"     {k:12s} {n:4d}  {b/1024:8.0f} KB")
    for r in sorted(sel, key=lambda r: -r["bytes"])[:8]:
        print(f"       {r['bytes']/1024:7.0f} KB  {r['tipo']:10s} {r['url'][:110]}")


def dif(a, b, titulo):
    print(f"  [{titulo}] CPU script={b['ScriptDuration']-a['ScriptDuration']:.1f}s  "
          f"layout={b['LayoutDuration']-a['LayoutDuration']:.1f}s  estilos={b['RecalcStyleDuration']-a['RecalcStyleDuration']:.1f}s  "
          f"tareas={b['TaskDuration']-a['TaskDuration']:.1f}s  heap JS={b['JSHeapUsedSize']/1048576:.0f} MB  nodos={b['Nodes']:.0f}")


for d in ("Network", "Page", "Runtime", "Performance"):
    llamar(d + ".enable")

FILAS = "document.querySelectorAll('a[href*=\"/messages/\"][role=link], a[href*=\"/messages/e2ee/t/\"], a[href*=\"/messages/t/\"]').length"

if ABRIR_LISTA:
    print("== 1) Abrir la lista de chats ==")
    m0 = metricas()
    t0 = time.time()
    js("location.href='https://www.facebook.com/messages/t/'")
    bombear(2)
    t = esperar(FILAS + " > 2", 120)
    print(f"  lista visible a los {None if t is None else round(t + 2, 1)} s")
    bombear(6)
    try:
        dif(m0, metricas(), "lista")
    except Exception as e:
        print("  (métricas reiniciadas por la recarga)", e)
    resumen_red(t0, "lista")

print("== 2) Abrir una conversación ==")
print("  url:", js("location.href"), "| agente:", js("navigator.userAgent"))
print("  filas:", js(FILAS))
m1 = metricas()
t1 = time.time()
ok = js("""(function(){
  var as=[].slice.call(document.querySelectorAll('a[href*="/messages/"]')).filter(function(a){
     return /\\/messages\\/(e2ee\\/)?t\\/\\d+/.test(a.getAttribute('href')||'') && a.offsetHeight>30;});
  if(!as.length) return null;
  var a=as[Math.min(2,as.length-1)]; a.click(); return a.getAttribute('href');})()""")
print("  toqué:", ok)
t_caja = esperar("!!document.querySelector('[role=textbox][contenteditable=true]')", 120)
print(f"  caja de escribir a los {None if t_caja is None else round(t_caja, 1)} s")
t_msj = esperar("document.querySelectorAll('[role=row]').length>0 || document.querySelectorAll('[data-scope=messages_table] [role=gridcell]').length>0", 60)
print(f"  mensajes/filas a los {None if t_msj is None else round(time.time() - t1, 1)} s")
bombear(8)
dif(m1, metricas(), "conversación")
resumen_red(t1, "conversación")
print("  url final:", js("location.href"))
