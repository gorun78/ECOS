-- ============================================================
-- V24__ecos_security_profile.sql — 用户/角色级联安全配置表
-- ============================================================
-- 设计: User级配置 > Role级配置 > 全局默认
-- 准入等级: L0(公开) L1(内部) L2(保密) L3(机密) L4(绝密)

-- 用户级安全配置表
CREATE TABLE IF NOT EXISTS td_user_security_profile (
    user_id              VARCHAR(64)     PRIMARY KEY,
    clearance_level      INT             DEFAULT 0,        -- 准入等级 0-4
    linked_workstation   VARCHAR(256),                     -- 物理工作站绑定
    audit_mode           VARCHAR(32)     DEFAULT 'basic',  -- 审计力度: basic/detailed/full
    sandbox_mandatory    BOOLEAN         DEFAULT FALSE,    -- 沙盒强制
    is_default           BOOLEAN         DEFAULT FALSE,    -- 是否作为全局默认
    created_at           TIMESTAMP       DEFAULT NOW(),
    updated_at           TIMESTAMP       DEFAULT NOW()
);

COMMENT ON TABLE  td_user_security_profile               IS '用户级安全配置表 — 优先级最高，覆盖角色级和全局默认';
COMMENT ON COLUMN td_user_security_profile.user_id       IS '用户ID，关联 td_user.USER_ID';
COMMENT ON COLUMN td_user_security_profile.clearance_level IS '准入等级 0-4: L0公开 L1内部 L2保密 L3机密 L4绝密';
COMMENT ON COLUMN td_user_security_profile.linked_workstation IS '物理工作站绑定（MAC/IP/主机名）';
COMMENT ON COLUMN td_user_security_profile.audit_mode    IS '审计力度: basic(基本) detailed(详细) full(全量)';
COMMENT ON COLUMN td_user_security_profile.sandbox_mandatory IS '是否强制沙盒运行';
COMMENT ON COLUMN td_user_security_profile.is_default    IS '是否为全局默认配置（仅允许一条记录为TRUE）';

-- 角色级安全配置表
CREATE TABLE IF NOT EXISTS td_role_security_profile (
    role_id              VARCHAR(64)     PRIMARY KEY,
    clearance_level      INT             DEFAULT 0,        -- 准入等级 0-4
    linked_workstation   VARCHAR(256),                     -- 物理工作站绑定
    audit_mode           VARCHAR(32)     DEFAULT 'basic',  -- 审计力度: basic/detailed/full
    sandbox_mandatory    BOOLEAN         DEFAULT FALSE,    -- 沙盒强制
    created_at           TIMESTAMP       DEFAULT NOW(),
    updated_at           TIMESTAMP       DEFAULT NOW()
);

COMMENT ON TABLE  td_role_security_profile               IS '角色级安全配置表 — 优先级次于用户级，高于全局默认';
COMMENT ON COLUMN td_role_security_profile.role_id       IS '角色ID，关联 td_role.ROLE_ID';
COMMENT ON COLUMN td_role_security_profile.clearance_level IS '准入等级 0-4: L0公开 L1内部 L2保密 L3机密 L4绝密';
COMMENT ON COLUMN td_role_security_profile.linked_workstation IS '物理工作站绑定（MAC/IP/主机名）';
COMMENT ON COLUMN td_role_security_profile.audit_mode    IS '审计力度: basic(基本) detailed(详细) full(全量)';
COMMENT ON COLUMN td_role_security_profile.sandbox_mandatory IS '是否强制沙盒运行';

-- 索引
CREATE INDEX IF NOT EXISTS idx_usp_clearance ON td_user_security_profile(clearance_level);
CREATE INDEX IF NOT EXISTS idx_rsp_clearance ON td_role_security_profile(clearance_level);

-- ============================================================
-- 种子数据: 角色级安全配置
-- R001(role_admin) → 绝密 L4; R002(role_operator) → 保密 L2; R003(role_viewer) → 内部 L1
-- ============================================================

-- R001: 系统管理员 → L4 绝密
INSERT INTO td_role_security_profile (role_id, clearance_level, audit_mode, sandbox_mandatory, created_at, updated_at)
VALUES ('R001', 4, 'detailed', TRUE, NOW(), NOW())
ON CONFLICT (role_id) DO UPDATE SET
    clearance_level = 4,
    audit_mode = 'detailed',
    sandbox_mandatory = TRUE,
    updated_at = NOW();

-- R002: 运维操作员 → L2 保密
INSERT INTO td_role_security_profile (role_id, clearance_level, audit_mode, sandbox_mandatory, created_at, updated_at)
VALUES ('R002', 2, 'basic', FALSE, NOW(), NOW())
ON CONFLICT (role_id) DO UPDATE SET
    clearance_level = 2,
    audit_mode = 'basic',
    sandbox_mandatory = FALSE,
    updated_at = NOW();

-- R003: 只读用户 → L1 内部
INSERT INTO td_role_security_profile (role_id, clearance_level, audit_mode, sandbox_mandatory, created_at, updated_at)
VALUES ('R003', 1, 'basic', FALSE, NOW(), NOW())
ON CONFLICT (role_id) DO UPDATE SET
    clearance_level = 1,
    audit_mode = 'basic',
    sandbox_mandatory = FALSE,
    updated_at = NOW();
