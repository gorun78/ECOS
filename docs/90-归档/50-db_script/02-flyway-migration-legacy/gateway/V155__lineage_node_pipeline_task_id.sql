-- V155: Data Lineage — 为 lineage_node 补齐 pipeline_task_id 列（前端"追溯来源"显示）
-- 现状: V58 只定义了 lineage_node (id/node_type/name/schema_name/table_name/datasource_id/layer/properties/timestamps)，
--       但 DataLineageService.persistParsed 已写入 pipeline_task_id（见 L560 INSERT），
--       getTopologyFromDb 也 SELECT 该列，导致 bad SQL grammar。
-- 打列: IR03 红线 "never 删/改已有" — 只加列；DDL 落运行时 `CREATE TABLE IF NOT EXISTS` 之外独立 ALTER。

BEGIN;

ALTER TABLE ecos_data.ecos_data_lineage_node
    ADD COLUMN IF NOT EXISTS pipeline_task_id VARCHAR(64);
COMMENT ON COLUMN ecos_data.ecos_data_lineage_node.pipeline_task_id IS '来源 pipeline definition/task ID';

CREATE INDEX IF NOT EXISTS idx_lineage_node_task
    ON ecos_data.ecos_data_lineage_node(pipeline_task_id) WHERE pipeline_task_id IS NOT NULL;

COMMIT;
