-- 详细设计-00 §6.2 (C.5.2 事件 DLQ)
CREATE TABLE IF NOT EXISTS public.ecos_runtime_event_dlq (
    id            VARCHAR(36) PRIMARY KEY,
    topic         VARCHAR(128) NOT NULL,
    dlt_topic     VARCHAR(160) NOT NULL,
    payload       TEXT         NOT NULL,
    error_message VARCHAR(1000),
    attempts      SMALLINT     NOT NULL DEFAULT 0,
    first_seen_at TIMESTAMP    NOT NULL,
    replayed_at   TIMESTAMP,
    status        VARCHAR(16)  NOT NULL DEFAULT 'pending',
    trace_id      VARCHAR(64),
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    version_no    VARCHAR(20)  NOT NULL,
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    domain        VARCHAR(50)  NOT NULL DEFAULT 'default',
    tenant_id     VARCHAR(36)
);
CREATE INDEX IF NOT EXISTS idx_dlq_status ON public.ecos_runtime_event_dlq (status, first_seen_at);
