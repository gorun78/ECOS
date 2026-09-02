#!/usr/bin/env python3
# w4-pose-probe.py — Wave-4.1 真实生产 endpoint 探测
import json, urllib.request, urllib.error, sys

BASE = "http://localhost:8080"
TOKEN = ""  # 在 login 成功后赋值

def req(method, path, body=None, headers=None, is_json=True):
    global TOKEN
    h = {"Content-Type": "application/json"}
    if TOKEN: h["Authorization"] = f"Bearer {TOKEN}"
    if headers: h.update(headers)
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(r, timeout=60) as resp:
            raw = resp.read().decode('utf-8', errors='replace')[:8000]
            try: return (resp.status, json.loads(raw))
            except: return (resp.status, raw)
    except urllib.error.HTTPError as e:
        raw = e.read().decode('utf-8', errors='replace')[:8000]
        try: return (e.code, json.loads(raw))
        except: return (e.code, raw)

# 1) login
r, b = req("POST", "/api/v1/auth/login", {"username": "admin", "password": "admin123"})
data = b.get("data") if isinstance(b, dict) else {}
TOKEN = data.get("accessToken") if isinstance(data, dict) else ""
print(f"[init] login status={r} token_len={len(TOKEN)}")
if not TOKEN:
    print("[init] FAILED login"); print(json.dumps(b, ensure_ascii=False)[:300]); sys.exit(1)

def probe(name, method, path, body=None):
    print(f"\n[probe] {name} → {method} {path}")
    rs, bs = req(method, path, body)
    if not (isinstance(bs, dict)):
        print(f"  status={rs} non-json body: {str(bs)[:300]}")
        return bs, rs
    code = bs.get("code")
    msg = bs.get("message")
    print(f"  status={rs} code={code} msg={str(msg)[:200]}")
    return bs, rs

# ── cognitive /diagnose (correct)
print("════════ 05-cognitive: /api/v1/cognitive/diagnose ════════")
probe("diagnose", "POST", "/api/v1/cognitive/diagnose",
      {"metric": "sales_growth", "deviation": -15, "domain": "finance", "maxDepth": 4})

# ── cognitive /demo/wave3 (entropy-safe: mermaid + bullets, markdown 不空)
print("\n════════ 05-cognitive: /api/v1/cognitive/demo/wave3 ════════")
md = """# Wave-4.1 联调 周报
销售额 下降 12.0% (deviation=-12%)

## 根因
- 配件 涨价 15%
- 库存 成本 上升
- 订单 量 下降

- 业务域: finance
- 财年底 还有 45 天

```mermaid
graph LR
  Sales -->|deviation| CashFlow
  CashFlow -->|trigger| Margin
  Margin -.-> root cause
  Inventory → Cost
```

- 在 1 周内应翻转 margin
"""
probe("demo/wave3", "POST", "/api/v1/cognitive/demo/wave3",
      {"markdown": md, "domain": "finance", "maxDepth": 4})

# ── compliance rules detail (test-spel-1 / test-legacy-1)
print("\n════════ 06-cheng: /api/v1/knowledge/compliance-rules/{id} ════════")
for rid in ("test-spel-1", "test-legacy-1"):
    probe(f"rule/{rid}", "GET", f"/api/v1/knowledge/compliance-rules/{rid}")

# ── compliance list (能 200 吗? 看 500 根因)
print("\n════════ 06-cheng: /api/v1/knowledge/compliance-rules (list, 看 500 根因) ════════")
probe("compliance-rules/list", "GET", "/api/v1/knowledge/compliance-rules")

# ── 04 onto: domains 列表
print("\n════════ 03-onto: /api/v1/ontology/domains (403 recorded) ════════")
probe("ontology/domains", "GET", "/api/v1/ontology/domains")

# ── 04 onto: /api/v1/ecos/domains
probe("ecos/domains", "GET", "/api/v1/ecos/domains")

# ── search
probe("ontology/domains/search", "GET", "/api/v1/ontology/domains/search?q=&limit=20")

print("\n[done]")
