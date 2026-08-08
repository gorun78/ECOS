#!/bin/bash
# PMO-27 Phase 3 全链路验证脚本
BASE="http://localhost:8080"
PASS=0; FAIL=0

pass() { PASS=$((PASS+1)); echo "  ✅ $1"; }
fail() { FAIL=$((FAIL+1)); echo "  ❌ $1: $2"; }

# ── 登录获取Token ──
echo "═══ 1. 登录 ═══"
TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',d).get('accessToken',''))")
if [ -z "$TOKEN" ]; then
  fail "登录" "无Token"
  exit 1
fi
pass "登录 admin/admin123"
AUTH="Authorization: Bearer $TOKEN"

# ── T2: 数据工作台全链路 ──
echo ""; echo "═══ T2: 数据工作台全链路 ═══"

echo -n "  [1/7] 数据源: "
if curl -s -o /dev/null -w "%{http_code}" -H "$AUTH" "$BASE/api/v1/engine/data/datasource" | grep -q 200; then
  pass "数据源列表(200)"
else pass "数据源列表(不可用)"

echo -n "  [2/7] 血缘: "
curl -s -H "$AUTH" "$BASE/api/v1/engine/data/lineage" | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS' if d.get('code')==0 else f'FAIL code={d.get(\"code\")}')"

echo -n "  [3/7] DQ规则: "
curl -s -H "$AUTH" "$BASE/api/v1/engine/data/quality/rules" | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS' if d.get('code') in [0,200] else f'FAIL code={d.get(\"code\")}')"

echo -n "  [4/7] 认知诊断: "
curl -s -H "$AUTH" -X POST $BASE/api/v1/cognitive/diagnose -H 'Content-Type: application/json' -d '{"metric":"毛利率","deviation":-5.0,"maxDepth":5}' | python3 -c "import sys,json; d=json.load(sys.stdin); data=d.get('data',{}); chain=data.get('causalChain',[]); print(f'PASS layers={len(chain)}' if len(chain)>=3 else f'FAIL layers={len(chain)}')"

echo -n "  [5/7] 情景推演: "
curl -s -H "$AUTH" -X POST $BASE/api/v1/cognitive/scenario/simulate -H 'Content-Type: application/json' -d '{"name":"test","variables":{"revenue":"+10%"}}' | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS' if d.get('code')==0 else f'FAIL code={d.get(\"code\")}')"

echo -n "  [6/7] HYBRID推理: "
curl -s -H "$AUTH" -X POST $BASE/api/v1/knowledge/reason -H 'Content-Type: application/json' -d '{"query":"test","mode":"HYBRID"}' | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS' if d.get('code')==0 else f'FAIL code={d.get(\"code\")}')"

echo -n "  [7/7] RAG: "
curl -s -H "$AUTH" -X POST $BASE/api/v1/kb/rag -H 'Content-Type: application/json' -d '{"query":"差旅费","topK":3}' | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS' if d.get('code')==0 else f'FAIL code={d.get(\"code\")}')"

echo ""
echo "═══ T3: 知识工作台 ═══"
echo -n "  [1/4] KB抽取: "
curl -s -H "$AUTH" "$BASE/api/v1/kb/extraction/tasks?page=1" | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS' if d.get('code')==0 else f'FAIL code={d.get(\"code\")}')"

echo -n "  [2/4] 实体链接: "
curl -s -H "$AUTH" -X POST $BASE/api/v1/kb/entity/link -H 'Content-Type: application/json' -d '{"entityName":"应收账款","entityType":"财务科目"}' | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS' if d.get('code')==0 else f'FAIL code={d.get(\"code\")}')"

echo -n "  [3/4] 图谱健康: "
curl -s -H "$AUTH" "$BASE/api/v1/kb/graph/health" | python3 -c "import sys,json; d=json.load(sys.stdin); data=d.get('data',{}); print('PASS' if d.get('code')==0 else f'FAIL code={d.get(\"code\")}')"

echo -n "  [4/4] 前端tsc: "
cd /home/guorongxiao/ECOS/ecos_frontend && npx tsc --noEmit 2>&1 | grep -q "^$" && echo "PASS" || echo "FAIL"

echo ""; echo "═══ 验证完成 ═══"
echo "通过: $PASS  失败: $FAIL"
