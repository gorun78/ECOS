#!/bin/bash
set -u
P="docker exec ecos-postgres psql -U postgres -d sys_man -t"

echo "=== 表行数 ==="
docker exec ecos-postgres psql -U postgres -d sys_man -c "
SELECT 'td_datasource' AS tbl, COUNT(*) FROM td_datasource
UNION ALL SELECT 'td_data_resource', COUNT(*) FROM td_data_resource
UNION ALL SELECT 'ecos_pipeline_task', COUNT(*) FROM ecos_pipeline_task
UNION ALL SELECT 'ecos_pipeline_step', COUNT(*) FROM ecos_pipeline_step
UNION ALL SELECT 'sys_rule_version', COUNT(*) FROM sys_rule_version
UNION ALL SELECT 'compliance_rules(KG)', COUNT(*) FROM compliance_rules
UNION ALL SELECT 'kb_node/GraphNode', COUNT(*) FROM (
  SELECT 1 FROM kb_node UNION ALL SELECT 1 FROM kb_knowledge_node
) x
" 2>&1 | head -30

echo ""
echo "=== 现有索引 ==="
docker exec ecos-postgres psql -U postgres -d sys_man -c "
SELECT tablename, indexname, indexdef FROM pg_indexes
WHERE tablename IN ('td_datasource','td_data_resource','ecos_pipeline_task','ecos_pipeline_step','sys_rule_version','compliance_rules','kb_node','kb_knowledge_node','kb_knowledge_edge')
ORDER BY tablename, indexname" 2>&1 | head -60

echo ""
echo "=== PG 扩展 (是否 open_trgm) ==="
docker exec ecos-postgres psql -U postgres -d sys_man -c "SELECT name, default_version FROM pg_available_extensions WHERE name LIKE '%trgm%'" 2>&1
docker exec ecos-postgres psql -U postgres -d sys_man -c "SELECT extname FROM pg_extension WHERE extname='pg_trgm'" 2>&1

echo ""
echo "=== 相关慢查询统计 (pg_stat_user_tables) ==="
docker exec ecos-postgres psql -U postgres -d sys_man -c "
SELECT relname, seq_scan, seq_tup_read, idx_scan, idx_tup_fetch, n_live_tup
FROM pg_stat_user_tables
WHERE relname IN ('td_datasource','td_data_resource','ecos_pipeline_task','ecos_pipeline_step','sys_rule_version','compliance_rules','kb_node','kb_knowledge_node','kb_knowledge_edge')
ORDER BY seq_tup_read DESC" 2>&1 | head -30
