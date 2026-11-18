-- ============================================================
-- V321__legacy_cache_config_rows_cleanup.sql
-- D-2：清退零读取方的归口外缓存配置存量（仅数据行，零 DDL）
-- ============================================================
-- 依据（2026-11-07 验证报告 D-2）：
--   代码面 12 条归口外键已清零（B 面 4 条 + A 面 8 条 MyBatis 开关）；
--   本脚本清退 live 侧仍有行、但当前零读取方的 4 条历史配置：
--     g1-data-foundation / cache_ttl_seconds
--     data-engine        / dw.cache.ttl_seconds
--     data-engine        / dw.lineage.cache_ttl_minutes（前端面板字段已同批删除）
--     ontology-engine    / ontology.cache.ttl_seconds
--   底座四键（config_group=cache 的 ecos.cache.*）不在本脚本范围。
--
-- 幂等：重复执行零命中。
-- 回滚：执行前 pg_dump 快照（sys_config 单表）可原样恢复。
-- ============================================================

DELETE FROM ecos_control.sys_config
 WHERE (config_group = 'g1-data-foundation' AND config_key = 'cache_ttl_seconds')
    OR (config_group = 'data-engine' AND config_key IN ('dw.cache.ttl_seconds', 'dw.lineage.cache_ttl_minutes'))
    OR (config_group = 'ontology-engine' AND config_key = 'ontology.cache.ttl_seconds');

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [IR03] 零 DROP TABLE / 零 ALTER / 零 CREATE；仅 DELETE 数据行 ✓
-- [DR01] schema 限定 ecos_control ✓  [ST07] 主控制 schema ✓
-- [语义] 四行均为零读取方存量；底座 cache 组四键不动 ✓
