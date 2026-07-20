-- ============================================================================
-- ECOS System Management Domain — PostgreSQL DDL
-- Schema: ecos_sysman
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS ecos_sysman;

-- ============================================
-- Table: td_user
-- Description: 用户表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_sysman.td_user (
    user_id        VARCHAR(64)  PRIMARY KEY,
    username       VARCHAR(128) NOT NULL,
    password       VARCHAR(512) NOT NULL,
    email          VARCHAR(256),
    mobile_tel1    VARCHAR(32),
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    locked         VARCHAR(1)   NOT NULL DEFAULT '0',
    lock_time      TIMESTAMP,
    last_login_time TIMESTAMP,
    mfa_secret     VARCHAR(128),
    mfa_type       VARCHAR(16),
    mfa_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    tenant_id      VARCHAR(64),
    created_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(64),
    updated_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by     VARCHAR(64)
);

COMMENT ON TABLE  ecos_sysman.td_user IS '用户表';
COMMENT ON COLUMN ecos_sysman.td_user.user_id IS '用户ID';
COMMENT ON COLUMN ecos_sysman.td_user.username IS '用户名';
COMMENT ON COLUMN ecos_sysman.td_user.password IS '密码哈希';
COMMENT ON COLUMN ecos_sysman.td_user.email IS '邮箱';
COMMENT ON COLUMN ecos_sysman.td_user.mobile_tel1 IS '手机号';
COMMENT ON COLUMN ecos_sysman.td_user.status IS '状态: ACTIVE/LOCKED/DISABLED';
COMMENT ON COLUMN ecos_sysman.td_user.locked IS '是否锁定: 0/1';
COMMENT ON COLUMN ecos_sysman.td_user.lock_time IS '锁定时间';
COMMENT ON COLUMN ecos_sysman.td_user.last_login_time IS '最后登录时间';
COMMENT ON COLUMN ecos_sysman.td_user.mfa_secret IS 'MFA密钥';
COMMENT ON COLUMN ecos_sysman.td_user.mfa_type IS 'MFA类型';
COMMENT ON COLUMN ecos_sysman.td_user.mfa_enabled IS '是否启用MFA';
COMMENT ON COLUMN ecos_sysman.td_user.tenant_id IS '租户ID';
COMMENT ON COLUMN ecos_sysman.td_user.created_time IS '创建时间';
COMMENT ON COLUMN ecos_sysman.td_user.created_by IS '创建人';
COMMENT ON COLUMN ecos_sysman.td_user.updated_time IS '更新时间';
COMMENT ON COLUMN ecos_sysman.td_user.updated_by IS '更新人';

CREATE UNIQUE INDEX IF NOT EXISTS idx_sysman_user_username ON ecos_sysman.td_user(username);
CREATE UNIQUE INDEX IF NOT EXISTS idx_sysman_user_email    ON ecos_sysman.td_user(email);
CREATE        INDEX IF NOT EXISTS idx_sysman_user_tenant   ON ecos_sysman.td_user(tenant_id);
CREATE        INDEX IF NOT EXISTS idx_sysman_user_status   ON ecos_sysman.td_user(status);

-- ============================================
-- Table: td_role
-- Description: 角色表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_sysman.td_role (
    role_id        VARCHAR(64)  PRIMARY KEY,
    role_name      VARCHAR(128) NOT NULL,
    role_code      VARCHAR(64)  NOT NULL,
    description    VARCHAR(512),
    parent_role_id VARCHAR(64),
    tenant_id      VARCHAR(64),
    created_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(64),
    updated_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by     VARCHAR(64)
);

COMMENT ON TABLE  ecos_sysman.td_role IS '角色表';
COMMENT ON COLUMN ecos_sysman.td_role.role_id IS '角色ID';
COMMENT ON COLUMN ecos_sysman.td_role.role_name IS '角色名称';
COMMENT ON COLUMN ecos_sysman.td_role.role_code IS '角色编码';
COMMENT ON COLUMN ecos_sysman.td_role.description IS '描述';
COMMENT ON COLUMN ecos_sysman.td_role.parent_role_id IS '父角色ID';
COMMENT ON COLUMN ecos_sysman.td_role.tenant_id IS '租户ID';
COMMENT ON COLUMN ecos_sysman.td_role.created_time IS '创建时间';
COMMENT ON COLUMN ecos_sysman.td_role.created_by IS '创建人';
COMMENT ON COLUMN ecos_sysman.td_role.updated_time IS '更新时间';
COMMENT ON COLUMN ecos_sysman.td_role.updated_by IS '更新人';

CREATE UNIQUE INDEX IF NOT EXISTS idx_sysman_role_code    ON ecos_sysman.td_role(role_code);
CREATE        INDEX IF NOT EXISTS idx_sysman_role_parent  ON ecos_sysman.td_role(parent_role_id);

