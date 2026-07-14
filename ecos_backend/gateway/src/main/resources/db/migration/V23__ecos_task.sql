-- ============================================================
-- V22__ecos_task.sql — 任务调度表
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_task (
    id              BIGSERIAL       PRIMARY KEY,
    task_name       VARCHAR(256)    NOT NULL,
    task_type       VARCHAR(32)     NOT NULL,       -- PIPELINE / REALTIME / AGENT / ADMIN
    status          VARCHAR(32)     DEFAULT 'PENDING', -- PENDING / RUNNING / SUCCESS / FAILED / CANCELLED
    config          JSONB,                          -- 任务配置: SQL语句、规则ID、参数等
    runner          VARCHAR(64),                    -- 执行器: doris / rule / hermes
    priority        INT             DEFAULT 0,
    retry_count     INT             DEFAULT 0,
    max_retries     INT             DEFAULT 3,
    scheduled_at    TIMESTAMP,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    result          JSONB,                          -- 执行结果
    error_message   TEXT,
    created_by      VARCHAR(64),
    created_at      TIMESTAMP       DEFAULT NOW(),
    updated_at      TIMESTAMP       DEFAULT NOW()
);

COMMENT ON TABLE  ecos_task               IS '任务调度表 — 统一任务编排与执行追踪';
COMMENT ON COLUMN ecos_task.id             IS '主键，自增';
COMMENT ON COLUMN ecos_task.task_name      IS '任务名称';
COMMENT ON COLUMN ecos_task.task_type      IS '任务类型: PIPELINE/REALTIME/AGENT/ADMIN';
COMMENT ON COLUMN ecos_task.status         IS '任务状态: PENDING/RUNNING/SUCCESS/FAILED/CANCELLED';
COMMENT ON COLUMN ecos_task.config         IS '任务配置(JSON): SQL语句、规则ID、参数等';
COMMENT ON COLUMN ecos_task.runner         IS '执行器: doris/rule/hermes';
COMMENT ON COLUMN ecos_task.priority       IS '优先级，数值越大优先级越高';
COMMENT ON COLUMN ecos_task.retry_count    IS '已重试次数';
COMMENT ON COLUMN ecos_task.max_retries    IS '最大重试次数';
COMMENT ON COLUMN ecos_task.scheduled_at   IS '计划执行时间';
COMMENT ON COLUMN ecos_task.started_at     IS '实际开始时间';
COMMENT ON COLUMN ecos_task.completed_at   IS '完成时间';
COMMENT ON COLUMN ecos_task.result         IS '执行结果(JSON)';
COMMENT ON COLUMN ecos_task.error_message  IS '错误信息';
COMMENT ON COLUMN ecos_task.created_by     IS '创建人';
COMMENT ON COLUMN ecos_task.created_at     IS '创建时间';
COMMENT ON COLUMN ecos_task.updated_at     IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_task_status  ON ecos_task(status);
CREATE INDEX IF NOT EXISTS idx_task_type    ON ecos_task(task_type);
CREATE INDEX IF NOT EXISTS idx_task_created ON ecos_task(created_at DESC);
