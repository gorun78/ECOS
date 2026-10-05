-- ============================================================
-- DataBridge V2 - sys-man 数据库初始化 & 种子数据
-- 数据库: sys-man (localhost:3306)
-- 用户: root / root
-- ============================================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS `sys-man`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `sys-man`;

-- ============================================================
-- DDL: Core IAM Tables
-- ============================================================

-- 用户表
CREATE TABLE IF NOT EXISTS TD_USER (
    user_id         VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT '用户ID',
    username        VARCHAR(128) NOT NULL COMMENT '用户名',
    password        VARCHAR(512) NOT NULL COMMENT '密码（PBKDF2/SHA-256 Base64）',
    email           VARCHAR(256)          COMMENT '邮箱',
    mobile_tel1     VARCHAR(32)           COMMENT '手机号',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE/DELETED',
    locked          VARCHAR(1)   NOT NULL DEFAULT '0' COMMENT '锁定: 0-未锁定, 1-已锁定',
    lock_time       DATETIME              COMMENT '锁定时间',
    last_login_time DATETIME              COMMENT '最后登录时间',
    created_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      VARCHAR(64)           COMMENT '创建者',
    updated_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      VARCHAR(64)           COMMENT '更新者',
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS TD_ROLE (
    role_id         VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT '角色ID',
    role_name       VARCHAR(128) NOT NULL COMMENT '角色名称',
    role_code       VARCHAR(64)  NOT NULL COMMENT '角色编码',
    description     VARCHAR(512)          COMMENT '描述',
    parent_role_id  VARCHAR(64)           COMMENT '父角色ID',
    created_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      VARCHAR(64)           COMMENT '创建者',
    updated_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      VARCHAR(64)           COMMENT '更新者',
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 权限表
CREATE TABLE IF NOT EXISTS TD_PERMISSION (
    permission_id   VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT '权限ID',
    permission_name VARCHAR(256) NOT NULL COMMENT '权限名称',
    permission_code VARCHAR(256) NOT NULL COMMENT '权限编码 (resource:action)',
    resource_id     VARCHAR(128) NOT NULL COMMENT '资源ID',
    action          VARCHAR(64)  NOT NULL COMMENT '操作: READ/WRITE/DELETE/ADMIN',
    description     VARCHAR(512)          COMMENT '描述',
    UNIQUE KEY uk_resource_action (resource_id, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS TD_USER_ROLE (
    user_id         VARCHAR(64) NOT NULL COMMENT '用户ID',
    role_id         VARCHAR(64) NOT NULL COMMENT '角色ID',
    org_id          VARCHAR(64) DEFAULT '-1' COMMENT '机构ID',
    created_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      VARCHAR(64)          COMMENT '创建者',
    PRIMARY KEY (user_id, role_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';

-- 角色-权限关联表
CREATE TABLE IF NOT EXISTS TD_ROLE_PERMISSION (
    role_id         VARCHAR(64) NOT NULL COMMENT '角色ID',
    permission_id   VARCHAR(64) NOT NULL COMMENT '权限ID',
    created_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      VARCHAR(64)          COMMENT '创建者',
    PRIMARY KEY (role_id, permission_id),
    INDEX idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联表';

-- 机构表
CREATE TABLE IF NOT EXISTS TD_ORGANIZATION (
    ORG_ID          VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT '机构ID',
    ORG_NAME        VARCHAR(256) NOT NULL COMMENT '机构名称',
    ORG_CODE        VARCHAR(128) NOT NULL COMMENT '机构编码',
    PARENT_ORG_ID   VARCHAR(64)           COMMENT '父机构ID',
    ORG_TYPE        VARCHAR(32)           COMMENT '机构类型',
    DESCRIPTION     VARCHAR(512)          COMMENT '描述',
    CREATED_TIME    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    CREATED_BY      VARCHAR(64)           COMMENT '创建者',
    UPDATED_TIME    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UPDATED_BY      VARCHAR(64)           COMMENT '更新者',
    STATUS          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE/DELETED',
    REMARK          VARCHAR(512)          COMMENT '备注',
    UNIQUE KEY uk_org_code (ORG_CODE),
    INDEX idx_parent_org (PARENT_ORG_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机构表';

-- 用户-机构关联表
CREATE TABLE IF NOT EXISTS TD_USER_ORGANIZATION (
    USER_ID         VARCHAR(64) NOT NULL COMMENT '用户ID',
    ORG_ID          VARCHAR(64) NOT NULL COMMENT '机构ID',
    IS_PRIMARY      VARCHAR(1)  DEFAULT '1' COMMENT '是否主机构',
    CREATED_TIME    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    CREATED_BY      VARCHAR(64)          COMMENT '创建者',
    PRIMARY KEY (USER_ID, ORG_ID),
    INDEX idx_org_id (ORG_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-机构关联表';

-- 机构权限表
CREATE TABLE IF NOT EXISTS TD_ORG_PERMISSION (
    PERMISSION_ID        VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '权限ID',
    ORG_ID               VARCHAR(64) NOT NULL COMMENT '机构ID',
    RESOURCE_ID          VARCHAR(128) NOT NULL COMMENT '资源ID',
    ACTION               VARCHAR(64) NOT NULL COMMENT '操作',
    INHERIT_FROM_PARENT  VARCHAR(1) DEFAULT '0' COMMENT '是否继承自父机构',
    CREATED_TIME         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    CREATED_BY           VARCHAR(64)          COMMENT '创建者',
    INDEX idx_org_id (ORG_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机构权限表';

-- 配置表（Runtime 使用）
CREATE TABLE IF NOT EXISTS td_config (
    config_id       VARCHAR(64)   NOT NULL PRIMARY KEY COMMENT '配置ID',
    config_type     VARCHAR(64)   NOT NULL COMMENT '配置类型',
    config_name     VARCHAR(256)  NOT NULL COMMENT '配置名称',
    config_content  TEXT                    COMMENT '配置内容',
    version         VARCHAR(32)            COMMENT '版本',
    environment     VARCHAR(32)            COMMENT '环境',
    created_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      VARCHAR(64)            COMMENT '创建者',
    updated_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      VARCHAR(64)            COMMENT '更新者',
    INDEX idx_type_env (config_type, environment)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- ============================================================
-- Seed Data: IAM 种子数据
-- 密码说明: admin123 的 SHA-256 Base64 编码
--   echo -n 'admin123' | sha256sum | xxd -r -p | base64
--   => JAvlGPq9JyTdtvBO6x2llnRI1+gxwIyPqCKAn3THIKk=
-- 兼容: PasswordHasher.matches() 同时支持 PBKDF2 前缀和旧版 SHA-256 Base64
-- ============================================================

-- 1. 种子角色 (admin / operator / user)
INSERT IGNORE INTO TD_ROLE (role_id, role_name, role_code, description, parent_role_id, created_time, created_by, updated_time, updated_by)
VALUES
    ('R001', '系统管理员', 'admin',     '系统管理员，拥有所有权限', NULL,  NOW(), 'seed', NOW(), 'seed'),
    ('R002', '运维操作员', 'operator',  '运维操作员，管理数据集成和任务', NULL, NOW(), 'seed', NOW(), 'seed'),
    ('R003', '普通用户',   'user',      '普通用户，查看和使用数据服务', NULL, NOW(), 'seed', NOW(), 'seed');

-- 2. 种子权限 (各模块基础权限)
INSERT IGNORE INTO TD_PERMISSION (permission_id, permission_name, permission_code, resource_id, action, description)
VALUES
    -- 用户管理
    ('P001', '用户管理:READ',  'user:READ',   'user', 'READ',   '查看用户'),
    ('P002', '用户管理:WRITE', 'user:WRITE',  'user', 'WRITE',  '创建/编辑用户'),
    ('P003', '用户管理:DELETE','user:DELETE', 'user', 'DELETE', '删除用户'),
    -- 角色管理
    ('P004', '角色管理:READ',  'role:READ',   'role', 'READ',   '查看角色'),
    ('P005', '角色管理:WRITE', 'role:WRITE',  'role', 'WRITE',  '创建/编辑角色'),
    ('P006', '角色管理:DELETE','role:DELETE', 'role', 'DELETE', '删除角色'),
    -- 权限管理
    ('P007', '权限管理:READ',  'permission:READ',   'permission', 'READ',   '查看权限'),
    ('P008', '权限管理:WRITE', 'permission:WRITE',  'permission', 'WRITE',  '创建/编辑权限'),
    -- 机构管理
    ('P009', '机构管理:READ',  'organization:READ',  'organization', 'READ',   '查看机构'),
    ('P010', '机构管理:WRITE', 'organization:WRITE', 'organization', 'WRITE',  '创建/编辑机构'),
    ('P011', '机构管理:DELETE','organization:DELETE','organization', 'DELETE', '删除机构'),
    -- 数据源管理
    ('P012', '数据源:READ',   'datasource:READ',   'datasource', 'READ',   '查看数据源'),
    ('P013', '数据源:WRITE',  'datasource:WRITE',  'datasource', 'WRITE',  '创建/编辑数据源'),
    ('P014', '数据源:DELETE', 'datasource:DELETE', 'datasource', 'DELETE', '删除数据源'),
    -- 任务管理
    ('P015', '任务:READ',    'task:READ',    'task', 'READ',    '查看任务'),
    ('P016', '任务:WRITE',   'task:WRITE',   'task', 'WRITE',   '创建/编辑任务'),
    ('P017', '任务:EXECUTE', 'task:EXECUTE', 'task', 'EXECUTE', '执行任务'),
    -- 配置管理
    ('P018', '配置:READ',    'config:READ',    'config', 'READ',    '查看配置'),
    ('P019', '配置:WRITE',   'config:WRITE',   'config', 'WRITE',   '修改配置'),
    -- 审计日志
    ('P020', '审计:READ',    'audit:READ',    'audit', 'READ',    '查看审计日志');

-- 3. 默认机构
INSERT IGNORE INTO TD_ORGANIZATION (ORG_ID, ORG_NAME, ORG_CODE, PARENT_ORG_ID, ORG_TYPE, DESCRIPTION, CREATED_TIME, CREATED_BY, UPDATED_TIME, UPDATED_BY, STATUS, REMARK)
VALUES
    ('ORG-ROOT', '根机构', 'ROOT', NULL, 'ROOT', '系统默认根机构', NOW(), 'seed', NOW(), 'seed', 'ACTIVE', '种子数据');

-- 4. 默认管理员用户 (用户名: admin, 密码: admin123)
INSERT IGNORE INTO TD_USER (user_id, username, password, email, mobile_tel1, status, locked, created_time, created_by, updated_time, updated_by)
VALUES
    ('U001', 'admin', 'JAvlGPq9JyTdtvBO6x2llnRI1+gxwIyPqCKAn3THIKk=', 'admin@databridge.local', '13800000000', 'ACTIVE', '0', NOW(), 'seed', NOW(), 'seed');

-- 5. admin 用户绑定 admin 角色 和 根机构
INSERT IGNORE INTO TD_USER_ROLE (user_id, role_id, org_id, created_time, created_by)
VALUES ('U001', 'R001', '-1', NOW(), 'seed');

INSERT IGNORE INTO TD_USER_ORGANIZATION (USER_ID, ORG_ID, IS_PRIMARY, CREATED_TIME, CREATED_BY)
VALUES ('U001', 'ORG-ROOT', '1', NOW(), 'seed');

-- 6. admin 角色获得所有权限 (角色-权限关联)
INSERT IGNORE INTO TD_ROLE_PERMISSION (role_id, permission_id, created_time, created_by)
SELECT 'R001', permission_id, NOW(), 'seed' FROM TD_PERMISSION;

-- 7. operator 角色获得数据源和任务相关权限
INSERT IGNORE INTO TD_ROLE_PERMISSION (role_id, permission_id, created_time, created_by)
SELECT 'R002', permission_id, NOW(), 'seed' FROM TD_PERMISSION
WHERE resource_id IN ('datasource', 'task', 'config', 'audit') AND action IN ('READ', 'WRITE', 'EXECUTE');

INSERT IGNORE INTO TD_ROLE_PERMISSION (role_id, permission_id, created_time, created_by)
SELECT 'R002', permission_id, NOW(), 'seed' FROM TD_PERMISSION
WHERE resource_id IN ('user', 'organization') AND action = 'READ';

-- 8. user 角色获得只读权限
INSERT IGNORE INTO TD_ROLE_PERMISSION (role_id, permission_id, created_time, created_by)
SELECT 'R003', permission_id, NOW(), 'seed' FROM TD_PERMISSION
WHERE action = 'READ';