-- ============================================
-- Table: td_permission
-- Description: 权限表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_sysman.td_permission (
    permission_id   VARCHAR(64)  PRIMARY KEY,
    permission_name VARCHAR(256) NOT NULL,
    permission_code VARCHAR(256) NOT NULL,
    resource_id     VARCHAR(128),
    action          VARCHAR(64),
    description     VARCHAR(512),
    tenant_id       VARCHAR(64),
    created_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(64),
    updated_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(64)
);

COMMENT ON TABLE  ecos_sysman.td_permission IS '权限表';
COMMENT ON COLUMN ecos_sysman.td_permission.permission_id IS '权限ID';
COMMENT ON COLUMN ecos_sysman.td_permission.permission_name IS '权限名称';
COMMENT ON COLUMN ecos_sysman.td_permission.permission_code IS '权限编码';
COMMENT ON COLUMN ecos_sysman.td_permission.resource_id IS '资源ID';
COMMENT ON COLUMN ecos_sysman.td_permission.action IS '操作类型';
COMMENT ON COLUMN ecos_sysman.td_permission.description IS '描述';
COMMENT ON COLUMN ecos_sysman.td_permission.tenant_id IS '租户ID';

CREATE UNIQUE INDEX IF NOT EXISTS idx_sysman_perm_res_action ON ecos_sysman.td_permission(resource_id, action);

-- ============================================
-- Table: td_user_role
-- Description: 用户角色关联表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_sysman.td_user_role (
    user_id      VARCHAR(64) NOT NULL,
    role_id      VARCHAR(64) NOT NULL,
    org_id       VARCHAR(64) DEFAULT '-1',
    created_time TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(64),
    PRIMARY KEY (user_id, role_id)
);

COMMENT ON TABLE  ecos_sysman.td_user_role IS '用户角色关联表';
COMMENT ON COLUMN ecos_sysman.td_user_role.user_id IS '用户ID';
COMMENT ON COLUMN ecos_sysman.td_user_role.role_id IS '角色ID';
COMMENT ON COLUMN ecos_sysman.td_user_role.org_id IS '组织ID';

-- ============================================
-- Table: td_role_permission
-- Description: 角色权限关联表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_sysman.td_role_permission (
    role_id       VARCHAR(64) NOT NULL,
    permission_id VARCHAR(64) NOT NULL,
    created_time  TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(64),
    PRIMARY KEY (role_id, permission_id)
);

COMMENT ON TABLE  ecos_sysman.td_role_permission IS '角色权限关联表';

-- ============================================
-- Table: td_organization
-- Description: 组织机构表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_sysman.td_organization (
    "ORG_ID"        VARCHAR(64)  PRIMARY KEY,
    "ORG_NAME"      VARCHAR(256) NOT NULL,
    "ORG_CODE"      VARCHAR(128) NOT NULL,
    "PARENT_ORG_ID" VARCHAR(64),
    "ORG_TYPE"      VARCHAR(32),
    "DESCRIPTION"   VARCHAR(512),
    "STATUS"        VARCHAR(16)  DEFAULT 'ACTIVE',
    "REMARK"        VARCHAR(512),
    "TENANT_ID"     VARCHAR(64),
    "CREATED_TIME"  TIMESTAMP    NOT NULL DEFAULT NOW(),
    "CREATED_BY"    VARCHAR(64),
    "UPDATED_TIME"  TIMESTAMP    NOT NULL DEFAULT NOW(),
    "UPDATED_BY"    VARCHAR(64)
);

COMMENT ON TABLE  ecos_sysman.td_organization IS '组织机构表';
COMMENT ON COLUMN ecos_sysman.td_organization."ORG_ID" IS '组织ID';
COMMENT ON COLUMN ecos_sysman.td_organization."ORG_NAME" IS '组织名称';
COMMENT ON COLUMN ecos_sysman.td_organization."ORG_CODE" IS '组织编码';
COMMENT ON COLUMN ecos_sysman.td_organization."PARENT_ORG_ID" IS '父组织ID';
COMMENT ON COLUMN ecos_sysman.td_organization."ORG_TYPE" IS '组织类型';
COMMENT ON COLUMN ecos_sysman.td_organization."STATUS" IS '状态';
COMMENT ON COLUMN ecos_sysman.td_organization."TENANT_ID" IS '租户ID';

CREATE UNIQUE INDEX IF NOT EXISTS idx_sysman_org_code   ON ecos_sysman.td_organization("ORG_CODE");
CREATE        INDEX IF NOT EXISTS idx_sysman_org_parent ON ecos_sysman.td_organization("PARENT_ORG_ID");

