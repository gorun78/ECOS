-- V197 (卷05 附录 E.3 / R-17 ① 定版): 确定性计算产物合规形态 ecos_dw.ecos_application_forecast_run 新建（forecast/scenario 运行与结果快照，业务域 APPLICATION 层）
-- 追溯: W125/C107（16 个二进制 JSON 列违 MC02，含 public.ecos_scenario_run 的 4 个结果列产物）、W126/C108（产物落点与 ADR-14 冲突）；需求依据 REQ-COG-03（反事实金额来源）、REQ-COG-09、PRD-09 FC-02/FC-03（运行快照读端点契约随 R-17 ① 同步改）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-17 ①（确定性计算产物经 data-engine 写通道落业务域 ecos_dw，ADR-14 保持；**不落 ecos_cognitive、不落 public**）与 R-8 ②（同源口径：认知数值事实属业务域）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(旧表只停写)
--
-- ── 旧表处置（R-17 ① 批准口径）───────────────────────────────
-- 旧表 public.ecos_scenario_run（V125）以 4 个二进制 JSON 结果列存 forecast/simulation 等产物，
-- 违 MC02 且落点违 ADR-14。本批**并建合规形态**（本表，4 结果列一律 _json 后缀 + TEXT，DR04），
-- 旧表旧列**只停写不 DROP、不改名**（IR03；knownLegacy）。既有行经下方幂等复制段搬运。
-- 写通道纪律（应用层，非 DDL 可表达）：新表写入只经**预测运行服务 → data-engine 写通道**，
-- cognitive 引擎不得直写 ecos_dw/业务域表（铁律 §0.6 / ADR-14 / F05-10 要点5，X-31 类跨域写必消除）。
--
-- ── 列形态依据 ───────────────────────────────────────────────
-- 旧列 1:1 合规化映射（V125 → 本表）：id→(新 UUID 主键+forecast_run_id 业务键)、run_type、status、
-- metric、deviation（比率→NUMERIC(9,6)，ST03-A）、diagnosis/forecast/simulation/strategy 四结果列→*_json TEXT、
-- decision_id、degraded(boolean)→is_degraded(SMALLINT，DR05)。
-- F05-09/F05-10 契约要素补列：caliber_id/caliber_version/as_of_time/project_id/department_id
-- （forecastContext 六要素，诊断链 contribution 确定性复算的快照定位键）、baseline_run_id、
-- is_exploratory（探索式通道隔离标记，"非核算口径"）、overrides_json（情景覆盖结构冻结
-- projectId/departmentId/periods[]/target/stage/delta，TEXT 存序列化，保证"重跑一致"门禁可复算）。

-- ── 1. 新表 ecos_dw.ecos_application_forecast_run ─────────────────
CREATE TABLE IF NOT EXISTS ecos_dw.ecos_application_forecast_run (
    id                     VARCHAR(36)   NOT NULL,                -- MC01: 应用侧生成 UUID，DDL 无默认值
    forecast_run_id        VARCHAR(64)   NOT NULL,                -- 运行业务键（= 契约 forecastRunId，外部键形态）
    scenario_id            VARCHAR(64)   NOT NULL,                -- 场景业务键（旧表既有宽度保留）
    run_type               VARCHAR(20)   NOT NULL,                -- DIAGNOSE/FORECAST/SIMULATE/STRATEGY/FULL（*_type→VARCHAR(20)）
    status                 VARCHAR(20)   NOT NULL DEFAULT 'RUNNING', -- RUNNING/SUCCEEDED/FAILED（枚举 CHECK 本批不建，R-15 同纪律）
    metric                 VARCHAR(255),                          -- 关注指标名（旧表同义列）
    deviation              NUMERIC(9,6),                          -- 偏差百分比（ST03-A 比率形态；派生统计量非个人数据）
    diagnosis_result_json  TEXT,                                  -- DR04: 因果诊断结果序列化 TEXT（旧二进制 JSON 列合规化）
    forecast_result_json   TEXT,                                  -- DR04: 时序预测结果序列化 TEXT
    simulation_result_json TEXT,                                  -- DR04: 情境模拟结果序列化 TEXT
    strategy_result_json   TEXT,                                  -- DR04: 策略推荐结果序列化 TEXT
    decision_id            VARCHAR(64),                           -- 关联决策业务键（旧表同义列）
    is_degraded            SMALLINT      NOT NULL DEFAULT 0,      -- DR05: 原 degraded 布尔→SMALLINT
    caliber_id             VARCHAR(64),                           -- 口径标识（forecastContext 六要素）
    caliber_version        VARCHAR(20),                           -- 口径版本（*_version→VARCHAR(20)）
    as_of_time             TIMESTAMP,                             -- 快照截至时点（*_time→TIMESTAMP，无时区戳列）
    project_id             VARCHAR(64),                           -- 项目维度（overrides 命中范围）
    department_id          VARCHAR(64),                           -- 部门维度（overrides 命中范围）
    baseline_run_id        VARCHAR(64),                           -- 反事实重算基准运行键（baselineRunId + overrides → 新运行）
    is_exploratory         SMALLINT      NOT NULL DEFAULT 0,      -- DR05: 探索式通道（RAG+Agent 推演）产物隔离标记，1=非核算口径
    overrides_json         TEXT,                                  -- DR04: 情景覆盖结构序列化 TEXT（结构冻结见 F05-10 要点1）
    -- DR06~DR08 七基线列
    domain                 VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no             VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted             SMALLINT      NOT NULL DEFAULT 0,
    create_by              VARCHAR(64)   NOT NULL DEFAULT 'system',
    update_by              VARCHAR(64),
    create_time            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time            TIMESTAMP,
    CONSTRAINT pk_ecos_application_forecast_run PRIMARY KEY (id)
);

