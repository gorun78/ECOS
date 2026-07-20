-- ============================================================================
-- ECOS Security Center Domain — PostgreSQL DDL
-- Schema: ecos_security
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS ecos_security;

-- ============================================
-- Table: td_user_security_profile
-- Description: 用户安全画像表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_security.td_user_security_profile (
    user_id            VARCHAR(64)  PRIMARY KEY,
    clearance_level    INT          DEFAULT 0,
    linked_workstation VARCHAR(256),
    audit_mode         VARCHAR(32)  DEFAULT 'basic',
    sandbox_mandatory  BOOLEAN      DEFAULT FALSE,
    is_default         BOOLEAN      DEFAULT FALSE,
    tenant_id          VARCHAR(64),
    org_id             VARCHAR(64),
    scope_type         VARCHAR(16)  DEFAULT 'USER',
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_security.td_user_security_profile IS '用户安全画像表';
COMMENT ON COLUMN ecos_security.td_user_security_profile.clearance_level IS '安全许可级别';
COMMENT ON COLUMN ecos_security.td_user_security_profile.audit_mode IS '审计模式: basic/enhanced/full';
COMMENT ON COLUMN ecos_security.td_user_security_profile.sandbox_mandatory IS '是否强制沙箱';
COMMENT ON COLUMN ecos_security.td_user_security_profile.scope_type IS '作用域类型: USER/ORG/GLOBAL';

-- ============================================
-- Table: td_role_security_profile
-- Description: 角色安全画像表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_security.td_role_security_profile (
    role_id            VARCHAR(64)  PRIMARY KEY,
    clearance_level    INT          DEFAULT 0,
    linked_workstation VARCHAR(256),
    audit_mode         VARCHAR(32)  DEFAULT 'basic',
    sandbox_mandatory  BOOLEAN      DEFAULT FALSE,
    tenant_id          VARCHAR(64),
    org_id             VARCHAR(64),
    scope_type         VARCHAR(16)  DEFAULT 'ROLE',
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_security.td_role_security_profile IS '角色安全画像表';

-- ============================================
-- Table: td_abac_policy
-- Description: ABAC策略表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_security.td_abac_policy (
    policy_id              VARCHAR(64)  PRIMARY KEY,
    policy_name            VARCHAR(128) NOT NULL,
    subject_condition      VARCHAR(512),
    resource_condition     VARCHAR(512) DEFAULT '*',
    action_condition       VARCHAR(256) DEFAULT '*',
    environment_condition  VARCHAR(512),
    effect                 VARCHAR(16)  NOT NULL,
    priority               INT          DEFAULT 100,
    scope_type             VARCHAR(16)  DEFAULT 'GLOBAL',
    scope_id               VARCHAR(64),
    tenant_id              VARCHAR(64),
    created_time           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_time           TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_security.td_abac_policy IS 'ABAC策略表';
COMMENT ON COLUMN ecos_security.td_abac_policy.effect IS '效果: ALLOW/DENY';
COMMENT ON COLUMN ecos_security.td_abac_policy.priority IS '优先级(越大越优先)';
COMMENT ON COLUMN ecos_security.td_abac_policy.scope_type IS '作用域: GLOBAL/ORG/TENANT';

-- ============================================
-- Table: td_audit_log
-- Description: 审计日志表(按月分区)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_security.td_audit_log (
    log_id      VARCHAR(64)  NOT NULL,
    event_type  VARCHAR(64)  NOT NULL,
    timestamp   TIMESTAMP    NOT NULL DEFAULT NOW(),
    user_id     VARCHAR(64),
    tenant_id   VARCHAR(64),
    resource    VARCHAR(256),
    action      VARCHAR(128),
    result      VARCHAR(32),
    ip_address  VARCHAR(64),
    user_agent  VARCHAR(512),
    request_id  VARCHAR(64),
    duration    INT,
    details     TEXT,
    PRIMARY KEY (log_id, timestamp)
) PARTITION BY RANGE (timestamp);

COMMENT ON TABLE  ecos_security.td_audit_log IS '审计日志表(按月分区)';

CREATE INDEX IF NOT EXISTS idx_security_audit_event     ON ecos_security.td_audit_log(event_type);
CREATE INDEX IF NOT EXISTS idx_security_audit_user      ON ecos_security.td_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_security_audit_tenant    ON ecos_security.td_audit_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_security_audit_timestamp ON ecos_security.td_audit_log(timestamp);

-- Monthly partitions 2024-2026
DO $$
BEGIN
    FOR y IN 2024..2026 LOOP
        FOR m IN 1..12 LOOP
            EXECUTE format(
                'CREATE TABLE IF NOT EXISTS ecos_security.td_audit_log_%s_%s PARTITION OF ecos_security.td_audit_log FOR VALUES FROM (%L) TO (%L)',
                y, lpad(m::text, 2, '0'),
                make_date(y, m, 1),
                make_date(y, m, 1) + interval '1 month'
            );
        END LOOP;
    END LOOP;
    EXECUTE 'CREATE TABLE IF NOT EXISTS ecos_security.td_audit_log_default PARTITION OF ecos_security.td_audit_log DEFAULT';
END $$;

-- ============================================
-- Table: ecos_spans
-- Description: 遥测追踪表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_security.ecos_spans (
    span_id         VARCHAR(64)  PRIMARY KEY,
    trace_id        VARCHAR(64)  NOT NULL,
    parent_span_id  VARCHAR(64),
    operation_name  VARCHAR(512),
    service_name    VARCHAR(128),
    http_method     VARCHAR(16),
    http_path       VARCHAR(512),
    http_status     INT          DEFAULT 0,
    start_time      TIMESTAMP,
    end_time        TIMESTAMP,
    duration_ms     BIGINT       DEFAULT 0,
    status          VARCHAR(16)  DEFAULT 'OK',
    attributes      JSONB,
    tenant_id       VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_security.ecos_spans IS '遥测追踪表';
CREATE INDEX IF NOT EXISTS idx_security_spans_trace ON ecos_security.ecos_spans(trace_id);
CREATE INDEX IF NOT EXISTS idx_security_spans_time  ON ecos_security.ecos_spans(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_security_spans_path  ON ecos_security.ecos_spans(http_path);

-- ============================================
-- Table: ecos_token_usage
-- Description: Token用量表(按季度分区)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_security.ecos_token_usage (
    id                BIGSERIAL,
    trace_id          VARCHAR(64),
    model             VARCHAR(64),
    operation         VARCHAR(256),
    prompt_tokens     INT           DEFAULT 0,
    completion_tokens INT           DEFAULT 0,
    total_tokens      INT           DEFAULT 0,
    cost_estimate     DECIMAL(10,6) DEFAULT 0,
    latency_ms        BIGINT        DEFAULT 0,
    tenant_id         VARCHAR(64),
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE  ecos_security.ecos_token_usage IS 'Token用量表(按季度分区)';

CREATE INDEX IF NOT EXISTS idx_security_token_trace ON ecos_security.ecos_token_usage(trace_id);

-- Quarterly partitions 2025-2026
DO $$
BEGIN
    FOR y IN 2025..2026 LOOP
        FOR q IN 1..4 LOOP
            EXECUTE format(
                'CREATE TABLE IF NOT EXISTS ecos_security.ecos_token_usage_%s_q%s PARTITION OF ecos_security.ecos_token_usage FOR VALUES FROM (%L) TO (%L)',
                y, q,
                make_date(y, (q-1)*3+1, 1),
                make_date(y, (q-1)*3+1, 1) + interval '3 months'
            );
        END LOOP;
    END LOOP;
    EXECUTE 'CREATE TABLE IF NOT EXISTS ecos_security.ecos_token_usage_default PARTITION OF ecos_security.ecos_token_usage DEFAULT';
END $$;

-- ============================================
-- Table: ecos_alert_history
-- Description: 安全告警历史表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_security.ecos_alert_history (
    id            VARCHAR(64)  PRIMARY KEY,
    alert_type    VARCHAR(64)  NOT NULL,
    severity      VARCHAR(16)  DEFAULT 'INFO',
    source        VARCHAR(128),
    message       TEXT,
    user_id       VARCHAR(64),
    tenant_id     VARCHAR(64),
    resource      VARCHAR(256),
    resolved      BOOLEAN      DEFAULT FALSE,
    resolved_by   VARCHAR(64),
    resolved_at   TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_security.ecos_alert_history IS '安全告警历史表';
CREATE INDEX IF NOT EXISTS idx_security_alert_type ON ecos_security.ecos_alert_history(alert_type);
CREATE INDEX IF NOT EXISTS idx_security_alert_sev  ON ecos_security.ecos_alert_history(severity);
CREATE INDEX IF NOT EXISTS idx_security_alert_time ON ecos_security.ecos_alert_history(created_at DESC);

-- ============================================
-- Foreign Keys (cross-domain)
-- ============================================
ALTER TABLE ecos_security.td_user_security_profile
    ADD CONSTRAINT fk_security_user_prof_user FOREIGN KEY (user_id) REFERENCES ecos_sysman.td_user(user_id);
ALTER TABLE ecos_security.td_role_security_profile
    ADD CONSTRAINT fk_security_role_prof_role FOREIGN KEY (role_id) REFERENCES ecos_sysman.td_role(role_id);
ALTER TABLE ecos_security.ecos_spans
    ADD CONSTRAINT fk_security_spans_tenant   FOREIGN KEY (tenant_id) REFERENCES ecos_sysman.ecos_tenant(id);
