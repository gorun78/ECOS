-- V283 (差距回补 / W 域决策行动闭环四表缺建): ecos_ai.ecos_wagent_decision / _option / _action / _action_effect
-- ============================================================
-- 缺陷事实（live 实证，非推断）:
--   详设《从零实施版》§10.4.2 表清单末行明确四表为"待定段，与 V240 同批脚本落地"，
--   但全仓 grep `CREATE TABLE.*ecos_wagent_decision|ecos_wagent_action` = 0 命中 ⇒ 四表**从未有建脚本**；
--   dev PG 实查 information_schema.tables（ILIKE 'ecos_wagent_decision%' / 'ecos_wagent_action%'）= 0 行；
--   legacy 归档（docs/50-db_script/01-ddl-多方言、ecos-sql）同零命中 ⇒ 属"真缺"，按用户裁决
--   （2026-10-14 R9 ③"缺的表查 legacy，没有则自行补上"）以设计附录定义补建。
--
-- 列清单单源 = 详设 §10.4.2「待定段」行逐列 + §10.4.1 通用列模板 + V227 家规类型映射
--   （未标类型的列：name/label→VARCHAR(255)、纯文本 *_text→TEXT、JSON 语义 *_json→TEXT（MC02）、
--    *_id→VARCHAR(36)、owner/decision_maker→VARCHAR(100)、日期→DATE、时间戳→TIMESTAMP）。
--   本表族**当前无代码消费方**（ai-engine wagent 面尚未写这四表 DAO）⇒ 无存量列名冻结，按设计形态建。
--
-- Schema 归属: ecos_ai（ST07 五引擎枚举，§10.4.4 禁建 ecos_agent schema；R-33① 同批口径）。
-- 唯一约束: `UNIQUE(decision_id, baseline_slot)`（§10.4.2 原文）——非 baseline 行 baseline_slot=NULL，
--   唯一索引忽略 NULL ⇒ 多库兼容，替代 partial index（禁项）。
-- 状态: **仅落文件，未对 ecos-postgres 实跑**（实跑需逐项授权，同 V227~V240 口径 §14.4）。
-- 纪律: DR01~DR08 / MC01~MC02 / ST04/ST05(不写 RLS) / ST07 / ST09(无跨 schema FK) / IR02 / IR03(只加不删)
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_decision (
    id                  VARCHAR(36) PRIMARY KEY,               -- MC01 应用侧 UUID，DDL 零默认函数
    tenant_id           VARCHAR(64) NOT NULL,                  -- §10.4.1 通用列
    org_id              VARCHAR(64),
    question_id         VARCHAR(36),                           -- 逻辑引用 ecos_wagent_question（ST09 不建 FK）
    goal_id             VARCHAR(36),                           -- 逻辑引用 ecos_wagent_goal
    readiness_id        VARCHAR(36),                           -- 逻辑引用 ecos_wagent_readiness（定级凭证）
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- 状态值域待 WAgentEnums 单源后经 CHECK 收紧（三段一致纪律，本批不臆造枚举）
    decision_maker_id   VARCHAR(100),                          -- 决策人（ServiceContext.userId，禁自填）
    rationale_text      TEXT,                                  -- 决策理由（纯文本 *_text 形态）
    decision_time       TIMESTAMP,
    effective_time      TIMESTAMP,
    review_plan_time    TIMESTAMP,                             -- 计划复盘时点（行动五阶段「复盘」锚）
    domain              VARCHAR(50) NOT NULL DEFAULT 'default', -- DR08
    version_no          VARCHAR(20) NOT NULL,                  -- DR07
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0,           -- DR05/DR06
    trace_id            VARCHAR(64)                            -- §10.4.1 通用列（V242~V245 同批形态）
);
COMMENT ON TABLE ecos_ai.ecos_wagent_decision IS
    'W 域决策对象（行动五阶段：决策）；详设 §10.4.2 待定段补建，列清单单源 = 设计文（无存量代码冻结列名）';

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_decision_option (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL,
    org_id              VARCHAR(64),
    decision_id         VARCHAR(36) NOT NULL,                  -- 逻辑引用 ecos_wagent_decision.id
    goal_id             VARCHAR(36),
    label               VARCHAR(255),                          -- 方案显示名
    lever_type          VARCHAR(16),                           -- 杠杆类型（值域待枚举单源）
    lever_target_id     VARCHAR(36),                           -- 作用对象（逻辑引用）
    delta_text          TEXT,                                  -- 差异摘要（叙事）
    feasibility         VARCHAR(16),                           -- 可行性（值域待枚举单源）
    profit_delta        NUMERIC(18,2),                         -- 金额列唯一形态 NUMERIC(p,s)（MC02/类型白名单）
    revenue_delta       NUMERIC(18,2),
    cost_delta          NUMERIC(18,2),
    cash_impact         NUMERIC(18,2),
    risk_delta          NUMERIC(9,6),                          -- 比率
    confidence          NUMERIC(5,4),                          -- 概率
    headcount_impact    NUMERIC(9,2),                          -- 人数影响
    baseline_slot       SMALLINT,                              -- 基准槽位；NULL=非 baseline 行（唯一索引忽略 NULL，多库兼容）
    domain              VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20) NOT NULL,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0,
    trace_id            VARCHAR(64)
);
COMMENT ON TABLE ecos_ai.ecos_wagent_decision_option IS
    'W 域备选方案对比（Option：feasibility/profit_delta/risk_delta/baseline_slot，详设 §10.4.2/§10.3 七问「备选方案与比较」）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_wagent_option_decision_baseline
    ON ecos_ai.ecos_wagent_decision_option (decision_id, baseline_slot);  -- §10.4.2 唯一约束原文，替代 partial index

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_action (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL,
    org_id              VARCHAR(64),
    option_id           VARCHAR(36) NOT NULL,                  -- 逻辑引用 ecos_wagent_decision_option.id
    title               VARCHAR(255),
    owner_id            VARCHAR(100),
    role                VARCHAR(64),                           -- 承担角色
    due_date            DATE,
    kpi_text            TEXT,                                  -- KPI 描述（纯文本）
    source_type         VARCHAR(12),                           -- 来源类型（值域待枚举单源）
    source_object_id    VARCHAR(36),                           -- 来源对象（逻辑引用）
    publish_target      VARCHAR(24),                           -- 发布目标（§10.2.8 字典组 wagent.action_publish_target.*）
    publish_mode        VARCHAR(12),                           -- 发布方式
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- 值域待枚举单源后 CHECK
    approval_token      VARCHAR(64),                           -- 审批凭证（L3 human_gate 提交前必填由应用层断言）
    domain              VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20) NOT NULL,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0,
    trace_id            VARCHAR(64)
);
COMMENT ON TABLE ecos_ai.ecos_wagent_action IS
    'W 域行动提交与发布（Action：commitAction 经 CommitService 五步，publish_target 回执，详设 §10.3/§10.4.2）';
