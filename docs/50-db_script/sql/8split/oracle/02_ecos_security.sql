-- ============================================================================
-- ECOS Security Center Domain — Oracle DDL
-- Schema: ecos_security
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

BEGIN
   EXECUTE IMMEDIATE 'CREATE USER ecos_security IDENTIFIED BY ecos_security DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE INDEX TO ecos_security;

-- ============================================
-- Table: td_user_security_profile
-- Description: 用户安全画像表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_security.td_user_security_profile (
    user_id            VARCHAR2(64)  PRIMARY KEY,
    clearance_level    NUMBER(10)    DEFAULT 0,
    linked_workstation VARCHAR2(256),
    audit_mode         VARCHAR2(32)  DEFAULT ''basic'',
    sandbox_mandatory  NUMBER(1)     DEFAULT 0,
    is_default         NUMBER(1)     DEFAULT 0,
    tenant_id          VARCHAR2(64),
    org_id             VARCHAR2(64),
    scope_type         VARCHAR2(16)  DEFAULT ''USER'',
    created_at         TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at         TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_security.td_user_security_profile IS '用户安全画像表';
COMMENT ON COLUMN ecos_security.td_user_security_profile.clearance_level IS '安全许可级别';
COMMENT ON COLUMN ecos_security.td_user_security_profile.audit_mode IS '审计模式: basic/enhanced/full';
COMMENT ON COLUMN ecos_security.td_user_security_profile.sandbox_mandatory IS '是否强制沙箱';
COMMENT ON COLUMN ecos_security.td_user_security_profile.scope_type IS '作用域类型: USER/ORG/GLOBAL';

-- ============================================
-- Table: td_role_security_profile
-- Description: 角色安全画像表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_security.td_role_security_profile (
    role_id            VARCHAR2(64)  PRIMARY KEY,
    clearance_level    NUMBER(10)    DEFAULT 0,
    linked_workstation VARCHAR2(256),
    audit_mode         VARCHAR2(32)  DEFAULT ''basic'',
    sandbox_mandatory  NUMBER(1)     DEFAULT 0,
    tenant_id          VARCHAR2(64),
    org_id             VARCHAR2(64),
    scope_type         VARCHAR2(16)  DEFAULT ''ROLE'',
    created_at         TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at         TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_security.td_role_security_profile IS '角色安全画像表';

-- ============================================
-- Table: td_abac_policy
-- Description: ABAC策略表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_security.td_abac_policy (
    policy_id              VARCHAR2(64)  PRIMARY KEY,
    policy_name            VARCHAR2(128) NOT NULL,
    subject_condition      VARCHAR2(512),
    resource_condition     VARCHAR2(512) DEFAULT ''*'',
    action_condition       VARCHAR2(256) DEFAULT ''*'',
    environment_condition  VARCHAR2(512),
    effect                 VARCHAR2(16)  NOT NULL,
    priority               NUMBER(10)    DEFAULT 100,
    scope_type             VARCHAR2(16)  DEFAULT ''GLOBAL'',
    scope_id               VARCHAR2(64),
    tenant_id              VARCHAR2(64),
    created_time           TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_time           TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_security.td_abac_policy IS 'ABAC策略表';
COMMENT ON COLUMN ecos_security.td_abac_policy.effect IS '效果: ALLOW/DENY';
COMMENT ON COLUMN ecos_security.td_abac_policy.priority IS '优先级(越大越优先)';
COMMENT ON COLUMN ecos_security.td_abac_policy.scope_type IS '作用域: GLOBAL/ORG/TENANT';

-- ============================================
-- Table: td_audit_log
-- Description: 审计日志表(按月分区)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_security.td_audit_log (
    log_id      VARCHAR2(64)  PRIMARY KEY,
    event_type  VARCHAR2(64)  NOT NULL,
    timestamp   TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    user_id     VARCHAR2(64),
    tenant_id   VARCHAR2(64),
    resource    VARCHAR2(256),
    action      VARCHAR2(128),
    result      VARCHAR2(32),
    ip_address  VARCHAR2(64),
    user_agent  VARCHAR2(512),
    request_id  VARCHAR2(64),
    duration    NUMBER(10),
    details     CLOB
)
PARTITION BY RANGE (timestamp)
(
    PARTITION audit_log_2024_01 VALUES LESS THAN (TO_DATE(''2024-02-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2024_02 VALUES LESS THAN (TO_DATE(''2024-03-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2024_03 VALUES LESS THAN (TO_DATE(''2024-04-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2024_04 VALUES LESS THAN (TO_DATE(''2024-05-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2024_05 VALUES LESS THAN (TO_DATE(''2024-06-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2024_06 VALUES LESS THAN (TO_DATE(''2024-07-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2024_07 VALUES LESS THAN (TO_DATE(''2024-08-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2024_08 VALUES LESS THAN (TO_DATE(''2024-09-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2024_09 VALUES LESS THAN (TO_DATE(''2024-10-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2024_10 VALUES LESS THAN (TO_DATE(''2024-11-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2024_11 VALUES LESS THAN (TO_DATE(''2024-12-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2024_12 VALUES LESS THAN (TO_DATE(''2025-01-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2025_01 VALUES LESS THAN (TO_DATE(''2025-02-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2025_02 VALUES LESS THAN (TO_DATE(''2025-03-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2025_03 VALUES LESS THAN (TO_DATE(''2025-04-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2025_04 VALUES LESS THAN (TO_DATE(''2025-05-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2025_05 VALUES LESS THAN (TO_DATE(''2025-06-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2025_06 VALUES LESS THAN (TO_DATE(''2025-07-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2025_07 VALUES LESS THAN (TO_DATE(''2025-08-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2025_08 VALUES LESS THAN (TO_DATE(''2025-09-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2025_09 VALUES LESS THAN (TO_DATE(''2025-10-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2025_10 VALUES LESS THAN (TO_DATE(''2025-11-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2025_11 VALUES LESS THAN (TO_DATE(''2025-12-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2025_12 VALUES LESS THAN (TO_DATE(''2026-01-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2026_01 VALUES LESS THAN (TO_DATE(''2026-02-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2026_02 VALUES LESS THAN (TO_DATE(''2026-03-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2026_03 VALUES LESS THAN (TO_DATE(''2026-04-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2026_04 VALUES LESS THAN (TO_DATE(''2026-05-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2026_05 VALUES LESS THAN (TO_DATE(''2026-06-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2026_06 VALUES LESS THAN (TO_DATE(''2026-07-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2026_07 VALUES LESS THAN (TO_DATE(''2026-08-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2026_08 VALUES LESS THAN (TO_DATE(''2026-09-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2026_09 VALUES LESS THAN (TO_DATE(''2026-10-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2026_10 VALUES LESS THAN (TO_DATE(''2026-11-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2026_11 VALUES LESS THAN (TO_DATE(''2026-12-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_2026_12 VALUES LESS THAN (TO_DATE(''2027-01-01'', ''YYYY-MM-DD'')),
    PARTITION audit_log_default VALUES LESS THAN (MAXVALUE)
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_security.td_audit_log IS '审计日志表(按月分区)';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_security_audit_event ON ecos_security.td_audit_log(event_type)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_security_audit_user ON ecos_security.td_audit_log(user_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_security_audit_tenant ON ecos_security.td_audit_log(tenant_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_security_audit_timestamp ON ecos_security.td_audit_log(timestamp)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_spans
-- Description: 遥测追踪表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_security.ecos_spans (
    span_id         VARCHAR2(64)  PRIMARY KEY,
    trace_id        VARCHAR2(64)  NOT NULL,
    parent_span_id  VARCHAR2(64),
    operation_name  VARCHAR2(512),
    service_name    VARCHAR2(128),
    http_method     VARCHAR2(16),
    http_path       VARCHAR2(512),
    http_status     NUMBER(10)    DEFAULT 0,
    start_time      TIMESTAMP,
    end_time        TIMESTAMP,
    duration_ms     NUMBER(19)    DEFAULT 0,
    status          VARCHAR2(16)  DEFAULT ''OK'',
    attributes      CLOB,
    tenant_id       VARCHAR2(64),
    created_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_security.ecos_spans IS '遥测追踪表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_security_spans_trace ON ecos_security.ecos_spans(trace_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_security_spans_time ON ecos_security.ecos_spans(created_at DESC)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_security_spans_path ON ecos_security.ecos_spans(http_path)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Sequence for ecos_token_usage
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'CREATE SEQUENCE ecos_security.seq_token_usage START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_token_usage
-- Description: Token用量表(按季度分区)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_security.ecos_token_usage (
    id                NUMBER(19)     PRIMARY KEY,
    trace_id          VARCHAR2(64),
    model             VARCHAR2(64),
    operation         VARCHAR2(256),
    prompt_tokens     NUMBER(10)     DEFAULT 0,
    completion_tokens NUMBER(10)     DEFAULT 0,
    total_tokens      NUMBER(10)     DEFAULT 0,
    cost_estimate     NUMBER(10,6)   DEFAULT 0,
    latency_ms        NUMBER(19)     DEFAULT 0,
    tenant_id         VARCHAR2(64),
    created_at        TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
)
PARTITION BY RANGE (created_at)
(
    PARTITION token_usage_2025_q1 VALUES LESS THAN (TO_DATE(''2025-04-01'', ''YYYY-MM-DD'')),
    PARTITION token_usage_2025_q2 VALUES LESS THAN (TO_DATE(''2025-07-01'', ''YYYY-MM-DD'')),
    PARTITION token_usage_2025_q3 VALUES LESS THAN (TO_DATE(''2025-10-01'', ''YYYY-MM-DD'')),
    PARTITION token_usage_2025_q4 VALUES LESS THAN (TO_DATE(''2026-01-01'', ''YYYY-MM-DD'')),
    PARTITION token_usage_2026_q1 VALUES LESS THAN (TO_DATE(''2026-04-01'', ''YYYY-MM-DD'')),
    PARTITION token_usage_2026_q2 VALUES LESS THAN (TO_DATE(''2026-07-01'', ''YYYY-MM-DD'')),
    PARTITION token_usage_2026_q3 VALUES LESS THAN (TO_DATE(''2026-10-01'', ''YYYY-MM-DD'')),
    PARTITION token_usage_2026_q4 VALUES LESS THAN (TO_DATE(''2027-01-01'', ''YYYY-MM-DD'')),
    PARTITION token_usage_default VALUES LESS THAN (MAXVALUE)
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_security.ecos_token_usage IS 'Token用量表(按季度分区)';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_security_token_trace ON ecos_security.ecos_token_usage(trace_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

CREATE OR REPLACE TRIGGER ecos_security.trg_token_usage_id
BEFORE INSERT ON ecos_security.ecos_token_usage
FOR EACH ROW
BEGIN
   IF :NEW.id IS NULL THEN
      :NEW.id := ecos_security.seq_token_usage.NEXTVAL;
   END IF;
END;
/

-- ============================================
-- Table: ecos_alert_history
-- Description: 安全告警历史表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_security.ecos_alert_history (
    id            VARCHAR2(64)  PRIMARY KEY,
    alert_type    VARCHAR2(64)  NOT NULL,
    severity      VARCHAR2(16)  DEFAULT ''INFO'',
    source        VARCHAR2(128),
    message       CLOB,
    user_id       VARCHAR2(64),
    tenant_id     VARCHAR2(64),
    resource      VARCHAR2(256),
    resolved      NUMBER(1)     DEFAULT 0,
    resolved_by   VARCHAR2(64),
    resolved_at   TIMESTAMP,
    created_at    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_security.ecos_alert_history IS '安全告警历史表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_security_alert_type ON ecos_security.ecos_alert_history(alert_type)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_security_alert_sev ON ecos_security.ecos_alert_history(severity)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_security_alert_time ON ecos_security.ecos_alert_history(created_at DESC)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Foreign Keys (cross-domain)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_security.td_user_security_profile ADD CONSTRAINT fk_security_user_prof_user FOREIGN KEY (user_id) REFERENCES ecos_sysman.td_user(user_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_security.td_role_security_profile ADD CONSTRAINT fk_security_role_prof_role FOREIGN KEY (role_id) REFERENCES ecos_sysman.td_role(role_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_security.ecos_spans ADD CONSTRAINT fk_security_spans_tenant FOREIGN KEY (tenant_id) REFERENCES ecos_sysman.ecos_tenant(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
