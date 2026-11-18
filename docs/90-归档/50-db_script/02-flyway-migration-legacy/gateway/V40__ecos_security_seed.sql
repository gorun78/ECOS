-- ============================================================
-- V40__ecos_security_seed.sql — 安全配置种子数据
-- ============================================================
-- 职责: 全局默认安全配置 / sys_dict 标签更新 / ABAC种子策略 / 审计种子日志
-- 前置: V24__ecos_security_profile.sql (td_user_security_profile, td_role_security_profile)
--        V12__ecos_sys_dict.sql (sys_dict)
-- 全部SQL idempotent，可重复执行

-- ============================================================
-- 1. ABAC 策略表 (td_abac_policy) —— 如未建表则创建
-- ============================================================
CREATE TABLE IF NOT EXISTS td_abac_policy (
    policy_id            VARCHAR(64)     PRIMARY KEY,
    policy_name          VARCHAR(128)    NOT NULL,
    subject_condition    VARCHAR(512),                     -- e.g. 'role:ROLE_SUPER_ADMIN'
    resource_condition   VARCHAR(512)    DEFAULT '*',      -- e.g. 'dataset:*'
    action_condition     VARCHAR(256)    DEFAULT '*',      -- e.g. 'read'
    environment_condition VARCHAR(512),                    -- e.g. 'region == APAC'
    effect               VARCHAR(16)     NOT NULL,         -- 'allow' / 'deny'
    priority             INT             DEFAULT 100,
    created_time         TIMESTAMP       DEFAULT NOW()
);

COMMENT ON TABLE  td_abac_policy                  IS 'ABAC(基于属性的访问控制)策略表';
COMMENT ON COLUMN td_abac_policy.policy_id        IS '策略唯一标识';
COMMENT ON COLUMN td_abac_policy.policy_name      IS '策略名称';
COMMENT ON COLUMN td_abac_policy.subject_condition IS '主体条件 (e.g. role:ROLE_SUPER_ADMIN)';
COMMENT ON COLUMN td_abac_policy.resource_condition IS '资源条件 (e.g. dataset:*)';
COMMENT ON COLUMN td_abac_policy.action_condition IS '动作条件 (e.g. read,write,delete)';
COMMENT ON COLUMN td_abac_policy.environment_condition IS '环境条件 (e.g. region == APAC)';
COMMENT ON COLUMN td_abac_policy.effect           IS '效果: allow(允许) / deny(拒绝)';
COMMENT ON COLUMN td_abac_policy.priority         IS '优先级 (越小越高)';

-- ============================================================
-- 2. 审计日志表 (td_audit_log) —— 如未建表则创建
-- ============================================================
CREATE TABLE IF NOT EXISTS td_audit_log (
    log_id       VARCHAR(64)     PRIMARY KEY,
    event_type   VARCHAR(64)     NOT NULL,                -- LOGIN / ACCESS / CUD / EXPORT / CONFIG
    timestamp    TIMESTAMP       NOT NULL DEFAULT NOW(),
    user_id      VARCHAR(64),
    tenant_id    VARCHAR(64),
    resource     VARCHAR(256),
    action       VARCHAR(128),
    result       VARCHAR(32),                             -- SUCCESS / FAILURE / DENIED
    ip_address   VARCHAR(64),
    user_agent   VARCHAR(512),
    request_id   VARCHAR(64),
    duration     INT,                                     -- 耗时(ms)
    details      TEXT                                     -- JSON格式补充信息
);

COMMENT ON TABLE  td_audit_log               IS '审计日志表';
COMMENT ON COLUMN td_audit_log.log_id        IS '日志唯一标识';
COMMENT ON COLUMN td_audit_log.event_type    IS '事件类型: LOGIN/ACCESS/CUD/EXPORT/CONFIG';
COMMENT ON COLUMN td_audit_log.user_id       IS '操作用户ID';
COMMENT ON COLUMN td_audit_log.tenant_id     IS '租户ID';
COMMENT ON COLUMN td_audit_log.resource      IS '操作资源路径';
COMMENT ON COLUMN td_audit_log.action        IS '操作动作';
COMMENT ON COLUMN td_audit_log.result        IS '操作结果: SUCCESS/FAILURE/DENIED';
COMMENT ON COLUMN td_audit_log.ip_address    IS '客户端IP';
COMMENT ON COLUMN td_audit_log.details       IS '补充信息(JSON格式)';

