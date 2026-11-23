"""Evalúa JavaScript en la página de Face. Uso: cdp.py <puerto> <archivo.js | expresión>"""
import json, os, sys, time, urllib.request
from websockets.sync.client import connect

pag = json.load(urllib.request.urlopen(f"http://127.0.0.1:{sys.argv[1]}/json"))[0]
ws = connect(pag["webSocketDebuggerUrl"], max_size=None, open_timeout=20)
expr = sys.argv[2]
if os.path.exists(expr):
    expr = open(expr, encoding="utf-8").read()
ws.send(json.dumps({"id": 1, "method": "Runtime.evaluate",
                    "params": {"expression": expr, "returnByValue": True, "awaitPromise": True}}))
fin = time.time() + 120
while time.time() < fin:
    m = json.loads(ws.recv(timeout=120))
    if m.get("id") == 1:
        r = m.get("result", {})
        v = r.get("result", {}).get("value", r)
        print(v if isinstance(v, str) else json.dumps(v, ensure_ascii=False, indent=1))
        break
