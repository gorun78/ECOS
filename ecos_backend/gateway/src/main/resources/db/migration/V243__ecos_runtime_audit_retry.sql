-- V243 (详细设计-00 §6.2): 审计 Kafka 投递失败兜底重试表（W12 审计 / C.5.4 审计兜底 SLA，控制域主控制 schema）
-- 追溯: 详细设计-00-平台入口与横切底座-2026-09-28.md §6.2 V243 段（原样 SQL）；舞台=public
-- 写方: runtime-event AuditRetrySink（publish 同步失败写行）；重放: AuditRetryTask（@Scheduled fixedDelay=60s，
--       到期行 attempts<3 重投，达 3 次置 'alerted' 并触发 critical 告警，ack 必须人工 — 只 mark ack 不自动 close）
-- SLA: 从失败到落库告警 <= 15min（C.5.4）；红线：兜底失败不回滚业务写（可用性）
-- 红线: MC01 应用侧 UUID；schema 只加不删（IR03）

CREATE TABLE IF NOT EXISTS public.ecos_runtime_audit_retry (
    id            VARCHAR(36) PRIMARY KEY,
    event_type    VARCHAR(64)  NOT NULL,
    payload       TEXT         NOT NULL,
    attempts      SMALLINT     NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP    NOT NULL,
    last_error    VARCHAR(1000),
    status        VARCHAR(16)  NOT NULL DEFAULT 'pending',  -- pending|replayed|alerted
    trace_id      VARCHAR(64),
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    version_no    VARCHAR(20)  NOT NULL,
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    domain        VARCHAR(50)  NOT NULL DEFAULT 'default',
    tenant_id     VARCHAR(36)
);
CREATE INDEX IF NOT EXISTS idx_audit_retry_due ON public.ecos_runtime_audit_retry (status, next_retry_at);
