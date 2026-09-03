#!/usr/bin/env bash
set -u
export PGPASSWORD=postgres
PSQL() { psql -h localhost -U postgres -d sys_man -tAc "$1" 2>&1; }
LOG=/tmp/fix5xx_probe.log
: > "$LOG"
echo '=== DB tables ===' | tee -a "$LOG"
for t in ecos_ontology_proposals ecos_agent_registry td_catalog_item ecos_dq_issue ecos_dq_rule ecos_workflow_instance ecos_ontology_relationship ecos_ontology_property ecos_ontology_entity ecos_glossary_term ecos_knowledge_graph_node; do
  echo "TABLE $t=$($(PSQL "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t';"))" | tee -a "$LOG"
done
echo '--- key columns ---' | tee -a "$LOG"
PSQL "SELECT table_name||'.'||column_name FROM information_schema.columns WHERE table_schema='public' AND ((table_name='ecos_workflow_instance' AND column_name IN ('error_message','current_node_id','context_json','retry_count')) OR (table_name='td_catalog_item' AND column_name='tenant_id') OR (table_name='ecos_dq_issue' AND column_name='id') OR (table_name='ecos_ontology_proposals' AND column_name='optimistic_lock_version')) ORDER BY 1;" | tee -a "$LOG"
echo '=== git recover ===' | tee -a "$LOG"
cd /home/guorongxiao/ECOS
git status --porcelain | head -5
git log --oneline -3 --all 2>&1
find / -name 'curl_all*' -not -path '/proc/*' -not -path '/sys/*' -not -path '/mnt/*' 2>/dev/null | head -5
