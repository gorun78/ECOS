-- ============================================================================
-- V361 — 场景定义/运行锚点/配置底座（场景工作台简化方案 v0.3，2026-11-21）
-- ============================================================================
-- 目标（只加不删）：
--   1) 场景运行锚点：给 ecos_scenario_run 补定义版本/摘要/来源列，统一读面；
--   2) 场景模板：ecos_scenario_template；
--   3) 算法库：ecos_algorithm_registry。
--
-- 纪律：IR03 只加不删；MC01 主键 VARCHAR(36) 应用侧 UUID；MC02 类型白名单；
--       MC03 标准 SQL；ST07 落 ecos_control（workspace 主控制域）；
--       金额列 NUMERIC(p,s)（本批无金额列）；不建 FK（ST09）。
-- ============================================================================

ALTER TABLE ecos_control.ecos_scenario_run
    ADD COLUMN IF NOT EXISTS definition_version VARCHAR(20),
    ADD COLUMN IF NOT EXISTS definition_digest  VARCHAR(120),
    ADD COLUMN IF NOT EXISTS analysis_type      VARCHAR(32),
    ADD COLUMN IF NOT EXISTS source_type        VARCHAR(32),
    ADD COLUMN IF NOT EXISTS source_id          VARCHAR(64);

-- 场景主表持有「当前已发布定义版本」（设计册 §7.3）：运行记录靠这两列回锚到具体版本。
ALTER TABLE ecos_control.ecos_business_scenario
    ADD COLUMN IF NOT EXISTS definition_version VARCHAR(20),
    ADD COLUMN IF NOT EXISTS definition_digest  VARCHAR(120);

COMMENT ON COLUMN ecos_control.ecos_scenario_run.definition_version IS
    '运行引用的场景定义版本（V361；发布时写，历史版本走 Git）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.definition_digest IS
    '运行引用的定义快照摘要（V361；防运行后定义漂移不可复现）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.analysis_type IS
    '分析类型 CAUSAL/FORECAST/SIMULATION（V361；与 run_type 正交，旧列保留）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.source_type IS
    '结果来源 SCENARIO_RUN/FORECAST_RUN（V361；预测锚点行为 FORECAST_RUN）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run.source_id IS
    '来源 run id（V361；预测为 forecastRunId）';

CREATE INDEX IF NOT EXISTS idx_scenario_run_analysis
    ON ecos_control.ecos_scenario_run (scenario_id, analysis_type, create_time DESC);

