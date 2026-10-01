-- ============================================================
-- V13__ecos_sys_config.sql — 系统配置表
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_config (
    id              VARCHAR(64)   PRIMARY KEY,
    config_group    VARCHAR(64)   NOT NULL,
    config_key      VARCHAR(128)  NOT NULL UNIQUE,
    config_value    TEXT          NOT NULL,
    config_type     VARCHAR(32)   DEFAULT 'string',
    config_label    VARCHAR(255)  NOT NULL,
    config_label_en VARCHAR(255),
    description     TEXT,
    sort_order      INT           DEFAULT 0,
    status          VARCHAR(32)   DEFAULT 'active',
    created_at      TIMESTAMP     DEFAULT NOW(),
    updated_at      TIMESTAMP     DEFAULT NOW()
);

-- ============================================================
-- Seed data: global (sysman) 11 configs
-- ============================================================
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order) VALUES
('cfg-global-page-size',              'global', 'default_page_size',         '20',    'number',  '默认分页大小',       'Default Page Size',        '默认每页数据条数',                 1),
('cfg-global-session-timeout',        'global', 'session_timeout_minutes',   '120',   'number',  '会话超时(分钟)',      'Session Timeout (min)',    '用户会话超时时间',                 2),
('cfg-global-max-login-attempts',     'global', 'max_login_attempts',        '5',     'number',  '最大登录尝试次数',    'Max Login Attempts',       '登录失败锁定阈值',                 3),
('cfg-global-lockout-duration',       'global', 'lockout_duration_minutes',  '30',    'number',  '锁定持续时间(分钟)',  'Lockout Duration (min)',    '账号锁定时长',                    4),
('cfg-global-password-min-length',    'global', 'password_min_length',       '8',     'number',  '密码最小长度',        'Password Min Length',      '密码最小字符数',                   5),
('cfg-global-password-expire-days',   'global', 'password_expire_days',      '90',    'number',  '密码过期天数',        'Password Expire Days',     '密码有效期(天)',                   6),
('cfg-global-password-history-count', 'global', 'password_history_count',    '5',     'number',  '密码历史记录数',      'Password History Count',   '不允许重复的历史密码数量',          7),
('cfg-global-max-concurrent-sessions','global', 'max_concurrent_sessions',   '3',     'number',  '最大并发会话数',      'Max Concurrent Sessions',  '同一用户最大同时在线数',           8),
('cfg-global-default-language',       'global', 'default_language',          'zh_CN', 'string',  '默认语言',            'Default Language',         '系统默认语言',                     9),
('cfg-global-default-theme',          'global', 'default_theme',             'dark',  'string',  '默认主题',            'Default Theme',            '系统默认UI主题',                   10),
('cfg-global-login-require-captcha',  'global', 'login_require_captcha',     'false', 'boolean', '登录需要验证码',      'Login Require Captcha',    '登录是否开启验证码',               11)
ON CONFLICT (config_key) DO NOTHING;

-- ============================================================
-- Seed data: g1-data-foundation 6 configs
-- ============================================================
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order) VALUES
('cfg-g1-db-connection-timeout',    'g1-data-foundation', 'db_connection_timeout',       '30000', 'number',  '数据库连接超时(ms)',   'DB Connection Timeout (ms)',    '数据库连接超时时间',              1),
('cfg-g1-db-idle-timeout',          'g1-data-foundation', 'db_idle_timeout',             '30000', 'number',  '数据库空闲超时(ms)',   'DB Idle Timeout (ms)',          '数据库连接池空闲超时',            2),
('cfg-g1-marketplace-hot-threshold','g1-data-foundation', 'marketplace_hot_threshold',   '50',    'number',  '热门资产阈值',         'Marketplace Hot Threshold',    '数据资产标记为热门的下载次数阈值',  3),
('cfg-g1-dq-alert-threshold',       'g1-data-foundation', 'dq_alert_threshold',          '80',    'number',  '数据质量告警阈值',     'DQ Alert Threshold',           '数据质量分数低于此值触发告警',      4),
('cfg-g1-datasource-max-connections','g1-data-foundation','data_source_max_connections', '10',    'number',  '数据源最大连接数',     'Data Source Max Connections',  '单个数据源最大连接数',             5),
('cfg-g1-cache-ttl',                'g1-data-foundation', 'cache_ttl_seconds',           '300',   'number',  '缓存TTL(秒)',          'Cache TTL (seconds)',          '默认缓存过期时间',                 6)
ON CONFLICT (config_key) DO NOTHING;