-- ── 2. 索引（普通索引，禁条件索引 MC03；is_deleted 并入键代偿）──
CREATE UNIQUE INDEX IF NOT EXISTS uniq_afcr_run_id
    ON ecos_dw.ecos_application_forecast_run (forecast_run_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_afcr_scenario ON ecos_dw.ecos_application_forecast_run (scenario_id);
CREATE INDEX IF NOT EXISTS idx_afcr_status   ON ecos_dw.ecos_application_forecast_run (status);
CREATE INDEX IF NOT EXISTS idx_afcr_create   ON ecos_dw.ecos_application_forecast_run (create_time);

-- ── 3. 注释登记 ──────────────────────────────────────────────
COMMENT ON TABLE  ecos_dw.ecos_application_forecast_run IS '确定性计算产物·运行与结果快照表（R-17 ①：业务域 APPLICATION 层落 ecos_dw，经 data-engine 写通道；旧 public.ecos_scenario_run 停写保留；cognitive 只读 forecastRunId 引用，不直写本表）';
COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.deviation IS '偏差百分比（ST03-A 比率形态 NUMERIC(9,6)；派生统计量，非个人数据）';
COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.baseline_run_id IS '反事实/情景重算基准运行键（F05-10：baselineRunId+overrides 重算得新 forecast_run_id）';
COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.is_exploratory IS '1=探索式通道产物（ScenarioSimulatorServiceImpl 强制 exploratory=true），响应/UI 不得作金额来源';

-- ── 4. 存量搬运（旧表 → 新表，幂等按 forecast_run_id 去重；cast 一律 CAST(x AS TEXT) 形态）──
INSERT INTO ecos_dw.ecos_application_forecast_run
    (id, forecast_run_id, scenario_id, run_type, status, metric, deviation,
     diagnosis_result_json, forecast_result_json, simulation_result_json, strategy_result_json,
     decision_id, is_degraded, create_time, update_time, create_by, update_by,
     is_deleted, domain, version_no)
SELECT left('afcr_' || o.id, 36),
       o.id, o.scenario_id, o.run_type,
       COALESCE(o.status, 'RUNNING'), o.metric, o.deviation,
       CAST(o.diagnosis_result AS TEXT), CAST(o.forecast_result AS TEXT),
       CAST(o.simulation_result AS TEXT), CAST(o.strategy_result AS TEXT),
       o.decision_id, CASE WHEN o.degraded THEN 1 ELSE 0 END,
       COALESCE(o.create_time, CURRENT_TIMESTAMP), COALESCE(o.update_time, o.create_time, CURRENT_TIMESTAMP),
       COALESCE(o.create_by, 'system'), o.update_by,
       COALESCE(o.is_deleted, 0), 'default', '1'
FROM public.ecos_scenario_run o
WHERE NOT EXISTS (
    SELECT 1 FROM ecos_dw.ecos_application_forecast_run n
    WHERE n.forecast_run_id = o.id
);
-- 注: 复制段前置条件 = public.ecos_scenario_run 存在（文档实测该表有行；若执行环境缺表，
-- 属迁移漂移面，按 IR02 人工核对后处理，禁在脚本内 DROP/CREATE 兜底改旧表）。

-- ── 5. 回滚说明 ──────────────────────────────────────────────
-- 数据回滚: 新表为并建形态，旧表 public.ecos_scenario_run 全程未改未删，读路径回退旧表即等效回滚；
--           新表已复制行如需清理，只允许置 is_deleted=1（IR03 禁 DELETE/DROP 由人工执行面另行裁决）。
-- 幂等性: CREATE TABLE/INDEX IF NOT EXISTS + INSERT NOT EXISTS 守卫，重复执行零重复行、零报错。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] schema 限定（ecos_dw.）+ 小写下划线 ✓  [DR02] 新表定名 ecos_application_forecast_run 带 ecos_ 前缀 ✓（R-17 ① 批准的是"落 ecos_dw + 经 data-engine 写通道"这一归属口径，未豁免命名红线；原稿以 `doc`/`doc_chunk` 违 DR02 的存量表为"先例"属错误援引，已收口）
-- [DR03] 单数 ✓  [DR04] 5 个 JSON 语义列全部 _json 后缀 + TEXT ✓  [DR05] is_degraded/is_exploratory/is_deleted SMALLINT ✓
-- [DR06] 审计五列 ✓  [DR07] version_no VARCHAR(20) NOT NULL ✓  [DR08] domain VARCHAR(50) NOT NULL DEFAULT 'default' ✓
-- [MC01] 主键 VARCHAR(36) DDL 无默认值，无自增序列 ✓  [MC02] 无二进制 JSON 列；数值列均带精度（NUMERIC(9,6)）✓
-- [MC03] 无裸 cast（搬运段用 CAST(x AS TEXT)）/无条件索引/无 RLS/无分区/无数组列/无时区戳列 ✓
-- [ST03-A] deviation=NUMERIC(9,6)（比率，派生统计量非个人数据，待逐列登记汇报）；**无金额列**（contribution 只出现在响应与审计事件不落库，E.6 口径）✓
-- [ST07] 落 ecos_dw（业务域白名单），不落 ecos_cognitive/public ✓  [R-17 ①] data-engine 写通道 + 旧表停写 ✓  [R-8 ②] 数值事实属业务域 ✓
-- [IR02] 手动 psql 执行 ✓  [IR03] 旧表旧列只停写，不 DROP 不改名 ✓
-- [待补] status/run_type 枚举 CHECK 本批不建（R-15 同纪律）；09 册 FC-02/FC-03 快照读端点契约同步属应用侧待办