CREATE TABLE IF NOT EXISTS ecos_control.ecos_scenario_template (
    id                  VARCHAR(36)  NOT NULL,
    name                VARCHAR(128) NOT NULL,
    category            VARCHAR(32)  NOT NULL DEFAULT 'GENERAL',
    description         TEXT,
    definition_defaults TEXT,
    default_analysis    TEXT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    domain              VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted          SMALLINT     NOT NULL DEFAULT 0,
    create_by           VARCHAR(64)  NOT NULL,
    create_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_by           VARCHAR(64),
    update_time         TIMESTAMP,
    CONSTRAINT pk_ecos_scenario_template PRIMARY KEY (id),
    CONSTRAINT ck_ecos_scenario_template_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uniq_scenario_template_name
    ON ecos_control.ecos_scenario_template (name, is_deleted);

CREATE TABLE IF NOT EXISTS ecos_control.ecos_algorithm_registry (
    id                  VARCHAR(36)  NOT NULL,
    name                VARCHAR(128) NOT NULL,
    analysis_type       VARCHAR(32)  NOT NULL,
    version             VARCHAR(20)  NOT NULL DEFAULT '1',
    provider_type       VARCHAR(32)  NOT NULL DEFAULT 'DETERMINISTIC_SPI',
    implementation_ref  VARCHAR(255) NOT NULL,
    params_schema       TEXT,
    output_schema       TEXT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    domain              VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted          SMALLINT     NOT NULL DEFAULT 0,
    create_by           VARCHAR(64)  NOT NULL,
    create_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_by           VARCHAR(64),
    update_time         TIMESTAMP,
    CONSTRAINT pk_ecos_algorithm_registry PRIMARY KEY (id),
    CONSTRAINT ck_ecos_algorithm_type CHECK (analysis_type IN ('CAUSAL', 'FORECAST', 'SIMULATION')),
    CONSTRAINT ck_ecos_algorithm_status CHECK (status IN ('ACTIVE', 'DISABLED', 'DEPRECATED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uniq_algorithm_name_version
    ON ecos_control.ecos_algorithm_registry (name, version, is_deleted);

-- 种子：只登记真实存在的 4 条承载（禁空壳）
INSERT INTO ecos_control.ecos_algorithm_registry
    (id, name, analysis_type, version, provider_type, implementation_ref, status, create_by, create_time)
SELECT 'alg-forecast-deterministic', '确定性预测（六要素）', 'FORECAST', '1', 'DETERMINISTIC_SPI',
       '/cognitive/forecast-runs/compute', 'ACTIVE', 'V361', NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_algorithm_registry WHERE id = 'alg-forecast-deterministic');

INSERT INTO ecos_control.ecos_algorithm_registry
    (id, name, analysis_type, version, provider_type, implementation_ref, status, create_by, create_time)
SELECT 'alg-causal-diagnose', '因果诊断', 'CAUSAL', '1', 'DETERMINISTIC_SPI',
       '/cognitive/diagnose', 'ACTIVE', 'V361', NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_algorithm_registry WHERE id = 'alg-causal-diagnose');

INSERT INTO ecos_control.ecos_algorithm_registry
    (id, name, analysis_type, version, provider_type, implementation_ref, status, create_by, create_time)
SELECT 'alg-simulation-scenario', '情景仿真', 'SIMULATION', '1', 'DETERMINISTIC_SPI',
       '/cognitive/scenario/simulate', 'ACTIVE', 'V361', NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_algorithm_registry WHERE id = 'alg-simulation-scenario');

INSERT INTO ecos_control.ecos_algorithm_registry
    (id, name, analysis_type, version, provider_type, implementation_ref, status, create_by, create_time)
SELECT 'alg-counterfactual', '反事实推演', 'SIMULATION', '1', 'DETERMINISTIC_SPI',
       '/cognitive/counterfactual', 'ACTIVE', 'V361', NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_algorithm_registry WHERE id = 'alg-counterfactual');

INSERT INTO ecos_control.ecos_scenario_template
    (id, name, category, description, definition_defaults, default_analysis, status, create_by, create_time)
SELECT 'tpl-blank', '空白场景', 'GENERAL', '从零开始定义',
       '{"goalStatement":"","successCriteria":[]}', '["CAUSAL"]', 'ACTIVE', 'V361', NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_scenario_template WHERE id = 'tpl-blank');

INSERT INTO ecos_control.ecos_scenario_template
    (id, name, category, description, definition_defaults, default_analysis, status, create_by, create_time)
SELECT 'tpl-forecast', '经营预测', 'FORECAST', '年度经营预测与偏差分析',
       '{"goalStatement":"完成年度经营预测并识别偏差","successCriteria":["预测结果可追溯","偏差可解释"]}', '["FORECAST","CAUSAL"]', 'ACTIVE', 'V361', NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_scenario_template WHERE id = 'tpl-forecast');

INSERT INTO ecos_control.ecos_scenario_template
    (id, name, category, description, definition_defaults, default_analysis, status, create_by, create_time)
SELECT 'tpl-cost', '成本诊断', 'COST', '成本异常定位与改善',
       '{"goalStatement":"定位成本异常并给出改善动作","successCriteria":["异常可定位","动作可跟踪"]}', '["CAUSAL","SIMULATION"]', 'ACTIVE', 'V361', NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_scenario_template WHERE id = 'tpl-cost');