-- ============================================================
-- Seed data: g2-biz-semantics 5 configs
-- ============================================================
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order) VALUES
('cfg-g2-ontology-max-entities',    'g2-biz-semantics', 'ontology_max_entities',     '1000',  'number',  '本体最大实体数',       'Ontology Max Entities',        '单个本体支持的最大实体数',         1),
('cfg-g2-kg-max-nodes',             'g2-biz-semantics', 'kg_max_nodes',               '500',   'number',  '知识图谱最大节点数',   'KG Max Nodes',                 '知识图谱最大节点数限制',            2),
('cfg-g2-glossary-review-required', 'g2-biz-semantics', 'glossary_review_required',   'true',  'boolean', '术语需要审核',         'Glossary Review Required',     '术语新增后是否需要审核',            3),
('cfg-g2-causal-link-max-depth',    'g2-biz-semantics', 'causal_link_max_depth',      '5',     'number',  '因果链最大深度',       'Causal Link Max Depth',        '因果推理链最大层级',               4),
('cfg-g2-scenario-max-comparisons', 'g2-biz-semantics', 'scenario_max_comparisons',   '5',     'number',  '场景最大对比数',       'Scenario Max Comparisons',     '场景对比分析最大场景数',            5)
ON CONFLICT (config_key) DO NOTHING;

-- ============================================================
-- Seed data: g3-operations 5 configs
-- ============================================================
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order) VALUES
('cfg-g3-monitoring-refresh-interval', 'g3-operations', 'monitoring_refresh_interval', '30',    'number',  '监控刷新间隔(秒)',     'Monitoring Refresh Interval',  '监控面板自动刷新间隔',             1),
('cfg-g3-workflow-max-steps',          'g3-operations', 'workflow_max_steps',          '20',    'number',  '工作流最大步骤数',     'Workflow Max Steps',           '单个工作流最大步骤数',              2),
('cfg-g3-audit-log-retention',         'g3-operations', 'audit_log_retention_days',    '90',    'number',  '审计日志保留天数',     'Audit Log Retention Days',     '审计日志自动清理前保留天数',        3),
('cfg-g3-alert-email-enabled',         'g3-operations', 'alert_email_enabled',         'false', 'boolean', '邮件告警启用',         'Alert Email Enabled',          '是否启用邮件告警通知',              4),
('cfg-g3-project-auto-archive',        'g3-operations', 'project_auto_archive_days',   '180',   'number',  '项目自动归档天数',     'Project Auto Archive Days',    '项目无活动后自动归档天数',          5)
ON CONFLICT (config_key) DO NOTHING;

-- ============================================================
-- Seed data: g4-agent 4 configs
-- ============================================================
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order) VALUES
('cfg-g4-agent-default-timeout',      'g4-agent', 'agent_default_timeout',       '600',                   'number', 'Agent默认超时(秒)',     'Agent Default Timeout',        'Agent调用默认超时时间',            1),
('cfg-g4-agent-max-tokens',           'g4-agent', 'agent_max_tokens',            '4096',                  'number', 'Agent最大Token数',      'Agent Max Tokens',             'Agent单次调用最大Token数',         2),
('cfg-g4-agent-default-model',        'g4-agent', 'agent_default_model',         'deepseek/deepseek-chat','string', 'Agent默认模型',         'Agent Default Model',          'Agent默认使用的LLM模型',           3),
('cfg-g4-agent-max-concurrent-calls', 'g4-agent', 'agent_max_concurrent_calls',  '5',                     'number', 'Agent最大并发调用数',   'Agent Max Concurrent Calls',   'Agent最大同时调用数',              4)
ON CONFLICT (config_key) DO NOTHING;
