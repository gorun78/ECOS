#!/usr/bin/env bash
# w4-probe-wave3.sh — probe cognitive demo/wave3 + 02 transform P0
set -e
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["accessToken"])')
echo "TOKEN_LEN=${#TOKEN}"
echo

echo "=== cognitive/demo/wave3 minimal ASCII ==="
curl -s -w '\nHTTP=%{http_code}\n' -X POST http://localhost:8080/api/v1/cognitive/demo/wave3 \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"markdown":"# demo\nSales dropped 12%. Root cause: cost.","domain":"finance","maxDepth":3}' | head -c 2500
echo
echo "=== data transform/execute meta ==="
curl -s -w '\nHTTP=%{http_code}\n' -X POST http://localhost:8080/api/v1/engine/data/transform/execute \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"input":{"columns":["a","b"],"data":[{"a":1,"b":2},{"a":3,"b":4}]},
       "chain":[{"type":"cleansing","params":{"op":"trim","column":"a"}}]}' | head -c 2500
