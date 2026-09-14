-- ============================================================
-- V40__sys_config_enhance.sql — 系统配置增强
-- 新增字段: config_options (JSONB), impact_scope, edition
-- ============================================================

ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS config_options JSONB;
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS impact_scope VARCHAR(255);
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS edition VARCHAR(32) DEFAULT 'all';

-- ============================================================
-- 标记企业版专属参数
-- ============================================================
UPDATE sys_config SET edition = 'enterprise' WHERE config_key LIKE 'neo4j_%' OR config_key LIKE 'minio_%' OR config_key LIKE 'opa_%' OR config_key LIKE 'doris_%';

-- ============================================================
-- 填充默认选项
-- ============================================================
UPDATE sys_config SET config_options = '["zh_CN","en_US"]'::jsonb WHERE config_key = 'default_language';
UPDATE sys_config SET config_options = '["dark","light"]'::jsonb WHERE config_key = 'default_theme';
UPDATE sys_config SET config_options = '["true","false"]'::jsonb WHERE config_key = 'login_require_captcha';
UPDATE sys_config SET config_options = '["true","false"]'::jsonb WHERE config_key = 'glossary_review_required';
UPDATE sys_config SET config_options = '["true","false"]'::jsonb WHERE config_key = 'alert_email_enabled';
UPDATE sys_config SET config_options = '["deepseek/deepseek-chat","openai/gpt-4o","anthropic/claude-sonnet"]'::jsonb WHERE config_key = 'agent_default_model';

-- ============================================================
-- 填充影响范围
-- ============================================================
UPDATE sys_config SET impact_scope = '安全/会话管理'      WHERE config_key = 'session_timeout_minutes';
UPDATE sys_config SET impact_scope = '安全/密码策略'        WHERE config_key = 'password_min_length';
UPDATE sys_config SET impact_scope = '安全/密码策略'        WHERE config_key = 'password_expire_days';
UPDATE sys_config SET impact_scope = '安全/密码策略'        WHERE config_key = 'password_history_count';
UPDATE sys_config SET impact_scope = '安全/登录安全'        WHERE config_key = 'max_login_attempts';
UPDATE sys_config SET impact_scope = '安全/登录安全'        WHERE config_key = 'lockout_duration_minutes';
UPDATE sys_config SET impact_scope = '安全/会话管理'        WHERE config_key = 'max_concurrent_sessions';
UPDATE sys_config SET impact_scope = '用户体验/界面'        WHERE config_key = 'default_language';
UPDATE sys_config SET impact_scope = '用户体验/界面'        WHERE config_key = 'default_theme';
UPDATE sys_config SET impact_scope = '安全/登录安全'        WHERE config_key = 'login_require_captcha';
UPDATE sys_config SET impact_scope = '性能/分页'            WHERE config_key = 'default_page_size';
UPDATE sys_config SET impact_scope = '性能/数据库连接池'    WHERE config_key = 'db_connection_timeout';
UPDATE sys_config SET impact_scope = '性能/数据库连接池'    WHERE config_key = 'db_idle_timeout';
UPDATE sys_config SET impact_scope = '业务/市场'            WHERE config_key = 'marketplace_hot_threshold';
UPDATE sys_config SET impact_scope = '质量/数据质量'        WHERE config_key = 'dq_alert_threshold';
UPDATE sys_config SET impact_scope = '性能/数据库连接池'    WHERE config_key = 'data_source_max_connections';
UPDATE sys_config SET impact_scope = '性能/缓存'            WHERE config_key = 'cache_ttl_seconds';
UPDATE sys_config SET impact_scope = '业务/本体建模'        WHERE config_key = 'ontology_max_entities';
UPDATE sys_config SET impact_scope = '业务/知识图谱'        WHERE config_key = 'kg_max_nodes';
UPDATE sys_config SET impact_scope = '业务/术语管理'        WHERE config_key = 'glossary_review_required';
UPDATE sys_config SET impact_scope = '业务/因果推理'        WHERE config_key = 'causal_link_max_depth';
UPDATE sys_config SET impact_scope = '业务/场景分析'        WHERE config_key = 'scenario_max_comparisons';
UPDATE sys_config SET impact_scope = '运维/监控'            WHERE config_key = 'monitoring_refresh_interval';
UPDATE sys_config SET impact_scope = '运维/工作流'          WHERE config_key = 'workflow_max_steps';
UPDATE sys_config SET impact_scope = '运维/审计'            WHERE config_key = 'audit_log_retention_days';
UPDATE sys_config SET impact_scope = '运维/告警'            WHERE config_key = 'alert_email_enabled';
UPDATE sys_config SET impact_scope = '运维/项目管理'        WHERE config_key = 'project_auto_archive_days';
UPDATE sys_config SET impact_scope = 'AI/Agent配置'         WHERE config_key = 'agent_default_timeout';
UPDATE sys_config SET impact_scope = 'AI/Agent配置'         WHERE config_key = 'agent_max_tokens';
UPDATE sys_config SET impact_scope = 'AI/Agent配置'         WHERE config_key = 'agent_default_model';
UPDATE sys_config SET impact_scope = 'AI/Agent配置'         WHERE config_key = 'agent_max_concurrent_calls';
