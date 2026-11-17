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

-- ============================================================================
-- 详细设计-01 §6.2 / W38 (F01-12, M0): RLS/CLS 策略表 + 敏感数据目录 + 密钥治理表
-- 多库等价镜像。PG 侧单源见 gateway/db/migration/V164__security_rls_cls_policy_ddl_backfill.sql
-- 与 V165__security_crypto_key_governance.sql。本镜像承接 ST07/MC 多库兼容路径
-- （"无脚本即无多库兼容" S-11 闭环）。列形态与 PG 现网真实形态一致（IR03 只加不删）。
-- 说明: PG 侧 ecos_security_asset 的部分唯一索引 WHERE is_deleted=0 在 MySQL 无等价，
--       一律用普通 UNIQUE(tenant_id,asset_key)；软删互斥判定由应用侧承接（偏差已登记）。
-- ============================================================================

-- ============================================
-- Table: ecos_rls_policy
-- Description: 行级安全（RLS）策略表（现网形态镜像 + 参数化谓词列）
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_rls_policy (
    id                 VARCHAR(64)  PRIMARY KEY COMMENT '策略ID',
    policy_name        VARCHAR(128) NOT NULL COMMENT '策略名',
    table_name         VARCHAR(128) NOT NULL COMMENT '目标表',
    filter_expr        VARCHAR(512) NOT NULL COMMENT '过滤表达式(legacy), 目标态走 predicate_template',
    role_id            VARCHAR(64)  COMMENT '作用角色',
    user_id            VARCHAR(64)  COMMENT '作用用户',
    priority           INT          DEFAULT 0 COMMENT '优先级',
    enabled            TINYINT(1)   DEFAULT 1 COMMENT '是否启用',
    description        TEXT COMMENT '描述',
    created_by         VARCHAR(64)  COMMENT '创建人(legacy)',
    created_at         DATETIME     DEFAULT NOW() COMMENT '创建时间(legacy)',
    updated_at         DATETIME     DEFAULT NOW() COMMENT '更新时间(legacy)',
    resource_id        VARCHAR(64)  COMMENT '资源ID',
    predicate_template VARCHAR(1000) COMMENT '参数化谓词模板(白名单占位)',
    bindings_json      TEXT COMMENT '参数绑定(JSON)',
    source_kind        VARCHAR(32)  NOT NULL DEFAULT 'legacy_text' COMMENT 'legacy_text|parameterized',
    create_time        DATETIME COMMENT '规范创建时间(DR06)',
    update_time        DATETIME COMMENT '规范更新时间(DR06)',
    create_by          VARCHAR(100) COMMENT '规范创建人(DR06)',
    update_by          VARCHAR(100) COMMENT '规范更新人(DR06)',
    version_no         VARCHAR(20)  NOT NULL DEFAULT '1' COMMENT '版本号(DR07)',
    is_deleted         SMALLINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除(DR05)',
    domain             VARCHAR(50)  NOT NULL DEFAULT 'default' COMMENT '域名(DR08)'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='行级安全策略表';

CREATE INDEX idx_rls_policy_table ON ecos_rls_policy (table_name, enabled, priority);

-- ============================================
-- Table: ecos_cls_policy
-- Description: 列级安全（CLS）策略表（现网形态镜像 + mode/scope 范围模型列）
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_cls_policy (
    id                   VARCHAR(64)  PRIMARY KEY COMMENT '策略ID',
    policy_name          VARCHAR(128) NOT NULL COMMENT '策略名',
    table_name           VARCHAR(128) NOT NULL COMMENT '目标表',
    visible_cols         TEXT         NOT NULL COMMENT '可见列(JSON数组文本)',
    blocked_cols         TEXT COMMENT '屏蔽列',
    role_id              VARCHAR(64) COMMENT '作用角色',
    user_id              VARCHAR(64) COMMENT '作用用户',
    priority             INT DEFAULT 0 COMMENT '优先级',
    enabled              TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    description          TEXT COMMENT '描述',
    created_by           VARCHAR(64) COMMENT '创建人(legacy)',
    created_at           DATETIME DEFAULT NOW() COMMENT '创建时间(legacy)',
    updated_at           DATETIME DEFAULT NOW() COMMENT '更新时间(legacy)',
    resource_id          VARCHAR(64) COMMENT '资源ID',
    column_name          VARCHAR(128) COMMENT '目标列(NULL=整表默认)',
    mode                 VARCHAR(16)  NOT NULL DEFAULT 'deny' COMMENT 'allow|deny',
    allowed_columns_json TEXT COMMENT '允许列(JSON)',
    scope_type           VARCHAR(16)  NOT NULL DEFAULT 'ROLE' COMMENT 'USER|ROLE|GLOBAL',
    scope_id             VARCHAR(64) COMMENT '作用对象ID',
    create_time          DATETIME COMMENT '规范创建时间(DR06)',
    update_time          DATETIME COMMENT '规范更新时间(DR06)',
    create_by            VARCHAR(100) COMMENT '规范创建人(DR06)',
    update_by            VARCHAR(100) COMMENT '规范更新人(DR06)',
    version_no           VARCHAR(20)  NOT NULL DEFAULT '1' COMMENT '版本号(DR07)',
    is_deleted           SMALLINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除(DR05)',
    domain               VARCHAR(50)  NOT NULL DEFAULT 'default' COMMENT '域名(DR08)'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='列级安全策略表';

CREATE INDEX idx_cls_policy_table ON ecos_cls_policy (table_name, scope_type, enabled);

-- ============================================
-- Table: ecos_security_asset
-- Description: 敏感数据目录（表/列/级别/防护组合/豁免引用），REQ-SEC-01 §1.1 六类对象落库
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_security_asset (
    id             VARCHAR(36)  PRIMARY KEY COMMENT 'ID(应用侧UUID, MC01)',
    asset_key      VARCHAR(192) NOT NULL COMMENT 'schema.table.column 或 schema.table',
    kind           VARCHAR(16)  NOT NULL COMMENT 'COLUMN|TABLE',
    sensitivity    VARCHAR(8)   NOT NULL COMMENT 'S0|S1|S2|S3|S4',
    guard_combo    VARCHAR(64)  NOT NULL COMMENT '防护组合(固定token: rls+cls+mask)',
    exemption_ref  VARCHAR(64) COMMENT 'ST03-A豁免登记表条目号(无外键)',
    owner_role     VARCHAR(64) COMMENT '责任角色',
    description    VARCHAR(500) COMMENT '描述',
    version_no     VARCHAR(20)  NOT NULL COMMENT '版本号(DR07)',
    is_deleted     SMALLINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除(DR05)',
    domain         VARCHAR(50)  NOT NULL DEFAULT 'default' COMMENT '域名(DR08)',
    tenant_id      VARCHAR(36) COMMENT '租户ID',
    create_time    DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间(DR06)',
    update_time    DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间(DR06)',
    create_by      VARCHAR(100) COMMENT '创建人(DR06)',
    update_by      VARCHAR(100) COMMENT '更新人(DR06)'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='敏感数据目录表';

-- 跨库等价：PG 侧为部分唯一索引 WHERE is_deleted=0；MySQL 无部分索引，改用普通唯一，软删互斥由应用侧判定
CREATE UNIQUE INDEX uniq_sec_asset_key ON ecos_security_asset (tenant_id, asset_key);

-- ============================================
-- Table: ecos_security_crypto_key
-- Description: 加密密钥治理表（仅存引用名，禁存密钥材料 R7）；替代 knownLegacy td_crypto_key 写入
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_security_crypto_key (
    id             VARCHAR(36)  PRIMARY KEY COMMENT 'ID(应用侧UUID, MC01)',
    key_id         VARCHAR(64)  NOT NULL COMMENT '密钥标识',
    algorithm      VARCHAR(32)  NOT NULL COMMENT '算法(唯一允许AES-256-GCM, W37)',
    key_version    SMALLINT     NOT NULL COMMENT '密钥版本',
    status         VARCHAR(16)  NOT NULL DEFAULT 'active' COMMENT 'active|retired|destroyed',
    master_key_ref VARCHAR(128) NOT NULL COMMENT '主钥引用名(env/KMS key id, 禁存材料)',
    rotate_at      DATETIME COMMENT '轮换时间',
    retired_at     DATETIME COMMENT '停用时间',
    create_time    DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间(DR06)',
    update_time    DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间(DR06)',
    create_by      VARCHAR(100) COMMENT '创建人(DR06)',
    update_by      VARCHAR(100) COMMENT '更新人(DR06)',
    version_no     VARCHAR(20)  NOT NULL COMMENT '版本号(DR07)',
    is_deleted     SMALLINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除(DR05)',
    domain         VARCHAR(50)  NOT NULL DEFAULT 'default' COMMENT '域名(DR08)',
    tenant_id      VARCHAR(36) COMMENT '租户ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='加密密钥治理表';

CREATE UNIQUE INDEX uniq_crypto_key_ver ON ecos_security_crypto_key (key_id, key_version);

-- ============================================
-- Foreign Keys (cross-domain)
-- ============================================
ALTER TABLE td_user_security_profile
    ADD CONSTRAINT fk_security_user_prof_user FOREIGN KEY (user_id) REFERENCES td_user(user_id);
ALTER TABLE td_role_security_profile
    ADD CONSTRAINT fk_security_role_prof_role FOREIGN KEY (role_id) REFERENCES td_role(role_id);
ALTER TABLE ecos_spans
    ADD CONSTRAINT fk_security_spans_tenant   FOREIGN KEY (tenant_id) REFERENCES ecos_tenant(id);
