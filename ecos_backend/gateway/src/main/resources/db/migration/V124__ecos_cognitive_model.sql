-- ============================================================
-- V124__ecos_cognitive_model.sql — 认知模型注册表（PMO-51 / ADR-8）
-- ADR-8 口径: 推理"结果"不落盘；模型"资产"(注册/版本/回测)可落盘
-- 表: ecos_cognitive_model (模型注册/版本/回测记录)
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_cognitive_model (
    id              VARCHAR(64) PRIMARY KEY,
    model_id        VARCHAR(64) NOT NULL,      -- 模型标识(如 baseline-linear / causal-v1)
    model_type      VARCHAR(32) NOT NULL,       -- FORECAST / CAUSAL / SIMULATION / DECISION
    version         INTEGER NOT NULL DEFAULT 1,
    features        JSONB,                      -- 特征清单/超参数
    backtest_score  DECIMAL(8,6),               -- 回测评分(可选)
    status          VARCHAR(16) NOT NULL DEFAULT 'active',  -- active / retired
    create_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    create_by       VARCHAR(64) DEFAULT 'system',
    update_by       VARCHAR(64) DEFAULT 'system',
    is_deleted      SMALLINT NOT NULL DEFAULT 0
);

-- 唯一约束: 同一 model_id 同一 version 不重复
CREATE UNIQUE INDEX IF NOT EXISTS uniq_cog_model_id_ver ON ecos_cognitive_model(model_id, version);
CREATE INDEX IF NOT EXISTS idx_cog_model_type  ON ecos_cognitive_model(model_type);
CREATE INDEX IF NOT EXISTS idx_cog_model_status ON ecos_cognitive_model(status);

COMMENT ON TABLE  ecos_cognitive_model IS '认知模型注册表 — ADR-8 模型资产落盘（推理结果不落盘）';
COMMENT ON COLUMN ecos_cognitive_model.model_id    IS '模型标识';
COMMENT ON COLUMN ecos_cognitive_model.model_type  IS '模型类型 FORECAST/CAUSAL/SIMULATION/DECISION';
COMMENT ON COLUMN ecos_cognitive_model.backtest_score IS '回测评分(0~1, 可选)';

-- 种子: 时序预测基线模型
INSERT INTO ecos_cognitive_model (id, model_id, model_type, version, features, status, create_by, update_by)
VALUES
    ('model_fcst_baseline_1', 'baseline-linear', 'FORECAST', 1,
     '{"algorithm":"moving-average + least-squares-linear","horizonMax":60,"confidenceMethod":"residual-std*1.96"}'::jsonb,
     'active','system','system')
ON CONFLICT DO NOTHING;
