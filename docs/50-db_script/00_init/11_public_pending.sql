-- ============================================================================
-- 11_public_pending.sql — public 挂起待裁定表（原样保留，勿臆造归属）
-- ============================================================================
-- 由 pg_dump --schema-only 从真实库 sys_man 反向导出后清洗装配（2026-11-17，V349 清理后重导）。
-- 共 7 张表 / 30 个对象。这批表在单源脚本里就建于 public，main 代码零真实 SQL 引用，无归属信号 ⇒ 归属待用户裁定。
-- 既不臆造 schema 搬走，也不丢弃唯一副本：原样保留 public 前缀。裁定后按 V345/V346 同口径归位并删除本册。
-- 表清单: ecos_decision_source_ref, ecos_fc_action_ext, ecos_knowledge_document, ecos_ontology_domain, ecos_runtime_llm_usage, ecos_scenario_active_mind, ecos_scenario_mind_ref
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

-- ecos_decision_source_ref (TABLE)
CREATE TABLE public.ecos_decision_source_ref (
    id character varying(36) NOT NULL,
    decision_id character varying(36) NOT NULL,
    ep character varying(120) NOT NULL,
    hash character varying(64) NOT NULL,
    mind_id character varying(36),
    ts timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64) DEFAULT 'system' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL
);

--

-- TABLE ecos_decision_source_ref (COMMENT)
COMMENT ON TABLE public.ecos_decision_source_ref IS '决策溯源明细表（E-3 定版 (decision_id, ep, hash, mind_id, ts)；1:N 替代 source_refs JSONB）';

--

-- ecos_fc_action_ext (TABLE)
CREATE TABLE public.ecos_fc_action_ext (
    id character varying(36) NOT NULL,
    decision_id character varying(36) NOT NULL,
    forecast_run_id character varying(36),
    factor_ref character varying(120),
    project_id character varying(36),
    department_id character varying(36),
    action_type character varying(20) NOT NULL,
    action_desc text NOT NULL,
    owner_id character varying(100) NOT NULL,
    due_date date NOT NULL,
    expected_impact numeric(18,2) NOT NULL,
    kpi_text character varying(500) NOT NULL,
    owner_role character varying(100),
    status character varying(20) DEFAULT 'DRAFT' NOT NULL,
    approved_by character varying(100),
    approved_at timestamp without time zone,
    effect_json text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_fc_action_ext (COMMENT)
COMMENT ON TABLE public.ecos_fc_action_ext IS '经营动作领域扩展（控制域，扩展随主挂 cognitive 决策底座 decision_id 1:1，R-30 受控组合）；非独立决策底座；ADR-12 迁 ecos_control 后 MC06 注入限定名';

--

-- ecos_fc_caliber (VIEW)
CREATE VIEW public.ecos_fc_caliber AS
 SELECT c.id AS caliber_id,
    c.code AS caliber_code,
    c.name AS caliber_name,
    c.unit,
    c.currency,
    c.owner_role,
    c.domain,
    cv.id AS caliber_version_id,
    cv.version_no AS caliber_version,
    cv.formula AS formula_text,
    cv.formula AS formula_json,
    cv.additive,
    cv.period_granularity,
    cv.status AS caliber_status,
    cv.approved_by,
    cv.approved_at,
    cv.git_ref AS content_git_ref,
    cv.create_time,
    cv.update_time,
    cv.create_by,
    cv.update_by,
    cv.is_deleted
   FROM (ecos_ontology.ecos_caliber c
     JOIN ecos_ontology.ecos_caliber_version cv ON (((cv.caliber_id)::text = (c.id)::text)))
  WHERE (c.is_deleted = 0);

--

-- VIEW ecos_fc_caliber (COMMENT)
COMMENT ON VIEW public.ecos_fc_caliber IS '财务口径引用视图（R-60② 已批准：不落 `ecos_fc_caliber` 表，口径主权在 ecos_ontology 本体域）；预测侧只读消费，禁 UPDATE/INSERT（视图天然单表 JOIN 可更新性由应用层禁写约束）；历史版本走 Git（铁律 §3.1）';

--

-- ecos_knowledge_document (TABLE)
CREATE TABLE public.ecos_knowledge_document (
    id character varying(64) NOT NULL,
    title character varying(255) NOT NULL,
    content text,
    doc_type character varying(32),
    tags character varying(256),
    entity_types character varying(512),
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);

--

-- TABLE ecos_knowledge_document (COMMENT)
COMMENT ON TABLE public.ecos_knowledge_document IS 'knownLegacy(V184/MC01): PK 宽度 36↔64/BIGSERIAL 违规，新写切 V184 ecos_knowledge.ecos_kb_document（VARCHAR(36) 统一）；本表只停写不迁（R-1 a + IR03 零 DROP）';

--

-- ecos_ontology_domain (TABLE)
CREATE TABLE public.ecos_ontology_domain (
    id character varying(64) NOT NULL,
    code character varying(128) NOT NULL,
    name character varying(255) NOT NULL,
    owner character varying(128),
    description text DEFAULT ''::text,
    status character varying(32) DEFAULT 'ACTIVE' NOT NULL,
    sort_order integer DEFAULT 1,
    tenant_id character varying(64),
    create_time timestamp without time zone DEFAULT now(),
    update_time timestamp without time zone DEFAULT now(),
    create_by character varying(128),
    update_by character varying(128),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- ecos_runtime_llm_usage (TABLE)
CREATE TABLE public.ecos_runtime_llm_usage (
    id character varying(36) NOT NULL,
    provider character varying(32) NOT NULL,
    model character varying(64) NOT NULL,
    purpose character varying(32) NOT NULL,
    prompt_tokens integer DEFAULT 0 NOT NULL,
    completion_tokens integer DEFAULT 0 NOT NULL,
    cost_amount numeric(18,6),
    caller_module character varying(64),
    caller_id character varying(36),
    scenario_id character varying(36),
    trace_id character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    tenant_id character varying(36)
);

--

-- ecos_scenario_active_mind (TABLE)
CREATE TABLE public.ecos_scenario_active_mind (
    scenario_id character varying(36) NOT NULL,
    mind_id character varying(36) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64) DEFAULT 'system' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL
);

--

-- TABLE ecos_scenario_active_mind (COMMENT)
COMMENT ON TABLE public.ecos_scenario_active_mind IS '场景激活心智 1:1 表（MC03 安全形态替代 partial unique；切换原子性由服务层事务 F07-19-2）';

--

-- ecos_scenario_mind_ref (TABLE)
CREATE TABLE public.ecos_scenario_mind_ref (
    id character varying(36) NOT NULL,
    mind_id character varying(36) NOT NULL,
    ref_kind character varying(20) NOT NULL,
    ref_id character varying(36) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64) DEFAULT 'system' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    CONSTRAINT ck_esmr_kind CHECK (((ref_kind)::text = ANY ((ARRAY['EVIDENCE', 'HYPOTHESIS', 'MODEL'])::text[])))
);

