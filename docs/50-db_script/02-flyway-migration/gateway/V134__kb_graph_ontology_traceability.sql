-- ============================================================
-- V134__kb_graph_ontology_traceability.sql
-- 图谱节点/边 本体版本溯源列（D9）+ 类型索引（Q4 裁决）
-- ============================================================
-- 来源: 肖国荣 / 日期: 2026-09-19 / 批次: B1（图谱骨架修复 + 版本对齐）
-- 依据: docs/plans/knowledge-workbench-replan-v1.md
--       §2.2 版本对齐机制 + §6.2 K3 知识存储 + 附录 A D9
--
-- 背景:
--   D9 —— 图谱节点/边没有本体版本对齐机制，本体发布新版本后无法判定
--   「哪些图谱记录基于旧版本生成」。B1 为 graph_node / graph_edge 补 4 个溯源列，
--   对齐基准 = ecos_knowledge.kb_ontology_snapshot（ontology_id + version + SHA-256，V116）。
--     1. ontology_id        — 本体业务 ID（对齐主键）
--     2. ontology_version   — 生成该记录时的本体版本
--     3. source_resource_id — 实例抽取来源的 DW 层数据资源 ID（B3 实例抽取写入）
--     4. source_pk          — 实例抽取来源的 DW 表主键值（B3 实例抽取写入）
--
-- Q4 裁决（骨架范围）:
--   不建独立「类型节点」——类型只作为节点属性（graph_node.node_type）+ 类型索引。
--   故本脚本补 idx_graph_node_node_type，不新增任何类型节点表。
--
-- Schema 铁律: 只加不删（铁律 3.1）+ ADD COLUMN IF NOT EXISTS 幂等
-- 执行方式: Flyway 已禁用（spring.flyway.enabled=false），本脚本为规范记录，
--           需人工执行到目标库（sys_man）后方可生效。
--
-- 列类型与既有列宽度对齐: id/label/node_type 为 VARCHAR(64)，
-- 故 ontology_id / source_resource_id 取 VARCHAR(64)，version 与
-- kb_ontology_snapshot.version 对齐取 VARCHAR(128)，source_pk 取 VARCHAR(255)。
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

-- 3) 版本对齐索引: 按 ontology_id + ontology_version 定位「未对齐/待重建」记录
CREATE INDEX IF NOT EXISTS idx_graph_node_ontology_version
    ON ecos_knowledge.graph_node (ontology_id, ontology_version);
CREATE INDEX IF NOT EXISTS idx_graph_edge_ontology_version
    ON ecos_knowledge.graph_edge (ontology_id, ontology_version);

-- 4) 类型索引（Q4: 类型作为节点属性 + 类型索引，不建类型节点）
CREATE INDEX IF NOT EXISTS idx_graph_node_node_type
    ON ecos_knowledge.graph_node (node_type);

COMMENT ON COLUMN ecos_knowledge.graph_node.ontology_id        IS '溯源: 本体业务 ID (kb_ontology_snapshot.ontology_id, D9)';
COMMENT ON COLUMN ecos_knowledge.graph_node.ontology_version   IS '溯源: 生成该节点时的本体版本 (kb_ontology_snapshot.version, D9)';
COMMENT ON COLUMN ecos_knowledge.graph_node.source_resource_id IS '溯源: 实例抽取来源 DW 层数据资源 ID (B3 写入)';
COMMENT ON COLUMN ecos_knowledge.graph_node.source_pk          IS '溯源: 实例抽取来源 DW 表主键值 (B3 写入)';
COMMENT ON COLUMN ecos_knowledge.graph_edge.ontology_id        IS '溯源: 本体业务 ID (kb_ontology_snapshot.ontology_id, D9)';
COMMENT ON COLUMN ecos_knowledge.graph_edge.ontology_version   IS '溯源: 生成该边时的本体版本 (kb_ontology_snapshot.version, D9)';
COMMENT ON COLUMN ecos_knowledge.graph_edge.source_resource_id IS '溯源: 实例抽取来源 DW 层数据资源 ID (B3 写入)';
COMMENT ON COLUMN ecos_knowledge.graph_edge.source_pk          IS '溯源: 实例抽取来源 DW 表主键值 (B3 写入)';