-- 审计日志索引
CREATE INDEX IF NOT EXISTS idx_audit_timestamp  ON td_audit_log(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_user       ON td_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_event_type ON td_audit_log(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_result     ON td_audit_log(result);

-- ============================================================
-- 3. 全局默认安全配置 (补充V24缺失的 _global_default_)
-- ============================================================
-- 准入等级 L2=保密，审计模式 detailed=详细审计
INSERT INTO td_user_security_profile (user_id, clearance_level, audit_mode, sandbox_mandatory, is_default, created_at, updated_at)
VALUES ('_global_default_', 2, 'detailed', FALSE, TRUE, NOW(), NOW())
ON CONFLICT (user_id) DO UPDATE SET
    clearance_level = 2,
    audit_mode = 'detailed',
    sandbox_mandatory = FALSE,
    is_default = TRUE,
    updated_at = NOW();

-- ============================================================
-- 4. sys_dict 准入等级标签更新 (适配SecurityConfigPanel展示 L1-L5)
--    V12 原有标签: 公开/内部/机密/绝密/最高机密
--    V40 更新为:   L1内部/L2保密/L3机密/L4绝密/L5最高
-- ============================================================
INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order, ext_value) VALUES
('dict-cl-1', 'clearance_level', '1', 'L1内部', 'L1 Internal',      1, '#52c41a'),
('dict-cl-2', 'clearance_level', '2', 'L2保密', 'L2 Confidential',  2, '#1890ff'),
('dict-cl-3', 'clearance_level', '3', 'L3机密', 'L3 Secret',        3, '#faad14'),
('dict-cl-4', 'clearance_level', '4', 'L4绝密', 'L4 Top Secret',    4, '#ff7a45'),
('dict-cl-5', 'clearance_level', '5', 'L5最高', 'L5 Maximum',       5, '#f5222d')
ON CONFLICT (dict_type, dict_code) DO UPDATE SET
    dict_label    = EXCLUDED.dict_label,
    dict_label_en = EXCLUDED.dict_label_en,
    updated_at    = NOW();

-- ============================================================
-- 5. sys_dict 审计模式标签更新
--    V12 原有标签: 基础/详细/全面
--    V40 更新为:   基础审计/详细审计/全面审计
-- ============================================================
INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order) VALUES
('dict-audit-mode-basic',         'audit_mode', 'basic',         '基础审计', 'Basic Audit',          1),
('dict-audit-mode-detailed',      'audit_mode', 'detailed',      '详细审计', 'Detailed Audit',       2),
('dict-audit-mode-comprehensive', 'audit_mode', 'comprehensive', '全面审计', 'Comprehensive Audit',  3)
ON CONFLICT (dict_type, dict_code) DO UPDATE SET
    dict_label    = EXCLUDED.dict_label,
    dict_label_en = EXCLUDED.dict_label_en,
    updated_at    = NOW();

-- ============================================================
-- 6. ABAC 种子策略 (3条)
--    优先级: 越小越高; 策略按 priority 升序评估, 首个匹配生效
-- ============================================================

-- 6.1 超级管理员允许所有操作 (最高优先级)
INSERT INTO td_abac_policy (policy_id, policy_name, resource_condition, action_condition, subject_condition, environment_condition, effect, priority, created_time)
VALUES ('abac-allow-admin-all', 'allow-admin-all', '*', '*', 'role:ROLE_SUPER_ADMIN', NULL, 'allow', 1, NOW())
ON CONFLICT (policy_id) DO UPDATE SET
    policy_name           = EXCLUDED.policy_name,
    resource_condition    = EXCLUDED.resource_condition,
    action_condition      = EXCLUDED.action_condition,
    subject_condition     = EXCLUDED.subject_condition,
    environment_condition = EXCLUDED.environment_condition,
    effect                = EXCLUDED.effect,
    priority              = EXCLUDED.priority;

-- 6.2 APAC区域数据集只读策略
INSERT INTO td_abac_policy (policy_id, policy_name, resource_condition, action_condition, subject_condition, environment_condition, effect, priority, created_time)
VALUES ('abac-region-apac-only', 'region-apac-only', 'dataset:*', 'read', NULL, 'region == APAC', 'allow', 100, NOW())
ON CONFLICT (policy_id) DO UPDATE SET
    policy_name           = EXCLUDED.policy_name,
    resource_condition    = EXCLUDED.resource_condition,
    action_condition      = EXCLUDED.action_condition,
    subject_condition     = EXCLUDED.subject_condition,
    environment_condition = EXCLUDED.environment_condition,
    effect                = EXCLUDED.effect,
    priority              = EXCLUDED.priority;

-- 6.3 默认拒绝所有 (最低优先级, 兜底策略)
INSERT INTO td_abac_policy (policy_id, policy_name, resource_condition, action_condition, subject_condition, environment_condition, effect, priority, created_time)
VALUES ('abac-deny-all-default', 'deny-all-default', '*', '*', NULL, NULL, 'deny', 9999, NOW())
ON CONFLICT (policy_id) DO UPDATE SET
    policy_name           = EXCLUDED.policy_name,
    resource_condition    = EXCLUDED.resource_condition,
    action_condition      = EXCLUDED.action_condition,
    subject_condition     = EXCLUDED.subject_condition,
    environment_condition = EXCLUDED.environment_condition,
    effect                = EXCLUDED.effect,
    priority              = EXCLUDED.priority;

-- ============================================================
-- 7. 审计种子日志 (5条, 用于 SecurityAudit 页面展示)
--    idempotent: ON CONFLICT (log_id) DO NOTHING
-- ============================================================
INSERT INTO td_audit_log (log_id, event_type, timestamp, user_id, tenant_id, resource, action, result, ip_address, details) VALUES
('seed-audit-001', 'LOGIN',   NOW() - INTERVAL '2 hours',   'admin',            'default', 'system/auth',   'login',    'SUCCESS', '192.168.1.100', '{"method":"password","browser":"Chrome 120"}'),
('seed-audit-002', 'CONFIG',  NOW() - INTERVAL '1.5 hours', 'admin',            'default', 'security/policy','update',   'SUCCESS', '192.168.1.100', '{"policy_id":"abac-allow-admin-all","field":"priority","old_value":null,"new_value":1}'),
('seed-audit-003', 'ACCESS',  NOW() - INTERVAL '1 hour',    'zhangsan',         'default', 'dataset/sales',  'read',     'SUCCESS', '10.0.1.55',    '{"dataset_name":"销售数据汇总","rows_returned":1280}'),
('seed-audit-004', 'ACCESS',  NOW() - INTERVAL '30 minutes','lisi',             'default', 'dataset/finance','read',     'DENIED',  '10.0.2.88',    '{"reason":"clearance_level_insufficient","required":4,"user_level":2}'),
('seed-audit-005', 'EXPORT',  NOW() - INTERVAL '10 minutes', 'wangwu',          'default', 'dataset/sales',  'export',   'SUCCESS', '10.0.3.12',    '{"format":"xlsx","rows":5000,"file_size_mb":2.3}')
ON CONFLICT (log_id) DO NOTHING;
