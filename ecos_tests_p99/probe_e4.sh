#!/bin/bash
TOKEN="$(cat /tmp/ecos_token.txt | tr -d '\n')"
BASE="http://localhost:8080"

echo "=== E4 真实响应 (200/404/500?) ==="
RESP=$(curl -s -X POST $BASE/api/v1/knowledge/compliance-rules \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"probe-rule","domain":"finance","ruleType":"EXPRESSION","condition":"x>1","action":"flag","priority":1,"enabled":true,"description":"probe"}' -w "\n__HTTP_CODE=%{http_code}")
echo "$RESP"
echo ""

echo "=== 表清单（*rule* / *knowledge* / *kb_*） ==="
docker exec ecos-postgres psql -U postgres -d sys_man -c "
SELECT schemaname, tablename FROM pg_tables
WHERE tablename LIKE '%rule%' OR tablename LIKE '%knowledge%' OR tablename LIKE '%kb_%' OR tablename LIKE '%node%' OR tablename LIKE '%version%'
ORDER BY schemaname, tablename" 2>&1 | head -30

echo ""
echo "=== compliance_rules 行数 + 索引 ==="
docker exec ecos-postgres psql -U postgres -d sys_man -c "SELECT COUNT(*) FROM compliance_rules" 2>&1
docker exec ecos-postgres psql -U postgres -d sys_man -c "
SELECT tablename, indexname FROM pg_indexes
WHERE tablename IN ('compliance_rules','sys_rule_version','kb_node','kb_knowledge_node','kb_knowledge_edge','kg_node')
" 2>&1
