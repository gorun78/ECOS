-- ECOS IAM 核心表（PG 版，建到 ecos_identity schema）
-- 从 ecos-sql/mysql/01_ecos_sysman.sql 转换，ADD-ONLY
CREATE TABLE IF NOT EXISTS ecos_identity.td_user (
    user_id         VARCHAR(64)  PRIMARY KEY,
    username        VARCHAR(128) NOT NULL,
    password        VARCHAR(512) NOT NULL,
    email           VARCHAR(256),
    mobile_tel1     VARCHAR(32),
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    locked          VARCHAR(1)   NOT NULL DEFAULT '0',
    lock_time       TIMESTAMP,
    last_login_time TIMESTAMP,
    mfa_secret      VARCHAR(128),
    mfa_type        VARCHAR(16),
    mfa_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    tenant_id       VARCHAR(64),
    created_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(64),
    updated_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_sysman_user_username ON ecos_identity.td_user(username);
CREATE INDEX IF NOT EXISTS idx_sysman_user_tenant   ON ecos_identity.td_user(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sysman_user_status   ON ecos_identity.td_user(status);

CREATE TABLE IF NOT EXISTS ecos_identity.td_role (
    role_id        VARCHAR(64)  PRIMARY KEY,
    role_name      VARCHAR(128) NOT NULL,
    role_code      VARCHAR(64)  NOT NULL,
    description    VARCHAR(512),
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    parent_role_id VARCHAR(64),
    tenant_id      VARCHAR(64),
    created_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(64),
    updated_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by     VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_sysman_role_code ON ecos_identity.td_role(role_code);
CREATE INDEX IF NOT EXISTS idx_sysman_role_parent      ON ecos_identity.td_role(parent_role_id);

CREATE TABLE IF NOT EXISTS ecos_identity.td_permission (
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
CREATE UNIQUE INDEX IF NOT EXISTS idx_sysman_perm_res_action ON ecos_identity.td_permission(resource_id, action);

CREATE TABLE IF NOT EXISTS ecos_identity.td_user_role (
    user_id      VARCHAR(64) NOT NULL,
    role_id      VARCHAR(64) NOT NULL,
    org_id       VARCHAR(64) DEFAULT '-1',
    created_time TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(64),
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES ecos_identity.td_user(user_id),
    FOREIGN KEY (role_id) REFERENCES ecos_identity.td_role(role_id)
);

CREATE TABLE IF NOT EXISTS ecos_identity.td_role_permission (
    role_id       VARCHAR(64) NOT NULL,
    permission_id VARCHAR(64) NOT NULL,
    created_time  TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(64),
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id)       REFERENCES ecos_identity.td_role(role_id),
    FOREIGN KEY (permission_id) REFERENCES ecos_identity.td_permission(permission_id)
);

CREATE TABLE IF NOT EXISTS ecos_identity.td_organization (
    org_id         VARCHAR(64)  PRIMARY KEY,
    org_name       VARCHAR(256) NOT NULL,
    org_code       VARCHAR(128) NOT NULL,
    parent_org_id  VARCHAR(64),
    org_type       VARCHAR(32),
    description    VARCHAR(512),
    status         VARCHAR(16)  DEFAULT 'ACTIVE',
    remark         VARCHAR(512),
    tenant_id      VARCHAR(64),
    created_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(64),
    updated_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by     VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_sysman_org_code   ON ecos_identity.td_organization(org_code);
CREATE INDEX IF NOT EXISTS idx_sysman_org_parent        ON ecos_identity.td_organization(parent_org_id);

CREATE TABLE IF NOT EXISTS ecos_identity.td_user_organization (
    user_id       VARCHAR(64) NOT NULL,
    org_id        VARCHAR(64) NOT NULL,
    is_primary    VARCHAR(1)  DEFAULT '1',
    created_time  TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(64),
    PRIMARY KEY (user_id, org_id),
    FOREIGN KEY (user_id) REFERENCES ecos_identity.td_user(user_id),
    FOREIGN KEY (org_id)  REFERENCES ecos_identity.td_organization(org_id)
);

CREATE TABLE IF NOT EXISTS ecos_identity.td_org_permission (
    permission_id       VARCHAR(64)  PRIMARY KEY,
    org_id              VARCHAR(64)  NOT NULL,
    resource_id         VARCHAR(128),
    action              VARCHAR(64),
    inherit_from_parent VARCHAR(1)   DEFAULT '0',
    created_time        TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(64),
    FOREIGN KEY (org_id) REFERENCES ecos_identity.td_organization(org_id)
);
CREATE INDEX IF NOT EXISTS idx_sysman_orgperm_org ON ecos_identity.td_org_permission(org_id);

CREATE TABLE IF NOT EXISTS ecos_identity.td_config (
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
CREATE INDEX IF NOT EXISTS idx_sysman_config_type_env ON ecos_identity.td_config(config_type, environment);

CREATE TABLE IF NOT EXISTS ecos_identity.users (
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
CREATE UNIQUE INDEX IF NOT EXISTS idx_sysman_users_username ON ecos_identity.users(username);

CREATE TABLE IF NOT EXISTS ecos_identity.ecos_tenant (
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
CREATE UNIQUE INDEX IF NOT EXISTS idx_sysman_tenant_code ON ecos_identity.ecos_tenant(tenant_code);

CREATE TABLE IF NOT EXISTS ecos_identity.ecos_tenant_quota (
    id            BIGSERIAL    NOT NULL,
    tenant_id     VARCHAR(64)  NOT NULL,
    quota_type    VARCHAR(32)  NOT NULL,
    daily_limit   BIGINT       DEFAULT 0,
    monthly_limit BIGINT       DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES ecos_identity.ecos_tenant(id)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_sysman_quota_tenant_type ON ecos_identity.ecos_tenant_quota(tenant_id, quota_type);

CREATE TABLE IF NOT EXISTS ecos_identity.ecos_tenant_usage (
    tenant_id  VARCHAR(64) NOT NULL,
    usage_date DATE        NOT NULL,
    quota_type VARCHAR(32) NOT NULL,
    used_count BIGINT      DEFAULT 0,
    updated_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, usage_date, quota_type),
    FOREIGN KEY (tenant_id) REFERENCES ecos_identity.ecos_tenant(id)
);
