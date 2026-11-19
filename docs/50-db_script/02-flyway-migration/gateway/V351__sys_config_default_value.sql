-- ============================================================================
-- V351 — sys_config 补 default_value 列并回填安全/全局 11 键默认基线
-- ============================================================================
-- 为什么需要本脚本（reset 404 根因修复，差距分析 G-3）：
--   `docs/30-设计/ECOS系统配置现状与差距分析-2026-11-19.md` G-3 登记：
--   `PUT /api/v1/sysconfig/{key}/reset` 因 `sys_config` 表无 `default_value` 列
--   在活体恒 404（gateway `SysConfigController.java:59-79` D23 实测口径：
--   「SELECT config_value, default_value」必然 SQL 报错，如实 404 而非伪造成功）。
--
--   前端规范 §九.3 同时要求「默认值事实源 = 后端 default_value」——
--   列缺位使「恢复默认」在前端只能由各 Controller 内硬编码常量兜底（E-X 差距根因之一）。
--
-- 修法（只加不改，IR03 兼容）：
--   ① `ALTER TABLE ... ADD COLUMN IF NOT EXISTS default_value`（同 V40 加列形态）；
--   ② 仅对 `default_value IS NULL` 的行回填，不覆盖任何既有值；
--      回填值取该键在 V13/V102 seed 中的原始插入值（= 平台出厂基线）。
--
-- 覆盖键（11 个，= 前端 SystemConfigManager 白名单 + KNOWN_CONSUMED 交集）：
--   global 组 7 键（V13 seed 原值）：
--     session_timeout_minutes=120 / max_login_attempts=5 / lockout_duration_minutes=30
--     password_min_length=8 / password_expire_days=90 / password_history_count=5
--     max_concurrent_sessions=3
--   security 组 4 键（V102 seed 原值）：
--     password_require_upper=true / password_require_digit=true
--     password_require_special=false / audit_retention_days=180
--
-- 非目标（不在本脚本范围）：
--   * 其余 config_group（data-engine / ontology-engine / knowledge / cognitive 等）的默认值
--     由各引擎后续把 Controller 内硬编码 DEFAULTS 常量迁移到本列时回填（E-X 整改项）。
--   * 历史归档区 V1~V349 不动（铁律 v2.4 §3.1：归档只读）。
-- ============================================================================

ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS default_value VARCHAR(2000);

-- global 组（V13 seed 原值）
UPDATE sys_config SET default_value = '120' WHERE config_key = 'session_timeout_minutes'   AND default_value IS NULL;
UPDATE sys_config SET default_value = '5'   WHERE config_key = 'max_login_attempts'        AND default_value IS NULL;
UPDATE sys_config SET default_value = '30'  WHERE config_key = 'lockout_duration_minutes'  AND default_value IS NULL;
UPDATE sys_config SET default_value = '8'   WHERE config_key = 'password_min_length'       AND default_value IS NULL;
UPDATE sys_config SET default_value = '90'  WHERE config_key = 'password_expire_days'      AND default_value IS NULL;
UPDATE sys_config SET default_value = '5'   WHERE config_key = 'password_history_count'    AND default_value IS NULL;
UPDATE sys_config SET default_value = '3'   WHERE config_key = 'max_concurrent_sessions'   AND default_value IS NULL;

-- security 组（V102 seed 原值）
UPDATE sys_config SET default_value = 'true'  WHERE config_key = 'password_require_upper'   AND default_value IS NULL;
UPDATE sys_config SET default_value = 'true'  WHERE config_key = 'password_require_digit'   AND default_value IS NULL;
UPDATE sys_config SET default_value = 'false' WHERE config_key = 'password_require_special' AND default_value IS NULL;
UPDATE sys_config SET default_value = '180'   WHERE config_key = 'audit_retention_days'     AND default_value IS NULL;
