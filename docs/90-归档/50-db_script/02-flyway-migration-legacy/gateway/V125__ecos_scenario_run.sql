-- ============================================================
-- V125__ecos_scenario_run.sql — 场景运行记录表（PMO-52）
-- 定位: workspace 场景工作台的 "端到端认知运行" 记录
--       一次场景运行 = 4 类认知调用（诊断/预测/模拟/策略）+ 决策回写
--       结论回写 ecos_business_scenario.actual_safety_index 与 metrics
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_scenario_run (
    id                  VARCHAR(64) PRIMARY KEY,
    scenario_id         VARCHAR(64) NOT NULL,
    run_type            VARCHAR(32) NOT NULL,      -- DIAGNOSE / FORECAST / SIMULATE / STRATEGY / FULL
    status              VARCHAR(32) NOT NULL DEFAULT 'RUNNING',  -- RUNNING / SUCCEEDED / FAILED
    metric              VARCHAR(255),             -- 关注的指标
    deviation           DECIMAL(10,4),            -- 偏差百分比
    diagnosis_result    JSONB,                    -- 因果诊断结果
    forecast_result     JSONB,                    -- 时序预测结果
    simulation_result   JSONB,                    -- 情境模拟结果
    strategy_result     JSONB,                    -- 策略推荐结果
    decision_id         VARCHAR(64),              -- 关联 ecos_decision.id
    degraded            BOOLEAN DEFAULT FALSE,    -- 是否存在 KG 降级
    create_time         TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMP NOT NULL DEFAULT NOW(),
    create_by           VARCHAR(64) DEFAULT 'system',
    update_by           VARCHAR(64) DEFAULT 'system',
    is_deleted          SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_scenario_run_sc     ON ecos_scenario_run(scenario_id);
CREATE INDEX IF NOT EXISTS idx_scenario_run_status ON ecos_scenario_run(status);
CREATE INDEX IF NOT EXISTS idx_scenario_run_type   ON ecos_scenario_run(run_type);
CREATE INDEX IF NOT EXISTS idx_scenario_run_time   ON ecos_scenario_run(create_time DESC);

COMMENT ON TABLE  ecos_scenario_run IS '场景运行记录 — PMO-52 场景工作台认知闭环';
COMMENT ON COLUMN ecos_scenario_run.run_type      IS '运行类型 DIAGNOSE/FORECAST/SIMULATE/STRATEGY/FULL';
COMMENT ON COLUMN ecos_scenario_run.degraded      IS '是否存在 KG 降级（任一步走规则兜底则 true）';
