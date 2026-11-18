--
-- PostgreSQL database dump
--

\restrict C4Vyw4KNb9LR9sYv9sDE9AybHwWJsBCI850LosvId4l8RbfgTDb0ohoMKMdcfh0

-- Dumped from database version 16.15 (Debian 16.15-1.pgdg12+2)
-- Dumped by pg_dump version 16.15 (Debian 16.15-1.pgdg12+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: ecos_ai; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA ecos_ai;


--
-- Name: ecos_cognitive; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA ecos_cognitive;


--
-- Name: ecos_control; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA ecos_control;


--
-- Name: ecos_data; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA ecos_data;


--
-- Name: ecos_dw; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA ecos_dw;


--
-- Name: SCHEMA ecos_dw; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA ecos_dw IS 'DW 层（CURATED）业务表 schema — 数据工作台唯一写入，本体/知识只读';


--
-- Name: ecos_infra; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA ecos_infra;


--
-- Name: ecos_knowledge; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA ecos_knowledge;


--
-- Name: ecos_ontology; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA ecos_ontology;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: agent_approval; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_approval (
    id character varying(64) NOT NULL,
    task_id character varying(64),
    risk_level character varying(4),
    status character varying(16) DEFAULT 'PENDING'::character varying,
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
-- Name: TABLE agent_approval; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.agent_approval IS 'Agent审批表';


--
-- Name: COLUMN agent_approval.token_digest; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.agent_approval.token_digest IS '审批凭证 SHA-256 摘要（禁存明文；明文仅签发响应出现一次）';


--
-- Name: COLUMN agent_approval.scope_type; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.agent_approval.scope_type IS '凭证作用域类型：CANDIDATE_PUBLISH / ACTION_COMMIT / RUN_APPROVAL（与 WAgentApprovalScope 单源）';


--
-- Name: COLUMN agent_approval.scope_id; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.agent_approval.scope_id IS '作用域对象 ID（candidateId / actionId）；凭证不得跨对象复用';


--
-- Name: COLUMN agent_approval.requested_by; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.agent_approval.requested_by IS '申请人（提交该 L3 动作的操作者）；四眼要求 approved_by ≠ requested_by';


--
-- Name: COLUMN agent_approval.expired_at; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.agent_approval.expired_at IS '凭证过期时刻；过期即拒绝（E-POLICY），不自动续期';


--
-- Name: COLUMN agent_approval.consumed_at; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.agent_approval.consumed_at IS '一次性消费时刻；消费后不可再用（CAS 置位，重放必拒）';


--
-- Name: COLUMN agent_approval.trace_id; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.agent_approval.trace_id IS '链路标识（与 ecos_audit_log.trace_id 同义，供凭证操作与审计行串联核对）';


--
-- Name: agent_cost; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
)
PARTITION BY RANGE (created_at);


--
-- Name: TABLE agent_cost; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.agent_cost IS 'Agent成本表(按月分区)';


--
-- Name: agent_cost_2025_01; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2025_01 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2025_02; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2025_02 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2025_03; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2025_03 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2025_04; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2025_04 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2025_05; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2025_05 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2025_06; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2025_06 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2025_07; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2025_07 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2025_08; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2025_08 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2025_09; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2025_09 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2025_10; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2025_10 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2025_11; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2025_11 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2025_12; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2025_12 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2026_01; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2026_01 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2026_02; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2026_02 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2026_03; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2026_03 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2026_04; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2026_04 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2026_05; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2026_05 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2026_06; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2026_06 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2026_07; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2026_07 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2026_08; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2026_08 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2026_09; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2026_09 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2026_10; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2026_10 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2026_11; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2026_11 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_2026_12; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_2026_12 (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_cost_default; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_cost_default (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    execution_id character varying(64),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_cost numeric(10,4) DEFAULT 0,
    currency character varying(8) DEFAULT 'CNY'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: agent_definition; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_definition (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(32),
    role character varying(128),
    description text,
    capability jsonb DEFAULT '{}'::jsonb,
    config jsonb DEFAULT '{}'::jsonb,
    status character varying(32) DEFAULT 'ACTIVE'::character varying,
    version integer DEFAULT 1,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE agent_definition; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.agent_definition IS 'Agent定义表(V50)';


--
-- Name: agent_evaluation; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
-- Name: TABLE agent_evaluation; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.agent_evaluation IS 'Agent评估表';


--
-- Name: agent_execution; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_execution (
    id character varying(64) NOT NULL,
    agent_id character varying(64) NOT NULL,
    goal text,
    plan jsonb,
    status character varying(32) DEFAULT 'CREATED'::character varying,
    result jsonb,
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE agent_execution; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.agent_execution IS 'Agent执行表';


--
-- Name: agent_execution_step; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_execution_step (
    id character varying(64) NOT NULL,
    execution_id character varying(64) NOT NULL,
    step_order integer,
    instruction text,
    tool_type character varying(16),
    tool_params jsonb,
    status character varying(32) DEFAULT 'PENDING'::character varying,
    output text,
    metrics jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE agent_execution_step; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.agent_execution_step IS 'Agent执行步骤表';


--
-- Name: agent_governance_policy; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_governance_policy (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    type character varying(16),
    rule jsonb DEFAULT '{}'::jsonb,
    enabled boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE agent_governance_policy; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.agent_governance_policy IS 'Agent治理策略表';


--
-- Name: agent_memory; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
-- Name: TABLE agent_memory; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.agent_memory IS 'Agent记忆表';


--
-- Name: agent_registry; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.agent_registry (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    role character varying(128),
    capability jsonb DEFAULT '{}'::jsonb,
    status character varying(32) DEFAULT 'ACTIVE'::character varying,
    endpoint character varying(512),
    metadata jsonb DEFAULT '{}'::jsonb
);


--
-- Name: causal_edge; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.causal_edge (
    id character varying(64) NOT NULL,
    source_node character varying(128),
    target_node character varying(128),
    weight numeric(5,4) DEFAULT 0.5,
    description text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE causal_edge; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.causal_edge IS '因果边表';


--
-- Name: ecos_agent; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.ecos_agent (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    model_provider character varying(64) DEFAULT 'deepseek'::character varying,
    model_name character varying(128) DEFAULT 'deepseek-v4-flash'::character varying,
    system_prompt text,
    tools text DEFAULT '[]'::text,
    knowledge text DEFAULT '[]'::text,
    status character varying(32) DEFAULT 'draft'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_agent; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_agent IS 'Agent配置表(旧版)';


--
-- Name: ecos_agent_registry; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.ecos_agent_registry (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    role character varying(128),
    capability jsonb DEFAULT '{}'::jsonb,
    status character varying(32) DEFAULT 'ACTIVE'::character varying,
    endpoint character varying(512),
    metadata jsonb DEFAULT '{}'::jsonb,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_agent_registry; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_agent_registry IS 'Agent注册表';


--
-- Name: ecos_ai_capability_task; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_ai_capability_task; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_ai_capability_task IS '能力调用台账（补充设计 v1.0 §10.4/10.5）：只存引用与元数据；LLM/Embedding 唯一出口仍 llm-gateway；小模型节点复用，禁第二套管理（§10.6/SUP-MODEL-01~03）';


--
-- Name: ecos_ai_compression_batch; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone,
    CONSTRAINT ck_acb_mode CHECK (((mode)::text = ANY ((ARRAY['LLM_SUMMARY'::character varying, 'TRUNCATE_FALLBACK'::character varying])::text[])))
);


--
-- Name: TABLE ecos_ai_compression_batch; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_ai_compression_batch IS '会话压缩批次头（F06-09；R-19 并建合规新表落 ecos_ai；session_id 跨 schema 逻辑引用不建 FK）';


--
-- Name: ecos_ai_guardrail_decision_ledger; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone,
    CONSTRAINT ck_agdl_decision CHECK (((decision)::text = ANY ((ARRAY['ALLOW'::character varying, 'DENY'::character varying, 'FAIL_CLOSED'::character varying])::text[])))
);


--
-- Name: TABLE ecos_ai_guardrail_decision_ledger; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_ai_guardrail_decision_ledger IS 'Guardrail 裁决台账（F06-03 对账可查询面；policy/obligations 仅为 security-engine 裁决响应缓存，权威在 security-engine）';


--
-- Name: ecos_ai_tool_permission_binding; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.ecos_ai_tool_permission_binding (
    id character varying(36) NOT NULL,
    tool_key character varying(120) NOT NULL,
    role_key character varying(64) NOT NULL,
    required_attributes text,
    obligation_expectation text,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);


--
-- Name: TABLE ecos_ai_tool_permission_binding; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_ai_tool_permission_binding IS '工具-角色权限绑定声明表（F06-02；禁把 LLM 任意入参塞进裁决载荷；策略权威在 security-engine，本表仅映射声明）';


--
-- Name: ecos_ai_tool_registry; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.ecos_ai_tool_registry (
    id character varying(36) NOT NULL,
    tool_key character varying(120) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    operation character varying(10) DEFAULT 'read'::character varying NOT NULL,
    args_schema_json text NOT NULL,
    backend_kind character varying(20) NOT NULL,
    backend_endpoint text,
    permission_hint text,
    opa_action_type character varying(40) DEFAULT 'TOOL_CALL'::character varying NOT NULL,
    resource_scope_projection text,
    timeout_ms integer DEFAULT 2000 NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone,
    CONSTRAINT ck_atr_operation CHECK (((operation)::text = ANY ((ARRAY['read'::character varying, 'write'::character varying])::text[]))),
    CONSTRAINT ck_atr_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DISABLED'::character varying, 'DRAFT'::character varying])::text[])))
);


--
-- Name: TABLE ecos_ai_tool_registry; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_ai_tool_registry IS 'AI 工具声明式注册表（F06-11；R-19 并建合规新表；旧 ecos_tool_definition 双镜像停写不删）';


--
-- Name: ecos_aip_pipeline; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.ecos_aip_pipeline (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(500),
    definition_json text DEFAULT '{}'::text NOT NULL,
    status character varying(12) DEFAULT 'draft'::character varying NOT NULL,
    template_ref character varying(120),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    last_executed_at timestamp without time zone,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_aip_pipeline; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_aip_pipeline IS 'AIP 逻辑编排定义（AI 工作台 LogicView 画布持久化；V329 批 1：内存 store 迁 PG；template_ref 预留批 2 Wagent Playbook 模板来源追溯）';


--
-- Name: COLUMN ecos_aip_pipeline.last_executed_at; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.ecos_aip_pipeline.last_executed_at IS '最近执行时刻（execute 端点回写；响应 lastExecutedAt 尾键来源，对齐 DI-2 #147EC 键序）';


--
-- Name: ecos_business_scenario; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.ecos_business_scenario (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    business_goal text DEFAULT ''::text,
    department character varying(128),
    priority character varying(16) DEFAULT 'MEDIUM'::character varying,
    status character varying(32) DEFAULT 'DRAFT'::character varying,
    budget character varying(64),
    safety_index_target numeric(8,4),
    actual_safety_index numeric(8,4),
    metrics_json text,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);


--
-- Name: ecos_decision_case; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.ecos_decision_case (
    id bigint NOT NULL,
    title character varying(256) NOT NULL,
    scenario text,
    tags text[],
    decision jsonb DEFAULT '{}'::jsonb,
    result jsonb DEFAULT '{}'::jsonb,
    feedback character varying(32) DEFAULT 'pending'::character varying,
    source character varying(64),
    created_by character varying(128),
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_decision_case; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_decision_case IS '决策案例表';


--
-- Name: ecos_decision_case_id_seq; Type: SEQUENCE; Schema: ecos_ai; Owner: -
--

CREATE SEQUENCE ecos_ai.ecos_decision_case_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_decision_case_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_ai; Owner: -
--

ALTER SEQUENCE ecos_ai.ecos_decision_case_id_seq OWNED BY ecos_ai.ecos_decision_case.id;


--
-- Name: ecos_evolution_log; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.ecos_evolution_log (
    id character varying(64) NOT NULL,
    mission_id character varying(64),
    stage character varying(32) NOT NULL,
    agent_id character varying(64),
    input_context jsonb,
    output_result jsonb,
    status character varying(32) DEFAULT 'STARTED'::character varying,
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: TABLE ecos_evolution_log; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_evolution_log IS '自治进化日志表';


--
-- Name: COLUMN ecos_evolution_log.stage; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.ecos_evolution_log.stage IS '阶段: DIAGNOSIS/ONTOLOGY_EVOLVE/KNOWLEDGE_REBUILD/SECURITY_HEAL/DEPLOYMENT';


--
-- Name: COLUMN ecos_evolution_log.status; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.ecos_evolution_log.status IS '状态: STARTED/COMPLETED/FAILED';


--
-- Name: ecos_guardrail_policy; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
-- Name: ecos_mission; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.ecos_mission (
    id character varying(64) NOT NULL,
    title character varying(256) NOT NULL,
    goal text,
    mode character varying(16) DEFAULT 'SUPERVISOR'::character varying,
    status character varying(32) DEFAULT 'PENDING'::character varying,
    plan jsonb,
    result jsonb,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_mission; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_mission IS 'Mission表';


--
-- Name: ecos_mission_task; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.ecos_mission_task (
    id character varying(64) NOT NULL,
    mission_id character varying(64) NOT NULL,
    agent_id character varying(64),
    instruction text,
    status character varying(32) DEFAULT 'PENDING'::character varying,
    result jsonb,
    depends_on character varying(256),
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_mission_task; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_mission_task IS 'Mission任务表';


--
-- Name: ecos_tool_definition; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.ecos_tool_definition (
    id character varying(64) NOT NULL,
    code character varying(128) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    tool_type character varying(32),
    endpoint_url character varying(512),
    http_method character varying(16),
    schema_json jsonb,
    status character varying(32) DEFAULT 'ACTIVE'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_tool_definition; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_tool_definition IS '工具定义表';


--
-- Name: ecos_wagent_action; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    approval_token character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);


--
-- Name: TABLE ecos_wagent_action; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_action IS 'W 域行动提交与发布（Action：commitAction 经 CommitService 五步，publish_target 回执，详设 §10.3/§10.4.2）';


--
-- Name: ecos_wagent_action_effect; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);


--
-- Name: TABLE ecos_wagent_action_effect; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_action_effect IS 'W 域行动效果（Effect：planned/actual/deviation_reason + data 层回灌事实对照，详设 §10.4.2/§12.4 幕 6~7）';


--
-- Name: ecos_wagent_candidate; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_wagent_candidate_status CHECK (((status IS NULL) OR ((status)::text = ANY ((ARRAY['generated'::character varying, 'validating'::character varying, 'validated'::character varying, 'review_pending'::character varying, 'approved'::character varying, 'committing'::character varying, 'published'::character varying, 'rejected'::character varying, 'expired'::character varying, 'rolled_back'::character varying])::text[])))),
    CONSTRAINT ck_wagent_candidate_type CHECK (((candidate_type IS NULL) OR ((candidate_type)::text = ANY ((ARRAY['DATA_MAPPING'::character varying, 'DQ_RULE'::character varying, 'ENTITY'::character varying, 'RELATIONSHIP'::character varying, 'METRIC'::character varying, 'KNOWLEDGE_ITEM'::character varying, 'ASSUMPTION'::character varying, 'SCENARIO'::character varying, 'DECISION_OPTION'::character varying, 'ACTION_DRAFT'::character varying, 'COGNITIVE_MODEL_DEF'::character varying, 'MODEL_ROUTE'::character varying])::text[]))))
);


--
-- Name: TABLE ecos_wagent_candidate; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_candidate IS 'W Agent 知识/资产候选（附件 agt_candidate 重写；R-40① 统一治理，kb_extract_candidate/agent_approval 收编口径见卷 10 §6.2）';


--
-- Name: ecos_wagent_candidate_review; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_wagent_candidate_review; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_candidate_review IS 'W Agent 候选评审记录（评审中心消费，卷 08；凭证链关联存量审批表，不建 FK）';


--
-- Name: ecos_wagent_claim; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_wagent_claim; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_claim IS 'W Agent 断言（数字一致性校验承载，§7.1）';


--
-- Name: ecos_wagent_claim_evidence; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.ecos_wagent_claim_evidence (
    claim_id character varying(36) NOT NULL,
    evidence_id character varying(36) NOT NULL,
    relation character varying(8),
    cover_ratio numeric(5,4),
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_wagent_claim_evidence; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_claim_evidence IS '断言×证据关联（PK(claim_id, evidence_id) 复合主键；证据主权在 V240 evidence 表）';


--
-- Name: ecos_wagent_decision; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.ecos_wagent_decision (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    question_id character varying(36),
    goal_id character varying(36),
    readiness_id character varying(36),
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    decision_maker_id character varying(100),
    rationale_text text,
    decision_time timestamp without time zone,
    effective_time timestamp without time zone,
    review_plan_time timestamp without time zone,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);


--
-- Name: TABLE ecos_wagent_decision; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_decision IS 'W 域决策对象（行动五阶段：决策）；详设 §10.4.2 待定段补建，列清单单源 = 设计文（无存量代码冻结列名）';


--
-- Name: ecos_wagent_decision_option; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);


--
-- Name: TABLE ecos_wagent_decision_option; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_decision_option IS 'W 域备选方案对比（Option：feasibility/risk_delta/confidence/headcount_impact/baseline_slot，详设 §10.4.2/§10.3 七问「备选方案与比较」）；四枚金额面经 amount_ref 弱关联至 ecos_dw.ecos_application_wagent_option_amount（V286），控制域不落金额实列';


--
-- Name: COLUMN ecos_wagent_decision_option.amount_ref; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.ecos_wagent_decision_option.amount_ref IS '金额面引用位（应用侧生成 VARCHAR(36)，不建 FK — ST09）：指向 ecos_dw.ecos_application_wagent_option_amount.option_id（V286）。四枚金额（profit/revenue/cost/cash）该表 1:1 承载；NULL=金额面尚未核算，禁在本控制域表以任何数值形态落地';


--
-- Name: ecos_wagent_evidence; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_wagent_evidence; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_evidence IS 'W Agent 运行证据（引用型，无内容字段，§7.1/§6.3）';


--
-- Name: ecos_wagent_goal; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_wagent_goal_type CHECK (((goal_type IS NULL) OR ((goal_type)::text = ANY ((ARRAY['FORECAST'::character varying, 'DIAGNOSE'::character varying, 'SIMULATE'::character varying, 'RANK'::character varying, 'MONITOR'::character varying, 'BUILD'::character varying, 'DECIDE'::character varying, 'REVIEW'::character varying])::text[]))))
);


--
-- Name: TABLE ecos_wagent_goal; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_goal IS 'W Agent 业务目标（附件 agt_business_goal 按 ECOS 模板重写；R-33① 落 ecos_ai，不建 ecos_agent schema / agt_ 前缀）';


--
-- Name: ecos_wagent_mission; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_wagent_mission; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_mission IS 'W Agent 任务书（附件 agt_mission 重写；R-33① ecos_ai 五枚举内）';


--
-- Name: ecos_wagent_question; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_wagent_question_confidence CHECK (((intent_confidence IS NULL) OR ((intent_confidence >= (0)::numeric) AND (intent_confidence <= (1)::numeric)))),
    CONSTRAINT ck_wagent_question_intent CHECK (((intent IS NULL) OR ((intent)::text = ANY ((ARRAY['FORECAST'::character varying, 'DIAGNOSE'::character varying, 'SIMULATE'::character varying, 'RANK'::character varying, 'MONITOR'::character varying, 'BUILD'::character varying, 'DECIDE'::character varying, 'REVIEW'::character varying])::text[]))))
);


--
-- Name: TABLE ecos_wagent_question; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_question IS 'W Agent 业务问题（附件 agt_business_question 重写）';


--
-- Name: ecos_wagent_readiness; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_wagent_readiness; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_readiness IS 'DIKC 就绪评估主表（附件 §3.1 三表合并；item/gap 子表见 V231/V232）';


--
-- Name: ecos_wagent_readiness_gap; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_wagent_readiness_gap; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_readiness_gap IS '就绪评估缺口明细（附件 §3.1 gap 分表；补齐循环走任务中心 runtime-task 单一来源）';


--
-- Name: ecos_wagent_readiness_item; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_wagent_readiness_item; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_readiness_item IS '就绪评估检查项明细（附件 §3.1 item 分表）';


--
-- Name: ecos_wagent_run; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_wagent_run_plan_source CHECK (((plan_source IS NULL) OR ((plan_source)::text = ANY ((ARRAY['PLAYBOOK'::character varying, 'EXPLORED'::character varying])::text[])))),
    CONSTRAINT ck_wagent_run_status CHECK (((status IS NULL) OR ((status)::text = ANY ((ARRAY['created'::character varying, 'planning'::character varying, 'plan_validated'::character varying, 'executing'::character varying, 'awaiting_input'::character varying, 'awaiting_approval'::character varying, 'waiting_task'::character varying, 'paused'::character varying, 'completed'::character varying, 'completed_degraded'::character varying, 'failed'::character varying, 'cancelled'::character varying, 'timed_out'::character varying])::text[]))))
);


--
-- Name: TABLE ecos_wagent_run; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_run IS 'W Agent 运行实例（附件 agt_run 重写；agent_execution 语义上位；预算/幂等承载 REQ-WAG-11）';


--
-- Name: ecos_wagent_run_event; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.ecos_wagent_run_event (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    run_id character varying(36) NOT NULL,
    step_id character varying(36),
    event_type character varying(32),
    payload_ref_text text,
    occur_time timestamp without time zone,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_wagent_run_event; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_run_event IS 'W Agent 引用型运行事件（append-only，无分区 X-6；审计走 Kafka ecos.audit，ST06 不建 wagent_audit_event）';


--
-- Name: ecos_wagent_run_step; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_wagent_step_policy_effect CHECK (((policy_effect IS NULL) OR ((policy_effect)::text = ANY ((ARRAY['allow'::character varying, 'allow_with_mask'::character varying, 'require_approval'::character varying, 'deny'::character varying])::text[])))),
    CONSTRAINT ck_wagent_step_type CHECK (((step_type IS NULL) OR ((step_type)::text = ANY ((ARRAY['tool'::character varying, 'llm'::character varying, 'human_gate'::character varying, 'wait_task'::character varying, 'sub_run'::character varying, 'aggregate'::character varying])::text[]))))
);


--
-- Name: TABLE ecos_wagent_run_step; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_run_step IS 'W Agent 运行步骤（附件 agt_step 重写；agent_execution_step 语义上位，停写口径见卷 10 §6.2 收编段）';


--
-- Name: ecos_wagent_skill; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_wagent_skill; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_skill IS 'W Agent 技能目录（附件 agt_skill 重写；DB 只存在用版本、历史走 Git；public.ecos_skill 收编口径见卷 10 §6.2，存量动作待 R-41）';


--
-- Name: ecos_wagent_tool_contract; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
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
    CONSTRAINT ck_wagent_tool_category CHECK (((category IS NULL) OR ((category)::text = ANY ((ARRAY['read'::character varying, 'compute'::character varying, 'candidate'::character varying, 'commit'::character varying, 'notify'::character varying])::text[])))),
    CONSTRAINT ck_wagent_tool_disclosure CHECK (((disclosure IS NULL) OR ((disclosure)::text = ANY ((ARRAY['always_loaded'::character varying, 'searchable'::character varying])::text[])))),
    CONSTRAINT ck_wagent_tool_rollback CHECK ((((category)::text <> 'commit'::text) OR (rollback_plan_text IS NOT NULL))),
    CONSTRAINT ck_wagent_tool_side_effect CHECK (((side_effect IS NULL) OR ((side_effect)::text = ANY ((ARRAY['none'::character varying, 'draft'::character varying, 'production'::character varying])::text[])))),
    CONSTRAINT ck_wagent_tool_status CHECK (((status IS NULL) OR ((status)::text = ANY ((ARRAY['draft'::character varying, 'registered'::character varying, 'validated'::character varying, 'active'::character varying, 'deprecated'::character varying, 'retired'::character varying])::text[]))))
);


--
-- Name: TABLE ecos_wagent_tool_contract; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.ecos_wagent_tool_contract IS 'W Agent 工具契约（附件 §5.1/§5.4 重写；UNIQUE(name,version)；commit 类必带回滚预案）；首批 12 业务语义工具 seed 见 V266（status=registered，toolset core/analytics/action 与 V265 组 4 逐字一致；run_cognitive_model/create_decision_option/create_action_draft 三行 endpoint 为设计路径待复核）';


--
-- Name: COLUMN ecos_wagent_tool_contract.operation_class; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.operation_class IS '<补充设计 v1.0 §11>操作类别 READ/SUGGEST/WRITE（词表单源 WAgentEnums）；SUGGEST 生成候选不改权威资产；WRITE 必走 ToolExecutor 咽喉+security 裁决+审批/幂等/审计/回滚（§11.2）';


--
-- Name: COLUMN ecos_wagent_tool_contract.evidence_required; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.evidence_required IS '<补充设计 v1.0 §11/§7.5>是否要求来源引用（与 V236 is_evidence_output 并存；新契约读以本列为准）';


--
-- Name: COLUMN ecos_wagent_tool_contract.automation_level; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.automation_level IS '<补充设计 v1.0 §11>自动化等级档位（显式契约位；NULL 回落 V236 min_level 语义，读侧单源在 Java 契约层合并）';


--
-- Name: COLUMN ecos_wagent_tool_contract.data_classification_max; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.data_classification_max IS '<补充设计 v1.0 §11>允许处理的最高密级档位（security-engine 权威裁决，本列只声明上限，禁 Router/引擎内复制策略）';


--
-- Name: COLUMN ecos_wagent_tool_contract.requires_approval; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.requires_approval IS '<补充设计 v1.0 §11>审批要求档位（NONE/HUMAN/TWO_EYE 语义；高风险 WRITE 默认人工审批，§7.2 DATA_CHANGE_APPLY 同口径）';


--
-- Name: COLUMN ecos_wagent_tool_contract.idempotency_key_required; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.idempotency_key_required IS '<补充设计 v1.0 §7.5>幂等键强制位（同一 idempotencyKey 重试不得重复创建/重复应用）';


--
-- Name: COLUMN ecos_wagent_tool_contract.rollback_capability; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.rollback_capability IS '<补充设计 v1.0 §11>回滚能力档位（NONE/NATURAL/MANUAL 语义；WRITE 高影响面禁止 NONE）';


--
-- Name: COLUMN ecos_wagent_tool_contract.audit_event_type; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.audit_event_type IS '<补充设计 v1.0 §11>统一审计事件类型（值必须命中 EventTypes 单源清单，禁自造字符串，SupplEventCatalogTest 守）';


--
-- Name: COLUMN ecos_wagent_tool_contract.owner_engine; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.owner_engine IS '<补充设计 v1.0 §11>正式语义与写入权归属引擎（值 = WAgentEngineCatalog.contractEngineCode 六名：data/ontology/kb/cognitive/ai/security；Agent 不写对方表，候选写回经属主 Engine REST，F10-23 同通道）';


--
-- Name: forecast; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
-- Name: TABLE forecast; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.forecast IS '预测表';


--
-- Name: optimization_job; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.optimization_job (
    id character varying(64) NOT NULL,
    objective text,
    constraints jsonb DEFAULT '[]'::jsonb,
    status character varying(32) DEFAULT 'CREATED'::character varying,
    result jsonb DEFAULT '{}'::jsonb,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE optimization_job; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.optimization_job IS '优化任务表';


--
-- Name: scenario; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.scenario (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(32) DEFAULT 'CUSTOM'::character varying,
    assumptions jsonb DEFAULT '{}'::jsonb,
    description text,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE scenario; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.scenario IS '场景表';


--
-- Name: simulation; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.simulation (
    id character varying(64) NOT NULL,
    scenario_id character varying(64),
    status character varying(32) DEFAULT 'CREATED'::character varying,
    config jsonb DEFAULT '{}'::jsonb,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE simulation; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.simulation IS '仿真表';


--
-- Name: simulation_result; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
-- Name: TABLE simulation_result; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.simulation_result IS '仿真结果表';


--
-- Name: strategy_recommendation; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
-- Name: TABLE strategy_recommendation; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.strategy_recommendation IS '策略推荐表';


--
-- Name: sys_agent_call_log; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.sys_agent_call_log (
    id character varying(64) NOT NULL,
    subsystem character varying(64),
    profile_name character varying(128),
    session_id character varying(64),
    user_message text,
    tokens_input integer DEFAULT 0,
    tokens_output integer DEFAULT 0,
    duration_ms integer DEFAULT 0,
    status character varying(16) DEFAULT 'success'::character varying,
    error_msg text,
    tenant_id character varying(64),
    created_time timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE sys_agent_call_log; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.sys_agent_call_log IS 'Agent调用日志表(Hermes)';


--
-- Name: sys_agent_profile; Type: TABLE; Schema: ecos_ai; Owner: -
--

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
-- Name: TABLE sys_agent_profile; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.sys_agent_profile IS 'Agent配置档案表(Hermes)';


--
-- Name: world_snapshot; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.world_snapshot (
    id character varying(64) NOT NULL,
    state_id character varying(64),
    snapshot_type character varying(32),
    data jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE world_snapshot; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.world_snapshot IS '世界快照表';


--
-- Name: world_state; Type: TABLE; Schema: ecos_ai; Owner: -
--

CREATE TABLE ecos_ai.world_state (
    id character varying(64) NOT NULL,
    "timestamp" timestamp without time zone,
    state_data jsonb DEFAULT '{}'::jsonb
);


--
-- Name: TABLE world_state; Type: COMMENT; Schema: ecos_ai; Owner: -
--

COMMENT ON TABLE ecos_ai.world_state IS '世界状态表';


--
-- Name: ecos_biz_contract; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

CREATE TABLE ecos_cognitive.ecos_biz_contract (
    id character varying(64) NOT NULL,
    contract_no character varying(128),
    contract_type character varying(32),
    project_id character varying(64),
    party_name character varying(256),
    amount numeric(18,2),
    signed_date date,
    status character varying(32) DEFAULT 'ACTIVE'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_biz_contract; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_biz_contract IS '业务合同表';


--
-- Name: ecos_biz_department; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

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
-- Name: TABLE ecos_biz_department; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_biz_department IS '业务部门表';


--
-- Name: ecos_biz_metric; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

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
-- Name: TABLE ecos_biz_metric; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_biz_metric IS '业务指标表';


--
-- Name: ecos_biz_metric_id_seq; Type: SEQUENCE; Schema: ecos_cognitive; Owner: -
--

CREATE SEQUENCE ecos_cognitive.ecos_biz_metric_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_biz_metric_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_cognitive; Owner: -
--

ALTER SEQUENCE ecos_cognitive.ecos_biz_metric_id_seq OWNED BY ecos_cognitive.ecos_biz_metric.id;


--
-- Name: ecos_biz_project; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

CREATE TABLE ecos_cognitive.ecos_biz_project (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    project_type character varying(32),
    dept_id character varying(64),
    customer_name character varying(256),
    contract_amount numeric(18,2),
    status character varying(32) DEFAULT 'ACTIVE'::character varying,
    start_date date,
    end_date date,
    manager character varying(64),
    goal_id bigint,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_biz_project; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_biz_project IS '业务项目表';


--
-- Name: ecos_biz_target; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

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
-- Name: TABLE ecos_biz_target; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_biz_target IS '业务目标表';


--
-- Name: ecos_biz_target_id_seq; Type: SEQUENCE; Schema: ecos_cognitive; Owner: -
--

CREATE SEQUENCE ecos_cognitive.ecos_biz_target_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_biz_target_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_cognitive; Owner: -
--

ALTER SEQUENCE ecos_cognitive.ecos_biz_target_id_seq OWNED BY ecos_cognitive.ecos_biz_target.id;


--
-- Name: ecos_business_scenario; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

CREATE TABLE ecos_cognitive.ecos_business_scenario (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    business_goal text DEFAULT ''::text,
    department character varying(128),
    priority character varying(16) DEFAULT 'MEDIUM'::character varying,
    status character varying(32) DEFAULT 'DRAFT'::character varying,
    budget character varying(64),
    safety_index_target numeric(8,4),
    actual_safety_index numeric(8,4),
    metrics_json text,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);


--
-- Name: TABLE ecos_business_scenario; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_business_scenario IS '业务场景主表（认知域真身 14 张之一，R-13 ① 归位 ecos_cognitive；旧 public.ecos_business_scenario 双写期保留；场景层 v2 权威面另见 V205/卷07）';


--
-- Name: ecos_cognitive_belief; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

CREATE TABLE ecos_cognitive.ecos_cognitive_belief (
    id character varying(36) NOT NULL,
    variable_name character varying(128) NOT NULL,
    tenant_scope character varying(64),
    domain character varying(64) DEFAULT 'default'::character varying NOT NULL,
    distribution_json text DEFAULT '[]'::text NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    snapshot_version integer,
    is_manual_override smallint DEFAULT 0 NOT NULL,
    override_reason text,
    last_evidence_id character varying(64),
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    mind_id character varying(36),
    scenario_id character varying(36),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
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
-- Name: TABLE ecos_cognitive_belief; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_cognitive_belief IS '不确定性判断表（ADR-9 心智层 P 库，R-13 ① 归位 ecos_cognitive；旧 public.ecos_cognitive_belief 双写期保留，读切换在 Mapper 层）';


--
-- Name: COLUMN ecos_cognitive_belief.distribution_json; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.distribution_json IS '有限离散概率分布序列化 TEXT [{"outcome","prob"}]（MC02 合规形态，源列 V127 系二进制 JSON）';


--
-- Name: COLUMN ecos_cognitive_belief.mind_id; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.mind_id IS 'R4 归属维度：所属 Mind 聚合根 id（详设 §8.4.2；可空，存量 NULL=未挂 Mind；读侧带 mind 参时 AND mind_id=? 严格过滤）';


--
-- Name: COLUMN ecos_cognitive_belief.probability; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.probability IS 'E2 算式唯一数值来源：belief 点概率（0..1，NUMERIC(6,5)，MC02 带精度）；NULL=未标定 ⇒ 应用侧从 Top-N 剔除并计入 missingProbability，禁 DEFAULT 0、禁由 distribution_json 反算冒充标定值（REQ-COG-09 规格 4 / X-44）';


--
-- Name: COLUMN ecos_cognitive_belief.lifecycle_state; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.lifecycle_state IS '<补充设计 v1.0 §8.4>Belief 治理生命周期（同 hypothesis 词表）；level-evidence 型生命周期 EVIDENCE_CHECKED=last_evidence_id 链完整；CONTRADICTED≠删除，保留反证链（§8.5）';


--
-- Name: COLUMN ecos_cognitive_belief.prob_kind; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.prob_kind IS '<补充设计 v1.0 §8.3/§16.5 红线>point probability 语义三分：STATISTICAL_PROBABILITY / CALIBRATED_MODEL_PROBABILITY / UNCALIBRATED_CONFIDENCE / UNCALIBRATED_MODEL_ESTIMATE；NULL+probability 非 NULL=UNKNOWN_LEGACY（应用侧回落，禁伪造补齐）；UNCALIBRATED 两类禁入金额计算/自动审批（SUP-COG-02/SUP-MODEL-03）';


--
-- Name: COLUMN ecos_cognitive_belief.calibration_ref; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.calibration_ref IS '<补充设计 v1.0 §8.3>校准方法/数据集/有效范围的引用声明（CALIBRATED_MODEL_PROBABILITY 必填语义，应用侧校验，DDL 不建 CHECK 约束）';


--
-- Name: COLUMN ecos_cognitive_belief.model_ref; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.model_ref IS '<补充设计 v1.0 §8.3>产生该判断的 CM 版本引用（如 cm-07:v3，CP 前缀式）';


--
-- Name: COLUMN ecos_cognitive_belief.scope_ref; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.scope_ref IS '<补充设计 v1.0 §8.3>适用范围引用（scenario/tenant-scope 键）';


--
-- Name: COLUMN ecos_cognitive_belief.as_of_time; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.as_of_time IS '<补充设计 v1.0 §5.3/§12.2>判断时点（T1S 轨；与 V261 model 的 as_of 同口径）';


--
-- Name: COLUMN ecos_cognitive_belief.subject_type; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.subject_type IS 'R4 归属维度：主体类型字面量（详设 §8.4.2 subject_type VARCHAR(20)；可空，存量 NULL 维持原行为）';


--
-- Name: COLUMN ecos_cognitive_belief.subject_id; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.subject_id IS 'R4 归属维度：主体 id（详设 §8.4.2 subject_id VARCHAR(36)；可空）';


--
-- Name: ecos_cognitive_evidence; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

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
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);


--
-- Name: TABLE ecos_cognitive_evidence; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_cognitive_evidence IS '认知证据表（ADR-9 感知/心智层；blob/refuting 原二进制 JSON 已合规化为 TEXT）';


--
-- Name: ecos_cognitive_framework; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

CREATE TABLE ecos_cognitive.ecos_cognitive_framework (
    id character varying(36) NOT NULL,
    framework_id character varying(64) NOT NULL,
    version character varying(32) NOT NULL,
    name character varying(128) NOT NULL,
    category character varying(64) NOT NULL,
    purpose text,
    status character varying(24) DEFAULT 'DRAFT'::character varying NOT NULL,
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
-- Name: TABLE ecos_cognitive_framework; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_cognitive_framework IS '认知框架注册表（详细设计-认知引擎 §5/§9.1）：版本化过程治理元数据；PUBLISHED 版本不可原地修改';


--
-- Name: COLUMN ecos_cognitive_framework.status; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_framework.status IS 'DRAFT / VALIDATING / PUBLISHED / DEPRECATED / DISABLED（单源 CognitiveFrameworkStatus）';


--
-- Name: COLUMN ecos_cognitive_framework.contract_resolvable; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_framework.contract_resolvable IS '输入/输出 Schema 与能力引用是否可解析（0/1；策略选择硬约束输入）';


--
-- Name: COLUMN ecos_cognitive_framework.content_hash; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_framework.content_hash IS '发布冻结的内容摘要（SHA-256），用于版本可追溯与篡改检测';


--
-- Name: ecos_cognitive_hypothesis; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

CREATE TABLE ecos_cognitive.ecos_cognitive_hypothesis (
    id character varying(36) NOT NULL,
    hypothesis_code character varying(128) NOT NULL,
    tenant_scope character varying(64),
    statement text NOT NULL,
    domain character varying(64) DEFAULT 'default'::character varying NOT NULL,
    metric_ref character varying(128),
    evidence_ids_json text DEFAULT '[]'::text NOT NULL,
    is_valid smallint DEFAULT 1 NOT NULL,
    invalid_at timestamp without time zone,
    invalid_reason text,
    status character varying(16) DEFAULT 'PROPOSED'::character varying NOT NULL,
    old_status character varying(20),
    mind_id character varying(36),
    scenario_id character varying(36),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
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
-- Name: TABLE ecos_cognitive_hypothesis; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_cognitive_hypothesis IS '认知假设表（ADR-9 心智层 H 库；status 终态 4 值枚举 R-15 ①，old_status 存映射前留痕）';


--
-- Name: COLUMN ecos_cognitive_hypothesis.evidence_ids_json; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.evidence_ids_json IS 'R4 证据引用数组 TEXT JSON；V128 旧 evidence_ids JSONB 列停跑保留（knownLegacy），本列为新权威读写面';


--
-- Name: COLUMN ecos_cognitive_hypothesis.status; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.status IS '四终态/proposed/evidenced/believed/refuted（R-15 ①；V191 建表时默认 VALID 系双轨期历史签发，
     V299 归一 PROPOSED=应用语义起点；存量 4 行全在四终态无 VALID 行，DEFAULT 改写零影响存量）';


--
-- Name: COLUMN ecos_cognitive_hypothesis.mind_id; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.mind_id IS 'R4 承参过滤列（详设 §8.4.2：E3 带 mind 参时严格等值过滤，可空）；V297 补建，存量 NULL=未挂 Mind';


--
-- Name: COLUMN ecos_cognitive_hypothesis.scenario_id; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.scenario_id IS 'R4 承参过滤列（E3 场景维度 WHERE scenario_id=?）；V297 补建，存量 NULL=未挂场景';


--
-- Name: COLUMN ecos_cognitive_hypothesis.lifecycle_state; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.lifecycle_state IS '<补充设计 v1.0 §8.4>统一认知资产生命周期唯一权威列（词表单源 CognitiveAssetStatus；DRAFT/CANDIDATE/EVIDENCE_CHECKED/VALIDATED/APPROVED/ACTIVE/SUPERSEDED/CONTRADICTED/EXPIRED/REJECTED/DEPRECATED/SUSPENDED）；存量 status 列停写不改语义；NULL=未接管（回落 legacy 展示，禁伪造）';


--
-- Name: COLUMN ecos_cognitive_hypothesis.subject_type; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.subject_type IS 'R4 承参维度主体类型（禁场景专有列；详设 §8.4.2：E3 按 subject 承参）';


--
-- Name: COLUMN ecos_cognitive_hypothesis.subject_id; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.subject_id IS 'R4 承参维度主体标识（与 subject_type 成对）';


--
-- Name: COLUMN ecos_cognitive_hypothesis.reason; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.reason IS 'R4 立据/理由（详设 §8.4.2 与 statement 并列必需；存量 DEFAULT 空串不伪造历史，应用层把关真必填）';


--
-- Name: COLUMN ecos_cognitive_hypothesis.confidence; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.confidence IS 'R4 信心度 NUMERIC(9,6) ∈[0,1]（详设精度；越界即数据缺陷，应用层守护；列 NULL=存量未评信心）';


--
-- Name: ecos_cognitive_mind; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

CREATE TABLE ecos_cognitive.ecos_cognitive_mind (
    id character varying(36) NOT NULL,
    mind_key character varying(120) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    capability_mask_json text DEFAULT '[]'::text NOT NULL,
    closed_loop_bounds_json text DEFAULT '[]'::text NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    mind_version character varying(20) NOT NULL,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
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
    validity_status character varying(20) DEFAULT 'PENDING_REVIEW'::character varying NOT NULL,
    review_status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    adoption_status character varying(20) DEFAULT 'NOT_ADOPTED'::character varying NOT NULL,
    change_reason character varying(500),
    superseded_by character varying(36)
);


--
-- Name: TABLE ecos_cognitive_mind; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_cognitive_mind IS '认知模型资产注册表（ADR-8 正名 cognitive_*，禁 kb_* 误名；R-13 ① 归位 ecos_cognitive；旧 E1 数据源 kb_mind_registry 仅存于文档，无库内对象）';


--
-- Name: COLUMN ecos_cognitive_mind.capability_mask_json; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.capability_mask_json IS '能力掩码 TEXT JSON 数组；序列化只经 MindCapabilityMaskCodec（REQ-COG-04，枚举 DETECT/FORECAST/SIMULATE/PLAN/REVIEW）';


--
-- Name: COLUMN ecos_cognitive_mind.closed_loop_bounds_json; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.closed_loop_bounds_json IS '闭环边界 TEXT JSON 数组，枚举 PRE/POST；非法值读侧容错为 [] + issue 记录（F05-03）';


--
-- Name: COLUMN ecos_cognitive_mind.status; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.status IS 'V187 单状态列【停写】；R4 起治理真值恒走三轴（validity_status/review_status/adoption_status），本列只读存量';


--
-- Name: COLUMN ecos_cognitive_mind.subject_ref; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.subject_ref IS 'R4 认知主体引用（六段·认知上下文）；V187 前为 NULL=未挂认知上下文';


--
-- Name: COLUMN ecos_cognitive_mind.scope; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.scope IS 'R4 认知范围（六段·认知上下文）；无明确认知范围的 Mind 禁注册（应用层把关）';


--
-- Name: COLUMN ecos_cognitive_mind.context_ref; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.context_ref IS 'R4 关联业务情境/Scenario 认知上下文引用';


--
-- Name: COLUMN ecos_cognitive_mind.goals_json; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.goals_json IS 'R4 认知目标数组 TEXT JSON（DR04 _json 后缀）';


--
-- Name: COLUMN ecos_cognitive_mind.key_questions_json; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.key_questions_json IS 'R4 关键问题数组 TEXT JSON';


--
-- Name: COLUMN ecos_cognitive_mind.explanation; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.explanation IS 'R4 解释结构（概念关系/因果/机制与推理依据）';


--
-- Name: COLUMN ecos_cognitive_mind.validity_status; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.validity_status IS 'R4 三维状态·认知有效性 VALID|PENDING_REVIEW|INVALIDATED；三轴独立禁合并，CHECK 延后（头注纪律）';


--
-- Name: COLUMN ecos_cognitive_mind.review_status; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.review_status IS 'R4 三维状态·审查治理 DRAFT|PENDING|APPROVED';


--
-- Name: COLUMN ecos_cognitive_mind.adoption_status; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.adoption_status IS 'R4 三维状态·业务采纳 NOT_ADOPTED|ADOPTED|WITHDRAWN';


--
-- Name: COLUMN ecos_cognitive_mind.change_reason; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.change_reason IS 'R4 本版本变化原因（版本历史段）';


--
-- Name: COLUMN ecos_cognitive_mind.superseded_by; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.superseded_by IS 'R4 替代版本行 id（新版本行落下时由受控端点回填本列，形成链）';


--
-- Name: ecos_cognitive_mind_update; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

CREATE TABLE ecos_cognitive.ecos_cognitive_mind_update (
    id character varying(36) NOT NULL,
    mind_id character varying(36) NOT NULL,
    agent_run_id character varying(36) NOT NULL,
    target_type character varying(20) NOT NULL,
    target_id character varying(36),
    change_json text NOT NULL,
    evidence_refs_json text DEFAULT '[]'::text NOT NULL,
    status character varying(20) DEFAULT 'PROPOSED'::character varying NOT NULL,
    reviewed_by character varying(64),
    reviewed_at timestamp without time zone,
    review_reason character varying(500),
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);


--
-- Name: TABLE ecos_cognitive_mind_update; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_cognitive_mind_update IS 'R4 候选更新闸门：W Agent 提交候选只写 PROPOSED；受控端点受理(ACCEPTED)/驳回(REJECTED)留痕；Agent 运行禁直写 belief/hypothesis';


--
-- Name: ecos_cognitive_model; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

CREATE TABLE ecos_cognitive.ecos_cognitive_model (
    id character varying(36) NOT NULL,
    model_id character varying(64) NOT NULL,
    model_type character varying(32) NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    features_json text,
    backtest_score numeric(8,6),
    status character varying(16) DEFAULT 'active'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone,
    cognitive_model_type character varying(8),
    registry_state character varying(16) DEFAULT 'DRAFT'::character varying NOT NULL,
    content_hash character varying(64),
    supersedes_id character varying(36),
    gate_contract character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    gate_regression character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    gate_backtest character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    gate_calibration character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
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
-- Name: TABLE ecos_cognitive_model; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_cognitive_model IS '认知模型注册表（ADR-8 模型资产落盘，R-13 ① 归位 ecos_cognitive；旧 public.ecos_cognitive_model 双写期保留）';


--
-- Name: COLUMN ecos_cognitive_model.cognitive_model_type; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.cognitive_model_type IS 'CM-01~CM-07 认知模型类型（枚举单源在 cognitive-engine-api；NULL=未映射旧值，注册端点 fail-loud 拒绝）；旧 model_type 列只读保留（API 只增不改）';


--
-- Name: COLUMN ecos_cognitive_model.registry_state; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.registry_state IS 'DRAFT/PUBLISHED（四类发布门禁 contract·regression·backtest·calibration 全 PASS 才可升 PUBLISHED；任一缺项停留 DRAFT 且发布端点返 4xx，禁静默成功）；存量行一律 DRAFT';


--
-- Name: COLUMN ecos_cognitive_model.content_hash; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.content_hash IS '发布时冻结的规范化内容摘要（SHA-256 hex）；PUBLISHED 且非空 = 不可变态，UPDATE 必败（应用侧守卫）';


--
-- Name: COLUMN ecos_cognitive_model.supersedes_id; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.supersedes_id IS '被替代版本行 id（版本变更只准新建版本行并指旧版，禁原地 UPDATE 已发布行）';


--
-- Name: COLUMN ecos_cognitive_model.gate_contract; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.gate_contract IS '发布门禁-契约校验：PASS/FAIL/PENDING（三值字符串，禁布尔——需区分未跑与跑失败）';


--
-- Name: COLUMN ecos_cognitive_model.gate_regression; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.gate_regression IS '发布门禁-回归集：PASS/FAIL/PENDING';


--
-- Name: COLUMN ecos_cognitive_model.gate_backtest; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.gate_backtest IS '发布门禁-回测：PASS/FAIL/PENDING（与既有 backtest_score 列配套：分数供排序，门禁供裁决）';


--
-- Name: COLUMN ecos_cognitive_model.gate_calibration; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.gate_calibration IS '发布门禁-校准：PASS/FAIL/PENDING';


--
-- Name: COLUMN ecos_cognitive_model.published_at; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.published_at IS '升格 PUBLISHED 时点（四门禁全 PASS 时由发布端点写入）';


--
-- Name: COLUMN ecos_cognitive_model.published_by; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.published_by IS '升格操作人（审计留痕，非个人敏感数据；与 create_by/update_by 分列）';


--
-- Name: COLUMN ecos_cognitive_model.lifecycle_state; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.lifecycle_state IS '<补充设计 v1.0 §8.4>CM 治理生命周期；与 registry_state（门禁态 PUBLISHED/REJECTED）、memora_state（合约上下线态）正交三列并存，各管一段禁合并';


--
-- Name: COLUMN ecos_cognitive_model.model_key; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.model_key IS 'R4 稳定业务键（详设 §8.4.2）；V296 前该行 NULL=未挂键，应用侧注册端点补挂';


--
-- Name: COLUMN ecos_cognitive_model.description; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.description IS 'R4 模型资产说明';


--
-- Name: COLUMN ecos_cognitive_model.phase; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.phase IS 'R4 三期路线分期 1/2/3（首期因果/诊断/假设检验，二期预测/风险/仿真/关系，三期优化/行为/信念更新）；CHECK 延后，应用层守护（V292 同纪律）';


--
-- Name: COLUMN ecos_cognitive_model.compute_owner; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.compute_owner IS 'R4 计算执行归属（认知模型≠AI模型≠计算引擎）';


--
-- Name: COLUMN ecos_cognitive_model.io_contract_json; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.io_contract_json IS 'R4 输入→输出契约 TEXT JSON（DR04 _json 后缀）';


--
-- Name: COLUMN ecos_cognitive_model.limits_json; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.limits_json IS 'R4 适用限制 TEXT JSON';


--
-- Name: COLUMN ecos_cognitive_model.model_category; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.model_category IS 'R4 九类分类权威列 CAUSAL|DIAGNOSTIC|HYPOTHESIS_TESTING|PREDICTIVE|SIMULATION|RISK|RELATIONAL|OPTIMIZATION|BEHAVIORAL（详设 §8.4.2）；与旧 model_type 四值、cognitive_model_type CM-01~07 并存各守其域；CHECK 延后，应用层单源 CognitiveR4ModelCategory 守护';


--
-- Name: ecos_cognitive_outcome_evaluation; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

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
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    metric_results_text text,
    diagnoses_text text,
    update_candidates_text text,
    warnings_text text,
    calculation_refs_text text,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
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
-- Name: TABLE ecos_cognitive_outcome_evaluation; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_cognitive_outcome_evaluation IS 'W→D 结果评估（补充设计 v1.0 §9）：评估记录落库，数值只存计算引用可复算；update_candidates 只存候选引用，单次偏差禁自动改写生产资产（SUP-OUT-01/02）';


--
-- Name: COLUMN ecos_cognitive_outcome_evaluation.idempotency_key; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_outcome_evaluation.idempotency_key IS '幂等键（§7.5）：同键重试返回既有评估记录，禁重复计算/重复建记录';


--
-- Name: COLUMN ecos_cognitive_outcome_evaluation.blockers_text; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_outcome_evaluation.blockers_text IS 'JSON 数组：§9.4 命中的禁自更新情形枚举名（ACTUALS_NOT_CLOSED 等七项词表）；空数组=未命中，非空 ⇒ status 必为 NEEDS_RECONCILE';


--
-- Name: COLUMN ecos_cognitive_outcome_evaluation.caliber_alignment; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_outcome_evaluation.caliber_alignment IS '§9.2 步骤 2 口径对齐结论：ALIGNED / MISMATCH:{维度} / UNCONFIRMED:{缺失侧}';


--
-- Name: ecos_cognitive_pipeline; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

CREATE TABLE ecos_cognitive.ecos_cognitive_pipeline (
    id character varying(36) NOT NULL,
    pipeline_id character varying(64) NOT NULL,
    name character varying(255),
    status character varying(32) DEFAULT 'DRAFT'::character varying NOT NULL,
    config_json text,
    result_json text,
    create_by character varying(100),
    update_by character varying(100),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


--
-- Name: TABLE ecos_cognitive_pipeline; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_cognitive_pipeline IS '认知管线定义表（cognitive-engine 自有，ST07 收编；旧 public.kb_cognitive_pipeline 停写保留）';


--
-- Name: ecos_cognitive_run_invalidation; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

CREATE TABLE ecos_cognitive.ecos_cognitive_run_invalidation (
    id character varying(36) NOT NULL,
    event_id character varying(64) NOT NULL,
    hypothesis_id character varying(64) NOT NULL,
    run_id character varying(64) NOT NULL,
    auto_detected smallint DEFAULT 1 NOT NULL,
    superseded_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    detail text,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);


--
-- Name: TABLE ecos_cognitive_run_invalidation; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_cognitive_run_invalidation IS '失效→作废推演结论关联留痕表（ADR-9 心智事件；run_id 指向 ecos_dw 产物面，随 R-17 ①）';


--
-- Name: ecos_cognitive_scenario_mind; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

CREATE TABLE ecos_cognitive.ecos_cognitive_scenario_mind (
    id character varying(36) NOT NULL,
    scenario_id character varying(36) NOT NULL,
    mind_id character varying(36) NOT NULL,
    binding_role character varying(20) DEFAULT 'PRIMARY'::character varying NOT NULL,
    priority integer DEFAULT 100 NOT NULL,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);


--
-- Name: TABLE ecos_cognitive_scenario_mind; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_cognitive_scenario_mind IS '场景↔Mind 绑定关系表（定义态资产，R-13 ① 落 ecos_cognitive；替代 legacy ecos_scenario_mind/V146，不重建 legacy 名；E1 detect = ecos_cognitive_mind × 本表 JOIN，仅取 is_active=1 且 is_deleted=0）';


--
-- Name: COLUMN ecos_cognitive_scenario_mind.mind_id; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_scenario_mind.mind_id IS '首个 mind_id 列（X-19：旧库全库 0 个）；指向 ecos_cognitive.ecos_cognitive_mind.id，FK 不建（跨表约束经应用层完整性校验）';


--
-- Name: COLUMN ecos_cognitive_scenario_mind.binding_role; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_scenario_mind.binding_role IS 'PRIMARY/SUPPORTING；CHECK 约束待应用全量切换后补建（本批不建，见头注）';


--
-- Name: ecos_goal_tracking; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

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
-- Name: TABLE ecos_goal_tracking; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_goal_tracking IS '目标追踪表';


--
-- Name: ecos_scenario_binding; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

CREATE TABLE ecos_cognitive.ecos_scenario_binding (
    id character varying(36) NOT NULL,
    scenario_id character varying(64) NOT NULL,
    binding_type character varying(32) NOT NULL,
    target_ref character varying(255) NOT NULL,
    target_id character varying(36),
    target_type character varying(20),
    remark text DEFAULT ''::text,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);


--
-- Name: TABLE ecos_scenario_binding; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_scenario_binding IS '场景六类绑定表（target_ref 旧别名只读兼容；target_id/target_type 真引用列，值域以 V199 契约为准）';


--
-- Name: ecos_scenario_binding_link; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

CREATE TABLE ecos_cognitive.ecos_scenario_binding_link (
    id character varying(36) NOT NULL,
    scenario_id character varying(64) NOT NULL,
    source_binding_id character varying(64) NOT NULL,
    target_binding_id character varying(64) NOT NULL,
    link_type character varying(16) NOT NULL,
    source_contract character varying(128),
    remark text DEFAULT ''::text,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone,
    CONSTRAINT ck_pg_mirror_blink_link_type CHECK (((link_type)::text = ANY ((ARRAY['MAPPING'::character varying, 'EXTRACTION'::character varying, 'COGNITION'::character varying, 'GOVERN'::character varying, 'EXPOSE'::character varying])::text[])))
);


--
-- Name: TABLE ecos_scenario_binding_link; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_scenario_binding_link IS '场景绑定关系边表（§0.6.2；link_type 值域与 V160 完全一致，非新枚举收敛）';


--
-- Name: ecos_wm_causal_link; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

CREATE TABLE ecos_cognitive.ecos_wm_causal_link (
    id bigint NOT NULL,
    source_goal_id bigint NOT NULL,
    target_goal_id bigint NOT NULL,
    relationship_type character varying(32) DEFAULT 'POSITIVE'::character varying,
    description text,
    time_lag_days integer DEFAULT 0,
    correlation_coefficient numeric(4,3) DEFAULT 0.0,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_wm_causal_link; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_wm_causal_link IS '目标因果链表';


--
-- Name: ecos_wm_causal_link_id_seq; Type: SEQUENCE; Schema: ecos_cognitive; Owner: -
--

CREATE SEQUENCE ecos_cognitive.ecos_wm_causal_link_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_wm_causal_link_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_cognitive; Owner: -
--

ALTER SEQUENCE ecos_cognitive.ecos_wm_causal_link_id_seq OWNED BY ecos_cognitive.ecos_wm_causal_link.id;


--
-- Name: ecos_wm_goal; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

CREATE TABLE ecos_cognitive.ecos_wm_goal (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    parent_id bigint,
    goal_type character varying(32) DEFAULT 'STRATEGIC'::character varying,
    weight integer DEFAULT 50,
    progress integer DEFAULT 0,
    status character varying(32) DEFAULT 'PLANNED'::character varying,
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
    measure_frequency character varying(16) DEFAULT 'MONTHLY'::character varying,
    alert_threshold_warn numeric(5,2) DEFAULT 80.0,
    alert_threshold_critical numeric(5,2) DEFAULT 50.0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT ecos_wm_goal_progress_check CHECK (((progress >= 0) AND (progress <= 100)))
);


--
-- Name: TABLE ecos_wm_goal; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_wm_goal IS '世界模型目标表';


--
-- Name: COLUMN ecos_wm_goal.goal_type; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_wm_goal.goal_type IS '目标类型: STRATEGIC/TACTICAL/OPERATIONAL';


--
-- Name: COLUMN ecos_wm_goal.kpi_formula; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_wm_goal.kpi_formula IS 'KPI公式';


--
-- Name: COLUMN ecos_wm_goal.measure_frequency; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON COLUMN ecos_cognitive.ecos_wm_goal.measure_frequency IS '度量频率: DAILY/WEEKLY/MONTHLY/QUARTERLY';


--
-- Name: ecos_wm_goal_id_seq; Type: SEQUENCE; Schema: ecos_cognitive; Owner: -
--

CREATE SEQUENCE ecos_cognitive.ecos_wm_goal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_wm_goal_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_cognitive; Owner: -
--

ALTER SEQUENCE ecos_cognitive.ecos_wm_goal_id_seq OWNED BY ecos_cognitive.ecos_wm_goal.id;


--
-- Name: ecos_wm_goal_log; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

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
-- Name: TABLE ecos_wm_goal_log; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_wm_goal_log IS '目标变更日志表';


--
-- Name: ecos_wm_goal_log_id_seq; Type: SEQUENCE; Schema: ecos_cognitive; Owner: -
--

CREATE SEQUENCE ecos_cognitive.ecos_wm_goal_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_wm_goal_log_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_cognitive; Owner: -
--

ALTER SEQUENCE ecos_cognitive.ecos_wm_goal_log_id_seq OWNED BY ecos_cognitive.ecos_wm_goal_log.id;


--
-- Name: ecos_wm_scenario; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

CREATE TABLE ecos_cognitive.ecos_wm_scenario (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    config_json text DEFAULT '{}'::text,
    status character varying(32) DEFAULT 'DRAFT'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_wm_scenario; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_wm_scenario IS '世界模型场景表';


--
-- Name: ecos_wm_scenario_id_seq; Type: SEQUENCE; Schema: ecos_cognitive; Owner: -
--

CREATE SEQUENCE ecos_cognitive.ecos_wm_scenario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_wm_scenario_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_cognitive; Owner: -
--

ALTER SEQUENCE ecos_cognitive.ecos_wm_scenario_id_seq OWNED BY ecos_cognitive.ecos_wm_scenario.id;


--
-- Name: ecos_world_scenarios; Type: TABLE; Schema: ecos_cognitive; Owner: -
--

CREATE TABLE ecos_cognitive.ecos_world_scenarios (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    goal_ids text DEFAULT '[]'::text,
    status character varying(32) DEFAULT 'draft'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_world_scenarios; Type: COMMENT; Schema: ecos_cognitive; Owner: -
--

COMMENT ON TABLE ecos_cognitive.ecos_world_scenarios IS '世界场景配置表';


--
-- Name: cache_invalidation_outbox; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.cache_invalidation_outbox (
    id character varying(36) NOT NULL,
    event_type character varying(64) NOT NULL,
    payload_json text NOT NULL,
    status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'sysman'::character varying NOT NULL,
    version_no character varying(20) DEFAULT 'v1'::character varying NOT NULL,
    published_at timestamp without time zone,
    last_error character varying(512),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_cache_invalidation_outbox_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PUBLISHED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: TABLE cache_invalidation_outbox; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.cache_invalidation_outbox IS '§2.9.8④ 缓存失效 Outbox：写路径事务内落行，后台中继投递 Redis 频道（至少一次、可重试）';


--
-- Name: COLUMN cache_invalidation_outbox.payload_json; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.cache_invalidation_outbox.payload_json IS '{env, domain, reason, objectType?, objectId?}；消费方按域/对象失效，禁携带业务内容';


--
-- Name: demo_customer; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.demo_customer (
    id character varying(64) NOT NULL,
    name character varying(255),
    industry character varying(128),
    region character varying(64),
    level character varying(16),
    credit_score integer,
    status character varying(32) DEFAULT 'active'::character varying,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: demo_invoice; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.demo_invoice (
    id character varying(64) NOT NULL,
    amount numeric(12,2),
    customer_id character varying(64),
    supplier_id character varying(64),
    status character varying(32) DEFAULT 'pending'::character varying,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: demo_supplier; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.demo_supplier (
    id character varying(64) NOT NULL,
    name character varying(255),
    industry character varying(128),
    region character varying(64),
    level character varying(16),
    supply_capacity character varying(128),
    status character varying(32) DEFAULT 'active'::character varying,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: dict_column; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: dict_table; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.dict_table (
    id character varying(36) NOT NULL,
    code character varying(200),
    name character varying(200) NOT NULL,
    name_zh character varying(200),
    schema_name character varying(200),
    description text,
    status character varying(32) DEFAULT 'DRAFT'::character varying,
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
-- Name: ecos_agent; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_agent (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    model_provider character varying(64) DEFAULT 'deepseek'::character varying,
    model_name character varying(128) DEFAULT 'deepseek-v4-flash'::character varying,
    system_prompt text DEFAULT ''::text,
    tools text DEFAULT '[]'::text,
    knowledge text DEFAULT '[]'::text,
    status character varying(32) DEFAULT 'draft'::character varying,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


--
-- Name: ecos_alert_history; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_alert_history (
    id bigint NOT NULL,
    rule_name character varying(256),
    level character varying(32),
    message text,
    status character varying(32) DEFAULT 'OPEN'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: ecos_alert_history_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.ecos_alert_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_alert_history_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.ecos_alert_history_id_seq OWNED BY ecos_control.ecos_alert_history.id;


--
-- Name: ecos_audit_log; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: TABLE ecos_audit_log; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_audit_log IS '审计日志（Kafka 消费者幂等落库载体 + P1-3 哈希链）——附录A 第6条 getAuditLogDetail 契约载荷 prevHash/currentHash 的唯一数据面；V295 单源锚定补登（2026-10-29 dev 实跑真执行，列清单取库面实查）';


--
-- Name: COLUMN ecos_audit_log.audit_event_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_audit_log.audit_event_id IS 'R7 §2.9.4③ 治理审计事件业务标识（与自增 id 分离，供跨系统对账）';


--
-- Name: COLUMN ecos_audit_log.risk_level; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_audit_log.risk_level IS '治理审计分级（L1~L4，映射见 common-api AuditLevel；不改既有 clearance_level）';


--
-- Name: COLUMN ecos_audit_log.git_commit_sha; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_audit_log.git_commit_sha IS 'Git 归档腿提交号（Commit SHA ≠ 发布授权，授权见 PublishManifest）';


--
-- Name: COLUMN ecos_audit_log.approval_policy_version; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_audit_log.approval_policy_version IS '审批/发布策略版本（可回溯当时按哪版规则放行）';


--
-- Name: ecos_audit_log_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.ecos_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.ecos_audit_log_id_seq OWNED BY ecos_control.ecos_audit_log.id;


--
-- Name: ecos_business_scenario; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_business_scenario (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    business_goal text DEFAULT ''::text,
    department character varying(128),
    priority character varying(16) DEFAULT 'MEDIUM'::character varying,
    status character varying(32) DEFAULT 'DRAFT'::character varying,
    budget character varying(64),
    safety_index_target numeric(8,4),
    actual_safety_index numeric(8,4),
    metrics jsonb DEFAULT '{}'::jsonb,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying,
    update_by character varying(64) DEFAULT 'system'::character varying,
    is_deleted smallint DEFAULT 0 NOT NULL,
    budget_amount numeric(18,2),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


--
-- Name: TABLE ecos_business_scenario; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_business_scenario IS '场景主表真身（workspace 唯一写入口落点；V123/V193/V315 另有两个只停写副本：ecos_ai / ecos_cognitive）';


--
-- Name: COLUMN ecos_business_scenario.budget; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_business_scenario.budget IS 'RT#14/V344：人类可读展示列（历史中文金额原值），不参与任何计算。金额权威列 = budget_amount（numeric(18,2)，MC01）。新写入请只写 budget_amount。';


--
-- Name: COLUMN ecos_business_scenario.budget_amount; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_business_scenario.budget_amount IS '预算金额（元，MC01 NUMERIC(18,2)）。V330 新增；由旧 budget 文本列清洗回填（「850万」→8500000）。旧列 budget 保留供存量读方过渡。';


--
-- Name: ecos_cls_policy; Type: TABLE; Schema: ecos_control; Owner: -
--

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
    mode character varying(16) DEFAULT 'deny'::character varying NOT NULL,
    allowed_columns_json text,
    scope_type character varying(16) DEFAULT 'ROLE'::character varying NOT NULL,
    scope_id character varying(64),
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: COLUMN ecos_cls_policy.resource_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cls_policy.resource_id IS 'PMO-data10 资产驱动 CLS（与 table_name 双轨，老行 resource_id 为空走 table_name 兜底）';


--
-- Name: ecos_cognitive_belief; Type: TABLE; Schema: ecos_control; Owner: -
--

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
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying,
    update_by character varying(64) DEFAULT 'system'::character varying,
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
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
-- Name: TABLE ecos_cognitive_belief; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_cognitive_belief IS '不确定性判断表 — ADR-9 心智层 P 库: 有限离散概率分布+版本+人工覆写(原稿"信念"改称)';


--
-- Name: COLUMN ecos_cognitive_belief.variable_name; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.variable_name IS '不可观测经营变量名';


--
-- Name: COLUMN ecos_cognitive_belief.distribution; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.distribution IS '有限离散概率分布 [{"outcome","prob"}], prob 和=1';


--
-- Name: COLUMN ecos_cognitive_belief.version; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.version IS '分布版本(新证据加权更新 +1)';


--
-- Name: COLUMN ecos_cognitive_belief.snapshot_version; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.snapshot_version IS '时间回放预留(Phase 3 历史决策同参数重算)';


--
-- Name: COLUMN ecos_cognitive_belief.is_manual_override; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.is_manual_override IS '人工覆写标记(专家干预优先)';


--
-- Name: COLUMN ecos_cognitive_belief.last_evidence_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.last_evidence_id IS '触发本次版本更新的证据引用(溯源)';


--
-- Name: COLUMN ecos_cognitive_belief.version_no; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.version_no IS '乐观锁版本号 (DR07 规范)';


--
-- Name: COLUMN ecos_cognitive_belief.distribution_json; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.distribution_json IS 'V333 补齐（D74）：BeliefStore 读端列；与 V191 真身同名同型（TEXT，MC02 合规形态）';


--
-- Name: COLUMN ecos_cognitive_belief.lifecycle_state; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.lifecycle_state IS 'V333 补齐（D74）：读取端 CognitiveAssetLifecycleService.resolve 依赖；权威实例 = ecos_cognitive.ecos_cognitive_belief';


--
-- Name: ecos_cognitive_evidence; Type: TABLE; Schema: ecos_control; Owner: -
--

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
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying,
    update_by character varying(64) DEFAULT 'system'::character varying,
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


--
-- Name: TABLE ecos_cognitive_evidence; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_cognitive_evidence IS '认知证据表 — ADR-9 心智层: 结构化证据(来源/可信度/冲突标记)';


--
-- Name: COLUMN ecos_cognitive_evidence.source_type; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_evidence.source_type IS '来源类型 SYSTEM_DATA/NEWS/EXPERT/PIPELINE';


--
-- Name: COLUMN ecos_cognitive_evidence.blob; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_evidence.blob IS '结构化事实载荷(事实/指标/数值/上下文)';


--
-- Name: COLUMN ecos_cognitive_evidence.confidence; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_evidence.confidence IS '可信度 0~1 (系统数据0.95+ / 权威新闻~0.8 / 小道消息0.3~0.5)';


--
-- Name: COLUMN ecos_cognitive_evidence.is_conflict; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_evidence.is_conflict IS '同事实多证据不一致→冲突待修正';


--
-- Name: COLUMN ecos_cognitive_evidence.version_no; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_evidence.version_no IS '乐观锁版本号 (DR07 规范)';


--
-- Name: ecos_cognitive_hypothesis; Type: TABLE; Schema: ecos_control; Owner: -
--

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
    status character varying(16) DEFAULT 'VALID'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying,
    update_by character varying(64) DEFAULT 'system'::character varying,
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
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
-- Name: TABLE ecos_cognitive_hypothesis; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_cognitive_hypothesis IS '认知假设表 — ADR-9 心智层 H 库: 假设/证据引用/失效监测';


--
-- Name: COLUMN ecos_cognitive_hypothesis.statement; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_hypothesis.statement IS '假设陈述(业务语言)';


--
-- Name: COLUMN ecos_cognitive_hypothesis.metric_ref; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_hypothesis.metric_ref IS '关联经营变量(与 belief.variable_name 对齐)';


--
-- Name: COLUMN ecos_cognitive_hypothesis.evidence_ids; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_hypothesis.evidence_ids IS '支撑证据 id 列表(引用 ecos_cognitive_evidence.id)';


--
-- Name: COLUMN ecos_cognitive_hypothesis.is_valid; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_hypothesis.is_valid IS '当前是否有效(监测命中失效→FALSE)';


--
-- Name: COLUMN ecos_cognitive_hypothesis.version_no; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_hypothesis.version_no IS '乐观锁版本号 (DR07 规范)';


--
-- Name: COLUMN ecos_cognitive_hypothesis.scenario_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_hypothesis.scenario_id IS 'V327 补齐：场景维度假设列表（V296）读列；权威实例 = ecos_cognitive.ecos_cognitive_hypothesis';


--
-- Name: ecos_cognitive_model; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_cognitive_model (
    id character varying(64) NOT NULL,
    model_id character varying(64) NOT NULL,
    model_type character varying(32) NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    features jsonb,
    backtest_score numeric(8,6),
    status character varying(16) DEFAULT 'active'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying,
    update_by character varying(64) DEFAULT 'system'::character varying,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


--
-- Name: TABLE ecos_cognitive_model; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_cognitive_model IS '认知模型注册表 — ADR-8 模型资产落盘（推理结果不落盘）';


--
-- Name: COLUMN ecos_cognitive_model.model_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_model.model_id IS '模型标识';


--
-- Name: COLUMN ecos_cognitive_model.model_type; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_model.model_type IS '模型类型 FORECAST/CAUSAL/SIMULATION/DECISION';


--
-- Name: COLUMN ecos_cognitive_model.backtest_score; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_model.backtest_score IS '回测评分(0~1, 可选)';


--
-- Name: COLUMN ecos_cognitive_model.domain; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_model.domain IS '多租户预留域 (DR08 规范)';


--
-- Name: COLUMN ecos_cognitive_model.version_no; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_model.version_no IS '乐观锁版本号 (DR07 规范)';


--
-- Name: ecos_cognitive_rule; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: TABLE ecos_cognitive_rule; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_cognitive_rule IS '认知规则引擎表 — 阈值/因果/优化规则定义';


--
-- Name: COLUMN ecos_cognitive_rule.id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.id IS '主键，自增';


--
-- Name: COLUMN ecos_cognitive_rule.rule_name; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.rule_name IS '规则名称';


--
-- Name: COLUMN ecos_cognitive_rule.rule_type; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.rule_type IS '规则类型: THRESHOLD/CAUSAL/OPTIMIZATION';


--
-- Name: COLUMN ecos_cognitive_rule.condition_expr; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.condition_expr IS '规则条件表达式';


--
-- Name: COLUMN ecos_cognitive_rule.action_config; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.action_config IS '动作配置 (JSONB)';


--
-- Name: COLUMN ecos_cognitive_rule.priority; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.priority IS '优先级，数值越大优先级越高';


--
-- Name: COLUMN ecos_cognitive_rule.enabled; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.enabled IS '是否启用';


--
-- Name: COLUMN ecos_cognitive_rule.description; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.description IS '规则描述';


--
-- Name: COLUMN ecos_cognitive_rule.created_by; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.created_by IS '创建人';


--
-- Name: COLUMN ecos_cognitive_rule.created_at; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.created_at IS '创建时间';


--
-- Name: COLUMN ecos_cognitive_rule.updated_at; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_rule.updated_at IS '更新时间';


--
-- Name: ecos_cognitive_rule_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.ecos_cognitive_rule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_cognitive_rule_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.ecos_cognitive_rule_id_seq OWNED BY ecos_control.ecos_cognitive_rule.id;


--
-- Name: ecos_cognitive_run_invalidation; Type: TABLE; Schema: ecos_control; Owner: -
--

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
    create_by character varying(64) DEFAULT 'system'::character varying,
    update_by character varying(64) DEFAULT 'system'::character varying,
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_cognitive_run_invalidation; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_cognitive_run_invalidation IS '失效→作废推演结论关联留痕表 — ADR-9 心智状态（PMO-59 P3b T2）';


--
-- Name: COLUMN ecos_cognitive_run_invalidation.event_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_run_invalidation.event_id IS '触发事件 id（ecos.cognitive 事件 cog_evt_ 前缀）';


--
-- Name: COLUMN ecos_cognitive_run_invalidation.hypothesis_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_run_invalidation.hypothesis_id IS '失效假设主键';


--
-- Name: COLUMN ecos_cognitive_run_invalidation.run_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_run_invalidation.run_id IS '被作废推演 run 主键（ecos_scenario_run.id）';


--
-- Name: COLUMN ecos_cognitive_run_invalidation.auto_detected; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_run_invalidation.auto_detected IS '失效是否自动检测触发（false=人工兜底）';


--
-- Name: ecos_decision; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: ecos_decision_approval; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_decision_approval (
    id character varying(64) NOT NULL,
    decision_id character varying(64) NOT NULL,
    approver character varying(64),
    level integer DEFAULT 1,
    status character varying(16) DEFAULT 'pending'::character varying,
    comment text,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: ecos_decision_causal_link; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_decision_causal_link (
    id character varying(64) NOT NULL,
    source_decision_id character varying(64) NOT NULL,
    target_decision_id character varying(64) NOT NULL,
    relationship character varying(32) NOT NULL,
    weight numeric(5,4) DEFAULT 0.5,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: ecos_decision_exception; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_decision_exception (
    id character varying(64) NOT NULL,
    decision_id character varying(64) NOT NULL,
    reason text,
    approver character varying(64),
    status character varying(16) DEFAULT 'pending'::character varying,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: ecos_decision_policy; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_decision_policy (
    id character varying(64) NOT NULL,
    name character varying(128) NOT NULL,
    category character varying(64),
    rules jsonb,
    version integer DEFAULT 1,
    status character varying(16) DEFAULT 'active'::character varying,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: ecos_decision_precedent; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_decision_precedent (
    id character varying(64) NOT NULL,
    decision_id character varying(64) NOT NULL,
    similar_decision_id character varying(64) NOT NULL,
    similarity numeric(5,4) DEFAULT 0,
    note text,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: ecos_decision_record; Type: TABLE; Schema: ecos_control; Owner: -
--

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
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_decision_record; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_decision_record IS '场景决策回执（策略端点可选落盘 + 推理输出 hash 溯源）';


--
-- Name: COLUMN ecos_decision_record.action_plan; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_decision_record.action_plan IS '处置方案 JSON：[{action, justification, tradeoff_cost}]';


--
-- Name: COLUMN ecos_decision_record.source_refs; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_decision_record.source_refs IS '推理输出 hash：[{ep, hash, mind_id, timestamp}]';


--
-- Name: ecos_dq_execution_result; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: TABLE ecos_dq_execution_result; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_dq_execution_result IS 'DQ规则执行结果表 — 记录每次规则执行的通过/失败情况';


--
-- Name: COLUMN ecos_dq_execution_result.id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_dq_execution_result.id IS '执行记录主键';


--
-- Name: COLUMN ecos_dq_execution_result.rule_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_dq_execution_result.rule_id IS '关联的规则ID，对应 ecos_dq_rule.id';


--
-- Name: COLUMN ecos_dq_execution_result.passed; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_dq_execution_result.passed IS '是否通过: true=全部通过, false=存在不满足的数据';


--
-- Name: COLUMN ecos_dq_execution_result.total_rows; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_dq_execution_result.total_rows IS '总行数';


--
-- Name: COLUMN ecos_dq_execution_result.failed_rows; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_dq_execution_result.failed_rows IS '不满足规则的行数';


--
-- Name: COLUMN ecos_dq_execution_result.error_details; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_dq_execution_result.error_details IS '错误详情(JSON)，包含失败行的样例数据';


--
-- Name: COLUMN ecos_dq_execution_result.executed_at; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_dq_execution_result.executed_at IS '执行时间';


--
-- Name: ecos_dq_issue; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_dq_issue (
    id bigint NOT NULL,
    rule_id bigint NOT NULL,
    asset_id character varying(255) DEFAULT ''::character varying NOT NULL,
    description text DEFAULT ''::text,
    status character varying(32) DEFAULT 'open'::character varying NOT NULL,
    severity character varying(32) DEFAULT 'HIGH'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    resolved_at timestamp without time zone
);


--
-- Name: ecos_dq_issue_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.ecos_dq_issue_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_dq_issue_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.ecos_dq_issue_id_seq OWNED BY ecos_control.ecos_dq_issue.id;


--
-- Name: ecos_dq_rule; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_dq_rule (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    rule_type character varying(64) DEFAULT 'NOT_NULL'::character varying NOT NULL,
    config_json text DEFAULT '{}'::text,
    severity character varying(32) DEFAULT 'HIGH'::character varying NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    tenant_id character varying(32)
);


--
-- Name: ecos_dq_rule_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.ecos_dq_rule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_dq_rule_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.ecos_dq_rule_id_seq OWNED BY ecos_control.ecos_dq_rule.id;


--
-- Name: ecos_function_audit_log; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: ecos_function_audit_log_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.ecos_function_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_function_audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.ecos_function_audit_log_id_seq OWNED BY ecos_control.ecos_function_audit_log.id;


--
-- Name: ecos_git_repo; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_git_repo (
    id character varying(64) NOT NULL,
    repository_id character varying(64) NOT NULL,
    repo_name character varying(128) NOT NULL,
    repo_root character varying(256),
    branch_default character varying(64) DEFAULT 'main'::character varying NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    include_paths text,
    exclude_paths text,
    owner_org_id character varying(64)
);


--
-- Name: TABLE ecos_git_repo; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_git_repo IS 'Git 仓库注册表（§2.9.5①/DQ-2 裁决定名；td_git_repository 作废）。服务端持久化注册面，repositoryId 由本表登记；仓库根路径单一事实源仍为 sys_config ecos_git_repo_root（GitRepoRootResolver 解析＋路径穿越守卫）。V300 建表';


--
-- Name: ecos_glossary_term; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_glossary_term (
    id bigint NOT NULL,
    code character varying(64) DEFAULT ''::character varying,
    name character varying(255) NOT NULL,
    definition text DEFAULT ''::text,
    domain character varying(128) DEFAULT ''::character varying,
    owner character varying(128) DEFAULT ''::character varying,
    status character varying(32) DEFAULT 'DRAFT'::character varying,
    created_by character varying(128) DEFAULT ''::character varying,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    domain_id character varying(50),
    tenant_id character varying(32),
    term_type character varying(32) DEFAULT 'CONCEPT'::character varying,
    aliases text[] DEFAULT '{}'::text[],
    object_type_id character varying(64),
    parent_term_id bigint,
    version integer DEFAULT 1,
    examples text[] DEFAULT '{}'::text[],
    tags text[] DEFAULT '{}'::text[],
    is_primary boolean DEFAULT false NOT NULL
);


--
-- Name: TABLE ecos_glossary_term; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_glossary_term IS '术语库 — Glossary terms';


--
-- Name: COLUMN ecos_glossary_term.id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.id IS '主键，自增';


--
-- Name: COLUMN ecos_glossary_term.code; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.code IS '术语编码';


--
-- Name: COLUMN ecos_glossary_term.name; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.name IS '术语名称';


--
-- Name: COLUMN ecos_glossary_term.definition; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.definition IS '术语定义';


--
-- Name: COLUMN ecos_glossary_term.domain; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.domain IS '所属领域';


--
-- Name: COLUMN ecos_glossary_term.owner; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.owner IS '负责人';


--
-- Name: COLUMN ecos_glossary_term.status; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.status IS '状态: DRAFT/REVIEW/PUBLISHED/DEPRECATED';


--
-- Name: COLUMN ecos_glossary_term.created_by; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.created_by IS '创建人';


--
-- Name: COLUMN ecos_glossary_term.created_at; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.created_at IS '创建时间';


--
-- Name: COLUMN ecos_glossary_term.updated_at; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.updated_at IS '更新时间';


--
-- Name: COLUMN ecos_glossary_term.term_type; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.term_type IS '词条分类 ENTITY/RELATION/METRIC/FUNCTION/CONCEPT（与 public 副本同义，D15 补齐）';


--
-- Name: COLUMN ecos_glossary_term.is_primary; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.is_primary IS '是否实体主术语（与 public 副本同义，D15 补齐）';


--
-- Name: ecos_glossary_term_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.ecos_glossary_term_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_glossary_term_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.ecos_glossary_term_id_seq OWNED BY ecos_control.ecos_glossary_term.id;


--
-- Name: ecos_goal_tracking; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: ecos_interface_ref; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_interface_ref (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    interface_type character varying(32) NOT NULL,
    endpoint character varying(512) NOT NULL,
    method character varying(16),
    timeout_ms integer DEFAULT 3000 NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_interface_ref; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_interface_ref IS '场景绑定的外部接口引用真表';


--
-- Name: ecos_marketplace_access_request; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_marketplace_access_request (
    id bigint NOT NULL,
    asset_id bigint NOT NULL,
    reason text DEFAULT ''::text,
    applicant character varying(128) DEFAULT ''::character varying,
    status character varying(32) DEFAULT 'PENDING'::character varying,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


--
-- Name: TABLE ecos_marketplace_access_request; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_marketplace_access_request IS '数据市场访问申请记录';


--
-- Name: COLUMN ecos_marketplace_access_request.id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_access_request.id IS '主键，自增';


--
-- Name: COLUMN ecos_marketplace_access_request.asset_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_access_request.asset_id IS '关联资产ID';


--
-- Name: COLUMN ecos_marketplace_access_request.reason; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_access_request.reason IS '申请理由';


--
-- Name: COLUMN ecos_marketplace_access_request.applicant; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_access_request.applicant IS '申请人';


--
-- Name: COLUMN ecos_marketplace_access_request.status; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_access_request.status IS '状态: PENDING/APPROVED/REJECTED';


--
-- Name: COLUMN ecos_marketplace_access_request.created_at; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_access_request.created_at IS '创建时间';


--
-- Name: ecos_marketplace_access_request_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.ecos_marketplace_access_request_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_marketplace_access_request_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.ecos_marketplace_access_request_id_seq OWNED BY ecos_control.ecos_marketplace_access_request.id;


--
-- Name: ecos_marketplace_asset; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_marketplace_asset (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    category character varying(64) DEFAULT ''::character varying,
    owner character varying(128) DEFAULT ''::character varying,
    rating numeric(3,2) DEFAULT 0.0,
    popularity integer DEFAULT 0,
    status character varying(32) DEFAULT 'PUBLISHED'::character varying,
    created_at timestamp without time zone DEFAULT now(),
    ontology_entity_id character varying(128) DEFAULT NULL::character varying
);


--
-- Name: TABLE ecos_marketplace_asset; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_marketplace_asset IS '数据市场 — marketplace assets';


--
-- Name: COLUMN ecos_marketplace_asset.id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.id IS '主键，自增';


--
-- Name: COLUMN ecos_marketplace_asset.name; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.name IS '资产名称';


--
-- Name: COLUMN ecos_marketplace_asset.description; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.description IS '资产描述';


--
-- Name: COLUMN ecos_marketplace_asset.category; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.category IS '分类: 数据集/AI模型/API/报表';


--
-- Name: COLUMN ecos_marketplace_asset.owner; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.owner IS '所属部门/所有者';


--
-- Name: COLUMN ecos_marketplace_asset.rating; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.rating IS '评分 0.00~5.00';


--
-- Name: COLUMN ecos_marketplace_asset.popularity; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.popularity IS '综合热度值';


--
-- Name: COLUMN ecos_marketplace_asset.status; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.status IS '状态: DRAFT/PUBLISHED/DEPRECATED';


--
-- Name: COLUMN ecos_marketplace_asset.created_at; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.created_at IS '创建时间';


--
-- Name: COLUMN ecos_marketplace_asset.ontology_entity_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.ontology_entity_id IS '关联的本体实体ID';


--
-- Name: ecos_marketplace_asset_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.ecos_marketplace_asset_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_marketplace_asset_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.ecos_marketplace_asset_id_seq OWNED BY ecos_control.ecos_marketplace_asset.id;


--
-- Name: ecos_mask_strategy; Type: TABLE; Schema: ecos_control; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_mask_strategy_code CHECK (((strategy_code)::text = ANY ((ARRAY['ID_CARD'::character varying, 'PHONE'::character varying, 'EMAIL'::character varying, 'BANK_CARD'::character varying, 'AMOUNT'::character varying, 'ADDRESS'::character varying, 'GENERAL'::character varying, 'none'::character varying])::text[])))
);


--
-- Name: TABLE ecos_mask_strategy; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_mask_strategy IS '脱敏策略字典（§4.4.2 第 6 行）：strategy_code 值域 = DataMaskingService.ruleForDataType 实测 7 类 + none 显式不脱敏；列清单单源 = 详设 §4.4.3 末段';


--
-- Name: COLUMN ecos_mask_strategy.strategy_code; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_mask_strategy.strategy_code IS '策略码封闭枚举：ID_CARD/PHONE/EMAIL/BANK_CARD/AMOUNT/ADDRESS/GENERAL/none（D1：设计"8 类"按代码实测含 none 共 8 值立 CHECK）';


--
-- Name: COLUMN ecos_mask_strategy.apply_channels; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_mask_strategy.apply_channels IS '应用通道绑定，`+` 连接（page/export/ai，对应 DecisionBundle.Purpose 三值）；NULL=全通道适用';


--
-- Name: ecos_object_attachment; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: ecos_object_links; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_object_links (
    id character varying(64) DEFAULT (gen_random_uuid())::text NOT NULL,
    source_id character varying(64) NOT NULL,
    target_id character varying(64) NOT NULL,
    relation_code character varying(128) NOT NULL,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: ecos_object_relationship; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: ecos_object_state_machine; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: ecos_object_timeline; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: ecos_ontology_action; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_ontology_action (
    id character varying(64) NOT NULL,
    entity_id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    action_type character varying(64) DEFAULT 'CUSTOM'::character varying NOT NULL,
    rule_json text DEFAULT ''::text,
    strategy character varying(255) DEFAULT ''::character varying,
    status character varying(32) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    is_deleted smallint DEFAULT 0 NOT NULL,
    update_by character varying(128),
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: ecos_ontology_entity; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_ontology_entity (
    id character varying(64) NOT NULL,
    ontology_id character varying(64) NOT NULL,
    code character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    entity_type character varying(64) DEFAULT 'MASTER'::character varying,
    sort_order integer DEFAULT 1,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    tenant_id character varying(32),
    domain_id character varying(50),
    is_deleted smallint DEFAULT 0 NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    update_by character varying(128),
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: ecos_ontology_property; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_ontology_property (
    id character varying(64) NOT NULL,
    entity_id character varying(64) NOT NULL,
    code character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    property_type character varying(64) DEFAULT 'STRING'::character varying,
    required_flag integer DEFAULT 0,
    searchable_flag integer DEFAULT 0,
    sort_order integer DEFAULT 1,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    function_type character varying(32),
    function_expression text,
    is_deleted smallint DEFAULT 0 NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    update_by character varying(128),
    unique_flag integer DEFAULT 0 NOT NULL,
    description text,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: COLUMN ecos_ontology_property.function_type; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_ontology_property.function_type IS '计算属性类型: EXPRESSION(同实体运算) / AGGREGATION(跨link聚合) / LOOKUP(简单link取值)';


--
-- Name: COLUMN ecos_ontology_property.function_expression; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_ontology_property.function_expression IS '计算表达式文本';


--
-- Name: COLUMN ecos_ontology_property.is_deleted; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_ontology_property.is_deleted IS 'V327 补齐：读端 WHERE is_deleted=0 依赖；该镜像属 V3 时代遗留，权威本体域读端在 ecos_ontology schema';


--
-- Name: ecos_ontology_relationship; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_ontology_relationship (
    id character varying(64) NOT NULL,
    source_entity_id character varying(64) NOT NULL,
    target_entity_id character varying(64) NOT NULL,
    code character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    relationship_type character varying(64) DEFAULT 'ONE_TO_MANY'::character varying,
    created_at timestamp without time zone DEFAULT now(),
    is_deleted smallint DEFAULT 0 NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    update_by character varying(128),
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: ecos_pareto_problem; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_pareto_problem (
    id character varying(36) NOT NULL,
    problem_name character varying(255) DEFAULT ''::character varying NOT NULL,
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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


--
-- Name: TABLE ecos_pareto_problem; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_pareto_problem IS 'Pareto 多目标优化问题登记与结果快照（D106 落地；solutions_json 存完整前沿，便于 /result/{id} 直取）';


--
-- Name: COLUMN ecos_pareto_problem.solutions_json; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_pareto_problem.solutions_json IS 'Pareto 前沿解集 JSON 数组：[{"variables":{"x1":..},"objectives":{"f1":..}}]';


--
-- Name: ecos_pipeline_edge; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_pipeline_edge (
    id character varying(64) NOT NULL,
    definition_id character varying(64) NOT NULL,
    from_node_id character varying(64) NOT NULL,
    to_node_id character varying(64) NOT NULL,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: ecos_pipeline_node; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_pipeline_node (
    id character varying(64) NOT NULL,
    definition_id character varying(64) NOT NULL,
    node_id character varying(64) NOT NULL,
    type character varying(64) DEFAULT 'TRANSFORM_SQL'::character varying NOT NULL,
    config jsonb DEFAULT '{}'::jsonb,
    position_x integer DEFAULT 0,
    position_y integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    depends_on jsonb DEFAULT '[]'::jsonb
);


--
-- Name: TABLE ecos_pipeline_node; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_pipeline_node IS 'Pipeline 节点表 — DAG 中的执行节点';


--
-- Name: COLUMN ecos_pipeline_node.id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_pipeline_node.id IS '节点主键';


--
-- Name: COLUMN ecos_pipeline_node.definition_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_pipeline_node.definition_id IS '所属 Pipeline 定义 ID，关联 ecos_pipeline_definition.id';


--
-- Name: COLUMN ecos_pipeline_node.node_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_pipeline_node.node_id IS '前端节点标识';


--
-- Name: COLUMN ecos_pipeline_node.type; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_pipeline_node.type IS '节点类型: SOURCE_JDBC / TRANSFORM_SQL / OUTPUT_OBJECT';


--
-- Name: COLUMN ecos_pipeline_node.config; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_pipeline_node.config IS '节点配置(JSON): SQL语句、JDBC连接信息等';


--
-- Name: COLUMN ecos_pipeline_node.position_x; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_pipeline_node.position_x IS '画布 X 坐标';


--
-- Name: COLUMN ecos_pipeline_node.position_y; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_pipeline_node.position_y IS '画布 Y 坐标';


--
-- Name: COLUMN ecos_pipeline_node.created_at; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_pipeline_node.created_at IS '创建时间';


--
-- Name: COLUMN ecos_pipeline_node.updated_at; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_pipeline_node.updated_at IS '更新时间';


--
-- Name: COLUMN ecos_pipeline_node.depends_on; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_pipeline_node.depends_on IS '依赖的节点ID列表（JSON数组），用于拓扑排序';


--
-- Name: ecos_provenance_entry; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: ecos_rls_policy; Type: TABLE; Schema: ecos_control; Owner: -
--

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
    source_kind character varying(32) DEFAULT 'legacy_text'::character varying NOT NULL,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: COLUMN ecos_rls_policy.resource_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_rls_policy.resource_id IS 'PMO-data10 资产驱动 RLS（兼容 table_name）';


--
-- Name: ecos_runtime_audit_retry; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_runtime_audit_retry (
    id character varying(36) NOT NULL,
    event_type character varying(64) NOT NULL,
    payload text NOT NULL,
    attempts smallint DEFAULT 0 NOT NULL,
    next_retry_at timestamp without time zone NOT NULL,
    last_error character varying(1000),
    status character varying(16) DEFAULT 'pending'::character varying NOT NULL,
    trace_id character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    tenant_id character varying(36),
    create_by character varying(100),
    update_by character varying(100)
);


--
-- Name: ecos_runtime_event_dlq; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_runtime_event_dlq (
    id character varying(36) NOT NULL,
    topic character varying(128) NOT NULL,
    dlt_topic character varying(160) NOT NULL,
    payload text NOT NULL,
    error_message character varying(1000),
    attempts smallint DEFAULT 0 NOT NULL,
    first_seen_at timestamp without time zone NOT NULL,
    replayed_at timestamp without time zone,
    status character varying(16) DEFAULT 'pending'::character varying NOT NULL,
    trace_id character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    tenant_id character varying(36),
    create_by character varying(100),
    update_by character varying(100)
);


--
-- Name: ecos_scenario_binding; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_scenario_binding (
    id character varying(64) NOT NULL,
    scenario_id character varying(64) NOT NULL,
    binding_type character varying(32) NOT NULL,
    target_ref character varying(255) NOT NULL,
    remark text DEFAULT ''::text,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying,
    update_by character varying(64) DEFAULT 'system'::character varying,
    is_deleted smallint DEFAULT 0 NOT NULL,
    target_id character varying(64),
    target_type character varying(32),
    CONSTRAINT chk_binding_target_type CHECK (((target_type IS NULL) OR ((target_type)::text = ANY ((ARRAY['DATASOURCE'::character varying, 'ONTOLOGY_ENTITY'::character varying, 'KNOWLEDGE_ARTICLE'::character varying, 'AGENT_PROFILE'::character varying, 'SECURITY_POLICY'::character varying, 'INTERFACE_REF'::character varying])::text[]))))
);


--
-- Name: COLUMN ecos_scenario_binding.target_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_scenario_binding.target_id IS 'v2.0 真 PG 主键（替代 target_ref 字符串别名；target_ref 保留只读兼容）';


--
-- Name: COLUMN ecos_scenario_binding.target_type; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_scenario_binding.target_type IS '真资源类型：DATASOURCE|ONTOLOGY_ENTITY|KNOWLEDGE_ARTICLE|AGENT_PROFILE|SECURITY_POLICY|INTERFACE_REF';


--
-- Name: ecos_scenario_binding_link; Type: TABLE; Schema: ecos_control; Owner: -
--

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
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    CONSTRAINT chk_bsl_link_type CHECK (((link_type)::text = ANY ((ARRAY['MAPPING'::character varying, 'EXTRACTION'::character varying, 'COGNITION'::character varying, 'GOVERN'::character varying, 'EXPOSE'::character varying])::text[])))
);


--
-- Name: TABLE ecos_scenario_binding_link; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_scenario_binding_link IS '场景绑定关系边：六类资源之外的有向关系（§0.6.2）';


--
-- Name: COLUMN ecos_scenario_binding_link.link_type; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_scenario_binding_link.link_type IS 'MAPPING | EXTRACTION | COGNITION | GOVERN | EXPOSE';


--
-- Name: COLUMN ecos_scenario_binding_link.source_contract; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_scenario_binding_link.source_contract IS '跨工作台契约引用（如 ecos_entity_table_mapping.id），§0.6.2.2 必带';


--
-- Name: ecos_scenario_blueprint; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_scenario_blueprint (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    scenario_id character varying(36) NOT NULL,
    version integer NOT NULL,
    status character varying(24) DEFAULT 'DRAFT'::character varying NOT NULL,
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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_scenario_blueprint; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_scenario_blueprint IS '场景蓝图权威载体（补充设计 v1.0 §6）：workspace 属主，唯一写入面；ai/cognitive 域禁复制副本（§6.1/§17 捷径禁令 2）；发布版本不可变，RUN 运行回链 version+context_digest（SUP-BP-02/03）';


--
-- Name: COLUMN ecos_scenario_blueprint.version; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_scenario_blueprint.version IS '蓝图版本号（应用侧递增，非乐观锁 version_no）；RELEASED 后该行 content_json/artifact_digest 只读，更新 = version+1 新行 + 旧行 DEPRECATED（§6.6 发布不可变）';


--
-- Name: ecos_scenario_mind; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_scenario_mind (
    id bigint NOT NULL,
    scenario_id character varying(64) NOT NULL,
    mind_label character varying(64) DEFAULT 'base'::character varying NOT NULL,
    active_mind smallint DEFAULT 0 NOT NULL,
    initial_belief_jsonb jsonb DEFAULT '{}'::jsonb NOT NULL,
    evidence_refs jsonb DEFAULT '[]'::jsonb NOT NULL,
    hypothesis_refs jsonb DEFAULT '[]'::jsonb NOT NULL,
    model_refs jsonb DEFAULT '[]'::jsonb NOT NULL,
    cognitive_endpoints jsonb DEFAULT '{}'::jsonb NOT NULL,
    initial_confidence double precision DEFAULT 0.5 NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_scenario_mind; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_scenario_mind IS '场景授权心智变体（1:N，active_mind 哨兵唯一激活）';


--
-- Name: ecos_scenario_mind_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

ALTER TABLE ecos_control.ecos_scenario_mind ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME ecos_control.ecos_scenario_mind_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: ecos_scenario_run; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_scenario_run (
    id character varying(64) NOT NULL,
    scenario_id character varying(64) NOT NULL,
    run_type character varying(32) NOT NULL,
    status character varying(32) DEFAULT 'RUNNING'::character varying NOT NULL,
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
    create_by character varying(64) DEFAULT 'system'::character varying,
    update_by character varying(64) DEFAULT 'system'::character varying,
    is_deleted smallint DEFAULT 0 NOT NULL,
    run_mode character varying(16) DEFAULT 'FORMAL'::character varying NOT NULL,
    source_run_id character varying(64),
    sandbox_lineage character varying(16) DEFAULT 'UNKNOWN'::character varying NOT NULL
);


--
-- Name: TABLE ecos_scenario_run; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_scenario_run IS '场景运行记录 — PMO-52 场景工作台认知闭环';


--
-- Name: COLUMN ecos_scenario_run.run_type; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_scenario_run.run_type IS '运行类型 DIAGNOSE/FORECAST/SIMULATE/STRATEGY/FULL';


--
-- Name: COLUMN ecos_scenario_run.degraded; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_scenario_run.degraded IS '是否存在 KG 降级（任一步走规则兜底则 true）';


--
-- Name: ecos_scenario_sandbox_layout; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_scenario_sandbox_layout (
    id bigint NOT NULL,
    scenario_id character varying(64) NOT NULL,
    layout_jsonb jsonb DEFAULT '{}'::jsonb NOT NULL,
    layout_version integer DEFAULT 1 NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_scenario_sandbox_layout; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_scenario_sandbox_layout IS '业务场景沙盘画布布局（React Flow 序列化，乐观锁 layout_version）';


--
-- Name: ecos_scenario_sandbox_layout_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

ALTER TABLE ecos_control.ecos_scenario_sandbox_layout ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME ecos_control.ecos_scenario_sandbox_layout_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: ecos_security_asset; Type: TABLE; Schema: ecos_control; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    tenant_id character varying(36),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100)
);


--
-- Name: ecos_security_crypto_key; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_security_crypto_key (
    id character varying(36) NOT NULL,
    key_id character varying(64) NOT NULL,
    algorithm character varying(32) NOT NULL,
    key_version smallint NOT NULL,
    status character varying(16) DEFAULT 'active'::character varying NOT NULL,
    master_key_ref character varying(128) NOT NULL,
    rotate_at timestamp without time zone,
    retired_at timestamp without time zone,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    tenant_id character varying(36)
);


--
-- Name: ecos_security_policy; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_security_policy (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    domain character varying(128),
    policy_expr text NOT NULL,
    priority integer DEFAULT 100 NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_security_policy; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_security_policy IS '场景绑定的安全策略真表';


--
-- Name: ecos_spans; Type: TABLE; Schema: ecos_control; Owner: -
--

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
    status character varying(16) DEFAULT 'OK'::character varying,
    attributes jsonb,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: ecos_task; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_task (
    id bigint NOT NULL,
    task_name character varying(256) NOT NULL,
    task_type character varying(32) NOT NULL,
    status character varying(32) DEFAULT 'PENDING'::character varying,
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
-- Name: TABLE ecos_task; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_task IS '任务调度表 — 统一任务编排与执行追踪';


--
-- Name: COLUMN ecos_task.id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_task.id IS '主键，自增';


--
-- Name: COLUMN ecos_task.task_name; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_task.task_name IS '任务名称';


--
-- Name: COLUMN ecos_task.task_type; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_task.task_type IS '任务类型: PIPELINE/REALTIME/AGENT/ADMIN';


--
-- Name: COLUMN ecos_task.status; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_task.status IS '任务状态: PENDING/RUNNING/SUCCESS/FAILED/CANCELLED';


--
-- Name: COLUMN ecos_task.config; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_task.config IS '任务配置(JSON): SQL语句、规则ID、参数等';


--
-- Name: COLUMN ecos_task.runner; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_task.runner IS '执行器: doris/rule/hermes';


--
-- Name: COLUMN ecos_task.priority; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_task.priority IS '优先级，数值越大优先级越高';


--
-- Name: COLUMN ecos_task.retry_count; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_task.retry_count IS '已重试次数';


--
-- Name: COLUMN ecos_task.max_retries; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_task.max_retries IS '最大重试次数';


--
-- Name: COLUMN ecos_task.scheduled_at; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_task.scheduled_at IS '计划执行时间';


--
-- Name: COLUMN ecos_task.started_at; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_task.started_at IS '实际开始时间';


--
-- Name: COLUMN ecos_task.completed_at; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_task.completed_at IS '完成时间';


--
-- Name: COLUMN ecos_task.result; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_task.result IS '执行结果(JSON)';


--
-- Name: COLUMN ecos_task.error_message; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_task.error_message IS '错误信息';


--
-- Name: COLUMN ecos_task.created_by; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_task.created_by IS '创建人';


--
-- Name: COLUMN ecos_task.created_at; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_task.created_at IS '创建时间';


--
-- Name: COLUMN ecos_task.updated_at; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_task.updated_at IS '更新时间';


--
-- Name: ecos_task_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.ecos_task_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_task_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.ecos_task_id_seq OWNED BY ecos_control.ecos_task.id;


--
-- Name: ecos_tenant; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_tenant (
    id character varying(32) NOT NULL,
    tenant_name character varying(64) NOT NULL,
    tenant_code character varying(32),
    status character varying(16) DEFAULT 'ACTIVE'::character varying,
    max_users integer DEFAULT 0,
    max_storage_mb bigint DEFAULT 0,
    max_api_per_day bigint DEFAULT 0,
    isolation_mode character varying(16) DEFAULT 'ROW_FILTER'::character varying,
    schema_name character varying(64),
    database_url character varying(256),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


--
-- Name: ecos_tenant_quota; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: ecos_tenant_quota_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.ecos_tenant_quota_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_tenant_quota_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.ecos_tenant_quota_id_seq OWNED BY ecos_control.ecos_tenant_quota.id;


--
-- Name: ecos_tenant_usage; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_tenant_usage (
    tenant_id character varying(64) NOT NULL,
    usage_date date NOT NULL,
    quota_type character varying(32) NOT NULL,
    used_count bigint DEFAULT 0,
    updated_at timestamp without time zone DEFAULT now()
);


--
-- Name: ecos_token_usage; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: ecos_token_usage_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.ecos_token_usage_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_token_usage_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.ecos_token_usage_id_seq OWNED BY ecos_control.ecos_token_usage.id;


--
-- Name: ecos_warn_log; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_warn_log (
    id character varying(64) NOT NULL,
    log_id character varying(64) NOT NULL,
    warn_type character varying(64),
    warn_level character varying(16) DEFAULT 'WARN'::character varying NOT NULL,
    warn_objid character varying(128),
    warn_objname character varying(256),
    warn_message text,
    fault_context jsonb,
    review_tag character varying(64),
    warn_hand character varying(64),
    warn_result text,
    ishanded character varying(4) DEFAULT '0'::character varying NOT NULL,
    warn_time timestamp without time zone DEFAULT now() NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying,
    update_by character varying(64) DEFAULT 'system'::character varying,
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_warn_log; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.ecos_warn_log IS 'runtime-monitor 告警日志落库表 — PMO-59 P4a（关闭 P2b 告警内存态残留风险）';


--
-- Name: COLUMN ecos_warn_log.log_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_warn_log.log_id IS '告警日志业务键（幂等去重，uniq 索引）';


--
-- Name: COLUMN ecos_warn_log.warn_type; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_warn_log.warn_type IS '告警类型（如 COGNITIVE_HYPOTHESIS_INVALIDATED）';


--
-- Name: COLUMN ecos_warn_log.fault_context; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_warn_log.fault_context IS '结构化故障上下文 JSONB（与 ecos.cognitive 事件 faultContext 同口径）';


--
-- Name: COLUMN ecos_warn_log.review_tag; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.ecos_warn_log.review_tag IS '复盘聚合标签（与 MentalEventPublisher.REVIEW_TAG 同源）';


--
-- Name: ecos_wm_causal_link; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_wm_causal_link (
    id bigint NOT NULL,
    source_goal_id bigint NOT NULL,
    target_goal_id bigint NOT NULL,
    relationship_type character varying(32) DEFAULT 'POSITIVE'::character varying NOT NULL,
    description text DEFAULT ''::text,
    created_at timestamp without time zone DEFAULT now(),
    time_lag_days integer DEFAULT 0,
    correlation_coefficient numeric(4,3) DEFAULT 0.0
);


--
-- Name: ecos_wm_causal_link_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.ecos_wm_causal_link_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_wm_causal_link_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.ecos_wm_causal_link_id_seq OWNED BY ecos_control.ecos_wm_causal_link.id;


--
-- Name: ecos_wm_goal; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_wm_goal (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    parent_id bigint,
    progress integer DEFAULT 0,
    status character varying(32) DEFAULT 'PLANNED'::character varying,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    goal_type character varying(32) DEFAULT 'STRATEGIC'::character varying,
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
    kpi_formula character varying(256) DEFAULT 'currentValue/targetValue*100'::character varying,
    measure_frequency character varying(16) DEFAULT 'MONTHLY'::character varying,
    alert_threshold_warn numeric(5,2) DEFAULT 80.0,
    alert_threshold_critical numeric(5,2) DEFAULT 50.0,
    CONSTRAINT ecos_wm_goal_progress_check CHECK (((progress >= 0) AND (progress <= 100)))
);


--
-- Name: ecos_wm_goal_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.ecos_wm_goal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_wm_goal_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.ecos_wm_goal_id_seq OWNED BY ecos_control.ecos_wm_goal.id;


--
-- Name: ecos_wm_goal_log; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: ecos_wm_goal_log_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.ecos_wm_goal_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_wm_goal_log_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.ecos_wm_goal_log_id_seq OWNED BY ecos_control.ecos_wm_goal_log.id;


--
-- Name: ecos_wm_scenario; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_wm_scenario (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    config_json text DEFAULT '{}'::text,
    status character varying(32) DEFAULT 'DRAFT'::character varying,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


--
-- Name: ecos_wm_scenario_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.ecos_wm_scenario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_wm_scenario_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.ecos_wm_scenario_id_seq OWNED BY ecos_control.ecos_wm_scenario.id;


--
-- Name: ecos_workflow; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_workflow (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    status character varying(32) DEFAULT 'draft'::character varying,
    mode character varying(32) DEFAULT 'sequential'::character varying,
    nodes text DEFAULT '[]'::text,
    edges text DEFAULT '[]'::text,
    published_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


--
-- Name: ecos_workflow_approval; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: ecos_workflow_instance; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: ecos_workflow_log; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: ecos_workflow_task; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_workflow_task (
    id character varying(50) NOT NULL,
    instance_id character varying(50) NOT NULL,
    node_id character varying(100) NOT NULL,
    task_type character varying(50) NOT NULL,
    title character varying(500),
    assignee character varying(100),
    candidate_users jsonb,
    candidate_roles jsonb,
    status character varying(50) DEFAULT 'New'::character varying,
    priority character varying(20) DEFAULT 'NORMAL'::character varying,
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
-- Name: ecos_world_scenarios; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.ecos_world_scenarios (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    goal_ids text DEFAULT '[]'::text,
    status character varying(32) DEFAULT 'draft'::character varying,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


--
-- Name: kb_cognitive_pipeline; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.kb_cognitive_pipeline (
    id bigint NOT NULL,
    pipeline_id character varying(64) NOT NULL,
    name character varying(256) NOT NULL,
    status character varying(32) DEFAULT 'DRAFT'::character varying NOT NULL,
    config jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_by character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    result jsonb,
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE kb_cognitive_pipeline; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.kb_cognitive_pipeline IS '认知管线定义（CognitivePipeline 持久化，原 ConcurrentHashMap 内存态）';


--
-- Name: COLUMN kb_cognitive_pipeline.id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.id IS '自增主键';


--
-- Name: COLUMN kb_cognitive_pipeline.pipeline_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.pipeline_id IS '管线业务 ID (UUID)';


--
-- Name: COLUMN kb_cognitive_pipeline.name; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.name IS '管线名称';


--
-- Name: COLUMN kb_cognitive_pipeline.status; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.status IS '管线状态: DRAFT/ACTIVE/ARCHIVED';


--
-- Name: COLUMN kb_cognitive_pipeline.config; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.config IS '管线节点集合 (JSONB: [{nodeId, nodeType, config, dependsOn}, ...])';


--
-- Name: COLUMN kb_cognitive_pipeline.created_by; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.created_by IS '创建人';


--
-- Name: COLUMN kb_cognitive_pipeline.created_at; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.created_at IS '创建时间';


--
-- Name: COLUMN kb_cognitive_pipeline.updated_at; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.updated_at IS '更新时间';


--
-- Name: COLUMN kb_cognitive_pipeline.result; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.result IS '最近执行结果 (JSONB，可为空)';


--
-- Name: COLUMN kb_cognitive_pipeline.is_deleted; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.is_deleted IS '逻辑删除标记: 0=未删除, 1=已删除';


--
-- Name: kb_cognitive_pipeline_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.kb_cognitive_pipeline_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_cognitive_pipeline_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.kb_cognitive_pipeline_id_seq OWNED BY ecos_control.kb_cognitive_pipeline.id;


--
-- Name: kb_lineage_event; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: TABLE kb_lineage_event; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.kb_lineage_event IS '血缘事件（Lineage parse 持久化，原 ConcurrentHashMap 内存态）';


--
-- Name: COLUMN kb_lineage_event.id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_lineage_event.id IS '自增主键';


--
-- Name: COLUMN kb_lineage_event.event_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_lineage_event.event_id IS '事件业务 ID (UUID)';


--
-- Name: COLUMN kb_lineage_event.query; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_lineage_event.query IS '解析时使用的查询/输入描述';


--
-- Name: COLUMN kb_lineage_event.format; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_lineage_event.format IS '血缘格式: openlineage/atlas';


--
-- Name: COLUMN kb_lineage_event.nodes; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_lineage_event.nodes IS '血缘节点列表 (JSONB: [{id, label, type}, ...])';


--
-- Name: COLUMN kb_lineage_event.edges; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_lineage_event.edges IS '血缘边列表 (JSONB: [{source, target, type}, ...])';


--
-- Name: COLUMN kb_lineage_event.parse_at; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_lineage_event.parse_at IS '解析时间';


--
-- Name: COLUMN kb_lineage_event.is_deleted; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_lineage_event.is_deleted IS '逻辑删除标记: 0=未删除, 1=已删除';


--
-- Name: kb_lineage_event_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.kb_lineage_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_lineage_event_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.kb_lineage_event_id_seq OWNED BY ecos_control.kb_lineage_event.id;


--
-- Name: kb_ontology_snapshot; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.kb_ontology_snapshot (
    id bigint NOT NULL,
    ontology_id character varying(64) NOT NULL,
    version character varying(128) NOT NULL,
    entity_codes jsonb DEFAULT '[]'::jsonb NOT NULL,
    relationship_codes jsonb DEFAULT '[]'::jsonb NOT NULL,
    schema_hash character varying(64),
    created_by character varying(128) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE kb_ontology_snapshot; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.kb_ontology_snapshot IS '本体版本快照（PMO-54 与 PMO-50 T4 本体发布事件消费 — V115/Kb 侧补齐）';


--
-- Name: COLUMN kb_ontology_snapshot.id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_ontology_snapshot.id IS '自增主键';


--
-- Name: COLUMN kb_ontology_snapshot.ontology_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_ontology_snapshot.ontology_id IS '本体业务 ID';


--
-- Name: COLUMN kb_ontology_snapshot.version; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_ontology_snapshot.version IS '本体版本 (published version)';


--
-- Name: COLUMN kb_ontology_snapshot.entity_codes; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_ontology_snapshot.entity_codes IS '本体包含实体 code 集合 (JSONB array)';


--
-- Name: COLUMN kb_ontology_snapshot.relationship_codes; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_ontology_snapshot.relationship_codes IS '本体包含关系 code 集合 (JSONB array)';


--
-- Name: COLUMN kb_ontology_snapshot.schema_hash; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_ontology_snapshot.schema_hash IS '发行时 schema JSON 的 SHA-256 hash 减少';


--
-- Name: COLUMN kb_ontology_snapshot.created_by; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_ontology_snapshot.created_by IS '发布人 / 系统标识';


--
-- Name: COLUMN kb_ontology_snapshot.created_at; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_ontology_snapshot.created_at IS '快照时间（含 upsert 时间刷行）';


--
-- Name: COLUMN kb_ontology_snapshot.is_deleted; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.kb_ontology_snapshot.is_deleted IS '逻辑删除: 0=当前 / 1=已删 / 2=历史旧版本 (EcosOntologyEventConsumer 标记语义)';


--
-- Name: kb_ontology_snapshot_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.kb_ontology_snapshot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_ontology_snapshot_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.kb_ontology_snapshot_id_seq OWNED BY ecos_control.kb_ontology_snapshot.id;


--
-- Name: schema_changes; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: schema_changes_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.schema_changes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: schema_changes_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.schema_changes_id_seq OWNED BY ecos_control.schema_changes.id;


--
-- Name: schema_snapshots; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.schema_snapshots (
    id bigint NOT NULL,
    datasource_id character varying(128) NOT NULL,
    table_name character varying(256) NOT NULL,
    column_hash character varying(64) NOT NULL,
    col_sig text,
    snapshot_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: schema_snapshots_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.schema_snapshots_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: schema_snapshots_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.schema_snapshots_id_seq OWNED BY ecos_control.schema_snapshots.id;


--
-- Name: sys_agent_message; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.sys_agent_message (
    id bigint NOT NULL,
    session_id character varying(64),
    role character varying(16),
    content text,
    tool_calls jsonb,
    tool_results jsonb,
    tokens integer,
    created_at bigint,
    thread_id character varying(32) DEFAULT 'main'::character varying NOT NULL
);


--
-- Name: COLUMN sys_agent_message.thread_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.sys_agent_message.thread_id IS '会话线程标识，默认 main，支持同一会话下多线程对话';


--
-- Name: sys_agent_message_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.sys_agent_message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_agent_message_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.sys_agent_message_id_seq OWNED BY ecos_control.sys_agent_message.id;


--
-- Name: sys_agent_session; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.sys_agent_session (
    id character varying(64) NOT NULL,
    agent_id character varying(64) NOT NULL,
    user_id character varying(64),
    tenant_id character varying(64),
    status character varying(16) DEFAULT 'ACTIVE'::character varying,
    message_count integer DEFAULT 0,
    created_at bigint,
    last_active_at bigint
);


--
-- Name: sys_compliance_rule; Type: TABLE; Schema: ecos_control; Owner: -
--

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
    status character varying(32) DEFAULT 'DRAFT'::character varying,
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
-- Name: sys_config; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.sys_config (
    id character varying(64) NOT NULL,
    config_group character varying(64) NOT NULL,
    config_key character varying(128) NOT NULL,
    config_value text NOT NULL,
    config_type character varying(32) DEFAULT 'string'::character varying,
    config_label character varying(255) NOT NULL,
    config_label_en character varying(255),
    description text,
    sort_order integer DEFAULT 0,
    status character varying(32) DEFAULT 'active'::character varying,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    edition character varying(16) DEFAULT 'all'::character varying
);


--
-- Name: TABLE sys_config; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.sys_config IS '系统配置单表（config_group 分组，铁律 §十 配置单源）；回测/复审判定阈值见 config_group=''fc.backtest''（V280，详设-09 H-4）';


--
-- Name: sys_dict; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.sys_dict (
    id character varying(64) NOT NULL,
    dict_type character varying(64) NOT NULL,
    dict_code character varying(128) NOT NULL,
    dict_label character varying(255) NOT NULL,
    dict_label_en character varying(255),
    sort_order integer DEFAULT 0,
    status character varying(32) DEFAULT 'active'::character varying,
    parent_code character varying(128),
    ext_value character varying(255),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    subsystem character varying(64)
);


--
-- Name: sys_extraction_source; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: sys_token_blacklist; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.sys_token_blacklist (
    token text NOT NULL,
    expire_at bigint NOT NULL
);


--
-- Name: td_abac_policy; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.td_abac_policy (
    policy_id character varying(64) NOT NULL,
    policy_name character varying(128) NOT NULL,
    subject_condition character varying(512),
    resource_condition character varying(512) DEFAULT '*'::character varying,
    action_condition character varying(256) DEFAULT '*'::character varying,
    environment_condition character varying(512),
    effect character varying(16) NOT NULL,
    priority integer DEFAULT 100,
    created_time timestamp without time zone DEFAULT now(),
    scope_type character varying(16) DEFAULT 'GLOBAL'::character varying,
    scope_id character varying(64)
);


--
-- Name: TABLE td_abac_policy; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_abac_policy IS 'ABAC(基于属性的访问控制)策略表';


--
-- Name: COLUMN td_abac_policy.policy_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_abac_policy.policy_id IS '策略唯一标识';


--
-- Name: COLUMN td_abac_policy.policy_name; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_abac_policy.policy_name IS '策略名称';


--
-- Name: COLUMN td_abac_policy.subject_condition; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_abac_policy.subject_condition IS '主体条件 (e.g. role:ROLE_SUPER_ADMIN)';


--
-- Name: COLUMN td_abac_policy.resource_condition; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_abac_policy.resource_condition IS '资源条件 (e.g. dataset:*)';


--
-- Name: COLUMN td_abac_policy.action_condition; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_abac_policy.action_condition IS '动作条件 (e.g. read,write,delete)';


--
-- Name: COLUMN td_abac_policy.environment_condition; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_abac_policy.environment_condition IS '环境条件 (e.g. region == APAC)';


--
-- Name: COLUMN td_abac_policy.effect; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_abac_policy.effect IS '效果: allow(允许) / deny(拒绝)';


--
-- Name: COLUMN td_abac_policy.priority; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_abac_policy.priority IS '优先级 (越小越高)';


--
-- Name: COLUMN td_abac_policy.scope_type; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_abac_policy.scope_type IS '作用域类型: GLOBAL/TENANT/ORG';


--
-- Name: COLUMN td_abac_policy.scope_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_abac_policy.scope_id IS '作用域ID (TENANT→tenant_id, ORG→org_id)';


--
-- Name: td_audit_log; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: TABLE td_audit_log; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_audit_log IS '审计日志表（D-6 补登：schema 锚定单源，首个 schema 限定 CREATE；消费面 = sysman 属主 AuditLog-sql.xml 13 列 + ontology ActionHookAuditDao 8 列子集）';


--
-- Name: COLUMN td_audit_log.log_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_audit_log.log_id IS '日志唯一标识（应用侧生成）';


--
-- Name: COLUMN td_audit_log.event_type; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_audit_log.event_type IS '事件类型: LOGIN/ACCESS/CUD/EXPORT/CONFIG';


--
-- Name: COLUMN td_audit_log.user_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_audit_log.user_id IS '操作用户ID';


--
-- Name: COLUMN td_audit_log.tenant_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_audit_log.tenant_id IS '租户ID';


--
-- Name: COLUMN td_audit_log.resource; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_audit_log.resource IS '操作资源路径';


--
-- Name: COLUMN td_audit_log.action; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_audit_log.action IS '操作动作';


--
-- Name: COLUMN td_audit_log.result; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_audit_log.result IS '操作结果: SUCCESS/FAILURE/DENIED';


--
-- Name: COLUMN td_audit_log.ip_address; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_audit_log.ip_address IS '客户端IP';


--
-- Name: COLUMN td_audit_log.details; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_audit_log.details IS '补充信息(JSON格式)';


--
-- Name: td_catalog_item; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.td_catalog_item (
    catalog_id character varying(64) NOT NULL,
    resource_id character varying(64) NOT NULL,
    resource_name character varying(256) NOT NULL,
    resource_type character varying(32) DEFAULT 'TABLE'::character varying NOT NULL,
    org_name character varying(128),
    description text,
    tags character varying(256),
    category_path character varying(256),
    access_type character varying(32) DEFAULT 'READ'::character varying,
    data_format character varying(32),
    field_count integer DEFAULT 0,
    record_count bigint DEFAULT 0,
    last_updated timestamp without time zone DEFAULT now() NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    tenant_id character varying(64)
);


--
-- Name: TABLE td_catalog_item; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_catalog_item IS '数据目录索引表 — 全局搜索入口';


--
-- Name: td_config; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: TABLE td_config; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_config IS '系统配置表';


--
-- Name: td_data_field; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: TABLE td_data_field; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_data_field IS '数据字段表 — 表的列级元数据';


--
-- Name: td_data_resource; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.td_data_resource (
    resource_id character varying(64) NOT NULL,
    resource_name character varying(256) NOT NULL,
    resource_type character varying(32) DEFAULT 'TABLE'::character varying NOT NULL,
    org_id character varying(64),
    org_name character varying(128),
    datasource_id character varying(64) NOT NULL,
    source_path character varying(512),
    description text,
    tags character varying(256),
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    field_count integer DEFAULT 0,
    record_count bigint DEFAULT 0,
    last_sync_time timestamp without time zone,
    create_by character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_by character varying(64),
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    layer character varying(16) DEFAULT 'RAW'::character varying,
    zone character varying(16),
    layer_bucket character varying(30),
    carrier_ref character varying(255),
    storage_kind character varying(20)
);


--
-- Name: TABLE td_data_resource; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_data_resource IS '数据资源表 — JDBC 发现的 TABLE/VIEW';


--
-- Name: COLUMN td_data_resource.layer; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_data_resource.layer IS 'Data layer: SOURCE/RAW/CURATED/SEMANTIC/APPLICATION';


--
-- Name: COLUMN td_data_resource.zone; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_data_resource.zone IS '近源区: STRUCTURED=结构化近源 / UNSTRUCTURED=非结构化近源 / NULL=非近源层';


--
-- Name: COLUMN td_data_resource.layer_bucket; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_data_resource.layer_bucket IS '载体子类: 数据湖五层(SOURCE/RAW/CURATED/SEMANTIC/APPLICATION)内的载体子分类';


--
-- Name: COLUMN td_data_resource.carrier_ref; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_data_resource.carrier_ref IS '载体标识: 对象 key / 表名 / 视图名 / 图谱 label（跨域只存定位符，铁律 §3.5）';


--
-- Name: COLUMN td_data_resource.storage_kind; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_data_resource.storage_kind IS '存储载体: PG/MINIO/DORIS/CLICKHOUSE/NEO4J/PG_VECTOR（湖规范 v2.0 §七A 载体分档）';


--
-- Name: td_datasource; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.td_datasource (
    datasource_id character varying(64) NOT NULL,
    datasource_name character varying(128) NOT NULL,
    datasource_type character varying(32) DEFAULT 'JDBC'::character varying NOT NULL,
    org_id character varying(64),
    node_id character varying(64),
    description text,
    connection_config text NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
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
-- Name: TABLE td_datasource; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_datasource IS '数据源注册表 — 支持 Oracle/MySQL/PG/SQLServer/达梦/金仓';


--
-- Name: COLUMN td_datasource.metadata_config; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_datasource.metadata_config IS '元数据获取策略配置: {strategy, includeRowCount, countMethod, scheduleCron, cacheTtlMinutes, onSourceEdit}';


--
-- Name: COLUMN td_datasource.last_collect_time; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_datasource.last_collect_time IS '最近一次元数据采集完成时间';


--
-- Name: td_dw_write_channel_idempotency; Type: TABLE; Schema: ecos_control; Owner: -
--

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
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE td_dw_write_channel_idempotency; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_dw_write_channel_idempotency IS 'F02-05 唯一写通道幂等台账 — 同 idempotencyKey 重放不产生第二行（C.3.6）';


--
-- Name: td_metadata_collect_log; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.td_metadata_collect_log (
    id bigint NOT NULL,
    datasource_id character varying(64) NOT NULL,
    count_method character varying(20) DEFAULT 'ESTIMATE'::character varying NOT NULL,
    tables_total integer DEFAULT 0,
    tables_ok integer DEFAULT 0,
    tables_failed integer DEFAULT 0,
    failed_tables text,
    status character varying(20) DEFAULT 'SUCCEEDED'::character varying NOT NULL,
    detail text,
    task_id character varying(64),
    elapsed_ms bigint,
    create_time timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE td_metadata_collect_log; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_metadata_collect_log IS 'PMO-37 元数据采集任务执行审计日志';


--
-- Name: td_metadata_collect_log_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control.td_metadata_collect_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: td_metadata_collect_log_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control.td_metadata_collect_log_id_seq OWNED BY ecos_control.td_metadata_collect_log.id;


--
-- Name: td_org_permission; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.td_org_permission (
    permission_id character varying(64) NOT NULL,
    org_id character varying(64) NOT NULL,
    resource_id character varying(128),
    action character varying(64),
    inherit_from_parent character varying(1) DEFAULT '0'::character varying,
    created_time timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64)
);


--
-- Name: TABLE td_org_permission; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_org_permission IS '组织权限表';


--
-- Name: td_organization; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.td_organization (
    org_id character varying(64) NOT NULL,
    org_name character varying(256),
    org_code character varying(128),
    parent_org_id character varying(64),
    org_type character varying(32),
    description character varying(512),
    status character varying(16) DEFAULT 'ACTIVE'::character varying,
    remark character varying(512),
    tenant_id character varying(64),
    created_time timestamp without time zone DEFAULT now(),
    created_by character varying(64),
    updated_time timestamp without time zone,
    updated_by character varying(64)
);


--
-- Name: td_permission; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: TABLE td_permission; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_permission IS '权限表';


--
-- Name: COLUMN td_permission.permission_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_permission.permission_id IS '权限ID';


--
-- Name: COLUMN td_permission.permission_name; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_permission.permission_name IS '权限名称';


--
-- Name: COLUMN td_permission.permission_code; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_permission.permission_code IS '权限编码';


--
-- Name: COLUMN td_permission.resource_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_permission.resource_id IS '资源ID';


--
-- Name: COLUMN td_permission.action; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_permission.action IS '操作类型';


--
-- Name: COLUMN td_permission.description; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_permission.description IS '描述';


--
-- Name: COLUMN td_permission.tenant_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_permission.tenant_id IS '租户ID';


--
-- Name: td_role; Type: TABLE; Schema: ecos_control; Owner: -
--

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
    status character varying(16) DEFAULT 'ACTIVE'::character varying
);


--
-- Name: TABLE td_role; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_role IS '角色表';


--
-- Name: COLUMN td_role.role_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_role.role_id IS '角色ID';


--
-- Name: COLUMN td_role.role_name; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_role.role_name IS '角色名称';


--
-- Name: COLUMN td_role.role_code; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_role.role_code IS '角色编码';


--
-- Name: COLUMN td_role.description; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_role.description IS '描述';


--
-- Name: COLUMN td_role.parent_role_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_role.parent_role_id IS '父角色ID';


--
-- Name: COLUMN td_role.tenant_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_role.tenant_id IS '租户ID';


--
-- Name: COLUMN td_role.created_time; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_role.created_time IS '创建时间';


--
-- Name: COLUMN td_role.created_by; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_role.created_by IS '创建人';


--
-- Name: COLUMN td_role.updated_time; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_role.updated_time IS '更新时间';


--
-- Name: COLUMN td_role.updated_by; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_role.updated_by IS '更新人';


--
-- Name: td_role_permission; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.td_role_permission (
    role_id character varying(64) NOT NULL,
    permission_id character varying(64) NOT NULL,
    created_time timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64)
);


--
-- Name: TABLE td_role_permission; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_role_permission IS '角色权限关联表';


--
-- Name: td_role_security_profile; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.td_role_security_profile (
    role_id character varying(64) NOT NULL,
    clearance_level integer DEFAULT 0,
    linked_workstation character varying(256),
    audit_mode character varying(32) DEFAULT 'basic'::character varying,
    sandbox_mandatory boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    tenant_id character varying(64),
    org_id character varying(64),
    scope_type character varying(16) DEFAULT 'ROLE'::character varying
);


--
-- Name: TABLE td_role_security_profile; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_role_security_profile IS '角色级安全配置表 — 优先级次于用户级，高于全局默认';


--
-- Name: COLUMN td_role_security_profile.role_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_role_security_profile.role_id IS '角色ID，关联 td_role.ROLE_ID';


--
-- Name: COLUMN td_role_security_profile.clearance_level; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_role_security_profile.clearance_level IS '准入等级 0-4: L0公开 L1内部 L2保密 L3机密 L4绝密';


--
-- Name: COLUMN td_role_security_profile.linked_workstation; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_role_security_profile.linked_workstation IS '物理工作站绑定（MAC/IP/主机名）';


--
-- Name: COLUMN td_role_security_profile.audit_mode; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_role_security_profile.audit_mode IS '审计力度: basic(基本) detailed(详细) full(全量)';


--
-- Name: COLUMN td_role_security_profile.sandbox_mandatory; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_role_security_profile.sandbox_mandatory IS '是否强制沙盒运行';


--
-- Name: COLUMN td_role_security_profile.tenant_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_role_security_profile.tenant_id IS '租户ID (scope_type=TENANT时填充)';


--
-- Name: COLUMN td_role_security_profile.org_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_role_security_profile.org_id IS '机构ID (scope_type=ORG时填充)';


--
-- Name: COLUMN td_role_security_profile.scope_type; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_role_security_profile.scope_type IS '作用域类型: GLOBAL/TENANT/ORG/ROLE/USER';


--
-- Name: td_runtime_task; Type: TABLE; Schema: ecos_control; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


--
-- Name: TABLE td_runtime_task; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_runtime_task IS 'runtime-task 即时/定时任务描述主表（task_id 主键, 含审计五字段 + domain + version_no）';


--
-- Name: td_runtime_task_execution; Type: TABLE; Schema: ecos_control; Owner: -
--

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
-- Name: TABLE td_runtime_task_execution; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_runtime_task_execution IS 'runtime-task 执行记录（recordExecution 唯一写面；列清单单源 = TaskPersistenceDao:322-329）';


--
-- Name: td_runtime_task_log; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.td_runtime_task_log (
    "LOG_SEQ" bigint NOT NULL,
    "EXECUTION_ID" character varying(64),
    "TASK_ID" character varying(64),
    "LOG_LEVEL" character varying(16),
    "MESSAGE" text,
    "CREATED_TIME" timestamp without time zone
);


--
-- Name: TABLE td_runtime_task_log; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_runtime_task_log IS 'runtime-task 任务日志（logTask 唯一写面；无软删/无唯一键 ⇒ PK 走 BIGSERIAL，登记 D5）';


--
-- Name: td_runtime_task_log_LOG_SEQ_seq; Type: SEQUENCE; Schema: ecos_control; Owner: -
--

CREATE SEQUENCE ecos_control."td_runtime_task_log_LOG_SEQ_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: td_runtime_task_log_LOG_SEQ_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: -
--

ALTER SEQUENCE ecos_control."td_runtime_task_log_LOG_SEQ_seq" OWNED BY ecos_control.td_runtime_task_log."LOG_SEQ";


--
-- Name: td_runtime_task_plan; Type: TABLE; Schema: ecos_control; Owner: -
--

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
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


--
-- Name: TABLE td_runtime_task_plan; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_runtime_task_plan IS 'runtime-task 执行计划主表（plan_content 持久化 + 定时四要素：cron/next_run_at/last_run_at/last_status）';


--
-- Name: td_runtime_task_status; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.td_runtime_task_status (
    "TASK_ID" character varying(64) NOT NULL,
    "STATUS" character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
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
-- Name: TABLE td_runtime_task_status; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_runtime_task_status IS 'runtime-task 运行状态表（列清单单源 = TaskPersistenceDao.saveStatus/queryStatus 16 列 + softDeleteTask 的 is_deleted；V152 头注"保持"但从未 CREATE，本脚本补建）';


--
-- Name: td_user; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.td_user (
    user_id character varying(64) NOT NULL,
    username character varying(128) NOT NULL,
    password character varying(512) NOT NULL,
    email character varying(256),
    mobile_tel1 character varying(32),
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    locked character varying(1) DEFAULT '0'::character varying NOT NULL,
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
    login_count integer DEFAULT 0 NOT NULL
);


--
-- Name: TABLE td_user; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_user IS '用户表';


--
-- Name: COLUMN td_user.user_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.user_id IS '用户ID';


--
-- Name: COLUMN td_user.username; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.username IS '用户名';


--
-- Name: COLUMN td_user.password; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.password IS '密码哈希';


--
-- Name: COLUMN td_user.email; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.email IS '邮箱';


--
-- Name: COLUMN td_user.mobile_tel1; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.mobile_tel1 IS '手机号';


--
-- Name: COLUMN td_user.status; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.status IS '状态: ACTIVE/LOCKED/DISABLED';


--
-- Name: COLUMN td_user.locked; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.locked IS '是否锁定: 0/1';


--
-- Name: COLUMN td_user.lock_time; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.lock_time IS '锁定时间';


--
-- Name: COLUMN td_user.last_login_time; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.last_login_time IS '最后登录时间';


--
-- Name: COLUMN td_user.mfa_secret; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.mfa_secret IS 'MFA密钥';


--
-- Name: COLUMN td_user.mfa_type; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.mfa_type IS 'MFA类型';


--
-- Name: COLUMN td_user.mfa_enabled; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.mfa_enabled IS '是否启用MFA';


--
-- Name: COLUMN td_user.tenant_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.tenant_id IS '租户ID';


--
-- Name: COLUMN td_user.created_time; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.created_time IS '创建时间';


--
-- Name: COLUMN td_user.created_by; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.created_by IS '创建人';


--
-- Name: COLUMN td_user.updated_time; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.updated_time IS '更新时间';


--
-- Name: COLUMN td_user.updated_by; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.updated_by IS '更新人';


--
-- Name: COLUMN td_user.real_name; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.real_name IS '人员姓名（User-sql.xml queryUsers 按名引用；演示数据 seed 回填，非凭据面）';


--
-- Name: COLUMN td_user.login_ip; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.login_ip IS '最近登录 IP（运行结果面，seed 恒空，禁伪造演示值）';


--
-- Name: COLUMN td_user.login_count; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user.login_count IS '登录次数（运行结果面，seed 恒 0，禁伪造演示值）';


--
-- Name: td_user_organization; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.td_user_organization (
    user_id character varying(64) NOT NULL,
    org_id character varying(64) NOT NULL,
    is_primary character varying(1) DEFAULT '1'::character varying,
    created_time timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64)
);


--
-- Name: td_user_role; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.td_user_role (
    user_id character varying(64) NOT NULL,
    role_id character varying(64) NOT NULL,
    org_id character varying(64) DEFAULT '-1'::character varying,
    created_time timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64)
);


--
-- Name: TABLE td_user_role; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_user_role IS '用户角色关联表';


--
-- Name: COLUMN td_user_role.user_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user_role.user_id IS '用户ID';


--
-- Name: COLUMN td_user_role.role_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user_role.role_id IS '角色ID';


--
-- Name: COLUMN td_user_role.org_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user_role.org_id IS '组织ID';


--
-- Name: td_user_security_profile; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.td_user_security_profile (
    user_id character varying(64) NOT NULL,
    clearance_level integer DEFAULT 0,
    linked_workstation character varying(256),
    audit_mode character varying(32) DEFAULT 'basic'::character varying,
    sandbox_mandatory boolean DEFAULT false,
    is_default boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    tenant_id character varying(64),
    org_id character varying(64),
    scope_type character varying(16) DEFAULT 'USER'::character varying
);


--
-- Name: TABLE td_user_security_profile; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.td_user_security_profile IS '用户级安全配置表 — 优先级最高，覆盖角色级和全局默认';


--
-- Name: COLUMN td_user_security_profile.user_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user_security_profile.user_id IS '用户ID，关联 td_user.USER_ID';


--
-- Name: COLUMN td_user_security_profile.clearance_level; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user_security_profile.clearance_level IS '准入等级 0-4: L0公开 L1内部 L2保密 L3机密 L4绝密';


--
-- Name: COLUMN td_user_security_profile.linked_workstation; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user_security_profile.linked_workstation IS '物理工作站绑定（MAC/IP/主机名）';


--
-- Name: COLUMN td_user_security_profile.audit_mode; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user_security_profile.audit_mode IS '审计力度: basic(基本) detailed(详细) full(全量)';


--
-- Name: COLUMN td_user_security_profile.sandbox_mandatory; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user_security_profile.sandbox_mandatory IS '是否强制沙盒运行';


--
-- Name: COLUMN td_user_security_profile.is_default; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user_security_profile.is_default IS '是否为全局默认配置（仅允许一条记录为TRUE）';


--
-- Name: COLUMN td_user_security_profile.tenant_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user_security_profile.tenant_id IS '租户ID (scope_type=TENANT时填充)';


--
-- Name: COLUMN td_user_security_profile.org_id; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user_security_profile.org_id IS '机构ID (scope_type=ORG时填充)';


--
-- Name: COLUMN td_user_security_profile.scope_type; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON COLUMN ecos_control.td_user_security_profile.scope_type IS '作用域类型: GLOBAL/TENANT/ORG/ROLE/USER';


--
-- Name: users; Type: TABLE; Schema: ecos_control; Owner: -
--

CREATE TABLE ecos_control.users (
    id character varying(64) NOT NULL,
    username character varying(128) NOT NULL,
    password_hash character varying(512) NOT NULL,
    display_name character varying(256),
    roles text,
    enabled boolean DEFAULT true,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    failed_attempts integer DEFAULT 0,
    locked_until timestamp without time zone,
    password_change_required boolean DEFAULT true,
    last_password_change timestamp without time zone DEFAULT now(),
    password_history text DEFAULT '[]'::text
);


--
-- Name: TABLE users; Type: COMMENT; Schema: ecos_control; Owner: -
--

COMMENT ON TABLE ecos_control.users IS '简化用户表(认证用)';


--
-- Name: dq_alert_record; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.dq_alert_record (
    id character varying(36) NOT NULL,
    rule_id character varying(64) NOT NULL,
    alert_level character varying(4) NOT NULL,
    alert_type character varying(32) NOT NULL,
    asset_id character varying(64),
    asset_name character varying(191),
    rule_name character varying(255),
    message text NOT NULL,
    payload jsonb,
    status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    escalated_to character varying(4),
    notify_count integer DEFAULT 0 NOT NULL,
    last_notify_at timestamp without time zone,
    notify_channels jsonb,
    ack_by character varying(128),
    ack_at timestamp without time zone,
    resolved_by character varying(128),
    resolved_at timestamp without time zone,
    resolved_note text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE dq_alert_record; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.dq_alert_record IS '告警事件记录 (P0-P3 分级, 升级链路)';


--
-- Name: COLUMN dq_alert_record.status; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_alert_record.status IS '告警状态: PENDING / NOTIFIED / ACKED / RESOLVED / IGNORED / ESCALATED';


--
-- Name: COLUMN dq_alert_record.escalated_to; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_alert_record.escalated_to IS '升级到的告警级别 (如 P1)';


--
-- Name: dq_knowledge_entry; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.dq_knowledge_entry (
    id character varying(36) NOT NULL,
    source_type character varying(16) NOT NULL,
    source_id character varying(64) NOT NULL,
    category character varying(32),
    title character varying(191) NOT NULL,
    summary text,
    content_md text,
    entity_json jsonb DEFAULT '{}'::jsonb,
    embedding text,
    score_hint double precision,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE dq_knowledge_entry; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.dq_knowledge_entry IS 'DQ 知识条目（PMO-48-D T16，规则/告警/工单沉淀，供 RCA + RAG 相似检索）';


--
-- Name: COLUMN dq_knowledge_entry.id; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_knowledge_entry.id IS '知识条目 ID (VARCHAR(36))';


--
-- Name: COLUMN dq_knowledge_entry.source_type; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_knowledge_entry.source_type IS '来源类型: RULE/ALERT/WORK_ORDER';


--
-- Name: COLUMN dq_knowledge_entry.source_id; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_knowledge_entry.source_id IS '来源记录 ID（对应 dq_rule.id / dq_alert_record.id / dq_work_order.id）';


--
-- Name: COLUMN dq_knowledge_entry.category; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_knowledge_entry.category IS '分类: rule-hit/alert-pattern/fix-pattern';


--
-- Name: COLUMN dq_knowledge_entry.title; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_knowledge_entry.title IS '知识条目标题 (<= 191)';


--
-- Name: COLUMN dq_knowledge_entry.summary; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_knowledge_entry.summary IS '摘要（供列表展示）';


--
-- Name: COLUMN dq_knowledge_entry.content_md; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_knowledge_entry.content_md IS '知识全文 Markdown';


--
-- Name: COLUMN dq_knowledge_entry.entity_json; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_knowledge_entry.entity_json IS '结构化实体 JSONB (ruleName/assetId/ruleType 等)';


--
-- Name: COLUMN dq_knowledge_entry.embedding; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_knowledge_entry.embedding IS '向量列（pgvector 未装，本版本降级 TEXT 存 JSON 数组字符串 DIM=1024）';


--
-- Name: COLUMN dq_knowledge_entry.score_hint; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_knowledge_entry.score_hint IS '预计算相似分参考 (0.0-1.0)';


--
-- Name: dq_report; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.dq_report (
    id character varying(36) NOT NULL,
    report_type character varying(16) NOT NULL,
    scope character varying(32) DEFAULT 'ALL'::character varying NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    payload_html text NOT NULL,
    pdf_object_key character varying(512),
    llm_summary text,
    row_count integer DEFAULT 0,
    alert_count integer DEFAULT 0,
    avg_score double precision,
    created_by character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE dq_report; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.dq_report IS 'DQ 报告（PMO-48-D T15，日报/周报/月报，HTML+PDF，AI 摘要）';


--
-- Name: COLUMN dq_report.id; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_report.id IS '报告 ID (VARCHAR(36) 主键)';


--
-- Name: COLUMN dq_report.report_type; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_report.report_type IS '报告类型: DAILY/WEEKLY/MONTHLY';


--
-- Name: COLUMN dq_report.scope; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_report.scope IS '报告范围: ALL/NATIVE/OFI/DOMAIN:xxx';


--
-- Name: COLUMN dq_report.start_date; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_report.start_date IS '报告起始日期（含）';


--
-- Name: COLUMN dq_report.end_date; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_report.end_date IS '报告截止日期（含）';


--
-- Name: COLUMN dq_report.payload_html; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_report.payload_html IS '内嵌 HTML（小文件 <= 200KB，直接存 TEXT）';


--
-- Name: COLUMN dq_report.pdf_object_key; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_report.pdf_object_key IS 'MinIO bucket=dq-reports 内 object key（PDF > 200KB 走对象存储）';


--
-- Name: COLUMN dq_report.llm_summary; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_report.llm_summary IS 'LLM 生成摘要（PMO-48-D Phase 5 接 cognitive-engine 后非空）';


--
-- Name: COLUMN dq_report.row_count; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_report.row_count IS '报告覆盖的数据行数';


--
-- Name: COLUMN dq_report.alert_count; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_report.alert_count IS '报告覆盖的告警位数';


--
-- Name: COLUMN dq_report.avg_score; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_report.avg_score IS '报告覆盖范围内平均分 0.0-1.0';


--
-- Name: dq_rule; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.dq_rule (
    id character varying(64) NOT NULL,
    rule_name character varying(255) NOT NULL,
    rule_code character varying(191),
    category character varying(32) NOT NULL,
    domain character varying(128),
    rule_type character varying(64) NOT NULL,
    severity character varying(16) DEFAULT 'MEDIUM'::character varying NOT NULL,
    target_kind character varying(32) NOT NULL,
    target_id character varying(64),
    target_table character varying(191),
    target_field character varying(191),
    target_pipeline_id character varying(64),
    parameters jsonb DEFAULT '{}'::jsonb NOT NULL,
    status character varying(32) DEFAULT 'DRAFT'::character varying NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    approved_by character varying(128),
    effective_date bigint,
    expiry_date bigint,
    source_type character varying(32) DEFAULT 'MANUAL'::character varying NOT NULL,
    source_ref character varying(64),
    description text DEFAULT ''::text,
    created_by character varying(128),
    updated_by character varying(128),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    deleted_at timestamp without time zone,
    is_deleted boolean DEFAULT false NOT NULL,
    approved_at timestamp without time zone,
    applied_at timestamp without time zone,
    legacy_rule_id character varying(36),
    parameters_json text,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL
);


--
-- Name: TABLE dq_rule; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.dq_rule IS 'DQ 规则主表 (双轨合并后的统一规则表, PMO-48-A T1 + PMO-48-B V112)';


--
-- Name: COLUMN dq_rule.category; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_rule.category IS '规则类别: BUSINESS / TECHNICAL / COMPLIANCE';


--
-- Name: COLUMN dq_rule.status; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_rule.status IS '规则状态: DRAFT / IN_REVIEW / ACTIVE / DEPRECATED / SUPERSEDED / REJECTED';


--
-- Name: COLUMN dq_rule.source_type; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_rule.source_type IS '规则来源: MANUAL / LEGACY_V2 / LEGACY_QUALITY / KB_SYNC';


--
-- Name: COLUMN dq_rule.source_ref; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_rule.source_ref IS '来源追溯 (legacy 表:原始行id)';


--
-- Name: COLUMN dq_rule.approved_at; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_rule.approved_at IS '审批通过时间 (submit → approve: IN_REVIEW → ACTIVE 切换)';


--
-- Name: COLUMN dq_rule.applied_at; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_rule.applied_at IS '正式生效时间 (approve → ACTIVE 切换时设置, 应用方使用)';


--
-- Name: dq_rule_check; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.dq_rule_check (
    id character varying(36) NOT NULL,
    rule_id character varying(64) NOT NULL,
    schedule_id character varying(64),
    trigger_type character varying(16) NOT NULL,
    executed_at timestamp without time zone DEFAULT now() NOT NULL,
    passed boolean DEFAULT false NOT NULL,
    total_rows bigint DEFAULT 0,
    failed_rows bigint DEFAULT 0,
    pass_rate double precision,
    latency_ms integer,
    error_message text,
    sample_size integer,
    sample_failures jsonb
);


--
-- Name: TABLE dq_rule_check; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.dq_rule_check IS '规则执行记录 (调度/事件/手动触发)';


--
-- Name: COLUMN dq_rule_check.trigger_type; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_rule_check.trigger_type IS '触发类型: SCHEDULE / EVENT / MANUAL';


--
-- Name: dq_rule_version; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.dq_rule_version (
    id character varying(64) NOT NULL,
    rule_id character varying(64) NOT NULL,
    version_number integer NOT NULL,
    snapshot jsonb NOT NULL,
    changed_by character varying(128),
    changed_at timestamp without time zone DEFAULT now() NOT NULL,
    change_note text
);


--
-- Name: TABLE dq_rule_version; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.dq_rule_version IS '规则版本快照 (每次修改存档, 审计追溯)';


--
-- Name: dq_schedule; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.dq_schedule (
    id character varying(64) NOT NULL,
    name character varying(191) NOT NULL,
    trigger_type character varying(16) NOT NULL,
    cron_expression character varying(64),
    event_type character varying(64),
    rule_ids jsonb DEFAULT '[]'::jsonb NOT NULL,
    scope_type character varying(16) DEFAULT 'DATASOURCE'::character varying,
    scope_id character varying(64),
    enabled boolean DEFAULT true NOT NULL,
    max_runtime_seconds integer DEFAULT 60,
    created_by character varying(128),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL
);


--
-- Name: TABLE dq_schedule; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.dq_schedule IS 'DQ 监控调度计划（PMO-48-C T11，接 runtime-task）';


--
-- Name: COLUMN dq_schedule.id; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_schedule.id IS '调度 ID (VARCHAR(64) PK)';


--
-- Name: COLUMN dq_schedule.trigger_type; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_schedule.trigger_type IS '触发类型: SCHEDULE=定时 / EVENT=Pipeline事件 / MANUAL=仅手动';


--
-- Name: COLUMN dq_schedule.cron_expression; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_schedule.cron_expression IS 'cron 表达式 (trigger_type=SCHEDULE 时必填)';


--
-- Name: COLUMN dq_schedule.event_type; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_schedule.event_type IS '事件类型常量 (trigger_type=EVENT 时必填, 如 PIPELINE_EXECUTION_SUCCEEDED)';


--
-- Name: COLUMN dq_schedule.rule_ids; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_schedule.rule_ids IS '关联规则 ID 列表 JSONB ["ruleId1","ruleId2"]';


--
-- Name: COLUMN dq_schedule.scope_type; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_schedule.scope_type IS '限流作用域类型: FIELD/TABLE/DATASOURCE/DOMAIN/SYSTEM (缺省 DATASOURCE)';


--
-- Name: COLUMN dq_schedule.scope_id; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_schedule.scope_id IS '限流作用域 ID (与 dq_throttle.scope_id 对齐)';


--
-- Name: COLUMN dq_schedule.enabled; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_schedule.enabled IS '启用开关 (FALSE 时调度器/EVENT 监听跳过)';


--
-- Name: COLUMN dq_schedule.max_runtime_seconds; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_schedule.max_runtime_seconds IS '单次执行超时秒数 (超时记 TIMEOUT 不中断)';


--
-- Name: dq_score_asset; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.dq_score_asset (
    asset_type character varying(16) NOT NULL,
    asset_id character varying(64) NOT NULL,
    overall_score double precision NOT NULL,
    rolled_up_scores jsonb DEFAULT '[]'::jsonb NOT NULL,
    last_evaluated_at timestamp without time zone DEFAULT now() NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL
);


--
-- Name: TABLE dq_score_asset; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.dq_score_asset IS 'DQ 资产级评分 (每资产 6 维加权汇总, PMO-48-B T8 评分引擎写入)';


--
-- Name: COLUMN dq_score_asset.asset_type; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_score_asset.asset_type IS '资产类型: DATASOURCE / TABLE (预留 FIELD/DOMAIN/SYSTEM 扩展)';


--
-- Name: COLUMN dq_score_asset.asset_id; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_score_asset.asset_id IS '资产唯一 id (DATASOURCE 时为 datasource_id; TABLE 时为 table_qualified_name)';


--
-- Name: COLUMN dq_score_asset.overall_score; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_score_asset.overall_score IS '加权汇总分, 0.0 - 1.0';


--
-- Name: COLUMN dq_score_asset.rolled_up_scores; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_score_asset.rolled_up_scores IS '各维度明细 JSON: [{dimension, score, weight}, ...]';


--
-- Name: COLUMN dq_score_asset.last_evaluated_at; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_score_asset.last_evaluated_at IS '最后一次评估时间';


--
-- Name: COLUMN dq_score_asset.is_deleted; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_score_asset.is_deleted IS '逻辑删除标记 (TRUE=软删, 列表过滤)';


--
-- Name: dq_score_snapshot; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.dq_score_snapshot (
    id bigint NOT NULL,
    dimension character varying(16) NOT NULL,
    scope_type character varying(16) NOT NULL,
    scope_id character varying(64) NOT NULL,
    score_value double precision NOT NULL,
    weight double precision DEFAULT 1.0 NOT NULL,
    sample_size integer,
    distinct_rule_count integer DEFAULT 0 NOT NULL,
    evaluated_at timestamp without time zone DEFAULT now() NOT NULL,
    metadata jsonb
);


--
-- Name: TABLE dq_score_snapshot; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.dq_score_snapshot IS 'DQ 6 维度评分快照 (PMO-48-B T8 评分引擎写入)';


--
-- Name: COLUMN dq_score_snapshot.dimension; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_score_snapshot.dimension IS '评估维度: ACCURACY/CONSISTENCY/FRESHNESS/UNIQUENESS/VALIDITY/COMPLETENESS';


--
-- Name: COLUMN dq_score_snapshot.scope_type; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_score_snapshot.scope_type IS '范围类型: FIELD/TABLE/DATASOURCE/DOMAIN/SYSTEM';


--
-- Name: COLUMN dq_score_snapshot.scope_id; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_score_snapshot.scope_id IS '范围实体 id (与 scope_type 匹配, TABLE 时为 datasource:table, FIELD 时为 datasource:table:field)';


--
-- Name: COLUMN dq_score_snapshot.score_value; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_score_snapshot.score_value IS '该次评估的维度分, 0.0 - 1.0 规范化 (PASS_RATE)';


--
-- Name: COLUMN dq_score_snapshot.weight; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_score_snapshot.weight IS '该维度在资产级汇总中的权重 (T8 按目标设置, 默认 1.0)';


--
-- Name: COLUMN dq_score_snapshot.sample_size; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_score_snapshot.sample_size IS '本次评估使用的样本量 (T8 用于诊断小样本偏差)';


--
-- Name: COLUMN dq_score_snapshot.distinct_rule_count; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_score_snapshot.distinct_rule_count IS '本次评估命中的规则数 (去重统计)';


--
-- Name: COLUMN dq_score_snapshot.metadata; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_score_snapshot.metadata IS '评估上下文 (sampleStats / threshold / relatedAlertId 等)';


--
-- Name: dq_score_snapshot_id_seq; Type: SEQUENCE; Schema: ecos_data; Owner: -
--

CREATE SEQUENCE ecos_data.dq_score_snapshot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dq_score_snapshot_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_data; Owner: -
--

ALTER SEQUENCE ecos_data.dq_score_snapshot_id_seq OWNED BY ecos_data.dq_score_snapshot.id;


--
-- Name: dq_throttle; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.dq_throttle (
    id character varying(64) NOT NULL,
    scope_type character varying(16) NOT NULL,
    scope_id character varying(64) NOT NULL,
    max_checks_per_day integer DEFAULT 100 NOT NULL,
    current_count integer DEFAULT 0 NOT NULL,
    reset_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE dq_throttle; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.dq_throttle IS 'DQ 监控限流（每资产/天最大检查数，防雪崩, PMO-48-C T11）';


--
-- Name: COLUMN dq_throttle.max_checks_per_day; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_throttle.max_checks_per_day IS '每日检查上限 (超过则 runRuleBatch 跳过并记 SKIP 日志)';


--
-- Name: COLUMN dq_throttle.current_count; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_throttle.current_count IS '当日内已执行检查数 (跨日自动重置)';


--
-- Name: COLUMN dq_throttle.reset_at; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_throttle.reset_at IS 'current_count 重置时点 (跨日判定)';


--
-- Name: dq_work_order; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.dq_work_order (
    id character varying(36) NOT NULL,
    order_no character varying(64) NOT NULL,
    alert_id character varying(36) NOT NULL,
    rule_id character varying(64) NOT NULL,
    asset_id character varying(64),
    title character varying(255) NOT NULL,
    description text,
    status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    handling_mode character varying(16) DEFAULT 'MANUAL'::character varying NOT NULL,
    severity character varying(16),
    assigned_to character varying(128),
    assigned_at timestamp without time zone,
    repair_action character varying(64),
    repair_status character varying(16),
    repair_log jsonb,
    verified_by character varying(128),
    verified_at timestamp without time zone,
    verify_pass boolean,
    verify_note text,
    resolved_by character varying(128),
    resolved_at timestamp without time zone,
    resolution_note text,
    preventive_actions jsonb,
    rca_result jsonb,
    rca_confidence double precision,
    rca_analyzed_at timestamp without time zone,
    retry_count integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    closed_at timestamp without time zone,
    is_deleted boolean DEFAULT false NOT NULL,
    reject_note text
);


--
-- Name: TABLE dq_work_order; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.dq_work_order IS '工单 (告警→处理→验证→关闭, 溯源 RCA)';


--
-- Name: COLUMN dq_work_order.status; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_work_order.status IS '工单状态: PENDING / ASSIGNED / IN_WORK / RESOLVED / VERIFIED / CLOSED / REJECTED';


--
-- Name: COLUMN dq_work_order.handling_mode; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_work_order.handling_mode IS '处理模式: MANUAL / AUTO_REPAIR / EXEMPT';


--
-- Name: COLUMN dq_work_order.reject_note; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.dq_work_order.reject_note IS '驳回原因（PMO-48-D T17，Phase 3 原文拼入 description，Phase 4 独立列）';


--
-- Name: ecos_data_asset; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.ecos_data_asset (
    asset_id character varying(36) NOT NULL,
    resource_id character varying(64) NOT NULL,
    asset_name character varying(256) NOT NULL,
    business_desc text,
    owner character varying(100),
    owner_org character varying(128),
    data_grain character varying(64),
    category_id character varying(36),
    sensitivity_level character varying(20) DEFAULT 'L1'::character varying,
    category_status character varying(20) DEFAULT 'PENDING'::character varying,
    last_tagged_by character varying(100),
    last_tagged_at timestamp without time zone,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE ecos_data_asset; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.ecos_data_asset IS '数据资产（业务视图）— PMO-data10 资产 CRUD / 分级 / 分类主表';


--
-- Name: ecos_data_asset_field; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.ecos_data_asset_field (
    field_asset_id character varying(36) NOT NULL,
    asset_id character varying(36) NOT NULL,
    field_id character varying(64) NOT NULL,
    field_name character varying(256) NOT NULL,
    field_type character varying(64),
    data_type character varying(64) DEFAULT 'GENERAL'::character varying,
    field_sensitivity character varying(20) DEFAULT 'L1'::character varying,
    mask_strategy character varying(50) DEFAULT 'none'::character varying,
    recommend_level character varying(20),
    recommend_source character varying(50),
    confirmed boolean DEFAULT false,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE ecos_data_asset_field; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.ecos_data_asset_field IS '资产-字段级敏感度 — 参考 td_data_field；mask_strategy: none/middle4/prefix3/suffix4/full';


--
-- Name: ecos_data_category_tree; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.ecos_data_category_tree (
    category_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    parent_id character varying(36),
    level smallint DEFAULT 1,
    description text,
    icon character varying(50),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    sort_order integer DEFAULT 0
);


--
-- Name: TABLE ecos_data_category_tree; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.ecos_data_category_tree IS '业务分类树（最多 3 级，资产/目录主用）';


--
-- Name: ecos_data_governance_candidate; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.ecos_data_governance_candidate (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    candidate_type character varying(30) NOT NULL,
    target_ref character varying(200) NOT NULL,
    proposal_text text NOT NULL,
    evidence_refs_text text,
    confidence_value numeric(6,5),
    confidence_kind character varying(50),
    calibrated smallint,
    assumptions_text text,
    conflicts_text text,
    impact_text text,
    preview_pass_rate numeric(6,5),
    rollback_plan_text text,
    status character varying(24) DEFAULT 'GENERATED'::character varying NOT NULL,
    tool_contract_ref character varying(100),
    approval_ref character varying(100),
    source_run_id character varying(36),
    trace_id character varying(64),
    idempotency_key character varying(100),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_data_governance_candidate; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.ecos_data_governance_candidate IS '数据治理 AI 候选（补充设计 v1.0 §7）：候选态载体；正式写入仍经 data-engine 写端点/审批（§7.1）；GENERATED/VALIDATED≠APPROVED；预览只读不改变正式资产（SUP-DATA-01/02）';


--
-- Name: ecos_data_level_def; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.ecos_data_level_def (
    level_code character varying(20) NOT NULL,
    level_value integer NOT NULL,
    level_name character varying(100) NOT NULL,
    level_description text,
    mask_strategy_json jsonb DEFAULT '{"type": "none"}'::jsonb,
    rls_strategy_json jsonb DEFAULT '{"type": "no_filter"}'::jsonb,
    sort_order integer DEFAULT 0,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE ecos_data_level_def; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.ecos_data_level_def IS '数据分级字典（L1..L4）— data-engine 唯一权威（替代已 deprecated 的 DataClassificationServiceImpl 内存 4 级）';


--
-- Name: ecos_data_lineage_edge; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.ecos_data_lineage_edge (
    id character varying(64) NOT NULL,
    source_node_id character varying(64) NOT NULL,
    target_node_id character varying(64) NOT NULL,
    edge_type character varying(30) NOT NULL,
    pipeline_task_id character varying(64),
    transformation character varying(500),
    properties jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


--
-- Name: TABLE ecos_data_lineage_edge; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.ecos_data_lineage_edge IS 'Data lineage edge — data flow between nodes';


--
-- Name: COLUMN ecos_data_lineage_edge.edge_type; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.ecos_data_lineage_edge.edge_type IS 'DATA_FLOW / DERIVATION / DEPENDENCY';


--
-- Name: COLUMN ecos_data_lineage_edge.pipeline_task_id; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.ecos_data_lineage_edge.pipeline_task_id IS 'Associated pipeline task that creates this data flow';


--
-- Name: ecos_data_lineage_node; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.ecos_data_lineage_node (
    id character varying(64) NOT NULL,
    node_type character varying(20) NOT NULL,
    name character varying(200) NOT NULL,
    schema_name character varying(100),
    table_name character varying(200),
    datasource_id character varying(64),
    layer character varying(20),
    properties jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    last_incremental_sync_at timestamp without time zone,
    pipeline_task_id character varying(64)
);


--
-- Name: TABLE ecos_data_lineage_node; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.ecos_data_lineage_node IS 'Data lineage node — represents a data source, target, or transform point';


--
-- Name: COLUMN ecos_data_lineage_node.node_type; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.ecos_data_lineage_node.node_type IS 'SOURCE / TARGET / TRANSFORM';


--
-- Name: COLUMN ecos_data_lineage_node.layer; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.ecos_data_lineage_node.layer IS 'SOURCE / RAW / CURATED / SEMANTIC / APPLICATION';


--
-- Name: COLUMN ecos_data_lineage_node.last_incremental_sync_at; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.ecos_data_lineage_node.last_incremental_sync_at IS '增量重建水位线：/api/v1/engine/data/lineage/topology/rebuild 增量模式执行完成后写入';


--
-- Name: COLUMN ecos_data_lineage_node.pipeline_task_id; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.ecos_data_lineage_node.pipeline_task_id IS '来源 pipeline definition/task ID';


--
-- Name: ecos_data_period_status; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.ecos_data_period_status (
    id character varying(36) NOT NULL,
    period character varying(7) NOT NULL,
    status character varying(10) DEFAULT 'OPEN'::character varying NOT NULL,
    closed_by character varying(64),
    closed_at timestamp without time zone,
    close_reason character varying(500),
    close_detail_json text,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);


--
-- Name: TABLE ecos_data_period_status; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.ecos_data_period_status IS '期间关账状态表（卷09 H-3：控制域真身；关账=OPEN→CLOSED 单向；历史事实行永不回写；降级路径=配置期锁 ecos.data.facts.closed-periods）';


--
-- Name: COLUMN ecos_data_period_status.period; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.ecos_data_period_status.period IS '期间键 YYYY-MM（与 PeriodLock 端口入参同形）';


--
-- Name: COLUMN ecos_data_period_status.status; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.ecos_data_period_status.status IS 'OPEN/CLOSED（应用侧枚举；CLOSED 后导入链对该期间 409 ECOS-DATA-022）';


--
-- Name: COLUMN ecos_data_period_status.closed_by; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.ecos_data_period_status.closed_by IS '关账操作者（security-engine OPA 财务角色裁决通过后写入；禁硬编码角色映射）';


--
-- Name: COLUMN ecos_data_period_status.close_detail_json; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.ecos_data_period_status.close_detail_json IS '关账附加上下文序列化 TEXT（受控 JSON，仅描述用，禁 WHERE/JOIN/索引，MC02）';


--
-- Name: ecos_dq_legacy_migration_report; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.ecos_dq_legacy_migration_report (
    id character varying(36) NOT NULL,
    migration_batch character varying(64) DEFAULT 'V166'::character varying NOT NULL,
    legacy_rule_id character varying(36) NOT NULL,
    legacy_rule_name character varying(255),
    outcome character varying(32) NOT NULL,
    reason text,
    migrated_rule_id character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


--
-- Name: ecos_dq_score_asset; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.ecos_dq_score_asset (
    id character varying(36) NOT NULL,
    asset_type character varying(20) NOT NULL,
    asset_id character varying(64) NOT NULL,
    overall_score numeric(5,2) NOT NULL,
    grade character varying(4) NOT NULL,
    dimensions_json text,
    snapshot_at timestamp without time zone NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100)
);


--
-- Name: ecos_pipeline_definition; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.ecos_pipeline_definition (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    definition jsonb DEFAULT '{}'::jsonb,
    status character varying(32) DEFAULT 'DRAFT'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    definition_json text,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20)
);


--
-- Name: TABLE ecos_pipeline_definition; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.ecos_pipeline_definition IS '管道定义表（JSON DAG 唯一承流模型，R-2 a）— 单源补写 V170';


--
-- Name: ecos_pipeline_execution; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.ecos_pipeline_execution (
    id character varying(64) NOT NULL,
    pipeline_id character varying(64) NOT NULL,
    status character varying(32) DEFAULT 'PENDING'::character varying,
    started_at timestamp without time zone,
    finished_at timestamp without time zone,
    error_message text,
    rows_processed bigint DEFAULT 0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20)
);


--
-- Name: TABLE ecos_pipeline_execution; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.ecos_pipeline_execution IS '管道执行表 — 单源补写 V170（pipeline_id 关联由应用层保证，ST09 不建跨表外键）';


--
-- Name: ecos_pipeline_yaml_migration_report; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.ecos_pipeline_yaml_migration_report (
    id character varying(36) NOT NULL,
    migration_batch character varying(64) DEFAULT 'V168'::character varying NOT NULL,
    task_id character varying(36) NOT NULL,
    task_name character varying(200),
    outcome character varying(32) NOT NULL,
    reason text,
    migrated_definition_id character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


--
-- Name: ecos_query_history; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.ecos_query_history (
    id character varying(36) NOT NULL,
    sql_content text NOT NULL,
    datasource_id character varying(64),
    status character varying(20) DEFAULT 'RUNNING'::character varying,
    rows_affected integer DEFAULT 0,
    error_msg text,
    execution_time_ms bigint DEFAULT 0,
    started_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    finished_at timestamp without time zone,
    created_by character varying(100)
);


--
-- Name: TABLE ecos_query_history; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.ecos_query_history IS 'SQL query execution history';


--
-- Name: COLUMN ecos_query_history.status; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.ecos_query_history.status IS 'RUNNING / COMPLETED / FAILED / CANCELLED';


--
-- Name: ecos_query_template; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.ecos_query_template (
    id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    sql_content text NOT NULL,
    datasource_id character varying(64),
    is_shared boolean DEFAULT false,
    created_by character varying(100),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE ecos_query_template; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.ecos_query_template IS 'Saved SQL query templates';


--
-- Name: td_data_category; Type: TABLE; Schema: ecos_data; Owner: -
--

CREATE TABLE ecos_data.td_data_category (
    category_id character varying(64) NOT NULL,
    category_name character varying(256) NOT NULL,
    parent_id character varying(64),
    path character varying(512),
    level integer DEFAULT 1,
    sort_order integer DEFAULT 0,
    description text,
    status character varying(16) DEFAULT 'ACTIVE'::character varying,
    tenant_id character varying(64),
    create_by character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_by character varying(64),
    update_time timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE td_data_category; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON TABLE ecos_data.td_data_category IS 'Data category tree for catalog classification';


--
-- Name: COLUMN td_data_category.level; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.td_data_category.level IS '分类层级，根=1，子=父+1；前端按 level=2 取业务分类。与 parent_id 必须自洽（D12）。';


--
-- Name: COLUMN td_data_category.status; Type: COMMENT; Schema: ecos_data; Owner: -
--

COMMENT ON COLUMN ecos_data.td_data_category.status IS 'ACTIVE / INACTIVE';


--
-- Name: doc; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.doc (
    id character varying(64) NOT NULL,
    doc_id character varying(128) NOT NULL,
    source character varying(128),
    original_file_name character varying(512),
    object_key character varying(512),
    content_type character varying(128),
    size_bytes bigint,
    parse_status character varying(16) DEFAULT 'queued'::character varying NOT NULL,
    error_message text,
    chunk_count integer DEFAULT 0 NOT NULL,
    parsed_at timestamp without time zone,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64),
    update_by character varying(64),
    is_deleted boolean DEFAULT false NOT NULL
);


--
-- Name: TABLE doc; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON TABLE ecos_dw.doc IS 'V186/R-10① 定性: 演示遗留（legacy）文档表，**只停写**：新文档一律写 ecos_knowledge.kb_doc（唯一权威）。V139 曾按 A1 目标态将其定义为「数据工作台唯一写入」，该口径已被 R-10① 覆写（业务域不承载文档权威）。本批不 DROP、不 ALTER（IR03；R-12 DROP 重建前置件——备份+引用扫描+代码切换——未闭环）';


--
-- Name: COLUMN doc.doc_id; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.doc.doc_id IS 'V186 登记: 停写后本列仅作历史对账键；权威文档业务键为 ecos_knowledge.kb_doc.doc_id（应用层映射，无跨 schema FK）';


--
-- Name: COLUMN doc.object_key; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.doc.object_key IS '近源层对象 key: raw/unstructured/{source}/{docId}/{originalFileName}';


--
-- Name: COLUMN doc.parse_status; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.doc.parse_status IS '解析状态: queued/parsing/extracting/done/failed';


--
-- Name: doc_chunk; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.doc_chunk (
    id character varying(64) NOT NULL,
    chunk_id character varying(128) NOT NULL,
    doc_id character varying(128) NOT NULL,
    chunk_index integer NOT NULL,
    content text NOT NULL,
    char_start integer,
    char_end integer,
    metadata jsonb DEFAULT '{}'::jsonb,
    status character varying(16) DEFAULT 'active'::character varying NOT NULL,
    embedding_id character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64),
    update_by character varying(64),
    is_deleted boolean DEFAULT false NOT NULL
);


--
-- Name: TABLE doc_chunk; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON TABLE ecos_dw.doc_chunk IS 'V186/R-10① 定性: 演示遗留（legacy）切片表，**只停写**：切片一律写 ecos_knowledge.kb_doc_chunk。切片/向量属 K 层，迁入/落 DW 会与 MC05 冲突（R-10 ② 否决理由）；不 DROP、不 ALTER（IR03）';


--
-- Name: COLUMN doc_chunk.metadata; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.doc_chunk.metadata IS 'V186 登记: 存量 JSONB 列，随本表停写一并冻结（MC02 不为本表补 _text 列——停写载体无读写路径，补列即制造第四形态）';


--
-- Name: COLUMN doc_chunk.status; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.doc_chunk.status IS '分块状态: active/deprecated';


--
-- Name: COLUMN doc_chunk.embedding_id; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.doc_chunk.embedding_id IS '关联知识向量索引 ID（知识工作台消费后回填），可空';


--
-- Name: e2e_target; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.e2e_target (
    one integer
);


--
-- Name: ecos_application_action_outcome; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.ecos_application_action_outcome (
    id character varying(36) NOT NULL,
    forecast_run_id character varying(64),
    action_id character varying(64),
    fact_type character varying(32) NOT NULL,
    fact_id character varying(36) NOT NULL,
    period character varying(7),
    outcome character varying(1000) NOT NULL,
    outcome_comment character varying(2000),
    expected_impact numeric(18,2),
    actual_impact numeric(18,2),
    currency character varying(8) DEFAULT 'CNY'::character varying NOT NULL,
    batch_id character varying(64),
    idempotency_key character varying(64),
    detail_json text,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);


--
-- Name: TABLE ecos_application_action_outcome; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON TABLE ecos_dw.ecos_application_action_outcome IS '行动结果回灌载体（卷09 H-3：预期影响 vs 实际影响可查询物理表；append-only 只增不改；写路径=data-engine 唯一写通道（ADR-14/ST08 cognitive 禁直连本表））';


--
-- Name: COLUMN ecos_application_action_outcome.expected_impact; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_application_action_outcome.expected_impact IS '预期影响金额 NUMERIC(18,2)（ST03-A 豁免登记：业务域 CURATED 层明文存储；RLS+CLS+审计承接；导出/AI 通道经 security-engine mask。见登记表文本 H-3-①）';


--
-- Name: COLUMN ecos_application_action_outcome.actual_impact; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_application_action_outcome.actual_impact IS '实际影响金额 NUMERIC(18,2)（ST03-A 豁免登记同上口径，H-3-②；"实际"为回灌真值，与 expected_impact 成对呈现，差值即行动复盘读数）';


--
-- Name: COLUMN ecos_application_action_outcome.idempotency_key; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_application_action_outcome.idempotency_key IS '回灌幂等键（应用侧先查 uniq_eaao_idem 后 INSERT，重复提交返回首写 outcome id，不产生第二行）';


--
-- Name: COLUMN ecos_application_action_outcome.detail_json; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_application_action_outcome.detail_json IS '扩展上下文序列化 TEXT（受控 JSON，仅描述用，禁 WHERE/JOIN/索引，MC02）';


--
-- Name: ecos_application_forecast_run; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.ecos_application_forecast_run (
    id character varying(36) NOT NULL,
    forecast_run_id character varying(64) NOT NULL,
    scenario_id character varying(64) NOT NULL,
    run_type character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'RUNNING'::character varying NOT NULL,
    metric character varying(255),
    deviation numeric(9,6),
    diagnosis_result_json text,
    forecast_result_json text,
    simulation_result_json text,
    strategy_result_json text,
    decision_id character varying(64),
    is_degraded smallint DEFAULT 0 NOT NULL,
    caliber_id character varying(64),
    caliber_version character varying(20),
    as_of_time timestamp without time zone,
    project_id character varying(64),
    department_id character varying(64),
    baseline_run_id character varying(64),
    is_exploratory smallint DEFAULT 0 NOT NULL,
    overrides_json text,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone,
    snapshot_id character varying(64),
    formula_version character varying(20),
    reused_from_run_id character varying(64),
    task_id character varying(64),
    idempotency_key character varying(128),
    content_hash character varying(64)
);


--
-- Name: TABLE ecos_application_forecast_run; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON TABLE ecos_dw.ecos_application_forecast_run IS '确定性计算产物·运行与结果快照表（R-17 ①：业务域 APPLICATION 层落 ecos_dw，经 data-engine 写通道；旧 public.ecos_scenario_run 停写保留；cognitive 只读 forecastRunId 引用，不直写本表）';


--
-- Name: COLUMN ecos_application_forecast_run.deviation; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.deviation IS '偏差百分比（ST03-A 比率形态 NUMERIC(9,6)；派生统计量，非个人数据）';


--
-- Name: COLUMN ecos_application_forecast_run.baseline_run_id; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.baseline_run_id IS '反事实/情景重算基准运行键（F05-10：baselineRunId+overrides 重算得新 forecast_run_id）';


--
-- Name: COLUMN ecos_application_forecast_run.is_exploratory; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.is_exploratory IS '1=探索式通道产物（ScenarioSimulatorServiceImpl 强制 exploratory=true），响应/UI 不得作金额来源';


--
-- Name: COLUMN ecos_application_forecast_run.snapshot_id; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.snapshot_id IS '六要素之输入快照标识（SNAPSHOT_FROZEN 后非空，指向 PRD-02 DATA-01 表五 ecos_forecast_input_snapshot；缺列期由计算层内存承载的历史行留 NULL，禁伪造回填）';


--
-- Name: COLUMN ecos_application_forecast_run.formula_version; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.formula_version IS '六要素之公式版本（与 caliber_version 同形态 VARCHAR(20) 版本号，非自增；APPROVED 口径公式的不可变版本引用，历史 run 公式文本由此列 + caliber_id 快照重建）';


--
-- Name: COLUMN ecos_application_forecast_run.reused_from_run_id; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.reused_from_run_id IS '幂等复用来源运行键（PRD-09 §2.1：命中同 caliberVersion+asOfTime+scopeHash+scenarioSet+overridesHash 的既有 SUCCEEDED run 时响应 reused=true；force=true 新建时本列指向被复用的基准 forecast_run_id，供 diff=0 对账锚定）';


--
-- Name: COLUMN ecos_application_forecast_run.task_id; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.task_id IS '异步运行任务键（铁律 v2.0 §1.6 经 runtime-task ITaskManagementService.submitTask 返回的真实 taskId；禁自造 jobId；同步小样本运行为 NULL）';


--
-- Name: COLUMN ecos_application_forecast_run.idempotency_key; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.idempotency_key IS '幂等键（详设-09 H-5：与唯一写通道 V260 台账 idempotency_key 同值，本表侧供 findByIdempotencyKey 直接定位既有 run；历史搬运行为 NULL，禁伪造回填）';


--
-- Name: COLUMN ecos_application_forecast_run.content_hash; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.content_hash IS '内容指纹（六要素+范围规范化后的确定性摘要；同键异指纹 ⇒ 409 冲突判定依据，ForecastRunStateMachine.resolveIdempotentReuse 读回比对，禁事后改写）';


--
-- Name: ecos_application_wagent_option_amount; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.ecos_application_wagent_option_amount (
    option_id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    profit_delta numeric(18,2),
    revenue_delta numeric(18,2),
    cost_delta numeric(18,2),
    cash_impact numeric(18,2),
    id character varying(36) NOT NULL,
    domain character varying(50) DEFAULT 'entmgmt'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);


--
-- Name: TABLE ecos_application_wagent_option_amount; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON TABLE ecos_dw.ecos_application_wagent_option_amount IS 'W 域备选方案的金额影响实列载体（V286；自 V283 控制域搬出，用户 2026-10-14 裁决）。四列均明文存储，豁免依据见《列级加密豁免登记表》§二·续·补五；写通道 = data-engine 唯一写通道';


--
-- Name: COLUMN ecos_application_wagent_option_amount.option_id; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_application_wagent_option_amount.option_id IS '逻辑引用 ecos_ai.ecos_wagent_decision_option.id（V283）；反向引用位 = 该表 amount_ref 列。两列同值，禁第三处存金额真相（防同列双权威）';


--
-- Name: COLUMN ecos_application_wagent_option_amount.profit_delta; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_application_wagent_option_amount.profit_delta IS '利润影响额（方案 vs 基准差额，可负）：明文 NUMERIC(18,2)，受 RLS+CLS+审计三重保护 + 导出/AI 通道经 security-engine 脱敏裁决；豁免登记 = 登记表 §二·续·补五，批准人 = 用户 2026-10-14 裁决';


--
-- Name: COLUMN ecos_application_wagent_option_amount.revenue_delta; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_application_wagent_option_amount.revenue_delta IS '收入影响额（同上口径与豁免条件；登记表 §二·续·补五逐列行）';


--
-- Name: COLUMN ecos_application_wagent_option_amount.cost_delta; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_application_wagent_option_amount.cost_delta IS '成本影响额（同上口径与豁免条件；登记表 §二·续·补五逐列行）';


--
-- Name: COLUMN ecos_application_wagent_option_amount.cash_impact; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_application_wagent_option_amount.cash_impact IS '现金影响额（同上口径与豁免条件；登记表 §二·续·补五逐列行）';


--
-- Name: ecos_biz_cost_fact; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.ecos_biz_cost_fact (
    id character varying(36) NOT NULL,
    project_id character varying(36),
    is_pool smallint DEFAULT 0 NOT NULL,
    department_id character varying(36) NOT NULL,
    period character varying(7) NOT NULL,
    cost_category character varying(20),
    direct_or_allocated character varying(10),
    allocation_rule_ref character varying(36),
    amount numeric(18,2),
    currency character varying(3),
    fact_type character varying(10) NOT NULL,
    dq_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    batch_id character varying(36),
    source_ref character varying(255),
    evidence_ref character varying(255),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    CONSTRAINT ck_bcf_is_pool CHECK ((is_pool = ANY (ARRAY[0, 1])))
);


--
-- Name: ecos_biz_project_attribution; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.ecos_biz_project_attribution (
    id character varying(36) NOT NULL,
    project_id character varying(36) NOT NULL,
    contract_id character varying(36) NOT NULL,
    department_id character varying(36) NOT NULL,
    attribution_ratio numeric(5,4) NOT NULL,
    attribution_type character varying(20) DEFAULT 'PRIMARY'::character varying NOT NULL,
    effective_from character varying(7) NOT NULL,
    effective_to character varying(7),
    source_evidence character varying(255),
    dq_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    batch_id character varying(36),
    source_ref character varying(255),
    evidence_ref character varying(255),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    CONSTRAINT ck_bpa_ratio CHECK (((attribution_ratio >= (0)::numeric) AND (attribution_ratio <= (1)::numeric)))
);


--
-- Name: ecos_biz_resource_fact; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.ecos_biz_resource_fact (
    id character varying(36) NOT NULL,
    project_id character varying(36) NOT NULL,
    department_id character varying(36) NOT NULL,
    period character varying(7) NOT NULL,
    staff_ref_hash character varying(64) NOT NULL,
    staff_hash_algo character varying(20) DEFAULT 'SHA-256'::character varying NOT NULL,
    fte numeric(5,2),
    work_hours numeric(8,1),
    hourly_rate numeric(18,2),
    cost_category character varying(20),
    fact_type character varying(10) NOT NULL,
    dq_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    batch_id character varying(36),
    source_ref character varying(255),
    evidence_ref character varying(255),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100)
);


--
-- Name: ecos_biz_stage_fact; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.ecos_biz_stage_fact (
    id character varying(36) NOT NULL,
    project_id character varying(36) NOT NULL,
    department_id character varying(36) NOT NULL,
    period character varying(7) NOT NULL,
    stage character varying(20) NOT NULL,
    fact_type character varying(10) NOT NULL,
    contract_base numeric(18,2),
    attribution_ratio numeric(5,4),
    realization_rate numeric(5,4),
    amount numeric(18,2),
    currency character varying(3),
    source_type character varying(20),
    dq_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    batch_id character varying(36),
    source_ref character varying(255),
    evidence_ref character varying(255),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    CONSTRAINT ck_bsf_attr_ratio CHECK (((attribution_ratio IS NULL) OR ((attribution_ratio >= (0)::numeric) AND (attribution_ratio <= (1)::numeric)))),
    CONSTRAINT ck_bsf_real_rate CHECK (((realization_rate IS NULL) OR ((realization_rate >= (0)::numeric) AND (realization_rate <= (1)::numeric))))
);


--
-- Name: ecos_fc_backtest; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.ecos_fc_backtest (
    id character varying(36) NOT NULL,
    forecast_run_id character varying(36) NOT NULL,
    period character varying(16) NOT NULL,
    granularity character varying(16) NOT NULL,
    project_id character varying(36),
    department_id character varying(36),
    stage character varying(32),
    metric_id character varying(36) NOT NULL,
    mae numeric(8,4),
    mape numeric(8,4),
    bias_direction character varying(16),
    coverage numeric(5,4),
    data_coverage numeric(5,4),
    zero_actual_count integer,
    sample_count integer,
    sample_flag character varying(16),
    review_task_id character varying(64),
    computed_time timestamp without time zone,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE ecos_fc_backtest; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON TABLE ecos_dw.ecos_fc_backtest IS '回测五指标结果（业务域 APPLICATION，data-engine 写通道 ADR-14）；仅对 CLOSED 期间计算（§3.4），阈值判定源在 sysman 配置单源（R-66①）不入 DDL';


--
-- Name: ecos_fc_result_detail; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.ecos_fc_result_detail (
    id character varying(36) NOT NULL,
    forecast_run_id character varying(36) NOT NULL,
    caliber_version character varying(20) NOT NULL,
    as_of_time timestamp without time zone NOT NULL,
    snapshot_id character varying(36) NOT NULL,
    formula_version character varying(20) NOT NULL,
    source_ref_json text NOT NULL,
    metric_id character varying(36) NOT NULL,
    metric_code character varying(64),
    period character varying(16) NOT NULL,
    granularity character varying(16) DEFAULT 'TOTAL'::character varying NOT NULL,
    project_id character varying(36),
    department_id character varying(36),
    stage character varying(32),
    amount numeric(18,2),
    amount_p10 numeric(18,2),
    amount_p50 numeric(18,2),
    amount_p90 numeric(18,2),
    rate_value numeric(5,4),
    source_type character varying(20) NOT NULL,
    parent_detail_id character varying(36),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE ecos_fc_result_detail; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON TABLE ecos_dw.ecos_fc_result_detail IS '预测结果明细（格×指标；业务域 APPLICATION，data-engine 唯一写通道 ADR-14）；行级六要素 NOT NULL（C209），SUCCEEDED 后只读（C210 应用层）';


--
-- Name: COLUMN ecos_fc_result_detail.amount; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_fc_result_detail.amount IS '预测金额主值（P50 同值存储冗余展示由后端保证，禁前端插值——R-62①）；明文+CLS 读取裁决，ST03-A 待登记';


--
-- Name: COLUMN ecos_fc_result_detail.parent_detail_id; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_fc_result_detail.parent_detail_id IS '构成树父明细行（COMPUTED 逐层展开；导出/AI 回答必须复用本行集，禁旁路 §4.2）';


--
-- Name: ecos_fc_run; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.ecos_fc_run (
    id character varying(36) NOT NULL,
    caliber_id character varying(36) NOT NULL,
    caliber_version character varying(20) NOT NULL,
    formula_version character varying(20) NOT NULL,
    as_of_time timestamp without time zone NOT NULL,
    snapshot_id character varying(36) NOT NULL,
    snapshot_checksum character varying(64),
    scope_hash character varying(64),
    overrides_hash character varying(64),
    scenario_set_hash character varying(64),
    overrides_json text,
    caliber_snapshot_json text,
    status character varying(20) DEFAULT 'CREATED'::character varying NOT NULL,
    reused_from_run_id character varying(36),
    task_id character varying(64),
    error_code character varying(24),
    error_text text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE ecos_fc_run; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON TABLE ecos_dw.ecos_fc_run IS '预测运行主记录（业务域 APPLICATION，data-engine 唯一写通道，R-17①/R-59①/ADR-14）；SUCCEEDED 后只读（应用层约束 C210）';


--
-- Name: COLUMN ecos_fc_run.as_of_time; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_fc_run.as_of_time IS '六要素·快照可见性时刻：事实行按 create_time<=asOfTime AND (is_deleted=0 OR deleted_at>asOfTime) 裁剪（§3.2）';


--
-- Name: COLUMN ecos_fc_run.task_id; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_fc_run.task_id IS 'runtime-task 任务真值（禁自造）；异步阈值见 §3.1/C218';


--
-- Name: ecos_forecast_input_snapshot; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.ecos_forecast_input_snapshot (
    id character varying(36) NOT NULL,
    forecast_run_id character varying(36) NOT NULL,
    as_of_time timestamp without time zone NOT NULL,
    scope_hash character varying(64) NOT NULL,
    fact_refs_json text,
    metric_versions_json text,
    profile_versions_json text,
    assumption_versions_json text,
    caliber_id character varying(36),
    caliber_version character varying(20),
    checksum character varying(64),
    dq_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    batch_id character varying(36),
    source_ref character varying(255),
    evidence_ref character varying(255),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100)
);


--
-- Name: ecos_kb_assumption_value; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.ecos_kb_assumption_value (
    id character varying(36) NOT NULL,
    assumption_id character varying(36) NOT NULL,
    assumption_key character varying(255) NOT NULL,
    value_type character varying(10) NOT NULL,
    value numeric(18,4) NOT NULL,
    value_semantic character varying(20) DEFAULT 'ABSOLUTE'::character varying NOT NULL,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: ecos_kb_profile_stats; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.ecos_kb_profile_stats (
    id character varying(36) NOT NULL,
    profile_id character varying(36) NOT NULL,
    profile_key character varying(255) NOT NULL,
    sample_count integer NOT NULL,
    missing_rate numeric(5,4) DEFAULT 0 NOT NULL,
    p10 numeric(18,4),
    p50 numeric(18,4),
    p90 numeric(18,4),
    mean_value numeric(18,4),
    ci_low numeric(18,4),
    ci_high numeric(18,4),
    window_from character varying(7) NOT NULL,
    window_to character varying(7) NOT NULL,
    stats_batch_id character varying(64),
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: TABLE ecos_kb_profile_stats; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON TABLE ecos_dw.ecos_kb_profile_stats IS '历史画像·数值统计事实（业务域 ecos_dw；写方=data-engine 代 kb 写(ADR-14)，kb 只读组装 profiles/resolve）';


--
-- Name: COLUMN ecos_kb_profile_stats.missing_rate; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.missing_rate IS '缺失率（概率形态 NUMERIC(5,4)；ST03-A 逐列登记）';


--
-- Name: COLUMN ecos_kb_profile_stats.p10; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.p10 IS '派生统计量 P10（ST03-A 说明行逐列登记，R-9 ①，不加密不豁免业务金额列规则）';


--
-- Name: COLUMN ecos_kb_profile_stats.p50; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.p50 IS '派生统计量 P50（ST03-A 逐列登记）';


--
-- Name: COLUMN ecos_kb_profile_stats.p90; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.p90 IS '派生统计量 P90（ST03-A 逐列登记）';


--
-- Name: COLUMN ecos_kb_profile_stats.mean_value; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.mean_value IS '派生统计量 均值（ST03-A 逐列登记）';


--
-- Name: COLUMN ecos_kb_profile_stats.ci_low; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.ci_low IS '置信区间下界（ST03-A 逐列登记）';


--
-- Name: COLUMN ecos_kb_profile_stats.ci_high; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.ci_high IS '置信区间上界（ST03-A 逐列登记）';


--
-- Name: ecos_kb_vector_fallback; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.ecos_kb_vector_fallback (
    id character varying(36) NOT NULL,
    embedding_ref character varying(120) NOT NULL,
    document_ref character varying(64),
    chunk_index integer,
    embedding_model character varying(128) NOT NULL,
    embedding_dim integer NOT NULL,
    embedding_text text NOT NULL,
    degraded_kind character varying(20) DEFAULT 'NO_PGVector'::character varying NOT NULL,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: TABLE ecos_kb_vector_fallback; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON TABLE ecos_dw.ecos_kb_vector_fallback IS '形态B(V185/MC05): 向量 TEXT 降级载体（业务域 ecos_dw；pgvector 未启用档由应用写入，vectorDegraded 显式标记，非权威链）';


--
-- Name: COLUMN ecos_kb_vector_fallback.embedding_text; Type: COMMENT; Schema: ecos_dw; Owner: -
--

COMMENT ON COLUMN ecos_dw.ecos_kb_vector_fallback.embedding_text IS 'JSON 数值数组文本（如 "[0.013,-0.2,...]"，1536/512 维由 embedding_dim 自证）；MC02 合规形态：TEXT，禁 JSONB';


--
-- Name: fc_cost_allocation_fact; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.fc_cost_allocation_fact (
    id character varying(36) NOT NULL,
    project_id character varying(36) NOT NULL,
    department_id character varying(36) NOT NULL,
    period character varying(7) NOT NULL,
    hourly_rate numeric(18,2),
    amount numeric(18,2),
    allocated_amount numeric(18,2),
    currency character varying(3),
    source_type character varying(20),
    dq_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    batch_id character varying(36),
    source_ref character varying(255),
    evidence_ref character varying(255),
    version_no character varying(20) NOT NULL,
    domain character varying(50) DEFAULT 'forecast'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100)
);


--
-- Name: fc_project_period_fact; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.fc_project_period_fact (
    id character varying(36) NOT NULL,
    project_id character varying(36) NOT NULL,
    department_id character varying(36) NOT NULL,
    period character varying(7) NOT NULL,
    plan_amount numeric(18,2),
    actual_amount numeric(18,2),
    currency character varying(3),
    source_type character varying(20),
    dq_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    batch_id character varying(36),
    source_ref character varying(255),
    evidence_ref character varying(255),
    version_no character varying(20) NOT NULL,
    domain character varying(50) DEFAULT 'forecast'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100)
);


--
-- Name: fc_resource_input_fact; Type: TABLE; Schema: ecos_dw; Owner: -
--

CREATE TABLE ecos_dw.fc_resource_input_fact (
    id character varying(36) NOT NULL,
    project_id character varying(36) NOT NULL,
    department_id character varying(36) NOT NULL,
    period character varying(7) NOT NULL,
    staff_ref character varying(64) NOT NULL,
    hours numeric(8,1),
    hourly_rate numeric(18,2),
    cost_category character varying(20),
    dq_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    batch_id character varying(36),
    source_ref character varying(255),
    evidence_ref character varying(255),
    version_no character varying(20) NOT NULL,
    domain character varying(50) DEFAULT 'forecast'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100)
);


--
-- Name: outbox_event; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event (
    id bigint NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
)
PARTITION BY RANGE (created_at);


--
-- Name: TABLE outbox_event; Type: COMMENT; Schema: ecos_infra; Owner: -
--

COMMENT ON TABLE ecos_infra.outbox_event IS 'Outbox事件表(统一,按月分区)';


--
-- Name: COLUMN outbox_event.event_type; Type: COMMENT; Schema: ecos_infra; Owner: -
--

COMMENT ON COLUMN ecos_infra.outbox_event.event_type IS '事件类型';


--
-- Name: COLUMN outbox_event.aggregate_type; Type: COMMENT; Schema: ecos_infra; Owner: -
--

COMMENT ON COLUMN ecos_infra.outbox_event.aggregate_type IS '聚合类型';


--
-- Name: COLUMN outbox_event.aggregate_id; Type: COMMENT; Schema: ecos_infra; Owner: -
--

COMMENT ON COLUMN ecos_infra.outbox_event.aggregate_id IS '聚合ID';


--
-- Name: COLUMN outbox_event.published; Type: COMMENT; Schema: ecos_infra; Owner: -
--

COMMENT ON COLUMN ecos_infra.outbox_event.published IS '是否已发布';


--
-- Name: COLUMN outbox_event.kafka_topic; Type: COMMENT; Schema: ecos_infra; Owner: -
--

COMMENT ON COLUMN ecos_infra.outbox_event.kafka_topic IS 'Kafka主题';


--
-- Name: outbox_event_id_seq; Type: SEQUENCE; Schema: ecos_infra; Owner: -
--

CREATE SEQUENCE ecos_infra.outbox_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: outbox_event_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_infra; Owner: -
--

ALTER SEQUENCE ecos_infra.outbox_event_id_seq OWNED BY ecos_infra.outbox_event.id;


--
-- Name: outbox_event_2025_01; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2025_01 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2025_02; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2025_02 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2025_03; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2025_03 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2025_04; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2025_04 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2025_05; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2025_05 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2025_06; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2025_06 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2025_07; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2025_07 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2025_08; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2025_08 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2025_09; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2025_09 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2025_10; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2025_10 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2025_11; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2025_11 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2025_12; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2025_12 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2026_01; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2026_01 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2026_02; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2026_02 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2026_03; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2026_03 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2026_04; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2026_04 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2026_05; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2026_05 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2026_06; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2026_06 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2026_07; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2026_07 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2026_08; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2026_08 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2026_09; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2026_09 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2026_10; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2026_10 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2026_11; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2026_11 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2026_12; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2026_12 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2027_01; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2027_01 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2027_02; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2027_02 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2027_03; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2027_03 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2027_04; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2027_04 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2027_05; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2027_05 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2027_06; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2027_06 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2027_07; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2027_07 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2027_08; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2027_08 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2027_09; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2027_09 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2027_10; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2027_10 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2027_11; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2027_11 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_2027_12; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_2027_12 (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: outbox_event_default; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.outbox_event_default (
    id bigint DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass) NOT NULL,
    event_type character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published boolean DEFAULT false,
    published_at timestamp with time zone,
    kafka_topic character varying(128),
    kafka_key character varying(128)
);


--
-- Name: saga_instance; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.saga_instance (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    saga_type character varying(128) NOT NULL,
    status character varying(32) DEFAULT 'STARTED'::character varying,
    current_step integer DEFAULT 0,
    total_steps integer,
    input_data jsonb DEFAULT '{}'::jsonb,
    compensation_data jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone,
    error_message text
);


--
-- Name: TABLE saga_instance; Type: COMMENT; Schema: ecos_infra; Owner: -
--

COMMENT ON TABLE ecos_infra.saga_instance IS 'Saga实例表';


--
-- Name: COLUMN saga_instance.saga_type; Type: COMMENT; Schema: ecos_infra; Owner: -
--

COMMENT ON COLUMN ecos_infra.saga_instance.saga_type IS 'Saga类型';


--
-- Name: COLUMN saga_instance.status; Type: COMMENT; Schema: ecos_infra; Owner: -
--

COMMENT ON COLUMN ecos_infra.saga_instance.status IS '状态: STARTED/COMPENSATING/COMPLETED/FAILED';


--
-- Name: schema_version; Type: TABLE; Schema: ecos_infra; Owner: -
--

CREATE TABLE ecos_infra.schema_version (
    id character varying(64) NOT NULL,
    schema_name character varying(64) NOT NULL,
    version character varying(32) NOT NULL,
    description text,
    applied_at timestamp without time zone DEFAULT now() NOT NULL,
    applied_by character varying(64),
    checksum character varying(64),
    execution_time_ms integer
);


--
-- Name: TABLE schema_version; Type: COMMENT; Schema: ecos_infra; Owner: -
--

COMMENT ON TABLE ecos_infra.schema_version IS 'Schema版本追踪表';


--
-- Name: ecos_kb_access_request; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.ecos_kb_access_request (
    id character varying(36) NOT NULL,
    asset_id character varying(36) NOT NULL,
    request_reason text,
    applicant_name character varying(100),
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    approve_by character varying(100),
    approve_time timestamp without time zone,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_ecos_kb_areq_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: ecos_kb_assumption; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.ecos_kb_assumption (
    id character varying(36) NOT NULL,
    assumption_key character varying(255) NOT NULL,
    scenario_type character varying(32) NOT NULL,
    metric_code character varying(64) NOT NULL,
    dim_scope_json text DEFAULT '{}'::text NOT NULL,
    value_type character varying(10) NOT NULL,
    value_ref character varying(36),
    value_semantic character varying(20) DEFAULT 'ABSOLUTE'::character varying NOT NULL,
    base_assumption_id character varying(36),
    valid_from character varying(7) NOT NULL,
    valid_to character varying(7),
    reason character varying(500) NOT NULL,
    evidence_ref character varying(255),
    is_unverified smallint DEFAULT 0 NOT NULL,
    expire_at timestamp without time zone,
    assumption_version character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    approved_by character varying(100),
    approved_time timestamp without time zone,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_kba_sem CHECK (((value_semantic)::text = ANY ((ARRAY['ABSOLUTE'::character varying, 'DELTA'::character varying])::text[]))),
    CONSTRAINT ck_kba_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_APPROVAL'::character varying, 'ACTIVE'::character varying, 'EXPIRED'::character varying, 'SUPERSEDED'::character varying])::text[]))),
    CONSTRAINT ck_kba_vt CHECK (((value_type)::text = ANY ((ARRAY['RATE'::character varying, 'AMOUNT'::character varying, 'DAYS'::character varying])::text[])))
);


--
-- Name: ecos_kb_document; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.ecos_kb_document (
    id character varying(36) NOT NULL,
    doc_code character varying(64),
    title character varying(255) NOT NULL,
    content text,
    doc_type character varying(20),
    tags_json text DEFAULT '[]'::text NOT NULL,
    entity_types_json text DEFAULT '[]'::text NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: ecos_kb_extract_audit; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.ecos_kb_extract_audit (
    id character varying(36) NOT NULL,
    job_id character varying(64) NOT NULL,
    task_id character varying(64),
    extract_tier character varying(20) DEFAULT 'standard'::character varying NOT NULL,
    extract_mode character varying(20),
    status character varying(20),
    duration_ms bigint,
    rows_total bigint,
    rows_ok bigint,
    rows_failed bigint,
    mismatched bigint,
    error_message text,
    suggestion text,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: ecos_kb_extract_candidate; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.ecos_kb_extract_candidate (
    id character varying(36) NOT NULL,
    doc_id character varying(64),
    entity_name character varying(255),
    entity_type character varying(20),
    relation_code character varying(64),
    subject_id character varying(36),
    object_id character varying(36),
    confidence numeric(5,4),
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    review_note text,
    ontology_id character varying(64),
    ontology_version character varying(128),
    source_resource_id character varying(64),
    source_pk character varying(255),
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_ecos_kbeic_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'MERGED'::character varying])::text[])))
);


--
-- Name: ecos_kb_extract_watermark; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.ecos_kb_extract_watermark (
    id character varying(36) NOT NULL,
    ontology_id character varying(64) NOT NULL,
    entity_code character varying(100) NOT NULL,
    resource_id character varying(64) NOT NULL,
    watermark character varying(255),
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: ecos_kb_graph_edge; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.ecos_kb_graph_edge (
    id character varying(36) NOT NULL,
    source_node_id character varying(36) NOT NULL,
    target_node_id character varying(36) NOT NULL,
    edge_type character varying(64) NOT NULL,
    properties_json text DEFAULT '{}'::text NOT NULL,
    weight numeric(9,6) DEFAULT 1.0 NOT NULL,
    ontology_id character varying(64),
    ontology_version character varying(128),
    source_resource_id character varying(64),
    source_pk character varying(255),
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: ecos_kb_graph_node; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.ecos_kb_graph_node (
    id character varying(36) NOT NULL,
    node_label character varying(120),
    node_type character varying(20) DEFAULT 'Concept'::character varying NOT NULL,
    description text,
    properties_json text DEFAULT '{}'::text NOT NULL,
    ontology_id character varying(64),
    ontology_version character varying(128),
    source_resource_id character varying(64),
    source_pk character varying(255),
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: ecos_kb_lineage_event; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.ecos_kb_lineage_event (
    id character varying(36) NOT NULL,
    event_id character varying(64) NOT NULL,
    query_text text,
    lineage_format character varying(20),
    nodes_json text DEFAULT '[]'::text NOT NULL,
    edges_json text DEFAULT '[]'::text NOT NULL,
    parse_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: ecos_kb_market_asset; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.ecos_kb_market_asset (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    asset_category character varying(64),
    owner_name character varying(100),
    rating numeric(9,6) DEFAULT 0 NOT NULL,
    popularity integer DEFAULT 0 NOT NULL,
    status character varying(20) DEFAULT 'PUBLISHED'::character varying NOT NULL,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_ecos_kb_mkt_rating CHECK (((rating >= (0)::numeric) AND (rating <= (5)::numeric)))
);


--
-- Name: ecos_kb_ontology_snapshot; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.ecos_kb_ontology_snapshot (
    id character varying(36) NOT NULL,
    ontology_id character varying(64) NOT NULL,
    snap_version character varying(20) NOT NULL,
    entity_codes_json text DEFAULT '[]'::text NOT NULL,
    relationship_codes_json text DEFAULT '[]'::text NOT NULL,
    schema_hash character varying(64),
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: ecos_kb_profile; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.ecos_kb_profile (
    id character varying(36) NOT NULL,
    profile_key character varying(255) NOT NULL,
    metric_code character varying(64) NOT NULL,
    group_dims_json text NOT NULL,
    window_from character varying(7) NOT NULL,
    window_to character varying(7) NOT NULL,
    stats_ref character varying(36),
    ci_level character varying(10) DEFAULT '95'::character varying NOT NULL,
    stats_method character varying(32) DEFAULT 'T_APPROX'::character varying NOT NULL,
    confidence character varying(10) NOT NULL,
    degrade_from character varying(36),
    degrade_path_json text,
    applicable_condition character varying(500),
    source_query_ref character varying(255),
    profile_version character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    approved_by character varying(100),
    approved_time timestamp without time zone,
    task_id character varying(64),
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sample_count integer DEFAULT 0 NOT NULL,
    missing_rate numeric(5,4),
    p10 numeric(18,4),
    p50 numeric(18,4),
    p90 numeric(18,4),
    mean_value numeric(18,4),
    ci_low numeric(18,4),
    ci_high numeric(18,4),
    CONSTRAINT ck_kbp_conf CHECK (((confidence)::text = ANY ((ARRAY['HIGH'::character varying, 'MEDIUM'::character varying, 'LOW'::character varying])::text[]))),
    CONSTRAINT ck_kbp_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying, 'SUPERSEDED'::character varying])::text[])))
);


--
-- Name: TABLE ecos_kb_profile; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON TABLE ecos_knowledge.ecos_kb_profile IS '历史画像·定义态（REQ-KB-02/F04-06；R-8 ② 拆分：数值统计事实见 ecos_dw.ecos_kb_profile_stats，经 data-engine 写通道）';


--
-- Name: COLUMN ecos_kb_profile.sample_count; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.sample_count IS '样本量（画像窗口内参与统计的事实行数；KbProfileDO.sampleCount int 原语 ⇒ NOT NULL DEFAULT 0；ST03-A 逐列登记，派生统计量不加密）';


--
-- Name: COLUMN ecos_kb_profile.missing_rate; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.missing_rate IS '缺失率（比率→概率档 NUMERIC(5,4)，承 V176:68 精度；可空=缺统计口径时留 NULL 而非 0，避免把 0 误读为"零缺失"；ST03-A 逐列登记）';


--
-- Name: COLUMN ecos_kb_profile.p10; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.p10 IS '派生统计量 P10（分位数，NUMERIC(18,4) 承 V176:69；ST03-A 逐列登记，不加密不豁免业务金额列规则）';


--
-- Name: COLUMN ecos_kb_profile.p50; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.p50 IS '派生统计量 P50（中位数，NUMERIC(18,4) 承 V176:70；ST03-A 逐列登记）';


--
-- Name: COLUMN ecos_kb_profile.p90; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.p90 IS '派生统计量 P90（分位数，NUMERIC(18,4) 承 V176:71；ST03-A 逐列登记）';


--
-- Name: COLUMN ecos_kb_profile.mean_value; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.mean_value IS '派生统计量 均值（NUMERIC(18,4) 承 V176:72；ST03-A 逐列登记）';


--
-- Name: COLUMN ecos_kb_profile.ci_low; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.ci_low IS '置信区间下界（CI 由 ci_level/stats_method 决定口径，NUMERIC(18,4) 承 V176:73；ST03-A 逐列登记）';


--
-- Name: COLUMN ecos_kb_profile.ci_high; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.ci_high IS '置信区间上界（NUMERIC(18,4) 承 V176:74；ST03-A 逐列登记）';


--
-- Name: ecos_kb_term; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.ecos_kb_term (
    id character varying(36) NOT NULL,
    term_code character varying(64),
    name character varying(255) NOT NULL,
    definition text,
    term_type character varying(20),
    subject_domain character varying(64),
    object_type_ref character varying(120),
    parent_term_id character varying(36),
    aliases_json text DEFAULT '[]'::text NOT NULL,
    examples_json text DEFAULT '[]'::text NOT NULL,
    tags_json text DEFAULT '[]'::text NOT NULL,
    definition_version character varying(20) DEFAULT '1'::character varying NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    owner_name character varying(100),
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_ecos_kb_term_type CHECK (((term_type)::text = ANY ((ARRAY['ENTITY'::character varying, 'RELATION'::character varying, 'METRIC'::character varying, 'FUNCTION'::character varying, 'CONCEPT'::character varying])::text[])))
);


--
-- Name: ecos_kg_sync_log; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.ecos_kg_sync_log (
    id character varying(36) NOT NULL,
    object_type character varying(128) NOT NULL,
    sync_op character varying(20) NOT NULL,
    job_id character varying(120) NOT NULL,
    status character varying(20) NOT NULL,
    progress integer DEFAULT 0 NOT NULL,
    node_count integer DEFAULT 0 NOT NULL,
    edge_count integer DEFAULT 0 NOT NULL,
    error_message character varying(200),
    report_json text,
    finished_time timestamp without time zone,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(100) DEFAULT 'system'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: ecos_knowledge_graph_edge; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.ecos_knowledge_graph_edge (
    id character varying(64) NOT NULL,
    source_node_id character varying(64) NOT NULL,
    target_node_id character varying(64) NOT NULL,
    relationship character varying(64),
    properties_json jsonb,
    weight numeric(5,2) DEFAULT 1.0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_knowledge_graph_edge; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON TABLE ecos_knowledge.ecos_knowledge_graph_edge IS 'knownLegacy(V184/MC01): 宽度违规存量侧（0 行），新写切 V184 ecos_knowledge.ecos_kb_graph_edge；只停写不 DROP（R-12 ① 前置未闭环）';


--
-- Name: ecos_knowledge_graph_node; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.ecos_knowledge_graph_node (
    id character varying(64) NOT NULL,
    label character varying(512) NOT NULL,
    node_type character varying(64) NOT NULL,
    description text,
    properties_json jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    processed_at bigint
);


--
-- Name: expert_rule; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.expert_rule (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    domain character varying(64),
    rule_type character varying(32) DEFAULT 'IF-THEN'::character varying,
    condition_expr text,
    action_expr text,
    priority integer DEFAULT 0,
    enabled boolean DEFAULT true,
    description text,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE expert_rule; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON TABLE ecos_knowledge.expert_rule IS '专家规则表(kb-engine)';


--
-- Name: graph_edge; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.graph_edge (
    id character varying(64) NOT NULL,
    source_id character varying(64) NOT NULL,
    target_id character varying(64) NOT NULL,
    type character varying(64),
    properties jsonb DEFAULT '{}'::jsonb,
    weight numeric(5,2) DEFAULT 1.0,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    ontology_id character varying(64),
    ontology_version character varying(128),
    source_resource_id character varying(64),
    source_pk character varying(255),
    properties_json text,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);


--
-- Name: TABLE graph_edge; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON TABLE ecos_knowledge.graph_edge IS '知识图谱边表(kb-engine)';


--
-- Name: COLUMN graph_edge.type; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_edge.type IS '关系类型';


--
-- Name: COLUMN graph_edge.properties; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_edge.properties IS 'V178/MC02 定性: 存量 JSONB 列，读写切 properties_json 后停写（IR03 只加不删，本批不 DROP）';


--
-- Name: COLUMN graph_edge.weight; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_edge.weight IS '关系权重';


--
-- Name: COLUMN graph_edge.ontology_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_edge.ontology_id IS '溯源: 本体业务 ID (V134/B28)';


--
-- Name: COLUMN graph_edge.ontology_version; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_edge.ontology_version IS '溯源: 生成该边时的本体版本 (V134/B28)';


--
-- Name: COLUMN graph_edge.source_resource_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_edge.source_resource_id IS '溯源: 实例抽取来源 DW 层数据资源 ID (V134/B28)';


--
-- Name: COLUMN graph_edge.source_pk; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_edge.source_pk IS '溯源: 实例抽取来源 DW 表主键值 (V134/B28)';


--
-- Name: COLUMN graph_edge.properties_json; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_edge.properties_json IS 'V178/MC02: properties 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）';


--
-- Name: graph_node; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.graph_node (
    id character varying(64) NOT NULL,
    label character varying(64),
    node_type character varying(64) DEFAULT 'Concept'::character varying,
    description text,
    properties jsonb DEFAULT '{}'::jsonb,
    domain character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    ontology_id character varying(64),
    ontology_version character varying(128),
    source_resource_id character varying(64),
    source_pk character varying(255),
    properties_json text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);


--
-- Name: TABLE graph_node; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON TABLE ecos_knowledge.graph_node IS '知识图谱节点表(kb-engine)';


--
-- Name: COLUMN graph_node.label; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_node.label IS '标签/名称';


--
-- Name: COLUMN graph_node.node_type; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_node.node_type IS '节点类型: Concept/Entity/Event';


--
-- Name: COLUMN graph_node.properties; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_node.properties IS 'V178/MC02 定性: 存量 JSONB 列，读写切 properties_json 后停写（IR03 只加不删，本批不 DROP）';


--
-- Name: COLUMN graph_node.ontology_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_node.ontology_id IS '溯源: 本体业务 ID (V134/B28)';


--
-- Name: COLUMN graph_node.ontology_version; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_node.ontology_version IS '溯源: 生成该节点时的本体版本 (V134/B28)';


--
-- Name: COLUMN graph_node.source_resource_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_node.source_resource_id IS '溯源: 实例抽取来源 DW 层数据资源 ID (V134/B28)';


--
-- Name: COLUMN graph_node.source_pk; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_node.source_pk IS '溯源: 实例抽取来源 DW 表主键值 (V134/B28)';


--
-- Name: COLUMN graph_node.properties_json; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_node.properties_json IS 'V178/MC02: properties 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）';


--
-- Name: graph_subgraph; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.graph_subgraph (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    node_ids jsonb DEFAULT '[]'::jsonb,
    edge_ids jsonb DEFAULT '[]'::jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    node_ids_json text,
    edge_ids_json text,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);


--
-- Name: TABLE graph_subgraph; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON TABLE ecos_knowledge.graph_subgraph IS '知识图谱子图表(kb-engine)';


--
-- Name: COLUMN graph_subgraph.node_ids; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_subgraph.node_ids IS 'V178/MC02 定性: 存量 JSONB 列，读写切 node_ids_json 后停写（IR03 只加不删，本批不 DROP）';


--
-- Name: COLUMN graph_subgraph.edge_ids; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_subgraph.edge_ids IS 'V178/MC02 定性: 存量 JSONB 列，读写切 edge_ids_json 后停写（IR03 只加不删，本批不 DROP）';


--
-- Name: COLUMN graph_subgraph.node_ids_json; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_subgraph.node_ids_json IS 'V178/MC02: node_ids 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）';


--
-- Name: COLUMN graph_subgraph.edge_ids_json; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.graph_subgraph.edge_ids_json IS 'V178/MC02: edge_ids 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）';


--
-- Name: kb_doc; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.kb_doc (
    doc_id character varying(128) NOT NULL,
    source character varying(128),
    original_file_name character varying(512),
    object_key character varying(512),
    content_type character varying(128),
    size_bytes bigint,
    parse_status character varying(16) DEFAULT 'queued'::character varying NOT NULL,
    chunk_size integer,
    chunk_overlap integer,
    chunk_count integer DEFAULT 0,
    text_source_path character varying(512),
    error_message text,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);


--
-- Name: TABLE kb_doc; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON TABLE ecos_knowledge.kb_doc IS 'V186/R-10① 权威载体（唯一）: 知识层非结构化文档级状态机（queued/parsing/extracting/done/failed）。DR 基线列（domain/version_no/is_deleted/审计五列/trace_id）由 V180 补齐；其余两套文档载体（public.extraction_drafts、ecos_dw.doc）自本脚本起定性 legacy 只停写。本表不新建、不迁不删（IR03）';


--
-- Name: COLUMN kb_doc.doc_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_doc.doc_id IS 'V186 登记: 权威文档业务键（VARCHAR(128) 承重旧形态，MC01 新表口径为 VARCHAR(36)）。本批不改列类型（IR03 禁 ALTER 既有列型）；宽度收敛随代码切写另批申报';


--
-- Name: COLUMN kb_doc.object_key; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_doc.object_key IS 'V186/F04-12: MinIO RAW 近源层唯一入口对象 key（raw/unstructured/{source}/{docId}/{originalFileName}）。上传一律走 KnowledgeDocIngestService 合规链；java.io.tmpdir 仅作抽取临时缓冲且必须 TTL 清理，禁终存（K-37/W102）';


--
-- Name: COLUMN kb_doc.parse_status; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_doc.parse_status IS '解析状态: queued/parsing/extracting/done/failed';


--
-- Name: COLUMN kb_doc.text_source_path; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_doc.text_source_path IS 'V186: 解析文本登记为 CURATED 资源后的 source_path（经数据工作台登记端点，非直写 DW 表；ADR-2 A2 永久否决）';


--
-- Name: kb_doc_chunk; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.kb_doc_chunk (
    id character varying(64) NOT NULL,
    doc_id character varying(128) NOT NULL,
    source character varying(128),
    chunk_index integer NOT NULL,
    content text NOT NULL,
    char_start integer,
    char_end integer,
    metadata jsonb DEFAULT '{}'::jsonb,
    status character varying(16) DEFAULT 'active'::character varying NOT NULL,
    embedding_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    metadata_json text,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);


--
-- Name: TABLE kb_doc_chunk; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON TABLE ecos_knowledge.kb_doc_chunk IS 'V186/R-10① 权威载体（唯一）: 知识层切片表（文本+序号+字符偏移+元数据）。A3 过渡语义自本脚本起转为目标态口径（知识层权威），V138「待 A1 落地迁入 DW」的退出计划随 R-10① 作废；切片/向量不得迁入业务域（MC05/R-10 ② 否决理由）';


--
-- Name: COLUMN kb_doc_chunk.doc_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_doc_chunk.doc_id IS 'V186: 权威链切片→文档外键（应用层保证，无跨 schema FK），指向本 schema kb_doc.doc_id';


--
-- Name: COLUMN kb_doc_chunk.metadata; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_doc_chunk.metadata IS 'V186 登记: 存量 JSONB 列，读写已切 V178 的 metadata_json 后本列停写（MC02/IR03，不 DROP）';


--
-- Name: COLUMN kb_doc_chunk.status; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_doc_chunk.status IS '分块状态: active/deprecated';


--
-- Name: COLUMN kb_doc_chunk.embedding_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_doc_chunk.embedding_id IS 'V186: 关联 ecos_knowledge.knowledge_embedding.id（向量权威在知识层，V137/V185 形态 A；降级副本见 ecos_dw.ecos_kb_vector_fallback，非权威）';


--
-- Name: COLUMN kb_doc_chunk.metadata_json; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_doc_chunk.metadata_json IS 'V178/MC02: metadata 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）';


--
-- Name: kb_extract_audit; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.kb_extract_audit (
    id bigint NOT NULL,
    job_id character varying(64) NOT NULL,
    task_id character varying(64),
    tier character varying(32) DEFAULT 'standard'::character varying NOT NULL,
    mode character varying(32),
    status character varying(32),
    duration_ms bigint,
    rows_total bigint,
    rows_ok bigint,
    rows_failed bigint,
    mismatched bigint,
    error_message text,
    suggestion text,
    created_at timestamp without time zone DEFAULT now(),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);


--
-- Name: TABLE kb_extract_audit; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON TABLE ecos_knowledge.kb_extract_audit IS 'knownLegacy(MC01/V179): BIGSERIAL 主键，新写切 ecos_knowledge.ecos_* 合规并建新表；本表只停写不迁移不 DROP（R-12 ① 前置未闭环 + IR03）';


--
-- Name: COLUMN kb_extract_audit.trace_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_audit.trace_id IS 'V181/§C.7: 全链路追踪 ID（与审计链/事件总线 ecos.audit 同键），可空（历史行为 NULL）';


--
-- Name: kb_extract_audit_id_seq; Type: SEQUENCE; Schema: ecos_knowledge; Owner: -
--

CREATE SEQUENCE ecos_knowledge.kb_extract_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_extract_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_knowledge; Owner: -
--

ALTER SEQUENCE ecos_knowledge.kb_extract_audit_id_seq OWNED BY ecos_knowledge.kb_extract_audit.id;


--
-- Name: kb_extract_candidate; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.kb_extract_candidate (
    id bigint NOT NULL,
    doc_id character varying(64),
    entity_name character varying(256),
    entity_type character varying(128),
    relation character varying(128),
    subject_id character varying(128),
    object_id character varying(128),
    confidence numeric(5,4),
    status character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    review_note text,
    ontology_id character varying(64),
    ontology_version character varying(64),
    source_resource_id character varying(128),
    source_pk character varying(256),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    trace_id character varying(64)
);


--
-- Name: TABLE kb_extract_candidate; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON TABLE ecos_knowledge.kb_extract_candidate IS 'knownLegacy(MC01/V179): BIGSERIAL 主键，新写切 ecos_knowledge.ecos_* 合规并建新表；本表只停写不迁移不 DROP（R-12 ① 前置未闭环 + IR03）';


--
-- Name: COLUMN kb_extract_candidate.doc_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.doc_id IS '关联文档 ID（上传临时文件的 fileId，同 extraction_drafts.id）';


--
-- Name: COLUMN kb_extract_candidate.entity_name; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.entity_name IS 'LLM 抽取的实体名称（自然语言）';


--
-- Name: COLUMN kb_extract_candidate.entity_type; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.entity_type IS 'LLM 推断的实体类型（可选映射到本体实体 code）';


--
-- Name: COLUMN kb_extract_candidate.relation; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.relation IS 'LLM 抽取的关系 code（可选映射到本体关系 code）';


--
-- Name: COLUMN kb_extract_candidate.subject_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.subject_id IS '关系源节点 ID（无关系时为空）';


--
-- Name: COLUMN kb_extract_candidate.object_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.object_id IS '关系目标节点 ID（无关系时为空）';


--
-- Name: COLUMN kb_extract_candidate.confidence; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.confidence IS 'LLM 置信度（0.0000 - 1.0000）';


--
-- Name: COLUMN kb_extract_candidate.status; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.status IS '候选状态：PENDING / APPROVED / REJECTED / MERGED';


--
-- Name: COLUMN kb_extract_candidate.review_note; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.review_note IS '审核意见 / LLM 解释';


--
-- Name: COLUMN kb_extract_candidate.ontology_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.ontology_id IS '关联本体业务 ID（同 graph_node.ontology_id，溯源对齐）';


--
-- Name: COLUMN kb_extract_candidate.ontology_version; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.ontology_version IS '关联本体版本（同 graph_node.ontology_version，溯源对齐）';


--
-- Name: COLUMN kb_extract_candidate.source_resource_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.source_resource_id IS '来源资源 ID（上传文档登记 ID，溯源对齐）';


--
-- Name: COLUMN kb_extract_candidate.source_pk; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.source_pk IS '来源主键（同 graph_node.source_pk，溯源对齐）';


--
-- Name: kb_extract_candidate_id_seq; Type: SEQUENCE; Schema: ecos_knowledge; Owner: -
--

CREATE SEQUENCE ecos_knowledge.kb_extract_candidate_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_extract_candidate_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_knowledge; Owner: -
--

ALTER SEQUENCE ecos_knowledge.kb_extract_candidate_id_seq OWNED BY ecos_knowledge.kb_extract_candidate.id;


--
-- Name: kb_extract_watermark; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.kb_extract_watermark (
    id bigint NOT NULL,
    ontology_id character varying(64) NOT NULL,
    entity_code character varying(100) NOT NULL,
    resource_id character varying(64) NOT NULL,
    watermark character varying(255),
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);


--
-- Name: TABLE kb_extract_watermark; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON TABLE ecos_knowledge.kb_extract_watermark IS 'knownLegacy(MC01/V179): BIGSERIAL 主键，新写切 ecos_knowledge.ecos_* 合规并建新表；本表只停写不迁移不 DROP（R-12 ① 前置未闭环 + IR03）';


--
-- Name: COLUMN kb_extract_watermark.ontology_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_watermark.ontology_id IS '本体业务 ID（kb_ontology_snapshot.ontology_id）';


--
-- Name: COLUMN kb_extract_watermark.entity_code; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_watermark.entity_code IS '本体实体 code（ecos_entity_table_mapping.entity_code）';


--
-- Name: COLUMN kb_extract_watermark.resource_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_watermark.resource_id IS 'DW 层数据资源 ID（td_data_resource.resource_id，只读消费）';


--
-- Name: COLUMN kb_extract_watermark.watermark; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_watermark.watermark IS '上次抽取收敛水位（DW 行读取端点的 nextWatermark）';


--
-- Name: kb_extract_watermark_id_seq; Type: SEQUENCE; Schema: ecos_knowledge; Owner: -
--

CREATE SEQUENCE ecos_knowledge.kb_extract_watermark_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_extract_watermark_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_knowledge; Owner: -
--

ALTER SEQUENCE ecos_knowledge.kb_extract_watermark_id_seq OWNED BY ecos_knowledge.kb_extract_watermark.id;


--
-- Name: kb_nav_article_rel; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.kb_nav_article_rel (
    id bigint NOT NULL,
    article_id character varying(64) NOT NULL,
    node_id character varying(64) NOT NULL,
    scope character varying(16) NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64),
    update_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    trace_id character varying(64),
    CONSTRAINT kb_nav_article_rel_scope_check CHECK (((scope)::text = ANY ((ARRAY['category'::character varying, 'tag'::character varying])::text[])))
);


--
-- Name: TABLE kb_nav_article_rel; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON TABLE ecos_knowledge.kb_nav_article_rel IS '知识导航·文章 ↔ (目录|标签) 多对多关联 — scope 字段区分 category/tag 两类节点';


--
-- Name: COLUMN kb_nav_article_rel.article_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_nav_article_rel.article_id IS '关联 knowledge_article.id（VARCHAR 64，弱 FK 不强制约束）';


--
-- Name: COLUMN kb_nav_article_rel.node_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_nav_article_rel.node_id IS '节点 ID：scope=category → kb_nav_category.id；scope=tag → kb_nav_tag.id';


--
-- Name: COLUMN kb_nav_article_rel.scope; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_nav_article_rel.scope IS '关联维度：category 或 tag（CHECK 约束）';


--
-- Name: kb_nav_article_rel_id_seq; Type: SEQUENCE; Schema: ecos_knowledge; Owner: -
--

CREATE SEQUENCE ecos_knowledge.kb_nav_article_rel_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_nav_article_rel_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_knowledge; Owner: -
--

ALTER SEQUENCE ecos_knowledge.kb_nav_article_rel_id_seq OWNED BY ecos_knowledge.kb_nav_article_rel.id;


--
-- Name: kb_nav_category; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.kb_nav_category (
    id character varying(36) DEFAULT gen_random_uuid() NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    parent_id character varying(36),
    name character varying(128) NOT NULL,
    path character varying(512),
    level smallint DEFAULT 1 NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64),
    update_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64),
    CONSTRAINT kb_nav_category_level_check CHECK (((level >= 1) AND (level <= 3)))
);


--
-- Name: TABLE kb_nav_category; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON TABLE ecos_knowledge.kb_nav_category IS '知识导航·业务目录树（≤3 级含叶子）— kb-engine 自有表，与 data 分级分类完全解耦（不带 level_code）';


--
-- Name: COLUMN kb_nav_category.domain; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_nav_category.domain IS '多租户预留（DR08）— NOT NULL DEFAULT default';


--
-- Name: COLUMN kb_nav_category.parent_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_nav_category.parent_id IS '父目录 ID；根目录 = NULL';


--
-- Name: COLUMN kb_nav_category.path; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_nav_category.path IS '从根到本节点的路径（/name1/name2/...），冗余提升子树查询性能';


--
-- Name: COLUMN kb_nav_category.level; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_nav_category.level IS '层级 1/2/3（1 = 域下一级目录，3 = 叶子极限）';


--
-- Name: COLUMN kb_nav_category.sort_order; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_nav_category.sort_order IS '同级排序（小者先）';


--
-- Name: COLUMN kb_nav_category.version_no; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_nav_category.version_no IS '乐观锁版本号（V150 DR07 规范）';


--
-- Name: kb_nav_tag; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.kb_nav_tag (
    id character varying(36) DEFAULT gen_random_uuid() NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    tag_name character varying(128) NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64),
    update_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);


--
-- Name: TABLE kb_nav_tag; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON TABLE ecos_knowledge.kb_nav_tag IS '知识导航·标签字典 — 文章多对多关联（kb_nav_article_rel, scope=tag）';


--
-- Name: COLUMN kb_nav_tag.domain; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_nav_tag.domain IS '多租户预留（DR08）— NOT NULL DEFAULT default';


--
-- Name: COLUMN kb_nav_tag.tag_name; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_nav_tag.tag_name IS '标签名（同 domain 下唯一）';


--
-- Name: kb_scheduled_extract; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.kb_scheduled_extract (
    id bigint NOT NULL,
    schedule_id character varying(64) NOT NULL,
    name character varying(128) NOT NULL,
    ontology_ids jsonb NOT NULL,
    mode character varying(32) DEFAULT 'INCREMENTAL'::character varying NOT NULL,
    period character varying(16) NOT NULL,
    cron_expression character varying(64) NOT NULL,
    next_run_at timestamp without time zone,
    enabled smallint DEFAULT 1 NOT NULL,
    last_run_at timestamp without time zone,
    last_status character varying(32),
    created_by character varying(64),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    is_deleted smallint DEFAULT 0,
    prefers_runtime_task character varying(64) DEFAULT 'td_runtime_task_plan'::character varying NOT NULL,
    ontology_ids_json text,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    trace_id character varying(64)
);


--
-- Name: TABLE kb_scheduled_extract; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON TABLE ecos_knowledge.kb_scheduled_extract IS 'DEPRECATED (V153, 2026-09-22, TB-1): 由 td_runtime_task_plan + ITaskManagementService 代替; 历史行只读不写; 数据修复走一次性脚本; R9 只加不删';


--
-- Name: COLUMN kb_scheduled_extract.ontology_ids; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_scheduled_extract.ontology_ids IS 'V178/MC02 定性: 存量 JSONB 列，读写切 ontology_ids_json 后停写（IR03 只加不删，本批不 DROP）';


--
-- Name: COLUMN kb_scheduled_extract.ontology_ids_json; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kb_scheduled_extract.ontology_ids_json IS 'V178/MC02: ontology_ids 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）';


--
-- Name: kb_scheduled_extract_id_seq; Type: SEQUENCE; Schema: ecos_knowledge; Owner: -
--

CREATE SEQUENCE ecos_knowledge.kb_scheduled_extract_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_scheduled_extract_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_knowledge; Owner: -
--

ALTER SEQUENCE ecos_knowledge.kb_scheduled_extract_id_seq OWNED BY ecos_knowledge.kb_scheduled_extract.id;


--
-- Name: kg_sync_log; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.kg_sync_log (
    id bigint NOT NULL,
    object_type character varying(64) NOT NULL,
    op character varying(32) NOT NULL,
    job_id character varying(128) NOT NULL,
    status character varying(16) NOT NULL,
    progress integer DEFAULT 0 NOT NULL,
    nodes integer DEFAULT 0 NOT NULL,
    edges integer DEFAULT 0 NOT NULL,
    error_message character varying(1024),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    finished_at timestamp without time zone,
    report jsonb,
    report_json text,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);


--
-- Name: TABLE kg_sync_log; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON TABLE ecos_knowledge.kg_sync_log IS 'knownLegacy(MC01/V179): BIGSERIAL 主键，新写切 ecos_knowledge.ecos_* 合规并建新表；本表只停写不迁移不 DROP（R-12 ① 前置未闭环 + IR03）';


--
-- Name: COLUMN kg_sync_log.report; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kg_sync_log.report IS 'V178/MC02 定性: 存量 JSONB 列，读写切 report_json 后停写（IR03 只加不删，本批不 DROP）';


--
-- Name: COLUMN kg_sync_log.report_json; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kg_sync_log.report_json IS 'V178/MC02: report 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）';


--
-- Name: COLUMN kg_sync_log.trace_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.kg_sync_log.trace_id IS 'V181/§C.7: 全链路追踪 ID（与审计链/事件总线 ecos.audit 同键），可空（历史行为 NULL）';


--
-- Name: kg_sync_log_id_seq; Type: SEQUENCE; Schema: ecos_knowledge; Owner: -
--

CREATE SEQUENCE ecos_knowledge.kg_sync_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kg_sync_log_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_knowledge; Owner: -
--

ALTER SEQUENCE ecos_knowledge.kg_sync_log_id_seq OWNED BY ecos_knowledge.kg_sync_log.id;


--
-- Name: knowledge_article; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.knowledge_article (
    id character varying(64) NOT NULL,
    title character varying(255) NOT NULL,
    content text,
    source character varying(512),
    source_type character varying(64),
    domain character varying(64),
    category character varying(64),
    tags jsonb DEFAULT '[]'::jsonb,
    status character varying(16) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    tags_json text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64),
    knowledge_category character varying(32),
    rule_ref character varying(64),
    CONSTRAINT ck_knowledge_article_category CHECK (((knowledge_category IS NULL) OR ((knowledge_category)::text = ANY ((ARRAY['Fact'::character varying, 'Concept'::character varying, 'Procedure'::character varying, 'Rule'::character varying, 'Case'::character varying, 'Evidence'::character varying, 'Pattern'::character varying])::text[]))))
);


--
-- Name: TABLE knowledge_article; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON TABLE ecos_knowledge.knowledge_article IS '知识文章表(kb-engine)';


--
-- Name: COLUMN knowledge_article.source_type; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.knowledge_article.source_type IS 'legacy-orphan(V182): 与 source 列语义重叠，Mapper 零读写 —— 待 Mapper 使用或标停用（卷04 §E.3 V182）；IR03 不 DROP';


--
-- Name: COLUMN knowledge_article.tags; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.knowledge_article.tags IS 'legacy-orphan 双身份(V182): JSONB 读写已由 V178 切 tags_json 双写（2026-09-30 校订：过渡列后缀由 _text 收口为 DR04 的 _json）；Mapper 若弃用则整列停用登记 —— 待 Mapper 使用或标停用；IR03 不 DROP';


--
-- Name: COLUMN knowledge_article.tags_json; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.knowledge_article.tags_json IS 'V178/MC02: tags 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）';


--
-- Name: COLUMN knowledge_article.knowledge_category; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.knowledge_article.knowledge_category IS 'R11/F-KB-18 知识对象七分类封闭枚举：Fact/Concept/Procedure/Rule/Case/Evidence/Pattern（受控新增须改 CHECK + 枚举）';


--
-- Name: COLUMN knowledge_article.rule_ref; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.knowledge_article.rule_ref IS 'Rule 类知识语义根：ecos_ontology_rule.id（rule_carrier=KNOWLEDGE_ASSET）；非 Rule 类必须为空';


--
-- Name: knowledge_embedding; Type: TABLE; Schema: ecos_knowledge; Owner: -
--

CREATE TABLE ecos_knowledge.knowledge_embedding (
    id character varying(64) NOT NULL,
    document_id character varying(64),
    article_id character varying(64),
    chunk_index integer,
    chunk_text text,
    content text,
    embedding jsonb,
    model character varying(128),
    embedding_model character varying(128) DEFAULT 'bge-small-zh-v1.5'::character varying,
    token_count integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    embedding_vec public.vector(1536),
    embedding_json text,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);


--
-- Name: TABLE knowledge_embedding; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON TABLE ecos_knowledge.knowledge_embedding IS '知识向量嵌入表(kb-engine)';


--
-- Name: COLUMN knowledge_embedding.article_id; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.knowledge_embedding.article_id IS 'legacy-orphan(V182): 与 document_id 语义重叠且 Mapper 零读写 —— 待 Mapper 使用或标停用（卷04 §E.3 V182 / F04-08）；IR03 不 DROP';


--
-- Name: COLUMN knowledge_embedding.chunk_text; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.knowledge_embedding.chunk_text IS 'legacy-orphan(V182): 正文冗余列，Mapper 零读写，正源=kb_doc_chunk.content —— 待 Mapper 使用或标停用（卷04 §E.3 V182）；IR03 不 DROP';


--
-- Name: COLUMN knowledge_embedding.embedding; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.knowledge_embedding.embedding IS 'V178/MC02 定性: 存量 JSONB 列，读写切 embedding_json 后停写（IR03 只加不删，本批不 DROP）';


--
-- Name: COLUMN knowledge_embedding.model; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.knowledge_embedding.model IS 'legacy-orphan(V182): 与 embedding_model 语义重叠且 Mapper 零读写 —— 待 Mapper 使用或标停用（卷04 §E.3 V182）；IR03 不 DROP';


--
-- Name: COLUMN knowledge_embedding.embedding_vec; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.knowledge_embedding.embedding_vec IS '形态A(V185 自动化固化的 V137 列): pgvector 1536 维；未启用档由应用探测切 ecos_dw.ecos_kb_vector_fallback（形态B），vectorDegraded 显式标记';


--
-- Name: COLUMN knowledge_embedding.embedding_json; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON COLUMN ecos_knowledge.knowledge_embedding.embedding_json IS 'V178/MC02: embedding 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）';


--
-- Name: v_kb_doc_carrier_registry; Type: VIEW; Schema: ecos_knowledge; Owner: -
--

CREATE VIEW ecos_knowledge.v_kb_doc_carrier_registry AS
 SELECT carrier_schema,
    carrier_table,
    carrier_role,
    is_authoritative,
    write_authority_schema,
    converge_action
   FROM ( VALUES ('ecos_knowledge'::text,'kb_doc'::text,'文档级状态机'::text,1,'ecos_knowledge'::text,'权威保留（本批零结构变更，V180 补 DR 基线列）'::text), ('ecos_knowledge'::text,'kb_doc_chunk'::text,'切片'::text,1,'ecos_knowledge'::text,'权威保留（metadata→metadata_json 双写，V178）'::text), ('public'::text,'extraction_drafts'::text,'抽取草稿'::text,0,'ecos_knowledge'::text,'降为草稿态：只承抽取中间产物，禁作文档库写入；文档/切片读写切 kb_doc*'::text), ('ecos_dw'::text,'doc'::text,'演示遗留文档表'::text,0,'ecos_knowledge'::text,'停写：V139 "A1 目标态" 口径被 R-10① 覆写为演示遗留；不 DROP（IR03）'::text), ('ecos_dw'::text,'doc_chunk'::text,'演示遗留切片表'::text,0,'ecos_knowledge'::text,'停写：同上；metadata 存量 JSONB 列随停写一并冻结'::text), ('ecos_knowledge'::text,'knowledge_embedding'::text,'向量载体'::text,0,'ecos_knowledge'::text,'向量权威在知识层（V137/V185 形态 A），非文档载体故标 0；降级副本 ecos_dw.ecos_kb_vector_fallback 亦非权威'::text), ('public'::text,'ecos_knowledge_document'::text,'双镜像文档表'::text,0,'ecos_knowledge'::text,'K-17 双镜像 6 对之一，由 V183 镜像登记表 + V184 ecos_kb_document 处理，本视图仅占位供对账'::text)) r(carrier_schema, carrier_table, carrier_role, is_authoritative, write_authority_schema, converge_action);


--
-- Name: VIEW v_kb_doc_carrier_registry; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON VIEW ecos_knowledge.v_kb_doc_carrier_registry IS 'V186/R-10①: 文档与切片载体登记表（三→一 唯一权威的可断言口径）。is_authoritative=1 仅 kb_doc/kb_doc_chunk 两行；门禁 DocCarrierConvergenceTest#singleAuthoritativeDocTable 据此 + information_schema 对账。视图只读、不写、无数据搬移';


--
-- Name: v_kb_mirror_registry; Type: VIEW; Schema: ecos_knowledge; Owner: -
--

CREATE VIEW ecos_knowledge.v_kb_mirror_registry AS
 SELECT table_name,
    write_authority_schema,
    legacy_public_shape,
    converge_to
   FROM ( VALUES ('ecos_glossary_term'::text,'ecos_knowledge'::text,'public（V7/V143 系，BIGSERIAL+text[]）'::text,'V184 ecos_knowledge.ecos_kb_term'::text), ('ecos_knowledge_document'::text,'ecos_knowledge'::text,'public（v1 时代实表，宽度待对账）'::text,'V184 ecos_knowledge.ecos_kb_document'::text), ('ecos_knowledge_graph_node'::text,'ecos_knowledge'::text,'public（V108，id VARCHAR(64)+JSONB）'::text,'V184 ecos_knowledge.ecos_kb_graph_node'::text), ('ecos_knowledge_graph_edge'::text,'ecos_knowledge'::text,'public（live 存在，单源无 DDL）'::text,'V184 ecos_knowledge.ecos_kb_graph_edge'::text), ('ecos_marketplace_asset'::text,'ecos_knowledge'::text,'public（V7，BIGSERIAL）'::text,'V184 ecos_knowledge.ecos_kb_market_asset'::text), ('ecos_marketplace_access_request'::text,'ecos_knowledge'::text,'public（V7，BIGSERIAL；文档名 access_request）'::text,'V184 ecos_knowledge.ecos_kb_access_request'::text)) reg(table_name, write_authority_schema, legacy_public_shape, converge_to);


--
-- Name: VIEW v_kb_mirror_registry; Type: COMMENT; Schema: ecos_knowledge; Owner: -
--

COMMENT ON VIEW ecos_knowledge.v_kb_mirror_registry IS 'V183 双镜像切换登记台账（K-17 6 对）：write_authority=新写唯一侧（schema 限定）；public 侧只停写不迁（R-1 a）；converge_to=PK 宽度/形态违规对的合规新表（V184）';


--
-- Name: action_definition; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.action_definition (
    id character varying(64) NOT NULL,
    code character varying(128),
    name character varying(255),
    type character varying(16) DEFAULT 'WORKFLOW'::character varying,
    config jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE action_definition; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.action_definition IS '动作定义表(本体编译器)';


--
-- Name: ecos_business_glossary; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_business_glossary (
    id bigint NOT NULL,
    code character varying(64),
    name character varying(255) NOT NULL,
    definition text,
    domain character varying(128),
    domain_id character varying(50),
    owner character varying(128),
    status character varying(32) DEFAULT 'DRAFT'::character varying,
    tenant_id character varying(64),
    created_by character varying(128),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


--
-- Name: TABLE ecos_business_glossary; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_business_glossary IS 'WAVE_D_T20: V47 搬, 0 行 ghost. Canonical = public.ecos_business_glossary. 未来清理需走 PMO 专项 drop.';


--
-- Name: ecos_business_glossary_id_seq; Type: SEQUENCE; Schema: ecos_ontology; Owner: -
--

CREATE SEQUENCE ecos_ontology.ecos_business_glossary_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_business_glossary_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_ontology; Owner: -
--

ALTER SEQUENCE ecos_ontology.ecos_business_glossary_id_seq OWNED BY ecos_ontology.ecos_business_glossary.id;


--
-- Name: ecos_caliber; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_caliber (
    id character varying(36) NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    unit character varying(32),
    currency character varying(8),
    owner_role character varying(64),
    dimension_json text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE ecos_caliber; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_caliber IS '口径主表（REQ-ONTO-02 / F03-01；R-6 ① 口径主权归 ontology；仅 ontology 可写，cognitive/workspace/kb 经 GET /api/v1/ontology/calibers/{code}/current 只读消费）';


--
-- Name: ecos_caliber_version; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_caliber_version (
    id character varying(36) NOT NULL,
    caliber_id character varying(36) NOT NULL,
    version_no character varying(20) NOT NULL,
    formula text,
    additive smallint,
    period_granularity character varying(16),
    status character varying(16) DEFAULT 'DRAFT'::character varying NOT NULL,
    approved_by character varying(100),
    approved_at timestamp without time zone,
    git_ref character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE ecos_caliber_version; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_caliber_version IS '口径版本表（F03-01；行不可变，一次升版一行；PUBLISHED 指标绑定的 caliber_version 须 status=APPROVED，V1 门禁判据）';


--
-- Name: ecos_domain; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_domain (
    id character varying(50) NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(200) NOT NULL,
    owner character varying(100),
    description text,
    status character varying(50) DEFAULT 'Draft'::character varying,
    sort_order integer DEFAULT 0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_domain; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_domain IS 'WAVE_D_T20: V47 搬, 0 行 ghost. Canonical = public.ecos_domain. 未来清理需走 PMO 专项 drop.';


--
-- Name: ecos_entity_table_mapping; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_entity_table_mapping (
    id character varying(36) NOT NULL,
    entity_code character varying(100) NOT NULL,
    entity_name character varying(200),
    domain_code character varying(64),
    datasource_id character varying(64) DEFAULT ''::character varying NOT NULL,
    resource_name character varying(200) NOT NULL,
    table_schema character varying(64),
    field_mappings jsonb NOT NULL,
    materialized boolean DEFAULT true NOT NULL,
    doc_anchor_json jsonb,
    doc_anchor_type character varying(32),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_entity_table_mapping; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_entity_table_mapping IS '本体实体 → 物理表映射契约（本体工作台产出、知识工作台实例抽取消费）；列清单单源 = OntologyMappingDao.MAPPING_COLUMNS；唯一约束 (entity_code, datasource_id, resource_name) 是 AutoDiscoverDao.upsertMapping ON CONFLICT 的前置条件';


--
-- Name: COLUMN ecos_entity_table_mapping.field_mappings; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON COLUMN ecos_ontology.ecos_entity_table_mapping.field_mappings IS '映射契约 JSON（DR04/MC02 偏差列 D1/D2 登记对象：无 _json 后缀 + JSONB 形态，理由见脚本头部下方）';


--
-- Name: COLUMN ecos_entity_table_mapping.materialized; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON COLUMN ecos_ontology.ecos_entity_table_mapping.materialized IS '是否参与图谱实例化: TRUE=参与(默认), FALSE=显式关闭（Q2 裁决原文，V135 登记，本脚本内联为建表默认）';


--
-- Name: COLUMN ecos_entity_table_mapping.doc_anchor_json; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON COLUMN ecos_ontology.ecos_entity_table_mapping.doc_anchor_json IS '非结构化文档锚点（W2）：JSON {"docId":"...","source":"...","docChunkCount":N}；V150 改名后的最终列名';


--
-- Name: COLUMN ecos_entity_table_mapping.doc_anchor_type; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON COLUMN ecos_ontology.ecos_entity_table_mapping.doc_anchor_type IS '锚点类型：TABLE(默认)/DOC_ONLY/MIXED（W2，V145 登记）';


--
-- Name: ecos_function_audit; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_function_audit (
    id character varying(36) NOT NULL,
    function_id character varying(64),
    function_name character varying(255),
    expression text NOT NULL,
    object_type character varying(128),
    params_json text,
    result_summary text,
    duration_ms integer,
    is_cache_hit smallint DEFAULT 0 NOT NULL,
    caller_id character varying(64),
    trace_id character varying(64),
    status character varying(20) NOT NULL,
    error_message text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE ecos_function_audit; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_function_audit IS 'Function 沙箱执行审计表·合规重建（W82/C65；Kafka ecos.audit FUNCTION_EXEC 的本地兜底落点，双失败=拒收 ECOS-ONTO-061 属代码侧）；旧 public.ecos_function_audit_log 停写保留';


--
-- Name: ecos_metric_definition; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_metric_definition (
    id character varying(36) NOT NULL,
    code character varying(128) NOT NULL,
    name character varying(255),
    expression text,
    aggregation character varying(16) DEFAULT 'SUM'::character varying,
    entity_code character varying(128),
    caliber_id character varying(36),
    formula_version character varying(20),
    caliber_snapshot text,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE ecos_metric_definition; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_metric_definition IS '指标定义合规表（V168.1；MC01 主键形态；口径绑定承载表；旧 ecos_ontology.metric_definition 转停写只读过渡）';


--
-- Name: ecos_object_attachment; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_object_attachment (
    id character varying(50) NOT NULL,
    object_id character varying(100) NOT NULL,
    entity_code character varying(100),
    file_name character varying(500),
    file_path character varying(1000),
    file_size bigint,
    mime_type character varying(200),
    version_no integer DEFAULT 1,
    uploaded_by character varying(100),
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_object_attachment; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_object_attachment IS '对象附件表';


--
-- Name: ecos_object_data; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_object_data (
    id character varying(64) NOT NULL,
    entity_code character varying(128) NOT NULL,
    object_data jsonb,
    status character varying(32) DEFAULT 'ACTIVE'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE ecos_object_data; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_object_data IS '对象数据表(运行时)';


--
-- Name: ecos_object_links; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_object_links (
    id character varying(64) DEFAULT (gen_random_uuid())::text NOT NULL,
    source_id character varying(64),
    target_id character varying(64),
    relation_code character varying(128),
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_object_links; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_object_links IS '对象链接表';


--
-- Name: ecos_object_relation; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_object_relation (
    id character varying(64) NOT NULL,
    source_entity_code character varying(128),
    target_entity_code character varying(128),
    relation_type character varying(64),
    description text,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_object_relation; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_object_relation IS '对象关联表(桥接)';


--
-- Name: ecos_object_relationship; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_object_relationship (
    id character varying(50) NOT NULL,
    source_object_id character varying(100),
    target_object_id character varying(100),
    source_entity_code character varying(100),
    target_entity_code character varying(100),
    relationship_code character varying(100),
    relationship_type character varying(50),
    properties jsonb,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_object_relationship; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_object_relationship IS '对象关系表';


--
-- Name: ecos_object_state_machine; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_object_state_machine (
    id character varying(50) NOT NULL,
    entity_code character varying(100) NOT NULL,
    from_status character varying(50),
    to_status character varying(50),
    transition_code character varying(100),
    transition_name character varying(200),
    require_role character varying(200),
    guard_rule text,
    side_effect text,
    sort_order integer DEFAULT 0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_object_state_machine; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_object_state_machine IS '对象状态机表';


--
-- Name: ecos_object_timeline; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_object_timeline (
    id character varying(50) NOT NULL,
    object_id character varying(100) NOT NULL,
    entity_code character varying(100),
    event_type character varying(100),
    event_summary character varying(500),
    actor character varying(100),
    details jsonb,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_object_timeline; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_object_timeline IS '对象时间线表';


--
-- Name: ecos_object_version; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_object_version (
    id character varying(50) NOT NULL,
    object_id character varying(100) NOT NULL,
    entity_code character varying(100),
    version_no integer,
    snapshot jsonb,
    change_summary character varying(500),
    created_by character varying(100),
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE ecos_object_version; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_object_version IS '对象版本表';


--
-- Name: ecos_ontology_candidate; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_ontology_candidate (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    candidate_type character varying(30) NOT NULL,
    subject_type character varying(100),
    predicate character varying(100),
    object_type character varying(100),
    target_ref character varying(200),
    proposal_text text NOT NULL,
    evidence_refs_text text,
    confidence_value numeric(6,5),
    confidence_kind character varying(50),
    calibrated smallint,
    conflicts_text text,
    compatibility_text text,
    diff_preview_text text,
    validation_text text,
    status character varying(24) DEFAULT 'GENERATED'::character varying NOT NULL,
    proposal_ref character varying(36),
    tool_contract_ref character varying(100),
    approval_ref character varying(100),
    source_run_id character varying(36),
    trace_id character varying(64),
    idempotency_key character varying(100),
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE ecos_ontology_candidate; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_ontology_candidate IS '本体 AI 候选（补充设计 v1.0 §7.3）：候选集载体；正式发布只经既有 proposals 审批流（AI 禁直发，SUP-ONTO-02）；候选必带证据+冲突+兼容性（SUP-ONTO-01）';


--
-- Name: ecos_ontology_data; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_ontology_data (
    id character varying(64) NOT NULL,
    ontology_id character varying(64) NOT NULL,
    object_type character varying(32) NOT NULL,
    record_key character varying(255),
    payload text,
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(128),
    update_by character varying(128),
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: ecos_ontology_entity; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_ontology_entity (
    id character varying(36) NOT NULL,
    ontology_id character varying(36) NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    entity_type character varying(50),
    sort_order integer,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    domain_id character varying(50),
    tenant_id character varying(32),
    is_deleted smallint DEFAULT 0 NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    update_by character varying(128),
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE ecos_ontology_entity; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_ontology_entity IS 'WAVE_D_T20: V47 建, 0 行 ghost. Canonical = public.ecos_ontology_entity. 未来清理需走 PMO 专项 drop.';


--
-- Name: ecos_ontology_git_archive_pending; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_ontology_git_archive_pending (
    id character varying(36) NOT NULL,
    ontology_id character varying(64) NOT NULL,
    version_id character varying(64) NOT NULL,
    target_version_no character varying(20) NOT NULL,
    repository_id character varying(64) DEFAULT 'ontology'::character varying NOT NULL,
    commit_message character varying(512),
    author_name character varying(100),
    payload text,
    attempts smallint DEFAULT 0 NOT NULL,
    next_retry_at timestamp without time zone NOT NULL,
    last_error character varying(1000),
    status character varying(16) DEFAULT 'pending'::character varying NOT NULL,
    last_commit_id character varying(64),
    trace_id character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1.0.0'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: ecos_ontology_proposal; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_ontology_proposal (
    id character varying(36) NOT NULL,
    domain_code character varying(128) NOT NULL,
    proposal_type character varying(32) NOT NULL,
    target_entity character varying(256),
    payload_json text NOT NULL,
    snapshot_json text,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    author character varying(100),
    reviewer character varying(100),
    reviewer_comment character varying(512),
    version_id character varying(64),
    optimistic_lock_version integer DEFAULT 1 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE ecos_ontology_proposal; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_ontology_proposal IS '本体变更提案合规表（W72/W74；V174 起唯一写入口；旧 public.ecos_ontology_proposals 停写）';


--
-- Name: ecos_ontology_proposals; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_ontology_proposals (
    id bigint NOT NULL,
    domain_code character varying(128) NOT NULL,
    proposal_type character varying(32) NOT NULL,
    target_entity character varying(256),
    payload jsonb NOT NULL,
    snapshot jsonb,
    status character varying(16) DEFAULT 'DRAFT'::character varying NOT NULL,
    author character varying(64),
    reviewer character varying(64),
    reviewer_comment character varying(512),
    version_id bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    optimistic_lock_version integer DEFAULT 0 NOT NULL
);


--
-- Name: ecos_ontology_proposals_id_seq; Type: SEQUENCE; Schema: ecos_ontology; Owner: -
--

CREATE SEQUENCE ecos_ontology.ecos_ontology_proposals_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ecos_ontology_proposals_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_ontology; Owner: -
--

ALTER SEQUENCE ecos_ontology.ecos_ontology_proposals_id_seq OWNED BY ecos_ontology.ecos_ontology_proposals.id;


--
-- Name: ecos_workflow; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_workflow (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    status character varying(32) DEFAULT 'draft'::character varying,
    mode character varying(32) DEFAULT 'sequential'::character varying,
    nodes text DEFAULT '[]'::text,
    edges text DEFAULT '[]'::text,
    published_at timestamp without time zone,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    code character varying(64),
    workflow_type character varying(20),
    nodes_json text,
    edges_json text,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE ecos_workflow; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_workflow IS '工作流定义唯一模型表（R-7 归属①+模型 b；旧 public.ecos_workflow(v1,8 行) 与 public.ecos_workflow_v2(11 行) 停写只读，数据收敛本表）';


--
-- Name: ecos_workflow_approval; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_workflow_approval (
    id character varying(50) NOT NULL,
    task_id character varying(50),
    instance_id character varying(50),
    approver character varying(100),
    decision character varying(50),
    opinion text,
    form_data jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    form_json text,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE ecos_workflow_approval; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_workflow_approval IS '工作流审批表（W85/C69 修复载体：与主表同域 ecos_ontology；代码侧必须 schema 限定查询（F03-13），裸名即 SchemaDriftLintTest CI 红）';


--
-- Name: ecos_workflow_edge; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_workflow_edge (
    id character varying(36) NOT NULL,
    workflow_id character varying(36) NOT NULL,
    edge_key character varying(100) NOT NULL,
    source_node_key character varying(100) NOT NULL,
    target_node_key character varying(100) NOT NULL,
    sort_order integer,
    attributes_json text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE ecos_workflow_edge; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_workflow_edge IS '工作流边投影表（F03-12）';


--
-- Name: ecos_workflow_instance; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_workflow_instance (
    id character varying(50) NOT NULL,
    workflow_id character varying(50),
    workflow_name character varying(200),
    version_no character varying(20),
    status character varying(50),
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
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_workflow_instance; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_workflow_instance IS '工作流实例表';


--
-- Name: ecos_workflow_log; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_workflow_log (
    id character varying(50) NOT NULL,
    instance_id character varying(50),
    node_id character varying(100),
    node_type character varying(50),
    event_type character varying(100),
    message text,
    details jsonb,
    duration_ms bigint,
    trace_id character varying(100),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_workflow_log; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_workflow_log IS '工作流日志表';


--
-- Name: ecos_workflow_node; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_workflow_node (
    id character varying(36) NOT NULL,
    workflow_id character varying(36) NOT NULL,
    node_key character varying(100) NOT NULL,
    name character varying(255),
    node_type character varying(20),
    sort_order integer,
    attributes_json text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE ecos_workflow_node; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_workflow_node IS '工作流节点投影表（F03-12；供血缘与校验检索，主表 nodes_json 不参与 WHERE）';


--
-- Name: ecos_workflow_task; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.ecos_workflow_task (
    id character varying(50) NOT NULL,
    instance_id character varying(50),
    node_id character varying(100),
    task_type character varying(50),
    title character varying(500),
    assignee character varying(100),
    candidate_users jsonb,
    candidate_roles jsonb,
    status character varying(50) DEFAULT 'New'::character varying,
    priority character varying(20) DEFAULT 'NORMAL'::character varying,
    form_schema jsonb,
    form_data jsonb,
    result jsonb,
    agent_result jsonb,
    due_date timestamp without time zone,
    completed_at timestamp without time zone,
    completed_by character varying(100),
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ecos_workflow_task; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.ecos_workflow_task IS '工作流任务表';


--
-- Name: entity_definition; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.entity_definition (
    id character varying(64) NOT NULL,
    code character varying(128) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    category character varying(32) DEFAULT 'MASTER'::character varying,
    properties jsonb DEFAULT '[]'::jsonb,
    lifecycle jsonb DEFAULT '{}'::jsonb,
    version integer DEFAULT 1,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE entity_definition; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.entity_definition IS '实体定义表(本体编译器)';


--
-- Name: event_definition; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.event_definition (
    id character varying(64) NOT NULL,
    code character varying(128),
    source character varying(128),
    payload_schema jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE event_definition; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.event_definition IS '事件定义表(本体编译器)';


--
-- Name: metric_definition; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.metric_definition (
    id character varying(64) NOT NULL,
    code character varying(128) NOT NULL,
    name character varying(255) NOT NULL,
    expression text,
    aggregation character varying(16) DEFAULT 'SUM'::character varying,
    entity_code character varying(128),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    caliber_id character varying(36),
    formula_version character varying(20),
    caliber_snapshot text,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


--
-- Name: TABLE metric_definition; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.metric_definition IS '指标定义旧表（V52 建，id VARCHAR(64) 违 MC01、created_at 违 DR06，O-11/W77）：V168.1 起停写，新写一律 ecos_ontology.ecos_metric_definition；只加不删（IR03），清理走 PMO 专项';


--
-- Name: policy_definition; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.policy_definition (
    id character varying(64) NOT NULL,
    code character varying(128),
    type character varying(32),
    expression text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE policy_definition; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.policy_definition IS '策略定义表(本体编译器)';


--
-- Name: relationship_definition; Type: TABLE; Schema: ecos_ontology; Owner: -
--

CREATE TABLE ecos_ontology.relationship_definition (
    id character varying(64) NOT NULL,
    source_entity character varying(128) NOT NULL,
    target_entity character varying(128) NOT NULL,
    type character varying(32),
    cardinality character varying(16) DEFAULT 'ONE_TO_MANY'::character varying,
    properties jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE relationship_definition; Type: COMMENT; Schema: ecos_ontology; Owner: -
--

COMMENT ON TABLE ecos_ontology.relationship_definition IS '关系定义表(本体编译器)';


--
-- Name: agent_cost_2025_01; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_01 FOR VALUES FROM ('2025-01-01 00:00:00') TO ('2025-02-01 00:00:00');


--
-- Name: agent_cost_2025_02; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_02 FOR VALUES FROM ('2025-02-01 00:00:00') TO ('2025-03-01 00:00:00');


--
-- Name: agent_cost_2025_03; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_03 FOR VALUES FROM ('2025-03-01 00:00:00') TO ('2025-04-01 00:00:00');


--
-- Name: agent_cost_2025_04; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_04 FOR VALUES FROM ('2025-04-01 00:00:00') TO ('2025-05-01 00:00:00');


--
-- Name: agent_cost_2025_05; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_05 FOR VALUES FROM ('2025-05-01 00:00:00') TO ('2025-06-01 00:00:00');


--
-- Name: agent_cost_2025_06; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_06 FOR VALUES FROM ('2025-06-01 00:00:00') TO ('2025-07-01 00:00:00');


--
-- Name: agent_cost_2025_07; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_07 FOR VALUES FROM ('2025-07-01 00:00:00') TO ('2025-08-01 00:00:00');


--
-- Name: agent_cost_2025_08; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_08 FOR VALUES FROM ('2025-08-01 00:00:00') TO ('2025-09-01 00:00:00');


--
-- Name: agent_cost_2025_09; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_09 FOR VALUES FROM ('2025-09-01 00:00:00') TO ('2025-10-01 00:00:00');


--
-- Name: agent_cost_2025_10; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_10 FOR VALUES FROM ('2025-10-01 00:00:00') TO ('2025-11-01 00:00:00');


--
-- Name: agent_cost_2025_11; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_11 FOR VALUES FROM ('2025-11-01 00:00:00') TO ('2025-12-01 00:00:00');


--
-- Name: agent_cost_2025_12; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_12 FOR VALUES FROM ('2025-12-01 00:00:00') TO ('2026-01-01 00:00:00');


--
-- Name: agent_cost_2026_01; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_01 FOR VALUES FROM ('2026-01-01 00:00:00') TO ('2026-02-01 00:00:00');


--
-- Name: agent_cost_2026_02; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_02 FOR VALUES FROM ('2026-02-01 00:00:00') TO ('2026-03-01 00:00:00');


--
-- Name: agent_cost_2026_03; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_03 FOR VALUES FROM ('2026-03-01 00:00:00') TO ('2026-04-01 00:00:00');


--
-- Name: agent_cost_2026_04; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_04 FOR VALUES FROM ('2026-04-01 00:00:00') TO ('2026-05-01 00:00:00');


--
-- Name: agent_cost_2026_05; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_05 FOR VALUES FROM ('2026-05-01 00:00:00') TO ('2026-06-01 00:00:00');


--
-- Name: agent_cost_2026_06; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_06 FOR VALUES FROM ('2026-06-01 00:00:00') TO ('2026-07-01 00:00:00');


--
-- Name: agent_cost_2026_07; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_07 FOR VALUES FROM ('2026-07-01 00:00:00') TO ('2026-08-01 00:00:00');


--
-- Name: agent_cost_2026_08; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_08 FOR VALUES FROM ('2026-08-01 00:00:00') TO ('2026-09-01 00:00:00');


--
-- Name: agent_cost_2026_09; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_09 FOR VALUES FROM ('2026-09-01 00:00:00') TO ('2026-10-01 00:00:00');


--
-- Name: agent_cost_2026_10; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_10 FOR VALUES FROM ('2026-10-01 00:00:00') TO ('2026-11-01 00:00:00');


--
-- Name: agent_cost_2026_11; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_11 FOR VALUES FROM ('2026-11-01 00:00:00') TO ('2026-12-01 00:00:00');


--
-- Name: agent_cost_2026_12; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_12 FOR VALUES FROM ('2026-12-01 00:00:00') TO ('2027-01-01 00:00:00');


--
-- Name: agent_cost_default; Type: TABLE ATTACH; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_default DEFAULT;


--
-- Name: outbox_event_2025_01; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_01 FOR VALUES FROM ('2025-01-01 00:00:00+00') TO ('2025-02-01 00:00:00+00');


--
-- Name: outbox_event_2025_02; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_02 FOR VALUES FROM ('2025-02-01 00:00:00+00') TO ('2025-03-01 00:00:00+00');


--
-- Name: outbox_event_2025_03; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_03 FOR VALUES FROM ('2025-03-01 00:00:00+00') TO ('2025-04-01 00:00:00+00');


--
-- Name: outbox_event_2025_04; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_04 FOR VALUES FROM ('2025-04-01 00:00:00+00') TO ('2025-05-01 00:00:00+00');


--
-- Name: outbox_event_2025_05; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_05 FOR VALUES FROM ('2025-05-01 00:00:00+00') TO ('2025-06-01 00:00:00+00');


--
-- Name: outbox_event_2025_06; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_06 FOR VALUES FROM ('2025-06-01 00:00:00+00') TO ('2025-07-01 00:00:00+00');


--
-- Name: outbox_event_2025_07; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_07 FOR VALUES FROM ('2025-07-01 00:00:00+00') TO ('2025-08-01 00:00:00+00');


--
-- Name: outbox_event_2025_08; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_08 FOR VALUES FROM ('2025-08-01 00:00:00+00') TO ('2025-09-01 00:00:00+00');


--
-- Name: outbox_event_2025_09; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_09 FOR VALUES FROM ('2025-09-01 00:00:00+00') TO ('2025-10-01 00:00:00+00');


--
-- Name: outbox_event_2025_10; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_10 FOR VALUES FROM ('2025-10-01 00:00:00+00') TO ('2025-11-01 00:00:00+00');


--
-- Name: outbox_event_2025_11; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_11 FOR VALUES FROM ('2025-11-01 00:00:00+00') TO ('2025-12-01 00:00:00+00');


--
-- Name: outbox_event_2025_12; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_12 FOR VALUES FROM ('2025-12-01 00:00:00+00') TO ('2026-01-01 00:00:00+00');


--
-- Name: outbox_event_2026_01; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_01 FOR VALUES FROM ('2026-01-01 00:00:00+00') TO ('2026-02-01 00:00:00+00');


--
-- Name: outbox_event_2026_02; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_02 FOR VALUES FROM ('2026-02-01 00:00:00+00') TO ('2026-03-01 00:00:00+00');


--
-- Name: outbox_event_2026_03; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_03 FOR VALUES FROM ('2026-03-01 00:00:00+00') TO ('2026-04-01 00:00:00+00');


--
-- Name: outbox_event_2026_04; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_04 FOR VALUES FROM ('2026-04-01 00:00:00+00') TO ('2026-05-01 00:00:00+00');


--
-- Name: outbox_event_2026_05; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_05 FOR VALUES FROM ('2026-05-01 00:00:00+00') TO ('2026-06-01 00:00:00+00');


--
-- Name: outbox_event_2026_06; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_06 FOR VALUES FROM ('2026-06-01 00:00:00+00') TO ('2026-07-01 00:00:00+00');


--
-- Name: outbox_event_2026_07; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_07 FOR VALUES FROM ('2026-07-01 00:00:00+00') TO ('2026-08-01 00:00:00+00');


--
-- Name: outbox_event_2026_08; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_08 FOR VALUES FROM ('2026-08-01 00:00:00+00') TO ('2026-09-01 00:00:00+00');


--
-- Name: outbox_event_2026_09; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_09 FOR VALUES FROM ('2026-09-01 00:00:00+00') TO ('2026-10-01 00:00:00+00');


--
-- Name: outbox_event_2026_10; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_10 FOR VALUES FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00');


--
-- Name: outbox_event_2026_11; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_11 FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00');


--
-- Name: outbox_event_2026_12; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_12 FOR VALUES FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');


--
-- Name: outbox_event_2027_01; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_01 FOR VALUES FROM ('2027-01-01 00:00:00+00') TO ('2027-02-01 00:00:00+00');


--
-- Name: outbox_event_2027_02; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_02 FOR VALUES FROM ('2027-02-01 00:00:00+00') TO ('2027-03-01 00:00:00+00');


--
-- Name: outbox_event_2027_03; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_03 FOR VALUES FROM ('2027-03-01 00:00:00+00') TO ('2027-04-01 00:00:00+00');


--
-- Name: outbox_event_2027_04; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_04 FOR VALUES FROM ('2027-04-01 00:00:00+00') TO ('2027-05-01 00:00:00+00');


--
-- Name: outbox_event_2027_05; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_05 FOR VALUES FROM ('2027-05-01 00:00:00+00') TO ('2027-06-01 00:00:00+00');


--
-- Name: outbox_event_2027_06; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_06 FOR VALUES FROM ('2027-06-01 00:00:00+00') TO ('2027-07-01 00:00:00+00');


--
-- Name: outbox_event_2027_07; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_07 FOR VALUES FROM ('2027-07-01 00:00:00+00') TO ('2027-08-01 00:00:00+00');


--
-- Name: outbox_event_2027_08; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_08 FOR VALUES FROM ('2027-08-01 00:00:00+00') TO ('2027-09-01 00:00:00+00');


--
-- Name: outbox_event_2027_09; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_09 FOR VALUES FROM ('2027-09-01 00:00:00+00') TO ('2027-10-01 00:00:00+00');


--
-- Name: outbox_event_2027_10; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_10 FOR VALUES FROM ('2027-10-01 00:00:00+00') TO ('2027-11-01 00:00:00+00');


--
-- Name: outbox_event_2027_11; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_11 FOR VALUES FROM ('2027-11-01 00:00:00+00') TO ('2027-12-01 00:00:00+00');


--
-- Name: outbox_event_2027_12; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_12 FOR VALUES FROM ('2027-12-01 00:00:00+00') TO ('2028-01-01 00:00:00+00');


--
-- Name: outbox_event_default; Type: TABLE ATTACH; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_default DEFAULT;


--
-- Name: ecos_decision_case id; Type: DEFAULT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_decision_case ALTER COLUMN id SET DEFAULT nextval('ecos_ai.ecos_decision_case_id_seq'::regclass);


--
-- Name: ecos_biz_metric id; Type: DEFAULT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_metric ALTER COLUMN id SET DEFAULT nextval('ecos_cognitive.ecos_biz_metric_id_seq'::regclass);


--
-- Name: ecos_biz_target id; Type: DEFAULT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_target ALTER COLUMN id SET DEFAULT nextval('ecos_cognitive.ecos_biz_target_id_seq'::regclass);


--
-- Name: ecos_wm_causal_link id; Type: DEFAULT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_causal_link ALTER COLUMN id SET DEFAULT nextval('ecos_cognitive.ecos_wm_causal_link_id_seq'::regclass);


--
-- Name: ecos_wm_goal id; Type: DEFAULT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_goal ALTER COLUMN id SET DEFAULT nextval('ecos_cognitive.ecos_wm_goal_id_seq'::regclass);


--
-- Name: ecos_wm_goal_log id; Type: DEFAULT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_goal_log ALTER COLUMN id SET DEFAULT nextval('ecos_cognitive.ecos_wm_goal_log_id_seq'::regclass);


--
-- Name: ecos_wm_scenario id; Type: DEFAULT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_scenario ALTER COLUMN id SET DEFAULT nextval('ecos_cognitive.ecos_wm_scenario_id_seq'::regclass);


--
-- Name: ecos_alert_history id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_alert_history ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_alert_history_id_seq'::regclass);


--
-- Name: ecos_audit_log id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_audit_log ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_audit_log_id_seq'::regclass);


--
-- Name: ecos_cognitive_rule id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_cognitive_rule ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_cognitive_rule_id_seq'::regclass);


--
-- Name: ecos_dq_issue id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_dq_issue ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_dq_issue_id_seq'::regclass);


--
-- Name: ecos_dq_rule id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_dq_rule ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_dq_rule_id_seq'::regclass);


--
-- Name: ecos_function_audit_log id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_function_audit_log ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_function_audit_log_id_seq'::regclass);


--
-- Name: ecos_glossary_term id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_glossary_term ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_glossary_term_id_seq'::regclass);


--
-- Name: ecos_marketplace_access_request id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_marketplace_access_request ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_marketplace_access_request_id_seq'::regclass);


--
-- Name: ecos_marketplace_asset id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_marketplace_asset ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_marketplace_asset_id_seq'::regclass);


--
-- Name: ecos_task id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_task ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_task_id_seq'::regclass);


--
-- Name: ecos_tenant_quota id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_tenant_quota ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_tenant_quota_id_seq'::regclass);


--
-- Name: ecos_token_usage id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_token_usage ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_token_usage_id_seq'::regclass);


--
-- Name: ecos_wm_causal_link id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_wm_causal_link ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_wm_causal_link_id_seq'::regclass);


--
-- Name: ecos_wm_goal id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_wm_goal ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_wm_goal_id_seq'::regclass);


--
-- Name: ecos_wm_goal_log id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_wm_goal_log ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_wm_goal_log_id_seq'::regclass);


--
-- Name: ecos_wm_scenario id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_wm_scenario ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_wm_scenario_id_seq'::regclass);


--
-- Name: kb_cognitive_pipeline id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.kb_cognitive_pipeline ALTER COLUMN id SET DEFAULT nextval('ecos_control.kb_cognitive_pipeline_id_seq'::regclass);


--
-- Name: kb_lineage_event id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.kb_lineage_event ALTER COLUMN id SET DEFAULT nextval('ecos_control.kb_lineage_event_id_seq'::regclass);


--
-- Name: kb_ontology_snapshot id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.kb_ontology_snapshot ALTER COLUMN id SET DEFAULT nextval('ecos_control.kb_ontology_snapshot_id_seq'::regclass);


--
-- Name: schema_changes id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.schema_changes ALTER COLUMN id SET DEFAULT nextval('ecos_control.schema_changes_id_seq'::regclass);


--
-- Name: schema_snapshots id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.schema_snapshots ALTER COLUMN id SET DEFAULT nextval('ecos_control.schema_snapshots_id_seq'::regclass);


--
-- Name: sys_agent_message id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.sys_agent_message ALTER COLUMN id SET DEFAULT nextval('ecos_control.sys_agent_message_id_seq'::regclass);


--
-- Name: td_metadata_collect_log id; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_metadata_collect_log ALTER COLUMN id SET DEFAULT nextval('ecos_control.td_metadata_collect_log_id_seq'::regclass);


--
-- Name: td_runtime_task_log LOG_SEQ; Type: DEFAULT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_runtime_task_log ALTER COLUMN "LOG_SEQ" SET DEFAULT nextval('ecos_control."td_runtime_task_log_LOG_SEQ_seq"'::regclass);


--
-- Name: dq_score_snapshot id; Type: DEFAULT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.dq_score_snapshot ALTER COLUMN id SET DEFAULT nextval('ecos_data.dq_score_snapshot_id_seq'::regclass);


--
-- Name: outbox_event id; Type: DEFAULT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event ALTER COLUMN id SET DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass);


--
-- Name: kb_extract_audit id; Type: DEFAULT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.kb_extract_audit ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.kb_extract_audit_id_seq'::regclass);


--
-- Name: kb_extract_candidate id; Type: DEFAULT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.kb_extract_candidate ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.kb_extract_candidate_id_seq'::regclass);


--
-- Name: kb_extract_watermark id; Type: DEFAULT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.kb_extract_watermark ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.kb_extract_watermark_id_seq'::regclass);


--
-- Name: kb_nav_article_rel id; Type: DEFAULT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.kb_nav_article_rel ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.kb_nav_article_rel_id_seq'::regclass);


--
-- Name: kb_scheduled_extract id; Type: DEFAULT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.kb_scheduled_extract ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.kb_scheduled_extract_id_seq'::regclass);


--
-- Name: kg_sync_log id; Type: DEFAULT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.kg_sync_log ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.kg_sync_log_id_seq'::regclass);


--
-- Name: ecos_business_glossary id; Type: DEFAULT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_business_glossary ALTER COLUMN id SET DEFAULT nextval('ecos_ontology.ecos_business_glossary_id_seq'::regclass);


--
-- Name: ecos_ontology_proposals id; Type: DEFAULT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_ontology_proposals ALTER COLUMN id SET DEFAULT nextval('ecos_ontology.ecos_ontology_proposals_id_seq'::regclass);


--
-- Name: agent_approval agent_approval_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_approval
    ADD CONSTRAINT agent_approval_pkey PRIMARY KEY (id);


--
-- Name: agent_cost agent_cost_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost
    ADD CONSTRAINT agent_cost_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_01 agent_cost_2025_01_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_01
    ADD CONSTRAINT agent_cost_2025_01_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_02 agent_cost_2025_02_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_02
    ADD CONSTRAINT agent_cost_2025_02_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_03 agent_cost_2025_03_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_03
    ADD CONSTRAINT agent_cost_2025_03_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_04 agent_cost_2025_04_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_04
    ADD CONSTRAINT agent_cost_2025_04_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_05 agent_cost_2025_05_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_05
    ADD CONSTRAINT agent_cost_2025_05_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_06 agent_cost_2025_06_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_06
    ADD CONSTRAINT agent_cost_2025_06_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_07 agent_cost_2025_07_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_07
    ADD CONSTRAINT agent_cost_2025_07_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_08 agent_cost_2025_08_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_08
    ADD CONSTRAINT agent_cost_2025_08_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_09 agent_cost_2025_09_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_09
    ADD CONSTRAINT agent_cost_2025_09_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_10 agent_cost_2025_10_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_10
    ADD CONSTRAINT agent_cost_2025_10_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_11 agent_cost_2025_11_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_11
    ADD CONSTRAINT agent_cost_2025_11_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_12 agent_cost_2025_12_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_12
    ADD CONSTRAINT agent_cost_2025_12_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_01 agent_cost_2026_01_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_01
    ADD CONSTRAINT agent_cost_2026_01_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_02 agent_cost_2026_02_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_02
    ADD CONSTRAINT agent_cost_2026_02_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_03 agent_cost_2026_03_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_03
    ADD CONSTRAINT agent_cost_2026_03_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_04 agent_cost_2026_04_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_04
    ADD CONSTRAINT agent_cost_2026_04_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_05 agent_cost_2026_05_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_05
    ADD CONSTRAINT agent_cost_2026_05_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_06 agent_cost_2026_06_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_06
    ADD CONSTRAINT agent_cost_2026_06_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_07 agent_cost_2026_07_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_07
    ADD CONSTRAINT agent_cost_2026_07_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_08 agent_cost_2026_08_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_08
    ADD CONSTRAINT agent_cost_2026_08_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_09 agent_cost_2026_09_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_09
    ADD CONSTRAINT agent_cost_2026_09_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_10 agent_cost_2026_10_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_10
    ADD CONSTRAINT agent_cost_2026_10_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_11 agent_cost_2026_11_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_11
    ADD CONSTRAINT agent_cost_2026_11_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_12 agent_cost_2026_12_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_12
    ADD CONSTRAINT agent_cost_2026_12_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_default agent_cost_default_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_cost_default
    ADD CONSTRAINT agent_cost_default_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_definition agent_definition_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_definition
    ADD CONSTRAINT agent_definition_pkey PRIMARY KEY (id);


--
-- Name: agent_evaluation agent_evaluation_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_evaluation
    ADD CONSTRAINT agent_evaluation_pkey PRIMARY KEY (id);


--
-- Name: agent_execution agent_execution_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_execution
    ADD CONSTRAINT agent_execution_pkey PRIMARY KEY (id);


--
-- Name: agent_execution_step agent_execution_step_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_execution_step
    ADD CONSTRAINT agent_execution_step_pkey PRIMARY KEY (id);


--
-- Name: agent_governance_policy agent_governance_policy_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_governance_policy
    ADD CONSTRAINT agent_governance_policy_pkey PRIMARY KEY (id);


--
-- Name: agent_memory agent_memory_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_memory
    ADD CONSTRAINT agent_memory_pkey PRIMARY KEY (id);


--
-- Name: agent_registry agent_registry_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_registry
    ADD CONSTRAINT agent_registry_pkey PRIMARY KEY (id);


--
-- Name: causal_edge causal_edge_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.causal_edge
    ADD CONSTRAINT causal_edge_pkey PRIMARY KEY (id);


--
-- Name: ecos_agent ecos_agent_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_agent
    ADD CONSTRAINT ecos_agent_pkey PRIMARY KEY (id);


--
-- Name: ecos_agent_registry ecos_agent_registry_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_agent_registry
    ADD CONSTRAINT ecos_agent_registry_pkey PRIMARY KEY (id);


--
-- Name: ecos_ai_capability_task ecos_ai_capability_task_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_ai_capability_task
    ADD CONSTRAINT ecos_ai_capability_task_pkey PRIMARY KEY (id);


--
-- Name: ecos_aip_pipeline ecos_aip_pipeline_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_aip_pipeline
    ADD CONSTRAINT ecos_aip_pipeline_pkey PRIMARY KEY (id);


--
-- Name: ecos_decision_case ecos_decision_case_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_decision_case
    ADD CONSTRAINT ecos_decision_case_pkey PRIMARY KEY (id);


--
-- Name: ecos_evolution_log ecos_evolution_log_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_evolution_log
    ADD CONSTRAINT ecos_evolution_log_pkey PRIMARY KEY (id);


--
-- Name: ecos_guardrail_policy ecos_guardrail_policy_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_guardrail_policy
    ADD CONSTRAINT ecos_guardrail_policy_pkey PRIMARY KEY (id);


--
-- Name: ecos_mission ecos_mission_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_mission
    ADD CONSTRAINT ecos_mission_pkey PRIMARY KEY (id);


--
-- Name: ecos_mission_task ecos_mission_task_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_mission_task
    ADD CONSTRAINT ecos_mission_task_pkey PRIMARY KEY (id);


--
-- Name: ecos_tool_definition ecos_tool_definition_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_tool_definition
    ADD CONSTRAINT ecos_tool_definition_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_action_effect ecos_wagent_action_effect_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_action_effect
    ADD CONSTRAINT ecos_wagent_action_effect_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_action ecos_wagent_action_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_action
    ADD CONSTRAINT ecos_wagent_action_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_candidate ecos_wagent_candidate_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_candidate
    ADD CONSTRAINT ecos_wagent_candidate_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_candidate_review ecos_wagent_candidate_review_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_candidate_review
    ADD CONSTRAINT ecos_wagent_candidate_review_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_claim ecos_wagent_claim_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_claim
    ADD CONSTRAINT ecos_wagent_claim_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_decision_option ecos_wagent_decision_option_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_decision_option
    ADD CONSTRAINT ecos_wagent_decision_option_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_decision ecos_wagent_decision_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_decision
    ADD CONSTRAINT ecos_wagent_decision_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_evidence ecos_wagent_evidence_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_evidence
    ADD CONSTRAINT ecos_wagent_evidence_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_goal ecos_wagent_goal_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_goal
    ADD CONSTRAINT ecos_wagent_goal_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_mission ecos_wagent_mission_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_mission
    ADD CONSTRAINT ecos_wagent_mission_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_question ecos_wagent_question_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_question
    ADD CONSTRAINT ecos_wagent_question_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_readiness_gap ecos_wagent_readiness_gap_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_readiness_gap
    ADD CONSTRAINT ecos_wagent_readiness_gap_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_readiness_item ecos_wagent_readiness_item_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_readiness_item
    ADD CONSTRAINT ecos_wagent_readiness_item_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_readiness ecos_wagent_readiness_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_readiness
    ADD CONSTRAINT ecos_wagent_readiness_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_run_event ecos_wagent_run_event_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_run_event
    ADD CONSTRAINT ecos_wagent_run_event_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_run ecos_wagent_run_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_run
    ADD CONSTRAINT ecos_wagent_run_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_run_step ecos_wagent_run_step_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_run_step
    ADD CONSTRAINT ecos_wagent_run_step_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_skill ecos_wagent_skill_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_skill
    ADD CONSTRAINT ecos_wagent_skill_pkey PRIMARY KEY (id);


--
-- Name: ecos_wagent_tool_contract ecos_wagent_tool_contract_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_tool_contract
    ADD CONSTRAINT ecos_wagent_tool_contract_pkey PRIMARY KEY (id);


--
-- Name: forecast forecast_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.forecast
    ADD CONSTRAINT forecast_pkey PRIMARY KEY (id);


--
-- Name: optimization_job optimization_job_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.optimization_job
    ADD CONSTRAINT optimization_job_pkey PRIMARY KEY (id);


--
-- Name: ecos_ai_guardrail_decision_ledger pk_agdl; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_ai_guardrail_decision_ledger
    ADD CONSTRAINT pk_agdl PRIMARY KEY (id);


--
-- Name: ecos_ai_tool_permission_binding pk_ai_tool_perm; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_ai_tool_permission_binding
    ADD CONSTRAINT pk_ai_tool_perm PRIMARY KEY (id);


--
-- Name: ecos_business_scenario pk_ecos_ai_business_scenario; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_business_scenario
    ADD CONSTRAINT pk_ecos_ai_business_scenario PRIMARY KEY (id);


--
-- Name: ecos_ai_compression_batch pk_ecos_ai_compression_batch; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_ai_compression_batch
    ADD CONSTRAINT pk_ecos_ai_compression_batch PRIMARY KEY (id);


--
-- Name: ecos_ai_tool_registry pk_ecos_ai_tool_registry; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_ai_tool_registry
    ADD CONSTRAINT pk_ecos_ai_tool_registry PRIMARY KEY (id);


--
-- Name: ecos_wagent_claim_evidence pk_wagent_claim_evidence; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_wagent_claim_evidence
    ADD CONSTRAINT pk_wagent_claim_evidence PRIMARY KEY (claim_id, evidence_id);


--
-- Name: scenario scenario_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.scenario
    ADD CONSTRAINT scenario_pkey PRIMARY KEY (id);


--
-- Name: simulation simulation_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.simulation
    ADD CONSTRAINT simulation_pkey PRIMARY KEY (id);


--
-- Name: simulation_result simulation_result_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.simulation_result
    ADD CONSTRAINT simulation_result_pkey PRIMARY KEY (id);


--
-- Name: strategy_recommendation strategy_recommendation_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.strategy_recommendation
    ADD CONSTRAINT strategy_recommendation_pkey PRIMARY KEY (id);


--
-- Name: sys_agent_call_log sys_agent_call_log_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.sys_agent_call_log
    ADD CONSTRAINT sys_agent_call_log_pkey PRIMARY KEY (id);


--
-- Name: sys_agent_profile sys_agent_profile_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.sys_agent_profile
    ADD CONSTRAINT sys_agent_profile_pkey PRIMARY KEY (id);


--
-- Name: world_snapshot world_snapshot_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.world_snapshot
    ADD CONSTRAINT world_snapshot_pkey PRIMARY KEY (id);


--
-- Name: world_state world_state_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.world_state
    ADD CONSTRAINT world_state_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_contract ecos_biz_contract_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_contract
    ADD CONSTRAINT ecos_biz_contract_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_department ecos_biz_department_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_department
    ADD CONSTRAINT ecos_biz_department_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_metric ecos_biz_metric_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_metric
    ADD CONSTRAINT ecos_biz_metric_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_project ecos_biz_project_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_project
    ADD CONSTRAINT ecos_biz_project_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_target ecos_biz_target_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_target
    ADD CONSTRAINT ecos_biz_target_pkey PRIMARY KEY (id);


--
-- Name: ecos_cognitive_framework ecos_cognitive_framework_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_framework
    ADD CONSTRAINT ecos_cognitive_framework_pkey PRIMARY KEY (id);


--
-- Name: ecos_cognitive_outcome_evaluation ecos_cognitive_outcome_evaluation_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_outcome_evaluation
    ADD CONSTRAINT ecos_cognitive_outcome_evaluation_pkey PRIMARY KEY (id);


--
-- Name: ecos_goal_tracking ecos_goal_tracking_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_goal_tracking
    ADD CONSTRAINT ecos_goal_tracking_pkey PRIMARY KEY (id);


--
-- Name: ecos_wm_causal_link ecos_wm_causal_link_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_causal_link
    ADD CONSTRAINT ecos_wm_causal_link_pkey PRIMARY KEY (id);


--
-- Name: ecos_wm_goal_log ecos_wm_goal_log_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_goal_log
    ADD CONSTRAINT ecos_wm_goal_log_pkey PRIMARY KEY (id);


--
-- Name: ecos_wm_goal ecos_wm_goal_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_goal
    ADD CONSTRAINT ecos_wm_goal_pkey PRIMARY KEY (id);


--
-- Name: ecos_wm_scenario ecos_wm_scenario_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_scenario
    ADD CONSTRAINT ecos_wm_scenario_pkey PRIMARY KEY (id);


--
-- Name: ecos_world_scenarios ecos_world_scenarios_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_world_scenarios
    ADD CONSTRAINT ecos_world_scenarios_pkey PRIMARY KEY (id);


--
-- Name: ecos_cognitive_mind_update pk_ecos_cognitive_mind_update; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_mind_update
    ADD CONSTRAINT pk_ecos_cognitive_mind_update PRIMARY KEY (id);


--
-- Name: ecos_scenario_binding_link pk_pg_mirror_binding_link; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_scenario_binding_link
    ADD CONSTRAINT pk_pg_mirror_binding_link PRIMARY KEY (id);


--
-- Name: ecos_business_scenario pk_pg_mirror_business_scenario; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_business_scenario
    ADD CONSTRAINT pk_pg_mirror_business_scenario PRIMARY KEY (id);


--
-- Name: ecos_cognitive_belief pk_pg_mirror_cognitive_belief; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_belief
    ADD CONSTRAINT pk_pg_mirror_cognitive_belief PRIMARY KEY (id);


--
-- Name: ecos_cognitive_evidence pk_pg_mirror_cognitive_evidence; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_evidence
    ADD CONSTRAINT pk_pg_mirror_cognitive_evidence PRIMARY KEY (id);


--
-- Name: ecos_cognitive_hypothesis pk_pg_mirror_cognitive_hypothesis; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_hypothesis
    ADD CONSTRAINT pk_pg_mirror_cognitive_hypothesis PRIMARY KEY (id);


--
-- Name: ecos_cognitive_mind pk_pg_mirror_cognitive_mind; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_mind
    ADD CONSTRAINT pk_pg_mirror_cognitive_mind PRIMARY KEY (id);


--
-- Name: ecos_cognitive_model pk_pg_mirror_cognitive_model; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_model
    ADD CONSTRAINT pk_pg_mirror_cognitive_model PRIMARY KEY (id);


--
-- Name: ecos_cognitive_pipeline pk_pg_mirror_cognitive_pipeline; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_pipeline
    ADD CONSTRAINT pk_pg_mirror_cognitive_pipeline PRIMARY KEY (id);


--
-- Name: ecos_cognitive_scenario_mind pk_pg_mirror_cognitive_scn_mind; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_scenario_mind
    ADD CONSTRAINT pk_pg_mirror_cognitive_scn_mind PRIMARY KEY (id);


--
-- Name: ecos_cognitive_run_invalidation pk_pg_mirror_run_invalidation; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_cognitive_run_invalidation
    ADD CONSTRAINT pk_pg_mirror_run_invalidation PRIMARY KEY (id);


--
-- Name: ecos_scenario_binding pk_pg_mirror_scenario_binding; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_scenario_binding
    ADD CONSTRAINT pk_pg_mirror_scenario_binding PRIMARY KEY (id);


--
-- Name: demo_customer demo_customer_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.demo_customer
    ADD CONSTRAINT demo_customer_pkey PRIMARY KEY (id);


--
-- Name: demo_invoice demo_invoice_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.demo_invoice
    ADD CONSTRAINT demo_invoice_pkey PRIMARY KEY (id);


--
-- Name: demo_supplier demo_supplier_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.demo_supplier
    ADD CONSTRAINT demo_supplier_pkey PRIMARY KEY (id);


--
-- Name: dict_column dict_column_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.dict_column
    ADD CONSTRAINT dict_column_pkey PRIMARY KEY (id);


--
-- Name: dict_table dict_table_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.dict_table
    ADD CONSTRAINT dict_table_pkey PRIMARY KEY (id);


--
-- Name: ecos_agent ecos_agent_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_agent
    ADD CONSTRAINT ecos_agent_pkey PRIMARY KEY (id);


--
-- Name: ecos_alert_history ecos_alert_history_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_alert_history
    ADD CONSTRAINT ecos_alert_history_pkey PRIMARY KEY (id);


--
-- Name: ecos_audit_log ecos_audit_log_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_audit_log
    ADD CONSTRAINT ecos_audit_log_pkey PRIMARY KEY (id);


--
-- Name: ecos_business_scenario ecos_business_scenario_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_business_scenario
    ADD CONSTRAINT ecos_business_scenario_pkey PRIMARY KEY (id);


--
-- Name: ecos_cls_policy ecos_cls_policy_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_cls_policy
    ADD CONSTRAINT ecos_cls_policy_pkey PRIMARY KEY (id);


--
-- Name: ecos_cognitive_belief ecos_cognitive_belief_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_cognitive_belief
    ADD CONSTRAINT ecos_cognitive_belief_pkey PRIMARY KEY (id);


--
-- Name: ecos_cognitive_evidence ecos_cognitive_evidence_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_cognitive_evidence
    ADD CONSTRAINT ecos_cognitive_evidence_pkey PRIMARY KEY (id);


--
-- Name: ecos_cognitive_hypothesis ecos_cognitive_hypothesis_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_cognitive_hypothesis
    ADD CONSTRAINT ecos_cognitive_hypothesis_pkey PRIMARY KEY (id);


--
-- Name: ecos_cognitive_model ecos_cognitive_model_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_cognitive_model
    ADD CONSTRAINT ecos_cognitive_model_pkey PRIMARY KEY (id);


--
-- Name: ecos_cognitive_rule ecos_cognitive_rule_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_cognitive_rule
    ADD CONSTRAINT ecos_cognitive_rule_pkey PRIMARY KEY (id);


--
-- Name: ecos_cognitive_run_invalidation ecos_cognitive_run_invalidation_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_cognitive_run_invalidation
    ADD CONSTRAINT ecos_cognitive_run_invalidation_pkey PRIMARY KEY (id);


--
-- Name: ecos_decision_approval ecos_decision_approval_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_decision_approval
    ADD CONSTRAINT ecos_decision_approval_pkey PRIMARY KEY (id);


--
-- Name: ecos_decision_causal_link ecos_decision_causal_link_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_decision_causal_link
    ADD CONSTRAINT ecos_decision_causal_link_pkey PRIMARY KEY (id);


--
-- Name: ecos_decision_exception ecos_decision_exception_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_decision_exception
    ADD CONSTRAINT ecos_decision_exception_pkey PRIMARY KEY (id);


--
-- Name: ecos_decision ecos_decision_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_decision
    ADD CONSTRAINT ecos_decision_pkey PRIMARY KEY (id);


--
-- Name: ecos_decision_policy ecos_decision_policy_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_decision_policy
    ADD CONSTRAINT ecos_decision_policy_pkey PRIMARY KEY (id);


--
-- Name: ecos_decision_precedent ecos_decision_precedent_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_decision_precedent
    ADD CONSTRAINT ecos_decision_precedent_pkey PRIMARY KEY (id);


--
-- Name: ecos_decision_record ecos_decision_record_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_decision_record
    ADD CONSTRAINT ecos_decision_record_pkey PRIMARY KEY (id);


--
-- Name: ecos_dq_execution_result ecos_dq_execution_result_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_dq_execution_result
    ADD CONSTRAINT ecos_dq_execution_result_pkey PRIMARY KEY (id);


--
-- Name: ecos_dq_issue ecos_dq_issue_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_dq_issue
    ADD CONSTRAINT ecos_dq_issue_pkey PRIMARY KEY (id);


--
-- Name: ecos_dq_rule ecos_dq_rule_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_dq_rule
    ADD CONSTRAINT ecos_dq_rule_pkey PRIMARY KEY (id);


--
-- Name: ecos_function_audit_log ecos_function_audit_log_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_function_audit_log
    ADD CONSTRAINT ecos_function_audit_log_pkey PRIMARY KEY (id);


--
-- Name: ecos_git_repo ecos_git_repo_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_git_repo
    ADD CONSTRAINT ecos_git_repo_pkey PRIMARY KEY (id);


--
-- Name: ecos_glossary_term ecos_glossary_term_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_glossary_term
    ADD CONSTRAINT ecos_glossary_term_pkey PRIMARY KEY (id);


--
-- Name: ecos_goal_tracking ecos_goal_tracking_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_goal_tracking
    ADD CONSTRAINT ecos_goal_tracking_pkey PRIMARY KEY (id);


--
-- Name: ecos_interface_ref ecos_interface_ref_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_interface_ref
    ADD CONSTRAINT ecos_interface_ref_pkey PRIMARY KEY (id);


--
-- Name: ecos_marketplace_access_request ecos_marketplace_access_request_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_marketplace_access_request
    ADD CONSTRAINT ecos_marketplace_access_request_pkey PRIMARY KEY (id);


--
-- Name: ecos_marketplace_asset ecos_marketplace_asset_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_marketplace_asset
    ADD CONSTRAINT ecos_marketplace_asset_pkey PRIMARY KEY (id);


--
-- Name: ecos_mask_strategy ecos_mask_strategy_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_mask_strategy
    ADD CONSTRAINT ecos_mask_strategy_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_attachment ecos_object_attachment_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_object_attachment
    ADD CONSTRAINT ecos_object_attachment_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_links ecos_object_links_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_object_links
    ADD CONSTRAINT ecos_object_links_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_relationship ecos_object_relationship_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_object_relationship
    ADD CONSTRAINT ecos_object_relationship_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_state_machine ecos_object_state_machine_entity_code_from_status_transitio_key; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_object_state_machine
    ADD CONSTRAINT ecos_object_state_machine_entity_code_from_status_transitio_key UNIQUE (entity_code, from_status, transition_code);


--
-- Name: ecos_object_state_machine ecos_object_state_machine_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_object_state_machine
    ADD CONSTRAINT ecos_object_state_machine_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_timeline ecos_object_timeline_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_object_timeline
    ADD CONSTRAINT ecos_object_timeline_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology_action ecos_ontology_action_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_ontology_action
    ADD CONSTRAINT ecos_ontology_action_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology_entity ecos_ontology_entity_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_ontology_entity
    ADD CONSTRAINT ecos_ontology_entity_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology_property ecos_ontology_property_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_ontology_property
    ADD CONSTRAINT ecos_ontology_property_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology_relationship ecos_ontology_relationship_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_ontology_relationship
    ADD CONSTRAINT ecos_ontology_relationship_pkey PRIMARY KEY (id);


--
-- Name: ecos_pareto_problem ecos_pareto_problem_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_pareto_problem
    ADD CONSTRAINT ecos_pareto_problem_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_edge ecos_pipeline_edge_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_pipeline_edge
    ADD CONSTRAINT ecos_pipeline_edge_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_node ecos_pipeline_node_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_pipeline_node
    ADD CONSTRAINT ecos_pipeline_node_pkey PRIMARY KEY (id);


--
-- Name: ecos_provenance_entry ecos_provenance_entry_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_provenance_entry
    ADD CONSTRAINT ecos_provenance_entry_pkey PRIMARY KEY (id);


--
-- Name: ecos_rls_policy ecos_rls_policy_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_rls_policy
    ADD CONSTRAINT ecos_rls_policy_pkey PRIMARY KEY (id);


--
-- Name: ecos_runtime_audit_retry ecos_runtime_audit_retry_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_runtime_audit_retry
    ADD CONSTRAINT ecos_runtime_audit_retry_pkey PRIMARY KEY (id);


--
-- Name: ecos_runtime_event_dlq ecos_runtime_event_dlq_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_runtime_event_dlq
    ADD CONSTRAINT ecos_runtime_event_dlq_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_binding_link ecos_scenario_binding_link_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_scenario_binding_link
    ADD CONSTRAINT ecos_scenario_binding_link_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_binding ecos_scenario_binding_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_scenario_binding
    ADD CONSTRAINT ecos_scenario_binding_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_blueprint ecos_scenario_blueprint_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_scenario_blueprint
    ADD CONSTRAINT ecos_scenario_blueprint_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_mind ecos_scenario_mind_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_scenario_mind
    ADD CONSTRAINT ecos_scenario_mind_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_run ecos_scenario_run_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_scenario_run
    ADD CONSTRAINT ecos_scenario_run_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_sandbox_layout ecos_scenario_sandbox_layout_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_scenario_sandbox_layout
    ADD CONSTRAINT ecos_scenario_sandbox_layout_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_sandbox_layout ecos_scenario_sandbox_layout_scenario_id_key; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_scenario_sandbox_layout
    ADD CONSTRAINT ecos_scenario_sandbox_layout_scenario_id_key UNIQUE (scenario_id);


--
-- Name: ecos_security_asset ecos_security_asset_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_security_asset
    ADD CONSTRAINT ecos_security_asset_pkey PRIMARY KEY (id);


--
-- Name: ecos_security_crypto_key ecos_security_crypto_key_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_security_crypto_key
    ADD CONSTRAINT ecos_security_crypto_key_pkey PRIMARY KEY (id);


--
-- Name: ecos_security_policy ecos_security_policy_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_security_policy
    ADD CONSTRAINT ecos_security_policy_pkey PRIMARY KEY (id);


--
-- Name: ecos_spans ecos_spans_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_spans
    ADD CONSTRAINT ecos_spans_pkey PRIMARY KEY (span_id);


--
-- Name: ecos_task ecos_task_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_task
    ADD CONSTRAINT ecos_task_pkey PRIMARY KEY (id);


--
-- Name: ecos_tenant ecos_tenant_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_tenant
    ADD CONSTRAINT ecos_tenant_pkey PRIMARY KEY (id);


--
-- Name: ecos_tenant_quota ecos_tenant_quota_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_tenant_quota
    ADD CONSTRAINT ecos_tenant_quota_pkey PRIMARY KEY (id);


--
-- Name: ecos_tenant ecos_tenant_tenant_code_key; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_tenant
    ADD CONSTRAINT ecos_tenant_tenant_code_key UNIQUE (tenant_code);


--
-- Name: ecos_tenant_usage ecos_tenant_usage_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_tenant_usage
    ADD CONSTRAINT ecos_tenant_usage_pkey PRIMARY KEY (tenant_id, usage_date, quota_type);


--
-- Name: ecos_token_usage ecos_token_usage_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_token_usage
    ADD CONSTRAINT ecos_token_usage_pkey PRIMARY KEY (id);


--
-- Name: ecos_warn_log ecos_warn_log_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_warn_log
    ADD CONSTRAINT ecos_warn_log_pkey PRIMARY KEY (id);


--
-- Name: ecos_wm_causal_link ecos_wm_causal_link_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_wm_causal_link
    ADD CONSTRAINT ecos_wm_causal_link_pkey PRIMARY KEY (id);


--
-- Name: ecos_wm_goal_log ecos_wm_goal_log_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_wm_goal_log
    ADD CONSTRAINT ecos_wm_goal_log_pkey PRIMARY KEY (id);


--
-- Name: ecos_wm_goal ecos_wm_goal_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_wm_goal
    ADD CONSTRAINT ecos_wm_goal_pkey PRIMARY KEY (id);


--
-- Name: ecos_wm_scenario ecos_wm_scenario_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_wm_scenario
    ADD CONSTRAINT ecos_wm_scenario_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_approval ecos_workflow_approval_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_workflow_approval
    ADD CONSTRAINT ecos_workflow_approval_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_instance ecos_workflow_instance_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_workflow_instance
    ADD CONSTRAINT ecos_workflow_instance_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_log ecos_workflow_log_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_workflow_log
    ADD CONSTRAINT ecos_workflow_log_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow ecos_workflow_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_workflow
    ADD CONSTRAINT ecos_workflow_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_task ecos_workflow_task_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_workflow_task
    ADD CONSTRAINT ecos_workflow_task_pkey PRIMARY KEY (id);


--
-- Name: ecos_world_scenarios ecos_world_scenarios_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_world_scenarios
    ADD CONSTRAINT ecos_world_scenarios_pkey PRIMARY KEY (id);


--
-- Name: kb_cognitive_pipeline kb_cognitive_pipeline_pipeline_id_key; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.kb_cognitive_pipeline
    ADD CONSTRAINT kb_cognitive_pipeline_pipeline_id_key UNIQUE (pipeline_id);


--
-- Name: kb_cognitive_pipeline kb_cognitive_pipeline_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.kb_cognitive_pipeline
    ADD CONSTRAINT kb_cognitive_pipeline_pkey PRIMARY KEY (id);


--
-- Name: kb_lineage_event kb_lineage_event_event_id_key; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.kb_lineage_event
    ADD CONSTRAINT kb_lineage_event_event_id_key UNIQUE (event_id);


--
-- Name: kb_lineage_event kb_lineage_event_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.kb_lineage_event
    ADD CONSTRAINT kb_lineage_event_pkey PRIMARY KEY (id);


--
-- Name: kb_ontology_snapshot kb_ontology_snapshot_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.kb_ontology_snapshot
    ADD CONSTRAINT kb_ontology_snapshot_pkey PRIMARY KEY (id);


--
-- Name: cache_invalidation_outbox pk_cache_invalidation_outbox; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.cache_invalidation_outbox
    ADD CONSTRAINT pk_cache_invalidation_outbox PRIMARY KEY (id);


--
-- Name: schema_changes schema_changes_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.schema_changes
    ADD CONSTRAINT schema_changes_pkey PRIMARY KEY (id);


--
-- Name: schema_snapshots schema_snapshots_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.schema_snapshots
    ADD CONSTRAINT schema_snapshots_pkey PRIMARY KEY (id);


--
-- Name: sys_agent_message sys_agent_message_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.sys_agent_message
    ADD CONSTRAINT sys_agent_message_pkey PRIMARY KEY (id);


--
-- Name: sys_agent_session sys_agent_session_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.sys_agent_session
    ADD CONSTRAINT sys_agent_session_pkey PRIMARY KEY (id);


--
-- Name: sys_compliance_rule sys_compliance_rule_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.sys_compliance_rule
    ADD CONSTRAINT sys_compliance_rule_pkey PRIMARY KEY (id);


--
-- Name: sys_config sys_config_config_key_key; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.sys_config
    ADD CONSTRAINT sys_config_config_key_key UNIQUE (config_key);


--
-- Name: sys_config sys_config_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.sys_config
    ADD CONSTRAINT sys_config_pkey PRIMARY KEY (id);


--
-- Name: sys_dict sys_dict_dict_type_dict_code_key; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.sys_dict
    ADD CONSTRAINT sys_dict_dict_type_dict_code_key UNIQUE (dict_type, dict_code);


--
-- Name: sys_dict sys_dict_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.sys_dict
    ADD CONSTRAINT sys_dict_pkey PRIMARY KEY (id);


--
-- Name: sys_extraction_source sys_extraction_source_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.sys_extraction_source
    ADD CONSTRAINT sys_extraction_source_pkey PRIMARY KEY (id);


--
-- Name: sys_token_blacklist sys_token_blacklist_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.sys_token_blacklist
    ADD CONSTRAINT sys_token_blacklist_pkey PRIMARY KEY (token);


--
-- Name: td_abac_policy td_abac_policy_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_abac_policy
    ADD CONSTRAINT td_abac_policy_pkey PRIMARY KEY (policy_id);


--
-- Name: td_audit_log td_audit_log_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_audit_log
    ADD CONSTRAINT td_audit_log_pkey PRIMARY KEY (log_id);


--
-- Name: td_catalog_item td_catalog_item_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_catalog_item
    ADD CONSTRAINT td_catalog_item_pkey PRIMARY KEY (catalog_id);


--
-- Name: td_config td_config_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_config
    ADD CONSTRAINT td_config_pkey PRIMARY KEY (config_id);


--
-- Name: td_data_field td_data_field_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_data_field
    ADD CONSTRAINT td_data_field_pkey PRIMARY KEY (field_id);


--
-- Name: td_data_resource td_data_resource_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_data_resource
    ADD CONSTRAINT td_data_resource_pkey PRIMARY KEY (resource_id);


--
-- Name: td_datasource td_datasource_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_datasource
    ADD CONSTRAINT td_datasource_pkey PRIMARY KEY (datasource_id);


--
-- Name: td_dw_write_channel_idempotency td_dw_write_channel_idempotency_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_dw_write_channel_idempotency
    ADD CONSTRAINT td_dw_write_channel_idempotency_pkey PRIMARY KEY (id);


--
-- Name: td_metadata_collect_log td_metadata_collect_log_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_metadata_collect_log
    ADD CONSTRAINT td_metadata_collect_log_pkey PRIMARY KEY (id);


--
-- Name: td_org_permission td_org_permission_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_org_permission
    ADD CONSTRAINT td_org_permission_pkey PRIMARY KEY (permission_id);


--
-- Name: td_organization td_organization_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_organization
    ADD CONSTRAINT td_organization_pkey PRIMARY KEY (org_id);


--
-- Name: td_permission td_permission_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_permission
    ADD CONSTRAINT td_permission_pkey PRIMARY KEY (permission_id);


--
-- Name: td_role_permission td_role_permission_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_role_permission
    ADD CONSTRAINT td_role_permission_pkey PRIMARY KEY (role_id, permission_id);


--
-- Name: td_role td_role_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_role
    ADD CONSTRAINT td_role_pkey PRIMARY KEY (role_id);


--
-- Name: td_role_security_profile td_role_security_profile_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_role_security_profile
    ADD CONSTRAINT td_role_security_profile_pkey PRIMARY KEY (role_id);


--
-- Name: td_runtime_task_execution td_runtime_task_execution_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_runtime_task_execution
    ADD CONSTRAINT td_runtime_task_execution_pkey PRIMARY KEY ("EXECUTION_ID");


--
-- Name: td_runtime_task_log td_runtime_task_log_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_runtime_task_log
    ADD CONSTRAINT td_runtime_task_log_pkey PRIMARY KEY ("LOG_SEQ");


--
-- Name: td_runtime_task td_runtime_task_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_runtime_task
    ADD CONSTRAINT td_runtime_task_pkey PRIMARY KEY ("TASK_ID");


--
-- Name: td_runtime_task_plan td_runtime_task_plan_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_runtime_task_plan
    ADD CONSTRAINT td_runtime_task_plan_pkey PRIMARY KEY ("TASK_ID");


--
-- Name: td_runtime_task_status td_runtime_task_status_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_runtime_task_status
    ADD CONSTRAINT td_runtime_task_status_pkey PRIMARY KEY ("TASK_ID");


--
-- Name: td_user_organization td_user_organization_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_user_organization
    ADD CONSTRAINT td_user_organization_pkey PRIMARY KEY (user_id, org_id);


--
-- Name: td_user td_user_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_user
    ADD CONSTRAINT td_user_pkey PRIMARY KEY (user_id);


--
-- Name: td_user_role td_user_role_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_user_role
    ADD CONSTRAINT td_user_role_pkey PRIMARY KEY (user_id, role_id);


--
-- Name: td_user_security_profile td_user_security_profile_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_user_security_profile
    ADD CONSTRAINT td_user_security_profile_pkey PRIMARY KEY (user_id);


--
-- Name: ecos_mask_strategy uk_mask_strategy; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_mask_strategy
    ADD CONSTRAINT uk_mask_strategy UNIQUE (tenant_id, strategy_code);


--
-- Name: ecos_interface_ref uq_ifc_ref_type_name; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_interface_ref
    ADD CONSTRAINT uq_ifc_ref_type_name UNIQUE (interface_type, name);


--
-- Name: kb_ontology_snapshot uq_kb_ontology_snapshot_ontology_version; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.kb_ontology_snapshot
    ADD CONSTRAINT uq_kb_ontology_snapshot_ontology_version UNIQUE (ontology_id, version);


--
-- Name: ecos_security_policy uq_sec_policy_domain_name; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_security_policy
    ADD CONSTRAINT uq_sec_policy_domain_name UNIQUE (domain, name);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: dq_alert_record dq_alert_record_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.dq_alert_record
    ADD CONSTRAINT dq_alert_record_pkey PRIMARY KEY (id);


--
-- Name: dq_knowledge_entry dq_knowledge_entry_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.dq_knowledge_entry
    ADD CONSTRAINT dq_knowledge_entry_pkey PRIMARY KEY (id);


--
-- Name: dq_report dq_report_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.dq_report
    ADD CONSTRAINT dq_report_pkey PRIMARY KEY (id);


--
-- Name: dq_rule_check dq_rule_check_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.dq_rule_check
    ADD CONSTRAINT dq_rule_check_pkey PRIMARY KEY (id);


--
-- Name: dq_rule dq_rule_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.dq_rule
    ADD CONSTRAINT dq_rule_pkey PRIMARY KEY (id);


--
-- Name: dq_rule dq_rule_rule_code_key; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.dq_rule
    ADD CONSTRAINT dq_rule_rule_code_key UNIQUE (rule_code);


--
-- Name: dq_rule_version dq_rule_version_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.dq_rule_version
    ADD CONSTRAINT dq_rule_version_pkey PRIMARY KEY (id);


--
-- Name: dq_rule_version dq_rule_version_rule_id_version_number_key; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.dq_rule_version
    ADD CONSTRAINT dq_rule_version_rule_id_version_number_key UNIQUE (rule_id, version_number);


--
-- Name: dq_schedule dq_schedule_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.dq_schedule
    ADD CONSTRAINT dq_schedule_pkey PRIMARY KEY (id);


--
-- Name: dq_score_asset dq_score_asset_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.dq_score_asset
    ADD CONSTRAINT dq_score_asset_pkey PRIMARY KEY (asset_type, asset_id);


--
-- Name: dq_score_snapshot dq_score_snapshot_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.dq_score_snapshot
    ADD CONSTRAINT dq_score_snapshot_pkey PRIMARY KEY (id);


--
-- Name: dq_throttle dq_throttle_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.dq_throttle
    ADD CONSTRAINT dq_throttle_pkey PRIMARY KEY (id);


--
-- Name: dq_throttle dq_throttle_scope_type_scope_id_key; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.dq_throttle
    ADD CONSTRAINT dq_throttle_scope_type_scope_id_key UNIQUE (scope_type, scope_id);


--
-- Name: dq_work_order dq_work_order_order_no_key; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.dq_work_order
    ADD CONSTRAINT dq_work_order_order_no_key UNIQUE (order_no);


--
-- Name: dq_work_order dq_work_order_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.dq_work_order
    ADD CONSTRAINT dq_work_order_pkey PRIMARY KEY (id);


--
-- Name: ecos_data_asset_field ecos_data_asset_field_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.ecos_data_asset_field
    ADD CONSTRAINT ecos_data_asset_field_pkey PRIMARY KEY (field_asset_id);


--
-- Name: ecos_data_asset ecos_data_asset_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.ecos_data_asset
    ADD CONSTRAINT ecos_data_asset_pkey PRIMARY KEY (asset_id);


--
-- Name: ecos_data_category_tree ecos_data_category_tree_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.ecos_data_category_tree
    ADD CONSTRAINT ecos_data_category_tree_pkey PRIMARY KEY (category_id);


--
-- Name: ecos_data_governance_candidate ecos_data_governance_candidate_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.ecos_data_governance_candidate
    ADD CONSTRAINT ecos_data_governance_candidate_pkey PRIMARY KEY (id);


--
-- Name: ecos_data_level_def ecos_data_level_def_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.ecos_data_level_def
    ADD CONSTRAINT ecos_data_level_def_pkey PRIMARY KEY (level_code);


--
-- Name: ecos_data_lineage_edge ecos_data_lineage_edge_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.ecos_data_lineage_edge
    ADD CONSTRAINT ecos_data_lineage_edge_pkey PRIMARY KEY (id);


--
-- Name: ecos_data_lineage_node ecos_data_lineage_node_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.ecos_data_lineage_node
    ADD CONSTRAINT ecos_data_lineage_node_pkey PRIMARY KEY (id);


--
-- Name: ecos_dq_legacy_migration_report ecos_dq_legacy_migration_report_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.ecos_dq_legacy_migration_report
    ADD CONSTRAINT ecos_dq_legacy_migration_report_pkey PRIMARY KEY (id);


--
-- Name: ecos_dq_score_asset ecos_dq_score_asset_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.ecos_dq_score_asset
    ADD CONSTRAINT ecos_dq_score_asset_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_definition ecos_pipeline_definition_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.ecos_pipeline_definition
    ADD CONSTRAINT ecos_pipeline_definition_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_execution ecos_pipeline_execution_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.ecos_pipeline_execution
    ADD CONSTRAINT ecos_pipeline_execution_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_yaml_migration_report ecos_pipeline_yaml_migration_report_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.ecos_pipeline_yaml_migration_report
    ADD CONSTRAINT ecos_pipeline_yaml_migration_report_pkey PRIMARY KEY (id);


--
-- Name: ecos_query_history ecos_query_history_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.ecos_query_history
    ADD CONSTRAINT ecos_query_history_pkey PRIMARY KEY (id);


--
-- Name: ecos_query_template ecos_query_template_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.ecos_query_template
    ADD CONSTRAINT ecos_query_template_pkey PRIMARY KEY (id);


--
-- Name: ecos_data_period_status pk_ecos_data_period_status; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.ecos_data_period_status
    ADD CONSTRAINT pk_ecos_data_period_status PRIMARY KEY (id);


--
-- Name: td_data_category td_data_category_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.td_data_category
    ADD CONSTRAINT td_data_category_pkey PRIMARY KEY (category_id);


--
-- Name: doc_chunk doc_chunk_pkey; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.doc_chunk
    ADD CONSTRAINT doc_chunk_pkey PRIMARY KEY (id);


--
-- Name: doc doc_pkey; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.doc
    ADD CONSTRAINT doc_pkey PRIMARY KEY (id);


--
-- Name: ecos_application_wagent_option_amount ecos_application_wagent_option_amount_pkey; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.ecos_application_wagent_option_amount
    ADD CONSTRAINT ecos_application_wagent_option_amount_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_cost_fact ecos_biz_cost_fact_pkey; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.ecos_biz_cost_fact
    ADD CONSTRAINT ecos_biz_cost_fact_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_project_attribution ecos_biz_project_attribution_pkey; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.ecos_biz_project_attribution
    ADD CONSTRAINT ecos_biz_project_attribution_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_resource_fact ecos_biz_resource_fact_pkey; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.ecos_biz_resource_fact
    ADD CONSTRAINT ecos_biz_resource_fact_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_stage_fact ecos_biz_stage_fact_pkey; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.ecos_biz_stage_fact
    ADD CONSTRAINT ecos_biz_stage_fact_pkey PRIMARY KEY (id);


--
-- Name: ecos_fc_backtest ecos_fc_backtest_pkey; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.ecos_fc_backtest
    ADD CONSTRAINT ecos_fc_backtest_pkey PRIMARY KEY (id);


--
-- Name: ecos_fc_result_detail ecos_fc_result_detail_pkey; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.ecos_fc_result_detail
    ADD CONSTRAINT ecos_fc_result_detail_pkey PRIMARY KEY (id);


--
-- Name: ecos_fc_run ecos_fc_run_pkey; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.ecos_fc_run
    ADD CONSTRAINT ecos_fc_run_pkey PRIMARY KEY (id);


--
-- Name: ecos_forecast_input_snapshot ecos_forecast_input_snapshot_pkey; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.ecos_forecast_input_snapshot
    ADD CONSTRAINT ecos_forecast_input_snapshot_pkey PRIMARY KEY (id);


--
-- Name: fc_cost_allocation_fact fc_cost_allocation_fact_pkey; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.fc_cost_allocation_fact
    ADD CONSTRAINT fc_cost_allocation_fact_pkey PRIMARY KEY (id);


--
-- Name: fc_project_period_fact fc_project_period_fact_pkey; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.fc_project_period_fact
    ADD CONSTRAINT fc_project_period_fact_pkey PRIMARY KEY (id);


--
-- Name: fc_resource_input_fact fc_resource_input_fact_pkey; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.fc_resource_input_fact
    ADD CONSTRAINT fc_resource_input_fact_pkey PRIMARY KEY (id);


--
-- Name: ecos_application_action_outcome pk_ecos_application_action_outcome; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.ecos_application_action_outcome
    ADD CONSTRAINT pk_ecos_application_action_outcome PRIMARY KEY (id);


--
-- Name: ecos_application_forecast_run pk_ecos_application_forecast_run; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.ecos_application_forecast_run
    ADD CONSTRAINT pk_ecos_application_forecast_run PRIMARY KEY (id);


--
-- Name: ecos_kb_assumption_value pk_ecos_kb_assumption_value; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.ecos_kb_assumption_value
    ADD CONSTRAINT pk_ecos_kb_assumption_value PRIMARY KEY (id);


--
-- Name: ecos_kb_profile_stats pk_ecos_kb_profile_stats; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.ecos_kb_profile_stats
    ADD CONSTRAINT pk_ecos_kb_profile_stats PRIMARY KEY (id);


--
-- Name: ecos_kb_vector_fallback pk_ecos_kb_vector_fallback; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.ecos_kb_vector_fallback
    ADD CONSTRAINT pk_ecos_kb_vector_fallback PRIMARY KEY (id);


--
-- Name: ecos_application_wagent_option_amount uk_app_wagent_option_amount; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.ecos_application_wagent_option_amount
    ADD CONSTRAINT uk_app_wagent_option_amount UNIQUE (option_id);


--
-- Name: ecos_kb_vector_fallback uniq_ecos_kb_vec_fb_ref; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.ecos_kb_vector_fallback
    ADD CONSTRAINT uniq_ecos_kb_vec_fb_ref UNIQUE (embedding_ref, is_deleted);


--
-- Name: ecos_kb_assumption_value uniq_kbau_assumption; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.ecos_kb_assumption_value
    ADD CONSTRAINT uniq_kbau_assumption UNIQUE (assumption_id, is_deleted);


--
-- Name: ecos_kb_profile_stats uniq_kbps_profile; Type: CONSTRAINT; Schema: ecos_dw; Owner: -
--

ALTER TABLE ONLY ecos_dw.ecos_kb_profile_stats
    ADD CONSTRAINT uniq_kbps_profile UNIQUE (profile_id, is_deleted);


--
-- Name: outbox_event outbox_event_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event
    ADD CONSTRAINT outbox_event_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_01 outbox_event_2025_01_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_01
    ADD CONSTRAINT outbox_event_2025_01_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_02 outbox_event_2025_02_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_02
    ADD CONSTRAINT outbox_event_2025_02_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_03 outbox_event_2025_03_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_03
    ADD CONSTRAINT outbox_event_2025_03_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_04 outbox_event_2025_04_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_04
    ADD CONSTRAINT outbox_event_2025_04_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_05 outbox_event_2025_05_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_05
    ADD CONSTRAINT outbox_event_2025_05_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_06 outbox_event_2025_06_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_06
    ADD CONSTRAINT outbox_event_2025_06_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_07 outbox_event_2025_07_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_07
    ADD CONSTRAINT outbox_event_2025_07_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_08 outbox_event_2025_08_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_08
    ADD CONSTRAINT outbox_event_2025_08_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_09 outbox_event_2025_09_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_09
    ADD CONSTRAINT outbox_event_2025_09_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_10 outbox_event_2025_10_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_10
    ADD CONSTRAINT outbox_event_2025_10_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_11 outbox_event_2025_11_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_11
    ADD CONSTRAINT outbox_event_2025_11_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_12 outbox_event_2025_12_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_12
    ADD CONSTRAINT outbox_event_2025_12_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_01 outbox_event_2026_01_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_01
    ADD CONSTRAINT outbox_event_2026_01_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_02 outbox_event_2026_02_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_02
    ADD CONSTRAINT outbox_event_2026_02_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_03 outbox_event_2026_03_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_03
    ADD CONSTRAINT outbox_event_2026_03_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_04 outbox_event_2026_04_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_04
    ADD CONSTRAINT outbox_event_2026_04_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_05 outbox_event_2026_05_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_05
    ADD CONSTRAINT outbox_event_2026_05_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_06 outbox_event_2026_06_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_06
    ADD CONSTRAINT outbox_event_2026_06_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_07 outbox_event_2026_07_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_07
    ADD CONSTRAINT outbox_event_2026_07_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_08 outbox_event_2026_08_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_08
    ADD CONSTRAINT outbox_event_2026_08_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_09 outbox_event_2026_09_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_09
    ADD CONSTRAINT outbox_event_2026_09_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_10 outbox_event_2026_10_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_10
    ADD CONSTRAINT outbox_event_2026_10_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_11 outbox_event_2026_11_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_11
    ADD CONSTRAINT outbox_event_2026_11_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_12 outbox_event_2026_12_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_12
    ADD CONSTRAINT outbox_event_2026_12_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_01 outbox_event_2027_01_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_01
    ADD CONSTRAINT outbox_event_2027_01_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_02 outbox_event_2027_02_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_02
    ADD CONSTRAINT outbox_event_2027_02_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_03 outbox_event_2027_03_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_03
    ADD CONSTRAINT outbox_event_2027_03_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_04 outbox_event_2027_04_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_04
    ADD CONSTRAINT outbox_event_2027_04_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_05 outbox_event_2027_05_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_05
    ADD CONSTRAINT outbox_event_2027_05_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_06 outbox_event_2027_06_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_06
    ADD CONSTRAINT outbox_event_2027_06_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_07 outbox_event_2027_07_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_07
    ADD CONSTRAINT outbox_event_2027_07_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_08 outbox_event_2027_08_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_08
    ADD CONSTRAINT outbox_event_2027_08_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_09 outbox_event_2027_09_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_09
    ADD CONSTRAINT outbox_event_2027_09_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_10 outbox_event_2027_10_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_10
    ADD CONSTRAINT outbox_event_2027_10_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_11 outbox_event_2027_11_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_11
    ADD CONSTRAINT outbox_event_2027_11_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_12 outbox_event_2027_12_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_12
    ADD CONSTRAINT outbox_event_2027_12_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_default outbox_event_default_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.outbox_event_default
    ADD CONSTRAINT outbox_event_default_pkey PRIMARY KEY (id, created_at);


--
-- Name: saga_instance saga_instance_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.saga_instance
    ADD CONSTRAINT saga_instance_pkey PRIMARY KEY (id);


--
-- Name: schema_version schema_version_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: -
--

ALTER TABLE ONLY ecos_infra.schema_version
    ADD CONSTRAINT schema_version_pkey PRIMARY KEY (id);


--
-- Name: ecos_knowledge_graph_edge ecos_knowledge_graph_edge_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_knowledge_graph_edge
    ADD CONSTRAINT ecos_knowledge_graph_edge_pkey PRIMARY KEY (id);


--
-- Name: ecos_knowledge_graph_node ecos_knowledge_graph_node_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_knowledge_graph_node
    ADD CONSTRAINT ecos_knowledge_graph_node_pkey PRIMARY KEY (id);


--
-- Name: expert_rule expert_rule_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.expert_rule
    ADD CONSTRAINT expert_rule_pkey PRIMARY KEY (id);


--
-- Name: graph_edge graph_edge_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.graph_edge
    ADD CONSTRAINT graph_edge_pkey PRIMARY KEY (id);


--
-- Name: graph_node graph_node_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.graph_node
    ADD CONSTRAINT graph_node_pkey PRIMARY KEY (id);


--
-- Name: graph_subgraph graph_subgraph_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.graph_subgraph
    ADD CONSTRAINT graph_subgraph_pkey PRIMARY KEY (id);


--
-- Name: kb_doc_chunk kb_doc_chunk_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.kb_doc_chunk
    ADD CONSTRAINT kb_doc_chunk_pkey PRIMARY KEY (id);


--
-- Name: kb_doc kb_doc_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.kb_doc
    ADD CONSTRAINT kb_doc_pkey PRIMARY KEY (doc_id);


--
-- Name: kb_extract_audit kb_extract_audit_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.kb_extract_audit
    ADD CONSTRAINT kb_extract_audit_pkey PRIMARY KEY (id);


--
-- Name: kb_extract_candidate kb_extract_candidate_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.kb_extract_candidate
    ADD CONSTRAINT kb_extract_candidate_pkey PRIMARY KEY (id);


--
-- Name: kb_extract_watermark kb_extract_watermark_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.kb_extract_watermark
    ADD CONSTRAINT kb_extract_watermark_pkey PRIMARY KEY (id);


--
-- Name: kb_nav_article_rel kb_nav_article_rel_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.kb_nav_article_rel
    ADD CONSTRAINT kb_nav_article_rel_pkey PRIMARY KEY (id);


--
-- Name: kb_nav_category kb_nav_category_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.kb_nav_category
    ADD CONSTRAINT kb_nav_category_pkey PRIMARY KEY (id);


--
-- Name: kb_nav_tag kb_nav_tag_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.kb_nav_tag
    ADD CONSTRAINT kb_nav_tag_pkey PRIMARY KEY (id);


--
-- Name: kb_scheduled_extract kb_scheduled_extract_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.kb_scheduled_extract
    ADD CONSTRAINT kb_scheduled_extract_pkey PRIMARY KEY (id);


--
-- Name: kb_scheduled_extract kb_scheduled_extract_schedule_id_key; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.kb_scheduled_extract
    ADD CONSTRAINT kb_scheduled_extract_schedule_id_key UNIQUE (schedule_id);


--
-- Name: kg_sync_log kg_sync_log_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.kg_sync_log
    ADD CONSTRAINT kg_sync_log_pkey PRIMARY KEY (id);


--
-- Name: knowledge_article knowledge_article_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.knowledge_article
    ADD CONSTRAINT knowledge_article_pkey PRIMARY KEY (id);


--
-- Name: knowledge_embedding knowledge_embedding_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.knowledge_embedding
    ADD CONSTRAINT knowledge_embedding_pkey PRIMARY KEY (id);


--
-- Name: ecos_kb_access_request pk_ecos_kb_access_request; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_kb_access_request
    ADD CONSTRAINT pk_ecos_kb_access_request PRIMARY KEY (id);


--
-- Name: ecos_kb_assumption pk_ecos_kb_assumption; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_kb_assumption
    ADD CONSTRAINT pk_ecos_kb_assumption PRIMARY KEY (id);


--
-- Name: ecos_kb_document pk_ecos_kb_document; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_kb_document
    ADD CONSTRAINT pk_ecos_kb_document PRIMARY KEY (id);


--
-- Name: ecos_kb_extract_audit pk_ecos_kb_extract_audit; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_kb_extract_audit
    ADD CONSTRAINT pk_ecos_kb_extract_audit PRIMARY KEY (id);


--
-- Name: ecos_kb_extract_candidate pk_ecos_kb_extract_candidate; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_kb_extract_candidate
    ADD CONSTRAINT pk_ecos_kb_extract_candidate PRIMARY KEY (id);


--
-- Name: ecos_kb_extract_watermark pk_ecos_kb_extract_watermark; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_kb_extract_watermark
    ADD CONSTRAINT pk_ecos_kb_extract_watermark PRIMARY KEY (id);


--
-- Name: ecos_kb_graph_edge pk_ecos_kb_graph_edge; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_kb_graph_edge
    ADD CONSTRAINT pk_ecos_kb_graph_edge PRIMARY KEY (id);


--
-- Name: ecos_kb_graph_node pk_ecos_kb_graph_node; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_kb_graph_node
    ADD CONSTRAINT pk_ecos_kb_graph_node PRIMARY KEY (id);


--
-- Name: ecos_kb_lineage_event pk_ecos_kb_lineage_event; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_kb_lineage_event
    ADD CONSTRAINT pk_ecos_kb_lineage_event PRIMARY KEY (id);


--
-- Name: ecos_kb_market_asset pk_ecos_kb_market_asset; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_kb_market_asset
    ADD CONSTRAINT pk_ecos_kb_market_asset PRIMARY KEY (id);


--
-- Name: ecos_kb_ontology_snapshot pk_ecos_kb_ontology_snapshot; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_kb_ontology_snapshot
    ADD CONSTRAINT pk_ecos_kb_ontology_snapshot PRIMARY KEY (id);


--
-- Name: ecos_kb_profile pk_ecos_kb_profile; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_kb_profile
    ADD CONSTRAINT pk_ecos_kb_profile PRIMARY KEY (id);


--
-- Name: ecos_kb_term pk_ecos_kb_term; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_kb_term
    ADD CONSTRAINT pk_ecos_kb_term PRIMARY KEY (id);


--
-- Name: ecos_kg_sync_log pk_ecos_kg_sync_log; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_kg_sync_log
    ADD CONSTRAINT pk_ecos_kg_sync_log PRIMARY KEY (id);


--
-- Name: ecos_kb_extract_watermark uniq_ecos_kb_extract_wm; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_kb_extract_watermark
    ADD CONSTRAINT uniq_ecos_kb_extract_wm UNIQUE (ontology_id, entity_code, resource_id, is_deleted);


--
-- Name: ecos_kb_lineage_event uniq_ecos_kb_lineage_evt; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_kb_lineage_event
    ADD CONSTRAINT uniq_ecos_kb_lineage_evt UNIQUE (event_id, is_deleted);


--
-- Name: ecos_kb_ontology_snapshot uniq_ecos_kb_onto_snap; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_kb_ontology_snapshot
    ADD CONSTRAINT uniq_ecos_kb_onto_snap UNIQUE (ontology_id, snap_version, is_deleted);


--
-- Name: ecos_kb_term uniq_ecos_kb_term_name; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.ecos_kb_term
    ADD CONSTRAINT uniq_ecos_kb_term_name UNIQUE (domain, subject_domain, name, is_deleted);


--
-- Name: kb_extract_watermark uq_kb_extract_watermark; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.kb_extract_watermark
    ADD CONSTRAINT uq_kb_extract_watermark UNIQUE (ontology_id, entity_code, resource_id);


--
-- Name: action_definition action_definition_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.action_definition
    ADD CONSTRAINT action_definition_pkey PRIMARY KEY (id);


--
-- Name: ecos_business_glossary ecos_business_glossary_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_business_glossary
    ADD CONSTRAINT ecos_business_glossary_pkey PRIMARY KEY (id);


--
-- Name: ecos_caliber ecos_caliber_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_caliber
    ADD CONSTRAINT ecos_caliber_pkey PRIMARY KEY (id);


--
-- Name: ecos_caliber_version ecos_caliber_version_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_caliber_version
    ADD CONSTRAINT ecos_caliber_version_pkey PRIMARY KEY (id);


--
-- Name: ecos_domain ecos_domain_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_domain
    ADD CONSTRAINT ecos_domain_pkey PRIMARY KEY (id);


--
-- Name: ecos_entity_table_mapping ecos_entity_table_mapping_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_entity_table_mapping
    ADD CONSTRAINT ecos_entity_table_mapping_pkey PRIMARY KEY (id);


--
-- Name: ecos_function_audit ecos_function_audit_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_function_audit
    ADD CONSTRAINT ecos_function_audit_pkey PRIMARY KEY (id);


--
-- Name: ecos_metric_definition ecos_metric_definition_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_metric_definition
    ADD CONSTRAINT ecos_metric_definition_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_attachment ecos_object_attachment_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_object_attachment
    ADD CONSTRAINT ecos_object_attachment_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_data ecos_object_data_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_object_data
    ADD CONSTRAINT ecos_object_data_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_links ecos_object_links_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_object_links
    ADD CONSTRAINT ecos_object_links_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_relation ecos_object_relation_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_object_relation
    ADD CONSTRAINT ecos_object_relation_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_relationship ecos_object_relationship_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_object_relationship
    ADD CONSTRAINT ecos_object_relationship_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_state_machine ecos_object_state_machine_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_object_state_machine
    ADD CONSTRAINT ecos_object_state_machine_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_timeline ecos_object_timeline_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_object_timeline
    ADD CONSTRAINT ecos_object_timeline_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_version ecos_object_version_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_object_version
    ADD CONSTRAINT ecos_object_version_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology_candidate ecos_ontology_candidate_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_ontology_candidate
    ADD CONSTRAINT ecos_ontology_candidate_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology_data ecos_ontology_data_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_ontology_data
    ADD CONSTRAINT ecos_ontology_data_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology_entity ecos_ontology_entity_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_ontology_entity
    ADD CONSTRAINT ecos_ontology_entity_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology_git_archive_pending ecos_ontology_git_archive_pending_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_ontology_git_archive_pending
    ADD CONSTRAINT ecos_ontology_git_archive_pending_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology_proposal ecos_ontology_proposal_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_ontology_proposal
    ADD CONSTRAINT ecos_ontology_proposal_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology_proposals ecos_ontology_proposals_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_ontology_proposals
    ADD CONSTRAINT ecos_ontology_proposals_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_approval ecos_workflow_approval_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_workflow_approval
    ADD CONSTRAINT ecos_workflow_approval_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_edge ecos_workflow_edge_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_workflow_edge
    ADD CONSTRAINT ecos_workflow_edge_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_instance ecos_workflow_instance_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_workflow_instance
    ADD CONSTRAINT ecos_workflow_instance_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_log ecos_workflow_log_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_workflow_log
    ADD CONSTRAINT ecos_workflow_log_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_node ecos_workflow_node_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_workflow_node
    ADD CONSTRAINT ecos_workflow_node_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow ecos_workflow_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_workflow
    ADD CONSTRAINT ecos_workflow_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_task ecos_workflow_task_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.ecos_workflow_task
    ADD CONSTRAINT ecos_workflow_task_pkey PRIMARY KEY (id);


--
-- Name: entity_definition entity_definition_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.entity_definition
    ADD CONSTRAINT entity_definition_pkey PRIMARY KEY (id);


--
-- Name: event_definition event_definition_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.event_definition
    ADD CONSTRAINT event_definition_pkey PRIMARY KEY (id);


--
-- Name: metric_definition metric_definition_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.metric_definition
    ADD CONSTRAINT metric_definition_pkey PRIMARY KEY (id);


--
-- Name: policy_definition policy_definition_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.policy_definition
    ADD CONSTRAINT policy_definition_pkey PRIMARY KEY (id);


--
-- Name: relationship_definition relationship_definition_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: -
--

ALTER TABLE ONLY ecos_ontology.relationship_definition
    ADD CONSTRAINT relationship_definition_pkey PRIMARY KEY (id);


--
-- Name: idx_acb_mode; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_acb_mode ON ecos_ai.ecos_ai_compression_batch USING btree (mode, create_time);


--
-- Name: idx_acb_session; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_acb_session ON ecos_ai.ecos_ai_compression_batch USING btree (session_id, create_time);


--
-- Name: idx_agdl_tool; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_agdl_tool ON ecos_ai.ecos_ai_guardrail_decision_ledger USING btree (tool_key, decision, create_time);


--
-- Name: idx_agdl_trace; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_agdl_trace ON ecos_ai.ecos_ai_guardrail_decision_ledger USING btree (trace_id);


--
-- Name: idx_agent_approval_expired; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_agent_approval_expired ON ecos_ai.agent_approval USING btree (expired_at);


--
-- Name: idx_agent_approval_scope; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_agent_approval_scope ON ecos_ai.agent_approval USING btree (scope_type, scope_id);


--
-- Name: idx_agent_approval_trace; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_agent_approval_trace ON ecos_ai.agent_approval USING btree (trace_id);


--
-- Name: idx_ai_agent_status; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_ai_agent_status ON ecos_ai.ecos_agent USING btree (status);


--
-- Name: idx_ai_calllog_profile; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_ai_calllog_profile ON ecos_ai.sys_agent_call_log USING btree (profile_name);


--
-- Name: idx_ai_calllog_time; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_ai_calllog_time ON ecos_ai.sys_agent_call_log USING btree (created_time DESC);


--
-- Name: idx_ai_causal_src; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_ai_causal_src ON ecos_ai.causal_edge USING btree (source_node);


--
-- Name: idx_ai_causal_tgt; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_ai_causal_tgt ON ecos_ai.causal_edge USING btree (target_node);


--
-- Name: idx_ai_dcase_tags; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_ai_dcase_tags ON ecos_ai.ecos_decision_case USING gin (tags);


--
-- Name: idx_ai_exec_agent; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_ai_exec_agent ON ecos_ai.agent_execution USING btree (agent_id);


--
-- Name: idx_ai_exec_status; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_ai_exec_status ON ecos_ai.agent_execution USING btree (status);


--
-- Name: idx_ai_mem_agent; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_ai_mem_agent ON ecos_ai.agent_memory USING btree (agent_id);


--
-- Name: idx_ai_mem_session; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_ai_mem_session ON ecos_ai.agent_memory USING btree (session_id);


--
-- Name: idx_ai_mtask_mission; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_ai_mtask_mission ON ecos_ai.ecos_mission_task USING btree (mission_id);


--
-- Name: idx_ai_scenario_type; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_ai_scenario_type ON ecos_ai.scenario USING btree (type);


--
-- Name: idx_ai_sim_status; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_ai_sim_status ON ecos_ai.simulation USING btree (status);


--
-- Name: idx_ai_step_exec; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_ai_step_exec ON ecos_ai.agent_execution_step USING btree (execution_id);


--
-- Name: idx_ai_tool_code; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE UNIQUE INDEX idx_ai_tool_code ON ecos_ai.ecos_tool_definition USING btree (code);


--
-- Name: idx_aip_pipeline_status; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_aip_pipeline_status ON ecos_ai.ecos_aip_pipeline USING btree (status);


--
-- Name: idx_aip_pipeline_tenant; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_aip_pipeline_tenant ON ecos_ai.ecos_aip_pipeline USING btree (tenant_id);


--
-- Name: idx_atpb_role; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_atpb_role ON ecos_ai.ecos_ai_tool_permission_binding USING btree (role_key, is_deleted);


--
-- Name: idx_atpb_tool; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_atpb_tool ON ecos_ai.ecos_ai_tool_permission_binding USING btree (tool_key, is_deleted);


--
-- Name: idx_atr_status; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_atr_status ON ecos_ai.ecos_ai_tool_registry USING btree (status, is_deleted);


--
-- Name: idx_causal_source; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_causal_source ON ecos_ai.causal_edge USING btree (source_node);


--
-- Name: idx_causal_target; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_causal_target ON ecos_ai.causal_edge USING btree (target_node);


--
-- Name: idx_cv269_cap_run; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_cv269_cap_run ON ecos_ai.ecos_ai_capability_task USING btree (run_id, is_deleted);


--
-- Name: idx_cv269_cap_stat; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_cv269_cap_stat ON ecos_ai.ecos_ai_capability_task USING btree (status, is_deleted);


--
-- Name: idx_cv269_cap_tid; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_cv269_cap_tid ON ecos_ai.ecos_ai_capability_task USING btree (tenant_id, capability_id, is_deleted);


--
-- Name: idx_ecos_ai_bizscn_dept; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_ecos_ai_bizscn_dept ON ecos_ai.ecos_business_scenario USING btree (department, is_deleted);


--
-- Name: idx_ecos_ai_bizscn_status; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_ecos_ai_bizscn_status ON ecos_ai.ecos_business_scenario USING btree (status, is_deleted);


--
-- Name: idx_scenario_type; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_scenario_type ON ecos_ai.scenario USING btree (type);


--
-- Name: idx_simulation_status; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_simulation_status ON ecos_ai.simulation USING btree (status);


--
-- Name: idx_v270_tc_capability; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_v270_tc_capability ON ecos_ai.ecos_wagent_tool_contract USING btree (capability_id, is_deleted);


--
-- Name: idx_wagent_action_option; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_action_option ON ecos_ai.ecos_wagent_action USING btree (option_id);


--
-- Name: idx_wagent_action_tenant_status; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_action_tenant_status ON ecos_ai.ecos_wagent_action USING btree (tenant_id, status);


--
-- Name: idx_wagent_cand_batch; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_cand_batch ON ecos_ai.ecos_wagent_candidate USING btree (batch_id);


--
-- Name: idx_wagent_cand_rev_cand; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_cand_rev_cand ON ecos_ai.ecos_wagent_candidate_review USING btree (candidate_id, review_time);


--
-- Name: idx_wagent_cand_run; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_cand_run ON ecos_ai.ecos_wagent_candidate USING btree (generated_by_run);


--
-- Name: idx_wagent_cand_status; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_cand_status ON ecos_ai.ecos_wagent_candidate USING btree (status, expire_time);


--
-- Name: idx_wagent_claim_ev_evidence; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_claim_ev_evidence ON ecos_ai.ecos_wagent_claim_evidence USING btree (evidence_id);


--
-- Name: idx_wagent_claim_run; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_claim_run ON ecos_ai.ecos_wagent_claim USING btree (run_id, verify_status);


--
-- Name: idx_wagent_effect_action; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_effect_action ON ecos_ai.ecos_wagent_action_effect USING btree (action_id);


--
-- Name: idx_wagent_event_run; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_event_run ON ecos_ai.ecos_wagent_run_event USING btree (run_id, occur_time);


--
-- Name: idx_wagent_evidence_src; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_evidence_src ON ecos_ai.ecos_wagent_evidence USING btree (source_engine, source_object_type);


--
-- Name: idx_wagent_gap_assess; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_gap_assess ON ecos_ai.ecos_wagent_readiness_gap USING btree (assessment_id, status);


--
-- Name: idx_wagent_gap_tenant; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_gap_tenant ON ecos_ai.ecos_wagent_readiness_gap USING btree (tenant_id, severity);


--
-- Name: idx_wagent_goal_owner; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_goal_owner ON ecos_ai.ecos_wagent_goal USING btree (owner_id);


--
-- Name: idx_wagent_goal_tenant; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_goal_tenant ON ecos_ai.ecos_wagent_goal USING btree (tenant_id, status);


--
-- Name: idx_wagent_mission_goal; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_mission_goal ON ecos_ai.ecos_wagent_mission USING btree (goal_id);


--
-- Name: idx_wagent_mission_tenant; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_mission_tenant ON ecos_ai.ecos_wagent_mission USING btree (tenant_id, stage);


--
-- Name: idx_wagent_question_mission; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_question_mission ON ecos_ai.ecos_wagent_question USING btree (mission_id);


--
-- Name: idx_wagent_question_tenant; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_question_tenant ON ecos_ai.ecos_wagent_question USING btree (tenant_id, status);


--
-- Name: idx_wagent_ready_item_assess; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_ready_item_assess ON ecos_ai.ecos_wagent_readiness_item USING btree (assessment_id, layer);


--
-- Name: idx_wagent_ready_question; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_ready_question ON ecos_ai.ecos_wagent_readiness USING btree (question_id, valid_until);


--
-- Name: idx_wagent_ready_tenant; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_ready_tenant ON ecos_ai.ecos_wagent_readiness USING btree (tenant_id, grade);


--
-- Name: idx_wagent_run_question; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_run_question ON ecos_ai.ecos_wagent_run USING btree (question_id, status);


--
-- Name: idx_wagent_skill_status; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_skill_status ON ecos_ai.ecos_wagent_skill USING btree (status);


--
-- Name: idx_wagent_step_run; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_step_run ON ecos_ai.ecos_wagent_run_step USING btree (run_id, status);


--
-- Name: idx_wagent_tool_engine; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_tool_engine ON ecos_ai.ecos_wagent_tool_contract USING btree (engine, status);


--
-- Name: idx_wagent_tool_toolset; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE INDEX idx_wagent_tool_toolset ON ecos_ai.ecos_wagent_tool_contract USING btree (toolset);


--
-- Name: uk_agent_approval_token_digest; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE UNIQUE INDEX uk_agent_approval_token_digest ON ecos_ai.agent_approval USING btree (token_digest);


--
-- Name: uk_wagent_option_decision_baseline; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE UNIQUE INDEX uk_wagent_option_decision_baseline ON ecos_ai.ecos_wagent_decision_option USING btree (decision_id, baseline_slot);


--
-- Name: uniq_aip_pipeline_name_ver; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE UNIQUE INDEX uniq_aip_pipeline_name_ver ON ecos_ai.ecos_aip_pipeline USING btree (name, version_no, is_deleted);


--
-- Name: uniq_atr_key; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE UNIQUE INDEX uniq_atr_key ON ecos_ai.ecos_ai_tool_registry USING btree (tool_key, is_deleted);


--
-- Name: uniq_wagent_run_idem; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE UNIQUE INDEX uniq_wagent_run_idem ON ecos_ai.ecos_wagent_run USING btree (tenant_id, initiated_by, idempotency_key, is_deleted);


--
-- Name: uniq_wagent_skill_name_ver; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE UNIQUE INDEX uniq_wagent_skill_name_ver ON ecos_ai.ecos_wagent_skill USING btree (name, version, is_deleted);


--
-- Name: uniq_wagent_step_attempt; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE UNIQUE INDEX uniq_wagent_step_attempt ON ecos_ai.ecos_wagent_run_step USING btree (run_id, step_key, attempt, is_deleted);


--
-- Name: uniq_wagent_tool_name_ver; Type: INDEX; Schema: ecos_ai; Owner: -
--

CREATE UNIQUE INDEX uniq_wagent_tool_name_ver ON ecos_ai.ecos_wagent_tool_contract USING btree (name, version, is_deleted);


--
-- Name: idx_cm_active; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cm_active ON ecos_cognitive.ecos_cognitive_mind USING btree (is_active, is_deleted);


--
-- Name: idx_cmu_mind; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cmu_mind ON ecos_cognitive.ecos_cognitive_mind_update USING btree (mind_id, status, is_deleted);


--
-- Name: idx_cmu_run; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cmu_run ON ecos_cognitive.ecos_cognitive_mind_update USING btree (agent_run_id, is_deleted);


--
-- Name: idx_cog_causal_src; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cog_causal_src ON ecos_cognitive.ecos_wm_causal_link USING btree (source_goal_id);


--
-- Name: idx_cog_causal_tgt; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cog_causal_tgt ON ecos_cognitive.ecos_wm_causal_link USING btree (target_goal_id);


--
-- Name: idx_cog_contract_proj; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cog_contract_proj ON ecos_cognitive.ecos_biz_contract USING btree (project_id);


--
-- Name: idx_cog_dept_parent; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cog_dept_parent ON ecos_cognitive.ecos_biz_department USING btree (parent_id);


--
-- Name: idx_cog_goal_org; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cog_goal_org ON ecos_cognitive.ecos_wm_goal USING btree (org_id);


--
-- Name: idx_cog_goal_parent; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cog_goal_parent ON ecos_cognitive.ecos_wm_goal USING btree (parent_id);


--
-- Name: idx_cog_goal_status; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cog_goal_status ON ecos_cognitive.ecos_wm_goal USING btree (status);


--
-- Name: idx_cog_goal_tenant; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cog_goal_tenant ON ecos_cognitive.ecos_wm_goal USING btree (tenant_id);


--
-- Name: idx_cog_goallog_goal; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cog_goallog_goal ON ecos_cognitive.ecos_wm_goal_log USING btree (goal_id);


--
-- Name: idx_cog_goallog_time; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cog_goallog_time ON ecos_cognitive.ecos_wm_goal_log USING btree (changed_at);


--
-- Name: idx_cog_metric_dept; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cog_metric_dept ON ecos_cognitive.ecos_biz_metric USING btree (dept_id);


--
-- Name: idx_cog_proj_dept; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cog_proj_dept ON ecos_cognitive.ecos_biz_project USING btree (dept_id);


--
-- Name: idx_cog_tracking_goal; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cog_tracking_goal ON ecos_cognitive.ecos_goal_tracking USING btree (goal_id, recorded_at);


--
-- Name: idx_cog_wmscen_status; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cog_wmscen_status ON ecos_cognitive.ecos_wm_scenario USING btree (status);


--
-- Name: idx_cog_worldscen_status; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cog_worldscen_status ON ecos_cognitive.ecos_world_scenarios USING btree (status);


--
-- Name: idx_csm_mind; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_csm_mind ON ecos_cognitive.ecos_cognitive_scenario_mind USING btree (mind_id, is_deleted);


--
-- Name: idx_cv191_belief_domain; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cv191_belief_domain ON ecos_cognitive.ecos_cognitive_belief USING btree (domain, is_deleted);


--
-- Name: idx_cv191_belief_mind; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cv191_belief_mind ON ecos_cognitive.ecos_cognitive_belief USING btree (mind_id, scenario_id);


--
-- Name: idx_cv192_model_domain; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cv192_model_domain ON ecos_cognitive.ecos_cognitive_model USING btree (domain, is_deleted);


--
-- Name: idx_cv192_model_type; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cv192_model_type ON ecos_cognitive.ecos_cognitive_model USING btree (model_type, status);


--
-- Name: idx_cv193_bizscn_dept; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cv193_bizscn_dept ON ecos_cognitive.ecos_business_scenario USING btree (department, is_deleted);


--
-- Name: idx_cv193_bizscn_status; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cv193_bizscn_status ON ecos_cognitive.ecos_business_scenario USING btree (status, is_deleted);


--
-- Name: idx_cv261_model_cm; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cv261_model_cm ON ecos_cognitive.ecos_cognitive_model USING btree (cognitive_model_type, registry_state);


--
-- Name: idx_cv261_model_state; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cv261_model_state ON ecos_cognitive.ecos_cognitive_model USING btree (registry_state, is_deleted);


--
-- Name: idx_cv261_model_supersed; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cv261_model_supersed ON ecos_cognitive.ecos_cognitive_model USING btree (supersedes_id);


--
-- Name: idx_cv262_belief_prob; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cv262_belief_prob ON ecos_cognitive.ecos_cognitive_belief USING btree (probability, is_deleted);


--
-- Name: idx_cv267_outcome_run; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cv267_outcome_run ON ecos_cognitive.ecos_cognitive_outcome_evaluation USING btree (run_id, is_deleted);


--
-- Name: idx_cv267_outcome_scenario; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cv267_outcome_scenario ON ecos_cognitive.ecos_cognitive_outcome_evaluation USING btree (scenario_id, status, is_deleted);


--
-- Name: idx_cv268_belief_kd; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cv268_belief_kd ON ecos_cognitive.ecos_cognitive_belief USING btree (prob_kind, is_deleted);


--
-- Name: idx_cv268_belief_lc; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cv268_belief_lc ON ecos_cognitive.ecos_cognitive_belief USING btree (lifecycle_state, is_deleted);


--
-- Name: idx_cv268_hypo_lc; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cv268_hypo_lc ON ecos_cognitive.ecos_cognitive_hypothesis USING btree (lifecycle_state, is_deleted);


--
-- Name: idx_cv268_model_lc; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cv268_model_lc ON ecos_cognitive.ecos_cognitive_model USING btree (lifecycle_state, is_deleted);


--
-- Name: idx_cv274_outcome_trace; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_cv274_outcome_trace ON ecos_cognitive.ecos_cognitive_outcome_evaluation USING btree (trace_id, is_deleted);


--
-- Name: idx_ecos_cognitive_pipeline_domain; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_ecos_cognitive_pipeline_domain ON ecos_cognitive.ecos_cognitive_pipeline USING btree (domain);


--
-- Name: idx_pg_mirror_belief_domain; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_belief_domain ON ecos_cognitive.ecos_cognitive_belief USING btree (domain, is_deleted);


--
-- Name: idx_pg_mirror_belief_mind; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_belief_mind ON ecos_cognitive.ecos_cognitive_belief USING btree (mind_id, scenario_id);


--
-- Name: idx_pg_mirror_belief_prob; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_belief_prob ON ecos_cognitive.ecos_cognitive_belief USING btree (probability, is_deleted);


--
-- Name: idx_pg_mirror_bind_scn; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_bind_scn ON ecos_cognitive.ecos_scenario_binding USING btree (scenario_id, is_deleted);


--
-- Name: idx_pg_mirror_bind_tid; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_bind_tid ON ecos_cognitive.ecos_scenario_binding USING btree (target_id, target_type);


--
-- Name: idx_pg_mirror_bind_type; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_bind_type ON ecos_cognitive.ecos_scenario_binding USING btree (binding_type, is_deleted);


--
-- Name: idx_pg_mirror_bizscn_dept; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_bizscn_dept ON ecos_cognitive.ecos_business_scenario USING btree (department, is_deleted);


--
-- Name: idx_pg_mirror_bizscn_status; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_bizscn_status ON ecos_cognitive.ecos_business_scenario USING btree (status, is_deleted);


--
-- Name: idx_pg_mirror_blink_scn; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_blink_scn ON ecos_cognitive.ecos_scenario_binding_link USING btree (scenario_id, is_deleted);


--
-- Name: idx_pg_mirror_blink_src; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_blink_src ON ecos_cognitive.ecos_scenario_binding_link USING btree (source_binding_id);


--
-- Name: idx_pg_mirror_blink_tgt; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_blink_tgt ON ecos_cognitive.ecos_scenario_binding_link USING btree (target_binding_id);


--
-- Name: idx_pg_mirror_blink_type; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_blink_type ON ecos_cognitive.ecos_scenario_binding_link USING btree (link_type);


--
-- Name: idx_pg_mirror_cm_active; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_cm_active ON ecos_cognitive.ecos_cognitive_mind USING btree (is_active, is_deleted);


--
-- Name: idx_pg_mirror_csm_mind; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_csm_mind ON ecos_cognitive.ecos_cognitive_scenario_mind USING btree (mind_id, is_deleted);


--
-- Name: idx_pg_mirror_ev_source; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_ev_source ON ecos_cognitive.ecos_cognitive_evidence USING btree (source_type, status);


--
-- Name: idx_pg_mirror_ev_time; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_ev_time ON ecos_cognitive.ecos_cognitive_evidence USING btree (create_time);


--
-- Name: idx_pg_mirror_hypo_domain; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_hypo_domain ON ecos_cognitive.ecos_cognitive_hypothesis USING btree (domain, invalid_at);


--
-- Name: idx_pg_mirror_hypo_mind; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_hypo_mind ON ecos_cognitive.ecos_cognitive_hypothesis USING btree (mind_id, scenario_id);


--
-- Name: idx_pg_mirror_model_cm; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_model_cm ON ecos_cognitive.ecos_cognitive_model USING btree (cognitive_model_type, registry_state);


--
-- Name: idx_pg_mirror_model_domain; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_model_domain ON ecos_cognitive.ecos_cognitive_model USING btree (domain, is_deleted);


--
-- Name: idx_pg_mirror_model_state; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_model_state ON ecos_cognitive.ecos_cognitive_model USING btree (registry_state, is_deleted);


--
-- Name: idx_pg_mirror_model_supersed; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_model_supersed ON ecos_cognitive.ecos_cognitive_model USING btree (supersedes_id);


--
-- Name: idx_pg_mirror_model_type; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_model_type ON ecos_cognitive.ecos_cognitive_model USING btree (model_type, status);


--
-- Name: idx_pg_mirror_pipeline_domain; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_pipeline_domain ON ecos_cognitive.ecos_cognitive_pipeline USING btree (domain);


--
-- Name: idx_pg_mirror_run_inv_hyp; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_run_inv_hyp ON ecos_cognitive.ecos_cognitive_run_invalidation USING btree (hypothesis_id);


--
-- Name: idx_pg_mirror_run_inv_run; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_run_inv_run ON ecos_cognitive.ecos_cognitive_run_invalidation USING btree (run_id);


--
-- Name: idx_pg_mirror_run_inv_time; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_pg_mirror_run_inv_time ON ecos_cognitive.ecos_cognitive_run_invalidation USING btree (superseded_at);


--
-- Name: idx_v311_cog_framework_status; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE INDEX idx_v311_cog_framework_status ON ecos_cognitive.ecos_cognitive_framework USING btree (status, category, is_deleted);


--
-- Name: uk_cv274_outcome_idem; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE UNIQUE INDEX uk_cv274_outcome_idem ON ecos_cognitive.ecos_cognitive_outcome_evaluation USING btree (tenant_id, idempotency_key, is_deleted);


--
-- Name: uk_v311_cog_framework_ver; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE UNIQUE INDEX uk_v311_cog_framework_ver ON ecos_cognitive.ecos_cognitive_framework USING btree (framework_id, version, is_deleted);


--
-- Name: uniq_cm_key_ver; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE UNIQUE INDEX uniq_cm_key_ver ON ecos_cognitive.ecos_cognitive_mind USING btree (mind_key, mind_version, is_deleted);


--
-- Name: uniq_csm_scn_mind; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE UNIQUE INDEX uniq_csm_scn_mind ON ecos_cognitive.ecos_cognitive_scenario_mind USING btree (scenario_id, mind_id, is_deleted);


--
-- Name: uniq_cv191_belief_var_ver; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE UNIQUE INDEX uniq_cv191_belief_var_ver ON ecos_cognitive.ecos_cognitive_belief USING btree (variable_name, domain, version, is_deleted);


--
-- Name: uniq_cv192_model_id_ver; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE UNIQUE INDEX uniq_cv192_model_id_ver ON ecos_cognitive.ecos_cognitive_model USING btree (model_id, version, is_deleted);


--
-- Name: uniq_ecos_cognitive_pipeline_pid; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE UNIQUE INDEX uniq_ecos_cognitive_pipeline_pid ON ecos_cognitive.ecos_cognitive_pipeline USING btree (pipeline_id) WHERE (is_deleted = 0);


--
-- Name: uniq_pg_mirror_belief_var_ver; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE UNIQUE INDEX uniq_pg_mirror_belief_var_ver ON ecos_cognitive.ecos_cognitive_belief USING btree (variable_name, domain, version, is_deleted);


--
-- Name: uniq_pg_mirror_cm_key_ver; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE UNIQUE INDEX uniq_pg_mirror_cm_key_ver ON ecos_cognitive.ecos_cognitive_mind USING btree (mind_key, mind_version, is_deleted);


--
-- Name: uniq_pg_mirror_csm_scn_mind; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE UNIQUE INDEX uniq_pg_mirror_csm_scn_mind ON ecos_cognitive.ecos_cognitive_scenario_mind USING btree (scenario_id, mind_id, is_deleted);


--
-- Name: uniq_pg_mirror_ev_code; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE UNIQUE INDEX uniq_pg_mirror_ev_code ON ecos_cognitive.ecos_cognitive_evidence USING btree (evidence_code, is_deleted);


--
-- Name: uniq_pg_mirror_hypo_code; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE UNIQUE INDEX uniq_pg_mirror_hypo_code ON ecos_cognitive.ecos_cognitive_hypothesis USING btree (hypothesis_code, is_deleted);


--
-- Name: uniq_pg_mirror_model_id_ver; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE UNIQUE INDEX uniq_pg_mirror_model_id_ver ON ecos_cognitive.ecos_cognitive_model USING btree (model_id, version, is_deleted);


--
-- Name: uniq_pg_mirror_pipeline_pid; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE UNIQUE INDEX uniq_pg_mirror_pipeline_pid ON ecos_cognitive.ecos_cognitive_pipeline USING btree (pipeline_id, is_deleted);


--
-- Name: uniq_pg_mirror_run_inv_evt_run; Type: INDEX; Schema: ecos_cognitive; Owner: -
--

CREATE UNIQUE INDEX uniq_pg_mirror_run_inv_evt_run ON ecos_cognitive.ecos_cognitive_run_invalidation USING btree (event_id, run_id, is_deleted);


--
-- Name: idx_abac_scope; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_abac_scope ON ecos_control.td_abac_policy USING btree (scope_type, scope_id);


--
-- Name: idx_access_request_asset_id; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_access_request_asset_id ON ecos_control.ecos_marketplace_access_request USING btree (asset_id);


--
-- Name: idx_access_request_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_access_request_status ON ecos_control.ecos_marketplace_access_request USING btree (status);


--
-- Name: idx_agent_message_session; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_agent_message_session ON ecos_control.sys_agent_message USING btree (session_id);


--
-- Name: idx_agent_message_thread; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_agent_message_thread ON ecos_control.sys_agent_message USING btree (session_id, thread_id);


--
-- Name: idx_agent_session_agent; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_agent_session_agent ON ecos_control.sys_agent_session USING btree (agent_id);


--
-- Name: idx_agent_session_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_agent_session_status ON ecos_control.sys_agent_session USING btree (status);


--
-- Name: idx_audit_change_request; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_audit_change_request ON ecos_control.ecos_audit_log USING btree (change_request_id);


--
-- Name: idx_audit_event_id; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_audit_event_id ON ecos_control.ecos_audit_log USING btree (audit_event_id);


--
-- Name: idx_audit_event_type; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_audit_event_type ON ecos_control.td_audit_log USING btree (event_type);


--
-- Name: idx_audit_git_commit; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_audit_git_commit ON ecos_control.ecos_audit_log USING btree (git_commit_sha);


--
-- Name: idx_audit_result; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_audit_result ON ecos_control.td_audit_log USING btree (result);


--
-- Name: idx_audit_timestamp; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_audit_timestamp ON ecos_control.td_audit_log USING btree ("timestamp" DESC);


--
-- Name: idx_audit_trace; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_audit_trace ON ecos_control.ecos_audit_log USING btree (trace_id);


--
-- Name: idx_audit_user; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_audit_user ON ecos_control.td_audit_log USING btree (user_id);


--
-- Name: idx_biz_scenario_dept; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_biz_scenario_dept ON ecos_control.ecos_business_scenario USING btree (department);


--
-- Name: idx_biz_scenario_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_biz_scenario_status ON ecos_control.ecos_business_scenario USING btree (status);


--
-- Name: idx_bsl_sco; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_bsl_sco ON ecos_control.ecos_scenario_binding_link USING btree (scenario_id);


--
-- Name: idx_bsl_src; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_bsl_src ON ecos_control.ecos_scenario_binding_link USING btree (source_binding_id);


--
-- Name: idx_bsl_tgt; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_bsl_tgt ON ecos_control.ecos_scenario_binding_link USING btree (target_binding_id);


--
-- Name: idx_bsl_typ; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_bsl_typ ON ecos_control.ecos_scenario_binding_link USING btree (link_type);


--
-- Name: idx_business_scenario_deleted; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_business_scenario_deleted ON ecos_control.ecos_business_scenario USING btree (is_deleted);


--
-- Name: idx_cache_invalidation_outbox_pending; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_cache_invalidation_outbox_pending ON ecos_control.cache_invalidation_outbox USING btree (status, create_time);


--
-- Name: idx_catalog_res; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_catalog_res ON ecos_control.td_catalog_item USING btree (resource_id);


--
-- Name: idx_catalog_type; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_catalog_type ON ecos_control.td_catalog_item USING btree (resource_type);


--
-- Name: idx_causal_source; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_causal_source ON ecos_control.ecos_decision_causal_link USING btree (source_decision_id);


--
-- Name: idx_causal_target; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_causal_target ON ecos_control.ecos_decision_causal_link USING btree (target_decision_id);


--
-- Name: idx_cls_policy_resource; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_cls_policy_resource ON ecos_control.ecos_cls_policy USING btree (resource_id);


--
-- Name: idx_cls_policy_table; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_cls_policy_table ON ecos_control.ecos_cls_policy USING btree (table_name, scope_type, enabled);


--
-- Name: idx_cog_model_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_cog_model_status ON ecos_control.ecos_cognitive_model USING btree (status);


--
-- Name: idx_cog_model_type; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_cog_model_type ON ecos_control.ecos_cognitive_model USING btree (model_type);


--
-- Name: idx_cog_rule_enabled; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_cog_rule_enabled ON ecos_control.ecos_cognitive_rule USING btree (enabled);


--
-- Name: idx_cog_rule_type; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_cog_rule_type ON ecos_control.ecos_cognitive_rule USING btree (rule_type);


--
-- Name: idx_compliance_rule_domain; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_compliance_rule_domain ON ecos_control.sys_compliance_rule USING btree (domain);


--
-- Name: idx_compliance_rule_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_compliance_rule_status ON ecos_control.sys_compliance_rule USING btree (status);


--
-- Name: idx_data_field_res; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_data_field_res ON ecos_control.td_data_field USING btree (resource_id);


--
-- Name: idx_data_res_ds; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_data_res_ds ON ecos_control.td_data_resource USING btree (datasource_id);


--
-- Name: idx_decision_category; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_decision_category ON ecos_control.ecos_decision USING btree (category);


--
-- Name: idx_decision_mind; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_decision_mind ON ecos_control.ecos_decision_record USING btree (mind_id, is_deleted);


--
-- Name: idx_decision_scenario; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_decision_scenario ON ecos_control.ecos_decision_record USING btree (scenario_id, is_deleted);


--
-- Name: idx_dict_column_table_id; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_dict_column_table_id ON ecos_control.dict_column USING btree (table_id);


--
-- Name: idx_dict_table_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_dict_table_status ON ecos_control.dict_table USING btree (status);


--
-- Name: idx_dq_exec_executed_at; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_dq_exec_executed_at ON ecos_control.ecos_dq_execution_result USING btree (executed_at DESC);


--
-- Name: idx_dq_exec_rule_id; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_dq_exec_rule_id ON ecos_control.ecos_dq_execution_result USING btree (rule_id);


--
-- Name: idx_dq_rule_tenant; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_dq_rule_tenant ON ecos_control.ecos_dq_rule USING btree (tenant_id);


--
-- Name: idx_dw_wc_idem_layer; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_dw_wc_idem_layer ON ecos_control.td_dw_write_channel_idempotency USING btree (layer);


--
-- Name: idx_dw_wc_idem_trace; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_dw_wc_idem_trace ON ecos_control.td_dw_write_channel_idempotency USING btree (trace_id);


--
-- Name: idx_ecos_cog_run_inv_hyp; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ecos_cog_run_inv_hyp ON ecos_control.ecos_cognitive_run_invalidation USING btree (hypothesis_id);


--
-- Name: idx_ecos_cog_run_inv_run; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ecos_cog_run_inv_run ON ecos_control.ecos_cognitive_run_invalidation USING btree (run_id);


--
-- Name: idx_ecos_cog_run_inv_time; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ecos_cog_run_inv_time ON ecos_control.ecos_cognitive_run_invalidation USING btree (superseded_at DESC);


--
-- Name: idx_ecos_cognitive_belief_domain; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ecos_cognitive_belief_domain ON ecos_control.ecos_cognitive_belief USING btree (domain);


--
-- Name: idx_ecos_cognitive_belief_time; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ecos_cognitive_belief_time ON ecos_control.ecos_cognitive_belief USING btree (update_time DESC);


--
-- Name: idx_ecos_cognitive_evidence_source; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ecos_cognitive_evidence_source ON ecos_control.ecos_cognitive_evidence USING btree (source_type);


--
-- Name: idx_ecos_cognitive_evidence_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ecos_cognitive_evidence_status ON ecos_control.ecos_cognitive_evidence USING btree (status);


--
-- Name: idx_ecos_cognitive_evidence_time; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ecos_cognitive_evidence_time ON ecos_control.ecos_cognitive_evidence USING btree (create_time DESC);


--
-- Name: idx_ecos_cognitive_hypothesis_domain; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ecos_cognitive_hypothesis_domain ON ecos_control.ecos_cognitive_hypothesis USING btree (domain);


--
-- Name: idx_ecos_cognitive_hypothesis_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ecos_cognitive_hypothesis_status ON ecos_control.ecos_cognitive_hypothesis USING btree (status);


--
-- Name: idx_ecos_cognitive_hypothesis_time; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ecos_cognitive_hypothesis_time ON ecos_control.ecos_cognitive_hypothesis USING btree (update_time DESC);


--
-- Name: idx_ecos_control_audit_retry_due; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ecos_control_audit_retry_due ON ecos_control.ecos_runtime_audit_retry USING btree (status, next_retry_at);


--
-- Name: idx_ecos_control_dlq_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ecos_control_dlq_status ON ecos_control.ecos_runtime_event_dlq USING btree (status, first_seen_at);


--
-- Name: idx_ecos_warn_log_objid; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ecos_warn_log_objid ON ecos_control.ecos_warn_log USING btree (warn_objid);


--
-- Name: idx_ecos_warn_log_review_tag; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ecos_warn_log_review_tag ON ecos_control.ecos_warn_log USING btree (review_tag);


--
-- Name: idx_ecos_warn_log_time; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ecos_warn_log_time ON ecos_control.ecos_warn_log USING btree (warn_time DESC);


--
-- Name: idx_ecos_warn_log_type; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ecos_warn_log_type ON ecos_control.ecos_warn_log USING btree (warn_type);


--
-- Name: idx_extraction_source_type; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_extraction_source_type ON ecos_control.sys_extraction_source USING btree (source_type);


--
-- Name: idx_func_audit_caller; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_func_audit_caller ON ecos_control.ecos_function_audit_log USING btree (caller_id);


--
-- Name: idx_func_audit_created; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_func_audit_created ON ecos_control.ecos_function_audit_log USING btree (created_at DESC);


--
-- Name: idx_func_audit_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_func_audit_status ON ecos_control.ecos_function_audit_log USING btree (status);


--
-- Name: idx_glossary_term_domain; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_glossary_term_domain ON ecos_control.ecos_glossary_term USING btree (domain);


--
-- Name: idx_glossary_term_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_glossary_term_status ON ecos_control.ecos_glossary_term USING btree (status);


--
-- Name: idx_goal_log_goal; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_goal_log_goal ON ecos_control.ecos_wm_goal_log USING btree (goal_id);


--
-- Name: idx_goal_log_time; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_goal_log_time ON ecos_control.ecos_wm_goal_log USING btree (changed_at);


--
-- Name: idx_goal_tracking_goal; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_goal_tracking_goal ON ecos_control.ecos_goal_tracking USING btree (goal_id, recorded_at);


--
-- Name: idx_ifc_ref_type; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ifc_ref_type ON ecos_control.ecos_interface_ref USING btree (interface_type, is_deleted);


--
-- Name: idx_kb_cognitive_pipeline_created_at; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_kb_cognitive_pipeline_created_at ON ecos_control.kb_cognitive_pipeline USING btree (created_at DESC) WHERE (is_deleted = 0);


--
-- Name: idx_kb_cognitive_pipeline_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_kb_cognitive_pipeline_status ON ecos_control.kb_cognitive_pipeline USING btree (status) WHERE (is_deleted = 0);


--
-- Name: idx_kb_lineage_event_format; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_kb_lineage_event_format ON ecos_control.kb_lineage_event USING btree (format) WHERE (is_deleted = 0);


--
-- Name: idx_kb_lineage_event_parse_at; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_kb_lineage_event_parse_at ON ecos_control.kb_lineage_event USING btree (parse_at DESC) WHERE (is_deleted = 0);


--
-- Name: idx_kb_ontology_snapshot_created_at; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_kb_ontology_snapshot_created_at ON ecos_control.kb_ontology_snapshot USING btree (created_at DESC) WHERE (is_deleted = 0);


--
-- Name: idx_kb_ontology_snapshot_ontology; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_kb_ontology_snapshot_ontology ON ecos_control.kb_ontology_snapshot USING btree (ontology_id) WHERE ((is_deleted = 0) OR (is_deleted = 2));


--
-- Name: idx_marketplace_asset_category; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_marketplace_asset_category ON ecos_control.ecos_marketplace_asset USING btree (category);


--
-- Name: idx_marketplace_asset_ontology; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_marketplace_asset_ontology ON ecos_control.ecos_marketplace_asset USING btree (ontology_entity_id);


--
-- Name: idx_marketplace_asset_popularity; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_marketplace_asset_popularity ON ecos_control.ecos_marketplace_asset USING btree (popularity DESC);


--
-- Name: idx_marketplace_asset_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_marketplace_asset_status ON ecos_control.ecos_marketplace_asset USING btree (status);


--
-- Name: idx_mask_strategy_enabled; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_mask_strategy_enabled ON ecos_control.ecos_mask_strategy USING btree (tenant_id, enabled);


--
-- Name: idx_mcl_datasource; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_mcl_datasource ON ecos_control.td_metadata_collect_log USING btree (datasource_id, create_time DESC);


--
-- Name: idx_mind_scenario; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_mind_scenario ON ecos_control.ecos_scenario_mind USING btree (scenario_id, is_deleted);


--
-- Name: idx_obj_links_relation; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_obj_links_relation ON ecos_control.ecos_object_links USING btree (relation_code);


--
-- Name: idx_obj_links_source; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_obj_links_source ON ecos_control.ecos_object_links USING btree (source_id);


--
-- Name: idx_obj_links_target; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_obj_links_target ON ecos_control.ecos_object_links USING btree (target_id);


--
-- Name: idx_obj_rel_source; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_obj_rel_source ON ecos_control.ecos_object_relationship USING btree (source_object_id);


--
-- Name: idx_obj_rel_target; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_obj_rel_target ON ecos_control.ecos_object_relationship USING btree (target_object_id);


--
-- Name: idx_ontology_entity_tenant; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ontology_entity_tenant ON ecos_control.ecos_ontology_entity USING btree (tenant_id);


--
-- Name: idx_pareto_problem_created; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_pareto_problem_created ON ecos_control.ecos_pareto_problem USING btree (created_at DESC);


--
-- Name: idx_pipeline_edge_def; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_pipeline_edge_def ON ecos_control.ecos_pipeline_edge USING btree (definition_id);


--
-- Name: idx_pipeline_node_def; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_pipeline_node_def ON ecos_control.ecos_pipeline_node USING btree (definition_id);


--
-- Name: idx_provenance_entity; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_provenance_entity ON ecos_control.ecos_provenance_entry USING btree (entity_type, entity_id);


--
-- Name: idx_rls_policy_resource; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_rls_policy_resource ON ecos_control.ecos_rls_policy USING btree (resource_id);


--
-- Name: idx_rls_policy_table; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_rls_policy_table ON ecos_control.ecos_rls_policy USING btree (table_name, enabled, priority);


--
-- Name: idx_rsp_clearance; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_rsp_clearance ON ecos_control.td_role_security_profile USING btree (clearance_level);


--
-- Name: idx_rsp_org; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_rsp_org ON ecos_control.td_role_security_profile USING btree (org_id);


--
-- Name: idx_rsp_scope; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_rsp_scope ON ecos_control.td_role_security_profile USING btree (scope_type);


--
-- Name: idx_rsp_tenant; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_rsp_tenant ON ecos_control.td_role_security_profile USING btree (tenant_id);


--
-- Name: idx_scenario_bind_tid; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_scenario_bind_tid ON ecos_control.ecos_scenario_binding USING btree (target_id);


--
-- Name: idx_scenario_bindsc; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_scenario_bindsc ON ecos_control.ecos_scenario_binding USING btree (scenario_id);


--
-- Name: idx_scenario_bindst; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_scenario_bindst ON ecos_control.ecos_scenario_binding USING btree (binding_type);


--
-- Name: idx_scenario_run_mode; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_scenario_run_mode ON ecos_control.ecos_scenario_run USING btree (scenario_id, run_mode, is_deleted);


--
-- Name: idx_scenario_run_sc; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_scenario_run_sc ON ecos_control.ecos_scenario_run USING btree (scenario_id);


--
-- Name: idx_scenario_run_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_scenario_run_status ON ecos_control.ecos_scenario_run USING btree (status);


--
-- Name: idx_scenario_run_time; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_scenario_run_time ON ecos_control.ecos_scenario_run USING btree (create_time DESC);


--
-- Name: idx_scenario_run_type; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_scenario_run_type ON ecos_control.ecos_scenario_run USING btree (run_type);


--
-- Name: idx_schema_changes_ack; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_schema_changes_ack ON ecos_control.schema_changes USING btree (acknowledged, detected_at);


--
-- Name: idx_sec_policy_domain; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_sec_policy_domain ON ecos_control.ecos_security_policy USING btree (domain, is_deleted);


--
-- Name: idx_spans_created; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_spans_created ON ecos_control.ecos_spans USING btree (created_at DESC);


--
-- Name: idx_spans_path; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_spans_path ON ecos_control.ecos_spans USING btree (http_path);


--
-- Name: idx_spans_trace; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_spans_trace ON ecos_control.ecos_spans USING btree (trace_id);


--
-- Name: idx_sys_compliance_rule_domain; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_sys_compliance_rule_domain ON ecos_control.sys_compliance_rule USING btree (domain);


--
-- Name: idx_sys_compliance_rule_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_sys_compliance_rule_status ON ecos_control.sys_compliance_rule USING btree (status);


--
-- Name: idx_sysman_config_type_env; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_sysman_config_type_env ON ecos_control.td_config USING btree (config_type, environment);


--
-- Name: idx_sysman_orgperm_org; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_sysman_orgperm_org ON ecos_control.td_org_permission USING btree (org_id);


--
-- Name: idx_sysman_perm_res_action; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX idx_sysman_perm_res_action ON ecos_control.td_permission USING btree (resource_id, action);


--
-- Name: idx_sysman_role_code; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX idx_sysman_role_code ON ecos_control.td_role USING btree (role_code);


--
-- Name: idx_sysman_role_parent; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_sysman_role_parent ON ecos_control.td_role USING btree (parent_role_id);


--
-- Name: idx_sysman_user_email; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX idx_sysman_user_email ON ecos_control.td_user USING btree (email);


--
-- Name: idx_sysman_user_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_sysman_user_status ON ecos_control.td_user USING btree (status);


--
-- Name: idx_sysman_user_tenant; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_sysman_user_tenant ON ecos_control.td_user USING btree (tenant_id);


--
-- Name: idx_sysman_user_username; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX idx_sysman_user_username ON ecos_control.td_user USING btree (username);


--
-- Name: idx_sysman_users_username; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX idx_sysman_users_username ON ecos_control.users USING btree (username);


--
-- Name: idx_task_created; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_task_created ON ecos_control.ecos_task USING btree (created_at DESC);


--
-- Name: idx_task_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_task_status ON ecos_control.ecos_task USING btree (status);


--
-- Name: idx_task_type; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_task_type ON ecos_control.ecos_task USING btree (task_type);


--
-- Name: idx_td_datasource_create_time; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_td_datasource_create_time ON ecos_control.td_datasource USING btree (create_time DESC);


--
-- Name: idx_td_runtime_task_created; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_td_runtime_task_created ON ecos_control.td_runtime_task USING btree ("CREATED_TIME" DESC NULLS LAST);


--
-- Name: idx_td_runtime_task_execution_task; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_td_runtime_task_execution_task ON ecos_control.td_runtime_task_execution USING btree ("TASK_ID", "START_TIME");


--
-- Name: idx_td_runtime_task_log_task; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_td_runtime_task_log_task ON ecos_control.td_runtime_task_log USING btree ("TASK_ID", "CREATED_TIME");


--
-- Name: idx_td_runtime_task_status_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_td_runtime_task_status_status ON ecos_control.td_runtime_task_status USING btree ("STATUS");


--
-- Name: idx_td_runtime_task_status_updated; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_td_runtime_task_status_updated ON ecos_control.td_runtime_task_status USING btree ("UPDATED_TIME" DESC NULLS LAST);


--
-- Name: idx_td_runtime_task_tenant; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_td_runtime_task_tenant ON ecos_control.td_runtime_task USING btree ("TENANT_ID", is_deleted);


--
-- Name: idx_td_runtime_task_type; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_td_runtime_task_type ON ecos_control.td_runtime_task USING btree ("TASK_TYPE");


--
-- Name: idx_td_task_create_time; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_td_task_create_time ON ecos_control.td_runtime_task USING btree (create_time);


--
-- Name: idx_td_task_domain; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_td_task_domain ON ecos_control.td_runtime_task USING btree (domain);


--
-- Name: idx_td_task_is_deleted; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_td_task_is_deleted ON ecos_control.td_runtime_task USING btree (is_deleted) WHERE (is_deleted = 0);


--
-- Name: idx_td_task_plan_cron; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_td_task_plan_cron ON ecos_control.td_runtime_task_plan USING btree (cron_expression) WHERE (cron_expression IS NOT NULL);


--
-- Name: idx_td_task_plan_last_run; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_td_task_plan_last_run ON ecos_control.td_runtime_task_plan USING btree (last_run_at);


--
-- Name: idx_td_task_plan_last_run_at; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_td_task_plan_last_run_at ON ecos_control.td_runtime_task_plan USING btree (last_run_at);


--
-- Name: idx_td_task_plan_next_run; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_td_task_plan_next_run ON ecos_control.td_runtime_task_plan USING btree (next_run_at) WHERE (next_run_at IS NOT NULL);


--
-- Name: idx_td_task_plan_next_run_at; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_td_task_plan_next_run_at ON ecos_control.td_runtime_task_plan USING btree (next_run_at) WHERE (next_run_at IS NOT NULL);


--
-- Name: idx_td_task_plan_task_type; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_td_task_plan_task_type ON ecos_control.td_runtime_task_plan USING btree ("TASK_TYPE");


--
-- Name: idx_td_task_task_type; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_td_task_task_type ON ecos_control.td_runtime_task USING btree ("TASK_TYPE", is_deleted);


--
-- Name: idx_tdr_layer_bucket; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_tdr_layer_bucket ON ecos_control.td_data_resource USING btree (layer_bucket);


--
-- Name: idx_tdr_storage_kind; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_tdr_storage_kind ON ecos_control.td_data_resource USING btree (storage_kind);


--
-- Name: idx_tenant_quota_type; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX idx_tenant_quota_type ON ecos_control.ecos_tenant_quota USING btree (tenant_id, quota_type);


--
-- Name: idx_tenant_usage_date; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_tenant_usage_date ON ecos_control.ecos_tenant_usage USING btree (tenant_id, usage_date DESC);


--
-- Name: idx_timeline_entity; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_timeline_entity ON ecos_control.ecos_object_timeline USING btree (entity_code, created_at DESC);


--
-- Name: idx_timeline_object; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_timeline_object ON ecos_control.ecos_object_timeline USING btree (object_id, created_at DESC);


--
-- Name: idx_token_usage_created; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_token_usage_created ON ecos_control.ecos_token_usage USING btree (created_at DESC);


--
-- Name: idx_token_usage_trace; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_token_usage_trace ON ecos_control.ecos_token_usage USING btree (trace_id);


--
-- Name: idx_usp_clearance; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_usp_clearance ON ecos_control.td_user_security_profile USING btree (clearance_level);


--
-- Name: idx_usp_org; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_usp_org ON ecos_control.td_user_security_profile USING btree (org_id);


--
-- Name: idx_usp_scope; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_usp_scope ON ecos_control.td_user_security_profile USING btree (scope_type);


--
-- Name: idx_usp_tenant; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_usp_tenant ON ecos_control.td_user_security_profile USING btree (tenant_id);


--
-- Name: idx_v271_bp_tenant_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_v271_bp_tenant_status ON ecos_control.ecos_scenario_blueprint USING btree (tenant_id, status, is_deleted);


--
-- Name: idx_wf_approval_instance; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_wf_approval_instance ON ecos_control.ecos_workflow_approval USING btree (instance_id);


--
-- Name: idx_wf_approval_task; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_wf_approval_task ON ecos_control.ecos_workflow_approval USING btree (task_id);


--
-- Name: idx_wf_instance_object; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_wf_instance_object ON ecos_control.ecos_workflow_instance USING btree (triggered_object_id);


--
-- Name: idx_wf_instance_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_wf_instance_status ON ecos_control.ecos_workflow_instance USING btree (status);


--
-- Name: idx_wf_instance_workflow; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_wf_instance_workflow ON ecos_control.ecos_workflow_instance USING btree (workflow_id);


--
-- Name: idx_wf_log_instance; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_wf_log_instance ON ecos_control.ecos_workflow_log USING btree (instance_id, created_at);


--
-- Name: idx_wf_log_trace; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_wf_log_trace ON ecos_control.ecos_workflow_log USING btree (trace_id);


--
-- Name: idx_wf_task_assignee; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_wf_task_assignee ON ecos_control.ecos_workflow_task USING btree (assignee);


--
-- Name: idx_wf_task_instance; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_wf_task_instance ON ecos_control.ecos_workflow_task USING btree (instance_id);


--
-- Name: idx_wf_task_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_wf_task_status ON ecos_control.ecos_workflow_task USING btree (status);


--
-- Name: idx_wm_causal_source; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_wm_causal_source ON ecos_control.ecos_wm_causal_link USING btree (source_goal_id);


--
-- Name: idx_wm_causal_target; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_wm_causal_target ON ecos_control.ecos_wm_causal_link USING btree (target_goal_id);


--
-- Name: idx_wm_goal_parent; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_wm_goal_parent ON ecos_control.ecos_wm_goal USING btree (parent_id);


--
-- Name: idx_wm_goal_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_wm_goal_status ON ecos_control.ecos_wm_goal USING btree (status);


--
-- Name: idx_wm_scenario_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_wm_scenario_status ON ecos_control.ecos_wm_scenario USING btree (status);


--
-- Name: idx_workflow_log_instance; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_workflow_log_instance ON ecos_control.ecos_workflow_log USING btree (instance_id, created_at DESC);


--
-- Name: idx_ws_status; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE INDEX idx_ws_status ON ecos_control.ecos_world_scenarios USING btree (status);


--
-- Name: uk_ecos_git_repo_repository_id; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX uk_ecos_git_repo_repository_id ON ecos_control.ecos_git_repo USING btree (repository_id) WHERE (is_deleted = 0);


--
-- Name: uk_v271_bp_scen_ver; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX uk_v271_bp_scen_ver ON ecos_control.ecos_scenario_blueprint USING btree (scenario_id, version, is_deleted);


--
-- Name: uniq_cog_model_id_ver; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX uniq_cog_model_id_ver ON ecos_control.ecos_cognitive_model USING btree (model_id, version);


--
-- Name: uniq_crypto_key_ver; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX uniq_crypto_key_ver ON ecos_control.ecos_security_crypto_key USING btree (key_id, key_version);


--
-- Name: uniq_dw_wc_idempotency_key; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX uniq_dw_wc_idempotency_key ON ecos_control.td_dw_write_channel_idempotency USING btree (idempotency_key);


--
-- Name: uniq_ecos_cog_run_inv_evt_run; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX uniq_ecos_cog_run_inv_evt_run ON ecos_control.ecos_cognitive_run_invalidation USING btree (event_id, run_id);


--
-- Name: uniq_ecos_cognitive_belief_variable_ver; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX uniq_ecos_cognitive_belief_variable_ver ON ecos_control.ecos_cognitive_belief USING btree (variable_name, domain, version);


--
-- Name: uniq_ecos_cognitive_evidence_code; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX uniq_ecos_cognitive_evidence_code ON ecos_control.ecos_cognitive_evidence USING btree (evidence_code);


--
-- Name: uniq_ecos_cognitive_hypothesis_code; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX uniq_ecos_cognitive_hypothesis_code ON ecos_control.ecos_cognitive_hypothesis USING btree (hypothesis_code);


--
-- Name: uniq_ecos_control_sec_asset_key; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX uniq_ecos_control_sec_asset_key ON ecos_control.ecos_security_asset USING btree (tenant_id, asset_key) WHERE (is_deleted = 0);


--
-- Name: uniq_ecos_warn_log_log_id; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX uniq_ecos_warn_log_log_id ON ecos_control.ecos_warn_log USING btree (log_id);


--
-- Name: uq_mind_active; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX uq_mind_active ON ecos_control.ecos_scenario_mind USING btree (scenario_id) WHERE ((is_deleted = 0) AND (active_mind = 1));


--
-- Name: uq_mind_scenario_label; Type: INDEX; Schema: ecos_control; Owner: -
--

CREATE UNIQUE INDEX uq_mind_scenario_label ON ecos_control.ecos_scenario_mind USING btree (scenario_id, mind_label) WHERE (is_deleted = 0);


--
-- Name: idx_asset_category; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_asset_category ON ecos_data.ecos_data_asset USING btree (category_id);


--
-- Name: idx_asset_field_asset; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_asset_field_asset ON ecos_data.ecos_data_asset_field USING btree (asset_id);


--
-- Name: idx_asset_field_conf; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_asset_field_conf ON ecos_data.ecos_data_asset_field USING btree (confirmed);


--
-- Name: idx_asset_field_sens; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_asset_field_sens ON ecos_data.ecos_data_asset_field USING btree (field_sensitivity);


--
-- Name: idx_asset_owner; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_asset_owner ON ecos_data.ecos_data_asset USING btree (owner);


--
-- Name: idx_asset_sensitivity; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_asset_sensitivity ON ecos_data.ecos_data_asset USING btree (sensitivity_level);


--
-- Name: idx_category_parent; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_category_parent ON ecos_data.td_data_category USING btree (parent_id);


--
-- Name: idx_category_tree_level; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_category_tree_level ON ecos_data.ecos_data_category_tree USING btree (level);


--
-- Name: idx_category_tree_parent; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_category_tree_parent ON ecos_data.ecos_data_category_tree USING btree (parent_id);


--
-- Name: idx_data_cat_parent; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_data_cat_parent ON ecos_data.td_data_category USING btree (parent_id);


--
-- Name: idx_data_level_code; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_data_level_code ON ecos_data.ecos_data_level_def USING btree (level_code);


--
-- Name: idx_data_pdef_status; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_data_pdef_status ON ecos_data.ecos_pipeline_definition USING btree (status);


--
-- Name: idx_data_pdef_tenant; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_data_pdef_tenant ON ecos_data.ecos_pipeline_definition USING btree (tenant_id);


--
-- Name: idx_data_pexec_pipeline; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_data_pexec_pipeline ON ecos_data.ecos_pipeline_execution USING btree (pipeline_id);


--
-- Name: idx_data_pexec_status; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_data_pexec_status ON ecos_data.ecos_pipeline_execution USING btree (status);


--
-- Name: idx_data_pexec_tenant; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_data_pexec_tenant ON ecos_data.ecos_pipeline_execution USING btree (tenant_id);


--
-- Name: idx_dq_alert_level; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_alert_level ON ecos_data.dq_alert_record USING btree (alert_level);


--
-- Name: idx_dq_alert_rule; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_alert_rule ON ecos_data.dq_alert_record USING btree (rule_id, created_at DESC);


--
-- Name: idx_dq_alert_status; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_alert_status ON ecos_data.dq_alert_record USING btree (status) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'NOTIFIED'::character varying])::text[]));


--
-- Name: idx_dq_alert_time; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_alert_time ON ecos_data.dq_alert_record USING btree (created_at DESC);


--
-- Name: idx_dq_check_rule_time; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_check_rule_time ON ecos_data.dq_rule_check USING btree (rule_id, executed_at DESC);


--
-- Name: idx_dq_check_time; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_check_time ON ecos_data.dq_rule_check USING btree (executed_at DESC);


--
-- Name: idx_dq_knowledge_category; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_knowledge_category ON ecos_data.dq_knowledge_entry USING btree (category);


--
-- Name: idx_dq_knowledge_type_source; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_knowledge_type_source ON ecos_data.dq_knowledge_entry USING btree (source_type, source_id);


--
-- Name: idx_dq_mig_report_outcome; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_mig_report_outcome ON ecos_data.ecos_dq_legacy_migration_report USING btree (outcome);


--
-- Name: idx_dq_report_scope; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_report_scope ON ecos_data.dq_report USING btree (scope, end_date DESC);


--
-- Name: idx_dq_report_type_date; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_report_type_date ON ecos_data.dq_report USING btree (report_type, end_date DESC);


--
-- Name: idx_dq_rule_category; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_rule_category ON ecos_data.dq_rule USING btree (category) WHERE (is_deleted = false);


--
-- Name: idx_dq_rule_domain; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_rule_domain ON ecos_data.dq_rule USING btree (domain) WHERE (is_deleted = false);


--
-- Name: idx_dq_rule_legacy; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_rule_legacy ON ecos_data.dq_rule USING btree (legacy_rule_id);


--
-- Name: idx_dq_rule_rule_type; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_rule_rule_type ON ecos_data.dq_rule USING btree (rule_type) WHERE (is_deleted = false);


--
-- Name: idx_dq_rule_status; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_rule_status ON ecos_data.dq_rule USING btree (status) WHERE (is_deleted = false);


--
-- Name: idx_dq_rule_status_active; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_rule_status_active ON ecos_data.dq_rule USING btree (status, is_active);


--
-- Name: idx_dq_rule_target; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_rule_target ON ecos_data.dq_rule USING btree (target_kind, target_id) WHERE (is_deleted = false);


--
-- Name: idx_dq_rule_version_rule; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_rule_version_rule ON ecos_data.dq_rule_version USING btree (rule_id, version_number DESC);


--
-- Name: idx_dq_schedule_enable; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_schedule_enable ON ecos_data.dq_schedule USING btree (trigger_type) WHERE (enabled AND (is_deleted = false));


--
-- Name: idx_dq_score_asset_type; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_score_asset_type ON ecos_data.dq_score_asset USING btree (asset_type, asset_id);


--
-- Name: idx_dq_score_scope_time; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_dq_score_scope_time ON ecos_data.dq_score_snapshot USING btree (scope_type, scope_id, evaluated_at DESC);


--
-- Name: idx_ecos_dq_score_asset_asset; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_ecos_dq_score_asset_asset ON ecos_data.ecos_dq_score_asset USING btree (asset_type, asset_id);


--
-- Name: idx_ecos_dq_score_asset_grade; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_ecos_dq_score_asset_grade ON ecos_data.ecos_dq_score_asset USING btree (grade, snapshot_at);


--
-- Name: idx_ecos_dq_score_asset_time; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_ecos_dq_score_asset_time ON ecos_data.ecos_dq_score_asset USING btree (snapshot_at);


--
-- Name: idx_edps_status; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_edps_status ON ecos_data.ecos_data_period_status USING btree (status, is_deleted);


--
-- Name: idx_lineage_edge_src; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_lineage_edge_src ON ecos_data.ecos_data_lineage_edge USING btree (source_node_id);


--
-- Name: idx_lineage_edge_task; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_lineage_edge_task ON ecos_data.ecos_data_lineage_edge USING btree (pipeline_task_id);


--
-- Name: idx_lineage_edge_tgt; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_lineage_edge_tgt ON ecos_data.ecos_data_lineage_edge USING btree (target_node_id);


--
-- Name: idx_lineage_node_ds; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_lineage_node_ds ON ecos_data.ecos_data_lineage_node USING btree (datasource_id);


--
-- Name: idx_lineage_node_task; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_lineage_node_task ON ecos_data.ecos_data_lineage_node USING btree (pipeline_task_id) WHERE (pipeline_task_id IS NOT NULL);


--
-- Name: idx_lineage_node_type; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_lineage_node_type ON ecos_data.ecos_data_lineage_node USING btree (node_type);


--
-- Name: idx_pipeline_tenant; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_pipeline_tenant ON ecos_data.ecos_pipeline_definition USING btree (tenant_id);


--
-- Name: idx_query_history_ds; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_query_history_ds ON ecos_data.ecos_query_history USING btree (datasource_id);


--
-- Name: idx_query_history_started; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_query_history_started ON ecos_data.ecos_query_history USING btree (started_at DESC);


--
-- Name: idx_v272_cand_target; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_v272_cand_target ON ecos_data.ecos_data_governance_candidate USING btree (target_ref, is_deleted);


--
-- Name: idx_v272_cand_tenant; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_v272_cand_tenant ON ecos_data.ecos_data_governance_candidate USING btree (tenant_id, status, is_deleted);


--
-- Name: idx_wo_alert; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_wo_alert ON ecos_data.dq_work_order USING btree (alert_id);


--
-- Name: idx_wo_asset; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_wo_asset ON ecos_data.dq_work_order USING btree (asset_id) WHERE (is_deleted = false);


--
-- Name: idx_wo_severity; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_wo_severity ON ecos_data.dq_work_order USING btree (severity, status) WHERE (is_deleted = false);


--
-- Name: idx_wo_status; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE INDEX idx_wo_status ON ecos_data.dq_work_order USING btree (status) WHERE (is_deleted = false);


--
-- Name: uk_v272_cand_idem; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE UNIQUE INDEX uk_v272_cand_idem ON ecos_data.ecos_data_governance_candidate USING btree (tenant_id, idempotency_key, is_deleted);


--
-- Name: uniq_asset_resource_domain; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE UNIQUE INDEX uniq_asset_resource_domain ON ecos_data.ecos_data_asset USING btree (resource_id, domain);


--
-- Name: uniq_dq_mig_report; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE UNIQUE INDEX uniq_dq_mig_report ON ecos_data.ecos_dq_legacy_migration_report USING btree (migration_batch, legacy_rule_id);


--
-- Name: uniq_edps_period; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE UNIQUE INDEX uniq_edps_period ON ecos_data.ecos_data_period_status USING btree (period, is_deleted);


--
-- Name: uniq_ypm_report_task; Type: INDEX; Schema: ecos_data; Owner: -
--

CREATE UNIQUE INDEX uniq_ypm_report_task ON ecos_data.ecos_pipeline_yaml_migration_report USING btree (migration_batch, task_id);


--
-- Name: idx_afcr_create; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_afcr_create ON ecos_dw.ecos_application_forecast_run USING btree (create_time);


--
-- Name: idx_afcr_idem; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_afcr_idem ON ecos_dw.ecos_application_forecast_run USING btree (idempotency_key, is_deleted);


--
-- Name: idx_afcr_reused; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_afcr_reused ON ecos_dw.ecos_application_forecast_run USING btree (reused_from_run_id);


--
-- Name: idx_afcr_scenario; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_afcr_scenario ON ecos_dw.ecos_application_forecast_run USING btree (scenario_id);


--
-- Name: idx_afcr_status; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_afcr_status ON ecos_dw.ecos_application_forecast_run USING btree (status);


--
-- Name: idx_afcr_task; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_afcr_task ON ecos_dw.ecos_application_forecast_run USING btree (task_id);


--
-- Name: idx_app_wagent_option_amount_tenant; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_app_wagent_option_amount_tenant ON ecos_dw.ecos_application_wagent_option_amount USING btree (tenant_id, is_deleted);


--
-- Name: idx_bcf_dept; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_bcf_dept ON ecos_dw.ecos_biz_cost_fact USING btree (department_id, period);


--
-- Name: idx_bcf_project; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_bcf_project ON ecos_dw.ecos_biz_cost_fact USING btree (project_id, period);


--
-- Name: idx_bpa_dept; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_bpa_dept ON ecos_dw.ecos_biz_project_attribution USING btree (department_id);


--
-- Name: idx_bpa_project; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_bpa_project ON ecos_dw.ecos_biz_project_attribution USING btree (project_id);


--
-- Name: idx_brf_hash; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_brf_hash ON ecos_dw.ecos_biz_resource_fact USING btree (staff_ref_hash);


--
-- Name: idx_brf_project; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_brf_project ON ecos_dw.ecos_biz_resource_fact USING btree (project_id, period);


--
-- Name: idx_bsf_dept; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_bsf_dept ON ecos_dw.ecos_biz_stage_fact USING btree (department_id);


--
-- Name: idx_bsf_project; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_bsf_project ON ecos_dw.ecos_biz_stage_fact USING btree (project_id, period);


--
-- Name: idx_caf_dept; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_caf_dept ON ecos_dw.fc_cost_allocation_fact USING btree (department_id);


--
-- Name: idx_caf_project; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_caf_project ON ecos_dw.fc_cost_allocation_fact USING btree (project_id, period);


--
-- Name: idx_dw_doc_chunk_doc; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_dw_doc_chunk_doc ON ecos_dw.doc_chunk USING btree (doc_id);


--
-- Name: idx_dw_doc_parse_status; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_dw_doc_parse_status ON ecos_dw.doc USING btree (parse_status);


--
-- Name: idx_eaao_act; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_eaao_act ON ecos_dw.ecos_application_action_outcome USING btree (action_id, is_deleted);


--
-- Name: idx_eaao_fact; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_eaao_fact ON ecos_dw.ecos_application_action_outcome USING btree (fact_id, is_deleted);


--
-- Name: idx_eaao_run; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_eaao_run ON ecos_dw.ecos_application_action_outcome USING btree (forecast_run_id, is_deleted);


--
-- Name: idx_ecos_kb_vec_fb_model; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_ecos_kb_vec_fb_model ON ecos_dw.ecos_kb_vector_fallback USING btree (embedding_model, domain, is_deleted);


--
-- Name: idx_fc_backtest_period; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_fc_backtest_period ON ecos_dw.ecos_fc_backtest USING btree (period, granularity);


--
-- Name: idx_fc_detail_parent; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_fc_detail_parent ON ecos_dw.ecos_fc_result_detail USING btree (parent_detail_id);


--
-- Name: idx_fc_detail_project; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_fc_detail_project ON ecos_dw.ecos_fc_result_detail USING btree (project_id, period);


--
-- Name: idx_fc_detail_run; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_fc_detail_run ON ecos_dw.ecos_fc_result_detail USING btree (forecast_run_id);


--
-- Name: idx_fc_detail_run_cell; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_fc_detail_run_cell ON ecos_dw.ecos_fc_result_detail USING btree (forecast_run_id, metric_id, period, granularity);


--
-- Name: idx_fc_run_caliber; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_fc_run_caliber ON ecos_dw.ecos_fc_run USING btree (caliber_id, caliber_version);


--
-- Name: idx_fc_run_domain_time; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_fc_run_domain_time ON ecos_dw.ecos_fc_run USING btree (domain, as_of_time);


--
-- Name: idx_fc_run_scope_hash; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_fc_run_scope_hash ON ecos_dw.ecos_fc_run USING btree (scope_hash);


--
-- Name: idx_fc_run_status; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_fc_run_status ON ecos_dw.ecos_fc_run USING btree (status);


--
-- Name: idx_fis_asof; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_fis_asof ON ecos_dw.ecos_forecast_input_snapshot USING btree (as_of_time);


--
-- Name: idx_kbav_key; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_kbav_key ON ecos_dw.ecos_kb_assumption_value USING btree (assumption_key, domain);


--
-- Name: idx_kbps_key_window; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_kbps_key_window ON ecos_dw.ecos_kb_profile_stats USING btree (profile_key, window_from, window_to);


--
-- Name: idx_kbps_metric_domain; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_kbps_metric_domain ON ecos_dw.ecos_kb_profile_stats USING btree (domain, trace_id);


--
-- Name: idx_pcf_dept; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_pcf_dept ON ecos_dw.fc_project_period_fact USING btree (department_id);


--
-- Name: idx_pcf_project; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_pcf_project ON ecos_dw.fc_project_period_fact USING btree (project_id, period);


--
-- Name: idx_rii_dept; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_rii_dept ON ecos_dw.fc_resource_input_fact USING btree (department_id);


--
-- Name: idx_rii_project; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE INDEX idx_rii_project ON ecos_dw.fc_resource_input_fact USING btree (project_id, period);


--
-- Name: uniq_afcr_run_id; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE UNIQUE INDEX uniq_afcr_run_id ON ecos_dw.ecos_application_forecast_run USING btree (forecast_run_id, is_deleted);


--
-- Name: uniq_bcf_active; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE UNIQUE INDEX uniq_bcf_active ON ecos_dw.ecos_biz_cost_fact USING btree (project_id, is_pool, department_id, period, cost_category, direct_or_allocated, fact_type, is_active);


--
-- Name: uniq_bpa_active; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE UNIQUE INDEX uniq_bpa_active ON ecos_dw.ecos_biz_project_attribution USING btree (project_id, contract_id, department_id, effective_from, is_active);


--
-- Name: uniq_brf_active; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE UNIQUE INDEX uniq_brf_active ON ecos_dw.ecos_biz_resource_fact USING btree (project_id, department_id, period, staff_ref_hash, fact_type, is_active);


--
-- Name: uniq_bsf_active; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE UNIQUE INDEX uniq_bsf_active ON ecos_dw.ecos_biz_stage_fact USING btree (project_id, department_id, period, stage, fact_type, is_active);


--
-- Name: uniq_caf_active; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE UNIQUE INDEX uniq_caf_active ON ecos_dw.fc_cost_allocation_fact USING btree (project_id, department_id, period, is_active);


--
-- Name: uniq_dw_doc_chunk_chunk_id; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE UNIQUE INDEX uniq_dw_doc_chunk_chunk_id ON ecos_dw.doc_chunk USING btree (chunk_id);


--
-- Name: uniq_dw_doc_chunk_doc_idx; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE UNIQUE INDEX uniq_dw_doc_chunk_doc_idx ON ecos_dw.doc_chunk USING btree (doc_id, chunk_index);


--
-- Name: uniq_dw_doc_doc_id; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE UNIQUE INDEX uniq_dw_doc_doc_id ON ecos_dw.doc USING btree (doc_id);


--
-- Name: uniq_eaao_idem; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE UNIQUE INDEX uniq_eaao_idem ON ecos_dw.ecos_application_action_outcome USING btree (idempotency_key, is_deleted);


--
-- Name: uniq_fc_backtest_cell; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE UNIQUE INDEX uniq_fc_backtest_cell ON ecos_dw.ecos_fc_backtest USING btree (forecast_run_id, period, granularity, metric_id, is_deleted);


--
-- Name: uniq_fis_run; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE UNIQUE INDEX uniq_fis_run ON ecos_dw.ecos_forecast_input_snapshot USING btree (forecast_run_id);


--
-- Name: uniq_pcf_active; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE UNIQUE INDEX uniq_pcf_active ON ecos_dw.fc_project_period_fact USING btree (project_id, department_id, period, is_active);


--
-- Name: uniq_rii_active; Type: INDEX; Schema: ecos_dw; Owner: -
--

CREATE UNIQUE INDEX uniq_rii_active ON ecos_dw.fc_resource_input_fact USING btree (project_id, department_id, period, staff_ref, is_active);


--
-- Name: idx_infra_outbox_aggregate; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX idx_infra_outbox_aggregate ON ONLY ecos_infra.outbox_event USING btree (aggregate_type, aggregate_id);


--
-- Name: idx_infra_outbox_published; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX idx_infra_outbox_published ON ONLY ecos_infra.outbox_event USING btree (published) WHERE (NOT published);


--
-- Name: idx_infra_outbox_type; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX idx_infra_outbox_type ON ONLY ecos_infra.outbox_event USING btree (event_type);


--
-- Name: idx_infra_saga_status; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX idx_infra_saga_status ON ecos_infra.saga_instance USING btree (status);


--
-- Name: idx_infra_saga_type; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX idx_infra_saga_type ON ecos_infra.saga_instance USING btree (saga_type);


--
-- Name: idx_infra_schemaver_name_ver; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE UNIQUE INDEX idx_infra_schemaver_name_ver ON ecos_infra.schema_version USING btree (schema_name, version);


--
-- Name: outbox_event_2025_01_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_01_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_01 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_01_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_01_event_type_idx ON ecos_infra.outbox_event_2025_01 USING btree (event_type);


--
-- Name: outbox_event_2025_01_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_01_published_idx ON ecos_infra.outbox_event_2025_01 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_02_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_02_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_02 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_02_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_02_event_type_idx ON ecos_infra.outbox_event_2025_02 USING btree (event_type);


--
-- Name: outbox_event_2025_02_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_02_published_idx ON ecos_infra.outbox_event_2025_02 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_03_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_03_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_03 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_03_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_03_event_type_idx ON ecos_infra.outbox_event_2025_03 USING btree (event_type);


--
-- Name: outbox_event_2025_03_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_03_published_idx ON ecos_infra.outbox_event_2025_03 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_04_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_04_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_04 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_04_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_04_event_type_idx ON ecos_infra.outbox_event_2025_04 USING btree (event_type);


--
-- Name: outbox_event_2025_04_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_04_published_idx ON ecos_infra.outbox_event_2025_04 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_05_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_05_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_05 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_05_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_05_event_type_idx ON ecos_infra.outbox_event_2025_05 USING btree (event_type);


--
-- Name: outbox_event_2025_05_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_05_published_idx ON ecos_infra.outbox_event_2025_05 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_06_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_06_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_06 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_06_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_06_event_type_idx ON ecos_infra.outbox_event_2025_06 USING btree (event_type);


--
-- Name: outbox_event_2025_06_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_06_published_idx ON ecos_infra.outbox_event_2025_06 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_07_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_07_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_07 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_07_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_07_event_type_idx ON ecos_infra.outbox_event_2025_07 USING btree (event_type);


--
-- Name: outbox_event_2025_07_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_07_published_idx ON ecos_infra.outbox_event_2025_07 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_08_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_08_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_08 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_08_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_08_event_type_idx ON ecos_infra.outbox_event_2025_08 USING btree (event_type);


--
-- Name: outbox_event_2025_08_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_08_published_idx ON ecos_infra.outbox_event_2025_08 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_09_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_09_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_09 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_09_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_09_event_type_idx ON ecos_infra.outbox_event_2025_09 USING btree (event_type);


--
-- Name: outbox_event_2025_09_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_09_published_idx ON ecos_infra.outbox_event_2025_09 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_10_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_10_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_10 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_10_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_10_event_type_idx ON ecos_infra.outbox_event_2025_10 USING btree (event_type);


--
-- Name: outbox_event_2025_10_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_10_published_idx ON ecos_infra.outbox_event_2025_10 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_11_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_11_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_11 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_11_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_11_event_type_idx ON ecos_infra.outbox_event_2025_11 USING btree (event_type);


--
-- Name: outbox_event_2025_11_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_11_published_idx ON ecos_infra.outbox_event_2025_11 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_12_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_12_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_12 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_12_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_12_event_type_idx ON ecos_infra.outbox_event_2025_12 USING btree (event_type);


--
-- Name: outbox_event_2025_12_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2025_12_published_idx ON ecos_infra.outbox_event_2025_12 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_01_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_01_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_01 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_01_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_01_event_type_idx ON ecos_infra.outbox_event_2026_01 USING btree (event_type);


--
-- Name: outbox_event_2026_01_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_01_published_idx ON ecos_infra.outbox_event_2026_01 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_02_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_02_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_02 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_02_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_02_event_type_idx ON ecos_infra.outbox_event_2026_02 USING btree (event_type);


--
-- Name: outbox_event_2026_02_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_02_published_idx ON ecos_infra.outbox_event_2026_02 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_03_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_03_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_03 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_03_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_03_event_type_idx ON ecos_infra.outbox_event_2026_03 USING btree (event_type);


--
-- Name: outbox_event_2026_03_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_03_published_idx ON ecos_infra.outbox_event_2026_03 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_04_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_04_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_04 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_04_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_04_event_type_idx ON ecos_infra.outbox_event_2026_04 USING btree (event_type);


--
-- Name: outbox_event_2026_04_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_04_published_idx ON ecos_infra.outbox_event_2026_04 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_05_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_05_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_05 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_05_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_05_event_type_idx ON ecos_infra.outbox_event_2026_05 USING btree (event_type);


--
-- Name: outbox_event_2026_05_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_05_published_idx ON ecos_infra.outbox_event_2026_05 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_06_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_06_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_06 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_06_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_06_event_type_idx ON ecos_infra.outbox_event_2026_06 USING btree (event_type);


--
-- Name: outbox_event_2026_06_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_06_published_idx ON ecos_infra.outbox_event_2026_06 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_07_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_07_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_07 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_07_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_07_event_type_idx ON ecos_infra.outbox_event_2026_07 USING btree (event_type);


--
-- Name: outbox_event_2026_07_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_07_published_idx ON ecos_infra.outbox_event_2026_07 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_08_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_08_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_08 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_08_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_08_event_type_idx ON ecos_infra.outbox_event_2026_08 USING btree (event_type);


--
-- Name: outbox_event_2026_08_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_08_published_idx ON ecos_infra.outbox_event_2026_08 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_09_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_09_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_09 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_09_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_09_event_type_idx ON ecos_infra.outbox_event_2026_09 USING btree (event_type);


--
-- Name: outbox_event_2026_09_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_09_published_idx ON ecos_infra.outbox_event_2026_09 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_10_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_10_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_10 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_10_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_10_event_type_idx ON ecos_infra.outbox_event_2026_10 USING btree (event_type);


--
-- Name: outbox_event_2026_10_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_10_published_idx ON ecos_infra.outbox_event_2026_10 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_11_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_11_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_11 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_11_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_11_event_type_idx ON ecos_infra.outbox_event_2026_11 USING btree (event_type);


--
-- Name: outbox_event_2026_11_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_11_published_idx ON ecos_infra.outbox_event_2026_11 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_12_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_12_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_12 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_12_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_12_event_type_idx ON ecos_infra.outbox_event_2026_12 USING btree (event_type);


--
-- Name: outbox_event_2026_12_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2026_12_published_idx ON ecos_infra.outbox_event_2026_12 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_01_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_01_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_01 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_01_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_01_event_type_idx ON ecos_infra.outbox_event_2027_01 USING btree (event_type);


--
-- Name: outbox_event_2027_01_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_01_published_idx ON ecos_infra.outbox_event_2027_01 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_02_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_02_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_02 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_02_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_02_event_type_idx ON ecos_infra.outbox_event_2027_02 USING btree (event_type);


--
-- Name: outbox_event_2027_02_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_02_published_idx ON ecos_infra.outbox_event_2027_02 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_03_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_03_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_03 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_03_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_03_event_type_idx ON ecos_infra.outbox_event_2027_03 USING btree (event_type);


--
-- Name: outbox_event_2027_03_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_03_published_idx ON ecos_infra.outbox_event_2027_03 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_04_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_04_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_04 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_04_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_04_event_type_idx ON ecos_infra.outbox_event_2027_04 USING btree (event_type);


--
-- Name: outbox_event_2027_04_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_04_published_idx ON ecos_infra.outbox_event_2027_04 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_05_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_05_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_05 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_05_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_05_event_type_idx ON ecos_infra.outbox_event_2027_05 USING btree (event_type);


--
-- Name: outbox_event_2027_05_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_05_published_idx ON ecos_infra.outbox_event_2027_05 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_06_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_06_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_06 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_06_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_06_event_type_idx ON ecos_infra.outbox_event_2027_06 USING btree (event_type);


--
-- Name: outbox_event_2027_06_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_06_published_idx ON ecos_infra.outbox_event_2027_06 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_07_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_07_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_07 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_07_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_07_event_type_idx ON ecos_infra.outbox_event_2027_07 USING btree (event_type);


--
-- Name: outbox_event_2027_07_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_07_published_idx ON ecos_infra.outbox_event_2027_07 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_08_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_08_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_08 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_08_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_08_event_type_idx ON ecos_infra.outbox_event_2027_08 USING btree (event_type);


--
-- Name: outbox_event_2027_08_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_08_published_idx ON ecos_infra.outbox_event_2027_08 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_09_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_09_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_09 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_09_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_09_event_type_idx ON ecos_infra.outbox_event_2027_09 USING btree (event_type);


--
-- Name: outbox_event_2027_09_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_09_published_idx ON ecos_infra.outbox_event_2027_09 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_10_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_10_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_10 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_10_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_10_event_type_idx ON ecos_infra.outbox_event_2027_10 USING btree (event_type);


--
-- Name: outbox_event_2027_10_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_10_published_idx ON ecos_infra.outbox_event_2027_10 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_11_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_11_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_11 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_11_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_11_event_type_idx ON ecos_infra.outbox_event_2027_11 USING btree (event_type);


--
-- Name: outbox_event_2027_11_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_11_published_idx ON ecos_infra.outbox_event_2027_11 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_12_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_12_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_12 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_12_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_12_event_type_idx ON ecos_infra.outbox_event_2027_12 USING btree (event_type);


--
-- Name: outbox_event_2027_12_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_2027_12_published_idx ON ecos_infra.outbox_event_2027_12 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_default_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_default_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_default USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_default_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_default_event_type_idx ON ecos_infra.outbox_event_default USING btree (event_type);


--
-- Name: outbox_event_default_published_idx; Type: INDEX; Schema: ecos_infra; Owner: -
--

CREATE INDEX outbox_event_default_published_idx ON ecos_infra.outbox_event_default USING btree (published) WHERE (NOT published);


--
-- Name: idx_ecos_kb_areq_asset; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_ecos_kb_areq_asset ON ecos_knowledge.ecos_kb_access_request USING btree (asset_id, status, is_deleted);


--
-- Name: idx_ecos_kb_gedge_dst; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_ecos_kb_gedge_dst ON ecos_knowledge.ecos_kb_graph_edge USING btree (target_node_id, edge_type, is_deleted);


--
-- Name: idx_ecos_kb_gedge_src; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_ecos_kb_gedge_src ON ecos_knowledge.ecos_kb_graph_edge USING btree (source_node_id, edge_type, is_deleted);


--
-- Name: idx_ecos_kb_gnode_onto; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_ecos_kb_gnode_onto ON ecos_knowledge.ecos_kb_graph_node USING btree (ontology_id, ontology_version, is_deleted);


--
-- Name: idx_ecos_kb_gnode_type; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_ecos_kb_gnode_type ON ecos_knowledge.ecos_kb_graph_node USING btree (node_type, domain, is_deleted);


--
-- Name: idx_ecos_kb_lineage_parse; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_ecos_kb_lineage_parse ON ecos_knowledge.ecos_kb_lineage_event USING btree (parse_time, lineage_format, is_deleted);


--
-- Name: idx_ecos_kb_mkt_cat; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_ecos_kb_mkt_cat ON ecos_knowledge.ecos_kb_market_asset USING btree (asset_category, status, is_deleted);


--
-- Name: idx_ecos_kb_onto_snap_dom; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_ecos_kb_onto_snap_dom ON ecos_knowledge.ecos_kb_ontology_snapshot USING btree (domain, is_deleted);


--
-- Name: idx_ecos_kb_term_type; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_ecos_kb_term_type ON ecos_knowledge.ecos_kb_term USING btree (term_type, status, is_deleted);


--
-- Name: idx_ecos_kbeat_job; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_ecos_kbeat_job ON ecos_knowledge.ecos_kb_extract_audit USING btree (job_id, create_time);


--
-- Name: idx_ecos_kbeic_doc; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_ecos_kbeic_doc ON ecos_knowledge.ecos_kb_extract_candidate USING btree (doc_id, is_deleted);


--
-- Name: idx_ecos_kbeic_status; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_ecos_kbeic_status ON ecos_knowledge.ecos_kb_extract_candidate USING btree (status, is_deleted);


--
-- Name: idx_ecos_kg_sync_log_dom; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_ecos_kg_sync_log_dom ON ecos_knowledge.ecos_kg_sync_log USING btree (domain, status, is_deleted);


--
-- Name: idx_ecos_kg_sync_log_job; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_ecos_kg_sync_log_job ON ecos_knowledge.ecos_kg_sync_log USING btree (job_id, create_time);


--
-- Name: idx_expert_rule_domain; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_expert_rule_domain ON ecos_knowledge.expert_rule USING btree (domain);


--
-- Name: idx_expert_rule_type; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_expert_rule_type ON ecos_knowledge.expert_rule USING btree (rule_type);


--
-- Name: idx_graph_edge_ontology_version; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_graph_edge_ontology_version ON ecos_knowledge.graph_edge USING btree (ontology_id, ontology_version);


--
-- Name: idx_graph_edge_source; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_graph_edge_source ON ecos_knowledge.graph_edge USING btree (source_id);


--
-- Name: idx_graph_edge_target; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_graph_edge_target ON ecos_knowledge.graph_edge USING btree (target_id);


--
-- Name: idx_graph_edge_type; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_graph_edge_type ON ecos_knowledge.graph_edge USING btree (type);


--
-- Name: idx_graph_node_label; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_graph_node_label ON ecos_knowledge.graph_node USING btree (label);


--
-- Name: idx_graph_node_label_trgm; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_graph_node_label_trgm ON ecos_knowledge.graph_node USING gin (label public.gin_trgm_ops);


--
-- Name: idx_graph_node_node_type; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_graph_node_node_type ON ecos_knowledge.graph_node USING btree (node_type);


--
-- Name: idx_graph_node_ontology_version; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_graph_node_ontology_version ON ecos_knowledge.graph_node USING btree (ontology_id, ontology_version);


--
-- Name: idx_kb_art_domain; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kb_art_domain ON ecos_knowledge.knowledge_article USING btree (domain);


--
-- Name: idx_kb_art_status; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kb_art_status ON ecos_knowledge.knowledge_article USING btree (status);


--
-- Name: idx_kb_doc_chunk_doc; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kb_doc_chunk_doc ON ecos_knowledge.kb_doc_chunk USING btree (doc_id);


--
-- Name: idx_kb_doc_chunk_status; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kb_doc_chunk_status ON ecos_knowledge.kb_doc_chunk USING btree (status);


--
-- Name: idx_kb_doc_parse_status; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kb_doc_parse_status ON ecos_knowledge.kb_doc USING btree (parse_status);


--
-- Name: idx_kb_emb_doc; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kb_emb_doc ON ecos_knowledge.knowledge_embedding USING btree (document_id);


--
-- Name: idx_kb_extract_audit_trace_id; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kb_extract_audit_trace_id ON ecos_knowledge.kb_extract_audit USING btree (trace_id);


--
-- Name: idx_kb_extract_watermark_ontology; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kb_extract_watermark_ontology ON ecos_knowledge.kb_extract_watermark USING btree (ontology_id);


--
-- Name: idx_kb_gedge_src; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kb_gedge_src ON ecos_knowledge.graph_edge USING btree (source_id);


--
-- Name: idx_kb_gedge_tgt; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kb_gedge_tgt ON ecos_knowledge.graph_edge USING btree (target_id);


--
-- Name: idx_kb_gedge_type; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kb_gedge_type ON ecos_knowledge.graph_edge USING btree (type);


--
-- Name: idx_kb_gnode_domain; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kb_gnode_domain ON ecos_knowledge.graph_node USING btree (domain);


--
-- Name: idx_kb_gnode_label; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kb_gnode_label ON ecos_knowledge.graph_node USING btree (label);


--
-- Name: idx_kb_kge_src; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kb_kge_src ON ecos_knowledge.ecos_knowledge_graph_edge USING btree (source_node_id);


--
-- Name: idx_kb_kge_tgt; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kb_kge_tgt ON ecos_knowledge.ecos_knowledge_graph_edge USING btree (target_node_id);


--
-- Name: idx_kb_nav_category_domain_parent; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kb_nav_category_domain_parent ON ecos_knowledge.kb_nav_category USING btree (domain, parent_id, is_deleted);


--
-- Name: idx_kb_rule_domain; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kb_rule_domain ON ecos_knowledge.expert_rule USING btree (domain);


--
-- Name: idx_kb_rule_type; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kb_rule_type ON ecos_knowledge.expert_rule USING btree (rule_type);


--
-- Name: idx_kba_expire; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kba_expire ON ecos_knowledge.ecos_kb_assumption USING btree (expire_at, status);


--
-- Name: idx_kba_scenario_status; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kba_scenario_status ON ecos_knowledge.ecos_kb_assumption USING btree (scenario_type, status, is_deleted);


--
-- Name: idx_kbea_job; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kbea_job ON ecos_knowledge.kb_extract_audit USING btree (job_id);


--
-- Name: idx_kbea_ts; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kbea_ts ON ecos_knowledge.kb_extract_audit USING btree (created_at DESC);


--
-- Name: idx_kbeic_doc; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kbeic_doc ON ecos_knowledge.kb_extract_candidate USING btree (doc_id);


--
-- Name: idx_kbeic_status; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kbeic_status ON ecos_knowledge.kb_extract_candidate USING btree (status);


--
-- Name: idx_kbp_domain_window; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kbp_domain_window ON ecos_knowledge.ecos_kb_profile USING btree (domain, window_from, window_to);


--
-- Name: idx_kbp_metric_status; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kbp_metric_status ON ecos_knowledge.ecos_kb_profile USING btree (metric_code, status, is_deleted);


--
-- Name: idx_kbsched_enabled; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kbsched_enabled ON ecos_knowledge.kb_scheduled_extract USING btree (enabled);


--
-- Name: idx_kg_node_created; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kg_node_created ON ecos_knowledge.ecos_knowledge_graph_node USING btree (created_at DESC);


--
-- Name: idx_kg_node_type; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kg_node_type ON ecos_knowledge.ecos_knowledge_graph_node USING btree (node_type);


--
-- Name: idx_kg_sync_log_created_at; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kg_sync_log_created_at ON ecos_knowledge.kg_sync_log USING btree (created_at DESC);


--
-- Name: idx_kg_sync_log_job_id; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kg_sync_log_job_id ON ecos_knowledge.kg_sync_log USING btree (job_id);


--
-- Name: idx_kg_sync_log_trace_id; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_kg_sync_log_trace_id ON ecos_knowledge.kg_sync_log USING btree (trace_id);


--
-- Name: idx_knowledge_article_category; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_knowledge_article_category ON ecos_knowledge.knowledge_article USING btree (knowledge_category);


--
-- Name: idx_knowledge_article_rule_ref; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_knowledge_article_rule_ref ON ecos_knowledge.knowledge_article USING btree (rule_ref);


--
-- Name: idx_knowledge_embedding_doc; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_knowledge_embedding_doc ON ecos_knowledge.knowledge_embedding USING btree (document_id);


--
-- Name: idx_knowledge_embedding_vec_hnsw; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_knowledge_embedding_vec_hnsw ON ecos_knowledge.knowledge_embedding USING hnsw (embedding_vec public.vector_cosine_ops);


--
-- Name: idx_nav_cat_domain; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_nav_cat_domain ON ecos_knowledge.kb_nav_category USING btree (domain) WHERE (is_deleted = 0);


--
-- Name: idx_nav_cat_parent; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_nav_cat_parent ON ecos_knowledge.kb_nav_category USING btree (parent_id) WHERE (is_deleted = 0);


--
-- Name: idx_rel_article; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_rel_article ON ecos_knowledge.kb_nav_article_rel USING btree (article_id) WHERE (is_deleted = 0);


--
-- Name: idx_rel_node; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE INDEX idx_rel_node ON ecos_knowledge.kb_nav_article_rel USING btree (node_id) WHERE (is_deleted = 0);


--
-- Name: uniq_ecos_kb_document_code; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE UNIQUE INDEX uniq_ecos_kb_document_code ON ecos_knowledge.ecos_kb_document USING btree (doc_code, is_deleted);


--
-- Name: uniq_kb_doc_chunk_doc_idx; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE UNIQUE INDEX uniq_kb_doc_chunk_doc_idx ON ecos_knowledge.kb_doc_chunk USING btree (doc_id, chunk_index);


--
-- Name: uniq_kb_nav_category; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE UNIQUE INDEX uniq_kb_nav_category ON ecos_knowledge.kb_nav_category USING btree (domain, parent_id, name, is_deleted);


--
-- Name: uniq_kba_key_ver; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE UNIQUE INDEX uniq_kba_key_ver ON ecos_knowledge.ecos_kb_assumption USING btree (assumption_key, assumption_version, is_deleted);


--
-- Name: uniq_kbp_key_ver; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE UNIQUE INDEX uniq_kbp_key_ver ON ecos_knowledge.ecos_kb_profile USING btree (profile_key, profile_version, is_deleted);


--
-- Name: uniq_nav_cat_name_domain; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE UNIQUE INDEX uniq_nav_cat_name_domain ON ecos_knowledge.kb_nav_category USING btree (domain, name) WHERE (is_deleted = 0);


--
-- Name: uniq_nav_tag_name_domain; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE UNIQUE INDEX uniq_nav_tag_name_domain ON ecos_knowledge.kb_nav_tag USING btree (domain, tag_name) WHERE (is_deleted = 0);


--
-- Name: uniq_rel_article_node_scope; Type: INDEX; Schema: ecos_knowledge; Owner: -
--

CREATE UNIQUE INDEX uniq_rel_article_node_scope ON ecos_knowledge.kb_nav_article_rel USING btree (article_id, node_id, scope) WHERE (is_deleted = 0);


--
-- Name: idx_ecos_caliber_domain; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_caliber_domain ON ecos_ontology.ecos_caliber USING btree (domain);


--
-- Name: idx_ecos_caliber_status; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_caliber_status ON ecos_ontology.ecos_caliber USING btree (status);


--
-- Name: idx_ecos_domain_tenant; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_domain_tenant ON ecos_ontology.ecos_domain USING btree (tenant_id);


--
-- Name: idx_ecos_func_audit_caller; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_func_audit_caller ON ecos_ontology.ecos_function_audit USING btree (caller_id);


--
-- Name: idx_ecos_func_audit_created; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_func_audit_created ON ecos_ontology.ecos_function_audit USING btree (create_time);


--
-- Name: idx_ecos_func_audit_domain; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_func_audit_domain ON ecos_ontology.ecos_function_audit USING btree (domain);


--
-- Name: idx_ecos_func_audit_status; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_func_audit_status ON ecos_ontology.ecos_function_audit USING btree (status);


--
-- Name: idx_ecos_func_audit_trace; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_func_audit_trace ON ecos_ontology.ecos_function_audit USING btree (trace_id);


--
-- Name: idx_ecos_metric_definition_domain; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_metric_definition_domain ON ecos_ontology.ecos_metric_definition USING btree (domain);


--
-- Name: idx_ecos_metric_definition_status; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_metric_definition_status ON ecos_ontology.ecos_metric_definition USING btree (status);


--
-- Name: idx_ecos_ontology_data_ontology; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_ontology_data_ontology ON ecos_ontology.ecos_ontology_data USING btree (ontology_id);


--
-- Name: idx_ecos_ontology_entity_ontology; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_ontology_entity_ontology ON ecos_ontology.ecos_ontology_entity USING btree (ontology_id);


--
-- Name: idx_ecos_ontology_git_archive_due; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_ontology_git_archive_due ON ecos_ontology.ecos_ontology_git_archive_pending USING btree (status, next_retry_at);


--
-- Name: idx_ecos_ontology_git_archive_onto; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_ontology_git_archive_onto ON ecos_ontology.ecos_ontology_git_archive_pending USING btree (ontology_id, version_id, is_deleted);


--
-- Name: idx_ecos_proposal_author; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_proposal_author ON ecos_ontology.ecos_ontology_proposal USING btree (author);


--
-- Name: idx_ecos_proposal_domain; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_proposal_domain ON ecos_ontology.ecos_ontology_proposal USING btree (domain);


--
-- Name: idx_ecos_proposal_domain_status; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_proposal_domain_status ON ecos_ontology.ecos_ontology_proposal USING btree (domain_code, status);


--
-- Name: idx_ecos_wf_appr_instance; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_wf_appr_instance ON ecos_ontology.ecos_workflow_approval USING btree (instance_id);


--
-- Name: idx_ecos_wf_appr_task; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_wf_appr_task ON ecos_ontology.ecos_workflow_approval USING btree (task_id);


--
-- Name: idx_ecos_workflow_domain; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_workflow_domain ON ecos_ontology.ecos_workflow USING btree (domain);


--
-- Name: idx_ecos_workflow_status; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_ecos_workflow_status ON ecos_ontology.ecos_workflow USING btree (status);


--
-- Name: idx_entity_def_code; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_entity_def_code ON ecos_ontology.entity_definition USING btree (code);


--
-- Name: idx_metric_def_code; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_metric_def_code ON ecos_ontology.metric_definition USING btree (code);


--
-- Name: idx_object_data_tenant; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_object_data_tenant ON ecos_ontology.ecos_object_data USING btree (tenant_id);


--
-- Name: idx_onto_domain_code; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE UNIQUE INDEX idx_onto_domain_code ON ecos_ontology.ecos_domain USING btree (code);


--
-- Name: idx_onto_entdef_code; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE UNIQUE INDEX idx_onto_entdef_code ON ecos_ontology.entity_definition USING btree (code);


--
-- Name: idx_onto_gloss_domain; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_gloss_domain ON ecos_ontology.ecos_business_glossary USING btree (domain);


--
-- Name: idx_onto_gloss_status; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_gloss_status ON ecos_ontology.ecos_business_glossary USING btree (status);


--
-- Name: idx_onto_links_src; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_links_src ON ecos_ontology.ecos_object_links USING btree (source_id);


--
-- Name: idx_onto_links_tgt; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_links_tgt ON ecos_ontology.ecos_object_links USING btree (target_id);


--
-- Name: idx_onto_metric_code; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE UNIQUE INDEX idx_onto_metric_code ON ecos_ontology.metric_definition USING btree (code);


--
-- Name: idx_onto_objdata_entity; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_objdata_entity ON ecos_ontology.ecos_object_data USING btree (entity_code);


--
-- Name: idx_onto_objdata_tenant; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_objdata_tenant ON ecos_ontology.ecos_object_data USING btree (tenant_id);


--
-- Name: idx_onto_objrel_src; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_objrel_src ON ecos_ontology.ecos_object_relationship USING btree (source_object_id);


--
-- Name: idx_onto_objrel_tenant; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_objrel_tenant ON ecos_ontology.ecos_object_relation USING btree (tenant_id);


--
-- Name: idx_onto_objrel_tgt; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_objrel_tgt ON ecos_ontology.ecos_object_relationship USING btree (target_object_id);


--
-- Name: idx_onto_objver_uniq; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE UNIQUE INDEX idx_onto_objver_uniq ON ecos_ontology.ecos_object_version USING btree (object_id, version_no);


--
-- Name: idx_onto_sm_uniq; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE UNIQUE INDEX idx_onto_sm_uniq ON ecos_ontology.ecos_object_state_machine USING btree (entity_code, from_status, transition_code);


--
-- Name: idx_onto_timeline_obj; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_timeline_obj ON ecos_ontology.ecos_object_timeline USING btree (object_id, created_at DESC);


--
-- Name: idx_onto_wfappr_inst; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_wfappr_inst ON ecos_ontology.ecos_workflow_approval USING btree (instance_id);


--
-- Name: idx_onto_wfappr_task; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_wfappr_task ON ecos_ontology.ecos_workflow_approval USING btree (task_id);


--
-- Name: idx_onto_wfinst_status; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_wfinst_status ON ecos_ontology.ecos_workflow_instance USING btree (status);


--
-- Name: idx_onto_wfinst_tenant; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_wfinst_tenant ON ecos_ontology.ecos_workflow_instance USING btree (tenant_id);


--
-- Name: idx_onto_wfinst_wf; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_wfinst_wf ON ecos_ontology.ecos_workflow_instance USING btree (workflow_id);


--
-- Name: idx_onto_wflog_inst; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_wflog_inst ON ecos_ontology.ecos_workflow_log USING btree (instance_id, created_at);


--
-- Name: idx_onto_wftask_assign; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_wftask_assign ON ecos_ontology.ecos_workflow_task USING btree (assignee);


--
-- Name: idx_onto_wftask_inst; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_wftask_inst ON ecos_ontology.ecos_workflow_task USING btree (instance_id);


--
-- Name: idx_onto_wftask_status; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_onto_wftask_status ON ecos_ontology.ecos_workflow_task USING btree (status);


--
-- Name: idx_proposals_author; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_proposals_author ON ecos_ontology.ecos_ontology_proposals USING btree (author);


--
-- Name: idx_proposals_domain; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_proposals_domain ON ecos_ontology.ecos_ontology_proposals USING btree (domain_code, status);


--
-- Name: idx_proposals_olock; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_proposals_olock ON ecos_ontology.ecos_ontology_proposals USING btree (optimistic_lock_version);


--
-- Name: idx_rel_def_source; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_rel_def_source ON ecos_ontology.relationship_definition USING btree (source_entity);


--
-- Name: idx_rel_def_target; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_rel_def_target ON ecos_ontology.relationship_definition USING btree (target_entity);


--
-- Name: idx_v273_onto_ref; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_v273_onto_ref ON ecos_ontology.ecos_ontology_candidate USING btree (proposal_ref, is_deleted);


--
-- Name: idx_v273_onto_tenant; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_v273_onto_tenant ON ecos_ontology.ecos_ontology_candidate USING btree (tenant_id, status, is_deleted);


--
-- Name: idx_v281_etm_domain_code; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE INDEX idx_v281_etm_domain_code ON ecos_ontology.ecos_entity_table_mapping USING btree (domain_code);


--
-- Name: uk_v273_onto_idem; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE UNIQUE INDEX uk_v273_onto_idem ON ecos_ontology.ecos_ontology_candidate USING btree (tenant_id, idempotency_key, is_deleted);


--
-- Name: uk_v281_etm_entity_ds_resource; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE UNIQUE INDEX uk_v281_etm_entity_ds_resource ON ecos_ontology.ecos_entity_table_mapping USING btree (entity_code, datasource_id, resource_name);


--
-- Name: uniq_ecos_caliber_code; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE UNIQUE INDEX uniq_ecos_caliber_code ON ecos_ontology.ecos_caliber USING btree (code, is_deleted);


--
-- Name: uniq_ecos_caliber_ver; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE UNIQUE INDEX uniq_ecos_caliber_ver ON ecos_ontology.ecos_caliber_version USING btree (caliber_id, version_no);


--
-- Name: uniq_ecos_metric_definition_code; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE UNIQUE INDEX uniq_ecos_metric_definition_code ON ecos_ontology.ecos_metric_definition USING btree (code, is_deleted);


--
-- Name: uniq_ecos_ontology_data_key; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE UNIQUE INDEX uniq_ecos_ontology_data_key ON ecos_ontology.ecos_ontology_data USING btree (ontology_id, object_type, record_key);


--
-- Name: uniq_ecos_wf_edge; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE UNIQUE INDEX uniq_ecos_wf_edge ON ecos_ontology.ecos_workflow_edge USING btree (workflow_id, edge_key, is_deleted);


--
-- Name: uniq_ecos_wf_node; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE UNIQUE INDEX uniq_ecos_wf_node ON ecos_ontology.ecos_workflow_node USING btree (workflow_id, node_key, is_deleted);


--
-- Name: uniq_ecos_workflow_name; Type: INDEX; Schema: ecos_ontology; Owner: -
--

CREATE UNIQUE INDEX uniq_ecos_workflow_name ON ecos_ontology.ecos_workflow USING btree (name, is_deleted);


--
-- Name: agent_cost_2025_01_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_01_pkey;


--
-- Name: agent_cost_2025_02_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_02_pkey;


--
-- Name: agent_cost_2025_03_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_03_pkey;


--
-- Name: agent_cost_2025_04_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_04_pkey;


--
-- Name: agent_cost_2025_05_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_05_pkey;


--
-- Name: agent_cost_2025_06_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_06_pkey;


--
-- Name: agent_cost_2025_07_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_07_pkey;


--
-- Name: agent_cost_2025_08_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_08_pkey;


--
-- Name: agent_cost_2025_09_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_09_pkey;


--
-- Name: agent_cost_2025_10_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_10_pkey;


--
-- Name: agent_cost_2025_11_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_11_pkey;


--
-- Name: agent_cost_2025_12_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_12_pkey;


--
-- Name: agent_cost_2026_01_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_01_pkey;


--
-- Name: agent_cost_2026_02_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_02_pkey;


--
-- Name: agent_cost_2026_03_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_03_pkey;


--
-- Name: agent_cost_2026_04_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_04_pkey;


--
-- Name: agent_cost_2026_05_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_05_pkey;


--
-- Name: agent_cost_2026_06_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_06_pkey;


--
-- Name: agent_cost_2026_07_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_07_pkey;


--
-- Name: agent_cost_2026_08_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_08_pkey;


--
-- Name: agent_cost_2026_09_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_09_pkey;


--
-- Name: agent_cost_2026_10_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_10_pkey;


--
-- Name: agent_cost_2026_11_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_11_pkey;


--
-- Name: agent_cost_2026_12_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_12_pkey;


--
-- Name: agent_cost_default_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: -
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_default_pkey;


--
-- Name: outbox_event_2025_01_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_01_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_01_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_01_event_type_idx;


--
-- Name: outbox_event_2025_01_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_01_pkey;


--
-- Name: outbox_event_2025_01_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_01_published_idx;


--
-- Name: outbox_event_2025_02_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_02_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_02_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_02_event_type_idx;


--
-- Name: outbox_event_2025_02_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_02_pkey;


--
-- Name: outbox_event_2025_02_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_02_published_idx;


--
-- Name: outbox_event_2025_03_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_03_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_03_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_03_event_type_idx;


--
-- Name: outbox_event_2025_03_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_03_pkey;


--
-- Name: outbox_event_2025_03_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_03_published_idx;


--
-- Name: outbox_event_2025_04_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_04_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_04_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_04_event_type_idx;


--
-- Name: outbox_event_2025_04_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_04_pkey;


--
-- Name: outbox_event_2025_04_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_04_published_idx;


--
-- Name: outbox_event_2025_05_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_05_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_05_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_05_event_type_idx;


--
-- Name: outbox_event_2025_05_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_05_pkey;


--
-- Name: outbox_event_2025_05_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_05_published_idx;


--
-- Name: outbox_event_2025_06_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_06_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_06_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_06_event_type_idx;


--
-- Name: outbox_event_2025_06_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_06_pkey;


--
-- Name: outbox_event_2025_06_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_06_published_idx;


--
-- Name: outbox_event_2025_07_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_07_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_07_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_07_event_type_idx;


--
-- Name: outbox_event_2025_07_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_07_pkey;


--
-- Name: outbox_event_2025_07_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_07_published_idx;


--
-- Name: outbox_event_2025_08_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_08_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_08_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_08_event_type_idx;


--
-- Name: outbox_event_2025_08_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_08_pkey;


--
-- Name: outbox_event_2025_08_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_08_published_idx;


--
-- Name: outbox_event_2025_09_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_09_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_09_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_09_event_type_idx;


--
-- Name: outbox_event_2025_09_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_09_pkey;


--
-- Name: outbox_event_2025_09_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_09_published_idx;


--
-- Name: outbox_event_2025_10_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_10_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_10_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_10_event_type_idx;


--
-- Name: outbox_event_2025_10_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_10_pkey;


--
-- Name: outbox_event_2025_10_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_10_published_idx;


--
-- Name: outbox_event_2025_11_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_11_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_11_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_11_event_type_idx;


--
-- Name: outbox_event_2025_11_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_11_pkey;


--
-- Name: outbox_event_2025_11_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_11_published_idx;


--
-- Name: outbox_event_2025_12_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_12_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_12_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_12_event_type_idx;


--
-- Name: outbox_event_2025_12_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_12_pkey;


--
-- Name: outbox_event_2025_12_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_12_published_idx;


--
-- Name: outbox_event_2026_01_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_01_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_01_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_01_event_type_idx;


--
-- Name: outbox_event_2026_01_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_01_pkey;


--
-- Name: outbox_event_2026_01_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_01_published_idx;


--
-- Name: outbox_event_2026_02_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_02_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_02_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_02_event_type_idx;


--
-- Name: outbox_event_2026_02_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_02_pkey;


--
-- Name: outbox_event_2026_02_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_02_published_idx;


--
-- Name: outbox_event_2026_03_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_03_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_03_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_03_event_type_idx;


--
-- Name: outbox_event_2026_03_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_03_pkey;


--
-- Name: outbox_event_2026_03_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_03_published_idx;


--
-- Name: outbox_event_2026_04_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_04_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_04_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_04_event_type_idx;


--
-- Name: outbox_event_2026_04_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_04_pkey;


--
-- Name: outbox_event_2026_04_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_04_published_idx;


--
-- Name: outbox_event_2026_05_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_05_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_05_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_05_event_type_idx;


--
-- Name: outbox_event_2026_05_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_05_pkey;


--
-- Name: outbox_event_2026_05_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_05_published_idx;


--
-- Name: outbox_event_2026_06_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_06_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_06_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_06_event_type_idx;


--
-- Name: outbox_event_2026_06_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_06_pkey;


--
-- Name: outbox_event_2026_06_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_06_published_idx;


--
-- Name: outbox_event_2026_07_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_07_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_07_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_07_event_type_idx;


--
-- Name: outbox_event_2026_07_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_07_pkey;


--
-- Name: outbox_event_2026_07_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_07_published_idx;


--
-- Name: outbox_event_2026_08_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_08_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_08_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_08_event_type_idx;


--
-- Name: outbox_event_2026_08_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_08_pkey;


--
-- Name: outbox_event_2026_08_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_08_published_idx;


--
-- Name: outbox_event_2026_09_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_09_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_09_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_09_event_type_idx;


--
-- Name: outbox_event_2026_09_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_09_pkey;


--
-- Name: outbox_event_2026_09_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_09_published_idx;


--
-- Name: outbox_event_2026_10_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_10_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_10_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_10_event_type_idx;


--
-- Name: outbox_event_2026_10_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_10_pkey;


--
-- Name: outbox_event_2026_10_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_10_published_idx;


--
-- Name: outbox_event_2026_11_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_11_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_11_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_11_event_type_idx;


--
-- Name: outbox_event_2026_11_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_11_pkey;


--
-- Name: outbox_event_2026_11_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_11_published_idx;


--
-- Name: outbox_event_2026_12_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_12_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_12_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_12_event_type_idx;


--
-- Name: outbox_event_2026_12_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_12_pkey;


--
-- Name: outbox_event_2026_12_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_12_published_idx;


--
-- Name: outbox_event_2027_01_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_01_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_01_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_01_event_type_idx;


--
-- Name: outbox_event_2027_01_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_01_pkey;


--
-- Name: outbox_event_2027_01_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_01_published_idx;


--
-- Name: outbox_event_2027_02_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_02_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_02_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_02_event_type_idx;


--
-- Name: outbox_event_2027_02_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_02_pkey;


--
-- Name: outbox_event_2027_02_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_02_published_idx;


--
-- Name: outbox_event_2027_03_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_03_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_03_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_03_event_type_idx;


--
-- Name: outbox_event_2027_03_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_03_pkey;


--
-- Name: outbox_event_2027_03_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_03_published_idx;


--
-- Name: outbox_event_2027_04_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_04_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_04_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_04_event_type_idx;


--
-- Name: outbox_event_2027_04_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_04_pkey;


--
-- Name: outbox_event_2027_04_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_04_published_idx;


--
-- Name: outbox_event_2027_05_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_05_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_05_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_05_event_type_idx;


--
-- Name: outbox_event_2027_05_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_05_pkey;


--
-- Name: outbox_event_2027_05_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_05_published_idx;


--
-- Name: outbox_event_2027_06_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_06_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_06_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_06_event_type_idx;


--
-- Name: outbox_event_2027_06_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_06_pkey;


--
-- Name: outbox_event_2027_06_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_06_published_idx;


--
-- Name: outbox_event_2027_07_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_07_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_07_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_07_event_type_idx;


--
-- Name: outbox_event_2027_07_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_07_pkey;


--
-- Name: outbox_event_2027_07_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_07_published_idx;


--
-- Name: outbox_event_2027_08_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_08_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_08_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_08_event_type_idx;


--
-- Name: outbox_event_2027_08_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_08_pkey;


--
-- Name: outbox_event_2027_08_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_08_published_idx;


--
-- Name: outbox_event_2027_09_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_09_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_09_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_09_event_type_idx;


--
-- Name: outbox_event_2027_09_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_09_pkey;


--
-- Name: outbox_event_2027_09_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_09_published_idx;


--
-- Name: outbox_event_2027_10_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_10_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_10_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_10_event_type_idx;


--
-- Name: outbox_event_2027_10_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_10_pkey;


--
-- Name: outbox_event_2027_10_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_10_published_idx;


--
-- Name: outbox_event_2027_11_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_11_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_11_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_11_event_type_idx;


--
-- Name: outbox_event_2027_11_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_11_pkey;


--
-- Name: outbox_event_2027_11_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_11_published_idx;


--
-- Name: outbox_event_2027_12_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_12_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_12_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_12_event_type_idx;


--
-- Name: outbox_event_2027_12_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_12_pkey;


--
-- Name: outbox_event_2027_12_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_12_published_idx;


--
-- Name: outbox_event_default_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_default_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_default_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_default_event_type_idx;


--
-- Name: outbox_event_default_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_default_pkey;


--
-- Name: outbox_event_default_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: -
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_default_published_idx;


--
-- Name: agent_execution fk_ai_exec_agent; Type: FK CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_execution
    ADD CONSTRAINT fk_ai_exec_agent FOREIGN KEY (agent_id) REFERENCES ecos_ai.agent_definition(id);


--
-- Name: ecos_mission_task fk_ai_mtask_mission; Type: FK CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.ecos_mission_task
    ADD CONSTRAINT fk_ai_mtask_mission FOREIGN KEY (mission_id) REFERENCES ecos_ai.ecos_mission(id);


--
-- Name: simulation fk_ai_sim_scenario; Type: FK CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.simulation
    ADD CONSTRAINT fk_ai_sim_scenario FOREIGN KEY (scenario_id) REFERENCES ecos_ai.scenario(id);


--
-- Name: simulation_result fk_ai_simres_sim; Type: FK CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.simulation_result
    ADD CONSTRAINT fk_ai_simres_sim FOREIGN KEY (simulation_id) REFERENCES ecos_ai.simulation(id);


--
-- Name: agent_execution_step fk_ai_step_exec; Type: FK CONSTRAINT; Schema: ecos_ai; Owner: -
--

ALTER TABLE ONLY ecos_ai.agent_execution_step
    ADD CONSTRAINT fk_ai_step_exec FOREIGN KEY (execution_id) REFERENCES ecos_ai.agent_execution(id);


--
-- Name: ecos_wm_causal_link fk_cog_causal_src; Type: FK CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_causal_link
    ADD CONSTRAINT fk_cog_causal_src FOREIGN KEY (source_goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);


--
-- Name: ecos_wm_causal_link fk_cog_causal_tgt; Type: FK CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_causal_link
    ADD CONSTRAINT fk_cog_causal_tgt FOREIGN KEY (target_goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id) ON DELETE CASCADE;


--
-- Name: ecos_biz_contract fk_cog_contract_proj; Type: FK CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_contract
    ADD CONSTRAINT fk_cog_contract_proj FOREIGN KEY (project_id) REFERENCES ecos_cognitive.ecos_biz_project(id);


--
-- Name: ecos_wm_goal fk_cog_goal_parent; Type: FK CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_goal
    ADD CONSTRAINT fk_cog_goal_parent FOREIGN KEY (parent_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);


--
-- Name: ecos_wm_goal_log fk_cog_goallog_goal; Type: FK CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_goal_log
    ADD CONSTRAINT fk_cog_goallog_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id) ON DELETE CASCADE;


--
-- Name: ecos_biz_metric fk_cog_metric_goal; Type: FK CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_metric
    ADD CONSTRAINT fk_cog_metric_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);


--
-- Name: ecos_biz_project fk_cog_proj_goal; Type: FK CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_project
    ADD CONSTRAINT fk_cog_proj_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);


--
-- Name: ecos_biz_target fk_cog_target_goal; Type: FK CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_target
    ADD CONSTRAINT fk_cog_target_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);


--
-- Name: ecos_goal_tracking fk_cog_tracking_goal; Type: FK CONSTRAINT; Schema: ecos_cognitive; Owner: -
--

ALTER TABLE ONLY ecos_cognitive.ecos_goal_tracking
    ADD CONSTRAINT fk_cog_tracking_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id) ON DELETE CASCADE;


--
-- Name: dict_column dict_column_table_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.dict_column
    ADD CONSTRAINT dict_column_table_id_fkey FOREIGN KEY (table_id) REFERENCES ecos_control.dict_table(id) ON DELETE CASCADE;


--
-- Name: ecos_glossary_term ecos_glossary_term_domain_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_glossary_term
    ADD CONSTRAINT ecos_glossary_term_domain_id_fkey FOREIGN KEY (domain_id) REFERENCES ecos_ontology.ecos_domain(id);


--
-- Name: ecos_goal_tracking ecos_goal_tracking_goal_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_goal_tracking
    ADD CONSTRAINT ecos_goal_tracking_goal_id_fkey FOREIGN KEY (goal_id) REFERENCES ecos_control.ecos_wm_goal(id) ON DELETE CASCADE;


--
-- Name: ecos_marketplace_access_request ecos_marketplace_access_request_asset_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_marketplace_access_request
    ADD CONSTRAINT ecos_marketplace_access_request_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ecos_control.ecos_marketplace_asset(id);


--
-- Name: ecos_ontology_entity ecos_ontology_entity_domain_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_ontology_entity
    ADD CONSTRAINT ecos_ontology_entity_domain_id_fkey FOREIGN KEY (domain_id) REFERENCES ecos_ontology.ecos_domain(id);


--
-- Name: ecos_wm_causal_link ecos_wm_causal_link_source_goal_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_wm_causal_link
    ADD CONSTRAINT ecos_wm_causal_link_source_goal_id_fkey FOREIGN KEY (source_goal_id) REFERENCES ecos_control.ecos_wm_goal(id) ON DELETE CASCADE;


--
-- Name: ecos_wm_causal_link ecos_wm_causal_link_target_goal_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_wm_causal_link
    ADD CONSTRAINT ecos_wm_causal_link_target_goal_id_fkey FOREIGN KEY (target_goal_id) REFERENCES ecos_control.ecos_wm_goal(id) ON DELETE CASCADE;


--
-- Name: ecos_wm_goal ecos_wm_goal_domain_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_wm_goal
    ADD CONSTRAINT ecos_wm_goal_domain_id_fkey FOREIGN KEY (domain_id) REFERENCES ecos_ontology.ecos_domain(id);


--
-- Name: ecos_wm_goal_log ecos_wm_goal_log_goal_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_wm_goal_log
    ADD CONSTRAINT ecos_wm_goal_log_goal_id_fkey FOREIGN KEY (goal_id) REFERENCES ecos_control.ecos_wm_goal(id) ON DELETE CASCADE;


--
-- Name: ecos_wm_goal ecos_wm_goal_parent_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_wm_goal
    ADD CONSTRAINT ecos_wm_goal_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES ecos_control.ecos_wm_goal(id) ON DELETE SET NULL;


--
-- Name: ecos_dq_issue fk_dq_issue_rule; Type: FK CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.ecos_dq_issue
    ADD CONSTRAINT fk_dq_issue_rule FOREIGN KEY (rule_id) REFERENCES ecos_control.ecos_dq_rule(id) ON DELETE CASCADE;


--
-- Name: td_org_permission fk_sysman_orgperm_org; Type: FK CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_org_permission
    ADD CONSTRAINT fk_sysman_orgperm_org FOREIGN KEY (org_id) REFERENCES ecos_control.td_organization(org_id);


--
-- Name: td_role_permission fk_sysman_role_perm_perm; Type: FK CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_role_permission
    ADD CONSTRAINT fk_sysman_role_perm_perm FOREIGN KEY (permission_id) REFERENCES ecos_control.td_permission(permission_id);


--
-- Name: td_role_permission fk_sysman_role_perm_role; Type: FK CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_role_permission
    ADD CONSTRAINT fk_sysman_role_perm_role FOREIGN KEY (role_id) REFERENCES ecos_control.td_role(role_id);


--
-- Name: td_user_role fk_sysman_user_role_role; Type: FK CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_user_role
    ADD CONSTRAINT fk_sysman_user_role_role FOREIGN KEY (role_id) REFERENCES ecos_control.td_role(role_id);


--
-- Name: td_user_role fk_sysman_user_role_user; Type: FK CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.td_user_role
    ADD CONSTRAINT fk_sysman_user_role_user FOREIGN KEY (user_id) REFERENCES ecos_control.td_user(user_id);


--
-- Name: sys_agent_message sys_agent_message_session_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: -
--

ALTER TABLE ONLY ecos_control.sys_agent_message
    ADD CONSTRAINT sys_agent_message_session_id_fkey FOREIGN KEY (session_id) REFERENCES ecos_control.sys_agent_session(id);


--
-- Name: ecos_pipeline_execution fk_data_pexec_def; Type: FK CONSTRAINT; Schema: ecos_data; Owner: -
--

ALTER TABLE ONLY ecos_data.ecos_pipeline_execution
    ADD CONSTRAINT fk_data_pexec_def FOREIGN KEY (pipeline_id) REFERENCES ecos_data.ecos_pipeline_definition(id);


--
-- Name: graph_edge fk_kb_gedge_src; Type: FK CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.graph_edge
    ADD CONSTRAINT fk_kb_gedge_src FOREIGN KEY (source_id) REFERENCES ecos_knowledge.graph_node(id);


--
-- Name: graph_edge fk_kb_gedge_tgt; Type: FK CONSTRAINT; Schema: ecos_knowledge; Owner: -
--

ALTER TABLE ONLY ecos_knowledge.graph_edge
    ADD CONSTRAINT fk_kb_gedge_tgt FOREIGN KEY (target_id) REFERENCES ecos_knowledge.graph_node(id);


--
-- PostgreSQL database dump complete
--

\unrestrict C4Vyw4KNb9LR9sYv9sDE9AybHwWJsBCI850LosvId4l8RbfgTDb0ohoMKMdcfh0