CREATE INDEX IF NOT EXISTS idx_wagent_action_tenant_status ON ecos_ai.ecos_wagent_action (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_wagent_action_option        ON ecos_ai.ecos_wagent_action (option_id);

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_action_effect (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL,
    org_id              VARCHAR(64),
    action_id           VARCHAR(36) NOT NULL,                  -- 逻辑引用 ecos_wagent_action.id
    due_date            DATE,                                  -- 效果核定期
    planned_json        TEXT,                                  -- 计划值快照（JSON 语义一律 TEXT，MC02；不进 WHERE）
    actual_json         TEXT,                                  -- 实际值回灌（data 层 APPLICATION 事实的引用摘要）
    deviation_reason    VARCHAR(32),                           -- 偏离原因分类（值域待枚举单源；§12 行动复盘触发面）
    backtest_ref_text   TEXT,                                  -- 回测对照指针（文本，不落 JSONB）
    domain              VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20) NOT NULL,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0,
    trace_id            VARCHAR(64)
);
COMMENT ON TABLE ecos_ai.ecos_wagent_action_effect IS
    'W 域行动效果（Effect：planned/actual/deviation_reason + data 层回灌事实对照，详设 §10.4.2/§12.4 幕 6~7）';
CREATE INDEX IF NOT EXISTS idx_wagent_effect_action ON ecos_ai.ecos_wagent_action_effect (action_id);

-- ── 偏差登记 ──────────────────────────────────────────────
-- D1 表名命名：设计 §10.3 L6134/L6136 行文用 `ecos_wagent_option` / `ecos_wagent_candidate` 简写，
--   §10.4.2 待定段正式清单用全名 `ecos_wagent_decision_option` / `_action` / `_action_effect`——
--   本脚本以**正式清单全名**为准（与 decision 主表构成决策-方案父子链，简写会在台账里造成同名歧义）。
--   台账登记见 docs/30-设计/ECOS实体物理表名映射台账-2026-10-14.md §C。
-- D2 status/lever_type/feasibility/source_type/publish_mode/deviation_reason 未建 CHECK：
--   三段一致纪律（PRD 冻结枚举 → 代码 enum → DDL CHECK）要求枚举单源存在后才收紧；
--   WAgentEnums 当前无这六组值域 ⇒ 本批不臆造，待 wagent 决策面代码批次（差距文档 B3）落地时同批补 CHECK。
-- D3 version_no NOT NULL 无默认：同 V227~V240 家规（应用侧写入方生成），不另立默认值造成双真相。
-- ──────────────────────────────────────────────────────────

-- ── 回滚说明 ──────────────────────────────────────────────
-- 幂等：全部 CREATE ... IF NOT EXISTS + COMMENT，重复执行零变更零报错。
-- 回滚 = 应用侧不启用四表（当前无消费方，零影响面）；删表只允许人工裁决（IR03），脚本不提供 DROP。
-- 实跑影响面：新增 4 表 + 4 索引，均 0 行；不触碰任何存量表/列/策略；无 RLS 语句。
--
-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] ecos_ai. 限定 ✓  [DR02] ecos_ 前缀 ✓  [DR03] 表单数 ✓  [DR04] planned_json/actual_json 带后缀 ✓
-- [DR05] is_deleted SMALLINT ✓  [DR06] create_time/update_time ✓  [DR07] version_no VARCHAR(20) ✓  [DR08] domain ✓
-- [MC01] VARCHAR(36) PK 无 gen_random_uuid ✓  [MC02] JSON 一律 *_json TEXT，无 JSONB ✓；金额 NUMERIC(18,2)/比率(9,6)/概率(5,4)/人数(9,2) 全在白名单 ✓
-- [MC03] 无 PG 专有语法（partial index/分区均避用）✓  [ST04/05] 不写 RLS ✓  [ST06] 无审计表 ✓
-- [ST07] ecos_ai 五引擎枚举内 ✓  [ST09] 无跨 schema FK（全逻辑引用）✓  [IR02] 只落文件，实跑待授权 ✓  [IR03] 只加不删 ✓
