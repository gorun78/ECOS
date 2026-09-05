#!/bin/bash
# 对照组验证: ECOS本地PG 完整采集链路
set -u

TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('accessToken',''))")
[ -z "$TOKEN" ] && { echo "登录失败"; exit 1; }

DS_ID="b6431abd27b34b408ac483dd71113787"

echo "=== ECOS本地PG 配置 ==="
curl -s http://localhost:8080/api/v1/datanet/datasource/$DS_ID \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json
d=json.load(sys.stdin).get('data',{})
print('config:', d.get('connectionConfig'))"

echo ""
echo "=== 测试连接 ==="
curl -s -X POST "http://localhost:8080/api/v1/datanet/datasource/$DS_ID/test" \
  -H "Authorization: Bearer $TOKEN" | head -c 200
echo ""

echo ""
echo "=== 刷新目录 (collect) ==="
curl -s -X POST "http://localhost:8080/api/v1/datanet/metadata/collect/$DS_ID" \
  -H "Authorization: Bearer $TOKEN" > /tmp/collect2.json
head -c 400 /tmp/collect2.json
echo ""

echo ""
echo "=== 采集后的资源清单 ==="
curl -s "http://localhost:8080/api/v1/datanet/resource?datasourceId=$DS_ID" \
  -H "Authorization: Bearer $TOKEN" > /tmp/resource2.json
python3 - <<'EOF'
import json
d = json.load(open('/tmp/resource2.json'))
rows = d.get('data') or []
if isinstance(rows, dict):
    rows = rows.get('rows') or rows.get('list') or rows.get('content') or []
print(f"共 {len(rows)} 个资源")
for r in rows[:12]:
    print(f"  {r.get('resourceName')} | {r.get('resourceType')} | {r.get('sourcePath') or r.get('schemaName')}")
EOF
