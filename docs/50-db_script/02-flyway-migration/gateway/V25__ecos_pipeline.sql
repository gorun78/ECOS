-- ============================================================
-- V25__ecos_pipeline.sql — Pipeline 编排表
-- ============================================================
-- ecos_pipeline_definition 和 ecos_pipeline_execution 已存在
-- 本次新增 ecos_pipeline_node 表，并为 execution 补充字段

-- 1. Pipeline 节点表
CREATE TABLE IF NOT EXISTS ecos_pipeline_node (
    id              VARCHAR(64)     PRIMARY KEY,
    definition_id   VARCHAR(64)     NOT NULL,
    node_id         VARCHAR(64)     NOT NULL,
    type            VARCHAR(64)     NOT NULL DEFAULT 'TRANSFORM_SQL',
    config          JSONB           DEFAULT '{}',
    position_x      INTEGER         DEFAULT 0,
    position_y      INTEGER         DEFAULT 0,
    created_at      TIMESTAMP       DEFAULT NOW(),
    updated_at      TIMESTAMP       DEFAULT NOW()
);

COMMENT ON TABLE  ecos_pipeline_node               IS 'Pipeline 节点表 — DAG 中的执行节点';
COMMENT ON COLUMN ecos_pipeline_node.id             IS '节点主键';
COMMENT ON COLUMN ecos_pipeline_node.definition_id  IS '所属 Pipeline 定义 ID，关联 ecos_pipeline_definition.id';
COMMENT ON COLUMN ecos_pipeline_node.node_id        IS '前端节点标识';
COMMENT ON COLUMN ecos_pipeline_node.type           IS '节点类型: SOURCE_JDBC / TRANSFORM_SQL / OUTPUT_OBJECT';
COMMENT ON COLUMN ecos_pipeline_node.config         IS '节点配置(JSON): SQL语句、JDBC连接信息等';
COMMENT ON COLUMN ecos_pipeline_node.position_x     IS '画布 X 坐标';
COMMENT ON COLUMN ecos_pipeline_node.position_y     IS '画布 Y 坐标';
COMMENT ON COLUMN ecos_pipeline_node.created_at     IS '创建时间';
COMMENT ON COLUMN ecos_pipeline_node.updated_at     IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_pipeline_node_def ON ecos_pipeline_node(definition_id);

-- 2. 为 ecos_pipeline_execution 补充字段（若不存在）
ALTER TABLE ecos_pipeline_execution ADD COLUMN IF NOT EXISTS error_message  TEXT;
ALTER TABLE ecos_pipeline_execution ADD COLUMN IF NOT EXISTS rows_processed BIGINT DEFAULT 0;

-- 3. 为 ecos_pipeline_node 补充 depends_on 字段（DAG边）
ALTER TABLE ecos_pipeline_node ADD COLUMN IF NOT EXISTS depends_on JSONB DEFAULT '[]';
COMMENT ON COLUMN ecos_pipeline_node.depends_on IS '依赖的节点ID列表（JSON数组），用于拓扑排序';

-- 4. Pipeline 边表（冗余存储，方便查询）
CREATE TABLE IF NOT EXISTS ecos_pipeline_edge (
    id              VARCHAR(64)     PRIMARY KEY,
    definition_id   VARCHAR(64)     NOT NULL,
    from_node_id    VARCHAR(64)     NOT NULL,
    to_node_id      VARCHAR(64)     NOT NULL,
    created_at      TIMESTAMP       DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_pipeline_edge_def ON ecos_pipeline_edge(definition_id);
