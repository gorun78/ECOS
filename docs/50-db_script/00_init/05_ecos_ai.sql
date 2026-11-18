-- ============================================================================
-- 05_ecos_ai.sql — ecos_ai 权威 DDL
-- ============================================================================
-- 由 pg_dump --schema-only 从真实库 sys_man 反向导出后清洗装配（2026-11-17，V349 清理后重导）。
-- 目标 schema = ecos_ai；保留对象 287 个，剔分区子表块 96 个，外键 5 条移入 09。
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

-- agent_approval (TABLE)
CREATE TABLE ecos_ai.agent_approval (
    id character varying(64) NOT NULL,
    task_id character varying(64),
    risk_level character varying(4),
    status character varying(16) DEFAULT 'PENDING',
    requested_at timestamp without time zone,
    processed_at timestamp without time zone,
    approved_by character varying(64),
    comment text,
    token_digest character varying(128),
    scope_type character varying(32),
    scope_id character varying(64),
    requested_by character varying(100),
    expired_at timestamp without time zone,
    consumed_at timestamp without time zone,
    trace_id character varying(64)
);

--

-- TABLE agent_approval (COMMENT)
COMMENT ON TABLE ecos_ai.agent_approval IS 'Agent审批表';

--

-- COLUMN agent_approval.token_digest (COMMENT)
COMMENT ON COLUMN ecos_ai.agent_approval.token_digest IS '审批凭证 SHA-256 摘要（禁存明文；明文仅签发响应出现一次）';

--

-- COLUMN agent_approval.scope_type (COMMENT)
COMMENT ON COLUMN ecos_ai.agent_approval.scope_type IS '凭证作用域类型：CANDIDATE_PUBLISH / ACTION_COMMIT / RUN_APPROVAL（与 WAgentApprovalScope 单源）';

--

-- COLUMN agent_approval.scope_id (COMMENT)
COMMENT ON COLUMN ecos_ai.agent_approval.scope_id IS '作用域对象 ID（candidateId / actionId）；凭证不得跨对象复用';

--

-- COLUMN agent_approval.requested_by (COMMENT)
COMMENT ON COLUMN ecos_ai.agent_approval.requested_by IS '申请人（提交该 L3 动作的操作者）；四眼要求 approved_by ≠ requested_by';

--

-- COLUMN agent_approval.expired_at (COMMENT)
COMMENT ON COLUMN ecos_ai.agent_approval.expired_at IS '凭证过期时刻；过期即拒绝（E-POLICY），不自动续期';

--

-- COLUMN agent_approval.consumed_at (COMMENT)
COMMENT ON COLUMN ecos_ai.agent_approval.consumed_at IS '一次性消费时刻；消费后不可再用（CAS 置位，重放必拒）';

--

-- COLUMN agent_approval.trace_id (COMMENT)
COMMENT ON COLUMN ecos_ai.agent_approval.trace_id IS '链路标识（与 ecos_audit_log.trace_id 同义，供凭证操作与审计行串联核对）';

--

-- agent_cost (TABLE)
CREATE TABLE ecos_ai.agent_cost (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY',
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
)
PARTITION BY RANGE (created_at);

--

-- TABLE agent_cost (COMMENT)
COMMENT ON TABLE ecos_ai.agent_cost IS 'Agent成本表(按月分区)';

--

