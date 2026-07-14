-- ============================================================
-- V25.1__ecos_pipeline_node_fix.sql — 确保 ecos_pipeline_node 表存在
-- ============================================================
-- 问题：创建 Pipeline 定义后查询节点表报 bad SQL grammar
-- 根因：ecos_pipeline_node 表可能未创建或列类型不匹配
-- 修复：幂等地创建表（IF NOT EXISTS）+ 补充缺少列

CREATE TABLE IF NOT EXISTS ecos_pipeline_node (
    id              VARCHAR(64)     PRIMARY KEY,
    definition_id   VARCHAR(64)     NOT NULL,
    node_id         VARCHAR(64)     NOT NULL,
    type            VARCHAR(64)     NOT NULL DEFAULT 'TRANSFORM_SQL',
    config          JSONB           DEFAULT '{}',
    depends_on      JSONB           DEFAULT '[]',
    position_x      INTEGER         DEFAULT 0,
    position_y      INTEGER         DEFAULT 0,
    created_at      TIMESTAMP       DEFAULT NOW(),
    updated_at      TIMESTAMP       DEFAULT NOW()
);

-- 补充可能缺失的列（幂等）
ALTER TABLE ecos_pipeline_node ADD COLUMN IF NOT EXISTS depends_on JSONB DEFAULT '[]';

CREATE INDEX IF NOT EXISTS idx_pipeline_node_def ON ecos_pipeline_node(definition_id);
