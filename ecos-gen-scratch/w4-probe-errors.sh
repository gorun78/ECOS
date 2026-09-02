#!/usr/bin/env bash
# w4-probe-errors.sh — 从 bash 内 probe 关键 endpoint 看真实 code 错误
set +e
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' 2>&1 | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('accessToken',''))" 2>/dev/null)
if [ -z "$TOKEN" ]; then
  echo "NO TOKEN"
  echo $TOKEN
  exit 1
fi
echo "TOKEN_LEN=${#TOKEN}"
H="Authorization: Bearer $TOKEN"

echo "=== 05 /api/v1/cognitive/demo/wave3 (400) ==="
curl -s -X POST http://localhost:8080/api/v1/cognitive/demo/wave3 -H "$H" -H "Content-Type: application/json" -d '{"markdown":"# test\n\nmetric -12%\n\n- 根因\n","domain":"finance","maxDepth":4}' 2>&1 | head -c 400
echo

echo "=== 05 /api/v1/engine/cognitive/wave3 (alt) ==="
curl -s -X POST http://localhost:8080/api/v1/engine/cognitive/wave3 -H "$H" -H "Content-Type: application/json" -d '{"markdown":"# test -12%","domain":"finance"}' 2>&1 | head -c 400
echo

echo "=== 05 /api/v1/cognitive/diagnose ==="
curl -s -X POST http://localhost:8080/api/v1/cognitive/diagnose -H "$H" -H "Content-Type: application/json" -d '{"metric":"sales","deviation":-12,"domain":"finance","maxDepth":4}' 2>&1 | head -c 800
echo

echo "=== 06 /api/v1/knowledge/compliance-rules (500?) ==="
curl -s http://localhost:8080/api/v1/knowledge/compliance-rules -H "$H" 2>&1 | head -c 600
echo

echo "=== 07 /api/v1/knowledge/compliance-rules — vs /api/v1/kb ==="
curl -s http://localhost:8080/api/v1/kb/rules -H "$H" 2>&1 | head -c 400
echo

echo "=== 03/04 ontologies — clearance L1 req (T2 control) ==="
# /api/v1/ontology 写多带 v1 prefix + 列表 — 这是 A-API Controller
curl -s http://localhost:8080/api/v1/ontology/objects -H "$H" 2>&1 | head -c 200
echo
curl -s http://localhost:8080/api/v1/ecos/domains -H "$H" 2>&1 | head -c 300
echo

echo "=== 02 transform execute — exact test (UT-5 case) with valid input ==="
curl -s -X POST http://localhost:8080/api/v1/engine/data/transform/execute -H "$H" -H "Content-Type: application/json" -d '{"input":{"rows":[{"name":"  张三  ","age":30}]},"chain":[{"type":"cleansing","params":{"op":"trim","column":"name"}}]}' 2>&1 | head -c 600
echo

echo "=== 02 transform execute — with mapping ==="
curl -s -X POST http://localhost:8080/api/v1/engine/data/transform/execute -H "$H" -H "Content-Type: application/json" -d '{"input":{"rows":[{"emp":"Z","age":30}]},"chain":[{"type":"mapping","params":{"mapping":{"emp":"name"}}}]}' 2>&1 | head -c 600
echo
echo "DONE"
