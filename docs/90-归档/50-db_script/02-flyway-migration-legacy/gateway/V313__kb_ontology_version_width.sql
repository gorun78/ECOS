-- ============================================================
-- V313__kb_ontology_version_width.sql — 知识图谱 ontology_version 宽度对齐
-- 追溯: 年度经营预测 E2E 取证（2026-11-04）：知识同步 500
--       `ECOS-KB-050 … value too long for type character varying(20)`
-- 根因: KgMapperDao.alignNodeVersion/alignEdgeVersion 把「本体版本号」写入
--       graph_node/graph_edge.ontology_version，而本体域版本列宽为 128
--       （ecos_ontology.version varchar(128)），知识域同名语义列仅 20 ⇒ 写入口径一致、列宽不一致。
-- 修法: 仅加宽（20 → 128），语义不变、存量数据不受影响、可回滚为更小宽度（需先确认无超长值）。
-- 纪律: DR01~DR08 / ST07 / IR02 / IR03(只加不删)
-- ============================================================

ALTER TABLE ecos_knowledge.ecos_kb_graph_node
    ALTER COLUMN ontology_version TYPE VARCHAR(128);

ALTER TABLE ecos_knowledge.ecos_kb_graph_edge
    ALTER COLUMN ontology_version TYPE VARCHAR(128);

ALTER TABLE ecos_knowledge.ecos_kb_extract_candidate
    ALTER COLUMN ontology_version TYPE VARCHAR(128);

-- 同步台账：object_type 既存领域码（entmgmt）也存本体 id（32 位），20 位不够（实测 500 的直接触发点）
ALTER TABLE ecos_knowledge.ecos_kg_sync_log
    ALTER COLUMN object_type TYPE VARCHAR(128);

-- ── 回滚说明 ──────────────────────────────────────────────
-- 回滚须先确认三表 ontology_version 无长度 > 20 的值，再执行：
--   ALTER TABLE ecos_knowledge.ecos_kb_graph_node ALTER COLUMN ontology_version TYPE VARCHAR(20);
--   ALTER TABLE ecos_knowledge.ecos_kb_graph_edge ALTER COLUMN ontology_version TYPE VARCHAR(20);
--   ALTER TABLE ecos_knowledge.ecos_kb_extract_candidate ALTER COLUMN ontology_version TYPE VARCHAR(20);
-- 幂等性：PG 对同一目标类型的 ALTER 可重复执行。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] schema 限定 ecos_knowledge. ✓ [DR02/03] 无新表 ✓ [DR04] 无 JSONB ✓ [DR05] 无布尔列 ✓
-- [DR06~08] 不涉及基线列增删 ✓ [MC01~03] 不涉及主键/金额 ✓ [ST07] ecos_knowledge 属五引擎 schema ✓
-- [IR02] 仅文件 ✓ [IR03] 只加宽不删列 ✓
