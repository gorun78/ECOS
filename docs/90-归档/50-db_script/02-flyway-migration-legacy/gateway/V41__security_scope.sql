-- ============================================================
-- V41__security_scope.sql — 安全中心作用域体系
-- ============================================================
-- 设计: 五维度级联 — USER > ROLE > ORG > TENANT > GLOBAL
-- 策略: 在现有表上加列 + 索引，零破坏迁移
-- 前置: V24 (td_user_security_profile, td_role_security_profile)
--        V40 (td_abac_policy, 种子数据)
-- 全部SQL idempotent (IF NOT EXISTS / ON CONFLICT)

-- ============================================================
-- 1. td_user_security_profile — 加作用域列
-- ============================================================
ALTER TABLE td_user_security_profile
    ADD COLUMN IF NOT EXISTS tenant_id      VARCHAR(64),
    ADD COLUMN IF NOT EXISTS org_id         VARCHAR(64),
    ADD COLUMN IF NOT EXISTS scope_type     VARCHAR(16) DEFAULT 'USER';

COMMENT ON COLUMN td_user_security_profile.tenant_id  IS '租户ID (scope_type=TENANT时填充)';
COMMENT ON COLUMN td_user_security_profile.org_id     IS '机构ID (scope_type=ORG时填充)';
COMMENT ON COLUMN td_user_security_profile.scope_type IS '作用域类型: GLOBAL/TENANT/ORG/ROLE/USER';

-- 迁移现有数据: _global_default_ → scope_type='GLOBAL'
UPDATE td_user_security_profile SET scope_type = 'GLOBAL' WHERE user_id = '_global_default_';

-- 索引
CREATE INDEX IF NOT EXISTS idx_usp_tenant    ON td_user_security_profile(tenant_id);
CREATE INDEX IF NOT EXISTS idx_usp_org       ON td_user_security_profile(org_id);
CREATE INDEX IF NOT EXISTS idx_usp_scope     ON td_user_security_profile(scope_type);

-- ============================================================
-- 2. td_role_security_profile — 加作用域列
-- ============================================================
ALTER TABLE td_role_security_profile
    ADD COLUMN IF NOT EXISTS tenant_id      VARCHAR(64),
    ADD COLUMN IF NOT EXISTS org_id         VARCHAR(64),
    ADD COLUMN IF NOT EXISTS scope_type     VARCHAR(16) DEFAULT 'ROLE';

COMMENT ON COLUMN td_role_security_profile.tenant_id  IS '租户ID (scope_type=TENANT时填充)';
COMMENT ON COLUMN td_role_security_profile.org_id     IS '机构ID (scope_type=ORG时填充)';
COMMENT ON COLUMN td_role_security_profile.scope_type IS '作用域类型: GLOBAL/TENANT/ORG/ROLE/USER';

CREATE INDEX IF NOT EXISTS idx_rsp_tenant    ON td_role_security_profile(tenant_id);
CREATE INDEX IF NOT EXISTS idx_rsp_org       ON td_role_security_profile(org_id);
CREATE INDEX IF NOT EXISTS idx_rsp_scope     ON td_role_security_profile(scope_type);

-- ============================================================
-- 3. td_abac_policy — 加作用域列
-- ============================================================
ALTER TABLE td_abac_policy
    ADD COLUMN IF NOT EXISTS scope_type     VARCHAR(16) DEFAULT 'GLOBAL',
    ADD COLUMN IF NOT EXISTS scope_id       VARCHAR(64);

COMMENT ON COLUMN td_abac_policy.scope_type IS '作用域类型: GLOBAL/TENANT/ORG';
COMMENT ON COLUMN td_abac_policy.scope_id   IS '作用域ID (TENANT→tenant_id, ORG→org_id)';

-- 现有策略标记为全局
UPDATE td_abac_policy SET scope_type = 'GLOBAL' WHERE scope_type IS NULL;

CREATE INDEX IF NOT EXISTS idx_abac_scope   ON td_abac_policy(scope_type, scope_id);

-- ============================================================
-- 4. 种子: 租户级安全配置 (tenant_id='default')
-- ============================================================
INSERT INTO td_user_security_profile (user_id, clearance_level, audit_mode, sandbox_mandatory, is_default, scope_type, tenant_id, created_at, updated_at)
VALUES ('_tenant_default_', 2, 'detailed', FALSE, FALSE, 'TENANT', 'default', NOW(), NOW())
ON CONFLICT (user_id) DO UPDATE SET
    clearance_level = 2,
    audit_mode = 'detailed',
    scope_type = 'TENANT',
    tenant_id = 'default',
    updated_at = NOW();

-- ============================================================
-- 5. 种子: 机构级安全配置 (org_id='org-finance')
-- ============================================================
INSERT INTO td_user_security_profile (user_id, clearance_level, audit_mode, sandbox_mandatory, is_default, scope_type, org_id, tenant_id, created_at, updated_at)
VALUES ('_org_finance_', 4, 'comprehensive', TRUE, FALSE, 'ORG', 'org-finance', 'default', NOW(), NOW())
ON CONFLICT (user_id) DO UPDATE SET
    clearance_level = 4,
    audit_mode = 'comprehensive',
    sandbox_mandatory = TRUE,
    scope_type = 'ORG',
    org_id = 'org-finance',
    tenant_id = 'default',
    updated_at = NOW();

-- ============================================================
-- 6. ABAC种子策略作用域标注 (V40的3条策略加scope)
-- ============================================================
UPDATE td_abac_policy SET scope_type = 'GLOBAL', scope_id = NULL WHERE policy_id = 'abac-allow-admin-all';
UPDATE td_abac_policy SET scope_type = 'GLOBAL', scope_id = NULL WHERE policy_id = 'abac-region-apac-only';
UPDATE td_abac_policy SET scope_type = 'GLOBAL', scope_id = NULL WHERE policy_id = 'abac-deny-all-default';

-- 新增: 租户default的专用ABAC策略
INSERT INTO td_abac_policy (policy_id, policy_name, resource_condition, action_condition, subject_condition, effect, priority, scope_type, scope_id, created_time)
VALUES ('abac-tenant-default-shared', 'tenant-default-shared-dataset', 'dataset:shared:*', 'read', NULL, 'allow', 50, 'TENANT', 'default', NOW())
ON CONFLICT (policy_id) DO NOTHING;
