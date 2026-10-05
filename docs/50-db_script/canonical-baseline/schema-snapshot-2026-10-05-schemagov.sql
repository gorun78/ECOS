--
-- PostgreSQL database dump
--

\restrict OGtDXORmaPeiE2zLxcTwuogPfpBxWG5tSIssFbSrv2JXZN2s4yvKXxD9v6pnQ0Y

-- Dumped from database version 16.14
-- Dumped by pg_dump version 16.14

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
-- Name: ecos_ai; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA ecos_ai;


ALTER SCHEMA ecos_ai OWNER TO postgres;

--
-- Name: ecos_aiming; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA ecos_aiming;


ALTER SCHEMA ecos_aiming OWNER TO postgres;

--
-- Name: ecos_buszhi; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA ecos_buszhi;


ALTER SCHEMA ecos_buszhi OWNER TO postgres;

--
-- Name: ecos_cognitive; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA ecos_cognitive;


ALTER SCHEMA ecos_cognitive OWNER TO postgres;

--
-- Name: ecos_control; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA ecos_control;


ALTER SCHEMA ecos_control OWNER TO postgres;

--
-- Name: ecos_data; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA ecos_data;


ALTER SCHEMA ecos_data OWNER TO postgres;

--
-- Name: ecos_datanet; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA ecos_datanet;


ALTER SCHEMA ecos_datanet OWNER TO postgres;

--
-- Name: ecos_dccheng; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA ecos_dccheng;


ALTER SCHEMA ecos_dccheng OWNER TO postgres;

--
-- Name: ecos_dq; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA ecos_dq;


ALTER SCHEMA ecos_dq OWNER TO postgres;

--
-- Name: ecos_dw; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA ecos_dw;


ALTER SCHEMA ecos_dw OWNER TO postgres;

--
-- Name: SCHEMA ecos_dw; Type: COMMENT; Schema: -; Owner: postgres
--

COMMENT ON SCHEMA ecos_dw IS 'DW 层（CURATED）业务表 schema — 数据工作台唯一写入，本体/知识只读';


--
-- Name: ecos_infra; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA ecos_infra;


ALTER SCHEMA ecos_infra OWNER TO postgres;

--
-- Name: ecos_knowledge; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA ecos_knowledge;


ALTER SCHEMA ecos_knowledge OWNER TO postgres;

--
-- Name: ecos_ontology; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA ecos_ontology;


ALTER SCHEMA ecos_ontology OWNER TO postgres;

--
-- Name: ecos_security; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA ecos_security;


ALTER SCHEMA ecos_security OWNER TO postgres;

--
-- Name: ecos_sysman; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA ecos_sysman;


ALTER SCHEMA ecos_sysman OWNER TO postgres;

--
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;


--
-- Name: EXTENSION pg_trgm; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';


--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: vector; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;


--
-- Name: EXTENSION vector; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION vector IS 'vector data type and ivfflat and hnsw access methods';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: agent_approval; Type: TABLE; Schema: ecos_ai; Owner: postgres
--

CREATE TABLE ecos_ai.agent_approval (
    id character varying(64) NOT NULL,
    task_id character varying(64),
    risk_level character varying(4),
    status character varying(16) DEFAULT 'PENDING'::character varying,
    requested_at timestamp without time zone,
    processed_at timestamp without time zone,
    approved_by character varying(64),
    comment text
);


ALTER TABLE ecos_ai.agent_approval OWNER TO postgres;

--
-- Name: TABLE agent_approval; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.agent_approval IS 'Agent审批表';


--
-- Name: agent_cost; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost OWNER TO postgres;

--
-- Name: TABLE agent_cost; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.agent_cost IS 'Agent成本表(按月分区)';


--
-- Name: agent_cost_2025_01; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2025_01 OWNER TO postgres;

--
-- Name: agent_cost_2025_02; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2025_02 OWNER TO postgres;

--
-- Name: agent_cost_2025_03; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2025_03 OWNER TO postgres;

--
-- Name: agent_cost_2025_04; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2025_04 OWNER TO postgres;

--
-- Name: agent_cost_2025_05; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2025_05 OWNER TO postgres;

--
-- Name: agent_cost_2025_06; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2025_06 OWNER TO postgres;

--
-- Name: agent_cost_2025_07; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2025_07 OWNER TO postgres;

--
-- Name: agent_cost_2025_08; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2025_08 OWNER TO postgres;

--
-- Name: agent_cost_2025_09; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2025_09 OWNER TO postgres;

--
-- Name: agent_cost_2025_10; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2025_10 OWNER TO postgres;

--
-- Name: agent_cost_2025_11; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2025_11 OWNER TO postgres;

--
-- Name: agent_cost_2025_12; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2025_12 OWNER TO postgres;

--
-- Name: agent_cost_2026_01; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2026_01 OWNER TO postgres;

--
-- Name: agent_cost_2026_02; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2026_02 OWNER TO postgres;

--
-- Name: agent_cost_2026_03; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2026_03 OWNER TO postgres;

--
-- Name: agent_cost_2026_04; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2026_04 OWNER TO postgres;

--
-- Name: agent_cost_2026_05; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2026_05 OWNER TO postgres;

--
-- Name: agent_cost_2026_06; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2026_06 OWNER TO postgres;

--
-- Name: agent_cost_2026_07; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2026_07 OWNER TO postgres;

--
-- Name: agent_cost_2026_08; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2026_08 OWNER TO postgres;

--
-- Name: agent_cost_2026_09; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2026_09 OWNER TO postgres;

--
-- Name: agent_cost_2026_10; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2026_10 OWNER TO postgres;

--
-- Name: agent_cost_2026_11; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2026_11 OWNER TO postgres;

--
-- Name: agent_cost_2026_12; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_2026_12 OWNER TO postgres;

--
-- Name: agent_cost_default; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_cost_default OWNER TO postgres;

--
-- Name: agent_definition; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_definition OWNER TO postgres;

--
-- Name: TABLE agent_definition; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.agent_definition IS 'Agent定义表(V50)';


--
-- Name: agent_evaluation; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_evaluation OWNER TO postgres;

--
-- Name: TABLE agent_evaluation; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.agent_evaluation IS 'Agent评估表';


--
-- Name: agent_execution; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_execution OWNER TO postgres;

--
-- Name: TABLE agent_execution; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.agent_execution IS 'Agent执行表';


--
-- Name: agent_execution_step; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_execution_step OWNER TO postgres;

--
-- Name: TABLE agent_execution_step; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.agent_execution_step IS 'Agent执行步骤表';


--
-- Name: agent_governance_policy; Type: TABLE; Schema: ecos_ai; Owner: postgres
--

CREATE TABLE ecos_ai.agent_governance_policy (
    id character varying(64) NOT NULL,
    agent_id character varying(64),
    type character varying(16),
    rule jsonb DEFAULT '{}'::jsonb,
    enabled boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_ai.agent_governance_policy OWNER TO postgres;

--
-- Name: TABLE agent_governance_policy; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.agent_governance_policy IS 'Agent治理策略表';


--
-- Name: agent_memory; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.agent_memory OWNER TO postgres;

--
-- Name: TABLE agent_memory; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.agent_memory IS 'Agent记忆表';


--
-- Name: causal_edge; Type: TABLE; Schema: ecos_ai; Owner: postgres
--

CREATE TABLE ecos_ai.causal_edge (
    id character varying(64) NOT NULL,
    source_node character varying(128),
    target_node character varying(128),
    weight numeric(5,4) DEFAULT 0.5,
    description text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_ai.causal_edge OWNER TO postgres;

--
-- Name: TABLE causal_edge; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.causal_edge IS '因果边表';


--
-- Name: ecos_agent; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.ecos_agent OWNER TO postgres;

--
-- Name: TABLE ecos_agent; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.ecos_agent IS 'Agent配置表(旧版)';


--
-- Name: ecos_agent_registry; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.ecos_agent_registry OWNER TO postgres;

--
-- Name: TABLE ecos_agent_registry; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.ecos_agent_registry IS 'Agent注册表';


--
-- Name: ecos_decision_case; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.ecos_decision_case OWNER TO postgres;

--
-- Name: TABLE ecos_decision_case; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.ecos_decision_case IS '决策案例表';


--
-- Name: ecos_decision_case_id_seq; Type: SEQUENCE; Schema: ecos_ai; Owner: postgres
--

CREATE SEQUENCE ecos_ai.ecos_decision_case_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_ai.ecos_decision_case_id_seq OWNER TO postgres;

--
-- Name: ecos_decision_case_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_ai; Owner: postgres
--

ALTER SEQUENCE ecos_ai.ecos_decision_case_id_seq OWNED BY ecos_ai.ecos_decision_case.id;


--
-- Name: ecos_mission; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.ecos_mission OWNER TO postgres;

--
-- Name: TABLE ecos_mission; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.ecos_mission IS 'Mission表';


--
-- Name: ecos_mission_task; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.ecos_mission_task OWNER TO postgres;

--
-- Name: TABLE ecos_mission_task; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.ecos_mission_task IS 'Mission任务表';


--
-- Name: ecos_tool_definition; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.ecos_tool_definition OWNER TO postgres;

--
-- Name: TABLE ecos_tool_definition; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.ecos_tool_definition IS '工具定义表';


--
-- Name: forecast; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.forecast OWNER TO postgres;

--
-- Name: TABLE forecast; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.forecast IS '预测表';


--
-- Name: optimization_job; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.optimization_job OWNER TO postgres;

--
-- Name: TABLE optimization_job; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.optimization_job IS '优化任务表';


--
-- Name: scenario; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.scenario OWNER TO postgres;

--
-- Name: TABLE scenario; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.scenario IS '场景表';


--
-- Name: simulation; Type: TABLE; Schema: ecos_ai; Owner: postgres
--

CREATE TABLE ecos_ai.simulation (
    id character varying(64) NOT NULL,
    scenario_id character varying(64),
    status character varying(32) DEFAULT 'CREATED'::character varying,
    config jsonb DEFAULT '{}'::jsonb,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_ai.simulation OWNER TO postgres;

--
-- Name: TABLE simulation; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.simulation IS '仿真表';


--
-- Name: simulation_result; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.simulation_result OWNER TO postgres;

--
-- Name: TABLE simulation_result; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.simulation_result IS '仿真结果表';


--
-- Name: strategy_recommendation; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.strategy_recommendation OWNER TO postgres;

--
-- Name: TABLE strategy_recommendation; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.strategy_recommendation IS '策略推荐表';


--
-- Name: sys_agent_call_log; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.sys_agent_call_log OWNER TO postgres;

--
-- Name: TABLE sys_agent_call_log; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.sys_agent_call_log IS 'Agent调用日志表(Hermes)';


--
-- Name: sys_agent_profile; Type: TABLE; Schema: ecos_ai; Owner: postgres
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


ALTER TABLE ecos_ai.sys_agent_profile OWNER TO postgres;

--
-- Name: TABLE sys_agent_profile; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.sys_agent_profile IS 'Agent配置档案表(Hermes)';


--
-- Name: world_snapshot; Type: TABLE; Schema: ecos_ai; Owner: postgres
--

CREATE TABLE ecos_ai.world_snapshot (
    id character varying(64) NOT NULL,
    state_id character varying(64),
    snapshot_type character varying(32),
    data jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_ai.world_snapshot OWNER TO postgres;

--
-- Name: TABLE world_snapshot; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.world_snapshot IS '世界快照表';


--
-- Name: world_state; Type: TABLE; Schema: ecos_ai; Owner: postgres
--

CREATE TABLE ecos_ai.world_state (
    id character varying(64) NOT NULL,
    "timestamp" timestamp without time zone,
    state_data jsonb DEFAULT '{}'::jsonb
);


ALTER TABLE ecos_ai.world_state OWNER TO postgres;

--
-- Name: TABLE world_state; Type: COMMENT; Schema: ecos_ai; Owner: postgres
--

COMMENT ON TABLE ecos_ai.world_state IS '世界状态表';


--
-- Name: ecos_biz_contract; Type: TABLE; Schema: ecos_cognitive; Owner: postgres
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


ALTER TABLE ecos_cognitive.ecos_biz_contract OWNER TO postgres;

--
-- Name: TABLE ecos_biz_contract; Type: COMMENT; Schema: ecos_cognitive; Owner: postgres
--

COMMENT ON TABLE ecos_cognitive.ecos_biz_contract IS '业务合同表';


--
-- Name: ecos_biz_department; Type: TABLE; Schema: ecos_cognitive; Owner: postgres
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


ALTER TABLE ecos_cognitive.ecos_biz_department OWNER TO postgres;

--
-- Name: TABLE ecos_biz_department; Type: COMMENT; Schema: ecos_cognitive; Owner: postgres
--

COMMENT ON TABLE ecos_cognitive.ecos_biz_department IS '业务部门表';


--
-- Name: ecos_biz_metric; Type: TABLE; Schema: ecos_cognitive; Owner: postgres
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


ALTER TABLE ecos_cognitive.ecos_biz_metric OWNER TO postgres;

--
-- Name: TABLE ecos_biz_metric; Type: COMMENT; Schema: ecos_cognitive; Owner: postgres
--

COMMENT ON TABLE ecos_cognitive.ecos_biz_metric IS '业务指标表';


--
-- Name: ecos_biz_metric_id_seq; Type: SEQUENCE; Schema: ecos_cognitive; Owner: postgres
--

CREATE SEQUENCE ecos_cognitive.ecos_biz_metric_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_cognitive.ecos_biz_metric_id_seq OWNER TO postgres;

--
-- Name: ecos_biz_metric_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_cognitive; Owner: postgres
--

ALTER SEQUENCE ecos_cognitive.ecos_biz_metric_id_seq OWNED BY ecos_cognitive.ecos_biz_metric.id;


--
-- Name: ecos_biz_project; Type: TABLE; Schema: ecos_cognitive; Owner: postgres
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


ALTER TABLE ecos_cognitive.ecos_biz_project OWNER TO postgres;

--
-- Name: TABLE ecos_biz_project; Type: COMMENT; Schema: ecos_cognitive; Owner: postgres
--

COMMENT ON TABLE ecos_cognitive.ecos_biz_project IS '业务项目表';


--
-- Name: ecos_biz_target; Type: TABLE; Schema: ecos_cognitive; Owner: postgres
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


ALTER TABLE ecos_cognitive.ecos_biz_target OWNER TO postgres;

--
-- Name: TABLE ecos_biz_target; Type: COMMENT; Schema: ecos_cognitive; Owner: postgres
--

COMMENT ON TABLE ecos_cognitive.ecos_biz_target IS '业务目标表';


--
-- Name: ecos_biz_target_id_seq; Type: SEQUENCE; Schema: ecos_cognitive; Owner: postgres
--

CREATE SEQUENCE ecos_cognitive.ecos_biz_target_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_cognitive.ecos_biz_target_id_seq OWNER TO postgres;

--
-- Name: ecos_biz_target_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_cognitive; Owner: postgres
--

ALTER SEQUENCE ecos_cognitive.ecos_biz_target_id_seq OWNED BY ecos_cognitive.ecos_biz_target.id;


--
-- Name: ecos_goal_tracking; Type: TABLE; Schema: ecos_cognitive; Owner: postgres
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


ALTER TABLE ecos_cognitive.ecos_goal_tracking OWNER TO postgres;

--
-- Name: TABLE ecos_goal_tracking; Type: COMMENT; Schema: ecos_cognitive; Owner: postgres
--

COMMENT ON TABLE ecos_cognitive.ecos_goal_tracking IS '目标追踪表';


--
-- Name: ecos_wm_causal_link; Type: TABLE; Schema: ecos_cognitive; Owner: postgres
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


ALTER TABLE ecos_cognitive.ecos_wm_causal_link OWNER TO postgres;

--
-- Name: TABLE ecos_wm_causal_link; Type: COMMENT; Schema: ecos_cognitive; Owner: postgres
--

COMMENT ON TABLE ecos_cognitive.ecos_wm_causal_link IS '目标因果链表';


--
-- Name: ecos_wm_causal_link_id_seq; Type: SEQUENCE; Schema: ecos_cognitive; Owner: postgres
--

CREATE SEQUENCE ecos_cognitive.ecos_wm_causal_link_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_cognitive.ecos_wm_causal_link_id_seq OWNER TO postgres;

--
-- Name: ecos_wm_causal_link_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_cognitive; Owner: postgres
--

ALTER SEQUENCE ecos_cognitive.ecos_wm_causal_link_id_seq OWNED BY ecos_cognitive.ecos_wm_causal_link.id;


--
-- Name: ecos_wm_goal; Type: TABLE; Schema: ecos_cognitive; Owner: postgres
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


ALTER TABLE ecos_cognitive.ecos_wm_goal OWNER TO postgres;

--
-- Name: TABLE ecos_wm_goal; Type: COMMENT; Schema: ecos_cognitive; Owner: postgres
--

COMMENT ON TABLE ecos_cognitive.ecos_wm_goal IS '世界模型目标表';


--
-- Name: COLUMN ecos_wm_goal.goal_type; Type: COMMENT; Schema: ecos_cognitive; Owner: postgres
--

COMMENT ON COLUMN ecos_cognitive.ecos_wm_goal.goal_type IS '目标类型: STRATEGIC/TACTICAL/OPERATIONAL';


--
-- Name: COLUMN ecos_wm_goal.kpi_formula; Type: COMMENT; Schema: ecos_cognitive; Owner: postgres
--

COMMENT ON COLUMN ecos_cognitive.ecos_wm_goal.kpi_formula IS 'KPI公式';


--
-- Name: COLUMN ecos_wm_goal.measure_frequency; Type: COMMENT; Schema: ecos_cognitive; Owner: postgres
--

COMMENT ON COLUMN ecos_cognitive.ecos_wm_goal.measure_frequency IS '度量频率: DAILY/WEEKLY/MONTHLY/QUARTERLY';


--
-- Name: ecos_wm_goal_id_seq; Type: SEQUENCE; Schema: ecos_cognitive; Owner: postgres
--

CREATE SEQUENCE ecos_cognitive.ecos_wm_goal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_cognitive.ecos_wm_goal_id_seq OWNER TO postgres;

--
-- Name: ecos_wm_goal_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_cognitive; Owner: postgres
--

ALTER SEQUENCE ecos_cognitive.ecos_wm_goal_id_seq OWNED BY ecos_cognitive.ecos_wm_goal.id;


--
-- Name: ecos_wm_goal_log; Type: TABLE; Schema: ecos_cognitive; Owner: postgres
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


ALTER TABLE ecos_cognitive.ecos_wm_goal_log OWNER TO postgres;

--
-- Name: TABLE ecos_wm_goal_log; Type: COMMENT; Schema: ecos_cognitive; Owner: postgres
--

COMMENT ON TABLE ecos_cognitive.ecos_wm_goal_log IS '目标变更日志表';


--
-- Name: ecos_wm_goal_log_id_seq; Type: SEQUENCE; Schema: ecos_cognitive; Owner: postgres
--

CREATE SEQUENCE ecos_cognitive.ecos_wm_goal_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_cognitive.ecos_wm_goal_log_id_seq OWNER TO postgres;

--
-- Name: ecos_wm_goal_log_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_cognitive; Owner: postgres
--

ALTER SEQUENCE ecos_cognitive.ecos_wm_goal_log_id_seq OWNED BY ecos_cognitive.ecos_wm_goal_log.id;


--
-- Name: ecos_wm_scenario; Type: TABLE; Schema: ecos_cognitive; Owner: postgres
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


ALTER TABLE ecos_cognitive.ecos_wm_scenario OWNER TO postgres;

--
-- Name: TABLE ecos_wm_scenario; Type: COMMENT; Schema: ecos_cognitive; Owner: postgres
--

COMMENT ON TABLE ecos_cognitive.ecos_wm_scenario IS '世界模型场景表';


--
-- Name: ecos_wm_scenario_id_seq; Type: SEQUENCE; Schema: ecos_cognitive; Owner: postgres
--

CREATE SEQUENCE ecos_cognitive.ecos_wm_scenario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_cognitive.ecos_wm_scenario_id_seq OWNER TO postgres;

--
-- Name: ecos_wm_scenario_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_cognitive; Owner: postgres
--

ALTER SEQUENCE ecos_cognitive.ecos_wm_scenario_id_seq OWNED BY ecos_cognitive.ecos_wm_scenario.id;


--
-- Name: ecos_world_scenarios; Type: TABLE; Schema: ecos_cognitive; Owner: postgres
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


ALTER TABLE ecos_cognitive.ecos_world_scenarios OWNER TO postgres;

--
-- Name: TABLE ecos_world_scenarios; Type: COMMENT; Schema: ecos_cognitive; Owner: postgres
--

COMMENT ON TABLE ecos_cognitive.ecos_world_scenarios IS '世界场景配置表';


--
-- Name: crypto_audit_ledger; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.crypto_audit_ledger (
    id character varying(64) NOT NULL,
    event_type character varying(64) NOT NULL,
    resource character varying(255),
    action character varying(128),
    operator_id character varying(64),
    payload text,
    prev_hash character varying(64),
    current_hash character varying(64),
    "timestamp" bigint,
    verified boolean DEFAULT true
);


ALTER TABLE ecos_control.crypto_audit_ledger OWNER TO postgres;

--
-- Name: dict_column; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.dict_column OWNER TO postgres;

--
-- Name: dict_table; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.dict_table OWNER TO postgres;

--
-- Name: ecos_action_type; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_action_type (
    id character varying(64) NOT NULL,
    name character varying(128) NOT NULL,
    description text,
    object_type_id character varying(64) NOT NULL,
    preconditions text,
    post_actions text,
    audit_required boolean DEFAULT true,
    enabled boolean DEFAULT true,
    created_by character varying(64),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_action_type OWNER TO postgres;

--
-- Name: ecos_agent; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_agent OWNER TO postgres;

--
-- Name: ecos_agent_alert; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_agent_alert (
    id bigint NOT NULL,
    trace_id character varying(16),
    agent_id character varying(64),
    alert_type character varying(32),
    message text,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_agent_alert OWNER TO postgres;

--
-- Name: ecos_agent_alert_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_agent_alert_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_agent_alert_id_seq OWNER TO postgres;

--
-- Name: ecos_agent_alert_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_agent_alert_id_seq OWNED BY ecos_control.ecos_agent_alert.id;


--
-- Name: ecos_agent_execution; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_agent_execution (
    id character varying(36) NOT NULL,
    session_id character varying(64) DEFAULT NULL::character varying,
    agent_id character varying(100) DEFAULT NULL::character varying,
    agent_name character varying(200) DEFAULT NULL::character varying,
    task_type character varying(50) DEFAULT 'CHAT'::character varying,
    input_text text,
    output_text text,
    status character varying(20) DEFAULT 'SUCCESS'::character varying,
    tokens_used integer DEFAULT 0,
    latency_ms integer DEFAULT 0,
    tool_calls integer DEFAULT 0,
    error_msg text,
    workflow_instance_id character varying(64) DEFAULT NULL::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_agent_execution OWNER TO postgres;

--
-- Name: ecos_agent_execution_step; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_agent_execution_step (
    id character varying(64) NOT NULL,
    session_id character varying(64) NOT NULL,
    step_type character varying(32) NOT NULL,
    step_content character varying(1000) DEFAULT NULL::character varying,
    tool_name character varying(128) DEFAULT NULL::character varying,
    tool_input text,
    tool_output text,
    tokens_used integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_agent_execution_step OWNER TO postgres;

--
-- Name: ecos_agent_memory; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_agent_memory (
    id character varying(64) NOT NULL,
    session_id character varying(64) DEFAULT NULL::character varying,
    agent_id character varying(64) DEFAULT NULL::character varying,
    memory_type character varying(32) NOT NULL,
    memory_key character varying(256) NOT NULL,
    memory_value text,
    importance integer DEFAULT 5,
    access_count integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_agent_memory OWNER TO postgres;

--
-- Name: ecos_agent_metrics; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_agent_metrics (
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


ALTER TABLE ecos_control.ecos_agent_metrics OWNER TO postgres;

--
-- Name: ecos_agent_metrics_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_agent_metrics_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_agent_metrics_id_seq OWNER TO postgres;

--
-- Name: ecos_agent_metrics_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_agent_metrics_id_seq OWNED BY ecos_control.ecos_agent_metrics.id;


--
-- Name: ecos_agent_registry; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_agent_registry (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    role character varying(128) NOT NULL,
    capability jsonb DEFAULT '{}'::jsonb NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE'::character varying,
    endpoint character varying(512),
    metadata jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_agent_registry OWNER TO postgres;

--
-- Name: ecos_agent_version; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_agent_version (
    id character varying(64) NOT NULL,
    agent_id character varying(64) NOT NULL,
    version integer NOT NULL,
    config text,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_agent_version OWNER TO postgres;

--
-- Name: ecos_alert_history; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_alert_history (
    id bigint NOT NULL,
    rule_id bigint,
    rule_name character varying(256),
    level character varying(16) NOT NULL,
    message text NOT NULL,
    metric character varying(128),
    metric_value double precision,
    threshold double precision,
    status character varying(16) DEFAULT 'FIRING'::character varying,
    acknowledged boolean DEFAULT false,
    acknowledged_by character varying(128),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    resolved_at timestamp without time zone
);


ALTER TABLE ecos_control.ecos_alert_history OWNER TO postgres;

--
-- Name: ecos_alert_history_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_alert_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_alert_history_id_seq OWNER TO postgres;

--
-- Name: ecos_alert_history_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_alert_history_id_seq OWNED BY ecos_control.ecos_alert_history.id;


--
-- Name: ecos_alert_rule; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_alert_rule (
    id bigint NOT NULL,
    name character varying(256) NOT NULL,
    description text,
    metric character varying(128) NOT NULL,
    operator character varying(16) DEFAULT '<'::character varying NOT NULL,
    threshold double precision NOT NULL,
    level character varying(16) DEFAULT 'WARN'::character varying NOT NULL,
    enabled boolean DEFAULT true,
    cooldown_min integer DEFAULT 5,
    config jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_control.ecos_alert_rule OWNER TO postgres;

--
-- Name: ecos_alert_rule_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_alert_rule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_alert_rule_id_seq OWNER TO postgres;

--
-- Name: ecos_alert_rule_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_alert_rule_id_seq OWNED BY ecos_control.ecos_alert_rule.id;


--
-- Name: ecos_audit_log; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_audit_log (
    id bigint NOT NULL,
    username character varying(64),
    operation character varying(32),
    entity_type character varying(64),
    entity_id character varying(64),
    changes jsonb,
    ip_address character varying(64),
    created_at timestamp without time zone DEFAULT now(),
    prev_hash character varying(64),
    curr_hash character varying(64),
    hash_algorithm character varying(16) DEFAULT 'SHA-256'::character varying,
    category character varying(32) DEFAULT 'general'::character varying NOT NULL
);


ALTER TABLE ecos_control.ecos_audit_log OWNER TO postgres;

--
-- Name: COLUMN ecos_audit_log.prev_hash; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_audit_log.prev_hash IS 'P1-3: 前一条记录的哈希值，首条为 SHA256("")';


--
-- Name: COLUMN ecos_audit_log.curr_hash; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_audit_log.curr_hash IS 'P1-3: 当前记录的哈希值 = SHA256(prev_hash || username || operation || entity_type || entity_id || created_at)';


--
-- Name: COLUMN ecos_audit_log.hash_algorithm; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_audit_log.hash_algorithm IS 'P1-3: 哈希算法标识';


--
-- Name: ecos_audit_log_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_audit_log_id_seq OWNER TO postgres;

--
-- Name: ecos_audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_audit_log_id_seq OWNED BY ecos_control.ecos_audit_log.id;


--
-- Name: ecos_biz_contract; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_biz_contract (
    id character varying(20) NOT NULL,
    contract_no character varying(50),
    contract_type character varying(20),
    project_id character varying(20),
    party_name character varying(100),
    amount numeric(18,2),
    signed_date date,
    status character varying(20)
);


ALTER TABLE ecos_control.ecos_biz_contract OWNER TO postgres;

--
-- Name: ecos_biz_department; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_biz_department (
    id character varying(20) NOT NULL,
    name character varying(100),
    manager character varying(50),
    parent_id character varying(20)
);


ALTER TABLE ecos_control.ecos_biz_department OWNER TO postgres;

--
-- Name: ecos_biz_metric; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_biz_metric (
    id character varying(20) NOT NULL,
    dept_id character varying(20),
    metric_type character varying(30),
    metric_value numeric(18,2),
    target_value numeric(18,2),
    metric_month character varying(7),
    created_at timestamp without time zone DEFAULT now(),
    goal_id bigint
);


ALTER TABLE ecos_control.ecos_biz_metric OWNER TO postgres;

--
-- Name: ecos_biz_project; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_biz_project (
    id character varying(20) NOT NULL,
    name character varying(200),
    project_type character varying(20),
    dept_id character varying(20),
    customer_name character varying(100),
    contract_amount numeric(18,2),
    status character varying(20),
    start_date date,
    end_date date,
    manager character varying(50),
    goal_id bigint
);


ALTER TABLE ecos_control.ecos_biz_project OWNER TO postgres;

--
-- Name: ecos_biz_target; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_biz_target (
    id character varying(20) NOT NULL,
    dept_id character varying(20),
    target_type character varying(30),
    target_value numeric(18,2),
    target_year integer,
    created_by character varying(50),
    goal_id bigint
);


ALTER TABLE ecos_control.ecos_biz_target OWNER TO postgres;

--
-- Name: ecos_business_glossary; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_business_glossary (
    id character varying(64) NOT NULL,
    term character varying(255) NOT NULL,
    definition text NOT NULL,
    aliases text,
    domain character varying(128),
    status character varying(32) DEFAULT 'Draft'::character varying,
    steward character varying(64),
    related_terms text,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    domain_id character varying(50)
);


ALTER TABLE ecos_control.ecos_business_glossary OWNER TO postgres;

--
-- Name: ecos_business_scenario; Type: TABLE; Schema: ecos_control; Owner: postgres
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
    is_deleted smallint DEFAULT 0 NOT NULL
);


ALTER TABLE ecos_control.ecos_business_scenario OWNER TO postgres;

--
-- Name: ecos_cls_policy; Type: TABLE; Schema: ecos_control; Owner: postgres
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
    resource_id character varying(64)
);


ALTER TABLE ecos_control.ecos_cls_policy OWNER TO postgres;

--
-- Name: COLUMN ecos_cls_policy.resource_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cls_policy.resource_id IS 'PMO-data10 资产驱动 CLS（与 table_name 双轨，老行 resource_id 为空走 table_name 兜底）';


--
-- Name: ecos_cognitive_belief; Type: TABLE; Schema: ecos_control; Owner: postgres
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
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


ALTER TABLE ecos_control.ecos_cognitive_belief OWNER TO postgres;

--
-- Name: TABLE ecos_cognitive_belief; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_cognitive_belief IS '不确定性判断表 — ADR-9 心智层 P 库: 有限离散概率分布+版本+人工覆写(原稿"信念"改称)';


--
-- Name: COLUMN ecos_cognitive_belief.variable_name; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.variable_name IS '不可观测经营变量名';


--
-- Name: COLUMN ecos_cognitive_belief.distribution; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.distribution IS '有限离散概率分布 [{"outcome","prob"}], prob 和=1';


--
-- Name: COLUMN ecos_cognitive_belief.version; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.version IS '分布版本(新证据加权更新 +1)';


--
-- Name: COLUMN ecos_cognitive_belief.snapshot_version; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.snapshot_version IS '时间回放预留(Phase 3 历史决策同参数重算)';


--
-- Name: COLUMN ecos_cognitive_belief.is_manual_override; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.is_manual_override IS '人工覆写标记(专家干预优先)';


--
-- Name: COLUMN ecos_cognitive_belief.last_evidence_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.last_evidence_id IS '触发本次版本更新的证据引用(溯源)';


--
-- Name: COLUMN ecos_cognitive_belief.version_no; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.version_no IS '乐观锁版本号 (DR07 规范)';


--
-- Name: ecos_cognitive_evidence; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_cognitive_evidence OWNER TO postgres;

--
-- Name: TABLE ecos_cognitive_evidence; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_cognitive_evidence IS '认知证据表 — ADR-9 心智层: 结构化证据(来源/可信度/冲突标记)';


--
-- Name: COLUMN ecos_cognitive_evidence.source_type; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_evidence.source_type IS '来源类型 SYSTEM_DATA/NEWS/EXPERT/PIPELINE';


--
-- Name: COLUMN ecos_cognitive_evidence.blob; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_evidence.blob IS '结构化事实载荷(事实/指标/数值/上下文)';


--
-- Name: COLUMN ecos_cognitive_evidence.confidence; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_evidence.confidence IS '可信度 0~1 (系统数据0.95+ / 权威新闻~0.8 / 小道消息0.3~0.5)';


--
-- Name: COLUMN ecos_cognitive_evidence.is_conflict; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_evidence.is_conflict IS '同事实多证据不一致→冲突待修正';


--
-- Name: COLUMN ecos_cognitive_evidence.version_no; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_evidence.version_no IS '乐观锁版本号 (DR07 规范)';


--
-- Name: ecos_cognitive_hypothesis; Type: TABLE; Schema: ecos_control; Owner: postgres
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
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


ALTER TABLE ecos_control.ecos_cognitive_hypothesis OWNER TO postgres;

--
-- Name: TABLE ecos_cognitive_hypothesis; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_cognitive_hypothesis IS '认知假设表 — ADR-9 心智层 H 库: 假设/证据引用/失效监测';


--
-- Name: COLUMN ecos_cognitive_hypothesis.statement; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_hypothesis.statement IS '假设陈述(业务语言)';


--
-- Name: COLUMN ecos_cognitive_hypothesis.metric_ref; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_hypothesis.metric_ref IS '关联经营变量(与 belief.variable_name 对齐)';


--
-- Name: COLUMN ecos_cognitive_hypothesis.evidence_ids; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_hypothesis.evidence_ids IS '支撑证据 id 列表(引用 ecos_cognitive_evidence.id)';


--
-- Name: COLUMN ecos_cognitive_hypothesis.is_valid; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_hypothesis.is_valid IS '当前是否有效(监测命中失效→FALSE)';


--
-- Name: COLUMN ecos_cognitive_hypothesis.version_no; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_hypothesis.version_no IS '乐观锁版本号 (DR07 规范)';


--
-- Name: ecos_cognitive_model; Type: TABLE; Schema: ecos_control; Owner: postgres
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
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL
);


ALTER TABLE ecos_control.ecos_cognitive_model OWNER TO postgres;

--
-- Name: TABLE ecos_cognitive_model; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_cognitive_model IS '认知模型注册表 — ADR-8 模型资产落盘（推理结果不落盘）';


--
-- Name: COLUMN ecos_cognitive_model.model_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_model.model_id IS '模型标识';


--
-- Name: COLUMN ecos_cognitive_model.model_type; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_model.model_type IS '模型类型 FORECAST/CAUSAL/SIMULATION/DECISION';


--
-- Name: COLUMN ecos_cognitive_model.backtest_score; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_model.backtest_score IS '回测评分(0~1, 可选)';


--
-- Name: COLUMN ecos_cognitive_model.version_no; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_model.version_no IS '乐观锁版本号 (DR07 规范)';


--
-- Name: COLUMN ecos_cognitive_model.domain; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_model.domain IS '多租户预留域 (DR08 规范)';


--
-- Name: ecos_cognitive_run_invalidation; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_cognitive_run_invalidation OWNER TO postgres;

--
-- Name: TABLE ecos_cognitive_run_invalidation; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_cognitive_run_invalidation IS '失效→作废推演结论关联留痕表 — ADR-9 心智状态（PMO-59 P3b T2）';


--
-- Name: COLUMN ecos_cognitive_run_invalidation.event_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_run_invalidation.event_id IS '触发事件 id（ecos.cognitive 事件 cog_evt_ 前缀）';


--
-- Name: COLUMN ecos_cognitive_run_invalidation.hypothesis_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_run_invalidation.hypothesis_id IS '失效假设主键';


--
-- Name: COLUMN ecos_cognitive_run_invalidation.run_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_run_invalidation.run_id IS '被作废推演 run 主键（ecos_scenario_run.id）';


--
-- Name: COLUMN ecos_cognitive_run_invalidation.auto_detected; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_cognitive_run_invalidation.auto_detected IS '失效是否自动检测触发（false=人工兜底）';


--
-- Name: ecos_cron_job; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_cron_job (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    cron_expression character varying(100),
    description text,
    enabled boolean DEFAULT true,
    last_run_at timestamp without time zone,
    next_run_at timestamp without time zone,
    status character varying(50) DEFAULT 'IDLE'::character varying,
    created_by character varying(100),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_cron_job OWNER TO postgres;

--
-- Name: ecos_cron_job_execution; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_cron_job_execution (
    id bigint NOT NULL,
    cron_job_id bigint NOT NULL,
    started_at timestamp without time zone,
    finished_at timestamp without time zone,
    status character varying(50) DEFAULT 'RUNNING'::character varying,
    result text,
    error_message text,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_cron_job_execution OWNER TO postgres;

--
-- Name: ecos_cron_job_execution_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_cron_job_execution_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_cron_job_execution_id_seq OWNER TO postgres;

--
-- Name: ecos_cron_job_execution_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_cron_job_execution_id_seq OWNED BY ecos_control.ecos_cron_job_execution.id;


--
-- Name: ecos_cron_job_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_cron_job_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_cron_job_id_seq OWNER TO postgres;

--
-- Name: ecos_cron_job_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_cron_job_id_seq OWNED BY ecos_control.ecos_cron_job.id;


--
-- Name: ecos_data_lineage_edge; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_data_lineage_edge (
    id character varying(64) NOT NULL,
    source_node_id character varying(64) NOT NULL,
    target_node_id character varying(64) NOT NULL,
    edge_type character varying(64) DEFAULT 'DERIVED'::character varying,
    description character varying(500),
    transform_rule text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_data_lineage_edge OWNER TO postgres;

--
-- Name: ecos_data_lineage_node; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_data_lineage_node (
    id character varying(64) NOT NULL,
    label character varying(200) NOT NULL,
    node_type character varying(64) DEFAULT 'DATASET'::character varying,
    entity_code character varying(100),
    description text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_data_lineage_node OWNER TO postgres;

--
-- Name: ecos_data_pipeline; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_data_pipeline (
    id character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    status character varying(32) DEFAULT 'DRAFT'::character varying,
    source_entity character varying(100),
    target_entity character varying(100),
    schedule character varying(100),
    last_run_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_data_pipeline OWNER TO postgres;

--
-- Name: ecos_data_pipeline_edge; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_data_pipeline_edge (
    id character varying(64) NOT NULL,
    pipeline_id character varying(64) NOT NULL,
    source_node_id character varying(64) NOT NULL,
    target_node_id character varying(64) NOT NULL,
    edge_type character varying(64) DEFAULT 'FLOW'::character varying,
    description character varying(500),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_data_pipeline_edge OWNER TO postgres;

--
-- Name: ecos_data_pipeline_node; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_data_pipeline_node (
    id character varying(64) NOT NULL,
    pipeline_id character varying(64) NOT NULL,
    node_type character varying(64) DEFAULT 'TRANSFORM'::character varying,
    label character varying(200) NOT NULL,
    config text,
    position_x integer DEFAULT 0,
    position_y integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_data_pipeline_node OWNER TO postgres;

--
-- Name: ecos_data_request; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_data_request (
    id character varying(64) NOT NULL,
    dataset_id character varying(64) NOT NULL,
    dataset_name character varying(255),
    requester character varying(64) NOT NULL,
    purpose text NOT NULL,
    status character varying(32) DEFAULT 'PENDING'::character varying,
    approved_by character varying(64),
    granted_until timestamp without time zone,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_data_request OWNER TO postgres;

--
-- Name: ecos_decision; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_decision OWNER TO postgres;

--
-- Name: ecos_decision_approval; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_decision_approval OWNER TO postgres;

--
-- Name: ecos_decision_case; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_decision_case (
    id bigint NOT NULL,
    title character varying(256) NOT NULL,
    scenario text,
    tags text[],
    decision jsonb DEFAULT '{}'::jsonb,
    result jsonb DEFAULT '{}'::jsonb,
    feedback character varying(32) DEFAULT 'pending'::character varying,
    source character varying(64),
    created_by character varying(128),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_control.ecos_decision_case OWNER TO postgres;

--
-- Name: ecos_decision_case_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_decision_case_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_decision_case_id_seq OWNER TO postgres;

--
-- Name: ecos_decision_case_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_decision_case_id_seq OWNED BY ecos_control.ecos_decision_case.id;


--
-- Name: ecos_decision_causal_link; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_decision_causal_link (
    id character varying(64) NOT NULL,
    source_decision_id character varying(64) NOT NULL,
    target_decision_id character varying(64) NOT NULL,
    relationship character varying(32) NOT NULL,
    weight numeric(5,4) DEFAULT 0.5,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_decision_causal_link OWNER TO postgres;

--
-- Name: ecos_decision_exception; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_decision_exception (
    id character varying(64) NOT NULL,
    decision_id character varying(64) NOT NULL,
    reason text,
    approver character varying(64),
    status character varying(16) DEFAULT 'pending'::character varying,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_decision_exception OWNER TO postgres;

--
-- Name: ecos_decision_policy; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_decision_policy OWNER TO postgres;

--
-- Name: ecos_decision_precedent; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_decision_precedent (
    id character varying(64) NOT NULL,
    decision_id character varying(64) NOT NULL,
    similar_decision_id character varying(64) NOT NULL,
    similarity numeric(5,4) DEFAULT 0,
    note text,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_decision_precedent OWNER TO postgres;

--
-- Name: ecos_decision_record; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_decision_record OWNER TO postgres;

--
-- Name: ecos_decision_source_ref; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_decision_source_ref (
    id character varying(36) NOT NULL,
    decision_id character varying(36) NOT NULL,
    ep character varying(120) NOT NULL,
    hash character varying(64) NOT NULL,
    mind_id character varying(36),
    ts timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


ALTER TABLE ecos_control.ecos_decision_source_ref OWNER TO postgres;

--
-- Name: TABLE ecos_decision_source_ref; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_decision_source_ref IS '决策溯源明细表（E-3 定版 (decision_id, ep, hash, mind_id, ts)；1:N 替代 source_refs JSONB）';


--
-- Name: ecos_domain; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_domain (
    id character varying(50) NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(200) NOT NULL,
    owner character varying(100),
    description text,
    status character varying(50) DEFAULT 'Draft'::character varying,
    sort_order integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    tenant_id character varying(64)
);


ALTER TABLE ecos_control.ecos_domain OWNER TO postgres;

--
-- Name: ecos_dq_execution_result; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_dq_execution_result OWNER TO postgres;

--
-- Name: ecos_dq_issue; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_dq_issue (
    id character varying(64) NOT NULL,
    rule_id character varying(64),
    entity_type character varying(200) NOT NULL,
    entity_id character varying(200) NOT NULL,
    field_name character varying(200),
    issue_type character varying(40) NOT NULL,
    severity character varying(20) DEFAULT 'MEDIUM'::character varying,
    description text,
    current_value text,
    expected_value text,
    status character varying(20) DEFAULT 'OPEN'::character varying,
    assigned_to character varying(100),
    detected_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    resolved_at timestamp without time zone,
    resolution_note text
);


ALTER TABLE ecos_control.ecos_dq_issue OWNER TO postgres;

--
-- Name: ecos_dq_rule; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_dq_rule (
    id character varying(64) NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    rule_type character varying(40) NOT NULL,
    target_entity character varying(200) NOT NULL,
    target_field character varying(200),
    rule_expression text,
    severity character varying(20) DEFAULT 'MEDIUM'::character varying,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    params jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    tenant_id character varying(32)
);


ALTER TABLE ecos_control.ecos_dq_rule OWNER TO postgres;

--
-- Name: ecos_dq_rule_v2; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_dq_rule_v2 (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    rule_type character varying(64),
    config_json text,
    severity character varying(32) DEFAULT 'MEDIUM'::character varying,
    enabled boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_dq_rule_v2 OWNER TO postgres;

--
-- Name: ecos_dq_rule_v2_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_dq_rule_v2_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_dq_rule_v2_id_seq OWNER TO postgres;

--
-- Name: ecos_dq_rule_v2_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_dq_rule_v2_id_seq OWNED BY ecos_control.ecos_dq_rule_v2.id;


--
-- Name: ecos_entity_table_mapping; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_entity_table_mapping (
    id character varying(64) NOT NULL,
    entity_code character varying(200) NOT NULL,
    entity_name character varying(500),
    domain_code character varying(100) NOT NULL,
    datasource_id character varying(64) NOT NULL,
    resource_name character varying(500) NOT NULL,
    table_schema character varying(200),
    field_mappings jsonb,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    materialized boolean DEFAULT true NOT NULL
);


ALTER TABLE ecos_control.ecos_entity_table_mapping OWNER TO postgres;

--
-- Name: COLUMN ecos_entity_table_mapping.materialized; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_entity_table_mapping.materialized IS '是否参与图谱实例化: TRUE=参与(默认), FALSE=显式关闭 (Q2 裁决, B3-1)';


--
-- Name: ecos_function_audit_log; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_function_audit_log OWNER TO postgres;

--
-- Name: ecos_function_audit_log_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_function_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_function_audit_log_id_seq OWNER TO postgres;

--
-- Name: ecos_function_audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_function_audit_log_id_seq OWNED BY ecos_control.ecos_function_audit_log.id;


--
-- Name: ecos_glossary_term; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_glossary_term OWNER TO postgres;

--
-- Name: TABLE ecos_glossary_term; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_glossary_term IS '术语库 — Glossary terms';


--
-- Name: COLUMN ecos_glossary_term.id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.id IS '主键，自增';


--
-- Name: COLUMN ecos_glossary_term.code; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.code IS '术语编码';


--
-- Name: COLUMN ecos_glossary_term.name; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.name IS '术语名称';


--
-- Name: COLUMN ecos_glossary_term.definition; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.definition IS '术语定义';


--
-- Name: COLUMN ecos_glossary_term.domain; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.domain IS '所属领域';


--
-- Name: COLUMN ecos_glossary_term.owner; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.owner IS '负责人';


--
-- Name: COLUMN ecos_glossary_term.status; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.status IS '状态: DRAFT/REVIEW/PUBLISHED/DEPRECATED';


--
-- Name: COLUMN ecos_glossary_term.created_by; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.created_by IS '创建人';


--
-- Name: COLUMN ecos_glossary_term.created_at; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.created_at IS '创建时间';


--
-- Name: COLUMN ecos_glossary_term.updated_at; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.updated_at IS '更新时间';


--
-- Name: COLUMN ecos_glossary_term.term_type; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.term_type IS '词条分类: ENTITY/RELATION/METRIC/FUNCTION/CONCEPT';


--
-- Name: COLUMN ecos_glossary_term.aliases; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.aliases IS '同义词/别名列表';


--
-- Name: COLUMN ecos_glossary_term.object_type_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.object_type_id IS '引用的本体实体主键(ecos_ontology_entity.id)';


--
-- Name: COLUMN ecos_glossary_term.parent_term_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.parent_term_id IS '上位词条 id(自引用)';


--
-- Name: COLUMN ecos_glossary_term.version; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.version IS '词条定义版本号';


--
-- Name: COLUMN ecos_glossary_term.examples; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.examples IS '示例值列表';


--
-- Name: COLUMN ecos_glossary_term.tags; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.tags IS '方法论/场景标签';


--
-- Name: COLUMN ecos_glossary_term.is_primary; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term.is_primary IS '是否为其所属本体实体(object_type_id)的主术语；每实体至多一条';


--
-- Name: ecos_glossary_term_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_glossary_term_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_glossary_term_id_seq OWNER TO postgres;

--
-- Name: ecos_glossary_term_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_glossary_term_id_seq OWNED BY ecos_control.ecos_glossary_term.id;


--
-- Name: ecos_glossary_term_relation; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_glossary_term_relation (
    id bigint NOT NULL,
    from_term_id bigint NOT NULL,
    to_term_id bigint NOT NULL,
    relation_type character varying(32) NOT NULL,
    weight integer DEFAULT 100,
    description character varying(512) DEFAULT ''::character varying,
    created_by character varying(128) DEFAULT ''::character varying,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_glossary_term_relation OWNER TO postgres;

--
-- Name: TABLE ecos_glossary_term_relation; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_glossary_term_relation IS '词条关系边 — Wiki 词条间语义关系';


--
-- Name: COLUMN ecos_glossary_term_relation.from_term_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term_relation.from_term_id IS '关系起点词条 id';


--
-- Name: COLUMN ecos_glossary_term_relation.to_term_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term_relation.to_term_id IS '关系终点词条 id';


--
-- Name: COLUMN ecos_glossary_term_relation.relation_type; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term_relation.relation_type IS '边类型: ISA/SYNONYM/PART_OF/SEE_ALSO/CAUSAL/RELATED';


--
-- Name: COLUMN ecos_glossary_term_relation.weight; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_glossary_term_relation.weight IS '关系权重(默认 100)';


--
-- Name: ecos_glossary_term_relation_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_glossary_term_relation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_glossary_term_relation_id_seq OWNER TO postgres;

--
-- Name: ecos_glossary_term_relation_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_glossary_term_relation_id_seq OWNED BY ecos_control.ecos_glossary_term_relation.id;


--
-- Name: ecos_goal_tracking; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_goal_tracking OWNER TO postgres;

--
-- Name: ecos_knowledge_document; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_knowledge_document (
    id character varying(36) NOT NULL,
    title character varying(300) NOT NULL,
    content text,
    embedding jsonb,
    category character varying(100) DEFAULT NULL::character varying,
    doc_type character varying(64) DEFAULT NULL::character varying,
    tags character varying(500) DEFAULT NULL::character varying,
    entity_types character varying(500) DEFAULT NULL::character varying,
    author character varying(100) DEFAULT NULL::character varying,
    doc_status character varying(20) DEFAULT 'PUBLISHED'::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_knowledge_document OWNER TO postgres;

--
-- Name: ecos_knowledge_graph_edge; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_knowledge_graph_edge (
    id character varying(36) NOT NULL,
    source_node_id character varying(36) NOT NULL,
    target_node_id character varying(36) NOT NULL,
    relationship character varying(200) DEFAULT NULL::character varying,
    weight double precision DEFAULT 1,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_knowledge_graph_edge OWNER TO postgres;

--
-- Name: ecos_knowledge_graph_node; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_knowledge_graph_node (
    id character varying(36) NOT NULL,
    label character varying(200) NOT NULL,
    node_type character varying(100) DEFAULT NULL::character varying,
    description text,
    properties_json jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    processed_at bigint
);


ALTER TABLE ecos_control.ecos_knowledge_graph_node OWNER TO postgres;

--
-- Name: ecos_marketplace_access_request; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_marketplace_access_request OWNER TO postgres;

--
-- Name: TABLE ecos_marketplace_access_request; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_marketplace_access_request IS '数据市场访问申请记录';


--
-- Name: COLUMN ecos_marketplace_access_request.id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_access_request.id IS '主键，自增';


--
-- Name: COLUMN ecos_marketplace_access_request.asset_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_access_request.asset_id IS '关联资产ID';


--
-- Name: COLUMN ecos_marketplace_access_request.reason; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_access_request.reason IS '申请理由';


--
-- Name: COLUMN ecos_marketplace_access_request.applicant; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_access_request.applicant IS '申请人';


--
-- Name: COLUMN ecos_marketplace_access_request.status; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_access_request.status IS '状态: PENDING/APPROVED/REJECTED';


--
-- Name: COLUMN ecos_marketplace_access_request.created_at; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_access_request.created_at IS '创建时间';


--
-- Name: ecos_marketplace_access_request_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_marketplace_access_request_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_marketplace_access_request_id_seq OWNER TO postgres;

--
-- Name: ecos_marketplace_access_request_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_marketplace_access_request_id_seq OWNED BY ecos_control.ecos_marketplace_access_request.id;


--
-- Name: ecos_marketplace_asset; Type: TABLE; Schema: ecos_control; Owner: postgres
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
    ontology_entity_id character varying(128)
);


ALTER TABLE ecos_control.ecos_marketplace_asset OWNER TO postgres;

--
-- Name: TABLE ecos_marketplace_asset; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_marketplace_asset IS '数据市场 — marketplace assets';


--
-- Name: COLUMN ecos_marketplace_asset.id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.id IS '主键，自增';


--
-- Name: COLUMN ecos_marketplace_asset.name; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.name IS '资产名称';


--
-- Name: COLUMN ecos_marketplace_asset.description; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.description IS '资产描述';


--
-- Name: COLUMN ecos_marketplace_asset.category; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.category IS '分类: 数据集/AI模型/API/报表';


--
-- Name: COLUMN ecos_marketplace_asset.owner; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.owner IS '所属部门/所有者';


--
-- Name: COLUMN ecos_marketplace_asset.rating; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.rating IS '评分 0.00~5.00';


--
-- Name: COLUMN ecos_marketplace_asset.popularity; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.popularity IS '综合热度值';


--
-- Name: COLUMN ecos_marketplace_asset.status; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.status IS '状态: DRAFT/PUBLISHED/DEPRECATED';


--
-- Name: COLUMN ecos_marketplace_asset.created_at; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_marketplace_asset.created_at IS '创建时间';


--
-- Name: ecos_marketplace_asset_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_marketplace_asset_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_marketplace_asset_id_seq OWNER TO postgres;

--
-- Name: ecos_marketplace_asset_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_marketplace_asset_id_seq OWNED BY ecos_control.ecos_marketplace_asset.id;


--
-- Name: ecos_mission; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_mission (
    id character varying(64) NOT NULL,
    title character varying(500) NOT NULL,
    goal text NOT NULL,
    mode character varying(32) NOT NULL,
    status character varying(32) DEFAULT 'PENDING'::character varying,
    plan jsonb,
    result jsonb,
    created_by character varying(64),
    created_at timestamp without time zone DEFAULT now(),
    finished_at timestamp without time zone
);


ALTER TABLE ecos_control.ecos_mission OWNER TO postgres;

--
-- Name: ecos_mission_task; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_mission_task (
    id character varying(64) NOT NULL,
    mission_id character varying(64) NOT NULL,
    agent_id character varying(64) NOT NULL,
    instruction text NOT NULL,
    status character varying(32) DEFAULT 'PENDING'::character varying,
    result jsonb,
    depends_on text,
    started_at timestamp without time zone,
    finished_at timestamp without time zone
);


ALTER TABLE ecos_control.ecos_mission_task OWNER TO postgres;

--
-- Name: ecos_object_attachment; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_object_attachment (
    id bigint NOT NULL,
    object_id character varying(64) NOT NULL,
    entity_code character varying(64) NOT NULL,
    file_name character varying(255) NOT NULL,
    file_path character varying(512),
    file_size bigint DEFAULT 0,
    mime_type character varying(128),
    uploaded_by character varying(64),
    uploaded_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_object_attachment OWNER TO postgres;

--
-- Name: ecos_object_attachment_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_object_attachment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_object_attachment_id_seq OWNER TO postgres;

--
-- Name: ecos_object_attachment_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_object_attachment_id_seq OWNED BY ecos_control.ecos_object_attachment.id;


--
-- Name: ecos_object_data; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_object_data (
    id character varying(36) NOT NULL,
    entity_code character varying(100) NOT NULL,
    object_data jsonb,
    status character varying(50) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(64),
    classification character varying(32) DEFAULT '内部'::character varying,
    sensitivity character varying(32) DEFAULT '普通'::character varying,
    tenant_id character varying(32)
);


ALTER TABLE ecos_control.ecos_object_data OWNER TO postgres;

--
-- Name: ecos_object_links; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_object_links (
    id character varying(64) DEFAULT (gen_random_uuid())::text NOT NULL,
    source_id character varying(64) NOT NULL,
    target_id character varying(64) NOT NULL,
    relation_code character varying(128) NOT NULL,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_object_links OWNER TO postgres;

--
-- Name: ecos_object_relation; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_object_relation (
    id character varying(36) NOT NULL,
    source_object_id character varying(36) NOT NULL,
    target_object_id character varying(36) NOT NULL,
    relation_code character varying(100) DEFAULT NULL::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    properties jsonb DEFAULT '{}'::jsonb,
    tenant_id character varying(32)
);


ALTER TABLE ecos_control.ecos_object_relation OWNER TO postgres;

--
-- Name: ecos_object_relationship; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_object_relationship OWNER TO postgres;

--
-- Name: ecos_object_state_machine; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_object_state_machine OWNER TO postgres;

--
-- Name: ecos_object_timeline; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_object_timeline (
    id character varying(36) NOT NULL,
    object_id character varying(36) NOT NULL,
    event_type character varying(64) NOT NULL,
    event_detail jsonb DEFAULT '{}'::jsonb,
    operator character varying(64),
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_object_timeline OWNER TO postgres;

--
-- Name: ecos_object_version; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_object_version (
    id bigint NOT NULL,
    object_id character varying(64) NOT NULL,
    entity_code character varying(64) NOT NULL,
    version_num integer DEFAULT 1 NOT NULL,
    snapshot jsonb,
    change_summary text,
    changed_by character varying(64),
    changed_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_object_version OWNER TO postgres;

--
-- Name: ecos_object_version_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_object_version_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_object_version_id_seq OWNER TO postgres;

--
-- Name: ecos_object_version_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_object_version_id_seq OWNED BY ecos_control.ecos_object_version.id;


--
-- Name: ecos_ontology; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_ontology (
    id character varying(36) NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(200) NOT NULL,
    version character varying(20) DEFAULT '1.0'::character varying,
    status character varying(20) DEFAULT 'DRAFT'::character varying,
    description text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    is_deleted smallint DEFAULT 0 NOT NULL,
    update_by character varying(128),
    tenant_id character varying(32)
);


ALTER TABLE ecos_control.ecos_ontology OWNER TO postgres;

--
-- Name: ecos_ontology_action; Type: TABLE; Schema: ecos_control; Owner: postgres
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
    preconditions text,
    effects text,
    code character varying(100),
    description text,
    is_deleted smallint DEFAULT 0 NOT NULL,
    update_by character varying(128)
);


ALTER TABLE ecos_control.ecos_ontology_action OWNER TO postgres;

--
-- Name: ecos_ontology_data; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_ontology_data (
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


ALTER TABLE ecos_control.ecos_ontology_data OWNER TO postgres;

--
-- Name: ecos_ontology_entity; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_ontology_entity (
    id character varying(36) NOT NULL,
    ontology_id character varying(36) NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    entity_type character varying(50) DEFAULT 'MASTER'::character varying,
    sort_order integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    domain_id character varying(50),
    tenant_id character varying(32),
    is_deleted smallint DEFAULT 0 NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    update_by character varying(128)
);


ALTER TABLE ecos_control.ecos_ontology_entity OWNER TO postgres;

--
-- Name: ecos_ontology_property; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_ontology_property (
    id character varying(36) NOT NULL,
    entity_id character varying(36) NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(200) DEFAULT NULL::character varying,
    property_type character varying(50) DEFAULT 'STRING'::character varying,
    required_flag integer DEFAULT 0,
    searchable_flag integer DEFAULT 0,
    sort_order integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    function_type character varying(32),
    function_expression text,
    is_deleted smallint DEFAULT 0 NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    update_by character varying(128),
    unique_flag integer DEFAULT 0,
    description character varying(500)
);


ALTER TABLE ecos_control.ecos_ontology_property OWNER TO postgres;

--
-- Name: COLUMN ecos_ontology_property.unique_flag; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_ontology_property.unique_flag IS '唯一/主键标识：1=主键(唯一)，0=普通属性';


--
-- Name: COLUMN ecos_ontology_property.description; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_ontology_property.description IS '属性描述（本体工作台属性编辑器）';


--
-- Name: ecos_ontology_proposals; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_ontology_proposals (
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
    optimistic_lock_version integer DEFAULT 1 NOT NULL
);


ALTER TABLE ecos_control.ecos_ontology_proposals OWNER TO postgres;

--
-- Name: ecos_ontology_proposals_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_ontology_proposals_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_ontology_proposals_id_seq OWNER TO postgres;

--
-- Name: ecos_ontology_proposals_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_ontology_proposals_id_seq OWNED BY ecos_control.ecos_ontology_proposals.id;


--
-- Name: ecos_ontology_relationship; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_ontology_relationship (
    id character varying(36) NOT NULL,
    source_entity_id character varying(36) NOT NULL,
    target_entity_id character varying(36) NOT NULL,
    code character varying(100) DEFAULT NULL::character varying,
    name character varying(200) DEFAULT NULL::character varying,
    relationship_type character varying(50) DEFAULT 'ONE_TO_MANY'::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    is_deleted smallint DEFAULT 0 NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    update_by character varying(128)
);


ALTER TABLE ecos_control.ecos_ontology_relationship OWNER TO postgres;

--
-- Name: ecos_ontology_rule; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_ontology_rule (
    id character varying(50) NOT NULL,
    entity_id character varying(50) NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(200) NOT NULL,
    rule_type character varying(50) NOT NULL,
    expression text NOT NULL,
    action text,
    priority integer DEFAULT 0,
    enabled integer DEFAULT 1,
    description text,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_ontology_rule OWNER TO postgres;

--
-- Name: ecos_ontology_version; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_ontology_version (
    id character varying(50) NOT NULL,
    ontology_id character varying(50) NOT NULL,
    version_no character varying(20) NOT NULL,
    status character varying(50) DEFAULT 'Draft'::character varying,
    snapshot jsonb NOT NULL,
    change_log text,
    publisher character varying(100),
    published_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_ontology_version OWNER TO postgres;

--
-- Name: ecos_pipeline_definition; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_pipeline_definition (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    definition jsonb NOT NULL,
    status character varying(32) DEFAULT 'Draft'::character varying,
    cron_expression character varying(128),
    event_trigger character varying(255),
    created_by character varying(64),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    tenant_id character varying(32)
);


ALTER TABLE ecos_control.ecos_pipeline_definition OWNER TO postgres;

--
-- Name: ecos_pipeline_execution; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_pipeline_execution (
    id character varying(64) NOT NULL,
    pipeline_id character varying(64) NOT NULL,
    status character varying(32) DEFAULT 'RUNNING'::character varying,
    node_statuses jsonb DEFAULT '{}'::jsonb,
    trigger_type character varying(32),
    started_at timestamp without time zone DEFAULT now(),
    finished_at timestamp without time zone,
    logs jsonb DEFAULT '[]'::jsonb,
    error_message text,
    rows_processed bigint DEFAULT 0,
    tenant_id character varying(32)
);


ALTER TABLE ecos_control.ecos_pipeline_execution OWNER TO postgres;

--
-- Name: ecos_pipeline_function; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_pipeline_function (
    id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    category character varying(50) NOT NULL,
    signature text NOT NULL,
    return_type character varying(50),
    description text,
    example text,
    is_builtin boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_pipeline_function OWNER TO postgres;

--
-- Name: ecos_pipeline_node; Type: TABLE; Schema: ecos_control; Owner: postgres
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
    depends_on jsonb
);


ALTER TABLE ecos_control.ecos_pipeline_node OWNER TO postgres;

--
-- Name: ecos_pipeline_run; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_pipeline_run (
    id character varying(36) NOT NULL,
    task_id character varying(36) NOT NULL,
    status character varying(20) DEFAULT 'QUEUED'::character varying,
    triggered_by character varying(50) DEFAULT 'manual'::character varying,
    total_steps integer DEFAULT 0,
    completed_steps integer DEFAULT 0,
    started_at timestamp without time zone,
    finished_at timestamp without time zone,
    elapsed_ms integer DEFAULT 0,
    error_msg text,
    log_json jsonb DEFAULT '[]'::jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_pipeline_run OWNER TO postgres;

--
-- Name: ecos_pipeline_step; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_pipeline_step (
    id character varying(36) NOT NULL,
    task_id character varying(36) NOT NULL,
    step_order integer NOT NULL,
    node_id character varying(100) NOT NULL,
    node_type character varying(50) NOT NULL,
    config_json jsonb DEFAULT '{}'::jsonb,
    depends_on jsonb DEFAULT '[]'::jsonb,
    position_x double precision DEFAULT 0,
    position_y double precision DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_pipeline_step OWNER TO postgres;

--
-- Name: ecos_pipeline_step_run; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_pipeline_step_run (
    id character varying(36) NOT NULL,
    run_id character varying(36) NOT NULL,
    step_id character varying(36) NOT NULL,
    node_id character varying(100) NOT NULL,
    status character varying(20) DEFAULT 'QUEUED'::character varying,
    rows_input integer DEFAULT 0,
    rows_output integer DEFAULT 0,
    started_at timestamp without time zone,
    finished_at timestamp without time zone,
    elapsed_ms integer DEFAULT 0,
    error_msg text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    node_type character varying(50),
    retry_count integer DEFAULT 0
);


ALTER TABLE ecos_control.ecos_pipeline_step_run OWNER TO postgres;

--
-- Name: ecos_pipeline_task; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_pipeline_task (
    id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    yaml_content text NOT NULL,
    git_url character varying(500),
    git_branch character varying(100) DEFAULT 'main'::character varying,
    git_commit_id character varying(40),
    status character varying(20) DEFAULT 'DRAFT'::character varying,
    cron_expression character varying(100),
    config_json jsonb DEFAULT '{}'::jsonb,
    created_by character varying(100),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    enabled boolean DEFAULT true,
    task_type character varying(20) DEFAULT 'TRANSFORM'::character varying
);


ALTER TABLE ecos_control.ecos_pipeline_task OWNER TO postgres;

--
-- Name: COLUMN ecos_pipeline_task.task_type; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_pipeline_task.task_type IS 'TRANSFORM / SYNC / LAKE_EXPORT';


--
-- Name: ecos_pipeline_test_out; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_pipeline_test_out (
    id integer,
    dept character varying(64),
    region character varying(64),
    amount numeric,
    rank integer
);


ALTER TABLE ecos_control.ecos_pipeline_test_out OWNER TO postgres;

--
-- Name: ecos_pipeline_test_out_marker; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_pipeline_test_out_marker (
    marker character varying(64),
    ts character varying(20)
);


ALTER TABLE ecos_control.ecos_pipeline_test_out_marker OWNER TO postgres;

--
-- Name: ecos_pipeline_udf; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_pipeline_udf (
    id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    category character varying(50),
    language character varying(20) DEFAULT 'python'::character varying,
    signature text,
    source_code text NOT NULL,
    compiled_path character varying(500),
    version integer DEFAULT 1,
    author character varying(100),
    is_shared boolean DEFAULT false,
    description text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_pipeline_udf OWNER TO postgres;

--
-- Name: ecos_provenance_entry; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_provenance_entry (
    id character varying(64) NOT NULL,
    entity_type character varying(32) NOT NULL,
    entity_id character varying(64) NOT NULL,
    source_type character varying(32),
    source_ref text,
    agent character varying(64),
    activity character varying(64),
    "timestamp" timestamp without time zone DEFAULT now(),
    detail text
);


ALTER TABLE ecos_control.ecos_provenance_entry OWNER TO postgres;

--
-- Name: ecos_quality_evaluation; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_quality_evaluation (
    id character varying(36) NOT NULL,
    dataset_id character varying(100),
    rule_id character varying(64),
    passed boolean,
    total_rows bigint,
    failed_rows bigint,
    pass_rate double precision,
    sample_size integer,
    sample_failures jsonb,
    severity character varying(10),
    message text,
    evaluated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_quality_evaluation OWNER TO postgres;

--
-- Name: ecos_quality_rule; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_quality_rule (
    rule_id character varying(64) NOT NULL,
    rule_name character varying(200) NOT NULL,
    rule_type character varying(30) NOT NULL,
    target character varying(200) NOT NULL,
    dataset_id character varying(100),
    parameters jsonb,
    severity character varying(10) DEFAULT 'WARN'::character varying,
    enabled boolean DEFAULT true,
    description text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_quality_rule OWNER TO postgres;

--
-- Name: ecos_query_history; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_query_history (
    id character varying(36) NOT NULL,
    template_id character varying(36),
    datasource_id character varying(36) NOT NULL,
    sql_content text NOT NULL,
    status character varying(20) DEFAULT 'RUNNING'::character varying,
    rows_returned integer DEFAULT 0,
    elapsed_ms integer DEFAULT 0,
    error_msg text,
    executed_by character varying(100),
    started_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    finished_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_query_history OWNER TO postgres;

--
-- Name: ecos_query_template; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_query_template (
    id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    datasource_id character varying(36) NOT NULL,
    sql_content text NOT NULL,
    params_json jsonb DEFAULT '{}'::jsonb,
    timeout_seconds integer DEFAULT 30,
    max_rows integer DEFAULT 10000,
    created_by character varying(100),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_query_template OWNER TO postgres;

--
-- Name: ecos_rls_policy; Type: TABLE; Schema: ecos_control; Owner: postgres
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
    resource_id character varying(64)
);


ALTER TABLE ecos_control.ecos_rls_policy OWNER TO postgres;

--
-- Name: COLUMN ecos_rls_policy.resource_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_rls_policy.resource_id IS 'PMO-data10 资产驱动 RLS（兼容 table_name）';


--
-- Name: ecos_scenario_active_mind; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_scenario_active_mind (
    scenario_id character varying(36) NOT NULL,
    mind_id character varying(36) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


ALTER TABLE ecos_control.ecos_scenario_active_mind OWNER TO postgres;

--
-- Name: TABLE ecos_scenario_active_mind; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_scenario_active_mind IS '场景激活心智 1:1 表（MC03 安全形态替代 partial unique；切换原子性由服务层事务 F07-19-2）';


--
-- Name: ecos_scenario_asset_binding; Type: TABLE; Schema: ecos_control; Owner: postgres
--

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
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    CONSTRAINT ck_scen_bind_binding_type CHECK (((binding_type)::text = ANY ((ARRAY['DATASET'::character varying, 'OBJECT_TYPE'::character varying, 'KNOWLEDGE_BASE'::character varying, 'AI_AGENT'::character varying, 'SECURITY_POLICY'::character varying, 'INTERFACE'::character varying])::text[])))
);


ALTER TABLE ecos_control.ecos_scenario_asset_binding OWNER TO postgres;

--
-- Name: TABLE ecos_scenario_asset_binding; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_scenario_asset_binding IS '场景六类绑定表（binding_type 六值 CHECK 单列权威；旧 ecos_scenario_binding 停写不删）';


--
-- Name: COLUMN ecos_scenario_asset_binding.is_island; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_scenario_asset_binding.is_island IS '孤岛标记（保存期打标，R-26① DRAFT 容忍；激活期 409 在服务层，DDL 不承载拒绝逻辑）';


--
-- Name: ecos_scenario_binding; Type: TABLE; Schema: ecos_control; Owner: postgres
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
    target_type character varying(32)
);


ALTER TABLE ecos_control.ecos_scenario_binding OWNER TO postgres;

--
-- Name: COLUMN ecos_scenario_binding.target_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_scenario_binding.target_id IS 'v2.0 真 PG 主键目标（替代 target_ref 字符串别名，与 V147/V199 注释同源口径）';


--
-- Name: COLUMN ecos_scenario_binding.target_type; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_scenario_binding.target_type IS '真资源类型：DATASOURCE/ONTOLOGY_ENTITY/KNOWLEDGE_ARTICLE/AGENT_PROFILE/SECURITY_POLICY/INTERFACE_REF';


--
-- Name: ecos_scenario_binding_edge; Type: TABLE; Schema: ecos_control; Owner: postgres
--

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
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    CONSTRAINT chk_scen_link_link_type CHECK (((link_type)::text = ANY ((ARRAY['MAPPING'::character varying, 'EXTRACTION'::character varying, 'COGNITION'::character varying, 'GOVERN'::character varying, 'EXPOSE'::character varying])::text[]))),
    CONSTRAINT chk_scen_link_no_placeholder CHECK (((source_contract)::text !~~ 'placeholder-%'::text))
);


ALTER TABLE ecos_control.ecos_scenario_binding_edge OWNER TO postgres;

--
-- Name: TABLE ecos_scenario_binding_edge; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_scenario_binding_edge IS '场景绑定关系边表（六类三层有向边，§0.6.2；占位契约 CHECK 禁入；旧表停写不删）';


--
-- Name: COLUMN ecos_scenario_binding_edge.source_contract; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_scenario_binding_edge.source_contract IS '跨工作台契约引用（如 ecos_entity_table_mapping.id），§0.6.2.2 必带；禁 placeholder- 前缀（X-48）';


--
-- Name: ecos_scenario_binding_link; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_scenario_binding_link OWNER TO postgres;

--
-- Name: TABLE ecos_scenario_binding_link; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_scenario_binding_link IS '场景绑定关系边：六类资源之外的有向关系（§0.6.2）';


--
-- Name: COLUMN ecos_scenario_binding_link.link_type; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_scenario_binding_link.link_type IS 'MAPPING | EXTRACTION | COGNITION | GOVERN | EXPOSE';


--
-- Name: COLUMN ecos_scenario_binding_link.source_contract; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_scenario_binding_link.source_contract IS '跨工作台契约引用（如 ecos_entity_table_mapping.id），§0.6.2.2 必带';


--
-- Name: ecos_scenario_canvas_layout; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_scenario_canvas_layout (
    id character varying(36) NOT NULL,
    scenario_id character varying(36) NOT NULL,
    layout_json text NOT NULL,
    layout_version integer DEFAULT 1 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


ALTER TABLE ecos_control.ecos_scenario_canvas_layout OWNER TO postgres;

--
-- Name: TABLE ecos_scenario_canvas_layout; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_scenario_canvas_layout IS '沙盘画布布局表（React Flow 序列化 TEXT 化；乐观锁 layout_version 与基线 version_no 双列并存语义分离；旧表停写不删）';


--
-- Name: COLUMN ecos_scenario_canvas_layout.layout_version; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_scenario_canvas_layout.layout_version IS '画布编辑乐观锁（防并发覆盖，业务语义）';


--
-- Name: COLUMN ecos_scenario_canvas_layout.version_no; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_scenario_canvas_layout.version_no IS 'DR07 记录行基线版本列（与 layout_version 语义无关，D 章声明）';


--
-- Name: ecos_scenario_decision_record; Type: TABLE; Schema: ecos_control; Owner: postgres
--

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
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    CONSTRAINT ck_scen_dec_run_mode CHECK (((run_mode IS NULL) OR ((run_mode)::text = ANY ((ARRAY['FORMAL'::character varying, 'SANDBOX'::character varying])::text[]))))
);


ALTER TABLE ecos_control.ecos_scenario_decision_record OWNER TO postgres;

--
-- Name: TABLE ecos_scenario_decision_record; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_scenario_decision_record IS '场景决策回执表（R-30①+②：场景侧记录台账，非第二套决策底座；底座唯一在 cognitive 经 DecisionGateway；FC-04 五必填实列）';


--
-- Name: ecos_scenario_definition; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_scenario_definition (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    business_goal text,
    department character varying(128),
    priority character varying(16) DEFAULT 'MEDIUM'::character varying NOT NULL,
    status character varying(32) DEFAULT 'DRAFT'::character varying NOT NULL,
    budget numeric(18,2),
    budget_currency character varying(8),
    safety_index_target numeric(8,4),
    actual_safety_index numeric(8,4),
    metrics_json text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    CONSTRAINT ck_scen_def_priority CHECK (((priority)::text = ANY ((ARRAY['CRITICAL'::character varying, 'HIGH'::character varying, 'MEDIUM'::character varying, 'LOW'::character varying])::text[]))),
    CONSTRAINT ck_scen_def_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'COMPLETED'::character varying, 'SUSPENDED'::character varying])::text[])))
);


ALTER TABLE ecos_control.ecos_scenario_definition OWNER TO postgres;

--
-- Name: TABLE ecos_scenario_definition; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_scenario_definition IS '业务场景主表（MC/DR 合规重建；旧 public.ecos_business_scenario 停写不删，对账走 V213 v_legacy_* 视图）';


--
-- Name: COLUMN ecos_scenario_definition.status; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_scenario_definition.status IS '值域按 V123 注释四态定版；服务端状态机（ScenarioStatusMachine, F07-07）为权威迁移校验，若终态值域扩展随 R-24/R-25 后续批以附加 CHECK 演进';


--
-- Name: COLUMN ecos_scenario_definition.budget; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_scenario_definition.budget IS '预算金额，NUMERIC(18,2)（ST03-A）；旧 VARCHAR 串值不做自动解析，存量换算待授权';


--
-- Name: ecos_scenario_execution; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_scenario_execution (
    id character varying(36) NOT NULL,
    scenario_id character varying(36) NOT NULL,
    run_type character varying(32) NOT NULL,
    run_mode character varying(16) DEFAULT 'FORMAL'::character varying NOT NULL,
    status character varying(32) DEFAULT 'RUNNING'::character varying NOT NULL,
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
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    CONSTRAINT ck_scen_exec_run_mode CHECK (((run_mode)::text = ANY ((ARRAY['FORMAL'::character varying, 'SANDBOX'::character varying])::text[]))),
    CONSTRAINT ck_scen_exec_status CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying])::text[])))
);


ALTER TABLE ecos_control.ecos_scenario_execution OWNER TO postgres;

--
-- Name: TABLE ecos_scenario_execution; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_scenario_execution IS '场景运行记录表（run_mode 正交列 R-25①；decision_record_id 属主 R-30②；旧 ecos_scenario_run 停写不删）';


--
-- Name: COLUMN ecos_scenario_execution.run_type; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_scenario_execution.run_type IS '运行目的；三方值域并存（X-33/G2-5），权威值域待 PRD-08 §4.1 定版后以附加 CHECK 演进';


--
-- Name: COLUMN ecos_scenario_execution.run_mode; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_scenario_execution.run_mode IS 'FORMAL=正式 / SANDBOX=演练（F07-08 写白名单 DAO 唯一通道校验输入；与 run_type 语义正交）';


--
-- Name: COLUMN ecos_scenario_execution.decision_record_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_scenario_execution.decision_record_id IS '场景决策回执引用（权威表 ecos_scenario_decision_record；决策底座在 cognitive 经门面，禁第二套底座）';


--
-- Name: ecos_scenario_mind; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_scenario_mind OWNER TO postgres;

--
-- Name: ecos_scenario_mind_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
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
-- Name: ecos_scenario_mind_ref; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_scenario_mind_ref (
    id character varying(36) NOT NULL,
    mind_id character varying(36) NOT NULL,
    ref_kind character varying(20) NOT NULL,
    ref_id character varying(36) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL,
    CONSTRAINT ck_esmr_kind CHECK (((ref_kind)::text = ANY ((ARRAY['EVIDENCE'::character varying, 'HYPOTHESIS'::character varying, 'MODEL'::character varying])::text[])))
);


ALTER TABLE ecos_control.ecos_scenario_mind_ref OWNER TO postgres;

--
-- Name: TABLE ecos_scenario_mind_ref; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_scenario_mind_ref IS '心智引用明细表（E-3：evidence/hypothesis/model 引用 1:N 明细；cognitive 侧仅存 id 引用、只读）';


--
-- Name: ecos_scenario_mind_variant; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_scenario_mind_variant (
    id character varying(36) NOT NULL,
    scenario_id character varying(36) NOT NULL,
    mind_label character varying(64) DEFAULT 'base'::character varying NOT NULL,
    deleted_guard timestamp without time zone DEFAULT '1970-01-01 00:00:00'::timestamp without time zone NOT NULL,
    initial_belief_json text NOT NULL,
    evidence_refs_json text,
    hypothesis_refs_json text,
    model_refs_json text,
    initial_confidence double precision DEFAULT 0.5 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


ALTER TABLE ecos_control.ecos_scenario_mind_variant OWNER TO postgres;

--
-- Name: TABLE ecos_scenario_mind_variant; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_scenario_mind_variant IS '场景多心智变体表（JSONB→TEXT、cognitive_endpoints 移出经 sysman 配置、active_mind 改激活表；旧表停写不删）';


--
-- Name: ecos_scenario_query_history; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_scenario_query_history (
    id character varying(36) NOT NULL,
    trace_id character varying(64),
    subject_id character varying(36) NOT NULL,
    sql_digest character varying(128) NOT NULL,
    row_count integer,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    update_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


ALTER TABLE ecos_control.ecos_scenario_query_history OWNER TO postgres;

--
-- Name: TABLE ecos_scenario_query_history; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_scenario_query_history IS '场景查询历史台账（替换 X-55 内存态；sql_digest 摘要形态，禁存 SQL 原文/结果集）';


--
-- Name: COLUMN ecos_scenario_query_history.sql_digest; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_scenario_query_history.sql_digest IS '查询规范化摘要（防审计面变敏感面，与分册 06 F06-03 同规则）';


--
-- Name: ecos_scenario_run; Type: TABLE; Schema: ecos_control; Owner: postgres
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
    is_deleted smallint DEFAULT 0 NOT NULL
);


ALTER TABLE ecos_control.ecos_scenario_run OWNER TO postgres;

--
-- Name: TABLE ecos_scenario_run; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_scenario_run IS '场景运行记录 — PMO-52 场景工作台认知闭环';


--
-- Name: COLUMN ecos_scenario_run.run_type; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_scenario_run.run_type IS '运行类型 DIAGNOSE/FORECAST/SIMULATE/STRATEGY/FULL';


--
-- Name: COLUMN ecos_scenario_run.degraded; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_scenario_run.degraded IS '是否存在 KG 降级（任一步走规则兜底则 true）';


--
-- Name: ecos_scenario_sandbox_layout; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_scenario_sandbox_layout OWNER TO postgres;

--
-- Name: ecos_scenario_sandbox_layout_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
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
-- Name: ecos_skill; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_skill (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    version character varying(50) DEFAULT '1.0.0'::character varying,
    enabled boolean DEFAULT true,
    category character varying(100),
    package_info text,
    created_by character varying(100),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_skill OWNER TO postgres;

--
-- Name: ecos_skill_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_skill_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_skill_id_seq OWNER TO postgres;

--
-- Name: ecos_skill_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_skill_id_seq OWNED BY ecos_control.ecos_skill.id;


--
-- Name: ecos_spans; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_spans OWNER TO postgres;

--
-- Name: ecos_tenant; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_tenant OWNER TO postgres;

--
-- Name: ecos_tenant_quota; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_tenant_quota OWNER TO postgres;

--
-- Name: ecos_tenant_quota_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_tenant_quota_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_tenant_quota_id_seq OWNER TO postgres;

--
-- Name: ecos_tenant_quota_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_tenant_quota_id_seq OWNED BY ecos_control.ecos_tenant_quota.id;


--
-- Name: ecos_tenant_usage; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_tenant_usage (
    tenant_id character varying(64) NOT NULL,
    usage_date date NOT NULL,
    quota_type character varying(32) NOT NULL,
    used_count bigint DEFAULT 0,
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_tenant_usage OWNER TO postgres;

--
-- Name: ecos_term_entity_binding; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_term_entity_binding (
    id character varying(64) NOT NULL,
    term_id character varying(64) NOT NULL,
    entity_code character varying(200) NOT NULL,
    property_code character varying(200),
    binding_type character varying(32) DEFAULT 'ENTITY'::character varying,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_term_entity_binding OWNER TO postgres;

--
-- Name: ecos_token_blacklist; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_token_blacklist (
    id character varying(64) NOT NULL,
    jti character varying(128) NOT NULL,
    user_id character varying(64) NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.ecos_token_blacklist OWNER TO postgres;

--
-- Name: ecos_token_usage; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_token_usage OWNER TO postgres;

--
-- Name: ecos_token_usage_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_token_usage_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_token_usage_id_seq OWNER TO postgres;

--
-- Name: ecos_token_usage_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_token_usage_id_seq OWNED BY ecos_control.ecos_token_usage.id;


--
-- Name: ecos_tool_definition; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_tool_definition (
    id character varying(36) NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    tool_type character varying(50) DEFAULT 'API'::character varying,
    endpoint_url character varying(500) DEFAULT NULL::character varying,
    http_method character varying(10) DEFAULT 'POST'::character varying,
    schema_json jsonb,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_tool_definition OWNER TO postgres;

--
-- Name: ecos_warn_log; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_warn_log OWNER TO postgres;

--
-- Name: TABLE ecos_warn_log; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.ecos_warn_log IS 'runtime-monitor 告警日志落库表 — PMO-59 P4a（关闭 P2b 告警内存态残留风险）';


--
-- Name: COLUMN ecos_warn_log.log_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_warn_log.log_id IS '告警日志业务键（幂等去重，uniq 索引）';


--
-- Name: COLUMN ecos_warn_log.warn_type; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_warn_log.warn_type IS '告警类型（如 COGNITIVE_HYPOTHESIS_INVALIDATED）';


--
-- Name: COLUMN ecos_warn_log.fault_context; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_warn_log.fault_context IS '结构化故障上下文 JSONB（与 ecos.cognitive 事件 faultContext 同口径）';


--
-- Name: COLUMN ecos_warn_log.review_tag; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.ecos_warn_log.review_tag IS '复盘聚合标签（与 MentalEventPublisher.REVIEW_TAG 同源）';


--
-- Name: ecos_wm_causal_link; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_wm_causal_link OWNER TO postgres;

--
-- Name: ecos_wm_causal_link_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_wm_causal_link_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_wm_causal_link_id_seq OWNER TO postgres;

--
-- Name: ecos_wm_causal_link_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_wm_causal_link_id_seq OWNED BY ecos_control.ecos_wm_causal_link.id;


--
-- Name: ecos_wm_goal; Type: TABLE; Schema: ecos_control; Owner: postgres
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
    weight integer DEFAULT 50,
    org_id character varying(64),
    owner_user_id character varying(64),
    start_date date,
    end_date date,
    target_value numeric(18,2),
    current_value numeric(18,2),
    unit character varying(32),
    linked_workflow_id character varying(64),
    goal_type character varying(32) DEFAULT 'STRATEGIC'::character varying,
    domain_id character varying(50),
    kpi_formula character varying(256) DEFAULT 'currentValue/targetValue*100'::character varying,
    measure_frequency character varying(16) DEFAULT 'MONTHLY'::character varying,
    alert_threshold_warn numeric(5,2) DEFAULT 80.0,
    alert_threshold_critical numeric(5,2) DEFAULT 50.0,
    CONSTRAINT ecos_wm_goal_progress_check CHECK (((progress >= 0) AND (progress <= 100)))
);


ALTER TABLE ecos_control.ecos_wm_goal OWNER TO postgres;

--
-- Name: ecos_wm_goal_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_wm_goal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_wm_goal_id_seq OWNER TO postgres;

--
-- Name: ecos_wm_goal_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_wm_goal_id_seq OWNED BY ecos_control.ecos_wm_goal.id;


--
-- Name: ecos_wm_goal_log; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_wm_goal_log OWNER TO postgres;

--
-- Name: ecos_wm_goal_log_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_wm_goal_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_wm_goal_log_id_seq OWNER TO postgres;

--
-- Name: ecos_wm_goal_log_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_wm_goal_log_id_seq OWNED BY ecos_control.ecos_wm_goal_log.id;


--
-- Name: ecos_wm_scenario; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.ecos_wm_scenario OWNER TO postgres;

--
-- Name: ecos_wm_scenario_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_wm_scenario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_wm_scenario_id_seq OWNER TO postgres;

--
-- Name: ecos_wm_scenario_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_wm_scenario_id_seq OWNED BY ecos_control.ecos_wm_scenario.id;


--
-- Name: ecos_workflow; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_workflow (
    id character varying(36) NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(200) NOT NULL,
    workflow_type character varying(50) DEFAULT 'APPROVAL'::character varying,
    description text,
    trigger_event character varying(200) DEFAULT NULL::character varying,
    status character varying(20) DEFAULT 'DRAFT'::character varying,
    version character varying(20) DEFAULT '1.0'::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_workflow OWNER TO postgres;

--
-- Name: ecos_workflow_edge; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_workflow_edge (
    id character varying(36) NOT NULL,
    workflow_id character varying(36) NOT NULL,
    source_node_id character varying(36) NOT NULL,
    target_node_id character varying(36) NOT NULL,
    condition_expr character varying(500) DEFAULT NULL::character varying,
    edge_label character varying(100) DEFAULT NULL::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_workflow_edge OWNER TO postgres;

--
-- Name: ecos_workflow_instance; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_workflow_instance (
    id character varying(36) NOT NULL,
    workflow_id character varying(36) NOT NULL,
    workflow_code character varying(100) DEFAULT NULL::character varying,
    business_key character varying(200) DEFAULT NULL::character varying,
    status character varying(20) DEFAULT 'RUNNING'::character varying,
    current_node_id character varying(36) DEFAULT NULL::character varying,
    context_json jsonb,
    started_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    completed_at timestamp without time zone,
    tenant_id character varying(32),
    error_message text,
    retry_count integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_control.ecos_workflow_instance OWNER TO postgres;

--
-- Name: ecos_workflow_log; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_workflow_log (
    id bigint NOT NULL,
    instance_id character varying(64) NOT NULL,
    node_id character varying(64),
    node_type character varying(64),
    event_type character varying(64) NOT NULL,
    message text,
    details jsonb,
    duration_ms bigint,
    trace_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_control.ecos_workflow_log OWNER TO postgres;

--
-- Name: ecos_workflow_log_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.ecos_workflow_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.ecos_workflow_log_id_seq OWNER TO postgres;

--
-- Name: ecos_workflow_log_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.ecos_workflow_log_id_seq OWNED BY ecos_control.ecos_workflow_log.id;


--
-- Name: ecos_workflow_node; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_workflow_node (
    id character varying(36) NOT NULL,
    workflow_id character varying(36) NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(200) NOT NULL,
    node_type character varying(50) DEFAULT 'TASK'::character varying,
    assignee_role character varying(100) DEFAULT NULL::character varying,
    config_json jsonb,
    sort_order integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_workflow_node OWNER TO postgres;

--
-- Name: ecos_workflow_task; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_workflow_task (
    id character varying(36) NOT NULL,
    instance_id character varying(36) NOT NULL,
    node_id character varying(36) NOT NULL,
    node_name character varying(200) DEFAULT NULL::character varying,
    assignee character varying(100) DEFAULT NULL::character varying,
    status character varying(20) DEFAULT 'PENDING'::character varying,
    comment text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    completed_at timestamp without time zone
);


ALTER TABLE ecos_control.ecos_workflow_task OWNER TO postgres;

--
-- Name: ecos_workflow_v2; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_workflow_v2 (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    status character varying(32) DEFAULT 'DRAFT'::character varying,
    mode character varying(32) DEFAULT 'sequential'::character varying,
    nodes jsonb DEFAULT '[]'::jsonb,
    edges jsonb DEFAULT '[]'::jsonb,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    published_at timestamp without time zone
);


ALTER TABLE ecos_control.ecos_workflow_v2 OWNER TO postgres;

--
-- Name: ecos_working_memory; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_working_memory (
    id character varying(64) NOT NULL,
    session_id character varying(64) NOT NULL,
    wm_key character varying(128) NOT NULL,
    wm_value text,
    ttl_minutes integer,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamp without time zone
);


ALTER TABLE ecos_control.ecos_working_memory OWNER TO postgres;

--
-- Name: ecos_world_causal_link; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_world_causal_link (
    id character varying(64) NOT NULL,
    source_type character varying(20) NOT NULL,
    source_id character varying(64) NOT NULL,
    target_type character varying(20) NOT NULL,
    target_id character varying(64) NOT NULL,
    relation_type character varying(30) NOT NULL,
    strength double precision DEFAULT 0.5,
    description text,
    metadata jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_world_causal_link OWNER TO postgres;

--
-- Name: ecos_world_goal; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_world_goal (
    id character varying(64) NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    target_value double precision,
    current_value double precision,
    unit character varying(50),
    parent_goal_id character varying(64),
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    priority integer DEFAULT 0,
    category character varying(50),
    owner character varying(100),
    deadline timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_world_goal OWNER TO postgres;

--
-- Name: ecos_world_scenario; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_world_scenario (
    id character varying(64) NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    assumptions jsonb DEFAULT '[]'::jsonb,
    projected_outcomes jsonb DEFAULT '[]'::jsonb,
    base_scenario_id character varying(64),
    status character varying(20) DEFAULT 'DRAFT'::character varying,
    probability double precision DEFAULT 0.5,
    impact_score integer DEFAULT 0,
    category character varying(50),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_world_scenario OWNER TO postgres;

--
-- Name: ecos_world_scenario_impact; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_world_scenario_impact (
    id character varying(64) NOT NULL,
    scenario_id character varying(64) NOT NULL,
    goal_id character varying(64) NOT NULL,
    projected_delta double precision,
    confidence double precision DEFAULT 0.5,
    rationale text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_world_scenario_impact OWNER TO postgres;

--
-- Name: ecos_world_scenarios; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.ecos_world_scenarios (
    id character varying(64) NOT NULL,
    name character varying(255),
    description text,
    goal_ids text,
    status character varying(32) DEFAULT 'draft'::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.ecos_world_scenarios OWNER TO postgres;

--
-- Name: extraction_drafts; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.extraction_drafts (
    id character varying(64) NOT NULL,
    file_name character varying(255),
    file_path text,
    status character varying(32) DEFAULT 'UPLOADED'::character varying,
    parsed_text text,
    extracted_entities_json text,
    extracted_rules_json text,
    error_msg text,
    retry_count integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    file_type character varying(16),
    page_count integer DEFAULT 1,
    char_count integer DEFAULT 0,
    extracted_links_json text,
    rejected_reason text
);


ALTER TABLE ecos_control.extraction_drafts OWNER TO postgres;

--
-- Name: flyway_schema_history; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE ecos_control.flyway_schema_history OWNER TO postgres;

--
-- Name: kb_cognitive_pipeline; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.kb_cognitive_pipeline OWNER TO postgres;

--
-- Name: TABLE kb_cognitive_pipeline; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.kb_cognitive_pipeline IS '认知管线定义（CognitivePipeline 持久化，原 ConcurrentHashMap 内存态）';


--
-- Name: COLUMN kb_cognitive_pipeline.id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.id IS '自增主键';


--
-- Name: COLUMN kb_cognitive_pipeline.pipeline_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.pipeline_id IS '管线业务 ID (UUID)';


--
-- Name: COLUMN kb_cognitive_pipeline.name; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.name IS '管线名称';


--
-- Name: COLUMN kb_cognitive_pipeline.status; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.status IS '管线状态: DRAFT/ACTIVE/ARCHIVED';


--
-- Name: COLUMN kb_cognitive_pipeline.config; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.config IS '管线节点集合 (JSONB: [{nodeId, nodeType, config, dependsOn}, ...])';


--
-- Name: COLUMN kb_cognitive_pipeline.created_by; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.created_by IS '创建人';


--
-- Name: COLUMN kb_cognitive_pipeline.created_at; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.created_at IS '创建时间';


--
-- Name: COLUMN kb_cognitive_pipeline.updated_at; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.updated_at IS '更新时间';


--
-- Name: COLUMN kb_cognitive_pipeline.result; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.result IS '最近执行结果 (JSONB，可为空)';


--
-- Name: COLUMN kb_cognitive_pipeline.is_deleted; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.kb_cognitive_pipeline.is_deleted IS '逻辑删除标记: 0=未删除, 1=已删除';


--
-- Name: kb_cognitive_pipeline_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.kb_cognitive_pipeline_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.kb_cognitive_pipeline_id_seq OWNER TO postgres;

--
-- Name: kb_cognitive_pipeline_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.kb_cognitive_pipeline_id_seq OWNED BY ecos_control.kb_cognitive_pipeline.id;


--
-- Name: kb_lineage_event; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.kb_lineage_event OWNER TO postgres;

--
-- Name: TABLE kb_lineage_event; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.kb_lineage_event IS '血缘事件（Lineage parse 持久化，原 ConcurrentHashMap 内存态）';


--
-- Name: COLUMN kb_lineage_event.id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.kb_lineage_event.id IS '自增主键';


--
-- Name: COLUMN kb_lineage_event.event_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.kb_lineage_event.event_id IS '事件业务 ID (UUID)';


--
-- Name: COLUMN kb_lineage_event.query; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.kb_lineage_event.query IS '解析时使用的查询/输入描述';


--
-- Name: COLUMN kb_lineage_event.format; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.kb_lineage_event.format IS '血缘格式: openlineage/atlas';


--
-- Name: COLUMN kb_lineage_event.nodes; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.kb_lineage_event.nodes IS '血缘节点列表 (JSONB: [{id, label, type}, ...])';


--
-- Name: COLUMN kb_lineage_event.edges; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.kb_lineage_event.edges IS '血缘边列表 (JSONB: [{source, target, type}, ...])';


--
-- Name: COLUMN kb_lineage_event.parse_at; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.kb_lineage_event.parse_at IS '解析时间';


--
-- Name: COLUMN kb_lineage_event.is_deleted; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.kb_lineage_event.is_deleted IS '逻辑删除标记: 0=未删除, 1=已删除';


--
-- Name: kb_lineage_event_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.kb_lineage_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.kb_lineage_event_id_seq OWNER TO postgres;

--
-- Name: kb_lineage_event_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.kb_lineage_event_id_seq OWNED BY ecos_control.kb_lineage_event.id;


--
-- Name: permissions; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.permissions (
    id character varying(64) NOT NULL,
    role_name character varying(128) NOT NULL,
    resource character varying(255) NOT NULL,
    action character varying(64) NOT NULL,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.permissions OWNER TO postgres;

--
-- Name: roles; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.roles (
    id character varying(64) NOT NULL,
    name character varying(128) NOT NULL,
    display_name character varying(255),
    description text,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.roles OWNER TO postgres;

--
-- Name: schema_changes; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.schema_changes OWNER TO postgres;

--
-- Name: schema_changes_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.schema_changes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.schema_changes_id_seq OWNER TO postgres;

--
-- Name: schema_changes_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.schema_changes_id_seq OWNED BY ecos_control.schema_changes.id;


--
-- Name: schema_snapshots; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.schema_snapshots (
    id bigint NOT NULL,
    datasource_id character varying(128) NOT NULL,
    table_name character varying(256) NOT NULL,
    column_hash character varying(64) NOT NULL,
    col_sig text,
    snapshot_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_control.schema_snapshots OWNER TO postgres;

--
-- Name: schema_snapshots_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.schema_snapshots_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.schema_snapshots_id_seq OWNER TO postgres;

--
-- Name: schema_snapshots_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.schema_snapshots_id_seq OWNED BY ecos_control.schema_snapshots.id;


--
-- Name: sys_agent_call_log; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.sys_agent_call_log (
    id character varying(64) NOT NULL,
    subsystem character varying(64) NOT NULL,
    profile_name character varying(128) NOT NULL,
    session_id character varying(64) NOT NULL,
    user_message text,
    tokens_input integer DEFAULT 0 NOT NULL,
    tokens_output integer DEFAULT 0 NOT NULL,
    duration_ms integer DEFAULT 0 NOT NULL,
    status character varying(16) DEFAULT 'success'::character varying NOT NULL,
    error_msg text,
    created_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


ALTER TABLE ecos_control.sys_agent_call_log OWNER TO postgres;

--
-- Name: sys_agent_message; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.sys_agent_message (
    id bigint NOT NULL,
    session_id character varying(64),
    role character varying(16),
    content text,
    tool_calls jsonb,
    tool_results jsonb,
    tokens integer,
    created_at bigint
);


ALTER TABLE ecos_control.sys_agent_message OWNER TO postgres;

--
-- Name: sys_agent_message_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.sys_agent_message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.sys_agent_message_id_seq OWNER TO postgres;

--
-- Name: sys_agent_message_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.sys_agent_message_id_seq OWNED BY ecos_control.sys_agent_message.id;


--
-- Name: sys_agent_profile; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.sys_agent_profile (
    id character varying(64) NOT NULL,
    profile_name character varying(128) NOT NULL,
    subsystem character varying(64) NOT NULL,
    enabled smallint DEFAULT '1'::smallint NOT NULL,
    description character varying(512) DEFAULT NULL::character varying,
    provider character varying(64) DEFAULT NULL::character varying,
    model character varying(128) DEFAULT NULL::character varying,
    base_url character varying(512) DEFAULT NULL::character varying,
    api_key_ref character varying(256) DEFAULT NULL::character varying,
    temperature double precision,
    max_tokens integer,
    system_prompt text,
    max_iterations integer DEFAULT 10,
    session_timeout_sec integer DEFAULT 300,
    tools_enabled smallint DEFAULT 1,
    auto_approve smallint DEFAULT 0,
    allowed_tools text,
    concurrency integer DEFAULT 5,
    priority integer DEFAULT 0,
    created_by character varying(64) DEFAULT NULL::character varying,
    created_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_by character varying(64) DEFAULT NULL::character varying,
    updated_time timestamp without time zone
);


ALTER TABLE ecos_control.sys_agent_profile OWNER TO postgres;

--
-- Name: sys_agent_session; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.sys_agent_session OWNER TO postgres;

--
-- Name: sys_audit_log; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.sys_audit_log (
    id bigint NOT NULL,
    username character varying(100),
    operation character varying(50),
    target character varying(200),
    method character varying(10),
    request_path character varying(500),
    request_body text,
    response_status integer,
    ip_address character varying(50),
    user_agent character varying(500),
    duration_ms bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.sys_audit_log OWNER TO postgres;

--
-- Name: sys_audit_log_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.sys_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.sys_audit_log_id_seq OWNER TO postgres;

--
-- Name: sys_audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.sys_audit_log_id_seq OWNED BY ecos_control.sys_audit_log.id;


--
-- Name: sys_compliance_rule; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.sys_compliance_rule (
    id character varying(64) NOT NULL,
    name character varying(255),
    domain character varying(128),
    rule_type character varying(32),
    condition text,
    action text,
    priority integer DEFAULT 0,
    enabled boolean DEFAULT true,
    description text,
    status character varying(16) DEFAULT 'active'::character varying,
    required_fact_list text,
    extracted_rule_id character varying(64),
    approved_by character varying(64),
    effective_date timestamp without time zone,
    expiry_date timestamp without time zone,
    version integer DEFAULT 1,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.sys_compliance_rule OWNER TO postgres;

--
-- Name: sys_config; Type: TABLE; Schema: ecos_control; Owner: postgres
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
    edition character varying(32) DEFAULT 'all'::character varying,
    config_options text,
    impact_scope text,
    default_value text,
    is_consumed boolean DEFAULT false,
    consumed_by character varying(255),
    consumed_at timestamp without time zone,
    config_desc_zh text
);


ALTER TABLE ecos_control.sys_config OWNER TO postgres;

--
-- Name: sys_dict; Type: TABLE; Schema: ecos_control; Owner: postgres
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
    subsystem character varying(32),
    usage_count integer DEFAULT 0,
    last_used_at timestamp without time zone
);


ALTER TABLE ecos_control.sys_dict OWNER TO postgres;

--
-- Name: sys_token_blacklist; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.sys_token_blacklist (
    token text NOT NULL,
    expire_at bigint NOT NULL
);


ALTER TABLE ecos_control.sys_token_blacklist OWNER TO postgres;

--
-- Name: tb_menu_module; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.tb_menu_module (
    "IDS" integer NOT NULL,
    "MODULE_ID" character varying(50) DEFAULT NULL::character varying,
    "PARENT_ID" character varying(50) NOT NULL,
    "APP_ID" character varying(50) NOT NULL,
    "MODULE_NAME" character varying(100) NOT NULL,
    "DBNAME" character varying(60) NOT NULL,
    "USED" integer DEFAULT 1,
    "ISFOLDER" integer DEFAULT 0,
    "ISREMOVE" integer DEFAULT 1,
    "ISMODIFY" integer DEFAULT 1,
    "ITEMTYPE" integer DEFAULT 1,
    "USERMAPPED" integer DEFAULT 0,
    "CONFIG" character varying(60) DEFAULT NULL::character varying,
    "MODEL" character varying(60) DEFAULT NULL::character varying,
    "ICONPATH" character varying(500) DEFAULT NULL::character varying,
    "ITEMPATH" character varying(500) DEFAULT NULL::character varying,
    "CALLBACKPATH" character varying(500) DEFAULT NULL::character varying,
    "HELPPATH" character varying(500) DEFAULT NULL::character varying,
    "REMARK" text,
    "REMARK1" character varying(100) DEFAULT NULL::character varying,
    "REMARK2" character varying(100) DEFAULT NULL::character varying,
    "REMARK3" character varying(100) DEFAULT NULL::character varying,
    "REMARK4" character varying(100) DEFAULT NULL::character varying,
    "CREATOR" character varying(20) DEFAULT NULL::character varying,
    "CREATE_TIME" timestamp without time zone,
    "SEQUENCES" integer DEFAULT 0
);


ALTER TABLE ecos_control.tb_menu_module OWNER TO postgres;

--
-- Name: tb_menu_module_IDS_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control."tb_menu_module_IDS_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control."tb_menu_module_IDS_seq" OWNER TO postgres;

--
-- Name: tb_menu_module_IDS_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control."tb_menu_module_IDS_seq" OWNED BY ecos_control.tb_menu_module."IDS";


--
-- Name: td_abac_policy; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_abac_policy (
    "POLICY_ID" character varying(50) NOT NULL,
    "POLICY_NAME" character varying(100) NOT NULL,
    "POLICY_RULE" text NOT NULL,
    "RESOURCE_ID" character varying(255) DEFAULT NULL::character varying,
    "ACTION" character varying(50) DEFAULT NULL::character varying,
    "STATUS" character varying(20) DEFAULT 'ACTIVE'::character varying,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying,
    "UPDATED_TIME" timestamp without time zone,
    "UPDATED_BY" character varying(50) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_abac_policy OWNER TO postgres;

--
-- Name: td_audit_log; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_audit_log (
    "LOG_ID" character varying(50) NOT NULL,
    "USER_ID" character varying(50) DEFAULT NULL::character varying,
    "USERNAME" character varying(200) DEFAULT NULL::character varying,
    "ACTION" character varying(100) NOT NULL,
    "RESOURCE_TYPE" character varying(100) DEFAULT NULL::character varying,
    "RESOURCE_ID" character varying(255) DEFAULT NULL::character varying,
    "OPERATION_RESULT" character varying(20) DEFAULT NULL::character varying,
    "IP_ADDRESS" character varying(15) DEFAULT NULL::character varying,
    "USER_AGENT" character varying(500) DEFAULT NULL::character varying,
    "REQUEST_DATA" text,
    "RESPONSE_DATA" text,
    "ERROR_MESSAGE" text,
    "CREATED_TIME" timestamp without time zone NOT NULL
);


ALTER TABLE ecos_control.td_audit_log OWNER TO postgres;

--
-- Name: td_catalog_item; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.td_catalog_item OWNER TO postgres;

--
-- Name: td_compliance_policy; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_compliance_policy (
    "POLICY_ID" character varying(50) NOT NULL,
    "POLICY_NAME" character varying(100) NOT NULL,
    "POLICY_TYPE" character varying(50) NOT NULL,
    "POLICY_RULE" text NOT NULL,
    "STATUS" character varying(20) DEFAULT 'ACTIVE'::character varying,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying,
    "UPDATED_TIME" timestamp without time zone,
    "UPDATED_BY" character varying(50) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_compliance_policy OWNER TO postgres;

--
-- Name: td_config; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_config (
    "CONFIG_ID" character varying(50) NOT NULL,
    "CONFIG_KEY" character varying(100) NOT NULL,
    "CONFIG_VALUE" text,
    "CONFIG_TYPE" character varying(50) NOT NULL,
    "ENVIRONMENT" character varying(50) DEFAULT NULL::character varying,
    "DESCRIPTION" character varying(500) DEFAULT NULL::character varying,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying,
    "UPDATED_TIME" timestamp without time zone,
    "UPDATED_BY" character varying(50) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_config OWNER TO postgres;

--
-- Name: td_config_version; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_config_version (
    "VERSION_ID" character varying(50) NOT NULL,
    "CONFIG_ID" character varying(50) NOT NULL,
    "VERSION_NUMBER" integer NOT NULL,
    "CONFIG_VALUE" text,
    "CHANGE_DESCRIPTION" character varying(500) DEFAULT NULL::character varying,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_config_version OWNER TO postgres;

--
-- Name: td_cross_border_transfer; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_cross_border_transfer (
    "TRANSFER_ID" character varying(50) NOT NULL,
    "SOURCE_REGION" character varying(100) NOT NULL,
    "TARGET_REGION" character varying(100) NOT NULL,
    "RESOURCE_ID" character varying(255) DEFAULT NULL::character varying,
    "TRANSFER_TYPE" character varying(50) DEFAULT NULL::character varying,
    "STATUS" character varying(20) DEFAULT 'ACTIVE'::character varying,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_cross_border_transfer OWNER TO postgres;

--
-- Name: td_crypto_key; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_crypto_key (
    "KEY_ID" character varying(36) NOT NULL,
    "KEY_TYPE" character varying(50) NOT NULL,
    "ALGORITHM" character varying(50) NOT NULL,
    "VERSION" integer DEFAULT 1 NOT NULL,
    "KEY_SIZE" integer,
    "KEY_CONTENT_ENCRYPTED" text NOT NULL,
    "MASTER_KEY_ID" character varying(36) DEFAULT NULL::character varying,
    "STATUS" character varying(20) DEFAULT 'ACTIVE'::character varying,
    "CREATED_TIME" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying,
    "UPDATED_TIME" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    "UPDATED_BY" character varying(50) DEFAULT NULL::character varying,
    "EXPIRY_TIME" timestamp without time zone,
    "ROTATION_PERIOD" integer,
    "LAST_ROTATION_TIME" timestamp without time zone,
    "NEXT_ROTATION_TIME" timestamp without time zone,
    "REMARK" character varying(500) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_crypto_key OWNER TO postgres;

--
-- Name: td_crypto_key_audit; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_crypto_key_audit (
    "AUDIT_ID" character varying(36) NOT NULL,
    "KEY_ID" character varying(36) NOT NULL,
    "VERSION" integer,
    "OPERATION_TYPE" character varying(50) NOT NULL,
    "OPERATION_RESULT" character varying(20) DEFAULT NULL::character varying,
    "OPERATOR_ID" character varying(50) DEFAULT NULL::character varying,
    "OPERATOR_IP" character varying(50) DEFAULT NULL::character varying,
    "OPERATION_TIME" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    "ERROR_MESSAGE" text,
    "REMARK" character varying(500) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_crypto_key_audit OWNER TO postgres;

--
-- Name: td_crypto_master_key; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_crypto_master_key (
    "MASTER_KEY_ID" character varying(36) NOT NULL,
    "MASTER_KEY_NAME" character varying(100) NOT NULL,
    "KEY_SOURCE" character varying(50) NOT NULL,
    "KEY_CONFIG" text,
    "STATUS" character varying(20) DEFAULT 'ACTIVE'::character varying,
    "CREATED_TIME" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying,
    "UPDATED_TIME" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    "UPDATED_BY" character varying(50) DEFAULT NULL::character varying,
    "REMARK" character varying(500) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_crypto_master_key OWNER TO postgres;

--
-- Name: td_data_category; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_data_category (
    id character varying(64) DEFAULT (gen_random_uuid())::text NOT NULL,
    parent_id character varying(64),
    name character varying(128) NOT NULL,
    type character varying(32) DEFAULT 'folder'::character varying,
    sort_order integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control.td_data_category OWNER TO postgres;

--
-- Name: td_data_description; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_data_description (
    "ID" character varying(36) NOT NULL,
    "DATA_TYPE" character varying(50) NOT NULL,
    "NAME" character varying(255) NOT NULL,
    "FORMAT" character varying(50) DEFAULT NULL::character varying,
    "SCHEMA_CONTENT" text,
    "SCHEMA_TYPE" character varying(50) DEFAULT NULL::character varying,
    "METADATA_CONTENT" text,
    "DESCRIPTION" text,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(36) DEFAULT NULL::character varying,
    "UPDATED_TIME" timestamp without time zone,
    "UPDATED_BY" character varying(36) DEFAULT NULL::character varying,
    "STATUS" character varying(20) DEFAULT 'ACTIVE'::character varying,
    "REMARK" character varying(1023) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_data_description OWNER TO postgres;

--
-- Name: td_data_field; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_data_field (
    field_id character varying(64) NOT NULL,
    resource_id character varying(64) NOT NULL,
    field_name character varying(256) NOT NULL,
    field_alias character varying(256),
    field_type character varying(64),
    field_length integer,
    data_precision integer,
    nullable boolean DEFAULT false,
    is_primary_key boolean DEFAULT false,
    default_value character varying(256),
    description text,
    field_order integer DEFAULT 0
);


ALTER TABLE ecos_control.td_data_field OWNER TO postgres;

--
-- Name: td_data_permission_policy; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_data_permission_policy (
    "POLICY_ID" character varying(50) NOT NULL,
    "POLICY_NAME" character varying(100) NOT NULL,
    "POLICY_TYPE" character varying(50) NOT NULL,
    "RESOURCE_ID" character varying(255) NOT NULL,
    "POLICY_RULE" text NOT NULL,
    "STATUS" character varying(20) DEFAULT 'ACTIVE'::character varying,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying,
    "UPDATED_TIME" timestamp without time zone,
    "UPDATED_BY" character varying(50) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_data_permission_policy OWNER TO postgres;

--
-- Name: td_data_residency; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_data_residency (
    "RESIDENCY_ID" character varying(50) NOT NULL,
    "RESOURCE_ID" character varying(255) NOT NULL,
    "REGION" character varying(100) NOT NULL,
    "STATUS" character varying(20) DEFAULT 'ACTIVE'::character varying,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_data_residency OWNER TO postgres;

--
-- Name: td_data_resource; Type: TABLE; Schema: ecos_control; Owner: postgres
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
    zone character varying(16),
    layer character varying(16) DEFAULT 'RAW'::character varying
);


ALTER TABLE ecos_control.td_data_resource OWNER TO postgres;

--
-- Name: COLUMN td_data_resource.zone; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.td_data_resource.zone IS '近源区: STRUCTURED=结构化近源 / UNSTRUCTURED=非结构化近源 / NULL=非近源层';


--
-- Name: COLUMN td_data_resource.layer; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.td_data_resource.layer IS 'Data layer: SOURCE/RAW/CURATED/SEMANTIC/APPLICATION';


--
-- Name: td_data_security_policy; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_data_security_policy (
    policy_id character varying(64) NOT NULL,
    policy_name character varying(255) NOT NULL,
    policy_type character varying(50) NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying,
    policy_content text,
    scope character varying(20) DEFAULT 'ALL'::character varying,
    scope_id character varying(64) DEFAULT NULL::character varying,
    priority integer DEFAULT 0,
    enabled smallint DEFAULT 0,
    created_time timestamp without time zone,
    created_by character varying(64) DEFAULT NULL::character varying,
    updated_time timestamp without time zone,
    updated_by character varying(64) DEFAULT NULL::character varying,
    description character varying(500) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_data_security_policy OWNER TO postgres;

--
-- Name: td_datasource; Type: TABLE; Schema: ecos_control; Owner: postgres
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
    last_test_result character varying(32),
    last_test_message text,
    create_by character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_by character varying(64),
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    is_default character varying(8),
    remark text,
    metadata_config jsonb DEFAULT '{}'::jsonb NOT NULL,
    last_collect_time timestamp without time zone,
    password_enc character varying(1024)
);


ALTER TABLE ecos_control.td_datasource OWNER TO postgres;

--
-- Name: COLUMN td_datasource.metadata_config; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.td_datasource.metadata_config IS '元数据获取策略配置: {strategy, includeRowCount, countMethod, scheduleCron, cacheTtlMinutes, onSourceEdit}';


--
-- Name: COLUMN td_datasource.last_collect_time; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.td_datasource.last_collect_time IS '最近一次元数据采集完成时间';


--
-- Name: td_git_repository; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_git_repository (
    "REPO_ID" character varying(50) NOT NULL,
    "REPO_NAME" character varying(100) NOT NULL,
    "REPO_URL" character varying(500) NOT NULL,
    "REPO_TYPE" character varying(50) DEFAULT NULL::character varying,
    "BRANCH" character varying(100) DEFAULT 'main'::character varying,
    "AUTH_TYPE" character varying(50) DEFAULT NULL::character varying,
    "AUTH_TOKEN" character varying(500) DEFAULT NULL::character varying,
    "STATUS" character varying(20) DEFAULT 'ACTIVE'::character varying,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying,
    "UPDATED_TIME" timestamp without time zone,
    "UPDATED_BY" character varying(50) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_git_repository OWNER TO postgres;

--
-- Name: td_ip_access; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_ip_access (
    "ACCESS_ID" character varying(50) NOT NULL,
    "IP_ADDRESS" character varying(100) NOT NULL,
    "ACCESS_TYPE" character varying(20) NOT NULL,
    "DESCRIPTION" character varying(500) DEFAULT NULL::character varying,
    "STATUS" character(1) DEFAULT '1'::bpchar,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying,
    "UPDATED_TIME" timestamp without time zone,
    "UPDATED_BY" character varying(50) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_ip_access OWNER TO postgres;

--
-- Name: td_metadata_collect_log; Type: TABLE; Schema: ecos_control; Owner: postgres
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


ALTER TABLE ecos_control.td_metadata_collect_log OWNER TO postgres;

--
-- Name: TABLE td_metadata_collect_log; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.td_metadata_collect_log IS 'PMO-37 元数据采集任务执行审计日志';


--
-- Name: td_metadata_collect_log_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control.td_metadata_collect_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control.td_metadata_collect_log_id_seq OWNER TO postgres;

--
-- Name: td_metadata_collect_log_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control.td_metadata_collect_log_id_seq OWNED BY ecos_control.td_metadata_collect_log.id;


--
-- Name: td_org_permission; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_org_permission (
    "PERMISSION_ID" character varying(36) NOT NULL,
    "ORG_ID" character varying(36) NOT NULL,
    "RESOURCE_ID" character varying(255) NOT NULL,
    "ACTION" character varying(50) NOT NULL,
    "INHERIT_FROM_PARENT" character(1) DEFAULT '1'::bpchar,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(36) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_org_permission OWNER TO postgres;

--
-- Name: td_organization; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_organization (
    "ORG_ID" character varying(36) NOT NULL,
    "ORG_NAME" character varying(255) NOT NULL,
    "ORG_CODE" character varying(100) DEFAULT NULL::character varying,
    "PARENT_ORG_ID" character varying(36) DEFAULT NULL::character varying,
    "ORG_TYPE" character varying(50) DEFAULT NULL::character varying,
    "DESCRIPTION" text,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(36) DEFAULT NULL::character varying,
    "UPDATED_TIME" timestamp without time zone,
    "UPDATED_BY" character varying(36) DEFAULT NULL::character varying,
    "STATUS" character varying(20) DEFAULT 'ACTIVE'::character varying,
    "REMARK" character varying(1023) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_organization OWNER TO postgres;

--
-- Name: td_organization_backup; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_organization_backup (
    "ORG_ID" character varying(36),
    "ORG_NAME" character varying(255),
    "ORG_CODE" character varying(100),
    "PARENT_ORG_ID" character varying(36),
    "ORG_TYPE" character varying(50),
    "DESCRIPTION" text,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(36),
    "UPDATED_TIME" timestamp without time zone,
    "UPDATED_BY" character varying(36),
    "STATUS" character varying(20),
    "REMARK" character varying(1023)
);


ALTER TABLE ecos_control.td_organization_backup OWNER TO postgres;

--
-- Name: td_permission; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_permission (
    "PERMISSION_ID" character varying(50) NOT NULL,
    "PERMISSION_NAME" character varying(100) NOT NULL,
    "PERMISSION_CODE" character varying(100) DEFAULT NULL::character varying,
    "RESOURCE_ID" character varying(255) DEFAULT NULL::character varying,
    "ACTION" character varying(50) DEFAULT NULL::character varying,
    "DESCRIPTION" text,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_permission OWNER TO postgres;

--
-- Name: td_permission_backup; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_permission_backup (
    "PERMISSION_ID" character varying(50),
    "PERMISSION_NAME" character varying(100),
    "PERMISSION_CODE" character varying(100),
    "RESOURCE_ID" character varying(255),
    "ACTION" character varying(50),
    "DESCRIPTION" text,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50)
);


ALTER TABLE ecos_control.td_permission_backup OWNER TO postgres;

--
-- Name: td_role; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_role (
    "ROLE_ID" character varying(50) NOT NULL,
    "ROLE_NAME" character varying(100) NOT NULL,
    "ROLE_CODE" character varying(100) DEFAULT NULL::character varying,
    "DESCRIPTION" text,
    "PARENT_ROLE_ID" character varying(50) DEFAULT NULL::character varying,
    "STATUS" character varying(20) DEFAULT 'ACTIVE'::character varying,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying,
    "UPDATED_TIME" timestamp without time zone,
    "UPDATED_BY" character varying(50) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_role OWNER TO postgres;

--
-- Name: td_role_backup; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_role_backup (
    "ROLE_ID" character varying(50),
    "ROLE_NAME" character varying(100),
    "ROLE_CODE" character varying(100),
    "DESCRIPTION" text,
    "PARENT_ROLE_ID" character varying(50),
    "STATUS" character varying(20),
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50),
    "UPDATED_TIME" timestamp without time zone,
    "UPDATED_BY" character varying(50)
);


ALTER TABLE ecos_control.td_role_backup OWNER TO postgres;

--
-- Name: td_role_permission; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_role_permission (
    "ROLE_ID" character varying(50) NOT NULL,
    "PERMISSION_ID" character varying(50) NOT NULL,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_role_permission OWNER TO postgres;

--
-- Name: td_role_security_profile; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_role_security_profile (
    role_id character varying(64) NOT NULL,
    clearance_level integer DEFAULT 0,
    linked_workstation character varying(256),
    audit_mode character varying(32) DEFAULT 'basic'::character varying,
    sandbox_mandatory boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    tenant_id character varying(64)
);


ALTER TABLE ecos_control.td_role_security_profile OWNER TO postgres;

--
-- Name: TABLE td_role_security_profile; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.td_role_security_profile IS '角色级安全配置表 — 优先级次于用户级，高于全局默认';


--
-- Name: COLUMN td_role_security_profile.role_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.td_role_security_profile.role_id IS '角色ID，关联 td_role.ROLE_ID';


--
-- Name: COLUMN td_role_security_profile.clearance_level; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.td_role_security_profile.clearance_level IS '准入等级 0-4: L0公开 L1内部 L2保密 L3机密 L4绝密';


--
-- Name: COLUMN td_role_security_profile.linked_workstation; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.td_role_security_profile.linked_workstation IS '物理工作站绑定（MAC/IP/主机名）';


--
-- Name: COLUMN td_role_security_profile.audit_mode; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.td_role_security_profile.audit_mode IS '审计力度: basic(基本) detailed(详细) full(全量)';


--
-- Name: COLUMN td_role_security_profile.sandbox_mandatory; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.td_role_security_profile.sandbox_mandatory IS '是否强制沙盒运行';


--
-- Name: td_runtime_task; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_runtime_task (
    "TASK_ID" character varying(64) NOT NULL,
    "TASK_NAME" character varying(255) DEFAULT NULL::character varying,
    "TASK_TYPE" character varying(64) DEFAULT NULL::character varying,
    "DESCRIPTION" text,
    "TASK_CONFIG" text,
    "PARAMETERS" text,
    "PRIORITY" integer DEFAULT 0,
    "TIMEOUT" bigint DEFAULT 0,
    "RETRY_COUNT" integer DEFAULT 0,
    "ASYNC_FLAG" character(1) DEFAULT '0'::bpchar,
    "DEPENDENCIES" text,
    "SCHEDULE_ID" character varying(64) DEFAULT NULL::character varying,
    "NODE_ID" character varying(64) DEFAULT NULL::character varying,
    "EXECUTION_MODE" character varying(16) DEFAULT NULL::character varying,
    "CREATED_BY" character varying(64) DEFAULT NULL::character varying,
    "TENANT_ID" character varying(64) DEFAULT NULL::character varying,
    "TAGS" text,
    "EXTENSIONS" text,
    "CREATED_TIME" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    "UPDATED_TIME" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


ALTER TABLE ecos_control.td_runtime_task OWNER TO postgres;

--
-- Name: TABLE td_runtime_task; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.td_runtime_task IS 'runtime-task 即时/定时任务描述主表（task_id 主键, 含审计五字段 + domain + version_no）';


--
-- Name: td_runtime_task_execution; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_runtime_task_execution (
    "EXECUTION_ID" character varying(64) NOT NULL,
    "TASK_ID" character varying(64) NOT NULL,
    "STATUS" character varying(32) DEFAULT NULL::character varying,
    "START_TIME" timestamp without time zone,
    "END_TIME" timestamp without time zone,
    "DURATION" bigint,
    "RESULT" text,
    "ERROR_MESSAGE" text,
    "ERROR_STACK" text,
    "EXECUTION_NODE_ID" character varying(64) DEFAULT NULL::character varying,
    "CREATED_TIME" timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.td_runtime_task_execution OWNER TO postgres;

--
-- Name: td_runtime_task_log; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_runtime_task_log (
    "LOG_ID" bigint NOT NULL,
    "EXECUTION_ID" character varying(64) DEFAULT NULL::character varying,
    "TASK_ID" character varying(64) DEFAULT NULL::character varying,
    "LOG_LEVEL" character varying(16) DEFAULT NULL::character varying,
    "MESSAGE" text,
    "CREATED_TIME" timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.td_runtime_task_log OWNER TO postgres;

--
-- Name: td_runtime_task_log_LOG_ID_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control."td_runtime_task_log_LOG_ID_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control."td_runtime_task_log_LOG_ID_seq" OWNER TO postgres;

--
-- Name: td_runtime_task_log_LOG_ID_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control."td_runtime_task_log_LOG_ID_seq" OWNED BY ecos_control.td_runtime_task_log."LOG_ID";


--
-- Name: td_runtime_task_plan; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_runtime_task_plan (
    "TASK_ID" character varying(64) NOT NULL,
    "PLAN_CONTENT" text,
    "EXECUTION_MODE" character varying(16) DEFAULT NULL::character varying,
    "TARGET_NODE_ID" character varying(64) DEFAULT NULL::character varying,
    "CREATED_TIME" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    "UPDATED_TIME" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    cron_expression character varying(128),
    next_run_at timestamp without time zone,
    last_run_at timestamp without time zone,
    last_status character varying(32),
    task_name character varying(255),
    task_type character varying(64),
    created_by character varying(64),
    tenant_id character varying(64),
    create_by character varying(64),
    update_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default'::character varying NOT NULL,
    version_no character varying(20) DEFAULT '1'::character varying NOT NULL
);


ALTER TABLE ecos_control.td_runtime_task_plan OWNER TO postgres;

--
-- Name: TABLE td_runtime_task_plan; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.td_runtime_task_plan IS 'runtime-task 执行计划主表（plan_content 持久化 + 定时四要素：cron/next_run_at/last_run_at/last_status）';


--
-- Name: td_runtime_task_status; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_runtime_task_status (
    "TASK_ID" character varying(64) NOT NULL,
    "STATUS" character varying(32) DEFAULT NULL::character varying,
    "STATUS_MESSAGE" character varying(500) DEFAULT NULL::character varying,
    "PROGRESS" integer DEFAULT 0,
    "CURRENT_STEP_ID" character varying(64) DEFAULT NULL::character varying,
    "START_TIME" timestamp without time zone,
    "END_TIME" timestamp without time zone,
    "ESTIMATED_REMAINING_TIME" bigint,
    "PROCESSED_COUNT" bigint DEFAULT 0,
    "TOTAL_COUNT" bigint DEFAULT 0,
    "ERROR_MESSAGE" text,
    "ERROR_STACK" text,
    "RESULT" text,
    "METRICS" text,
    "EXECUTION_NODE_ID" character varying(64) DEFAULT NULL::character varying,
    "UPDATED_TIME" timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_control.td_runtime_task_status OWNER TO postgres;

--
-- Name: td_schema_registry; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_schema_registry (
    "ID" character varying(36) NOT NULL,
    "SUBJECT" character varying(255) NOT NULL,
    "VERSION" integer NOT NULL,
    "SCHEMA_CONTENT" text NOT NULL,
    "SCHEMA_TYPE" character varying(50) DEFAULT NULL::character varying,
    "COMPATIBILITY_LEVEL" character varying(50) DEFAULT 'BACKWARD'::character varying,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(36) DEFAULT NULL::character varying,
    "MODIFIED_TIME" timestamp without time zone,
    "MODIFIED_BY" character varying(36) DEFAULT NULL::character varying,
    "REMARK" character varying(1023) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_schema_registry OWNER TO postgres;

--
-- Name: td_schema_version; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_schema_version (
    "VERSION_ID" character varying(36) NOT NULL,
    "SUBJECT" character varying(255) NOT NULL,
    "VERSION" integer NOT NULL,
    "SCHEMA_ID" character varying(36) DEFAULT NULL::character varying,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(36) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_schema_version OWNER TO postgres;

--
-- Name: td_sm_user; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_sm_user (
    user_id character varying(64) NOT NULL,
    username character varying(100) NOT NULL,
    password character varying(255) NOT NULL,
    email character varying(100) DEFAULT NULL::character varying,
    phone character varying(20) DEFAULT NULL::character varying,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    locked character varying(1) DEFAULT '0'::character varying,
    lock_time timestamp without time zone,
    last_login_time timestamp without time zone,
    created_time timestamp without time zone,
    created_by character varying(64) DEFAULT NULL::character varying,
    updated_time timestamp without time zone,
    updated_by character varying(64) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_sm_user OWNER TO postgres;

--
-- Name: td_system_param; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_system_param (
    "PARAM_ID" character varying(50) NOT NULL,
    "PARAM_NAME" character varying(100) NOT NULL,
    "PARAM_CONTENT" text,
    "PARAM_DESC" character varying(500) DEFAULT NULL::character varying,
    "PARAM_TYPE" character varying(50) DEFAULT NULL::character varying,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying,
    "UPDATED_TIME" timestamp without time zone,
    "UPDATED_BY" character varying(50) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_system_param OWNER TO postgres;

--
-- Name: td_system_variable; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_system_variable (
    "VAR_ID" character varying(50) NOT NULL,
    "SCOPE_ID" character varying(50) DEFAULT NULL::character varying,
    "VAR_CODE" character varying(100) NOT NULL,
    "SCOPE_NAME" character varying(100) DEFAULT NULL::character varying,
    "VAR_DESC" character varying(500) DEFAULT NULL::character varying,
    "VAL_TYPE" character varying(50) DEFAULT NULL::character varying,
    "VAR_FORMAT" character varying(100) DEFAULT NULL::character varying,
    "VAR_VALUE" text,
    "VAR_STATUS" character(1) DEFAULT '1'::bpchar,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying,
    "UPDATED_TIME" timestamp without time zone,
    "UPDATED_BY" character varying(50) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_system_variable OWNER TO postgres;

--
-- Name: td_tenant; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_tenant (
    "TENANT_ID" character varying(50) NOT NULL,
    "TENANT_NAME" character varying(100) NOT NULL,
    "DOMAIN" character varying(255) DEFAULT NULL::character varying,
    "STATUS" character varying(20) DEFAULT 'ACTIVE'::character varying,
    "CONFIG" text,
    "QUOTA" text,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying,
    "UPDATED_TIME" timestamp without time zone,
    "UPDATED_BY" character varying(50) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_tenant OWNER TO postgres;

--
-- Name: td_tenant_config; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_tenant_config (
    "CONFIG_ID" character varying(50) NOT NULL,
    "TENANT_ID" character varying(50) NOT NULL,
    "CONFIG_KEY" character varying(100) NOT NULL,
    "CONFIG_VALUE" text,
    "CONFIG_TYPE" character varying(50) DEFAULT NULL::character varying,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying,
    "UPDATED_TIME" timestamp without time zone,
    "UPDATED_BY" character varying(50) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_tenant_config OWNER TO postgres;

--
-- Name: td_user; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_user (
    "USER_ID" character varying(50) NOT NULL,
    "USERNAME" character varying(200) NOT NULL,
    "PASSWORD" character varying(255) NOT NULL,
    "REAL_NAME" character varying(100) DEFAULT NULL::character varying,
    "PINYIN" character varying(100) DEFAULT NULL::character varying,
    "SEX" character varying(100) DEFAULT NULL::character varying,
    "HOME_TEL" character varying(100) DEFAULT NULL::character varying,
    "WORK_TEL" character varying(100) DEFAULT NULL::character varying,
    "WORK_ADDRESS" character varying(100) DEFAULT NULL::character varying,
    "MOBILE_TEL1" character varying(100) DEFAULT NULL::character varying,
    "MOBILE_TEL2" character varying(100) DEFAULT NULL::character varying,
    "FAX" character varying(100) DEFAULT NULL::character varying,
    "OICQ" character varying(100) DEFAULT NULL::character varying,
    "BIRTHDAY" timestamp without time zone,
    "EMAIL" character varying(100) DEFAULT NULL::character varying,
    "ADDRESS" character varying(200) DEFAULT NULL::character varying,
    "POSTAL_CODE" character varying(10) DEFAULT NULL::character varying,
    "ID_CARD" character varying(50) DEFAULT NULL::character varying,
    "STATUS" character varying(20) DEFAULT 'ACTIVE'::character varying,
    "LOCKED" character(1) DEFAULT '0'::bpchar,
    "LOCK_TIME" timestamp without time zone,
    "REG_DATE" timestamp without time zone,
    "LOGIN_COUNT" integer DEFAULT 0,
    "USER_TYPE" character varying(100) DEFAULT NULL::character varying,
    "PAST_TIME" timestamp without time zone,
    "DREDGE_TIME" character varying(50) DEFAULT NULL::character varying,
    "LAST_LOGIN_TIME" timestamp without time zone,
    "LOGIN_IP" character varying(15) DEFAULT NULL::character varying,
    "WORK_LENGTH" character varying(50) DEFAULT NULL::character varying,
    "POLITICS" character varying(100) DEFAULT NULL::character varying,
    "CERT_SN" character varying(50) DEFAULT NULL::character varying,
    "USER_SN" integer DEFAULT 999,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying,
    "UPDATED_TIME" timestamp without time zone,
    "UPDATED_BY" character varying(50) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_user OWNER TO postgres;

--
-- Name: td_user_backup; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_user_backup (
    "USER_ID" character varying(50),
    "USERNAME" character varying(200),
    "PASSWORD" character varying(255),
    "REAL_NAME" character varying(100),
    "PINYIN" character varying(100),
    "SEX" character varying(100),
    "HOME_TEL" character varying(100),
    "WORK_TEL" character varying(100),
    "WORK_ADDRESS" character varying(100),
    "MOBILE_TEL1" character varying(100),
    "MOBILE_TEL2" character varying(100),
    "FAX" character varying(100),
    "OICQ" character varying(100),
    "BIRTHDAY" timestamp without time zone,
    "EMAIL" character varying(100),
    "ADDRESS" character varying(200),
    "POSTAL_CODE" character varying(10),
    "ID_CARD" character varying(50),
    "STATUS" character varying(20),
    "LOCKED" character(1),
    "LOCK_TIME" timestamp without time zone,
    "REG_DATE" timestamp without time zone,
    "LOGIN_COUNT" integer,
    "USER_TYPE" character varying(100),
    "PAST_TIME" timestamp without time zone,
    "DREDGE_TIME" character varying(50),
    "LAST_LOGIN_TIME" timestamp without time zone,
    "LOGIN_IP" character varying(15),
    "WORK_LENGTH" character varying(50),
    "POLITICS" character varying(100),
    "CERT_SN" character varying(50),
    "USER_SN" integer,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50),
    "UPDATED_TIME" timestamp without time zone,
    "UPDATED_BY" character varying(50)
);


ALTER TABLE ecos_control.td_user_backup OWNER TO postgres;

--
-- Name: td_user_organization; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_user_organization (
    "USER_ID" character varying(36) NOT NULL,
    "ORG_ID" character varying(36) NOT NULL,
    "IS_PRIMARY" character(1) DEFAULT '0'::bpchar,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(36) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_user_organization OWNER TO postgres;

--
-- Name: td_user_role; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.td_user_role (
    "USER_ID" character varying(50) NOT NULL,
    "ROLE_ID" character varying(50) NOT NULL,
    "ORG_ID" character varying(50) DEFAULT '-1'::character varying NOT NULL,
    "CREATED_TIME" timestamp without time zone,
    "CREATED_BY" character varying(50) DEFAULT NULL::character varying
);


ALTER TABLE ecos_control.td_user_role OWNER TO postgres;

--
-- Name: td_user_security_profile; Type: TABLE; Schema: ecos_control; Owner: postgres
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
    org_id character varying(64),
    scope_type character varying(16) DEFAULT 'USER'::character varying,
    tenant_id character varying(64)
);


ALTER TABLE ecos_control.td_user_security_profile OWNER TO postgres;

--
-- Name: TABLE td_user_security_profile; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON TABLE ecos_control.td_user_security_profile IS '用户级安全配置表 — 优先级最高，覆盖角色级和全局默认';


--
-- Name: COLUMN td_user_security_profile.user_id; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.td_user_security_profile.user_id IS '用户ID，关联 td_user.USER_ID';


--
-- Name: COLUMN td_user_security_profile.clearance_level; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.td_user_security_profile.clearance_level IS '准入等级 0-4: L0公开 L1内部 L2保密 L3机密 L4绝密';


--
-- Name: COLUMN td_user_security_profile.linked_workstation; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.td_user_security_profile.linked_workstation IS '物理工作站绑定（MAC/IP/主机名）';


--
-- Name: COLUMN td_user_security_profile.audit_mode; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.td_user_security_profile.audit_mode IS '审计力度: basic(基本) detailed(详细) full(全量)';


--
-- Name: COLUMN td_user_security_profile.sandbox_mandatory; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.td_user_security_profile.sandbox_mandatory IS '是否强制沙盒运行';


--
-- Name: COLUMN td_user_security_profile.is_default; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON COLUMN ecos_control.td_user_security_profile.is_default IS '是否为全局默认配置（仅允许一条记录为TRUE）';


--
-- Name: user_security_configs; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.user_security_configs (
    id character varying(64) NOT NULL,
    user_id character varying(64) NOT NULL,
    clearance_level integer DEFAULT 1,
    linked_workstation character varying(255) DEFAULT ''::character varying,
    audit_mode character varying(32) DEFAULT 'basic'::character varying,
    sandbox_mandatory boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_security_configs_audit_mode_check CHECK (((audit_mode)::text = ANY (ARRAY[('basic'::character varying)::text, ('detailed'::character varying)::text, ('comprehensive'::character varying)::text]))),
    CONSTRAINT user_security_configs_clearance_level_check CHECK (((clearance_level >= 1) AND (clearance_level <= 5)))
);


ALTER TABLE ecos_control.user_security_configs OWNER TO postgres;

--
-- Name: users; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control.users (
    id character varying(50) NOT NULL,
    username character varying(100) NOT NULL,
    password_hash character varying(200) NOT NULL,
    display_name character varying(100),
    roles text DEFAULT '["admin"]'::text,
    enabled boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT now(),
    failed_attempts integer DEFAULT 0,
    locked_until timestamp without time zone,
    password_change_required boolean DEFAULT true,
    last_password_change timestamp without time zone DEFAULT now(),
    password_history text DEFAULT '[]'::text
);


ALTER TABLE ecos_control.users OWNER TO postgres;

--
-- Name: v_legacy_ecos_business_scenario; Type: VIEW; Schema: ecos_control; Owner: postgres
--

CREATE VIEW ecos_control.v_legacy_ecos_business_scenario AS
 SELECT (id)::text AS id,
    name,
    description,
    business_goal,
    department,
    priority,
    status,
    budget AS budget_legacy_text,
    NULL::numeric(18,2) AS budget,
    NULL::character varying(8) AS budget_currency,
    safety_index_target,
    actual_safety_index,
    (metrics)::text AS metrics_json,
    create_time,
    update_time,
    create_by,
    update_by,
    is_deleted,
    NULL::character varying(20) AS version_no,
    'default'::text AS domain
   FROM ecos_control.ecos_business_scenario o;


ALTER VIEW ecos_control.v_legacy_ecos_business_scenario OWNER TO postgres;

--
-- Name: VIEW v_legacy_ecos_business_scenario; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON VIEW ecos_control.v_legacy_ecos_business_scenario IS '只读对账视图（V213）：旧场景主表→v2 列面；budget 历史串值不解析；仅供 E-7 对账，不接业务链路';


--
-- Name: v_legacy_ecos_decision_record; Type: VIEW; Schema: ecos_control; Owner: postgres
--

CREATE VIEW ecos_control.v_legacy_ecos_decision_record AS
 SELECT (id)::text AS id,
    (scenario_id)::text AS scenario_id,
    (mind_id)::text AS mind_id,
    (action_plan)::text AS action_plan_json,
    (source_refs)::text AS source_refs_json,
    (compliance_check)::text AS compliance_detail_json,
    NULL::character varying(16) AS compliance_verdict,
    proposed_by,
    accepted_by,
    accepted_at,
    NULL::character varying(36) AS owner_user_id,
    NULL::date AS due_date,
    NULL::character varying(36) AS approver_user_id,
    NULL::numeric(18,2) AS expected_impact,
    NULL::character varying(128) AS control_metric,
    NULL::character varying(36) AS forecast_run_id,
    NULL::character varying(16) AS run_mode,
    create_time,
    update_time,
    create_by,
    update_by,
    is_deleted,
    NULL::character varying(20) AS version_no,
    'default'::text AS domain
   FROM ecos_control.ecos_decision_record o;


ALTER VIEW ecos_control.v_legacy_ecos_decision_record OWNER TO postgres;

--
-- Name: VIEW v_legacy_ecos_decision_record; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON VIEW ecos_control.v_legacy_ecos_decision_record IS '只读对账视图（V213）：五必填 v2 实列旧面无源显式补 NULL（禁冒充映射）；不接业务链路';


--
-- Name: v_legacy_ecos_scenario_binding; Type: VIEW; Schema: ecos_control; Owner: postgres
--

CREATE VIEW ecos_control.v_legacy_ecos_scenario_binding AS
 SELECT (id)::text AS id,
    (scenario_id)::text AS scenario_id,
    binding_type,
        CASE target_type
            WHEN 'DATASOURCE'::text THEN 'DATASET'::character varying
            WHEN 'ONTOLOGY_ENTITY'::text THEN 'OBJECT_TYPE'::character varying
            WHEN 'KNOWLEDGE_ARTICLE'::text THEN 'KNOWLEDGE_BASE'::character varying
            WHEN 'AGENT_PROFILE'::text THEN 'AI_AGENT'::character varying
            WHEN 'INTERFACE_REF'::text THEN 'INTERFACE'::character varying
            ELSE target_type
        END AS target_type_mapped,
    target_ref,
    target_id,
    remark,
    0 AS is_island,
    NULL::character varying(64) AS island_reason,
    create_time,
    update_time,
    create_by,
    update_by,
    is_deleted,
    NULL::character varying(20) AS version_no,
    'default'::text AS domain
   FROM ecos_control.ecos_scenario_binding o;


ALTER VIEW ecos_control.v_legacy_ecos_scenario_binding OWNER TO postgres;

--
-- Name: VIEW v_legacy_ecos_scenario_binding; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON VIEW ecos_control.v_legacy_ecos_scenario_binding IS '只读对账视图（V213）：target_type 前端词表→后端六值映射（W169）；is_island 补 0；不接业务链路';


--
-- Name: v_legacy_ecos_scenario_binding_link; Type: VIEW; Schema: ecos_control; Owner: postgres
--

CREATE VIEW ecos_control.v_legacy_ecos_scenario_binding_link AS
 SELECT (id)::text AS id,
    (scenario_id)::text AS scenario_id,
    (source_binding_id)::text AS source_binding_id,
    (target_binding_id)::text AS target_binding_id,
    link_type,
    source_contract,
    remark,
    create_time,
    update_time,
    create_by,
    update_by,
    is_deleted,
    version_no,
    domain
   FROM ecos_control.ecos_scenario_binding_link o;


ALTER VIEW ecos_control.v_legacy_ecos_scenario_binding_link OWNER TO postgres;

--
-- Name: VIEW v_legacy_ecos_scenario_binding_link; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON VIEW ecos_control.v_legacy_ecos_scenario_binding_link IS '只读对账视图（V213）：边表列面对齐；placeholder 契约原样取证；不接业务链路';


--
-- Name: v_legacy_ecos_scenario_mind; Type: VIEW; Schema: ecos_control; Owner: postgres
--

CREATE VIEW ecos_control.v_legacy_ecos_scenario_mind AS
 SELECT (id)::text AS id,
    (scenario_id)::text AS scenario_id,
    mind_label,
    active_mind,
    (initial_belief_jsonb)::text AS initial_belief_json,
    (evidence_refs)::text AS evidence_refs_json,
    (hypothesis_refs)::text AS hypothesis_refs_json,
    (model_refs)::text AS model_refs_json,
    (cognitive_endpoints)::text AS cognitive_endpoints_json,
    initial_confidence,
        CASE
            WHEN (COALESCE((is_deleted)::integer, 0) = 0) THEN '1970-01-01 00:00:00'::timestamp without time zone
            ELSE update_time
        END AS deleted_guard,
    create_time,
    update_time,
    create_by,
    update_by,
    is_deleted,
    NULL::character varying(20) AS version_no,
    'default'::text AS domain
   FROM ecos_control.ecos_scenario_mind o;


ALTER VIEW ecos_control.v_legacy_ecos_scenario_mind OWNER TO postgres;

--
-- Name: VIEW v_legacy_ecos_scenario_mind; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON VIEW ecos_control.v_legacy_ecos_scenario_mind IS '只读对账视图（V213）：JSONB 文本化对齐 _json 名；cognitive_endpoints 仅取证（v2 已移出）；不接业务链路';


--
-- Name: v_legacy_ecos_scenario_run; Type: VIEW; Schema: ecos_control; Owner: postgres
--

CREATE VIEW ecos_control.v_legacy_ecos_scenario_run AS
 SELECT (id)::text AS id,
    (scenario_id)::text AS scenario_id,
    run_type,
    status,
    metric,
    deviation,
    (diagnosis_result)::text AS diagnosis_result_json,
    (forecast_result)::text AS forecast_result_json,
    (simulation_result)::text AS simulation_result_json,
    (strategy_result)::text AS strategy_result_json,
    (decision_id)::text AS decision_id,
    NULL::character varying(36) AS decision_record_id,
        CASE
            WHEN COALESCE(degraded, false) THEN 1
            ELSE 0
        END AS is_degraded,
    'FORMAL'::text AS run_mode,
    NULL::character varying(64) AS trace_id,
    create_time,
    update_time,
    create_by,
    update_by,
    is_deleted,
    NULL::character varying(20) AS version_no,
    'default'::text AS domain
   FROM ecos_control.ecos_scenario_run o;


ALTER VIEW ecos_control.v_legacy_ecos_scenario_run OWNER TO postgres;

--
-- Name: VIEW v_legacy_ecos_scenario_run; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON VIEW ecos_control.v_legacy_ecos_scenario_run IS '只读对账视图（V213）：degraded→is_degraded、decision_id 空引用取证、run_mode 补 FORMAL；不接业务链路';


--
-- Name: v_legacy_ecos_scenario_sandbox_layout; Type: VIEW; Schema: ecos_control; Owner: postgres
--

CREATE VIEW ecos_control.v_legacy_ecos_scenario_sandbox_layout AS
 SELECT (id)::text AS id,
    (scenario_id)::text AS scenario_id,
    (layout_jsonb)::text AS layout_json,
    layout_version,
    create_time,
    update_time,
    create_by,
    update_by,
    is_deleted,
    NULL::character varying(20) AS version_no,
    'default'::text AS domain
   FROM ecos_control.ecos_scenario_sandbox_layout o;


ALTER VIEW ecos_control.v_legacy_ecos_scenario_sandbox_layout OWNER TO postgres;

--
-- Name: VIEW v_legacy_ecos_scenario_sandbox_layout; Type: COMMENT; Schema: ecos_control; Owner: postgres
--

COMMENT ON VIEW ecos_control.v_legacy_ecos_scenario_sandbox_layout IS '只读对账视图（V213）：layout_jsonb→layout_json 对齐；不接业务链路';


--
-- Name: 员工; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control."员工" (
    id integer NOT NULL,
    "姓名" character varying(50) NOT NULL,
    "手机号" character varying(20),
    "邮箱" character varying(100),
    "部门" character varying(50),
    "入职日期" date,
    "薪资" numeric(12,2)
);


ALTER TABLE ecos_control."员工" OWNER TO postgres;

--
-- Name: 员工_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control."员工_id_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control."员工_id_seq" OWNER TO postgres;

--
-- Name: 员工_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control."员工_id_seq" OWNED BY ecos_control."员工".id;


--
-- Name: 数据目录; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control."数据目录" (
    id integer NOT NULL,
    "数据集名称" character varying(200) NOT NULL,
    "数据源类型" character varying(50) NOT NULL,
    "表名" character varying(100),
    "行数" integer DEFAULT 0,
    "列数" integer DEFAULT 0,
    "负责人" character varying(50),
    "最后更新" timestamp without time zone DEFAULT now(),
    "描述" text
);


ALTER TABLE ecos_control."数据目录" OWNER TO postgres;

--
-- Name: 数据目录_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control."数据目录_id_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control."数据目录_id_seq" OWNER TO postgres;

--
-- Name: 数据目录_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control."数据目录_id_seq" OWNED BY ecos_control."数据目录".id;


--
-- Name: 经营数据; Type: TABLE; Schema: ecos_control; Owner: postgres
--

CREATE TABLE ecos_control."经营数据" (
    id integer NOT NULL,
    "月份" character varying(10) NOT NULL,
    "营收" numeric(15,2) DEFAULT 0 NOT NULL,
    "成本" numeric(15,2) DEFAULT 0 NOT NULL,
    "利润" numeric(15,2) DEFAULT 0,
    "订单数" integer DEFAULT 0 NOT NULL,
    "交付率" numeric(5,2),
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_control."经营数据" OWNER TO postgres;

--
-- Name: 经营数据_id_seq; Type: SEQUENCE; Schema: ecos_control; Owner: postgres
--

CREATE SEQUENCE ecos_control."经营数据_id_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_control."经营数据_id_seq" OWNER TO postgres;

--
-- Name: 经营数据_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_control; Owner: postgres
--

ALTER SEQUENCE ecos_control."经营数据_id_seq" OWNED BY ecos_control."经营数据".id;


--
-- Name: ecos_cognitive_rule; Type: TABLE; Schema: ecos_data; Owner: postgres
--

CREATE TABLE ecos_data.ecos_cognitive_rule (
    id bigint NOT NULL,
    rule_name character varying(128) NOT NULL,
    rule_type character varying(32),
    condition_expr text,
    action_config jsonb,
    priority integer DEFAULT 0,
    enabled boolean DEFAULT true,
    description text,
    tenant_id character varying(64),
    created_by character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_data.ecos_cognitive_rule OWNER TO postgres;

--
-- Name: TABLE ecos_cognitive_rule; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.ecos_cognitive_rule IS '认知规则表';


--
-- Name: ecos_cognitive_rule_id_seq; Type: SEQUENCE; Schema: ecos_data; Owner: postgres
--

CREATE SEQUENCE ecos_data.ecos_cognitive_rule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_data.ecos_cognitive_rule_id_seq OWNER TO postgres;

--
-- Name: ecos_cognitive_rule_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_data; Owner: postgres
--

ALTER SEQUENCE ecos_data.ecos_cognitive_rule_id_seq OWNED BY ecos_data.ecos_cognitive_rule.id;


--
-- Name: ecos_data_asset; Type: TABLE; Schema: ecos_data; Owner: postgres
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


ALTER TABLE ecos_data.ecos_data_asset OWNER TO postgres;

--
-- Name: TABLE ecos_data_asset; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.ecos_data_asset IS '数据资产（业务视图）— PMO-data10 资产 CRUD / 分级 / 分类主表';


--
-- Name: ecos_data_asset_field; Type: TABLE; Schema: ecos_data; Owner: postgres
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


ALTER TABLE ecos_data.ecos_data_asset_field OWNER TO postgres;

--
-- Name: TABLE ecos_data_asset_field; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.ecos_data_asset_field IS '资产-字段级敏感度 — 参考 td_data_field；mask_strategy: none/middle4/prefix3/suffix4/full';


--
-- Name: ecos_data_category_tree; Type: TABLE; Schema: ecos_data; Owner: postgres
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


ALTER TABLE ecos_data.ecos_data_category_tree OWNER TO postgres;

--
-- Name: TABLE ecos_data_category_tree; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.ecos_data_category_tree IS '业务分类树（最多 3 级，资产/目录主用）';


--
-- Name: ecos_data_level_def; Type: TABLE; Schema: ecos_data; Owner: postgres
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


ALTER TABLE ecos_data.ecos_data_level_def OWNER TO postgres;

--
-- Name: TABLE ecos_data_level_def; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.ecos_data_level_def IS '数据分级字典（L1..L4）— data-engine 唯一权威（替代已 deprecated 的 DataClassificationServiceImpl 内存 4 级）';


--
-- Name: ecos_data_lineage_edge; Type: TABLE; Schema: ecos_data; Owner: postgres
--

CREATE TABLE ecos_data.ecos_data_lineage_edge (
    id character varying(64) NOT NULL,
    source_node_id character varying(64) NOT NULL,
    target_node_id character varying(64) NOT NULL,
    edge_type character varying(30) NOT NULL,
    pipeline_task_id character varying(64),
    transformation character varying(500),
    properties jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_data.ecos_data_lineage_edge OWNER TO postgres;

--
-- Name: TABLE ecos_data_lineage_edge; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.ecos_data_lineage_edge IS 'Data lineage edge 鈥?data flow between nodes';


--
-- Name: COLUMN ecos_data_lineage_edge.edge_type; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON COLUMN ecos_data.ecos_data_lineage_edge.edge_type IS 'DATA_FLOW / DERIVATION / DEPENDENCY';


--
-- Name: COLUMN ecos_data_lineage_edge.pipeline_task_id; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON COLUMN ecos_data.ecos_data_lineage_edge.pipeline_task_id IS 'Associated pipeline task that creates this data flow';


--
-- Name: ecos_data_lineage_node; Type: TABLE; Schema: ecos_data; Owner: postgres
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
    pipeline_task_id character varying(64)
);


ALTER TABLE ecos_data.ecos_data_lineage_node OWNER TO postgres;

--
-- Name: TABLE ecos_data_lineage_node; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.ecos_data_lineage_node IS 'Data lineage node 鈥?represents a data source, target, or transform point';


--
-- Name: COLUMN ecos_data_lineage_node.node_type; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON COLUMN ecos_data.ecos_data_lineage_node.node_type IS 'SOURCE / TARGET / TRANSFORM';


--
-- Name: COLUMN ecos_data_lineage_node.layer; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON COLUMN ecos_data.ecos_data_lineage_node.layer IS 'SOURCE / RAW / CURATED / SEMANTIC / APPLICATION';


--
-- Name: COLUMN ecos_data_lineage_node.pipeline_task_id; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON COLUMN ecos_data.ecos_data_lineage_node.pipeline_task_id IS '来源 pipeline definition/task ID';


--
-- Name: ecos_dq_execution_result; Type: TABLE; Schema: ecos_data; Owner: postgres
--

CREATE TABLE ecos_data.ecos_dq_execution_result (
    id character varying(64) NOT NULL,
    rule_id character varying(64),
    passed boolean DEFAULT false,
    total_rows integer DEFAULT 0,
    failed_rows integer DEFAULT 0,
    error_details text,
    executed_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_data.ecos_dq_execution_result OWNER TO postgres;

--
-- Name: TABLE ecos_dq_execution_result; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.ecos_dq_execution_result IS '数据质量执行结果表';


--
-- Name: ecos_dq_issue; Type: TABLE; Schema: ecos_data; Owner: postgres
--

CREATE TABLE ecos_data.ecos_dq_issue (
    id bigint NOT NULL,
    rule_id bigint NOT NULL,
    asset_id character varying(255) DEFAULT ''::character varying,
    description text,
    status character varying(32) DEFAULT 'open'::character varying,
    severity character varying(32) DEFAULT 'HIGH'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    resolved_at timestamp without time zone
);


ALTER TABLE ecos_data.ecos_dq_issue OWNER TO postgres;

--
-- Name: TABLE ecos_dq_issue; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.ecos_dq_issue IS '数据质量问题表';


--
-- Name: ecos_dq_issue_id_seq; Type: SEQUENCE; Schema: ecos_data; Owner: postgres
--

CREATE SEQUENCE ecos_data.ecos_dq_issue_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_data.ecos_dq_issue_id_seq OWNER TO postgres;

--
-- Name: ecos_dq_issue_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_data; Owner: postgres
--

ALTER SEQUENCE ecos_data.ecos_dq_issue_id_seq OWNED BY ecos_data.ecos_dq_issue.id;


--
-- Name: ecos_dq_rule; Type: TABLE; Schema: ecos_data; Owner: postgres
--

CREATE TABLE ecos_data.ecos_dq_rule (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    rule_type character varying(64) DEFAULT 'NOT_NULL'::character varying,
    config_json text,
    severity character varying(32) DEFAULT 'HIGH'::character varying,
    enabled boolean DEFAULT true,
    target_entity character varying(128),
    target_field character varying(128),
    rule_expression text,
    code character varying(64),
    status character varying(32) DEFAULT 'ACTIVE'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_data.ecos_dq_rule OWNER TO postgres;

--
-- Name: TABLE ecos_dq_rule; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.ecos_dq_rule IS '数据质量规则表';


--
-- Name: ecos_dq_rule_id_seq; Type: SEQUENCE; Schema: ecos_data; Owner: postgres
--

CREATE SEQUENCE ecos_data.ecos_dq_rule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_data.ecos_dq_rule_id_seq OWNER TO postgres;

--
-- Name: ecos_dq_rule_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_data; Owner: postgres
--

ALTER SEQUENCE ecos_data.ecos_dq_rule_id_seq OWNED BY ecos_data.ecos_dq_rule.id;


--
-- Name: ecos_pipeline_definition; Type: TABLE; Schema: ecos_data; Owner: postgres
--

CREATE TABLE ecos_data.ecos_pipeline_definition (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    definition jsonb DEFAULT '{}'::jsonb,
    status character varying(32) DEFAULT 'DRAFT'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_data.ecos_pipeline_definition OWNER TO postgres;

--
-- Name: TABLE ecos_pipeline_definition; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.ecos_pipeline_definition IS '管道定义表';


--
-- Name: ecos_pipeline_edge; Type: TABLE; Schema: ecos_data; Owner: postgres
--

CREATE TABLE ecos_data.ecos_pipeline_edge (
    id character varying(64) NOT NULL,
    definition_id character varying(64) NOT NULL,
    from_node_id character varying(64) NOT NULL,
    to_node_id character varying(64) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_data.ecos_pipeline_edge OWNER TO postgres;

--
-- Name: TABLE ecos_pipeline_edge; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.ecos_pipeline_edge IS '管道边表';


--
-- Name: ecos_pipeline_execution; Type: TABLE; Schema: ecos_data; Owner: postgres
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
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_data.ecos_pipeline_execution OWNER TO postgres;

--
-- Name: TABLE ecos_pipeline_execution; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.ecos_pipeline_execution IS '管道执行表';


--
-- Name: ecos_pipeline_node; Type: TABLE; Schema: ecos_data; Owner: postgres
--

CREATE TABLE ecos_data.ecos_pipeline_node (
    id character varying(64) NOT NULL,
    definition_id character varying(64) NOT NULL,
    node_id character varying(64) NOT NULL,
    type character varying(64) DEFAULT 'TRANSFORM_SQL'::character varying,
    config jsonb DEFAULT '{}'::jsonb,
    depends_on jsonb DEFAULT '[]'::jsonb,
    position_x integer DEFAULT 0,
    position_y integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_data.ecos_pipeline_node OWNER TO postgres;

--
-- Name: TABLE ecos_pipeline_node; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.ecos_pipeline_node IS '管道节点表';


--
-- Name: ecos_query_history; Type: TABLE; Schema: ecos_data; Owner: postgres
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


ALTER TABLE ecos_data.ecos_query_history OWNER TO postgres;

--
-- Name: TABLE ecos_query_history; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.ecos_query_history IS 'SQL query execution history';


--
-- Name: COLUMN ecos_query_history.status; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON COLUMN ecos_data.ecos_query_history.status IS 'RUNNING / COMPLETED / FAILED / CANCELLED';


--
-- Name: ecos_query_template; Type: TABLE; Schema: ecos_data; Owner: postgres
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


ALTER TABLE ecos_data.ecos_query_template OWNER TO postgres;

--
-- Name: TABLE ecos_query_template; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.ecos_query_template IS 'Saved SQL query templates';


--
-- Name: ecos_task; Type: TABLE; Schema: ecos_data; Owner: postgres
--

CREATE TABLE ecos_data.ecos_task (
    id bigint NOT NULL,
    task_name character varying(256) NOT NULL,
    task_type character varying(32),
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
    tenant_id character varying(64),
    created_by character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_data.ecos_task OWNER TO postgres;

--
-- Name: TABLE ecos_task; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.ecos_task IS '任务表';


--
-- Name: ecos_task_id_seq; Type: SEQUENCE; Schema: ecos_data; Owner: postgres
--

CREATE SEQUENCE ecos_data.ecos_task_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_data.ecos_task_id_seq OWNER TO postgres;

--
-- Name: ecos_task_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_data; Owner: postgres
--

ALTER SEQUENCE ecos_data.ecos_task_id_seq OWNED BY ecos_data.ecos_task.id;


--
-- Name: source; Type: TABLE; Schema: ecos_data; Owner: postgres
--

CREATE TABLE ecos_data.source (
    id character varying(64) NOT NULL,
    name character varying(256),
    source_type character varying(32),
    url text,
    status character varying(32),
    tenant_id character varying(64),
    detail jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    is_deleted integer DEFAULT 0
);


ALTER TABLE ecos_data.source OWNER TO postgres;

--
-- Name: td_catalog_item; Type: TABLE; Schema: ecos_data; Owner: postgres
--

CREATE TABLE ecos_data.td_catalog_item (
    catalog_id character varying(64) NOT NULL,
    resource_id character varying(64),
    resource_name character varying(256),
    resource_type character varying(32) DEFAULT 'TABLE'::character varying,
    org_name character varying(128),
    description text,
    tags character varying(256),
    category_path character varying(256),
    access_type character varying(32) DEFAULT 'READ'::character varying,
    data_format character varying(32),
    field_count integer DEFAULT 0,
    record_count bigint DEFAULT 0,
    last_updated timestamp without time zone,
    status character varying(16) DEFAULT 'ACTIVE'::character varying,
    tenant_id character varying(64)
);


ALTER TABLE ecos_data.td_catalog_item OWNER TO postgres;

--
-- Name: TABLE td_catalog_item; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.td_catalog_item IS '数据目录项表';


--
-- Name: td_data_category; Type: TABLE; Schema: ecos_data; Owner: postgres
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


ALTER TABLE ecos_data.td_data_category OWNER TO postgres;

--
-- Name: TABLE td_data_category; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.td_data_category IS 'Data category tree for catalog classification';


--
-- Name: COLUMN td_data_category.status; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON COLUMN ecos_data.td_data_category.status IS 'ACTIVE / INACTIVE';


--
-- Name: td_data_field; Type: TABLE; Schema: ecos_data; Owner: postgres
--

CREATE TABLE ecos_data.td_data_field (
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


ALTER TABLE ecos_data.td_data_field OWNER TO postgres;

--
-- Name: TABLE td_data_field; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.td_data_field IS '数据字段表';


--
-- Name: td_data_resource; Type: TABLE; Schema: ecos_data; Owner: postgres
--

CREATE TABLE ecos_data.td_data_resource (
    resource_id character varying(64) NOT NULL,
    resource_name character varying(256) NOT NULL,
    resource_type character varying(32) DEFAULT 'TABLE'::character varying,
    org_id character varying(64),
    org_name character varying(128),
    datasource_id character varying(64),
    source_path character varying(512),
    description text,
    tags character varying(256),
    status character varying(16) DEFAULT 'ACTIVE'::character varying,
    field_count integer DEFAULT 0,
    record_count bigint DEFAULT 0,
    last_sync_time timestamp without time zone,
    tenant_id character varying(64),
    create_by character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_by character varying(64),
    update_time timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_data.td_data_resource OWNER TO postgres;

--
-- Name: TABLE td_data_resource; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.td_data_resource IS '数据资源表';


--
-- Name: td_datasource; Type: TABLE; Schema: ecos_data; Owner: postgres
--

CREATE TABLE ecos_data.td_datasource (
    datasource_id character varying(64) NOT NULL,
    datasource_name character varying(128) NOT NULL,
    datasource_type character varying(32) DEFAULT 'JDBC'::character varying,
    org_id character varying(64),
    node_id character varying(64),
    description text,
    connection_config text,
    status character varying(16) DEFAULT 'ACTIVE'::character varying,
    tags character varying(256),
    last_test_time timestamp without time zone,
    last_test_result boolean,
    last_test_message text,
    tenant_id character varying(64),
    create_by character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_by character varying(64),
    update_time timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_data.td_datasource OWNER TO postgres;

--
-- Name: TABLE td_datasource; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON TABLE ecos_data.td_datasource IS '数据源表';


--
-- Name: COLUMN td_datasource.datasource_type; Type: COMMENT; Schema: ecos_data; Owner: postgres
--

COMMENT ON COLUMN ecos_data.td_datasource.datasource_type IS '类型: JDBC/API/FILE/MQ';


--
-- Name: dq_alert_record; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.dq_alert_record (
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


ALTER TABLE ecos_dq.dq_alert_record OWNER TO postgres;

--
-- Name: TABLE dq_alert_record; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON TABLE ecos_dq.dq_alert_record IS '告警事件记录 (P0-P3 分级, 升级链路)';


--
-- Name: COLUMN dq_alert_record.status; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_alert_record.status IS '告警状态: PENDING / NOTIFIED / ACKED / RESOLVED / IGNORED / ESCALATED';


--
-- Name: COLUMN dq_alert_record.escalated_to; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_alert_record.escalated_to IS '升级到的告警级别 (如 P1)';


--
-- Name: dq_knowledge_entry; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.dq_knowledge_entry (
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


ALTER TABLE ecos_dq.dq_knowledge_entry OWNER TO postgres;

--
-- Name: TABLE dq_knowledge_entry; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON TABLE ecos_dq.dq_knowledge_entry IS 'DQ 知识条目（PMO-48-D T16，规则/告警/工单沉淀，供 RCA + RAG 相似检索）';


--
-- Name: COLUMN dq_knowledge_entry.id; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.id IS '知识条目 ID (VARCHAR(36))';


--
-- Name: COLUMN dq_knowledge_entry.source_type; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.source_type IS '来源类型: RULE/ALERT/WORK_ORDER';


--
-- Name: COLUMN dq_knowledge_entry.source_id; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.source_id IS '来源记录 ID（对应 dq_rule.id / dq_alert_record.id / dq_work_order.id）';


--
-- Name: COLUMN dq_knowledge_entry.category; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.category IS '分类: rule-hit/alert-pattern/fix-pattern';


--
-- Name: COLUMN dq_knowledge_entry.title; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.title IS '知识条目标题 (<= 191)';


--
-- Name: COLUMN dq_knowledge_entry.summary; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.summary IS '摘要（供列表展示）';


--
-- Name: COLUMN dq_knowledge_entry.content_md; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.content_md IS '知识全文 Markdown';


--
-- Name: COLUMN dq_knowledge_entry.entity_json; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.entity_json IS '结构化实体 JSONB (ruleName/assetId/ruleType 等)';


--
-- Name: COLUMN dq_knowledge_entry.embedding; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.embedding IS '向量列（pgvector 未装，本版本降级 TEXT 存 JSON 数组字符串 DIM=1024）';


--
-- Name: COLUMN dq_knowledge_entry.score_hint; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.score_hint IS '预计算相似分参考 (0.0-1.0)';


--
-- Name: dq_report; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.dq_report (
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


ALTER TABLE ecos_dq.dq_report OWNER TO postgres;

--
-- Name: TABLE dq_report; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON TABLE ecos_dq.dq_report IS 'DQ 报告（PMO-48-D T15，日报/周报/月报，HTML+PDF，AI 摘要）';


--
-- Name: COLUMN dq_report.id; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_report.id IS '报告 ID (VARCHAR(36) 主键)';


--
-- Name: COLUMN dq_report.report_type; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_report.report_type IS '报告类型: DAILY/WEEKLY/MONTHLY';


--
-- Name: COLUMN dq_report.scope; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_report.scope IS '报告范围: ALL/NATIVE/OFI/DOMAIN:xxx';


--
-- Name: COLUMN dq_report.start_date; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_report.start_date IS '报告起始日期（含）';


--
-- Name: COLUMN dq_report.end_date; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_report.end_date IS '报告截止日期（含）';


--
-- Name: COLUMN dq_report.payload_html; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_report.payload_html IS '内嵌 HTML（小文件 <= 200KB，直接存 TEXT）';


--
-- Name: COLUMN dq_report.pdf_object_key; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_report.pdf_object_key IS 'MinIO bucket=dq-reports 内 object key（PDF > 200KB 走对象存储）';


--
-- Name: COLUMN dq_report.llm_summary; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_report.llm_summary IS 'LLM 生成摘要（PMO-48-D Phase 5 接 cognitive-engine 后非空）';


--
-- Name: COLUMN dq_report.row_count; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_report.row_count IS '报告覆盖的数据行数';


--
-- Name: COLUMN dq_report.alert_count; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_report.alert_count IS '报告覆盖的告警位数';


--
-- Name: COLUMN dq_report.avg_score; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_report.avg_score IS '报告覆盖范围内平均分 0.0-1.0';


--
-- Name: dq_rule; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.dq_rule (
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
    applied_at timestamp without time zone
);


ALTER TABLE ecos_dq.dq_rule OWNER TO postgres;

--
-- Name: TABLE dq_rule; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON TABLE ecos_dq.dq_rule IS 'DQ 规则主表 (双轨合并后的统一规则表, PMO-48-A T1 + PMO-48-B V112)';


--
-- Name: COLUMN dq_rule.category; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_rule.category IS '规则类别: BUSINESS / TECHNICAL / COMPLIANCE';


--
-- Name: COLUMN dq_rule.status; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_rule.status IS '规则状态: DRAFT / IN_REVIEW / ACTIVE / DEPRECATED / SUPERSEDED / REJECTED';


--
-- Name: COLUMN dq_rule.source_type; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_rule.source_type IS '规则来源: MANUAL / LEGACY_V2 / LEGACY_QUALITY / KB_SYNC';


--
-- Name: COLUMN dq_rule.source_ref; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_rule.source_ref IS '来源追溯 (legacy 表:原始行id)';


--
-- Name: COLUMN dq_rule.applied_at; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_rule.applied_at IS '正式生效时间 (approve → ACTIVE 切换时设置, 应用方使用)';


--
-- Name: dq_rule_check; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.dq_rule_check (
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


ALTER TABLE ecos_dq.dq_rule_check OWNER TO postgres;

--
-- Name: TABLE dq_rule_check; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON TABLE ecos_dq.dq_rule_check IS '规则执行记录 (调度/事件/手动触发)';


--
-- Name: COLUMN dq_rule_check.trigger_type; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_rule_check.trigger_type IS '触发类型: SCHEDULE / EVENT / MANUAL';


--
-- Name: dq_rule_version; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.dq_rule_version (
    id character varying(64) NOT NULL,
    rule_id character varying(64) NOT NULL,
    version_number integer NOT NULL,
    snapshot jsonb NOT NULL,
    changed_by character varying(128),
    changed_at timestamp without time zone DEFAULT now() NOT NULL,
    change_note text
);


ALTER TABLE ecos_dq.dq_rule_version OWNER TO postgres;

--
-- Name: TABLE dq_rule_version; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON TABLE ecos_dq.dq_rule_version IS '规则版本快照 (每次修改存档, 审计追溯)';


--
-- Name: dq_schedule; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.dq_schedule (
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


ALTER TABLE ecos_dq.dq_schedule OWNER TO postgres;

--
-- Name: TABLE dq_schedule; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON TABLE ecos_dq.dq_schedule IS 'DQ 监控调度计划（PMO-48-C T11，接 runtime-task）';


--
-- Name: COLUMN dq_schedule.id; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_schedule.id IS '调度 ID (VARCHAR(64) PK)';


--
-- Name: COLUMN dq_schedule.trigger_type; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_schedule.trigger_type IS '触发类型: SCHEDULE=定时 / EVENT=Pipeline事件 / MANUAL=仅手动';


--
-- Name: COLUMN dq_schedule.cron_expression; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_schedule.cron_expression IS 'cron 表达式 (trigger_type=SCHEDULE 时必填)';


--
-- Name: COLUMN dq_schedule.event_type; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_schedule.event_type IS '事件类型常量 (trigger_type=EVENT 时必填, 如 PIPELINE_EXECUTION_SUCCEEDED)';


--
-- Name: COLUMN dq_schedule.rule_ids; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_schedule.rule_ids IS '关联规则 ID 列表 JSONB ["ruleId1","ruleId2"]';


--
-- Name: COLUMN dq_schedule.scope_type; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_schedule.scope_type IS '限流作用域类型: FIELD/TABLE/DATASOURCE/DOMAIN/SYSTEM (缺省 DATASOURCE)';


--
-- Name: COLUMN dq_schedule.scope_id; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_schedule.scope_id IS '限流作用域 ID (与 dq_throttle.scope_id 对齐)';


--
-- Name: COLUMN dq_schedule.enabled; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_schedule.enabled IS '启用开关 (FALSE 时调度器/EVENT 监听跳过)';


--
-- Name: COLUMN dq_schedule.max_runtime_seconds; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_schedule.max_runtime_seconds IS '单次执行超时秒数 (超时记 TIMEOUT 不中断)';


--
-- Name: dq_score_snapshot; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.dq_score_snapshot (
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


ALTER TABLE ecos_dq.dq_score_snapshot OWNER TO postgres;

--
-- Name: TABLE dq_score_snapshot; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON TABLE ecos_dq.dq_score_snapshot IS 'DQ 6 维度评分快照 (PMO-48-B T8 评分引擎写入)';


--
-- Name: COLUMN dq_score_snapshot.dimension; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_score_snapshot.dimension IS '评估维度: ACCURACY/CONSISTENCY/FRESHNESS/UNIQUENESS/VALIDITY/COMPLETENESS';


--
-- Name: COLUMN dq_score_snapshot.scope_type; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_score_snapshot.scope_type IS '范围类型: FIELD/TABLE/DATASOURCE/DOMAIN/SYSTEM';


--
-- Name: COLUMN dq_score_snapshot.scope_id; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_score_snapshot.scope_id IS '范围实体 id (与 scope_type 匹配, TABLE 时为 datasource:table, FIELD 时为 datasource:table:field)';


--
-- Name: COLUMN dq_score_snapshot.score_value; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_score_snapshot.score_value IS '该次评估的维度分, 0.0 - 1.0 规范化 (PASS_RATE)';


--
-- Name: COLUMN dq_score_snapshot.weight; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_score_snapshot.weight IS '该维度在资产级汇总中的权重 (T8 按目标设置, 默认 1.0)';


--
-- Name: COLUMN dq_score_snapshot.sample_size; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_score_snapshot.sample_size IS '本次评估使用的样本量 (T8 用于诊断小样本偏差)';


--
-- Name: COLUMN dq_score_snapshot.distinct_rule_count; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_score_snapshot.distinct_rule_count IS '本次评估命中的规则数 (去重统计)';


--
-- Name: COLUMN dq_score_snapshot.metadata; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_score_snapshot.metadata IS '评估上下文 (sampleStats / threshold / relatedAlertId 等)';


--
-- Name: dq_score_snapshot_id_seq; Type: SEQUENCE; Schema: ecos_dq; Owner: postgres
--

CREATE SEQUENCE ecos_dq.dq_score_snapshot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_dq.dq_score_snapshot_id_seq OWNER TO postgres;

--
-- Name: dq_score_snapshot_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_dq; Owner: postgres
--

ALTER SEQUENCE ecos_dq.dq_score_snapshot_id_seq OWNED BY ecos_dq.dq_score_snapshot.id;


--
-- Name: dq_throttle; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.dq_throttle (
    id character varying(64) NOT NULL,
    scope_type character varying(16) NOT NULL,
    scope_id character varying(64) NOT NULL,
    max_checks_per_day integer DEFAULT 100 NOT NULL,
    current_count integer DEFAULT 0 NOT NULL,
    reset_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_dq.dq_throttle OWNER TO postgres;

--
-- Name: TABLE dq_throttle; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON TABLE ecos_dq.dq_throttle IS 'DQ 监控限流（每资产/天最大检查数，防雪崩, PMO-48-C T11）';


--
-- Name: COLUMN dq_throttle.max_checks_per_day; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_throttle.max_checks_per_day IS '每日检查上限 (超过则 runRuleBatch 跳过并记 SKIP 日志)';


--
-- Name: COLUMN dq_throttle.current_count; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_throttle.current_count IS '当日内已执行检查数 (跨日自动重置)';


--
-- Name: COLUMN dq_throttle.reset_at; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_throttle.reset_at IS 'current_count 重置时点 (跨日判定)';


--
-- Name: dq_work_order; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.dq_work_order (
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


ALTER TABLE ecos_dq.dq_work_order OWNER TO postgres;

--
-- Name: TABLE dq_work_order; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON TABLE ecos_dq.dq_work_order IS '工单 (告警→处理→验证→关闭, 溯源 RCA)';


--
-- Name: COLUMN dq_work_order.status; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_work_order.status IS '工单状态: PENDING / ASSIGNED / IN_WORK / RESOLVED / VERIFIED / CLOSED / REJECTED';


--
-- Name: COLUMN dq_work_order.handling_mode; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_work_order.handling_mode IS '处理模式: MANUAL / AUTO_REPAIR / EXEMPT';


--
-- Name: COLUMN dq_work_order.reject_note; Type: COMMENT; Schema: ecos_dq; Owner: postgres
--

COMMENT ON COLUMN ecos_dq.dq_work_order.reject_note IS '驳回原因（PMO-48-D T17，Phase 3 原文拼入 description，Phase 4 独立列）';


--
-- Name: ecos_pipeline_function; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.ecos_pipeline_function (
    id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    category character varying(50) NOT NULL,
    signature text NOT NULL,
    return_type character varying(50),
    description text,
    example text,
    is_builtin boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_dq.ecos_pipeline_function OWNER TO postgres;

--
-- Name: ecos_pipeline_run; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.ecos_pipeline_run (
    id character varying(36) NOT NULL,
    task_id character varying(36) NOT NULL,
    status character varying(20) DEFAULT 'QUEUED'::character varying,
    triggered_by character varying(50) DEFAULT 'manual'::character varying,
    total_steps integer DEFAULT 0,
    completed_steps integer DEFAULT 0,
    started_at timestamp without time zone,
    finished_at timestamp without time zone,
    elapsed_ms integer DEFAULT 0,
    error_msg text,
    log_json jsonb DEFAULT '[]'::jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_dq.ecos_pipeline_run OWNER TO postgres;

--
-- Name: ecos_pipeline_step; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.ecos_pipeline_step (
    id character varying(36) NOT NULL,
    task_id character varying(36) NOT NULL,
    step_order integer NOT NULL,
    node_id character varying(100) NOT NULL,
    node_type character varying(50) NOT NULL,
    config_json jsonb DEFAULT '{}'::jsonb,
    depends_on jsonb DEFAULT '[]'::jsonb,
    position_x double precision DEFAULT 0,
    position_y double precision DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_dq.ecos_pipeline_step OWNER TO postgres;

--
-- Name: ecos_pipeline_step_run; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.ecos_pipeline_step_run (
    id character varying(36) NOT NULL,
    run_id character varying(36) NOT NULL,
    step_id character varying(36) NOT NULL,
    node_id character varying(100) NOT NULL,
    status character varying(20) DEFAULT 'QUEUED'::character varying,
    rows_input integer DEFAULT 0,
    rows_output integer DEFAULT 0,
    started_at timestamp without time zone,
    finished_at timestamp without time zone,
    elapsed_ms integer DEFAULT 0,
    error_msg text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_dq.ecos_pipeline_step_run OWNER TO postgres;

--
-- Name: ecos_pipeline_task; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.ecos_pipeline_task (
    id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    yaml_content text NOT NULL,
    git_url character varying(500),
    git_branch character varying(100) DEFAULT 'main'::character varying,
    git_commit_id character varying(40),
    status character varying(20) DEFAULT 'DRAFT'::character varying,
    cron_expression character varying(100),
    config_json jsonb DEFAULT '{}'::jsonb,
    enabled boolean DEFAULT true,
    created_by character varying(100),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    task_type character varying(20) DEFAULT 'TRANSFORM'::character varying
);


ALTER TABLE ecos_dq.ecos_pipeline_task OWNER TO postgres;

--
-- Name: ecos_pipeline_udf; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.ecos_pipeline_udf (
    id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    category character varying(50),
    language character varying(20) DEFAULT 'python'::character varying,
    signature text,
    source_code text NOT NULL,
    compiled_path character varying(500),
    version integer DEFAULT 1,
    author character varying(100),
    is_shared boolean DEFAULT false,
    description text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_dq.ecos_pipeline_udf OWNER TO postgres;

--
-- Name: ecos_quality_evaluation; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.ecos_quality_evaluation (
    id character varying(36) NOT NULL,
    dataset_id character varying(100),
    rule_id character varying(64),
    passed boolean,
    total_rows bigint,
    failed_rows bigint,
    pass_rate double precision,
    sample_size integer,
    sample_failures jsonb,
    severity character varying(10),
    message text,
    evaluated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_dq.ecos_quality_evaluation OWNER TO postgres;

--
-- Name: ecos_quality_rule; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.ecos_quality_rule (
    rule_id character varying(64) NOT NULL,
    rule_name character varying(200) NOT NULL,
    rule_type character varying(30) NOT NULL,
    target character varying(200) NOT NULL,
    dataset_id character varying(100),
    parameters jsonb,
    severity character varying(10) DEFAULT 'WARN'::character varying,
    enabled boolean DEFAULT true,
    description text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE ecos_dq.ecos_quality_rule OWNER TO postgres;

--
-- Name: schema_changes; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.schema_changes (
    id bigint NOT NULL,
    datasource_id character varying(128) NOT NULL,
    table_name character varying(256) NOT NULL,
    change_type character varying(32) NOT NULL,
    detail_json text,
    detected_at timestamp without time zone DEFAULT now() NOT NULL,
    acknowledged boolean DEFAULT false NOT NULL
);


ALTER TABLE ecos_dq.schema_changes OWNER TO postgres;

--
-- Name: schema_changes_id_seq; Type: SEQUENCE; Schema: ecos_dq; Owner: postgres
--

CREATE SEQUENCE ecos_dq.schema_changes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_dq.schema_changes_id_seq OWNER TO postgres;

--
-- Name: schema_changes_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_dq; Owner: postgres
--

ALTER SEQUENCE ecos_dq.schema_changes_id_seq OWNED BY ecos_dq.schema_changes.id;


--
-- Name: schema_snapshots; Type: TABLE; Schema: ecos_dq; Owner: postgres
--

CREATE TABLE ecos_dq.schema_snapshots (
    id bigint NOT NULL,
    datasource_id character varying(128) NOT NULL,
    table_name character varying(256) NOT NULL,
    column_hash character varying(64) NOT NULL,
    col_sig text,
    snapshot_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_dq.schema_snapshots OWNER TO postgres;

--
-- Name: schema_snapshots_id_seq; Type: SEQUENCE; Schema: ecos_dq; Owner: postgres
--

CREATE SEQUENCE ecos_dq.schema_snapshots_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_dq.schema_snapshots_id_seq OWNER TO postgres;

--
-- Name: schema_snapshots_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_dq; Owner: postgres
--

ALTER SEQUENCE ecos_dq.schema_snapshots_id_seq OWNED BY ecos_dq.schema_snapshots.id;


--
-- Name: doc; Type: TABLE; Schema: ecos_dw; Owner: postgres
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


ALTER TABLE ecos_dw.doc OWNER TO postgres;

--
-- Name: TABLE doc; Type: COMMENT; Schema: ecos_dw; Owner: postgres
--

COMMENT ON TABLE ecos_dw.doc IS 'DW 层非结构化文档表（A1）：文档级解析状态机，写入权归数据工作台';


--
-- Name: COLUMN doc.doc_id; Type: COMMENT; Schema: ecos_dw; Owner: postgres
--

COMMENT ON COLUMN ecos_dw.doc.doc_id IS '文档 ID（业务唯一键，幂等重解析按此 upsert）';


--
-- Name: COLUMN doc.object_key; Type: COMMENT; Schema: ecos_dw; Owner: postgres
--

COMMENT ON COLUMN ecos_dw.doc.object_key IS '近源层对象 key: raw/unstructured/{source}/{docId}/{originalFileName}';


--
-- Name: COLUMN doc.parse_status; Type: COMMENT; Schema: ecos_dw; Owner: postgres
--

COMMENT ON COLUMN ecos_dw.doc.parse_status IS '解析状态: queued/parsing/extracting/done/failed';


--
-- Name: doc_chunk; Type: TABLE; Schema: ecos_dw; Owner: postgres
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


ALTER TABLE ecos_dw.doc_chunk OWNER TO postgres;

--
-- Name: TABLE doc_chunk; Type: COMMENT; Schema: ecos_dw; Owner: postgres
--

COMMENT ON TABLE ecos_dw.doc_chunk IS 'DW 层非结构化文档分块表（A1）：解析文本 + 序号 + 偏移 + 元数据，写入权归数据工作台';


--
-- Name: COLUMN doc_chunk.status; Type: COMMENT; Schema: ecos_dw; Owner: postgres
--

COMMENT ON COLUMN ecos_dw.doc_chunk.status IS '分块状态: active/deprecated';


--
-- Name: COLUMN doc_chunk.embedding_id; Type: COMMENT; Schema: ecos_dw; Owner: postgres
--

COMMENT ON COLUMN ecos_dw.doc_chunk.embedding_id IS '关联知识向量索引 ID（知识工作台消费后回填），可空';


--
-- Name: outbox_event; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event OWNER TO postgres;

--
-- Name: TABLE outbox_event; Type: COMMENT; Schema: ecos_infra; Owner: postgres
--

COMMENT ON TABLE ecos_infra.outbox_event IS 'Outbox事件表(统一,按月分区)';


--
-- Name: COLUMN outbox_event.event_type; Type: COMMENT; Schema: ecos_infra; Owner: postgres
--

COMMENT ON COLUMN ecos_infra.outbox_event.event_type IS '事件类型';


--
-- Name: COLUMN outbox_event.aggregate_type; Type: COMMENT; Schema: ecos_infra; Owner: postgres
--

COMMENT ON COLUMN ecos_infra.outbox_event.aggregate_type IS '聚合类型';


--
-- Name: COLUMN outbox_event.aggregate_id; Type: COMMENT; Schema: ecos_infra; Owner: postgres
--

COMMENT ON COLUMN ecos_infra.outbox_event.aggregate_id IS '聚合ID';


--
-- Name: COLUMN outbox_event.published; Type: COMMENT; Schema: ecos_infra; Owner: postgres
--

COMMENT ON COLUMN ecos_infra.outbox_event.published IS '是否已发布';


--
-- Name: COLUMN outbox_event.kafka_topic; Type: COMMENT; Schema: ecos_infra; Owner: postgres
--

COMMENT ON COLUMN ecos_infra.outbox_event.kafka_topic IS 'Kafka主题';


--
-- Name: outbox_event_id_seq; Type: SEQUENCE; Schema: ecos_infra; Owner: postgres
--

CREATE SEQUENCE ecos_infra.outbox_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_infra.outbox_event_id_seq OWNER TO postgres;

--
-- Name: outbox_event_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_infra; Owner: postgres
--

ALTER SEQUENCE ecos_infra.outbox_event_id_seq OWNED BY ecos_infra.outbox_event.id;


--
-- Name: outbox_event_2025_01; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2025_01 OWNER TO postgres;

--
-- Name: outbox_event_2025_02; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2025_02 OWNER TO postgres;

--
-- Name: outbox_event_2025_03; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2025_03 OWNER TO postgres;

--
-- Name: outbox_event_2025_04; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2025_04 OWNER TO postgres;

--
-- Name: outbox_event_2025_05; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2025_05 OWNER TO postgres;

--
-- Name: outbox_event_2025_06; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2025_06 OWNER TO postgres;

--
-- Name: outbox_event_2025_07; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2025_07 OWNER TO postgres;

--
-- Name: outbox_event_2025_08; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2025_08 OWNER TO postgres;

--
-- Name: outbox_event_2025_09; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2025_09 OWNER TO postgres;

--
-- Name: outbox_event_2025_10; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2025_10 OWNER TO postgres;

--
-- Name: outbox_event_2025_11; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2025_11 OWNER TO postgres;

--
-- Name: outbox_event_2025_12; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2025_12 OWNER TO postgres;

--
-- Name: outbox_event_2026_01; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2026_01 OWNER TO postgres;

--
-- Name: outbox_event_2026_02; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2026_02 OWNER TO postgres;

--
-- Name: outbox_event_2026_03; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2026_03 OWNER TO postgres;

--
-- Name: outbox_event_2026_04; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2026_04 OWNER TO postgres;

--
-- Name: outbox_event_2026_05; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2026_05 OWNER TO postgres;

--
-- Name: outbox_event_2026_06; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2026_06 OWNER TO postgres;

--
-- Name: outbox_event_2026_07; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2026_07 OWNER TO postgres;

--
-- Name: outbox_event_2026_08; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2026_08 OWNER TO postgres;

--
-- Name: outbox_event_2026_09; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2026_09 OWNER TO postgres;

--
-- Name: outbox_event_2026_10; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2026_10 OWNER TO postgres;

--
-- Name: outbox_event_2026_11; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2026_11 OWNER TO postgres;

--
-- Name: outbox_event_2026_12; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2026_12 OWNER TO postgres;

--
-- Name: outbox_event_2027_01; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2027_01 OWNER TO postgres;

--
-- Name: outbox_event_2027_02; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2027_02 OWNER TO postgres;

--
-- Name: outbox_event_2027_03; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2027_03 OWNER TO postgres;

--
-- Name: outbox_event_2027_04; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2027_04 OWNER TO postgres;

--
-- Name: outbox_event_2027_05; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2027_05 OWNER TO postgres;

--
-- Name: outbox_event_2027_06; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2027_06 OWNER TO postgres;

--
-- Name: outbox_event_2027_07; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2027_07 OWNER TO postgres;

--
-- Name: outbox_event_2027_08; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2027_08 OWNER TO postgres;

--
-- Name: outbox_event_2027_09; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2027_09 OWNER TO postgres;

--
-- Name: outbox_event_2027_10; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2027_10 OWNER TO postgres;

--
-- Name: outbox_event_2027_11; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2027_11 OWNER TO postgres;

--
-- Name: outbox_event_2027_12; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_2027_12 OWNER TO postgres;

--
-- Name: outbox_event_default; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.outbox_event_default OWNER TO postgres;

--
-- Name: saga_instance; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.saga_instance OWNER TO postgres;

--
-- Name: TABLE saga_instance; Type: COMMENT; Schema: ecos_infra; Owner: postgres
--

COMMENT ON TABLE ecos_infra.saga_instance IS 'Saga实例表';


--
-- Name: COLUMN saga_instance.saga_type; Type: COMMENT; Schema: ecos_infra; Owner: postgres
--

COMMENT ON COLUMN ecos_infra.saga_instance.saga_type IS 'Saga类型';


--
-- Name: COLUMN saga_instance.status; Type: COMMENT; Schema: ecos_infra; Owner: postgres
--

COMMENT ON COLUMN ecos_infra.saga_instance.status IS '状态: STARTED/COMPENSATING/COMPLETED/FAILED';


--
-- Name: schema_version; Type: TABLE; Schema: ecos_infra; Owner: postgres
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


ALTER TABLE ecos_infra.schema_version OWNER TO postgres;

--
-- Name: TABLE schema_version; Type: COMMENT; Schema: ecos_infra; Owner: postgres
--

COMMENT ON TABLE ecos_infra.schema_version IS 'Schema版本追踪表';


--
-- Name: ecos_glossary_term; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
--

CREATE TABLE ecos_knowledge.ecos_glossary_term (
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
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_knowledge.ecos_glossary_term OWNER TO postgres;

--
-- Name: TABLE ecos_glossary_term; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON TABLE ecos_knowledge.ecos_glossary_term IS '术语表';


--
-- Name: ecos_glossary_term_id_seq; Type: SEQUENCE; Schema: ecos_knowledge; Owner: postgres
--

CREATE SEQUENCE ecos_knowledge.ecos_glossary_term_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_knowledge.ecos_glossary_term_id_seq OWNER TO postgres;

--
-- Name: ecos_glossary_term_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_knowledge; Owner: postgres
--

ALTER SEQUENCE ecos_knowledge.ecos_glossary_term_id_seq OWNED BY ecos_knowledge.ecos_glossary_term.id;


--
-- Name: ecos_knowledge_document; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
--

CREATE TABLE ecos_knowledge.ecos_knowledge_document (
    id character varying(64) NOT NULL,
    title character varying(255) NOT NULL,
    content text,
    doc_type character varying(32),
    tags character varying(256),
    entity_types character varying(512),
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_knowledge.ecos_knowledge_document OWNER TO postgres;

--
-- Name: TABLE ecos_knowledge_document; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON TABLE ecos_knowledge.ecos_knowledge_document IS '知识文档表';


--
-- Name: ecos_knowledge_graph_edge; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
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


ALTER TABLE ecos_knowledge.ecos_knowledge_graph_edge OWNER TO postgres;

--
-- Name: TABLE ecos_knowledge_graph_edge; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON TABLE ecos_knowledge.ecos_knowledge_graph_edge IS '知识图谱边表(runtime-core兼容)';


--
-- Name: ecos_knowledge_graph_node; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
--

CREATE TABLE ecos_knowledge.ecos_knowledge_graph_node (
    id character varying(64) NOT NULL,
    label character varying(128),
    node_type character varying(64) DEFAULT 'Concept'::character varying,
    description text,
    properties_json jsonb,
    source_node_id character varying(64),
    domain character varying(64),
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_knowledge.ecos_knowledge_graph_node OWNER TO postgres;

--
-- Name: TABLE ecos_knowledge_graph_node; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON TABLE ecos_knowledge.ecos_knowledge_graph_node IS '知识图谱节点表(runtime-core兼容)';


--
-- Name: ecos_marketplace_access_request; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
--

CREATE TABLE ecos_knowledge.ecos_marketplace_access_request (
    id bigint NOT NULL,
    asset_id bigint NOT NULL,
    reason text,
    applicant character varying(128),
    status character varying(32) DEFAULT 'PENDING'::character varying,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_knowledge.ecos_marketplace_access_request OWNER TO postgres;

--
-- Name: TABLE ecos_marketplace_access_request; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON TABLE ecos_knowledge.ecos_marketplace_access_request IS '市场访问请求表';


--
-- Name: ecos_marketplace_access_request_id_seq; Type: SEQUENCE; Schema: ecos_knowledge; Owner: postgres
--

CREATE SEQUENCE ecos_knowledge.ecos_marketplace_access_request_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_knowledge.ecos_marketplace_access_request_id_seq OWNER TO postgres;

--
-- Name: ecos_marketplace_access_request_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_knowledge; Owner: postgres
--

ALTER SEQUENCE ecos_knowledge.ecos_marketplace_access_request_id_seq OWNED BY ecos_knowledge.ecos_marketplace_access_request.id;


--
-- Name: ecos_marketplace_asset; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
--

CREATE TABLE ecos_knowledge.ecos_marketplace_asset (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    category character varying(64),
    owner character varying(128),
    rating numeric(3,2) DEFAULT 0.0,
    popularity integer DEFAULT 0,
    status character varying(32) DEFAULT 'PUBLISHED'::character varying,
    ontology_entity_id character varying(128),
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_knowledge.ecos_marketplace_asset OWNER TO postgres;

--
-- Name: TABLE ecos_marketplace_asset; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON TABLE ecos_knowledge.ecos_marketplace_asset IS '市场资产表';


--
-- Name: ecos_marketplace_asset_id_seq; Type: SEQUENCE; Schema: ecos_knowledge; Owner: postgres
--

CREATE SEQUENCE ecos_knowledge.ecos_marketplace_asset_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_knowledge.ecos_marketplace_asset_id_seq OWNER TO postgres;

--
-- Name: ecos_marketplace_asset_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_knowledge; Owner: postgres
--

ALTER SEQUENCE ecos_knowledge.ecos_marketplace_asset_id_seq OWNED BY ecos_knowledge.ecos_marketplace_asset.id;


--
-- Name: expert_rule; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
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


ALTER TABLE ecos_knowledge.expert_rule OWNER TO postgres;

--
-- Name: TABLE expert_rule; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON TABLE ecos_knowledge.expert_rule IS '专家规则表(kb-engine)';


--
-- Name: graph_edge; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
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
    source_pk character varying(255)
);


ALTER TABLE ecos_knowledge.graph_edge OWNER TO postgres;

--
-- Name: TABLE graph_edge; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON TABLE ecos_knowledge.graph_edge IS '知识图谱边表(kb-engine)';


--
-- Name: COLUMN graph_edge.type; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.graph_edge.type IS '关系类型';


--
-- Name: COLUMN graph_edge.weight; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.graph_edge.weight IS '关系权重';


--
-- Name: COLUMN graph_edge.ontology_id; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.graph_edge.ontology_id IS '溯源: 本体业务 ID (kb_ontology_snapshot.ontology_id, D9)';


--
-- Name: COLUMN graph_edge.ontology_version; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.graph_edge.ontology_version IS '溯源: 生成该边时的本体版本 (kb_ontology_snapshot.version, D9)';


--
-- Name: COLUMN graph_edge.source_resource_id; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.graph_edge.source_resource_id IS '溯源: 实例抽取来源 DW 层数据资源 ID (B3 写入)';


--
-- Name: COLUMN graph_edge.source_pk; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.graph_edge.source_pk IS '溯源: 实例抽取来源 DW 表主键值 (B3 写入)';


--
-- Name: graph_node; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
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
    source_pk character varying(255)
);


ALTER TABLE ecos_knowledge.graph_node OWNER TO postgres;

--
-- Name: TABLE graph_node; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON TABLE ecos_knowledge.graph_node IS '知识图谱节点表(kb-engine)';


--
-- Name: COLUMN graph_node.label; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.graph_node.label IS '标签/名称';


--
-- Name: COLUMN graph_node.node_type; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.graph_node.node_type IS '节点类型: Concept/Entity/Event';


--
-- Name: COLUMN graph_node.properties; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.graph_node.properties IS '属性JSON';


--
-- Name: COLUMN graph_node.ontology_id; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.graph_node.ontology_id IS '溯源: 本体业务 ID (kb_ontology_snapshot.ontology_id, D9)';


--
-- Name: COLUMN graph_node.ontology_version; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.graph_node.ontology_version IS '溯源: 生成该节点时的本体版本 (kb_ontology_snapshot.version, D9)';


--
-- Name: COLUMN graph_node.source_resource_id; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.graph_node.source_resource_id IS '溯源: 实例抽取来源 DW 层数据资源 ID (B3 写入)';


--
-- Name: COLUMN graph_node.source_pk; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.graph_node.source_pk IS '溯源: 实例抽取来源 DW 表主键值 (B3 写入)';


--
-- Name: graph_subgraph; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
--

CREATE TABLE ecos_knowledge.graph_subgraph (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    node_ids jsonb DEFAULT '[]'::jsonb,
    edge_ids jsonb DEFAULT '[]'::jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_knowledge.graph_subgraph OWNER TO postgres;

--
-- Name: TABLE graph_subgraph; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON TABLE ecos_knowledge.graph_subgraph IS '知识图谱子图表(kb-engine)';


--
-- Name: kb_doc; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
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
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_knowledge.kb_doc OWNER TO postgres;

--
-- Name: TABLE kb_doc; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON TABLE ecos_knowledge.kb_doc IS '非结构化文档过渡表（A3）：文档级解析状态机，A1 落地时迁入 DW 层';


--
-- Name: COLUMN kb_doc.object_key; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_doc.object_key IS '近源层对象 key: raw/unstructured/{source}/{docId}/{originalFileName}';


--
-- Name: COLUMN kb_doc.parse_status; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_doc.parse_status IS '解析状态: queued/parsing/extracting/done/failed';


--
-- Name: COLUMN kb_doc.text_source_path; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_doc.text_source_path IS '解析文本登记为 CURATED 资源时的 source_path';


--
-- Name: kb_doc_chunk; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
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
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_knowledge.kb_doc_chunk OWNER TO postgres;

--
-- Name: TABLE kb_doc_chunk; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON TABLE ecos_knowledge.kb_doc_chunk IS '非结构化文档分块表（A3 过渡）：kb 自有表，A1 落地时迁入 DW 层 doc_chunk';


--
-- Name: COLUMN kb_doc_chunk.status; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_doc_chunk.status IS '分块状态: active/deprecated';


--
-- Name: COLUMN kb_doc_chunk.embedding_id; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_doc_chunk.embedding_id IS '关联 ecos_knowledge.knowledge_embedding.id（B4 向量写入），可空';


--
-- Name: kb_extract_audit; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
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
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_knowledge.kb_extract_audit OWNER TO postgres;

--
-- Name: kb_extract_audit_id_seq; Type: SEQUENCE; Schema: ecos_knowledge; Owner: postgres
--

CREATE SEQUENCE ecos_knowledge.kb_extract_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_knowledge.kb_extract_audit_id_seq OWNER TO postgres;

--
-- Name: kb_extract_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_knowledge; Owner: postgres
--

ALTER SEQUENCE ecos_knowledge.kb_extract_audit_id_seq OWNED BY ecos_knowledge.kb_extract_audit.id;


--
-- Name: kb_extract_candidate; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
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
    is_deleted smallint DEFAULT 0 NOT NULL
);


ALTER TABLE ecos_knowledge.kb_extract_candidate OWNER TO postgres;

--
-- Name: TABLE kb_extract_candidate; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON TABLE ecos_knowledge.kb_extract_candidate IS 'K1 非结构化通道 LLM 抽取候选池（暂存，K2 融合消费；溯源列与 graph_node 对齐）';


--
-- Name: COLUMN kb_extract_candidate.doc_id; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.doc_id IS '关联文档 ID（上传临时文件的 fileId，同 extraction_drafts.id）';


--
-- Name: COLUMN kb_extract_candidate.entity_name; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.entity_name IS 'LLM 抽取的实体名称（自然语言）';


--
-- Name: COLUMN kb_extract_candidate.entity_type; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.entity_type IS 'LLM 推断的实体类型（可选映射到本体实体 code）';


--
-- Name: COLUMN kb_extract_candidate.relation; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.relation IS 'LLM 抽取的关系 code（可选映射到本体关系 code）';


--
-- Name: COLUMN kb_extract_candidate.subject_id; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.subject_id IS '关系源节点 ID（无关系时为空）';


--
-- Name: COLUMN kb_extract_candidate.object_id; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.object_id IS '关系目标节点 ID（无关系时为空）';


--
-- Name: COLUMN kb_extract_candidate.confidence; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.confidence IS 'LLM 置信度（0.0000 - 1.0000）';


--
-- Name: COLUMN kb_extract_candidate.status; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.status IS '候选状态：PENDING / APPROVED / REJECTED / MERGED';


--
-- Name: COLUMN kb_extract_candidate.review_note; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.review_note IS '审核意见 / LLM 解释';


--
-- Name: COLUMN kb_extract_candidate.ontology_id; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.ontology_id IS '关联本体业务 ID（同 graph_node.ontology_id，溯源对齐）';


--
-- Name: COLUMN kb_extract_candidate.ontology_version; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.ontology_version IS '关联本体版本（同 graph_node.ontology_version，溯源对齐）';


--
-- Name: COLUMN kb_extract_candidate.source_resource_id; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.source_resource_id IS '来源资源 ID（上传文档登记 ID，溯源对齐）';


--
-- Name: COLUMN kb_extract_candidate.source_pk; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.source_pk IS '来源主键（同 graph_node.source_pk，溯源对齐）';


--
-- Name: kb_extract_candidate_id_seq; Type: SEQUENCE; Schema: ecos_knowledge; Owner: postgres
--

CREATE SEQUENCE ecos_knowledge.kb_extract_candidate_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_knowledge.kb_extract_candidate_id_seq OWNER TO postgres;

--
-- Name: kb_extract_candidate_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_knowledge; Owner: postgres
--

ALTER SEQUENCE ecos_knowledge.kb_extract_candidate_id_seq OWNED BY ecos_knowledge.kb_extract_candidate.id;


--
-- Name: kb_extract_watermark; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
--

CREATE TABLE ecos_knowledge.kb_extract_watermark (
    id bigint NOT NULL,
    ontology_id character varying(64) NOT NULL,
    entity_code character varying(100) NOT NULL,
    resource_id character varying(64) NOT NULL,
    watermark character varying(255),
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_knowledge.kb_extract_watermark OWNER TO postgres;

--
-- Name: TABLE kb_extract_watermark; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON TABLE ecos_knowledge.kb_extract_watermark IS '图谱实例抽取水位线（B3-2 / K4 增量基准）';


--
-- Name: COLUMN kb_extract_watermark.ontology_id; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_watermark.ontology_id IS '本体业务 ID';


--
-- Name: COLUMN kb_extract_watermark.entity_code; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_watermark.entity_code IS '本体实体 code';


--
-- Name: COLUMN kb_extract_watermark.resource_id; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_watermark.resource_id IS 'DW 层数据资源 ID（只读消费）';


--
-- Name: COLUMN kb_extract_watermark.watermark; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_extract_watermark.watermark IS '上次抽取收敛水位';


--
-- Name: kb_extract_watermark_id_seq; Type: SEQUENCE; Schema: ecos_knowledge; Owner: postgres
--

CREATE SEQUENCE ecos_knowledge.kb_extract_watermark_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_knowledge.kb_extract_watermark_id_seq OWNER TO postgres;

--
-- Name: kb_extract_watermark_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_knowledge; Owner: postgres
--

ALTER SEQUENCE ecos_knowledge.kb_extract_watermark_id_seq OWNED BY ecos_knowledge.kb_extract_watermark.id;


--
-- Name: kb_ontology_snapshot; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
--

CREATE TABLE ecos_knowledge.kb_ontology_snapshot (
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


ALTER TABLE ecos_knowledge.kb_ontology_snapshot OWNER TO postgres;

--
-- Name: TABLE kb_ontology_snapshot; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON TABLE ecos_knowledge.kb_ontology_snapshot IS '本体版本快照（PMO-54 与 PMO-50 T4 本体发布事件消费 — V115/Kb 侧补齐）';


--
-- Name: COLUMN kb_ontology_snapshot.id; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_ontology_snapshot.id IS '自增主键';


--
-- Name: COLUMN kb_ontology_snapshot.ontology_id; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_ontology_snapshot.ontology_id IS '本体业务 ID';


--
-- Name: COLUMN kb_ontology_snapshot.version; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_ontology_snapshot.version IS '本体版本 (published version)';


--
-- Name: COLUMN kb_ontology_snapshot.entity_codes; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_ontology_snapshot.entity_codes IS '本体包含实体 code 集合 (JSONB array)';


--
-- Name: COLUMN kb_ontology_snapshot.relationship_codes; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_ontology_snapshot.relationship_codes IS '本体包含关系 code 集合 (JSONB array)';


--
-- Name: COLUMN kb_ontology_snapshot.schema_hash; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_ontology_snapshot.schema_hash IS '发行时 schema JSON 的 SHA-256 hash 减少';


--
-- Name: COLUMN kb_ontology_snapshot.created_by; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_ontology_snapshot.created_by IS '发布人 / 系统标识';


--
-- Name: COLUMN kb_ontology_snapshot.created_at; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_ontology_snapshot.created_at IS '快照时间（含 upsert 时间刷行）';


--
-- Name: COLUMN kb_ontology_snapshot.is_deleted; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kb_ontology_snapshot.is_deleted IS '逻辑删除: 0=当前 / 1=已删 / 2=历史旧版本 (EcosOntologyEventConsumer 标记语义)';


--
-- Name: kb_ontology_snapshot_id_seq; Type: SEQUENCE; Schema: ecos_knowledge; Owner: postgres
--

CREATE SEQUENCE ecos_knowledge.kb_ontology_snapshot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_knowledge.kb_ontology_snapshot_id_seq OWNER TO postgres;

--
-- Name: kb_ontology_snapshot_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_knowledge; Owner: postgres
--

ALTER SEQUENCE ecos_knowledge.kb_ontology_snapshot_id_seq OWNED BY ecos_knowledge.kb_ontology_snapshot.id;


--
-- Name: kb_scheduled_extract; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
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
    is_deleted smallint DEFAULT 0
);


ALTER TABLE ecos_knowledge.kb_scheduled_extract OWNER TO postgres;

--
-- Name: kb_scheduled_extract_id_seq; Type: SEQUENCE; Schema: ecos_knowledge; Owner: postgres
--

CREATE SEQUENCE ecos_knowledge.kb_scheduled_extract_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_knowledge.kb_scheduled_extract_id_seq OWNER TO postgres;

--
-- Name: kb_scheduled_extract_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_knowledge; Owner: postgres
--

ALTER SEQUENCE ecos_knowledge.kb_scheduled_extract_id_seq OWNED BY ecos_knowledge.kb_scheduled_extract.id;


--
-- Name: kg_sync_log; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
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
    report jsonb
);


ALTER TABLE ecos_knowledge.kg_sync_log OWNER TO postgres;

--
-- Name: COLUMN kg_sync_log.report; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.kg_sync_log.report IS '实例抽取结构化报告 JSONB (B3-2)';


--
-- Name: kg_sync_log_id_seq; Type: SEQUENCE; Schema: ecos_knowledge; Owner: postgres
--

CREATE SEQUENCE ecos_knowledge.kg_sync_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_knowledge.kg_sync_log_id_seq OWNER TO postgres;

--
-- Name: kg_sync_log_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_knowledge; Owner: postgres
--

ALTER SEQUENCE ecos_knowledge.kg_sync_log_id_seq OWNED BY ecos_knowledge.kg_sync_log.id;


--
-- Name: knowledge_article; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
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
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_knowledge.knowledge_article OWNER TO postgres;

--
-- Name: TABLE knowledge_article; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON TABLE ecos_knowledge.knowledge_article IS '知识文章表(kb-engine)';


--
-- Name: knowledge_embedding; Type: TABLE; Schema: ecos_knowledge; Owner: postgres
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
    embedding_vec public.vector(1536)
);


ALTER TABLE ecos_knowledge.knowledge_embedding OWNER TO postgres;

--
-- Name: TABLE knowledge_embedding; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON TABLE ecos_knowledge.knowledge_embedding IS '知识向量嵌入表(kb-engine)';


--
-- Name: COLUMN knowledge_embedding.embedding_vec; Type: COMMENT; Schema: ecos_knowledge; Owner: postgres
--

COMMENT ON COLUMN ecos_knowledge.knowledge_embedding.embedding_vec IS 'B4: pgvector 向量列（1536 维，text-embedding-3-small）；原 embedding(JSONB) 保留双写过渡';


--
-- Name: action_definition; Type: TABLE; Schema: ecos_ontology; Owner: postgres
--

CREATE TABLE ecos_ontology.action_definition (
    id character varying(64) NOT NULL,
    code character varying(128),
    name character varying(255),
    type character varying(16) DEFAULT 'WORKFLOW'::character varying,
    config jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_ontology.action_definition OWNER TO postgres;

--
-- Name: TABLE action_definition; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.action_definition IS '动作定义表(本体编译器)';


--
-- Name: ecos_object_attachment; Type: TABLE; Schema: ecos_ontology; Owner: postgres
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


ALTER TABLE ecos_ontology.ecos_object_attachment OWNER TO postgres;

--
-- Name: TABLE ecos_object_attachment; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.ecos_object_attachment IS '对象附件表';


--
-- Name: ecos_object_data; Type: TABLE; Schema: ecos_ontology; Owner: postgres
--

CREATE TABLE ecos_ontology.ecos_object_data (
    id character varying(64) NOT NULL,
    entity_code character varying(128) NOT NULL,
    object_data jsonb,
    status character varying(32) DEFAULT 'ACTIVE'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_ontology.ecos_object_data OWNER TO postgres;

--
-- Name: TABLE ecos_object_data; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.ecos_object_data IS '对象数据表(运行时)';


--
-- Name: ecos_object_links; Type: TABLE; Schema: ecos_ontology; Owner: postgres
--

CREATE TABLE ecos_ontology.ecos_object_links (
    id character varying(64) DEFAULT (gen_random_uuid())::text NOT NULL,
    source_id character varying(64),
    target_id character varying(64),
    relation_code character varying(128),
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_ontology.ecos_object_links OWNER TO postgres;

--
-- Name: TABLE ecos_object_links; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.ecos_object_links IS '对象链接表';


--
-- Name: ecos_object_relation; Type: TABLE; Schema: ecos_ontology; Owner: postgres
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


ALTER TABLE ecos_ontology.ecos_object_relation OWNER TO postgres;

--
-- Name: TABLE ecos_object_relation; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.ecos_object_relation IS '对象关联表(桥接)';


--
-- Name: ecos_object_relationship; Type: TABLE; Schema: ecos_ontology; Owner: postgres
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


ALTER TABLE ecos_ontology.ecos_object_relationship OWNER TO postgres;

--
-- Name: TABLE ecos_object_relationship; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.ecos_object_relationship IS '对象关系表';


--
-- Name: ecos_object_state_machine; Type: TABLE; Schema: ecos_ontology; Owner: postgres
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


ALTER TABLE ecos_ontology.ecos_object_state_machine OWNER TO postgres;

--
-- Name: TABLE ecos_object_state_machine; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.ecos_object_state_machine IS '对象状态机表';


--
-- Name: ecos_object_timeline; Type: TABLE; Schema: ecos_ontology; Owner: postgres
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


ALTER TABLE ecos_ontology.ecos_object_timeline OWNER TO postgres;

--
-- Name: TABLE ecos_object_timeline; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.ecos_object_timeline IS '对象时间线表';


--
-- Name: ecos_object_version; Type: TABLE; Schema: ecos_ontology; Owner: postgres
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
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_ontology.ecos_object_version OWNER TO postgres;

--
-- Name: TABLE ecos_object_version; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.ecos_object_version IS '对象版本表';


--
-- Name: ecos_workflow; Type: TABLE; Schema: ecos_ontology; Owner: postgres
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
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_ontology.ecos_workflow OWNER TO postgres;

--
-- Name: TABLE ecos_workflow; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.ecos_workflow IS '工作流定义表';


--
-- Name: ecos_workflow_approval; Type: TABLE; Schema: ecos_ontology; Owner: postgres
--

CREATE TABLE ecos_ontology.ecos_workflow_approval (
    id character varying(50) NOT NULL,
    task_id character varying(50),
    instance_id character varying(50),
    approver character varying(100),
    decision character varying(50),
    opinion text,
    form_data jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_ontology.ecos_workflow_approval OWNER TO postgres;

--
-- Name: TABLE ecos_workflow_approval; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.ecos_workflow_approval IS '工作流审批表';


--
-- Name: ecos_workflow_instance; Type: TABLE; Schema: ecos_ontology; Owner: postgres
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


ALTER TABLE ecos_ontology.ecos_workflow_instance OWNER TO postgres;

--
-- Name: TABLE ecos_workflow_instance; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.ecos_workflow_instance IS '工作流实例表';


--
-- Name: ecos_workflow_log; Type: TABLE; Schema: ecos_ontology; Owner: postgres
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


ALTER TABLE ecos_ontology.ecos_workflow_log OWNER TO postgres;

--
-- Name: TABLE ecos_workflow_log; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.ecos_workflow_log IS '工作流日志表';


--
-- Name: ecos_workflow_task; Type: TABLE; Schema: ecos_ontology; Owner: postgres
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


ALTER TABLE ecos_ontology.ecos_workflow_task OWNER TO postgres;

--
-- Name: TABLE ecos_workflow_task; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.ecos_workflow_task IS '工作流任务表';


--
-- Name: entity_definition; Type: TABLE; Schema: ecos_ontology; Owner: postgres
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


ALTER TABLE ecos_ontology.entity_definition OWNER TO postgres;

--
-- Name: TABLE entity_definition; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.entity_definition IS '实体定义表(本体编译器)';


--
-- Name: event_definition; Type: TABLE; Schema: ecos_ontology; Owner: postgres
--

CREATE TABLE ecos_ontology.event_definition (
    id character varying(64) NOT NULL,
    code character varying(128),
    source character varying(128),
    payload_schema jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_ontology.event_definition OWNER TO postgres;

--
-- Name: TABLE event_definition; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.event_definition IS '事件定义表(本体编译器)';


--
-- Name: metric_definition; Type: TABLE; Schema: ecos_ontology; Owner: postgres
--

CREATE TABLE ecos_ontology.metric_definition (
    id character varying(64) NOT NULL,
    code character varying(128) NOT NULL,
    name character varying(255) NOT NULL,
    expression text,
    aggregation character varying(16) DEFAULT 'SUM'::character varying,
    entity_code character varying(128),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_ontology.metric_definition OWNER TO postgres;

--
-- Name: TABLE metric_definition; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.metric_definition IS '指标定义表(本体编译器)';


--
-- Name: policy_definition; Type: TABLE; Schema: ecos_ontology; Owner: postgres
--

CREATE TABLE ecos_ontology.policy_definition (
    id character varying(64) NOT NULL,
    code character varying(128),
    type character varying(32),
    expression text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_ontology.policy_definition OWNER TO postgres;

--
-- Name: TABLE policy_definition; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.policy_definition IS '策略定义表(本体编译器)';


--
-- Name: relationship_definition; Type: TABLE; Schema: ecos_ontology; Owner: postgres
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


ALTER TABLE ecos_ontology.relationship_definition OWNER TO postgres;

--
-- Name: TABLE relationship_definition; Type: COMMENT; Schema: ecos_ontology; Owner: postgres
--

COMMENT ON TABLE ecos_ontology.relationship_definition IS '关系定义表(本体编译器)';


--
-- Name: ecos_alert_history; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.ecos_alert_history (
    id character varying(64) NOT NULL,
    alert_type character varying(64) NOT NULL,
    severity character varying(16) DEFAULT 'INFO'::character varying,
    source character varying(128),
    message text,
    user_id character varying(64),
    tenant_id character varying(64),
    resource character varying(256),
    resolved boolean DEFAULT false,
    resolved_by character varying(64),
    resolved_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_security.ecos_alert_history OWNER TO postgres;

--
-- Name: TABLE ecos_alert_history; Type: COMMENT; Schema: ecos_security; Owner: postgres
--

COMMENT ON TABLE ecos_security.ecos_alert_history IS '安全告警历史表';


--
-- Name: ecos_spans; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.ecos_spans (
    span_id character varying(64) NOT NULL,
    trace_id character varying(64) NOT NULL,
    parent_span_id character varying(64),
    operation_name character varying(512),
    service_name character varying(128),
    http_method character varying(16),
    http_path character varying(512),
    http_status integer DEFAULT 0,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    duration_ms bigint DEFAULT 0,
    status character varying(16) DEFAULT 'OK'::character varying,
    attributes jsonb,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_security.ecos_spans OWNER TO postgres;

--
-- Name: TABLE ecos_spans; Type: COMMENT; Schema: ecos_security; Owner: postgres
--

COMMENT ON TABLE ecos_security.ecos_spans IS '遥测追踪表';


--
-- Name: ecos_token_usage; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.ecos_token_usage (
    id bigint NOT NULL,
    trace_id character varying(64),
    model character varying(64),
    operation character varying(256),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_tokens integer DEFAULT 0,
    cost_estimate numeric(10,6) DEFAULT 0,
    latency_ms bigint DEFAULT 0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
)
PARTITION BY RANGE (created_at);


ALTER TABLE ecos_security.ecos_token_usage OWNER TO postgres;

--
-- Name: TABLE ecos_token_usage; Type: COMMENT; Schema: ecos_security; Owner: postgres
--

COMMENT ON TABLE ecos_security.ecos_token_usage IS 'Token用量表(按季度分区)';


--
-- Name: ecos_token_usage_id_seq; Type: SEQUENCE; Schema: ecos_security; Owner: postgres
--

CREATE SEQUENCE ecos_security.ecos_token_usage_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_security.ecos_token_usage_id_seq OWNER TO postgres;

--
-- Name: ecos_token_usage_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_security; Owner: postgres
--

ALTER SEQUENCE ecos_security.ecos_token_usage_id_seq OWNED BY ecos_security.ecos_token_usage.id;


--
-- Name: ecos_token_usage_2025_q1; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.ecos_token_usage_2025_q1 (
    id bigint DEFAULT nextval('ecos_security.ecos_token_usage_id_seq'::regclass) NOT NULL,
    trace_id character varying(64),
    model character varying(64),
    operation character varying(256),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_tokens integer DEFAULT 0,
    cost_estimate numeric(10,6) DEFAULT 0,
    latency_ms bigint DEFAULT 0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_security.ecos_token_usage_2025_q1 OWNER TO postgres;

--
-- Name: ecos_token_usage_2025_q2; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.ecos_token_usage_2025_q2 (
    id bigint DEFAULT nextval('ecos_security.ecos_token_usage_id_seq'::regclass) NOT NULL,
    trace_id character varying(64),
    model character varying(64),
    operation character varying(256),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_tokens integer DEFAULT 0,
    cost_estimate numeric(10,6) DEFAULT 0,
    latency_ms bigint DEFAULT 0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_security.ecos_token_usage_2025_q2 OWNER TO postgres;

--
-- Name: ecos_token_usage_2025_q3; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.ecos_token_usage_2025_q3 (
    id bigint DEFAULT nextval('ecos_security.ecos_token_usage_id_seq'::regclass) NOT NULL,
    trace_id character varying(64),
    model character varying(64),
    operation character varying(256),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_tokens integer DEFAULT 0,
    cost_estimate numeric(10,6) DEFAULT 0,
    latency_ms bigint DEFAULT 0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_security.ecos_token_usage_2025_q3 OWNER TO postgres;

--
-- Name: ecos_token_usage_2025_q4; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.ecos_token_usage_2025_q4 (
    id bigint DEFAULT nextval('ecos_security.ecos_token_usage_id_seq'::regclass) NOT NULL,
    trace_id character varying(64),
    model character varying(64),
    operation character varying(256),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_tokens integer DEFAULT 0,
    cost_estimate numeric(10,6) DEFAULT 0,
    latency_ms bigint DEFAULT 0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_security.ecos_token_usage_2025_q4 OWNER TO postgres;

--
-- Name: ecos_token_usage_2026_q1; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.ecos_token_usage_2026_q1 (
    id bigint DEFAULT nextval('ecos_security.ecos_token_usage_id_seq'::regclass) NOT NULL,
    trace_id character varying(64),
    model character varying(64),
    operation character varying(256),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_tokens integer DEFAULT 0,
    cost_estimate numeric(10,6) DEFAULT 0,
    latency_ms bigint DEFAULT 0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_security.ecos_token_usage_2026_q1 OWNER TO postgres;

--
-- Name: ecos_token_usage_2026_q2; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.ecos_token_usage_2026_q2 (
    id bigint DEFAULT nextval('ecos_security.ecos_token_usage_id_seq'::regclass) NOT NULL,
    trace_id character varying(64),
    model character varying(64),
    operation character varying(256),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_tokens integer DEFAULT 0,
    cost_estimate numeric(10,6) DEFAULT 0,
    latency_ms bigint DEFAULT 0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_security.ecos_token_usage_2026_q2 OWNER TO postgres;

--
-- Name: ecos_token_usage_2026_q3; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.ecos_token_usage_2026_q3 (
    id bigint DEFAULT nextval('ecos_security.ecos_token_usage_id_seq'::regclass) NOT NULL,
    trace_id character varying(64),
    model character varying(64),
    operation character varying(256),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_tokens integer DEFAULT 0,
    cost_estimate numeric(10,6) DEFAULT 0,
    latency_ms bigint DEFAULT 0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_security.ecos_token_usage_2026_q3 OWNER TO postgres;

--
-- Name: ecos_token_usage_2026_q4; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.ecos_token_usage_2026_q4 (
    id bigint DEFAULT nextval('ecos_security.ecos_token_usage_id_seq'::regclass) NOT NULL,
    trace_id character varying(64),
    model character varying(64),
    operation character varying(256),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_tokens integer DEFAULT 0,
    cost_estimate numeric(10,6) DEFAULT 0,
    latency_ms bigint DEFAULT 0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_security.ecos_token_usage_2026_q4 OWNER TO postgres;

--
-- Name: ecos_token_usage_default; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.ecos_token_usage_default (
    id bigint DEFAULT nextval('ecos_security.ecos_token_usage_id_seq'::regclass) NOT NULL,
    trace_id character varying(64),
    model character varying(64),
    operation character varying(256),
    prompt_tokens integer DEFAULT 0,
    completion_tokens integer DEFAULT 0,
    total_tokens integer DEFAULT 0,
    cost_estimate numeric(10,6) DEFAULT 0,
    latency_ms bigint DEFAULT 0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_security.ecos_token_usage_default OWNER TO postgres;

--
-- Name: td_abac_policy; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_abac_policy (
    policy_id character varying(64) NOT NULL,
    policy_name character varying(128) NOT NULL,
    subject_condition character varying(512),
    resource_condition character varying(512) DEFAULT '*'::character varying,
    action_condition character varying(256) DEFAULT '*'::character varying,
    environment_condition character varying(512),
    effect character varying(16) NOT NULL,
    priority integer DEFAULT 100,
    scope_type character varying(16) DEFAULT 'GLOBAL'::character varying,
    scope_id character varying(64),
    tenant_id character varying(64),
    created_time timestamp without time zone DEFAULT now() NOT NULL,
    updated_time timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_security.td_abac_policy OWNER TO postgres;

--
-- Name: TABLE td_abac_policy; Type: COMMENT; Schema: ecos_security; Owner: postgres
--

COMMENT ON TABLE ecos_security.td_abac_policy IS 'ABAC策略表';


--
-- Name: COLUMN td_abac_policy.effect; Type: COMMENT; Schema: ecos_security; Owner: postgres
--

COMMENT ON COLUMN ecos_security.td_abac_policy.effect IS '效果: ALLOW/DENY';


--
-- Name: COLUMN td_abac_policy.priority; Type: COMMENT; Schema: ecos_security; Owner: postgres
--

COMMENT ON COLUMN ecos_security.td_abac_policy.priority IS '优先级(越大越优先)';


--
-- Name: COLUMN td_abac_policy.scope_type; Type: COMMENT; Schema: ecos_security; Owner: postgres
--

COMMENT ON COLUMN ecos_security.td_abac_policy.scope_type IS '作用域: GLOBAL/ORG/TENANT';


--
-- Name: td_audit_log; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log (
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
)
PARTITION BY RANGE ("timestamp");


ALTER TABLE ecos_security.td_audit_log OWNER TO postgres;

--
-- Name: TABLE td_audit_log; Type: COMMENT; Schema: ecos_security; Owner: postgres
--

COMMENT ON TABLE ecos_security.td_audit_log IS '审计日志表(按月分区)';


--
-- Name: td_audit_log_2024_01; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2024_01 (
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


ALTER TABLE ecos_security.td_audit_log_2024_01 OWNER TO postgres;

--
-- Name: td_audit_log_2024_02; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2024_02 (
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


ALTER TABLE ecos_security.td_audit_log_2024_02 OWNER TO postgres;

--
-- Name: td_audit_log_2024_03; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2024_03 (
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


ALTER TABLE ecos_security.td_audit_log_2024_03 OWNER TO postgres;

--
-- Name: td_audit_log_2024_04; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2024_04 (
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


ALTER TABLE ecos_security.td_audit_log_2024_04 OWNER TO postgres;

--
-- Name: td_audit_log_2024_05; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2024_05 (
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


ALTER TABLE ecos_security.td_audit_log_2024_05 OWNER TO postgres;

--
-- Name: td_audit_log_2024_06; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2024_06 (
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


ALTER TABLE ecos_security.td_audit_log_2024_06 OWNER TO postgres;

--
-- Name: td_audit_log_2024_07; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2024_07 (
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


ALTER TABLE ecos_security.td_audit_log_2024_07 OWNER TO postgres;

--
-- Name: td_audit_log_2024_08; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2024_08 (
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


ALTER TABLE ecos_security.td_audit_log_2024_08 OWNER TO postgres;

--
-- Name: td_audit_log_2024_09; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2024_09 (
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


ALTER TABLE ecos_security.td_audit_log_2024_09 OWNER TO postgres;

--
-- Name: td_audit_log_2024_10; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2024_10 (
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


ALTER TABLE ecos_security.td_audit_log_2024_10 OWNER TO postgres;

--
-- Name: td_audit_log_2024_11; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2024_11 (
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


ALTER TABLE ecos_security.td_audit_log_2024_11 OWNER TO postgres;

--
-- Name: td_audit_log_2024_12; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2024_12 (
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


ALTER TABLE ecos_security.td_audit_log_2024_12 OWNER TO postgres;

--
-- Name: td_audit_log_2025_01; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2025_01 (
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


ALTER TABLE ecos_security.td_audit_log_2025_01 OWNER TO postgres;

--
-- Name: td_audit_log_2025_02; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2025_02 (
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


ALTER TABLE ecos_security.td_audit_log_2025_02 OWNER TO postgres;

--
-- Name: td_audit_log_2025_03; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2025_03 (
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


ALTER TABLE ecos_security.td_audit_log_2025_03 OWNER TO postgres;

--
-- Name: td_audit_log_2025_04; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2025_04 (
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


ALTER TABLE ecos_security.td_audit_log_2025_04 OWNER TO postgres;

--
-- Name: td_audit_log_2025_05; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2025_05 (
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


ALTER TABLE ecos_security.td_audit_log_2025_05 OWNER TO postgres;

--
-- Name: td_audit_log_2025_06; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2025_06 (
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


ALTER TABLE ecos_security.td_audit_log_2025_06 OWNER TO postgres;

--
-- Name: td_audit_log_2025_07; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2025_07 (
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


ALTER TABLE ecos_security.td_audit_log_2025_07 OWNER TO postgres;

--
-- Name: td_audit_log_2025_08; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2025_08 (
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


ALTER TABLE ecos_security.td_audit_log_2025_08 OWNER TO postgres;

--
-- Name: td_audit_log_2025_09; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2025_09 (
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


ALTER TABLE ecos_security.td_audit_log_2025_09 OWNER TO postgres;

--
-- Name: td_audit_log_2025_10; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2025_10 (
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


ALTER TABLE ecos_security.td_audit_log_2025_10 OWNER TO postgres;

--
-- Name: td_audit_log_2025_11; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2025_11 (
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


ALTER TABLE ecos_security.td_audit_log_2025_11 OWNER TO postgres;

--
-- Name: td_audit_log_2025_12; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2025_12 (
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


ALTER TABLE ecos_security.td_audit_log_2025_12 OWNER TO postgres;

--
-- Name: td_audit_log_2026_01; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2026_01 (
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


ALTER TABLE ecos_security.td_audit_log_2026_01 OWNER TO postgres;

--
-- Name: td_audit_log_2026_02; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2026_02 (
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


ALTER TABLE ecos_security.td_audit_log_2026_02 OWNER TO postgres;

--
-- Name: td_audit_log_2026_03; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2026_03 (
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


ALTER TABLE ecos_security.td_audit_log_2026_03 OWNER TO postgres;

--
-- Name: td_audit_log_2026_04; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2026_04 (
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


ALTER TABLE ecos_security.td_audit_log_2026_04 OWNER TO postgres;

--
-- Name: td_audit_log_2026_05; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2026_05 (
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


ALTER TABLE ecos_security.td_audit_log_2026_05 OWNER TO postgres;

--
-- Name: td_audit_log_2026_06; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2026_06 (
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


ALTER TABLE ecos_security.td_audit_log_2026_06 OWNER TO postgres;

--
-- Name: td_audit_log_2026_07; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2026_07 (
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


ALTER TABLE ecos_security.td_audit_log_2026_07 OWNER TO postgres;

--
-- Name: td_audit_log_2026_08; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2026_08 (
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


ALTER TABLE ecos_security.td_audit_log_2026_08 OWNER TO postgres;

--
-- Name: td_audit_log_2026_09; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2026_09 (
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


ALTER TABLE ecos_security.td_audit_log_2026_09 OWNER TO postgres;

--
-- Name: td_audit_log_2026_10; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2026_10 (
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


ALTER TABLE ecos_security.td_audit_log_2026_10 OWNER TO postgres;

--
-- Name: td_audit_log_2026_11; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2026_11 (
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


ALTER TABLE ecos_security.td_audit_log_2026_11 OWNER TO postgres;

--
-- Name: td_audit_log_2026_12; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_2026_12 (
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


ALTER TABLE ecos_security.td_audit_log_2026_12 OWNER TO postgres;

--
-- Name: td_audit_log_default; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_audit_log_default (
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


ALTER TABLE ecos_security.td_audit_log_default OWNER TO postgres;

--
-- Name: td_role_security_profile; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_role_security_profile (
    role_id character varying(64) NOT NULL,
    clearance_level integer DEFAULT 0,
    linked_workstation character varying(256),
    audit_mode character varying(32) DEFAULT 'basic'::character varying,
    sandbox_mandatory boolean DEFAULT false,
    tenant_id character varying(64),
    org_id character varying(64),
    scope_type character varying(16) DEFAULT 'ROLE'::character varying,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_security.td_role_security_profile OWNER TO postgres;

--
-- Name: TABLE td_role_security_profile; Type: COMMENT; Schema: ecos_security; Owner: postgres
--

COMMENT ON TABLE ecos_security.td_role_security_profile IS '角色安全画像表';


--
-- Name: td_user_security_profile; Type: TABLE; Schema: ecos_security; Owner: postgres
--

CREATE TABLE ecos_security.td_user_security_profile (
    user_id character varying(64) NOT NULL,
    clearance_level integer DEFAULT 0,
    linked_workstation character varying(256),
    audit_mode character varying(32) DEFAULT 'basic'::character varying,
    sandbox_mandatory boolean DEFAULT false,
    is_default boolean DEFAULT false,
    tenant_id character varying(64),
    org_id character varying(64),
    scope_type character varying(16) DEFAULT 'USER'::character varying,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_security.td_user_security_profile OWNER TO postgres;

--
-- Name: TABLE td_user_security_profile; Type: COMMENT; Schema: ecos_security; Owner: postgres
--

COMMENT ON TABLE ecos_security.td_user_security_profile IS '用户安全画像表';


--
-- Name: COLUMN td_user_security_profile.clearance_level; Type: COMMENT; Schema: ecos_security; Owner: postgres
--

COMMENT ON COLUMN ecos_security.td_user_security_profile.clearance_level IS '安全许可级别';


--
-- Name: COLUMN td_user_security_profile.audit_mode; Type: COMMENT; Schema: ecos_security; Owner: postgres
--

COMMENT ON COLUMN ecos_security.td_user_security_profile.audit_mode IS '审计模式: basic/enhanced/full';


--
-- Name: COLUMN td_user_security_profile.sandbox_mandatory; Type: COMMENT; Schema: ecos_security; Owner: postgres
--

COMMENT ON COLUMN ecos_security.td_user_security_profile.sandbox_mandatory IS '是否强制沙箱';


--
-- Name: COLUMN td_user_security_profile.scope_type; Type: COMMENT; Schema: ecos_security; Owner: postgres
--

COMMENT ON COLUMN ecos_security.td_user_security_profile.scope_type IS '作用域类型: USER/ORG/GLOBAL';


--
-- Name: demo_customer; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.demo_customer (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    industry character varying(128),
    region character varying(64),
    level character varying(16),
    credit_score integer,
    status character varying(32) DEFAULT 'active'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_sysman.demo_customer OWNER TO postgres;

--
-- Name: TABLE demo_customer; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.demo_customer IS '演示客户表';


--
-- Name: demo_invoice; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.demo_invoice (
    id character varying(64) NOT NULL,
    amount numeric(12,2),
    customer_id character varying(64),
    supplier_id character varying(64),
    status character varying(32) DEFAULT 'pending'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_sysman.demo_invoice OWNER TO postgres;

--
-- Name: TABLE demo_invoice; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.demo_invoice IS '演示发票表';


--
-- Name: demo_supplier; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.demo_supplier (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    industry character varying(128),
    region character varying(64),
    level character varying(16),
    supply_capacity character varying(128),
    status character varying(32) DEFAULT 'active'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_sysman.demo_supplier OWNER TO postgres;

--
-- Name: TABLE demo_supplier; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.demo_supplier IS '演示供应商表';


--
-- Name: ecos_tenant; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.ecos_tenant (
    id character varying(32) NOT NULL,
    tenant_name character varying(64) NOT NULL,
    tenant_code character varying(32) NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE'::character varying,
    max_users integer DEFAULT 0,
    max_storage_mb bigint DEFAULT 0,
    max_api_per_day bigint DEFAULT 0,
    isolation_mode character varying(16) DEFAULT 'ROW_FILTER'::character varying,
    schema_name character varying(64),
    database_url character varying(256),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_sysman.ecos_tenant OWNER TO postgres;

--
-- Name: TABLE ecos_tenant; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.ecos_tenant IS '租户表';


--
-- Name: COLUMN ecos_tenant.isolation_mode; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.ecos_tenant.isolation_mode IS '隔离模式: ROW_FILTER/SCHEMA/DB';


--
-- Name: ecos_tenant_quota; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.ecos_tenant_quota (
    id bigint NOT NULL,
    tenant_id character varying(64) NOT NULL,
    quota_type character varying(32) NOT NULL,
    daily_limit bigint DEFAULT 0,
    monthly_limit bigint DEFAULT 0,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_sysman.ecos_tenant_quota OWNER TO postgres;

--
-- Name: TABLE ecos_tenant_quota; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.ecos_tenant_quota IS '租户配额表';


--
-- Name: ecos_tenant_quota_id_seq; Type: SEQUENCE; Schema: ecos_sysman; Owner: postgres
--

CREATE SEQUENCE ecos_sysman.ecos_tenant_quota_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE ecos_sysman.ecos_tenant_quota_id_seq OWNER TO postgres;

--
-- Name: ecos_tenant_quota_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_sysman; Owner: postgres
--

ALTER SEQUENCE ecos_sysman.ecos_tenant_quota_id_seq OWNED BY ecos_sysman.ecos_tenant_quota.id;


--
-- Name: ecos_tenant_usage; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.ecos_tenant_usage (
    tenant_id character varying(64) NOT NULL,
    usage_date date NOT NULL,
    quota_type character varying(32) NOT NULL,
    used_count bigint DEFAULT 0,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_sysman.ecos_tenant_usage OWNER TO postgres;

--
-- Name: TABLE ecos_tenant_usage; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.ecos_tenant_usage IS '租户用量表';


--
-- Name: sys_config; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.sys_config (
    id character varying(64) NOT NULL,
    config_group character varying(64) NOT NULL,
    config_key character varying(128) NOT NULL,
    config_value text,
    config_type character varying(32) DEFAULT 'string'::character varying,
    config_label character varying(255),
    config_label_en character varying(255),
    description text,
    sort_order integer DEFAULT 0,
    status character varying(32) DEFAULT 'active'::character varying,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_sysman.sys_config OWNER TO postgres;

--
-- Name: TABLE sys_config; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.sys_config IS '系统参数配置表';


--
-- Name: sys_dict; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.sys_dict (
    id character varying(64) NOT NULL,
    dict_type character varying(64) NOT NULL,
    dict_code character varying(128) NOT NULL,
    dict_label character varying(255),
    dict_label_en character varying(255),
    sort_order integer DEFAULT 0,
    status character varying(32) DEFAULT 'active'::character varying,
    parent_code character varying(128),
    ext_value character varying(255),
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_sysman.sys_dict OWNER TO postgres;

--
-- Name: TABLE sys_dict; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.sys_dict IS '数据字典表';


--
-- Name: td_config; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.td_config (
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


ALTER TABLE ecos_sysman.td_config OWNER TO postgres;

--
-- Name: TABLE td_config; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.td_config IS '系统配置表';


--
-- Name: td_org_permission; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.td_org_permission (
    permission_id character varying(64) NOT NULL,
    org_id character varying(64) NOT NULL,
    resource_id character varying(128),
    action character varying(64),
    inherit_from_parent character varying(1) DEFAULT '0'::character varying,
    created_time timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64)
);


ALTER TABLE ecos_sysman.td_org_permission OWNER TO postgres;

--
-- Name: TABLE td_org_permission; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.td_org_permission IS '组织权限表';


--
-- Name: td_organization; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.td_organization (
    "ORG_ID" character varying(64) NOT NULL,
    "ORG_NAME" character varying(256) NOT NULL,
    "ORG_CODE" character varying(128) NOT NULL,
    "PARENT_ORG_ID" character varying(64),
    "ORG_TYPE" character varying(32),
    "DESCRIPTION" character varying(512),
    "STATUS" character varying(16) DEFAULT 'ACTIVE'::character varying,
    "REMARK" character varying(512),
    "TENANT_ID" character varying(64),
    "CREATED_TIME" timestamp without time zone DEFAULT now() NOT NULL,
    "CREATED_BY" character varying(64),
    "UPDATED_TIME" timestamp without time zone DEFAULT now() NOT NULL,
    "UPDATED_BY" character varying(64)
);


ALTER TABLE ecos_sysman.td_organization OWNER TO postgres;

--
-- Name: TABLE td_organization; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.td_organization IS '组织机构表';


--
-- Name: COLUMN td_organization."ORG_ID"; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_organization."ORG_ID" IS '组织ID';


--
-- Name: COLUMN td_organization."ORG_NAME"; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_organization."ORG_NAME" IS '组织名称';


--
-- Name: COLUMN td_organization."ORG_CODE"; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_organization."ORG_CODE" IS '组织编码';


--
-- Name: COLUMN td_organization."PARENT_ORG_ID"; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_organization."PARENT_ORG_ID" IS '父组织ID';


--
-- Name: COLUMN td_organization."ORG_TYPE"; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_organization."ORG_TYPE" IS '组织类型';


--
-- Name: COLUMN td_organization."STATUS"; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_organization."STATUS" IS '状态';


--
-- Name: COLUMN td_organization."TENANT_ID"; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_organization."TENANT_ID" IS '租户ID';


--
-- Name: td_permission; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.td_permission (
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


ALTER TABLE ecos_sysman.td_permission OWNER TO postgres;

--
-- Name: TABLE td_permission; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.td_permission IS '权限表';


--
-- Name: COLUMN td_permission.permission_id; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_permission.permission_id IS '权限ID';


--
-- Name: COLUMN td_permission.permission_name; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_permission.permission_name IS '权限名称';


--
-- Name: COLUMN td_permission.permission_code; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_permission.permission_code IS '权限编码';


--
-- Name: COLUMN td_permission.resource_id; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_permission.resource_id IS '资源ID';


--
-- Name: COLUMN td_permission.action; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_permission.action IS '操作类型';


--
-- Name: COLUMN td_permission.description; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_permission.description IS '描述';


--
-- Name: COLUMN td_permission.tenant_id; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_permission.tenant_id IS '租户ID';


--
-- Name: td_role; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.td_role (
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
    status character varying(16) DEFAULT 'ACTIVE'::character varying,
    role_type character varying(32) DEFAULT 'CUSTOM'::character varying
);


ALTER TABLE ecos_sysman.td_role OWNER TO postgres;

--
-- Name: TABLE td_role; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.td_role IS '角色表';


--
-- Name: COLUMN td_role.role_id; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_role.role_id IS '角色ID';


--
-- Name: COLUMN td_role.role_name; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_role.role_name IS '角色名称';


--
-- Name: COLUMN td_role.role_code; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_role.role_code IS '角色编码';


--
-- Name: COLUMN td_role.description; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_role.description IS '描述';


--
-- Name: COLUMN td_role.parent_role_id; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_role.parent_role_id IS '父角色ID';


--
-- Name: COLUMN td_role.tenant_id; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_role.tenant_id IS '租户ID';


--
-- Name: COLUMN td_role.created_time; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_role.created_time IS '创建时间';


--
-- Name: COLUMN td_role.created_by; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_role.created_by IS '创建人';


--
-- Name: COLUMN td_role.updated_time; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_role.updated_time IS '更新时间';


--
-- Name: COLUMN td_role.updated_by; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_role.updated_by IS '更新人';


--
-- Name: td_role_permission; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.td_role_permission (
    role_id character varying(64) NOT NULL,
    permission_id character varying(64) NOT NULL,
    created_time timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64)
);


ALTER TABLE ecos_sysman.td_role_permission OWNER TO postgres;

--
-- Name: TABLE td_role_permission; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.td_role_permission IS '角色权限关联表';


--
-- Name: td_role_security_profile; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.td_role_security_profile (
    role_id character varying(64) NOT NULL,
    clearance_level integer DEFAULT 0,
    linked_workstation character varying(256),
    audit_mode character varying(32) DEFAULT 'basic'::character varying,
    sandbox_mandatory boolean DEFAULT false,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_sysman.td_role_security_profile OWNER TO postgres;

--
-- Name: TABLE td_role_security_profile; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.td_role_security_profile IS '????????????';


--
-- Name: COLUMN td_role_security_profile.clearance_level; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_role_security_profile.clearance_level IS '?????? 0-4: L0??? L1??? L2??? L3??? L4???';


--
-- Name: td_user; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.td_user (
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
    real_name character varying(128),
    org_id character varying(64)
);


ALTER TABLE ecos_sysman.td_user OWNER TO postgres;

--
-- Name: TABLE td_user; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.td_user IS '用户表';


--
-- Name: COLUMN td_user.user_id; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user.user_id IS '用户ID';


--
-- Name: COLUMN td_user.username; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user.username IS '用户名';


--
-- Name: COLUMN td_user.password; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user.password IS '密码哈希';


--
-- Name: COLUMN td_user.email; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user.email IS '邮箱';


--
-- Name: COLUMN td_user.mobile_tel1; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user.mobile_tel1 IS '手机号';


--
-- Name: COLUMN td_user.status; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user.status IS '状态: ACTIVE/LOCKED/DISABLED';


--
-- Name: COLUMN td_user.locked; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user.locked IS '是否锁定: 0/1';


--
-- Name: COLUMN td_user.lock_time; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user.lock_time IS '锁定时间';


--
-- Name: COLUMN td_user.last_login_time; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user.last_login_time IS '最后登录时间';


--
-- Name: COLUMN td_user.mfa_secret; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user.mfa_secret IS 'MFA密钥';


--
-- Name: COLUMN td_user.mfa_type; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user.mfa_type IS 'MFA类型';


--
-- Name: COLUMN td_user.mfa_enabled; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user.mfa_enabled IS '是否启用MFA';


--
-- Name: COLUMN td_user.tenant_id; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user.tenant_id IS '租户ID';


--
-- Name: COLUMN td_user.created_time; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user.created_time IS '创建时间';


--
-- Name: COLUMN td_user.created_by; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user.created_by IS '创建人';


--
-- Name: COLUMN td_user.updated_time; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user.updated_time IS '更新时间';


--
-- Name: COLUMN td_user.updated_by; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user.updated_by IS '更新人';


--
-- Name: COLUMN td_user.real_name; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user.real_name IS '真实姓名';


--
-- Name: td_user_organization; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.td_user_organization (
    "USER_ID" character varying(64) NOT NULL,
    "ORG_ID" character varying(64) NOT NULL,
    "IS_PRIMARY" character varying(1) DEFAULT '1'::character varying,
    "CREATED_TIME" timestamp without time zone DEFAULT now() NOT NULL,
    "CREATED_BY" character varying(64)
);


ALTER TABLE ecos_sysman.td_user_organization OWNER TO postgres;

--
-- Name: TABLE td_user_organization; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.td_user_organization IS '用户组织关联表';


--
-- Name: td_user_role; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.td_user_role (
    user_id character varying(64) NOT NULL,
    role_id character varying(64) NOT NULL,
    org_id character varying(64) DEFAULT '-1'::character varying,
    created_time timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64)
);


ALTER TABLE ecos_sysman.td_user_role OWNER TO postgres;

--
-- Name: TABLE td_user_role; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.td_user_role IS '用户角色关联表';


--
-- Name: COLUMN td_user_role.user_id; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user_role.user_id IS '用户ID';


--
-- Name: COLUMN td_user_role.role_id; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user_role.role_id IS '角色ID';


--
-- Name: COLUMN td_user_role.org_id; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user_role.org_id IS '组织ID';


--
-- Name: td_user_security_profile; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.td_user_security_profile (
    user_id character varying(64) NOT NULL,
    clearance_level integer DEFAULT 0,
    linked_workstation character varying(256),
    audit_mode character varying(32) DEFAULT 'basic'::character varying,
    sandbox_mandatory boolean DEFAULT false,
    is_default boolean DEFAULT false,
    tenant_id character varying(64),
    org_id character varying(64),
    scope_type character varying(16) DEFAULT 'USER'::character varying,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE ecos_sysman.td_user_security_profile OWNER TO postgres;

--
-- Name: TABLE td_user_security_profile; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.td_user_security_profile IS '????????????';


--
-- Name: COLUMN td_user_security_profile.clearance_level; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user_security_profile.clearance_level IS '?????? 0-4: L0??? L1??? L2??? L3??? L4???';


--
-- Name: COLUMN td_user_security_profile.is_default; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user_security_profile.is_default IS '??????????????';


--
-- Name: COLUMN td_user_security_profile.scope_type; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON COLUMN ecos_sysman.td_user_security_profile.scope_type IS '??????: USER/ORG/TENANT/GLOBAL';


--
-- Name: users; Type: TABLE; Schema: ecos_sysman; Owner: postgres
--

CREATE TABLE ecos_sysman.users (
    id character varying(64) NOT NULL,
    username character varying(128) NOT NULL,
    password_hash character varying(512) NOT NULL,
    display_name character varying(256),
    roles text,
    enabled boolean DEFAULT true,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE ecos_sysman.users OWNER TO postgres;

--
-- Name: TABLE users; Type: COMMENT; Schema: ecos_sysman; Owner: postgres
--

COMMENT ON TABLE ecos_sysman.users IS '简化用户表(认证用)';


--
-- Name: agent_cost_2025_01; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_01 FOR VALUES FROM ('2025-01-01 00:00:00') TO ('2025-02-01 00:00:00');


--
-- Name: agent_cost_2025_02; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_02 FOR VALUES FROM ('2025-02-01 00:00:00') TO ('2025-03-01 00:00:00');


--
-- Name: agent_cost_2025_03; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_03 FOR VALUES FROM ('2025-03-01 00:00:00') TO ('2025-04-01 00:00:00');


--
-- Name: agent_cost_2025_04; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_04 FOR VALUES FROM ('2025-04-01 00:00:00') TO ('2025-05-01 00:00:00');


--
-- Name: agent_cost_2025_05; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_05 FOR VALUES FROM ('2025-05-01 00:00:00') TO ('2025-06-01 00:00:00');


--
-- Name: agent_cost_2025_06; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_06 FOR VALUES FROM ('2025-06-01 00:00:00') TO ('2025-07-01 00:00:00');


--
-- Name: agent_cost_2025_07; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_07 FOR VALUES FROM ('2025-07-01 00:00:00') TO ('2025-08-01 00:00:00');


--
-- Name: agent_cost_2025_08; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_08 FOR VALUES FROM ('2025-08-01 00:00:00') TO ('2025-09-01 00:00:00');


--
-- Name: agent_cost_2025_09; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_09 FOR VALUES FROM ('2025-09-01 00:00:00') TO ('2025-10-01 00:00:00');


--
-- Name: agent_cost_2025_10; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_10 FOR VALUES FROM ('2025-10-01 00:00:00') TO ('2025-11-01 00:00:00');


--
-- Name: agent_cost_2025_11; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_11 FOR VALUES FROM ('2025-11-01 00:00:00') TO ('2025-12-01 00:00:00');


--
-- Name: agent_cost_2025_12; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2025_12 FOR VALUES FROM ('2025-12-01 00:00:00') TO ('2026-01-01 00:00:00');


--
-- Name: agent_cost_2026_01; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_01 FOR VALUES FROM ('2026-01-01 00:00:00') TO ('2026-02-01 00:00:00');


--
-- Name: agent_cost_2026_02; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_02 FOR VALUES FROM ('2026-02-01 00:00:00') TO ('2026-03-01 00:00:00');


--
-- Name: agent_cost_2026_03; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_03 FOR VALUES FROM ('2026-03-01 00:00:00') TO ('2026-04-01 00:00:00');


--
-- Name: agent_cost_2026_04; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_04 FOR VALUES FROM ('2026-04-01 00:00:00') TO ('2026-05-01 00:00:00');


--
-- Name: agent_cost_2026_05; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_05 FOR VALUES FROM ('2026-05-01 00:00:00') TO ('2026-06-01 00:00:00');


--
-- Name: agent_cost_2026_06; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_06 FOR VALUES FROM ('2026-06-01 00:00:00') TO ('2026-07-01 00:00:00');


--
-- Name: agent_cost_2026_07; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_07 FOR VALUES FROM ('2026-07-01 00:00:00') TO ('2026-08-01 00:00:00');


--
-- Name: agent_cost_2026_08; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_08 FOR VALUES FROM ('2026-08-01 00:00:00') TO ('2026-09-01 00:00:00');


--
-- Name: agent_cost_2026_09; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_09 FOR VALUES FROM ('2026-09-01 00:00:00') TO ('2026-10-01 00:00:00');


--
-- Name: agent_cost_2026_10; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_10 FOR VALUES FROM ('2026-10-01 00:00:00') TO ('2026-11-01 00:00:00');


--
-- Name: agent_cost_2026_11; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_11 FOR VALUES FROM ('2026-11-01 00:00:00') TO ('2026-12-01 00:00:00');


--
-- Name: agent_cost_2026_12; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_2026_12 FOR VALUES FROM ('2026-12-01 00:00:00') TO ('2027-01-01 00:00:00');


--
-- Name: agent_cost_default; Type: TABLE ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost ATTACH PARTITION ecos_ai.agent_cost_default DEFAULT;


--
-- Name: outbox_event_2025_01; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_01 FOR VALUES FROM ('2025-01-01 00:00:00+00') TO ('2025-02-01 00:00:00+00');


--
-- Name: outbox_event_2025_02; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_02 FOR VALUES FROM ('2025-02-01 00:00:00+00') TO ('2025-03-01 00:00:00+00');


--
-- Name: outbox_event_2025_03; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_03 FOR VALUES FROM ('2025-03-01 00:00:00+00') TO ('2025-04-01 00:00:00+00');


--
-- Name: outbox_event_2025_04; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_04 FOR VALUES FROM ('2025-04-01 00:00:00+00') TO ('2025-05-01 00:00:00+00');


--
-- Name: outbox_event_2025_05; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_05 FOR VALUES FROM ('2025-05-01 00:00:00+00') TO ('2025-06-01 00:00:00+00');


--
-- Name: outbox_event_2025_06; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_06 FOR VALUES FROM ('2025-06-01 00:00:00+00') TO ('2025-07-01 00:00:00+00');


--
-- Name: outbox_event_2025_07; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_07 FOR VALUES FROM ('2025-07-01 00:00:00+00') TO ('2025-08-01 00:00:00+00');


--
-- Name: outbox_event_2025_08; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_08 FOR VALUES FROM ('2025-08-01 00:00:00+00') TO ('2025-09-01 00:00:00+00');


--
-- Name: outbox_event_2025_09; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_09 FOR VALUES FROM ('2025-09-01 00:00:00+00') TO ('2025-10-01 00:00:00+00');


--
-- Name: outbox_event_2025_10; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_10 FOR VALUES FROM ('2025-10-01 00:00:00+00') TO ('2025-11-01 00:00:00+00');


--
-- Name: outbox_event_2025_11; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_11 FOR VALUES FROM ('2025-11-01 00:00:00+00') TO ('2025-12-01 00:00:00+00');


--
-- Name: outbox_event_2025_12; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2025_12 FOR VALUES FROM ('2025-12-01 00:00:00+00') TO ('2026-01-01 00:00:00+00');


--
-- Name: outbox_event_2026_01; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_01 FOR VALUES FROM ('2026-01-01 00:00:00+00') TO ('2026-02-01 00:00:00+00');


--
-- Name: outbox_event_2026_02; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_02 FOR VALUES FROM ('2026-02-01 00:00:00+00') TO ('2026-03-01 00:00:00+00');


--
-- Name: outbox_event_2026_03; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_03 FOR VALUES FROM ('2026-03-01 00:00:00+00') TO ('2026-04-01 00:00:00+00');


--
-- Name: outbox_event_2026_04; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_04 FOR VALUES FROM ('2026-04-01 00:00:00+00') TO ('2026-05-01 00:00:00+00');


--
-- Name: outbox_event_2026_05; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_05 FOR VALUES FROM ('2026-05-01 00:00:00+00') TO ('2026-06-01 00:00:00+00');


--
-- Name: outbox_event_2026_06; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_06 FOR VALUES FROM ('2026-06-01 00:00:00+00') TO ('2026-07-01 00:00:00+00');


--
-- Name: outbox_event_2026_07; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_07 FOR VALUES FROM ('2026-07-01 00:00:00+00') TO ('2026-08-01 00:00:00+00');


--
-- Name: outbox_event_2026_08; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_08 FOR VALUES FROM ('2026-08-01 00:00:00+00') TO ('2026-09-01 00:00:00+00');


--
-- Name: outbox_event_2026_09; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_09 FOR VALUES FROM ('2026-09-01 00:00:00+00') TO ('2026-10-01 00:00:00+00');


--
-- Name: outbox_event_2026_10; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_10 FOR VALUES FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00');


--
-- Name: outbox_event_2026_11; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_11 FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00');


--
-- Name: outbox_event_2026_12; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2026_12 FOR VALUES FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');


--
-- Name: outbox_event_2027_01; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_01 FOR VALUES FROM ('2027-01-01 00:00:00+00') TO ('2027-02-01 00:00:00+00');


--
-- Name: outbox_event_2027_02; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_02 FOR VALUES FROM ('2027-02-01 00:00:00+00') TO ('2027-03-01 00:00:00+00');


--
-- Name: outbox_event_2027_03; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_03 FOR VALUES FROM ('2027-03-01 00:00:00+00') TO ('2027-04-01 00:00:00+00');


--
-- Name: outbox_event_2027_04; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_04 FOR VALUES FROM ('2027-04-01 00:00:00+00') TO ('2027-05-01 00:00:00+00');


--
-- Name: outbox_event_2027_05; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_05 FOR VALUES FROM ('2027-05-01 00:00:00+00') TO ('2027-06-01 00:00:00+00');


--
-- Name: outbox_event_2027_06; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_06 FOR VALUES FROM ('2027-06-01 00:00:00+00') TO ('2027-07-01 00:00:00+00');


--
-- Name: outbox_event_2027_07; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_07 FOR VALUES FROM ('2027-07-01 00:00:00+00') TO ('2027-08-01 00:00:00+00');


--
-- Name: outbox_event_2027_08; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_08 FOR VALUES FROM ('2027-08-01 00:00:00+00') TO ('2027-09-01 00:00:00+00');


--
-- Name: outbox_event_2027_09; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_09 FOR VALUES FROM ('2027-09-01 00:00:00+00') TO ('2027-10-01 00:00:00+00');


--
-- Name: outbox_event_2027_10; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_10 FOR VALUES FROM ('2027-10-01 00:00:00+00') TO ('2027-11-01 00:00:00+00');


--
-- Name: outbox_event_2027_11; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_11 FOR VALUES FROM ('2027-11-01 00:00:00+00') TO ('2027-12-01 00:00:00+00');


--
-- Name: outbox_event_2027_12; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_2027_12 FOR VALUES FROM ('2027-12-01 00:00:00+00') TO ('2028-01-01 00:00:00+00');


--
-- Name: outbox_event_default; Type: TABLE ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_default DEFAULT;


--
-- Name: ecos_token_usage_2025_q1; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage ATTACH PARTITION ecos_security.ecos_token_usage_2025_q1 FOR VALUES FROM ('2025-01-01 00:00:00') TO ('2025-04-01 00:00:00');


--
-- Name: ecos_token_usage_2025_q2; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage ATTACH PARTITION ecos_security.ecos_token_usage_2025_q2 FOR VALUES FROM ('2025-04-01 00:00:00') TO ('2025-07-01 00:00:00');


--
-- Name: ecos_token_usage_2025_q3; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage ATTACH PARTITION ecos_security.ecos_token_usage_2025_q3 FOR VALUES FROM ('2025-07-01 00:00:00') TO ('2025-10-01 00:00:00');


--
-- Name: ecos_token_usage_2025_q4; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage ATTACH PARTITION ecos_security.ecos_token_usage_2025_q4 FOR VALUES FROM ('2025-10-01 00:00:00') TO ('2026-01-01 00:00:00');


--
-- Name: ecos_token_usage_2026_q1; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage ATTACH PARTITION ecos_security.ecos_token_usage_2026_q1 FOR VALUES FROM ('2026-01-01 00:00:00') TO ('2026-04-01 00:00:00');


--
-- Name: ecos_token_usage_2026_q2; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage ATTACH PARTITION ecos_security.ecos_token_usage_2026_q2 FOR VALUES FROM ('2026-04-01 00:00:00') TO ('2026-07-01 00:00:00');


--
-- Name: ecos_token_usage_2026_q3; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage ATTACH PARTITION ecos_security.ecos_token_usage_2026_q3 FOR VALUES FROM ('2026-07-01 00:00:00') TO ('2026-10-01 00:00:00');


--
-- Name: ecos_token_usage_2026_q4; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage ATTACH PARTITION ecos_security.ecos_token_usage_2026_q4 FOR VALUES FROM ('2026-10-01 00:00:00') TO ('2027-01-01 00:00:00');


--
-- Name: ecos_token_usage_default; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage ATTACH PARTITION ecos_security.ecos_token_usage_default DEFAULT;


--
-- Name: td_audit_log_2024_01; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2024_01 FOR VALUES FROM ('2024-01-01 00:00:00') TO ('2024-02-01 00:00:00');


--
-- Name: td_audit_log_2024_02; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2024_02 FOR VALUES FROM ('2024-02-01 00:00:00') TO ('2024-03-01 00:00:00');


--
-- Name: td_audit_log_2024_03; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2024_03 FOR VALUES FROM ('2024-03-01 00:00:00') TO ('2024-04-01 00:00:00');


--
-- Name: td_audit_log_2024_04; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2024_04 FOR VALUES FROM ('2024-04-01 00:00:00') TO ('2024-05-01 00:00:00');


--
-- Name: td_audit_log_2024_05; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2024_05 FOR VALUES FROM ('2024-05-01 00:00:00') TO ('2024-06-01 00:00:00');


--
-- Name: td_audit_log_2024_06; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2024_06 FOR VALUES FROM ('2024-06-01 00:00:00') TO ('2024-07-01 00:00:00');


--
-- Name: td_audit_log_2024_07; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2024_07 FOR VALUES FROM ('2024-07-01 00:00:00') TO ('2024-08-01 00:00:00');


--
-- Name: td_audit_log_2024_08; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2024_08 FOR VALUES FROM ('2024-08-01 00:00:00') TO ('2024-09-01 00:00:00');


--
-- Name: td_audit_log_2024_09; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2024_09 FOR VALUES FROM ('2024-09-01 00:00:00') TO ('2024-10-01 00:00:00');


--
-- Name: td_audit_log_2024_10; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2024_10 FOR VALUES FROM ('2024-10-01 00:00:00') TO ('2024-11-01 00:00:00');


--
-- Name: td_audit_log_2024_11; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2024_11 FOR VALUES FROM ('2024-11-01 00:00:00') TO ('2024-12-01 00:00:00');


--
-- Name: td_audit_log_2024_12; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2024_12 FOR VALUES FROM ('2024-12-01 00:00:00') TO ('2025-01-01 00:00:00');


--
-- Name: td_audit_log_2025_01; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2025_01 FOR VALUES FROM ('2025-01-01 00:00:00') TO ('2025-02-01 00:00:00');


--
-- Name: td_audit_log_2025_02; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2025_02 FOR VALUES FROM ('2025-02-01 00:00:00') TO ('2025-03-01 00:00:00');


--
-- Name: td_audit_log_2025_03; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2025_03 FOR VALUES FROM ('2025-03-01 00:00:00') TO ('2025-04-01 00:00:00');


--
-- Name: td_audit_log_2025_04; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2025_04 FOR VALUES FROM ('2025-04-01 00:00:00') TO ('2025-05-01 00:00:00');


--
-- Name: td_audit_log_2025_05; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2025_05 FOR VALUES FROM ('2025-05-01 00:00:00') TO ('2025-06-01 00:00:00');


--
-- Name: td_audit_log_2025_06; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2025_06 FOR VALUES FROM ('2025-06-01 00:00:00') TO ('2025-07-01 00:00:00');


--
-- Name: td_audit_log_2025_07; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2025_07 FOR VALUES FROM ('2025-07-01 00:00:00') TO ('2025-08-01 00:00:00');


--
-- Name: td_audit_log_2025_08; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2025_08 FOR VALUES FROM ('2025-08-01 00:00:00') TO ('2025-09-01 00:00:00');


--
-- Name: td_audit_log_2025_09; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2025_09 FOR VALUES FROM ('2025-09-01 00:00:00') TO ('2025-10-01 00:00:00');


--
-- Name: td_audit_log_2025_10; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2025_10 FOR VALUES FROM ('2025-10-01 00:00:00') TO ('2025-11-01 00:00:00');


--
-- Name: td_audit_log_2025_11; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2025_11 FOR VALUES FROM ('2025-11-01 00:00:00') TO ('2025-12-01 00:00:00');


--
-- Name: td_audit_log_2025_12; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2025_12 FOR VALUES FROM ('2025-12-01 00:00:00') TO ('2026-01-01 00:00:00');


--
-- Name: td_audit_log_2026_01; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2026_01 FOR VALUES FROM ('2026-01-01 00:00:00') TO ('2026-02-01 00:00:00');


--
-- Name: td_audit_log_2026_02; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2026_02 FOR VALUES FROM ('2026-02-01 00:00:00') TO ('2026-03-01 00:00:00');


--
-- Name: td_audit_log_2026_03; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2026_03 FOR VALUES FROM ('2026-03-01 00:00:00') TO ('2026-04-01 00:00:00');


--
-- Name: td_audit_log_2026_04; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2026_04 FOR VALUES FROM ('2026-04-01 00:00:00') TO ('2026-05-01 00:00:00');


--
-- Name: td_audit_log_2026_05; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2026_05 FOR VALUES FROM ('2026-05-01 00:00:00') TO ('2026-06-01 00:00:00');


--
-- Name: td_audit_log_2026_06; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2026_06 FOR VALUES FROM ('2026-06-01 00:00:00') TO ('2026-07-01 00:00:00');


--
-- Name: td_audit_log_2026_07; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2026_07 FOR VALUES FROM ('2026-07-01 00:00:00') TO ('2026-08-01 00:00:00');


--
-- Name: td_audit_log_2026_08; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2026_08 FOR VALUES FROM ('2026-08-01 00:00:00') TO ('2026-09-01 00:00:00');


--
-- Name: td_audit_log_2026_09; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2026_09 FOR VALUES FROM ('2026-09-01 00:00:00') TO ('2026-10-01 00:00:00');


--
-- Name: td_audit_log_2026_10; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2026_10 FOR VALUES FROM ('2026-10-01 00:00:00') TO ('2026-11-01 00:00:00');


--
-- Name: td_audit_log_2026_11; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2026_11 FOR VALUES FROM ('2026-11-01 00:00:00') TO ('2026-12-01 00:00:00');


--
-- Name: td_audit_log_2026_12; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_2026_12 FOR VALUES FROM ('2026-12-01 00:00:00') TO ('2027-01-01 00:00:00');


--
-- Name: td_audit_log_default; Type: TABLE ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log ATTACH PARTITION ecos_security.td_audit_log_default DEFAULT;


--
-- Name: ecos_decision_case id; Type: DEFAULT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.ecos_decision_case ALTER COLUMN id SET DEFAULT nextval('ecos_ai.ecos_decision_case_id_seq'::regclass);


--
-- Name: ecos_biz_metric id; Type: DEFAULT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_metric ALTER COLUMN id SET DEFAULT nextval('ecos_cognitive.ecos_biz_metric_id_seq'::regclass);


--
-- Name: ecos_biz_target id; Type: DEFAULT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_target ALTER COLUMN id SET DEFAULT nextval('ecos_cognitive.ecos_biz_target_id_seq'::regclass);


--
-- Name: ecos_wm_causal_link id; Type: DEFAULT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_causal_link ALTER COLUMN id SET DEFAULT nextval('ecos_cognitive.ecos_wm_causal_link_id_seq'::regclass);


--
-- Name: ecos_wm_goal id; Type: DEFAULT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_goal ALTER COLUMN id SET DEFAULT nextval('ecos_cognitive.ecos_wm_goal_id_seq'::regclass);


--
-- Name: ecos_wm_goal_log id; Type: DEFAULT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_goal_log ALTER COLUMN id SET DEFAULT nextval('ecos_cognitive.ecos_wm_goal_log_id_seq'::regclass);


--
-- Name: ecos_wm_scenario id; Type: DEFAULT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_scenario ALTER COLUMN id SET DEFAULT nextval('ecos_cognitive.ecos_wm_scenario_id_seq'::regclass);


--
-- Name: ecos_agent_alert id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_agent_alert ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_agent_alert_id_seq'::regclass);


--
-- Name: ecos_agent_metrics id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_agent_metrics ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_agent_metrics_id_seq'::regclass);


--
-- Name: ecos_alert_history id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_alert_history ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_alert_history_id_seq'::regclass);


--
-- Name: ecos_alert_rule id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_alert_rule ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_alert_rule_id_seq'::regclass);


--
-- Name: ecos_audit_log id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_audit_log ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_audit_log_id_seq'::regclass);


--
-- Name: ecos_cron_job id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_cron_job ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_cron_job_id_seq'::regclass);


--
-- Name: ecos_cron_job_execution id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_cron_job_execution ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_cron_job_execution_id_seq'::regclass);


--
-- Name: ecos_decision_case id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_decision_case ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_decision_case_id_seq'::regclass);


--
-- Name: ecos_dq_rule_v2 id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_dq_rule_v2 ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_dq_rule_v2_id_seq'::regclass);


--
-- Name: ecos_function_audit_log id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_function_audit_log ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_function_audit_log_id_seq'::regclass);


--
-- Name: ecos_glossary_term id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_glossary_term ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_glossary_term_id_seq'::regclass);


--
-- Name: ecos_glossary_term_relation id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_glossary_term_relation ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_glossary_term_relation_id_seq'::regclass);


--
-- Name: ecos_marketplace_access_request id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_marketplace_access_request ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_marketplace_access_request_id_seq'::regclass);


--
-- Name: ecos_marketplace_asset id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_marketplace_asset ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_marketplace_asset_id_seq'::regclass);


--
-- Name: ecos_object_attachment id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_object_attachment ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_object_attachment_id_seq'::regclass);


--
-- Name: ecos_object_version id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_object_version ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_object_version_id_seq'::regclass);


--
-- Name: ecos_ontology_proposals id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_ontology_proposals ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_ontology_proposals_id_seq'::regclass);


--
-- Name: ecos_skill id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_skill ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_skill_id_seq'::regclass);


--
-- Name: ecos_tenant_quota id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_tenant_quota ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_tenant_quota_id_seq'::regclass);


--
-- Name: ecos_token_usage id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_token_usage ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_token_usage_id_seq'::regclass);


--
-- Name: ecos_wm_causal_link id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_wm_causal_link ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_wm_causal_link_id_seq'::regclass);


--
-- Name: ecos_wm_goal id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_wm_goal ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_wm_goal_id_seq'::regclass);


--
-- Name: ecos_wm_goal_log id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_wm_goal_log ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_wm_goal_log_id_seq'::regclass);


--
-- Name: ecos_wm_scenario id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_wm_scenario ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_wm_scenario_id_seq'::regclass);


--
-- Name: ecos_workflow_log id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_workflow_log ALTER COLUMN id SET DEFAULT nextval('ecos_control.ecos_workflow_log_id_seq'::regclass);


--
-- Name: kb_cognitive_pipeline id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.kb_cognitive_pipeline ALTER COLUMN id SET DEFAULT nextval('ecos_control.kb_cognitive_pipeline_id_seq'::regclass);


--
-- Name: kb_lineage_event id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.kb_lineage_event ALTER COLUMN id SET DEFAULT nextval('ecos_control.kb_lineage_event_id_seq'::regclass);


--
-- Name: schema_changes id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.schema_changes ALTER COLUMN id SET DEFAULT nextval('ecos_control.schema_changes_id_seq'::regclass);


--
-- Name: schema_snapshots id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.schema_snapshots ALTER COLUMN id SET DEFAULT nextval('ecos_control.schema_snapshots_id_seq'::regclass);


--
-- Name: sys_agent_message id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.sys_agent_message ALTER COLUMN id SET DEFAULT nextval('ecos_control.sys_agent_message_id_seq'::regclass);


--
-- Name: sys_audit_log id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.sys_audit_log ALTER COLUMN id SET DEFAULT nextval('ecos_control.sys_audit_log_id_seq'::regclass);


--
-- Name: tb_menu_module IDS; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.tb_menu_module ALTER COLUMN "IDS" SET DEFAULT nextval('ecos_control."tb_menu_module_IDS_seq"'::regclass);


--
-- Name: td_metadata_collect_log id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_metadata_collect_log ALTER COLUMN id SET DEFAULT nextval('ecos_control.td_metadata_collect_log_id_seq'::regclass);


--
-- Name: td_runtime_task_log LOG_ID; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_runtime_task_log ALTER COLUMN "LOG_ID" SET DEFAULT nextval('ecos_control."td_runtime_task_log_LOG_ID_seq"'::regclass);


--
-- Name: 员工 id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control."员工" ALTER COLUMN id SET DEFAULT nextval('ecos_control."员工_id_seq"'::regclass);


--
-- Name: 数据目录 id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control."数据目录" ALTER COLUMN id SET DEFAULT nextval('ecos_control."数据目录_id_seq"'::regclass);


--
-- Name: 经营数据 id; Type: DEFAULT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control."经营数据" ALTER COLUMN id SET DEFAULT nextval('ecos_control."经营数据_id_seq"'::regclass);


--
-- Name: ecos_cognitive_rule id; Type: DEFAULT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_cognitive_rule ALTER COLUMN id SET DEFAULT nextval('ecos_data.ecos_cognitive_rule_id_seq'::regclass);


--
-- Name: ecos_dq_issue id; Type: DEFAULT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_dq_issue ALTER COLUMN id SET DEFAULT nextval('ecos_data.ecos_dq_issue_id_seq'::regclass);


--
-- Name: ecos_dq_rule id; Type: DEFAULT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_dq_rule ALTER COLUMN id SET DEFAULT nextval('ecos_data.ecos_dq_rule_id_seq'::regclass);


--
-- Name: ecos_task id; Type: DEFAULT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_task ALTER COLUMN id SET DEFAULT nextval('ecos_data.ecos_task_id_seq'::regclass);


--
-- Name: dq_score_snapshot id; Type: DEFAULT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.dq_score_snapshot ALTER COLUMN id SET DEFAULT nextval('ecos_dq.dq_score_snapshot_id_seq'::regclass);


--
-- Name: schema_changes id; Type: DEFAULT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.schema_changes ALTER COLUMN id SET DEFAULT nextval('ecos_dq.schema_changes_id_seq'::regclass);


--
-- Name: schema_snapshots id; Type: DEFAULT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.schema_snapshots ALTER COLUMN id SET DEFAULT nextval('ecos_dq.schema_snapshots_id_seq'::regclass);


--
-- Name: outbox_event id; Type: DEFAULT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event ALTER COLUMN id SET DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass);


--
-- Name: ecos_glossary_term id; Type: DEFAULT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.ecos_glossary_term ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.ecos_glossary_term_id_seq'::regclass);


--
-- Name: ecos_marketplace_access_request id; Type: DEFAULT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.ecos_marketplace_access_request ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.ecos_marketplace_access_request_id_seq'::regclass);


--
-- Name: ecos_marketplace_asset id; Type: DEFAULT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.ecos_marketplace_asset ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.ecos_marketplace_asset_id_seq'::regclass);


--
-- Name: kb_extract_audit id; Type: DEFAULT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.kb_extract_audit ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.kb_extract_audit_id_seq'::regclass);


--
-- Name: kb_extract_candidate id; Type: DEFAULT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.kb_extract_candidate ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.kb_extract_candidate_id_seq'::regclass);


--
-- Name: kb_extract_watermark id; Type: DEFAULT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.kb_extract_watermark ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.kb_extract_watermark_id_seq'::regclass);


--
-- Name: kb_ontology_snapshot id; Type: DEFAULT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.kb_ontology_snapshot ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.kb_ontology_snapshot_id_seq'::regclass);


--
-- Name: kb_scheduled_extract id; Type: DEFAULT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.kb_scheduled_extract ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.kb_scheduled_extract_id_seq'::regclass);


--
-- Name: kg_sync_log id; Type: DEFAULT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.kg_sync_log ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.kg_sync_log_id_seq'::regclass);


--
-- Name: ecos_token_usage id; Type: DEFAULT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage ALTER COLUMN id SET DEFAULT nextval('ecos_security.ecos_token_usage_id_seq'::regclass);


--
-- Name: ecos_tenant_quota id; Type: DEFAULT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.ecos_tenant_quota ALTER COLUMN id SET DEFAULT nextval('ecos_sysman.ecos_tenant_quota_id_seq'::regclass);


--
-- Name: agent_approval agent_approval_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_approval
    ADD CONSTRAINT agent_approval_pkey PRIMARY KEY (id);


--
-- Name: agent_cost agent_cost_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost
    ADD CONSTRAINT agent_cost_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_01 agent_cost_2025_01_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_01
    ADD CONSTRAINT agent_cost_2025_01_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_02 agent_cost_2025_02_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_02
    ADD CONSTRAINT agent_cost_2025_02_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_03 agent_cost_2025_03_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_03
    ADD CONSTRAINT agent_cost_2025_03_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_04 agent_cost_2025_04_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_04
    ADD CONSTRAINT agent_cost_2025_04_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_05 agent_cost_2025_05_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_05
    ADD CONSTRAINT agent_cost_2025_05_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_06 agent_cost_2025_06_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_06
    ADD CONSTRAINT agent_cost_2025_06_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_07 agent_cost_2025_07_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_07
    ADD CONSTRAINT agent_cost_2025_07_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_08 agent_cost_2025_08_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_08
    ADD CONSTRAINT agent_cost_2025_08_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_09 agent_cost_2025_09_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_09
    ADD CONSTRAINT agent_cost_2025_09_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_10 agent_cost_2025_10_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_10
    ADD CONSTRAINT agent_cost_2025_10_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_11 agent_cost_2025_11_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_11
    ADD CONSTRAINT agent_cost_2025_11_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2025_12 agent_cost_2025_12_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2025_12
    ADD CONSTRAINT agent_cost_2025_12_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_01 agent_cost_2026_01_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_01
    ADD CONSTRAINT agent_cost_2026_01_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_02 agent_cost_2026_02_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_02
    ADD CONSTRAINT agent_cost_2026_02_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_03 agent_cost_2026_03_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_03
    ADD CONSTRAINT agent_cost_2026_03_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_04 agent_cost_2026_04_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_04
    ADD CONSTRAINT agent_cost_2026_04_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_05 agent_cost_2026_05_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_05
    ADD CONSTRAINT agent_cost_2026_05_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_06 agent_cost_2026_06_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_06
    ADD CONSTRAINT agent_cost_2026_06_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_07 agent_cost_2026_07_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_07
    ADD CONSTRAINT agent_cost_2026_07_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_08 agent_cost_2026_08_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_08
    ADD CONSTRAINT agent_cost_2026_08_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_09 agent_cost_2026_09_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_09
    ADD CONSTRAINT agent_cost_2026_09_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_10 agent_cost_2026_10_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_10
    ADD CONSTRAINT agent_cost_2026_10_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_11 agent_cost_2026_11_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_11
    ADD CONSTRAINT agent_cost_2026_11_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_2026_12 agent_cost_2026_12_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_2026_12
    ADD CONSTRAINT agent_cost_2026_12_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_cost_default agent_cost_default_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_cost_default
    ADD CONSTRAINT agent_cost_default_pkey PRIMARY KEY (id, created_at);


--
-- Name: agent_definition agent_definition_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_definition
    ADD CONSTRAINT agent_definition_pkey PRIMARY KEY (id);


--
-- Name: agent_evaluation agent_evaluation_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_evaluation
    ADD CONSTRAINT agent_evaluation_pkey PRIMARY KEY (id);


--
-- Name: agent_execution agent_execution_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_execution
    ADD CONSTRAINT agent_execution_pkey PRIMARY KEY (id);


--
-- Name: agent_execution_step agent_execution_step_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_execution_step
    ADD CONSTRAINT agent_execution_step_pkey PRIMARY KEY (id);


--
-- Name: agent_governance_policy agent_governance_policy_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_governance_policy
    ADD CONSTRAINT agent_governance_policy_pkey PRIMARY KEY (id);


--
-- Name: agent_memory agent_memory_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_memory
    ADD CONSTRAINT agent_memory_pkey PRIMARY KEY (id);


--
-- Name: causal_edge causal_edge_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.causal_edge
    ADD CONSTRAINT causal_edge_pkey PRIMARY KEY (id);


--
-- Name: ecos_agent ecos_agent_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.ecos_agent
    ADD CONSTRAINT ecos_agent_pkey PRIMARY KEY (id);


--
-- Name: ecos_agent_registry ecos_agent_registry_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.ecos_agent_registry
    ADD CONSTRAINT ecos_agent_registry_pkey PRIMARY KEY (id);


--
-- Name: ecos_decision_case ecos_decision_case_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.ecos_decision_case
    ADD CONSTRAINT ecos_decision_case_pkey PRIMARY KEY (id);


--
-- Name: ecos_mission ecos_mission_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.ecos_mission
    ADD CONSTRAINT ecos_mission_pkey PRIMARY KEY (id);


--
-- Name: ecos_mission_task ecos_mission_task_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.ecos_mission_task
    ADD CONSTRAINT ecos_mission_task_pkey PRIMARY KEY (id);


--
-- Name: ecos_tool_definition ecos_tool_definition_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.ecos_tool_definition
    ADD CONSTRAINT ecos_tool_definition_pkey PRIMARY KEY (id);


--
-- Name: forecast forecast_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.forecast
    ADD CONSTRAINT forecast_pkey PRIMARY KEY (id);


--
-- Name: optimization_job optimization_job_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.optimization_job
    ADD CONSTRAINT optimization_job_pkey PRIMARY KEY (id);


--
-- Name: scenario scenario_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.scenario
    ADD CONSTRAINT scenario_pkey PRIMARY KEY (id);


--
-- Name: simulation simulation_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.simulation
    ADD CONSTRAINT simulation_pkey PRIMARY KEY (id);


--
-- Name: simulation_result simulation_result_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.simulation_result
    ADD CONSTRAINT simulation_result_pkey PRIMARY KEY (id);


--
-- Name: strategy_recommendation strategy_recommendation_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.strategy_recommendation
    ADD CONSTRAINT strategy_recommendation_pkey PRIMARY KEY (id);


--
-- Name: sys_agent_call_log sys_agent_call_log_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.sys_agent_call_log
    ADD CONSTRAINT sys_agent_call_log_pkey PRIMARY KEY (id);


--
-- Name: sys_agent_profile sys_agent_profile_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.sys_agent_profile
    ADD CONSTRAINT sys_agent_profile_pkey PRIMARY KEY (id);


--
-- Name: world_snapshot world_snapshot_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.world_snapshot
    ADD CONSTRAINT world_snapshot_pkey PRIMARY KEY (id);


--
-- Name: world_state world_state_pkey; Type: CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.world_state
    ADD CONSTRAINT world_state_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_contract ecos_biz_contract_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_contract
    ADD CONSTRAINT ecos_biz_contract_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_department ecos_biz_department_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_department
    ADD CONSTRAINT ecos_biz_department_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_metric ecos_biz_metric_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_metric
    ADD CONSTRAINT ecos_biz_metric_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_project ecos_biz_project_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_project
    ADD CONSTRAINT ecos_biz_project_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_target ecos_biz_target_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_target
    ADD CONSTRAINT ecos_biz_target_pkey PRIMARY KEY (id);


--
-- Name: ecos_goal_tracking ecos_goal_tracking_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_goal_tracking
    ADD CONSTRAINT ecos_goal_tracking_pkey PRIMARY KEY (id);


--
-- Name: ecos_wm_causal_link ecos_wm_causal_link_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_causal_link
    ADD CONSTRAINT ecos_wm_causal_link_pkey PRIMARY KEY (id);


--
-- Name: ecos_wm_goal_log ecos_wm_goal_log_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_goal_log
    ADD CONSTRAINT ecos_wm_goal_log_pkey PRIMARY KEY (id);


--
-- Name: ecos_wm_goal ecos_wm_goal_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_goal
    ADD CONSTRAINT ecos_wm_goal_pkey PRIMARY KEY (id);


--
-- Name: ecos_wm_scenario ecos_wm_scenario_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_scenario
    ADD CONSTRAINT ecos_wm_scenario_pkey PRIMARY KEY (id);


--
-- Name: ecos_world_scenarios ecos_world_scenarios_pkey; Type: CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_world_scenarios
    ADD CONSTRAINT ecos_world_scenarios_pkey PRIMARY KEY (id);


--
-- Name: crypto_audit_ledger crypto_audit_ledger_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.crypto_audit_ledger
    ADD CONSTRAINT crypto_audit_ledger_pkey PRIMARY KEY (id);


--
-- Name: dict_column dict_column_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.dict_column
    ADD CONSTRAINT dict_column_pkey PRIMARY KEY (id);


--
-- Name: dict_table dict_table_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.dict_table
    ADD CONSTRAINT dict_table_pkey PRIMARY KEY (id);


--
-- Name: ecos_action_type ecos_action_type_name_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_action_type
    ADD CONSTRAINT ecos_action_type_name_key UNIQUE (name);


--
-- Name: ecos_action_type ecos_action_type_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_action_type
    ADD CONSTRAINT ecos_action_type_pkey PRIMARY KEY (id);


--
-- Name: ecos_agent_alert ecos_agent_alert_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_agent_alert
    ADD CONSTRAINT ecos_agent_alert_pkey PRIMARY KEY (id);


--
-- Name: ecos_agent_execution ecos_agent_execution_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_agent_execution
    ADD CONSTRAINT ecos_agent_execution_pkey PRIMARY KEY (id);


--
-- Name: ecos_agent_execution_step ecos_agent_execution_step_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_agent_execution_step
    ADD CONSTRAINT ecos_agent_execution_step_pkey PRIMARY KEY (id);


--
-- Name: ecos_agent_memory ecos_agent_memory_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_agent_memory
    ADD CONSTRAINT ecos_agent_memory_pkey PRIMARY KEY (id);


--
-- Name: ecos_agent_metrics ecos_agent_metrics_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_agent_metrics
    ADD CONSTRAINT ecos_agent_metrics_pkey PRIMARY KEY (id);


--
-- Name: ecos_agent ecos_agent_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_agent
    ADD CONSTRAINT ecos_agent_pkey PRIMARY KEY (id);


--
-- Name: ecos_agent_registry ecos_agent_registry_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_agent_registry
    ADD CONSTRAINT ecos_agent_registry_pkey PRIMARY KEY (id);


--
-- Name: ecos_agent_version ecos_agent_version_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_agent_version
    ADD CONSTRAINT ecos_agent_version_pkey PRIMARY KEY (id);


--
-- Name: ecos_alert_history ecos_alert_history_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_alert_history
    ADD CONSTRAINT ecos_alert_history_pkey PRIMARY KEY (id);


--
-- Name: ecos_alert_rule ecos_alert_rule_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_alert_rule
    ADD CONSTRAINT ecos_alert_rule_pkey PRIMARY KEY (id);


--
-- Name: ecos_audit_log ecos_audit_log_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_audit_log
    ADD CONSTRAINT ecos_audit_log_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_contract ecos_biz_contract_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_biz_contract
    ADD CONSTRAINT ecos_biz_contract_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_department ecos_biz_department_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_biz_department
    ADD CONSTRAINT ecos_biz_department_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_metric ecos_biz_metric_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_biz_metric
    ADD CONSTRAINT ecos_biz_metric_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_project ecos_biz_project_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_biz_project
    ADD CONSTRAINT ecos_biz_project_pkey PRIMARY KEY (id);


--
-- Name: ecos_biz_target ecos_biz_target_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_biz_target
    ADD CONSTRAINT ecos_biz_target_pkey PRIMARY KEY (id);


--
-- Name: ecos_business_glossary ecos_business_glossary_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_business_glossary
    ADD CONSTRAINT ecos_business_glossary_pkey PRIMARY KEY (id);


--
-- Name: ecos_business_glossary ecos_business_glossary_term_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_business_glossary
    ADD CONSTRAINT ecos_business_glossary_term_key UNIQUE (term);


--
-- Name: ecos_business_scenario ecos_business_scenario_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_business_scenario
    ADD CONSTRAINT ecos_business_scenario_pkey PRIMARY KEY (id);


--
-- Name: ecos_cls_policy ecos_cls_policy_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_cls_policy
    ADD CONSTRAINT ecos_cls_policy_pkey PRIMARY KEY (id);


--
-- Name: ecos_cognitive_belief ecos_cognitive_belief_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_cognitive_belief
    ADD CONSTRAINT ecos_cognitive_belief_pkey PRIMARY KEY (id);


--
-- Name: ecos_cognitive_evidence ecos_cognitive_evidence_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_cognitive_evidence
    ADD CONSTRAINT ecos_cognitive_evidence_pkey PRIMARY KEY (id);


--
-- Name: ecos_cognitive_hypothesis ecos_cognitive_hypothesis_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_cognitive_hypothesis
    ADD CONSTRAINT ecos_cognitive_hypothesis_pkey PRIMARY KEY (id);


--
-- Name: ecos_cognitive_model ecos_cognitive_model_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_cognitive_model
    ADD CONSTRAINT ecos_cognitive_model_pkey PRIMARY KEY (id);


--
-- Name: ecos_cognitive_run_invalidation ecos_cognitive_run_invalidation_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_cognitive_run_invalidation
    ADD CONSTRAINT ecos_cognitive_run_invalidation_pkey PRIMARY KEY (id);


--
-- Name: ecos_cron_job_execution ecos_cron_job_execution_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_cron_job_execution
    ADD CONSTRAINT ecos_cron_job_execution_pkey PRIMARY KEY (id);


--
-- Name: ecos_cron_job ecos_cron_job_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_cron_job
    ADD CONSTRAINT ecos_cron_job_pkey PRIMARY KEY (id);


--
-- Name: ecos_data_lineage_edge ecos_data_lineage_edge_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_data_lineage_edge
    ADD CONSTRAINT ecos_data_lineage_edge_pkey PRIMARY KEY (id);


--
-- Name: ecos_data_lineage_node ecos_data_lineage_node_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_data_lineage_node
    ADD CONSTRAINT ecos_data_lineage_node_pkey PRIMARY KEY (id);


--
-- Name: ecos_data_pipeline_edge ecos_data_pipeline_edge_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_data_pipeline_edge
    ADD CONSTRAINT ecos_data_pipeline_edge_pkey PRIMARY KEY (id);


--
-- Name: ecos_data_pipeline_node ecos_data_pipeline_node_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_data_pipeline_node
    ADD CONSTRAINT ecos_data_pipeline_node_pkey PRIMARY KEY (id);


--
-- Name: ecos_data_pipeline ecos_data_pipeline_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_data_pipeline
    ADD CONSTRAINT ecos_data_pipeline_pkey PRIMARY KEY (id);


--
-- Name: ecos_data_request ecos_data_request_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_data_request
    ADD CONSTRAINT ecos_data_request_pkey PRIMARY KEY (id);


--
-- Name: ecos_decision_approval ecos_decision_approval_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_decision_approval
    ADD CONSTRAINT ecos_decision_approval_pkey PRIMARY KEY (id);


--
-- Name: ecos_decision_case ecos_decision_case_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_decision_case
    ADD CONSTRAINT ecos_decision_case_pkey PRIMARY KEY (id);


--
-- Name: ecos_decision_causal_link ecos_decision_causal_link_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_decision_causal_link
    ADD CONSTRAINT ecos_decision_causal_link_pkey PRIMARY KEY (id);


--
-- Name: ecos_decision_exception ecos_decision_exception_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_decision_exception
    ADD CONSTRAINT ecos_decision_exception_pkey PRIMARY KEY (id);


--
-- Name: ecos_decision ecos_decision_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_decision
    ADD CONSTRAINT ecos_decision_pkey PRIMARY KEY (id);


--
-- Name: ecos_decision_policy ecos_decision_policy_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_decision_policy
    ADD CONSTRAINT ecos_decision_policy_pkey PRIMARY KEY (id);


--
-- Name: ecos_decision_precedent ecos_decision_precedent_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_decision_precedent
    ADD CONSTRAINT ecos_decision_precedent_pkey PRIMARY KEY (id);


--
-- Name: ecos_decision_record ecos_decision_record_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_decision_record
    ADD CONSTRAINT ecos_decision_record_pkey PRIMARY KEY (id);


--
-- Name: ecos_decision_source_ref ecos_decision_source_ref_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_decision_source_ref
    ADD CONSTRAINT ecos_decision_source_ref_pkey PRIMARY KEY (id);


--
-- Name: ecos_domain ecos_domain_code_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_domain
    ADD CONSTRAINT ecos_domain_code_key UNIQUE (code);


--
-- Name: ecos_domain ecos_domain_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_domain
    ADD CONSTRAINT ecos_domain_pkey PRIMARY KEY (id);


--
-- Name: ecos_dq_execution_result ecos_dq_execution_result_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_dq_execution_result
    ADD CONSTRAINT ecos_dq_execution_result_pkey PRIMARY KEY (id);


--
-- Name: ecos_dq_issue ecos_dq_issue_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_dq_issue
    ADD CONSTRAINT ecos_dq_issue_pkey PRIMARY KEY (id);


--
-- Name: ecos_dq_rule ecos_dq_rule_code_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_dq_rule
    ADD CONSTRAINT ecos_dq_rule_code_key UNIQUE (code);


--
-- Name: ecos_dq_rule ecos_dq_rule_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_dq_rule
    ADD CONSTRAINT ecos_dq_rule_pkey PRIMARY KEY (id);


--
-- Name: ecos_dq_rule_v2 ecos_dq_rule_v2_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_dq_rule_v2
    ADD CONSTRAINT ecos_dq_rule_v2_pkey PRIMARY KEY (id);


--
-- Name: ecos_entity_table_mapping ecos_entity_table_mapping_entity_code_datasource_id_resourc_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_entity_table_mapping
    ADD CONSTRAINT ecos_entity_table_mapping_entity_code_datasource_id_resourc_key UNIQUE (entity_code, datasource_id, resource_name);


--
-- Name: ecos_entity_table_mapping ecos_entity_table_mapping_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_entity_table_mapping
    ADD CONSTRAINT ecos_entity_table_mapping_pkey PRIMARY KEY (id);


--
-- Name: ecos_function_audit_log ecos_function_audit_log_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_function_audit_log
    ADD CONSTRAINT ecos_function_audit_log_pkey PRIMARY KEY (id);


--
-- Name: ecos_glossary_term ecos_glossary_term_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_glossary_term
    ADD CONSTRAINT ecos_glossary_term_pkey PRIMARY KEY (id);


--
-- Name: ecos_glossary_term_relation ecos_glossary_term_relation_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_glossary_term_relation
    ADD CONSTRAINT ecos_glossary_term_relation_pkey PRIMARY KEY (id);


--
-- Name: ecos_goal_tracking ecos_goal_tracking_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_goal_tracking
    ADD CONSTRAINT ecos_goal_tracking_pkey PRIMARY KEY (id);


--
-- Name: ecos_knowledge_document ecos_knowledge_document_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_knowledge_document
    ADD CONSTRAINT ecos_knowledge_document_pkey PRIMARY KEY (id);


--
-- Name: ecos_knowledge_graph_edge ecos_knowledge_graph_edge_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_knowledge_graph_edge
    ADD CONSTRAINT ecos_knowledge_graph_edge_pkey PRIMARY KEY (id);


--
-- Name: ecos_knowledge_graph_node ecos_knowledge_graph_node_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_knowledge_graph_node
    ADD CONSTRAINT ecos_knowledge_graph_node_pkey PRIMARY KEY (id);


--
-- Name: ecos_marketplace_access_request ecos_marketplace_access_request_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_marketplace_access_request
    ADD CONSTRAINT ecos_marketplace_access_request_pkey PRIMARY KEY (id);


--
-- Name: ecos_marketplace_asset ecos_marketplace_asset_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_marketplace_asset
    ADD CONSTRAINT ecos_marketplace_asset_pkey PRIMARY KEY (id);


--
-- Name: ecos_mission ecos_mission_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_mission
    ADD CONSTRAINT ecos_mission_pkey PRIMARY KEY (id);


--
-- Name: ecos_mission_task ecos_mission_task_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_mission_task
    ADD CONSTRAINT ecos_mission_task_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_attachment ecos_object_attachment_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_object_attachment
    ADD CONSTRAINT ecos_object_attachment_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_data ecos_object_data_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_object_data
    ADD CONSTRAINT ecos_object_data_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_links ecos_object_links_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_object_links
    ADD CONSTRAINT ecos_object_links_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_relation ecos_object_relation_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_object_relation
    ADD CONSTRAINT ecos_object_relation_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_relationship ecos_object_relationship_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_object_relationship
    ADD CONSTRAINT ecos_object_relationship_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_state_machine ecos_object_state_machine_entity_code_from_status_transitio_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_object_state_machine
    ADD CONSTRAINT ecos_object_state_machine_entity_code_from_status_transitio_key UNIQUE (entity_code, from_status, transition_code);


--
-- Name: ecos_object_state_machine ecos_object_state_machine_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_object_state_machine
    ADD CONSTRAINT ecos_object_state_machine_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_timeline ecos_object_timeline_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_object_timeline
    ADD CONSTRAINT ecos_object_timeline_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_version ecos_object_version_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_object_version
    ADD CONSTRAINT ecos_object_version_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology_action ecos_ontology_action_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_ontology_action
    ADD CONSTRAINT ecos_ontology_action_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology_data ecos_ontology_data_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_ontology_data
    ADD CONSTRAINT ecos_ontology_data_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology_entity ecos_ontology_entity_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_ontology_entity
    ADD CONSTRAINT ecos_ontology_entity_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology ecos_ontology_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_ontology
    ADD CONSTRAINT ecos_ontology_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology_property ecos_ontology_property_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_ontology_property
    ADD CONSTRAINT ecos_ontology_property_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology_proposals ecos_ontology_proposals_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_ontology_proposals
    ADD CONSTRAINT ecos_ontology_proposals_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology_relationship ecos_ontology_relationship_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_ontology_relationship
    ADD CONSTRAINT ecos_ontology_relationship_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology_rule ecos_ontology_rule_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_ontology_rule
    ADD CONSTRAINT ecos_ontology_rule_pkey PRIMARY KEY (id);


--
-- Name: ecos_ontology_version ecos_ontology_version_ontology_id_version_no_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_ontology_version
    ADD CONSTRAINT ecos_ontology_version_ontology_id_version_no_key UNIQUE (ontology_id, version_no);


--
-- Name: ecos_ontology_version ecos_ontology_version_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_ontology_version
    ADD CONSTRAINT ecos_ontology_version_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_definition ecos_pipeline_definition_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_pipeline_definition
    ADD CONSTRAINT ecos_pipeline_definition_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_execution ecos_pipeline_execution_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_pipeline_execution
    ADD CONSTRAINT ecos_pipeline_execution_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_function ecos_pipeline_function_name_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_pipeline_function
    ADD CONSTRAINT ecos_pipeline_function_name_key UNIQUE (name);


--
-- Name: ecos_pipeline_function ecos_pipeline_function_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_pipeline_function
    ADD CONSTRAINT ecos_pipeline_function_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_node ecos_pipeline_node_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_pipeline_node
    ADD CONSTRAINT ecos_pipeline_node_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_run ecos_pipeline_run_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_pipeline_run
    ADD CONSTRAINT ecos_pipeline_run_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_step ecos_pipeline_step_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_pipeline_step
    ADD CONSTRAINT ecos_pipeline_step_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_step_run ecos_pipeline_step_run_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_pipeline_step_run
    ADD CONSTRAINT ecos_pipeline_step_run_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_task ecos_pipeline_task_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_pipeline_task
    ADD CONSTRAINT ecos_pipeline_task_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_udf ecos_pipeline_udf_name_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_pipeline_udf
    ADD CONSTRAINT ecos_pipeline_udf_name_key UNIQUE (name);


--
-- Name: ecos_pipeline_udf ecos_pipeline_udf_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_pipeline_udf
    ADD CONSTRAINT ecos_pipeline_udf_pkey PRIMARY KEY (id);


--
-- Name: ecos_provenance_entry ecos_provenance_entry_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_provenance_entry
    ADD CONSTRAINT ecos_provenance_entry_pkey PRIMARY KEY (id);


--
-- Name: ecos_quality_evaluation ecos_quality_evaluation_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_quality_evaluation
    ADD CONSTRAINT ecos_quality_evaluation_pkey PRIMARY KEY (id);


--
-- Name: ecos_quality_rule ecos_quality_rule_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_quality_rule
    ADD CONSTRAINT ecos_quality_rule_pkey PRIMARY KEY (rule_id);


--
-- Name: ecos_query_history ecos_query_history_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_query_history
    ADD CONSTRAINT ecos_query_history_pkey PRIMARY KEY (id);


--
-- Name: ecos_query_template ecos_query_template_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_query_template
    ADD CONSTRAINT ecos_query_template_pkey PRIMARY KEY (id);


--
-- Name: ecos_rls_policy ecos_rls_policy_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_rls_policy
    ADD CONSTRAINT ecos_rls_policy_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_active_mind ecos_scenario_active_mind_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_scenario_active_mind
    ADD CONSTRAINT ecos_scenario_active_mind_pkey PRIMARY KEY (scenario_id);


--
-- Name: ecos_scenario_asset_binding ecos_scenario_asset_binding_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_scenario_asset_binding
    ADD CONSTRAINT ecos_scenario_asset_binding_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_binding_edge ecos_scenario_binding_edge_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_scenario_binding_edge
    ADD CONSTRAINT ecos_scenario_binding_edge_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_binding_link ecos_scenario_binding_link_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_scenario_binding_link
    ADD CONSTRAINT ecos_scenario_binding_link_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_binding ecos_scenario_binding_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_scenario_binding
    ADD CONSTRAINT ecos_scenario_binding_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_canvas_layout ecos_scenario_canvas_layout_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_scenario_canvas_layout
    ADD CONSTRAINT ecos_scenario_canvas_layout_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_decision_record ecos_scenario_decision_record_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_scenario_decision_record
    ADD CONSTRAINT ecos_scenario_decision_record_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_definition ecos_scenario_definition_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_scenario_definition
    ADD CONSTRAINT ecos_scenario_definition_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_execution ecos_scenario_execution_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_scenario_execution
    ADD CONSTRAINT ecos_scenario_execution_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_mind ecos_scenario_mind_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_scenario_mind
    ADD CONSTRAINT ecos_scenario_mind_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_mind_ref ecos_scenario_mind_ref_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_scenario_mind_ref
    ADD CONSTRAINT ecos_scenario_mind_ref_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_mind_variant ecos_scenario_mind_variant_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_scenario_mind_variant
    ADD CONSTRAINT ecos_scenario_mind_variant_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_query_history ecos_scenario_query_history_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_scenario_query_history
    ADD CONSTRAINT ecos_scenario_query_history_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_run ecos_scenario_run_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_scenario_run
    ADD CONSTRAINT ecos_scenario_run_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_sandbox_layout ecos_scenario_sandbox_layout_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_scenario_sandbox_layout
    ADD CONSTRAINT ecos_scenario_sandbox_layout_pkey PRIMARY KEY (id);


--
-- Name: ecos_scenario_sandbox_layout ecos_scenario_sandbox_layout_scenario_id_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_scenario_sandbox_layout
    ADD CONSTRAINT ecos_scenario_sandbox_layout_scenario_id_key UNIQUE (scenario_id);


--
-- Name: ecos_skill ecos_skill_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_skill
    ADD CONSTRAINT ecos_skill_pkey PRIMARY KEY (id);


--
-- Name: ecos_spans ecos_spans_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_spans
    ADD CONSTRAINT ecos_spans_pkey PRIMARY KEY (span_id);


--
-- Name: ecos_tenant ecos_tenant_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_tenant
    ADD CONSTRAINT ecos_tenant_pkey PRIMARY KEY (id);


--
-- Name: ecos_tenant_quota ecos_tenant_quota_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_tenant_quota
    ADD CONSTRAINT ecos_tenant_quota_pkey PRIMARY KEY (id);


--
-- Name: ecos_tenant ecos_tenant_tenant_code_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_tenant
    ADD CONSTRAINT ecos_tenant_tenant_code_key UNIQUE (tenant_code);


--
-- Name: ecos_tenant_usage ecos_tenant_usage_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_tenant_usage
    ADD CONSTRAINT ecos_tenant_usage_pkey PRIMARY KEY (tenant_id, usage_date, quota_type);


--
-- Name: ecos_term_entity_binding ecos_term_entity_binding_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_term_entity_binding
    ADD CONSTRAINT ecos_term_entity_binding_pkey PRIMARY KEY (id);


--
-- Name: ecos_term_entity_binding ecos_term_entity_binding_term_id_entity_code_property_code_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_term_entity_binding
    ADD CONSTRAINT ecos_term_entity_binding_term_id_entity_code_property_code_key UNIQUE (term_id, entity_code, property_code);


--
-- Name: ecos_token_blacklist ecos_token_blacklist_jti_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_token_blacklist
    ADD CONSTRAINT ecos_token_blacklist_jti_key UNIQUE (jti);


--
-- Name: ecos_token_blacklist ecos_token_blacklist_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_token_blacklist
    ADD CONSTRAINT ecos_token_blacklist_pkey PRIMARY KEY (id);


--
-- Name: ecos_token_usage ecos_token_usage_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_token_usage
    ADD CONSTRAINT ecos_token_usage_pkey PRIMARY KEY (id);


--
-- Name: ecos_tool_definition ecos_tool_definition_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_tool_definition
    ADD CONSTRAINT ecos_tool_definition_pkey PRIMARY KEY (id);


--
-- Name: ecos_warn_log ecos_warn_log_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_warn_log
    ADD CONSTRAINT ecos_warn_log_pkey PRIMARY KEY (id);


--
-- Name: ecos_wm_causal_link ecos_wm_causal_link_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_wm_causal_link
    ADD CONSTRAINT ecos_wm_causal_link_pkey PRIMARY KEY (id);


--
-- Name: ecos_wm_goal_log ecos_wm_goal_log_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_wm_goal_log
    ADD CONSTRAINT ecos_wm_goal_log_pkey PRIMARY KEY (id);


--
-- Name: ecos_wm_goal ecos_wm_goal_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_wm_goal
    ADD CONSTRAINT ecos_wm_goal_pkey PRIMARY KEY (id);


--
-- Name: ecos_wm_scenario ecos_wm_scenario_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_wm_scenario
    ADD CONSTRAINT ecos_wm_scenario_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_edge ecos_workflow_edge_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_workflow_edge
    ADD CONSTRAINT ecos_workflow_edge_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_instance ecos_workflow_instance_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_workflow_instance
    ADD CONSTRAINT ecos_workflow_instance_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_log ecos_workflow_log_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_workflow_log
    ADD CONSTRAINT ecos_workflow_log_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_node ecos_workflow_node_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_workflow_node
    ADD CONSTRAINT ecos_workflow_node_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow ecos_workflow_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_workflow
    ADD CONSTRAINT ecos_workflow_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_task ecos_workflow_task_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_workflow_task
    ADD CONSTRAINT ecos_workflow_task_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_v2 ecos_workflow_v2_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_workflow_v2
    ADD CONSTRAINT ecos_workflow_v2_pkey PRIMARY KEY (id);


--
-- Name: ecos_working_memory ecos_working_memory_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_working_memory
    ADD CONSTRAINT ecos_working_memory_pkey PRIMARY KEY (id);


--
-- Name: ecos_world_causal_link ecos_world_causal_link_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_world_causal_link
    ADD CONSTRAINT ecos_world_causal_link_pkey PRIMARY KEY (id);


--
-- Name: ecos_world_goal ecos_world_goal_code_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_world_goal
    ADD CONSTRAINT ecos_world_goal_code_key UNIQUE (code);


--
-- Name: ecos_world_goal ecos_world_goal_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_world_goal
    ADD CONSTRAINT ecos_world_goal_pkey PRIMARY KEY (id);


--
-- Name: ecos_world_scenario ecos_world_scenario_code_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_world_scenario
    ADD CONSTRAINT ecos_world_scenario_code_key UNIQUE (code);


--
-- Name: ecos_world_scenario_impact ecos_world_scenario_impact_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_world_scenario_impact
    ADD CONSTRAINT ecos_world_scenario_impact_pkey PRIMARY KEY (id);


--
-- Name: ecos_world_scenario ecos_world_scenario_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_world_scenario
    ADD CONSTRAINT ecos_world_scenario_pkey PRIMARY KEY (id);


--
-- Name: ecos_world_scenarios ecos_world_scenarios_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_world_scenarios
    ADD CONSTRAINT ecos_world_scenarios_pkey PRIMARY KEY (id);


--
-- Name: extraction_drafts extraction_drafts_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.extraction_drafts
    ADD CONSTRAINT extraction_drafts_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: kb_cognitive_pipeline kb_cognitive_pipeline_pipeline_id_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.kb_cognitive_pipeline
    ADD CONSTRAINT kb_cognitive_pipeline_pipeline_id_key UNIQUE (pipeline_id);


--
-- Name: kb_cognitive_pipeline kb_cognitive_pipeline_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.kb_cognitive_pipeline
    ADD CONSTRAINT kb_cognitive_pipeline_pkey PRIMARY KEY (id);


--
-- Name: kb_lineage_event kb_lineage_event_event_id_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.kb_lineage_event
    ADD CONSTRAINT kb_lineage_event_event_id_key UNIQUE (event_id);


--
-- Name: kb_lineage_event kb_lineage_event_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.kb_lineage_event
    ADD CONSTRAINT kb_lineage_event_pkey PRIMARY KEY (id);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);


--
-- Name: roles roles_name_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.roles
    ADD CONSTRAINT roles_name_key UNIQUE (name);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: schema_changes schema_changes_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.schema_changes
    ADD CONSTRAINT schema_changes_pkey PRIMARY KEY (id);


--
-- Name: schema_snapshots schema_snapshots_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.schema_snapshots
    ADD CONSTRAINT schema_snapshots_pkey PRIMARY KEY (id);


--
-- Name: sys_agent_call_log sys_agent_call_log_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.sys_agent_call_log
    ADD CONSTRAINT sys_agent_call_log_pkey PRIMARY KEY (id);


--
-- Name: sys_agent_message sys_agent_message_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.sys_agent_message
    ADD CONSTRAINT sys_agent_message_pkey PRIMARY KEY (id);


--
-- Name: sys_agent_profile sys_agent_profile_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.sys_agent_profile
    ADD CONSTRAINT sys_agent_profile_pkey PRIMARY KEY (id);


--
-- Name: sys_agent_session sys_agent_session_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.sys_agent_session
    ADD CONSTRAINT sys_agent_session_pkey PRIMARY KEY (id);


--
-- Name: sys_audit_log sys_audit_log_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.sys_audit_log
    ADD CONSTRAINT sys_audit_log_pkey PRIMARY KEY (id);


--
-- Name: sys_compliance_rule sys_compliance_rule_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.sys_compliance_rule
    ADD CONSTRAINT sys_compliance_rule_pkey PRIMARY KEY (id);


--
-- Name: sys_config sys_config_config_key_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.sys_config
    ADD CONSTRAINT sys_config_config_key_key UNIQUE (config_key);


--
-- Name: sys_config sys_config_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.sys_config
    ADD CONSTRAINT sys_config_pkey PRIMARY KEY (id);


--
-- Name: sys_dict sys_dict_dict_type_dict_code_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.sys_dict
    ADD CONSTRAINT sys_dict_dict_type_dict_code_key UNIQUE (dict_type, dict_code);


--
-- Name: sys_dict sys_dict_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.sys_dict
    ADD CONSTRAINT sys_dict_pkey PRIMARY KEY (id);


--
-- Name: sys_token_blacklist sys_token_blacklist_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.sys_token_blacklist
    ADD CONSTRAINT sys_token_blacklist_pkey PRIMARY KEY (token);


--
-- Name: tb_menu_module tb_menu_module_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.tb_menu_module
    ADD CONSTRAINT tb_menu_module_pkey PRIMARY KEY ("IDS");


--
-- Name: td_abac_policy td_abac_policy_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_abac_policy
    ADD CONSTRAINT td_abac_policy_pkey PRIMARY KEY ("POLICY_ID");


--
-- Name: td_audit_log td_audit_log_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_audit_log
    ADD CONSTRAINT td_audit_log_pkey PRIMARY KEY ("LOG_ID");


--
-- Name: td_catalog_item td_catalog_item_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_catalog_item
    ADD CONSTRAINT td_catalog_item_pkey PRIMARY KEY (catalog_id);


--
-- Name: td_compliance_policy td_compliance_policy_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_compliance_policy
    ADD CONSTRAINT td_compliance_policy_pkey PRIMARY KEY ("POLICY_ID");


--
-- Name: td_config td_config_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_config
    ADD CONSTRAINT td_config_pkey PRIMARY KEY ("CONFIG_ID");


--
-- Name: td_config_version td_config_version_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_config_version
    ADD CONSTRAINT td_config_version_pkey PRIMARY KEY ("VERSION_ID");


--
-- Name: td_cross_border_transfer td_cross_border_transfer_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_cross_border_transfer
    ADD CONSTRAINT td_cross_border_transfer_pkey PRIMARY KEY ("TRANSFER_ID");


--
-- Name: td_crypto_key_audit td_crypto_key_audit_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_crypto_key_audit
    ADD CONSTRAINT td_crypto_key_audit_pkey PRIMARY KEY ("AUDIT_ID");


--
-- Name: td_crypto_key td_crypto_key_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_crypto_key
    ADD CONSTRAINT td_crypto_key_pkey PRIMARY KEY ("KEY_ID", "VERSION");


--
-- Name: td_crypto_master_key td_crypto_master_key_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_crypto_master_key
    ADD CONSTRAINT td_crypto_master_key_pkey PRIMARY KEY ("MASTER_KEY_ID");


--
-- Name: td_data_category td_data_category_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_data_category
    ADD CONSTRAINT td_data_category_pkey PRIMARY KEY (id);


--
-- Name: td_data_description td_data_description_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_data_description
    ADD CONSTRAINT td_data_description_pkey PRIMARY KEY ("ID");


--
-- Name: td_data_field td_data_field_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_data_field
    ADD CONSTRAINT td_data_field_pkey PRIMARY KEY (field_id);


--
-- Name: td_data_permission_policy td_data_permission_policy_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_data_permission_policy
    ADD CONSTRAINT td_data_permission_policy_pkey PRIMARY KEY ("POLICY_ID");


--
-- Name: td_data_residency td_data_residency_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_data_residency
    ADD CONSTRAINT td_data_residency_pkey PRIMARY KEY ("RESIDENCY_ID");


--
-- Name: td_data_resource td_data_resource_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_data_resource
    ADD CONSTRAINT td_data_resource_pkey PRIMARY KEY (resource_id);


--
-- Name: td_data_security_policy td_data_security_policy_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_data_security_policy
    ADD CONSTRAINT td_data_security_policy_pkey PRIMARY KEY (policy_id);


--
-- Name: td_datasource td_datasource_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_datasource
    ADD CONSTRAINT td_datasource_pkey PRIMARY KEY (datasource_id);


--
-- Name: td_git_repository td_git_repository_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_git_repository
    ADD CONSTRAINT td_git_repository_pkey PRIMARY KEY ("REPO_ID");


--
-- Name: td_ip_access td_ip_access_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_ip_access
    ADD CONSTRAINT td_ip_access_pkey PRIMARY KEY ("ACCESS_ID");


--
-- Name: td_metadata_collect_log td_metadata_collect_log_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_metadata_collect_log
    ADD CONSTRAINT td_metadata_collect_log_pkey PRIMARY KEY (id);


--
-- Name: td_org_permission td_org_permission_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_org_permission
    ADD CONSTRAINT td_org_permission_pkey PRIMARY KEY ("PERMISSION_ID");


--
-- Name: td_organization td_organization_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_organization
    ADD CONSTRAINT td_organization_pkey PRIMARY KEY ("ORG_ID");


--
-- Name: td_permission td_permission_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_permission
    ADD CONSTRAINT td_permission_pkey PRIMARY KEY ("PERMISSION_ID");


--
-- Name: td_role_permission td_role_permission_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_role_permission
    ADD CONSTRAINT td_role_permission_pkey PRIMARY KEY ("ROLE_ID", "PERMISSION_ID");


--
-- Name: td_role td_role_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_role
    ADD CONSTRAINT td_role_pkey PRIMARY KEY ("ROLE_ID");


--
-- Name: td_role_security_profile td_role_security_profile_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_role_security_profile
    ADD CONSTRAINT td_role_security_profile_pkey PRIMARY KEY (role_id);


--
-- Name: td_runtime_task_execution td_runtime_task_execution_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_runtime_task_execution
    ADD CONSTRAINT td_runtime_task_execution_pkey PRIMARY KEY ("EXECUTION_ID");


--
-- Name: td_runtime_task_log td_runtime_task_log_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_runtime_task_log
    ADD CONSTRAINT td_runtime_task_log_pkey PRIMARY KEY ("LOG_ID");


--
-- Name: td_runtime_task td_runtime_task_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_runtime_task
    ADD CONSTRAINT td_runtime_task_pkey PRIMARY KEY ("TASK_ID");


--
-- Name: td_runtime_task_plan td_runtime_task_plan_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_runtime_task_plan
    ADD CONSTRAINT td_runtime_task_plan_pkey PRIMARY KEY ("TASK_ID");


--
-- Name: td_runtime_task_status td_runtime_task_status_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_runtime_task_status
    ADD CONSTRAINT td_runtime_task_status_pkey PRIMARY KEY ("TASK_ID");


--
-- Name: td_schema_registry td_schema_registry_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_schema_registry
    ADD CONSTRAINT td_schema_registry_pkey PRIMARY KEY ("ID");


--
-- Name: td_schema_version td_schema_version_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_schema_version
    ADD CONSTRAINT td_schema_version_pkey PRIMARY KEY ("VERSION_ID");


--
-- Name: td_sm_user td_sm_user_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_sm_user
    ADD CONSTRAINT td_sm_user_pkey PRIMARY KEY (user_id);


--
-- Name: td_system_param td_system_param_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_system_param
    ADD CONSTRAINT td_system_param_pkey PRIMARY KEY ("PARAM_ID");


--
-- Name: td_system_variable td_system_variable_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_system_variable
    ADD CONSTRAINT td_system_variable_pkey PRIMARY KEY ("VAR_ID");


--
-- Name: td_tenant_config td_tenant_config_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_tenant_config
    ADD CONSTRAINT td_tenant_config_pkey PRIMARY KEY ("CONFIG_ID");


--
-- Name: td_tenant td_tenant_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_tenant
    ADD CONSTRAINT td_tenant_pkey PRIMARY KEY ("TENANT_ID");


--
-- Name: td_user_organization td_user_organization_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_user_organization
    ADD CONSTRAINT td_user_organization_pkey PRIMARY KEY ("USER_ID", "ORG_ID");


--
-- Name: td_user td_user_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_user
    ADD CONSTRAINT td_user_pkey PRIMARY KEY ("USER_ID");


--
-- Name: td_user_role td_user_role_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_user_role
    ADD CONSTRAINT td_user_role_pkey PRIMARY KEY ("USER_ID", "ROLE_ID", "ORG_ID");


--
-- Name: td_user_security_profile td_user_security_profile_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_user_security_profile
    ADD CONSTRAINT td_user_security_profile_pkey PRIMARY KEY (user_id);


--
-- Name: ecos_glossary_term_relation uniq_glossary_relation_edge; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_glossary_term_relation
    ADD CONSTRAINT uniq_glossary_relation_edge UNIQUE (from_term_id, to_term_id, relation_type);


--
-- Name: user_security_configs user_security_configs_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.user_security_configs
    ADD CONSTRAINT user_security_configs_pkey PRIMARY KEY (id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_username_key; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.users
    ADD CONSTRAINT users_username_key UNIQUE (username);


--
-- Name: 员工 员工_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control."员工"
    ADD CONSTRAINT "员工_pkey" PRIMARY KEY (id);


--
-- Name: 数据目录 数据目录_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control."数据目录"
    ADD CONSTRAINT "数据目录_pkey" PRIMARY KEY (id);


--
-- Name: 经营数据 经营数据_pkey; Type: CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control."经营数据"
    ADD CONSTRAINT "经营数据_pkey" PRIMARY KEY (id);


--
-- Name: ecos_cognitive_rule ecos_cognitive_rule_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_cognitive_rule
    ADD CONSTRAINT ecos_cognitive_rule_pkey PRIMARY KEY (id);


--
-- Name: ecos_data_asset_field ecos_data_asset_field_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_data_asset_field
    ADD CONSTRAINT ecos_data_asset_field_pkey PRIMARY KEY (field_asset_id);


--
-- Name: ecos_data_asset ecos_data_asset_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_data_asset
    ADD CONSTRAINT ecos_data_asset_pkey PRIMARY KEY (asset_id);


--
-- Name: ecos_data_category_tree ecos_data_category_tree_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_data_category_tree
    ADD CONSTRAINT ecos_data_category_tree_pkey PRIMARY KEY (category_id);


--
-- Name: ecos_data_level_def ecos_data_level_def_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_data_level_def
    ADD CONSTRAINT ecos_data_level_def_pkey PRIMARY KEY (level_code);


--
-- Name: ecos_data_lineage_edge ecos_data_lineage_edge_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_data_lineage_edge
    ADD CONSTRAINT ecos_data_lineage_edge_pkey PRIMARY KEY (id);


--
-- Name: ecos_data_lineage_node ecos_data_lineage_node_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_data_lineage_node
    ADD CONSTRAINT ecos_data_lineage_node_pkey PRIMARY KEY (id);


--
-- Name: ecos_dq_execution_result ecos_dq_execution_result_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_dq_execution_result
    ADD CONSTRAINT ecos_dq_execution_result_pkey PRIMARY KEY (id);


--
-- Name: ecos_dq_issue ecos_dq_issue_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_dq_issue
    ADD CONSTRAINT ecos_dq_issue_pkey PRIMARY KEY (id);


--
-- Name: ecos_dq_rule ecos_dq_rule_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_dq_rule
    ADD CONSTRAINT ecos_dq_rule_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_definition ecos_pipeline_definition_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_pipeline_definition
    ADD CONSTRAINT ecos_pipeline_definition_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_edge ecos_pipeline_edge_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_pipeline_edge
    ADD CONSTRAINT ecos_pipeline_edge_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_execution ecos_pipeline_execution_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_pipeline_execution
    ADD CONSTRAINT ecos_pipeline_execution_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_node ecos_pipeline_node_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_pipeline_node
    ADD CONSTRAINT ecos_pipeline_node_pkey PRIMARY KEY (id);


--
-- Name: ecos_query_history ecos_query_history_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_query_history
    ADD CONSTRAINT ecos_query_history_pkey PRIMARY KEY (id);


--
-- Name: ecos_query_template ecos_query_template_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_query_template
    ADD CONSTRAINT ecos_query_template_pkey PRIMARY KEY (id);


--
-- Name: ecos_task ecos_task_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_task
    ADD CONSTRAINT ecos_task_pkey PRIMARY KEY (id);


--
-- Name: source source_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.source
    ADD CONSTRAINT source_pkey PRIMARY KEY (id);


--
-- Name: td_catalog_item td_catalog_item_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.td_catalog_item
    ADD CONSTRAINT td_catalog_item_pkey PRIMARY KEY (catalog_id);


--
-- Name: td_data_category td_data_category_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.td_data_category
    ADD CONSTRAINT td_data_category_pkey PRIMARY KEY (category_id);


--
-- Name: td_data_field td_data_field_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.td_data_field
    ADD CONSTRAINT td_data_field_pkey PRIMARY KEY (field_id);


--
-- Name: td_data_resource td_data_resource_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.td_data_resource
    ADD CONSTRAINT td_data_resource_pkey PRIMARY KEY (resource_id);


--
-- Name: td_datasource td_datasource_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.td_datasource
    ADD CONSTRAINT td_datasource_pkey PRIMARY KEY (datasource_id);


--
-- Name: dq_alert_record dq_alert_record_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.dq_alert_record
    ADD CONSTRAINT dq_alert_record_pkey PRIMARY KEY (id);


--
-- Name: dq_knowledge_entry dq_knowledge_entry_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.dq_knowledge_entry
    ADD CONSTRAINT dq_knowledge_entry_pkey PRIMARY KEY (id);


--
-- Name: dq_report dq_report_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.dq_report
    ADD CONSTRAINT dq_report_pkey PRIMARY KEY (id);


--
-- Name: dq_rule_check dq_rule_check_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.dq_rule_check
    ADD CONSTRAINT dq_rule_check_pkey PRIMARY KEY (id);


--
-- Name: dq_rule dq_rule_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.dq_rule
    ADD CONSTRAINT dq_rule_pkey PRIMARY KEY (id);


--
-- Name: dq_rule dq_rule_rule_code_key; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.dq_rule
    ADD CONSTRAINT dq_rule_rule_code_key UNIQUE (rule_code);


--
-- Name: dq_rule_version dq_rule_version_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.dq_rule_version
    ADD CONSTRAINT dq_rule_version_pkey PRIMARY KEY (id);


--
-- Name: dq_rule_version dq_rule_version_rule_id_version_number_key; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.dq_rule_version
    ADD CONSTRAINT dq_rule_version_rule_id_version_number_key UNIQUE (rule_id, version_number);


--
-- Name: dq_schedule dq_schedule_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.dq_schedule
    ADD CONSTRAINT dq_schedule_pkey PRIMARY KEY (id);


--
-- Name: dq_score_snapshot dq_score_snapshot_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.dq_score_snapshot
    ADD CONSTRAINT dq_score_snapshot_pkey PRIMARY KEY (id);


--
-- Name: dq_throttle dq_throttle_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.dq_throttle
    ADD CONSTRAINT dq_throttle_pkey PRIMARY KEY (id);


--
-- Name: dq_throttle dq_throttle_scope_type_scope_id_key; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.dq_throttle
    ADD CONSTRAINT dq_throttle_scope_type_scope_id_key UNIQUE (scope_type, scope_id);


--
-- Name: dq_work_order dq_work_order_order_no_key; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.dq_work_order
    ADD CONSTRAINT dq_work_order_order_no_key UNIQUE (order_no);


--
-- Name: dq_work_order dq_work_order_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.dq_work_order
    ADD CONSTRAINT dq_work_order_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_function ecos_pipeline_function_name_key; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.ecos_pipeline_function
    ADD CONSTRAINT ecos_pipeline_function_name_key UNIQUE (name);


--
-- Name: ecos_pipeline_function ecos_pipeline_function_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.ecos_pipeline_function
    ADD CONSTRAINT ecos_pipeline_function_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_run ecos_pipeline_run_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.ecos_pipeline_run
    ADD CONSTRAINT ecos_pipeline_run_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_step ecos_pipeline_step_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.ecos_pipeline_step
    ADD CONSTRAINT ecos_pipeline_step_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_step_run ecos_pipeline_step_run_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.ecos_pipeline_step_run
    ADD CONSTRAINT ecos_pipeline_step_run_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_task ecos_pipeline_task_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.ecos_pipeline_task
    ADD CONSTRAINT ecos_pipeline_task_pkey PRIMARY KEY (id);


--
-- Name: ecos_pipeline_udf ecos_pipeline_udf_name_key; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.ecos_pipeline_udf
    ADD CONSTRAINT ecos_pipeline_udf_name_key UNIQUE (name);


--
-- Name: ecos_pipeline_udf ecos_pipeline_udf_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.ecos_pipeline_udf
    ADD CONSTRAINT ecos_pipeline_udf_pkey PRIMARY KEY (id);


--
-- Name: ecos_quality_evaluation ecos_quality_evaluation_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.ecos_quality_evaluation
    ADD CONSTRAINT ecos_quality_evaluation_pkey PRIMARY KEY (id);


--
-- Name: ecos_quality_rule ecos_quality_rule_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.ecos_quality_rule
    ADD CONSTRAINT ecos_quality_rule_pkey PRIMARY KEY (rule_id);


--
-- Name: schema_changes schema_changes_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.schema_changes
    ADD CONSTRAINT schema_changes_pkey PRIMARY KEY (id);


--
-- Name: schema_snapshots schema_snapshots_pkey; Type: CONSTRAINT; Schema: ecos_dq; Owner: postgres
--

ALTER TABLE ONLY ecos_dq.schema_snapshots
    ADD CONSTRAINT schema_snapshots_pkey PRIMARY KEY (id);


--
-- Name: doc_chunk doc_chunk_pkey; Type: CONSTRAINT; Schema: ecos_dw; Owner: postgres
--

ALTER TABLE ONLY ecos_dw.doc_chunk
    ADD CONSTRAINT doc_chunk_pkey PRIMARY KEY (id);


--
-- Name: doc doc_pkey; Type: CONSTRAINT; Schema: ecos_dw; Owner: postgres
--

ALTER TABLE ONLY ecos_dw.doc
    ADD CONSTRAINT doc_pkey PRIMARY KEY (id);


--
-- Name: outbox_event outbox_event_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event
    ADD CONSTRAINT outbox_event_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_01 outbox_event_2025_01_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_01
    ADD CONSTRAINT outbox_event_2025_01_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_02 outbox_event_2025_02_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_02
    ADD CONSTRAINT outbox_event_2025_02_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_03 outbox_event_2025_03_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_03
    ADD CONSTRAINT outbox_event_2025_03_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_04 outbox_event_2025_04_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_04
    ADD CONSTRAINT outbox_event_2025_04_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_05 outbox_event_2025_05_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_05
    ADD CONSTRAINT outbox_event_2025_05_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_06 outbox_event_2025_06_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_06
    ADD CONSTRAINT outbox_event_2025_06_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_07 outbox_event_2025_07_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_07
    ADD CONSTRAINT outbox_event_2025_07_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_08 outbox_event_2025_08_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_08
    ADD CONSTRAINT outbox_event_2025_08_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_09 outbox_event_2025_09_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_09
    ADD CONSTRAINT outbox_event_2025_09_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_10 outbox_event_2025_10_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_10
    ADD CONSTRAINT outbox_event_2025_10_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_11 outbox_event_2025_11_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_11
    ADD CONSTRAINT outbox_event_2025_11_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2025_12 outbox_event_2025_12_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2025_12
    ADD CONSTRAINT outbox_event_2025_12_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_01 outbox_event_2026_01_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_01
    ADD CONSTRAINT outbox_event_2026_01_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_02 outbox_event_2026_02_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_02
    ADD CONSTRAINT outbox_event_2026_02_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_03 outbox_event_2026_03_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_03
    ADD CONSTRAINT outbox_event_2026_03_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_04 outbox_event_2026_04_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_04
    ADD CONSTRAINT outbox_event_2026_04_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_05 outbox_event_2026_05_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_05
    ADD CONSTRAINT outbox_event_2026_05_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_06 outbox_event_2026_06_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_06
    ADD CONSTRAINT outbox_event_2026_06_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_07 outbox_event_2026_07_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_07
    ADD CONSTRAINT outbox_event_2026_07_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_08 outbox_event_2026_08_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_08
    ADD CONSTRAINT outbox_event_2026_08_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_09 outbox_event_2026_09_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_09
    ADD CONSTRAINT outbox_event_2026_09_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_10 outbox_event_2026_10_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_10
    ADD CONSTRAINT outbox_event_2026_10_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_11 outbox_event_2026_11_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_11
    ADD CONSTRAINT outbox_event_2026_11_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2026_12 outbox_event_2026_12_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2026_12
    ADD CONSTRAINT outbox_event_2026_12_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_01 outbox_event_2027_01_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_01
    ADD CONSTRAINT outbox_event_2027_01_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_02 outbox_event_2027_02_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_02
    ADD CONSTRAINT outbox_event_2027_02_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_03 outbox_event_2027_03_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_03
    ADD CONSTRAINT outbox_event_2027_03_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_04 outbox_event_2027_04_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_04
    ADD CONSTRAINT outbox_event_2027_04_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_05 outbox_event_2027_05_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_05
    ADD CONSTRAINT outbox_event_2027_05_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_06 outbox_event_2027_06_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_06
    ADD CONSTRAINT outbox_event_2027_06_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_07 outbox_event_2027_07_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_07
    ADD CONSTRAINT outbox_event_2027_07_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_08 outbox_event_2027_08_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_08
    ADD CONSTRAINT outbox_event_2027_08_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_09 outbox_event_2027_09_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_09
    ADD CONSTRAINT outbox_event_2027_09_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_10 outbox_event_2027_10_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_10
    ADD CONSTRAINT outbox_event_2027_10_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_11 outbox_event_2027_11_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_11
    ADD CONSTRAINT outbox_event_2027_11_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_2027_12 outbox_event_2027_12_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_2027_12
    ADD CONSTRAINT outbox_event_2027_12_pkey PRIMARY KEY (id, created_at);


--
-- Name: outbox_event_default outbox_event_default_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.outbox_event_default
    ADD CONSTRAINT outbox_event_default_pkey PRIMARY KEY (id, created_at);


--
-- Name: saga_instance saga_instance_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.saga_instance
    ADD CONSTRAINT saga_instance_pkey PRIMARY KEY (id);


--
-- Name: schema_version schema_version_pkey; Type: CONSTRAINT; Schema: ecos_infra; Owner: postgres
--

ALTER TABLE ONLY ecos_infra.schema_version
    ADD CONSTRAINT schema_version_pkey PRIMARY KEY (id);


--
-- Name: ecos_glossary_term ecos_glossary_term_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.ecos_glossary_term
    ADD CONSTRAINT ecos_glossary_term_pkey PRIMARY KEY (id);


--
-- Name: ecos_knowledge_document ecos_knowledge_document_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.ecos_knowledge_document
    ADD CONSTRAINT ecos_knowledge_document_pkey PRIMARY KEY (id);


--
-- Name: ecos_knowledge_graph_edge ecos_knowledge_graph_edge_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.ecos_knowledge_graph_edge
    ADD CONSTRAINT ecos_knowledge_graph_edge_pkey PRIMARY KEY (id);


--
-- Name: ecos_knowledge_graph_node ecos_knowledge_graph_node_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.ecos_knowledge_graph_node
    ADD CONSTRAINT ecos_knowledge_graph_node_pkey PRIMARY KEY (id);


--
-- Name: ecos_marketplace_access_request ecos_marketplace_access_request_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.ecos_marketplace_access_request
    ADD CONSTRAINT ecos_marketplace_access_request_pkey PRIMARY KEY (id);


--
-- Name: ecos_marketplace_asset ecos_marketplace_asset_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.ecos_marketplace_asset
    ADD CONSTRAINT ecos_marketplace_asset_pkey PRIMARY KEY (id);


--
-- Name: expert_rule expert_rule_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.expert_rule
    ADD CONSTRAINT expert_rule_pkey PRIMARY KEY (id);


--
-- Name: graph_edge graph_edge_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.graph_edge
    ADD CONSTRAINT graph_edge_pkey PRIMARY KEY (id);


--
-- Name: graph_node graph_node_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.graph_node
    ADD CONSTRAINT graph_node_pkey PRIMARY KEY (id);


--
-- Name: graph_subgraph graph_subgraph_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.graph_subgraph
    ADD CONSTRAINT graph_subgraph_pkey PRIMARY KEY (id);


--
-- Name: kb_doc_chunk kb_doc_chunk_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.kb_doc_chunk
    ADD CONSTRAINT kb_doc_chunk_pkey PRIMARY KEY (id);


--
-- Name: kb_doc kb_doc_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.kb_doc
    ADD CONSTRAINT kb_doc_pkey PRIMARY KEY (doc_id);


--
-- Name: kb_extract_audit kb_extract_audit_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.kb_extract_audit
    ADD CONSTRAINT kb_extract_audit_pkey PRIMARY KEY (id);


--
-- Name: kb_extract_candidate kb_extract_candidate_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.kb_extract_candidate
    ADD CONSTRAINT kb_extract_candidate_pkey PRIMARY KEY (id);


--
-- Name: kb_extract_watermark kb_extract_watermark_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.kb_extract_watermark
    ADD CONSTRAINT kb_extract_watermark_pkey PRIMARY KEY (id);


--
-- Name: kb_ontology_snapshot kb_ontology_snapshot_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.kb_ontology_snapshot
    ADD CONSTRAINT kb_ontology_snapshot_pkey PRIMARY KEY (id);


--
-- Name: kb_scheduled_extract kb_scheduled_extract_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.kb_scheduled_extract
    ADD CONSTRAINT kb_scheduled_extract_pkey PRIMARY KEY (id);


--
-- Name: kb_scheduled_extract kb_scheduled_extract_schedule_id_key; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.kb_scheduled_extract
    ADD CONSTRAINT kb_scheduled_extract_schedule_id_key UNIQUE (schedule_id);


--
-- Name: kg_sync_log kg_sync_log_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.kg_sync_log
    ADD CONSTRAINT kg_sync_log_pkey PRIMARY KEY (id);


--
-- Name: knowledge_article knowledge_article_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.knowledge_article
    ADD CONSTRAINT knowledge_article_pkey PRIMARY KEY (id);


--
-- Name: knowledge_embedding knowledge_embedding_pkey; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.knowledge_embedding
    ADD CONSTRAINT knowledge_embedding_pkey PRIMARY KEY (id);


--
-- Name: kb_extract_watermark uq_kb_extract_watermark; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.kb_extract_watermark
    ADD CONSTRAINT uq_kb_extract_watermark UNIQUE (ontology_id, entity_code, resource_id);


--
-- Name: kb_ontology_snapshot uq_kb_ontology_snapshot_ontology_version; Type: CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.kb_ontology_snapshot
    ADD CONSTRAINT uq_kb_ontology_snapshot_ontology_version UNIQUE (ontology_id, version);


--
-- Name: action_definition action_definition_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.action_definition
    ADD CONSTRAINT action_definition_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_attachment ecos_object_attachment_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.ecos_object_attachment
    ADD CONSTRAINT ecos_object_attachment_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_data ecos_object_data_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.ecos_object_data
    ADD CONSTRAINT ecos_object_data_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_links ecos_object_links_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.ecos_object_links
    ADD CONSTRAINT ecos_object_links_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_relation ecos_object_relation_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.ecos_object_relation
    ADD CONSTRAINT ecos_object_relation_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_relationship ecos_object_relationship_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.ecos_object_relationship
    ADD CONSTRAINT ecos_object_relationship_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_state_machine ecos_object_state_machine_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.ecos_object_state_machine
    ADD CONSTRAINT ecos_object_state_machine_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_timeline ecos_object_timeline_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.ecos_object_timeline
    ADD CONSTRAINT ecos_object_timeline_pkey PRIMARY KEY (id);


--
-- Name: ecos_object_version ecos_object_version_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.ecos_object_version
    ADD CONSTRAINT ecos_object_version_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_approval ecos_workflow_approval_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.ecos_workflow_approval
    ADD CONSTRAINT ecos_workflow_approval_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_instance ecos_workflow_instance_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.ecos_workflow_instance
    ADD CONSTRAINT ecos_workflow_instance_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_log ecos_workflow_log_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.ecos_workflow_log
    ADD CONSTRAINT ecos_workflow_log_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow ecos_workflow_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.ecos_workflow
    ADD CONSTRAINT ecos_workflow_pkey PRIMARY KEY (id);


--
-- Name: ecos_workflow_task ecos_workflow_task_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.ecos_workflow_task
    ADD CONSTRAINT ecos_workflow_task_pkey PRIMARY KEY (id);


--
-- Name: entity_definition entity_definition_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.entity_definition
    ADD CONSTRAINT entity_definition_pkey PRIMARY KEY (id);


--
-- Name: event_definition event_definition_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.event_definition
    ADD CONSTRAINT event_definition_pkey PRIMARY KEY (id);


--
-- Name: metric_definition metric_definition_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.metric_definition
    ADD CONSTRAINT metric_definition_pkey PRIMARY KEY (id);


--
-- Name: policy_definition policy_definition_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.policy_definition
    ADD CONSTRAINT policy_definition_pkey PRIMARY KEY (id);


--
-- Name: relationship_definition relationship_definition_pkey; Type: CONSTRAINT; Schema: ecos_ontology; Owner: postgres
--

ALTER TABLE ONLY ecos_ontology.relationship_definition
    ADD CONSTRAINT relationship_definition_pkey PRIMARY KEY (id);


--
-- Name: ecos_alert_history ecos_alert_history_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_alert_history
    ADD CONSTRAINT ecos_alert_history_pkey PRIMARY KEY (id);


--
-- Name: ecos_spans ecos_spans_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_spans
    ADD CONSTRAINT ecos_spans_pkey PRIMARY KEY (span_id);


--
-- Name: ecos_token_usage ecos_token_usage_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage
    ADD CONSTRAINT ecos_token_usage_pkey PRIMARY KEY (id, created_at);


--
-- Name: ecos_token_usage_2025_q1 ecos_token_usage_2025_q1_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage_2025_q1
    ADD CONSTRAINT ecos_token_usage_2025_q1_pkey PRIMARY KEY (id, created_at);


--
-- Name: ecos_token_usage_2025_q2 ecos_token_usage_2025_q2_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage_2025_q2
    ADD CONSTRAINT ecos_token_usage_2025_q2_pkey PRIMARY KEY (id, created_at);


--
-- Name: ecos_token_usage_2025_q3 ecos_token_usage_2025_q3_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage_2025_q3
    ADD CONSTRAINT ecos_token_usage_2025_q3_pkey PRIMARY KEY (id, created_at);


--
-- Name: ecos_token_usage_2025_q4 ecos_token_usage_2025_q4_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage_2025_q4
    ADD CONSTRAINT ecos_token_usage_2025_q4_pkey PRIMARY KEY (id, created_at);


--
-- Name: ecos_token_usage_2026_q1 ecos_token_usage_2026_q1_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage_2026_q1
    ADD CONSTRAINT ecos_token_usage_2026_q1_pkey PRIMARY KEY (id, created_at);


--
-- Name: ecos_token_usage_2026_q2 ecos_token_usage_2026_q2_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage_2026_q2
    ADD CONSTRAINT ecos_token_usage_2026_q2_pkey PRIMARY KEY (id, created_at);


--
-- Name: ecos_token_usage_2026_q3 ecos_token_usage_2026_q3_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage_2026_q3
    ADD CONSTRAINT ecos_token_usage_2026_q3_pkey PRIMARY KEY (id, created_at);


--
-- Name: ecos_token_usage_2026_q4 ecos_token_usage_2026_q4_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage_2026_q4
    ADD CONSTRAINT ecos_token_usage_2026_q4_pkey PRIMARY KEY (id, created_at);


--
-- Name: ecos_token_usage_default ecos_token_usage_default_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_token_usage_default
    ADD CONSTRAINT ecos_token_usage_default_pkey PRIMARY KEY (id, created_at);


--
-- Name: td_abac_policy td_abac_policy_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_abac_policy
    ADD CONSTRAINT td_abac_policy_pkey PRIMARY KEY (policy_id);


--
-- Name: td_audit_log td_audit_log_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log
    ADD CONSTRAINT td_audit_log_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2024_01 td_audit_log_2024_01_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2024_01
    ADD CONSTRAINT td_audit_log_2024_01_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2024_02 td_audit_log_2024_02_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2024_02
    ADD CONSTRAINT td_audit_log_2024_02_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2024_03 td_audit_log_2024_03_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2024_03
    ADD CONSTRAINT td_audit_log_2024_03_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2024_04 td_audit_log_2024_04_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2024_04
    ADD CONSTRAINT td_audit_log_2024_04_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2024_05 td_audit_log_2024_05_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2024_05
    ADD CONSTRAINT td_audit_log_2024_05_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2024_06 td_audit_log_2024_06_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2024_06
    ADD CONSTRAINT td_audit_log_2024_06_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2024_07 td_audit_log_2024_07_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2024_07
    ADD CONSTRAINT td_audit_log_2024_07_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2024_08 td_audit_log_2024_08_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2024_08
    ADD CONSTRAINT td_audit_log_2024_08_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2024_09 td_audit_log_2024_09_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2024_09
    ADD CONSTRAINT td_audit_log_2024_09_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2024_10 td_audit_log_2024_10_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2024_10
    ADD CONSTRAINT td_audit_log_2024_10_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2024_11 td_audit_log_2024_11_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2024_11
    ADD CONSTRAINT td_audit_log_2024_11_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2024_12 td_audit_log_2024_12_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2024_12
    ADD CONSTRAINT td_audit_log_2024_12_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2025_01 td_audit_log_2025_01_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2025_01
    ADD CONSTRAINT td_audit_log_2025_01_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2025_02 td_audit_log_2025_02_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2025_02
    ADD CONSTRAINT td_audit_log_2025_02_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2025_03 td_audit_log_2025_03_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2025_03
    ADD CONSTRAINT td_audit_log_2025_03_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2025_04 td_audit_log_2025_04_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2025_04
    ADD CONSTRAINT td_audit_log_2025_04_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2025_05 td_audit_log_2025_05_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2025_05
    ADD CONSTRAINT td_audit_log_2025_05_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2025_06 td_audit_log_2025_06_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2025_06
    ADD CONSTRAINT td_audit_log_2025_06_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2025_07 td_audit_log_2025_07_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2025_07
    ADD CONSTRAINT td_audit_log_2025_07_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2025_08 td_audit_log_2025_08_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2025_08
    ADD CONSTRAINT td_audit_log_2025_08_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2025_09 td_audit_log_2025_09_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2025_09
    ADD CONSTRAINT td_audit_log_2025_09_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2025_10 td_audit_log_2025_10_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2025_10
    ADD CONSTRAINT td_audit_log_2025_10_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2025_11 td_audit_log_2025_11_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2025_11
    ADD CONSTRAINT td_audit_log_2025_11_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2025_12 td_audit_log_2025_12_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2025_12
    ADD CONSTRAINT td_audit_log_2025_12_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2026_01 td_audit_log_2026_01_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2026_01
    ADD CONSTRAINT td_audit_log_2026_01_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2026_02 td_audit_log_2026_02_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2026_02
    ADD CONSTRAINT td_audit_log_2026_02_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2026_03 td_audit_log_2026_03_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2026_03
    ADD CONSTRAINT td_audit_log_2026_03_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2026_04 td_audit_log_2026_04_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2026_04
    ADD CONSTRAINT td_audit_log_2026_04_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2026_05 td_audit_log_2026_05_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2026_05
    ADD CONSTRAINT td_audit_log_2026_05_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2026_06 td_audit_log_2026_06_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2026_06
    ADD CONSTRAINT td_audit_log_2026_06_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2026_07 td_audit_log_2026_07_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2026_07
    ADD CONSTRAINT td_audit_log_2026_07_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2026_08 td_audit_log_2026_08_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2026_08
    ADD CONSTRAINT td_audit_log_2026_08_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2026_09 td_audit_log_2026_09_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2026_09
    ADD CONSTRAINT td_audit_log_2026_09_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2026_10 td_audit_log_2026_10_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2026_10
    ADD CONSTRAINT td_audit_log_2026_10_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2026_11 td_audit_log_2026_11_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2026_11
    ADD CONSTRAINT td_audit_log_2026_11_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_2026_12 td_audit_log_2026_12_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_2026_12
    ADD CONSTRAINT td_audit_log_2026_12_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_audit_log_default td_audit_log_default_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_audit_log_default
    ADD CONSTRAINT td_audit_log_default_pkey PRIMARY KEY (log_id, "timestamp");


--
-- Name: td_role_security_profile td_role_security_profile_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_role_security_profile
    ADD CONSTRAINT td_role_security_profile_pkey PRIMARY KEY (role_id);


--
-- Name: td_user_security_profile td_user_security_profile_pkey; Type: CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_user_security_profile
    ADD CONSTRAINT td_user_security_profile_pkey PRIMARY KEY (user_id);


--
-- Name: demo_customer demo_customer_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.demo_customer
    ADD CONSTRAINT demo_customer_pkey PRIMARY KEY (id);


--
-- Name: demo_invoice demo_invoice_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.demo_invoice
    ADD CONSTRAINT demo_invoice_pkey PRIMARY KEY (id);


--
-- Name: demo_supplier demo_supplier_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.demo_supplier
    ADD CONSTRAINT demo_supplier_pkey PRIMARY KEY (id);


--
-- Name: ecos_tenant ecos_tenant_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.ecos_tenant
    ADD CONSTRAINT ecos_tenant_pkey PRIMARY KEY (id);


--
-- Name: ecos_tenant_quota ecos_tenant_quota_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.ecos_tenant_quota
    ADD CONSTRAINT ecos_tenant_quota_pkey PRIMARY KEY (id);


--
-- Name: ecos_tenant_usage ecos_tenant_usage_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.ecos_tenant_usage
    ADD CONSTRAINT ecos_tenant_usage_pkey PRIMARY KEY (tenant_id, usage_date, quota_type);


--
-- Name: sys_config sys_config_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.sys_config
    ADD CONSTRAINT sys_config_pkey PRIMARY KEY (id);


--
-- Name: sys_dict sys_dict_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.sys_dict
    ADD CONSTRAINT sys_dict_pkey PRIMARY KEY (id);


--
-- Name: td_config td_config_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.td_config
    ADD CONSTRAINT td_config_pkey PRIMARY KEY (config_id);


--
-- Name: td_org_permission td_org_permission_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.td_org_permission
    ADD CONSTRAINT td_org_permission_pkey PRIMARY KEY (permission_id);


--
-- Name: td_organization td_organization_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.td_organization
    ADD CONSTRAINT td_organization_pkey PRIMARY KEY ("ORG_ID");


--
-- Name: td_permission td_permission_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.td_permission
    ADD CONSTRAINT td_permission_pkey PRIMARY KEY (permission_id);


--
-- Name: td_role_permission td_role_permission_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.td_role_permission
    ADD CONSTRAINT td_role_permission_pkey PRIMARY KEY (role_id, permission_id);


--
-- Name: td_role td_role_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.td_role
    ADD CONSTRAINT td_role_pkey PRIMARY KEY (role_id);


--
-- Name: td_role_security_profile td_role_security_profile_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.td_role_security_profile
    ADD CONSTRAINT td_role_security_profile_pkey PRIMARY KEY (role_id);


--
-- Name: td_user_organization td_user_organization_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.td_user_organization
    ADD CONSTRAINT td_user_organization_pkey PRIMARY KEY ("USER_ID", "ORG_ID");


--
-- Name: td_user td_user_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.td_user
    ADD CONSTRAINT td_user_pkey PRIMARY KEY (user_id);


--
-- Name: td_user_role td_user_role_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.td_user_role
    ADD CONSTRAINT td_user_role_pkey PRIMARY KEY (user_id, role_id);


--
-- Name: td_user_security_profile td_user_security_profile_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.td_user_security_profile
    ADD CONSTRAINT td_user_security_profile_pkey PRIMARY KEY (user_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: idx_ai_agent_status; Type: INDEX; Schema: ecos_ai; Owner: postgres
--

CREATE INDEX idx_ai_agent_status ON ecos_ai.ecos_agent USING btree (status);


--
-- Name: idx_ai_calllog_profile; Type: INDEX; Schema: ecos_ai; Owner: postgres
--

CREATE INDEX idx_ai_calllog_profile ON ecos_ai.sys_agent_call_log USING btree (profile_name);


--
-- Name: idx_ai_calllog_time; Type: INDEX; Schema: ecos_ai; Owner: postgres
--

CREATE INDEX idx_ai_calllog_time ON ecos_ai.sys_agent_call_log USING btree (created_time DESC);


--
-- Name: idx_ai_causal_src; Type: INDEX; Schema: ecos_ai; Owner: postgres
--

CREATE INDEX idx_ai_causal_src ON ecos_ai.causal_edge USING btree (source_node);


--
-- Name: idx_ai_causal_tgt; Type: INDEX; Schema: ecos_ai; Owner: postgres
--

CREATE INDEX idx_ai_causal_tgt ON ecos_ai.causal_edge USING btree (target_node);


--
-- Name: idx_ai_dcase_tags; Type: INDEX; Schema: ecos_ai; Owner: postgres
--

CREATE INDEX idx_ai_dcase_tags ON ecos_ai.ecos_decision_case USING gin (tags);


--
-- Name: idx_ai_exec_agent; Type: INDEX; Schema: ecos_ai; Owner: postgres
--

CREATE INDEX idx_ai_exec_agent ON ecos_ai.agent_execution USING btree (agent_id);


--
-- Name: idx_ai_exec_status; Type: INDEX; Schema: ecos_ai; Owner: postgres
--

CREATE INDEX idx_ai_exec_status ON ecos_ai.agent_execution USING btree (status);


--
-- Name: idx_ai_mem_agent; Type: INDEX; Schema: ecos_ai; Owner: postgres
--

CREATE INDEX idx_ai_mem_agent ON ecos_ai.agent_memory USING btree (agent_id);


--
-- Name: idx_ai_mem_session; Type: INDEX; Schema: ecos_ai; Owner: postgres
--

CREATE INDEX idx_ai_mem_session ON ecos_ai.agent_memory USING btree (session_id);


--
-- Name: idx_ai_mtask_mission; Type: INDEX; Schema: ecos_ai; Owner: postgres
--

CREATE INDEX idx_ai_mtask_mission ON ecos_ai.ecos_mission_task USING btree (mission_id);


--
-- Name: idx_ai_scenario_type; Type: INDEX; Schema: ecos_ai; Owner: postgres
--

CREATE INDEX idx_ai_scenario_type ON ecos_ai.scenario USING btree (type);


--
-- Name: idx_ai_sim_status; Type: INDEX; Schema: ecos_ai; Owner: postgres
--

CREATE INDEX idx_ai_sim_status ON ecos_ai.simulation USING btree (status);


--
-- Name: idx_ai_step_exec; Type: INDEX; Schema: ecos_ai; Owner: postgres
--

CREATE INDEX idx_ai_step_exec ON ecos_ai.agent_execution_step USING btree (execution_id);


--
-- Name: idx_ai_tool_code; Type: INDEX; Schema: ecos_ai; Owner: postgres
--

CREATE UNIQUE INDEX idx_ai_tool_code ON ecos_ai.ecos_tool_definition USING btree (code);


--
-- Name: idx_cog_causal_src; Type: INDEX; Schema: ecos_cognitive; Owner: postgres
--

CREATE INDEX idx_cog_causal_src ON ecos_cognitive.ecos_wm_causal_link USING btree (source_goal_id);


--
-- Name: idx_cog_causal_tgt; Type: INDEX; Schema: ecos_cognitive; Owner: postgres
--

CREATE INDEX idx_cog_causal_tgt ON ecos_cognitive.ecos_wm_causal_link USING btree (target_goal_id);


--
-- Name: idx_cog_contract_proj; Type: INDEX; Schema: ecos_cognitive; Owner: postgres
--

CREATE INDEX idx_cog_contract_proj ON ecos_cognitive.ecos_biz_contract USING btree (project_id);


--
-- Name: idx_cog_dept_parent; Type: INDEX; Schema: ecos_cognitive; Owner: postgres
--

CREATE INDEX idx_cog_dept_parent ON ecos_cognitive.ecos_biz_department USING btree (parent_id);


--
-- Name: idx_cog_goal_org; Type: INDEX; Schema: ecos_cognitive; Owner: postgres
--

CREATE INDEX idx_cog_goal_org ON ecos_cognitive.ecos_wm_goal USING btree (org_id);


--
-- Name: idx_cog_goal_parent; Type: INDEX; Schema: ecos_cognitive; Owner: postgres
--

CREATE INDEX idx_cog_goal_parent ON ecos_cognitive.ecos_wm_goal USING btree (parent_id);


--
-- Name: idx_cog_goal_status; Type: INDEX; Schema: ecos_cognitive; Owner: postgres
--

CREATE INDEX idx_cog_goal_status ON ecos_cognitive.ecos_wm_goal USING btree (status);


--
-- Name: idx_cog_goal_tenant; Type: INDEX; Schema: ecos_cognitive; Owner: postgres
--

CREATE INDEX idx_cog_goal_tenant ON ecos_cognitive.ecos_wm_goal USING btree (tenant_id);


--
-- Name: idx_cog_goallog_goal; Type: INDEX; Schema: ecos_cognitive; Owner: postgres
--

CREATE INDEX idx_cog_goallog_goal ON ecos_cognitive.ecos_wm_goal_log USING btree (goal_id);


--
-- Name: idx_cog_goallog_time; Type: INDEX; Schema: ecos_cognitive; Owner: postgres
--

CREATE INDEX idx_cog_goallog_time ON ecos_cognitive.ecos_wm_goal_log USING btree (changed_at);


--
-- Name: idx_cog_metric_dept; Type: INDEX; Schema: ecos_cognitive; Owner: postgres
--

CREATE INDEX idx_cog_metric_dept ON ecos_cognitive.ecos_biz_metric USING btree (dept_id);


--
-- Name: idx_cog_proj_dept; Type: INDEX; Schema: ecos_cognitive; Owner: postgres
--

CREATE INDEX idx_cog_proj_dept ON ecos_cognitive.ecos_biz_project USING btree (dept_id);


--
-- Name: idx_cog_tracking_goal; Type: INDEX; Schema: ecos_cognitive; Owner: postgres
--

CREATE INDEX idx_cog_tracking_goal ON ecos_cognitive.ecos_goal_tracking USING btree (goal_id, recorded_at);


--
-- Name: idx_cog_wmscen_status; Type: INDEX; Schema: ecos_cognitive; Owner: postgres
--

CREATE INDEX idx_cog_wmscen_status ON ecos_cognitive.ecos_wm_scenario USING btree (status);


--
-- Name: idx_cog_worldscen_status; Type: INDEX; Schema: ecos_cognitive; Owner: postgres
--

CREATE INDEX idx_cog_worldscen_status ON ecos_cognitive.ecos_world_scenarios USING btree (status);


--
-- Name: ecos_agent_execution_idx_agent; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_agent_execution_idx_agent ON ecos_control.ecos_agent_execution USING btree (agent_id);


--
-- Name: ecos_agent_execution_idx_created; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_agent_execution_idx_created ON ecos_control.ecos_agent_execution USING btree (created_at);


--
-- Name: ecos_agent_execution_idx_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_agent_execution_idx_status ON ecos_control.ecos_agent_execution USING btree (status);


--
-- Name: ecos_agent_execution_step_idx_session; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_agent_execution_step_idx_session ON ecos_control.ecos_agent_execution_step USING btree (session_id);


--
-- Name: ecos_agent_memory_idx_accessed; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_agent_memory_idx_accessed ON ecos_control.ecos_agent_memory USING btree (last_accessed_at);


--
-- Name: ecos_agent_memory_idx_importance; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_agent_memory_idx_importance ON ecos_control.ecos_agent_memory USING btree (importance);


--
-- Name: ecos_agent_memory_idx_type; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_agent_memory_idx_type ON ecos_control.ecos_agent_memory USING btree (memory_type);


--
-- Name: ecos_agent_memory_uk_memory_key; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX ecos_agent_memory_uk_memory_key ON ecos_control.ecos_agent_memory USING btree (memory_key);


--
-- Name: ecos_knowledge_graph_edge_idx_source; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_knowledge_graph_edge_idx_source ON ecos_control.ecos_knowledge_graph_edge USING btree (source_node_id);


--
-- Name: ecos_knowledge_graph_edge_idx_target; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_knowledge_graph_edge_idx_target ON ecos_control.ecos_knowledge_graph_edge USING btree (target_node_id);


--
-- Name: ecos_obj_timeline_obj_idx; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_obj_timeline_obj_idx ON ecos_control.ecos_object_timeline USING btree (object_id);


--
-- Name: ecos_obj_timeline_ts_idx; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_obj_timeline_ts_idx ON ecos_control.ecos_object_timeline USING btree (created_at);


--
-- Name: ecos_object_data_idx_entity; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_object_data_idx_entity ON ecos_control.ecos_object_data USING btree (entity_code);


--
-- Name: ecos_object_data_idx_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_object_data_idx_status ON ecos_control.ecos_object_data USING btree (status);


--
-- Name: ecos_object_relation_idx_source; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_object_relation_idx_source ON ecos_control.ecos_object_relation USING btree (source_object_id);


--
-- Name: ecos_object_relation_idx_target; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_object_relation_idx_target ON ecos_control.ecos_object_relation USING btree (target_object_id);


--
-- Name: ecos_ontology_code; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX ecos_ontology_code ON ecos_control.ecos_ontology USING btree (code);


--
-- Name: ecos_ontology_entity_idx_ontology; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_ontology_entity_idx_ontology ON ecos_control.ecos_ontology_entity USING btree (ontology_id);


--
-- Name: ecos_ontology_entity_uk_ont_code; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX ecos_ontology_entity_uk_ont_code ON ecos_control.ecos_ontology_entity USING btree (ontology_id, code);


--
-- Name: ecos_ontology_property_idx_entity; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_ontology_property_idx_entity ON ecos_control.ecos_ontology_property USING btree (entity_id);


--
-- Name: ecos_ontology_property_uk_ent_code; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX ecos_ontology_property_uk_ent_code ON ecos_control.ecos_ontology_property USING btree (entity_id, code);


--
-- Name: ecos_ontology_relationship_idx_source; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_ontology_relationship_idx_source ON ecos_control.ecos_ontology_relationship USING btree (source_entity_id);


--
-- Name: ecos_ontology_relationship_idx_target; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_ontology_relationship_idx_target ON ecos_control.ecos_ontology_relationship USING btree (target_entity_id);


--
-- Name: ecos_tool_definition_code; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX ecos_tool_definition_code ON ecos_control.ecos_tool_definition USING btree (code);


--
-- Name: ecos_workflow_code; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX ecos_workflow_code ON ecos_control.ecos_workflow USING btree (code);


--
-- Name: ecos_workflow_edge_idx_workflow; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_workflow_edge_idx_workflow ON ecos_control.ecos_workflow_edge USING btree (workflow_id);


--
-- Name: ecos_workflow_instance_idx_business; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_workflow_instance_idx_business ON ecos_control.ecos_workflow_instance USING btree (business_key);


--
-- Name: ecos_workflow_instance_idx_workflow; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_workflow_instance_idx_workflow ON ecos_control.ecos_workflow_instance USING btree (workflow_id);


--
-- Name: ecos_workflow_node_idx_workflow; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_workflow_node_idx_workflow ON ecos_control.ecos_workflow_node USING btree (workflow_id);


--
-- Name: ecos_workflow_task_idx_assignee; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_workflow_task_idx_assignee ON ecos_control.ecos_workflow_task USING btree (assignee);


--
-- Name: ecos_workflow_task_idx_instance; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_workflow_task_idx_instance ON ecos_control.ecos_workflow_task USING btree (instance_id);


--
-- Name: ecos_working_memory_idx_expires; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX ecos_working_memory_idx_expires ON ecos_control.ecos_working_memory USING btree (expires_at);


--
-- Name: ecos_working_memory_uk_session_key; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX ecos_working_memory_uk_session_key ON ecos_control.ecos_working_memory USING btree (session_id, wm_key);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX flyway_schema_history_s_idx ON ecos_control.flyway_schema_history USING btree (success);


--
-- Name: idx_access_request_asset_id; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_access_request_asset_id ON ecos_control.ecos_marketplace_access_request USING btree (asset_id);


--
-- Name: idx_access_request_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_access_request_status ON ecos_control.ecos_marketplace_access_request USING btree (status);


--
-- Name: idx_agent_message_session; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_agent_message_session ON ecos_control.sys_agent_message USING btree (session_id);


--
-- Name: idx_agent_metrics_agent; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_agent_metrics_agent ON ecos_control.ecos_agent_metrics USING btree (agent_id, created_at DESC);


--
-- Name: idx_agent_session_agent; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_agent_session_agent ON ecos_control.sys_agent_session USING btree (agent_id);


--
-- Name: idx_agent_session_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_agent_session_status ON ecos_control.sys_agent_session USING btree (status);


--
-- Name: idx_agent_version_agent; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_agent_version_agent ON ecos_control.ecos_agent_version USING btree (agent_id);


--
-- Name: idx_agent_version_latest; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_agent_version_latest ON ecos_control.ecos_agent_version USING btree (agent_id, version DESC);


--
-- Name: idx_audit_created; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_audit_created ON ecos_control.sys_audit_log USING btree (created_at);


--
-- Name: idx_audit_username; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_audit_username ON ecos_control.sys_audit_log USING btree (username);


--
-- Name: idx_biz_scenario_dept; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_biz_scenario_dept ON ecos_control.ecos_business_scenario USING btree (department);


--
-- Name: idx_biz_scenario_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_biz_scenario_status ON ecos_control.ecos_business_scenario USING btree (status);


--
-- Name: idx_bsl_sco; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_bsl_sco ON ecos_control.ecos_scenario_binding_link USING btree (scenario_id);


--
-- Name: idx_bsl_src; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_bsl_src ON ecos_control.ecos_scenario_binding_link USING btree (source_binding_id);


--
-- Name: idx_bsl_tgt; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_bsl_tgt ON ecos_control.ecos_scenario_binding_link USING btree (target_binding_id);


--
-- Name: idx_bsl_typ; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_bsl_typ ON ecos_control.ecos_scenario_binding_link USING btree (link_type);


--
-- Name: idx_catalog_res; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_catalog_res ON ecos_control.td_catalog_item USING btree (resource_id);


--
-- Name: idx_catalog_type; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_catalog_type ON ecos_control.td_catalog_item USING btree (resource_type);


--
-- Name: idx_causal_source; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_causal_source ON ecos_control.ecos_decision_causal_link USING btree (source_decision_id);


--
-- Name: idx_causal_target; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_causal_target ON ecos_control.ecos_decision_causal_link USING btree (target_decision_id);


--
-- Name: idx_cls_policy_resource; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_cls_policy_resource ON ecos_control.ecos_cls_policy USING btree (resource_id);


--
-- Name: idx_cog_model_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_cog_model_status ON ecos_control.ecos_cognitive_model USING btree (status);


--
-- Name: idx_cog_model_type; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_cog_model_type ON ecos_control.ecos_cognitive_model USING btree (model_type);


--
-- Name: idx_data_field_res; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_data_field_res ON ecos_control.td_data_field USING btree (resource_id);


--
-- Name: idx_data_res_ds; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_data_res_ds ON ecos_control.td_data_resource USING btree (datasource_id);


--
-- Name: idx_decision_case_tags; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_decision_case_tags ON ecos_control.ecos_decision_case USING gin (tags);


--
-- Name: idx_decision_case_title; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_decision_case_title ON ecos_control.ecos_decision_case USING btree (title);


--
-- Name: idx_decision_category; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_decision_category ON ecos_control.ecos_decision USING btree (category);


--
-- Name: idx_decision_mind; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_decision_mind ON ecos_control.ecos_decision_record USING btree (mind_id, is_deleted);


--
-- Name: idx_decision_scenario; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_decision_scenario ON ecos_control.ecos_decision_record USING btree (scenario_id, is_deleted);


--
-- Name: idx_dict_column_table_id; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_dict_column_table_id ON ecos_control.dict_column USING btree (table_id);


--
-- Name: idx_dict_table_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_dict_table_status ON ecos_control.dict_table USING btree (status);


--
-- Name: idx_dq_exec_executed_at; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_dq_exec_executed_at ON ecos_control.ecos_dq_execution_result USING btree (executed_at DESC);


--
-- Name: idx_dq_exec_rule_id; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_dq_exec_rule_id ON ecos_control.ecos_dq_execution_result USING btree (rule_id);


--
-- Name: idx_dq_rule_tenant; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_dq_rule_tenant ON ecos_control.ecos_dq_rule USING btree (tenant_id);


--
-- Name: idx_ecos_audit_log_hash; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_audit_log_hash ON ecos_control.ecos_audit_log USING btree (id) WHERE (curr_hash IS NOT NULL);


--
-- Name: idx_ecos_cog_run_inv_hyp; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_cog_run_inv_hyp ON ecos_control.ecos_cognitive_run_invalidation USING btree (hypothesis_id);


--
-- Name: idx_ecos_cog_run_inv_run; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_cog_run_inv_run ON ecos_control.ecos_cognitive_run_invalidation USING btree (run_id);


--
-- Name: idx_ecos_cog_run_inv_time; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_cog_run_inv_time ON ecos_control.ecos_cognitive_run_invalidation USING btree (superseded_at DESC);


--
-- Name: idx_ecos_cognitive_belief_domain; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_cognitive_belief_domain ON ecos_control.ecos_cognitive_belief USING btree (domain);


--
-- Name: idx_ecos_cognitive_belief_time; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_cognitive_belief_time ON ecos_control.ecos_cognitive_belief USING btree (update_time DESC);


--
-- Name: idx_ecos_cognitive_evidence_source; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_cognitive_evidence_source ON ecos_control.ecos_cognitive_evidence USING btree (source_type);


--
-- Name: idx_ecos_cognitive_evidence_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_cognitive_evidence_status ON ecos_control.ecos_cognitive_evidence USING btree (status);


--
-- Name: idx_ecos_cognitive_evidence_time; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_cognitive_evidence_time ON ecos_control.ecos_cognitive_evidence USING btree (create_time DESC);


--
-- Name: idx_ecos_cognitive_hypothesis_domain; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_cognitive_hypothesis_domain ON ecos_control.ecos_cognitive_hypothesis USING btree (domain);


--
-- Name: idx_ecos_cognitive_hypothesis_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_cognitive_hypothesis_status ON ecos_control.ecos_cognitive_hypothesis USING btree (status);


--
-- Name: idx_ecos_cognitive_hypothesis_time; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_cognitive_hypothesis_time ON ecos_control.ecos_cognitive_hypothesis USING btree (update_time DESC);


--
-- Name: idx_ecos_domain_tenant; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_domain_tenant ON ecos_control.ecos_domain USING btree (tenant_id);


--
-- Name: idx_ecos_ontology_data_ontology; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_ontology_data_ontology ON ecos_control.ecos_ontology_data USING btree (ontology_id);


--
-- Name: idx_ecos_pipeline_task_updated_at; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_pipeline_task_updated_at ON ecos_control.ecos_pipeline_task USING btree (updated_at DESC);


--
-- Name: idx_ecos_warn_log_objid; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_warn_log_objid ON ecos_control.ecos_warn_log USING btree (warn_objid);


--
-- Name: idx_ecos_warn_log_review_tag; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_warn_log_review_tag ON ecos_control.ecos_warn_log USING btree (review_tag);


--
-- Name: idx_ecos_warn_log_time; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_warn_log_time ON ecos_control.ecos_warn_log USING btree (warn_time DESC);


--
-- Name: idx_ecos_warn_log_type; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_ecos_warn_log_type ON ecos_control.ecos_warn_log USING btree (warn_type);


--
-- Name: idx_edsr_decision; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_edsr_decision ON ecos_control.ecos_decision_source_ref USING btree (decision_id);


--
-- Name: idx_edsr_hash; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_edsr_hash ON ecos_control.ecos_decision_source_ref USING btree (hash);


--
-- Name: idx_esmr_mind; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_esmr_mind ON ecos_control.ecos_scenario_mind_ref USING btree (mind_id, ref_kind);


--
-- Name: idx_esqh_subject; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_esqh_subject ON ecos_control.ecos_scenario_query_history USING btree (subject_id, create_time);


--
-- Name: idx_esqh_trace; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_esqh_trace ON ecos_control.ecos_scenario_query_history USING btree (trace_id);


--
-- Name: idx_func_audit_caller; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_func_audit_caller ON ecos_control.ecos_function_audit_log USING btree (caller_id);


--
-- Name: idx_func_audit_created; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_func_audit_created ON ecos_control.ecos_function_audit_log USING btree (created_at DESC);


--
-- Name: idx_func_audit_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_func_audit_status ON ecos_control.ecos_function_audit_log USING btree (status);


--
-- Name: idx_glossary_relation_from; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_glossary_relation_from ON ecos_control.ecos_glossary_term_relation USING btree (from_term_id);


--
-- Name: idx_glossary_relation_to; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_glossary_relation_to ON ecos_control.ecos_glossary_term_relation USING btree (to_term_id);


--
-- Name: idx_glossary_relation_type; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_glossary_relation_type ON ecos_control.ecos_glossary_term_relation USING btree (relation_type);


--
-- Name: idx_glossary_term_domain; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_glossary_term_domain ON ecos_control.ecos_glossary_term USING btree (domain);


--
-- Name: idx_glossary_term_object_type; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_glossary_term_object_type ON ecos_control.ecos_glossary_term USING btree (object_type_id);


--
-- Name: idx_glossary_term_parent; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_glossary_term_parent ON ecos_control.ecos_glossary_term USING btree (parent_term_id);


--
-- Name: idx_glossary_term_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_glossary_term_status ON ecos_control.ecos_glossary_term USING btree (status);


--
-- Name: idx_glossary_term_type; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_glossary_term_type ON ecos_control.ecos_glossary_term USING btree (term_type);


--
-- Name: idx_goal_log_goal; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_goal_log_goal ON ecos_control.ecos_wm_goal_log USING btree (goal_id);


--
-- Name: idx_goal_log_time; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_goal_log_time ON ecos_control.ecos_wm_goal_log USING btree (changed_at);


--
-- Name: idx_goal_tracking_goal; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_goal_tracking_goal ON ecos_control.ecos_goal_tracking USING btree (goal_id, recorded_at);


--
-- Name: idx_kb_cognitive_pipeline_created_at; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_kb_cognitive_pipeline_created_at ON ecos_control.kb_cognitive_pipeline USING btree (created_at DESC) WHERE (is_deleted = 0);


--
-- Name: idx_kb_cognitive_pipeline_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_kb_cognitive_pipeline_status ON ecos_control.kb_cognitive_pipeline USING btree (status) WHERE (is_deleted = 0);


--
-- Name: idx_kb_lineage_event_format; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_kb_lineage_event_format ON ecos_control.kb_lineage_event USING btree (format) WHERE (is_deleted = 0);


--
-- Name: idx_kb_lineage_event_parse_at; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_kb_lineage_event_parse_at ON ecos_control.kb_lineage_event USING btree (parse_at DESC) WHERE (is_deleted = 0);


--
-- Name: idx_kg_node_created; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_kg_node_created ON ecos_control.ecos_knowledge_graph_node USING btree (created_at DESC);


--
-- Name: idx_kg_node_type; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_kg_node_type ON ecos_control.ecos_knowledge_graph_node USING btree (node_type);


--
-- Name: idx_market_asset_ontology; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_market_asset_ontology ON ecos_control.ecos_marketplace_asset USING btree (ontology_entity_id);


--
-- Name: idx_marketplace_asset_category; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_marketplace_asset_category ON ecos_control.ecos_marketplace_asset USING btree (category);


--
-- Name: idx_marketplace_asset_popularity; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_marketplace_asset_popularity ON ecos_control.ecos_marketplace_asset USING btree (popularity DESC);


--
-- Name: idx_marketplace_asset_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_marketplace_asset_status ON ecos_control.ecos_marketplace_asset USING btree (status);


--
-- Name: idx_mcl_datasource; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_mcl_datasource ON ecos_control.td_metadata_collect_log USING btree (datasource_id, create_time DESC);


--
-- Name: idx_mind_scenario; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_mind_scenario ON ecos_control.ecos_scenario_mind USING btree (scenario_id, is_deleted);


--
-- Name: idx_obj_links_relation; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_obj_links_relation ON ecos_control.ecos_object_links USING btree (relation_code);


--
-- Name: idx_obj_links_source; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_obj_links_source ON ecos_control.ecos_object_links USING btree (source_id);


--
-- Name: idx_obj_links_target; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_obj_links_target ON ecos_control.ecos_object_links USING btree (target_id);


--
-- Name: idx_obj_rel_source; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_obj_rel_source ON ecos_control.ecos_object_relationship USING btree (source_object_id);


--
-- Name: idx_obj_rel_target; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_obj_rel_target ON ecos_control.ecos_object_relationship USING btree (target_object_id);


--
-- Name: idx_object_data_tenant; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_object_data_tenant ON ecos_control.ecos_object_data USING btree (tenant_id);


--
-- Name: idx_pipeline_tenant; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_pipeline_tenant ON ecos_control.ecos_pipeline_definition USING btree (tenant_id);


--
-- Name: idx_proposals_author; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_proposals_author ON ecos_control.ecos_ontology_proposals USING btree (author);


--
-- Name: idx_proposals_domain; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_proposals_domain ON ecos_control.ecos_ontology_proposals USING btree (domain_code, status);


--
-- Name: idx_provenance_entity; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_provenance_entity ON ecos_control.ecos_provenance_entry USING btree (entity_type, entity_id);


--
-- Name: idx_qh_ds; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_qh_ds ON ecos_control.ecos_query_history USING btree (datasource_id);


--
-- Name: idx_qh_time; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_qh_time ON ecos_control.ecos_query_history USING btree (started_at DESC);


--
-- Name: idx_rls_policy_resource; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_rls_policy_resource ON ecos_control.ecos_rls_policy USING btree (resource_id);


--
-- Name: idx_rsp_clearance; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_rsp_clearance ON ecos_control.td_role_security_profile USING btree (clearance_level);


--
-- Name: idx_rule_code; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX idx_rule_code ON ecos_control.ecos_ontology_rule USING btree (entity_id, code);


--
-- Name: idx_scen_bind_scenario; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_bind_scenario ON ecos_control.ecos_scenario_asset_binding USING btree (scenario_id);


--
-- Name: idx_scen_bind_tid; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_bind_tid ON ecos_control.ecos_scenario_asset_binding USING btree (target_id);


--
-- Name: idx_scen_bind_type; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_bind_type ON ecos_control.ecos_scenario_asset_binding USING btree (binding_type);


--
-- Name: idx_scen_dec_mind; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_dec_mind ON ecos_control.ecos_scenario_decision_record USING btree (mind_id);


--
-- Name: idx_scen_dec_run; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_dec_run ON ecos_control.ecos_scenario_decision_record USING btree (forecast_run_id);


--
-- Name: idx_scen_dec_scenario; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_dec_scenario ON ecos_control.ecos_scenario_decision_record USING btree (scenario_id, is_deleted);


--
-- Name: idx_scen_def_dept; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_def_dept ON ecos_control.ecos_scenario_definition USING btree (department);


--
-- Name: idx_scen_def_statdel; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_def_statdel ON ecos_control.ecos_scenario_definition USING btree (status, is_deleted, domain);


--
-- Name: idx_scen_def_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_def_status ON ecos_control.ecos_scenario_definition USING btree (status);


--
-- Name: idx_scen_exec_mode; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_exec_mode ON ecos_control.ecos_scenario_execution USING btree (run_mode, scenario_id);


--
-- Name: idx_scen_exec_scenario; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_exec_scenario ON ecos_control.ecos_scenario_execution USING btree (scenario_id);


--
-- Name: idx_scen_exec_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_exec_status ON ecos_control.ecos_scenario_execution USING btree (status);


--
-- Name: idx_scen_exec_time; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_exec_time ON ecos_control.ecos_scenario_execution USING btree (create_time DESC);


--
-- Name: idx_scen_exec_type; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_exec_type ON ecos_control.ecos_scenario_execution USING btree (run_type);


--
-- Name: idx_scen_link_sco; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_link_sco ON ecos_control.ecos_scenario_binding_edge USING btree (scenario_id);


--
-- Name: idx_scen_link_sco_typ; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_link_sco_typ ON ecos_control.ecos_scenario_binding_edge USING btree (scenario_id, link_type);


--
-- Name: idx_scen_link_src; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_link_src ON ecos_control.ecos_scenario_binding_edge USING btree (source_binding_id);


--
-- Name: idx_scen_link_tgt; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_link_tgt ON ecos_control.ecos_scenario_binding_edge USING btree (target_binding_id);


--
-- Name: idx_scen_link_typ; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_link_typ ON ecos_control.ecos_scenario_binding_edge USING btree (link_type);


--
-- Name: idx_scen_mind_var_scenario; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scen_mind_var_scenario ON ecos_control.ecos_scenario_mind_variant USING btree (scenario_id, is_deleted);


--
-- Name: idx_scenario_bind_tid; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scenario_bind_tid ON ecos_control.ecos_scenario_binding USING btree (target_id);


--
-- Name: idx_scenario_bindsc; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scenario_bindsc ON ecos_control.ecos_scenario_binding USING btree (scenario_id);


--
-- Name: idx_scenario_bindst; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scenario_bindst ON ecos_control.ecos_scenario_binding USING btree (binding_type);


--
-- Name: idx_scenario_run_sc; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scenario_run_sc ON ecos_control.ecos_scenario_run USING btree (scenario_id);


--
-- Name: idx_scenario_run_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scenario_run_status ON ecos_control.ecos_scenario_run USING btree (status);


--
-- Name: idx_scenario_run_time; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scenario_run_time ON ecos_control.ecos_scenario_run USING btree (create_time DESC);


--
-- Name: idx_scenario_run_type; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_scenario_run_type ON ecos_control.ecos_scenario_run USING btree (run_type);


--
-- Name: idx_schema_changes_ack; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_schema_changes_ack ON ecos_control.schema_changes USING btree (acknowledged, detected_at);


--
-- Name: idx_spans_created; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_spans_created ON ecos_control.ecos_spans USING btree (created_at DESC);


--
-- Name: idx_spans_path; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_spans_path ON ecos_control.ecos_spans USING btree (http_path);


--
-- Name: idx_spans_trace; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_spans_trace ON ecos_control.ecos_spans USING btree (trace_id);


--
-- Name: idx_sys_compliance_rule_domain; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_sys_compliance_rule_domain ON ecos_control.sys_compliance_rule USING btree (domain);


--
-- Name: idx_sys_compliance_rule_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_sys_compliance_rule_status ON ecos_control.sys_compliance_rule USING btree (status);


--
-- Name: idx_td_datasource_create_time; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_td_datasource_create_time ON ecos_control.td_datasource USING btree (create_time DESC);


--
-- Name: idx_td_runtime_task_create_time; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_td_runtime_task_create_time ON ecos_control.td_runtime_task USING btree (create_time);


--
-- Name: idx_td_runtime_task_domain; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_td_runtime_task_domain ON ecos_control.td_runtime_task USING btree (domain);


--
-- Name: idx_td_runtime_task_is_deleted; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_td_runtime_task_is_deleted ON ecos_control.td_runtime_task USING btree (is_deleted) WHERE (is_deleted = 0);


--
-- Name: idx_td_task_create_time; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_td_task_create_time ON ecos_control.td_runtime_task USING btree (create_time);


--
-- Name: idx_td_task_domain; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_td_task_domain ON ecos_control.td_runtime_task USING btree (domain);


--
-- Name: idx_td_task_is_deleted; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_td_task_is_deleted ON ecos_control.td_runtime_task USING btree (is_deleted) WHERE (is_deleted = 0);


--
-- Name: idx_td_task_plan_cron; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_td_task_plan_cron ON ecos_control.td_runtime_task_plan USING btree (cron_expression) WHERE (cron_expression IS NOT NULL);


--
-- Name: idx_td_task_plan_last_run_at; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_td_task_plan_last_run_at ON ecos_control.td_runtime_task_plan USING btree (last_run_at);


--
-- Name: idx_td_task_plan_next_run_at; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_td_task_plan_next_run_at ON ecos_control.td_runtime_task_plan USING btree (next_run_at) WHERE (next_run_at IS NOT NULL);


--
-- Name: idx_td_task_plan_task_type; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_td_task_plan_task_type ON ecos_control.td_runtime_task_plan USING btree (task_type);


--
-- Name: idx_td_task_task_type; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_td_task_task_type ON ecos_control.td_runtime_task USING btree ("TASK_TYPE", is_deleted);


--
-- Name: idx_tenant_quota_type; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX idx_tenant_quota_type ON ecos_control.ecos_tenant_quota USING btree (tenant_id, quota_type);


--
-- Name: idx_tenant_usage_date; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_tenant_usage_date ON ecos_control.ecos_tenant_usage USING btree (tenant_id, usage_date DESC);


--
-- Name: idx_token_usage_created; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_token_usage_created ON ecos_control.ecos_token_usage USING btree (created_at DESC);


--
-- Name: idx_token_usage_trace; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_token_usage_trace ON ecos_control.ecos_token_usage USING btree (trace_id);


--
-- Name: idx_usp_clearance; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_usp_clearance ON ecos_control.td_user_security_profile USING btree (clearance_level);


--
-- Name: idx_wm_causal_source; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_wm_causal_source ON ecos_control.ecos_wm_causal_link USING btree (source_goal_id);


--
-- Name: idx_wm_causal_target; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_wm_causal_target ON ecos_control.ecos_wm_causal_link USING btree (target_goal_id);


--
-- Name: idx_wm_goal_parent; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_wm_goal_parent ON ecos_control.ecos_wm_goal USING btree (parent_id);


--
-- Name: idx_wm_goal_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_wm_goal_status ON ecos_control.ecos_wm_goal USING btree (status);


--
-- Name: idx_wm_scenario_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_wm_scenario_status ON ecos_control.ecos_wm_scenario USING btree (status);


--
-- Name: idx_workflow_log_instance; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX idx_workflow_log_instance ON ecos_control.ecos_workflow_log USING btree (instance_id, created_at DESC);


--
-- Name: sys_agent_call_log_idx_created_time; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX sys_agent_call_log_idx_created_time ON ecos_control.sys_agent_call_log USING btree (created_time);


--
-- Name: sys_agent_call_log_idx_subsystem; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX sys_agent_call_log_idx_subsystem ON ecos_control.sys_agent_call_log USING btree (subsystem);


--
-- Name: sys_agent_call_log_idx_subsystem_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX sys_agent_call_log_idx_subsystem_status ON ecos_control.sys_agent_call_log USING btree (subsystem, status);


--
-- Name: td_abac_policy_IDX_RESOURCE_ACTION; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_abac_policy_IDX_RESOURCE_ACTION" ON ecos_control.td_abac_policy USING btree ("RESOURCE_ID", "ACTION");


--
-- Name: td_audit_log_IDX_ACTION; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_audit_log_IDX_ACTION" ON ecos_control.td_audit_log USING btree ("ACTION");


--
-- Name: td_audit_log_IDX_CREATED_TIME; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_audit_log_IDX_CREATED_TIME" ON ecos_control.td_audit_log USING btree ("CREATED_TIME");


--
-- Name: td_audit_log_IDX_RESOURCE; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_audit_log_IDX_RESOURCE" ON ecos_control.td_audit_log USING btree ("RESOURCE_TYPE", "RESOURCE_ID");


--
-- Name: td_audit_log_IDX_USER_ID; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_audit_log_IDX_USER_ID" ON ecos_control.td_audit_log USING btree ("USER_ID");


--
-- Name: td_config_IDX_CONFIG_TYPE; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_config_IDX_CONFIG_TYPE" ON ecos_control.td_config USING btree ("CONFIG_TYPE");


--
-- Name: td_config_UK_CONFIG_KEY_TYPE; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX "td_config_UK_CONFIG_KEY_TYPE" ON ecos_control.td_config USING btree ("CONFIG_KEY", "CONFIG_TYPE", "ENVIRONMENT");


--
-- Name: td_config_version_IDX_CONFIG_ID; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_config_version_IDX_CONFIG_ID" ON ecos_control.td_config_version USING btree ("CONFIG_ID");


--
-- Name: td_config_version_IDX_VERSION_NUMBER; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_config_version_IDX_VERSION_NUMBER" ON ecos_control.td_config_version USING btree ("VERSION_NUMBER");


--
-- Name: td_cross_border_transfer_IDX_RESOURCE_ID; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_cross_border_transfer_IDX_RESOURCE_ID" ON ecos_control.td_cross_border_transfer USING btree ("RESOURCE_ID");


--
-- Name: td_crypto_key_audit_idx_key_id; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_crypto_key_audit_idx_key_id ON ecos_control.td_crypto_key_audit USING btree ("KEY_ID");


--
-- Name: td_crypto_key_audit_idx_operation_time; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_crypto_key_audit_idx_operation_time ON ecos_control.td_crypto_key_audit USING btree ("OPERATION_TIME");


--
-- Name: td_crypto_key_audit_idx_operation_type; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_crypto_key_audit_idx_operation_type ON ecos_control.td_crypto_key_audit USING btree ("OPERATION_TYPE");


--
-- Name: td_crypto_key_audit_idx_operator; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_crypto_key_audit_idx_operator ON ecos_control.td_crypto_key_audit USING btree ("OPERATOR_ID");


--
-- Name: td_crypto_key_idx_key_id; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_crypto_key_idx_key_id ON ecos_control.td_crypto_key USING btree ("KEY_ID");


--
-- Name: td_crypto_key_idx_key_type; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_crypto_key_idx_key_type ON ecos_control.td_crypto_key USING btree ("KEY_TYPE");


--
-- Name: td_crypto_key_idx_next_rotation; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_crypto_key_idx_next_rotation ON ecos_control.td_crypto_key USING btree ("NEXT_ROTATION_TIME");


--
-- Name: td_crypto_key_idx_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_crypto_key_idx_status ON ecos_control.td_crypto_key USING btree ("STATUS");


--
-- Name: td_crypto_master_key_MASTER_KEY_NAME; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX "td_crypto_master_key_MASTER_KEY_NAME" ON ecos_control.td_crypto_master_key USING btree ("MASTER_KEY_NAME");


--
-- Name: td_crypto_master_key_idx_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_crypto_master_key_idx_status ON ecos_control.td_crypto_master_key USING btree ("STATUS");


--
-- Name: td_data_description_IDX_CREATED_TIME; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_data_description_IDX_CREATED_TIME" ON ecos_control.td_data_description USING btree ("CREATED_TIME");


--
-- Name: td_data_description_IDX_DATA_TYPE; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_data_description_IDX_DATA_TYPE" ON ecos_control.td_data_description USING btree ("DATA_TYPE");


--
-- Name: td_data_description_IDX_FORMAT; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_data_description_IDX_FORMAT" ON ecos_control.td_data_description USING btree ("FORMAT");


--
-- Name: td_data_description_IDX_NAME; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_data_description_IDX_NAME" ON ecos_control.td_data_description USING btree ("NAME");


--
-- Name: td_data_description_IDX_STATUS; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_data_description_IDX_STATUS" ON ecos_control.td_data_description USING btree ("STATUS");


--
-- Name: td_data_permission_policy_IDX_RESOURCE_TYPE; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_data_permission_policy_IDX_RESOURCE_TYPE" ON ecos_control.td_data_permission_policy USING btree ("RESOURCE_ID", "POLICY_TYPE");


--
-- Name: td_data_residency_IDX_RESOURCE_ID; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_data_residency_IDX_RESOURCE_ID" ON ecos_control.td_data_residency USING btree ("RESOURCE_ID");


--
-- Name: td_data_security_policy_idx_enabled; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_data_security_policy_idx_enabled ON ecos_control.td_data_security_policy USING btree (enabled);


--
-- Name: td_data_security_policy_idx_policy_type; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_data_security_policy_idx_policy_type ON ecos_control.td_data_security_policy USING btree (policy_type);


--
-- Name: td_data_security_policy_idx_scope; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_data_security_policy_idx_scope ON ecos_control.td_data_security_policy USING btree (scope, scope_id);


--
-- Name: td_data_security_policy_idx_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_data_security_policy_idx_status ON ecos_control.td_data_security_policy USING btree (status);


--
-- Name: td_git_repository_UK_REPO_NAME; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX "td_git_repository_UK_REPO_NAME" ON ecos_control.td_git_repository USING btree ("REPO_NAME");


--
-- Name: td_ip_access_IDX_ACCESS_TYPE; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_ip_access_IDX_ACCESS_TYPE" ON ecos_control.td_ip_access USING btree ("ACCESS_TYPE");


--
-- Name: td_ip_access_IDX_IP_ADDRESS; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_ip_access_IDX_IP_ADDRESS" ON ecos_control.td_ip_access USING btree ("IP_ADDRESS");


--
-- Name: td_ip_access_IDX_STATUS; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_ip_access_IDX_STATUS" ON ecos_control.td_ip_access USING btree ("STATUS");


--
-- Name: td_org_permission_IDX_ORG_ID; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_org_permission_IDX_ORG_ID" ON ecos_control.td_org_permission USING btree ("ORG_ID");


--
-- Name: td_org_permission_IDX_RESOURCE_ID; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_org_permission_IDX_RESOURCE_ID" ON ecos_control.td_org_permission USING btree ("RESOURCE_ID");


--
-- Name: td_organization_IDX_ORG_TYPE; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_organization_IDX_ORG_TYPE" ON ecos_control.td_organization USING btree ("ORG_TYPE");


--
-- Name: td_organization_IDX_PARENT_ORG_ID; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_organization_IDX_PARENT_ORG_ID" ON ecos_control.td_organization USING btree ("PARENT_ORG_ID");


--
-- Name: td_organization_IDX_STATUS; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_organization_IDX_STATUS" ON ecos_control.td_organization USING btree ("STATUS");


--
-- Name: td_organization_UK_ORG_CODE; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX "td_organization_UK_ORG_CODE" ON ecos_control.td_organization USING btree ("ORG_CODE");


--
-- Name: td_permission_UK_PERMISSION_CODE; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX "td_permission_UK_PERMISSION_CODE" ON ecos_control.td_permission USING btree ("PERMISSION_CODE");


--
-- Name: td_role_IDX_PARENT_ROLE; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_role_IDX_PARENT_ROLE" ON ecos_control.td_role USING btree ("PARENT_ROLE_ID");


--
-- Name: td_role_UK_ROLE_CODE; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX "td_role_UK_ROLE_CODE" ON ecos_control.td_role USING btree ("ROLE_CODE");


--
-- Name: td_role_permission_IDX_PERMISSION_ID; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_role_permission_IDX_PERMISSION_ID" ON ecos_control.td_role_permission USING btree ("PERMISSION_ID");


--
-- Name: td_role_permission_IDX_ROLE_ID; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_role_permission_IDX_ROLE_ID" ON ecos_control.td_role_permission USING btree ("ROLE_ID");


--
-- Name: td_runtime_task_execution_idx_execution_node_id; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_runtime_task_execution_idx_execution_node_id ON ecos_control.td_runtime_task_execution USING btree ("EXECUTION_NODE_ID");


--
-- Name: td_runtime_task_execution_idx_start_time; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_runtime_task_execution_idx_start_time ON ecos_control.td_runtime_task_execution USING btree ("START_TIME");


--
-- Name: td_runtime_task_execution_idx_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_runtime_task_execution_idx_status ON ecos_control.td_runtime_task_execution USING btree ("STATUS");


--
-- Name: td_runtime_task_execution_idx_task_id; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_runtime_task_execution_idx_task_id ON ecos_control.td_runtime_task_execution USING btree ("TASK_ID");


--
-- Name: td_runtime_task_idx_created_time; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_runtime_task_idx_created_time ON ecos_control.td_runtime_task USING btree ("CREATED_TIME");


--
-- Name: td_runtime_task_idx_execution_mode; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_runtime_task_idx_execution_mode ON ecos_control.td_runtime_task USING btree ("EXECUTION_MODE");


--
-- Name: td_runtime_task_idx_node_id; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_runtime_task_idx_node_id ON ecos_control.td_runtime_task USING btree ("NODE_ID");


--
-- Name: td_runtime_task_idx_schedule_id; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_runtime_task_idx_schedule_id ON ecos_control.td_runtime_task USING btree ("SCHEDULE_ID");


--
-- Name: td_runtime_task_log_idx_created_time; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_runtime_task_log_idx_created_time ON ecos_control.td_runtime_task_log USING btree ("CREATED_TIME");


--
-- Name: td_runtime_task_log_idx_execution_id; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_runtime_task_log_idx_execution_id ON ecos_control.td_runtime_task_log USING btree ("EXECUTION_ID");


--
-- Name: td_runtime_task_log_idx_log_level; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_runtime_task_log_idx_log_level ON ecos_control.td_runtime_task_log USING btree ("LOG_LEVEL");


--
-- Name: td_runtime_task_log_idx_task_id; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_runtime_task_log_idx_task_id ON ecos_control.td_runtime_task_log USING btree ("TASK_ID");


--
-- Name: td_runtime_task_plan_idx_execution_mode; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_runtime_task_plan_idx_execution_mode ON ecos_control.td_runtime_task_plan USING btree ("EXECUTION_MODE");


--
-- Name: td_runtime_task_plan_idx_target_node_id; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_runtime_task_plan_idx_target_node_id ON ecos_control.td_runtime_task_plan USING btree ("TARGET_NODE_ID");


--
-- Name: td_runtime_task_status_idx_execution_node_id; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_runtime_task_status_idx_execution_node_id ON ecos_control.td_runtime_task_status USING btree ("EXECUTION_NODE_ID");


--
-- Name: td_runtime_task_status_idx_start_time; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_runtime_task_status_idx_start_time ON ecos_control.td_runtime_task_status USING btree ("START_TIME");


--
-- Name: td_runtime_task_status_idx_status; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX td_runtime_task_status_idx_status ON ecos_control.td_runtime_task_status USING btree ("STATUS");


--
-- Name: td_schema_registry_IDX_CREATED_TIME; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_schema_registry_IDX_CREATED_TIME" ON ecos_control.td_schema_registry USING btree ("CREATED_TIME");


--
-- Name: td_schema_registry_IDX_SUBJECT; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_schema_registry_IDX_SUBJECT" ON ecos_control.td_schema_registry USING btree ("SUBJECT");


--
-- Name: td_schema_registry_UK_SUBJECT_VERSION; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX "td_schema_registry_UK_SUBJECT_VERSION" ON ecos_control.td_schema_registry USING btree ("SUBJECT", "VERSION");


--
-- Name: td_schema_version_IDX_SCHEMA_ID; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_schema_version_IDX_SCHEMA_ID" ON ecos_control.td_schema_version USING btree ("SCHEMA_ID");


--
-- Name: td_schema_version_IDX_SUBJECT_VERSION; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_schema_version_IDX_SUBJECT_VERSION" ON ecos_control.td_schema_version USING btree ("SUBJECT", "VERSION");


--
-- Name: td_sm_user_username; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX td_sm_user_username ON ecos_control.td_sm_user USING btree (username);


--
-- Name: td_system_param_IDX_PARAM_TYPE; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_system_param_IDX_PARAM_TYPE" ON ecos_control.td_system_param USING btree ("PARAM_TYPE");


--
-- Name: td_system_param_UK_PARAM_NAME; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX "td_system_param_UK_PARAM_NAME" ON ecos_control.td_system_param USING btree ("PARAM_NAME");


--
-- Name: td_system_variable_IDX_SCOPE_ID; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_system_variable_IDX_SCOPE_ID" ON ecos_control.td_system_variable USING btree ("SCOPE_ID");


--
-- Name: td_system_variable_IDX_VAR_STATUS; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_system_variable_IDX_VAR_STATUS" ON ecos_control.td_system_variable USING btree ("VAR_STATUS");


--
-- Name: td_system_variable_UK_VAR_CODE_SCOPE; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX "td_system_variable_UK_VAR_CODE_SCOPE" ON ecos_control.td_system_variable USING btree ("VAR_CODE", "SCOPE_ID");


--
-- Name: td_tenant_UK_DOMAIN; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX "td_tenant_UK_DOMAIN" ON ecos_control.td_tenant USING btree ("DOMAIN");


--
-- Name: td_tenant_UK_TENANT_NAME; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX "td_tenant_UK_TENANT_NAME" ON ecos_control.td_tenant USING btree ("TENANT_NAME");


--
-- Name: td_tenant_config_IDX_TENANT_ID; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_tenant_config_IDX_TENANT_ID" ON ecos_control.td_tenant_config USING btree ("TENANT_ID");


--
-- Name: td_tenant_config_UK_TENANT_KEY; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX "td_tenant_config_UK_TENANT_KEY" ON ecos_control.td_tenant_config USING btree ("TENANT_ID", "CONFIG_KEY");


--
-- Name: td_user_IDX_EMAIL; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_user_IDX_EMAIL" ON ecos_control.td_user USING btree ("EMAIL");


--
-- Name: td_user_IDX_STATUS; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_user_IDX_STATUS" ON ecos_control.td_user USING btree ("STATUS");


--
-- Name: td_user_UK_USERNAME; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX "td_user_UK_USERNAME" ON ecos_control.td_user USING btree ("USERNAME");


--
-- Name: td_user_organization_IDX_IS_PRIMARY; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_user_organization_IDX_IS_PRIMARY" ON ecos_control.td_user_organization USING btree ("IS_PRIMARY");


--
-- Name: td_user_organization_IDX_ORG_ID; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_user_organization_IDX_ORG_ID" ON ecos_control.td_user_organization USING btree ("ORG_ID");


--
-- Name: td_user_role_IDX_ROLE_ID; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_user_role_IDX_ROLE_ID" ON ecos_control.td_user_role USING btree ("ROLE_ID");


--
-- Name: td_user_role_IDX_USER_ID; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE INDEX "td_user_role_IDX_USER_ID" ON ecos_control.td_user_role USING btree ("USER_ID");


--
-- Name: uniq_cog_model_id_ver; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX uniq_cog_model_id_ver ON ecos_control.ecos_cognitive_model USING btree (model_id, version);


--
-- Name: uniq_ecos_cog_run_inv_evt_run; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX uniq_ecos_cog_run_inv_evt_run ON ecos_control.ecos_cognitive_run_invalidation USING btree (event_id, run_id);


--
-- Name: uniq_ecos_cognitive_belief_variable_ver; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX uniq_ecos_cognitive_belief_variable_ver ON ecos_control.ecos_cognitive_belief USING btree (variable_name, domain, version);


--
-- Name: uniq_ecos_cognitive_evidence_code; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX uniq_ecos_cognitive_evidence_code ON ecos_control.ecos_cognitive_evidence USING btree (evidence_code);


--
-- Name: uniq_ecos_cognitive_hypothesis_code; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX uniq_ecos_cognitive_hypothesis_code ON ecos_control.ecos_cognitive_hypothesis USING btree (hypothesis_code);


--
-- Name: uniq_ecos_ontology_data_key; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX uniq_ecos_ontology_data_key ON ecos_control.ecos_ontology_data USING btree (ontology_id, object_type, record_key);


--
-- Name: uniq_ecos_warn_log_log_id; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX uniq_ecos_warn_log_log_id ON ecos_control.ecos_warn_log USING btree (log_id);


--
-- Name: uniq_esmr_ref; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX uniq_esmr_ref ON ecos_control.ecos_scenario_mind_ref USING btree (mind_id, ref_kind, ref_id);


--
-- Name: uniq_glossary_term_primary; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX uniq_glossary_term_primary ON ecos_control.ecos_glossary_term USING btree (object_type_id) WHERE (is_primary AND (object_type_id IS NOT NULL));


--
-- Name: uniq_scen_canvas_scenario; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX uniq_scen_canvas_scenario ON ecos_control.ecos_scenario_canvas_layout USING btree (scenario_id);


--
-- Name: uniq_scen_link_edge; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX uniq_scen_link_edge ON ecos_control.ecos_scenario_binding_edge USING btree (scenario_id, source_binding_id, target_binding_id, link_type);


--
-- Name: uniq_scen_mind_var_scenario_label; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX uniq_scen_mind_var_scenario_label ON ecos_control.ecos_scenario_mind_variant USING btree (scenario_id, mind_label, deleted_guard);


--
-- Name: uq_mind_active; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX uq_mind_active ON ecos_control.ecos_scenario_mind USING btree (scenario_id) WHERE ((is_deleted = 0) AND (active_mind = 1));


--
-- Name: uq_mind_scenario_label; Type: INDEX; Schema: ecos_control; Owner: postgres
--

CREATE UNIQUE INDEX uq_mind_scenario_label ON ecos_control.ecos_scenario_mind USING btree (scenario_id, mind_label) WHERE (is_deleted = 0);


--
-- Name: idx_asset_category; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_asset_category ON ecos_data.ecos_data_asset USING btree (category_id);


--
-- Name: idx_asset_field_asset; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_asset_field_asset ON ecos_data.ecos_data_asset_field USING btree (asset_id);


--
-- Name: idx_asset_field_conf; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_asset_field_conf ON ecos_data.ecos_data_asset_field USING btree (confirmed);


--
-- Name: idx_asset_field_sens; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_asset_field_sens ON ecos_data.ecos_data_asset_field USING btree (field_sensitivity);


--
-- Name: idx_asset_owner; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_asset_owner ON ecos_data.ecos_data_asset USING btree (owner);


--
-- Name: idx_asset_sensitivity; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_asset_sensitivity ON ecos_data.ecos_data_asset USING btree (sensitivity_level);


--
-- Name: idx_category_parent; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_category_parent ON ecos_data.td_data_category USING btree (parent_id);


--
-- Name: idx_category_tree_level; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_category_tree_level ON ecos_data.ecos_data_category_tree USING btree (level);


--
-- Name: idx_category_tree_parent; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_category_tree_parent ON ecos_data.ecos_data_category_tree USING btree (parent_id);


--
-- Name: idx_data_cat_parent; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_cat_parent ON ecos_data.td_data_category USING btree (parent_id);


--
-- Name: idx_data_cat_path; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_cat_path ON ecos_data.td_catalog_item USING btree (category_path);


--
-- Name: idx_data_cat_res; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_cat_res ON ecos_data.td_catalog_item USING btree (resource_id);


--
-- Name: idx_data_cat_type; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_cat_type ON ecos_data.td_catalog_item USING btree (resource_type);


--
-- Name: idx_data_cog_enabled; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_cog_enabled ON ecos_data.ecos_cognitive_rule USING btree (enabled);


--
-- Name: idx_data_cog_type; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_cog_type ON ecos_data.ecos_cognitive_rule USING btree (rule_type);


--
-- Name: idx_data_dq_tenant; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_dq_tenant ON ecos_data.ecos_dq_rule USING btree (tenant_id);


--
-- Name: idx_data_dq_type; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_dq_type ON ecos_data.ecos_dq_rule USING btree (rule_type);


--
-- Name: idx_data_dqi_rule; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_dqi_rule ON ecos_data.ecos_dq_issue USING btree (rule_id);


--
-- Name: idx_data_dqr_rule; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_dqr_rule ON ecos_data.ecos_dq_execution_result USING btree (rule_id);


--
-- Name: idx_data_dqr_time; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_dqr_time ON ecos_data.ecos_dq_execution_result USING btree (executed_at DESC);


--
-- Name: idx_data_ds_org; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_ds_org ON ecos_data.td_datasource USING btree (org_id);


--
-- Name: idx_data_ds_status; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_ds_status ON ecos_data.td_datasource USING btree (status);


--
-- Name: idx_data_ds_tenant; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_ds_tenant ON ecos_data.td_datasource USING btree (tenant_id);


--
-- Name: idx_data_field_res; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_field_res ON ecos_data.td_data_field USING btree (resource_id);


--
-- Name: idx_data_level_code; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_level_code ON ecos_data.ecos_data_level_def USING btree (level_code);


--
-- Name: idx_data_pdef_status; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_pdef_status ON ecos_data.ecos_pipeline_definition USING btree (status);


--
-- Name: idx_data_pdef_tenant; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_pdef_tenant ON ecos_data.ecos_pipeline_definition USING btree (tenant_id);


--
-- Name: idx_data_pedge_def; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_pedge_def ON ecos_data.ecos_pipeline_edge USING btree (definition_id);


--
-- Name: idx_data_pexec_pipeline; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_pexec_pipeline ON ecos_data.ecos_pipeline_execution USING btree (pipeline_id);


--
-- Name: idx_data_pexec_status; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_pexec_status ON ecos_data.ecos_pipeline_execution USING btree (status);


--
-- Name: idx_data_pexec_tenant; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_pexec_tenant ON ecos_data.ecos_pipeline_execution USING btree (tenant_id);


--
-- Name: idx_data_pnode_def; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_pnode_def ON ecos_data.ecos_pipeline_node USING btree (definition_id);


--
-- Name: idx_data_res_ds; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_res_ds ON ecos_data.td_data_resource USING btree (datasource_id);


--
-- Name: idx_data_res_org; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_res_org ON ecos_data.td_data_resource USING btree (org_id);


--
-- Name: idx_data_res_tenant; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_res_tenant ON ecos_data.td_data_resource USING btree (tenant_id);


--
-- Name: idx_data_task_status; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_task_status ON ecos_data.ecos_task USING btree (status);


--
-- Name: idx_data_task_time; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_task_time ON ecos_data.ecos_task USING btree (created_at DESC);


--
-- Name: idx_data_task_type; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_data_task_type ON ecos_data.ecos_task USING btree (task_type);


--
-- Name: idx_lineage_edge_src; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_lineage_edge_src ON ecos_data.ecos_data_lineage_edge USING btree (source_node_id);


--
-- Name: idx_lineage_edge_task; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_lineage_edge_task ON ecos_data.ecos_data_lineage_edge USING btree (pipeline_task_id);


--
-- Name: idx_lineage_edge_tgt; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_lineage_edge_tgt ON ecos_data.ecos_data_lineage_edge USING btree (target_node_id);


--
-- Name: idx_lineage_node_ds; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_lineage_node_ds ON ecos_data.ecos_data_lineage_node USING btree (datasource_id);


--
-- Name: idx_lineage_node_task; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_lineage_node_task ON ecos_data.ecos_data_lineage_node USING btree (pipeline_task_id) WHERE (pipeline_task_id IS NOT NULL);


--
-- Name: idx_lineage_node_type; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_lineage_node_type ON ecos_data.ecos_data_lineage_node USING btree (node_type);


--
-- Name: idx_query_history_ds; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_query_history_ds ON ecos_data.ecos_query_history USING btree (datasource_id);


--
-- Name: idx_query_history_started; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_query_history_started ON ecos_data.ecos_query_history USING btree (started_at DESC);


--
-- Name: idx_td_datasource_create_time_data; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE INDEX idx_td_datasource_create_time_data ON ecos_data.td_datasource USING btree (create_time DESC);


--
-- Name: uniq_asset_resource_domain; Type: INDEX; Schema: ecos_data; Owner: postgres
--

CREATE UNIQUE INDEX uniq_asset_resource_domain ON ecos_data.ecos_data_asset USING btree (resource_id, domain);


--
-- Name: idx_dq_alert_level; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_dq_alert_level ON ecos_dq.dq_alert_record USING btree (alert_level);


--
-- Name: idx_dq_alert_rule; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_dq_alert_rule ON ecos_dq.dq_alert_record USING btree (rule_id, created_at DESC);


--
-- Name: idx_dq_alert_status; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_dq_alert_status ON ecos_dq.dq_alert_record USING btree (status) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'NOTIFIED'::character varying])::text[]));


--
-- Name: idx_dq_alert_time; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_dq_alert_time ON ecos_dq.dq_alert_record USING btree (created_at DESC);


--
-- Name: idx_dq_check_rule_time; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_dq_check_rule_time ON ecos_dq.dq_rule_check USING btree (rule_id, executed_at DESC);


--
-- Name: idx_dq_check_time; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_dq_check_time ON ecos_dq.dq_rule_check USING btree (executed_at DESC);


--
-- Name: idx_dq_knowledge_category; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_dq_knowledge_category ON ecos_dq.dq_knowledge_entry USING btree (category);


--
-- Name: idx_dq_knowledge_type_source; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_dq_knowledge_type_source ON ecos_dq.dq_knowledge_entry USING btree (source_type, source_id);


--
-- Name: idx_dq_report_scope; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_dq_report_scope ON ecos_dq.dq_report USING btree (scope, end_date DESC);


--
-- Name: idx_dq_report_type_date; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_dq_report_type_date ON ecos_dq.dq_report USING btree (report_type, end_date DESC);


--
-- Name: idx_dq_rule_category; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_dq_rule_category ON ecos_dq.dq_rule USING btree (category) WHERE (is_deleted = false);


--
-- Name: idx_dq_rule_domain; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_dq_rule_domain ON ecos_dq.dq_rule USING btree (domain) WHERE (is_deleted = false);


--
-- Name: idx_dq_rule_rule_type; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_dq_rule_rule_type ON ecos_dq.dq_rule USING btree (rule_type) WHERE (is_deleted = false);


--
-- Name: idx_dq_rule_status; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_dq_rule_status ON ecos_dq.dq_rule USING btree (status) WHERE (is_deleted = false);


--
-- Name: idx_dq_rule_target; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_dq_rule_target ON ecos_dq.dq_rule USING btree (target_kind, target_id) WHERE (is_deleted = false);


--
-- Name: idx_dq_rule_version_rule; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_dq_rule_version_rule ON ecos_dq.dq_rule_version USING btree (rule_id, version_number DESC);


--
-- Name: idx_dq_schedule_enable; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_dq_schedule_enable ON ecos_dq.dq_schedule USING btree (trigger_type) WHERE (enabled AND (is_deleted = false));


--
-- Name: idx_dq_score_scope_time; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_dq_score_scope_time ON ecos_dq.dq_score_snapshot USING btree (scope_type, scope_id, evaluated_at DESC);


--
-- Name: idx_schema_changes_ack; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_schema_changes_ack ON ecos_dq.schema_changes USING btree (acknowledged, detected_at);


--
-- Name: idx_wo_alert; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_wo_alert ON ecos_dq.dq_work_order USING btree (alert_id);


--
-- Name: idx_wo_asset; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_wo_asset ON ecos_dq.dq_work_order USING btree (asset_id) WHERE (is_deleted = false);


--
-- Name: idx_wo_severity; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_wo_severity ON ecos_dq.dq_work_order USING btree (severity, status) WHERE (is_deleted = false);


--
-- Name: idx_wo_status; Type: INDEX; Schema: ecos_dq; Owner: postgres
--

CREATE INDEX idx_wo_status ON ecos_dq.dq_work_order USING btree (status) WHERE (is_deleted = false);


--
-- Name: idx_dw_doc_chunk_doc; Type: INDEX; Schema: ecos_dw; Owner: postgres
--

CREATE INDEX idx_dw_doc_chunk_doc ON ecos_dw.doc_chunk USING btree (doc_id);


--
-- Name: idx_dw_doc_parse_status; Type: INDEX; Schema: ecos_dw; Owner: postgres
--

CREATE INDEX idx_dw_doc_parse_status ON ecos_dw.doc USING btree (parse_status);


--
-- Name: uniq_dw_doc_chunk_chunk_id; Type: INDEX; Schema: ecos_dw; Owner: postgres
--

CREATE UNIQUE INDEX uniq_dw_doc_chunk_chunk_id ON ecos_dw.doc_chunk USING btree (chunk_id);


--
-- Name: uniq_dw_doc_chunk_doc_idx; Type: INDEX; Schema: ecos_dw; Owner: postgres
--

CREATE UNIQUE INDEX uniq_dw_doc_chunk_doc_idx ON ecos_dw.doc_chunk USING btree (doc_id, chunk_index);


--
-- Name: uniq_dw_doc_doc_id; Type: INDEX; Schema: ecos_dw; Owner: postgres
--

CREATE UNIQUE INDEX uniq_dw_doc_doc_id ON ecos_dw.doc USING btree (doc_id);


--
-- Name: idx_infra_outbox_aggregate; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX idx_infra_outbox_aggregate ON ONLY ecos_infra.outbox_event USING btree (aggregate_type, aggregate_id);


--
-- Name: idx_infra_outbox_published; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX idx_infra_outbox_published ON ONLY ecos_infra.outbox_event USING btree (published) WHERE (NOT published);


--
-- Name: idx_infra_outbox_type; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX idx_infra_outbox_type ON ONLY ecos_infra.outbox_event USING btree (event_type);


--
-- Name: idx_infra_saga_status; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX idx_infra_saga_status ON ecos_infra.saga_instance USING btree (status);


--
-- Name: idx_infra_saga_type; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX idx_infra_saga_type ON ecos_infra.saga_instance USING btree (saga_type);


--
-- Name: idx_infra_schemaver_name_ver; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE UNIQUE INDEX idx_infra_schemaver_name_ver ON ecos_infra.schema_version USING btree (schema_name, version);


--
-- Name: outbox_event_2025_01_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_01_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_01 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_01_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_01_event_type_idx ON ecos_infra.outbox_event_2025_01 USING btree (event_type);


--
-- Name: outbox_event_2025_01_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_01_published_idx ON ecos_infra.outbox_event_2025_01 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_02_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_02_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_02 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_02_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_02_event_type_idx ON ecos_infra.outbox_event_2025_02 USING btree (event_type);


--
-- Name: outbox_event_2025_02_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_02_published_idx ON ecos_infra.outbox_event_2025_02 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_03_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_03_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_03 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_03_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_03_event_type_idx ON ecos_infra.outbox_event_2025_03 USING btree (event_type);


--
-- Name: outbox_event_2025_03_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_03_published_idx ON ecos_infra.outbox_event_2025_03 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_04_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_04_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_04 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_04_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_04_event_type_idx ON ecos_infra.outbox_event_2025_04 USING btree (event_type);


--
-- Name: outbox_event_2025_04_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_04_published_idx ON ecos_infra.outbox_event_2025_04 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_05_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_05_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_05 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_05_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_05_event_type_idx ON ecos_infra.outbox_event_2025_05 USING btree (event_type);


--
-- Name: outbox_event_2025_05_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_05_published_idx ON ecos_infra.outbox_event_2025_05 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_06_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_06_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_06 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_06_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_06_event_type_idx ON ecos_infra.outbox_event_2025_06 USING btree (event_type);


--
-- Name: outbox_event_2025_06_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_06_published_idx ON ecos_infra.outbox_event_2025_06 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_07_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_07_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_07 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_07_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_07_event_type_idx ON ecos_infra.outbox_event_2025_07 USING btree (event_type);


--
-- Name: outbox_event_2025_07_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_07_published_idx ON ecos_infra.outbox_event_2025_07 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_08_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_08_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_08 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_08_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_08_event_type_idx ON ecos_infra.outbox_event_2025_08 USING btree (event_type);


--
-- Name: outbox_event_2025_08_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_08_published_idx ON ecos_infra.outbox_event_2025_08 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_09_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_09_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_09 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_09_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_09_event_type_idx ON ecos_infra.outbox_event_2025_09 USING btree (event_type);


--
-- Name: outbox_event_2025_09_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_09_published_idx ON ecos_infra.outbox_event_2025_09 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_10_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_10_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_10 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_10_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_10_event_type_idx ON ecos_infra.outbox_event_2025_10 USING btree (event_type);


--
-- Name: outbox_event_2025_10_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_10_published_idx ON ecos_infra.outbox_event_2025_10 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_11_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_11_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_11 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_11_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_11_event_type_idx ON ecos_infra.outbox_event_2025_11 USING btree (event_type);


--
-- Name: outbox_event_2025_11_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_11_published_idx ON ecos_infra.outbox_event_2025_11 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2025_12_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_12_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2025_12 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2025_12_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_12_event_type_idx ON ecos_infra.outbox_event_2025_12 USING btree (event_type);


--
-- Name: outbox_event_2025_12_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2025_12_published_idx ON ecos_infra.outbox_event_2025_12 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_01_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_01_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_01 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_01_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_01_event_type_idx ON ecos_infra.outbox_event_2026_01 USING btree (event_type);


--
-- Name: outbox_event_2026_01_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_01_published_idx ON ecos_infra.outbox_event_2026_01 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_02_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_02_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_02 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_02_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_02_event_type_idx ON ecos_infra.outbox_event_2026_02 USING btree (event_type);


--
-- Name: outbox_event_2026_02_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_02_published_idx ON ecos_infra.outbox_event_2026_02 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_03_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_03_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_03 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_03_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_03_event_type_idx ON ecos_infra.outbox_event_2026_03 USING btree (event_type);


--
-- Name: outbox_event_2026_03_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_03_published_idx ON ecos_infra.outbox_event_2026_03 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_04_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_04_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_04 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_04_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_04_event_type_idx ON ecos_infra.outbox_event_2026_04 USING btree (event_type);


--
-- Name: outbox_event_2026_04_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_04_published_idx ON ecos_infra.outbox_event_2026_04 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_05_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_05_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_05 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_05_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_05_event_type_idx ON ecos_infra.outbox_event_2026_05 USING btree (event_type);


--
-- Name: outbox_event_2026_05_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_05_published_idx ON ecos_infra.outbox_event_2026_05 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_06_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_06_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_06 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_06_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_06_event_type_idx ON ecos_infra.outbox_event_2026_06 USING btree (event_type);


--
-- Name: outbox_event_2026_06_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_06_published_idx ON ecos_infra.outbox_event_2026_06 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_07_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_07_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_07 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_07_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_07_event_type_idx ON ecos_infra.outbox_event_2026_07 USING btree (event_type);


--
-- Name: outbox_event_2026_07_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_07_published_idx ON ecos_infra.outbox_event_2026_07 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_08_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_08_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_08 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_08_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_08_event_type_idx ON ecos_infra.outbox_event_2026_08 USING btree (event_type);


--
-- Name: outbox_event_2026_08_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_08_published_idx ON ecos_infra.outbox_event_2026_08 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_09_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_09_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_09 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_09_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_09_event_type_idx ON ecos_infra.outbox_event_2026_09 USING btree (event_type);


--
-- Name: outbox_event_2026_09_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_09_published_idx ON ecos_infra.outbox_event_2026_09 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_10_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_10_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_10 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_10_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_10_event_type_idx ON ecos_infra.outbox_event_2026_10 USING btree (event_type);


--
-- Name: outbox_event_2026_10_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_10_published_idx ON ecos_infra.outbox_event_2026_10 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_11_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_11_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_11 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_11_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_11_event_type_idx ON ecos_infra.outbox_event_2026_11 USING btree (event_type);


--
-- Name: outbox_event_2026_11_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_11_published_idx ON ecos_infra.outbox_event_2026_11 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2026_12_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_12_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2026_12 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2026_12_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_12_event_type_idx ON ecos_infra.outbox_event_2026_12 USING btree (event_type);


--
-- Name: outbox_event_2026_12_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2026_12_published_idx ON ecos_infra.outbox_event_2026_12 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_01_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_01_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_01 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_01_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_01_event_type_idx ON ecos_infra.outbox_event_2027_01 USING btree (event_type);


--
-- Name: outbox_event_2027_01_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_01_published_idx ON ecos_infra.outbox_event_2027_01 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_02_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_02_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_02 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_02_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_02_event_type_idx ON ecos_infra.outbox_event_2027_02 USING btree (event_type);


--
-- Name: outbox_event_2027_02_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_02_published_idx ON ecos_infra.outbox_event_2027_02 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_03_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_03_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_03 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_03_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_03_event_type_idx ON ecos_infra.outbox_event_2027_03 USING btree (event_type);


--
-- Name: outbox_event_2027_03_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_03_published_idx ON ecos_infra.outbox_event_2027_03 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_04_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_04_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_04 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_04_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_04_event_type_idx ON ecos_infra.outbox_event_2027_04 USING btree (event_type);


--
-- Name: outbox_event_2027_04_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_04_published_idx ON ecos_infra.outbox_event_2027_04 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_05_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_05_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_05 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_05_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_05_event_type_idx ON ecos_infra.outbox_event_2027_05 USING btree (event_type);


--
-- Name: outbox_event_2027_05_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_05_published_idx ON ecos_infra.outbox_event_2027_05 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_06_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_06_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_06 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_06_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_06_event_type_idx ON ecos_infra.outbox_event_2027_06 USING btree (event_type);


--
-- Name: outbox_event_2027_06_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_06_published_idx ON ecos_infra.outbox_event_2027_06 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_07_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_07_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_07 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_07_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_07_event_type_idx ON ecos_infra.outbox_event_2027_07 USING btree (event_type);


--
-- Name: outbox_event_2027_07_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_07_published_idx ON ecos_infra.outbox_event_2027_07 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_08_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_08_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_08 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_08_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_08_event_type_idx ON ecos_infra.outbox_event_2027_08 USING btree (event_type);


--
-- Name: outbox_event_2027_08_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_08_published_idx ON ecos_infra.outbox_event_2027_08 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_09_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_09_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_09 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_09_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_09_event_type_idx ON ecos_infra.outbox_event_2027_09 USING btree (event_type);


--
-- Name: outbox_event_2027_09_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_09_published_idx ON ecos_infra.outbox_event_2027_09 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_10_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_10_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_10 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_10_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_10_event_type_idx ON ecos_infra.outbox_event_2027_10 USING btree (event_type);


--
-- Name: outbox_event_2027_10_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_10_published_idx ON ecos_infra.outbox_event_2027_10 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_11_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_11_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_11 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_11_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_11_event_type_idx ON ecos_infra.outbox_event_2027_11 USING btree (event_type);


--
-- Name: outbox_event_2027_11_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_11_published_idx ON ecos_infra.outbox_event_2027_11 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_2027_12_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_12_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_2027_12 USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_2027_12_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_12_event_type_idx ON ecos_infra.outbox_event_2027_12 USING btree (event_type);


--
-- Name: outbox_event_2027_12_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_2027_12_published_idx ON ecos_infra.outbox_event_2027_12 USING btree (published) WHERE (NOT published);


--
-- Name: outbox_event_default_aggregate_type_aggregate_id_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_default_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_default USING btree (aggregate_type, aggregate_id);


--
-- Name: outbox_event_default_event_type_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_default_event_type_idx ON ecos_infra.outbox_event_default USING btree (event_type);


--
-- Name: outbox_event_default_published_idx; Type: INDEX; Schema: ecos_infra; Owner: postgres
--

CREATE INDEX outbox_event_default_published_idx ON ecos_infra.outbox_event_default USING btree (published) WHERE (NOT published);


--
-- Name: idx_graph_edge_ontology_version; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_graph_edge_ontology_version ON ecos_knowledge.graph_edge USING btree (ontology_id, ontology_version);


--
-- Name: idx_graph_node_label_trgm; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_graph_node_label_trgm ON ecos_knowledge.graph_node USING gin (label public.gin_trgm_ops);


--
-- Name: idx_graph_node_node_type; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_graph_node_node_type ON ecos_knowledge.graph_node USING btree (node_type);


--
-- Name: idx_graph_node_ontology_version; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_graph_node_ontology_version ON ecos_knowledge.graph_node USING btree (ontology_id, ontology_version);


--
-- Name: idx_kb_art_domain; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_art_domain ON ecos_knowledge.knowledge_article USING btree (domain);


--
-- Name: idx_kb_art_status; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_art_status ON ecos_knowledge.knowledge_article USING btree (status);


--
-- Name: idx_kb_doc_chunk_doc; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_doc_chunk_doc ON ecos_knowledge.kb_doc_chunk USING btree (doc_id);


--
-- Name: idx_kb_doc_chunk_status; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_doc_chunk_status ON ecos_knowledge.kb_doc_chunk USING btree (status);


--
-- Name: idx_kb_doc_parse_status; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_doc_parse_status ON ecos_knowledge.kb_doc USING btree (parse_status);


--
-- Name: idx_kb_emb_doc; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_emb_doc ON ecos_knowledge.knowledge_embedding USING btree (document_id);


--
-- Name: idx_kb_extract_watermark_ontology; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_extract_watermark_ontology ON ecos_knowledge.kb_extract_watermark USING btree (ontology_id);


--
-- Name: idx_kb_gedge_src; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_gedge_src ON ecos_knowledge.graph_edge USING btree (source_id);


--
-- Name: idx_kb_gedge_tgt; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_gedge_tgt ON ecos_knowledge.graph_edge USING btree (target_id);


--
-- Name: idx_kb_gedge_type; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_gedge_type ON ecos_knowledge.graph_edge USING btree (type);


--
-- Name: idx_kb_gloss_domain; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_gloss_domain ON ecos_knowledge.ecos_glossary_term USING btree (domain);


--
-- Name: idx_kb_gloss_status; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_gloss_status ON ecos_knowledge.ecos_glossary_term USING btree (status);


--
-- Name: idx_kb_gnode_domain; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_gnode_domain ON ecos_knowledge.graph_node USING btree (domain);


--
-- Name: idx_kb_gnode_label; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_gnode_label ON ecos_knowledge.graph_node USING btree (label);


--
-- Name: idx_kb_kge_src; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_kge_src ON ecos_knowledge.ecos_knowledge_graph_edge USING btree (source_node_id);


--
-- Name: idx_kb_kge_tgt; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_kge_tgt ON ecos_knowledge.ecos_knowledge_graph_edge USING btree (target_node_id);


--
-- Name: idx_kb_kgn_label; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_kgn_label ON ecos_knowledge.ecos_knowledge_graph_node USING btree (label);


--
-- Name: idx_kb_mkt_cat; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_mkt_cat ON ecos_knowledge.ecos_marketplace_asset USING btree (category);


--
-- Name: idx_kb_mkt_pop; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_mkt_pop ON ecos_knowledge.ecos_marketplace_asset USING btree (popularity DESC);


--
-- Name: idx_kb_mkt_stat; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_mkt_stat ON ecos_knowledge.ecos_marketplace_asset USING btree (status);


--
-- Name: idx_kb_mktreq_asset; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_mktreq_asset ON ecos_knowledge.ecos_marketplace_access_request USING btree (asset_id);


--
-- Name: idx_kb_mktreq_status; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_mktreq_status ON ecos_knowledge.ecos_marketplace_access_request USING btree (status);


--
-- Name: idx_kb_ontology_snapshot_created_at; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_ontology_snapshot_created_at ON ecos_knowledge.kb_ontology_snapshot USING btree (created_at DESC) WHERE (is_deleted = 0);


--
-- Name: idx_kb_ontology_snapshot_ontology; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_ontology_snapshot_ontology ON ecos_knowledge.kb_ontology_snapshot USING btree (ontology_id) WHERE ((is_deleted = 0) OR (is_deleted = 2));


--
-- Name: idx_kb_rule_domain; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_rule_domain ON ecos_knowledge.expert_rule USING btree (domain);


--
-- Name: idx_kb_rule_type; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kb_rule_type ON ecos_knowledge.expert_rule USING btree (rule_type);


--
-- Name: idx_kbea_job; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kbea_job ON ecos_knowledge.kb_extract_audit USING btree (job_id);


--
-- Name: idx_kbea_ts; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kbea_ts ON ecos_knowledge.kb_extract_audit USING btree (created_at DESC);


--
-- Name: idx_kbeic_doc; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kbeic_doc ON ecos_knowledge.kb_extract_candidate USING btree (doc_id);


--
-- Name: idx_kbeic_status; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kbeic_status ON ecos_knowledge.kb_extract_candidate USING btree (status);


--
-- Name: idx_kbsched_enabled; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kbsched_enabled ON ecos_knowledge.kb_scheduled_extract USING btree (enabled);


--
-- Name: idx_kg_sync_log_created_at; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kg_sync_log_created_at ON ecos_knowledge.kg_sync_log USING btree (created_at DESC);


--
-- Name: idx_kg_sync_log_job_id; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_kg_sync_log_job_id ON ecos_knowledge.kg_sync_log USING btree (job_id);


--
-- Name: idx_knowledge_embedding_vec_hnsw; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE INDEX idx_knowledge_embedding_vec_hnsw ON ecos_knowledge.knowledge_embedding USING hnsw (embedding_vec public.vector_cosine_ops);


--
-- Name: uniq_kb_doc_chunk_doc_idx; Type: INDEX; Schema: ecos_knowledge; Owner: postgres
--

CREATE UNIQUE INDEX uniq_kb_doc_chunk_doc_idx ON ecos_knowledge.kb_doc_chunk USING btree (doc_id, chunk_index);


--
-- Name: idx_onto_entdef_code; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE UNIQUE INDEX idx_onto_entdef_code ON ecos_ontology.entity_definition USING btree (code);


--
-- Name: idx_onto_links_src; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE INDEX idx_onto_links_src ON ecos_ontology.ecos_object_links USING btree (source_id);


--
-- Name: idx_onto_links_tgt; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE INDEX idx_onto_links_tgt ON ecos_ontology.ecos_object_links USING btree (target_id);


--
-- Name: idx_onto_metric_code; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE UNIQUE INDEX idx_onto_metric_code ON ecos_ontology.metric_definition USING btree (code);


--
-- Name: idx_onto_objdata_entity; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE INDEX idx_onto_objdata_entity ON ecos_ontology.ecos_object_data USING btree (entity_code);


--
-- Name: idx_onto_objdata_tenant; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE INDEX idx_onto_objdata_tenant ON ecos_ontology.ecos_object_data USING btree (tenant_id);


--
-- Name: idx_onto_objrel_src; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE INDEX idx_onto_objrel_src ON ecos_ontology.ecos_object_relationship USING btree (source_object_id);


--
-- Name: idx_onto_objrel_tenant; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE INDEX idx_onto_objrel_tenant ON ecos_ontology.ecos_object_relation USING btree (tenant_id);


--
-- Name: idx_onto_objrel_tgt; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE INDEX idx_onto_objrel_tgt ON ecos_ontology.ecos_object_relationship USING btree (target_object_id);


--
-- Name: idx_onto_objver_uniq; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE UNIQUE INDEX idx_onto_objver_uniq ON ecos_ontology.ecos_object_version USING btree (object_id, version_no);


--
-- Name: idx_onto_sm_uniq; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE UNIQUE INDEX idx_onto_sm_uniq ON ecos_ontology.ecos_object_state_machine USING btree (entity_code, from_status, transition_code);


--
-- Name: idx_onto_timeline_obj; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE INDEX idx_onto_timeline_obj ON ecos_ontology.ecos_object_timeline USING btree (object_id, created_at DESC);


--
-- Name: idx_onto_wfappr_inst; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE INDEX idx_onto_wfappr_inst ON ecos_ontology.ecos_workflow_approval USING btree (instance_id);


--
-- Name: idx_onto_wfappr_task; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE INDEX idx_onto_wfappr_task ON ecos_ontology.ecos_workflow_approval USING btree (task_id);


--
-- Name: idx_onto_wfinst_status; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE INDEX idx_onto_wfinst_status ON ecos_ontology.ecos_workflow_instance USING btree (status);


--
-- Name: idx_onto_wfinst_tenant; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE INDEX idx_onto_wfinst_tenant ON ecos_ontology.ecos_workflow_instance USING btree (tenant_id);


--
-- Name: idx_onto_wfinst_wf; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE INDEX idx_onto_wfinst_wf ON ecos_ontology.ecos_workflow_instance USING btree (workflow_id);


--
-- Name: idx_onto_wflog_inst; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE INDEX idx_onto_wflog_inst ON ecos_ontology.ecos_workflow_log USING btree (instance_id, created_at);


--
-- Name: idx_onto_wftask_assign; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE INDEX idx_onto_wftask_assign ON ecos_ontology.ecos_workflow_task USING btree (assignee);


--
-- Name: idx_onto_wftask_inst; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE INDEX idx_onto_wftask_inst ON ecos_ontology.ecos_workflow_task USING btree (instance_id);


--
-- Name: idx_onto_wftask_status; Type: INDEX; Schema: ecos_ontology; Owner: postgres
--

CREATE INDEX idx_onto_wftask_status ON ecos_ontology.ecos_workflow_task USING btree (status);


--
-- Name: idx_security_token_trace; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX idx_security_token_trace ON ONLY ecos_security.ecos_token_usage USING btree (trace_id);


--
-- Name: ecos_token_usage_2025_q1_trace_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX ecos_token_usage_2025_q1_trace_id_idx ON ecos_security.ecos_token_usage_2025_q1 USING btree (trace_id);


--
-- Name: ecos_token_usage_2025_q2_trace_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX ecos_token_usage_2025_q2_trace_id_idx ON ecos_security.ecos_token_usage_2025_q2 USING btree (trace_id);


--
-- Name: ecos_token_usage_2025_q3_trace_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX ecos_token_usage_2025_q3_trace_id_idx ON ecos_security.ecos_token_usage_2025_q3 USING btree (trace_id);


--
-- Name: ecos_token_usage_2025_q4_trace_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX ecos_token_usage_2025_q4_trace_id_idx ON ecos_security.ecos_token_usage_2025_q4 USING btree (trace_id);


--
-- Name: ecos_token_usage_2026_q1_trace_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX ecos_token_usage_2026_q1_trace_id_idx ON ecos_security.ecos_token_usage_2026_q1 USING btree (trace_id);


--
-- Name: ecos_token_usage_2026_q2_trace_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX ecos_token_usage_2026_q2_trace_id_idx ON ecos_security.ecos_token_usage_2026_q2 USING btree (trace_id);


--
-- Name: ecos_token_usage_2026_q3_trace_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX ecos_token_usage_2026_q3_trace_id_idx ON ecos_security.ecos_token_usage_2026_q3 USING btree (trace_id);


--
-- Name: ecos_token_usage_2026_q4_trace_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX ecos_token_usage_2026_q4_trace_id_idx ON ecos_security.ecos_token_usage_2026_q4 USING btree (trace_id);


--
-- Name: ecos_token_usage_default_trace_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX ecos_token_usage_default_trace_id_idx ON ecos_security.ecos_token_usage_default USING btree (trace_id);


--
-- Name: idx_security_alert_sev; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX idx_security_alert_sev ON ecos_security.ecos_alert_history USING btree (severity);


--
-- Name: idx_security_alert_time; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX idx_security_alert_time ON ecos_security.ecos_alert_history USING btree (created_at DESC);


--
-- Name: idx_security_alert_type; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX idx_security_alert_type ON ecos_security.ecos_alert_history USING btree (alert_type);


--
-- Name: idx_security_audit_event; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX idx_security_audit_event ON ONLY ecos_security.td_audit_log USING btree (event_type);


--
-- Name: idx_security_audit_tenant; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX idx_security_audit_tenant ON ONLY ecos_security.td_audit_log USING btree (tenant_id);


--
-- Name: idx_security_audit_timestamp; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX idx_security_audit_timestamp ON ONLY ecos_security.td_audit_log USING btree ("timestamp");


--
-- Name: idx_security_audit_user; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX idx_security_audit_user ON ONLY ecos_security.td_audit_log USING btree (user_id);


--
-- Name: idx_security_spans_path; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX idx_security_spans_path ON ecos_security.ecos_spans USING btree (http_path);


--
-- Name: idx_security_spans_time; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX idx_security_spans_time ON ecos_security.ecos_spans USING btree (created_at DESC);


--
-- Name: idx_security_spans_trace; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX idx_security_spans_trace ON ecos_security.ecos_spans USING btree (trace_id);


--
-- Name: td_audit_log_2024_01_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_01_event_type_idx ON ecos_security.td_audit_log_2024_01 USING btree (event_type);


--
-- Name: td_audit_log_2024_01_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_01_tenant_id_idx ON ecos_security.td_audit_log_2024_01 USING btree (tenant_id);


--
-- Name: td_audit_log_2024_01_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_01_timestamp_idx ON ecos_security.td_audit_log_2024_01 USING btree ("timestamp");


--
-- Name: td_audit_log_2024_01_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_01_user_id_idx ON ecos_security.td_audit_log_2024_01 USING btree (user_id);


--
-- Name: td_audit_log_2024_02_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_02_event_type_idx ON ecos_security.td_audit_log_2024_02 USING btree (event_type);


--
-- Name: td_audit_log_2024_02_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_02_tenant_id_idx ON ecos_security.td_audit_log_2024_02 USING btree (tenant_id);


--
-- Name: td_audit_log_2024_02_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_02_timestamp_idx ON ecos_security.td_audit_log_2024_02 USING btree ("timestamp");


--
-- Name: td_audit_log_2024_02_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_02_user_id_idx ON ecos_security.td_audit_log_2024_02 USING btree (user_id);


--
-- Name: td_audit_log_2024_03_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_03_event_type_idx ON ecos_security.td_audit_log_2024_03 USING btree (event_type);


--
-- Name: td_audit_log_2024_03_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_03_tenant_id_idx ON ecos_security.td_audit_log_2024_03 USING btree (tenant_id);


--
-- Name: td_audit_log_2024_03_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_03_timestamp_idx ON ecos_security.td_audit_log_2024_03 USING btree ("timestamp");


--
-- Name: td_audit_log_2024_03_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_03_user_id_idx ON ecos_security.td_audit_log_2024_03 USING btree (user_id);


--
-- Name: td_audit_log_2024_04_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_04_event_type_idx ON ecos_security.td_audit_log_2024_04 USING btree (event_type);


--
-- Name: td_audit_log_2024_04_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_04_tenant_id_idx ON ecos_security.td_audit_log_2024_04 USING btree (tenant_id);


--
-- Name: td_audit_log_2024_04_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_04_timestamp_idx ON ecos_security.td_audit_log_2024_04 USING btree ("timestamp");


--
-- Name: td_audit_log_2024_04_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_04_user_id_idx ON ecos_security.td_audit_log_2024_04 USING btree (user_id);


--
-- Name: td_audit_log_2024_05_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_05_event_type_idx ON ecos_security.td_audit_log_2024_05 USING btree (event_type);


--
-- Name: td_audit_log_2024_05_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_05_tenant_id_idx ON ecos_security.td_audit_log_2024_05 USING btree (tenant_id);


--
-- Name: td_audit_log_2024_05_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_05_timestamp_idx ON ecos_security.td_audit_log_2024_05 USING btree ("timestamp");


--
-- Name: td_audit_log_2024_05_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_05_user_id_idx ON ecos_security.td_audit_log_2024_05 USING btree (user_id);


--
-- Name: td_audit_log_2024_06_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_06_event_type_idx ON ecos_security.td_audit_log_2024_06 USING btree (event_type);


--
-- Name: td_audit_log_2024_06_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_06_tenant_id_idx ON ecos_security.td_audit_log_2024_06 USING btree (tenant_id);


--
-- Name: td_audit_log_2024_06_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_06_timestamp_idx ON ecos_security.td_audit_log_2024_06 USING btree ("timestamp");


--
-- Name: td_audit_log_2024_06_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_06_user_id_idx ON ecos_security.td_audit_log_2024_06 USING btree (user_id);


--
-- Name: td_audit_log_2024_07_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_07_event_type_idx ON ecos_security.td_audit_log_2024_07 USING btree (event_type);


--
-- Name: td_audit_log_2024_07_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_07_tenant_id_idx ON ecos_security.td_audit_log_2024_07 USING btree (tenant_id);


--
-- Name: td_audit_log_2024_07_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_07_timestamp_idx ON ecos_security.td_audit_log_2024_07 USING btree ("timestamp");


--
-- Name: td_audit_log_2024_07_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_07_user_id_idx ON ecos_security.td_audit_log_2024_07 USING btree (user_id);


--
-- Name: td_audit_log_2024_08_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_08_event_type_idx ON ecos_security.td_audit_log_2024_08 USING btree (event_type);


--
-- Name: td_audit_log_2024_08_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_08_tenant_id_idx ON ecos_security.td_audit_log_2024_08 USING btree (tenant_id);


--
-- Name: td_audit_log_2024_08_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_08_timestamp_idx ON ecos_security.td_audit_log_2024_08 USING btree ("timestamp");


--
-- Name: td_audit_log_2024_08_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_08_user_id_idx ON ecos_security.td_audit_log_2024_08 USING btree (user_id);


--
-- Name: td_audit_log_2024_09_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_09_event_type_idx ON ecos_security.td_audit_log_2024_09 USING btree (event_type);


--
-- Name: td_audit_log_2024_09_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_09_tenant_id_idx ON ecos_security.td_audit_log_2024_09 USING btree (tenant_id);


--
-- Name: td_audit_log_2024_09_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_09_timestamp_idx ON ecos_security.td_audit_log_2024_09 USING btree ("timestamp");


--
-- Name: td_audit_log_2024_09_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_09_user_id_idx ON ecos_security.td_audit_log_2024_09 USING btree (user_id);


--
-- Name: td_audit_log_2024_10_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_10_event_type_idx ON ecos_security.td_audit_log_2024_10 USING btree (event_type);


--
-- Name: td_audit_log_2024_10_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_10_tenant_id_idx ON ecos_security.td_audit_log_2024_10 USING btree (tenant_id);


--
-- Name: td_audit_log_2024_10_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_10_timestamp_idx ON ecos_security.td_audit_log_2024_10 USING btree ("timestamp");


--
-- Name: td_audit_log_2024_10_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_10_user_id_idx ON ecos_security.td_audit_log_2024_10 USING btree (user_id);


--
-- Name: td_audit_log_2024_11_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_11_event_type_idx ON ecos_security.td_audit_log_2024_11 USING btree (event_type);


--
-- Name: td_audit_log_2024_11_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_11_tenant_id_idx ON ecos_security.td_audit_log_2024_11 USING btree (tenant_id);


--
-- Name: td_audit_log_2024_11_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_11_timestamp_idx ON ecos_security.td_audit_log_2024_11 USING btree ("timestamp");


--
-- Name: td_audit_log_2024_11_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_11_user_id_idx ON ecos_security.td_audit_log_2024_11 USING btree (user_id);


--
-- Name: td_audit_log_2024_12_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_12_event_type_idx ON ecos_security.td_audit_log_2024_12 USING btree (event_type);


--
-- Name: td_audit_log_2024_12_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_12_tenant_id_idx ON ecos_security.td_audit_log_2024_12 USING btree (tenant_id);


--
-- Name: td_audit_log_2024_12_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_12_timestamp_idx ON ecos_security.td_audit_log_2024_12 USING btree ("timestamp");


--
-- Name: td_audit_log_2024_12_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2024_12_user_id_idx ON ecos_security.td_audit_log_2024_12 USING btree (user_id);


--
-- Name: td_audit_log_2025_01_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_01_event_type_idx ON ecos_security.td_audit_log_2025_01 USING btree (event_type);


--
-- Name: td_audit_log_2025_01_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_01_tenant_id_idx ON ecos_security.td_audit_log_2025_01 USING btree (tenant_id);


--
-- Name: td_audit_log_2025_01_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_01_timestamp_idx ON ecos_security.td_audit_log_2025_01 USING btree ("timestamp");


--
-- Name: td_audit_log_2025_01_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_01_user_id_idx ON ecos_security.td_audit_log_2025_01 USING btree (user_id);


--
-- Name: td_audit_log_2025_02_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_02_event_type_idx ON ecos_security.td_audit_log_2025_02 USING btree (event_type);


--
-- Name: td_audit_log_2025_02_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_02_tenant_id_idx ON ecos_security.td_audit_log_2025_02 USING btree (tenant_id);


--
-- Name: td_audit_log_2025_02_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_02_timestamp_idx ON ecos_security.td_audit_log_2025_02 USING btree ("timestamp");


--
-- Name: td_audit_log_2025_02_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_02_user_id_idx ON ecos_security.td_audit_log_2025_02 USING btree (user_id);


--
-- Name: td_audit_log_2025_03_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_03_event_type_idx ON ecos_security.td_audit_log_2025_03 USING btree (event_type);


--
-- Name: td_audit_log_2025_03_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_03_tenant_id_idx ON ecos_security.td_audit_log_2025_03 USING btree (tenant_id);


--
-- Name: td_audit_log_2025_03_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_03_timestamp_idx ON ecos_security.td_audit_log_2025_03 USING btree ("timestamp");


--
-- Name: td_audit_log_2025_03_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_03_user_id_idx ON ecos_security.td_audit_log_2025_03 USING btree (user_id);


--
-- Name: td_audit_log_2025_04_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_04_event_type_idx ON ecos_security.td_audit_log_2025_04 USING btree (event_type);


--
-- Name: td_audit_log_2025_04_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_04_tenant_id_idx ON ecos_security.td_audit_log_2025_04 USING btree (tenant_id);


--
-- Name: td_audit_log_2025_04_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_04_timestamp_idx ON ecos_security.td_audit_log_2025_04 USING btree ("timestamp");


--
-- Name: td_audit_log_2025_04_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_04_user_id_idx ON ecos_security.td_audit_log_2025_04 USING btree (user_id);


--
-- Name: td_audit_log_2025_05_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_05_event_type_idx ON ecos_security.td_audit_log_2025_05 USING btree (event_type);


--
-- Name: td_audit_log_2025_05_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_05_tenant_id_idx ON ecos_security.td_audit_log_2025_05 USING btree (tenant_id);


--
-- Name: td_audit_log_2025_05_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_05_timestamp_idx ON ecos_security.td_audit_log_2025_05 USING btree ("timestamp");


--
-- Name: td_audit_log_2025_05_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_05_user_id_idx ON ecos_security.td_audit_log_2025_05 USING btree (user_id);


--
-- Name: td_audit_log_2025_06_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_06_event_type_idx ON ecos_security.td_audit_log_2025_06 USING btree (event_type);


--
-- Name: td_audit_log_2025_06_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_06_tenant_id_idx ON ecos_security.td_audit_log_2025_06 USING btree (tenant_id);


--
-- Name: td_audit_log_2025_06_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_06_timestamp_idx ON ecos_security.td_audit_log_2025_06 USING btree ("timestamp");


--
-- Name: td_audit_log_2025_06_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_06_user_id_idx ON ecos_security.td_audit_log_2025_06 USING btree (user_id);


--
-- Name: td_audit_log_2025_07_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_07_event_type_idx ON ecos_security.td_audit_log_2025_07 USING btree (event_type);


--
-- Name: td_audit_log_2025_07_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_07_tenant_id_idx ON ecos_security.td_audit_log_2025_07 USING btree (tenant_id);


--
-- Name: td_audit_log_2025_07_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_07_timestamp_idx ON ecos_security.td_audit_log_2025_07 USING btree ("timestamp");


--
-- Name: td_audit_log_2025_07_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_07_user_id_idx ON ecos_security.td_audit_log_2025_07 USING btree (user_id);


--
-- Name: td_audit_log_2025_08_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_08_event_type_idx ON ecos_security.td_audit_log_2025_08 USING btree (event_type);


--
-- Name: td_audit_log_2025_08_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_08_tenant_id_idx ON ecos_security.td_audit_log_2025_08 USING btree (tenant_id);


--
-- Name: td_audit_log_2025_08_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_08_timestamp_idx ON ecos_security.td_audit_log_2025_08 USING btree ("timestamp");


--
-- Name: td_audit_log_2025_08_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_08_user_id_idx ON ecos_security.td_audit_log_2025_08 USING btree (user_id);


--
-- Name: td_audit_log_2025_09_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_09_event_type_idx ON ecos_security.td_audit_log_2025_09 USING btree (event_type);


--
-- Name: td_audit_log_2025_09_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_09_tenant_id_idx ON ecos_security.td_audit_log_2025_09 USING btree (tenant_id);


--
-- Name: td_audit_log_2025_09_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_09_timestamp_idx ON ecos_security.td_audit_log_2025_09 USING btree ("timestamp");


--
-- Name: td_audit_log_2025_09_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_09_user_id_idx ON ecos_security.td_audit_log_2025_09 USING btree (user_id);


--
-- Name: td_audit_log_2025_10_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_10_event_type_idx ON ecos_security.td_audit_log_2025_10 USING btree (event_type);


--
-- Name: td_audit_log_2025_10_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_10_tenant_id_idx ON ecos_security.td_audit_log_2025_10 USING btree (tenant_id);


--
-- Name: td_audit_log_2025_10_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_10_timestamp_idx ON ecos_security.td_audit_log_2025_10 USING btree ("timestamp");


--
-- Name: td_audit_log_2025_10_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_10_user_id_idx ON ecos_security.td_audit_log_2025_10 USING btree (user_id);


--
-- Name: td_audit_log_2025_11_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_11_event_type_idx ON ecos_security.td_audit_log_2025_11 USING btree (event_type);


--
-- Name: td_audit_log_2025_11_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_11_tenant_id_idx ON ecos_security.td_audit_log_2025_11 USING btree (tenant_id);


--
-- Name: td_audit_log_2025_11_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_11_timestamp_idx ON ecos_security.td_audit_log_2025_11 USING btree ("timestamp");


--
-- Name: td_audit_log_2025_11_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_11_user_id_idx ON ecos_security.td_audit_log_2025_11 USING btree (user_id);


--
-- Name: td_audit_log_2025_12_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_12_event_type_idx ON ecos_security.td_audit_log_2025_12 USING btree (event_type);


--
-- Name: td_audit_log_2025_12_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_12_tenant_id_idx ON ecos_security.td_audit_log_2025_12 USING btree (tenant_id);


--
-- Name: td_audit_log_2025_12_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_12_timestamp_idx ON ecos_security.td_audit_log_2025_12 USING btree ("timestamp");


--
-- Name: td_audit_log_2025_12_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2025_12_user_id_idx ON ecos_security.td_audit_log_2025_12 USING btree (user_id);


--
-- Name: td_audit_log_2026_01_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_01_event_type_idx ON ecos_security.td_audit_log_2026_01 USING btree (event_type);


--
-- Name: td_audit_log_2026_01_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_01_tenant_id_idx ON ecos_security.td_audit_log_2026_01 USING btree (tenant_id);


--
-- Name: td_audit_log_2026_01_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_01_timestamp_idx ON ecos_security.td_audit_log_2026_01 USING btree ("timestamp");


--
-- Name: td_audit_log_2026_01_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_01_user_id_idx ON ecos_security.td_audit_log_2026_01 USING btree (user_id);


--
-- Name: td_audit_log_2026_02_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_02_event_type_idx ON ecos_security.td_audit_log_2026_02 USING btree (event_type);


--
-- Name: td_audit_log_2026_02_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_02_tenant_id_idx ON ecos_security.td_audit_log_2026_02 USING btree (tenant_id);


--
-- Name: td_audit_log_2026_02_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_02_timestamp_idx ON ecos_security.td_audit_log_2026_02 USING btree ("timestamp");


--
-- Name: td_audit_log_2026_02_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_02_user_id_idx ON ecos_security.td_audit_log_2026_02 USING btree (user_id);


--
-- Name: td_audit_log_2026_03_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_03_event_type_idx ON ecos_security.td_audit_log_2026_03 USING btree (event_type);


--
-- Name: td_audit_log_2026_03_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_03_tenant_id_idx ON ecos_security.td_audit_log_2026_03 USING btree (tenant_id);


--
-- Name: td_audit_log_2026_03_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_03_timestamp_idx ON ecos_security.td_audit_log_2026_03 USING btree ("timestamp");


--
-- Name: td_audit_log_2026_03_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_03_user_id_idx ON ecos_security.td_audit_log_2026_03 USING btree (user_id);


--
-- Name: td_audit_log_2026_04_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_04_event_type_idx ON ecos_security.td_audit_log_2026_04 USING btree (event_type);


--
-- Name: td_audit_log_2026_04_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_04_tenant_id_idx ON ecos_security.td_audit_log_2026_04 USING btree (tenant_id);


--
-- Name: td_audit_log_2026_04_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_04_timestamp_idx ON ecos_security.td_audit_log_2026_04 USING btree ("timestamp");


--
-- Name: td_audit_log_2026_04_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_04_user_id_idx ON ecos_security.td_audit_log_2026_04 USING btree (user_id);


--
-- Name: td_audit_log_2026_05_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_05_event_type_idx ON ecos_security.td_audit_log_2026_05 USING btree (event_type);


--
-- Name: td_audit_log_2026_05_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_05_tenant_id_idx ON ecos_security.td_audit_log_2026_05 USING btree (tenant_id);


--
-- Name: td_audit_log_2026_05_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_05_timestamp_idx ON ecos_security.td_audit_log_2026_05 USING btree ("timestamp");


--
-- Name: td_audit_log_2026_05_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_05_user_id_idx ON ecos_security.td_audit_log_2026_05 USING btree (user_id);


--
-- Name: td_audit_log_2026_06_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_06_event_type_idx ON ecos_security.td_audit_log_2026_06 USING btree (event_type);


--
-- Name: td_audit_log_2026_06_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_06_tenant_id_idx ON ecos_security.td_audit_log_2026_06 USING btree (tenant_id);


--
-- Name: td_audit_log_2026_06_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_06_timestamp_idx ON ecos_security.td_audit_log_2026_06 USING btree ("timestamp");


--
-- Name: td_audit_log_2026_06_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_06_user_id_idx ON ecos_security.td_audit_log_2026_06 USING btree (user_id);


--
-- Name: td_audit_log_2026_07_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_07_event_type_idx ON ecos_security.td_audit_log_2026_07 USING btree (event_type);


--
-- Name: td_audit_log_2026_07_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_07_tenant_id_idx ON ecos_security.td_audit_log_2026_07 USING btree (tenant_id);


--
-- Name: td_audit_log_2026_07_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_07_timestamp_idx ON ecos_security.td_audit_log_2026_07 USING btree ("timestamp");


--
-- Name: td_audit_log_2026_07_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_07_user_id_idx ON ecos_security.td_audit_log_2026_07 USING btree (user_id);


--
-- Name: td_audit_log_2026_08_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_08_event_type_idx ON ecos_security.td_audit_log_2026_08 USING btree (event_type);


--
-- Name: td_audit_log_2026_08_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_08_tenant_id_idx ON ecos_security.td_audit_log_2026_08 USING btree (tenant_id);


--
-- Name: td_audit_log_2026_08_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_08_timestamp_idx ON ecos_security.td_audit_log_2026_08 USING btree ("timestamp");


--
-- Name: td_audit_log_2026_08_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_08_user_id_idx ON ecos_security.td_audit_log_2026_08 USING btree (user_id);


--
-- Name: td_audit_log_2026_09_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_09_event_type_idx ON ecos_security.td_audit_log_2026_09 USING btree (event_type);


--
-- Name: td_audit_log_2026_09_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_09_tenant_id_idx ON ecos_security.td_audit_log_2026_09 USING btree (tenant_id);


--
-- Name: td_audit_log_2026_09_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_09_timestamp_idx ON ecos_security.td_audit_log_2026_09 USING btree ("timestamp");


--
-- Name: td_audit_log_2026_09_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_09_user_id_idx ON ecos_security.td_audit_log_2026_09 USING btree (user_id);


--
-- Name: td_audit_log_2026_10_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_10_event_type_idx ON ecos_security.td_audit_log_2026_10 USING btree (event_type);


--
-- Name: td_audit_log_2026_10_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_10_tenant_id_idx ON ecos_security.td_audit_log_2026_10 USING btree (tenant_id);


--
-- Name: td_audit_log_2026_10_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_10_timestamp_idx ON ecos_security.td_audit_log_2026_10 USING btree ("timestamp");


--
-- Name: td_audit_log_2026_10_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_10_user_id_idx ON ecos_security.td_audit_log_2026_10 USING btree (user_id);


--
-- Name: td_audit_log_2026_11_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_11_event_type_idx ON ecos_security.td_audit_log_2026_11 USING btree (event_type);


--
-- Name: td_audit_log_2026_11_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_11_tenant_id_idx ON ecos_security.td_audit_log_2026_11 USING btree (tenant_id);


--
-- Name: td_audit_log_2026_11_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_11_timestamp_idx ON ecos_security.td_audit_log_2026_11 USING btree ("timestamp");


--
-- Name: td_audit_log_2026_11_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_11_user_id_idx ON ecos_security.td_audit_log_2026_11 USING btree (user_id);


--
-- Name: td_audit_log_2026_12_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_12_event_type_idx ON ecos_security.td_audit_log_2026_12 USING btree (event_type);


--
-- Name: td_audit_log_2026_12_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_12_tenant_id_idx ON ecos_security.td_audit_log_2026_12 USING btree (tenant_id);


--
-- Name: td_audit_log_2026_12_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_12_timestamp_idx ON ecos_security.td_audit_log_2026_12 USING btree ("timestamp");


--
-- Name: td_audit_log_2026_12_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_2026_12_user_id_idx ON ecos_security.td_audit_log_2026_12 USING btree (user_id);


--
-- Name: td_audit_log_default_event_type_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_default_event_type_idx ON ecos_security.td_audit_log_default USING btree (event_type);


--
-- Name: td_audit_log_default_tenant_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_default_tenant_id_idx ON ecos_security.td_audit_log_default USING btree (tenant_id);


--
-- Name: td_audit_log_default_timestamp_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_default_timestamp_idx ON ecos_security.td_audit_log_default USING btree ("timestamp");


--
-- Name: td_audit_log_default_user_id_idx; Type: INDEX; Schema: ecos_security; Owner: postgres
--

CREATE INDEX td_audit_log_default_user_id_idx ON ecos_security.td_audit_log_default USING btree (user_id);


--
-- Name: idx_rsp_clearance; Type: INDEX; Schema: ecos_sysman; Owner: postgres
--

CREATE INDEX idx_rsp_clearance ON ecos_sysman.td_role_security_profile USING btree (clearance_level);


--
-- Name: idx_sysman_config_type_env; Type: INDEX; Schema: ecos_sysman; Owner: postgres
--

CREATE INDEX idx_sysman_config_type_env ON ecos_sysman.td_config USING btree (config_type, environment);


--
-- Name: idx_sysman_dict_type_code; Type: INDEX; Schema: ecos_sysman; Owner: postgres
--

CREATE UNIQUE INDEX idx_sysman_dict_type_code ON ecos_sysman.sys_dict USING btree (dict_type, dict_code);


--
-- Name: idx_sysman_org_code; Type: INDEX; Schema: ecos_sysman; Owner: postgres
--

CREATE UNIQUE INDEX idx_sysman_org_code ON ecos_sysman.td_organization USING btree ("ORG_CODE");


--
-- Name: idx_sysman_org_parent; Type: INDEX; Schema: ecos_sysman; Owner: postgres
--

CREATE INDEX idx_sysman_org_parent ON ecos_sysman.td_organization USING btree ("PARENT_ORG_ID");


--
-- Name: idx_sysman_orgperm_org; Type: INDEX; Schema: ecos_sysman; Owner: postgres
--

CREATE INDEX idx_sysman_orgperm_org ON ecos_sysman.td_org_permission USING btree (org_id);


--
-- Name: idx_sysman_perm_res_action; Type: INDEX; Schema: ecos_sysman; Owner: postgres
--

CREATE UNIQUE INDEX idx_sysman_perm_res_action ON ecos_sysman.td_permission USING btree (resource_id, action);


--
-- Name: idx_sysman_quota_tenant_type; Type: INDEX; Schema: ecos_sysman; Owner: postgres
--

CREATE UNIQUE INDEX idx_sysman_quota_tenant_type ON ecos_sysman.ecos_tenant_quota USING btree (tenant_id, quota_type);


--
-- Name: idx_sysman_role_code; Type: INDEX; Schema: ecos_sysman; Owner: postgres
--

CREATE UNIQUE INDEX idx_sysman_role_code ON ecos_sysman.td_role USING btree (role_code);


--
-- Name: idx_sysman_role_parent; Type: INDEX; Schema: ecos_sysman; Owner: postgres
--

CREATE INDEX idx_sysman_role_parent ON ecos_sysman.td_role USING btree (parent_role_id);


--
-- Name: idx_sysman_sysconfig_key; Type: INDEX; Schema: ecos_sysman; Owner: postgres
--

CREATE UNIQUE INDEX idx_sysman_sysconfig_key ON ecos_sysman.sys_config USING btree (config_key);


--
-- Name: idx_sysman_tenant_code; Type: INDEX; Schema: ecos_sysman; Owner: postgres
--

CREATE UNIQUE INDEX idx_sysman_tenant_code ON ecos_sysman.ecos_tenant USING btree (tenant_code);


--
-- Name: idx_sysman_user_email; Type: INDEX; Schema: ecos_sysman; Owner: postgres
--

CREATE UNIQUE INDEX idx_sysman_user_email ON ecos_sysman.td_user USING btree (email);


--
-- Name: idx_sysman_user_status; Type: INDEX; Schema: ecos_sysman; Owner: postgres
--

CREATE INDEX idx_sysman_user_status ON ecos_sysman.td_user USING btree (status);


--
-- Name: idx_sysman_user_tenant; Type: INDEX; Schema: ecos_sysman; Owner: postgres
--

CREATE INDEX idx_sysman_user_tenant ON ecos_sysman.td_user USING btree (tenant_id);


--
-- Name: idx_sysman_user_username; Type: INDEX; Schema: ecos_sysman; Owner: postgres
--

CREATE UNIQUE INDEX idx_sysman_user_username ON ecos_sysman.td_user USING btree (username);


--
-- Name: idx_sysman_users_username; Type: INDEX; Schema: ecos_sysman; Owner: postgres
--

CREATE UNIQUE INDEX idx_sysman_users_username ON ecos_sysman.users USING btree (username);


--
-- Name: idx_usp_clearance; Type: INDEX; Schema: ecos_sysman; Owner: postgres
--

CREATE INDEX idx_usp_clearance ON ecos_sysman.td_user_security_profile USING btree (clearance_level);


--
-- Name: agent_cost_2025_01_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_01_pkey;


--
-- Name: agent_cost_2025_02_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_02_pkey;


--
-- Name: agent_cost_2025_03_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_03_pkey;


--
-- Name: agent_cost_2025_04_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_04_pkey;


--
-- Name: agent_cost_2025_05_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_05_pkey;


--
-- Name: agent_cost_2025_06_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_06_pkey;


--
-- Name: agent_cost_2025_07_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_07_pkey;


--
-- Name: agent_cost_2025_08_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_08_pkey;


--
-- Name: agent_cost_2025_09_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_09_pkey;


--
-- Name: agent_cost_2025_10_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_10_pkey;


--
-- Name: agent_cost_2025_11_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_11_pkey;


--
-- Name: agent_cost_2025_12_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2025_12_pkey;


--
-- Name: agent_cost_2026_01_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_01_pkey;


--
-- Name: agent_cost_2026_02_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_02_pkey;


--
-- Name: agent_cost_2026_03_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_03_pkey;


--
-- Name: agent_cost_2026_04_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_04_pkey;


--
-- Name: agent_cost_2026_05_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_05_pkey;


--
-- Name: agent_cost_2026_06_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_06_pkey;


--
-- Name: agent_cost_2026_07_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_07_pkey;


--
-- Name: agent_cost_2026_08_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_08_pkey;


--
-- Name: agent_cost_2026_09_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_09_pkey;


--
-- Name: agent_cost_2026_10_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_10_pkey;


--
-- Name: agent_cost_2026_11_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_11_pkey;


--
-- Name: agent_cost_2026_12_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_2026_12_pkey;


--
-- Name: agent_cost_default_pkey; Type: INDEX ATTACH; Schema: ecos_ai; Owner: postgres
--

ALTER INDEX ecos_ai.agent_cost_pkey ATTACH PARTITION ecos_ai.agent_cost_default_pkey;


--
-- Name: outbox_event_2025_01_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_01_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_01_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_01_event_type_idx;


--
-- Name: outbox_event_2025_01_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_01_pkey;


--
-- Name: outbox_event_2025_01_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_01_published_idx;


--
-- Name: outbox_event_2025_02_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_02_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_02_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_02_event_type_idx;


--
-- Name: outbox_event_2025_02_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_02_pkey;


--
-- Name: outbox_event_2025_02_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_02_published_idx;


--
-- Name: outbox_event_2025_03_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_03_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_03_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_03_event_type_idx;


--
-- Name: outbox_event_2025_03_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_03_pkey;


--
-- Name: outbox_event_2025_03_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_03_published_idx;


--
-- Name: outbox_event_2025_04_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_04_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_04_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_04_event_type_idx;


--
-- Name: outbox_event_2025_04_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_04_pkey;


--
-- Name: outbox_event_2025_04_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_04_published_idx;


--
-- Name: outbox_event_2025_05_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_05_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_05_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_05_event_type_idx;


--
-- Name: outbox_event_2025_05_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_05_pkey;


--
-- Name: outbox_event_2025_05_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_05_published_idx;


--
-- Name: outbox_event_2025_06_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_06_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_06_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_06_event_type_idx;


--
-- Name: outbox_event_2025_06_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_06_pkey;


--
-- Name: outbox_event_2025_06_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_06_published_idx;


--
-- Name: outbox_event_2025_07_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_07_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_07_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_07_event_type_idx;


--
-- Name: outbox_event_2025_07_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_07_pkey;


--
-- Name: outbox_event_2025_07_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_07_published_idx;


--
-- Name: outbox_event_2025_08_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_08_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_08_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_08_event_type_idx;


--
-- Name: outbox_event_2025_08_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_08_pkey;


--
-- Name: outbox_event_2025_08_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_08_published_idx;


--
-- Name: outbox_event_2025_09_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_09_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_09_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_09_event_type_idx;


--
-- Name: outbox_event_2025_09_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_09_pkey;


--
-- Name: outbox_event_2025_09_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_09_published_idx;


--
-- Name: outbox_event_2025_10_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_10_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_10_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_10_event_type_idx;


--
-- Name: outbox_event_2025_10_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_10_pkey;


--
-- Name: outbox_event_2025_10_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_10_published_idx;


--
-- Name: outbox_event_2025_11_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_11_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_11_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_11_event_type_idx;


--
-- Name: outbox_event_2025_11_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_11_pkey;


--
-- Name: outbox_event_2025_11_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_11_published_idx;


--
-- Name: outbox_event_2025_12_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2025_12_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2025_12_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2025_12_event_type_idx;


--
-- Name: outbox_event_2025_12_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2025_12_pkey;


--
-- Name: outbox_event_2025_12_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2025_12_published_idx;


--
-- Name: outbox_event_2026_01_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_01_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_01_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_01_event_type_idx;


--
-- Name: outbox_event_2026_01_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_01_pkey;


--
-- Name: outbox_event_2026_01_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_01_published_idx;


--
-- Name: outbox_event_2026_02_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_02_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_02_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_02_event_type_idx;


--
-- Name: outbox_event_2026_02_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_02_pkey;


--
-- Name: outbox_event_2026_02_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_02_published_idx;


--
-- Name: outbox_event_2026_03_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_03_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_03_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_03_event_type_idx;


--
-- Name: outbox_event_2026_03_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_03_pkey;


--
-- Name: outbox_event_2026_03_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_03_published_idx;


--
-- Name: outbox_event_2026_04_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_04_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_04_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_04_event_type_idx;


--
-- Name: outbox_event_2026_04_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_04_pkey;


--
-- Name: outbox_event_2026_04_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_04_published_idx;


--
-- Name: outbox_event_2026_05_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_05_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_05_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_05_event_type_idx;


--
-- Name: outbox_event_2026_05_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_05_pkey;


--
-- Name: outbox_event_2026_05_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_05_published_idx;


--
-- Name: outbox_event_2026_06_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_06_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_06_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_06_event_type_idx;


--
-- Name: outbox_event_2026_06_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_06_pkey;


--
-- Name: outbox_event_2026_06_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_06_published_idx;


--
-- Name: outbox_event_2026_07_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_07_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_07_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_07_event_type_idx;


--
-- Name: outbox_event_2026_07_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_07_pkey;


--
-- Name: outbox_event_2026_07_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_07_published_idx;


--
-- Name: outbox_event_2026_08_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_08_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_08_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_08_event_type_idx;


--
-- Name: outbox_event_2026_08_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_08_pkey;


--
-- Name: outbox_event_2026_08_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_08_published_idx;


--
-- Name: outbox_event_2026_09_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_09_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_09_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_09_event_type_idx;


--
-- Name: outbox_event_2026_09_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_09_pkey;


--
-- Name: outbox_event_2026_09_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_09_published_idx;


--
-- Name: outbox_event_2026_10_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_10_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_10_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_10_event_type_idx;


--
-- Name: outbox_event_2026_10_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_10_pkey;


--
-- Name: outbox_event_2026_10_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_10_published_idx;


--
-- Name: outbox_event_2026_11_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_11_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_11_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_11_event_type_idx;


--
-- Name: outbox_event_2026_11_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_11_pkey;


--
-- Name: outbox_event_2026_11_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_11_published_idx;


--
-- Name: outbox_event_2026_12_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2026_12_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2026_12_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2026_12_event_type_idx;


--
-- Name: outbox_event_2026_12_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2026_12_pkey;


--
-- Name: outbox_event_2026_12_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2026_12_published_idx;


--
-- Name: outbox_event_2027_01_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_01_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_01_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_01_event_type_idx;


--
-- Name: outbox_event_2027_01_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_01_pkey;


--
-- Name: outbox_event_2027_01_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_01_published_idx;


--
-- Name: outbox_event_2027_02_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_02_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_02_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_02_event_type_idx;


--
-- Name: outbox_event_2027_02_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_02_pkey;


--
-- Name: outbox_event_2027_02_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_02_published_idx;


--
-- Name: outbox_event_2027_03_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_03_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_03_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_03_event_type_idx;


--
-- Name: outbox_event_2027_03_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_03_pkey;


--
-- Name: outbox_event_2027_03_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_03_published_idx;


--
-- Name: outbox_event_2027_04_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_04_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_04_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_04_event_type_idx;


--
-- Name: outbox_event_2027_04_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_04_pkey;


--
-- Name: outbox_event_2027_04_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_04_published_idx;


--
-- Name: outbox_event_2027_05_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_05_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_05_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_05_event_type_idx;


--
-- Name: outbox_event_2027_05_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_05_pkey;


--
-- Name: outbox_event_2027_05_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_05_published_idx;


--
-- Name: outbox_event_2027_06_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_06_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_06_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_06_event_type_idx;


--
-- Name: outbox_event_2027_06_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_06_pkey;


--
-- Name: outbox_event_2027_06_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_06_published_idx;


--
-- Name: outbox_event_2027_07_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_07_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_07_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_07_event_type_idx;


--
-- Name: outbox_event_2027_07_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_07_pkey;


--
-- Name: outbox_event_2027_07_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_07_published_idx;


--
-- Name: outbox_event_2027_08_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_08_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_08_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_08_event_type_idx;


--
-- Name: outbox_event_2027_08_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_08_pkey;


--
-- Name: outbox_event_2027_08_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_08_published_idx;


--
-- Name: outbox_event_2027_09_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_09_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_09_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_09_event_type_idx;


--
-- Name: outbox_event_2027_09_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_09_pkey;


--
-- Name: outbox_event_2027_09_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_09_published_idx;


--
-- Name: outbox_event_2027_10_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_10_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_10_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_10_event_type_idx;


--
-- Name: outbox_event_2027_10_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_10_pkey;


--
-- Name: outbox_event_2027_10_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_10_published_idx;


--
-- Name: outbox_event_2027_11_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_11_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_11_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_11_event_type_idx;


--
-- Name: outbox_event_2027_11_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_11_pkey;


--
-- Name: outbox_event_2027_11_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_11_published_idx;


--
-- Name: outbox_event_2027_12_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_2027_12_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_2027_12_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_2027_12_event_type_idx;


--
-- Name: outbox_event_2027_12_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_2027_12_pkey;


--
-- Name: outbox_event_2027_12_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_2027_12_published_idx;


--
-- Name: outbox_event_default_aggregate_type_aggregate_id_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_default_aggregate_type_aggregate_id_idx;


--
-- Name: outbox_event_default_event_type_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_default_event_type_idx;


--
-- Name: outbox_event_default_pkey; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_default_pkey;


--
-- Name: outbox_event_default_published_idx; Type: INDEX ATTACH; Schema: ecos_infra; Owner: postgres
--

ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_default_published_idx;


--
-- Name: ecos_token_usage_2025_q1_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.ecos_token_usage_pkey ATTACH PARTITION ecos_security.ecos_token_usage_2025_q1_pkey;


--
-- Name: ecos_token_usage_2025_q1_trace_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_token_trace ATTACH PARTITION ecos_security.ecos_token_usage_2025_q1_trace_id_idx;


--
-- Name: ecos_token_usage_2025_q2_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.ecos_token_usage_pkey ATTACH PARTITION ecos_security.ecos_token_usage_2025_q2_pkey;


--
-- Name: ecos_token_usage_2025_q2_trace_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_token_trace ATTACH PARTITION ecos_security.ecos_token_usage_2025_q2_trace_id_idx;


--
-- Name: ecos_token_usage_2025_q3_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.ecos_token_usage_pkey ATTACH PARTITION ecos_security.ecos_token_usage_2025_q3_pkey;


--
-- Name: ecos_token_usage_2025_q3_trace_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_token_trace ATTACH PARTITION ecos_security.ecos_token_usage_2025_q3_trace_id_idx;


--
-- Name: ecos_token_usage_2025_q4_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.ecos_token_usage_pkey ATTACH PARTITION ecos_security.ecos_token_usage_2025_q4_pkey;


--
-- Name: ecos_token_usage_2025_q4_trace_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_token_trace ATTACH PARTITION ecos_security.ecos_token_usage_2025_q4_trace_id_idx;


--
-- Name: ecos_token_usage_2026_q1_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.ecos_token_usage_pkey ATTACH PARTITION ecos_security.ecos_token_usage_2026_q1_pkey;


--
-- Name: ecos_token_usage_2026_q1_trace_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_token_trace ATTACH PARTITION ecos_security.ecos_token_usage_2026_q1_trace_id_idx;


--
-- Name: ecos_token_usage_2026_q2_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.ecos_token_usage_pkey ATTACH PARTITION ecos_security.ecos_token_usage_2026_q2_pkey;


--
-- Name: ecos_token_usage_2026_q2_trace_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_token_trace ATTACH PARTITION ecos_security.ecos_token_usage_2026_q2_trace_id_idx;


--
-- Name: ecos_token_usage_2026_q3_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.ecos_token_usage_pkey ATTACH PARTITION ecos_security.ecos_token_usage_2026_q3_pkey;


--
-- Name: ecos_token_usage_2026_q3_trace_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_token_trace ATTACH PARTITION ecos_security.ecos_token_usage_2026_q3_trace_id_idx;


--
-- Name: ecos_token_usage_2026_q4_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.ecos_token_usage_pkey ATTACH PARTITION ecos_security.ecos_token_usage_2026_q4_pkey;


--
-- Name: ecos_token_usage_2026_q4_trace_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_token_trace ATTACH PARTITION ecos_security.ecos_token_usage_2026_q4_trace_id_idx;


--
-- Name: ecos_token_usage_default_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.ecos_token_usage_pkey ATTACH PARTITION ecos_security.ecos_token_usage_default_pkey;


--
-- Name: ecos_token_usage_default_trace_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_token_trace ATTACH PARTITION ecos_security.ecos_token_usage_default_trace_id_idx;


--
-- Name: td_audit_log_2024_01_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2024_01_event_type_idx;


--
-- Name: td_audit_log_2024_01_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2024_01_pkey;


--
-- Name: td_audit_log_2024_01_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2024_01_tenant_id_idx;


--
-- Name: td_audit_log_2024_01_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2024_01_timestamp_idx;


--
-- Name: td_audit_log_2024_01_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2024_01_user_id_idx;


--
-- Name: td_audit_log_2024_02_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2024_02_event_type_idx;


--
-- Name: td_audit_log_2024_02_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2024_02_pkey;


--
-- Name: td_audit_log_2024_02_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2024_02_tenant_id_idx;


--
-- Name: td_audit_log_2024_02_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2024_02_timestamp_idx;


--
-- Name: td_audit_log_2024_02_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2024_02_user_id_idx;


--
-- Name: td_audit_log_2024_03_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2024_03_event_type_idx;


--
-- Name: td_audit_log_2024_03_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2024_03_pkey;


--
-- Name: td_audit_log_2024_03_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2024_03_tenant_id_idx;


--
-- Name: td_audit_log_2024_03_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2024_03_timestamp_idx;


--
-- Name: td_audit_log_2024_03_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2024_03_user_id_idx;


--
-- Name: td_audit_log_2024_04_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2024_04_event_type_idx;


--
-- Name: td_audit_log_2024_04_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2024_04_pkey;


--
-- Name: td_audit_log_2024_04_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2024_04_tenant_id_idx;


--
-- Name: td_audit_log_2024_04_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2024_04_timestamp_idx;


--
-- Name: td_audit_log_2024_04_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2024_04_user_id_idx;


--
-- Name: td_audit_log_2024_05_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2024_05_event_type_idx;


--
-- Name: td_audit_log_2024_05_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2024_05_pkey;


--
-- Name: td_audit_log_2024_05_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2024_05_tenant_id_idx;


--
-- Name: td_audit_log_2024_05_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2024_05_timestamp_idx;


--
-- Name: td_audit_log_2024_05_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2024_05_user_id_idx;


--
-- Name: td_audit_log_2024_06_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2024_06_event_type_idx;


--
-- Name: td_audit_log_2024_06_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2024_06_pkey;


--
-- Name: td_audit_log_2024_06_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2024_06_tenant_id_idx;


--
-- Name: td_audit_log_2024_06_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2024_06_timestamp_idx;


--
-- Name: td_audit_log_2024_06_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2024_06_user_id_idx;


--
-- Name: td_audit_log_2024_07_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2024_07_event_type_idx;


--
-- Name: td_audit_log_2024_07_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2024_07_pkey;


--
-- Name: td_audit_log_2024_07_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2024_07_tenant_id_idx;


--
-- Name: td_audit_log_2024_07_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2024_07_timestamp_idx;


--
-- Name: td_audit_log_2024_07_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2024_07_user_id_idx;


--
-- Name: td_audit_log_2024_08_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2024_08_event_type_idx;


--
-- Name: td_audit_log_2024_08_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2024_08_pkey;


--
-- Name: td_audit_log_2024_08_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2024_08_tenant_id_idx;


--
-- Name: td_audit_log_2024_08_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2024_08_timestamp_idx;


--
-- Name: td_audit_log_2024_08_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2024_08_user_id_idx;


--
-- Name: td_audit_log_2024_09_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2024_09_event_type_idx;


--
-- Name: td_audit_log_2024_09_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2024_09_pkey;


--
-- Name: td_audit_log_2024_09_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2024_09_tenant_id_idx;


--
-- Name: td_audit_log_2024_09_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2024_09_timestamp_idx;


--
-- Name: td_audit_log_2024_09_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2024_09_user_id_idx;


--
-- Name: td_audit_log_2024_10_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2024_10_event_type_idx;


--
-- Name: td_audit_log_2024_10_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2024_10_pkey;


--
-- Name: td_audit_log_2024_10_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2024_10_tenant_id_idx;


--
-- Name: td_audit_log_2024_10_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2024_10_timestamp_idx;


--
-- Name: td_audit_log_2024_10_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2024_10_user_id_idx;


--
-- Name: td_audit_log_2024_11_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2024_11_event_type_idx;


--
-- Name: td_audit_log_2024_11_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2024_11_pkey;


--
-- Name: td_audit_log_2024_11_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2024_11_tenant_id_idx;


--
-- Name: td_audit_log_2024_11_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2024_11_timestamp_idx;


--
-- Name: td_audit_log_2024_11_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2024_11_user_id_idx;


--
-- Name: td_audit_log_2024_12_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2024_12_event_type_idx;


--
-- Name: td_audit_log_2024_12_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2024_12_pkey;


--
-- Name: td_audit_log_2024_12_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2024_12_tenant_id_idx;


--
-- Name: td_audit_log_2024_12_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2024_12_timestamp_idx;


--
-- Name: td_audit_log_2024_12_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2024_12_user_id_idx;


--
-- Name: td_audit_log_2025_01_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2025_01_event_type_idx;


--
-- Name: td_audit_log_2025_01_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2025_01_pkey;


--
-- Name: td_audit_log_2025_01_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2025_01_tenant_id_idx;


--
-- Name: td_audit_log_2025_01_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2025_01_timestamp_idx;


--
-- Name: td_audit_log_2025_01_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2025_01_user_id_idx;


--
-- Name: td_audit_log_2025_02_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2025_02_event_type_idx;


--
-- Name: td_audit_log_2025_02_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2025_02_pkey;


--
-- Name: td_audit_log_2025_02_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2025_02_tenant_id_idx;


--
-- Name: td_audit_log_2025_02_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2025_02_timestamp_idx;


--
-- Name: td_audit_log_2025_02_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2025_02_user_id_idx;


--
-- Name: td_audit_log_2025_03_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2025_03_event_type_idx;


--
-- Name: td_audit_log_2025_03_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2025_03_pkey;


--
-- Name: td_audit_log_2025_03_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2025_03_tenant_id_idx;


--
-- Name: td_audit_log_2025_03_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2025_03_timestamp_idx;


--
-- Name: td_audit_log_2025_03_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2025_03_user_id_idx;


--
-- Name: td_audit_log_2025_04_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2025_04_event_type_idx;


--
-- Name: td_audit_log_2025_04_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2025_04_pkey;


--
-- Name: td_audit_log_2025_04_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2025_04_tenant_id_idx;


--
-- Name: td_audit_log_2025_04_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2025_04_timestamp_idx;


--
-- Name: td_audit_log_2025_04_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2025_04_user_id_idx;


--
-- Name: td_audit_log_2025_05_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2025_05_event_type_idx;


--
-- Name: td_audit_log_2025_05_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2025_05_pkey;


--
-- Name: td_audit_log_2025_05_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2025_05_tenant_id_idx;


--
-- Name: td_audit_log_2025_05_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2025_05_timestamp_idx;


--
-- Name: td_audit_log_2025_05_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2025_05_user_id_idx;


--
-- Name: td_audit_log_2025_06_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2025_06_event_type_idx;


--
-- Name: td_audit_log_2025_06_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2025_06_pkey;


--
-- Name: td_audit_log_2025_06_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2025_06_tenant_id_idx;


--
-- Name: td_audit_log_2025_06_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2025_06_timestamp_idx;


--
-- Name: td_audit_log_2025_06_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2025_06_user_id_idx;


--
-- Name: td_audit_log_2025_07_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2025_07_event_type_idx;


--
-- Name: td_audit_log_2025_07_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2025_07_pkey;


--
-- Name: td_audit_log_2025_07_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2025_07_tenant_id_idx;


--
-- Name: td_audit_log_2025_07_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2025_07_timestamp_idx;


--
-- Name: td_audit_log_2025_07_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2025_07_user_id_idx;


--
-- Name: td_audit_log_2025_08_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2025_08_event_type_idx;


--
-- Name: td_audit_log_2025_08_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2025_08_pkey;


--
-- Name: td_audit_log_2025_08_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2025_08_tenant_id_idx;


--
-- Name: td_audit_log_2025_08_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2025_08_timestamp_idx;


--
-- Name: td_audit_log_2025_08_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2025_08_user_id_idx;


--
-- Name: td_audit_log_2025_09_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2025_09_event_type_idx;


--
-- Name: td_audit_log_2025_09_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2025_09_pkey;


--
-- Name: td_audit_log_2025_09_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2025_09_tenant_id_idx;


--
-- Name: td_audit_log_2025_09_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2025_09_timestamp_idx;


--
-- Name: td_audit_log_2025_09_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2025_09_user_id_idx;


--
-- Name: td_audit_log_2025_10_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2025_10_event_type_idx;


--
-- Name: td_audit_log_2025_10_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2025_10_pkey;


--
-- Name: td_audit_log_2025_10_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2025_10_tenant_id_idx;


--
-- Name: td_audit_log_2025_10_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2025_10_timestamp_idx;


--
-- Name: td_audit_log_2025_10_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2025_10_user_id_idx;


--
-- Name: td_audit_log_2025_11_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2025_11_event_type_idx;


--
-- Name: td_audit_log_2025_11_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2025_11_pkey;


--
-- Name: td_audit_log_2025_11_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2025_11_tenant_id_idx;


--
-- Name: td_audit_log_2025_11_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2025_11_timestamp_idx;


--
-- Name: td_audit_log_2025_11_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2025_11_user_id_idx;


--
-- Name: td_audit_log_2025_12_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2025_12_event_type_idx;


--
-- Name: td_audit_log_2025_12_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2025_12_pkey;


--
-- Name: td_audit_log_2025_12_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2025_12_tenant_id_idx;


--
-- Name: td_audit_log_2025_12_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2025_12_timestamp_idx;


--
-- Name: td_audit_log_2025_12_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2025_12_user_id_idx;


--
-- Name: td_audit_log_2026_01_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2026_01_event_type_idx;


--
-- Name: td_audit_log_2026_01_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2026_01_pkey;


--
-- Name: td_audit_log_2026_01_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2026_01_tenant_id_idx;


--
-- Name: td_audit_log_2026_01_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2026_01_timestamp_idx;


--
-- Name: td_audit_log_2026_01_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2026_01_user_id_idx;


--
-- Name: td_audit_log_2026_02_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2026_02_event_type_idx;


--
-- Name: td_audit_log_2026_02_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2026_02_pkey;


--
-- Name: td_audit_log_2026_02_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2026_02_tenant_id_idx;


--
-- Name: td_audit_log_2026_02_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2026_02_timestamp_idx;


--
-- Name: td_audit_log_2026_02_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2026_02_user_id_idx;


--
-- Name: td_audit_log_2026_03_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2026_03_event_type_idx;


--
-- Name: td_audit_log_2026_03_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2026_03_pkey;


--
-- Name: td_audit_log_2026_03_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2026_03_tenant_id_idx;


--
-- Name: td_audit_log_2026_03_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2026_03_timestamp_idx;


--
-- Name: td_audit_log_2026_03_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2026_03_user_id_idx;


--
-- Name: td_audit_log_2026_04_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2026_04_event_type_idx;


--
-- Name: td_audit_log_2026_04_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2026_04_pkey;


--
-- Name: td_audit_log_2026_04_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2026_04_tenant_id_idx;


--
-- Name: td_audit_log_2026_04_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2026_04_timestamp_idx;


--
-- Name: td_audit_log_2026_04_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2026_04_user_id_idx;


--
-- Name: td_audit_log_2026_05_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2026_05_event_type_idx;


--
-- Name: td_audit_log_2026_05_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2026_05_pkey;


--
-- Name: td_audit_log_2026_05_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2026_05_tenant_id_idx;


--
-- Name: td_audit_log_2026_05_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2026_05_timestamp_idx;


--
-- Name: td_audit_log_2026_05_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2026_05_user_id_idx;


--
-- Name: td_audit_log_2026_06_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2026_06_event_type_idx;


--
-- Name: td_audit_log_2026_06_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2026_06_pkey;


--
-- Name: td_audit_log_2026_06_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2026_06_tenant_id_idx;


--
-- Name: td_audit_log_2026_06_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2026_06_timestamp_idx;


--
-- Name: td_audit_log_2026_06_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2026_06_user_id_idx;


--
-- Name: td_audit_log_2026_07_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2026_07_event_type_idx;


--
-- Name: td_audit_log_2026_07_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2026_07_pkey;


--
-- Name: td_audit_log_2026_07_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2026_07_tenant_id_idx;


--
-- Name: td_audit_log_2026_07_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2026_07_timestamp_idx;


--
-- Name: td_audit_log_2026_07_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2026_07_user_id_idx;


--
-- Name: td_audit_log_2026_08_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2026_08_event_type_idx;


--
-- Name: td_audit_log_2026_08_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2026_08_pkey;


--
-- Name: td_audit_log_2026_08_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2026_08_tenant_id_idx;


--
-- Name: td_audit_log_2026_08_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2026_08_timestamp_idx;


--
-- Name: td_audit_log_2026_08_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2026_08_user_id_idx;


--
-- Name: td_audit_log_2026_09_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2026_09_event_type_idx;


--
-- Name: td_audit_log_2026_09_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2026_09_pkey;


--
-- Name: td_audit_log_2026_09_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2026_09_tenant_id_idx;


--
-- Name: td_audit_log_2026_09_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2026_09_timestamp_idx;


--
-- Name: td_audit_log_2026_09_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2026_09_user_id_idx;


--
-- Name: td_audit_log_2026_10_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2026_10_event_type_idx;


--
-- Name: td_audit_log_2026_10_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2026_10_pkey;


--
-- Name: td_audit_log_2026_10_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2026_10_tenant_id_idx;


--
-- Name: td_audit_log_2026_10_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2026_10_timestamp_idx;


--
-- Name: td_audit_log_2026_10_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2026_10_user_id_idx;


--
-- Name: td_audit_log_2026_11_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2026_11_event_type_idx;


--
-- Name: td_audit_log_2026_11_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2026_11_pkey;


--
-- Name: td_audit_log_2026_11_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2026_11_tenant_id_idx;


--
-- Name: td_audit_log_2026_11_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2026_11_timestamp_idx;


--
-- Name: td_audit_log_2026_11_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2026_11_user_id_idx;


--
-- Name: td_audit_log_2026_12_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_2026_12_event_type_idx;


--
-- Name: td_audit_log_2026_12_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_2026_12_pkey;


--
-- Name: td_audit_log_2026_12_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_2026_12_tenant_id_idx;


--
-- Name: td_audit_log_2026_12_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_2026_12_timestamp_idx;


--
-- Name: td_audit_log_2026_12_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_2026_12_user_id_idx;


--
-- Name: td_audit_log_default_event_type_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_event ATTACH PARTITION ecos_security.td_audit_log_default_event_type_idx;


--
-- Name: td_audit_log_default_pkey; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.td_audit_log_pkey ATTACH PARTITION ecos_security.td_audit_log_default_pkey;


--
-- Name: td_audit_log_default_tenant_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_tenant ATTACH PARTITION ecos_security.td_audit_log_default_tenant_id_idx;


--
-- Name: td_audit_log_default_timestamp_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_timestamp ATTACH PARTITION ecos_security.td_audit_log_default_timestamp_idx;


--
-- Name: td_audit_log_default_user_id_idx; Type: INDEX ATTACH; Schema: ecos_security; Owner: postgres
--

ALTER INDEX ecos_security.idx_security_audit_user ATTACH PARTITION ecos_security.td_audit_log_default_user_id_idx;


--
-- Name: agent_execution fk_ai_exec_agent; Type: FK CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_execution
    ADD CONSTRAINT fk_ai_exec_agent FOREIGN KEY (agent_id) REFERENCES ecos_ai.agent_definition(id);


--
-- Name: ecos_mission_task fk_ai_mtask_mission; Type: FK CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.ecos_mission_task
    ADD CONSTRAINT fk_ai_mtask_mission FOREIGN KEY (mission_id) REFERENCES ecos_ai.ecos_mission(id);


--
-- Name: simulation fk_ai_sim_scenario; Type: FK CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.simulation
    ADD CONSTRAINT fk_ai_sim_scenario FOREIGN KEY (scenario_id) REFERENCES ecos_ai.scenario(id);


--
-- Name: simulation_result fk_ai_simres_sim; Type: FK CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.simulation_result
    ADD CONSTRAINT fk_ai_simres_sim FOREIGN KEY (simulation_id) REFERENCES ecos_ai.simulation(id);


--
-- Name: agent_execution_step fk_ai_step_exec; Type: FK CONSTRAINT; Schema: ecos_ai; Owner: postgres
--

ALTER TABLE ONLY ecos_ai.agent_execution_step
    ADD CONSTRAINT fk_ai_step_exec FOREIGN KEY (execution_id) REFERENCES ecos_ai.agent_execution(id);


--
-- Name: ecos_wm_causal_link fk_cog_causal_src; Type: FK CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_causal_link
    ADD CONSTRAINT fk_cog_causal_src FOREIGN KEY (source_goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);


--
-- Name: ecos_wm_causal_link fk_cog_causal_tgt; Type: FK CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_causal_link
    ADD CONSTRAINT fk_cog_causal_tgt FOREIGN KEY (target_goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id) ON DELETE CASCADE;


--
-- Name: ecos_biz_contract fk_cog_contract_proj; Type: FK CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_contract
    ADD CONSTRAINT fk_cog_contract_proj FOREIGN KEY (project_id) REFERENCES ecos_cognitive.ecos_biz_project(id);


--
-- Name: ecos_wm_goal fk_cog_goal_parent; Type: FK CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_goal
    ADD CONSTRAINT fk_cog_goal_parent FOREIGN KEY (parent_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);


--
-- Name: ecos_wm_goal_log fk_cog_goallog_goal; Type: FK CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_wm_goal_log
    ADD CONSTRAINT fk_cog_goallog_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id) ON DELETE CASCADE;


--
-- Name: ecos_biz_metric fk_cog_metric_goal; Type: FK CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_metric
    ADD CONSTRAINT fk_cog_metric_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);


--
-- Name: ecos_biz_project fk_cog_proj_goal; Type: FK CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_project
    ADD CONSTRAINT fk_cog_proj_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);


--
-- Name: ecos_biz_target fk_cog_target_goal; Type: FK CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_biz_target
    ADD CONSTRAINT fk_cog_target_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);


--
-- Name: ecos_goal_tracking fk_cog_tracking_goal; Type: FK CONSTRAINT; Schema: ecos_cognitive; Owner: postgres
--

ALTER TABLE ONLY ecos_cognitive.ecos_goal_tracking
    ADD CONSTRAINT fk_cog_tracking_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id) ON DELETE CASCADE;


--
-- Name: dict_column dict_column_table_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.dict_column
    ADD CONSTRAINT dict_column_table_id_fkey FOREIGN KEY (table_id) REFERENCES ecos_control.dict_table(id) ON DELETE CASCADE;


--
-- Name: ecos_alert_history ecos_alert_history_rule_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_alert_history
    ADD CONSTRAINT ecos_alert_history_rule_id_fkey FOREIGN KEY (rule_id) REFERENCES ecos_control.ecos_alert_rule(id);


--
-- Name: ecos_biz_metric ecos_biz_metric_goal_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_biz_metric
    ADD CONSTRAINT ecos_biz_metric_goal_id_fkey FOREIGN KEY (goal_id) REFERENCES ecos_control.ecos_wm_goal(id);


--
-- Name: ecos_biz_project ecos_biz_project_goal_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_biz_project
    ADD CONSTRAINT ecos_biz_project_goal_id_fkey FOREIGN KEY (goal_id) REFERENCES ecos_control.ecos_wm_goal(id);


--
-- Name: ecos_biz_target ecos_biz_target_goal_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_biz_target
    ADD CONSTRAINT ecos_biz_target_goal_id_fkey FOREIGN KEY (goal_id) REFERENCES ecos_control.ecos_wm_goal(id);


--
-- Name: ecos_business_glossary ecos_business_glossary_domain_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_business_glossary
    ADD CONSTRAINT ecos_business_glossary_domain_id_fkey FOREIGN KEY (domain_id) REFERENCES ecos_control.ecos_domain(id);


--
-- Name: ecos_data_lineage_edge ecos_data_lineage_edge_source_node_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_data_lineage_edge
    ADD CONSTRAINT ecos_data_lineage_edge_source_node_id_fkey FOREIGN KEY (source_node_id) REFERENCES ecos_control.ecos_data_lineage_node(id);


--
-- Name: ecos_data_lineage_edge ecos_data_lineage_edge_target_node_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_data_lineage_edge
    ADD CONSTRAINT ecos_data_lineage_edge_target_node_id_fkey FOREIGN KEY (target_node_id) REFERENCES ecos_control.ecos_data_lineage_node(id);


--
-- Name: ecos_data_pipeline_edge ecos_data_pipeline_edge_pipeline_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_data_pipeline_edge
    ADD CONSTRAINT ecos_data_pipeline_edge_pipeline_id_fkey FOREIGN KEY (pipeline_id) REFERENCES ecos_control.ecos_data_pipeline(id);


--
-- Name: ecos_data_pipeline_edge ecos_data_pipeline_edge_source_node_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_data_pipeline_edge
    ADD CONSTRAINT ecos_data_pipeline_edge_source_node_id_fkey FOREIGN KEY (source_node_id) REFERENCES ecos_control.ecos_data_pipeline_node(id);


--
-- Name: ecos_data_pipeline_edge ecos_data_pipeline_edge_target_node_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_data_pipeline_edge
    ADD CONSTRAINT ecos_data_pipeline_edge_target_node_id_fkey FOREIGN KEY (target_node_id) REFERENCES ecos_control.ecos_data_pipeline_node(id);


--
-- Name: ecos_data_pipeline_node ecos_data_pipeline_node_pipeline_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_data_pipeline_node
    ADD CONSTRAINT ecos_data_pipeline_node_pipeline_id_fkey FOREIGN KEY (pipeline_id) REFERENCES ecos_control.ecos_data_pipeline(id);


--
-- Name: ecos_dq_issue ecos_dq_issue_rule_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_dq_issue
    ADD CONSTRAINT ecos_dq_issue_rule_id_fkey FOREIGN KEY (rule_id) REFERENCES ecos_control.ecos_dq_rule(id);


--
-- Name: ecos_glossary_term ecos_glossary_term_domain_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_glossary_term
    ADD CONSTRAINT ecos_glossary_term_domain_id_fkey FOREIGN KEY (domain_id) REFERENCES ecos_control.ecos_domain(id);


--
-- Name: ecos_goal_tracking ecos_goal_tracking_goal_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_goal_tracking
    ADD CONSTRAINT ecos_goal_tracking_goal_id_fkey FOREIGN KEY (goal_id) REFERENCES ecos_control.ecos_wm_goal(id) ON DELETE CASCADE;


--
-- Name: ecos_marketplace_access_request ecos_marketplace_access_request_asset_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_marketplace_access_request
    ADD CONSTRAINT ecos_marketplace_access_request_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ecos_control.ecos_marketplace_asset(id);


--
-- Name: ecos_ontology_entity ecos_ontology_entity_domain_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_ontology_entity
    ADD CONSTRAINT ecos_ontology_entity_domain_id_fkey FOREIGN KEY (domain_id) REFERENCES ecos_control.ecos_domain(id);


--
-- Name: ecos_pipeline_execution ecos_pipeline_execution_pipeline_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_pipeline_execution
    ADD CONSTRAINT ecos_pipeline_execution_pipeline_id_fkey FOREIGN KEY (pipeline_id) REFERENCES ecos_control.ecos_pipeline_definition(id);


--
-- Name: ecos_wm_causal_link ecos_wm_causal_link_source_goal_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_wm_causal_link
    ADD CONSTRAINT ecos_wm_causal_link_source_goal_id_fkey FOREIGN KEY (source_goal_id) REFERENCES ecos_control.ecos_wm_goal(id) ON DELETE CASCADE;


--
-- Name: ecos_wm_causal_link ecos_wm_causal_link_target_goal_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_wm_causal_link
    ADD CONSTRAINT ecos_wm_causal_link_target_goal_id_fkey FOREIGN KEY (target_goal_id) REFERENCES ecos_control.ecos_wm_goal(id) ON DELETE CASCADE;


--
-- Name: ecos_wm_goal ecos_wm_goal_domain_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_wm_goal
    ADD CONSTRAINT ecos_wm_goal_domain_id_fkey FOREIGN KEY (domain_id) REFERENCES ecos_control.ecos_domain(id);


--
-- Name: ecos_wm_goal_log ecos_wm_goal_log_goal_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_wm_goal_log
    ADD CONSTRAINT ecos_wm_goal_log_goal_id_fkey FOREIGN KEY (goal_id) REFERENCES ecos_control.ecos_wm_goal(id) ON DELETE CASCADE;


--
-- Name: ecos_wm_goal ecos_wm_goal_parent_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_wm_goal
    ADD CONSTRAINT ecos_wm_goal_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES ecos_control.ecos_wm_goal(id) ON DELETE SET NULL;


--
-- Name: ecos_world_goal ecos_world_goal_parent_goal_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_world_goal
    ADD CONSTRAINT ecos_world_goal_parent_goal_id_fkey FOREIGN KEY (parent_goal_id) REFERENCES ecos_control.ecos_world_goal(id);


--
-- Name: ecos_world_scenario ecos_world_scenario_base_scenario_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_world_scenario
    ADD CONSTRAINT ecos_world_scenario_base_scenario_id_fkey FOREIGN KEY (base_scenario_id) REFERENCES ecos_control.ecos_world_scenario(id);


--
-- Name: ecos_world_scenario_impact ecos_world_scenario_impact_goal_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_world_scenario_impact
    ADD CONSTRAINT ecos_world_scenario_impact_goal_id_fkey FOREIGN KEY (goal_id) REFERENCES ecos_control.ecos_world_goal(id);


--
-- Name: ecos_world_scenario_impact ecos_world_scenario_impact_scenario_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_world_scenario_impact
    ADD CONSTRAINT ecos_world_scenario_impact_scenario_id_fkey FOREIGN KEY (scenario_id) REFERENCES ecos_control.ecos_world_scenario(id);


--
-- Name: ecos_glossary_term_relation fk_glossary_relation_from; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_glossary_term_relation
    ADD CONSTRAINT fk_glossary_relation_from FOREIGN KEY (from_term_id) REFERENCES ecos_control.ecos_glossary_term(id) ON DELETE CASCADE;


--
-- Name: ecos_glossary_term_relation fk_glossary_relation_to; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_glossary_term_relation
    ADD CONSTRAINT fk_glossary_relation_to FOREIGN KEY (to_term_id) REFERENCES ecos_control.ecos_glossary_term(id) ON DELETE CASCADE;


--
-- Name: ecos_glossary_term fk_glossary_term_parent; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.ecos_glossary_term
    ADD CONSTRAINT fk_glossary_term_parent FOREIGN KEY (parent_term_id) REFERENCES ecos_control.ecos_glossary_term(id) ON DELETE SET NULL;


--
-- Name: permissions permissions_role_name_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.permissions
    ADD CONSTRAINT permissions_role_name_fkey FOREIGN KEY (role_name) REFERENCES ecos_control.roles(name);


--
-- Name: sys_agent_message sys_agent_message_session_id_fkey; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.sys_agent_message
    ADD CONSTRAINT sys_agent_message_session_id_fkey FOREIGN KEY (session_id) REFERENCES ecos_control.sys_agent_session(id);


--
-- Name: td_org_permission td_org_permission_td_org_permission_ibfk_1; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_org_permission
    ADD CONSTRAINT td_org_permission_td_org_permission_ibfk_1 FOREIGN KEY ("ORG_ID") REFERENCES ecos_control.td_organization("ORG_ID");


--
-- Name: td_organization td_organization_td_organization_ibfk_1; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_organization
    ADD CONSTRAINT td_organization_td_organization_ibfk_1 FOREIGN KEY ("PARENT_ORG_ID") REFERENCES ecos_control.td_organization("ORG_ID");


--
-- Name: td_runtime_task_execution td_runtime_task_execution_td_runtime_task_execution_ibfk_1; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_runtime_task_execution
    ADD CONSTRAINT td_runtime_task_execution_td_runtime_task_execution_ibfk_1 FOREIGN KEY ("TASK_ID") REFERENCES ecos_control.td_runtime_task("TASK_ID") ON DELETE CASCADE;


--
-- Name: td_runtime_task_log td_runtime_task_log_td_runtime_task_log_ibfk_1; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_runtime_task_log
    ADD CONSTRAINT td_runtime_task_log_td_runtime_task_log_ibfk_1 FOREIGN KEY ("TASK_ID") REFERENCES ecos_control.td_runtime_task("TASK_ID") ON DELETE CASCADE;


--
-- Name: td_runtime_task_plan td_runtime_task_plan_td_runtime_task_plan_ibfk_1; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_runtime_task_plan
    ADD CONSTRAINT td_runtime_task_plan_td_runtime_task_plan_ibfk_1 FOREIGN KEY ("TASK_ID") REFERENCES ecos_control.td_runtime_task("TASK_ID") ON DELETE CASCADE;


--
-- Name: td_runtime_task_status td_runtime_task_status_td_runtime_task_status_ibfk_1; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_runtime_task_status
    ADD CONSTRAINT td_runtime_task_status_td_runtime_task_status_ibfk_1 FOREIGN KEY ("TASK_ID") REFERENCES ecos_control.td_runtime_task("TASK_ID") ON DELETE CASCADE;


--
-- Name: td_user_organization td_user_organization_td_user_organization_ibfk_1; Type: FK CONSTRAINT; Schema: ecos_control; Owner: postgres
--

ALTER TABLE ONLY ecos_control.td_user_organization
    ADD CONSTRAINT td_user_organization_td_user_organization_ibfk_1 FOREIGN KEY ("ORG_ID") REFERENCES ecos_control.td_organization("ORG_ID");


--
-- Name: ecos_dq_issue fk_data_dqi_rule; Type: FK CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_dq_issue
    ADD CONSTRAINT fk_data_dqi_rule FOREIGN KEY (rule_id) REFERENCES ecos_data.ecos_dq_rule(id) ON DELETE CASCADE;


--
-- Name: td_datasource fk_data_ds_org; Type: FK CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.td_datasource
    ADD CONSTRAINT fk_data_ds_org FOREIGN KEY (org_id) REFERENCES ecos_sysman.td_organization("ORG_ID");


--
-- Name: td_data_field fk_data_field_res; Type: FK CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.td_data_field
    ADD CONSTRAINT fk_data_field_res FOREIGN KEY (resource_id) REFERENCES ecos_data.td_data_resource(resource_id);


--
-- Name: ecos_pipeline_edge fk_data_pedge_def; Type: FK CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_pipeline_edge
    ADD CONSTRAINT fk_data_pedge_def FOREIGN KEY (definition_id) REFERENCES ecos_data.ecos_pipeline_definition(id);


--
-- Name: ecos_pipeline_execution fk_data_pexec_def; Type: FK CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_pipeline_execution
    ADD CONSTRAINT fk_data_pexec_def FOREIGN KEY (pipeline_id) REFERENCES ecos_data.ecos_pipeline_definition(id);


--
-- Name: ecos_pipeline_node fk_data_pnode_def; Type: FK CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.ecos_pipeline_node
    ADD CONSTRAINT fk_data_pnode_def FOREIGN KEY (definition_id) REFERENCES ecos_data.ecos_pipeline_definition(id);


--
-- Name: td_data_resource fk_data_res_ds; Type: FK CONSTRAINT; Schema: ecos_data; Owner: postgres
--

ALTER TABLE ONLY ecos_data.td_data_resource
    ADD CONSTRAINT fk_data_res_ds FOREIGN KEY (datasource_id) REFERENCES ecos_data.td_datasource(datasource_id);


--
-- Name: graph_edge fk_kb_gedge_src; Type: FK CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.graph_edge
    ADD CONSTRAINT fk_kb_gedge_src FOREIGN KEY (source_id) REFERENCES ecos_knowledge.graph_node(id);


--
-- Name: graph_edge fk_kb_gedge_tgt; Type: FK CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.graph_edge
    ADD CONSTRAINT fk_kb_gedge_tgt FOREIGN KEY (target_id) REFERENCES ecos_knowledge.graph_node(id);


--
-- Name: ecos_marketplace_access_request fk_kb_mktreq_asset; Type: FK CONSTRAINT; Schema: ecos_knowledge; Owner: postgres
--

ALTER TABLE ONLY ecos_knowledge.ecos_marketplace_access_request
    ADD CONSTRAINT fk_kb_mktreq_asset FOREIGN KEY (asset_id) REFERENCES ecos_knowledge.ecos_marketplace_asset(id);


--
-- Name: td_role_security_profile fk_security_role_prof_role; Type: FK CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_role_security_profile
    ADD CONSTRAINT fk_security_role_prof_role FOREIGN KEY (role_id) REFERENCES ecos_sysman.td_role(role_id);


--
-- Name: ecos_spans fk_security_spans_tenant; Type: FK CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.ecos_spans
    ADD CONSTRAINT fk_security_spans_tenant FOREIGN KEY (tenant_id) REFERENCES ecos_sysman.ecos_tenant(id);


--
-- Name: td_user_security_profile fk_security_user_prof_user; Type: FK CONSTRAINT; Schema: ecos_security; Owner: postgres
--

ALTER TABLE ONLY ecos_security.td_user_security_profile
    ADD CONSTRAINT fk_security_user_prof_user FOREIGN KEY (user_id) REFERENCES ecos_sysman.td_user(user_id);


--
-- Name: td_org_permission fk_sysman_orgperm_org; Type: FK CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.td_org_permission
    ADD CONSTRAINT fk_sysman_orgperm_org FOREIGN KEY (org_id) REFERENCES ecos_sysman.td_organization("ORG_ID");


--
-- Name: ecos_tenant_quota fk_sysman_quota_tenant; Type: FK CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.ecos_tenant_quota
    ADD CONSTRAINT fk_sysman_quota_tenant FOREIGN KEY (tenant_id) REFERENCES ecos_sysman.ecos_tenant(id);


--
-- Name: td_role_permission fk_sysman_role_perm_perm; Type: FK CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.td_role_permission
    ADD CONSTRAINT fk_sysman_role_perm_perm FOREIGN KEY (permission_id) REFERENCES ecos_sysman.td_permission(permission_id);


--
-- Name: td_role_permission fk_sysman_role_perm_role; Type: FK CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.td_role_permission
    ADD CONSTRAINT fk_sysman_role_perm_role FOREIGN KEY (role_id) REFERENCES ecos_sysman.td_role(role_id);


--
-- Name: ecos_tenant_usage fk_sysman_usage_tenant; Type: FK CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.ecos_tenant_usage
    ADD CONSTRAINT fk_sysman_usage_tenant FOREIGN KEY (tenant_id) REFERENCES ecos_sysman.ecos_tenant(id);


--
-- Name: td_user_organization fk_sysman_user_org_org; Type: FK CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.td_user_organization
    ADD CONSTRAINT fk_sysman_user_org_org FOREIGN KEY ("ORG_ID") REFERENCES ecos_sysman.td_organization("ORG_ID");


--
-- Name: td_user_organization fk_sysman_user_org_user; Type: FK CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.td_user_organization
    ADD CONSTRAINT fk_sysman_user_org_user FOREIGN KEY ("USER_ID") REFERENCES ecos_sysman.td_user(user_id);


--
-- Name: td_user_role fk_sysman_user_role_role; Type: FK CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.td_user_role
    ADD CONSTRAINT fk_sysman_user_role_role FOREIGN KEY (role_id) REFERENCES ecos_sysman.td_role(role_id);


--
-- Name: td_user_role fk_sysman_user_role_user; Type: FK CONSTRAINT; Schema: ecos_sysman; Owner: postgres
--

ALTER TABLE ONLY ecos_sysman.td_user_role
    ADD CONSTRAINT fk_sysman_user_role_user FOREIGN KEY (user_id) REFERENCES ecos_sysman.td_user(user_id);


--
-- PostgreSQL database dump complete
--

\unrestrict OGtDXORmaPeiE2zLxcTwuogPfpBxWG5tSIssFbSrv2JXZN2s4yvKXxD9v6pnQ0Y

