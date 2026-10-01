-- ============================================================================
-- ECOS System Management Domain — MySQL DDL
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

-- ============================================
-- Table: td_user
-- Description: 用户表
-- ============================================
CREATE TABLE IF NOT EXISTS td_user (
    user_id         VARCHAR(64)  PRIMARY KEY COMMENT '用户ID',
    username        VARCHAR(128) NOT NULL COMMENT '用户名',
    password        VARCHAR(512) NOT NULL COMMENT '密码哈希',
    email           VARCHAR(256) COMMENT '邮箱',
    mobile_tel1     VARCHAR(32) COMMENT '手机号',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/LOCKED/DISABLED',
    locked          VARCHAR(1)   NOT NULL DEFAULT '0' COMMENT '是否锁定: 0/1',
    lock_time       DATETIME COMMENT '锁定时间',
    last_login_time DATETIME COMMENT '最后登录时间',
    mfa_secret      VARCHAR(128) COMMENT 'MFA密钥',
    mfa_type        VARCHAR(16) COMMENT 'MFA类型',
    mfa_enabled     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否启用MFA',
    tenant_id       VARCHAR(64) COMMENT '租户ID',
    created_time    DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    created_by      VARCHAR(64) COMMENT '创建人',
    updated_time    DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间',
    updated_by      VARCHAR(64) COMMENT '更新人'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE UNIQUE INDEX idx_sysman_user_username ON td_user(username);
CREATE UNIQUE INDEX idx_sysman_user_email    ON td_user(email);
CREATE        INDEX idx_sysman_user_tenant   ON td_user(tenant_id);
CREATE        INDEX idx_sysman_user_status   ON td_user(status);

-- ============================================
-- Table: td_role
-- Description: 角色表
-- ============================================
CREATE TABLE IF NOT EXISTS td_role (
    role_id        VARCHAR(64)  PRIMARY KEY COMMENT '角色ID',
    role_name      VARCHAR(128) NOT NULL COMMENT '角色名称',
    role_code      VARCHAR(64)  NOT NULL COMMENT '角色编码',
    description    VARCHAR(512) COMMENT '描述',
    parent_role_id VARCHAR(64) COMMENT '父角色ID',
    tenant_id      VARCHAR(64) COMMENT '租户ID',
    created_time   DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    created_by     VARCHAR(64) COMMENT '创建人',
    updated_time   DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间',
    updated_by     VARCHAR(64) COMMENT '更新人'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

CREATE UNIQUE INDEX idx_sysman_role_code    ON td_role(role_code);
CREATE        INDEX idx_sysman_role_parent  ON td_role(parent_role_id);

-- ============================================
-- Table: td_permission
-- Description: 权限表
-- ============================================
CREATE TABLE IF NOT EXISTS td_permission (
    permission_id   VARCHAR(64)  PRIMARY KEY COMMENT '权限ID',
    permission_name VARCHAR(256) NOT NULL COMMENT '权限名称',
    permission_code VARCHAR(256) NOT NULL COMMENT '权限编码',
    resource_id     VARCHAR(128) COMMENT '资源ID',
    action          VARCHAR(64) COMMENT '操作类型',
    description     VARCHAR(512) COMMENT '描述',
    tenant_id       VARCHAR(64) COMMENT '租户ID',
    created_time    DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    created_by      VARCHAR(64) COMMENT '创建人',
    updated_time    DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间',
    updated_by      VARCHAR(64) COMMENT '更新人'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

CREATE UNIQUE INDEX idx_sysman_perm_res_action ON td_permission(resource_id, action);

-- ============================================
-- Table: td_user_role
-- Description: 用户角色关联表
-- ============================================
CREATE TABLE IF NOT EXISTS td_user_role (
    user_id      VARCHAR(64) NOT NULL COMMENT '用户ID',
    role_id      VARCHAR(64) NOT NULL COMMENT '角色ID',
    org_id       VARCHAR(64) DEFAULT '-1' COMMENT '组织ID',
    created_time DATETIME    NOT NULL DEFAULT NOW() COMMENT '创建时间',
    created_by   VARCHAR(64) COMMENT '创建人',
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- ============================================
-- Table: td_role_permission
-- Description: 角色权限关联表
-- ============================================
CREATE TABLE IF NOT EXISTS td_role_permission (
    role_id       VARCHAR(64) NOT NULL COMMENT '角色ID',
    permission_id VARCHAR(64) NOT NULL COMMENT '权限ID',
    created_time  DATETIME    NOT NULL DEFAULT NOW() COMMENT '创建时间',
    created_by    VARCHAR(64) COMMENT '创建人',
    PRIMARY KEY (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- ============================================
-- Table: td_organization
-- Description: 组织机构表
-- ============================================
CREATE TABLE IF NOT EXISTS td_organization (
    `ORG_ID`        VARCHAR(64)  PRIMARY KEY COMMENT '组织ID',
    `ORG_NAME`      VARCHAR(256) NOT NULL COMMENT '组织名称',
    `ORG_CODE`      VARCHAR(128) NOT NULL COMMENT '组织编码',
    `PARENT_ORG_ID` VARCHAR(64) COMMENT '父组织ID',
    `ORG_TYPE`      VARCHAR(32) COMMENT '组织类型',
    `DESCRIPTION`   VARCHAR(512) COMMENT '描述',
    `STATUS`        VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT '状态',
    `REMARK`        VARCHAR(512) COMMENT '备注',
    `TENANT_ID`     VARCHAR(64) COMMENT '租户ID',
    `CREATED_TIME`  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    `CREATED_BY`    VARCHAR(64) COMMENT '创建人',
    `UPDATED_TIME`  DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间',
    `UPDATED_BY`    VARCHAR(64) COMMENT '更新人'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织机构表';

CREATE UNIQUE INDEX idx_sysman_org_code   ON td_organization(`ORG_CODE`);
CREATE        INDEX idx_sysman_org_parent ON td_organization(`PARENT_ORG_ID`);

-- ============================================
-- Table: td_user_organization
-- Description: 用户组织关联表
-- ============================================
CREATE TABLE IF NOT EXISTS td_user_organization (
    `USER_ID`      VARCHAR(64) NOT NULL COMMENT '用户ID',
    `ORG_ID`       VARCHAR(64) NOT NULL COMMENT '组织ID',
    `IS_PRIMARY`   VARCHAR(1)  DEFAULT '1' COMMENT '是否主组织',
    `CREATED_TIME` DATETIME    NOT NULL DEFAULT NOW() COMMENT '创建时间',
    `CREATED_BY`   VARCHAR(64) COMMENT '创建人',
    PRIMARY KEY (`USER_ID`, `ORG_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户组织关联表';

-- ============================================
-- Table: td_org_permission
-- Description: 组织权限表
-- ============================================
CREATE TABLE IF NOT EXISTS td_org_permission (
    permission_id       VARCHAR(64)  PRIMARY KEY COMMENT '权限ID',
    org_id              VARCHAR(64)  NOT NULL COMMENT '组织ID',
    resource_id         VARCHAR(128) COMMENT '资源ID',
    action              VARCHAR(64) COMMENT '操作类型',
    inherit_from_parent VARCHAR(1)   DEFAULT '0' COMMENT '是否继承父级',
    created_time        DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    created_by          VARCHAR(64) COMMENT '创建人'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织权限表';

CREATE INDEX idx_sysman_orgperm_org ON td_org_permission(org_id);

-- ============================================
-- Table: td_config
-- Description: 系统配置表
-- ============================================
CREATE TABLE IF NOT EXISTS td_config (
    config_id      VARCHAR(64)  PRIMARY KEY COMMENT '配置ID',
    config_type    VARCHAR(64)  NOT NULL COMMENT '配置类型',
    config_name    VARCHAR(256) NOT NULL COMMENT '配置名称',
    config_content LONGTEXT COMMENT '配置内容',
    version        VARCHAR(32) COMMENT '版本',
    environment    VARCHAR(32) COMMENT '环境',
    tenant_id      VARCHAR(64) COMMENT '租户ID',
    created_time   DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    created_by     VARCHAR(64) COMMENT '创建人',
    updated_time   DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间',
    updated_by     VARCHAR(64) COMMENT '更新人'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

CREATE INDEX idx_sysman_config_type_env ON td_config(config_type, environment);

-- ============================================
-- Table: sys_config
-- Description: 系统参数配置表
-- ============================================
CREATE TABLE IF NOT EXISTS sys_config (
    id              VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    config_group    VARCHAR(64)  NOT NULL COMMENT '配置分组',
    config_key      VARCHAR(128) NOT NULL COMMENT '配置键',
    config_value    LONGTEXT COMMENT '配置值',
    config_type     VARCHAR(32)  DEFAULT 'string' COMMENT '值类型',
    config_label    VARCHAR(255) COMMENT '标签(中文)',
    config_label_en VARCHAR(255) COMMENT '标签(英文)',
    description     LONGTEXT COMMENT '描述',
    sort_order      INT          DEFAULT 0 COMMENT '排序',
    status          VARCHAR(32)  DEFAULT 'active' COMMENT '状态',
    tenant_id       VARCHAR(64) COMMENT '租户ID',
    created_at      DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统参数配置表';

CREATE UNIQUE INDEX idx_sysman_sysconfig_key ON sys_config(config_key);

-- ============================================
-- Table: sys_dict
-- Description: 数据字典表
-- ============================================
CREATE TABLE IF NOT EXISTS sys_dict (
    id             VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    dict_type      VARCHAR(64)  NOT NULL COMMENT '字典类型',
    dict_code      VARCHAR(128) NOT NULL COMMENT '字典编码',
    dict_label     VARCHAR(255) COMMENT '标签(中文)',
    dict_label_en  VARCHAR(255) COMMENT '标签(英文)',
    sort_order     INT          DEFAULT 0 COMMENT '排序',
    status         VARCHAR(32)  DEFAULT 'active' COMMENT '状态',
    parent_code    VARCHAR(128) COMMENT '父编码',
    ext_value      VARCHAR(255) COMMENT '扩展值',
    tenant_id      VARCHAR(64) COMMENT '租户ID',
    created_at     DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at     DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据字典表';

CREATE UNIQUE INDEX idx_sysman_dict_type_code ON sys_dict(dict_type, dict_code);

-- ============================================
-- Table: ecos_tenant
-- Description: 租户表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_tenant (
    id              VARCHAR(32)  PRIMARY KEY COMMENT 'ID',
    tenant_name     VARCHAR(64)  NOT NULL COMMENT '租户名称',
    tenant_code     VARCHAR(32)  NOT NULL COMMENT '租户编码',
    status          VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT '状态',
    max_users       INT          DEFAULT 0 COMMENT '最大用户数',
    max_storage_mb  BIGINT       DEFAULT 0 COMMENT '最大存储(MB)',
    max_api_per_day BIGINT       DEFAULT 0 COMMENT '最大API调用/天',
    isolation_mode  VARCHAR(16)  DEFAULT 'ROW_FILTER' COMMENT '隔离模式: ROW_FILTER/SCHEMA/DB',
    schema_name     VARCHAR(64) COMMENT 'Schema名称',
    database_url    VARCHAR(256) COMMENT '数据库URL',
    created_at      DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表';

CREATE UNIQUE INDEX idx_sysman_tenant_code ON ecos_tenant(tenant_code);

-- ============================================
-- Table: ecos_tenant_quota
-- Description: 租户配额表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_tenant_quota (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    tenant_id     VARCHAR(64)  NOT NULL COMMENT '租户ID',
    quota_type    VARCHAR(32)  NOT NULL COMMENT '配额类型',
    daily_limit   BIGINT       DEFAULT 0 COMMENT '日限额',
    monthly_limit BIGINT       DEFAULT 0 COMMENT '月限额',
    created_at    DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户配额表';

CREATE UNIQUE INDEX idx_sysman_quota_tenant_type ON ecos_tenant_quota(tenant_id, quota_type);

-- ============================================
-- Table: ecos_tenant_usage
-- Description: 租户用量表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_tenant_usage (
    tenant_id   VARCHAR(64) NOT NULL COMMENT '租户ID',
    usage_date  DATE        NOT NULL COMMENT '使用日期',
    quota_type  VARCHAR(32) NOT NULL COMMENT '配额类型',
    used_count  BIGINT      DEFAULT 0 COMMENT '使用量',
    updated_at  DATETIME    NOT NULL DEFAULT NOW() COMMENT '更新时间',
    PRIMARY KEY (tenant_id, usage_date, quota_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户用量表';

-- ============================================
-- Table: users
-- Description: 简化用户表(认证用)
-- ============================================
CREATE TABLE IF NOT EXISTS users (
    id            VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    username      VARCHAR(128) NOT NULL COMMENT '用户名',
    password_hash VARCHAR(512) NOT NULL COMMENT '密码哈希',
    display_name  VARCHAR(256) COMMENT '显示名',
    roles         LONGTEXT COMMENT '角色',
    enabled       TINYINT(1)   DEFAULT 1 COMMENT '是否启用',
    tenant_id     VARCHAR(64) COMMENT '租户ID',
    created_at    DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简化用户表(认证用)';

CREATE UNIQUE INDEX idx_sysman_users_username ON users(username);

-- ============================================
-- Table: demo_customer
-- Description: 演示客户表
-- ============================================
CREATE TABLE IF NOT EXISTS demo_customer (
    id          VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    name        VARCHAR(255) NOT NULL COMMENT '名称',
    industry    VARCHAR(128) COMMENT '行业',
    region      VARCHAR(64) COMMENT '区域',
    level       VARCHAR(16) COMMENT '等级',
    credit_score INT COMMENT '信用分',
    status      VARCHAR(32)  DEFAULT 'active' COMMENT '状态',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='演示客户表';

-- ============================================
-- Table: demo_supplier
-- Description: 演示供应商表
-- ============================================
CREATE TABLE IF NOT EXISTS demo_supplier (
    id              VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    name            VARCHAR(255) NOT NULL COMMENT '名称',
    industry        VARCHAR(128) COMMENT '行业',
    region          VARCHAR(64) COMMENT '区域',
    level           VARCHAR(16) COMMENT '等级',
    supply_capacity VARCHAR(128) COMMENT '供应能力',
    status          VARCHAR(32)  DEFAULT 'active' COMMENT '状态',
    tenant_id       VARCHAR(64) COMMENT '租户ID',
    created_at      DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='演示供应商表';

-- ============================================
-- Table: demo_invoice
-- Description: 演示发票表
-- ============================================
CREATE TABLE IF NOT EXISTS demo_invoice (
    id          VARCHAR(64)   PRIMARY KEY COMMENT 'ID',
    amount      DECIMAL(12,2) COMMENT '金额',
    customer_id VARCHAR(64) COMMENT '客户ID',
    supplier_id VARCHAR(64) COMMENT '供应商ID',
    status      VARCHAR(32)   DEFAULT 'pending' COMMENT '状态',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_at  DATETIME      NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='演示发票表';

-- ============================================
-- Foreign Keys
-- ============================================
ALTER TABLE td_user_role
    ADD CONSTRAINT fk_sysman_user_role_user FOREIGN KEY (user_id) REFERENCES td_user(user_id);
ALTER TABLE td_user_role
    ADD CONSTRAINT fk_sysman_user_role_role FOREIGN KEY (role_id) REFERENCES td_role(role_id);
ALTER TABLE td_role_permission
    ADD CONSTRAINT fk_sysman_role_perm_role FOREIGN KEY (role_id) REFERENCES td_role(role_id);
ALTER TABLE td_role_permission
    ADD CONSTRAINT fk_sysman_role_perm_perm FOREIGN KEY (permission_id) REFERENCES td_permission(permission_id);
ALTER TABLE td_user_organization
    ADD CONSTRAINT fk_sysman_user_org_user FOREIGN KEY (`USER_ID`) REFERENCES td_user(user_id);
ALTER TABLE td_user_organization
    ADD CONSTRAINT fk_sysman_user_org_org  FOREIGN KEY (`ORG_ID`) REFERENCES td_organization(`ORG_ID`);
ALTER TABLE td_org_permission
    ADD CONSTRAINT fk_sysman_orgperm_org   FOREIGN KEY (org_id) REFERENCES td_organization(`ORG_ID`);
ALTER TABLE ecos_tenant_quota
    ADD CONSTRAINT fk_sysman_quota_tenant  FOREIGN KEY (tenant_id) REFERENCES ecos_tenant(id);
ALTER TABLE ecos_tenant_usage
    ADD CONSTRAINT fk_sysman_usage_tenant  FOREIGN KEY (tenant_id) REFERENCES ecos_tenant(id);
