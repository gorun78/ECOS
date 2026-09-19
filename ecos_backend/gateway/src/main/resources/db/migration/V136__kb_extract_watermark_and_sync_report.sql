-- ============================================================
-- V136__kb_extract_watermark_and_sync_report.sql
-- 图谱实例抽取：水位线持久化表 + kg_sync_log 结构化报告列（PMO 批次 B3-2）
-- ============================================================
-- 来源: 肖国荣 / 日期: 2026-09-19 / 批次: B3-2（DW 层实例 → 知识图谱实例 抽取链路）
-- 依据: docs/plans/knowledge-workbench-replan-v1.md
--       §2.1 实例层（契约驱动抽取）+ §2.3 C1~C4 校验 + §6.1 K1 + §6.2 K4（水位线增量）
--       附录 A D2（实例数据无来源）+ 风险 R3（强制分页/水位线增量）
--
-- 背景:
--   1) K4 知识更新要求「DW 层数据变更 → 定时任务按水位线增量抽实例」，
--      需持久化「(ontologyId, entityCode, resourceId) → watermark」；
--   2) T2 要求实例抽取输出结构化报告（nodesCreated / edgesCreated / skipped /
--      invalidMappings / nextWatermark）并落地到既有 kg_sync_log 行，
--      供前端图谱构建 Tab 看到真实抽取结果（原 nodes/edges 恒 0）。
--
-- 表定位（均属 kb-engine 自有 schema ecos_knowledge，不跨引擎写）:
--   1. ecos_knowledge.kb_extract_watermark — 抽取水位线（唯一键 ontology_id + entity_code + resource_id）
--   2. ecos_knowledge.kg_sync_log.report   — 抽取报告 JSONB（V115 表只加列，不删不改）
--
-- Schema 铁律: 只加不删（铁律 3.1）+ IF NOT EXISTS 幂等
-- 执行方式: Flyway 已禁用（spring.flyway.enabled=false），本脚本为规范记录，
--           需人工执行到目标库（sys_man）后方可生效。
--           代码侧对水位线读写与 report 列写入均做「表/列缺失 → warn 不阻断」容错，
--           故未执行本脚本时抽取功能仍可用（增量退化为全量、报告仅计数）。
-- ============================================================

-- 1) 抽取水位线表（K4 增量基准）
CREATE TABLE IF NOT EXISTS ecos_knowledge.kb_extract_watermark (
    id          BIGSERIAL     PRIMARY KEY,
    ontology_id VARCHAR(64)   NOT NULL,
    entity_code VARCHAR(100)  NOT NULL,
    resource_id VARCHAR(64)   NOT NULL,
    watermark   VARCHAR(255),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_kb_extract_watermark UNIQUE (ontology_id, entity_code, resource_id)
);

CREATE INDEX IF NOT EXISTS idx_kb_extract_watermark_ontology
    ON ecos_knowledge.kb_extract_watermark (ontology_id);

COMMENT ON TABLE  ecos_knowledge.kb_extract_watermark              IS '图谱实例抽取水位线（B3-2 / K4 增量基准）';
COMMENT ON COLUMN ecos_knowledge.kb_extract_watermark.ontology_id  IS '本体业务 ID（kb_ontology_snapshot.ontology_id）';
COMMENT ON COLUMN ecos_knowledge.kb_extract_watermark.entity_code  IS '本体实体 code（ecos_entity_table_mapping.entity_code）';
COMMENT ON COLUMN ecos_knowledge.kb_extract_watermark.resource_id  IS 'DW 层数据资源 ID（td_data_resource.resource_id，只读消费）';
COMMENT ON COLUMN ecos_knowledge.kb_extract_watermark.watermark    IS '上次抽取收敛水位（DW 行读取端点的 nextWatermark）';

-- 2) kg_sync_log 结构化报告列（B3-2 T2：抽取报告落地，前端图谱构建 Tab 消费）
ALTER TABLE ecos_knowledge.kg_sync_log ADD COLUMN IF NOT EXISTS report JSONB;

COMMENT ON COLUMN ecos_knowledge.kg_sync_log.report
    IS '实例抽取结构化报告 JSONB: nodesCreated/edgesCreated/skipped/invalidMappings/nextWatermark/mode/... (B3-2)';
