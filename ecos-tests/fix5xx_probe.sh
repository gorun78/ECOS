#!/usr/bin/env bash
set -u
export PGPASSWORD=postgres
PSQL() { psql -h localhost -U postgres -d sys_man -tAc "$1" 2>&1; }
LOG=/tmp/fix5xx_probe.log
: > "$LOG"

echo '=== DB tables ===' >> "$LOG"
for t in ecos_ontology_proposals ecos_agent_registry td_catalog_item ecos_dq_issue ecos_dq_rule ecos_workflow_instance ecos_ontology_relationship ecos_ontology_property ecos_ontology_entity ecos_glossary_term ecos_knowledge_graph_node; do
  n=$($PSQL "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t';")
  echo "TABLE $t=$n" >> "$LOG"
done

echo '=== key columns ===' >> "$LOG"
$PSQL "SELECT table_name||':: '||column_name FROM information_schema.columns WHERE table_schema='public' AND ((table_name='ecos_workflow_instance' AND column_name IN ('error_message','current_node_id','context_json','retry_count')) OR (table_name='td_catalog_item' AND column_name='tenant_id') OR (table_name='ecos_dq_issue' AND column_name='id') OR (table_name='ecos_ontology_proposals' AND column_name='optimistic_lock_version') OR (table_name='ecos_knowledge_graph_node' AND column_name IN ('created_at','processed_at'))) ORDER BY 1;" >> "$LOG"

echo '=== git recover ===' >> "$LOG"
cd /home/guorongxiao/ECOS
git status --porcelain 2>&1 | head -10 >> "$LOG"
git log --oneline -3 --all >> "$LOG" 2>&1
find / -name 'curl_all*' -not -path '/proc/*' -not -path '/sys/*' -not -path '/mnt/*' 2>/dev/null | head -5 >> "$LOG"

echo '=== curl 36 endpoints ===' >> "$LOG"
BASE=http://localhost:8080
for CRED in '{"username":"admin","password":"admin123"}' '{"username":"super_admin","password":"SuperAdmin@2026"}'; do
  TK=$(curl -s -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' -d "$CRED")
  TOKEN=$(echo "$TK" | python3 -c 'import sys,json
try:
    d=json.load(sys.stdin)
    x=d.get("data") or {}
    print(x.get("token") or x.get("accessToken") or "")
except Exception:
    print("")' 2>/dev/null)
  [ -n "$TOKEN" ] && break
done
echo "TOKEN len=${#TOKEN}" >> "$LOG"

run() {
  local m="$1" p="$2" b="$3" code
  if [ -n "$b" ]; then
    code=$(curl -s -o /dev/null -w '%{http_code}' -X "$m" "$BASE$p" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$b")
  else
    code=$(curl -s -o /dev/null -w '%{http_code}' -X "$m" "$BASE$p" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json')
  fi
  echo "$code $m $p" >> "$LOG"
}

run DELETE '/api/v1/ontology/proposals/x' ''
run GET    '/api/datanet/metadata/preview/x' ''
run GET    '/api/v1/datanet/metadata/preview/x' ''
run GET    '/api/v1/knowledge/graph' ''
run GET    '/api/v1/ontology/proposals/x' ''
run GET    '/api/v1/task/x' ''
run GET    '/api/v1/task/x/status' ''
run POST   '/api/agent-mesh/agents' '{}'
run POST   '/api/datanet/metadata/collect/x' '{}'
run POST   '/api/v1/cognitive/diagnose' '{}'
run POST   '/api/v1/datanet/catalog/register' '{}'
run POST   '/api/v1/datanet/metadata/collect/x' '{}'
run POST   '/api/v1/ecos/dq/issues' '{}'
run POST   '/api/v1/ecos/dq/rules' '{}'
run POST   '/api/v1/ecos/entities/x/relationships' '{"target":"y","type":"x"}'
run POST   '/api/v1/ecos/ontologies/entities/x/properties' '{"code":"probe_a"}'
run POST   '/api/v1/ecos/ontologies/x/entities' '{"code":"probe_a"}'
run POST   '/api/v1/ecos/ontologies/x/versions/publish-from-proposal/x' '{}'
run POST   '/api/v1/ecos/workflows/instances/x/resume' '{}'
run POST   '/api/v1/ecos/workflows/instances/x/suspend' '{}'
run POST   '/api/v1/ecos/workflows/instances/x/terminate' '{}'
run POST   '/api/v1/engine/ontology/workflow/instances/x/approve' '{}'
run POST   '/api/v1/engine/ontology/workflow/instances/x/reject' '{}'
run POST   '/api/v1/guardrails/policies' '{}'
run POST   '/api/v1/knowledge/edges' '{}'
run POST   '/api/v1/knowledge/nodes' '{}'
run POST   '/api/v1/ontology/glossary/terms' '{"code":"a","name":"b"}'
run POST   '/api/v1/ontology/proposals/x/approve' '{}'
run POST   '/api/v1/ontology/proposals/x/approve-and-publish' '{}'
run POST   '/api/v1/ontology/proposals/x/execute' '{}'
run POST   '/api/v1/ontology/proposals/x/reject' '{}'
run POST   '/api/v1/ontology/proposals/x/submit' '{}'
run POST   '/api/v1/ontology/proposals/x/verify' '{}'
run POST   '/datanet/catalog/register' '{}'
run PUT    '/api/v1/knowledge/rules/x' '{}'
run PUT    '/api/v1/ontology/proposals/x' '{}'

echo '=== 5xx summary ===' >> "$LOG"
echo "5xx count: $(grep -cE '^5' "$LOG")" >> "$LOG"
grep -E '^5' "$LOG" | cut -d' ' -f2- | sort | uniq -c | sort -rn >> "$LOG"
echo DONE > /tmp/fix5xx_probe.done
cat "$LOG"
