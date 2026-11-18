-- V267 (补充设计-认知域与智能域 v1.0 §9): W→D 结果评估 ecos_cognitive.ecos_cognitive_outcome_evaluation
-- 追溯: 八项建议 #7「W→D 验证学习回流」；§9.2 流程 / §9.3 输出契约（POST /api/v1/cognitive/outcomes/evaluate）
-- 归属: 控制域认知引擎 schema（ST07；与 V191/V261/V262 同族 ecos_cognitive）
-- 落库红线:
--   * 只有评估记录本身落库；metrics[] 内 predicted/actual/误差数值由**确定性业务计算服务**产生
--     （§9.3 末段铁律：认知引擎只组织引用、诊断与候选建议；LLM 零参与数值）；
--   * update_candidates_text 只存候选**引用**与状态，禁直接改写生产资产（§9.4 防错误学习）；
--   * 单次偏差不得自动升格生产 Belief/Rule/Model（§8.4 状态迁移要求；SUP-OUT-01 验收）。
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST06 / ST07 / ST09 / IR02 / IR03(只加不删)
-- 类型映射: blueprint_id/version、run、actual、window、metric/diagnoses/candidates/warnings → TEXT/JSON-TEXT；
--           NUMERIC 只用于概率/置信类比列（本表无金额列，金额只以 calculationRef 引用出现）

CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_outcome_evaluation (
    id                     VARCHAR(36) PRIMARY KEY,          -- MC01 应用侧 UUID
    tenant_id              VARCHAR(64) NOT NULL,
    org_id                 VARCHAR(64),
    scenario_id            VARCHAR(36) NOT NULL,             -- 逻辑引用 workspace 场景（ST09 禁跨 schema FK）
    blueprint_id           VARCHAR(100),                     -- 蓝图引用（可空：非蓝图运行也可评估）
    blueprint_version      VARCHAR(20),
    run_id                 VARCHAR(36),
    forecast_run_id        VARCHAR(36),                      -- 预测运行快照引用（§9.2 步骤 1 输入快照引用）
    actual_asset_id        VARCHAR(64),                      -- 实际值权威数据资产引用
    actual_version         VARCHAR(20),
    caliber_id             VARCHAR(64),                      -- 对齐口径（§9.2 步骤 2；PUBLISHED 口径绑定）
    window_from_date       DATE,                             -- 评估窗口起（DATE 无时区，§M-列）
    window_to_date         DATE,                             -- 评估窗口止
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- PENDING/EVALUATED/NEEDS_RECONCILE（口径未对齐→对账）/REJECTED
    metric_results_text    TEXT,                             -- JSON 数组：[{metricRef,predicted,actual,absoluteError,relativeError,calculationRef}]（MC02 JSON 全 TEXT；数值只经计算引用复算）
    diagnoses_text         TEXT,                             -- JSON 数组：认知诊断（引用当时 Hypothesis/Belief/CM 版本）
    update_candidates_text TEXT,                             -- JSON 数组：仅候选引用（candidateType/candidateRef/状态）；禁.writeValue 生产资产
    warnings_text          TEXT,                             -- JSON 数组：样本不适用原因、门禁失败原因等（§9.4）
    calculation_refs_text  TEXT,                             -- JSON 数组：确定性计算产物引用（CalculationResultRef 全量，REQ-COG-09 §4.1）
    trace_id               VARCHAR(64),
    domain                 VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no             VARCHAR(20) NOT NULL,
    create_time            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by              VARCHAR(100),
    update_by              VARCHAR(100),
    is_deleted             SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_cognitive.ecos_cognitive_outcome_evaluation IS
    'W→D 结果评估（补充设计 v1.0 §9）：评估记录落库，数值只存计算引用可复算；update_candidates 只存候选引用，单次偏差禁自动改写生产资产（SUP-OUT-01/02）';
CREATE INDEX IF NOT EXISTS idx_cv267_outcome_scenario ON ecos_cognitive.ecos_cognitive_outcome_evaluation (scenario_id, status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_cv267_outcome_run      ON ecos_cognitive.ecos_cognitive_outcome_evaluation (run_id, is_deleted);

-- ── 回滚说明 ──────────────────────────────────────────────
-- 数据回滚：仅新增表。回滚 = 应用侧停用 /outcomes/evaluate 端点（只增不改的既有端点不受影响）；
--           如需删表，只允许人工裁决（IR03 禁脚本内 DROP）。
-- 幂等性：CREATE TABLE/INDEX IF NOT EXISTS ⇒ 重复执行零报错。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] schema 限定（ecos_cognitive.）+ 小写下划线 ✓ [DR02/DR03] 新表 ✓
-- [DR04] JSON 全 TEXT ✓ [DR05] 无布尔列（unused）✓ [DR06~DR08] 七基线列齐 ✓
-- [MC01] UUID 主键，无序列 ✓ [MC02] JSON 全 TEXT；金额不落地（只引用）✓
-- [MC03] 无裸 cast / 无条件索引 / 无 RLS / 无时区戳列（window_*_date = DATE）✓
-- [ST07] 控制域认知引擎 schema ✓ [ST09] 逻辑引用无跨 schema FK ✓ [IR02] 仅文件 ✓ [IR03] 只加 ✓
