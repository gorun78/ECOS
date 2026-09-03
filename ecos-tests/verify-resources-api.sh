#!/bin/bash
# 验证资源清单端点: GET /api/v1/datanet/metadata/resources/{id}
set -u

TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('accessToken',''))")

echo "=== ECOS本地PG (应有180表) ==="
curl -s "http://localhost:8080/api/v1/datanet/metadata/resources/b6431abd27b34b408ac483dd71113787" \
  -H "Authorization: Bearer $TOKEN" > /tmp/res_local.json
python3 - <<'EOF'
import json
d = json.load(open('/tmp/res_local.json'))
rows = d if isinstance(d, list) else (d.get('data') or [])
if isinstance(rows, dict):
    rows = rows.get('rows') or rows.get('list') or rows.get('content') or []
print(f"共 {len(rows)} 个资源:")
for r in rows[:10]:
    if isinstance(r, dict):
        print(f"  {r.get('resourceName') or r.get('name')} | {r.get('resourceType') or r.get('type')}")
EOF

echo ""
echo "=== 湖南高速测试库 (应为0) ==="
curl -s "http://localhost:8080/api/v1/datanet/metadata/resources/22a28749db5a416ba7c80a71edefff15" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json
d=json.load(sys.stdin)
rows=d if isinstance(d,list) else (d.get('data') or [])
print(f'共 {len(rows)} 个资源')"
