-- V102: 登录安全增强 — users表字段 + SysConfig补配置
-- 引擎: sysman | 库: sys_man

-- 1. users表加安全字段
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_attempts INT DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_change_required BOOLEAN DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_password_change TIMESTAMP DEFAULT NOW();
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_history TEXT DEFAULT '[]';

-- 2. SysConfig安全配置补齐
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, description, status)
VALUES 
  ('cfg-pwd-upper', 'security', 'password_require_upper', 'true', 'BOOLEAN', '密码需含大写字母', '密码必须包含大写字母', 'active'),
  ('cfg-pwd-digit', 'security', 'password_require_digit', 'true', 'BOOLEAN', '密码需含数字', '密码必须包含数字', 'active'),
  ('cfg-pwd-special', 'security', 'password_require_special', 'false', 'BOOLEAN', '密码需含特殊字符', '密码必须包含特殊字符', 'active'),
  ('cfg-audit-retention', 'security', 'audit_retention_days', '180', 'INTEGER', '审计日志保留天数', '审计日志保留天数', 'active')
ON CONFLICT (config_key) DO NOTHING;

-- 3. 确认SysConfig默认值
UPDATE sys_config SET config_value = '8' WHERE config_key = 'password_min_length' AND config_value IS NULL;
UPDATE sys_config SET config_value = '5' WHERE config_key = 'max_login_attempts' AND config_value IS NULL;
UPDATE sys_config SET config_value = '15' WHERE config_key = 'lockout_duration_minutes' AND config_value IS NULL;
UPDATE sys_config SET config_value = '90' WHERE config_key = 'password_expire_days' AND config_value IS NULL;
UPDATE sys_config SET config_value = '3' WHERE config_key = 'password_history_count' AND config_value IS NULL;
UPDATE sys_config SET config_value = '3' WHERE config_key = 'max_concurrent_sessions' AND config_value IS NULL;
