#!/bin/bash
# 完成表盘点
docker exec ecos-postgres psql -U postgres -d sys_man -c "
SELECT n.nspname, c.relname, c.reltuples::bigint AS approx_rows,
       pg_size_pretty(pg_total_relation_size(c.oid)) AS size
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE c.relkind='r' AND n.nspname='public'
  AND (c.relname LIKE '%compliance%' OR c.relname LIKE '%rule_version%' OR c.relname LIKE '%experts%' OR c.relname LIKE '%step%' OR c.relname LIKE '%pipeline%')
ORDER BY n.nspname, c.relname" 2>&1

echo ""
echo "=== g_cognition schema ==="
docker exec ecos-postgres psql -U postgres -d sys_man -c "
SELECT n.nspname, c.relname, c.reltuples::bigint AS approx_rows
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE c.relkind='r' AND (c.relname LIKE '%compliance%' OR c.relname LIKE '%rule_version%' OR c.relname LIKE '%kb_node%' OR c.relname LIKE '%step%' OR c.relname LIKE '%pipeline_task%')" 2>&1

echo ""
echo "=== 全部 schema 列表 ==="
docker exec ecos-postgres psql -U postgres -d sys_man -c "SELECT nspname FROM pg_namespace WHERE nspname NOT LIKE 'pg_%' AND nspname<>'information_schema'" 2>&1 | head -20
