-- ============================================================================
-- 01_ecos_control.sql — ecos_control 权威 DDL
-- ============================================================================
-- 由 pg_dump --schema-only 从真实库 sys_man 反向导出后清洗装配（2026-11-17，V349 清理后重导）。
-- 目标 schema = ecos_control；保留对象 861 个，剔分区子表块 0 个，外键 17 条移入 09。
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

-- cache_invalidation_outbox (TABLE)
CREATE TABLE ecos_control.cache_invalidation_outbox (
    id character varying(36) NOT NULL,
    event_type character varying(64) NOT NULL,
    payload_json text NOT NULL,
    status character varying(16) DEFAULT 'PENDING' NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'sysman' NOT NULL,
    version_no character varying(20) DEFAULT 'v1' NOT NULL,
    published_at timestamp without time zone,
    last_error character varying(512),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64) DEFAULT 'system' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_cache_invalidation_outbox_status CHECK (((status)::text = ANY ((ARRAY['PENDING', 'PUBLISHED', 'FAILED'])::text[])))
);

--

-- TABLE cache_invalidation_outbox (COMMENT)
COMMENT ON TABLE ecos_control.cache_invalidation_outbox IS '§2.9.8④ 缓存失效 Outbox：写路径事务内落行，后台中继投递 Redis 频道（至少一次、可重试）';

--

-- COLUMN cache_invalidation_outbox.payload_json (COMMENT)
COMMENT ON COLUMN ecos_control.cache_invalidation_outbox.payload_json IS '{env, domain, reason, objectType?, objectId?}；消费方按域/对象失效，禁携带业务内容';

--

-- demo_customer (TABLE)
CREATE TABLE ecos_control.demo_customer (
    id character varying(64) NOT NULL,
    name character varying(255),
    industry character varying(128),
    region character varying(64),
    level character varying(16),
    credit_score integer,
    status character varying(32) DEFAULT 'active',
    created_at timestamp without time zone DEFAULT now()
);

--

-- demo_invoice (TABLE)
CREATE TABLE ecos_control.demo_invoice (
    id character varying(64) NOT NULL,
    amount numeric(12,2),
    customer_id character varying(64),
    supplier_id character varying(64),
    status character varying(32) DEFAULT 'pending',
    created_at timestamp without time zone DEFAULT now()
);

--

-- demo_supplier (TABLE)
CREATE TABLE ecos_control.demo_supplier (
    id character varying(64) NOT NULL,
    name character varying(255),
    industry character varying(128),
    region character varying(64),
    level character varying(16),
    supply_capacity character varying(128),
    status character varying(32) DEFAULT 'active',
    created_at timestamp without time zone DEFAULT now()
);

--

-- dict_column (TABLE)
CREATE TABLE ecos_control.dict_column (
    id character varying(36) NOT NULL,
    table_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    type character varying(100) NOT NULL,
    length integer,
    precision_val integer,
    scale integer,
    nullable boolean DEFAULT true,
    primary_key boolean DEFAULT false,
    default_value character varying(500),
    description text,
    sort_order integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);

--

-- dict_table (TABLE)
CREATE TABLE ecos_control.dict_table (
    id character varying(36) NOT NULL,
    code character varying(200),
    name character varying(200) NOT NULL,
    name_zh character varying(200),
    schema_name character varying(200),
    description text,
    status character varying(32) DEFAULT 'DRAFT',
    source character varying(100),
    row_count bigint,
    storage_size character varying(50),
    owner character varying(100),
    tags text,
    created_by character varying(100),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);

--

-- ecos_agent (TABLE)
CREATE TABLE ecos_control.ecos_agent (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    model_provider character varying(64) DEFAULT 'deepseek',
    model_name character varying(128) DEFAULT 'deepseek-v4-flash',
    system_prompt text DEFAULT ''::text,
    tools text DEFAULT '[]'::text,
    knowledge text DEFAULT '[]'::text,
    status character varying(32) DEFAULT 'draft',
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);

--

