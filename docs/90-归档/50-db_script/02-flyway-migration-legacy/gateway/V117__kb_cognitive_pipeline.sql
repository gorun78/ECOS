-- ============================================================
-- V117__kb_cognitive_pipeline.sql
-- 认知管线定义持久化 — 将 CognitivePipelineController 的
-- ConcurrentHashMap 内存存储迁入 PostgreSQL
-- ============================================================
-- 来源: PMO 内存态 PG 化任务
--
-- 背景:
--   CognitivePipelineController 使用 ConcurrentHashMap 在内存中
--   保存认知管线定义（nodes 集合），进程重启后全部丢失。
--
-- 产出:
--   kb_cognitive_pipeline 表 — 一行 = 一条认知管线定义
--   nodes 列以 JSONB 存储管线节点数组（nodeId/nodeType/config/dependsOn）
--
-- 铁律 3.1 遵循:
--   - schema 只加不删: 使用 CREATE TABLE IF NOT EXISTS
--   - 逻辑删除: is_deleted SMALLINT DEFAULT 0
-- ============================================================

CREATE TABLE IF NOT EXISTS kb_cognitive_pipeline (
    id          BIGSERIAL       PRIMARY KEY,
    pipeline_id VARCHAR(64)     NOT NULL UNIQUE,
    name        VARCHAR(256)    NOT NULL,
    status      VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',
    config      JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_by  VARCHAR(64),
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    result      JSONB           DEFAULT NULL,
    is_deleted  SMALLINT        NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_kb_cognitive_pipeline_status
    ON kb_cognitive_pipeline (status) WHERE is_deleted = 0;
CREATE INDEX IF NOT EXISTS idx_kb_cognitive_pipeline_created_at
    ON kb_cognitive_pipeline (created_at DESC) WHERE is_deleted = 0;

COMMENT ON TABLE  kb_cognitive_pipeline         IS '认知管线定义（CognitivePipeline 持久化，原 ConcurrentHashMap 内存态）';
COMMENT ON COLUMN kb_cognitive_pipeline.id            IS '自增主键';
COMMENT ON COLUMN kb_cognitive_pipeline.pipeline_id   IS '管线业务 ID (UUID)';
COMMENT ON COLUMN kb_cognitive_pipeline.name          IS '管线名称';
COMMENT ON COLUMN kb_cognitive_pipeline.status        IS '管线状态: DRAFT/ACTIVE/ARCHIVED';
COMMENT ON COLUMN kb_cognitive_pipeline.config        IS '管线节点集合 (JSONB: [{nodeId, nodeType, config, dependsOn}, ...])';
COMMENT ON COLUMN kb_cognitive_pipeline.created_by    IS '创建人';
COMMENT ON COLUMN kb_cognitive_pipeline.created_at    IS '创建时间';
COMMENT ON COLUMN kb_cognitive_pipeline.updated_at    IS '更新时间';
COMMENT ON COLUMN kb_cognitive_pipeline.result        IS '最近执行结果 (JSONB，可为空)';
COMMENT ON COLUMN kb_cognitive_pipeline.is_deleted    IS '逻辑删除标记: 0=未删除, 1=已删除';

-- 说明: 原内存数据无法迁移（纯进程内态，无持久化），
-- 故本 DDL 仅建表，不做数据迁移。
