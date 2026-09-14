-- ============================================================
-- V113__ecos_dq_monitoring.sql
-- PMO-48-C T11: DQ 监控调度计划 + 限流配置
-- ============================================================
-- 来源: PMO-48-C-数据质量监控告警与工单.md T11
--
-- 产出:
--   dq_schedule  — 监控调度计划 (SCHEDULE/EVENT/MANUAL 三种触发)
--   dq_throttle  — 限流配置 (每 scope 每日上限, 防雪崩)
--
-- 铁律 3.1 遵循:
--   - schema 只加不删: 所有 CREATE 用 IF NOT EXISTS
--   - 不修改 dq_rule / dq_rule_check 已有列 (V111/V112)
-- ============================================================

CREATE SCHEMA IF NOT EXISTS ecos_dq;

-- ============================================================
-- 1. dq_schedule — 监控调度计划
-- ============================================================
-- 一条调度 = 触发方式 + 一批规则 (rule_ids JSONB) + 作用域 (scope)
-- T11 执行器遍历 enabled 且 trigger_type='SCHEDULE' 的计划调 runRuleBatch
CREATE TABLE IF NOT EXISTS ecos_dq.dq_schedule (
    id                  VARCHAR(64)     PRIMARY KEY,
    name                VARCHAR(191)    NOT NULL,
    trigger_type        VARCHAR(16)     NOT NULL,         -- SCHEDULE / EVENT / MANUAL
    cron_expression     VARCHAR(64),                       -- trigger_type=SCHEDULE 时必填
    event_type          VARCHAR(64),                       -- trigger_type=EVENT 时的事件类型 (如 PIPELINE_EXECUTION_SUCCEEDED)
    rule_ids            JSONB           NOT NULL DEFAULT '[]'::jsonb,
    scope_type          VARCHAR(16)     DEFAULT 'DATASOURCE',  -- FIELD/TABLE/DATASOURCE/DOMAIN/SYSTEM
    scope_id            VARCHAR(64),
    enabled             BOOLEAN         NOT NULL DEFAULT TRUE,
    max_runtime_seconds INT             DEFAULT 60,
    created_by          VARCHAR(128),
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    is_deleted          BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_dq_schedule_enable
    ON ecos_dq.dq_schedule (trigger_type) WHERE enabled AND is_deleted = FALSE;

COMMENT ON TABLE  ecos_dq.dq_schedule IS 'DQ 监控调度计划（PMO-48-C T11，接 runtime-task）';
COMMENT ON COLUMN ecos_dq.dq_schedule.id IS '调度 ID (VARCHAR(64) PK)';
COMMENT ON COLUMN ecos_dq.dq_schedule.trigger_type IS '触发类型: SCHEDULE=定时 / EVENT=Pipeline事件 / MANUAL=仅手动';
COMMENT ON COLUMN ecos_dq.dq_schedule.cron_expression IS 'cron 表达式 (trigger_type=SCHEDULE 时必填)';
COMMENT ON COLUMN ecos_dq.dq_schedule.event_type IS '事件类型常量 (trigger_type=EVENT 时必填, 如 PIPELINE_EXECUTION_SUCCEEDED)';
COMMENT ON COLUMN ecos_dq.dq_schedule.rule_ids IS '关联规则 ID 列表 JSONB ["ruleId1","ruleId2"]';
COMMENT ON COLUMN ecos_dq.dq_schedule.scope_type IS '限流作用域类型: FIELD/TABLE/DATASOURCE/DOMAIN/SYSTEM (缺省 DATASOURCE)';
COMMENT ON COLUMN ecos_dq.dq_schedule.scope_id IS '限流作用域 ID (与 dq_throttle.scope_id 对齐)';
COMMENT ON COLUMN ecos_dq.dq_schedule.max_runtime_seconds IS '单次执行超时秒数 (超时记 TIMEOUT 不中断)';
COMMENT ON COLUMN ecos_dq.dq_schedule.enabled IS '启用开关 (FALSE 时调度器/EVENT 监听跳过)';

-- ============================================================
-- 2. dq_throttle — 限流配置 (每资产每天最大检查数, 防雪崩)
-- ============================================================
CREATE TABLE IF NOT EXISTS ecos_dq.dq_throttle (
    id                VARCHAR(64)     PRIMARY KEY,
    scope_type        VARCHAR(16)     NOT NULL,           -- FIELD/TABLE/DATASOURCE/DOMAIN/SYSTEM
    scope_id          VARCHAR(64)     NOT NULL,
    max_checks_per_day INT            NOT NULL DEFAULT 100,
    current_count     INT             NOT NULL DEFAULT 0,
    reset_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP       NOT NULL DEFAULT NOW(),
    UNIQUE (scope_type, scope_id)
);

COMMENT ON TABLE  ecos_dq.dq_throttle IS 'DQ 监控限流（每资产/天最大检查数，防雪崩, PMO-48-C T11）';
COMMENT ON COLUMN ecos_dq.dq_throttle.max_checks_per_day IS '每日检查上限 (超过则 runRuleBatch 跳过并记 SKIP 日志)';
COMMENT ON COLUMN ecos_dq.dq_throttle.current_count IS '当日内已执行检查数 (跨日自动重置)';
COMMENT ON COLUMN ecos_dq.dq_throttle.reset_at IS 'current_count 重置时点 (跨日判定)';

-- ============================================================
-- END V113
-- ============================================================
