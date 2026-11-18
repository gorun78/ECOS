-- ============================================================================
-- 06_ecos_cognitive.sql — ecos_cognitive 权威 DDL
-- ============================================================================
-- 由 pg_dump --schema-only 从真实库 sys_man 反向导出后清洗装配（2026-11-17，V349 清理后重导）。
-- 目标 schema = ecos_cognitive；保留对象 236 个，剔分区子表块 0 个，外键 9 条移入 09。
--
-- 清洗口径:
--   · 剥离 pg_dump 噪音（\restrict / SET / set_config / CREATE SCHEMA / OWNER）
--   · 分区表只保父表 + DEFAULT 分区，60 张按月日期子表由运行时创建
--   · 冗余 ::character varying cast 去除（MC03 口径）
--   · 外键全部延后到 09_foreign_keys.sql（跨 schema 依赖，禁按文件顺序内联）
--   · public 归位表已由 V345/V346 实库搬入归属 schema，本册直接反映搬后落点
--   · 平行误会表 users 已由 V347 统一登录权威到 td_user、V349 实库清退，本册不含
--
-- 前置: 空库 + 已执行 00_create_schemas.sql。本文件不含 IF NOT EXISTS，
--       结构不符即报错（刻意 fail-loud，禁静默跳过）。
-- ============================================================================

