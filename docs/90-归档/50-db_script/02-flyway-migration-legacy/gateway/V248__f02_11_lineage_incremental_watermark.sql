-- V248: F02-11 血缘自动派生 — 增量重建水位线列
-- 红线: IR03 只加不删 / IR04 显式列 / V248（V241/V247 已占用，取下一可用号）

BEGIN;

ALTER TABLE ecos_data.ecos_data_lineage_node
    ADD COLUMN IF NOT EXISTS last_incremental_sync_at TIMESTAMP;
COMMENT ON COLUMN ecos_data.ecos_data_lineage_node.last_incremental_sync_at
    IS '增量重建水位线：/api/v1/engine/data/lineage/topology/rebuild 增量模式执行完成后写入';

COMMIT;
