-- V110__wave7_create_workflow_log.sql
-- Wave-7 T-26 (R2 真实根因) -- 创建 ecos_workflow_log
--
-- 根因: WorkflowLogRepository (buszhi-impl) INSERT INTO ecos_workflow_log
--      但生产库没有这张表, resume/suspend/terminate 三条 transition 全部
--      BadSqlGrammarException "relation ecos_workflow_log does not exist" → 500
--
-- 字段对齐 WorkflowLogRepository.log() 的 INSERT (id, instance_id, node_id, node_type, event_type, message, details, duration_ms, trace_id, created_at)
-- 铁律: schema 只增; CREATE TABLE IF NOT EXISTS 幂等

CREATE TABLE IF NOT EXISTS ecos_workflow_log (
    id            BIGSERIAL PRIMARY KEY,
    instance_id   VARCHAR(64) NOT NULL,
    node_id       VARCHAR(64),
    node_type     VARCHAR(64),
    event_type    VARCHAR(64) NOT NULL,
    message       TEXT,
    details       JSONB,
    duration_ms   BIGINT,
    trace_id      VARCHAR(64),
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_workflow_log_instance ON ecos_workflow_log(instance_id, created_at DESC);
