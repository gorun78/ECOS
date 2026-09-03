#!/bin/bash
# 验证"刷新目录"修复 — 端到端测试
# API: GET /api/v1/datanet/datasource (列表)
#      POST /api/v1/datanet/metadata/collect/{id} (刷新目录)

set -u

echo "=== 登录 ==="
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('accessToken',''))")
[ -z "$TOKEN" ] && { echo "登录失败"; exit 1; }
echo "OK"

echo ""
echo "=== 数据源列表 ==="
curl -s http://localhost:8080/api/v1/datanet/datasource \
  -H "Authorization: Bearer $TOKEN" > /tmp/ds_list.json
python3 - <<'EOF'
import json
d = json.load(open('/tmp/ds_list.json'))
rows = d.get('data') or []
for r in rows:
    print(f"{r.get('datasourceId')} | {r.get('datasourceName')} | {r.get('datasourceType')}")
EOF

echo ""
echo "=== 湖南高速测试库 connectionConfig ==="
python3 - <<'EOF'
import json
d = json.load(open('/tmp/ds_list.json'))
rows = d.get('data') or []
for r in rows:
    if r.get('datasourceId') == '22a28749db5a416ba7c80a71edefff15':
        print("config:", r.get('connectionConfig'))
EOF

echo ""
echo "=== 测试连接: 湖南高速测试库 ==="
DS_ID="22a28749db5a416ba7c80a71edefff15"
curl -s -X POST "http://localhost:8080/api/v1/datanet/datasource/$DS_ID/test" \
  -H "Authorization: Bearer $TOKEN" | head -c 300
echo ""

echo ""
echo "=== 刷新目录 (collect) ==="
curl -s -X POST "http://localhost:8080/api/v1/datanet/metadata/collect/$DS_ID" \
  -H "Authorization: Bearer $TOKEN" > /tmp/collect_result.json
cat /tmp/collect_result.json | head -c 600
echo ""

echo ""
echo "=== 采集后的资源清单 ==="
curl -s "http://localhost:8080/api/v1/datanet/resource?datasourceId=$DS_ID" \
  -H "Authorization: Bearer $TOKEN" > /tmp/resource_list.json
python3 - <<'EOF'
import json
d = json.load(open('/tmp/resource_list.json'))
rows = d.get('data') or []
if isinstance(rows, dict):
    rows = rows.get('rows') or rows.get('list') or rows.get('content') or []
print(f"共 {len(rows)} 个资源")
for r in rows[:15]:
    print(f"  {r.get('resourceName')} | {r.get('resourceType')} | schema={r.get('schemaName') or r.get('sourcePath')}")
EOF
