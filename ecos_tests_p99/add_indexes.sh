#!/bin/bash
# 4 个 PG 索引 + EXPLAIN 验证
set -e
run() {
  docker exec ecos-postgres psql -U postgres -d sys_man -c "$1"
}

echo "=== 前置: 创建 pg_trgm 用于 ILIKE %..% 加速 ==="
run "CREATE EXTENSION IF NOT EXISTS pg_trgm;"

echo ""
echo "=== 索引 1: ecos_knowledge.graph_node.label 的 GIN trigram 索引 (E5 node label search) ==="
run "CREATE EXTENSION IF NOT EXISTS pg_trgm;"
run "CREATE INDEX IF NOT EXISTS idx_graph_node_label_trgm ON ecos_knowledge.graph_node USING gin (label gin_trgm_ops);"

echo ""
echo "=== 索引 2: ecos_pipeline_task.updated_at DESC (E3 listTasks 排序/分页) ==="
run "CREATE INDEX IF NOT EXISTS idx_ecos_pipeline_task_updated_at ON ecos_pipeline_task (updated_at DESC);"

echo ""
echo "=== 索引 3: sys_compliance_rule (E4 findByDomain / findByStatus) ==="
run "CREATE INDEX IF NOT EXISTS idx_sys_compliance_rule_domain ON sys_compliance_rule (domain);"
run "CREATE INDEX IF NOT EXISTS idx_sys_compliance_rule_status ON sys_compliance_rule (status);"

echo ""
echo "=== 索引 4: td_datasource.create_time DESC (E2 listAll 默认排序) ==="
# public 和 ecos_data 两份 td_datasource
run "CREATE INDEX IF NOT EXISTS idx_td_datasource_create_time ON public.td_datasource (create_time DESC);"
run "CREATE INDEX IF NOT EXISTS idx_td_datasource_create_time_data ON ecos_data.td_datasource (create_time DESC);" 2>&1 || echo "(ecos_data.td_datasource 可能不存在，忽略)"

echo ""
echo "=== 验证: 新索引 ==="
run "SELECT indexname, indexdef FROM pg_indexes WHERE indexname LIKE 'idx_graph_node_label_trgm' OR indexname LIKE 'idx_ecos_pipeline_task%' OR indexname LIKE 'idx_sys_compliance_rule%' OR indexname LIKE 'idx_td_datasource_create_time%'"

echo ""
echo "=== EXPLAIN: E5 search ILIKE ==="
run "EXPLAIN ANALYZE SELECT id, label FROM ecos_knowledge.graph_node WHERE label ILIKE '%cost%';"

echo ""
echo "=== EXPLAIN: E3 pipeline list orderBy updated_at ==="
run "EXPLAIN ANALYZE SELECT id FROM ecos_pipeline_task ORDER BY updated_at DESC LIMIT 50;"

echo ""
echo "=== EXPLAIN: E4 findByDomain ==="
run "EXPLAIN ANALYZE SELECT id FROM sys_compliance_rule WHERE domain='finance';"

echo ""
echo "=== EXPLAIN: E2 datasource list orderBy create_time ==="
run "EXPLAIN ANALYZE SELECT datasource_id FROM td_datasource ORDER BY create_time DESC;"
