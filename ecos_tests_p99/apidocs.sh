#!/bin/bash
# 从 OpenAPI 拿真实端点清单
TOKEN="$(cat /tmp/ecos_token.txt | tr -d '\n')"
BASE="http://localhost:8080"

echo "=== /v3/api-docs 可达性 ==="
code=$(curl -s -o /tmp/api_docs.json -w "%{http_code}" -H "Authorization: Bearer $TOKEN" -m 10 "$BASE/v3/api-docs")
echo "code=$code size=$(wc -c < /tmp/api_docs.json)"
if [ "$code" = "200" ]; then
  python3 - <<'PY'
import json
d=json.load(open('/tmp/api_docs.json'))
paths=d.get('paths',{})
print("total paths:", len(paths))
# 找与7个目标相关的
targets=['source','pipeline','compliance','search','reasoning','worldmodel','demo']
hits=[p for p in paths if any(t in p.lower() for t in targets)]
print("hits:")
for h in hits: print(" ", h, "methods=", list(paths[h].keys()))
PY
else
  echo "OpenAPI 不可用,尝试 actuator mappings"
  curl -s -m 10 -H "Authorization: Bearer $TOKEN" "$BASE/actuator/mappings" -o /tmp/mappings.json -w "actuator code=%{http_code}\n"
fi
