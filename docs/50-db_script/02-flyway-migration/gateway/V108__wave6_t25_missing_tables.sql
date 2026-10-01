-- V108__wave6_t25_missing_tables.sql (PMO Wave6 T25 — T22 端点回归 5xx 修复)
-- PAT 18 WSL T22 回归中 15 个 500 的根因: PG relation does not exist + column does not exist。
-- 本迁移只加不删、全部幂等 (IF NOT EXISTS / ADD COLUMN IF NOT EXISTS)，重复执行无副作用。
--
-- 涉及 4 组根因:
--   1. /api/v1/ontology/proposals (arch 1/30/36) — ecos_ontology_proposals 表缺失
--   2. /api/v1/catalog/* + td_datasource (catalog 11/34 register) — td_catalog_item.tenant_id 列缺失
--   3. workflow terminate/resume (19-21) — ecos_workflow_instance.error_message 等列缺失
--   4. knowledge node/edge DQ (25/26) — ecos_knowledge_graph_node 表缺失

-- ============================================================
-- 1. 本体变更提案表 (OntologyProposalService/OntologyProposalController)
--    代码使用复数表名 ecos_ontology_proposals，字段与 V4.1 一致；
--    额外加 V4.3 乐观锁列 (OntologyProposalService 156/185/208 行依赖)。
-- ============================================================
CREATE TABLE IF NOT EXISTS ecos_ontology_proposals (
    id              BIGSERIAL PRIMARY KEY,
    domain_code     VARCHAR(128) NOT NULL,
    proposal_type   VARCHAR(32) NOT NULL,
    target_entity   VARCHAR(256),
    payload         JSONB NOT NULL,
    snapshot        JSONB,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    author          VARCHAR(64),
    reviewer        VARCHAR(64),
    reviewer_comment VARCHAR(512),
    version_id      BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
ALTER TABLE ecos_ontology_proposals ADD COLUMN IF NOT EXISTS optimistic_lock_version INTEGER NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_proposals_domain ON ecos_ontology_proposals(domain_code, status);
CREATE INDEX IF NOT EXISTS idx_proposals_author ON ecos_ontology_proposals(author);

-- ============================================================
-- 2. 数据目录索引表补 tenant_id 列 (CatalogServiceImpl.register INSERT 该列)
--    V19 原始 DDL 未含 tenant_id；代码 register() 显式写入 → column does not exist 500。
--    同时把 resource_id 唯一保证为 (tenant_id, resource_id)，兼容多租户注册。
-- ============================================================
ALTER TABLE td_catalog_item ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
-- V38 可能已加过 tenant_id，这里仅保证列存在（IF NOT EXISTS 幂等）

-- ============================================================
-- 3. 工作流实例表补列 (WorkflowInstanceRepository ROW_MAPPER 依赖)
--    生产库表结构旧于 V1.1，缺 error_message/current_node_id/context_json；
--    ROW_MAPPER 读 current_node_id、context_json，terminate/resume 写 error_message。
--    注: V1.1 原始名为 current_node_ids/context，此处按 Repository 实际读取的列名补齐。
-- ============================================================
ALTER TABLE ecos_workflow_instance ADD COLUMN IF NOT EXISTS error_message TEXT;
ALTER TABLE ecos_workflow_instance ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE ecos_workflow_instance ADD COLUMN IF NOT EXISTS current_node_id JSONB;
ALTER TABLE ecos_workflow_instance ADD COLUMN IF NOT EXISTS context_json JSONB;
ALTER TABLE ecos_workflow_instance ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE ecos_workflow_instance ADD COLUMN IF NOT EXISTS started_at TIMESTAMP;
ALTER TABLE ecos_workflow_instance ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;

-- ============================================================
-- 4. 知识图谱节点表 (KnowledgeNodeRepository MyBatis Mapper 依赖)
--    代码按 public schema + created_at TIMESTAMP 访问；生产库缺失该表。
-- ============================================================
CREATE TABLE IF NOT EXISTS ecos_knowledge_graph_node (
    id              VARCHAR(64) PRIMARY KEY,
    label           VARCHAR(512) NOT NULL,
    node_type       VARCHAR(64) NOT NULL,
    description     TEXT,
    properties_json JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_kg_node_type ON ecos_knowledge_graph_node(node_type);
CREATE INDEX IF NOT EXISTS idx_kg_node_created ON ecos_knowledge_graph_node(created_at DESC);

-- FAQ 408 痛点 4: knowledge node/edge 的 ProcessedAt → DQ links createdat (bigint epoch) 对齐。
-- 业务层按 BIGINT epoch 赋值时旧 TIMESTAMP 列报 5xx。只加新列兼容读写，不删旧列（schema 只加不删铁律）。
-- 若生产表尚不存在 processed_at 列则补充为 BIGINT；旧 created_at TIMESTAMP 保留。
ALTER TABLE ecos_knowledge_graph_node ADD COLUMN IF NOT EXISTS processed_at BIGINT;