-- ecos_biz_contract (TABLE)
CREATE TABLE ecos_cognitive.ecos_biz_contract (
    id character varying(64) NOT NULL,
    contract_no character varying(128),
    contract_type character varying(32),
    project_id character varying(64),
    party_name character varying(256),
    amount numeric(18,2),
    signed_date date,
    status character varying(32) DEFAULT 'ACTIVE',
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE ecos_biz_contract (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_biz_contract IS '业务合同表';

--

-- ecos_biz_department (TABLE)
CREATE TABLE ecos_cognitive.ecos_biz_department (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    manager character varying(64),
    parent_id character varying(64),
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE ecos_biz_department (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_biz_department IS '业务部门表';

--

-- ecos_biz_metric (TABLE)
CREATE TABLE ecos_cognitive.ecos_biz_metric (
    id bigint NOT NULL,
    dept_id character varying(64),
    metric_type character varying(32),
    metric_value numeric(18,2),
    target_value numeric(18,2),
    metric_month character varying(7),
    goal_id bigint,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE ecos_biz_metric (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_biz_metric IS '业务指标表';

--

-- ecos_biz_metric_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_cognitive.ecos_biz_metric_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_biz_metric_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_cognitive.ecos_biz_metric_id_seq OWNED BY ecos_cognitive.ecos_biz_metric.id;

--

-- ecos_biz_project (TABLE)
CREATE TABLE ecos_cognitive.ecos_biz_project (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    project_type character varying(32),
    dept_id character varying(64),
    customer_name character varying(256),
    contract_amount numeric(18,2),
    status character varying(32) DEFAULT 'ACTIVE',
    start_date date,
    end_date date,
    manager character varying(64),
    goal_id bigint,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE ecos_biz_project (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_biz_project IS '业务项目表';

--

-- ecos_biz_target (TABLE)
CREATE TABLE ecos_cognitive.ecos_biz_target (
    id bigint NOT NULL,
    dept_id character varying(64),
    target_type character varying(32),
    target_value numeric(18,2),
    target_year integer,
    goal_id bigint,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE ecos_biz_target (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_biz_target IS '业务目标表';

--

-- ecos_biz_target_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_cognitive.ecos_biz_target_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_biz_target_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_cognitive.ecos_biz_target_id_seq OWNED BY ecos_cognitive.ecos_biz_target.id;

--

-- ecos_business_scenario (TABLE)
CREATE TABLE ecos_cognitive.ecos_business_scenario (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    business_goal text DEFAULT ''::text,
    department character varying(128),
    priority character varying(16) DEFAULT 'MEDIUM',
    status character varying(32) DEFAULT 'DRAFT',
    budget character varying(64),
    safety_index_target numeric(8,4),
    actual_safety_index numeric(8,4),
    metrics_json text,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);

--

-- TABLE ecos_business_scenario (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_business_scenario IS '业务场景主表（认知域真身 14 张之一，R-13 ① 归位 ecos_cognitive；旧 public.ecos_business_scenario 双写期保留；场景层 v2 权威面另见 V205/卷07）';

--

-- ecos_cognitive_belief (TABLE)
CREATE TABLE ecos_cognitive.ecos_cognitive_belief (
    id character varying(36) NOT NULL,
    variable_name character varying(128) NOT NULL,
    tenant_scope character varying(64),
    domain character varying(64) DEFAULT 'default' NOT NULL,
    distribution_json text DEFAULT '[]'::text NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    snapshot_version integer,
    is_manual_override smallint DEFAULT 0 NOT NULL,
    override_reason text,
    last_evidence_id character varying(64),
    status character varying(16) DEFAULT 'ACTIVE' NOT NULL,
    mind_id character varying(36),
    scenario_id character varying(36),
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone,
    probability numeric(6,5),
    lifecycle_state character varying(30),
    prob_kind character varying(30),
    calibration_ref character varying(100),
    model_ref character varying(100),
    scope_ref character varying(100),
    as_of_time timestamp without time zone,
    subject_type character varying(20),
    subject_id character varying(36)
);

--

-- TABLE ecos_cognitive_belief (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_cognitive_belief IS '不确定性判断表（ADR-9 心智层 P 库，R-13 ① 归位 ecos_cognitive；旧 public.ecos_cognitive_belief 双写期保留，读切换在 Mapper 层）';

--

-- COLUMN ecos_cognitive_belief.distribution_json (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.distribution_json IS '有限离散概率分布序列化 TEXT [{"outcome","prob"}]（MC02 合规形态，源列 V127 系二进制 JSON）';

--

-- COLUMN ecos_cognitive_belief.mind_id (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.mind_id IS 'R4 归属维度：所属 Mind 聚合根 id（详设 §8.4.2；可空，存量 NULL=未挂 Mind；读侧带 mind 参时 AND mind_id=? 严格过滤）';

--

-- COLUMN ecos_cognitive_belief.probability (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.probability IS 'E2 算式唯一数值来源：belief 点概率（0..1，NUMERIC(6,5)，MC02 带精度）；NULL=未标定 ⇒ 应用侧从 Top-N 剔除并计入 missingProbability，禁 DEFAULT 0、禁由 distribution_json 反算冒充标定值（REQ-COG-09 规格 4 / X-44）';

--

-- COLUMN ecos_cognitive_belief.lifecycle_state (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.lifecycle_state IS '<补充设计 v1.0 §8.4>Belief 治理生命周期（同 hypothesis 词表）；level-evidence 型生命周期 EVIDENCE_CHECKED=last_evidence_id 链完整；CONTRADICTED≠删除，保留反证链（§8.5）';

--

-- COLUMN ecos_cognitive_belief.prob_kind (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.prob_kind IS '<补充设计 v1.0 §8.3/§16.5 红线>point probability 语义三分：STATISTICAL_PROBABILITY / CALIBRATED_MODEL_PROBABILITY / UNCALIBRATED_CONFIDENCE / UNCALIBRATED_MODEL_ESTIMATE；NULL+probability 非 NULL=UNKNOWN_LEGACY（应用侧回落，禁伪造补齐）；UNCALIBRATED 两类禁入金额计算/自动审批（SUP-COG-02/SUP-MODEL-03）';

--

-- COLUMN ecos_cognitive_belief.calibration_ref (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.calibration_ref IS '<补充设计 v1.0 §8.3>校准方法/数据集/有效范围的引用声明（CALIBRATED_MODEL_PROBABILITY 必填语义，应用侧校验，DDL 不建 CHECK 约束）';

--

-- COLUMN ecos_cognitive_belief.model_ref (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.model_ref IS '<补充设计 v1.0 §8.3>产生该判断的 CM 版本引用（如 cm-07:v3，CP 前缀式）';

--

-- COLUMN ecos_cognitive_belief.scope_ref (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.scope_ref IS '<补充设计 v1.0 §8.3>适用范围引用（scenario/tenant-scope 键）';

--

-- COLUMN ecos_cognitive_belief.as_of_time (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.as_of_time IS '<补充设计 v1.0 §5.3/§12.2>判断时点（T1S 轨；与 V261 model 的 as_of 同口径）';

--

-- COLUMN ecos_cognitive_belief.subject_type (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.subject_type IS 'R4 归属维度：主体类型字面量（详设 §8.4.2 subject_type VARCHAR(20)；可空，存量 NULL 维持原行为）';

--

-- COLUMN ecos_cognitive_belief.subject_id (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.subject_id IS 'R4 归属维度：主体 id（详设 §8.4.2 subject_id VARCHAR(36)；可空）';

--

-- ecos_cognitive_evidence (TABLE)
CREATE TABLE ecos_cognitive.ecos_cognitive_evidence (
    id character varying(36) NOT NULL,
    evidence_code character varying(128) NOT NULL,
    tenant_scope character varying(64),
    source_type character varying(32) NOT NULL,
    source_ref character varying(256),
    blob_json text DEFAULT '{}'::text NOT NULL,
    confidence numeric(5,4) DEFAULT 0.5 NOT NULL,
    is_conflict smallint DEFAULT 0 NOT NULL,
    refuting_evidence_ids_json text DEFAULT '[]'::text NOT NULL,
    effective_time timestamp without time zone,
    expire_time timestamp without time zone,
    status character varying(16) DEFAULT 'ACTIVE' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);

--

-- TABLE ecos_cognitive_evidence (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_cognitive_evidence IS '认知证据表（ADR-9 感知/心智层；blob/refuting 原二进制 JSON 已合规化为 TEXT）';

--

-- ecos_cognitive_framework (TABLE)
CREATE TABLE ecos_cognitive.ecos_cognitive_framework (
    id character varying(36) NOT NULL,
    framework_id character varying(64) NOT NULL,
    version character varying(32) NOT NULL,
    name character varying(128) NOT NULL,
    category character varying(64) NOT NULL,
    purpose text,
    status character varying(24) DEFAULT 'DRAFT' NOT NULL,
    applicability_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    preconditions_json jsonb DEFAULT '[]'::jsonb NOT NULL,
    input_schema_ref character varying(200),
    output_schema_ref character varying(200),
    steps_json jsonb DEFAULT '[]'::jsonb NOT NULL,
    required_capabilities_json jsonb DEFAULT '[]'::jsonb NOT NULL,
    validation_profile_ref character varying(200),
    budget_policy_ref character varying(200),
    stop_conditions_json jsonb DEFAULT '[]'::jsonb NOT NULL,
    governance_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    contract_resolvable smallint DEFAULT 0 NOT NULL,
    content_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    content_hash character varying(128) NOT NULL,
    tenant_scope character varying(64),
    publish_time timestamp without time zone,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_cognitive_framework (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_cognitive_framework IS '认知框架注册表（详细设计-认知引擎 §5/§9.1）：版本化过程治理元数据；PUBLISHED 版本不可原地修改';

--

-- COLUMN ecos_cognitive_framework.status (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_framework.status IS 'DRAFT / VALIDATING / PUBLISHED / DEPRECATED / DISABLED（单源 CognitiveFrameworkStatus）';

--

-- COLUMN ecos_cognitive_framework.contract_resolvable (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_framework.contract_resolvable IS '输入/输出 Schema 与能力引用是否可解析（0/1；策略选择硬约束输入）';

--

-- COLUMN ecos_cognitive_framework.content_hash (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_framework.content_hash IS '发布冻结的内容摘要（SHA-256），用于版本可追溯与篡改检测';

--

-- ecos_cognitive_hypothesis (TABLE)
CREATE TABLE ecos_cognitive.ecos_cognitive_hypothesis (
    id character varying(36) NOT NULL,
    hypothesis_code character varying(128) NOT NULL,
    tenant_scope character varying(64),
    statement text NOT NULL,
    domain character varying(64) DEFAULT 'default' NOT NULL,
    metric_ref character varying(128),
    evidence_ids_json text DEFAULT '[]'::text NOT NULL,
    is_valid smallint DEFAULT 1 NOT NULL,
    invalid_at timestamp without time zone,
    invalid_reason text,
    status character varying(16) DEFAULT 'PROPOSED' NOT NULL,
    old_status character varying(20),
    mind_id character varying(36),
    scenario_id character varying(36),
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone,
    lifecycle_state character varying(30),
    subject_type character varying(20),
    subject_id character varying(36),
    reason text DEFAULT ''::text NOT NULL,
    confidence numeric(9,6)
);

--

-- TABLE ecos_cognitive_hypothesis (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_cognitive_hypothesis IS '认知假设表（ADR-9 心智层 H 库；status 终态 4 值枚举 R-15 ①，old_status 存映射前留痕）';

--

-- COLUMN ecos_cognitive_hypothesis.evidence_ids_json (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.evidence_ids_json IS 'R4 证据引用数组 TEXT JSON；V128 旧 evidence_ids JSONB 列停跑保留（knownLegacy），本列为新权威读写面';

--

-- COLUMN ecos_cognitive_hypothesis.status (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.status IS '四终态/proposed/evidenced/believed/refuted（R-15 ①；V191 建表时默认 VALID 系双轨期历史签发，
     V299 归一 PROPOSED=应用语义起点；存量 4 行全在四终态无 VALID 行，DEFAULT 改写零影响存量）';

--

-- COLUMN ecos_cognitive_hypothesis.mind_id (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.mind_id IS 'R4 承参过滤列（详设 §8.4.2：E3 带 mind 参时严格等值过滤，可空）；V297 补建，存量 NULL=未挂 Mind';

--

-- COLUMN ecos_cognitive_hypothesis.scenario_id (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.scenario_id IS 'R4 承参过滤列（E3 场景维度 WHERE scenario_id=?）；V297 补建，存量 NULL=未挂场景';

--

-- COLUMN ecos_cognitive_hypothesis.lifecycle_state (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.lifecycle_state IS '<补充设计 v1.0 §8.4>统一认知资产生命周期唯一权威列（词表单源 CognitiveAssetStatus；DRAFT/CANDIDATE/EVIDENCE_CHECKED/VALIDATED/APPROVED/ACTIVE/SUPERSEDED/CONTRADICTED/EXPIRED/REJECTED/DEPRECATED/SUSPENDED）；存量 status 列停写不改语义；NULL=未接管（回落 legacy 展示，禁伪造）';

--

-- COLUMN ecos_cognitive_hypothesis.subject_type (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.subject_type IS 'R4 承参维度主体类型（禁场景专有列；详设 §8.4.2：E3 按 subject 承参）';

--

-- COLUMN ecos_cognitive_hypothesis.subject_id (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.subject_id IS 'R4 承参维度主体标识（与 subject_type 成对）';

--

-- COLUMN ecos_cognitive_hypothesis.reason (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.reason IS 'R4 立据/理由（详设 §8.4.2 与 statement 并列必需；存量 DEFAULT 空串不伪造历史，应用层把关真必填）';

--

-- COLUMN ecos_cognitive_hypothesis.confidence (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.confidence IS 'R4 信心度 NUMERIC(9,6) ∈[0,1]（详设精度；越界即数据缺陷，应用层守护；列 NULL=存量未评信心）';

--

-- ecos_cognitive_mind (TABLE)
CREATE TABLE ecos_cognitive.ecos_cognitive_mind (
    id character varying(36) NOT NULL,
    mind_key character varying(120) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    capability_mask_json text DEFAULT '[]'::text NOT NULL,
    closed_loop_bounds_json text DEFAULT '[]'::text NOT NULL,
    status character varying(20) DEFAULT 'DRAFT' NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    mind_version character varying(20) NOT NULL,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone,
    subject_ref character varying(255),
    scope character varying(255),
    context_ref character varying(255),
    goals_json text,
    key_questions_json text,
    explanation text,
    validity_status character varying(20) DEFAULT 'PENDING_REVIEW' NOT NULL,
    review_status character varying(20) DEFAULT 'DRAFT' NOT NULL,
    adoption_status character varying(20) DEFAULT 'NOT_ADOPTED' NOT NULL,
    change_reason character varying(500),
    superseded_by character varying(36)
);

--

-- TABLE ecos_cognitive_mind (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_cognitive_mind IS '认知模型资产注册表（ADR-8 正名 cognitive_*，禁 kb_* 误名；R-13 ① 归位 ecos_cognitive；旧 E1 数据源 kb_mind_registry 仅存于文档，无库内对象）';

--

-- COLUMN ecos_cognitive_mind.capability_mask_json (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.capability_mask_json IS '能力掩码 TEXT JSON 数组；序列化只经 MindCapabilityMaskCodec（REQ-COG-04，枚举 DETECT/FORECAST/SIMULATE/PLAN/REVIEW）';

--

-- COLUMN ecos_cognitive_mind.closed_loop_bounds_json (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.closed_loop_bounds_json IS '闭环边界 TEXT JSON 数组，枚举 PRE/POST；非法值读侧容错为 [] + issue 记录（F05-03）';

--

-- COLUMN ecos_cognitive_mind.status (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.status IS 'V187 单状态列【停写】；R4 起治理真值恒走三轴（validity_status/review_status/adoption_status），本列只读存量';

--

-- COLUMN ecos_cognitive_mind.subject_ref (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.subject_ref IS 'R4 认知主体引用（六段·认知上下文）；V187 前为 NULL=未挂认知上下文';

--

-- COLUMN ecos_cognitive_mind.scope (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.scope IS 'R4 认知范围（六段·认知上下文）；无明确认知范围的 Mind 禁注册（应用层把关）';

--

-- COLUMN ecos_cognitive_mind.context_ref (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.context_ref IS 'R4 关联业务情境/Scenario 认知上下文引用';

--

-- COLUMN ecos_cognitive_mind.goals_json (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.goals_json IS 'R4 认知目标数组 TEXT JSON（DR04 _json 后缀）';

--

-- COLUMN ecos_cognitive_mind.key_questions_json (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.key_questions_json IS 'R4 关键问题数组 TEXT JSON';

--

-- COLUMN ecos_cognitive_mind.explanation (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.explanation IS 'R4 解释结构（概念关系/因果/机制与推理依据）';

--

-- COLUMN ecos_cognitive_mind.validity_status (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.validity_status IS 'R4 三维状态·认知有效性 VALID|PENDING_REVIEW|INVALIDATED；三轴独立禁合并，CHECK 延后（头注纪律）';

--

-- COLUMN ecos_cognitive_mind.review_status (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.review_status IS 'R4 三维状态·审查治理 DRAFT|PENDING|APPROVED';

--

-- COLUMN ecos_cognitive_mind.adoption_status (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.adoption_status IS 'R4 三维状态·业务采纳 NOT_ADOPTED|ADOPTED|WITHDRAWN';

--

-- COLUMN ecos_cognitive_mind.change_reason (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.change_reason IS 'R4 本版本变化原因（版本历史段）';

--

-- COLUMN ecos_cognitive_mind.superseded_by (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.superseded_by IS 'R4 替代版本行 id（新版本行落下时由受控端点回填本列，形成链）';

--

-- ecos_cognitive_mind_update (TABLE)
CREATE TABLE ecos_cognitive.ecos_cognitive_mind_update (
    id character varying(36) NOT NULL,
    mind_id character varying(36) NOT NULL,
    agent_run_id character varying(36) NOT NULL,
    target_type character varying(20) NOT NULL,
    target_id character varying(36),
    change_json text NOT NULL,
    evidence_refs_json text DEFAULT '[]'::text NOT NULL,
    status character varying(20) DEFAULT 'PROPOSED' NOT NULL,
    reviewed_by character varying(64),
    reviewed_at timestamp without time zone,
    review_reason character varying(500),
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);

--

-- TABLE ecos_cognitive_mind_update (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_cognitive_mind_update IS 'R4 候选更新闸门：W Agent 提交候选只写 PROPOSED；受控端点受理(ACCEPTED)/驳回(REJECTED)留痕；Agent 运行禁直写 belief/hypothesis';

--

-- ecos_cognitive_model (TABLE)
CREATE TABLE ecos_cognitive.ecos_cognitive_model (
    id character varying(36) NOT NULL,
    model_id character varying(64) NOT NULL,
    model_type character varying(32) NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    features_json text,
    backtest_score numeric(8,6),
    status character varying(16) DEFAULT 'active' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone,
    cognitive_model_type character varying(8),
    registry_state character varying(16) DEFAULT 'DRAFT' NOT NULL,
    content_hash character varying(64),
    supersedes_id character varying(36),
    gate_contract character varying(16) DEFAULT 'PENDING' NOT NULL,
    gate_regression character varying(16) DEFAULT 'PENDING' NOT NULL,
    gate_backtest character varying(16) DEFAULT 'PENDING' NOT NULL,
    gate_calibration character varying(16) DEFAULT 'PENDING' NOT NULL,
    published_at timestamp without time zone,
    published_by character varying(64),
    lifecycle_state character varying(30),
    model_key character varying(120),
    description text,
    phase smallint DEFAULT 1 NOT NULL,
    compute_owner character varying(64),
    io_contract_json text,
    limits_json text,
    model_category character varying(32)
);

--

-- TABLE ecos_cognitive_model (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_cognitive_model IS '认知模型注册表（ADR-8 模型资产落盘，R-13 ① 归位 ecos_cognitive；旧 public.ecos_cognitive_model 双写期保留）';

--

-- COLUMN ecos_cognitive_model.cognitive_model_type (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.cognitive_model_type IS 'CM-01~CM-07 认知模型类型（枚举单源在 cognitive-engine-api；NULL=未映射旧值，注册端点 fail-loud 拒绝）；旧 model_type 列只读保留（API 只增不改）';

--

-- COLUMN ecos_cognitive_model.registry_state (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.registry_state IS 'DRAFT/PUBLISHED（四类发布门禁 contract·regression·backtest·calibration 全 PASS 才可升 PUBLISHED；任一缺项停留 DRAFT 且发布端点返 4xx，禁静默成功）；存量行一律 DRAFT';

--

-- COLUMN ecos_cognitive_model.content_hash (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.content_hash IS '发布时冻结的规范化内容摘要（SHA-256 hex）；PUBLISHED 且非空 = 不可变态，UPDATE 必败（应用侧守卫）';

--

-- COLUMN ecos_cognitive_model.supersedes_id (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.supersedes_id IS '被替代版本行 id（版本变更只准新建版本行并指旧版，禁原地 UPDATE 已发布行）';

--

-- COLUMN ecos_cognitive_model.gate_contract (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.gate_contract IS '发布门禁-契约校验：PASS/FAIL/PENDING（三值字符串，禁布尔——需区分未跑与跑失败）';

--

-- COLUMN ecos_cognitive_model.gate_regression (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.gate_regression IS '发布门禁-回归集：PASS/FAIL/PENDING';

--

-- COLUMN ecos_cognitive_model.gate_backtest (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.gate_backtest IS '发布门禁-回测：PASS/FAIL/PENDING（与既有 backtest_score 列配套：分数供排序，门禁供裁决）';

--

-- COLUMN ecos_cognitive_model.gate_calibration (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.gate_calibration IS '发布门禁-校准：PASS/FAIL/PENDING';

--

-- COLUMN ecos_cognitive_model.published_at (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.published_at IS '升格 PUBLISHED 时点（四门禁全 PASS 时由发布端点写入）';

--

-- COLUMN ecos_cognitive_model.published_by (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.published_by IS '升格操作人（审计留痕，非个人敏感数据；与 create_by/update_by 分列）';

--

-- COLUMN ecos_cognitive_model.lifecycle_state (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.lifecycle_state IS '<补充设计 v1.0 §8.4>CM 治理生命周期；与 registry_state（门禁态 PUBLISHED/REJECTED）、memora_state（合约上下线态）正交三列并存，各管一段禁合并';

--

-- COLUMN ecos_cognitive_model.model_key (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.model_key IS 'R4 稳定业务键（详设 §8.4.2）；V296 前该行 NULL=未挂键，应用侧注册端点补挂';

--

-- COLUMN ecos_cognitive_model.description (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.description IS 'R4 模型资产说明';

--

-- COLUMN ecos_cognitive_model.phase (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.phase IS 'R4 三期路线分期 1/2/3（首期因果/诊断/假设检验，二期预测/风险/仿真/关系，三期优化/行为/信念更新）；CHECK 延后，应用层守护（V292 同纪律）';

--

-- COLUMN ecos_cognitive_model.compute_owner (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.compute_owner IS 'R4 计算执行归属（认知模型≠AI模型≠计算引擎）';

--

-- COLUMN ecos_cognitive_model.io_contract_json (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.io_contract_json IS 'R4 输入→输出契约 TEXT JSON（DR04 _json 后缀）';

--

-- COLUMN ecos_cognitive_model.limits_json (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.limits_json IS 'R4 适用限制 TEXT JSON';

--

-- COLUMN ecos_cognitive_model.model_category (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.model_category IS 'R4 九类分类权威列 CAUSAL|DIAGNOSTIC|HYPOTHESIS_TESTING|PREDICTIVE|SIMULATION|RISK|RELATIONAL|OPTIMIZATION|BEHAVIORAL（详设 §8.4.2）；与旧 model_type 四值、cognitive_model_type CM-01~07 并存各守其域；CHECK 延后，应用层单源 CognitiveR4ModelCategory 守护';

--

-- ecos_cognitive_outcome_evaluation (TABLE)
CREATE TABLE ecos_cognitive.ecos_cognitive_outcome_evaluation (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    scenario_id character varying(36) NOT NULL,
    blueprint_id character varying(100),
    blueprint_version character varying(20),
    run_id character varying(36),
    forecast_run_id character varying(36),
    actual_asset_id character varying(64),
    actual_version character varying(20),
    caliber_id character varying(64),
    window_from_date date,
    window_to_date date,
    status character varying(20) DEFAULT 'PENDING' NOT NULL,
    metric_results_text text,
    diagnoses_text text,
    update_candidates_text text,
    warnings_text text,
    calculation_refs_text text,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    idempotency_key character varying(100),
    blockers_text text,
    caliber_alignment character varying(200)
);

--

-- TABLE ecos_cognitive_outcome_evaluation (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_cognitive_outcome_evaluation IS 'W→D 结果评估（补充设计 v1.0 §9）：评估记录落库，数值只存计算引用可复算；update_candidates 只存候选引用，单次偏差禁自动改写生产资产（SUP-OUT-01/02）';

--

-- COLUMN ecos_cognitive_outcome_evaluation.idempotency_key (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_outcome_evaluation.idempotency_key IS '幂等键（§7.5）：同键重试返回既有评估记录，禁重复计算/重复建记录';

--

-- COLUMN ecos_cognitive_outcome_evaluation.blockers_text (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_outcome_evaluation.blockers_text IS 'JSON 数组：§9.4 命中的禁自更新情形枚举名（ACTUALS_NOT_CLOSED 等七项词表）；空数组=未命中，非空 ⇒ status 必为 NEEDS_RECONCILE';

--

-- COLUMN ecos_cognitive_outcome_evaluation.caliber_alignment (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_outcome_evaluation.caliber_alignment IS '§9.2 步骤 2 口径对齐结论：ALIGNED / MISMATCH:{维度} / UNCONFIRMED:{缺失侧}';

--

-- ecos_cognitive_pipeline (TABLE)
CREATE TABLE ecos_cognitive.ecos_cognitive_pipeline (
    id character varying(36) NOT NULL,
    pipeline_id character varying(64) NOT NULL,
    name character varying(255),
    status character varying(32) DEFAULT 'DRAFT' NOT NULL,
    config_json text,
    result_json text,
    create_by character varying(100),
    update_by character varying(100),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL
);

--

-- TABLE ecos_cognitive_pipeline (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_cognitive_pipeline IS '认知管线定义表（cognitive-engine 自有，ST07 收编；旧 public.kb_cognitive_pipeline 停写保留）';

--

-- ecos_cognitive_run_invalidation (TABLE)
CREATE TABLE ecos_cognitive.ecos_cognitive_run_invalidation (
    id character varying(36) NOT NULL,
    event_id character varying(64) NOT NULL,
    hypothesis_id character varying(64) NOT NULL,
    run_id character varying(64) NOT NULL,
    auto_detected smallint DEFAULT 1 NOT NULL,
    superseded_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    detail text,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);

--

-- TABLE ecos_cognitive_run_invalidation (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_cognitive_run_invalidation IS '失效→作废推演结论关联留痕表（ADR-9 心智事件；run_id 指向 ecos_dw 产物面，随 R-17 ①）';

--

-- ecos_cognitive_scenario_mind (TABLE)
CREATE TABLE ecos_cognitive.ecos_cognitive_scenario_mind (
    id character varying(36) NOT NULL,
    scenario_id character varying(36) NOT NULL,
    mind_id character varying(36) NOT NULL,
    binding_role character varying(20) DEFAULT 'PRIMARY' NOT NULL,
    priority integer DEFAULT 100 NOT NULL,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);

--

-- TABLE ecos_cognitive_scenario_mind (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_cognitive_scenario_mind IS '场景↔Mind 绑定关系表（定义态资产，R-13 ① 落 ecos_cognitive；替代 legacy ecos_scenario_mind/V146，不重建 legacy 名；E1 detect = ecos_cognitive_mind × 本表 JOIN，仅取 is_active=1 且 is_deleted=0）';

--

-- COLUMN ecos_cognitive_scenario_mind.mind_id (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_scenario_mind.mind_id IS '首个 mind_id 列（X-19：旧库全库 0 个）；指向 ecos_cognitive.ecos_cognitive_mind.id，FK 不建（跨表约束经应用层完整性校验）';

--

-- COLUMN ecos_cognitive_scenario_mind.binding_role (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_scenario_mind.binding_role IS 'PRIMARY/SUPPORTING；CHECK 约束待应用全量切换后补建（本批不建，见头注）';

--

-- ecos_goal_tracking (TABLE)
CREATE TABLE ecos_cognitive.ecos_goal_tracking (
    id character varying(36) NOT NULL,
    goal_id bigint NOT NULL,
    progress integer,
    actual_value numeric(18,2),
    note text,
    recorded_at date,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT ecos_goal_tracking_progress_check CHECK (((progress >= 0) AND (progress <= 100)))
);

--

-- TABLE ecos_goal_tracking (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_goal_tracking IS '目标追踪表';

--

-- ecos_scenario_binding (TABLE)
CREATE TABLE ecos_cognitive.ecos_scenario_binding (
    id character varying(36) NOT NULL,
    scenario_id character varying(64) NOT NULL,
    binding_type character varying(32) NOT NULL,
    target_ref character varying(255) NOT NULL,
    target_id character varying(36),
    target_type character varying(20),
    remark text DEFAULT ''::text,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);

--

-- TABLE ecos_scenario_binding (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_scenario_binding IS '场景六类绑定表（target_ref 旧别名只读兼容；target_id/target_type 真引用列，值域以 V199 契约为准）';

--

-- ecos_scenario_binding_link (TABLE)
CREATE TABLE ecos_cognitive.ecos_scenario_binding_link (
    id character varying(36) NOT NULL,
    scenario_id character varying(64) NOT NULL,
    source_binding_id character varying(64) NOT NULL,
    target_binding_id character varying(64) NOT NULL,
    link_type character varying(16) NOT NULL,
    source_contract character varying(128),
    remark text DEFAULT ''::text,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone,
    CONSTRAINT ck_pg_mirror_blink_link_type CHECK (((link_type)::text = ANY ((ARRAY['MAPPING', 'EXTRACTION', 'COGNITION', 'GOVERN', 'EXPOSE'])::text[])))
);

--

-- TABLE ecos_scenario_binding_link (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_scenario_binding_link IS '场景绑定关系边表（§0.6.2；link_type 值域与 V160 完全一致，非新枚举收敛）';

--

-- ecos_wm_causal_link (TABLE)
CREATE TABLE ecos_cognitive.ecos_wm_causal_link (
    id bigint NOT NULL,
    source_goal_id bigint NOT NULL,
    target_goal_id bigint NOT NULL,
    relationship_type character varying(32) DEFAULT 'POSITIVE',
    description text,
    time_lag_days integer DEFAULT 0,
    correlation_coefficient numeric(4,3) DEFAULT 0.0,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE ecos_wm_causal_link (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_wm_causal_link IS '目标因果链表';

--

-- ecos_wm_causal_link_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_cognitive.ecos_wm_causal_link_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_wm_causal_link_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_cognitive.ecos_wm_causal_link_id_seq OWNED BY ecos_cognitive.ecos_wm_causal_link.id;

--

-- ecos_wm_goal (TABLE)
CREATE TABLE ecos_cognitive.ecos_wm_goal (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    parent_id bigint,
    goal_type character varying(32) DEFAULT 'STRATEGIC',
    weight integer DEFAULT 50,
    progress integer DEFAULT 0,
    status character varying(32) DEFAULT 'PLANNED',
    org_id character varying(64),
    owner_user_id character varying(64),
    start_date date,
    end_date date,
    target_value numeric(18,2),
    current_value numeric(18,2),
    unit character varying(32),
    linked_workflow_id character varying(64),
    domain_id character varying(50),
    kpi_formula character varying(256),
    measure_frequency character varying(16) DEFAULT 'MONTHLY',
    alert_threshold_warn numeric(5,2) DEFAULT 80.0,
    alert_threshold_critical numeric(5,2) DEFAULT 50.0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT ecos_wm_goal_progress_check CHECK (((progress >= 0) AND (progress <= 100)))
);

--

-- TABLE ecos_wm_goal (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_wm_goal IS '世界模型目标表';

--

-- COLUMN ecos_wm_goal.goal_type (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_wm_goal.goal_type IS '目标类型: STRATEGIC/TACTICAL/OPERATIONAL';

--

-- COLUMN ecos_wm_goal.kpi_formula (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_wm_goal.kpi_formula IS 'KPI公式';

--

-- COLUMN ecos_wm_goal.measure_frequency (COMMENT)
COMMENT ON COLUMN ecos_cognitive.ecos_wm_goal.measure_frequency IS '度量频率: DAILY/WEEKLY/MONTHLY/QUARTERLY';

--

-- ecos_wm_goal_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_cognitive.ecos_wm_goal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_wm_goal_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_cognitive.ecos_wm_goal_id_seq OWNED BY ecos_cognitive.ecos_wm_goal.id;

--

-- ecos_wm_goal_log (TABLE)
CREATE TABLE ecos_cognitive.ecos_wm_goal_log (
    id bigint NOT NULL,
    goal_id bigint NOT NULL,
    change_type character varying(32),
    old_value text,
    new_value text,
    changed_by character varying(64),
    changed_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE ecos_wm_goal_log (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_wm_goal_log IS '目标变更日志表';

--

-- ecos_wm_goal_log_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_cognitive.ecos_wm_goal_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_wm_goal_log_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_cognitive.ecos_wm_goal_log_id_seq OWNED BY ecos_cognitive.ecos_wm_goal_log.id;

--

-- ecos_wm_scenario (TABLE)
CREATE TABLE ecos_cognitive.ecos_wm_scenario (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    config_json text DEFAULT '{}'::text,
    status character varying(32) DEFAULT 'DRAFT',
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE ecos_wm_scenario (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_wm_scenario IS '世界模型场景表';

--

-- ecos_wm_scenario_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_cognitive.ecos_wm_scenario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_wm_scenario_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_cognitive.ecos_wm_scenario_id_seq OWNED BY ecos_cognitive.ecos_wm_scenario.id;

--

-- ecos_world_scenarios (TABLE)
CREATE TABLE ecos_cognitive.ecos_world_scenarios (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    goal_ids text DEFAULT '[]'::text,
    status character varying(32) DEFAULT 'draft',
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE ecos_world_scenarios (COMMENT)
COMMENT ON TABLE ecos_cognitive.ecos_world_scenarios IS '世界场景配置表';

--

-- ecos_biz_metric id (DEFAULT)
ALTER TABLE ONLY ecos_cognitive.ecos_biz_metric ALTER COLUMN id SET DEFAULT nextval('ecos_cognitive.ecos_biz_metric_id_seq'::regclass);

--

-- ecos_biz_target id (DEFAULT)
ALTER TABLE ONLY ecos_cognitive.ecos_biz_target ALTER COLUMN id SET DEFAULT nextval('ecos_cognitive.ecos_biz_target_id_seq'::regclass);

--

-- ecos_wm_causal_link id (DEFAULT)
ALTER TABLE ONLY ecos_cognitive.ecos_wm_causal_link ALTER COLUMN id SET DEFAULT nextval('ecos_cognitive.ecos_wm_causal_link_id_seq'::regclass);

--

-- ecos_wm_goal id (DEFAULT)
ALTER TABLE ONLY ecos_cognitive.ecos_wm_goal ALTER COLUMN id SET DEFAULT nextval('ecos_cognitive.ecos_wm_goal_id_seq'::regclass);

--

-- ecos_wm_goal_log id (DEFAULT)
ALTER TABLE ONLY ecos_cognitive.ecos_wm_goal_log ALTER COLUMN id SET DEFAULT nextval('ecos_cognitive.ecos_wm_goal_log_id_seq'::regclass);

--

-- ecos_wm_scenario id (DEFAULT)
ALTER TABLE ONLY ecos_cognitive.ecos_wm_scenario ALTER COLUMN id SET DEFAULT nextval('ecos_cognitive.ecos_wm_scenario_id_seq'::regclass);

--

-- ecos_biz_contract ecos_biz_contract_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_biz_contract
    ADD CONSTRAINT ecos_biz_contract_pkey PRIMARY KEY (id);

--

-- ecos_biz_department ecos_biz_department_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_biz_department
    ADD CONSTRAINT ecos_biz_department_pkey PRIMARY KEY (id);

--

-- ecos_biz_metric ecos_biz_metric_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_biz_metric
    ADD CONSTRAINT ecos_biz_metric_pkey PRIMARY KEY (id);

--

-- ecos_biz_project ecos_biz_project_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_biz_project
    ADD CONSTRAINT ecos_biz_project_pkey PRIMARY KEY (id);

--

-- ecos_biz_target ecos_biz_target_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_biz_target
    ADD CONSTRAINT ecos_biz_target_pkey PRIMARY KEY (id);

--

-- ecos_cognitive_framework ecos_cognitive_framework_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_framework
    ADD CONSTRAINT ecos_cognitive_framework_pkey PRIMARY KEY (id);

--

-- ecos_cognitive_outcome_evaluation ecos_cognitive_outcome_evaluation_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_outcome_evaluation
    ADD CONSTRAINT ecos_cognitive_outcome_evaluation_pkey PRIMARY KEY (id);

--

-- ecos_goal_tracking ecos_goal_tracking_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_goal_tracking
    ADD CONSTRAINT ecos_goal_tracking_pkey PRIMARY KEY (id);

--

-- ecos_wm_causal_link ecos_wm_causal_link_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_wm_causal_link
    ADD CONSTRAINT ecos_wm_causal_link_pkey PRIMARY KEY (id);

--

-- ecos_wm_goal_log ecos_wm_goal_log_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_wm_goal_log
    ADD CONSTRAINT ecos_wm_goal_log_pkey PRIMARY KEY (id);

--

-- ecos_wm_goal ecos_wm_goal_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_wm_goal
    ADD CONSTRAINT ecos_wm_goal_pkey PRIMARY KEY (id);

--

-- ecos_wm_scenario ecos_wm_scenario_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_wm_scenario
    ADD CONSTRAINT ecos_wm_scenario_pkey PRIMARY KEY (id);

--

-- ecos_world_scenarios ecos_world_scenarios_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_world_scenarios
    ADD CONSTRAINT ecos_world_scenarios_pkey PRIMARY KEY (id);

--

-- ecos_cognitive_mind_update pk_ecos_cognitive_mind_update (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_mind_update
    ADD CONSTRAINT pk_ecos_cognitive_mind_update PRIMARY KEY (id);

--

-- ecos_scenario_binding_link pk_pg_mirror_binding_link (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_scenario_binding_link
    ADD CONSTRAINT pk_pg_mirror_binding_link PRIMARY KEY (id);

--

-- ecos_business_scenario pk_pg_mirror_business_scenario (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_business_scenario
    ADD CONSTRAINT pk_pg_mirror_business_scenario PRIMARY KEY (id);

--

-- ecos_cognitive_belief pk_pg_mirror_cognitive_belief (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_belief
    ADD CONSTRAINT pk_pg_mirror_cognitive_belief PRIMARY KEY (id);

--

-- ecos_cognitive_evidence pk_pg_mirror_cognitive_evidence (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_evidence
    ADD CONSTRAINT pk_pg_mirror_cognitive_evidence PRIMARY KEY (id);

--

-- ecos_cognitive_hypothesis pk_pg_mirror_cognitive_hypothesis (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_hypothesis
    ADD CONSTRAINT pk_pg_mirror_cognitive_hypothesis PRIMARY KEY (id);

--

-- ecos_cognitive_mind pk_pg_mirror_cognitive_mind (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_mind
    ADD CONSTRAINT pk_pg_mirror_cognitive_mind PRIMARY KEY (id);

--

-- ecos_cognitive_model pk_pg_mirror_cognitive_model (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_model
    ADD CONSTRAINT pk_pg_mirror_cognitive_model PRIMARY KEY (id);

--

-- ecos_cognitive_pipeline pk_pg_mirror_cognitive_pipeline (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_pipeline
    ADD CONSTRAINT pk_pg_mirror_cognitive_pipeline PRIMARY KEY (id);

--

-- ecos_cognitive_scenario_mind pk_pg_mirror_cognitive_scn_mind (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_scenario_mind
    ADD CONSTRAINT pk_pg_mirror_cognitive_scn_mind PRIMARY KEY (id);

--

-- ecos_cognitive_run_invalidation pk_pg_mirror_run_invalidation (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_run_invalidation
    ADD CONSTRAINT pk_pg_mirror_run_invalidation PRIMARY KEY (id);

--

-- ecos_scenario_binding pk_pg_mirror_scenario_binding (CONSTRAINT)
ALTER TABLE ONLY ecos_cognitive.ecos_scenario_binding
    ADD CONSTRAINT pk_pg_mirror_scenario_binding PRIMARY KEY (id);

--

-- idx_cm_active (INDEX)
CREATE INDEX idx_cm_active ON ecos_cognitive.ecos_cognitive_mind USING btree (is_active, is_deleted);

--

-- idx_cmu_mind (INDEX)
CREATE INDEX idx_cmu_mind ON ecos_cognitive.ecos_cognitive_mind_update USING btree (mind_id, status, is_deleted);

--

-- idx_cmu_run (INDEX)
CREATE INDEX idx_cmu_run ON ecos_cognitive.ecos_cognitive_mind_update USING btree (agent_run_id, is_deleted);

--

-- idx_cog_causal_src (INDEX)
CREATE INDEX idx_cog_causal_src ON ecos_cognitive.ecos_wm_causal_link USING btree (source_goal_id);

--

-- idx_cog_causal_tgt (INDEX)
CREATE INDEX idx_cog_causal_tgt ON ecos_cognitive.ecos_wm_causal_link USING btree (target_goal_id);

--

-- idx_cog_contract_proj (INDEX)
CREATE INDEX idx_cog_contract_proj ON ecos_cognitive.ecos_biz_contract USING btree (project_id);

--

-- idx_cog_dept_parent (INDEX)
CREATE INDEX idx_cog_dept_parent ON ecos_cognitive.ecos_biz_department USING btree (parent_id);

--

-- idx_cog_goal_org (INDEX)
CREATE INDEX idx_cog_goal_org ON ecos_cognitive.ecos_wm_goal USING btree (org_id);

--

-- idx_cog_goal_parent (INDEX)
CREATE INDEX idx_cog_goal_parent ON ecos_cognitive.ecos_wm_goal USING btree (parent_id);

--

-- idx_cog_goal_status (INDEX)
CREATE INDEX idx_cog_goal_status ON ecos_cognitive.ecos_wm_goal USING btree (status);

--

-- idx_cog_goal_tenant (INDEX)
CREATE INDEX idx_cog_goal_tenant ON ecos_cognitive.ecos_wm_goal USING btree (tenant_id);

--

-- idx_cog_goallog_goal (INDEX)
CREATE INDEX idx_cog_goallog_goal ON ecos_cognitive.ecos_wm_goal_log USING btree (goal_id);

--

-- idx_cog_goallog_time (INDEX)
CREATE INDEX idx_cog_goallog_time ON ecos_cognitive.ecos_wm_goal_log USING btree (changed_at);

--

-- idx_cog_metric_dept (INDEX)
CREATE INDEX idx_cog_metric_dept ON ecos_cognitive.ecos_biz_metric USING btree (dept_id);

--

-- idx_cog_proj_dept (INDEX)
CREATE INDEX idx_cog_proj_dept ON ecos_cognitive.ecos_biz_project USING btree (dept_id);

--

-- idx_cog_tracking_goal (INDEX)
CREATE INDEX idx_cog_tracking_goal ON ecos_cognitive.ecos_goal_tracking USING btree (goal_id, recorded_at);

--

-- idx_cog_wmscen_status (INDEX)
CREATE INDEX idx_cog_wmscen_status ON ecos_cognitive.ecos_wm_scenario USING btree (status);

--

-- idx_cog_worldscen_status (INDEX)
CREATE INDEX idx_cog_worldscen_status ON ecos_cognitive.ecos_world_scenarios USING btree (status);

--

-- idx_csm_mind (INDEX)
CREATE INDEX idx_csm_mind ON ecos_cognitive.ecos_cognitive_scenario_mind USING btree (mind_id, is_deleted);

--

-- idx_cv191_belief_domain (INDEX)
CREATE INDEX idx_cv191_belief_domain ON ecos_cognitive.ecos_cognitive_belief USING btree (domain, is_deleted);

--

-- idx_cv191_belief_mind (INDEX)
CREATE INDEX idx_cv191_belief_mind ON ecos_cognitive.ecos_cognitive_belief USING btree (mind_id, scenario_id);

--

-- idx_cv192_model_domain (INDEX)
CREATE INDEX idx_cv192_model_domain ON ecos_cognitive.ecos_cognitive_model USING btree (domain, is_deleted);

--

-- idx_cv192_model_type (INDEX)
CREATE INDEX idx_cv192_model_type ON ecos_cognitive.ecos_cognitive_model USING btree (model_type, status);

--

-- idx_cv193_bizscn_dept (INDEX)
CREATE INDEX idx_cv193_bizscn_dept ON ecos_cognitive.ecos_business_scenario USING btree (department, is_deleted);

--

-- idx_cv193_bizscn_status (INDEX)
CREATE INDEX idx_cv193_bizscn_status ON ecos_cognitive.ecos_business_scenario USING btree (status, is_deleted);

--

-- idx_cv261_model_cm (INDEX)
CREATE INDEX idx_cv261_model_cm ON ecos_cognitive.ecos_cognitive_model USING btree (cognitive_model_type, registry_state);

--

-- idx_cv261_model_state (INDEX)
CREATE INDEX idx_cv261_model_state ON ecos_cognitive.ecos_cognitive_model USING btree (registry_state, is_deleted);

--

-- idx_cv261_model_supersed (INDEX)
CREATE INDEX idx_cv261_model_supersed ON ecos_cognitive.ecos_cognitive_model USING btree (supersedes_id);

--

-- idx_cv262_belief_prob (INDEX)
CREATE INDEX idx_cv262_belief_prob ON ecos_cognitive.ecos_cognitive_belief USING btree (probability, is_deleted);

--

-- idx_cv267_outcome_run (INDEX)
CREATE INDEX idx_cv267_outcome_run ON ecos_cognitive.ecos_cognitive_outcome_evaluation USING btree (run_id, is_deleted);

--

-- idx_cv267_outcome_scenario (INDEX)
CREATE INDEX idx_cv267_outcome_scenario ON ecos_cognitive.ecos_cognitive_outcome_evaluation USING btree (scenario_id, status, is_deleted);

--

-- idx_cv268_belief_kd (INDEX)
CREATE INDEX idx_cv268_belief_kd ON ecos_cognitive.ecos_cognitive_belief USING btree (prob_kind, is_deleted);

--

-- idx_cv268_belief_lc (INDEX)
CREATE INDEX idx_cv268_belief_lc ON ecos_cognitive.ecos_cognitive_belief USING btree (lifecycle_state, is_deleted);

--

-- idx_cv268_hypo_lc (INDEX)
CREATE INDEX idx_cv268_hypo_lc ON ecos_cognitive.ecos_cognitive_hypothesis USING btree (lifecycle_state, is_deleted);

--

-- idx_cv268_model_lc (INDEX)
CREATE INDEX idx_cv268_model_lc ON ecos_cognitive.ecos_cognitive_model USING btree (lifecycle_state, is_deleted);

--

-- idx_cv274_outcome_trace (INDEX)
CREATE INDEX idx_cv274_outcome_trace ON ecos_cognitive.ecos_cognitive_outcome_evaluation USING btree (trace_id, is_deleted);

--

-- idx_ecos_cognitive_pipeline_domain (INDEX)
CREATE INDEX idx_ecos_cognitive_pipeline_domain ON ecos_cognitive.ecos_cognitive_pipeline USING btree (domain);

--

-- idx_pg_mirror_belief_domain (INDEX)
CREATE INDEX idx_pg_mirror_belief_domain ON ecos_cognitive.ecos_cognitive_belief USING btree (domain, is_deleted);

--

-- idx_pg_mirror_belief_mind (INDEX)
CREATE INDEX idx_pg_mirror_belief_mind ON ecos_cognitive.ecos_cognitive_belief USING btree (mind_id, scenario_id);

--

-- idx_pg_mirror_belief_prob (INDEX)
CREATE INDEX idx_pg_mirror_belief_prob ON ecos_cognitive.ecos_cognitive_belief USING btree (probability, is_deleted);

--

-- idx_pg_mirror_bind_scn (INDEX)
CREATE INDEX idx_pg_mirror_bind_scn ON ecos_cognitive.ecos_scenario_binding USING btree (scenario_id, is_deleted);

--

-- idx_pg_mirror_bind_tid (INDEX)
CREATE INDEX idx_pg_mirror_bind_tid ON ecos_cognitive.ecos_scenario_binding USING btree (target_id, target_type);

--

-- idx_pg_mirror_bind_type (INDEX)
CREATE INDEX idx_pg_mirror_bind_type ON ecos_cognitive.ecos_scenario_binding USING btree (binding_type, is_deleted);

--

-- idx_pg_mirror_bizscn_dept (INDEX)
CREATE INDEX idx_pg_mirror_bizscn_dept ON ecos_cognitive.ecos_business_scenario USING btree (department, is_deleted);

--

-- idx_pg_mirror_bizscn_status (INDEX)
CREATE INDEX idx_pg_mirror_bizscn_status ON ecos_cognitive.ecos_business_scenario USING btree (status, is_deleted);

--

-- idx_pg_mirror_blink_scn (INDEX)
CREATE INDEX idx_pg_mirror_blink_scn ON ecos_cognitive.ecos_scenario_binding_link USING btree (scenario_id, is_deleted);

--

-- idx_pg_mirror_blink_src (INDEX)
CREATE INDEX idx_pg_mirror_blink_src ON ecos_cognitive.ecos_scenario_binding_link USING btree (source_binding_id);

--

-- idx_pg_mirror_blink_tgt (INDEX)
CREATE INDEX idx_pg_mirror_blink_tgt ON ecos_cognitive.ecos_scenario_binding_link USING btree (target_binding_id);

--

-- idx_pg_mirror_blink_type (INDEX)
CREATE INDEX idx_pg_mirror_blink_type ON ecos_cognitive.ecos_scenario_binding_link USING btree (link_type);

--

-- idx_pg_mirror_cm_active (INDEX)
CREATE INDEX idx_pg_mirror_cm_active ON ecos_cognitive.ecos_cognitive_mind USING btree (is_active, is_deleted);

--

-- idx_pg_mirror_csm_mind (INDEX)
CREATE INDEX idx_pg_mirror_csm_mind ON ecos_cognitive.ecos_cognitive_scenario_mind USING btree (mind_id, is_deleted);

--

-- idx_pg_mirror_ev_source (INDEX)
CREATE INDEX idx_pg_mirror_ev_source ON ecos_cognitive.ecos_cognitive_evidence USING btree (source_type, status);

--

-- idx_pg_mirror_ev_time (INDEX)
CREATE INDEX idx_pg_mirror_ev_time ON ecos_cognitive.ecos_cognitive_evidence USING btree (create_time);

--

-- idx_pg_mirror_hypo_domain (INDEX)
CREATE INDEX idx_pg_mirror_hypo_domain ON ecos_cognitive.ecos_cognitive_hypothesis USING btree (domain, invalid_at);

--

-- idx_pg_mirror_hypo_mind (INDEX)
CREATE INDEX idx_pg_mirror_hypo_mind ON ecos_cognitive.ecos_cognitive_hypothesis USING btree (mind_id, scenario_id);

--

-- idx_pg_mirror_model_cm (INDEX)
CREATE INDEX idx_pg_mirror_model_cm ON ecos_cognitive.ecos_cognitive_model USING btree (cognitive_model_type, registry_state);

--

-- idx_pg_mirror_model_domain (INDEX)
CREATE INDEX idx_pg_mirror_model_domain ON ecos_cognitive.ecos_cognitive_model USING btree (domain, is_deleted);

--

-- idx_pg_mirror_model_state (INDEX)
CREATE INDEX idx_pg_mirror_model_state ON ecos_cognitive.ecos_cognitive_model USING btree (registry_state, is_deleted);

--

-- idx_pg_mirror_model_supersed (INDEX)
CREATE INDEX idx_pg_mirror_model_supersed ON ecos_cognitive.ecos_cognitive_model USING btree (supersedes_id);

--

-- idx_pg_mirror_model_type (INDEX)
CREATE INDEX idx_pg_mirror_model_type ON ecos_cognitive.ecos_cognitive_model USING btree (model_type, status);

--

-- idx_pg_mirror_pipeline_domain (INDEX)
CREATE INDEX idx_pg_mirror_pipeline_domain ON ecos_cognitive.ecos_cognitive_pipeline USING btree (domain);

--

-- idx_pg_mirror_run_inv_hyp (INDEX)
CREATE INDEX idx_pg_mirror_run_inv_hyp ON ecos_cognitive.ecos_cognitive_run_invalidation USING btree (hypothesis_id);

--

-- idx_pg_mirror_run_inv_run (INDEX)
CREATE INDEX idx_pg_mirror_run_inv_run ON ecos_cognitive.ecos_cognitive_run_invalidation USING btree (run_id);

--

-- idx_pg_mirror_run_inv_time (INDEX)
CREATE INDEX idx_pg_mirror_run_inv_time ON ecos_cognitive.ecos_cognitive_run_invalidation USING btree (superseded_at);

--

-- idx_v311_cog_framework_status (INDEX)
CREATE INDEX idx_v311_cog_framework_status ON ecos_cognitive.ecos_cognitive_framework USING btree (status, category, is_deleted);

--

-- uk_cv274_outcome_idem (INDEX)
CREATE UNIQUE INDEX uk_cv274_outcome_idem ON ecos_cognitive.ecos_cognitive_outcome_evaluation USING btree (tenant_id, idempotency_key, is_deleted);

--

-- uk_v311_cog_framework_ver (INDEX)
CREATE UNIQUE INDEX uk_v311_cog_framework_ver ON ecos_cognitive.ecos_cognitive_framework USING btree (framework_id, version, is_deleted);

--

-- uniq_cm_key_ver (INDEX)
CREATE UNIQUE INDEX uniq_cm_key_ver ON ecos_cognitive.ecos_cognitive_mind USING btree (mind_key, mind_version, is_deleted);

--

-- uniq_csm_scn_mind (INDEX)
CREATE UNIQUE INDEX uniq_csm_scn_mind ON ecos_cognitive.ecos_cognitive_scenario_mind USING btree (scenario_id, mind_id, is_deleted);

--

-- uniq_cv191_belief_var_ver (INDEX)
CREATE UNIQUE INDEX uniq_cv191_belief_var_ver ON ecos_cognitive.ecos_cognitive_belief USING btree (variable_name, domain, version, is_deleted);

--

-- uniq_cv192_model_id_ver (INDEX)
CREATE UNIQUE INDEX uniq_cv192_model_id_ver ON ecos_cognitive.ecos_cognitive_model USING btree (model_id, version, is_deleted);

--

-- uniq_ecos_cognitive_pipeline_pid (INDEX)
CREATE UNIQUE INDEX uniq_ecos_cognitive_pipeline_pid ON ecos_cognitive.ecos_cognitive_pipeline USING btree (pipeline_id) WHERE (is_deleted = 0);

--

-- uniq_pg_mirror_belief_var_ver (INDEX)
CREATE UNIQUE INDEX uniq_pg_mirror_belief_var_ver ON ecos_cognitive.ecos_cognitive_belief USING btree (variable_name, domain, version, is_deleted);

--

-- uniq_pg_mirror_cm_key_ver (INDEX)
CREATE UNIQUE INDEX uniq_pg_mirror_cm_key_ver ON ecos_cognitive.ecos_cognitive_mind USING btree (mind_key, mind_version, is_deleted);

--

-- uniq_pg_mirror_csm_scn_mind (INDEX)
CREATE UNIQUE INDEX uniq_pg_mirror_csm_scn_mind ON ecos_cognitive.ecos_cognitive_scenario_mind USING btree (scenario_id, mind_id, is_deleted);

--

-- uniq_pg_mirror_ev_code (INDEX)
CREATE UNIQUE INDEX uniq_pg_mirror_ev_code ON ecos_cognitive.ecos_cognitive_evidence USING btree (evidence_code, is_deleted);

--

-- uniq_pg_mirror_hypo_code (INDEX)
CREATE UNIQUE INDEX uniq_pg_mirror_hypo_code ON ecos_cognitive.ecos_cognitive_hypothesis USING btree (hypothesis_code, is_deleted);

--

-- uniq_pg_mirror_model_id_ver (INDEX)
CREATE UNIQUE INDEX uniq_pg_mirror_model_id_ver ON ecos_cognitive.ecos_cognitive_model USING btree (model_id, version, is_deleted);

--

-- uniq_pg_mirror_pipeline_pid (INDEX)
CREATE UNIQUE INDEX uniq_pg_mirror_pipeline_pid ON ecos_cognitive.ecos_cognitive_pipeline USING btree (pipeline_id, is_deleted);

--

-- uniq_pg_mirror_run_inv_evt_run (INDEX)
CREATE UNIQUE INDEX uniq_pg_mirror_run_inv_evt_run ON ecos_cognitive.ecos_cognitive_run_invalidation USING btree (event_id, run_id, is_deleted);

--