-- ============================================
-- Table: td_user_organization
-- Description: 用户组织关联表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_sysman.td_user_organization (
    "USER_ID"      VARCHAR(64) NOT NULL,
    "ORG_ID"       VARCHAR(64) NOT NULL,
    "IS_PRIMARY"   VARCHAR(1)  DEFAULT '1',
    "CREATED_TIME" TIMESTAMP   NOT NULL DEFAULT NOW(),
    "CREATED_BY"   VARCHAR(64),
    PRIMARY KEY ("USER_ID", "ORG_ID")
);

COMMENT ON TABLE  ecos_sysman.td_user_organization IS '用户组织关联表';

-- ============================================
-- Table: td_org_permission
-- Description: 组织权限表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_sysman.td_org_permission (
    permission_id       VARCHAR(64)  PRIMARY KEY,
    org_id              VARCHAR(64)  NOT NULL,
    resource_id         VARCHAR(128),
    action              VARCHAR(64),
    inherit_from_parent VARCHAR(1)   DEFAULT '0',
    created_time        TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(64)
);

COMMENT ON TABLE  ecos_sysman.td_org_permission IS '组织权限表';
CREATE INDEX IF NOT EXISTS idx_sysman_orgperm_org ON ecos_sysman.td_org_permission(org_id);

-- ============================================
-- Table: td_config
-- Description: 系统配置表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_sysman.td_config (
    config_id      VARCHAR(64)  PRIMARY KEY,
    config_type    VARCHAR(64)  NOT NULL,
    config_name    VARCHAR(256) NOT NULL,
    config_content TEXT,
    version        VARCHAR(32),
    environment    VARCHAR(32),
    tenant_id      VARCHAR(64),
    created_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(64),
    updated_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by     VARCHAR(64)
);

COMMENT ON TABLE  ecos_sysman.td_config IS '系统配置表';
CREATE INDEX IF NOT EXISTS idx_sysman_config_type_env ON ecos_sysman.td_config(config_type, environment);

