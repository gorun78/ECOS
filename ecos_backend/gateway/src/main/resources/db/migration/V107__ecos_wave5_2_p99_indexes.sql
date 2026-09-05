-- Wave-5.2 T20: P99 API < 500ms G4 — 5 hot-path 索引 + pg_trgm 启用
-- 对应 5 个慢端点 (详见 docs/08-产品化重构方案/17-Wave5.2-T20-P99-optimization.md):
--   E2 GET /api/v1/datanet/datasource           → E2 DataSourceServiceImpl.findAll ORDER BY create_time DESC
--   E3 GET /api/v1/engine/data/pipeline/tasks   → E3 PipelineTaskServiceImpl.listTasks ORDER BY updated_at DESC LIMIT/OFFSET
--   E4 POST/GET /api/v1/knowledge/compliance-rules → ComplianceRuleMapper.findByDomain / findByStatus
--   E5 GET /api/v1/knowledge/search?q=xxx       → KnowledgeNodeMapper.searchByLabelPattern (ILIKE %word%)
-- 当前生产数据 < 1000 行, planner 仍可能选 Seq Scan (cost 估算), 但小表不慢。
-- 5000+ 行后 GIN/btree 立即被 SELECT (本次 EXPLAIN 已验证)。
-- 不阻断: 若同名索引对象在多 schema 不存在, IF NOT EXISTS 静默跳过。

-- E5: graph_node.label GIN trigram (pg_trgm)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_graph_node_label_trgm        ON ecos_knowledge.graph_node USING gin (label gin_trgm_ops);

-- E3: pipeline_task 排序分页
CREATE INDEX IF NOT EXISTS idx_ecos_pipeline_task_updated_at ON ecos_pipeline_task (updated_at DESC);

-- E4: compliance_rule 两维度查询
CREATE INDEX IF NOT EXISTS idx_sys_compliance_rule_domain    ON sys_compliance_rule (domain);
CREATE INDEX IF NOT EXISTS idx_sys_compliance_rule_status    ON sys_compliance_rule (status);

-- E2: datasource 默认排序
CREATE INDEX IF NOT EXISTS idx_td_datasource_create_time     ON td_datasource (create_time DESC);
