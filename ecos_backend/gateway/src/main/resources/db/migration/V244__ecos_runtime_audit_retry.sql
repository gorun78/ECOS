-- 详细设计-00 §6.2 (C.5.4 审计兜底)
CREATE TABLE IF NOT EXISTS public.ecos_runtime_audit_retry (
    id            VARCHAR(36) PRIMARY KEY,
    event_type    VARCHAR(64)  NOT NULL,
    payload       TEXT         NOT NULL,
    attempts      SMALLINT     NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP    NOT NULL,
    last_error    VARCHAR(1000),
    status        VARCHAR(16)  NOT NULL DEFAULT 'pending',
    trace_id      VARCHAR(64),
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    version_no    VARCHAR(20)  NOT NULL,
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    domain        VARCHAR(50)  NOT NULL DEFAULT 'default',
    tenant_id     VARCHAR(36)
);
CREATE INDEX IF NOT EXISTS idx_audit_retry_due ON public.ecos_runtime_audit_retry (status, next_retry_at);
