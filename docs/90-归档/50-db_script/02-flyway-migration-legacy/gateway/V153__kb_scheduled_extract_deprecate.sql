-- V153__kb_scheduled_extract_deprecate.sql — PMO-73 W3 / TB-1: 弃自建定时任务表
-- 池: sys_man (schema ecos_knowledge)
-- 铁律 §1.6-2 弃自建定时任务表: 已迁 td_runtime_task_plan + ITaskManagementService
-- 策略: R9 只加不删 (§3.1) —— 保留物理表与列，仅标 deprecate + 加回退标记列
-- 旧数据回退读: kb_scheduled_extract 仅读不写，新行统一走 td_runtime_task_plan
-- 幂等: 所有 DDL 使用 IF NOT EXISTS / COMMENT 重复调用安全
-- 上线: psql -U postgres -d sys_man -f V153__kb_scheduled_extract_deprecate.sql

COMMENT ON TABLE ecos_knowledge.kb_scheduled_extract IS
  'DEPRECATED (V153, 2026-09-22, TB-1): 由 td_runtime_task_plan + ITaskManagementService 代替; 历史行只读不写; 数据修复走一次性脚本; R9 只加不删';

ALTER TABLE ecos_knowledge.kb_scheduled_extract
    ADD COLUMN IF NOT EXISTS prefers_runtime_task VARCHAR(64) NOT NULL DEFAULT 'td_runtime_task_plan';

CREATE INDEX IF NOT EXISTS idx_kb_scheduled_extract_paused ON ecos_knowledge.kb_scheduled_extract (is_paused) WHERE is_paused;

COMMENT ON COLUMN ecos_knowledge.kb_scheduled_extract.prefers_runtime_task IS
  'TB-1 迁移标记: 旧行回填 td_runtime_task_plan 草稿; 新行统一写 td_runtime_task_plan (single source of truth)';
