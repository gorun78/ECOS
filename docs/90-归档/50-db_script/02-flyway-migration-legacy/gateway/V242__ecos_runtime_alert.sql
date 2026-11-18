-- 详细设计-00 §6.2 (F00-09 runtime-monitor 分级告警 + ack + 升级 + 持久化)
-- 控制域主控制 schema，前缀 ecos_runtime_*；MC01 UUID 应用侧，MC02 白名单类型。
CREATE TABLE IF NOT EXISTS public.ecos_runtime_alert_rule (
    id            VARCHAR(36) PRIMARY KEY,
    rule_code     VARCHAR(64)  NOT NULL,
    metric_key    VARCHAR(128) NOT NULL,
    severity      VARCHAR(16)  NOT NULL,
    threshold_num NUMERIC(18,4),
    window_sec    INTEGER      NOT NULL DEFAULT 300,
    upgrade_min   SMALLINT,
    enabled       SMALLINT     NOT NULL DEFAULT 1,
    channel       VARCHAR(32)  NOT NULL DEFAULT 'log',
    webhook_url   VARCHAR(512),
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    create_by     VARCHAR(36),
    update_by     VARCHAR(36),
    version_no    VARCHAR(20)  NOT NULL,
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    domain        VARCHAR(50)  NOT NULL DEFAULT 'default',
    tenant_id     VARCHAR(36)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_alert_rule_code
    ON public.ecos_runtime_alert_rule (tenant_id, rule_code) WHERE is_deleted = 0;

CREATE TABLE IF NOT EXISTS public.ecos_runtime_alert_record (
    id            VARCHAR(36) PRIMARY KEY,
    rule_code     VARCHAR(64)  NOT NULL,
    severity      VARCHAR(16)  NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'open',
    title         VARCHAR(255) NOT NULL,
    detail_json   TEXT,
    source_module VARCHAR(64),
    trace_id      VARCHAR(64),
    ack_by        VARCHAR(36),
    ack_time      TIMESTAMP,
    close_time    TIMESTAMP,
    occurred_at   TIMESTAMP    NOT NULL,
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    version_no    VARCHAR(20)  NOT NULL,
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    domain        VARCHAR(50)  NOT NULL DEFAULT 'default',
    tenant_id     VARCHAR(36)
);
CREATE INDEX IF NOT EXISTS idx_alert_record_status ON public.ecos_runtime_alert_record (status, occurred_at);
