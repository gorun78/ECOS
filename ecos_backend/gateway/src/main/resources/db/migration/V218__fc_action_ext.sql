-- V218 (卷09 §6.1 / 报告 R-30): 经营动作领域扩展 public.ecos_fc_action_ext——1:1 挂决策底座 decision_id（列扩展承载，禁第二套底座）
-- 追溯: W224↔C206（FC-04 五必填只有 2 项成列）、W229↔C211（run 回链缺失）、W239↔C221（金额列 ST03-A 登记无条目）；需求依据 REQ-FC-04
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-30=「①+② 受控组合：Decision 底座唯一在 cognitive，workspace 经 DecisionGateway 适配；FC-04 五必填以列扩展承载，禁第二套底座」、R-64=①、R-65=①；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST06 / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- 号段: 接续卷 07 V213，凭证 = 已批准 R-64 ①
-- 归属: 控制域（决策底座在 cognitive/sysman，**扩展随主** ⇒ 落现 `public`；ADR-12 主控制迁 `ecos_control` 后由 MC06 配置注入改限定名，本脚本文本不改）。
-- 底座引用: decision_id 指向唯一决策底座（cognitive `ecos_decision`，经卷 07 `DecisionGateway`/V211 `_v2` 台账溯源 decision_record_id）；
--   **本表不是第二套决策底座**，只承载 FC-04 领域扩展列（R-30 批准原文）；ST09 不建跨 schema FK，由 DecisionGateway 写侧校验。
-- 金额列 ST03-A: expected_impact 明文存储 + CLS 读取裁决（R-65①）；待登记表条目 ID = TBD-卷09-2026-09-29。

CREATE TABLE IF NOT EXISTS public.ecos_fc_action_ext (
    id                      VARCHAR(36) PRIMARY KEY,          -- MC01: 应用侧 UUID
    decision_id             VARCHAR(36) NOT NULL,             -- 1:1 挂决策底座（R-30：禁另立底座；本列唯一性见下方索引）
    forecast_run_id         VARCHAR(36),                      -- run 回链（C211：决策看板卡回链 forecastRunId）
    factor_ref              VARCHAR(120),                     -- 因子引用（§5.1 #17 预填 factorRef）
    project_id              VARCHAR(36),                      -- 预填项目（§5.1 #17）
    department_id           VARCHAR(36),
    action_type             VARCHAR(20) NOT NULL,             -- 动作模板 4 类（§03 事件 12）
    -- ── FC-04 五必填实列（C206：原 action_plan 草案非 TEXT JSON 列内字段提升为列；清单以 PRD-09 FC-04 为准，若文本口径不符需同步修订，不实跑）──
    action_desc             TEXT NOT NULL,                    -- 必填①：动作内容
    owner_id                VARCHAR(100) NOT NULL,            -- 必填②：负责人
    due_date                DATE NOT NULL,                    -- 必填③：截止日
    expected_impact         NUMERIC(18,2) NOT NULL,           -- 必填④：预期影响额；金额列唯一形态；ST03-A 待登记 TBD-卷09-2026-09-29
    kpi_text                VARCHAR(500) NOT NULL,            -- 必填⑤：控制指标（KPI）
    -- ── 其余扩展 ──
    owner_role              VARCHAR(100),                     -- 负责人角色（审批人回避判定辅助，§3.4）
    status                  VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- 状态机列（创建/审批/完成三事件走 Kafka ecos.audit，ST06，不入列堆积）
    approved_by             VARCHAR(100),
    approved_at             TIMESTAMP,
    effect_json             TEXT,                             -- 效果回灌载荷（PRD-09 草案 effect_json 非 TEXT 形态违 MC02，已按 C200 同族更正为 TEXT；实际效果行经 data-engine ACTUAL 唯一管道，§3.4）
    -- DR06 六列 + DR07/DR08（卷 09 §6.1 每表统一）
    create_time             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by               VARCHAR(100),
    update_by               VARCHAR(100),
    is_deleted              SMALLINT NOT NULL DEFAULT 0,      -- DR05
    version_no              VARCHAR(20) NOT NULL DEFAULT '1', -- DR07
    domain                  VARCHAR(50) NOT NULL DEFAULT 'default' -- DR08
);

COMMENT ON TABLE  public.ecos_fc_action_ext IS '经营动作领域扩展（控制域，扩展随主挂 cognitive 决策底座 decision_id 1:1，R-30 受控组合）；非独立决策底座；ADR-12 迁 ecos_control 后 MC06 注入限定名';

-- 1:1 唯一（红线：禁 partial unique ⇒ 含 is_deleted；逻辑删除的 decision_id 不再复用，由 DecisionGateway 保证）
CREATE UNIQUE INDEX IF NOT EXISTS uniq_fc_action_ext_decision
    ON public.ecos_fc_action_ext(decision_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_fc_action_ext_run     ON public.ecos_fc_action_ext(forecast_run_id);
CREATE INDEX IF NOT EXISTS idx_fc_action_ext_status  ON public.ecos_fc_action_ext(status);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] public. 限定（ADR-12 目标 ecos_control 经 MC06 注入）✓  [DR02] ecos_ 前缀 ✓  [DR04] effect_json _json 后缀 + TEXT ✓
-- [DR05] is_deleted SMALLINT ✓  [DR06/07/08] 审计六列 + version_no + domain ✓
-- [MC01] VARCHAR(36) 应用侧 UUID ✓  [MC02] JSON 全 TEXT；expected_impact NUMERIC(18,2) ✓
-- [ST03-A] expected_impact 逐列注释登记（TBD-卷09-2026-09-29）✓  [ST06] 审批事件走 Kafka ecos.audit，不建审计表 ✓
-- [ST09] decision_id 无跨 schema FK ✓  [IR03] 只加不删 ✓
-- [R-64①] 仅脚本文件落地，未实跑 ✓
