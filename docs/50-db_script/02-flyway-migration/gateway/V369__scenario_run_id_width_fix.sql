-- ============================================================================
-- V369 — 运行 id 宽度对齐（2026-11-26 场景工作台下一步落地 · 活体修复）
-- ============================================================================
-- 问题（活体取证）：问题运行主键形如 `run-<uuid>` = 40 字符，但
--   ecos_scenario_run_artifact.run_id / ecos_scenario_run_approval.run_id 为 VARCHAR(36)，
--   导致产物引用行 INSERT 报 "value too long for type character varying(36)"；
--   ecos_scenario_run.parent_run_id / sota_run_id 同宽也会限制谱系。
-- 处置：只加宽（ALTER COLUMN TYPE），不删列/不改语义；幂等（重复执行仍为 VARCHAR(64)）。
--
-- 纪律：IR03 只加不删；MC01/MC02/MC03；ST07 ecos_control；DR06/07/08；不建 FK（ST09）。
-- ============================================================================

ALTER TABLE ecos_control.ecos_scenario_run_artifact
    ALTER COLUMN run_id TYPE VARCHAR(64);

ALTER TABLE ecos_control.ecos_scenario_run_approval
    ALTER COLUMN run_id TYPE VARCHAR(64);

ALTER TABLE ecos_control.ecos_scenario_run
    ALTER COLUMN parent_run_id TYPE VARCHAR(64),
    ALTER COLUMN sota_run_id   TYPE VARCHAR(64);
