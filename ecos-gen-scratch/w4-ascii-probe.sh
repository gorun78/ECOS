#!/usr/bin/env bash
set +e
TOKEN=$(curl -s -m 5 -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["accessToken"])' 2>/dev/null)
H="Authorization: Bearer $TOKEN"

echo "=== 02 transform — full 6.step UTC, no Chinese ==="
BODY='{"input":{"data":[{"emp":"A","dept":"RD","age":"30","salary":10000},{"emp":"B","dept":"RD","age":"40","salary":11000},{"emp":"C","dept":"QA","age":"35","salary":12000}]},"chain":[{"type":"cleansing","params":{"op":"trim","column":"emp"}},{"type":"mapping","params":{"mapping":{"emp":"name"}}},{"type":"typeConversion","params":{"column":"age","target":"integer"}},{"type":"validation","params":{"rules":[{"field":"age","type":"min","value":1}]}},{"type":"aggregation","params":{"groupBy":"dept","op":"avg","column":"salary"}}]}'
curl -s -m 30 -X POST http://localhost:8080/api/v1/engine/data/transform/execute -H "$H" -H "Content-Type: application/json" -d "$BODY" | head -c 1500
echo
echo
echo "=== 05 cognitive /diagnose — ASCII metric ==="
curl -s -m 30 -X POST http://localhost:8080/api/v1/cognitive/diagnose -H "$H" -H "Content-Type: application/json" -d '{"metric":"revenue_g","deviation":-15,"domain":"finance","maxDepth":5}' | head -c 1500
echo
echo "=== 05 cognitive /demo/wave3 — ASCII only ==="
BODY2='{"markdown":"# wave4 demo\n\nSales down 12% (deviation=-12%)\n\n- inventory up\n- demand down\n- root: sales down\n\n```mermaid\ngraph LR\n  Sales -->|dev| Cash\n  Cash -->|trig| Margin\n  Margin -.-> root\n```\n\n- margin Q1 -15%\n- close 45 days\n","domain":"finance","maxDepth":4}'
echo "$BODY2" | head -c 200
curl -s -m 60 -X POST http://localhost:8080/api/v1/cognitive/demo/wave3 -H "$H" -H "Content-Type: application/json" -d "$BODY2" | head -c 3000
echo
echo "DONE-ASCII"
