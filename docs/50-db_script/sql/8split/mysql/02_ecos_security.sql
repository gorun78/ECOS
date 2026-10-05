-- ============================================================================
-- ECOS Security Center Domain — MySQL DDL
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

-- ============================================
-- Table: td_user_security_profile
-- Description: 用户安全画像表
-- ============================================
CREATE TABLE IF NOT EXISTS td_user_security_profile (
    user_id            VARCHAR(64)  PRIMARY KEY COMMENT '用户ID',
    clearance_level    INT          DEFAULT 0 COMMENT '安全许可级别',
    linked_workstation VARCHAR(256) COMMENT '关联工作站',
    audit_mode         VARCHAR(32)  DEFAULT 'basic' COMMENT '审计模式: basic/enhanced/full',
    sandbox_mandatory  TINYINT(1)   DEFAULT 0 COMMENT '是否强制沙箱',
    is_default         TINYINT(1)   DEFAULT 0 COMMENT '是否默认',
    tenant_id          VARCHAR(64) COMMENT '租户ID',
    org_id             VARCHAR(64) COMMENT '组织ID',
    scope_type         VARCHAR(16)  DEFAULT 'USER' COMMENT '作用域类型: USER/ORG/GLOBAL',
    created_at         DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at         DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户安全画像表';

-- ============================================
-- Table: td_role_security_profile
-- Description: 角色安全画像表
-- ============================================
CREATE TABLE IF NOT EXISTS td_role_security_profile (
    role_id            VARCHAR(64)  PRIMARY KEY COMMENT '角色ID',
    clearance_level    INT          DEFAULT 0 COMMENT '安全许可级别',
    linked_workstation VARCHAR(256) COMMENT '关联工作站',
    audit_mode         VARCHAR(32)  DEFAULT 'basic' COMMENT '审计模式',
    sandbox_mandatory  TINYINT(1)   DEFAULT 0 COMMENT '是否强制沙箱',
    tenant_id          VARCHAR(64) COMMENT '租户ID',
    org_id             VARCHAR(64) COMMENT '组织ID',
    scope_type         VARCHAR(16)  DEFAULT 'ROLE' COMMENT '作用域类型',
    created_at         DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at         DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色安全画像表';

-- ============================================
-- Table: td_abac_policy
-- Description: ABAC策略表
-- ============================================
CREATE TABLE IF NOT EXISTS td_abac_policy (
    policy_id              VARCHAR(64)  PRIMARY KEY COMMENT '策略ID',
    policy_name            VARCHAR(128) NOT NULL COMMENT '策略名称',
    subject_condition      VARCHAR(512) COMMENT '主体条件',
    resource_condition     VARCHAR(512) DEFAULT '*' COMMENT '资源条件',
    action_condition       VARCHAR(256) DEFAULT '*' COMMENT '操作条件',
    environment_condition  VARCHAR(512) COMMENT '环境条件',
    effect                 VARCHAR(16)  NOT NULL COMMENT '效果: ALLOW/DENY',
    priority               INT          DEFAULT 100 COMMENT '优先级(越大越优先)',
    scope_type             VARCHAR(16)  DEFAULT 'GLOBAL' COMMENT '作用域: GLOBAL/ORG/TENANT',
    scope_id               VARCHAR(64) COMMENT '作用域ID',
    tenant_id              VARCHAR(64) COMMENT '租户ID',
    created_time           DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_time           DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ABAC策略表';

-- ============================================
-- Table: td_audit_log
-- Description: 审计日志表(按季度分区)
-- Note: MySQL partitioned tables require partition column in PK
-- ============================================
CREATE TABLE IF NOT EXISTS td_audit_log (
    log_id      VARCHAR(64)  NOT NULL COMMENT '日志ID',
    event_type  VARCHAR(64)  NOT NULL COMMENT '事件类型',
    `timestamp` DATETIME     NOT NULL DEFAULT NOW() COMMENT '时间戳',
    user_id     VARCHAR(64) COMMENT '用户ID',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    resource    VARCHAR(256) COMMENT '资源',
    action      VARCHAR(128) COMMENT '操作',
    result      VARCHAR(32) COMMENT '结果',
    ip_address  VARCHAR(64) COMMENT 'IP地址',
    user_agent  VARCHAR(512) COMMENT '用户代理',
    request_id  VARCHAR(64) COMMENT '请求ID',
    duration    INT COMMENT '耗时(ms)',
    details     LONGTEXT COMMENT '详情',
    PRIMARY KEY (log_id, `timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表(按季度分区)'
PARTITION BY RANGE COLUMNS(`timestamp`) (
    PARTITION p2025_q1 VALUES LESS THAN ('2025-04-01 00:00:00'),
    PARTITION p2025_q2 VALUES LESS THAN ('2025-07-01 00:00:00'),
    PARTITION p2025_q3 VALUES LESS THAN ('2025-10-01 00:00:00'),
    PARTITION p2025_q4 VALUES LESS THAN ('2026-01-01 00:00:00'),
    PARTITION p2026_q1 VALUES LESS THAN ('2026-04-01 00:00:00'),
    PARTITION p2026_q2 VALUES LESS THAN ('2026-07-01 00:00:00'),
    PARTITION p2026_q3 VALUES LESS THAN ('2026-10-01 00:00:00'),
    PARTITION p2026_q4 VALUES LESS THAN ('2027-01-01 00:00:00'),
    PARTITION p_future  VALUES LESS THAN MAXVALUE
);

CREATE INDEX idx_security_audit_event     ON td_audit_log(event_type);
CREATE INDEX idx_security_audit_user      ON td_audit_log(user_id);
CREATE INDEX idx_security_audit_tenant    ON td_audit_log(tenant_id);
CREATE INDEX idx_security_audit_timestamp ON td_audit_log(`timestamp`);

-- ============================================
-- Table: ecos_spans
-- Description: 遥测追踪表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_spans (
    span_id         VARCHAR(64)  PRIMARY KEY COMMENT 'Span ID',
    trace_id        VARCHAR(64)  NOT NULL COMMENT 'Trace ID',
    parent_span_id  VARCHAR(64) COMMENT '父Span ID',
    operation_name  VARCHAR(512) COMMENT '操作名',
    service_name    VARCHAR(128) COMMENT '服务名',
    http_method     VARCHAR(16) COMMENT 'HTTP方法',
    http_path       VARCHAR(512) COMMENT 'HTTP路径',
    http_status     INT          DEFAULT 0 COMMENT 'HTTP状态码',
    start_time      DATETIME COMMENT '开始时间',
    end_time        DATETIME COMMENT '结束时间',
    duration_ms     BIGINT       DEFAULT 0 COMMENT '耗时(ms)',
    status          VARCHAR(16)  DEFAULT 'OK' COMMENT '状态',
    attributes      JSON COMMENT '属性',
    tenant_id       VARCHAR(64) COMMENT '租户ID',
    created_at      DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='遥测追踪表';

CREATE INDEX idx_security_spans_trace ON ecos_spans(trace_id);
CREATE INDEX idx_security_spans_time  ON ecos_spans(created_at DESC);
CREATE INDEX idx_security_spans_path  ON ecos_spans(http_path);

-- ============================================
-- Table: ecos_token_usage
-- Description: Token用量表(按季度分区)
-- Note: MySQL partitioned tables require partition column in PK
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_token_usage (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'ID',
    trace_id          VARCHAR(64) COMMENT 'Trace ID',
    model             VARCHAR(64) COMMENT '模型',
    operation         VARCHAR(256) COMMENT '操作',
    prompt_tokens     INT           DEFAULT 0 COMMENT '输入Token数',
    completion_tokens INT           DEFAULT 0 COMMENT '输出Token数',
    total_tokens      INT           DEFAULT 0 COMMENT '总Token数',
    cost_estimate     DECIMAL(10,6) DEFAULT 0 COMMENT '成本估算',
    latency_ms        BIGINT        DEFAULT 0 COMMENT '延迟(ms)',
    tenant_id         VARCHAR(64) COMMENT '租户ID',
    created_at        DATETIME      NOT NULL DEFAULT NOW() COMMENT '创建时间',
    PRIMARY KEY (id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Token用量表(按季度分区)'
PARTITION BY RANGE COLUMNS(created_at) (
    PARTITION p2025_q1 VALUES LESS THAN ('2025-04-01 00:00:00'),
    PARTITION p2025_q2 VALUES LESS THAN ('2025-07-01 00:00:00'),
    PARTITION p2025_q3 VALUES LESS THAN ('2025-10-01 00:00:00'),
    PARTITION p2025_q4 VALUES LESS THAN ('2026-01-01 00:00:00'),
    PARTITION p2026_q1 VALUES LESS THAN ('2026-04-01 00:00:00'),
    PARTITION p2026_q2 VALUES LESS THAN ('2026-07-01 00:00:00'),
    PARTITION p2026_q3 VALUES LESS THAN ('2026-10-01 00:00:00'),
    PARTITION p2026_q4 VALUES LESS THAN ('2027-01-01 00:00:00'),
    PARTITION p_future  VALUES LESS THAN MAXVALUE
);

CREATE INDEX idx_security_token_trace ON ecos_token_usage(trace_id);

-- ============================================
-- Table: ecos_alert_history
-- Description: 安全告警历史表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_alert_history (
    id            VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    alert_type    VARCHAR(64)  NOT NULL COMMENT '告警类型',
    severity      VARCHAR(16)  DEFAULT 'INFO' COMMENT '严重级别',
    source        VARCHAR(128) COMMENT '来源',
    message       LONGTEXT COMMENT '消息',
    user_id       VARCHAR(64) COMMENT '用户ID',
    tenant_id     VARCHAR(64) COMMENT '租户ID',
    resource      VARCHAR(256) COMMENT '资源',
    resolved      TINYINT(1)   DEFAULT 0 COMMENT '是否已解决',
    resolved_by   VARCHAR(64) COMMENT '解决人',
    resolved_at   DATETIME COMMENT '解决时间',
    created_at    DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全告警历史表';

CREATE INDEX idx_security_alert_type ON ecos_alert_history(alert_type);
CREATE INDEX idx_security_alert_sev  ON ecos_alert_history(severity);
CREATE INDEX idx_security_alert_time ON ecos_alert_history(created_at DESC);

-- ============================================
-- Foreign Keys (cross-domain)
-- ============================================
ALTER TABLE td_user_security_profile
    ADD CONSTRAINT fk_security_user_prof_user FOREIGN KEY (user_id) REFERENCES td_user(user_id);
ALTER TABLE td_role_security_profile
    ADD CONSTRAINT fk_security_role_prof_role FOREIGN KEY (role_id) REFERENCES td_role(role_id);
ALTER TABLE ecos_spans
    ADD CONSTRAINT fk_security_spans_tenant   FOREIGN KEY (tenant_id) REFERENCES ecos_tenant(id);
