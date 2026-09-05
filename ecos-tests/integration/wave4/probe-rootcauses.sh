#!/bin/bash
# 验证 1: admin 的 role 是不是 ROLE_SUPER_ADMIN
# 验证 2: P0-3 QuotaFilter 是否还在 (500 in 02/05/07)
# 验证 3: P0-4 compliance-rules timestamp->long 是否还在 (500 in 06)
# 验证 4: 03 域 500 根因

cd /home/guorongxiao/ECOS/ecos-tests/integration/wave4
NODE=/home/guorongxiao/.local/bin/node
export PATH="/home/guorongxiao/.local/bin:$PATH"

# 取 admin JWT
TOKEN=$($NODE -e '
const r = await fetch("http://localhost:8080/api/v1/auth/login", { method: "POST", headers: {"Content-Type":"application/json"}, body: JSON.stringify({username:"admin",password:"admin123"}) });
const j = await r.json();
const data = j.data || j;
console.log(data.accessToken || data.token || data.access_token || "");
')
echo "=== JWT (first 80) ==="
echo "${TOKEN:0:80}..."
echo ""

# 验证 1: admin roles (解码 + 直接看 UserContext)
echo "=== 1. admin JWT claims (decoded payload) ==="
$NODE -e '
const p = process.argv[1].split(".")[1];
const b = Buffer.from(p, "base64url").toString();
console.log(b);
' "$TOKEN"
echo ""

# 验证 2: P0-3 QuotaFilter — 看是否有 5-step 链 500
echo "=== 2. P0-3 QuotaFilter: 02-data 5-step POST (应 500 则 P0-3 未修) ==="
curl -s -o /tmp/probe-2.json -w 'HTTP=%{http_code}\n' -X POST 'http://localhost:8080/api/v1/engine/data/transform/execute' \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"input":{"columns":["emp","dept"],"rows":[{"emp":"A","dept":"R&D"},{"emp":"B","dept":"QA"}]},"chain":[{"type":"cleansing","params":{"op":"trim","column":"emp"}},{"type":"aggregation","params":{"groupBy":"dept","op":"avg","column":"salary"}}]}'
head -c 600 /tmp/probe-2.json
echo ""

# 验证 3: P0-4 compliance-rules
echo "=== 3. P0-4 compliance-rules GET (应 500 则 P0-4 未修) ==="
curl -s -o /tmp/probe-3.json -w 'HTTP=%{http_code}\n' \
  -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/api/v1/knowledge/compliance-rules'
head -c 600 /tmp/probe-3.json
echo ""

# 验证 4: 03 域 onto domains 500 根因
echo "=== 4. 03-onto GET /api/v1/ontology/domains (super-admin bypass，应 200 + 列表) ==="
curl -s -o /tmp/probe-4.json -w 'HTTP=%{http_code}\n' \
  -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/api/v1/ontology/domains'
head -c 600 /tmp/probe-4.json
echo ""

# 验证 5: POST /ontology/domains (super-admin bypass — 之前 403)
echo "=== 5. 04-onto POST /api/v1/ontology/domains (super-admin bypass，应 200 则 P0-1 已修) ==="
curl -s -o /tmp/probe-5.json -w 'HTTP=%{http_code}\n' -X POST \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Wave41V5Probe","description":"wave41 P0-1 probe"}' \
  'http://localhost:8080/api/v1/ontology/domains'
head -c 600 /tmp/probe-5.json
echo ""

# 验证 6: cognitive demo wave3 (应 400 则 P0-3 未修)
echo "=== 6. 05-cognitive POST /api/v1/cognitive/demo/wave3 (应 400 则 P0-3 未修) ==="
curl -s -o /tmp/probe-6.json -w 'HTTP=%{http_code}\n' -X POST \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"markdown":"# V5 Demo\n- 销售额下降 12%","domain":"finance","maxDepth":4}' \
  'http://localhost:8080/api/v1/cognitive/demo/wave3'
head -c 800 /tmp/probe-6.json
echo ""
echo ""
echo "=== tail gateway log (probe window) ==="
tail -n 30 /tmp/w4-gw-v5.log 2>/dev/null