-- ============================================
-- Table: sys_config
-- Description: 系统参数配置表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_sysman.sys_config (
    id              VARCHAR(64)  PRIMARY KEY,
    config_group    VARCHAR(64)  NOT NULL,
    config_key      VARCHAR(128) NOT NULL,
    config_value    TEXT,
    config_type     VARCHAR(32)  DEFAULT 'string',
    config_label    VARCHAR(255),
    config_label_en VARCHAR(255),
    description     TEXT,
    sort_order      INT          DEFAULT 0,
    status          VARCHAR(32)  DEFAULT 'active',
    tenant_id       VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_sysman.sys_config IS '系统参数配置表';
CREATE UNIQUE INDEX IF NOT EXISTS idx_sysman_sysconfig_key ON ecos_sysman.sys_config(config_key);

-- ============================================
-- Table: sys_dict
-- Description: 数据字典表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_sysman.sys_dict (
    id             VARCHAR(64)  PRIMARY KEY,
    dict_type      VARCHAR(64)  NOT NULL,
    dict_code      VARCHAR(128) NOT NULL,
    dict_label     VARCHAR(255),
    dict_label_en  VARCHAR(255),
    sort_order     INT          DEFAULT 0,
    status         VARCHAR(32)  DEFAULT 'active',
    parent_code    VARCHAR(128),
    ext_value      VARCHAR(255),
    tenant_id      VARCHAR(64),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_sysman.sys_dict IS '数据字典表';
CREATE UNIQUE INDEX IF NOT EXISTS idx_sysman_dict_type_code ON ecos_sysman.sys_dict(dict_type, dict_code);

-- ============================================
-- Table: ecos_tenant
-- Description: 租户表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_sysman.ecos_tenant (
    id              VARCHAR(32)  PRIMARY KEY,
    tenant_name     VARCHAR(64)  NOT NULL,
    tenant_code     VARCHAR(32)  NOT NULL,
    status          VARCHAR(16)  DEFAULT 'ACTIVE',
    max_users       INT          DEFAULT 0,
    max_storage_mb  BIGINT       DEFAULT 0,
    max_api_per_day BIGINT       DEFAULT 0,
    isolation_mode  VARCHAR(16)  DEFAULT 'ROW_FILTER',
    schema_name     VARCHAR(64),
    database_url    VARCHAR(256),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_sysman.ecos_tenant IS '租户表';
COMMENT ON COLUMN ecos_sysman.ecos_tenant.isolation_mode IS '隔离模式: ROW_FILTER/SCHEMA/DB';
CREATE UNIQUE INDEX IF NOT EXISTS idx_sysman_tenant_code ON ecos_sysman.ecos_tenant(tenant_code);

-- ============================================
-- Table: ecos_tenant_quota
-- Description: 租户配额表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_sysman.ecos_tenant_quota (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     VARCHAR(64)  NOT NULL,
    quota_type    VARCHAR(32)  NOT NULL,
    daily_limit   BIGINT       DEFAULT 0,
    monthly_limit BIGINT       DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_sysman.ecos_tenant_quota IS '租户配额表';
CREATE UNIQUE INDEX IF NOT EXISTS idx_sysman_quota_tenant_type ON ecos_sysman.ecos_tenant_quota(tenant_id, quota_type);

-- ============================================
-- Table: ecos_tenant_usage
-- Description: 租户用量表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_sysman.ecos_tenant_usage (
    tenant_id   VARCHAR(64) NOT NULL,
    usage_date  DATE        NOT NULL,
    quota_type  VARCHAR(32) NOT NULL,
    used_count  BIGINT      DEFAULT 0,
    updated_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, usage_date, quota_type)
);

COMMENT ON TABLE  ecos_sysman.ecos_tenant_usage IS '租户用量表';

-- ============================================
-- Table: users
-- Description: 简化用户表(认证用)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_sysman.users (
    id            VARCHAR(64)  PRIMARY KEY,
    username      VARCHAR(128) NOT NULL,
    password_hash VARCHAR(512) NOT NULL,
    display_name  VARCHAR(256),
    roles         TEXT,
    enabled       BOOLEAN      DEFAULT TRUE,
    tenant_id     VARCHAR(64),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_sysman.users IS '简化用户表(认证用)';
CREATE UNIQUE INDEX IF NOT EXISTS idx_sysman_users_username ON ecos_sysman.users(username);

-- ============================================
-- Table: demo_customer
-- Description: 演示客户表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_sysman.demo_customer (
    id          VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    industry    VARCHAR(128),
    region      VARCHAR(64),
    level       VARCHAR(16),
    credit_score INTEGER,
    status      VARCHAR(32)  DEFAULT 'active',
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_sysman.demo_customer IS '演示客户表';

-- ============================================
-- Table: demo_supplier
-- Description: 演示供应商表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_sysman.demo_supplier (
    id              VARCHAR(64)  PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    industry        VARCHAR(128),
    region          VARCHAR(64),
    level           VARCHAR(16),
    supply_capacity VARCHAR(128),
    status          VARCHAR(32)  DEFAULT 'active',
    tenant_id       VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_sysman.demo_supplier IS '演示供应商表';

-- ============================================
-- Table: demo_invoice
-- Description: 演示发票表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_sysman.demo_invoice (
    id          VARCHAR(64)  PRIMARY KEY,
    amount      DECIMAL(12,2),
    customer_id VARCHAR(64),
    supplier_id VARCHAR(64),
    status      VARCHAR(32)  DEFAULT 'pending',
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_sysman.demo_invoice IS '演示发票表';

-- ============================================
-- Foreign Keys
-- ============================================
ALTER TABLE ecos_sysman.td_user_role
    ADD CONSTRAINT fk_sysman_user_role_user FOREIGN KEY (user_id) REFERENCES ecos_sysman.td_user(user_id);
ALTER TABLE ecos_sysman.td_user_role
    ADD CONSTRAINT fk_sysman_user_role_role FOREIGN KEY (role_id) REFERENCES ecos_sysman.td_role(role_id);
ALTER TABLE ecos_sysman.td_role_permission
    ADD CONSTRAINT fk_sysman_role_perm_role FOREIGN KEY (role_id) REFERENCES ecos_sysman.td_role(role_id);
ALTER TABLE ecos_sysman.td_role_permission
    ADD CONSTRAINT fk_sysman_role_perm_perm FOREIGN KEY (permission_id) REFERENCES ecos_sysman.td_permission(permission_id);
ALTER TABLE ecos_sysman.td_user_organization
    ADD CONSTRAINT fk_sysman_user_org_user FOREIGN KEY ("USER_ID") REFERENCES ecos_sysman.td_user(user_id);
ALTER TABLE ecos_sysman.td_user_organization
    ADD CONSTRAINT fk_sysman_user_org_org  FOREIGN KEY ("ORG_ID") REFERENCES ecos_sysman.td_organization("ORG_ID");
ALTER TABLE ecos_sysman.td_org_permission
    ADD CONSTRAINT fk_sysman_orgperm_org   FOREIGN KEY (org_id) REFERENCES ecos_sysman.td_organization("ORG_ID");
ALTER TABLE ecos_sysman.ecos_tenant_quota
    ADD CONSTRAINT fk_sysman_quota_tenant  FOREIGN KEY (tenant_id) REFERENCES ecos_sysman.ecos_tenant(id);
ALTER TABLE ecos_sysman.ecos_tenant_usage
    ADD CONSTRAINT fk_sysman_usage_tenant  FOREIGN KEY (tenant_id) REFERENCES ecos_sysman.ecos_tenant(id);
