-- V44__ecos_goal_kpi_config.sql
-- 目标 KPI 配置扩展 + 因果链增强
-- 日期: 2026-06-30

-- ═══ 1. ecos_wm_goal 新增 KPI 字段 ═══
ALTER TABLE ecos_wm_goal ADD COLUMN IF NOT EXISTS kpi_formula        VARCHAR(256) DEFAULT 'currentValue/targetValue*100';
ALTER TABLE ecos_wm_goal ADD COLUMN IF NOT EXISTS measure_frequency  VARCHAR(16)  DEFAULT 'MONTHLY';
ALTER TABLE ecos_wm_goal ADD COLUMN IF NOT EXISTS alert_threshold_warn     DECIMAL(5,2) DEFAULT 80.0;
ALTER TABLE ecos_wm_goal ADD COLUMN IF NOT EXISTS alert_threshold_critical DECIMAL(5,2) DEFAULT 50.0;

-- ═══ 2. ecos_wm_causal_link 新增延迟和相关性字段 ═══
ALTER TABLE ecos_wm_causal_link ADD COLUMN IF NOT EXISTS time_lag_days         INTEGER      DEFAULT 0;
ALTER TABLE ecos_wm_causal_link ADD COLUMN IF NOT EXISTS correlation_coefficient DECIMAL(4,3) DEFAULT 0.0;

-- ═══ 3. 为现有的 FINANCIAL 类目标补充 KPI 配置 ═══
UPDATE ecos_wm_goal SET kpi_formula = 'currentValue/targetValue*100', measure_frequency = 'MONTHLY'
 WHERE kpi_formula IS NULL;
