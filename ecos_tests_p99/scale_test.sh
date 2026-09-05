#!/bin/bash
# 5000 rows → EXPLAIN for index adoption
set -e
run_sql() {
  local sql="$1"
  # psql -c 不支持从 stdin 接收多行 SQL, 用 -c 单引号包裹
  docker exec ecos-postgres psql -U postgres -d sys_man -c "$sql"
}

echo "=== BEFORE counts ==="
docker exec ecos-postgres psql -U postgres -d sys_man -c "SELECT (SELECT COUNT(*) FROM ecos_pipeline_task) AS pipeline_task, (SELECT COUNT(*) FROM ecos_knowledge.graph_node) AS graph_node, (SELECT COUNT(*) FROM sys_compliance_rule) AS compliance_rule;"

echo "=== inject 5000 rows each ==="
echo "  task:"
docker exec ecos-postgres psql -U postgres -d sys_man -c "
  INSERT INTO ecos_pipeline_task (id, name, description, task_type, status, yaml_content, config_json, created_at, updated_at)
  SELECT 'bench-p99-t-'||g.id, 'bench-p99-task-'||g.id, 'p99-bench', 'ETL', 'ACTIVE', 'yaml: bench', '{\"a\": 1}'::jsonb,
         (NOW() - (random()*432000)::bigint * INTERVAL '1 second'),
         (NOW() - (random()*432000)::bigint * INTERVAL '1 second')
  FROM generate_series(1, 5000) AS g(id)"
echo "  graph_node:"
docker exec ecos-postgres psql -U postgres -d sys_man -c "
  INSERT INTO ecos_knowledge.graph_node (id, label, node_type, description, properties, domain, created_at, updated_at)
  SELECT 'bench-kg-p99-'||g.id, 'p99-bench-label-'||g.id, 'Concept', 'p99', '{}', 'finance', NOW(), NOW()
  FROM generate_series(1, 5000) AS g(id)"
echo "  compliance_rule:"
docker exec ecos-postgres psql -U postgres -d sys_man -c "
  INSERT INTO sys_compliance_rule (id, name, domain, rule_type, condition, action, priority, enabled, description, status, version, created_at, updated_at)
  SELECT 'bench-cpr-p99-'||g.id, 'p99-bench-rule-'||g.id,
         CASE g.id % 6 WHEN 0 THEN 'finance' WHEN 1 THEN 'hr' WHEN 2 THEN 'data' WHEN 3 THEN 'finance' WHEN 4 THEN 'sec' ELSE 'ops' END,
         'EXPRESSION', 'x>1', 'flag', 1, true, 'p99', 'DRAFT', 1, NOW(), NOW()
  FROM generate_series(1, 5000) AS g(id)"

echo "=== AFTER counts ==="
docker exec ecos-postgres psql -U postgres -d sys_man -c "SELECT (SELECT COUNT(*) FROM ecos_pipeline_task) AS pipeline_task, (SELECT COUNT(*) FROM ecos_knowledge.graph_node) AS graph_node, (SELECT COUNT(*) FROM sys_compliance_rule) AS compliance_rule;"

echo ""
echo "=== EXPLAIN (5000+ rows) ==="
echo ""
echo "--- E3: pipeline_task order by updated_at limit 50 ---"
docker exec ecos-postgres psql -U postgres -d sys_man -c "EXPLAIN ANALYZE SELECT id FROM ecos_pipeline_task ORDER BY updated_at DESC LIMIT 50"

echo ""
echo "--- E5: graph_node ILIKE %word% (trigram GIN should kick in) ---"
docker exec ecos-postgres psql -U postgres -d sys_man -c "EXPLAIN ANALYZE SELECT id FROM ecos_knowledge.graph_node WHERE label ILIKE '%label-123%'"

echo ""
echo "--- E4: compliance findByStatus ---"
docker exec ecos-postgres psql -U postgres -d sys_man -c "EXPLAIN ANALYZE SELECT id FROM sys_compliance_rule WHERE status='DRAFT'"

echo ""
echo "--- E4: compliance findByDomain ---"
docker exec ecos-postgres psql -U postgres -d sys_man -c "EXPLAIN ANALYZE SELECT id FROM sys_compliance_rule WHERE domain='finance'"

echo ""
echo "=== cleanup ==="
docker exec ecos-postgres psql -U postgres -d sys_man -c "DELETE FROM ecos_pipeline_task WHERE id LIKE 'bench-p99-t-%'"
docker exec ecos-postgres psql -U postgres -d sys_man -c "DELETE FROM ecos_knowledge.graph_node WHERE id LIKE 'bench-kg-p99-%'"
docker exec ecos-postgres psql -U postgres -d sys_man -c "DELETE FROM sys_compliance_rule WHERE name LIKE 'p99-bench-rule-%'"

echo "=== FINAL counts ==="
docker exec ecos-postgres psql -U postgres -d sys_man -c "SELECT (SELECT COUNT(*) FROM ecos_pipeline_task) AS pipeline_task, (SELECT COUNT(*) FROM ecos_knowledge.graph_node) AS graph_node, (SELECT COUNT(*) FROM sys_compliance_rule) AS compliance_rule;"
