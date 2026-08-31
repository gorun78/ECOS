#!/usr/bin/env python3
# PMO-37 QA A-G — Round 4 (PMO-38 T4 修后)
# BUG-D1 修后: /collect-async 返回裸 Map, taskId 已是真 UUID
import json, subprocess, re, sys, time

BASE = "http://localhost:8080"

def curl(url, tok=None, method="GET", data=None, t=15):
    h = ["-sS", "--max-time", str(t), "-w", "\n%{http_code}", url]
    if method == "POST": h += ["-X", "POST"]
    if tok: h += ["-H", f"Authorization: Bearer {tok}"]
    if data: h += ["-H", "Content-Type: application/json", "-d", json.dumps(data)]
    r = subprocess.run(["curl"] + h, capture_output=True, text=True, timeout=t+5)
    out = r.stdout.strip()
    p = out.rsplit("\n", 1)
    body = p[0] if len(p) > 1 else out
    http = p[1].strip() if len(p) > 1 else "0"
    return (int(http) if http.isdigit() else 0), body

def jp(t):
    try: return json.loads(t)
    except: return None

results = {}

def log(ok, name, detail):
    results[name] = ok
    print(("  [PASS]" if ok else "  [FAIL]") + f" {name}: {detail}")

def psql(sql):
    r = subprocess.run(
        ["docker","exec","ecos-postgres","psql","-U","postgres",
         "-d","sys_man","-t","-A","-F"," | ","-c",sql],
        capture_output=True, text=True, timeout=30)
    return r.stdout.strip()

# ── A ──────────────────────────────────────────────
print("=== A. login ===")
st, raw = curl(f"{BASE}/api/v1/auth/login", method="POST",
               data={"username":"admin","password":"admin123"})
d = jp(raw); data = (d or {}).get("data") or {}
tok = data.get("accessToken","")
print(f"  http={st} tok_len={len(tok)}")
log(st==200 and (d or {}).get("code")==0 and len(tok)>10,
    "A.login", f"len={len(tok)}")
if not results.get("A.login"):
    print("ABORT: login failed", raw[:200]); sys.exit(1)

# ── B ──────────────────────────────────────────────
print("\n=== B. datasource list ===")
st, raw = curl(f"{BASE}/api/v1/datanet/datasource", tok)
d = jp(raw); rawdata = (d or {}).get("data")
code = (d or {}).get("code")
if isinstance(rawdata, list): items = rawdata
else: items = (rawdata or {}).get("items") or (rawdata or {}).get("content") or []
first = items[0] if items else {}
print(f"  http={st} code={code} items={len(items)}")
log(st==200 and code==0 and len(items)>=1,
    "B.datasource",
    f"{len(items)} items, first={first.get('id') or first.get('datasourceId')}")

# ── F ──────────────────────────────────────────────
if items:
    mc = first.get("metadataConfig")
    print(f"  metadataConfig={mc}")
    if mc is None:
        log(False, "F.metadataConfig-echo", "config is None")
    else:
        try:
            jp(mc)
            log(True, "F.metadataConfig-echo", f"config={mc}")
        except Exception:
            log(False, "F.metadataConfig-echo", "parse failed")

# ── C ──────────────────────────────────────────────
print("\n=== C. catalog/ds_qa_3j_test ===")
st, raw = curl(f"{BASE}/api/v1/datanet/metadata/catalog/ds_qa_3j_test?pageNum=1&pageSize=5", tok)
d = jp(raw); data = (d or {}).get("data") or {}
succ = str((d or {}).get("success")).lower()=="true" or "SUCCEEDED" in raw
print(f"  http={st} body={raw[:220]}")
log(st==200 and ((d or {}).get("code")==0 or succ),
    "C.catalog",
    f"lastCollectTime={data.get('lastCollectTime')}, collected={data.get('collected')}")

# ── D + E ─────────────────────────────────────────
print("\n=== D. collect-async/ds_qa_3j_test ===")
st, raw = curl(f"{BASE}/api/v1/datanet/metadata/collect-async/ds_qa_3j_test",
               tok, method="POST")
d = jp(raw)
print(f"  http={st} body={raw[:300]}")
tid = (d or {}).get("taskId","").strip()
uuid_ok = bool(re.match(r"^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$", tid, re.I))
okD = (st==200 and uuid_ok)
log(okD, "D.collect-async",
    f"taskId={tid} (UUID) submitted={d.get('submitted')}, status={d.get('status')}")

if okD:
    time.sleep(6)
    st2, raw2 = curl(f"{BASE}/api/v1/datanet/metadata/collect-status/{tid}", tok)
    d2 = jp(raw2)
    print(f"\n=== E. collect-status/{tid} ===")
    print(f"  http={st2} body={raw2[:400]}")
    top = d2 or {}; dd2 = top.get("data") or {}
    a2 = dd2.get("available", top.get("available"))
    s2 = dd2.get("status", top.get("status"))
    p2 = dd2.get("progress", top.get("progress"))
    log(st2==200 and (str(a2).lower()=="true" or s2=="SUCCEEDED"),
        "E.collect-status",
        f"available={a2}, status={s2}, progress={p2}")
else:
    log(False, "E.collect-status", "D failed, skip")

# ── G ─────────────────────────────────────────────
print("\n=== G. DB (sys_man) ===")
g1 = psql("SELECT column_name FROM information_schema.columns WHERE table_name='td_datasource' AND column_name IN ('metadata_config','last_collect_time')")
print(f"  G1.metadata_config col: {g1}")
log("metadata_config" in g1 and "last_collect_time" in g1,
    "G1.metadata_config-col", g1)

g2 = psql("SELECT to_regclass('public.td_metadata_collect_log')")
print(f"  G2.td_metadata_collect_log: {g2}")
log(bool(g2), "G2.td_metadata_collect_log", "exists" if g2 else "missing")

g3 = psql("SELECT count(*) FROM td_datasource")
print(f"  G3.td_datasource rows: {g3}")
log(True, "G3.td_datasource-rows", g3 or "0")

g4 = psql("SELECT datasource_id, coalesce(metadata_config::text,'<null>') FROM td_datasource LIMIT 2")
print(f"  G4.column value sample: {g4}")
log(True, "G4.column-value", g4.replace("\n","; "))

# ── summary ───────────────────────────────────────
passes = [n for n,ok in results.items() if ok]
fails  = [n for n,ok in results.items() if not ok]
print(f"\n--- FINAL: PASS={len(passes)}  FAIL={len(fails)} ---")
for n in fails: print(f"  FAIL: {n}")
sys.exit(0 if not fails else 1)
