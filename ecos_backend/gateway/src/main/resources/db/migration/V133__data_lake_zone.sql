-- V133: 数据湖近源层 zone 列
--
-- 背景（数据湖分层规划）：
--   近源层（RAW）以 MinIO 数据湖为物理载体，内部分两个正交 zone：
--     STRUCTURED   — 结构化近源区，表数据 CSV 快照，按 dt=YYYY-MM-DD 日期分区
--     UNSTRUCTURED — 非结构化近源区，原始文档二进制，按 docId 寻址
--   两者格式要求/分区策略/消费方/生命周期完全正交，需在资源元数据上显式区分。
--
-- 对象 key 规范（与本次规划一致）：
--   raw/structured/{source}/{table}/dt=YYYY-MM-DD/{table}_{ts}.csv
--   raw/unstructured/{source}/{docId}/{originalFileName}
--
-- 合法性矩阵：
--   layer = RAW   ⟺ zone IN ('STRUCTURED','UNSTRUCTURED')
--   layer ≠ RAW   ⟹ zone IS NULL
--
-- 迁移方式：Flyway 已禁用（spring.flyway.enabled: false），本脚本手工执行。
-- 兼容性：仅增列，不改结构、不删列、不回填（遵守「schema 只加不删」）。

ALTER TABLE td_data_resource ADD COLUMN IF NOT EXISTS zone VARCHAR(16);

COMMENT ON COLUMN td_data_resource.zone IS
    '近源区: STRUCTURED=结构化近源 / UNSTRUCTURED=非结构化近源 / NULL=非近源层';