--

-- TABLE ecos_scenario_mind_ref (COMMENT)
COMMENT ON TABLE public.ecos_scenario_mind_ref IS '心智引用明细表（E-3：evidence/hypothesis/model 引用 1:N 明细；cognitive 侧仅存 id 引用、只读）';

--

-- ecos_decision_source_ref ecos_decision_source_ref_pkey (CONSTRAINT)
ALTER TABLE ONLY public.ecos_decision_source_ref
    ADD CONSTRAINT ecos_decision_source_ref_pkey PRIMARY KEY (id);

--

-- ecos_fc_action_ext ecos_fc_action_ext_pkey (CONSTRAINT)
ALTER TABLE ONLY public.ecos_fc_action_ext
    ADD CONSTRAINT ecos_fc_action_ext_pkey PRIMARY KEY (id);

--

-- ecos_knowledge_document ecos_knowledge_document_pkey (CONSTRAINT)
ALTER TABLE ONLY public.ecos_knowledge_document
    ADD CONSTRAINT ecos_knowledge_document_pkey PRIMARY KEY (id);

--

-- ecos_ontology_domain ecos_ontology_domain_code_key (CONSTRAINT)
ALTER TABLE ONLY public.ecos_ontology_domain
    ADD CONSTRAINT ecos_ontology_domain_code_key UNIQUE (code);

--

-- ecos_ontology_domain ecos_ontology_domain_pkey (CONSTRAINT)
ALTER TABLE ONLY public.ecos_ontology_domain
    ADD CONSTRAINT ecos_ontology_domain_pkey PRIMARY KEY (id);

--

-- ecos_runtime_llm_usage ecos_runtime_llm_usage_pkey (CONSTRAINT)
ALTER TABLE ONLY public.ecos_runtime_llm_usage
    ADD CONSTRAINT ecos_runtime_llm_usage_pkey PRIMARY KEY (id);

--

-- ecos_scenario_active_mind ecos_scenario_active_mind_pkey (CONSTRAINT)
ALTER TABLE ONLY public.ecos_scenario_active_mind
    ADD CONSTRAINT ecos_scenario_active_mind_pkey PRIMARY KEY (scenario_id);

--

-- ecos_scenario_mind_ref ecos_scenario_mind_ref_pkey (CONSTRAINT)
ALTER TABLE ONLY public.ecos_scenario_mind_ref
    ADD CONSTRAINT ecos_scenario_mind_ref_pkey PRIMARY KEY (id);

--

-- idx_edsr_decision (INDEX)
CREATE INDEX idx_edsr_decision ON public.ecos_decision_source_ref USING btree (decision_id);

--

-- idx_edsr_hash (INDEX)
CREATE INDEX idx_edsr_hash ON public.ecos_decision_source_ref USING btree (hash);

--

-- idx_esmr_mind (INDEX)
CREATE INDEX idx_esmr_mind ON public.ecos_scenario_mind_ref USING btree (mind_id, ref_kind);

--

-- idx_fc_action_ext_run (INDEX)
CREATE INDEX idx_fc_action_ext_run ON public.ecos_fc_action_ext USING btree (forecast_run_id);

--

-- idx_fc_action_ext_status (INDEX)
CREATE INDEX idx_fc_action_ext_status ON public.ecos_fc_action_ext USING btree (status);

--

-- idx_llm_usage_time (INDEX)
CREATE INDEX idx_llm_usage_time ON public.ecos_runtime_llm_usage USING btree (create_time, caller_module);

--

-- uniq_esmr_ref (INDEX)
CREATE UNIQUE INDEX uniq_esmr_ref ON public.ecos_scenario_mind_ref USING btree (mind_id, ref_kind, ref_id);

--

-- uniq_fc_action_ext_decision (INDEX)
CREATE UNIQUE INDEX uniq_fc_action_ext_decision ON public.ecos_fc_action_ext USING btree (decision_id, is_deleted);

--
-- PostgreSQL database dump complete
--