-- agent_cost_default (TABLE)
CREATE TABLE ecos_ai.agent_cost_default (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY',
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- agent_definition (TABLE)
CREATE TABLE ecos_ai.agent_definition (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(32),
    role character varying(128),
    description text,
    capability jsonb DEFAULT '{}'::jsonb,
    config jsonb DEFAULT '{}'::jsonb,
    status character varying(32) DEFAULT 'ACTIVE',
    version integer DEFAULT 1,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE agent_definition (COMMENT)
COMMENT ON TABLE ecos_ai.agent_definition IS 'Agent定义表(V50)';

--

-- agent_evaluation (TABLE)
CREATE TABLE ecos_ai.agent_evaluation (
    id character varying(64) NOT NULL,
    execution_id character varying(64) NOT NULL,
    correctness numeric(5,2),
    completeness numeric(5,2),
    safety numeric(5,2),
    efficiency numeric(5,2),
    overall numeric(5,2),
    feedback text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE agent_evaluation (COMMENT)
COMMENT ON TABLE ecos_ai.agent_evaluation IS 'Agent评估表';

--

-- agent_execution (TABLE)
CREATE TABLE ecos_ai.agent_execution (
    id character varying(64) NOT NULL,
    agent_id character varying(64) NOT NULL,
    goal text,
    plan jsonb,
    status character varying(32) DEFAULT 'CREATED',
    result jsonb,
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE agent_execution (COMMENT)
COMMENT ON TABLE ecos_ai.agent_execution IS 'Agent执行表';

--

-- agent_execution_step (TABLE)
CREATE TABLE ecos_ai.agent_execution_step (
    id character varying(64) NOT NULL,
    execution_id character varying(64) NOT NULL,
    step_order integer,
    instruction text,
    tool_type character varying(16),
    tool_params jsonb,
    status character varying(32) DEFAULT 'PENDING',
    output text,
    metrics jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE agent_execution_step (COMMENT)
COMMENT ON TABLE ecos_ai.agent_execution_step IS 'Agent执行步骤表';

--

-- agent_governance_policy (TABLE)
CREATE TABLE ecos_ai.agent_governance_policy (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    type character varying(16),
    rule jsonb DEFAULT '{}'::jsonb,
    enabled boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE agent_governance_policy (COMMENT)
COMMENT ON TABLE ecos_ai.agent_governance_policy IS 'Agent治理策略表';

--

-- agent_memory (TABLE)
CREATE TABLE ecos_ai.agent_memory (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    session_id character varying(64),
    layer character varying(16),
    content text,
    embedding jsonb,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE agent_memory (COMMENT)
COMMENT ON TABLE ecos_ai.agent_memory IS 'Agent记忆表';

--

-- agent_registry (TABLE)
CREATE TABLE ecos_ai.agent_registry (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    role character varying(128),
    capability jsonb DEFAULT '{}'::jsonb,
    status character varying(32) DEFAULT 'ACTIVE',
    endpoint character varying(512),
    metadata jsonb DEFAULT '{}'::jsonb
);

--

-- causal_edge (TABLE)
CREATE TABLE ecos_ai.causal_edge (
    id character varying(64) NOT NULL,
    source_node character varying(128),
    target_node character varying(128),
    weight numeric(5,4) DEFAULT 0.5,
    description text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE causal_edge (COMMENT)
COMMENT ON TABLE ecos_ai.causal_edge IS '因果边表';

--

-- ecos_agent (TABLE)
CREATE TABLE ecos_ai.ecos_agent (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    model_provider character varying(64) DEFAULT 'deepseek',
    model_name character varying(128) DEFAULT 'deepseek-v4-flash',
    system_prompt text,
    tools text DEFAULT '[]'::text,
    knowledge text DEFAULT '[]'::text,
    status character varying(32) DEFAULT 'draft',
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE ecos_agent (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_agent IS 'Agent配置表(旧版)';

--

-- ecos_agent_alert (TABLE)
CREATE TABLE ecos_ai.ecos_agent_alert (
    id bigint NOT NULL,
    trace_id character varying(16),
    agent_id character varying(64),
    alert_type character varying(32),
    message text,
    created_at timestamp without time zone DEFAULT now()
);

--

-- ecos_agent_alert_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_ai.ecos_agent_alert_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_agent_alert_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_ai.ecos_agent_alert_id_seq OWNED BY ecos_ai.ecos_agent_alert.id;

--

-- ecos_agent_metrics (TABLE)
CREATE TABLE ecos_ai.ecos_agent_metrics (
    id bigint NOT NULL,
    agent_id character varying(64),
    action character varying(32),
    success boolean,
    elapsed_ms bigint,
    tokens_in integer DEFAULT 0,
    tokens_out integer DEFAULT 0,
    trace_id character varying(16),
    created_at timestamp without time zone DEFAULT now()
);

--

-- ecos_agent_metrics_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_ai.ecos_agent_metrics_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_agent_metrics_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_ai.ecos_agent_metrics_id_seq OWNED BY ecos_ai.ecos_agent_metrics.id;

--

-- ecos_agent_registry (TABLE)
CREATE TABLE ecos_ai.ecos_agent_registry (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    role character varying(128),
    capability jsonb DEFAULT '{}'::jsonb,
    status character varying(32) DEFAULT 'ACTIVE',
    endpoint character varying(512),
    metadata jsonb DEFAULT '{}'::jsonb,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE ecos_agent_registry (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_agent_registry IS 'Agent注册表';

--

-- ecos_ai_capability_task (TABLE)
CREATE TABLE ecos_ai.ecos_ai_capability_task (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    task_kind character varying(10) NOT NULL,
    capability_id character varying(50) NOT NULL,
    tool_contract_ref character varying(100),
    input_ref character varying(200) NOT NULL,
    model_ref character varying(100),
    model_version character varying(20),
    output_ref character varying(200),
    status character varying(20) DEFAULT 'PENDING' NOT NULL,
    quality_score numeric(6,5),
    quality_kind character varying(30),
    calibrated smallint,
    cost_unit character varying(40),
    latency_ms bigint,
    degraded smallint DEFAULT 0 NOT NULL,
    warnings_text text,
    selected_model_reason text,
    trace_id character varying(64),
    run_id character varying(36),
    submitter character varying(100),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_ai_capability_task (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_ai_capability_task IS '能力调用台账（补充设计 v1.0 §10.4/10.5）：只存引用与元数据；LLM/Embedding 唯一出口仍 llm-gateway；小模型节点复用，禁第二套管理（§10.6/SUP-MODEL-01~03）';

--

-- ecos_ai_compression_batch (TABLE)
CREATE TABLE ecos_ai.ecos_ai_compression_batch (
    id character varying(36) NOT NULL,
    session_id character varying(64) NOT NULL,
    mode character varying(24) NOT NULL,
    compressed_from_seq integer,
    compressed_to_seq integer,
    estimated_tokens_before integer,
    estimated_tokens_after integer,
    kept_key_refs text,
    dropped_key_refs text,
    failure_reason character varying(120),
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone,
    CONSTRAINT ck_acb_mode CHECK (((mode)::text = ANY ((ARRAY['LLM_SUMMARY', 'TRUNCATE_FALLBACK'])::text[])))
);

--

-- TABLE ecos_ai_compression_batch (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_ai_compression_batch IS '会话压缩批次头（F06-09；R-19 并建合规新表落 ecos_ai；session_id 跨 schema 逻辑引用不建 FK）';

--

-- ecos_ai_guardrail_decision_ledger (TABLE)
CREATE TABLE ecos_ai.ecos_ai_guardrail_decision_ledger (
    id character varying(36) NOT NULL,
    trace_id character varying(64) NOT NULL,
    session_id character varying(64),
    tool_key character varying(120) NOT NULL,
    decision character varying(20) NOT NULL,
    policy_id character varying(120),
    obligations text,
    latency_ms integer,
    subject_snapshot text,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone,
    CONSTRAINT ck_agdl_decision CHECK (((decision)::text = ANY ((ARRAY['ALLOW', 'DENY', 'FAIL_CLOSED'])::text[])))
);

--

-- TABLE ecos_ai_guardrail_decision_ledger (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_ai_guardrail_decision_ledger IS 'Guardrail 裁决台账（F06-03 对账可查询面；policy/obligations 仅为 security-engine 裁决响应缓存，权威在 security-engine）';

--

-- ecos_ai_tool_permission_binding (TABLE)
CREATE TABLE ecos_ai.ecos_ai_tool_permission_binding (
    id character varying(36) NOT NULL,
    tool_key character varying(120) NOT NULL,
    role_key character varying(64) NOT NULL,
    required_attributes text,
    obligation_expectation text,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);

--

-- TABLE ecos_ai_tool_permission_binding (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_ai_tool_permission_binding IS '工具-角色权限绑定声明表（F06-02；禁把 LLM 任意入参塞进裁决载荷；策略权威在 security-engine，本表仅映射声明）';

--

-- ecos_ai_tool_registry (TABLE)
CREATE TABLE ecos_ai.ecos_ai_tool_registry (
    id character varying(36) NOT NULL,
    tool_key character varying(120) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    operation character varying(10) DEFAULT 'read' NOT NULL,
    args_schema_json text NOT NULL,
    backend_kind character varying(20) NOT NULL,
    backend_endpoint text,
    permission_hint text,
    opa_action_type character varying(40) DEFAULT 'TOOL_CALL' NOT NULL,
    resource_scope_projection text,
    timeout_ms integer DEFAULT 2000 NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE' NOT NULL,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone,
    CONSTRAINT ck_atr_operation CHECK (((operation)::text = ANY ((ARRAY['read', 'write'])::text[]))),
    CONSTRAINT ck_atr_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE', 'DISABLED', 'DRAFT'])::text[])))
);

--

-- TABLE ecos_ai_tool_registry (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_ai_tool_registry IS 'AI 工具声明式注册表（F06-11；R-19 并建合规新表；旧 ecos_tool_definition 双镜像停写不删）';

--

-- ecos_aip_pipeline (TABLE)
CREATE TABLE ecos_ai.ecos_aip_pipeline (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(500),
    definition_json text DEFAULT '{}'::text NOT NULL,
    status character varying(12) DEFAULT 'draft' NOT NULL,
    template_ref character varying(120),
    version_no character varying(20) DEFAULT '1' NOT NULL,
    last_executed_at timestamp without time zone,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_aip_pipeline (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_aip_pipeline IS 'AIP 逻辑编排定义（AI 工作台 LogicView 画布持久化；V329 批 1：内存 store 迁 PG；template_ref 预留批 2 Wagent Playbook 模板来源追溯）';

--

-- COLUMN ecos_aip_pipeline.last_executed_at (COMMENT)
COMMENT ON COLUMN ecos_ai.ecos_aip_pipeline.last_executed_at IS '最近执行时刻（execute 端点回写；响应 lastExecutedAt 尾键来源，对齐 DI-2 #147EC 键序）';

--

-- ecos_business_scenario (TABLE)
CREATE TABLE ecos_ai.ecos_business_scenario (
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

-- ecos_cron_job (TABLE)
CREATE TABLE ecos_ai.ecos_cron_job (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    cron_expression character varying(100),
    description text,
    enabled boolean DEFAULT true,
    last_run_at timestamp without time zone,
    next_run_at timestamp without time zone,
    status character varying(50) DEFAULT 'IDLE',
    created_by character varying(100),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);

--

-- ecos_cron_job_execution (TABLE)
CREATE TABLE ecos_ai.ecos_cron_job_execution (
    id bigint NOT NULL,
    cron_job_id bigint NOT NULL,
    started_at timestamp without time zone,
    finished_at timestamp without time zone,
    status character varying(50) DEFAULT 'RUNNING',
    result text,
    error_message text,
    created_at timestamp without time zone DEFAULT now()
);

--

-- ecos_cron_job_execution_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_ai.ecos_cron_job_execution_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_cron_job_execution_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_ai.ecos_cron_job_execution_id_seq OWNED BY ecos_ai.ecos_cron_job_execution.id;

--

-- ecos_cron_job_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_ai.ecos_cron_job_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_cron_job_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_ai.ecos_cron_job_id_seq OWNED BY ecos_ai.ecos_cron_job.id;

--

-- ecos_decision_case (TABLE)
CREATE TABLE ecos_ai.ecos_decision_case (
    id bigint NOT NULL,
    title character varying(256) NOT NULL,
    scenario text,
    tags text[],
    decision jsonb DEFAULT '{}'::jsonb,
    result jsonb DEFAULT '{}'::jsonb,
    feedback character varying(32) DEFAULT 'pending',
    source character varying(64),
    created_by character varying(128),
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE ecos_decision_case (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_decision_case IS '决策案例表';

--

-- ecos_decision_case_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_ai.ecos_decision_case_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_decision_case_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_ai.ecos_decision_case_id_seq OWNED BY ecos_ai.ecos_decision_case.id;

--

-- ecos_evolution_log (TABLE)
CREATE TABLE ecos_ai.ecos_evolution_log (
    id character varying(64) NOT NULL,
    mission_id character varying(64),
    stage character varying(32) NOT NULL,
    agent_id character varying(64),
    input_context jsonb,
    output_result jsonb,
    status character varying(32) DEFAULT 'STARTED',
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now()
);

--

-- TABLE ecos_evolution_log (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_evolution_log IS '自治进化日志表';

--

-- COLUMN ecos_evolution_log.stage (COMMENT)
COMMENT ON COLUMN ecos_ai.ecos_evolution_log.stage IS '阶段: DIAGNOSIS/ONTOLOGY_EVOLVE/KNOWLEDGE_REBUILD/SECURITY_HEAL/DEPLOYMENT';

--

-- COLUMN ecos_evolution_log.status (COMMENT)
COMMENT ON COLUMN ecos_ai.ecos_evolution_log.status IS '状态: STARTED/COMPLETED/FAILED';

--

-- ecos_guardrail_policy (TABLE)
CREATE TABLE ecos_ai.ecos_guardrail_policy (
    id character varying(64) NOT NULL,
    name character varying(256) NOT NULL,
    type character varying(64),
    severity character varying(32),
    is_enabled boolean DEFAULT true NOT NULL,
    parameters jsonb DEFAULT '[]'::jsonb NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- ecos_mission (TABLE)
CREATE TABLE ecos_ai.ecos_mission (
    id character varying(64) NOT NULL,
    title character varying(256) NOT NULL,
    goal text,
    mode character varying(16) DEFAULT 'SUPERVISOR',
    status character varying(32) DEFAULT 'PENDING',
    plan jsonb,
    result jsonb,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE ecos_mission (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_mission IS 'Mission表';

--

-- ecos_mission_task (TABLE)
CREATE TABLE ecos_ai.ecos_mission_task (
    id character varying(64) NOT NULL,
    mission_id character varying(64) NOT NULL,
    agent_id character varying(64),
    instruction text,
    status character varying(32) DEFAULT 'PENDING',
    result jsonb,
    depends_on character varying(256),
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE ecos_mission_task (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_mission_task IS 'Mission任务表';

--

-- ecos_skill (TABLE)
CREATE TABLE ecos_ai.ecos_skill (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    version character varying(50) DEFAULT '1.0.0',
    enabled boolean DEFAULT true,
    category character varying(100),
    package_info text,
    created_by character varying(100),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);

--

-- ecos_skill_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_ai.ecos_skill_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_skill_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_ai.ecos_skill_id_seq OWNED BY ecos_ai.ecos_skill.id;

--

-- ecos_tool_definition (TABLE)
CREATE TABLE ecos_ai.ecos_tool_definition (
    id character varying(64) NOT NULL,
    code character varying(128) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    tool_type character varying(32),
    endpoint_url character varying(512),
    http_method character varying(16),
    schema_json jsonb,
    status character varying(32) DEFAULT 'ACTIVE',
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE ecos_tool_definition (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_tool_definition IS '工具定义表';

--

-- ecos_wagent_action (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_action (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    option_id character varying(36) NOT NULL,
    title character varying(255),
    owner_id character varying(100),
    role character varying(64),
    due_date date,
    kpi_text text,
    source_type character varying(12),
    source_object_id character varying(36),
    publish_target character varying(24),
    publish_mode character varying(12),
    status character varying(20) DEFAULT 'DRAFT' NOT NULL,
    approval_token character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);

--

-- TABLE ecos_wagent_action (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_action IS 'W 域行动提交与发布（Action：commitAction 经 CommitService 五步，publish_target 回执，详设 §10.3/§10.4.2）';

--

-- ecos_wagent_action_effect (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_action_effect (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    action_id character varying(36) NOT NULL,
    due_date date,
    planned_json text,
    actual_json text,
    deviation_reason character varying(32),
    backtest_ref_text text,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);

--

-- TABLE ecos_wagent_action_effect (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_action_effect IS 'W 域行动效果（Effect：planned/actual/deviation_reason + data 层回灌事实对照，详设 §10.4.2/§12.4 幕 6~7）';

--

-- ecos_wagent_candidate (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_candidate (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    candidate_type character varying(24),
    target_engine character varying(32),
    target_object_ref_json text,
    payload_json text,
    payload_hash character varying(64),
    generated_by_run character varying(36),
    generated_by_step character varying(36),
    confidence numeric(5,4),
    basis_json text,
    validation_structure character varying(12),
    validation_reference character varying(12),
    validation_conflict character varying(12),
    validation_impact character varying(12),
    validation_regression character varying(12),
    status character varying(16),
    batch_id character varying(64),
    engine_draft_ref_text text,
    published_ref_text text,
    published_git_ref character varying(120),
    expire_time timestamp without time zone,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_wagent_candidate_status CHECK (((status IS NULL) OR ((status)::text = ANY ((ARRAY['generated', 'validating', 'validated', 'review_pending', 'approved', 'committing', 'published', 'rejected', 'expired', 'rolled_back'])::text[])))),
    CONSTRAINT ck_wagent_candidate_type CHECK (((candidate_type IS NULL) OR ((candidate_type)::text = ANY ((ARRAY['DATA_MAPPING', 'DQ_RULE', 'ENTITY', 'RELATIONSHIP', 'METRIC', 'KNOWLEDGE_ITEM', 'ASSUMPTION', 'SCENARIO', 'DECISION_OPTION', 'ACTION_DRAFT', 'COGNITIVE_MODEL_DEF', 'MODEL_ROUTE'])::text[]))))
);

--

-- TABLE ecos_wagent_candidate (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_candidate IS 'W Agent 知识/资产候选（附件 agt_candidate 重写；R-40① 统一治理，kb_extract_candidate/agent_approval 收编口径见卷 10 §6.2）';

--

-- ecos_wagent_candidate_review (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_candidate_review (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    candidate_id character varying(36) NOT NULL,
    reviewer_id character varying(100),
    decision character varying(16),
    comment_text text,
    changes_json text,
    approval_token character varying(64),
    review_time timestamp without time zone,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_wagent_candidate_review (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_candidate_review IS 'W Agent 候选评审记录（评审中心消费，卷 08；凭证链关联存量审批表，不建 FK）';

--

-- ecos_wagent_claim (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_claim (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    run_id character varying(36) NOT NULL,
    step_id character varying(36),
    claim_text text,
    claim_type character varying(12),
    numeric_value numeric(18,6),
    numeric_unit character varying(16),
    value_source character varying(24),
    evidence_grade character(1),
    verify_status character varying(16),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_wagent_claim (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_claim IS 'W Agent 断言（数字一致性校验承载，§7.1）';

--

-- ecos_wagent_claim_evidence (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_claim_evidence (
    claim_id character varying(36) NOT NULL,
    evidence_id character varying(36) NOT NULL,
    relation character varying(8),
    cover_ratio numeric(5,4),
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_wagent_claim_evidence (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_claim_evidence IS '断言×证据关联（PK(claim_id, evidence_id) 复合主键；证据主权在 V240 evidence 表）';

--

-- ecos_wagent_decision (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_decision (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    question_id character varying(36),
    goal_id character varying(36),
    readiness_id character varying(36),
    status character varying(20) DEFAULT 'DRAFT' NOT NULL,
    decision_maker_id character varying(100),
    rationale_text text,
    decision_time timestamp without time zone,
    effective_time timestamp without time zone,
    review_plan_time timestamp without time zone,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);

--

-- TABLE ecos_wagent_decision (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_decision IS 'W 域决策对象（行动五阶段：决策）；详设 §10.4.2 待定段补建，列清单单源 = 设计文（无存量代码冻结列名）';

--

-- ecos_wagent_decision_option (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_decision_option (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    decision_id character varying(36) NOT NULL,
    goal_id character varying(36),
    label character varying(255),
    lever_type character varying(16),
    lever_target_id character varying(36),
    delta_text text,
    feasibility character varying(16),
    amount_ref character varying(36),
    risk_delta numeric(9,6),
    confidence numeric(5,4),
    headcount_impact numeric(9,2),
    baseline_slot smallint,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);

--

-- TABLE ecos_wagent_decision_option (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_decision_option IS 'W 域备选方案对比（Option：feasibility/risk_delta/confidence/headcount_impact/baseline_slot，详设 §10.4.2/§10.3 七问「备选方案与比较」）；四枚金额面经 amount_ref 弱关联至 ecos_dw.ecos_application_wagent_option_amount（V286），控制域不落金额实列';

--

-- COLUMN ecos_wagent_decision_option.amount_ref (COMMENT)
COMMENT ON COLUMN ecos_ai.ecos_wagent_decision_option.amount_ref IS '金额面引用位（应用侧生成 VARCHAR(36)，不建 FK — ST09）：指向 ecos_dw.ecos_application_wagent_option_amount.option_id（V286）。四枚金额（profit/revenue/cost/cash）该表 1:1 承载；NULL=金额面尚未核算，禁在本控制域表以任何数值形态落地';

--

-- ecos_wagent_evidence (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_evidence (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    evidence_type character varying(16),
    source_engine character varying(32),
    source_object_type character varying(32),
    source_object_id character varying(64),
    source_version character varying(20),
    snapshot_ref_json text,
    locator_text text,
    content_hash character varying(64),
    classify_level character varying(16),
    is_valid smallint DEFAULT 1 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_wagent_evidence (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_evidence IS 'W Agent 运行证据（引用型，无内容字段，§7.1/§6.3）';

--

-- ecos_wagent_goal (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_goal (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    name character varying(255),
    goal_type character varying(20),
    scope_json text,
    time_range_text text,
    as_of_date date,
    metric_id character varying(36),
    metric_version character varying(20),
    caliber_version character varying(20),
    success_criteria_text text,
    owner_id character varying(100),
    due_date date,
    status character varying(20),
    slots_source_json text,
    inferred_count integer,
    supersedes_goal_id character varying(36),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_wagent_goal_type CHECK (((goal_type IS NULL) OR ((goal_type)::text = ANY ((ARRAY['FORECAST', 'DIAGNOSE', 'SIMULATE', 'RANK', 'MONITOR', 'BUILD', 'DECIDE', 'REVIEW'])::text[]))))
);

--

-- TABLE ecos_wagent_goal (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_goal IS 'W Agent 业务目标（附件 agt_business_goal 按 ECOS 模板重写；R-33① 落 ecos_ai，不建 ecos_agent schema / agt_ 前缀）';

--

-- ecos_wagent_mission (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_mission (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    goal_id character varying(36) NOT NULL,
    name character varying(255),
    stage character varying(24),
    owner_id character varying(100),
    due_date date,
    playbook_id character varying(36),
    playbook_git_ref character varying(120),
    playbook_version character varying(20),
    is_temporary smallint DEFAULT 0 NOT NULL,
    blocked_reason_text text,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_wagent_mission (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_mission IS 'W Agent 任务书（附件 agt_mission 重写；R-33① ecos_ai 五枚举内）';

--

-- ecos_wagent_question (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_question (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    mission_id character varying(36) NOT NULL,
    raw_text text,
    intent character varying(20),
    intent_confidence numeric(5,4),
    slots_json text,
    depends_on_json text,
    status character varying(20),
    latest_run_id character varying(36),
    latest_grade character(1),
    answer_summary_text text,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_wagent_question_confidence CHECK (((intent_confidence IS NULL) OR ((intent_confidence >= (0)::numeric) AND (intent_confidence <= (1)::numeric)))),
    CONSTRAINT ck_wagent_question_intent CHECK (((intent IS NULL) OR ((intent)::text = ANY ((ARRAY['FORECAST', 'DIAGNOSE', 'SIMULATE', 'RANK', 'MONITOR', 'BUILD', 'DECIDE', 'REVIEW'])::text[]))))
);

--

-- TABLE ecos_wagent_question (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_question IS 'W Agent 业务问题（附件 agt_business_question 重写）';

--

-- ecos_wagent_readiness (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_readiness (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    question_id character varying(36) NOT NULL,
    run_id character varying(36),
    profile_id character varying(36),
    profile_version character varying(20),
    grade character(1),
    layer_d character varying(10),
    layer_i character varying(10),
    layer_k character varying(10),
    layer_c character varying(10),
    layer_summary_json text,
    degradations_json text,
    computed_time timestamp without time zone,
    valid_until timestamp without time zone,
    superseded_by character varying(36),
    is_pinned smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_wagent_readiness (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_readiness IS 'DIKC 就绪评估主表（附件 §3.1 三表合并；item/gap 子表见 V231/V232）';

--

-- ecos_wagent_readiness_gap (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_readiness_gap (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    assessment_id character varying(36) NOT NULL,
    layer character(1),
    severity character varying(10),
    description_text text,
    impact_text text,
    suggested_action_text text,
    suggested_owner character varying(100),
    effort_estimate character varying(64),
    unlock_from character(1),
    unlock_to character(1),
    status character varying(20),
    resolved_time timestamp without time zone,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_wagent_readiness_gap (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_readiness_gap IS '就绪评估缺口明细（附件 §3.1 gap 分表；补齐循环走任务中心 runtime-task 单一来源）';

--

-- ecos_wagent_readiness_item (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_readiness_item (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    assessment_id character varying(36) NOT NULL,
    layer character(1),
    check_code character varying(40),
    status character varying(10),
    measured_json text,
    threshold_json text,
    evidence_ids_json text,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_wagent_readiness_item (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_readiness_item IS '就绪评估检查项明细（附件 §3.1 item 分表）';

--

-- ecos_wagent_run (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_run (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    question_id character varying(36) NOT NULL,
    mission_id character varying(36),
    parent_run_id character varying(36),
    previous_run_id character varying(36),
    plan_source character varying(12),
    plan_json text,
    status character varying(24),
    automation_ceiling smallint,
    initiated_by character varying(100),
    idempotency_key character varying(128),
    budget_json text,
    consumption_json text,
    readiness_id character varying(36),
    degradations_json text,
    situation_snapshot_json text,
    result_ref_text text,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_wagent_run_plan_source CHECK (((plan_source IS NULL) OR ((plan_source)::text = ANY ((ARRAY['PLAYBOOK', 'EXPLORED'])::text[])))),
    CONSTRAINT ck_wagent_run_status CHECK (((status IS NULL) OR ((status)::text = ANY ((ARRAY['created', 'planning', 'plan_validated', 'executing', 'awaiting_input', 'awaiting_approval', 'waiting_task', 'paused', 'completed', 'completed_degraded', 'failed', 'cancelled', 'timed_out'])::text[]))))
);

--

-- TABLE ecos_wagent_run (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_run IS 'W Agent 运行实例（附件 agt_run 重写；agent_execution 语义上位；预算/幂等承载 REQ-WAG-11）';

--

-- ecos_wagent_run_event (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_run_event (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    run_id character varying(36) NOT NULL,
    step_id character varying(36),
    event_type character varying(32),
    payload_ref_text text,
    occur_time timestamp without time zone,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_wagent_run_event (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_run_event IS 'W Agent 引用型运行事件（append-only，无分区 X-6；审计走 Kafka ecos.audit，ST06 不建 wagent_audit_event）';

--

-- ecos_wagent_run_step (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_run_step (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    run_id character varying(36) NOT NULL,
    step_key character varying(60),
    attempt smallint,
    step_type character varying(16),
    tool_name character varying(255),
    tool_version character varying(20),
    prompt_id character varying(36),
    prompt_version character varying(20),
    automation_level smallint,
    status character varying(16),
    input_ref_text text,
    input_hash character varying(64),
    output_ref_text text,
    output_hash character varying(64),
    task_id character varying(64),
    policy_effect character varying(20),
    policy_rule_id character varying(36),
    metrics_json text,
    error_code character varying(24),
    error_text text,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_wagent_step_policy_effect CHECK (((policy_effect IS NULL) OR ((policy_effect)::text = ANY ((ARRAY['allow', 'allow_with_mask', 'require_approval', 'deny'])::text[])))),
    CONSTRAINT ck_wagent_step_type CHECK (((step_type IS NULL) OR ((step_type)::text = ANY ((ARRAY['tool', 'llm', 'human_gate', 'wait_task', 'sub_run', 'aggregate'])::text[]))))
);

--

-- TABLE ecos_wagent_run_step (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_run_step IS 'W Agent 运行步骤（附件 agt_step 重写；agent_execution_step 语义上位，停写口径见卷 10 §6.2 收编段）';

--

-- ecos_wagent_skill (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_skill (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    name character varying(255) NOT NULL,
    version character varying(20) NOT NULL,
    summary character varying(200),
    content_git_ref character varying(120),
    content_version character varying(20),
    bound_playbooks_json text,
    owner character varying(100),
    status character varying(12),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_wagent_skill (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_skill IS 'W Agent 技能目录（附件 agt_skill 重写；DB 只存在用版本、历史走 Git；public.ecos_skill 收编口径见卷 10 §6.2，存量动作待 R-41）';

--

-- ecos_wagent_tool_contract (TABLE)
CREATE TABLE ecos_ai.ecos_wagent_tool_contract (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    name character varying(255) NOT NULL,
    version character varying(20) NOT NULL,
    engine character varying(32),
    category character varying(12),
    side_effect character varying(12),
    min_level smallint,
    required_permission character varying(80),
    input_schema_json text,
    output_schema_json text,
    timeout_ms integer,
    retry_text text,
    is_idempotent smallint DEFAULT 0 NOT NULL,
    cost_class character varying(8),
    is_evidence_output smallint DEFAULT 0 NOT NULL,
    data_classification_text text,
    rollback_plan_text text,
    endpoint_kind character varying(8),
    endpoint_url character varying(500),
    endpoint_method character varying(10),
    owner character varying(100),
    sla_text text,
    status character varying(12),
    deprecated_by character varying(100),
    toolset character varying(32),
    summary character varying(120),
    disclosure character varying(16),
    capability_deps_text text,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    capability_id character varying(50),
    operation_class character varying(10),
    input_schema_ref character varying(200),
    output_schema_ref character varying(200),
    evidence_required smallint,
    deterministic_output smallint,
    automation_level character varying(10),
    data_classification_max character varying(10),
    requires_approval character varying(10),
    idempotent smallint,
    idempotency_key_required smallint,
    retry_policy_ref character varying(100),
    side_effect_scope character varying(200),
    rollback_capability character varying(10),
    rollback_ref character varying(200),
    audit_event_type character varying(50),
    owner_engine character varying(32),
    CONSTRAINT ck_wagent_tool_category CHECK (((category IS NULL) OR ((category)::text = ANY ((ARRAY['read', 'compute', 'candidate', 'commit', 'notify'])::text[])))),
    CONSTRAINT ck_wagent_tool_disclosure CHECK (((disclosure IS NULL) OR ((disclosure)::text = ANY ((ARRAY['always_loaded', 'searchable'])::text[])))),
    CONSTRAINT ck_wagent_tool_rollback CHECK ((((category)::text <> 'commit'::text) OR (rollback_plan_text IS NOT NULL))),
    CONSTRAINT ck_wagent_tool_side_effect CHECK (((side_effect IS NULL) OR ((side_effect)::text = ANY ((ARRAY['none', 'draft', 'production'])::text[])))),
    CONSTRAINT ck_wagent_tool_status CHECK (((status IS NULL) OR ((status)::text = ANY ((ARRAY['draft', 'registered', 'validated', 'active', 'deprecated', 'retired'])::text[]))))
);

--

-- TABLE ecos_wagent_tool_contract (COMMENT)
COMMENT ON TABLE ecos_ai.ecos_wagent_tool_contract IS 'W Agent 工具契约（附件 §5.1/§5.4 重写；UNIQUE(name,version)；commit 类必带回滚预案）；首批 12 业务语义工具 seed 见 V266（status=registered，toolset core/analytics/action 与 V265 组 4 逐字一致；run_cognitive_model/create_decision_option/create_action_draft 三行 endpoint 为设计路径待复核）';

--

-- COLUMN ecos_wagent_tool_contract.operation_class (COMMENT)
COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.operation_class IS '<补充设计 v1.0 §11>操作类别 READ/SUGGEST/WRITE（词表单源 WAgentEnums）；SUGGEST 生成候选不改权威资产；WRITE 必走 ToolExecutor 咽喉+security 裁决+审批/幂等/审计/回滚（§11.2）';

--

-- COLUMN ecos_wagent_tool_contract.evidence_required (COMMENT)
COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.evidence_required IS '<补充设计 v1.0 §11/§7.5>是否要求来源引用（与 V236 is_evidence_output 并存；新契约读以本列为准）';

--

-- COLUMN ecos_wagent_tool_contract.automation_level (COMMENT)
COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.automation_level IS '<补充设计 v1.0 §11>自动化等级档位（显式契约位；NULL 回落 V236 min_level 语义，读侧单源在 Java 契约层合并）';

--

-- COLUMN ecos_wagent_tool_contract.data_classification_max (COMMENT)
COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.data_classification_max IS '<补充设计 v1.0 §11>允许处理的最高密级档位（security-engine 权威裁决，本列只声明上限，禁 Router/引擎内复制策略）';

--

-- COLUMN ecos_wagent_tool_contract.requires_approval (COMMENT)
COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.requires_approval IS '<补充设计 v1.0 §11>审批要求档位（NONE/HUMAN/TWO_EYE 语义；高风险 WRITE 默认人工审批，§7.2 DATA_CHANGE_APPLY 同口径）';

--

-- COLUMN ecos_wagent_tool_contract.idempotency_key_required (COMMENT)
COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.idempotency_key_required IS '<补充设计 v1.0 §7.5>幂等键强制位（同一 idempotencyKey 重试不得重复创建/重复应用）';

--

-- COLUMN ecos_wagent_tool_contract.rollback_capability (COMMENT)
COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.rollback_capability IS '<补充设计 v1.0 §11>回滚能力档位（NONE/NATURAL/MANUAL 语义；WRITE 高影响面禁止 NONE）';

--

-- COLUMN ecos_wagent_tool_contract.audit_event_type (COMMENT)
COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.audit_event_type IS '<补充设计 v1.0 §11>统一审计事件类型（值必须命中 EventTypes 单源清单，禁自造字符串，SupplEventCatalogTest 守）';

--

-- COLUMN ecos_wagent_tool_contract.owner_engine (COMMENT)
COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.owner_engine IS '<补充设计 v1.0 §11>正式语义与写入权归属引擎（值 = WAgentEngineCatalog.contractEngineCode 六名：data/ontology/kb/cognitive/ai/security；Agent 不写对方表，候选写回经属主 Engine REST，F10-23 同通道）';

--

-- forecast (TABLE)
CREATE TABLE ecos_ai.forecast (
    id character varying(64) NOT NULL,
    target_entity character varying(128),
    target_metric character varying(128),
    horizon character varying(32),
    "values" jsonb DEFAULT '[]'::jsonb,
    model character varying(64),
    confidence numeric(5,4) DEFAULT 0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE forecast (COMMENT)
COMMENT ON TABLE ecos_ai.forecast IS '预测表';

--

-- optimization_job (TABLE)
CREATE TABLE ecos_ai.optimization_job (
    id character varying(64) NOT NULL,
    objective text,
    constraints jsonb DEFAULT '[]'::jsonb,
    status character varying(32) DEFAULT 'CREATED',
    result jsonb DEFAULT '{}'::jsonb,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE optimization_job (COMMENT)
COMMENT ON TABLE ecos_ai.optimization_job IS '优化任务表';

--

-- scenario (TABLE)
CREATE TABLE ecos_ai.scenario (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(32) DEFAULT 'CUSTOM',
    assumptions jsonb DEFAULT '{}'::jsonb,
    description text,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE scenario (COMMENT)
COMMENT ON TABLE ecos_ai.scenario IS '场景表';

--

-- simulation (TABLE)
CREATE TABLE ecos_ai.simulation (
    id character varying(64) NOT NULL,
    scenario_id character varying(64),
    status character varying(32) DEFAULT 'CREATED',
    config jsonb DEFAULT '{}'::jsonb,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE simulation (COMMENT)
COMMENT ON TABLE ecos_ai.simulation IS '仿真表';

--

-- simulation_result (TABLE)
CREATE TABLE ecos_ai.simulation_result (
    id character varying(64) NOT NULL,
    simulation_id character varying(64),
    output_state jsonb DEFAULT '{}'::jsonb,
    predictions jsonb DEFAULT '{}'::jsonb,
    confidence numeric(5,4) DEFAULT 0,
    summary text,
    completed_at timestamp without time zone
);

--

-- TABLE simulation_result (COMMENT)
COMMENT ON TABLE ecos_ai.simulation_result IS '仿真结果表';

--

-- strategy_recommendation (TABLE)
CREATE TABLE ecos_ai.strategy_recommendation (
    id character varying(64) NOT NULL,
    goal text,
    actions jsonb DEFAULT '[]'::jsonb,
    expected_impact numeric(5,4) DEFAULT 0,
    risk_level numeric(5,4) DEFAULT 0,
    reasoning text,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE strategy_recommendation (COMMENT)
COMMENT ON TABLE ecos_ai.strategy_recommendation IS '策略推荐表';

--

-- sys_agent_call_log (TABLE)
CREATE TABLE ecos_ai.sys_agent_call_log (
    id character varying(64) NOT NULL,
    subsystem character varying(64),
    profile_name character varying(128),
    session_id character varying(64),
    user_message text,
    tokens_input integer DEFAULT 0,
    tokens_output integer DEFAULT 0,
    duration_ms integer DEFAULT 0,
    status character varying(16) DEFAULT 'success',
    error_msg text,
    tenant_id character varying(64),
    created_time timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE sys_agent_call_log (COMMENT)
COMMENT ON TABLE ecos_ai.sys_agent_call_log IS 'Agent调用日志表(Hermes)';

--

-- sys_agent_profile (TABLE)
CREATE TABLE ecos_ai.sys_agent_profile (
    id character varying(64) NOT NULL,
    profile_name character varying(128) NOT NULL,
    subsystem character varying(64),
    enabled boolean DEFAULT true,
    description character varying(512),
    provider character varying(64),
    model character varying(128),
    base_url character varying(512),
    api_key_ref character varying(256),
    temperature double precision,
    max_tokens integer,
    system_prompt text,
    max_iterations integer DEFAULT 10,
    session_timeout_sec integer DEFAULT 300,
    tools_enabled boolean DEFAULT true,
    auto_approve boolean DEFAULT false,
    allowed_tools text,
    concurrency integer DEFAULT 5,
    priority integer DEFAULT 0,
    tenant_id character varying(64),
    created_by character varying(64),
    created_time timestamp without time zone DEFAULT now() NOT NULL,
    updated_by character varying(64),
    updated_time timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE sys_agent_profile (COMMENT)
COMMENT ON TABLE ecos_ai.sys_agent_profile IS 'Agent配置档案表(Hermes)';

--

-- world_snapshot (TABLE)
CREATE TABLE ecos_ai.world_snapshot (
    id character varying(64) NOT NULL,
    state_id character varying(64),
    snapshot_type character varying(32),
    data jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE world_snapshot (COMMENT)
COMMENT ON TABLE ecos_ai.world_snapshot IS '世界快照表';

--

-- world_state (TABLE)
CREATE TABLE ecos_ai.world_state (
    id character varying(64) NOT NULL,
    "timestamp" timestamp without time zone,
    state_data jsonb DEFAULT '{}'::jsonb
);

--

-- TABLE world_state (COMMENT)
COMMENT ON TABLE ecos_ai.world_state IS '世界状态表';

--

-- agent_cost_default (TABLE ATTACH)
ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_default DEFAULT;

--

-- ecos_agent_alert id (DEFAULT)
ALTER TABLE ONLY ecos_ai.ecos_agent_alert ALTER COLUMN id SET DEFAULT nextval('ecos_ai.ecos_agent_alert_id_seq'::regclass);

--

-- ecos_agent_metrics id (DEFAULT)
ALTER TABLE ONLY ecos_ai.ecos_agent_metrics ALTER COLUMN id SET DEFAULT nextval('ecos_ai.ecos_agent_metrics_id_seq'::regclass);

--

-- ecos_cron_job id (DEFAULT)
ALTER TABLE ONLY ecos_ai.ecos_cron_job ALTER COLUMN id SET DEFAULT nextval('ecos_ai.ecos_cron_job_id_seq'::regclass);

--

-- ecos_cron_job_execution id (DEFAULT)
ALTER TABLE ONLY ecos_ai.ecos_cron_job_execution ALTER COLUMN id SET DEFAULT nextval('ecos_ai.ecos_cron_job_execution_id_seq'::regclass);

--

-- ecos_decision_case id (DEFAULT)
ALTER TABLE ONLY ecos_ai.ecos_decision_case ALTER COLUMN id SET DEFAULT nextval('ecos_ai.ecos_decision_case_id_seq'::regclass);

--

-- ecos_skill id (DEFAULT)
ALTER TABLE ONLY ecos_ai.ecos_skill ALTER COLUMN id SET DEFAULT nextval('ecos_ai.ecos_skill_id_seq'::regclass);

--

-- agent_approval agent_approval_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.agent_approval
    ADD CONSTRAINT agent_approval_pkey PRIMARY KEY (id);

--

-- agent_cost agent_cost_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.agent_cost
    ADD CONSTRAINT agent_cost_pkey PRIMARY KEY (id, created_at);

--

-- agent_cost_default agent_cost_default_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.agent_cost_default
    ADD CONSTRAINT agent_cost_default_pkey PRIMARY KEY (id, created_at);

--

-- agent_definition agent_definition_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.agent_definition
    ADD CONSTRAINT agent_definition_pkey PRIMARY KEY (id);

--

-- agent_evaluation agent_evaluation_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.agent_evaluation
    ADD CONSTRAINT agent_evaluation_pkey PRIMARY KEY (id);

--

-- agent_execution agent_execution_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.agent_execution
    ADD CONSTRAINT agent_execution_pkey PRIMARY KEY (id);

--

-- agent_execution_step agent_execution_step_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.agent_execution_step
    ADD CONSTRAINT agent_execution_step_pkey PRIMARY KEY (id);

--

-- agent_governance_policy agent_governance_policy_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.agent_governance_policy
    ADD CONSTRAINT agent_governance_policy_pkey PRIMARY KEY (id);

--

-- agent_memory agent_memory_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.agent_memory
    ADD CONSTRAINT agent_memory_pkey PRIMARY KEY (id);

--

-- agent_registry agent_registry_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.agent_registry
    ADD CONSTRAINT agent_registry_pkey PRIMARY KEY (id);

--

-- causal_edge causal_edge_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.causal_edge
    ADD CONSTRAINT causal_edge_pkey PRIMARY KEY (id);

--

-- ecos_agent_alert ecos_agent_alert_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_agent_alert
    ADD CONSTRAINT ecos_agent_alert_pkey PRIMARY KEY (id);

--

-- ecos_agent_metrics ecos_agent_metrics_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_agent_metrics
    ADD CONSTRAINT ecos_agent_metrics_pkey PRIMARY KEY (id);

--

-- ecos_agent ecos_agent_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_agent
    ADD CONSTRAINT ecos_agent_pkey PRIMARY KEY (id);

--

-- ecos_agent_registry ecos_agent_registry_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_agent_registry
    ADD CONSTRAINT ecos_agent_registry_pkey PRIMARY KEY (id);

--

-- ecos_ai_capability_task ecos_ai_capability_task_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_ai_capability_task
    ADD CONSTRAINT ecos_ai_capability_task_pkey PRIMARY KEY (id);

--

-- ecos_aip_pipeline ecos_aip_pipeline_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_aip_pipeline
    ADD CONSTRAINT ecos_aip_pipeline_pkey PRIMARY KEY (id);

--

-- ecos_cron_job_execution ecos_cron_job_execution_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_cron_job_execution
    ADD CONSTRAINT ecos_cron_job_execution_pkey PRIMARY KEY (id);

--

-- ecos_cron_job ecos_cron_job_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_cron_job
    ADD CONSTRAINT ecos_cron_job_pkey PRIMARY KEY (id);

--

-- ecos_decision_case ecos_decision_case_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_decision_case
    ADD CONSTRAINT ecos_decision_case_pkey PRIMARY KEY (id);

--

-- ecos_evolution_log ecos_evolution_log_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_evolution_log
    ADD CONSTRAINT ecos_evolution_log_pkey PRIMARY KEY (id);

--

-- ecos_guardrail_policy ecos_guardrail_policy_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_guardrail_policy
    ADD CONSTRAINT ecos_guardrail_policy_pkey PRIMARY KEY (id);

--

-- ecos_mission ecos_mission_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_mission
    ADD CONSTRAINT ecos_mission_pkey PRIMARY KEY (id);

--

-- ecos_mission_task ecos_mission_task_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_mission_task
    ADD CONSTRAINT ecos_mission_task_pkey PRIMARY KEY (id);

--

-- ecos_skill ecos_skill_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_skill
    ADD CONSTRAINT ecos_skill_pkey PRIMARY KEY (id);

--

-- ecos_tool_definition ecos_tool_definition_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_tool_definition
    ADD CONSTRAINT ecos_tool_definition_pkey PRIMARY KEY (id);

--

-- ecos_wagent_action_effect ecos_wagent_action_effect_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_action_effect
    ADD CONSTRAINT ecos_wagent_action_effect_pkey PRIMARY KEY (id);

--

-- ecos_wagent_action ecos_wagent_action_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_action
    ADD CONSTRAINT ecos_wagent_action_pkey PRIMARY KEY (id);

--

-- ecos_wagent_candidate ecos_wagent_candidate_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_candidate
    ADD CONSTRAINT ecos_wagent_candidate_pkey PRIMARY KEY (id);

--

-- ecos_wagent_candidate_review ecos_wagent_candidate_review_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_candidate_review
    ADD CONSTRAINT ecos_wagent_candidate_review_pkey PRIMARY KEY (id);

--

-- ecos_wagent_claim ecos_wagent_claim_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_claim
    ADD CONSTRAINT ecos_wagent_claim_pkey PRIMARY KEY (id);

--

-- ecos_wagent_decision_option ecos_wagent_decision_option_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_decision_option
    ADD CONSTRAINT ecos_wagent_decision_option_pkey PRIMARY KEY (id);

--

-- ecos_wagent_decision ecos_wagent_decision_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_decision
    ADD CONSTRAINT ecos_wagent_decision_pkey PRIMARY KEY (id);

--

-- ecos_wagent_evidence ecos_wagent_evidence_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_evidence
    ADD CONSTRAINT ecos_wagent_evidence_pkey PRIMARY KEY (id);

--

-- ecos_wagent_goal ecos_wagent_goal_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_goal
    ADD CONSTRAINT ecos_wagent_goal_pkey PRIMARY KEY (id);

--

-- ecos_wagent_mission ecos_wagent_mission_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_mission
    ADD CONSTRAINT ecos_wagent_mission_pkey PRIMARY KEY (id);

--

-- ecos_wagent_question ecos_wagent_question_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_question
    ADD CONSTRAINT ecos_wagent_question_pkey PRIMARY KEY (id);

--

-- ecos_wagent_readiness_gap ecos_wagent_readiness_gap_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_readiness_gap
    ADD CONSTRAINT ecos_wagent_readiness_gap_pkey PRIMARY KEY (id);

--

-- ecos_wagent_readiness_item ecos_wagent_readiness_item_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_readiness_item
    ADD CONSTRAINT ecos_wagent_readiness_item_pkey PRIMARY KEY (id);

--

-- ecos_wagent_readiness ecos_wagent_readiness_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_readiness
    ADD CONSTRAINT ecos_wagent_readiness_pkey PRIMARY KEY (id);

--

-- ecos_wagent_run_event ecos_wagent_run_event_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_run_event
    ADD CONSTRAINT ecos_wagent_run_event_pkey PRIMARY KEY (id);

--

-- ecos_wagent_run ecos_wagent_run_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_run
    ADD CONSTRAINT ecos_wagent_run_pkey PRIMARY KEY (id);

--

-- ecos_wagent_run_step ecos_wagent_run_step_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_run_step
    ADD CONSTRAINT ecos_wagent_run_step_pkey PRIMARY KEY (id);

--

-- ecos_wagent_skill ecos_wagent_skill_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_skill
    ADD CONSTRAINT ecos_wagent_skill_pkey PRIMARY KEY (id);

--

-- ecos_wagent_tool_contract ecos_wagent_tool_contract_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_tool_contract
    ADD CONSTRAINT ecos_wagent_tool_contract_pkey PRIMARY KEY (id);

--

-- forecast forecast_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.forecast
    ADD CONSTRAINT forecast_pkey PRIMARY KEY (id);

--

-- optimization_job optimization_job_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.optimization_job
    ADD CONSTRAINT optimization_job_pkey PRIMARY KEY (id);

--

-- ecos_ai_guardrail_decision_ledger pk_agdl (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_ai_guardrail_decision_ledger
    ADD CONSTRAINT pk_agdl PRIMARY KEY (id);

--

-- ecos_ai_tool_permission_binding pk_ai_tool_perm (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_ai_tool_permission_binding
    ADD CONSTRAINT pk_ai_tool_perm PRIMARY KEY (id);

--

-- ecos_business_scenario pk_ecos_ai_business_scenario (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_business_scenario
    ADD CONSTRAINT pk_ecos_ai_business_scenario PRIMARY KEY (id);

--

-- ecos_ai_compression_batch pk_ecos_ai_compression_batch (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_ai_compression_batch
    ADD CONSTRAINT pk_ecos_ai_compression_batch PRIMARY KEY (id);

--

-- ecos_ai_tool_registry pk_ecos_ai_tool_registry (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_ai_tool_registry
    ADD CONSTRAINT pk_ecos_ai_tool_registry PRIMARY KEY (id);

--

-- ecos_wagent_claim_evidence pk_wagent_claim_evidence (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.ecos_wagent_claim_evidence
    ADD CONSTRAINT pk_wagent_claim_evidence PRIMARY KEY (claim_id, evidence_id);

--

-- scenario scenario_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.scenario
    ADD CONSTRAINT scenario_pkey PRIMARY KEY (id);

--

-- simulation simulation_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.simulation
    ADD CONSTRAINT simulation_pkey PRIMARY KEY (id);

--

-- simulation_result simulation_result_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.simulation_result
    ADD CONSTRAINT simulation_result_pkey PRIMARY KEY (id);

--

-- strategy_recommendation strategy_recommendation_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.strategy_recommendation
    ADD CONSTRAINT strategy_recommendation_pkey PRIMARY KEY (id);

--

-- sys_agent_call_log sys_agent_call_log_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.sys_agent_call_log
    ADD CONSTRAINT sys_agent_call_log_pkey PRIMARY KEY (id);

--

-- sys_agent_profile sys_agent_profile_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.sys_agent_profile
    ADD CONSTRAINT sys_agent_profile_pkey PRIMARY KEY (id);

--

-- world_snapshot world_snapshot_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.world_snapshot
    ADD CONSTRAINT world_snapshot_pkey PRIMARY KEY (id);

--

-- world_state world_state_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ai.world_state
    ADD CONSTRAINT world_state_pkey PRIMARY KEY (id);

--

-- idx_acb_mode (INDEX)
CREATE INDEX idx_acb_mode ON ecos_ai.ecos_ai_compression_batch USING btree (mode, create_time);

--

-- idx_acb_session (INDEX)
CREATE INDEX idx_acb_session ON ecos_ai.ecos_ai_compression_batch USING btree (session_id, create_time);

--

-- idx_agdl_tool (INDEX)
CREATE INDEX idx_agdl_tool ON ecos_ai.ecos_ai_guardrail_decision_ledger USING btree (tool_key, decision, create_time);

--

-- idx_agdl_trace (INDEX)
CREATE INDEX idx_agdl_trace ON ecos_ai.ecos_ai_guardrail_decision_ledger USING btree (trace_id);

--

-- idx_agent_approval_expired (INDEX)
CREATE INDEX idx_agent_approval_expired ON ecos_ai.agent_approval USING btree (expired_at);

--

-- idx_agent_approval_scope (INDEX)
CREATE INDEX idx_agent_approval_scope ON ecos_ai.agent_approval USING btree (scope_type, scope_id);

--

-- idx_agent_approval_trace (INDEX)
CREATE INDEX idx_agent_approval_trace ON ecos_ai.agent_approval USING btree (trace_id);

--

-- idx_agent_metrics_agent (INDEX)
CREATE INDEX idx_agent_metrics_agent ON ecos_ai.ecos_agent_metrics USING btree (agent_id, created_at DESC);

--

-- idx_ai_agent_status (INDEX)
CREATE INDEX idx_ai_agent_status ON ecos_ai.ecos_agent USING btree (status);

--

-- idx_ai_calllog_profile (INDEX)
CREATE INDEX idx_ai_calllog_profile ON ecos_ai.sys_agent_call_log USING btree (profile_name);

--

-- idx_ai_calllog_time (INDEX)
CREATE INDEX idx_ai_calllog_time ON ecos_ai.sys_agent_call_log USING btree (created_time DESC);

--

-- idx_ai_causal_src (INDEX)
CREATE INDEX idx_ai_causal_src ON ecos_ai.causal_edge USING btree (source_node);

--

-- idx_ai_causal_tgt (INDEX)
CREATE INDEX idx_ai_causal_tgt ON ecos_ai.causal_edge USING btree (target_node);

--

-- idx_ai_dcase_tags (INDEX)
CREATE INDEX idx_ai_dcase_tags ON ecos_ai.ecos_decision_case USING gin (tags);

--

-- idx_ai_exec_agent (INDEX)
CREATE INDEX idx_ai_exec_agent ON ecos_ai.agent_execution USING btree (agent_id);

--

-- idx_ai_exec_status (INDEX)
CREATE INDEX idx_ai_exec_status ON ecos_ai.agent_execution USING btree (status);

--

-- idx_ai_mem_agent (INDEX)
CREATE INDEX idx_ai_mem_agent ON ecos_ai.agent_memory USING btree (agent_id);

--

-- idx_ai_mem_session (INDEX)
CREATE INDEX idx_ai_mem_session ON ecos_ai.agent_memory USING btree (session_id);

--

-- idx_ai_mtask_mission (INDEX)
CREATE INDEX idx_ai_mtask_mission ON ecos_ai.ecos_mission_task USING btree (mission_id);

--

-- idx_ai_scenario_type (INDEX)
CREATE INDEX idx_ai_scenario_type ON ecos_ai.scenario USING btree (type);

--

-- idx_ai_sim_status (INDEX)
CREATE INDEX idx_ai_sim_status ON ecos_ai.simulation USING btree (status);

--

-- idx_ai_step_exec (INDEX)
CREATE INDEX idx_ai_step_exec ON ecos_ai.agent_execution_step USING btree (execution_id);

--

-- idx_ai_tool_code (INDEX)
CREATE UNIQUE INDEX idx_ai_tool_code ON ecos_ai.ecos_tool_definition USING btree (code);

--

-- idx_aip_pipeline_status (INDEX)
CREATE INDEX idx_aip_pipeline_status ON ecos_ai.ecos_aip_pipeline USING btree (status);

--

-- idx_aip_pipeline_tenant (INDEX)
CREATE INDEX idx_aip_pipeline_tenant ON ecos_ai.ecos_aip_pipeline USING btree (tenant_id);

--

-- idx_atpb_role (INDEX)
CREATE INDEX idx_atpb_role ON ecos_ai.ecos_ai_tool_permission_binding USING btree (role_key, is_deleted);

--

-- idx_atpb_tool (INDEX)
CREATE INDEX idx_atpb_tool ON ecos_ai.ecos_ai_tool_permission_binding USING btree (tool_key, is_deleted);

--

-- idx_atr_status (INDEX)
CREATE INDEX idx_atr_status ON ecos_ai.ecos_ai_tool_registry USING btree (status, is_deleted);

--

-- idx_causal_source (INDEX)
CREATE INDEX idx_causal_source ON ecos_ai.causal_edge USING btree (source_node);

--

-- idx_causal_target (INDEX)
CREATE INDEX idx_causal_target ON ecos_ai.causal_edge USING btree (target_node);

--

-- idx_cv269_cap_run (INDEX)
CREATE INDEX idx_cv269_cap_run ON ecos_ai.ecos_ai_capability_task USING btree (run_id, is_deleted);

--

-- idx_cv269_cap_stat (INDEX)
CREATE INDEX idx_cv269_cap_stat ON ecos_ai.ecos_ai_capability_task USING btree (status, is_deleted);

--

-- idx_cv269_cap_tid (INDEX)
CREATE INDEX idx_cv269_cap_tid ON ecos_ai.ecos_ai_capability_task USING btree (tenant_id, capability_id, is_deleted);

--

-- idx_ecos_ai_bizscn_dept (INDEX)
CREATE INDEX idx_ecos_ai_bizscn_dept ON ecos_ai.ecos_business_scenario USING btree (department, is_deleted);

--

-- idx_ecos_ai_bizscn_status (INDEX)
CREATE INDEX idx_ecos_ai_bizscn_status ON ecos_ai.ecos_business_scenario USING btree (status, is_deleted);

--

-- idx_scenario_type (INDEX)
CREATE INDEX idx_scenario_type ON ecos_ai.scenario USING btree (type);

--

-- idx_simulation_status (INDEX)
CREATE INDEX idx_simulation_status ON ecos_ai.simulation USING btree (status);

--

-- idx_v270_tc_capability (INDEX)
CREATE INDEX idx_v270_tc_capability ON ecos_ai.ecos_wagent_tool_contract USING btree (capability_id, is_deleted);

--

-- idx_wagent_action_option (INDEX)
CREATE INDEX idx_wagent_action_option ON ecos_ai.ecos_wagent_action USING btree (option_id);

--

-- idx_wagent_action_tenant_status (INDEX)
CREATE INDEX idx_wagent_action_tenant_status ON ecos_ai.ecos_wagent_action USING btree (tenant_id, status);

--

-- idx_wagent_cand_batch (INDEX)
CREATE INDEX idx_wagent_cand_batch ON ecos_ai.ecos_wagent_candidate USING btree (batch_id);

--

-- idx_wagent_cand_rev_cand (INDEX)
CREATE INDEX idx_wagent_cand_rev_cand ON ecos_ai.ecos_wagent_candidate_review USING btree (candidate_id, review_time);

--

-- idx_wagent_cand_run (INDEX)
CREATE INDEX idx_wagent_cand_run ON ecos_ai.ecos_wagent_candidate USING btree (generated_by_run);

--

-- idx_wagent_cand_status (INDEX)
CREATE INDEX idx_wagent_cand_status ON ecos_ai.ecos_wagent_candidate USING btree (status, expire_time);

--

-- idx_wagent_claim_ev_evidence (INDEX)
CREATE INDEX idx_wagent_claim_ev_evidence ON ecos_ai.ecos_wagent_claim_evidence USING btree (evidence_id);

--

-- idx_wagent_claim_run (INDEX)
CREATE INDEX idx_wagent_claim_run ON ecos_ai.ecos_wagent_claim USING btree (run_id, verify_status);

--

-- idx_wagent_effect_action (INDEX)
CREATE INDEX idx_wagent_effect_action ON ecos_ai.ecos_wagent_action_effect USING btree (action_id);

--

-- idx_wagent_event_run (INDEX)
CREATE INDEX idx_wagent_event_run ON ecos_ai.ecos_wagent_run_event USING btree (run_id, occur_time);

--

-- idx_wagent_evidence_src (INDEX)
CREATE INDEX idx_wagent_evidence_src ON ecos_ai.ecos_wagent_evidence USING btree (source_engine, source_object_type);

--

-- idx_wagent_gap_assess (INDEX)
CREATE INDEX idx_wagent_gap_assess ON ecos_ai.ecos_wagent_readiness_gap USING btree (assessment_id, status);

--

-- idx_wagent_gap_tenant (INDEX)
CREATE INDEX idx_wagent_gap_tenant ON ecos_ai.ecos_wagent_readiness_gap USING btree (tenant_id, severity);

--

-- idx_wagent_goal_owner (INDEX)
CREATE INDEX idx_wagent_goal_owner ON ecos_ai.ecos_wagent_goal USING btree (owner_id);

--

-- idx_wagent_goal_tenant (INDEX)
CREATE INDEX idx_wagent_goal_tenant ON ecos_ai.ecos_wagent_goal USING btree (tenant_id, status);

--

-- idx_wagent_mission_goal (INDEX)
CREATE INDEX idx_wagent_mission_goal ON ecos_ai.ecos_wagent_mission USING btree (goal_id);

--

-- idx_wagent_mission_tenant (INDEX)
CREATE INDEX idx_wagent_mission_tenant ON ecos_ai.ecos_wagent_mission USING btree (tenant_id, stage);

--

-- idx_wagent_question_mission (INDEX)
CREATE INDEX idx_wagent_question_mission ON ecos_ai.ecos_wagent_question USING btree (mission_id);

--

-- idx_wagent_question_tenant (INDEX)
CREATE INDEX idx_wagent_question_tenant ON ecos_ai.ecos_wagent_question USING btree (tenant_id, status);

--

-- idx_wagent_ready_item_assess (INDEX)
CREATE INDEX idx_wagent_ready_item_assess ON ecos_ai.ecos_wagent_readiness_item USING btree (assessment_id, layer);

--

-- idx_wagent_ready_question (INDEX)
CREATE INDEX idx_wagent_ready_question ON ecos_ai.ecos_wagent_readiness USING btree (question_id, valid_until);

--

-- idx_wagent_ready_tenant (INDEX)
CREATE INDEX idx_wagent_ready_tenant ON ecos_ai.ecos_wagent_readiness USING btree (tenant_id, grade);

--

-- idx_wagent_run_question (INDEX)
CREATE INDEX idx_wagent_run_question ON ecos_ai.ecos_wagent_run USING btree (question_id, status);

--

-- idx_wagent_skill_status (INDEX)
CREATE INDEX idx_wagent_skill_status ON ecos_ai.ecos_wagent_skill USING btree (status);

--

-- idx_wagent_step_run (INDEX)
CREATE INDEX idx_wagent_step_run ON ecos_ai.ecos_wagent_run_step USING btree (run_id, status);

--

-- idx_wagent_tool_engine (INDEX)
CREATE INDEX idx_wagent_tool_engine ON ecos_ai.ecos_wagent_tool_contract USING btree (engine, status);

--

-- idx_wagent_tool_toolset (INDEX)
CREATE INDEX idx_wagent_tool_toolset ON ecos_ai.ecos_wagent_tool_contract USING btree (toolset);

--

-- uk_agent_approval_token_digest (INDEX)
CREATE UNIQUE INDEX uk_agent_approval_token_digest ON ecos_ai.agent_approval USING btree (token_digest);

--

-- uk_wagent_option_decision_baseline (INDEX)
CREATE UNIQUE INDEX uk_wagent_option_decision_baseline ON ecos_ai.ecos_wagent_decision_option USING btree (decision_id, baseline_slot);

--

-- uniq_aip_pipeline_name_ver (INDEX)
CREATE UNIQUE INDEX uniq_aip_pipeline_name_ver ON ecos_ai.ecos_aip_pipeline USING btree (name, version_no, is_deleted);

--

-- uniq_atr_key (INDEX)
CREATE UNIQUE INDEX uniq_atr_key ON ecos_ai.ecos_ai_tool_registry USING btree (tool_key, is_deleted);

--

-- uniq_wagent_run_idem (INDEX)
CREATE UNIQUE INDEX uniq_wagent_run_idem ON ecos_ai.ecos_wagent_run USING btree (tenant_id, initiated_by, idempotency_key, is_deleted);

--

-- uniq_wagent_skill_name_ver (INDEX)
CREATE UNIQUE INDEX uniq_wagent_skill_name_ver ON ecos_ai.ecos_wagent_skill USING btree (name, version, is_deleted);

--

-- uniq_wagent_step_attempt (INDEX)
CREATE UNIQUE INDEX uniq_wagent_step_attempt ON ecos_ai.ecos_wagent_run_step USING btree (run_id, step_key, attempt, is_deleted);

--

-- uniq_wagent_tool_name_ver (INDEX)
CREATE UNIQUE INDEX uniq_wagent_tool_name_ver ON ecos_ai.ecos_wagent_tool_contract USING btree (name, version, is_deleted);

--

-- agent_cost_default_pkey (INDEX ATTACH)
ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_default_pkey;

--

