#!/usr/bin/env bash
export PGPASSWORD=postgres
P() { psql -h localhost -U postgres -d sys_man -tAc "$1" 2>&1; }
echo '=== DB tables ==='
for t in ecos_ontology_proposals ecos_agent_registry td_catalog_item ecos_dq_issue ecos_dq_rule ecos_workflow_instance ecos_ontology_relationship ecos_ontology_property ecos_ontology_entity ecos_glossary_term ecos_knowledge_graph_node; do
  echo "TABLE $t=$($P "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t';")"
done
echo '=== cols ==='
$P "SELECT table_name||':: '||column_name FROM information_schema.columns WHERE table_schema='public' AND ((table_name='ecos_workflow_instance' AND column_name IN ('error_message','current_node_id','context_json','retry_count')) OR (table_name='td_catalog_item' AND column_name='tenant_id') OR (table_name='ecos_dq_issue' AND column_name='id') OR (table_name='ecos_ontology_proposals' AND column_name='optimistic_lock_version') OR (table_name='ecos_knowledge_graph_node')) ORDER BY 1;"
echo '=== recover check ==='
cd /home/guorongxiao/ECOS && git status --porcelain | head -5
find / -name 'curl_all*' -not -path '/proc/*' -not -path '/sys/*' -not -path '/mnt/*' 2>/dev/null | head -5
