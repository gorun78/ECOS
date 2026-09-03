#!/bin/bash
# 导航：列出实际可调用的端点（通过尝试常见路径）+ 调研 PG 访问方式
TOKEN="$(cat /tmp/ecos_token.txt | tr -d '\n')"
BASE="http://localhost:8080"

echo "=== 探测候选端点真实路径 ==="
for path in \
  "/api/data/sources" \
  "/api/v1/data/sources" \
  "/api/v1/engine/data/datasource" \
  "/api/datapipe/pipelines" \
  "/api/v1/data/pipelines" \
  "/api/v1/kb/compliance-rules" \
  "/api/kb/compliance-rules" \
  "/api/v1/knowledge/search" \
  "/api/v1/knowledge/graph" \
  "/api/v1/cognitive/reasoning-path" \
  "/api/v1/worldmodel/domains" \
  "/api/v1/worldmodel" \
  "/api/worldmodel/domains" ; do
  code=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" -m 8 "$BASE$path")
  echo "  $path -> $code"
done

echo ""
echo "=== PG psql 是否存在 ==="
which psql || echo "no psql"
ls /usr/lib/postgresql/*/bin/psql 2>/dev/null | head -3
echo ""
echo "=== gateway 运行 java 进程启动的 jar ==="
ps -ef | grep -i "GatewayApplication\|gateway" | grep -v grep | head -3
echo ""
echo "=== start-gateway.sh 内容摘录 ==="
cat ~/start-gateway.sh 2>/dev/null | head -40
