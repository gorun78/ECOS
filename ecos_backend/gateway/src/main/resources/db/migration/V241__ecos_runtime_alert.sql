-- V241 (详细设计-00 §6.2): 告警规则 + 告警记录两表（F00-09 / W07 告警持久化，控制域主控制 schema）
-- 追溯: 详细设计-00-平台入口与横切底座-2026-09-28.md §6.2 V241 段（原样 SQL）；舞台=public（现主控制，ADR-12 S1 后切 ecos_control）
-- 纪律: MC01 应用侧 UUID 无默认 / MC02 白名单类型（布尔 SMALLINT / JSON TEXT / 数值 NUMERIC 显式精度）/ DR01 小写下划线
-- 写方唯一: runtime-core AlertServiceImpl（规则 CRUD 落库 + triggerAlert 落 record）；读方: runtime-monitor 端点 /api/v1/monitor/alerts*
-- 红线: schema 只加不删（IR03）；无 DROP/ALTER；无 SELECT *（IR04 由 Mapper 侧保证）

CREATE TABLE IF NOT EXISTS public.ecos_runtime_alert_rule (
    id            VARCHAR(36) PRIMARY KEY,          -- MC01：应用侧 UUID，DDL 无默认值
    rule_code     VARCHAR(64)  NOT NULL,
    metric_key    VARCHAR(128) NOT NULL,            -- 如 kafka.dlt.lag / llm.error.ratio
    severity      VARCHAR(16)  NOT NULL,            -- info|warn|error|critical（枚举，MC02 无 ENUM）
    threshold_num NUMERIC(18,4),                    -- MC02：数值阈值用 NUMERIC 显式精度
    window_sec    INTEGER      NOT NULL DEFAULT 300,
    upgrade_min   SMALLINT,                         -- 升级 SLA（分钟）
    enabled       SMALLINT     NOT NULL DEFAULT 1,  -- MC02：布尔用 SMALLINT 0/1
    channel       VARCHAR(32)  NOT NULL DEFAULT 'log',  -- log|webhook
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
    status        VARCHAR(16)  NOT NULL DEFAULT 'open',   -- open|acked|closed
    title         VARCHAR(255) NOT NULL,
    detail_json   TEXT,                                    -- MC02：JSON 存 TEXT，不参与 WHERE/索引
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
