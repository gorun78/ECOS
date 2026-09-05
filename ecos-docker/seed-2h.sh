#!/bin/bash
# 临时: 完整种子 (compliance_rule 50 + graph_node 200 + knowledge_article 100 + audit_event 100)
set +e
PSQL="docker exec -i ecos-postgres psql -U postgres -d sys_man"
LOG=/tmp/ecos-soak-seed.log
exec 1> $LOG 2>&1
echo "=== START $(date) ==="

echo "1) 看现有内容"
$PSQL -tA -c "SELECT 'compliance', COUNT(*) FROM sys_compliance_rule UNION ALL SELECT 'graph_node', COUNT(*) FROM ecos_knowledge.graph_node UNION ALL SELECT 'source', COUNT(*) FROM ecos_data.source UNION ALL SELECT 'users', COUNT(*) FROM users WHERE enabled" 2>&1

echo ""
echo "2) 灌 compliance_rule 50"
$PSQL << 'EOF' 2>&1
INSERT INTO sys_compliance_rule (id, name, domain, rule_type, condition, action, priority, enabled, description, status, required_fact_list, effective_date, expiry_date, version, created_at, updated_at)
SELECT
  'cr-' || i,
  'Rule-' || i,
  (ARRAY['finance','sales','operations','risk'])[1 + (i % 4)],
  (ARRAY['causal','threshold','permission','audit'])[1 + (i % 4)],
  '$metric = threshold',
  'emit_alert',
  10 + (i % 100),
  TRUE,
  'Rule description ' || i,
  'active',
  NULL,
  NOW() - INTERVAL '1 day',
  NOW() + INTERVAL '365 day',
  1,
  NOW(),
  NOW()
FROM generate_series(1, 50) AS i
ON CONFLICT (id) DO NOTHING;
SELECT COUNT(*) AS rules_total FROM sys_compliance_rule;
EOF

echo ""
echo "3) 灌 graph_node 200"
$PSQL << 'EOF' 2>&1
INSERT INTO ecos_knowledge.graph_node (id, label, node_type, description, properties, domain, created_at, updated_at)
SELECT
  'gn-' || i,
  'Metric-' || i,
  (ARRAY['Concept','Entity','Attribute'])[1 + (i % 3)],
  'demo entity ' || i,
  '{}'::jsonb,
  (ARRAY['finance','sales','operations'])[1 + (i % 3)],
  NOW(),
  NOW()
FROM generate_series(1, 200) AS i
ON CONFLICT (id) DO NOTHING;
SELECT COUNT(*) AS graph_nodes FROM ecos_knowledge.graph_node;
EOF

echo ""
echo "4) 灌 knowledge_article 100 (验证存在)"
$PSQL << 'EOF' 2>&1
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='ecos_knowledge' AND table_name='knowledge_article') THEN
    CREATE TABLE ecos_knowledge.knowledge_article (
      id VARCHAR(64) PRIMARY KEY,
      space_id VARCHAR(64),
      title VARCHAR(256),
      content TEXT,
      author VARCHAR(64),
      status VARCHAR(32) DEFAULT 'published',
      created_at TIMESTAMP DEFAULT NOW(),
      updated_at TIMESTAMP DEFAULT NOW()
    );
  END IF;
END
$$;
INSERT INTO ecos_knowledge.knowledge_article (id, space_id, title, content, author, status)
SELECT
  'ka-' || i,
  'space-' || (i % 10),
  'Article-' || i,
  repeat('lorem ipsum ', 50),
  'seed',
  'published'
FROM generate_series(1, 100) AS i
ON CONFLICT (id) DO NOTHING;
SELECT COUNT(*) AS articles FROM ecos_knowledge.knowledge_article;
EOF

echo ""
echo "5) 验证 admin login"
curl -s -o /dev/null -w "  admin login HTTP=%{http_code}\n" -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'

echo ""
echo "6) final summary"
$PSQL -tA -c "SELECT 'compliance', COUNT(*) FROM sys_compliance_rule UNION ALL SELECT 'graph_node', COUNT(*) FROM ecos_knowledge.graph_node UNION ALL SELECT 'graph_edge', COUNT(*) FROM ecos_knowledge.graph_edge UNION ALL SELECT 'source', COUNT(*) FROM ecos_data.source UNION ALL SELECT 'users', COUNT(*) FROM users WHERE enabled UNION ALL SELECT 'kb_article', COUNT(*) FROM ecos_knowledge.knowledge_article" 2>&1

echo "=== END $(date) ==="
echo "size=$(wc -l < $LOG)"
