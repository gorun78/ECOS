-- ============================================================
-- V314__kb_graph_ontology_traceability_apply.sql
-- B28（2026-11-04）：落 V134 图谱溯源列的 dev 库实跑版
-- ============================================================
-- 背景（实证）：
--   V134（2026-09-19）定义了 ecos_knowledge.graph_node / graph_edge 的 4 个本体溯源列
--   （ontology_id / ontology_version / source_resource_id / source_pk），但 dev 库实测
--   8 列全部缺失（information_schema 0 命中）。后果：
--     ① 本体发布事件 → KgMapperService 版本对齐 UPDATE graph_node SET ontology_version → 42703；
--     ② 实例抽取 upsert INTO graph_node(ontology_id, ...) → 42703。
--   两份代码都按 V134 后的形态书写，属"脚本未实跑"而非设计变更。
--
-- 与 V134 的关系：
--   本脚本 = V134 的**幂等重放**（ADD COLUMN IF NOT EXISTS 全量 + 同名索引 + COMMENT），
--   不新增列、不改既有列、不动表数据；对已跑过 V134 的环境为 no-op。
--   之所以另起 V314 而非重跑 V134：迁移单源按版本号单调推进，V134 < 已实跑 MAX，
--   重放需新的记录锚点（MigrationVersionUniquenessTest 同批升至 314/315）。
--
-- 纪律：IR03 只加不删；DR01 schema 限定；DR06/DR07/DR08 基线列不在本脚本面
--      （graph_node/graph_edge 为 knownLegacy 存量表，基线列已由 V180/V181 补列）。
-- 回滚说明：8 列均为可空新增列；本批写入的溯源值仅为元数据，回滚 = 逐列
--          `ALTER TABLE ... DROP COLUMN IF EXISTS ...`（按 IR03 不主动执行，登记即可）。
-- ============================================================

-- 1) graph_node 溯源列
ALTER TABLE ecos_knowledge.graph_node ADD COLUMN IF NOT EXISTS ontology_id        VARCHAR(64);
ALTER TABLE ecos_knowledge.graph_node ADD COLUMN IF NOT EXISTS ontology_version   VARCHAR(128);
ALTER TABLE ecos_knowledge.graph_node ADD COLUMN IF NOT EXISTS source_resource_id VARCHAR(64);
ALTER TABLE ecos_knowledge.graph_node ADD COLUMN IF NOT EXISTS source_pk          VARCHAR(255);

-- 2) graph_edge 溯源列
ALTER TABLE ecos_knowledge.graph_edge ADD COLUMN IF NOT EXISTS ontology_id        VARCHAR(64);
ALTER TABLE ecos_knowledge.graph_edge ADD COLUMN IF NOT EXISTS ontology_version   VARCHAR(128);
ALTER TABLE ecos_knowledge.graph_edge ADD COLUMN IF NOT EXISTS source_resource_id VARCHAR(64);
ALTER TABLE ecos_knowledge.graph_edge ADD COLUMN IF NOT EXISTS source_pk          VARCHAR(255);

-- 3) 版本对齐索引
CREATE INDEX IF NOT EXISTS idx_graph_node_ontology_version
    ON ecos_knowledge.graph_node (ontology_id, ontology_version);
CREATE INDEX IF NOT EXISTS idx_graph_edge_ontology_version
    ON ecos_knowledge.graph_edge (ontology_id, ontology_version);

-- 4) 类型索引（Q4 裁决：类型作为节点属性 + 类型索引，不建类型节点表）
CREATE INDEX IF NOT EXISTS idx_graph_node_node_type
    ON ecos_knowledge.graph_node (node_type);

COMMENT ON COLUMN ecos_knowledge.graph_node.ontology_id        IS '溯源: 本体业务 ID (V134/B28)';
COMMENT ON COLUMN ecos_knowledge.graph_node.ontology_version   IS '溯源: 生成该节点时的本体版本 (V134/B28)';
COMMENT ON COLUMN ecos_knowledge.graph_node.source_resource_id IS '溯源: 实例抽取来源 DW 层数据资源 ID (V134/B28)';
COMMENT ON COLUMN ecos_knowledge.graph_node.source_pk          IS '溯源: 实例抽取来源 DW 表主键值 (V134/B28)';
COMMENT ON COLUMN ecos_knowledge.graph_edge.ontology_id        IS '溯源: 本体业务 ID (V134/B28)';
COMMENT ON COLUMN ecos_knowledge.graph_edge.ontology_version   IS '溯源: 生成该边时的本体版本 (V134/B28)';
COMMENT ON COLUMN ecos_knowledge.graph_edge.source_resource_id IS '溯源: 实例抽取来源 DW 层数据资源 ID (V134/B28)';
COMMENT ON COLUMN ecos_knowledge.graph_edge.source_pk          IS '溯源: 实例抽取来源 DW 表主键值 (V134/B28)';

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [IR03] 零 DROP / 零 ALTER TYPE / 零 RENAME；8 列 + 3 索引，全部 IF NOT EXISTS 幂等 ✓
-- [DR01] 全部 schema 限定 ecos_knowledge ✓  [ST07] 知识引擎 schema 自有表 ✓
-- [MC01] 无新表；存量 id 宽度不改（固化列不加主键）✓
