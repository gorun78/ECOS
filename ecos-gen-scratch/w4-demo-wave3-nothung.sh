#!/usr/bin/env bash
# w4-demo-wave3-nostarg.sh — demo/wave3 limit 3 个对照组
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["accessToken"])')

echo "=== A: 带 token + 完整 body (默认 admin L0, 走 QuotaFilter) ==="
curl -s -w '\nHTTP=%{http_code}\n' -X POST http://localhost:8080/api/v1/cognitive/demo/wave3 \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"markdown":"# demo\nSales dropped 12%. Cost root cause.","domain":"finance","maxDepth":3}' | head -c 3000
echo

echo "=== B: 不带 token (走 401 fail-closed, 这只是 verification) ==="
curl -s -w '\nHTTP=%{http_code}\n' -X POST http://localhost:8080/api/v1/cognitive/demo/wave3 \
  -H 'Content-Type: application/json' \
  -d '{"markdown":"# demo","domain":"finance","maxDepth":3}' | head -c 600
echo

echo "=== C: 带 X-Tenant-Id (和 admin 关联) ==="
curl -s -w '\nHTTP=%{http_code}\n' -X POST http://localhost:8080/api/v1/cognitive/demo/wave3 \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -H 'X-Tenant-Id: tenant-a' \
  -d '{"markdown":"# Fin demo\nSales down.","domain":"finance","maxDepth":3}' | head -c 3000
echo