-- ecos_alert_history (TABLE)
CREATE TABLE ecos_control.ecos_alert_history (
    id bigint NOT NULL,
    rule_name character varying(256),
    level character varying(32),
    message text,
    status character varying(32) DEFAULT 'OPEN' NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- ecos_alert_history_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.ecos_alert_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_alert_history_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.ecos_alert_history_id_seq OWNED BY ecos_control.ecos_alert_history.id;

--

-- ecos_audit_log (TABLE)
CREATE TABLE ecos_control.ecos_audit_log (
    id bigint NOT NULL,
    event_type character varying(128),
    user_id character varying(64),
    username character varying(64),
    operation character varying(128),
    action character varying(128),
    resource character varying(256),
    entity_type character varying(64),
    entity_id character varying(64),
    detail text,
    ip_address character varying(64),
    result character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    prev_hash character varying(64),
    curr_hash character varying(64),
    hash_algorithm character varying(16),
    audit_event_id character varying(64),
    change_request_id character varying(64),
    actor_id character varying(64),
    approver_ids text,
    domain character varying(64),
    object_type character varying(64),
    object_id character varying(64),
    risk_level character varying(16),
    git_repository character varying(64),
    git_commit_sha character varying(64),
    git_parent_sha character varying(64),
    content_hash character varying(128),
    target_environment character varying(32),
    approval_policy_version character varying(64),
    deployment_id character varying(64),
    before_hash character varying(128),
    after_hash character varying(128),
    apply_status character varying(32),
    verification_status character varying(32),
    trace_id character varying(64)
);

--

-- TABLE ecos_audit_log (COMMENT)
COMMENT ON TABLE ecos_control.ecos_audit_log IS '审计日志（Kafka 消费者幂等落库载体 + P1-3 哈希链）——附录A 第6条 getAuditLogDetail 契约载荷 prevHash/currentHash 的唯一数据面；V295 单源锚定补登（2026-10-29 dev 实跑真执行，列清单取库面实查）';

--

-- COLUMN ecos_audit_log.audit_event_id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_audit_log.audit_event_id IS 'R7 §2.9.4③ 治理审计事件业务标识（与自增 id 分离，供跨系统对账）';

--

-- COLUMN ecos_audit_log.risk_level (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_audit_log.risk_level IS '治理审计分级（L1~L4，映射见 common-api AuditLevel；不改既有 clearance_level）';

--

-- COLUMN ecos_audit_log.git_commit_sha (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_audit_log.git_commit_sha IS 'Git 归档腿提交号（Commit SHA ≠ 发布授权，授权见 PublishManifest）';

--

-- COLUMN ecos_audit_log.approval_policy_version (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_audit_log.approval_policy_version IS '审批/发布策略版本（可回溯当时按哪版规则放行）';

--

-- ecos_audit_log_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.ecos_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_audit_log_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.ecos_audit_log_id_seq OWNED BY ecos_control.ecos_audit_log.id;

--

-- ecos_business_scenario (TABLE)
CREATE TABLE ecos_control.ecos_business_scenario (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    business_goal text DEFAULT ''::text,
    department character varying(128),
    priority character varying(16) DEFAULT 'MEDIUM',
    status character varying(32) DEFAULT 'DRAFT',
    budget character varying(64),
    safety_index_target numeric(8,4),
    actual_safety_index numeric(8,4),
    metrics jsonb DEFAULT '{}'::jsonb,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system',
    update_by character varying(64) DEFAULT 'system',
    is_deleted smallint DEFAULT 0 NOT NULL,
    budget_amount numeric(18,2),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL
);

--

-- TABLE ecos_business_scenario (COMMENT)
COMMENT ON TABLE ecos_control.ecos_business_scenario IS '场景主表真身（workspace 唯一写入口落点；V123/V193/V315 另有两个只停写副本：ecos_ai / ecos_cognitive）';

--

-- COLUMN ecos_business_scenario.budget (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_business_scenario.budget IS 'RT#14/V344：人类可读展示列（历史中文金额原值），不参与任何计算。金额权威列 = budget_amount（numeric(18,2)，MC01）。新写入请只写 budget_amount。';

--

-- COLUMN ecos_business_scenario.budget_amount (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_business_scenario.budget_amount IS '预算金额（元，MC01 NUMERIC(18,2)）。V330 新增；由旧 budget 文本列清洗回填（「850万」→8500000）。旧列 budget 保留供存量读方过渡。';

--

-- ecos_cls_policy (TABLE)
CREATE TABLE ecos_control.ecos_cls_policy (
    id character varying(64) NOT NULL,
    policy_name character varying(128) NOT NULL,
    table_name character varying(128) NOT NULL,
    visible_cols text NOT NULL,
    blocked_cols text,
    role_id character varying(64),
    user_id character varying(64),
    priority integer DEFAULT 0,
    enabled boolean DEFAULT true,
    description text,
    created_by character varying(64),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    resource_id character varying(64),
    column_name character varying(128),
    mode character varying(16) DEFAULT 'deny' NOT NULL,
    allowed_columns_json text,
    scope_type character varying(16) DEFAULT 'ROLE' NOT NULL,
    scope_id character varying(64),
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- COLUMN ecos_cls_policy.resource_id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cls_policy.resource_id IS 'PMO-data10 资产驱动 CLS（与 table_name 双轨，老行 resource_id 为空走 table_name 兜底）';

--

-- ecos_cognitive_belief (TABLE)
CREATE TABLE ecos_control.ecos_cognitive_belief (
    id character varying(64) NOT NULL,
    variable_name character varying(128) NOT NULL,
    tenant_scope character varying(64),
    domain character varying(64) NOT NULL,
    distribution jsonb DEFAULT '[]'::jsonb NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    snapshot_version integer,
    is_manual_override boolean DEFAULT false NOT NULL,
    override_reason text,
    last_evidence_id character varying(64),
    status character varying(16) DEFAULT 'ACTIVE' NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system',
    update_by character varying(64) DEFAULT 'system',
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    distribution_json text DEFAULT '[]'::text NOT NULL,
    probability numeric(6,5),
    prob_kind character varying(30),
    calibration_ref character varying(100),
    model_ref character varying(100),
    scope_ref character varying(100),
    as_of_time timestamp without time zone,
    lifecycle_state character varying(30),
    mind_id character varying(36),
    scenario_id character varying(36),
    subject_type character varying(20),
    subject_id character varying(36)
);

--

-- TABLE ecos_cognitive_belief (COMMENT)
COMMENT ON TABLE ecos_control.ecos_cognitive_belief IS '不确定性判断表 — ADR-9 心智层 P 库: 有限离散概率分布+版本+人工覆写(原稿"信念"改称)';

--

-- COLUMN ecos_cognitive_belief.variable_name (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.variable_name IS '不可观测经营变量名';

--

-- COLUMN ecos_cognitive_belief.distribution (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.distribution IS '有限离散概率分布 [{"outcome","prob"}], prob 和=1';

--

-- COLUMN ecos_cognitive_belief.version (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.version IS '分布版本(新证据加权更新 +1)';

--

-- COLUMN ecos_cognitive_belief.snapshot_version (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.snapshot_version IS '时间回放预留(Phase 3 历史决策同参数重算)';

--

-- COLUMN ecos_cognitive_belief.is_manual_override (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.is_manual_override IS '人工覆写标记(专家干预优先)';

--

-- COLUMN ecos_cognitive_belief.last_evidence_id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.last_evidence_id IS '触发本次版本更新的证据引用(溯源)';

--

-- COLUMN ecos_cognitive_belief.version_no (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.version_no IS '乐观锁版本号 (DR07 规范)';

--

-- COLUMN ecos_cognitive_belief.distribution_json (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.distribution_json IS 'V333 补齐（D74）：BeliefStore 读端列；与 V191 真身同名同型（TEXT，MC02 合规形态）';

--

-- COLUMN ecos_cognitive_belief.lifecycle_state (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.lifecycle_state IS 'V333 补齐（D74）：读取端 CognitiveAssetLifecycleService.resolve 依赖；权威实例 = ecos_cognitive.ecos_cognitive_belief';

--

-- ecos_cognitive_evidence (TABLE)
CREATE TABLE ecos_control.ecos_cognitive_evidence (
    id character varying(64) NOT NULL,
    evidence_code character varying(128) NOT NULL,
    tenant_scope character varying(64),
    source_type character varying(32) NOT NULL,
    source_ref character varying(256),
    blob jsonb DEFAULT '{}'::jsonb NOT NULL,
    confidence numeric(5,4) DEFAULT 0.5 NOT NULL,
    is_conflict boolean DEFAULT false NOT NULL,
    refuting_evidence_ids jsonb DEFAULT '[]'::jsonb NOT NULL,
    effective_time timestamp without time zone,
    expire_time timestamp without time zone,
    status character varying(16) DEFAULT 'ACTIVE' NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system',
    update_by character varying(64) DEFAULT 'system',
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL
);

--

-- TABLE ecos_cognitive_evidence (COMMENT)
COMMENT ON TABLE ecos_control.ecos_cognitive_evidence IS '认知证据表 — ADR-9 心智层: 结构化证据(来源/可信度/冲突标记)';

--

-- COLUMN ecos_cognitive_evidence.source_type (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_evidence.source_type IS '来源类型 SYSTEM_DATA/NEWS/EXPERT/PIPELINE';

--

-- COLUMN ecos_cognitive_evidence.blob (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_evidence.blob IS '结构化事实载荷(事实/指标/数值/上下文)';

--

-- COLUMN ecos_cognitive_evidence.confidence (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_evidence.confidence IS '可信度 0~1 (系统数据0.95+ / 权威新闻~0.8 / 小道消息0.3~0.5)';

--

-- COLUMN ecos_cognitive_evidence.is_conflict (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_evidence.is_conflict IS '同事实多证据不一致→冲突待修正';

--

-- COLUMN ecos_cognitive_evidence.version_no (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_evidence.version_no IS '乐观锁版本号 (DR07 规范)';

--

-- ecos_cognitive_hypothesis (TABLE)
CREATE TABLE ecos_control.ecos_cognitive_hypothesis (
    id character varying(64) NOT NULL,
    hypothesis_code character varying(128) NOT NULL,
    tenant_scope character varying(64),
    statement text NOT NULL,
    domain character varying(64),
    metric_ref character varying(128),
    evidence_ids jsonb DEFAULT '[]'::jsonb NOT NULL,
    is_valid boolean DEFAULT true NOT NULL,
    invalid_at timestamp without time zone,
    invalid_reason text,
    status character varying(16) DEFAULT 'VALID' NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system',
    update_by character varying(64) DEFAULT 'system',
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    evidence_ids_json text,
    scenario_id character varying(64),
    mind_id character varying(36),
    subject_type character varying(32),
    subject_id character varying(64),
    reason text,
    confidence numeric(18,4),
    lifecycle_state character varying(32),
    old_status character varying(32)
);

--

-- TABLE ecos_cognitive_hypothesis (COMMENT)
COMMENT ON TABLE ecos_control.ecos_cognitive_hypothesis IS '认知假设表 — ADR-9 心智层 H 库: 假设/证据引用/失效监测';

--

-- COLUMN ecos_cognitive_hypothesis.statement (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_hypothesis.statement IS '假设陈述(业务语言)';

--

-- COLUMN ecos_cognitive_hypothesis.metric_ref (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_hypothesis.metric_ref IS '关联经营变量(与 belief.variable_name 对齐)';

--

-- COLUMN ecos_cognitive_hypothesis.evidence_ids (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_hypothesis.evidence_ids IS '支撑证据 id 列表(引用 ecos_cognitive_evidence.id)';

--

-- COLUMN ecos_cognitive_hypothesis.is_valid (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_hypothesis.is_valid IS '当前是否有效(监测命中失效→FALSE)';

--

-- COLUMN ecos_cognitive_hypothesis.version_no (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_hypothesis.version_no IS '乐观锁版本号 (DR07 规范)';

--

-- COLUMN ecos_cognitive_hypothesis.scenario_id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_hypothesis.scenario_id IS 'V327 补齐：场景维度假设列表（V296）读列；权威实例 = ecos_cognitive.ecos_cognitive_hypothesis';

--

-- ecos_cognitive_model (TABLE)
CREATE TABLE ecos_control.ecos_cognitive_model (
    id character varying(64) NOT NULL,
    model_id character varying(64) NOT NULL,
    model_type character varying(32) NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    features jsonb,
    backtest_score numeric(8,6),
    status character varying(16) DEFAULT 'active' NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system',
    update_by character varying(64) DEFAULT 'system',
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL
);

--

-- TABLE ecos_cognitive_model (COMMENT)
COMMENT ON TABLE ecos_control.ecos_cognitive_model IS '认知模型注册表 — ADR-8 模型资产落盘（推理结果不落盘）';

--

-- COLUMN ecos_cognitive_model.model_id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_model.model_id IS '模型标识';

--

-- COLUMN ecos_cognitive_model.model_type (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_model.model_type IS '模型类型 FORECAST/CAUSAL/SIMULATION/DECISION';

--

-- COLUMN ecos_cognitive_model.backtest_score (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_model.backtest_score IS '回测评分(0~1, 可选)';

--

-- COLUMN ecos_cognitive_model.domain (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_model.domain IS '多租户预留域 (DR08 规范)';

--

-- COLUMN ecos_cognitive_model.version_no (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_model.version_no IS '乐观锁版本号 (DR07 规范)';

--

-- ecos_cognitive_rule (TABLE)
CREATE TABLE ecos_control.ecos_cognitive_rule (
    id bigint NOT NULL,
    rule_name character varying(128) NOT NULL,
    rule_type character varying(32) NOT NULL,
    condition_expr text NOT NULL,
    action_config jsonb,
    priority integer DEFAULT 0,
    enabled boolean DEFAULT true,
    description text,
    created_by character varying(64),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);

--

-- TABLE ecos_cognitive_rule (COMMENT)
COMMENT ON TABLE ecos_control.ecos_cognitive_rule IS '认知规则引擎表 — 阈值/因果/优化规则定义';

--

-- COLUMN ecos_cognitive_rule.id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.id IS '主键，自增';

--

-- COLUMN ecos_cognitive_rule.rule_name (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.rule_name IS '规则名称';

--

-- COLUMN ecos_cognitive_rule.rule_type (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.rule_type IS '规则类型: THRESHOLD/CAUSAL/OPTIMIZATION';

--

-- COLUMN ecos_cognitive_rule.condition_expr (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.condition_expr IS '规则条件表达式';

--

-- COLUMN ecos_cognitive_rule.action_config (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.action_config IS '动作配置 (JSONB)';

--

-- COLUMN ecos_cognitive_rule.priority (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.priority IS '优先级，数值越大优先级越高';

--

-- COLUMN ecos_cognitive_rule.enabled (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.enabled IS '是否启用';

--

-- COLUMN ecos_cognitive_rule.description (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.description IS '规则描述';

--

-- COLUMN ecos_cognitive_rule.created_by (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.created_by IS '创建人';

--

-- COLUMN ecos_cognitive_rule.created_at (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.created_at IS '创建时间';

--

-- COLUMN ecos_cognitive_rule.updated_at (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.updated_at IS '更新时间';

--

-- ecos_cognitive_rule_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.ecos_cognitive_rule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_cognitive_rule_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.ecos_cognitive_rule_id_seq OWNED BY ecos_control.ecos_cognitive_rule.id;

--

-- ecos_cognitive_run_invalidation (TABLE)
CREATE TABLE ecos_control.ecos_cognitive_run_invalidation (
    id character varying(64) NOT NULL,
    event_id character varying(64) NOT NULL,
    hypothesis_id character varying(64) NOT NULL,
    run_id character varying(64) NOT NULL,
    auto_detected boolean DEFAULT true NOT NULL,
    superseded_at timestamp without time zone DEFAULT now() NOT NULL,
    detail text,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system',
    update_by character varying(64) DEFAULT 'system',
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_cognitive_run_invalidation (COMMENT)
COMMENT ON TABLE ecos_control.ecos_cognitive_run_invalidation IS '失效→作废推演结论关联留痕表 — ADR-9 心智状态（PMO-59 P3b T2）';

--

-- COLUMN ecos_cognitive_run_invalidation.event_id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_run_invalidation.event_id IS '触发事件 id（ecos.cognitive 事件 cog_evt_ 前缀）';

--

-- COLUMN ecos_cognitive_run_invalidation.hypothesis_id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_run_invalidation.hypothesis_id IS '失效假设主键';

--

-- COLUMN ecos_cognitive_run_invalidation.run_id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_run_invalidation.run_id IS '被作废推演 run 主键（ecos_scenario_run.id）';

--

-- COLUMN ecos_cognitive_run_invalidation.auto_detected (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_cognitive_run_invalidation.auto_detected IS '失效是否自动检测触发（false=人工兜底）';

--

-- ecos_decision (TABLE)
CREATE TABLE ecos_control.ecos_decision (
    id character varying(64) NOT NULL,
    category character varying(64) NOT NULL,
    scenario text,
    reasoning text,
    outcome character varying(128),
    confidence numeric(5,4),
    decision_maker character varying(64),
    valid_from timestamp without time zone,
    valid_until timestamp without time zone,
    metadata jsonb,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);

--

-- ecos_decision_approval (TABLE)
CREATE TABLE ecos_control.ecos_decision_approval (
    id character varying(64) NOT NULL,
    decision_id character varying(64) NOT NULL,
    approver character varying(64),
    level integer DEFAULT 1,
    status character varying(16) DEFAULT 'pending',
    comment text,
    created_at timestamp without time zone DEFAULT now()
);

--

-- ecos_decision_causal_link (TABLE)
CREATE TABLE ecos_control.ecos_decision_causal_link (
    id character varying(64) NOT NULL,
    source_decision_id character varying(64) NOT NULL,
    target_decision_id character varying(64) NOT NULL,
    relationship character varying(32) NOT NULL,
    weight numeric(5,4) DEFAULT 0.5,
    created_at timestamp without time zone DEFAULT now()
);

--

-- ecos_decision_exception (TABLE)
CREATE TABLE ecos_control.ecos_decision_exception (
    id character varying(64) NOT NULL,
    decision_id character varying(64) NOT NULL,
    reason text,
    approver character varying(64),
    status character varying(16) DEFAULT 'pending',
    created_at timestamp without time zone DEFAULT now()
);

--

-- ecos_decision_policy (TABLE)
CREATE TABLE ecos_control.ecos_decision_policy (
    id character varying(64) NOT NULL,
    name character varying(128) NOT NULL,
    category character varying(64),
    rules jsonb,
    version integer DEFAULT 1,
    status character varying(16) DEFAULT 'active',
    created_at timestamp without time zone DEFAULT now()
);

--

-- ecos_decision_precedent (TABLE)
CREATE TABLE ecos_control.ecos_decision_precedent (
    id character varying(64) NOT NULL,
    decision_id character varying(64) NOT NULL,
    similar_decision_id character varying(64) NOT NULL,
    similarity numeric(5,4) DEFAULT 0,
    note text,
    created_at timestamp without time zone DEFAULT now()
);

--

-- ecos_decision_record (TABLE)
CREATE TABLE ecos_control.ecos_decision_record (
    id character varying(64) NOT NULL,
    scenario_id character varying(64) NOT NULL,
    mind_id bigint,
    action_plan jsonb DEFAULT '[]'::jsonb NOT NULL,
    source_refs jsonb DEFAULT '[]'::jsonb NOT NULL,
    proposed_by character varying(64),
    accepted_by character varying(64),
    accepted_at timestamp without time zone,
    compliance_check jsonb DEFAULT '{}'::jsonb NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64) DEFAULT 'system' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_decision_record (COMMENT)
COMMENT ON TABLE ecos_control.ecos_decision_record IS '场景决策回执（策略端点可选落盘 + 推理输出 hash 溯源）';

--

-- COLUMN ecos_decision_record.action_plan (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_decision_record.action_plan IS '处置方案 JSON：[{action, justification, tradeoff_cost}]';

--

-- COLUMN ecos_decision_record.source_refs (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_decision_record.source_refs IS '推理输出 hash：[{ep, hash, mind_id, timestamp}]';

--

-- ecos_dq_execution_result (TABLE)
CREATE TABLE ecos_control.ecos_dq_execution_result (
    id character varying(64) NOT NULL,
    rule_id character varying(64) NOT NULL,
    passed boolean DEFAULT false NOT NULL,
    total_rows integer DEFAULT 0,
    failed_rows integer DEFAULT 0,
    error_details text,
    executed_at timestamp without time zone DEFAULT now()
);

--

-- TABLE ecos_dq_execution_result (COMMENT)
COMMENT ON TABLE ecos_control.ecos_dq_execution_result IS 'DQ规则执行结果表 — 记录每次规则执行的通过/失败情况';

--

-- COLUMN ecos_dq_execution_result.id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_dq_execution_result.id IS '执行记录主键';

--

-- COLUMN ecos_dq_execution_result.rule_id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_dq_execution_result.rule_id IS '关联的规则ID，对应 ecos_dq_rule.id';

--

-- COLUMN ecos_dq_execution_result.passed (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_dq_execution_result.passed IS '是否通过: true=全部通过, false=存在不满足的数据';

--

-- COLUMN ecos_dq_execution_result.total_rows (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_dq_execution_result.total_rows IS '总行数';

--

-- COLUMN ecos_dq_execution_result.failed_rows (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_dq_execution_result.failed_rows IS '不满足规则的行数';

--

-- COLUMN ecos_dq_execution_result.error_details (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_dq_execution_result.error_details IS '错误详情(JSON)，包含失败行的样例数据';

--

-- COLUMN ecos_dq_execution_result.executed_at (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_dq_execution_result.executed_at IS '执行时间';

--

-- ecos_dq_issue (TABLE)
CREATE TABLE ecos_control.ecos_dq_issue (
    id bigint NOT NULL,
    rule_id bigint NOT NULL,
    asset_id character varying(255) DEFAULT '' NOT NULL,
    description text DEFAULT ''::text,
    status character varying(32) DEFAULT 'open' NOT NULL,
    severity character varying(32) DEFAULT 'HIGH' NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    resolved_at timestamp without time zone
);

--

-- ecos_dq_issue_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.ecos_dq_issue_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_dq_issue_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.ecos_dq_issue_id_seq OWNED BY ecos_control.ecos_dq_issue.id;

--

-- ecos_dq_rule (TABLE)
CREATE TABLE ecos_control.ecos_dq_rule (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    rule_type character varying(64) DEFAULT 'NOT_NULL' NOT NULL,
    config_json text DEFAULT '{}'::text,
    severity character varying(32) DEFAULT 'HIGH' NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    tenant_id character varying(32)
);

--

-- ecos_dq_rule_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.ecos_dq_rule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_dq_rule_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.ecos_dq_rule_id_seq OWNED BY ecos_control.ecos_dq_rule.id;

--

-- ecos_function_audit_log (TABLE)
CREATE TABLE ecos_control.ecos_function_audit_log (
    id bigint NOT NULL,
    function_name character varying(256),
    expression text NOT NULL,
    entity_name character varying(128),
    result_value text,
    execution_time_ms integer,
    caller_id character varying(64),
    status character varying(16) NOT NULL,
    error_message text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- ecos_function_audit_log_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.ecos_function_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_function_audit_log_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.ecos_function_audit_log_id_seq OWNED BY ecos_control.ecos_function_audit_log.id;

--

-- ecos_git_repo (TABLE)
CREATE TABLE ecos_control.ecos_git_repo (
    id character varying(64) NOT NULL,
    repository_id character varying(64) NOT NULL,
    repo_name character varying(128) NOT NULL,
    repo_root character varying(256),
    branch_default character varying(64) DEFAULT 'main' NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE' NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    include_paths text,
    exclude_paths text,
    owner_org_id character varying(64)
);

--

-- TABLE ecos_git_repo (COMMENT)
COMMENT ON TABLE ecos_control.ecos_git_repo IS 'Git 仓库注册表（§2.9.5①/DQ-2 裁决定名；td_git_repository 作废）。服务端持久化注册面，repositoryId 由本表登记；仓库根路径单一事实源仍为 sys_config ecos_git_repo_root（GitRepoRootResolver 解析＋路径穿越守卫）。V300 建表';

--

-- ecos_glossary_term (TABLE)
CREATE TABLE ecos_control.ecos_glossary_term (
    id bigint NOT NULL,
    code character varying(64) DEFAULT '',
    name character varying(255) NOT NULL,
    definition text DEFAULT ''::text,
    domain character varying(128) DEFAULT '',
    owner character varying(128) DEFAULT '',
    status character varying(32) DEFAULT 'DRAFT',
    created_by character varying(128) DEFAULT '',
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    domain_id character varying(50),
    tenant_id character varying(32),
    term_type character varying(32) DEFAULT 'CONCEPT',
    aliases text[] DEFAULT '{}'::text[],
    object_type_id character varying(64),
    parent_term_id bigint,
    version integer DEFAULT 1,
    examples text[] DEFAULT '{}'::text[],
    tags text[] DEFAULT '{}'::text[],
    is_primary boolean DEFAULT false NOT NULL
);

--

-- TABLE ecos_glossary_term (COMMENT)
COMMENT ON TABLE ecos_control.ecos_glossary_term IS '术语库 — Glossary terms';

--

-- COLUMN ecos_glossary_term.id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_glossary_term.id IS '主键，自增';

--

-- COLUMN ecos_glossary_term.code (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_glossary_term.code IS '术语编码';

--

-- COLUMN ecos_glossary_term.name (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_glossary_term.name IS '术语名称';

--

-- COLUMN ecos_glossary_term.definition (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_glossary_term.definition IS '术语定义';

--

-- COLUMN ecos_glossary_term.domain (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_glossary_term.domain IS '所属领域';

--

-- COLUMN ecos_glossary_term.owner (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_glossary_term.owner IS '负责人';

--

-- COLUMN ecos_glossary_term.status (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_glossary_term.status IS '状态: DRAFT/REVIEW/PUBLISHED/DEPRECATED';

--

-- COLUMN ecos_glossary_term.created_by (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_glossary_term.created_by IS '创建人';

--

-- COLUMN ecos_glossary_term.created_at (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_glossary_term.created_at IS '创建时间';

--

-- COLUMN ecos_glossary_term.updated_at (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_glossary_term.updated_at IS '更新时间';

--

-- COLUMN ecos_glossary_term.term_type (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_glossary_term.term_type IS '词条分类 ENTITY/RELATION/METRIC/FUNCTION/CONCEPT（与 public 副本同义，D15 补齐）';

--

-- COLUMN ecos_glossary_term.is_primary (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_glossary_term.is_primary IS '是否实体主术语（与 public 副本同义，D15 补齐）';

--

-- ecos_glossary_term_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.ecos_glossary_term_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_glossary_term_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.ecos_glossary_term_id_seq OWNED BY ecos_control.ecos_glossary_term.id;

--

-- ecos_goal_tracking (TABLE)
CREATE TABLE ecos_control.ecos_goal_tracking (
    id character varying(36) NOT NULL,
    goal_id bigint NOT NULL,
    progress integer NOT NULL,
    actual_value numeric(18,2),
    note text,
    recorded_at date DEFAULT CURRENT_DATE NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT ecos_goal_tracking_progress_check CHECK (((progress >= 0) AND (progress <= 100)))
);

--

-- ecos_interface_ref (TABLE)
CREATE TABLE ecos_control.ecos_interface_ref (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    interface_type character varying(32) NOT NULL,
    endpoint character varying(512) NOT NULL,
    method character varying(16),
    timeout_ms integer DEFAULT 3000 NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64) DEFAULT 'system' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_interface_ref (COMMENT)
COMMENT ON TABLE ecos_control.ecos_interface_ref IS '场景绑定的外部接口引用真表';

--

-- ecos_marketplace_access_request (TABLE)
CREATE TABLE ecos_control.ecos_marketplace_access_request (
    id bigint NOT NULL,
    asset_id bigint NOT NULL,
    reason text DEFAULT ''::text,
    applicant character varying(128) DEFAULT '',
    status character varying(32) DEFAULT 'PENDING',
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);

--

-- TABLE ecos_marketplace_access_request (COMMENT)
COMMENT ON TABLE ecos_control.ecos_marketplace_access_request IS '数据市场访问申请记录';

--

-- COLUMN ecos_marketplace_access_request.id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_marketplace_access_request.id IS '主键，自增';

--

-- COLUMN ecos_marketplace_access_request.asset_id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_marketplace_access_request.asset_id IS '关联资产ID';

--

-- COLUMN ecos_marketplace_access_request.reason (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_marketplace_access_request.reason IS '申请理由';

--

-- COLUMN ecos_marketplace_access_request.applicant (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_marketplace_access_request.applicant IS '申请人';

--

-- COLUMN ecos_marketplace_access_request.status (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_marketplace_access_request.status IS '状态: PENDING/APPROVED/REJECTED';

--

-- COLUMN ecos_marketplace_access_request.created_at (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_marketplace_access_request.created_at IS '创建时间';

--

-- ecos_marketplace_access_request_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.ecos_marketplace_access_request_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_marketplace_access_request_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.ecos_marketplace_access_request_id_seq OWNED BY ecos_control.ecos_marketplace_access_request.id;

--

-- ecos_marketplace_asset (TABLE)
CREATE TABLE ecos_control.ecos_marketplace_asset (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    category character varying(64) DEFAULT '',
    owner character varying(128) DEFAULT '',
    rating numeric(3,2) DEFAULT 0.0,
    popularity integer DEFAULT 0,
    status character varying(32) DEFAULT 'PUBLISHED',
    created_at timestamp without time zone DEFAULT now(),
    ontology_entity_id character varying(128) DEFAULT NULL
);

--

-- TABLE ecos_marketplace_asset (COMMENT)
COMMENT ON TABLE ecos_control.ecos_marketplace_asset IS '数据市场 — marketplace assets';

--

-- COLUMN ecos_marketplace_asset.id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.id IS '主键，自增';

--

-- COLUMN ecos_marketplace_asset.name (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.name IS '资产名称';

--

-- COLUMN ecos_marketplace_asset.description (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.description IS '资产描述';

--

-- COLUMN ecos_marketplace_asset.category (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.category IS '分类: 数据集/AI模型/API/报表';

--

-- COLUMN ecos_marketplace_asset.owner (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.owner IS '所属部门/所有者';

--

-- COLUMN ecos_marketplace_asset.rating (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.rating IS '评分 0.00~5.00';

--

-- COLUMN ecos_marketplace_asset.popularity (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.popularity IS '综合热度值';

--

-- COLUMN ecos_marketplace_asset.status (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.status IS '状态: DRAFT/PUBLISHED/DEPRECATED';

--

-- COLUMN ecos_marketplace_asset.created_at (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.created_at IS '创建时间';

--

-- COLUMN ecos_marketplace_asset.ontology_entity_id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.ontology_entity_id IS '关联的本体实体ID';

--

-- ecos_marketplace_asset_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.ecos_marketplace_asset_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_marketplace_asset_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.ecos_marketplace_asset_id_seq OWNED BY ecos_control.ecos_marketplace_asset.id;

--

-- ecos_mask_strategy (TABLE)
CREATE TABLE ecos_control.ecos_mask_strategy (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    strategy_code character varying(32) NOT NULL,
    keep_prefix smallint,
    keep_suffix smallint,
    amount_bucket character varying(32),
    apply_channels character varying(64),
    enabled smallint DEFAULT 1 NOT NULL,
    comment character varying(512),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_mask_strategy_code CHECK (((strategy_code)::text = ANY ((ARRAY['ID_CARD', 'PHONE', 'EMAIL', 'BANK_CARD', 'AMOUNT', 'ADDRESS', 'GENERAL', 'none'])::text[])))
);

--

-- TABLE ecos_mask_strategy (COMMENT)
COMMENT ON TABLE ecos_control.ecos_mask_strategy IS '脱敏策略字典（§4.4.2 第 6 行）：strategy_code 值域 = DataMaskingService.ruleForDataType 实测 7 类 + none 显式不脱敏；列清单单源 = 详设 §4.4.3 末段';

--

-- COLUMN ecos_mask_strategy.strategy_code (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_mask_strategy.strategy_code IS '策略码封闭枚举：ID_CARD/PHONE/EMAIL/BANK_CARD/AMOUNT/ADDRESS/GENERAL/none（D1：设计"8 类"按代码实测含 none 共 8 值立 CHECK）';

--

-- COLUMN ecos_mask_strategy.apply_channels (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_mask_strategy.apply_channels IS '应用通道绑定，`+` 连接（page/export/ai，对应 DecisionBundle.Purpose 三值）；NULL=全通道适用';

--

-- ecos_object_attachment (TABLE)
CREATE TABLE ecos_control.ecos_object_attachment (
    id character varying(50) NOT NULL,
    object_id character varying(100) NOT NULL,
    entity_code character varying(100) NOT NULL,
    file_name character varying(500) NOT NULL,
    file_path character varying(1000) NOT NULL,
    file_size bigint,
    mime_type character varying(200),
    version_no integer DEFAULT 1,
    uploaded_by character varying(100),
    created_at timestamp without time zone DEFAULT now()
);

--

-- ecos_object_links (TABLE)
CREATE TABLE ecos_control.ecos_object_links (
    id character varying(64) DEFAULT (gen_random_uuid())::text NOT NULL,
    source_id character varying(64) NOT NULL,
    target_id character varying(64) NOT NULL,
    relation_code character varying(128) NOT NULL,
    created_at timestamp without time zone DEFAULT now()
);

--

-- ecos_object_relationship (TABLE)
CREATE TABLE ecos_control.ecos_object_relationship (
    id character varying(50) NOT NULL,
    source_object_id character varying(100) NOT NULL,
    target_object_id character varying(100) NOT NULL,
    source_entity_code character varying(100) NOT NULL,
    target_entity_code character varying(100) NOT NULL,
    relationship_code character varying(100) NOT NULL,
    relationship_type character varying(50) NOT NULL,
    properties jsonb,
    created_at timestamp without time zone DEFAULT now()
);

--

-- ecos_object_state_machine (TABLE)
CREATE TABLE ecos_control.ecos_object_state_machine (
    id character varying(50) NOT NULL,
    entity_code character varying(100) NOT NULL,
    from_status character varying(50) NOT NULL,
    to_status character varying(50) NOT NULL,
    transition_code character varying(100) NOT NULL,
    transition_name character varying(200),
    require_role character varying(200),
    guard_rule text,
    side_effect text,
    sort_order integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now()
);

--

-- ecos_object_timeline (TABLE)
CREATE TABLE ecos_control.ecos_object_timeline (
    id character varying(50) NOT NULL,
    object_id character varying(100) NOT NULL,
    entity_code character varying(100) NOT NULL,
    event_type character varying(100) NOT NULL,
    event_summary character varying(500),
    actor character varying(100),
    details jsonb,
    created_at timestamp without time zone DEFAULT now()
);

--

-- ecos_ontology_action (TABLE)
CREATE TABLE ecos_control.ecos_ontology_action (
    id character varying(64) NOT NULL,
    entity_id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    action_type character varying(64) DEFAULT 'CUSTOM' NOT NULL,
    rule_json text DEFAULT ''::text,
    strategy character varying(255) DEFAULT '',
    status character varying(32) DEFAULT 'ACTIVE',
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    is_deleted smallint DEFAULT 0 NOT NULL,
    update_by character varying(128),
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- ecos_ontology_entity (TABLE)
CREATE TABLE ecos_control.ecos_ontology_entity (
    id character varying(64) NOT NULL,
    ontology_id character varying(64) NOT NULL,
    code character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    entity_type character varying(64) DEFAULT 'MASTER',
    sort_order integer DEFAULT 1,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    tenant_id character varying(32),
    domain_id character varying(50),
    is_deleted smallint DEFAULT 0 NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE' NOT NULL,
    update_by character varying(128),
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- ecos_ontology_property (TABLE)
CREATE TABLE ecos_control.ecos_ontology_property (
    id character varying(64) NOT NULL,
    entity_id character varying(64) NOT NULL,
    code character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    property_type character varying(64) DEFAULT 'STRING',
    required_flag integer DEFAULT 0,
    searchable_flag integer DEFAULT 0,
    sort_order integer DEFAULT 1,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    function_type character varying(32),
    function_expression text,
    is_deleted smallint DEFAULT 0 NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE' NOT NULL,
    update_by character varying(128),
    unique_flag integer DEFAULT 0 NOT NULL,
    description text,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- COLUMN ecos_ontology_property.function_type (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_ontology_property.function_type IS '计算属性类型: EXPRESSION(同实体运算) / AGGREGATION(跨link聚合) / LOOKUP(简单link取值)';

--

-- COLUMN ecos_ontology_property.function_expression (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_ontology_property.function_expression IS '计算表达式文本';

--

-- COLUMN ecos_ontology_property.is_deleted (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_ontology_property.is_deleted IS 'V327 补齐：读端 WHERE is_deleted=0 依赖；该镜像属 V3 时代遗留，权威本体域读端在 ecos_ontology schema';

--

-- ecos_ontology_relationship (TABLE)
CREATE TABLE ecos_control.ecos_ontology_relationship (
    id character varying(64) NOT NULL,
    source_entity_id character varying(64) NOT NULL,
    target_entity_id character varying(64) NOT NULL,
    code character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    relationship_type character varying(64) DEFAULT 'ONE_TO_MANY',
    created_at timestamp without time zone DEFAULT now(),
    is_deleted smallint DEFAULT 0 NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE' NOT NULL,
    update_by character varying(128),
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- ecos_pareto_problem (TABLE)
CREATE TABLE ecos_control.ecos_pareto_problem (
    id character varying(36) NOT NULL,
    problem_name character varying(255) DEFAULT '' NOT NULL,
    num_objectives integer NOT NULL,
    num_variables integer NOT NULL,
    population_size integer NOT NULL,
    generations integer NOT NULL,
    front_size integer DEFAULT 0 NOT NULL,
    solutions_json text DEFAULT '[]'::text NOT NULL,
    elapsed_ms bigint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_by character varying(100),
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL
);

--

-- TABLE ecos_pareto_problem (COMMENT)
COMMENT ON TABLE ecos_control.ecos_pareto_problem IS 'Pareto 多目标优化问题登记与结果快照（D106 落地；solutions_json 存完整前沿，便于 /result/{id} 直取）';

--

-- COLUMN ecos_pareto_problem.solutions_json (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_pareto_problem.solutions_json IS 'Pareto 前沿解集 JSON 数组：[{"variables":{"x1":..},"objectives":{"f1":..}}]';

--

-- ecos_pipeline_edge (TABLE)
CREATE TABLE ecos_control.ecos_pipeline_edge (
    id character varying(64) NOT NULL,
    definition_id character varying(64) NOT NULL,
    from_node_id character varying(64) NOT NULL,
    to_node_id character varying(64) NOT NULL,
    created_at timestamp without time zone DEFAULT now()
);

--

-- ecos_pipeline_node (TABLE)
CREATE TABLE ecos_control.ecos_pipeline_node (
    id character varying(64) NOT NULL,
    definition_id character varying(64) NOT NULL,
    node_id character varying(64) NOT NULL,
    type character varying(64) DEFAULT 'TRANSFORM_SQL' NOT NULL,
    config jsonb DEFAULT '{}'::jsonb,
    position_x integer DEFAULT 0,
    position_y integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    depends_on jsonb DEFAULT '[]'::jsonb
);

--

-- TABLE ecos_pipeline_node (COMMENT)
COMMENT ON TABLE ecos_control.ecos_pipeline_node IS 'Pipeline 节点表 — DAG 中的执行节点';

--

-- COLUMN ecos_pipeline_node.id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_pipeline_node.id IS '节点主键';

--

-- COLUMN ecos_pipeline_node.definition_id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_pipeline_node.definition_id IS '所属 Pipeline 定义 ID，关联 ecos_pipeline_definition.id';

--

-- COLUMN ecos_pipeline_node.node_id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_pipeline_node.node_id IS '前端节点标识';

--

-- COLUMN ecos_pipeline_node.type (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_pipeline_node.type IS '节点类型: SOURCE_JDBC / TRANSFORM_SQL / OUTPUT_OBJECT';

--

-- COLUMN ecos_pipeline_node.config (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_pipeline_node.config IS '节点配置(JSON): SQL语句、JDBC连接信息等';

--

-- COLUMN ecos_pipeline_node.position_x (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_pipeline_node.position_x IS '画布 X 坐标';

--

-- COLUMN ecos_pipeline_node.position_y (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_pipeline_node.position_y IS '画布 Y 坐标';

--

-- COLUMN ecos_pipeline_node.created_at (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_pipeline_node.created_at IS '创建时间';

--

-- COLUMN ecos_pipeline_node.updated_at (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_pipeline_node.updated_at IS '更新时间';

--

-- COLUMN ecos_pipeline_node.depends_on (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_pipeline_node.depends_on IS '依赖的节点ID列表（JSON数组），用于拓扑排序';

--

-- ecos_provenance_entry (TABLE)
CREATE TABLE ecos_control.ecos_provenance_entry (
    id character varying(64) NOT NULL,
    entity_type character varying(32) NOT NULL,
    entity_id character varying(64) NOT NULL,
    source_type character varying(32),
    source_ref text,
    agent character varying(64),
    activity character varying(64),
    "timestamp" timestamp without time zone DEFAULT now()
);

--

-- ecos_rls_policy (TABLE)
CREATE TABLE ecos_control.ecos_rls_policy (
    id character varying(64) NOT NULL,
    policy_name character varying(128) NOT NULL,
    table_name character varying(128) NOT NULL,
    filter_expr character varying(512) NOT NULL,
    role_id character varying(64),
    user_id character varying(64),
    priority integer DEFAULT 0,
    enabled boolean DEFAULT true,
    description text,
    created_by character varying(64),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    resource_id character varying(64),
    predicate_template character varying(1000),
    bindings_json text,
    source_kind character varying(32) DEFAULT 'legacy_text' NOT NULL,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- COLUMN ecos_rls_policy.resource_id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_rls_policy.resource_id IS 'PMO-data10 资产驱动 RLS（兼容 table_name）';

--

-- ecos_runtime_alert_record (TABLE)
CREATE TABLE ecos_control.ecos_runtime_alert_record (
    id character varying(36) NOT NULL,
    rule_code character varying(64) NOT NULL,
    severity character varying(16) NOT NULL,
    status character varying(16) DEFAULT 'open' NOT NULL,
    title character varying(255) NOT NULL,
    detail_json text,
    source_module character varying(64),
    trace_id character varying(64),
    ack_by character varying(36),
    ack_time timestamp without time zone,
    close_time timestamp without time zone,
    occurred_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    tenant_id character varying(36)
);

--

-- ecos_runtime_alert_rule (TABLE)
CREATE TABLE ecos_control.ecos_runtime_alert_rule (
    id character varying(36) NOT NULL,
    rule_code character varying(64) NOT NULL,
    metric_key character varying(128) NOT NULL,
    severity character varying(16) NOT NULL,
    threshold_num numeric(18,4),
    window_sec integer DEFAULT 300 NOT NULL,
    upgrade_min smallint,
    enabled smallint DEFAULT 1 NOT NULL,
    channel character varying(32) DEFAULT 'log' NOT NULL,
    webhook_url character varying(512),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(36),
    update_by character varying(36),
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    tenant_id character varying(36)
);

--

-- ecos_runtime_audit_retry (TABLE)
CREATE TABLE ecos_control.ecos_runtime_audit_retry (
    id character varying(36) NOT NULL,
    event_type character varying(64) NOT NULL,
    payload text NOT NULL,
    attempts smallint DEFAULT 0 NOT NULL,
    next_retry_at timestamp without time zone NOT NULL,
    last_error character varying(1000),
    status character varying(16) DEFAULT 'pending' NOT NULL,
    trace_id character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    tenant_id character varying(36),
    create_by character varying(100),
    update_by character varying(100)
);

--

-- ecos_runtime_event_dlq (TABLE)
CREATE TABLE ecos_control.ecos_runtime_event_dlq (
    id character varying(36) NOT NULL,
    topic character varying(128) NOT NULL,
    dlt_topic character varying(160) NOT NULL,
    payload text NOT NULL,
    error_message character varying(1000),
    attempts smallint DEFAULT 0 NOT NULL,
    first_seen_at timestamp without time zone NOT NULL,
    replayed_at timestamp without time zone,
    status character varying(16) DEFAULT 'pending' NOT NULL,
    trace_id character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    tenant_id character varying(36),
    create_by character varying(100),
    update_by character varying(100)
);

--

-- ecos_scenario_asset_binding (TABLE)
CREATE TABLE ecos_control.ecos_scenario_asset_binding (
    id character varying(36) NOT NULL,
    scenario_id character varying(36) NOT NULL,
    binding_type character varying(32) NOT NULL,
    target_ref character varying(255),
    target_id character varying(64),
    target_type character varying(32),
    remark text,
    is_island smallint DEFAULT 0 NOT NULL,
    island_reason character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64) DEFAULT 'system' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    CONSTRAINT ck_scen_bind_binding_type CHECK (((binding_type)::text = ANY ((ARRAY['DATASET', 'OBJECT_TYPE', 'KNOWLEDGE_BASE', 'AI_AGENT', 'SECURITY_POLICY', 'INTERFACE'])::text[])))
);

--

-- TABLE ecos_scenario_asset_binding (COMMENT)
COMMENT ON TABLE ecos_control.ecos_scenario_asset_binding IS '场景六类绑定表（binding_type 六值 CHECK 单列权威；旧 ecos_scenario_binding 停写不删）';

--

-- COLUMN ecos_scenario_asset_binding.is_island (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_scenario_asset_binding.is_island IS '孤岛标记（保存期打标，R-26① DRAFT 容忍；激活期 409 在服务层，DDL 不承载拒绝逻辑）';

--

-- ecos_scenario_binding (TABLE)
CREATE TABLE ecos_control.ecos_scenario_binding (
    id character varying(64) NOT NULL,
    scenario_id character varying(64) NOT NULL,
    binding_type character varying(32) NOT NULL,
    target_ref character varying(255) NOT NULL,
    remark text DEFAULT ''::text,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system',
    update_by character varying(64) DEFAULT 'system',
    is_deleted smallint DEFAULT 0 NOT NULL,
    target_id character varying(64),
    target_type character varying(32),
    CONSTRAINT chk_binding_target_type CHECK (((target_type IS NULL) OR ((target_type)::text = ANY ((ARRAY['DATASOURCE', 'ONTOLOGY_ENTITY', 'KNOWLEDGE_ARTICLE', 'AGENT_PROFILE', 'SECURITY_POLICY', 'INTERFACE_REF'])::text[]))))
);

--

-- COLUMN ecos_scenario_binding.target_id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_scenario_binding.target_id IS 'v2.0 真 PG 主键（替代 target_ref 字符串别名；target_ref 保留只读兼容）';

--

-- COLUMN ecos_scenario_binding.target_type (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_scenario_binding.target_type IS '真资源类型：DATASOURCE|ONTOLOGY_ENTITY|KNOWLEDGE_ARTICLE|AGENT_PROFILE|SECURITY_POLICY|INTERFACE_REF';

--

-- ecos_scenario_binding_edge (TABLE)
CREATE TABLE ecos_control.ecos_scenario_binding_edge (
    id character varying(36) NOT NULL,
    scenario_id character varying(36) NOT NULL,
    source_binding_id character varying(36) NOT NULL,
    target_binding_id character varying(36) NOT NULL,
    link_type character varying(16) NOT NULL,
    source_contract character varying(128) NOT NULL,
    remark text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64) DEFAULT 'system' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    CONSTRAINT chk_scen_link_link_type CHECK (((link_type)::text = ANY ((ARRAY['MAPPING', 'EXTRACTION', 'COGNITION', 'GOVERN', 'EXPOSE'])::text[]))),
    CONSTRAINT chk_scen_link_no_placeholder CHECK (((source_contract)::text !~~ 'placeholder-%'::text))
);

--

-- TABLE ecos_scenario_binding_edge (COMMENT)
COMMENT ON TABLE ecos_control.ecos_scenario_binding_edge IS '场景绑定关系边表（六类三层有向边，§0.6.2；占位契约 CHECK 禁入；旧表停写不删）';

--

-- COLUMN ecos_scenario_binding_edge.source_contract (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_scenario_binding_edge.source_contract IS '跨工作台契约引用（如 ecos_entity_table_mapping.id），§0.6.2.2 必带；禁 placeholder- 前缀（X-48）';

--

-- ecos_scenario_binding_link (TABLE)
CREATE TABLE ecos_control.ecos_scenario_binding_link (
    id character varying(64) NOT NULL,
    scenario_id character varying(64) NOT NULL,
    source_binding_id character varying(64) NOT NULL,
    target_binding_id character varying(64) NOT NULL,
    link_type character varying(16) NOT NULL,
    source_contract character varying(128),
    remark text DEFAULT ''::text,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64) DEFAULT 'system' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    CONSTRAINT chk_bsl_link_type CHECK (((link_type)::text = ANY ((ARRAY['MAPPING', 'EXTRACTION', 'COGNITION', 'GOVERN', 'EXPOSE'])::text[])))
);

--

-- TABLE ecos_scenario_binding_link (COMMENT)
COMMENT ON TABLE ecos_control.ecos_scenario_binding_link IS '场景绑定关系边：六类资源之外的有向关系（§0.6.2）';

--

-- COLUMN ecos_scenario_binding_link.link_type (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_scenario_binding_link.link_type IS 'MAPPING | EXTRACTION | COGNITION | GOVERN | EXPOSE';

--

-- COLUMN ecos_scenario_binding_link.source_contract (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_scenario_binding_link.source_contract IS '跨工作台契约引用（如 ecos_entity_table_mapping.id），§0.6.2.2 必带';

--

-- ecos_scenario_blueprint (TABLE)
CREATE TABLE ecos_control.ecos_scenario_blueprint (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    scenario_id character varying(36) NOT NULL,
    version integer NOT NULL,
    status character varying(24) DEFAULT 'DRAFT' NOT NULL,
    goal_statement text,
    goal_success_text text,
    scope_json_text text,
    dependencies_text text,
    capabilities_text text,
    gaps_text text,
    risks_text text,
    tests_text text,
    content_json text,
    artifact_digest character varying(120),
    approval_required smallint DEFAULT 0 NOT NULL,
    approval_ref character varying(100),
    context_digest character varying(120),
    release_artifact_ref character varying(200),
    released_time timestamp without time zone,
    trace_id character varying(64),
    actor character varying(100),
    transition_reason text,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_scenario_blueprint (COMMENT)
COMMENT ON TABLE ecos_control.ecos_scenario_blueprint IS '场景蓝图权威载体（补充设计 v1.0 §6）：workspace 属主，唯一写入面；ai/cognitive 域禁复制副本（§6.1/§17 捷径禁令 2）；发布版本不可变，RUN 运行回链 version+context_digest（SUP-BP-02/03）';

--

-- COLUMN ecos_scenario_blueprint.version (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_scenario_blueprint.version IS '蓝图版本号（应用侧递增，非乐观锁 version_no）；RELEASED 后该行 content_json/artifact_digest 只读，更新 = version+1 新行 + 旧行 DEPRECATED（§6.6 发布不可变）';

--

-- ecos_scenario_canvas_layout (TABLE)
CREATE TABLE ecos_control.ecos_scenario_canvas_layout (
    id character varying(36) NOT NULL,
    scenario_id character varying(36) NOT NULL,
    layout_json text NOT NULL,
    layout_version integer DEFAULT 1 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64) DEFAULT 'system' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL
);

--

-- TABLE ecos_scenario_canvas_layout (COMMENT)
COMMENT ON TABLE ecos_control.ecos_scenario_canvas_layout IS '沙盘画布布局表（React Flow 序列化 TEXT 化；乐观锁 layout_version 与基线 version_no 双列并存语义分离；旧表停写不删）';

--

-- COLUMN ecos_scenario_canvas_layout.layout_version (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_scenario_canvas_layout.layout_version IS '画布编辑乐观锁（防并发覆盖，业务语义）';

--

-- COLUMN ecos_scenario_canvas_layout.version_no (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_scenario_canvas_layout.version_no IS 'DR07 记录行基线版本列（与 layout_version 语义无关，D 章声明）';

--

-- ecos_scenario_decision_record (TABLE)
CREATE TABLE ecos_control.ecos_scenario_decision_record (
    id character varying(36) NOT NULL,
    scenario_id character varying(36) NOT NULL,
    mind_id character varying(36),
    forecast_run_id character varying(36),
    run_mode character varying(16),
    owner_user_id character varying(36) NOT NULL,
    due_date date,
    approver_user_id character varying(36) NOT NULL,
    expected_impact numeric(18,2),
    control_metric character varying(128) NOT NULL,
    action_plan_json text,
    source_refs_json text,
    compliance_verdict character varying(16),
    compliance_detail_json text,
    proposed_by character varying(64),
    accepted_by character varying(64),
    accepted_at timestamp without time zone,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64) DEFAULT 'system' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    CONSTRAINT ck_scen_dec_run_mode CHECK (((run_mode IS NULL) OR ((run_mode)::text = ANY ((ARRAY['FORMAL', 'SANDBOX'])::text[]))))
);

--

-- TABLE ecos_scenario_decision_record (COMMENT)
COMMENT ON TABLE ecos_control.ecos_scenario_decision_record IS '场景决策回执表（R-30①+②：场景侧记录台账，非第二套决策底座；底座唯一在 cognitive 经 DecisionGateway；FC-04 五必填实列）';

--

-- ecos_scenario_definition (TABLE)
CREATE TABLE ecos_control.ecos_scenario_definition (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    business_goal text,
    department character varying(128),
    priority character varying(16) DEFAULT 'MEDIUM' NOT NULL,
    status character varying(32) DEFAULT 'DRAFT' NOT NULL,
    budget numeric(18,2),
    budget_currency character varying(8),
    safety_index_target numeric(8,4),
    actual_safety_index numeric(8,4),
    metrics_json text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64) DEFAULT 'system' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    CONSTRAINT ck_scen_def_priority CHECK (((priority)::text = ANY ((ARRAY['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'])::text[]))),
    CONSTRAINT ck_scen_def_status CHECK (((status)::text = ANY ((ARRAY['DRAFT', 'ACTIVE', 'COMPLETED', 'SUSPENDED'])::text[])))
);

--

-- TABLE ecos_scenario_definition (COMMENT)
COMMENT ON TABLE ecos_control.ecos_scenario_definition IS '业务场景主表（MC/DR 合规重建；旧 public.ecos_business_scenario 停写不删，对账走 V213 v_legacy_* 视图）';

--

-- COLUMN ecos_scenario_definition.status (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_scenario_definition.status IS '值域按 V123 注释四态定版；服务端状态机（ScenarioStatusMachine, F07-07）为权威迁移校验，若终态值域扩展随 R-24/R-25 后续批以附加 CHECK 演进';

--

-- COLUMN ecos_scenario_definition.budget (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_scenario_definition.budget IS '预算金额，NUMERIC(18,2)（ST03-A）；旧 VARCHAR 串值不做自动解析，存量换算待授权';

--

-- ecos_scenario_execution (TABLE)
CREATE TABLE ecos_control.ecos_scenario_execution (
    id character varying(36) NOT NULL,
    scenario_id character varying(36) NOT NULL,
    run_type character varying(32) NOT NULL,
    run_mode character varying(16) DEFAULT 'FORMAL' NOT NULL,
    status character varying(32) DEFAULT 'RUNNING' NOT NULL,
    metric character varying(255),
    deviation numeric(10,4),
    diagnosis_result_json text,
    forecast_result_json text,
    simulation_result_json text,
    strategy_result_json text,
    decision_record_id character varying(36),
    is_degraded smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64) DEFAULT 'system' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    CONSTRAINT ck_scen_exec_run_mode CHECK (((run_mode)::text = ANY ((ARRAY['FORMAL', 'SANDBOX'])::text[]))),
    CONSTRAINT ck_scen_exec_status CHECK (((status)::text = ANY ((ARRAY['RUNNING', 'SUCCEEDED', 'FAILED'])::text[])))
);

--

-- TABLE ecos_scenario_execution (COMMENT)
COMMENT ON TABLE ecos_control.ecos_scenario_execution IS '场景运行记录表（run_mode 正交列 R-25①；decision_record_id 属主 R-30②；旧 ecos_scenario_run 停写不删）';

--

-- COLUMN ecos_scenario_execution.run_type (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_scenario_execution.run_type IS '运行目的；三方值域并存（X-33/G2-5），权威值域待 PRD-08 §4.1 定版后以附加 CHECK 演进';

--

-- COLUMN ecos_scenario_execution.run_mode (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_scenario_execution.run_mode IS 'FORMAL=正式 / SANDBOX=演练（F07-08 写白名单 DAO 唯一通道校验输入；与 run_type 语义正交）';

--

-- COLUMN ecos_scenario_execution.decision_record_id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_scenario_execution.decision_record_id IS '场景决策回执引用（权威表 ecos_scenario_decision_record；决策底座在 cognitive 经门面，禁第二套底座）';

--

-- ecos_scenario_mind (TABLE)
CREATE TABLE ecos_control.ecos_scenario_mind (
    id bigint NOT NULL,
    scenario_id character varying(64) NOT NULL,
    mind_label character varying(64) DEFAULT 'base' NOT NULL,
    active_mind smallint DEFAULT 0 NOT NULL,
    initial_belief_jsonb jsonb DEFAULT '{}'::jsonb NOT NULL,
    evidence_refs jsonb DEFAULT '[]'::jsonb NOT NULL,
    hypothesis_refs jsonb DEFAULT '[]'::jsonb NOT NULL,
    model_refs jsonb DEFAULT '[]'::jsonb NOT NULL,
    cognitive_endpoints jsonb DEFAULT '{}'::jsonb NOT NULL,
    initial_confidence double precision DEFAULT 0.5 NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64) DEFAULT 'system' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_scenario_mind (COMMENT)
COMMENT ON TABLE ecos_control.ecos_scenario_mind IS '场景授权心智变体（1:N，active_mind 哨兵唯一激活）';

--

-- ecos_scenario_mind_id_seq (SEQUENCE)
ALTER TABLE ecos_control.ecos_scenario_mind ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME ecos_control.ecos_scenario_mind_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--

-- ecos_scenario_mind_variant (TABLE)
CREATE TABLE ecos_control.ecos_scenario_mind_variant (
    id character varying(36) NOT NULL,
    scenario_id character varying(36) NOT NULL,
    mind_label character varying(64) DEFAULT 'base' NOT NULL,
    deleted_guard timestamp without time zone DEFAULT '1970-01-01 00:00:00'::timestamp without time zone NOT NULL,
    initial_belief_json text NOT NULL,
    evidence_refs_json text,
    hypothesis_refs_json text,
    model_refs_json text,
    initial_confidence double precision DEFAULT 0.5 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64) DEFAULT 'system' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL
);

--

-- TABLE ecos_scenario_mind_variant (COMMENT)
COMMENT ON TABLE ecos_control.ecos_scenario_mind_variant IS '场景多心智变体表（JSONB→TEXT、cognitive_endpoints 移出经 sysman 配置、active_mind 改激活表；旧表停写不删）';

--

-- ecos_scenario_query_history (TABLE)
CREATE TABLE ecos_control.ecos_scenario_query_history (
    id character varying(36) NOT NULL,
    trace_id character varying(64),
    subject_id character varying(36) NOT NULL,
    sql_digest character varying(128) NOT NULL,
    row_count integer,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64) DEFAULT 'system' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL
);

--

-- TABLE ecos_scenario_query_history (COMMENT)
COMMENT ON TABLE ecos_control.ecos_scenario_query_history IS '场景查询历史台账（替换 X-55 内存态；sql_digest 摘要形态，禁存 SQL 原文/结果集）';

--

-- COLUMN ecos_scenario_query_history.sql_digest (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_scenario_query_history.sql_digest IS '查询规范化摘要（防审计面变敏感面，与分册 06 F06-03 同规则）';

--

-- ecos_scenario_run (TABLE)
CREATE TABLE ecos_control.ecos_scenario_run (
    id character varying(64) NOT NULL,
    scenario_id character varying(64) NOT NULL,
    run_type character varying(32) NOT NULL,
    status character varying(32) DEFAULT 'RUNNING' NOT NULL,
    metric character varying(255),
    deviation numeric(10,4),
    diagnosis_result jsonb,
    forecast_result jsonb,
    simulation_result jsonb,
    strategy_result jsonb,
    decision_id character varying(64),
    degraded boolean DEFAULT false,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system',
    update_by character varying(64) DEFAULT 'system',
    is_deleted smallint DEFAULT 0 NOT NULL,
    run_mode character varying(16) DEFAULT 'FORMAL' NOT NULL,
    source_run_id character varying(64),
    sandbox_lineage character varying(16) DEFAULT 'UNKNOWN' NOT NULL
);

--

-- TABLE ecos_scenario_run (COMMENT)
COMMENT ON TABLE ecos_control.ecos_scenario_run IS '场景运行记录 — PMO-52 场景工作台认知闭环';

--

-- COLUMN ecos_scenario_run.run_type (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_scenario_run.run_type IS '运行类型 DIAGNOSE/FORECAST/SIMULATE/STRATEGY/FULL';

--

-- COLUMN ecos_scenario_run.degraded (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_scenario_run.degraded IS '是否存在 KG 降级（任一步走规则兜底则 true）';

--

-- ecos_scenario_sandbox_layout (TABLE)
CREATE TABLE ecos_control.ecos_scenario_sandbox_layout (
    id bigint NOT NULL,
    scenario_id character varying(64) NOT NULL,
    layout_jsonb jsonb DEFAULT '{}'::jsonb NOT NULL,
    layout_version integer DEFAULT 1 NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64) DEFAULT 'system' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_scenario_sandbox_layout (COMMENT)
COMMENT ON TABLE ecos_control.ecos_scenario_sandbox_layout IS '业务场景沙盘画布布局（React Flow 序列化，乐观锁 layout_version）';

--

-- ecos_scenario_sandbox_layout_id_seq (SEQUENCE)
ALTER TABLE ecos_control.ecos_scenario_sandbox_layout ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME ecos_control.ecos_scenario_sandbox_layout_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--

-- ecos_security_asset (TABLE)
CREATE TABLE ecos_control.ecos_security_asset (
    id character varying(36) NOT NULL,
    asset_key character varying(192) NOT NULL,
    kind character varying(16) NOT NULL,
    sensitivity character varying(8) NOT NULL,
    guard_combo character varying(64) NOT NULL,
    exemption_ref character varying(64),
    owner_role character varying(64),
    description character varying(500),
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    tenant_id character varying(36),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100)
);

--

-- ecos_security_crypto_key (TABLE)
CREATE TABLE ecos_control.ecos_security_crypto_key (
    id character varying(36) NOT NULL,
    key_id character varying(64) NOT NULL,
    algorithm character varying(32) NOT NULL,
    key_version smallint NOT NULL,
    status character varying(16) DEFAULT 'active' NOT NULL,
    master_key_ref character varying(128) NOT NULL,
    rotate_at timestamp without time zone,
    retired_at timestamp without time zone,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    tenant_id character varying(36)
);

--

-- ecos_security_policy (TABLE)
CREATE TABLE ecos_control.ecos_security_policy (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    domain character varying(128),
    policy_expr text NOT NULL,
    priority integer DEFAULT 100 NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64) DEFAULT 'system' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_security_policy (COMMENT)
COMMENT ON TABLE ecos_control.ecos_security_policy IS '场景绑定的安全策略真表';

--

-- ecos_spans (TABLE)
CREATE TABLE ecos_control.ecos_spans (
    span_id character varying(64) NOT NULL,
    trace_id character varying(64) NOT NULL,
    parent_span_id character varying(64),
    operation_name character varying(512),
    service_name character varying(128),
    http_method character varying(16),
    http_path character varying(512),
    http_status integer DEFAULT 0,
    start_time timestamp without time zone DEFAULT now() NOT NULL,
    end_time timestamp without time zone,
    duration_ms bigint DEFAULT 0,
    status character varying(16) DEFAULT 'OK',
    attributes jsonb,
    created_at timestamp without time zone DEFAULT now()
);

--

-- ecos_task (TABLE)
CREATE TABLE ecos_control.ecos_task (
    id bigint NOT NULL,
    task_name character varying(256) NOT NULL,
    task_type character varying(32) NOT NULL,
    status character varying(32) DEFAULT 'PENDING',
    config jsonb,
    runner character varying(64),
    priority integer DEFAULT 0,
    retry_count integer DEFAULT 0,
    max_retries integer DEFAULT 3,
    scheduled_at timestamp without time zone,
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    result jsonb,
    error_message text,
    created_by character varying(64),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);

--

-- TABLE ecos_task (COMMENT)
COMMENT ON TABLE ecos_control.ecos_task IS '任务调度表 — 统一任务编排与执行追踪';

--

-- COLUMN ecos_task.id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_task.id IS '主键，自增';

--

-- COLUMN ecos_task.task_name (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_task.task_name IS '任务名称';

--

-- COLUMN ecos_task.task_type (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_task.task_type IS '任务类型: PIPELINE/REALTIME/AGENT/ADMIN';

--

-- COLUMN ecos_task.status (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_task.status IS '任务状态: PENDING/RUNNING/SUCCESS/FAILED/CANCELLED';

--

-- COLUMN ecos_task.config (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_task.config IS '任务配置(JSON): SQL语句、规则ID、参数等';

--

-- COLUMN ecos_task.runner (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_task.runner IS '执行器: doris/rule/hermes';

--

-- COLUMN ecos_task.priority (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_task.priority IS '优先级，数值越大优先级越高';

--

-- COLUMN ecos_task.retry_count (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_task.retry_count IS '已重试次数';

--

-- COLUMN ecos_task.max_retries (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_task.max_retries IS '最大重试次数';

--

-- COLUMN ecos_task.scheduled_at (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_task.scheduled_at IS '计划执行时间';

--

-- COLUMN ecos_task.started_at (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_task.started_at IS '实际开始时间';

--

-- COLUMN ecos_task.completed_at (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_task.completed_at IS '完成时间';

--

-- COLUMN ecos_task.result (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_task.result IS '执行结果(JSON)';

--

-- COLUMN ecos_task.error_message (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_task.error_message IS '错误信息';

--

-- COLUMN ecos_task.created_by (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_task.created_by IS '创建人';

--

-- COLUMN ecos_task.created_at (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_task.created_at IS '创建时间';

--

-- COLUMN ecos_task.updated_at (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_task.updated_at IS '更新时间';

--

-- ecos_task_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.ecos_task_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_task_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.ecos_task_id_seq OWNED BY ecos_control.ecos_task.id;

--

-- ecos_tenant (TABLE)
CREATE TABLE ecos_control.ecos_tenant (
    id character varying(32) NOT NULL,
    tenant_name character varying(64) NOT NULL,
    tenant_code character varying(32),
    status character varying(16) DEFAULT 'ACTIVE',
    max_users integer DEFAULT 0,
    max_storage_mb bigint DEFAULT 0,
    max_api_per_day bigint DEFAULT 0,
    isolation_mode character varying(16) DEFAULT 'ROW_FILTER',
    schema_name character varying(64),
    database_url character varying(256),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);

--

-- ecos_tenant_quota (TABLE)
CREATE TABLE ecos_control.ecos_tenant_quota (
    id bigint NOT NULL,
    tenant_id character varying(64) NOT NULL,
    quota_type character varying(32) NOT NULL,
    daily_limit bigint DEFAULT 0,
    monthly_limit bigint DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);

--

-- ecos_tenant_quota_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.ecos_tenant_quota_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_tenant_quota_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.ecos_tenant_quota_id_seq OWNED BY ecos_control.ecos_tenant_quota.id;

--

-- ecos_tenant_usage (TABLE)
CREATE TABLE ecos_control.ecos_tenant_usage (
    tenant_id character varying(64) NOT NULL,
    usage_date date NOT NULL,
    quota_type character varying(32) NOT NULL,
    used_count bigint DEFAULT 0,
    updated_at timestamp without time zone DEFAULT now()
);

--

-- ecos_token_usage (TABLE)
CREATE TABLE ecos_control.ecos_token_usage (
    id bigint NOT NULL,
    trace_id character varying(64),
    model character varying(64),
    operation character varying(256),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_tokens integer DEFAULT 0,
    cost_estimate numeric(10,6) DEFAULT 0,
    latency_ms bigint DEFAULT 0,
    created_at timestamp without time zone DEFAULT now()
);

--

-- ecos_token_usage_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.ecos_token_usage_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_token_usage_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.ecos_token_usage_id_seq OWNED BY ecos_control.ecos_token_usage.id;

--

-- ecos_warn_log (TABLE)
CREATE TABLE ecos_control.ecos_warn_log (
    id character varying(64) NOT NULL,
    log_id character varying(64) NOT NULL,
    warn_type character varying(64),
    warn_level character varying(16) DEFAULT 'WARN' NOT NULL,
    warn_objid character varying(128),
    warn_objname character varying(256),
    warn_message text,
    fault_context jsonb,
    review_tag character varying(64),
    warn_hand character varying(64),
    warn_result text,
    ishanded character varying(4) DEFAULT '0' NOT NULL,
    warn_time timestamp without time zone DEFAULT now() NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system',
    update_by character varying(64) DEFAULT 'system',
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_warn_log (COMMENT)
COMMENT ON TABLE ecos_control.ecos_warn_log IS 'runtime-monitor 告警日志落库表 — PMO-59 P4a（关闭 P2b 告警内存态残留风险）';

--

-- COLUMN ecos_warn_log.log_id (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_warn_log.log_id IS '告警日志业务键（幂等去重，uniq 索引）';

--

-- COLUMN ecos_warn_log.warn_type (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_warn_log.warn_type IS '告警类型（如 COGNITIVE_HYPOTHESIS_INVALIDATED）';

--

-- COLUMN ecos_warn_log.fault_context (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_warn_log.fault_context IS '结构化故障上下文 JSONB（与 ecos.cognitive 事件 faultContext 同口径）';

--

-- COLUMN ecos_warn_log.review_tag (COMMENT)
COMMENT ON COLUMN ecos_control.ecos_warn_log.review_tag IS '复盘聚合标签（与 MentalEventPublisher.REVIEW_TAG 同源）';

--

-- ecos_wm_causal_link (TABLE)
CREATE TABLE ecos_control.ecos_wm_causal_link (
    id bigint NOT NULL,
    source_goal_id bigint NOT NULL,
    target_goal_id bigint NOT NULL,
    relationship_type character varying(32) DEFAULT 'POSITIVE' NOT NULL,
    description text DEFAULT ''::text,
    created_at timestamp without time zone DEFAULT now(),
    time_lag_days integer DEFAULT 0,
    correlation_coefficient numeric(4,3) DEFAULT 0.0
);

--

-- ecos_wm_causal_link_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.ecos_wm_causal_link_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_wm_causal_link_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.ecos_wm_causal_link_id_seq OWNED BY ecos_control.ecos_wm_causal_link.id;

--

-- ecos_wm_goal (TABLE)
CREATE TABLE ecos_control.ecos_wm_goal (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    parent_id bigint,
    progress integer DEFAULT 0,
    status character varying(32) DEFAULT 'PLANNED',
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    goal_type character varying(32) DEFAULT 'STRATEGIC',
    weight integer DEFAULT 50,
    org_id character varying(64),
    owner_user_id character varying(64),
    start_date date,
    end_date date,
    target_value numeric(18,2),
    current_value numeric(18,2),
    unit character varying(32),
    linked_workflow_id character varying(64),
    domain_id character varying(50),
    kpi_formula character varying(256) DEFAULT 'currentValue/targetValue*100',
    measure_frequency character varying(16) DEFAULT 'MONTHLY',
    alert_threshold_warn numeric(5,2) DEFAULT 80.0,
    alert_threshold_critical numeric(5,2) DEFAULT 50.0,
    CONSTRAINT ecos_wm_goal_progress_check CHECK (((progress >= 0) AND (progress <= 100)))
);

--

-- ecos_wm_goal_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.ecos_wm_goal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_wm_goal_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.ecos_wm_goal_id_seq OWNED BY ecos_control.ecos_wm_goal.id;

--

-- ecos_wm_goal_log (TABLE)
CREATE TABLE ecos_control.ecos_wm_goal_log (
    id bigint NOT NULL,
    goal_id bigint NOT NULL,
    change_type character varying(32) NOT NULL,
    old_value text,
    new_value text,
    changed_by character varying(64),
    changed_at timestamp without time zone DEFAULT now()
);

--

-- ecos_wm_goal_log_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.ecos_wm_goal_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_wm_goal_log_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.ecos_wm_goal_log_id_seq OWNED BY ecos_control.ecos_wm_goal_log.id;

--

-- ecos_wm_scenario (TABLE)
CREATE TABLE ecos_control.ecos_wm_scenario (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    config_json text DEFAULT '{}'::text,
    status character varying(32) DEFAULT 'DRAFT',
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);

--

-- ecos_wm_scenario_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.ecos_wm_scenario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_wm_scenario_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.ecos_wm_scenario_id_seq OWNED BY ecos_control.ecos_wm_scenario.id;

--

-- ecos_workflow (TABLE)
CREATE TABLE ecos_control.ecos_workflow (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    status character varying(32) DEFAULT 'draft',
    mode character varying(32) DEFAULT 'sequential',
    nodes text DEFAULT '[]'::text,
    edges text DEFAULT '[]'::text,
    published_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);

--

-- ecos_workflow_approval (TABLE)
CREATE TABLE ecos_control.ecos_workflow_approval (
    id character varying(50) NOT NULL,
    task_id character varying(50) NOT NULL,
    instance_id character varying(50) NOT NULL,
    approver character varying(100) NOT NULL,
    decision character varying(50) NOT NULL,
    opinion text,
    form_data jsonb,
    created_at timestamp without time zone DEFAULT now()
);

--

-- ecos_workflow_instance (TABLE)
CREATE TABLE ecos_control.ecos_workflow_instance (
    id character varying(50) NOT NULL,
    workflow_id character varying(50) NOT NULL,
    workflow_name character varying(200),
    version_no character varying(20),
    status character varying(50) NOT NULL,
    trigger_type character varying(50),
    triggered_by character varying(100),
    triggered_object_id character varying(100),
    trigger_event character varying(200),
    variables jsonb DEFAULT '{}'::jsonb,
    context jsonb,
    current_node_ids jsonb,
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    error_message text,
    retry_count integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    tenant_id character varying(32),
    current_node_id jsonb,
    context_json jsonb
);

--

-- ecos_workflow_log (TABLE)
CREATE TABLE ecos_control.ecos_workflow_log (
    id character varying(50) NOT NULL,
    instance_id character varying(50) NOT NULL,
    node_id character varying(100),
    node_type character varying(50),
    event_type character varying(100) NOT NULL,
    message text,
    details jsonb,
    duration_ms bigint,
    trace_id character varying(100),
    created_at timestamp without time zone DEFAULT now()
);

--

-- ecos_workflow_task (TABLE)
CREATE TABLE ecos_control.ecos_workflow_task (
    id character varying(50) NOT NULL,
    instance_id character varying(50) NOT NULL,
    node_id character varying(100) NOT NULL,
    task_type character varying(50) NOT NULL,
    title character varying(500),
    assignee character varying(100),
    candidate_users jsonb,
    candidate_roles jsonb,
    status character varying(50) DEFAULT 'New',
    priority character varying(20) DEFAULT 'NORMAL',
    form_schema jsonb,
    form_data jsonb,
    result jsonb,
    agent_result jsonb,
    due_date timestamp without time zone,
    completed_at timestamp without time zone,
    completed_by character varying(100),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);

--

-- ecos_world_scenarios (TABLE)
CREATE TABLE ecos_control.ecos_world_scenarios (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    goal_ids text DEFAULT '[]'::text,
    status character varying(32) DEFAULT 'draft',
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);

--

-- kb_cognitive_pipeline (TABLE)
CREATE TABLE ecos_control.kb_cognitive_pipeline (
    id bigint NOT NULL,
    pipeline_id character varying(64) NOT NULL,
    name character varying(256) NOT NULL,
    status character varying(32) DEFAULT 'DRAFT' NOT NULL,
    config jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_by character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    result jsonb,
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE kb_cognitive_pipeline (COMMENT)
COMMENT ON TABLE ecos_control.kb_cognitive_pipeline IS '认知管线定义（CognitivePipeline 持久化，原 ConcurrentHashMap 内存态）';

--

-- COLUMN kb_cognitive_pipeline.id (COMMENT)
COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.id IS '自增主键';

--

-- COLUMN kb_cognitive_pipeline.pipeline_id (COMMENT)
COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.pipeline_id IS '管线业务 ID (UUID)';

--

-- COLUMN kb_cognitive_pipeline.name (COMMENT)
COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.name IS '管线名称';

--

-- COLUMN kb_cognitive_pipeline.status (COMMENT)
COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.status IS '管线状态: DRAFT/ACTIVE/ARCHIVED';

--

-- COLUMN kb_cognitive_pipeline.config (COMMENT)
COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.config IS '管线节点集合 (JSONB: [{nodeId, nodeType, config, dependsOn}, ...])';

--

-- COLUMN kb_cognitive_pipeline.created_by (COMMENT)
COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.created_by IS '创建人';

--

-- COLUMN kb_cognitive_pipeline.created_at (COMMENT)
COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.created_at IS '创建时间';

--

-- COLUMN kb_cognitive_pipeline.updated_at (COMMENT)
COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.updated_at IS '更新时间';

--

-- COLUMN kb_cognitive_pipeline.result (COMMENT)
COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.result IS '最近执行结果 (JSONB，可为空)';

--

-- COLUMN kb_cognitive_pipeline.is_deleted (COMMENT)
COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.is_deleted IS '逻辑删除标记: 0=未删除, 1=已删除';

--

-- kb_cognitive_pipeline_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.kb_cognitive_pipeline_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- kb_cognitive_pipeline_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.kb_cognitive_pipeline_id_seq OWNED BY ecos_control.kb_cognitive_pipeline.id;

--

-- kb_lineage_event (TABLE)
CREATE TABLE ecos_control.kb_lineage_event (
    id bigint NOT NULL,
    event_id character varying(64) NOT NULL,
    query text,
    format character varying(32),
    nodes jsonb DEFAULT '[]'::jsonb NOT NULL,
    edges jsonb DEFAULT '[]'::jsonb NOT NULL,
    parse_at timestamp without time zone DEFAULT now() NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE kb_lineage_event (COMMENT)
COMMENT ON TABLE ecos_control.kb_lineage_event IS '血缘事件（Lineage parse 持久化，原 ConcurrentHashMap 内存态）';

--

-- COLUMN kb_lineage_event.id (COMMENT)
COMMENT ON COLUMN ecos_control.kb_lineage_event.id IS '自增主键';

--

-- COLUMN kb_lineage_event.event_id (COMMENT)
COMMENT ON COLUMN ecos_control.kb_lineage_event.event_id IS '事件业务 ID (UUID)';

--

-- COLUMN kb_lineage_event.query (COMMENT)
COMMENT ON COLUMN ecos_control.kb_lineage_event.query IS '解析时使用的查询/输入描述';

--

-- COLUMN kb_lineage_event.format (COMMENT)
COMMENT ON COLUMN ecos_control.kb_lineage_event.format IS '血缘格式: openlineage/atlas';

--

-- COLUMN kb_lineage_event.nodes (COMMENT)
COMMENT ON COLUMN ecos_control.kb_lineage_event.nodes IS '血缘节点列表 (JSONB: [{id, label, type}, ...])';

--

-- COLUMN kb_lineage_event.edges (COMMENT)
COMMENT ON COLUMN ecos_control.kb_lineage_event.edges IS '血缘边列表 (JSONB: [{source, target, type}, ...])';

--

-- COLUMN kb_lineage_event.parse_at (COMMENT)
COMMENT ON COLUMN ecos_control.kb_lineage_event.parse_at IS '解析时间';

--

-- COLUMN kb_lineage_event.is_deleted (COMMENT)
COMMENT ON COLUMN ecos_control.kb_lineage_event.is_deleted IS '逻辑删除标记: 0=未删除, 1=已删除';

--

-- kb_lineage_event_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.kb_lineage_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- kb_lineage_event_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.kb_lineage_event_id_seq OWNED BY ecos_control.kb_lineage_event.id;

--

-- kb_ontology_snapshot (TABLE)
CREATE TABLE ecos_control.kb_ontology_snapshot (
    id bigint NOT NULL,
    ontology_id character varying(64) NOT NULL,
    version character varying(128) NOT NULL,
    entity_codes jsonb DEFAULT '[]'::jsonb NOT NULL,
    relationship_codes jsonb DEFAULT '[]'::jsonb NOT NULL,
    schema_hash character varying(64),
    created_by character varying(128) DEFAULT 'system' NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE kb_ontology_snapshot (COMMENT)
COMMENT ON TABLE ecos_control.kb_ontology_snapshot IS '本体版本快照（PMO-54 与 PMO-50 T4 本体发布事件消费 — V115/Kb 侧补齐）';

--

-- COLUMN kb_ontology_snapshot.id (COMMENT)
COMMENT ON COLUMN ecos_control.kb_ontology_snapshot.id IS '自增主键';

--

-- COLUMN kb_ontology_snapshot.ontology_id (COMMENT)
COMMENT ON COLUMN ecos_control.kb_ontology_snapshot.ontology_id IS '本体业务 ID';

--

-- COLUMN kb_ontology_snapshot.version (COMMENT)
COMMENT ON COLUMN ecos_control.kb_ontology_snapshot.version IS '本体版本 (published version)';

--

-- COLUMN kb_ontology_snapshot.entity_codes (COMMENT)
COMMENT ON COLUMN ecos_control.kb_ontology_snapshot.entity_codes IS '本体包含实体 code 集合 (JSONB array)';

--

-- COLUMN kb_ontology_snapshot.relationship_codes (COMMENT)
COMMENT ON COLUMN ecos_control.kb_ontology_snapshot.relationship_codes IS '本体包含关系 code 集合 (JSONB array)';

--

-- COLUMN kb_ontology_snapshot.schema_hash (COMMENT)
COMMENT ON COLUMN ecos_control.kb_ontology_snapshot.schema_hash IS '发行时 schema JSON 的 SHA-256 hash 减少';

--

-- COLUMN kb_ontology_snapshot.created_by (COMMENT)
COMMENT ON COLUMN ecos_control.kb_ontology_snapshot.created_by IS '发布人 / 系统标识';

--

-- COLUMN kb_ontology_snapshot.created_at (COMMENT)
COMMENT ON COLUMN ecos_control.kb_ontology_snapshot.created_at IS '快照时间（含 upsert 时间刷行）';

--

-- COLUMN kb_ontology_snapshot.is_deleted (COMMENT)
COMMENT ON COLUMN ecos_control.kb_ontology_snapshot.is_deleted IS '逻辑删除: 0=当前 / 1=已删 / 2=历史旧版本 (EcosOntologyEventConsumer 标记语义)';

--

-- kb_ontology_snapshot_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.kb_ontology_snapshot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- kb_ontology_snapshot_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.kb_ontology_snapshot_id_seq OWNED BY ecos_control.kb_ontology_snapshot.id;

--

-- schema_changes (TABLE)
CREATE TABLE ecos_control.schema_changes (
    id bigint NOT NULL,
    datasource_id character varying(128) NOT NULL,
    table_name character varying(256) NOT NULL,
    change_type character varying(32) NOT NULL,
    detail_json text,
    detected_at timestamp without time zone DEFAULT now() NOT NULL,
    acknowledged boolean DEFAULT false NOT NULL
);

--

-- schema_changes_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.schema_changes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- schema_changes_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.schema_changes_id_seq OWNED BY ecos_control.schema_changes.id;

--

-- schema_snapshots (TABLE)
CREATE TABLE ecos_control.schema_snapshots (
    id bigint NOT NULL,
    datasource_id character varying(128) NOT NULL,
    table_name character varying(256) NOT NULL,
    column_hash character varying(64) NOT NULL,
    col_sig text,
    snapshot_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- schema_snapshots_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.schema_snapshots_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- schema_snapshots_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.schema_snapshots_id_seq OWNED BY ecos_control.schema_snapshots.id;

--

-- sys_agent_message (TABLE)
CREATE TABLE ecos_control.sys_agent_message (
    id bigint NOT NULL,
    session_id character varying(64),
    role character varying(16),
    content text,
    tool_calls jsonb,
    tool_results jsonb,
    tokens integer,
    created_at bigint,
    thread_id character varying(32) DEFAULT 'main' NOT NULL
);

--

-- COLUMN sys_agent_message.thread_id (COMMENT)
COMMENT ON COLUMN ecos_control.sys_agent_message.thread_id IS '会话线程标识，默认 main，支持同一会话下多线程对话';

--

-- sys_agent_message_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.sys_agent_message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- sys_agent_message_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.sys_agent_message_id_seq OWNED BY ecos_control.sys_agent_message.id;

--

-- sys_agent_session (TABLE)
CREATE TABLE ecos_control.sys_agent_session (
    id character varying(64) NOT NULL,
    agent_id character varying(64) NOT NULL,
    user_id character varying(64),
    tenant_id character varying(64),
    status character varying(16) DEFAULT 'ACTIVE',
    message_count integer DEFAULT 0,
    created_at bigint,
    last_active_at bigint
);

--

-- sys_compliance_rule (TABLE)
CREATE TABLE ecos_control.sys_compliance_rule (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    domain character varying(128),
    rule_type character varying(64),
    condition text,
    action text,
    priority integer DEFAULT 0,
    enabled boolean DEFAULT true,
    description text,
    status character varying(32) DEFAULT 'DRAFT',
    required_fact_list text,
    extracted_rule_id character varying(64),
    approved_by character varying(128),
    effective_date bigint,
    expiry_date bigint,
    version integer DEFAULT 1,
    created_at bigint,
    updated_at bigint
);

--

-- sys_config (TABLE)
CREATE TABLE ecos_control.sys_config (
    id character varying(64) NOT NULL,
    config_group character varying(64) NOT NULL,
    config_key character varying(128) NOT NULL,
    config_value text NOT NULL,
    config_type character varying(32) DEFAULT 'string',
    config_label character varying(255) NOT NULL,
    config_label_en character varying(255),
    description text,
    sort_order integer DEFAULT 0,
    status character varying(32) DEFAULT 'active',
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    edition character varying(16) DEFAULT 'all'
);

--

-- TABLE sys_config (COMMENT)
COMMENT ON TABLE ecos_control.sys_config IS '系统配置单表（config_group 分组，铁律 §十 配置单源）；回测/复审判定阈值见 config_group=''fc.backtest''（V280，详设-09 H-4）';

--

-- sys_dict (TABLE)
CREATE TABLE ecos_control.sys_dict (
    id character varying(64) NOT NULL,
    dict_type character varying(64) NOT NULL,
    dict_code character varying(128) NOT NULL,
    dict_label character varying(255) NOT NULL,
    dict_label_en character varying(255),
    sort_order integer DEFAULT 0,
    status character varying(32) DEFAULT 'active',
    parent_code character varying(128),
    ext_value character varying(255),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    subsystem character varying(64)
);

--

-- sys_extraction_source (TABLE)
CREATE TABLE ecos_control.sys_extraction_source (
    id character varying(64) NOT NULL,
    source_type character varying(32),
    source_id character varying(255),
    source_title character varying(512),
    source_excerpt text,
    extracted_at bigint,
    extractor_version character varying(32)
);

--

-- sys_token_blacklist (TABLE)
CREATE TABLE ecos_control.sys_token_blacklist (
    token text NOT NULL,
    expire_at bigint NOT NULL
);

--

-- td_abac_policy (TABLE)
CREATE TABLE ecos_control.td_abac_policy (
    policy_id character varying(64) NOT NULL,
    policy_name character varying(128) NOT NULL,
    subject_condition character varying(512),
    resource_condition character varying(512) DEFAULT '*',
    action_condition character varying(256) DEFAULT '*',
    environment_condition character varying(512),
    effect character varying(16) NOT NULL,
    priority integer DEFAULT 100,
    created_time timestamp without time zone DEFAULT now(),
    scope_type character varying(16) DEFAULT 'GLOBAL',
    scope_id character varying(64)
);

--

-- TABLE td_abac_policy (COMMENT)
COMMENT ON TABLE ecos_control.td_abac_policy IS 'ABAC(基于属性的访问控制)策略表';

--

-- COLUMN td_abac_policy.policy_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_abac_policy.policy_id IS '策略唯一标识';

--

-- COLUMN td_abac_policy.policy_name (COMMENT)
COMMENT ON COLUMN ecos_control.td_abac_policy.policy_name IS '策略名称';

--

-- COLUMN td_abac_policy.subject_condition (COMMENT)
COMMENT ON COLUMN ecos_control.td_abac_policy.subject_condition IS '主体条件 (e.g. role:ROLE_SUPER_ADMIN)';

--

-- COLUMN td_abac_policy.resource_condition (COMMENT)
COMMENT ON COLUMN ecos_control.td_abac_policy.resource_condition IS '资源条件 (e.g. dataset:*)';

--

-- COLUMN td_abac_policy.action_condition (COMMENT)
COMMENT ON COLUMN ecos_control.td_abac_policy.action_condition IS '动作条件 (e.g. read,write,delete)';

--

-- COLUMN td_abac_policy.environment_condition (COMMENT)
COMMENT ON COLUMN ecos_control.td_abac_policy.environment_condition IS '环境条件 (e.g. region == APAC)';

--

-- COLUMN td_abac_policy.effect (COMMENT)
COMMENT ON COLUMN ecos_control.td_abac_policy.effect IS '效果: allow(允许) / deny(拒绝)';

--

-- COLUMN td_abac_policy.priority (COMMENT)
COMMENT ON COLUMN ecos_control.td_abac_policy.priority IS '优先级 (越小越高)';

--

-- COLUMN td_abac_policy.scope_type (COMMENT)
COMMENT ON COLUMN ecos_control.td_abac_policy.scope_type IS '作用域类型: GLOBAL/TENANT/ORG';

--

-- COLUMN td_abac_policy.scope_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_abac_policy.scope_id IS '作用域ID (TENANT→tenant_id, ORG→org_id)';

--

-- td_audit_log (TABLE)
CREATE TABLE ecos_control.td_audit_log (
    log_id character varying(64) NOT NULL,
    event_type character varying(64) NOT NULL,
    "timestamp" timestamp without time zone DEFAULT now() NOT NULL,
    user_id character varying(64),
    tenant_id character varying(64),
    resource character varying(256),
    action character varying(128),
    result character varying(32),
    ip_address character varying(64),
    user_agent character varying(512),
    request_id character varying(64),
    duration integer,
    details text
);

--

-- TABLE td_audit_log (COMMENT)
COMMENT ON TABLE ecos_control.td_audit_log IS '审计日志表（D-6 补登：schema 锚定单源，首个 schema 限定 CREATE；消费面 = sysman 属主 AuditLog-sql.xml 13 列 + ontology ActionHookAuditDao 8 列子集）';

--

-- COLUMN td_audit_log.log_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_audit_log.log_id IS '日志唯一标识（应用侧生成）';

--

-- COLUMN td_audit_log.event_type (COMMENT)
COMMENT ON COLUMN ecos_control.td_audit_log.event_type IS '事件类型: LOGIN/ACCESS/CUD/EXPORT/CONFIG';

--

-- COLUMN td_audit_log.user_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_audit_log.user_id IS '操作用户ID';

--

-- COLUMN td_audit_log.tenant_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_audit_log.tenant_id IS '租户ID';

--

-- COLUMN td_audit_log.resource (COMMENT)
COMMENT ON COLUMN ecos_control.td_audit_log.resource IS '操作资源路径';

--

-- COLUMN td_audit_log.action (COMMENT)
COMMENT ON COLUMN ecos_control.td_audit_log.action IS '操作动作';

--

-- COLUMN td_audit_log.result (COMMENT)
COMMENT ON COLUMN ecos_control.td_audit_log.result IS '操作结果: SUCCESS/FAILURE/DENIED';

--

-- COLUMN td_audit_log.ip_address (COMMENT)
COMMENT ON COLUMN ecos_control.td_audit_log.ip_address IS '客户端IP';

--

-- COLUMN td_audit_log.details (COMMENT)
COMMENT ON COLUMN ecos_control.td_audit_log.details IS '补充信息(JSON格式)';

--

-- td_catalog_item (TABLE)
CREATE TABLE ecos_control.td_catalog_item (
    catalog_id character varying(64) NOT NULL,
    resource_id character varying(64) NOT NULL,
    resource_name character varying(256) NOT NULL,
    resource_type character varying(32) DEFAULT 'TABLE' NOT NULL,
    org_name character varying(128),
    description text,
    tags character varying(256),
    category_path character varying(256),
    access_type character varying(32) DEFAULT 'READ',
    data_format character varying(32),
    field_count integer DEFAULT 0,
    record_count bigint DEFAULT 0,
    last_updated timestamp without time zone DEFAULT now() NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE' NOT NULL,
    tenant_id character varying(64)
);

--

-- TABLE td_catalog_item (COMMENT)
COMMENT ON TABLE ecos_control.td_catalog_item IS '数据目录索引表 — 全局搜索入口';

--

-- td_config (TABLE)
CREATE TABLE ecos_control.td_config (
    config_id character varying(64) NOT NULL,
    config_type character varying(64) NOT NULL,
    config_name character varying(256) NOT NULL,
    config_content text,
    version character varying(32),
    environment character varying(32),
    tenant_id character varying(64),
    created_time timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64),
    updated_time timestamp without time zone DEFAULT now() NOT NULL,
    updated_by character varying(64)
);

--

-- TABLE td_config (COMMENT)
COMMENT ON TABLE ecos_control.td_config IS '系统配置表';

--

-- td_data_field (TABLE)
CREATE TABLE ecos_control.td_data_field (
    field_id character varying(64) NOT NULL,
    resource_id character varying(64) NOT NULL,
    field_name character varying(256) NOT NULL,
    field_alias character varying(256),
    field_type character varying(64),
    field_length integer,
    data_precision integer,
    nullable smallint DEFAULT 1,
    is_primary_key smallint DEFAULT 0,
    default_value character varying(256),
    description text,
    field_order integer DEFAULT 0
);

--

-- TABLE td_data_field (COMMENT)
COMMENT ON TABLE ecos_control.td_data_field IS '数据字段表 — 表的列级元数据';

--

-- td_data_resource (TABLE)
CREATE TABLE ecos_control.td_data_resource (
    resource_id character varying(64) NOT NULL,
    resource_name character varying(256) NOT NULL,
    resource_type character varying(32) DEFAULT 'TABLE' NOT NULL,
    org_id character varying(64),
    org_name character varying(128),
    datasource_id character varying(64) NOT NULL,
    source_path character varying(512),
    description text,
    tags character varying(256),
    status character varying(16) DEFAULT 'ACTIVE' NOT NULL,
    field_count integer DEFAULT 0,
    record_count bigint DEFAULT 0,
    last_sync_time timestamp without time zone,
    create_by character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_by character varying(64),
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    layer character varying(16) DEFAULT 'RAW',
    zone character varying(16),
    layer_bucket character varying(30),
    carrier_ref character varying(255),
    storage_kind character varying(20)
);

--

-- TABLE td_data_resource (COMMENT)
COMMENT ON TABLE ecos_control.td_data_resource IS '数据资源表 — JDBC 发现的 TABLE/VIEW';

--

-- COLUMN td_data_resource.layer (COMMENT)
COMMENT ON COLUMN ecos_control.td_data_resource.layer IS 'Data layer: SOURCE/RAW/CURATED/SEMANTIC/APPLICATION';

--

-- COLUMN td_data_resource.zone (COMMENT)
COMMENT ON COLUMN ecos_control.td_data_resource.zone IS '近源区: STRUCTURED=结构化近源 / UNSTRUCTURED=非结构化近源 / NULL=非近源层';

--

-- COLUMN td_data_resource.layer_bucket (COMMENT)
COMMENT ON COLUMN ecos_control.td_data_resource.layer_bucket IS '载体子类: 数据湖五层(SOURCE/RAW/CURATED/SEMANTIC/APPLICATION)内的载体子分类';

--

-- COLUMN td_data_resource.carrier_ref (COMMENT)
COMMENT ON COLUMN ecos_control.td_data_resource.carrier_ref IS '载体标识: 对象 key / 表名 / 视图名 / 图谱 label（跨域只存定位符，铁律 §3.5）';

--

-- COLUMN td_data_resource.storage_kind (COMMENT)
COMMENT ON COLUMN ecos_control.td_data_resource.storage_kind IS '存储载体: PG/MINIO/DORIS/CLICKHOUSE/NEO4J/PG_VECTOR（湖规范 v2.0 §七A 载体分档）';

--

-- td_datasource (TABLE)
CREATE TABLE ecos_control.td_datasource (
    datasource_id character varying(64) NOT NULL,
    datasource_name character varying(128) NOT NULL,
    datasource_type character varying(32) DEFAULT 'JDBC' NOT NULL,
    org_id character varying(64),
    node_id character varying(64),
    description text,
    connection_config text NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE' NOT NULL,
    tags character varying(256),
    last_test_time timestamp without time zone,
    last_test_result boolean,
    last_test_message text,
    create_by character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_by character varying(64),
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    metadata_config jsonb DEFAULT '{}'::jsonb NOT NULL,
    last_collect_time timestamp without time zone,
    credential_encrypted text,
    is_default smallint,
    remark text,
    password_enc text,
    tenant_id character varying(64)
);

--

-- TABLE td_datasource (COMMENT)
COMMENT ON TABLE ecos_control.td_datasource IS '数据源注册表 — 支持 Oracle/MySQL/PG/SQLServer/达梦/金仓';

--

-- COLUMN td_datasource.metadata_config (COMMENT)
COMMENT ON COLUMN ecos_control.td_datasource.metadata_config IS '元数据获取策略配置: {strategy, includeRowCount, countMethod, scheduleCron, cacheTtlMinutes, onSourceEdit}';

--

-- COLUMN td_datasource.last_collect_time (COMMENT)
COMMENT ON COLUMN ecos_control.td_datasource.last_collect_time IS '最近一次元数据采集完成时间';

--

-- td_dw_write_channel_idempotency (TABLE)
CREATE TABLE ecos_control.td_dw_write_channel_idempotency (
    id character varying(36) NOT NULL,
    idempotency_key character varying(128) NOT NULL,
    trace_id character varying(128),
    layer character varying(16) NOT NULL,
    layer_bucket character varying(30),
    carrier_ref character varying(255),
    storage_kind character varying(20),
    target_table character varying(255),
    write_id character varying(36) NOT NULL,
    create_by character varying(100),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_by character varying(100),
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE td_dw_write_channel_idempotency (COMMENT)
COMMENT ON TABLE ecos_control.td_dw_write_channel_idempotency IS 'F02-05 唯一写通道幂等台账 — 同 idempotencyKey 重放不产生第二行（C.3.6）';

--

-- td_metadata_collect_log (TABLE)
CREATE TABLE ecos_control.td_metadata_collect_log (
    id bigint NOT NULL,
    datasource_id character varying(64) NOT NULL,
    count_method character varying(20) DEFAULT 'ESTIMATE' NOT NULL,
    tables_total integer DEFAULT 0,
    tables_ok integer DEFAULT 0,
    tables_failed integer DEFAULT 0,
    failed_tables text,
    status character varying(20) DEFAULT 'SUCCEEDED' NOT NULL,
    detail text,
    task_id character varying(64),
    elapsed_ms bigint,
    create_time timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE td_metadata_collect_log (COMMENT)
COMMENT ON TABLE ecos_control.td_metadata_collect_log IS 'PMO-37 元数据采集任务执行审计日志';

--

-- td_metadata_collect_log_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_control.td_metadata_collect_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- td_metadata_collect_log_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control.td_metadata_collect_log_id_seq OWNED BY ecos_control.td_metadata_collect_log.id;

--

-- td_org_permission (TABLE)
CREATE TABLE ecos_control.td_org_permission (
    permission_id character varying(64) NOT NULL,
    org_id character varying(64) NOT NULL,
    resource_id character varying(128),
    action character varying(64),
    inherit_from_parent character varying(1) DEFAULT '0',
    created_time timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64)
);

--

-- TABLE td_org_permission (COMMENT)
COMMENT ON TABLE ecos_control.td_org_permission IS '组织权限表';

--

-- td_organization (TABLE)
CREATE TABLE ecos_control.td_organization (
    org_id character varying(64) NOT NULL,
    org_name character varying(256),
    org_code character varying(128),
    parent_org_id character varying(64),
    org_type character varying(32),
    description character varying(512),
    status character varying(16) DEFAULT 'ACTIVE',
    remark character varying(512),
    tenant_id character varying(64),
    created_time timestamp without time zone DEFAULT now(),
    created_by character varying(64),
    updated_time timestamp without time zone,
    updated_by character varying(64)
);

--

-- td_permission (TABLE)
CREATE TABLE ecos_control.td_permission (
    permission_id character varying(64) NOT NULL,
    permission_name character varying(256) NOT NULL,
    permission_code character varying(256) NOT NULL,
    resource_id character varying(128),
    action character varying(64),
    description character varying(512),
    tenant_id character varying(64),
    created_time timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64),
    updated_time timestamp without time zone DEFAULT now() NOT NULL,
    updated_by character varying(64)
);

--

-- TABLE td_permission (COMMENT)
COMMENT ON TABLE ecos_control.td_permission IS '权限表';

--

-- COLUMN td_permission.permission_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_permission.permission_id IS '权限ID';

--

-- COLUMN td_permission.permission_name (COMMENT)
COMMENT ON COLUMN ecos_control.td_permission.permission_name IS '权限名称';

--

-- COLUMN td_permission.permission_code (COMMENT)
COMMENT ON COLUMN ecos_control.td_permission.permission_code IS '权限编码';

--

-- COLUMN td_permission.resource_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_permission.resource_id IS '资源ID';

--

-- COLUMN td_permission.action (COMMENT)
COMMENT ON COLUMN ecos_control.td_permission.action IS '操作类型';

--

-- COLUMN td_permission.description (COMMENT)
COMMENT ON COLUMN ecos_control.td_permission.description IS '描述';

--

-- COLUMN td_permission.tenant_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_permission.tenant_id IS '租户ID';

--

-- td_role (TABLE)
CREATE TABLE ecos_control.td_role (
    role_id character varying(64) NOT NULL,
    role_name character varying(128) NOT NULL,
    role_code character varying(64) NOT NULL,
    description character varying(512),
    parent_role_id character varying(64),
    tenant_id character varying(64),
    created_time timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64),
    updated_time timestamp without time zone DEFAULT now() NOT NULL,
    updated_by character varying(64),
    status character varying(16) DEFAULT 'ACTIVE'
);

--

-- TABLE td_role (COMMENT)
COMMENT ON TABLE ecos_control.td_role IS '角色表';

--

-- COLUMN td_role.role_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_role.role_id IS '角色ID';

--

-- COLUMN td_role.role_name (COMMENT)
COMMENT ON COLUMN ecos_control.td_role.role_name IS '角色名称';

--

-- COLUMN td_role.role_code (COMMENT)
COMMENT ON COLUMN ecos_control.td_role.role_code IS '角色编码';

--

-- COLUMN td_role.description (COMMENT)
COMMENT ON COLUMN ecos_control.td_role.description IS '描述';

--

-- COLUMN td_role.parent_role_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_role.parent_role_id IS '父角色ID';

--

-- COLUMN td_role.tenant_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_role.tenant_id IS '租户ID';

--

-- COLUMN td_role.created_time (COMMENT)
COMMENT ON COLUMN ecos_control.td_role.created_time IS '创建时间';

--

-- COLUMN td_role.created_by (COMMENT)
COMMENT ON COLUMN ecos_control.td_role.created_by IS '创建人';

--

-- COLUMN td_role.updated_time (COMMENT)
COMMENT ON COLUMN ecos_control.td_role.updated_time IS '更新时间';

--

-- COLUMN td_role.updated_by (COMMENT)
COMMENT ON COLUMN ecos_control.td_role.updated_by IS '更新人';

--

-- td_role_permission (TABLE)
CREATE TABLE ecos_control.td_role_permission (
    role_id character varying(64) NOT NULL,
    permission_id character varying(64) NOT NULL,
    created_time timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64)
);

--

-- TABLE td_role_permission (COMMENT)
COMMENT ON TABLE ecos_control.td_role_permission IS '角色权限关联表';

--

-- td_role_security_profile (TABLE)
CREATE TABLE ecos_control.td_role_security_profile (
    role_id character varying(64) NOT NULL,
    clearance_level integer DEFAULT 0,
    linked_workstation character varying(256),
    audit_mode character varying(32) DEFAULT 'basic',
    sandbox_mandatory boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    tenant_id character varying(64),
    org_id character varying(64),
    scope_type character varying(16) DEFAULT 'ROLE'
);

--

-- TABLE td_role_security_profile (COMMENT)
COMMENT ON TABLE ecos_control.td_role_security_profile IS '角色级安全配置表 — 优先级次于用户级，高于全局默认';

--

-- COLUMN td_role_security_profile.role_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_role_security_profile.role_id IS '角色ID，关联 td_role.ROLE_ID';

--

-- COLUMN td_role_security_profile.clearance_level (COMMENT)
COMMENT ON COLUMN ecos_control.td_role_security_profile.clearance_level IS '准入等级 0-4: L0公开 L1内部 L2保密 L3机密 L4绝密';

--

-- COLUMN td_role_security_profile.linked_workstation (COMMENT)
COMMENT ON COLUMN ecos_control.td_role_security_profile.linked_workstation IS '物理工作站绑定（MAC/IP/主机名）';

--

-- COLUMN td_role_security_profile.audit_mode (COMMENT)
COMMENT ON COLUMN ecos_control.td_role_security_profile.audit_mode IS '审计力度: basic(基本) detailed(详细) full(全量)';

--

-- COLUMN td_role_security_profile.sandbox_mandatory (COMMENT)
COMMENT ON COLUMN ecos_control.td_role_security_profile.sandbox_mandatory IS '是否强制沙盒运行';

--

-- COLUMN td_role_security_profile.tenant_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_role_security_profile.tenant_id IS '租户ID (scope_type=TENANT时填充)';

--

-- COLUMN td_role_security_profile.org_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_role_security_profile.org_id IS '机构ID (scope_type=ORG时填充)';

--

-- COLUMN td_role_security_profile.scope_type (COMMENT)
COMMENT ON COLUMN ecos_control.td_role_security_profile.scope_type IS '作用域类型: GLOBAL/TENANT/ORG/ROLE/USER';

--

-- td_runtime_task (TABLE)
CREATE TABLE ecos_control.td_runtime_task (
    "TASK_ID" character varying(64) NOT NULL,
    "TASK_NAME" character varying(255),
    "TASK_TYPE" character varying(64),
    "DESCRIPTION" text,
    "TASK_CONFIG" text,
    "PARAMETERS" text,
    "PRIORITY" integer,
    "TIMEOUT" bigint,
    "RETRY_COUNT" integer,
    "ASYNC_FLAG" character varying(2),
    "DEPENDENCIES" text,
    "SCHEDULE_ID" character varying(64),
    "NODE_ID" character varying(64),
    "EXECUTION_MODE" character varying(32),
    "CREATED_BY" character varying(100),
    "TENANT_ID" character varying(64),
    "TAGS" text,
    "EXTENSIONS" text,
    "CREATED_TIME" timestamp without time zone,
    "UPDATED_TIME" timestamp without time zone,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL
);

--

-- TABLE td_runtime_task (COMMENT)
COMMENT ON TABLE ecos_control.td_runtime_task IS 'runtime-task 即时/定时任务描述主表（task_id 主键, 含审计五字段 + domain + version_no）';

--

-- td_runtime_task_execution (TABLE)
CREATE TABLE ecos_control.td_runtime_task_execution (
    "EXECUTION_ID" character varying(64) NOT NULL,
    "TASK_ID" character varying(64),
    "STATUS" character varying(32),
    "START_TIME" timestamp without time zone,
    "END_TIME" timestamp without time zone,
    "DURATION" bigint,
    "RESULT" text,
    "ERROR_MESSAGE" text,
    "ERROR_STACK" text,
    "EXECUTION_NODE_ID" character varying(64),
    "CREATED_TIME" timestamp without time zone
);

--

-- TABLE td_runtime_task_execution (COMMENT)
COMMENT ON TABLE ecos_control.td_runtime_task_execution IS 'runtime-task 执行记录（recordExecution 唯一写面；列清单单源 = TaskPersistenceDao:322-329）';

--

-- td_runtime_task_log (TABLE)
CREATE TABLE ecos_control.td_runtime_task_log (
    "LOG_SEQ" bigint NOT NULL,
    "EXECUTION_ID" character varying(64),
    "TASK_ID" character varying(64),
    "LOG_LEVEL" character varying(16),
    "MESSAGE" text,
    "CREATED_TIME" timestamp without time zone
);

--

-- TABLE td_runtime_task_log (COMMENT)
COMMENT ON TABLE ecos_control.td_runtime_task_log IS 'runtime-task 任务日志（logTask 唯一写面；无软删/无唯一键 ⇒ PK 走 BIGSERIAL，登记 D5）';

--

-- td_runtime_task_log_LOG_SEQ_seq (SEQUENCE)
CREATE SEQUENCE ecos_control."td_runtime_task_log_LOG_SEQ_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- td_runtime_task_log_LOG_SEQ_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_control."td_runtime_task_log_LOG_SEQ_seq" OWNED BY ecos_control.td_runtime_task_log."LOG_SEQ";

--

-- td_runtime_task_plan (TABLE)
CREATE TABLE ecos_control.td_runtime_task_plan (
    "TASK_ID" character varying(64) NOT NULL,
    "PLAN_CONTENT" text,
    "EXECUTION_MODE" character varying(32),
    "TARGET_NODE_ID" character varying(64),
    "CREATED_TIME" timestamp without time zone,
    "UPDATED_TIME" timestamp without time zone,
    "TASK_NAME" character varying(255),
    "TASK_TYPE" character varying(64),
    "CRON_EXPRESSION" character varying(128),
    "NEXT_RUN_AT" timestamp without time zone,
    "LAST_RUN_AT" timestamp without time zone,
    "LAST_STATUS" character varying(32),
    cron_expression character varying(128),
    next_run_at timestamp without time zone,
    last_run_at timestamp without time zone,
    last_status character varying(32),
    task_name character varying(255),
    task_type character varying(64),
    create_by character varying(64),
    update_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL
);

--

-- TABLE td_runtime_task_plan (COMMENT)
COMMENT ON TABLE ecos_control.td_runtime_task_plan IS 'runtime-task 执行计划主表（plan_content 持久化 + 定时四要素：cron/next_run_at/last_run_at/last_status）';

--

-- td_runtime_task_status (TABLE)
CREATE TABLE ecos_control.td_runtime_task_status (
    "TASK_ID" character varying(64) NOT NULL,
    "STATUS" character varying(32) DEFAULT 'PENDING' NOT NULL,
    "STATUS_MESSAGE" character varying(512),
    "PROGRESS" integer,
    "CURRENT_STEP_ID" character varying(64),
    "START_TIME" timestamp without time zone,
    "END_TIME" timestamp without time zone,
    "ESTIMATED_REMAINING_TIME" bigint,
    "PROCESSED_COUNT" bigint,
    "TOTAL_COUNT" bigint,
    "ERROR_MESSAGE" text,
    "ERROR_STACK" text,
    "RESULT" text,
    "METRICS" text,
    "EXECUTION_NODE_ID" character varying(64),
    "UPDATED_TIME" timestamp without time zone,
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE td_runtime_task_status (COMMENT)
COMMENT ON TABLE ecos_control.td_runtime_task_status IS 'runtime-task 运行状态表（列清单单源 = TaskPersistenceDao.saveStatus/queryStatus 16 列 + softDeleteTask 的 is_deleted；V152 头注"保持"但从未 CREATE，本脚本补建）';

--

-- td_user (TABLE)
CREATE TABLE ecos_control.td_user (
    user_id character varying(64) NOT NULL,
    username character varying(128) NOT NULL,
    password character varying(512) NOT NULL,
    email character varying(256),
    mobile_tel1 character varying(32),
    status character varying(16) DEFAULT 'ACTIVE' NOT NULL,
    locked character varying(1) DEFAULT '0' NOT NULL,
    lock_time timestamp without time zone,
    last_login_time timestamp without time zone,
    mfa_secret character varying(128),
    mfa_type character varying(16),
    mfa_enabled boolean DEFAULT false NOT NULL,
    tenant_id character varying(64),
    created_time timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64),
    updated_time timestamp without time zone DEFAULT now() NOT NULL,
    updated_by character varying(64),
    real_name character varying(64),
    login_ip character varying(64),
    login_count integer DEFAULT 0 NOT NULL,
    failed_attempts integer DEFAULT 0,
    locked_until timestamp without time zone,
    is_password_change_required boolean DEFAULT false,
    last_password_change timestamp without time zone,
    password_history_json text
);

--

-- TABLE td_user (COMMENT)
COMMENT ON TABLE ecos_control.td_user IS '用户表';

--

-- COLUMN td_user.user_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.user_id IS '用户ID';

--

-- COLUMN td_user.username (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.username IS '用户名';

--

-- COLUMN td_user.password (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.password IS '密码哈希';

--

-- COLUMN td_user.email (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.email IS '邮箱';

--

-- COLUMN td_user.mobile_tel1 (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.mobile_tel1 IS '手机号';

--

-- COLUMN td_user.status (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.status IS '状态: ACTIVE/LOCKED/DISABLED';

--

-- COLUMN td_user.locked (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.locked IS '是否锁定: 0/1';

--

-- COLUMN td_user.lock_time (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.lock_time IS '锁定时间';

--

-- COLUMN td_user.last_login_time (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.last_login_time IS '最后登录时间';

--

-- COLUMN td_user.mfa_secret (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.mfa_secret IS 'MFA密钥';

--

-- COLUMN td_user.mfa_type (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.mfa_type IS 'MFA类型';

--

-- COLUMN td_user.mfa_enabled (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.mfa_enabled IS '是否启用MFA';

--

-- COLUMN td_user.tenant_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.tenant_id IS '租户ID';

--

-- COLUMN td_user.created_time (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.created_time IS '创建时间';

--

-- COLUMN td_user.created_by (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.created_by IS '创建人';

--

-- COLUMN td_user.updated_time (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.updated_time IS '更新时间';

--

-- COLUMN td_user.updated_by (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.updated_by IS '更新人';

--

-- COLUMN td_user.real_name (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.real_name IS '人员姓名（User-sql.xml queryUsers 按名引用；演示数据 seed 回填，非凭据面）';

--

-- COLUMN td_user.login_ip (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.login_ip IS '最近登录 IP（运行结果面，seed 恒空，禁伪造演示值）';

--

-- COLUMN td_user.login_count (COMMENT)
COMMENT ON COLUMN ecos_control.td_user.login_count IS '登录次数（运行结果面，seed 恒 0，禁伪造演示值）';

--

-- td_user_organization (TABLE)
CREATE TABLE ecos_control.td_user_organization (
    user_id character varying(64) NOT NULL,
    org_id character varying(64) NOT NULL,
    is_primary character varying(1) DEFAULT '1',
    created_time timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64)
);

--

-- td_user_role (TABLE)
CREATE TABLE ecos_control.td_user_role (
    user_id character varying(64) NOT NULL,
    role_id character varying(64) NOT NULL,
    org_id character varying(64) DEFAULT '-1',
    created_time timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64)
);

--

-- TABLE td_user_role (COMMENT)
COMMENT ON TABLE ecos_control.td_user_role IS '用户角色关联表';

--

-- COLUMN td_user_role.user_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_user_role.user_id IS '用户ID';

--

-- COLUMN td_user_role.role_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_user_role.role_id IS '角色ID';

--

-- COLUMN td_user_role.org_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_user_role.org_id IS '组织ID';

--

-- td_user_security_profile (TABLE)
CREATE TABLE ecos_control.td_user_security_profile (
    user_id character varying(64) NOT NULL,
    clearance_level integer DEFAULT 0,
    linked_workstation character varying(256),
    audit_mode character varying(32) DEFAULT 'basic',
    sandbox_mandatory boolean DEFAULT false,
    is_default boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    tenant_id character varying(64),
    org_id character varying(64),
    scope_type character varying(16) DEFAULT 'USER'
);

--

-- TABLE td_user_security_profile (COMMENT)
COMMENT ON TABLE ecos_control.td_user_security_profile IS '用户级安全配置表 — 优先级最高，覆盖角色级和全局默认';

--

-- COLUMN td_user_security_profile.user_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_user_security_profile.user_id IS '用户ID，关联 td_user.USER_ID';

--

-- COLUMN td_user_security_profile.clearance_level (COMMENT)
COMMENT ON COLUMN ecos_control.td_user_security_profile.clearance_level IS '准入等级 0-4: L0公开 L1内部 L2保密 L3机密 L4绝密';

--

-- COLUMN td_user_security_profile.linked_workstation (COMMENT)
COMMENT ON COLUMN ecos_control.td_user_security_profile.linked_workstation IS '物理工作站绑定（MAC/IP/主机名）';

--

-- COLUMN td_user_security_profile.audit_mode (COMMENT)
COMMENT ON COLUMN ecos_control.td_user_security_profile.audit_mode IS '审计力度: basic(基本) detailed(详细) full(全量)';

--

-- COLUMN td_user_security_profile.sandbox_mandatory (COMMENT)
COMMENT ON COLUMN ecos_control.td_user_security_profile.sandbox_mandatory IS '是否强制沙盒运行';

--

-- COLUMN td_user_security_profile.is_default (COMMENT)
COMMENT ON COLUMN ecos_control.td_user_security_profile.is_default IS '是否为全局默认配置（仅允许一条记录为TRUE）';

--

-- COLUMN td_user_security_profile.tenant_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_user_security_profile.tenant_id IS '租户ID (scope_type=TENANT时填充)';

--

-- COLUMN td_user_security_profile.org_id (COMMENT)
COMMENT ON COLUMN ecos_control.td_user_security_profile.org_id IS '机构ID (scope_type=ORG时填充)';

--

-- COLUMN td_user_security_profile.scope_type (COMMENT)
COMMENT ON COLUMN ecos_control.td_user_security_profile.scope_type IS '作用域类型: GLOBAL/TENANT/ORG/ROLE/USER';

--

-- ecos_alert_history id (DEFAULT)
ALTER TABLE ONLY ecos_control.ecos_alert_history ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_alert_history_id_seq'::regclass);

--

-- ecos_audit_log id (DEFAULT)
ALTER TABLE ONLY ecos_control.ecos_audit_log ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_audit_log_id_seq'::regclass);

--

-- ecos_cognitive_rule id (DEFAULT)
ALTER TABLE ONLY ecos_control.ecos_cognitive_rule ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_cognitive_rule_id_seq'::regclass);

--

-- ecos_dq_issue id (DEFAULT)
ALTER TABLE ONLY ecos_control.ecos_dq_issue ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_dq_issue_id_seq'::regclass);

--

-- ecos_dq_rule id (DEFAULT)
ALTER TABLE ONLY ecos_control.ecos_dq_rule ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_dq_rule_id_seq'::regclass);

--

-- ecos_function_audit_log id (DEFAULT)
ALTER TABLE ONLY ecos_control.ecos_function_audit_log ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_function_audit_log_id_seq'::regclass);

--

-- ecos_glossary_term id (DEFAULT)
ALTER TABLE ONLY ecos_control.ecos_glossary_term ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_glossary_term_id_seq'::regclass);

--

-- ecos_marketplace_access_request id (DEFAULT)
ALTER TABLE ONLY ecos_control.ecos_marketplace_access_request ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_marketplace_access_request_id_seq'::regclass);

--

-- ecos_marketplace_asset id (DEFAULT)
ALTER TABLE ONLY ecos_control.ecos_marketplace_asset ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_marketplace_asset_id_seq'::regclass);

--

-- ecos_task id (DEFAULT)
ALTER TABLE ONLY ecos_control.ecos_task ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_task_id_seq'::regclass);

--

-- ecos_tenant_quota id (DEFAULT)
ALTER TABLE ONLY ecos_control.ecos_tenant_quota ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_tenant_quota_id_seq'::regclass);

--

-- ecos_token_usage id (DEFAULT)
ALTER TABLE ONLY ecos_control.ecos_token_usage ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_token_usage_id_seq'::regclass);

--

-- ecos_wm_causal_link id (DEFAULT)
ALTER TABLE ONLY ecos_control.ecos_wm_causal_link ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_wm_causal_link_id_seq'::regclass);

--

-- ecos_wm_goal id (DEFAULT)
ALTER TABLE ONLY ecos_control.ecos_wm_goal ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_wm_goal_id_seq'::regclass);

--

-- ecos_wm_goal_log id (DEFAULT)
ALTER TABLE ONLY ecos_control.ecos_wm_goal_log ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_wm_goal_log_id_seq'::regclass);

--

-- ecos_wm_scenario id (DEFAULT)
ALTER TABLE ONLY ecos_control.ecos_wm_scenario ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_wm_scenario_id_seq'::regclass);

--

-- kb_cognitive_pipeline id (DEFAULT)
ALTER TABLE ONLY ecos_control.kb_cognitive_pipeline ALTER COLUMN id SET DEFAULT nextval('ecos_control.kb_cognitive_pipeline_id_seq'::regclass);

--

-- kb_lineage_event id (DEFAULT)
ALTER TABLE ONLY ecos_control.kb_lineage_event ALTER COLUMN id SET DEFAULT nextval('ecos_control.kb_lineage_event_id_seq'::regclass);

--

-- kb_ontology_snapshot id (DEFAULT)
ALTER TABLE ONLY ecos_control.kb_ontology_snapshot ALTER COLUMN id SET DEFAULT nextval('ecos_control.kb_ontology_snapshot_id_seq'::regclass);

--

-- schema_changes id (DEFAULT)
ALTER TABLE ONLY ecos_control.schema_changes ALTER COLUMN id SET DEFAULT nextval('ecos_control.schema_changes_id_seq'::regclass);

--

-- schema_snapshots id (DEFAULT)
ALTER TABLE ONLY ecos_control.schema_snapshots ALTER COLUMN id SET DEFAULT nextval('ecos_control.schema_snapshots_id_seq'::regclass);

--

-- sys_agent_message id (DEFAULT)
ALTER TABLE ONLY ecos_control.sys_agent_message ALTER COLUMN id SET DEFAULT nextval('ecos_control.sys_agent_message_id_seq'::regclass);

--

-- td_metadata_collect_log id (DEFAULT)
ALTER TABLE ONLY ecos_control.td_metadata_collect_log ALTER COLUMN id SET DEFAULT nextval('ecos_control.td_metadata_collect_log_id_seq'::regclass);

--

-- td_runtime_task_log LOG_SEQ (DEFAULT)
ALTER TABLE ONLY ecos_control.td_runtime_task_log ALTER COLUMN "LOG_SEQ" SET DEFAULT nextval('ecos_control."td_runtime_task_log_LOG_SEQ_seq"'::regclass);

--

-- demo_customer demo_customer_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.demo_customer
    ADD CONSTRAINT demo_customer_pkey PRIMARY KEY (id);

--

-- demo_invoice demo_invoice_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.demo_invoice
    ADD CONSTRAINT demo_invoice_pkey PRIMARY KEY (id);

--

-- demo_supplier demo_supplier_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.demo_supplier
    ADD CONSTRAINT demo_supplier_pkey PRIMARY KEY (id);

--

-- dict_column dict_column_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.dict_column
    ADD CONSTRAINT dict_column_pkey PRIMARY KEY (id);

--

-- dict_table dict_table_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.dict_table
    ADD CONSTRAINT dict_table_pkey PRIMARY KEY (id);

--

-- ecos_agent ecos_agent_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_agent
    ADD CONSTRAINT ecos_agent_pkey PRIMARY KEY (id);

--

-- ecos_alert_history ecos_alert_history_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_alert_history
    ADD CONSTRAINT ecos_alert_history_pkey PRIMARY KEY (id);

--

-- ecos_audit_log ecos_audit_log_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_audit_log
    ADD CONSTRAINT ecos_audit_log_pkey PRIMARY KEY (id);

--

-- ecos_business_scenario ecos_business_scenario_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_business_scenario
    ADD CONSTRAINT ecos_business_scenario_pkey PRIMARY KEY (id);

--

-- ecos_cls_policy ecos_cls_policy_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_cls_policy
    ADD CONSTRAINT ecos_cls_policy_pkey PRIMARY KEY (id);

--

-- ecos_cognitive_belief ecos_cognitive_belief_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_cognitive_belief
    ADD CONSTRAINT ecos_cognitive_belief_pkey PRIMARY KEY (id);

--

-- ecos_cognitive_evidence ecos_cognitive_evidence_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_cognitive_evidence
    ADD CONSTRAINT ecos_cognitive_evidence_pkey PRIMARY KEY (id);

--

-- ecos_cognitive_hypothesis ecos_cognitive_hypothesis_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_cognitive_hypothesis
    ADD CONSTRAINT ecos_cognitive_hypothesis_pkey PRIMARY KEY (id);

--

-- ecos_cognitive_model ecos_cognitive_model_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_cognitive_model
    ADD CONSTRAINT ecos_cognitive_model_pkey PRIMARY KEY (id);

--

-- ecos_cognitive_rule ecos_cognitive_rule_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_cognitive_rule
    ADD CONSTRAINT ecos_cognitive_rule_pkey PRIMARY KEY (id);

--

-- ecos_cognitive_run_invalidation ecos_cognitive_run_invalidation_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_cognitive_run_invalidation
    ADD CONSTRAINT ecos_cognitive_run_invalidation_pkey PRIMARY KEY (id);

--

-- ecos_decision_approval ecos_decision_approval_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_decision_approval
    ADD CONSTRAINT ecos_decision_approval_pkey PRIMARY KEY (id);

--

-- ecos_decision_causal_link ecos_decision_causal_link_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_decision_causal_link
    ADD CONSTRAINT ecos_decision_causal_link_pkey PRIMARY KEY (id);

--

-- ecos_decision_exception ecos_decision_exception_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_decision_exception
    ADD CONSTRAINT ecos_decision_exception_pkey PRIMARY KEY (id);

--

-- ecos_decision ecos_decision_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_decision
    ADD CONSTRAINT ecos_decision_pkey PRIMARY KEY (id);

--

-- ecos_decision_policy ecos_decision_policy_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_decision_policy
    ADD CONSTRAINT ecos_decision_policy_pkey PRIMARY KEY (id);

--

-- ecos_decision_precedent ecos_decision_precedent_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_decision_precedent
    ADD CONSTRAINT ecos_decision_precedent_pkey PRIMARY KEY (id);

--

-- ecos_decision_record ecos_decision_record_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_decision_record
    ADD CONSTRAINT ecos_decision_record_pkey PRIMARY KEY (id);

--

-- ecos_dq_execution_result ecos_dq_execution_result_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_dq_execution_result
    ADD CONSTRAINT ecos_dq_execution_result_pkey PRIMARY KEY (id);

--

-- ecos_dq_issue ecos_dq_issue_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_dq_issue
    ADD CONSTRAINT ecos_dq_issue_pkey PRIMARY KEY (id);

--

-- ecos_dq_rule ecos_dq_rule_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_dq_rule
    ADD CONSTRAINT ecos_dq_rule_pkey PRIMARY KEY (id);

--

-- ecos_function_audit_log ecos_function_audit_log_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_function_audit_log
    ADD CONSTRAINT ecos_function_audit_log_pkey PRIMARY KEY (id);

--

-- ecos_git_repo ecos_git_repo_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_git_repo
    ADD CONSTRAINT ecos_git_repo_pkey PRIMARY KEY (id);

--

-- ecos_glossary_term ecos_glossary_term_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_glossary_term
    ADD CONSTRAINT ecos_glossary_term_pkey PRIMARY KEY (id);

--

-- ecos_goal_tracking ecos_goal_tracking_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_goal_tracking
    ADD CONSTRAINT ecos_goal_tracking_pkey PRIMARY KEY (id);

--

-- ecos_interface_ref ecos_interface_ref_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_interface_ref
    ADD CONSTRAINT ecos_interface_ref_pkey PRIMARY KEY (id);

--

-- ecos_marketplace_access_request ecos_marketplace_access_request_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_marketplace_access_request
    ADD CONSTRAINT ecos_marketplace_access_request_pkey PRIMARY KEY (id);

--

-- ecos_marketplace_asset ecos_marketplace_asset_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_marketplace_asset
    ADD CONSTRAINT ecos_marketplace_asset_pkey PRIMARY KEY (id);

--

-- ecos_mask_strategy ecos_mask_strategy_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_mask_strategy
    ADD CONSTRAINT ecos_mask_strategy_pkey PRIMARY KEY (id);

--

-- ecos_object_attachment ecos_object_attachment_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_object_attachment
    ADD CONSTRAINT ecos_object_attachment_pkey PRIMARY KEY (id);

--

-- ecos_object_links ecos_object_links_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_object_links
    ADD CONSTRAINT ecos_object_links_pkey PRIMARY KEY (id);

--

-- ecos_object_relationship ecos_object_relationship_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_object_relationship
    ADD CONSTRAINT ecos_object_relationship_pkey PRIMARY KEY (id);

--

-- ecos_object_state_machine ecos_object_state_machine_entity_code_from_status_transitio_key (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_object_state_machine
    ADD CONSTRAINT ecos_object_state_machine_entity_code_from_status_transitio_key UNIQUE (entity_code, from_status, transition_code);

--

-- ecos_object_state_machine ecos_object_state_machine_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_object_state_machine
    ADD CONSTRAINT ecos_object_state_machine_pkey PRIMARY KEY (id);

--

-- ecos_object_timeline ecos_object_timeline_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_object_timeline
    ADD CONSTRAINT ecos_object_timeline_pkey PRIMARY KEY (id);

--

-- ecos_ontology_action ecos_ontology_action_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_ontology_action
    ADD CONSTRAINT ecos_ontology_action_pkey PRIMARY KEY (id);

--

-- ecos_ontology_entity ecos_ontology_entity_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_ontology_entity
    ADD CONSTRAINT ecos_ontology_entity_pkey PRIMARY KEY (id);

--

-- ecos_ontology_property ecos_ontology_property_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_ontology_property
    ADD CONSTRAINT ecos_ontology_property_pkey PRIMARY KEY (id);

--

-- ecos_ontology_relationship ecos_ontology_relationship_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_ontology_relationship
    ADD CONSTRAINT ecos_ontology_relationship_pkey PRIMARY KEY (id);

--

-- ecos_pareto_problem ecos_pareto_problem_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_pareto_problem
    ADD CONSTRAINT ecos_pareto_problem_pkey PRIMARY KEY (id);

--

-- ecos_pipeline_edge ecos_pipeline_edge_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_pipeline_edge
    ADD CONSTRAINT ecos_pipeline_edge_pkey PRIMARY KEY (id);

--

-- ecos_pipeline_node ecos_pipeline_node_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_pipeline_node
    ADD CONSTRAINT ecos_pipeline_node_pkey PRIMARY KEY (id);

--

-- ecos_provenance_entry ecos_provenance_entry_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_provenance_entry
    ADD CONSTRAINT ecos_provenance_entry_pkey PRIMARY KEY (id);

--

-- ecos_rls_policy ecos_rls_policy_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_rls_policy
    ADD CONSTRAINT ecos_rls_policy_pkey PRIMARY KEY (id);

--

-- ecos_runtime_alert_record ecos_runtime_alert_record_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_runtime_alert_record
    ADD CONSTRAINT ecos_runtime_alert_record_pkey PRIMARY KEY (id);

--

-- ecos_runtime_alert_rule ecos_runtime_alert_rule_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_runtime_alert_rule
    ADD CONSTRAINT ecos_runtime_alert_rule_pkey PRIMARY KEY (id);

--

-- ecos_runtime_audit_retry ecos_runtime_audit_retry_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_runtime_audit_retry
    ADD CONSTRAINT ecos_runtime_audit_retry_pkey PRIMARY KEY (id);

--

-- ecos_runtime_event_dlq ecos_runtime_event_dlq_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_runtime_event_dlq
    ADD CONSTRAINT ecos_runtime_event_dlq_pkey PRIMARY KEY (id);

--

-- ecos_scenario_asset_binding ecos_scenario_asset_binding_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_scenario_asset_binding
    ADD CONSTRAINT ecos_scenario_asset_binding_pkey PRIMARY KEY (id);

--

-- ecos_scenario_binding_edge ecos_scenario_binding_edge_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_scenario_binding_edge
    ADD CONSTRAINT ecos_scenario_binding_edge_pkey PRIMARY KEY (id);

--

-- ecos_scenario_binding_link ecos_scenario_binding_link_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_scenario_binding_link
    ADD CONSTRAINT ecos_scenario_binding_link_pkey PRIMARY KEY (id);

--

-- ecos_scenario_binding ecos_scenario_binding_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_scenario_binding
    ADD CONSTRAINT ecos_scenario_binding_pkey PRIMARY KEY (id);

--

-- ecos_scenario_blueprint ecos_scenario_blueprint_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_scenario_blueprint
    ADD CONSTRAINT ecos_scenario_blueprint_pkey PRIMARY KEY (id);

--

-- ecos_scenario_canvas_layout ecos_scenario_canvas_layout_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_scenario_canvas_layout
    ADD CONSTRAINT ecos_scenario_canvas_layout_pkey PRIMARY KEY (id);

--

-- ecos_scenario_decision_record ecos_scenario_decision_record_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_scenario_decision_record
    ADD CONSTRAINT ecos_scenario_decision_record_pkey PRIMARY KEY (id);

--

-- ecos_scenario_definition ecos_scenario_definition_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_scenario_definition
    ADD CONSTRAINT ecos_scenario_definition_pkey PRIMARY KEY (id);

--

-- ecos_scenario_execution ecos_scenario_execution_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_scenario_execution
    ADD CONSTRAINT ecos_scenario_execution_pkey PRIMARY KEY (id);

--

-- ecos_scenario_mind ecos_scenario_mind_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_scenario_mind
    ADD CONSTRAINT ecos_scenario_mind_pkey PRIMARY KEY (id);

--

-- ecos_scenario_mind_variant ecos_scenario_mind_variant_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_scenario_mind_variant
    ADD CONSTRAINT ecos_scenario_mind_variant_pkey PRIMARY KEY (id);

--

-- ecos_scenario_query_history ecos_scenario_query_history_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_scenario_query_history
    ADD CONSTRAINT ecos_scenario_query_history_pkey PRIMARY KEY (id);

--

-- ecos_scenario_run ecos_scenario_run_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_scenario_run
    ADD CONSTRAINT ecos_scenario_run_pkey PRIMARY KEY (id);

--

-- ecos_scenario_sandbox_layout ecos_scenario_sandbox_layout_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_scenario_sandbox_layout
    ADD CONSTRAINT ecos_scenario_sandbox_layout_pkey PRIMARY KEY (id);

--

-- ecos_scenario_sandbox_layout ecos_scenario_sandbox_layout_scenario_id_key (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_scenario_sandbox_layout
    ADD CONSTRAINT ecos_scenario_sandbox_layout_scenario_id_key UNIQUE (scenario_id);

--

-- ecos_security_asset ecos_security_asset_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_security_asset
    ADD CONSTRAINT ecos_security_asset_pkey PRIMARY KEY (id);

--

-- ecos_security_crypto_key ecos_security_crypto_key_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_security_crypto_key
    ADD CONSTRAINT ecos_security_crypto_key_pkey PRIMARY KEY (id);

--

-- ecos_security_policy ecos_security_policy_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_security_policy
    ADD CONSTRAINT ecos_security_policy_pkey PRIMARY KEY (id);

--

-- ecos_spans ecos_spans_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_spans
    ADD CONSTRAINT ecos_spans_pkey PRIMARY KEY (span_id);

--

-- ecos_task ecos_task_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_task
    ADD CONSTRAINT ecos_task_pkey PRIMARY KEY (id);

--

-- ecos_tenant ecos_tenant_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_tenant
    ADD CONSTRAINT ecos_tenant_pkey PRIMARY KEY (id);

--

-- ecos_tenant_quota ecos_tenant_quota_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_tenant_quota
    ADD CONSTRAINT ecos_tenant_quota_pkey PRIMARY KEY (id);

--

-- ecos_tenant ecos_tenant_tenant_code_key (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_tenant
    ADD CONSTRAINT ecos_tenant_tenant_code_key UNIQUE (tenant_code);

--

-- ecos_tenant_usage ecos_tenant_usage_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_tenant_usage
    ADD CONSTRAINT ecos_tenant_usage_pkey PRIMARY KEY (tenant_id, usage_date, quota_type);

--

-- ecos_token_usage ecos_token_usage_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_token_usage
    ADD CONSTRAINT ecos_token_usage_pkey PRIMARY KEY (id);

--

-- ecos_warn_log ecos_warn_log_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_warn_log
    ADD CONSTRAINT ecos_warn_log_pkey PRIMARY KEY (id);

--

-- ecos_wm_causal_link ecos_wm_causal_link_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_wm_causal_link
    ADD CONSTRAINT ecos_wm_causal_link_pkey PRIMARY KEY (id);

--

-- ecos_wm_goal_log ecos_wm_goal_log_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_wm_goal_log
    ADD CONSTRAINT ecos_wm_goal_log_pkey PRIMARY KEY (id);

--

-- ecos_wm_goal ecos_wm_goal_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_wm_goal
    ADD CONSTRAINT ecos_wm_goal_pkey PRIMARY KEY (id);

--

-- ecos_wm_scenario ecos_wm_scenario_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_wm_scenario
    ADD CONSTRAINT ecos_wm_scenario_pkey PRIMARY KEY (id);

--

-- ecos_workflow_approval ecos_workflow_approval_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_workflow_approval
    ADD CONSTRAINT ecos_workflow_approval_pkey PRIMARY KEY (id);

--

-- ecos_workflow_instance ecos_workflow_instance_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_workflow_instance
    ADD CONSTRAINT ecos_workflow_instance_pkey PRIMARY KEY (id);

--

-- ecos_workflow_log ecos_workflow_log_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_workflow_log
    ADD CONSTRAINT ecos_workflow_log_pkey PRIMARY KEY (id);

--

-- ecos_workflow ecos_workflow_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_workflow
    ADD CONSTRAINT ecos_workflow_pkey PRIMARY KEY (id);

--

-- ecos_workflow_task ecos_workflow_task_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_workflow_task
    ADD CONSTRAINT ecos_workflow_task_pkey PRIMARY KEY (id);

--

-- ecos_world_scenarios ecos_world_scenarios_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_world_scenarios
    ADD CONSTRAINT ecos_world_scenarios_pkey PRIMARY KEY (id);

--

-- kb_cognitive_pipeline kb_cognitive_pipeline_pipeline_id_key (CONSTRAINT)
ALTER TABLE ONLY ecos_control.kb_cognitive_pipeline
    ADD CONSTRAINT kb_cognitive_pipeline_pipeline_id_key UNIQUE (pipeline_id);

--

-- kb_cognitive_pipeline kb_cognitive_pipeline_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.kb_cognitive_pipeline
    ADD CONSTRAINT kb_cognitive_pipeline_pkey PRIMARY KEY (id);

--

-- kb_lineage_event kb_lineage_event_event_id_key (CONSTRAINT)
ALTER TABLE ONLY ecos_control.kb_lineage_event
    ADD CONSTRAINT kb_lineage_event_event_id_key UNIQUE (event_id);

--

-- kb_lineage_event kb_lineage_event_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.kb_lineage_event
    ADD CONSTRAINT kb_lineage_event_pkey PRIMARY KEY (id);

--

-- kb_ontology_snapshot kb_ontology_snapshot_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.kb_ontology_snapshot
    ADD CONSTRAINT kb_ontology_snapshot_pkey PRIMARY KEY (id);

--

-- cache_invalidation_outbox pk_cache_invalidation_outbox (CONSTRAINT)
ALTER TABLE ONLY ecos_control.cache_invalidation_outbox
    ADD CONSTRAINT pk_cache_invalidation_outbox PRIMARY KEY (id);

--

-- schema_changes schema_changes_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.schema_changes
    ADD CONSTRAINT schema_changes_pkey PRIMARY KEY (id);

--

-- schema_snapshots schema_snapshots_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.schema_snapshots
    ADD CONSTRAINT schema_snapshots_pkey PRIMARY KEY (id);

--

-- sys_agent_message sys_agent_message_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.sys_agent_message
    ADD CONSTRAINT sys_agent_message_pkey PRIMARY KEY (id);

--

-- sys_agent_session sys_agent_session_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.sys_agent_session
    ADD CONSTRAINT sys_agent_session_pkey PRIMARY KEY (id);

--

-- sys_compliance_rule sys_compliance_rule_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.sys_compliance_rule
    ADD CONSTRAINT sys_compliance_rule_pkey PRIMARY KEY (id);

--

-- sys_config sys_config_config_key_key (CONSTRAINT)
ALTER TABLE ONLY ecos_control.sys_config
    ADD CONSTRAINT sys_config_config_key_key UNIQUE (config_key);

--

-- sys_config sys_config_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.sys_config
    ADD CONSTRAINT sys_config_pkey PRIMARY KEY (id);

--

-- sys_dict sys_dict_dict_type_dict_code_key (CONSTRAINT)
ALTER TABLE ONLY ecos_control.sys_dict
    ADD CONSTRAINT sys_dict_dict_type_dict_code_key UNIQUE (dict_type, dict_code);

--

-- sys_dict sys_dict_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.sys_dict
    ADD CONSTRAINT sys_dict_pkey PRIMARY KEY (id);

--

-- sys_extraction_source sys_extraction_source_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.sys_extraction_source
    ADD CONSTRAINT sys_extraction_source_pkey PRIMARY KEY (id);

--

-- sys_token_blacklist sys_token_blacklist_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.sys_token_blacklist
    ADD CONSTRAINT sys_token_blacklist_pkey PRIMARY KEY (token);

--

-- td_abac_policy td_abac_policy_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_abac_policy
    ADD CONSTRAINT td_abac_policy_pkey PRIMARY KEY (policy_id);

--

-- td_audit_log td_audit_log_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_audit_log
    ADD CONSTRAINT td_audit_log_pkey PRIMARY KEY (log_id);

--

-- td_catalog_item td_catalog_item_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_catalog_item
    ADD CONSTRAINT td_catalog_item_pkey PRIMARY KEY (catalog_id);

--

-- td_config td_config_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_config
    ADD CONSTRAINT td_config_pkey PRIMARY KEY (config_id);

--

-- td_data_field td_data_field_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_data_field
    ADD CONSTRAINT td_data_field_pkey PRIMARY KEY (field_id);

--

-- td_data_resource td_data_resource_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_data_resource
    ADD CONSTRAINT td_data_resource_pkey PRIMARY KEY (resource_id);

--

-- td_datasource td_datasource_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_datasource
    ADD CONSTRAINT td_datasource_pkey PRIMARY KEY (datasource_id);

--

-- td_dw_write_channel_idempotency td_dw_write_channel_idempotency_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_dw_write_channel_idempotency
    ADD CONSTRAINT td_dw_write_channel_idempotency_pkey PRIMARY KEY (id);

--

-- td_metadata_collect_log td_metadata_collect_log_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_metadata_collect_log
    ADD CONSTRAINT td_metadata_collect_log_pkey PRIMARY KEY (id);

--

-- td_org_permission td_org_permission_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_org_permission
    ADD CONSTRAINT td_org_permission_pkey PRIMARY KEY (permission_id);

--

-- td_organization td_organization_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_organization
    ADD CONSTRAINT td_organization_pkey PRIMARY KEY (org_id);

--

-- td_permission td_permission_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_permission
    ADD CONSTRAINT td_permission_pkey PRIMARY KEY (permission_id);

--

-- td_role_permission td_role_permission_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_role_permission
    ADD CONSTRAINT td_role_permission_pkey PRIMARY KEY (role_id, permission_id);

--

-- td_role td_role_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_role
    ADD CONSTRAINT td_role_pkey PRIMARY KEY (role_id);

--

-- td_role_security_profile td_role_security_profile_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_role_security_profile
    ADD CONSTRAINT td_role_security_profile_pkey PRIMARY KEY (role_id);

--

-- td_runtime_task_execution td_runtime_task_execution_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_runtime_task_execution
    ADD CONSTRAINT td_runtime_task_execution_pkey PRIMARY KEY ("EXECUTION_ID");

--

-- td_runtime_task_log td_runtime_task_log_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_runtime_task_log
    ADD CONSTRAINT td_runtime_task_log_pkey PRIMARY KEY ("LOG_SEQ");

--

-- td_runtime_task td_runtime_task_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_runtime_task
    ADD CONSTRAINT td_runtime_task_pkey PRIMARY KEY ("TASK_ID");

--

-- td_runtime_task_plan td_runtime_task_plan_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_runtime_task_plan
    ADD CONSTRAINT td_runtime_task_plan_pkey PRIMARY KEY ("TASK_ID");

--

-- td_runtime_task_status td_runtime_task_status_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_runtime_task_status
    ADD CONSTRAINT td_runtime_task_status_pkey PRIMARY KEY ("TASK_ID");

--

-- td_user_organization td_user_organization_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_user_organization
    ADD CONSTRAINT td_user_organization_pkey PRIMARY KEY (user_id, org_id);

--

-- td_user td_user_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_user
    ADD CONSTRAINT td_user_pkey PRIMARY KEY (user_id);

--

-- td_user_role td_user_role_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_user_role
    ADD CONSTRAINT td_user_role_pkey PRIMARY KEY (user_id, role_id);

--

-- td_user_security_profile td_user_security_profile_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_control.td_user_security_profile
    ADD CONSTRAINT td_user_security_profile_pkey PRIMARY KEY (user_id);

--

-- ecos_mask_strategy uk_mask_strategy (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_mask_strategy
    ADD CONSTRAINT uk_mask_strategy UNIQUE (tenant_id, strategy_code);

--

-- ecos_interface_ref uq_ifc_ref_type_name (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_interface_ref
    ADD CONSTRAINT uq_ifc_ref_type_name UNIQUE (interface_type, name);

--

-- kb_ontology_snapshot uq_kb_ontology_snapshot_ontology_version (CONSTRAINT)
ALTER TABLE ONLY ecos_control.kb_ontology_snapshot
    ADD CONSTRAINT uq_kb_ontology_snapshot_ontology_version UNIQUE (ontology_id, version);

--

-- ecos_security_policy uq_sec_policy_domain_name (CONSTRAINT)
ALTER TABLE ONLY ecos_control.ecos_security_policy
    ADD CONSTRAINT uq_sec_policy_domain_name UNIQUE (domain, name);

--

-- idx_abac_scope (INDEX)
CREATE INDEX idx_abac_scope ON ecos_control.td_abac_policy USING btree (scope_type, scope_id);

--

-- idx_access_request_asset_id (INDEX)
CREATE INDEX idx_access_request_asset_id ON ecos_control.ecos_marketplace_access_request USING btree (asset_id);

--

-- idx_access_request_status (INDEX)
CREATE INDEX idx_access_request_status ON ecos_control.ecos_marketplace_access_request USING btree (status);

--

-- idx_agent_message_session (INDEX)
CREATE INDEX idx_agent_message_session ON ecos_control.sys_agent_message USING btree (session_id);

--

-- idx_agent_message_thread (INDEX)
CREATE INDEX idx_agent_message_thread ON ecos_control.sys_agent_message USING btree (session_id, thread_id);

--

-- idx_agent_session_agent (INDEX)
CREATE INDEX idx_agent_session_agent ON ecos_control.sys_agent_session USING btree (agent_id);

--

-- idx_agent_session_status (INDEX)
CREATE INDEX idx_agent_session_status ON ecos_control.sys_agent_session USING btree (status);

--

-- idx_alert_record_status (INDEX)
CREATE INDEX idx_alert_record_status ON ecos_control.ecos_runtime_alert_record USING btree (status, occurred_at);

--

-- idx_audit_change_request (INDEX)
CREATE INDEX idx_audit_change_request ON ecos_control.ecos_audit_log USING btree (change_request_id);

--

-- idx_audit_event_id (INDEX)
CREATE INDEX idx_audit_event_id ON ecos_control.ecos_audit_log USING btree (audit_event_id);

--

-- idx_audit_event_type (INDEX)
CREATE INDEX idx_audit_event_type ON ecos_control.td_audit_log USING btree (event_type);

--

-- idx_audit_git_commit (INDEX)
CREATE INDEX idx_audit_git_commit ON ecos_control.ecos_audit_log USING btree (git_commit_sha);

--

-- idx_audit_result (INDEX)
CREATE INDEX idx_audit_result ON ecos_control.td_audit_log USING btree (result);

--

-- idx_audit_timestamp (INDEX)
CREATE INDEX idx_audit_timestamp ON ecos_control.td_audit_log USING btree ("timestamp" DESC);

--

-- idx_audit_trace (INDEX)
CREATE INDEX idx_audit_trace ON ecos_control.ecos_audit_log USING btree (trace_id);

--

-- idx_audit_user (INDEX)
CREATE INDEX idx_audit_user ON ecos_control.td_audit_log USING btree (user_id);

--

-- idx_biz_scenario_dept (INDEX)
CREATE INDEX idx_biz_scenario_dept ON ecos_control.ecos_business_scenario USING btree (department);

--

-- idx_biz_scenario_status (INDEX)
CREATE INDEX idx_biz_scenario_status ON ecos_control.ecos_business_scenario USING btree (status);

--

-- idx_bsl_sco (INDEX)
CREATE INDEX idx_bsl_sco ON ecos_control.ecos_scenario_binding_link USING btree (scenario_id);

--

-- idx_bsl_src (INDEX)
CREATE INDEX idx_bsl_src ON ecos_control.ecos_scenario_binding_link USING btree (source_binding_id);

--

-- idx_bsl_tgt (INDEX)
CREATE INDEX idx_bsl_tgt ON ecos_control.ecos_scenario_binding_link USING btree (target_binding_id);

--

-- idx_bsl_typ (INDEX)
CREATE INDEX idx_bsl_typ ON ecos_control.ecos_scenario_binding_link USING btree (link_type);

--

-- idx_business_scenario_deleted (INDEX)
CREATE INDEX idx_business_scenario_deleted ON ecos_control.ecos_business_scenario USING btree (is_deleted);

--

-- idx_cache_invalidation_outbox_pending (INDEX)
CREATE INDEX idx_cache_invalidation_outbox_pending ON ecos_control.cache_invalidation_outbox USING btree (status, create_time);

--

-- idx_catalog_res (INDEX)
CREATE INDEX idx_catalog_res ON ecos_control.td_catalog_item USING btree (resource_id);

--

-- idx_catalog_type (INDEX)
CREATE INDEX idx_catalog_type ON ecos_control.td_catalog_item USING btree (resource_type);

--

-- idx_causal_source (INDEX)
CREATE INDEX idx_causal_source ON ecos_control.ecos_decision_causal_link USING btree (source_decision_id);

--

-- idx_causal_target (INDEX)
CREATE INDEX idx_causal_target ON ecos_control.ecos_decision_causal_link USING btree (target_decision_id);

--

-- idx_cls_policy_resource (INDEX)
CREATE INDEX idx_cls_policy_resource ON ecos_control.ecos_cls_policy USING btree (resource_id);

--

-- idx_cls_policy_table (INDEX)
CREATE INDEX idx_cls_policy_table ON ecos_control.ecos_cls_policy USING btree (table_name, scope_type, enabled);

--

-- idx_cog_model_status (INDEX)
CREATE INDEX idx_cog_model_status ON ecos_control.ecos_cognitive_model USING btree (status);

--

-- idx_cog_model_type (INDEX)
CREATE INDEX idx_cog_model_type ON ecos_control.ecos_cognitive_model USING btree (model_type);

--

-- idx_cog_rule_enabled (INDEX)
CREATE INDEX idx_cog_rule_enabled ON ecos_control.ecos_cognitive_rule USING btree (enabled);

--

-- idx_cog_rule_type (INDEX)
CREATE INDEX idx_cog_rule_type ON ecos_control.ecos_cognitive_rule USING btree (rule_type);

--

-- idx_compliance_rule_domain (INDEX)
CREATE INDEX idx_compliance_rule_domain ON ecos_control.sys_compliance_rule USING btree (domain);

--

-- idx_compliance_rule_status (INDEX)
CREATE INDEX idx_compliance_rule_status ON ecos_control.sys_compliance_rule USING btree (status);

--

-- idx_data_field_res (INDEX)
CREATE INDEX idx_data_field_res ON ecos_control.td_data_field USING btree (resource_id);

--

-- idx_data_res_ds (INDEX)
CREATE INDEX idx_data_res_ds ON ecos_control.td_data_resource USING btree (datasource_id);

--

-- idx_decision_category (INDEX)
CREATE INDEX idx_decision_category ON ecos_control.ecos_decision USING btree (category);

--

-- idx_decision_mind (INDEX)
CREATE INDEX idx_decision_mind ON ecos_control.ecos_decision_record USING btree (mind_id, is_deleted);

--

-- idx_decision_scenario (INDEX)
CREATE INDEX idx_decision_scenario ON ecos_control.ecos_decision_record USING btree (scenario_id, is_deleted);

--

-- idx_dict_column_table_id (INDEX)
CREATE INDEX idx_dict_column_table_id ON ecos_control.dict_column USING btree (table_id);

--

-- idx_dict_table_status (INDEX)
CREATE INDEX idx_dict_table_status ON ecos_control.dict_table USING btree (status);

--

-- idx_dq_exec_executed_at (INDEX)
CREATE INDEX idx_dq_exec_executed_at ON ecos_control.ecos_dq_execution_result USING btree (executed_at DESC);

--

-- idx_dq_exec_rule_id (INDEX)
CREATE INDEX idx_dq_exec_rule_id ON ecos_control.ecos_dq_execution_result USING btree (rule_id);

--

-- idx_dq_rule_tenant (INDEX)
CREATE INDEX idx_dq_rule_tenant ON ecos_control.ecos_dq_rule USING btree (tenant_id);

--

-- idx_dw_wc_idem_layer (INDEX)
CREATE INDEX idx_dw_wc_idem_layer ON ecos_control.td_dw_write_channel_idempotency USING btree (layer);

--

-- idx_dw_wc_idem_trace (INDEX)
CREATE INDEX idx_dw_wc_idem_trace ON ecos_control.td_dw_write_channel_idempotency USING btree (trace_id);

--

-- idx_ecos_cog_run_inv_hyp (INDEX)
CREATE INDEX idx_ecos_cog_run_inv_hyp ON ecos_control.ecos_cognitive_run_invalidation USING btree (hypothesis_id);

--

-- idx_ecos_cog_run_inv_run (INDEX)
CREATE INDEX idx_ecos_cog_run_inv_run ON ecos_control.ecos_cognitive_run_invalidation USING btree (run_id);

--

-- idx_ecos_cog_run_inv_time (INDEX)
CREATE INDEX idx_ecos_cog_run_inv_time ON ecos_control.ecos_cognitive_run_invalidation USING btree (superseded_at DESC);

--

-- idx_ecos_cognitive_belief_domain (INDEX)
CREATE INDEX idx_ecos_cognitive_belief_domain ON ecos_control.ecos_cognitive_belief USING btree (domain);

--

-- idx_ecos_cognitive_belief_time (INDEX)
CREATE INDEX idx_ecos_cognitive_belief_time ON ecos_control.ecos_cognitive_belief USING btree (update_time DESC);

--

-- idx_ecos_cognitive_evidence_source (INDEX)
CREATE INDEX idx_ecos_cognitive_evidence_source ON ecos_control.ecos_cognitive_evidence USING btree (source_type);

--

-- idx_ecos_cognitive_evidence_status (INDEX)
CREATE INDEX idx_ecos_cognitive_evidence_status ON ecos_control.ecos_cognitive_evidence USING btree (status);

--

-- idx_ecos_cognitive_evidence_time (INDEX)
CREATE INDEX idx_ecos_cognitive_evidence_time ON ecos_control.ecos_cognitive_evidence USING btree (create_time DESC);

--

-- idx_ecos_cognitive_hypothesis_domain (INDEX)
CREATE INDEX idx_ecos_cognitive_hypothesis_domain ON ecos_control.ecos_cognitive_hypothesis USING btree (domain);

--

-- idx_ecos_cognitive_hypothesis_status (INDEX)
CREATE INDEX idx_ecos_cognitive_hypothesis_status ON ecos_control.ecos_cognitive_hypothesis USING btree (status);

--

-- idx_ecos_cognitive_hypothesis_time (INDEX)
CREATE INDEX idx_ecos_cognitive_hypothesis_time ON ecos_control.ecos_cognitive_hypothesis USING btree (update_time DESC);

--

-- idx_ecos_control_audit_retry_due (INDEX)
CREATE INDEX idx_ecos_control_audit_retry_due ON ecos_control.ecos_runtime_audit_retry USING btree (status, next_retry_at);

--

-- idx_ecos_control_dlq_status (INDEX)
CREATE INDEX idx_ecos_control_dlq_status ON ecos_control.ecos_runtime_event_dlq USING btree (status, first_seen_at);

--

-- idx_ecos_warn_log_objid (INDEX)
CREATE INDEX idx_ecos_warn_log_objid ON ecos_control.ecos_warn_log USING btree (warn_objid);

--

-- idx_ecos_warn_log_review_tag (INDEX)
CREATE INDEX idx_ecos_warn_log_review_tag ON ecos_control.ecos_warn_log USING btree (review_tag);

--

-- idx_ecos_warn_log_time (INDEX)
CREATE INDEX idx_ecos_warn_log_time ON ecos_control.ecos_warn_log USING btree (warn_time DESC);

--

-- idx_ecos_warn_log_type (INDEX)
CREATE INDEX idx_ecos_warn_log_type ON ecos_control.ecos_warn_log USING btree (warn_type);

--

-- idx_esqh_subject (INDEX)
CREATE INDEX idx_esqh_subject ON ecos_control.ecos_scenario_query_history USING btree (subject_id, create_time);

--

-- idx_esqh_trace (INDEX)
CREATE INDEX idx_esqh_trace ON ecos_control.ecos_scenario_query_history USING btree (trace_id);

--

-- idx_extraction_source_type (INDEX)
CREATE INDEX idx_extraction_source_type ON ecos_control.sys_extraction_source USING btree (source_type);

--

-- idx_func_audit_caller (INDEX)
CREATE INDEX idx_func_audit_caller ON ecos_control.ecos_function_audit_log USING btree (caller_id);

--

-- idx_func_audit_created (INDEX)
CREATE INDEX idx_func_audit_created ON ecos_control.ecos_function_audit_log USING btree (created_at DESC);

--

-- idx_func_audit_status (INDEX)
CREATE INDEX idx_func_audit_status ON ecos_control.ecos_function_audit_log USING btree (status);

--

-- idx_glossary_term_domain (INDEX)
CREATE INDEX idx_glossary_term_domain ON ecos_control.ecos_glossary_term USING btree (domain);

--

-- idx_glossary_term_status (INDEX)
CREATE INDEX idx_glossary_term_status ON ecos_control.ecos_glossary_term USING btree (status);

--

-- idx_goal_log_goal (INDEX)
CREATE INDEX idx_goal_log_goal ON ecos_control.ecos_wm_goal_log USING btree (goal_id);

--

-- idx_goal_log_time (INDEX)
CREATE INDEX idx_goal_log_time ON ecos_control.ecos_wm_goal_log USING btree (changed_at);

--

-- idx_goal_tracking_goal (INDEX)
CREATE INDEX idx_goal_tracking_goal ON ecos_control.ecos_goal_tracking USING btree (goal_id, recorded_at);

--

-- idx_ifc_ref_type (INDEX)
CREATE INDEX idx_ifc_ref_type ON ecos_control.ecos_interface_ref USING btree (interface_type, is_deleted);

--

-- idx_kb_cognitive_pipeline_created_at (INDEX)
CREATE INDEX idx_kb_cognitive_pipeline_created_at ON ecos_control.kb_cognitive_pipeline USING btree (created_at DESC) WHERE (is_deleted = 0);

--

-- idx_kb_cognitive_pipeline_status (INDEX)
CREATE INDEX idx_kb_cognitive_pipeline_status ON ecos_control.kb_cognitive_pipeline USING btree (status) WHERE (is_deleted = 0);

--

-- idx_kb_lineage_event_format (INDEX)
CREATE INDEX idx_kb_lineage_event_format ON ecos_control.kb_lineage_event USING btree (format) WHERE (is_deleted = 0);

--

-- idx_kb_lineage_event_parse_at (INDEX)
CREATE INDEX idx_kb_lineage_event_parse_at ON ecos_control.kb_lineage_event USING btree (parse_at DESC) WHERE (is_deleted = 0);

--

-- idx_kb_ontology_snapshot_created_at (INDEX)
CREATE INDEX idx_kb_ontology_snapshot_created_at ON ecos_control.kb_ontology_snapshot USING btree (created_at DESC) WHERE (is_deleted = 0);

--

-- idx_kb_ontology_snapshot_ontology (INDEX)
CREATE INDEX idx_kb_ontology_snapshot_ontology ON ecos_control.kb_ontology_snapshot USING btree (ontology_id) WHERE ((is_deleted = 0) OR (is_deleted = 2));

--

-- idx_marketplace_asset_category (INDEX)
CREATE INDEX idx_marketplace_asset_category ON ecos_control.ecos_marketplace_asset USING btree (category);

--

-- idx_marketplace_asset_ontology (INDEX)
CREATE INDEX idx_marketplace_asset_ontology ON ecos_control.ecos_marketplace_asset USING btree (ontology_entity_id);

--

-- idx_marketplace_asset_popularity (INDEX)
CREATE INDEX idx_marketplace_asset_popularity ON ecos_control.ecos_marketplace_asset USING btree (popularity DESC);

--

-- idx_marketplace_asset_status (INDEX)
CREATE INDEX idx_marketplace_asset_status ON ecos_control.ecos_marketplace_asset USING btree (status);

--

-- idx_mask_strategy_enabled (INDEX)
CREATE INDEX idx_mask_strategy_enabled ON ecos_control.ecos_mask_strategy USING btree (tenant_id, enabled);

--

-- idx_mcl_datasource (INDEX)
CREATE INDEX idx_mcl_datasource ON ecos_control.td_metadata_collect_log USING btree (datasource_id, create_time DESC);

--

-- idx_mind_scenario (INDEX)
CREATE INDEX idx_mind_scenario ON ecos_control.ecos_scenario_mind USING btree (scenario_id, is_deleted);

--

-- idx_obj_links_relation (INDEX)
CREATE INDEX idx_obj_links_relation ON ecos_control.ecos_object_links USING btree (relation_code);

--

-- idx_obj_links_source (INDEX)
CREATE INDEX idx_obj_links_source ON ecos_control.ecos_object_links USING btree (source_id);

--

-- idx_obj_links_target (INDEX)
CREATE INDEX idx_obj_links_target ON ecos_control.ecos_object_links USING btree (target_id);

--

-- idx_obj_rel_source (INDEX)
CREATE INDEX idx_obj_rel_source ON ecos_control.ecos_object_relationship USING btree (source_object_id);

--

-- idx_obj_rel_target (INDEX)
CREATE INDEX idx_obj_rel_target ON ecos_control.ecos_object_relationship USING btree (target_object_id);

--

-- idx_ontology_entity_tenant (INDEX)
CREATE INDEX idx_ontology_entity_tenant ON ecos_control.ecos_ontology_entity USING btree (tenant_id);

--

-- idx_pareto_problem_created (INDEX)
CREATE INDEX idx_pareto_problem_created ON ecos_control.ecos_pareto_problem USING btree (created_at DESC);

--

-- idx_pipeline_edge_def (INDEX)
CREATE INDEX idx_pipeline_edge_def ON ecos_control.ecos_pipeline_edge USING btree (definition_id);

--

-- idx_pipeline_node_def (INDEX)
CREATE INDEX idx_pipeline_node_def ON ecos_control.ecos_pipeline_node USING btree (definition_id);

--

-- idx_provenance_entity (INDEX)
CREATE INDEX idx_provenance_entity ON ecos_control.ecos_provenance_entry USING btree (entity_type, entity_id);

--

-- idx_rls_policy_resource (INDEX)
CREATE INDEX idx_rls_policy_resource ON ecos_control.ecos_rls_policy USING btree (resource_id);

--

-- idx_rls_policy_table (INDEX)
CREATE INDEX idx_rls_policy_table ON ecos_control.ecos_rls_policy USING btree (table_name, enabled, priority);

--

-- idx_rsp_clearance (INDEX)
CREATE INDEX idx_rsp_clearance ON ecos_control.td_role_security_profile USING btree (clearance_level);

--

-- idx_rsp_org (INDEX)
CREATE INDEX idx_rsp_org ON ecos_control.td_role_security_profile USING btree (org_id);

--

-- idx_rsp_scope (INDEX)
CREATE INDEX idx_rsp_scope ON ecos_control.td_role_security_profile USING btree (scope_type);

--

-- idx_rsp_tenant (INDEX)
CREATE INDEX idx_rsp_tenant ON ecos_control.td_role_security_profile USING btree (tenant_id);

--

-- idx_scen_bind_scenario (INDEX)
CREATE INDEX idx_scen_bind_scenario ON ecos_control.ecos_scenario_asset_binding USING btree (scenario_id);

--

-- idx_scen_bind_tid (INDEX)
CREATE INDEX idx_scen_bind_tid ON ecos_control.ecos_scenario_asset_binding USING btree (target_id);

--

-- idx_scen_bind_type (INDEX)
CREATE INDEX idx_scen_bind_type ON ecos_control.ecos_scenario_asset_binding USING btree (binding_type);

--

-- idx_scen_dec_mind (INDEX)
CREATE INDEX idx_scen_dec_mind ON ecos_control.ecos_scenario_decision_record USING btree (mind_id);

--

-- idx_scen_dec_run (INDEX)
CREATE INDEX idx_scen_dec_run ON ecos_control.ecos_scenario_decision_record USING btree (forecast_run_id);

--

-- idx_scen_dec_scenario (INDEX)
CREATE INDEX idx_scen_dec_scenario ON ecos_control.ecos_scenario_decision_record USING btree (scenario_id, is_deleted);

--

-- idx_scen_def_dept (INDEX)
CREATE INDEX idx_scen_def_dept ON ecos_control.ecos_scenario_definition USING btree (department);

--

-- idx_scen_def_statdel (INDEX)
CREATE INDEX idx_scen_def_statdel ON ecos_control.ecos_scenario_definition USING btree (status, is_deleted, domain);

--

-- idx_scen_def_status (INDEX)
CREATE INDEX idx_scen_def_status ON ecos_control.ecos_scenario_definition USING btree (status);

--

-- idx_scen_exec_mode (INDEX)
CREATE INDEX idx_scen_exec_mode ON ecos_control.ecos_scenario_execution USING btree (run_mode, scenario_id);

--

-- idx_scen_exec_scenario (INDEX)
CREATE INDEX idx_scen_exec_scenario ON ecos_control.ecos_scenario_execution USING btree (scenario_id);

--

-- idx_scen_exec_status (INDEX)
CREATE INDEX idx_scen_exec_status ON ecos_control.ecos_scenario_execution USING btree (status);

--

-- idx_scen_exec_time (INDEX)
CREATE INDEX idx_scen_exec_time ON ecos_control.ecos_scenario_execution USING btree (create_time DESC);

--

-- idx_scen_exec_type (INDEX)
CREATE INDEX idx_scen_exec_type ON ecos_control.ecos_scenario_execution USING btree (run_type);

--

-- idx_scen_link_sco (INDEX)
CREATE INDEX idx_scen_link_sco ON ecos_control.ecos_scenario_binding_edge USING btree (scenario_id);

--

-- idx_scen_link_sco_typ (INDEX)
CREATE INDEX idx_scen_link_sco_typ ON ecos_control.ecos_scenario_binding_edge USING btree (scenario_id, link_type);

--

-- idx_scen_link_src (INDEX)
CREATE INDEX idx_scen_link_src ON ecos_control.ecos_scenario_binding_edge USING btree (source_binding_id);

--

-- idx_scen_link_tgt (INDEX)
CREATE INDEX idx_scen_link_tgt ON ecos_control.ecos_scenario_binding_edge USING btree (target_binding_id);

--

-- idx_scen_link_typ (INDEX)
CREATE INDEX idx_scen_link_typ ON ecos_control.ecos_scenario_binding_edge USING btree (link_type);

--

-- idx_scen_mind_var_scenario (INDEX)
CREATE INDEX idx_scen_mind_var_scenario ON ecos_control.ecos_scenario_mind_variant USING btree (scenario_id, is_deleted);

--

-- idx_scenario_bind_tid (INDEX)
CREATE INDEX idx_scenario_bind_tid ON ecos_control.ecos_scenario_binding USING btree (target_id);

--

-- idx_scenario_bindsc (INDEX)
CREATE INDEX idx_scenario_bindsc ON ecos_control.ecos_scenario_binding USING btree (scenario_id);

--

-- idx_scenario_bindst (INDEX)
CREATE INDEX idx_scenario_bindst ON ecos_control.ecos_scenario_binding USING btree (binding_type);

--

-- idx_scenario_run_mode (INDEX)
CREATE INDEX idx_scenario_run_mode ON ecos_control.ecos_scenario_run USING btree (scenario_id, run_mode, is_deleted);

--

-- idx_scenario_run_sc (INDEX)
CREATE INDEX idx_scenario_run_sc ON ecos_control.ecos_scenario_run USING btree (scenario_id);

--

-- idx_scenario_run_status (INDEX)
CREATE INDEX idx_scenario_run_status ON ecos_control.ecos_scenario_run USING btree (status);

--

-- idx_scenario_run_time (INDEX)
CREATE INDEX idx_scenario_run_time ON ecos_control.ecos_scenario_run USING btree (create_time DESC);

--

-- idx_scenario_run_type (INDEX)
CREATE INDEX idx_scenario_run_type ON ecos_control.ecos_scenario_run USING btree (run_type);

--

-- idx_schema_changes_ack (INDEX)
CREATE INDEX idx_schema_changes_ack ON ecos_control.schema_changes USING btree (acknowledged, detected_at);

--

-- idx_sec_policy_domain (INDEX)
CREATE INDEX idx_sec_policy_domain ON ecos_control.ecos_security_policy USING btree (domain, is_deleted);

--

-- idx_spans_created (INDEX)
CREATE INDEX idx_spans_created ON ecos_control.ecos_spans USING btree (created_at DESC);

--

-- idx_spans_path (INDEX)
CREATE INDEX idx_spans_path ON ecos_control.ecos_spans USING btree (http_path);

--

-- idx_spans_trace (INDEX)
CREATE INDEX idx_spans_trace ON ecos_control.ecos_spans USING btree (trace_id);

--

-- idx_sys_compliance_rule_domain (INDEX)
CREATE INDEX idx_sys_compliance_rule_domain ON ecos_control.sys_compliance_rule USING btree (domain);

--

-- idx_sys_compliance_rule_status (INDEX)
CREATE INDEX idx_sys_compliance_rule_status ON ecos_control.sys_compliance_rule USING btree (status);

--

-- idx_sysman_config_type_env (INDEX)
CREATE INDEX idx_sysman_config_type_env ON ecos_control.td_config USING btree (config_type, environment);

--

-- idx_sysman_orgperm_org (INDEX)
CREATE INDEX idx_sysman_orgperm_org ON ecos_control.td_org_permission USING btree (org_id);

--

-- idx_sysman_perm_res_action (INDEX)
CREATE UNIQUE INDEX idx_sysman_perm_res_action ON ecos_control.td_permission USING btree (resource_id, action);

--

-- idx_sysman_role_code (INDEX)
CREATE UNIQUE INDEX idx_sysman_role_code ON ecos_control.td_role USING btree (role_code);

--

-- idx_sysman_role_parent (INDEX)
CREATE INDEX idx_sysman_role_parent ON ecos_control.td_role USING btree (parent_role_id);

--

-- idx_sysman_user_email (INDEX)
CREATE UNIQUE INDEX idx_sysman_user_email ON ecos_control.td_user USING btree (email);

--

-- idx_sysman_user_status (INDEX)
CREATE INDEX idx_sysman_user_status ON ecos_control.td_user USING btree (status);

--

-- idx_sysman_user_tenant (INDEX)
CREATE INDEX idx_sysman_user_tenant ON ecos_control.td_user USING btree (tenant_id);

--

-- idx_sysman_user_username (INDEX)
CREATE UNIQUE INDEX idx_sysman_user_username ON ecos_control.td_user USING btree (username);

--

-- idx_task_created (INDEX)
CREATE INDEX idx_task_created ON ecos_control.ecos_task USING btree (created_at DESC);

--

-- idx_task_status (INDEX)
CREATE INDEX idx_task_status ON ecos_control.ecos_task USING btree (status);

--

-- idx_task_type (INDEX)
CREATE INDEX idx_task_type ON ecos_control.ecos_task USING btree (task_type);

--

-- idx_td_datasource_create_time (INDEX)
CREATE INDEX idx_td_datasource_create_time ON ecos_control.td_datasource USING btree (create_time DESC);

--

-- idx_td_runtime_task_created (INDEX)
CREATE INDEX idx_td_runtime_task_created ON ecos_control.td_runtime_task USING btree ("CREATED_TIME" DESC NULLS LAST);

--

-- idx_td_runtime_task_execution_task (INDEX)
CREATE INDEX idx_td_runtime_task_execution_task ON ecos_control.td_runtime_task_execution USING btree ("TASK_ID", "START_TIME");

--

-- idx_td_runtime_task_log_task (INDEX)
CREATE INDEX idx_td_runtime_task_log_task ON ecos_control.td_runtime_task_log USING btree ("TASK_ID", "CREATED_TIME");

--

-- idx_td_runtime_task_status_status (INDEX)
CREATE INDEX idx_td_runtime_task_status_status ON ecos_control.td_runtime_task_status USING btree ("STATUS");

--

-- idx_td_runtime_task_status_updated (INDEX)
CREATE INDEX idx_td_runtime_task_status_updated ON ecos_control.td_runtime_task_status USING btree ("UPDATED_TIME" DESC NULLS LAST);

--

-- idx_td_runtime_task_tenant (INDEX)
CREATE INDEX idx_td_runtime_task_tenant ON ecos_control.td_runtime_task USING btree ("TENANT_ID", is_deleted);

--

-- idx_td_runtime_task_type (INDEX)
CREATE INDEX idx_td_runtime_task_type ON ecos_control.td_runtime_task USING btree ("TASK_TYPE");

--

-- idx_td_task_create_time (INDEX)
CREATE INDEX idx_td_task_create_time ON ecos_control.td_runtime_task USING btree (create_time);

--

-- idx_td_task_domain (INDEX)
CREATE INDEX idx_td_task_domain ON ecos_control.td_runtime_task USING btree (domain);

--

-- idx_td_task_is_deleted (INDEX)
CREATE INDEX idx_td_task_is_deleted ON ecos_control.td_runtime_task USING btree (is_deleted) WHERE (is_deleted = 0);

--

-- idx_td_task_plan_cron (INDEX)
CREATE INDEX idx_td_task_plan_cron ON ecos_control.td_runtime_task_plan USING btree (cron_expression) WHERE (cron_expression IS NOT NULL);

--

-- idx_td_task_plan_last_run (INDEX)
CREATE INDEX idx_td_task_plan_last_run ON ecos_control.td_runtime_task_plan USING btree (last_run_at);

--

-- idx_td_task_plan_last_run_at (INDEX)
CREATE INDEX idx_td_task_plan_last_run_at ON ecos_control.td_runtime_task_plan USING btree (last_run_at);

--

-- idx_td_task_plan_next_run (INDEX)
CREATE INDEX idx_td_task_plan_next_run ON ecos_control.td_runtime_task_plan USING btree (next_run_at) WHERE (next_run_at IS NOT NULL);

--

-- idx_td_task_plan_next_run_at (INDEX)
CREATE INDEX idx_td_task_plan_next_run_at ON ecos_control.td_runtime_task_plan USING btree (next_run_at) WHERE (next_run_at IS NOT NULL);

--

-- idx_td_task_plan_task_type (INDEX)
CREATE INDEX idx_td_task_plan_task_type ON ecos_control.td_runtime_task_plan USING btree ("TASK_TYPE");

--

-- idx_td_task_task_type (INDEX)
CREATE INDEX idx_td_task_task_type ON ecos_control.td_runtime_task USING btree ("TASK_TYPE", is_deleted);

--

-- idx_tdr_layer_bucket (INDEX)
CREATE INDEX idx_tdr_layer_bucket ON ecos_control.td_data_resource USING btree (layer_bucket);

--

-- idx_tdr_storage_kind (INDEX)
CREATE INDEX idx_tdr_storage_kind ON ecos_control.td_data_resource USING btree (storage_kind);

--

-- idx_tenant_quota_type (INDEX)
CREATE UNIQUE INDEX idx_tenant_quota_type ON ecos_control.ecos_tenant_quota USING btree (tenant_id, quota_type);

--

-- idx_tenant_usage_date (INDEX)
CREATE INDEX idx_tenant_usage_date ON ecos_control.ecos_tenant_usage USING btree (tenant_id, usage_date DESC);

--

-- idx_timeline_entity (INDEX)
CREATE INDEX idx_timeline_entity ON ecos_control.ecos_object_timeline USING btree (entity_code, created_at DESC);

--

-- idx_timeline_object (INDEX)
CREATE INDEX idx_timeline_object ON ecos_control.ecos_object_timeline USING btree (object_id, created_at DESC);

--

-- idx_token_usage_created (INDEX)
CREATE INDEX idx_token_usage_created ON ecos_control.ecos_token_usage USING btree (created_at DESC);

--

-- idx_token_usage_trace (INDEX)
CREATE INDEX idx_token_usage_trace ON ecos_control.ecos_token_usage USING btree (trace_id);

--

-- idx_usp_clearance (INDEX)
CREATE INDEX idx_usp_clearance ON ecos_control.td_user_security_profile USING btree (clearance_level);

--

-- idx_usp_org (INDEX)
CREATE INDEX idx_usp_org ON ecos_control.td_user_security_profile USING btree (org_id);

--

-- idx_usp_scope (INDEX)
CREATE INDEX idx_usp_scope ON ecos_control.td_user_security_profile USING btree (scope_type);

--

-- idx_usp_tenant (INDEX)
CREATE INDEX idx_usp_tenant ON ecos_control.td_user_security_profile USING btree (tenant_id);

--

-- idx_v271_bp_tenant_status (INDEX)
CREATE INDEX idx_v271_bp_tenant_status ON ecos_control.ecos_scenario_blueprint USING btree (tenant_id, status, is_deleted);

--

-- idx_wf_approval_instance (INDEX)
CREATE INDEX idx_wf_approval_instance ON ecos_control.ecos_workflow_approval USING btree (instance_id);

--

-- idx_wf_approval_task (INDEX)
CREATE INDEX idx_wf_approval_task ON ecos_control.ecos_workflow_approval USING btree (task_id);

--

-- idx_wf_instance_object (INDEX)
CREATE INDEX idx_wf_instance_object ON ecos_control.ecos_workflow_instance USING btree (triggered_object_id);

--

-- idx_wf_instance_status (INDEX)
CREATE INDEX idx_wf_instance_status ON ecos_control.ecos_workflow_instance USING btree (status);

--

-- idx_wf_instance_workflow (INDEX)
CREATE INDEX idx_wf_instance_workflow ON ecos_control.ecos_workflow_instance USING btree (workflow_id);

--

-- idx_wf_log_instance (INDEX)
CREATE INDEX idx_wf_log_instance ON ecos_control.ecos_workflow_log USING btree (instance_id, created_at);

--

-- idx_wf_log_trace (INDEX)
CREATE INDEX idx_wf_log_trace ON ecos_control.ecos_workflow_log USING btree (trace_id);

--

-- idx_wf_task_assignee (INDEX)
CREATE INDEX idx_wf_task_assignee ON ecos_control.ecos_workflow_task USING btree (assignee);

--

-- idx_wf_task_instance (INDEX)
CREATE INDEX idx_wf_task_instance ON ecos_control.ecos_workflow_task USING btree (instance_id);

--

-- idx_wf_task_status (INDEX)
CREATE INDEX idx_wf_task_status ON ecos_control.ecos_workflow_task USING btree (status);

--

-- idx_wm_causal_source (INDEX)
CREATE INDEX idx_wm_causal_source ON ecos_control.ecos_wm_causal_link USING btree (source_goal_id);

--

-- idx_wm_causal_target (INDEX)
CREATE INDEX idx_wm_causal_target ON ecos_control.ecos_wm_causal_link USING btree (target_goal_id);

--

-- idx_wm_goal_parent (INDEX)
CREATE INDEX idx_wm_goal_parent ON ecos_control.ecos_wm_goal USING btree (parent_id);

--

-- idx_wm_goal_status (INDEX)
CREATE INDEX idx_wm_goal_status ON ecos_control.ecos_wm_goal USING btree (status);

--

-- idx_wm_scenario_status (INDEX)
CREATE INDEX idx_wm_scenario_status ON ecos_control.ecos_wm_scenario USING btree (status);

--

-- idx_workflow_log_instance (INDEX)
CREATE INDEX idx_workflow_log_instance ON ecos_control.ecos_workflow_log USING btree (instance_id, created_at DESC);

--

-- idx_ws_status (INDEX)
CREATE INDEX idx_ws_status ON ecos_control.ecos_world_scenarios USING btree (status);

--

-- uk_alert_rule_code (INDEX)
CREATE UNIQUE INDEX uk_alert_rule_code ON ecos_control.ecos_runtime_alert_rule USING btree (tenant_id, rule_code) WHERE (is_deleted = 0);

--

-- uk_ecos_git_repo_repository_id (INDEX)
CREATE UNIQUE INDEX uk_ecos_git_repo_repository_id ON ecos_control.ecos_git_repo USING btree (repository_id) WHERE (is_deleted = 0);

--

-- uk_v271_bp_scen_ver (INDEX)
CREATE UNIQUE INDEX uk_v271_bp_scen_ver ON ecos_control.ecos_scenario_blueprint USING btree (scenario_id, version, is_deleted);

--

-- uniq_cog_model_id_ver (INDEX)
CREATE UNIQUE INDEX uniq_cog_model_id_ver ON ecos_control.ecos_cognitive_model USING btree (model_id, version);

--

-- uniq_crypto_key_ver (INDEX)
CREATE UNIQUE INDEX uniq_crypto_key_ver ON ecos_control.ecos_security_crypto_key USING btree (key_id, key_version);

--

-- uniq_dw_wc_idempotency_key (INDEX)
CREATE UNIQUE INDEX uniq_dw_wc_idempotency_key ON ecos_control.td_dw_write_channel_idempotency USING btree (idempotency_key);

--

-- uniq_ecos_cog_run_inv_evt_run (INDEX)
CREATE UNIQUE INDEX uniq_ecos_cog_run_inv_evt_run ON ecos_control.ecos_cognitive_run_invalidation USING btree (event_id, run_id);

--

-- uniq_ecos_cognitive_belief_variable_ver (INDEX)
CREATE UNIQUE INDEX uniq_ecos_cognitive_belief_variable_ver ON ecos_control.ecos_cognitive_belief USING btree (variable_name, domain, version);

--

-- uniq_ecos_cognitive_evidence_code (INDEX)
CREATE UNIQUE INDEX uniq_ecos_cognitive_evidence_code ON ecos_control.ecos_cognitive_evidence USING btree (evidence_code);

--

-- uniq_ecos_cognitive_hypothesis_code (INDEX)
CREATE UNIQUE INDEX uniq_ecos_cognitive_hypothesis_code ON ecos_control.ecos_cognitive_hypothesis USING btree (hypothesis_code);

--

-- uniq_ecos_control_sec_asset_key (INDEX)
CREATE UNIQUE INDEX uniq_ecos_control_sec_asset_key ON ecos_control.ecos_security_asset USING btree (tenant_id, asset_key) WHERE (is_deleted = 0);

--

-- uniq_ecos_warn_log_log_id (INDEX)
CREATE UNIQUE INDEX uniq_ecos_warn_log_log_id ON ecos_control.ecos_warn_log USING btree (log_id);

--

-- uniq_scen_canvas_scenario (INDEX)
CREATE UNIQUE INDEX uniq_scen_canvas_scenario ON ecos_control.ecos_scenario_canvas_layout USING btree (scenario_id);

--

-- uniq_scen_link_edge (INDEX)
CREATE UNIQUE INDEX uniq_scen_link_edge ON ecos_control.ecos_scenario_binding_edge USING btree (scenario_id, source_binding_id, target_binding_id, link_type);

--

-- uniq_scen_mind_var_scenario_label (INDEX)
CREATE UNIQUE INDEX uniq_scen_mind_var_scenario_label ON ecos_control.ecos_scenario_mind_variant USING btree (scenario_id, mind_label, deleted_guard);

--

-- uq_mind_active (INDEX)
CREATE UNIQUE INDEX uq_mind_active ON ecos_control.ecos_scenario_mind USING btree (scenario_id) WHERE ((is_deleted = 0) AND (active_mind = 1));

--

-- uq_mind_scenario_label (INDEX)
CREATE UNIQUE INDEX uq_mind_scenario_label ON ecos_control.ecos_scenario_mind USING btree (scenario_id, mind_label) WHERE (is_deleted = 0);

--

