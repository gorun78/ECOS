-- V152__runtime_task_persistence.sql — PMO-72 W4: runtime-task PG 持久化基石
-- 池: sys_man (schema public)
-- 策略: R9 只加不删 + 列名小写下划线
-- 缺口补齐:
--   1) td_runtime_task      增补审计五字段 (create_time / update_time / create_by / update_by / is_deleted) + domain + version_no
--   2) td_runtime_task_plan 增补 cron_expression / next_run_at / last_run_at / last_status（定时四要素）+ task_name / task_type / create_by / update_by / is_deleted / domain / version_no
--   3) td_runtime_task_status 保持（无缺列, R9 不动）
-- 幂等：所有 DDL 使用 IF NOT EXISTS
-- 上线: psql -U postgres -d sys_man -f V152__runtime_task_persistence.sql

-- ============================================================
-- T1.1 主表补加 7 列: 审计 5 字段 + domain + version_no（小写下划线，与 V150 对齐）
-- ============================================================
ALTER TABLE td_runtime_task
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default',
    ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_td_task_task_type ON td_runtime_task ("TASK_TYPE", is_deleted);
CREATE INDEX IF NOT EXISTS idx_td_task_create_time ON td_runtime_task (create_time);
CREATE INDEX IF NOT EXISTS idx_td_task_domain ON td_runtime_task (domain);
CREATE INDEX IF NOT EXISTS idx_td_task_is_deleted ON td_runtime_task (is_deleted) WHERE is_deleted = 0;

-- ============================================================
-- T1.2 plan 表补加 11 列 + 4 索引（定时四要素 + 审计 + plan 元信息）
-- ============================================================
ALTER TABLE td_runtime_task_plan
    ADD COLUMN IF NOT EXISTS cron_expression VARCHAR(128),
    ADD COLUMN IF NOT EXISTS next_run_at     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_run_at     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_status     VARCHAR(32),
    ADD COLUMN IF NOT EXISTS task_name       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS task_type       VARCHAR(64),
    ADD COLUMN IF NOT EXISTS create_by       VARCHAR(64),
    ADD COLUMN IF NOT EXISTS update_by       VARCHAR(64),
    ADD COLUMN IF NOT EXISTS is_deleted      SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS domain          VARCHAR(50) NOT NULL DEFAULT 'default',
    ADD COLUMN IF NOT EXISTS version_no      VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_td_task_plan_cron ON td_runtime_task_plan (cron_expression) WHERE cron_expression IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_td_task_plan_next_run_at ON td_runtime_task_plan (next_run_at) WHERE next_run_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_td_task_plan_last_run_at ON td_runtime_task_plan (last_run_at);
CREATE INDEX IF NOT EXISTS idx_td_task_plan_task_type ON td_runtime_task_plan (task_type);

COMMENT ON TABLE td_runtime_task IS 'runtime-task 即时/定时任务描述主表（task_id 主键, 含审计五字段 + domain + version_no）';
COMMENT ON TABLE td_runtime_task_plan IS 'runtime-task 执行计划主表（plan_content 持久化 + 定时四要素：cron/next_run_at/last_run_at/last_status）';
