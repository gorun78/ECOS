-- =====================================================
-- V105: 数据源元数据获取策略 (PMO-37)
-- td_datasource 增加 metadata_config (JSONB) + last_collect_time
-- 只加不删，兼容既有行（DEFAULT '{}' / NULL）
-- =====================================================

ALTER TABLE td_datasource ADD COLUMN IF NOT EXISTS metadata_config JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE td_datasource ADD COLUMN IF NOT EXISTS last_collect_time TIMESTAMP;

COMMENT ON COLUMN td_datasource.metadata_config IS '元数据获取策略配置: {strategy, includeRowCount, countMethod, scheduleCron, cacheTtlMinutes, onSourceEdit}';
COMMENT ON COLUMN td_datasource.last_collect_time IS '最近一次元数据采集完成时间';
