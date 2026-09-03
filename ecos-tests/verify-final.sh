#!/bin/bash
# 最终验证: 幂等性 + 资源清单
set -u
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('accessToken',''))")
DS_ID="b6431abd27b34b408ac483dd71113787"

echo "=== 二次 collect (幂等性) ==="
curl -s -X POST "http://localhost:8080/api/v1/datanet/metadata/collect/$DS_ID" \
  -H "Authorization: Bearer $TOKEN" | head -c 200
echo ""

echo ""
echo "=== 采集后行数 (应仍为180) ==="
docker exec ecos-postgres psql -U postgres -d sys_man -t -c \
  "SELECT count(*), count(DISTINCT COALESCE(source_path, resource_name)) FROM td_data_resource WHERE datasource_id='$DS_ID'"

echo ""
echo "=== 资源清单 API (无重复) ==="
curl -s "http://localhost:8080/api/v1/datanet/metadata/resources/$DS_ID" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json
d=json.load(sys.stdin)
rows=d if isinstance(d,list) else (d.get('data') or [])
names=[r.get('resourceName') for r in rows if isinstance(r,dict)]
print(f'API返回: {len(rows)} 条, 唯一名称: {len(set(names))}')"
