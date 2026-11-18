-- ============================================================================
-- 04_ecos_knowledge.sql — ecos_knowledge 权威 DDL
-- ============================================================================
-- 由 pg_dump --schema-only 从真实库 sys_man 反向导出后清洗装配（2026-11-17，V349 清理后重导）。
-- 目标 schema = ecos_knowledge；保留对象 266 个，剔分区子表块 0 个，外键 2 条移入 09。
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

-- ecos_kb_access_request (TABLE)
CREATE TABLE ecos_knowledge.ecos_kb_access_request (
    id character varying(36) NOT NULL,
    asset_id character varying(36) NOT NULL,
    request_reason text,
    applicant_name character varying(100),
    status character varying(20) DEFAULT 'PENDING' NOT NULL,
    approve_by character varying(100),
    approve_time timestamp without time zone,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system' NOT NULL,
    update_by character varying(100) DEFAULT 'system' NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_ecos_kb_areq_status CHECK (((status)::text = ANY ((ARRAY['PENDING', 'APPROVED', 'REJECTED'])::text[])))
);

--

-- ecos_kb_assumption (TABLE)
CREATE TABLE ecos_knowledge.ecos_kb_assumption (
    id character varying(36) NOT NULL,
    assumption_key character varying(255) NOT NULL,
    scenario_type character varying(32) NOT NULL,
    metric_code character varying(64) NOT NULL,
    dim_scope_json text DEFAULT '{}'::text NOT NULL,
    value_type character varying(10) NOT NULL,
    value_ref character varying(36),
    value_semantic character varying(20) DEFAULT 'ABSOLUTE' NOT NULL,
    base_assumption_id character varying(36),
    valid_from character varying(7) NOT NULL,
    valid_to character varying(7),
    reason character varying(500) NOT NULL,
    evidence_ref character varying(255),
    is_unverified smallint DEFAULT 0 NOT NULL,
    expire_at timestamp without time zone,
    assumption_version character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'DRAFT' NOT NULL,
    approved_by character varying(100),
    approved_time timestamp without time zone,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_kba_sem CHECK (((value_semantic)::text = ANY ((ARRAY['ABSOLUTE', 'DELTA'])::text[]))),
    CONSTRAINT ck_kba_status CHECK (((status)::text = ANY ((ARRAY['DRAFT', 'PENDING_APPROVAL', 'ACTIVE', 'EXPIRED', 'SUPERSEDED'])::text[]))),
    CONSTRAINT ck_kba_vt CHECK (((value_type)::text = ANY ((ARRAY['RATE', 'AMOUNT', 'DAYS'])::text[])))
);

--

-- ecos_kb_document (TABLE)
CREATE TABLE ecos_knowledge.ecos_kb_document (
    id character varying(36) NOT NULL,
    doc_code character varying(64),
    title character varying(255) NOT NULL,
    content text,
    doc_type character varying(20),
    tags_json text DEFAULT '[]'::text NOT NULL,
    entity_types_json text DEFAULT '[]'::text NOT NULL,
    status character varying(20) DEFAULT 'DRAFT' NOT NULL,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system' NOT NULL,
    update_by character varying(100) DEFAULT 'system' NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--

-- ecos_kb_extract_audit (TABLE)
CREATE TABLE ecos_knowledge.ecos_kb_extract_audit (
    id character varying(36) NOT NULL,
    job_id character varying(64) NOT NULL,
    task_id character varying(64),
    extract_tier character varying(20) DEFAULT 'standard' NOT NULL,
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
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system' NOT NULL,
    update_by character varying(100) DEFAULT 'system' NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--

-- ecos_kb_extract_candidate (TABLE)
CREATE TABLE ecos_knowledge.ecos_kb_extract_candidate (
    id character varying(36) NOT NULL,
    doc_id character varying(64),
    entity_name character varying(255),
    entity_type character varying(20),
    relation_code character varying(64),
    subject_id character varying(36),
    object_id character varying(36),
    confidence numeric(5,4),
    status character varying(20) DEFAULT 'PENDING' NOT NULL,
    review_note text,
    ontology_id character varying(64),
    ontology_version character varying(128),
    source_resource_id character varying(64),
    source_pk character varying(255),
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system' NOT NULL,
    update_by character varying(100) DEFAULT 'system' NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_ecos_kbeic_status CHECK (((status)::text = ANY ((ARRAY['PENDING', 'APPROVED', 'REJECTED', 'MERGED'])::text[])))
);

--

-- ecos_kb_extract_watermark (TABLE)
CREATE TABLE ecos_knowledge.ecos_kb_extract_watermark (
    id character varying(36) NOT NULL,
    ontology_id character varying(64) NOT NULL,
    entity_code character varying(100) NOT NULL,
    resource_id character varying(64) NOT NULL,
    watermark character varying(255),
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system' NOT NULL,
    update_by character varying(100) DEFAULT 'system' NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--

-- ecos_kb_graph_edge (TABLE)
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
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system' NOT NULL,
    update_by character varying(100) DEFAULT 'system' NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--

-- ecos_kb_graph_node (TABLE)
CREATE TABLE ecos_knowledge.ecos_kb_graph_node (
    id character varying(36) NOT NULL,
    node_label character varying(120),
    node_type character varying(20) DEFAULT 'Concept' NOT NULL,
    description text,
    properties_json text DEFAULT '{}'::text NOT NULL,
    ontology_id character varying(64),
    ontology_version character varying(128),
    source_resource_id character varying(64),
    source_pk character varying(255),
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system' NOT NULL,
    update_by character varying(100) DEFAULT 'system' NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--

-- ecos_kb_lineage_event (TABLE)
CREATE TABLE ecos_knowledge.ecos_kb_lineage_event (
    id character varying(36) NOT NULL,
    event_id character varying(64) NOT NULL,
    query_text text,
    lineage_format character varying(20),
    nodes_json text DEFAULT '[]'::text NOT NULL,
    edges_json text DEFAULT '[]'::text NOT NULL,
    parse_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system' NOT NULL,
    update_by character varying(100) DEFAULT 'system' NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--

-- ecos_kb_market_asset (TABLE)
CREATE TABLE ecos_knowledge.ecos_kb_market_asset (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    asset_category character varying(64),
    owner_name character varying(100),
    rating numeric(9,6) DEFAULT 0 NOT NULL,
    popularity integer DEFAULT 0 NOT NULL,
    status character varying(20) DEFAULT 'PUBLISHED' NOT NULL,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system' NOT NULL,
    update_by character varying(100) DEFAULT 'system' NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_ecos_kb_mkt_rating CHECK (((rating >= (0)::numeric) AND (rating <= (5)::numeric)))
);

--

-- ecos_kb_ontology_snapshot (TABLE)
CREATE TABLE ecos_knowledge.ecos_kb_ontology_snapshot (
    id character varying(36) NOT NULL,
    ontology_id character varying(64) NOT NULL,
    snap_version character varying(20) NOT NULL,
    entity_codes_json text DEFAULT '[]'::text NOT NULL,
    relationship_codes_json text DEFAULT '[]'::text NOT NULL,
    schema_hash character varying(64),
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system' NOT NULL,
    update_by character varying(100) DEFAULT 'system' NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--

-- ecos_kb_profile (TABLE)
CREATE TABLE ecos_knowledge.ecos_kb_profile (
    id character varying(36) NOT NULL,
    profile_key character varying(255) NOT NULL,
    metric_code character varying(64) NOT NULL,
    group_dims_json text NOT NULL,
    window_from character varying(7) NOT NULL,
    window_to character varying(7) NOT NULL,
    stats_ref character varying(36),
    ci_level character varying(10) DEFAULT '95' NOT NULL,
    stats_method character varying(32) DEFAULT 'T_APPROX' NOT NULL,
    confidence character varying(10) NOT NULL,
    degrade_from character varying(36),
    degrade_path_json text,
    applicable_condition character varying(500),
    source_query_ref character varying(255),
    profile_version character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'DRAFT' NOT NULL,
    approved_by character varying(100),
    approved_time timestamp without time zone,
    task_id character varying(64),
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
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
    CONSTRAINT ck_kbp_conf CHECK (((confidence)::text = ANY ((ARRAY['HIGH', 'MEDIUM', 'LOW'])::text[]))),
    CONSTRAINT ck_kbp_status CHECK (((status)::text = ANY ((ARRAY['DRAFT', 'PUBLISHED', 'SUPERSEDED'])::text[])))
);

--

-- TABLE ecos_kb_profile (COMMENT)
COMMENT ON TABLE ecos_knowledge.ecos_kb_profile IS '历史画像·定义态（REQ-KB-02/F04-06；R-8 ② 拆分：数值统计事实见 ecos_dw.ecos_kb_profile_stats，经 data-engine 写通道）';

--

-- COLUMN ecos_kb_profile.sample_count (COMMENT)
COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.sample_count IS '样本量（画像窗口内参与统计的事实行数；KbProfileDO.sampleCount int 原语 ⇒ NOT NULL DEFAULT 0；ST03-A 逐列登记，派生统计量不加密）';

--

-- COLUMN ecos_kb_profile.missing_rate (COMMENT)
COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.missing_rate IS '缺失率（比率→概率档 NUMERIC(5,4)，承 V176:68 精度；可空=缺统计口径时留 NULL 而非 0，避免把 0 误读为"零缺失"；ST03-A 逐列登记）';

--

-- COLUMN ecos_kb_profile.p10 (COMMENT)
COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.p10 IS '派生统计量 P10（分位数，NUMERIC(18,4) 承 V176:69；ST03-A 逐列登记，不加密不豁免业务金额列规则）';

--

-- COLUMN ecos_kb_profile.p50 (COMMENT)
COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.p50 IS '派生统计量 P50（中位数，NUMERIC(18,4) 承 V176:70；ST03-A 逐列登记）';

--

-- COLUMN ecos_kb_profile.p90 (COMMENT)
COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.p90 IS '派生统计量 P90（分位数，NUMERIC(18,4) 承 V176:71；ST03-A 逐列登记）';

--

-- COLUMN ecos_kb_profile.mean_value (COMMENT)
COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.mean_value IS '派生统计量 均值（NUMERIC(18,4) 承 V176:72；ST03-A 逐列登记）';

--

-- COLUMN ecos_kb_profile.ci_low (COMMENT)
COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.ci_low IS '置信区间下界（CI 由 ci_level/stats_method 决定口径，NUMERIC(18,4) 承 V176:73；ST03-A 逐列登记）';

--

-- COLUMN ecos_kb_profile.ci_high (COMMENT)
COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.ci_high IS '置信区间上界（NUMERIC(18,4) 承 V176:74；ST03-A 逐列登记）';

--

-- ecos_kb_term (TABLE)
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
    definition_version character varying(20) DEFAULT '1' NOT NULL,
    status character varying(20) DEFAULT 'DRAFT' NOT NULL,
    owner_name character varying(100),
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system' NOT NULL,
    update_by character varying(100) DEFAULT 'system' NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_ecos_kb_term_type CHECK (((term_type)::text = ANY ((ARRAY['ENTITY', 'RELATION', 'METRIC', 'FUNCTION', 'CONCEPT'])::text[])))
);

--

-- ecos_kg_sync_log (TABLE)
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
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system' NOT NULL,
    update_by character varying(100) DEFAULT 'system' NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--

-- ecos_knowledge_graph_edge (TABLE)
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

-- TABLE ecos_knowledge_graph_edge (COMMENT)
COMMENT ON TABLE ecos_knowledge.ecos_knowledge_graph_edge IS 'knownLegacy(V184/MC01): 宽度违规存量侧（0 行），新写切 V184 ecos_knowledge.ecos_kb_graph_edge；只停写不 DROP（R-12 ① 前置未闭环）';

--

-- ecos_knowledge_graph_node (TABLE)
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

-- expert_rule (TABLE)
CREATE TABLE ecos_knowledge.expert_rule (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    domain character varying(64),
    rule_type character varying(32) DEFAULT 'IF-THEN',
    condition_expr text,
    action_expr text,
    priority integer DEFAULT 0,
    enabled boolean DEFAULT true,
    description text,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE expert_rule (COMMENT)
COMMENT ON TABLE ecos_knowledge.expert_rule IS '专家规则表(kb-engine)';

--

-- extraction_drafts (TABLE)
CREATE TABLE ecos_knowledge.extraction_drafts (
    id character varying(64) NOT NULL,
    file_name character varying(512),
    file_path character varying(1024),
    status character varying(32) DEFAULT 'UPLOADED' NOT NULL,
    parsed_text text,
    extracted_entities_json text,
    extracted_relations_json text,
    extracted_rules_json text,
    extracted_links_json text,
    rejected_reason text,
    error_msg text,
    retry_count integer DEFAULT 0 NOT NULL,
    file_type character varying(16),
    page_count integer DEFAULT 1,
    char_count integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- graph_edge (TABLE)
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

-- TABLE graph_edge (COMMENT)
COMMENT ON TABLE ecos_knowledge.graph_edge IS '知识图谱边表(kb-engine)';

--

-- COLUMN graph_edge.type (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_edge.type IS '关系类型';

--

-- COLUMN graph_edge.properties (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_edge.properties IS 'V178/MC02 定性: 存量 JSONB 列，读写切 properties_json 后停写（IR03 只加不删，本批不 DROP）';

--

-- COLUMN graph_edge.weight (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_edge.weight IS '关系权重';

--

-- COLUMN graph_edge.ontology_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_edge.ontology_id IS '溯源: 本体业务 ID (V134/B28)';

--

-- COLUMN graph_edge.ontology_version (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_edge.ontology_version IS '溯源: 生成该边时的本体版本 (V134/B28)';

--

-- COLUMN graph_edge.source_resource_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_edge.source_resource_id IS '溯源: 实例抽取来源 DW 层数据资源 ID (V134/B28)';

--

-- COLUMN graph_edge.source_pk (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_edge.source_pk IS '溯源: 实例抽取来源 DW 表主键值 (V134/B28)';

--

-- COLUMN graph_edge.properties_json (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_edge.properties_json IS 'V178/MC02: properties 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）';

--

-- graph_node (TABLE)
CREATE TABLE ecos_knowledge.graph_node (
    id character varying(64) NOT NULL,
    label character varying(64),
    node_type character varying(64) DEFAULT 'Concept',
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
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);

--

-- TABLE graph_node (COMMENT)
COMMENT ON TABLE ecos_knowledge.graph_node IS '知识图谱节点表(kb-engine)';

--

-- COLUMN graph_node.label (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_node.label IS '标签/名称';

--

-- COLUMN graph_node.node_type (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_node.node_type IS '节点类型: Concept/Entity/Event';

--

-- COLUMN graph_node.properties (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_node.properties IS 'V178/MC02 定性: 存量 JSONB 列，读写切 properties_json 后停写（IR03 只加不删，本批不 DROP）';

--

-- COLUMN graph_node.ontology_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_node.ontology_id IS '溯源: 本体业务 ID (V134/B28)';

--

-- COLUMN graph_node.ontology_version (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_node.ontology_version IS '溯源: 生成该节点时的本体版本 (V134/B28)';

--

-- COLUMN graph_node.source_resource_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_node.source_resource_id IS '溯源: 实例抽取来源 DW 层数据资源 ID (V134/B28)';

--

-- COLUMN graph_node.source_pk (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_node.source_pk IS '溯源: 实例抽取来源 DW 表主键值 (V134/B28)';

--

-- COLUMN graph_node.properties_json (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_node.properties_json IS 'V178/MC02: properties 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）';

--

-- graph_subgraph (TABLE)
CREATE TABLE ecos_knowledge.graph_subgraph (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    node_ids jsonb DEFAULT '[]'::jsonb,
    edge_ids jsonb DEFAULT '[]'::jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    node_ids_json text,
    edge_ids_json text,
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

-- TABLE graph_subgraph (COMMENT)
COMMENT ON TABLE ecos_knowledge.graph_subgraph IS '知识图谱子图表(kb-engine)';

--

-- COLUMN graph_subgraph.node_ids (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_subgraph.node_ids IS 'V178/MC02 定性: 存量 JSONB 列，读写切 node_ids_json 后停写（IR03 只加不删，本批不 DROP）';

--

-- COLUMN graph_subgraph.edge_ids (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_subgraph.edge_ids IS 'V178/MC02 定性: 存量 JSONB 列，读写切 edge_ids_json 后停写（IR03 只加不删，本批不 DROP）';

--

-- COLUMN graph_subgraph.node_ids_json (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_subgraph.node_ids_json IS 'V178/MC02: node_ids 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）';

--

-- COLUMN graph_subgraph.edge_ids_json (COMMENT)
COMMENT ON COLUMN ecos_knowledge.graph_subgraph.edge_ids_json IS 'V178/MC02: edge_ids 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）';

--

-- kb_doc (TABLE)
CREATE TABLE ecos_knowledge.kb_doc (
    doc_id character varying(128) NOT NULL,
    source character varying(128),
    original_file_name character varying(512),
    object_key character varying(512),
    content_type character varying(128),
    size_bytes bigint,
    parse_status character varying(16) DEFAULT 'queued' NOT NULL,
    chunk_size integer,
    chunk_overlap integer,
    chunk_count integer DEFAULT 0,
    text_source_path character varying(512),
    error_message text,
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

-- TABLE kb_doc (COMMENT)
COMMENT ON TABLE ecos_knowledge.kb_doc IS 'V186/R-10① 权威载体（唯一）: 知识层非结构化文档级状态机（queued/parsing/extracting/done/failed）。DR 基线列（domain/version_no/is_deleted/审计五列/trace_id）由 V180 补齐；其余两套文档载体（public.extraction_drafts、ecos_dw.doc）自本脚本起定性 legacy 只停写。本表不新建、不迁不删（IR03）';

--

-- COLUMN kb_doc.doc_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_doc.doc_id IS 'V186 登记: 权威文档业务键（VARCHAR(128) 承重旧形态，MC01 新表口径为 VARCHAR(36)）。本批不改列类型（IR03 禁 ALTER 既有列型）；宽度收敛随代码切写另批申报';

--

-- COLUMN kb_doc.object_key (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_doc.object_key IS 'V186/F04-12: MinIO RAW 近源层唯一入口对象 key（raw/unstructured/{source}/{docId}/{originalFileName}）。上传一律走 KnowledgeDocIngestService 合规链；java.io.tmpdir 仅作抽取临时缓冲且必须 TTL 清理，禁终存（K-37/W102）';

--

-- COLUMN kb_doc.parse_status (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_doc.parse_status IS '解析状态: queued/parsing/extracting/done/failed';

--

-- COLUMN kb_doc.text_source_path (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_doc.text_source_path IS 'V186: 解析文本登记为 CURATED 资源后的 source_path（经数据工作台登记端点，非直写 DW 表；ADR-2 A2 永久否决）';

--

-- kb_doc_chunk (TABLE)
CREATE TABLE ecos_knowledge.kb_doc_chunk (
    id character varying(64) NOT NULL,
    doc_id character varying(128) NOT NULL,
    source character varying(128),
    chunk_index integer NOT NULL,
    content text NOT NULL,
    char_start integer,
    char_end integer,
    metadata jsonb DEFAULT '{}'::jsonb,
    status character varying(16) DEFAULT 'active' NOT NULL,
    embedding_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    metadata_json text,
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

-- TABLE kb_doc_chunk (COMMENT)
COMMENT ON TABLE ecos_knowledge.kb_doc_chunk IS 'V186/R-10① 权威载体（唯一）: 知识层切片表（文本+序号+字符偏移+元数据）。A3 过渡语义自本脚本起转为目标态口径（知识层权威），V138「待 A1 落地迁入 DW」的退出计划随 R-10① 作废；切片/向量不得迁入业务域（MC05/R-10 ② 否决理由）';

--

-- COLUMN kb_doc_chunk.doc_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_doc_chunk.doc_id IS 'V186: 权威链切片→文档外键（应用层保证，无跨 schema FK），指向本 schema kb_doc.doc_id';

--

-- COLUMN kb_doc_chunk.metadata (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_doc_chunk.metadata IS 'V186 登记: 存量 JSONB 列，读写已切 V178 的 metadata_json 后本列停写（MC02/IR03，不 DROP）';

--

-- COLUMN kb_doc_chunk.status (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_doc_chunk.status IS '分块状态: active/deprecated';

--

-- COLUMN kb_doc_chunk.embedding_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_doc_chunk.embedding_id IS 'V186: 关联 ecos_knowledge.knowledge_embedding.id（向量权威在知识层，V137/V185 形态 A；降级副本见 ecos_dw.ecos_kb_vector_fallback，非权威）';

--

-- COLUMN kb_doc_chunk.metadata_json (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_doc_chunk.metadata_json IS 'V178/MC02: metadata 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）';

--

-- kb_extract_audit (TABLE)
CREATE TABLE ecos_knowledge.kb_extract_audit (
    id bigint NOT NULL,
    job_id character varying(64) NOT NULL,
    task_id character varying(64),
    tier character varying(32) DEFAULT 'standard' NOT NULL,
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

-- TABLE kb_extract_audit (COMMENT)
COMMENT ON TABLE ecos_knowledge.kb_extract_audit IS 'knownLegacy(MC01/V179): BIGSERIAL 主键，新写切 ecos_knowledge.ecos_* 合规并建新表；本表只停写不迁移不 DROP（R-12 ① 前置未闭环 + IR03）';

--

-- COLUMN kb_extract_audit.trace_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_extract_audit.trace_id IS 'V181/§C.7: 全链路追踪 ID（与审计链/事件总线 ecos.audit 同键），可空（历史行为 NULL）';

--

-- kb_extract_audit_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_knowledge.kb_extract_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- kb_extract_audit_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_knowledge.kb_extract_audit_id_seq OWNED BY ecos_knowledge.kb_extract_audit.id;

--

-- kb_extract_candidate (TABLE)
CREATE TABLE ecos_knowledge.kb_extract_candidate (
    id bigint NOT NULL,
    doc_id character varying(64),
    entity_name character varying(256),
    entity_type character varying(128),
    relation character varying(128),
    subject_id character varying(128),
    object_id character varying(128),
    confidence numeric(5,4),
    status character varying(32) DEFAULT 'PENDING' NOT NULL,
    review_note text,
    ontology_id character varying(64),
    ontology_version character varying(64),
    source_resource_id character varying(128),
    source_pk character varying(256),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1' NOT NULL,
    trace_id character varying(64)
);

--

-- TABLE kb_extract_candidate (COMMENT)
COMMENT ON TABLE ecos_knowledge.kb_extract_candidate IS 'knownLegacy(MC01/V179): BIGSERIAL 主键，新写切 ecos_knowledge.ecos_* 合规并建新表；本表只停写不迁移不 DROP（R-12 ① 前置未闭环 + IR03）';

--

-- COLUMN kb_extract_candidate.doc_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.doc_id IS '关联文档 ID（上传临时文件的 fileId，同 extraction_drafts.id）';

--

-- COLUMN kb_extract_candidate.entity_name (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.entity_name IS 'LLM 抽取的实体名称（自然语言）';

--

-- COLUMN kb_extract_candidate.entity_type (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.entity_type IS 'LLM 推断的实体类型（可选映射到本体实体 code）';

--

-- COLUMN kb_extract_candidate.relation (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.relation IS 'LLM 抽取的关系 code（可选映射到本体关系 code）';

--

-- COLUMN kb_extract_candidate.subject_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.subject_id IS '关系源节点 ID（无关系时为空）';

--

-- COLUMN kb_extract_candidate.object_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.object_id IS '关系目标节点 ID（无关系时为空）';

--

-- COLUMN kb_extract_candidate.confidence (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.confidence IS 'LLM 置信度（0.0000 - 1.0000）';

--

-- COLUMN kb_extract_candidate.status (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.status IS '候选状态：PENDING / APPROVED / REJECTED / MERGED';

--

-- COLUMN kb_extract_candidate.review_note (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.review_note IS '审核意见 / LLM 解释';

--

-- COLUMN kb_extract_candidate.ontology_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.ontology_id IS '关联本体业务 ID（同 graph_node.ontology_id，溯源对齐）';

--

-- COLUMN kb_extract_candidate.ontology_version (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.ontology_version IS '关联本体版本（同 graph_node.ontology_version，溯源对齐）';

--

-- COLUMN kb_extract_candidate.source_resource_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.source_resource_id IS '来源资源 ID（上传文档登记 ID，溯源对齐）';

--

-- COLUMN kb_extract_candidate.source_pk (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.source_pk IS '来源主键（同 graph_node.source_pk，溯源对齐）';

--

-- kb_extract_candidate_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_knowledge.kb_extract_candidate_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- kb_extract_candidate_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_knowledge.kb_extract_candidate_id_seq OWNED BY ecos_knowledge.kb_extract_candidate.id;

--

-- kb_extract_watermark (TABLE)
CREATE TABLE ecos_knowledge.kb_extract_watermark (
    id bigint NOT NULL,
    ontology_id character varying(64) NOT NULL,
    entity_code character varying(100) NOT NULL,
    resource_id character varying(64) NOT NULL,
    watermark character varying(255),
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

-- TABLE kb_extract_watermark (COMMENT)
COMMENT ON TABLE ecos_knowledge.kb_extract_watermark IS 'knownLegacy(MC01/V179): BIGSERIAL 主键，新写切 ecos_knowledge.ecos_* 合规并建新表；本表只停写不迁移不 DROP（R-12 ① 前置未闭环 + IR03）';

--

-- COLUMN kb_extract_watermark.ontology_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_extract_watermark.ontology_id IS '本体业务 ID（kb_ontology_snapshot.ontology_id）';

--

-- COLUMN kb_extract_watermark.entity_code (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_extract_watermark.entity_code IS '本体实体 code（ecos_entity_table_mapping.entity_code）';

--

-- COLUMN kb_extract_watermark.resource_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_extract_watermark.resource_id IS 'DW 层数据资源 ID（td_data_resource.resource_id，只读消费）';

--

-- COLUMN kb_extract_watermark.watermark (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_extract_watermark.watermark IS '上次抽取收敛水位（DW 行读取端点的 nextWatermark）';

--

-- kb_extract_watermark_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_knowledge.kb_extract_watermark_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- kb_extract_watermark_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_knowledge.kb_extract_watermark_id_seq OWNED BY ecos_knowledge.kb_extract_watermark.id;

--

-- kb_nav_article_rel (TABLE)
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
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    trace_id character varying(64),
    CONSTRAINT kb_nav_article_rel_scope_check CHECK (((scope)::text = ANY ((ARRAY['category', 'tag'])::text[])))
);

--

-- TABLE kb_nav_article_rel (COMMENT)
COMMENT ON TABLE ecos_knowledge.kb_nav_article_rel IS '知识导航·文章 ↔ (目录|标签) 多对多关联 — scope 字段区分 category/tag 两类节点';

--

-- COLUMN kb_nav_article_rel.article_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_nav_article_rel.article_id IS '关联 knowledge_article.id（VARCHAR 64，弱 FK 不强制约束）';

--

-- COLUMN kb_nav_article_rel.node_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_nav_article_rel.node_id IS '节点 ID：scope=category → kb_nav_category.id；scope=tag → kb_nav_tag.id';

--

-- COLUMN kb_nav_article_rel.scope (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_nav_article_rel.scope IS '关联维度：category 或 tag（CHECK 约束）';

--

-- kb_nav_article_rel_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_knowledge.kb_nav_article_rel_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- kb_nav_article_rel_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_knowledge.kb_nav_article_rel_id_seq OWNED BY ecos_knowledge.kb_nav_article_rel.id;

--

-- kb_nav_category (TABLE)
CREATE TABLE ecos_knowledge.kb_nav_category (
    id character varying(36) DEFAULT gen_random_uuid() NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    parent_id character varying(36),
    name character varying(128) NOT NULL,
    path character varying(512),
    level smallint DEFAULT 1 NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64),
    update_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64),
    CONSTRAINT kb_nav_category_level_check CHECK (((level >= 1) AND (level <= 3)))
);

--

-- TABLE kb_nav_category (COMMENT)
COMMENT ON TABLE ecos_knowledge.kb_nav_category IS '知识导航·业务目录树（≤3 级含叶子）— kb-engine 自有表，与 data 分级分类完全解耦（不带 level_code）';

--

-- COLUMN kb_nav_category.domain (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_nav_category.domain IS '多租户预留（DR08）— NOT NULL DEFAULT default';

--

-- COLUMN kb_nav_category.parent_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_nav_category.parent_id IS '父目录 ID；根目录 = NULL';

--

-- COLUMN kb_nav_category.path (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_nav_category.path IS '从根到本节点的路径（/name1/name2/...），冗余提升子树查询性能';

--

-- COLUMN kb_nav_category.level (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_nav_category.level IS '层级 1/2/3（1 = 域下一级目录，3 = 叶子极限）';

--

-- COLUMN kb_nav_category.sort_order (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_nav_category.sort_order IS '同级排序（小者先）';

--

-- COLUMN kb_nav_category.version_no (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_nav_category.version_no IS '乐观锁版本号（V150 DR07 规范）';

--

-- kb_nav_tag (TABLE)
CREATE TABLE ecos_knowledge.kb_nav_tag (
    id character varying(36) DEFAULT gen_random_uuid() NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    tag_name character varying(128) NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64),
    update_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);

--

-- TABLE kb_nav_tag (COMMENT)
COMMENT ON TABLE ecos_knowledge.kb_nav_tag IS '知识导航·标签字典 — 文章多对多关联（kb_nav_article_rel, scope=tag）';

--

-- COLUMN kb_nav_tag.domain (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_nav_tag.domain IS '多租户预留（DR08）— NOT NULL DEFAULT default';

--

-- COLUMN kb_nav_tag.tag_name (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_nav_tag.tag_name IS '标签名（同 domain 下唯一）';

--

-- kb_scheduled_extract (TABLE)
CREATE TABLE ecos_knowledge.kb_scheduled_extract (
    id bigint NOT NULL,
    schedule_id character varying(64) NOT NULL,
    name character varying(128) NOT NULL,
    ontology_ids jsonb NOT NULL,
    mode character varying(32) DEFAULT 'INCREMENTAL' NOT NULL,
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
    prefers_runtime_task character varying(64) DEFAULT 'td_runtime_task_plan' NOT NULL,
    ontology_ids_json text,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1' NOT NULL,
    trace_id character varying(64)
);

--

-- TABLE kb_scheduled_extract (COMMENT)
COMMENT ON TABLE ecos_knowledge.kb_scheduled_extract IS 'DEPRECATED (V153, 2026-09-22, TB-1): 由 td_runtime_task_plan + ITaskManagementService 代替; 历史行只读不写; 数据修复走一次性脚本; R9 只加不删';

--

-- COLUMN kb_scheduled_extract.ontology_ids (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_scheduled_extract.ontology_ids IS 'V178/MC02 定性: 存量 JSONB 列，读写切 ontology_ids_json 后停写（IR03 只加不删，本批不 DROP）';

--

-- COLUMN kb_scheduled_extract.ontology_ids_json (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kb_scheduled_extract.ontology_ids_json IS 'V178/MC02: ontology_ids 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）';

--

-- kb_scheduled_extract_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_knowledge.kb_scheduled_extract_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- kb_scheduled_extract_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_knowledge.kb_scheduled_extract_id_seq OWNED BY ecos_knowledge.kb_scheduled_extract.id;

--

-- kg_sync_log (TABLE)
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

-- TABLE kg_sync_log (COMMENT)
COMMENT ON TABLE ecos_knowledge.kg_sync_log IS 'knownLegacy(MC01/V179): BIGSERIAL 主键，新写切 ecos_knowledge.ecos_* 合规并建新表；本表只停写不迁移不 DROP（R-12 ① 前置未闭环 + IR03）';

--

-- COLUMN kg_sync_log.report (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kg_sync_log.report IS 'V178/MC02 定性: 存量 JSONB 列，读写切 report_json 后停写（IR03 只加不删，本批不 DROP）';

--

-- COLUMN kg_sync_log.report_json (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kg_sync_log.report_json IS 'V178/MC02: report 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）';

--

-- COLUMN kg_sync_log.trace_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.kg_sync_log.trace_id IS 'V181/§C.7: 全链路追踪 ID（与审计链/事件总线 ecos.audit 同键），可空（历史行为 NULL）';

--

-- kg_sync_log_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_knowledge.kg_sync_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- kg_sync_log_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_knowledge.kg_sync_log_id_seq OWNED BY ecos_knowledge.kg_sync_log.id;

--

-- knowledge_article (TABLE)
CREATE TABLE ecos_knowledge.knowledge_article (
    id character varying(64) NOT NULL,
    title character varying(255) NOT NULL,
    content text,
    source character varying(512),
    source_type character varying(64),
    domain character varying(64),
    category character varying(64),
    tags jsonb DEFAULT '[]'::jsonb,
    status character varying(16) DEFAULT 'ACTIVE',
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    tags_json text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64),
    knowledge_category character varying(32),
    rule_ref character varying(64),
    CONSTRAINT ck_knowledge_article_category CHECK (((knowledge_category IS NULL) OR ((knowledge_category)::text = ANY ((ARRAY['Fact', 'Concept', 'Procedure', 'Rule', 'Case', 'Evidence', 'Pattern'])::text[]))))
);

--

-- TABLE knowledge_article (COMMENT)
COMMENT ON TABLE ecos_knowledge.knowledge_article IS '知识文章表(kb-engine)';

--

-- COLUMN knowledge_article.source_type (COMMENT)
COMMENT ON COLUMN ecos_knowledge.knowledge_article.source_type IS 'legacy-orphan(V182): 与 source 列语义重叠，Mapper 零读写 —— 待 Mapper 使用或标停用（卷04 §E.3 V182）；IR03 不 DROP';

--

-- COLUMN knowledge_article.tags (COMMENT)
COMMENT ON COLUMN ecos_knowledge.knowledge_article.tags IS 'legacy-orphan 双身份(V182): JSONB 读写已由 V178 切 tags_json 双写（2026-09-30 校订：过渡列后缀由 _text 收口为 DR04 的 _json）；Mapper 若弃用则整列停用登记 —— 待 Mapper 使用或标停用；IR03 不 DROP';

--

-- COLUMN knowledge_article.tags_json (COMMENT)
COMMENT ON COLUMN ecos_knowledge.knowledge_article.tags_json IS 'V178/MC02: tags 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）';

--

-- COLUMN knowledge_article.knowledge_category (COMMENT)
COMMENT ON COLUMN ecos_knowledge.knowledge_article.knowledge_category IS 'R11/F-KB-18 知识对象七分类封闭枚举：Fact/Concept/Procedure/Rule/Case/Evidence/Pattern（受控新增须改 CHECK + 枚举）';

--

-- COLUMN knowledge_article.rule_ref (COMMENT)
COMMENT ON COLUMN ecos_knowledge.knowledge_article.rule_ref IS 'Rule 类知识语义根：ecos_ontology_rule.id（rule_carrier=KNOWLEDGE_ASSET）；非 Rule 类必须为空';

--

-- knowledge_embedding (TABLE)
CREATE TABLE ecos_knowledge.knowledge_embedding (
    id character varying(64) NOT NULL,
    document_id character varying(64),
    article_id character varying(64),
    chunk_index integer,
    chunk_text text,
    content text,
    embedding jsonb,
    model character varying(128),
    embedding_model character varying(128) DEFAULT 'bge-small-zh-v1.5',
    token_count integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    embedding_vec public.vector(1536),
    embedding_json text,
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

-- TABLE knowledge_embedding (COMMENT)
COMMENT ON TABLE ecos_knowledge.knowledge_embedding IS '知识向量嵌入表(kb-engine)';

--

-- COLUMN knowledge_embedding.article_id (COMMENT)
COMMENT ON COLUMN ecos_knowledge.knowledge_embedding.article_id IS 'legacy-orphan(V182): 与 document_id 语义重叠且 Mapper 零读写 —— 待 Mapper 使用或标停用（卷04 §E.3 V182 / F04-08）；IR03 不 DROP';

--

-- COLUMN knowledge_embedding.chunk_text (COMMENT)
COMMENT ON COLUMN ecos_knowledge.knowledge_embedding.chunk_text IS 'legacy-orphan(V182): 正文冗余列，Mapper 零读写，正源=kb_doc_chunk.content —— 待 Mapper 使用或标停用（卷04 §E.3 V182）；IR03 不 DROP';

--

-- COLUMN knowledge_embedding.embedding (COMMENT)
COMMENT ON COLUMN ecos_knowledge.knowledge_embedding.embedding IS 'V178/MC02 定性: 存量 JSONB 列，读写切 embedding_json 后停写（IR03 只加不删，本批不 DROP）';

--

-- COLUMN knowledge_embedding.model (COMMENT)
COMMENT ON COLUMN ecos_knowledge.knowledge_embedding.model IS 'legacy-orphan(V182): 与 embedding_model 语义重叠且 Mapper 零读写 —— 待 Mapper 使用或标停用（卷04 §E.3 V182）；IR03 不 DROP';

--

-- COLUMN knowledge_embedding.embedding_vec (COMMENT)
COMMENT ON COLUMN ecos_knowledge.knowledge_embedding.embedding_vec IS '形态A(V185 自动化固化的 V137 列): pgvector 1536 维；未启用档由应用探测切 ecos_dw.ecos_kb_vector_fallback（形态B），vectorDegraded 显式标记';

--

-- COLUMN knowledge_embedding.embedding_json (COMMENT)
COMMENT ON COLUMN ecos_knowledge.knowledge_embedding.embedding_json IS 'V178/MC02: embedding 的 TEXT 形态（双写过渡期；读写切本列后旧 jsonb 列停写）';

--

-- v_kb_doc_carrier_registry (VIEW)
CREATE VIEW ecos_knowledge.v_kb_doc_carrier_registry AS
 SELECT carrier_schema,
    carrier_table,
    carrier_role,
    is_authoritative,
    write_authority_schema,
    converge_action
   FROM ( VALUES ('ecos_knowledge'::text,'kb_doc'::text,'文档级状态机'::text,1,'ecos_knowledge'::text,'权威保留（本批零结构变更，V180 补 DR 基线列）'::text), ('ecos_knowledge'::text,'kb_doc_chunk'::text,'切片'::text,1,'ecos_knowledge'::text,'权威保留（metadata→metadata_json 双写，V178）'::text), ('ecos_knowledge'::text,'extraction_drafts'::text,'抽取草稿'::text,0,'ecos_knowledge'::text,'降为草稿态：只承抽取中间产物，禁作文档库写入；文档/切片读写切 kb_doc*'::text), ('ecos_dw'::text,'doc'::text,'演示遗留文档表'::text,0,'ecos_knowledge'::text,'停写：V139 "A1 目标态" 口径被 R-10① 覆写为演示遗留；不 DROP（IR03）'::text), ('ecos_dw'::text,'doc_chunk'::text,'演示遗留切片表'::text,0,'ecos_knowledge'::text,'停写：同上；metadata 存量 JSONB 列随停写一并冻结'::text), ('ecos_knowledge'::text,'knowledge_embedding'::text,'向量载体'::text,0,'ecos_knowledge'::text,'向量权威在知识层（V137/V185 形态 A），非文档载体故标 0；降级副本 ecos_dw.ecos_kb_vector_fallback 亦非权威'::text), ('public'::text,'ecos_knowledge_document'::text,'双镜像文档表'::text,0,'ecos_knowledge'::text,'K-17 双镜像 6 对之一，由 V183 镜像登记表 + V184 ecos_kb_document 处理，本视图仅占位供对账'::text)) r(carrier_schema, carrier_table, carrier_role, is_authoritative, write_authority_schema, converge_action);

--

-- VIEW v_kb_doc_carrier_registry (COMMENT)
COMMENT ON VIEW ecos_knowledge.v_kb_doc_carrier_registry IS 'V186/R-10①: 文档与切片载体登记表（三→一 唯一权威的可断言口径）。is_authoritative=1 仅 kb_doc/kb_doc_chunk 两行；门禁 DocCarrierConvergenceTest#singleAuthoritativeDocTable 据此 + information_schema 对账。视图只读、不写、无数据搬移';

--

-- v_kb_mirror_registry (VIEW)
CREATE VIEW ecos_knowledge.v_kb_mirror_registry AS
 SELECT table_name,
    write_authority_schema,
    legacy_public_shape,
    converge_to
   FROM ( VALUES ('ecos_glossary_term'::text,'ecos_knowledge'::text,'public（V7/V143 系，BIGSERIAL+text[]）'::text,'V184 ecos_knowledge.ecos_kb_term'::text), ('ecos_knowledge_document'::text,'ecos_knowledge'::text,'public（v1 时代实表，宽度待对账）'::text,'V184 ecos_knowledge.ecos_kb_document'::text), ('ecos_knowledge_graph_node'::text,'ecos_knowledge'::text,'public（V108，id VARCHAR(64)+JSONB）'::text,'V184 ecos_knowledge.ecos_kb_graph_node'::text), ('ecos_knowledge_graph_edge'::text,'ecos_knowledge'::text,'public（live 存在，单源无 DDL）'::text,'V184 ecos_knowledge.ecos_kb_graph_edge'::text), ('ecos_marketplace_asset'::text,'ecos_knowledge'::text,'public（V7，BIGSERIAL）'::text,'V184 ecos_knowledge.ecos_kb_market_asset'::text), ('ecos_marketplace_access_request'::text,'ecos_knowledge'::text,'public（V7，BIGSERIAL；文档名 access_request）'::text,'V184 ecos_knowledge.ecos_kb_access_request'::text)) reg(table_name, write_authority_schema, legacy_public_shape, converge_to);

--

-- VIEW v_kb_mirror_registry (COMMENT)
COMMENT ON VIEW ecos_knowledge.v_kb_mirror_registry IS 'V183 双镜像切换登记台账（K-17 6 对）：write_authority=新写唯一侧（schema 限定）；public 侧只停写不迁（R-1 a）；converge_to=PK 宽度/形态违规对的合规新表（V184）';

--

-- kb_extract_audit id (DEFAULT)
ALTER TABLE ONLY ecos_knowledge.kb_extract_audit ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.kb_extract_audit_id_seq'::regclass);

--

-- kb_extract_candidate id (DEFAULT)
ALTER TABLE ONLY ecos_knowledge.kb_extract_candidate ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.kb_extract_candidate_id_seq'::regclass);

--

-- kb_extract_watermark id (DEFAULT)
ALTER TABLE ONLY ecos_knowledge.kb_extract_watermark ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.kb_extract_watermark_id_seq'::regclass);

--

-- kb_nav_article_rel id (DEFAULT)
ALTER TABLE ONLY ecos_knowledge.kb_nav_article_rel ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.kb_nav_article_rel_id_seq'::regclass);

--

-- kb_scheduled_extract id (DEFAULT)
ALTER TABLE ONLY ecos_knowledge.kb_scheduled_extract ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.kb_scheduled_extract_id_seq'::regclass);

--

-- kg_sync_log id (DEFAULT)
ALTER TABLE ONLY ecos_knowledge.kg_sync_log ALTER COLUMN id SET DEFAULT nextval('ecos_knowledge.kg_sync_log_id_seq'::regclass);

--

-- ecos_knowledge_graph_edge ecos_knowledge_graph_edge_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_knowledge_graph_edge
    ADD CONSTRAINT ecos_knowledge_graph_edge_pkey PRIMARY KEY (id);

--

-- ecos_knowledge_graph_node ecos_knowledge_graph_node_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_knowledge_graph_node
    ADD CONSTRAINT ecos_knowledge_graph_node_pkey PRIMARY KEY (id);

--

-- expert_rule expert_rule_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.expert_rule
    ADD CONSTRAINT expert_rule_pkey PRIMARY KEY (id);

--

-- extraction_drafts extraction_drafts_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.extraction_drafts
    ADD CONSTRAINT extraction_drafts_pkey PRIMARY KEY (id);

--

-- graph_edge graph_edge_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.graph_edge
    ADD CONSTRAINT graph_edge_pkey PRIMARY KEY (id);

--

-- graph_node graph_node_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.graph_node
    ADD CONSTRAINT graph_node_pkey PRIMARY KEY (id);

--

-- graph_subgraph graph_subgraph_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.graph_subgraph
    ADD CONSTRAINT graph_subgraph_pkey PRIMARY KEY (id);

--

-- kb_doc_chunk kb_doc_chunk_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.kb_doc_chunk
    ADD CONSTRAINT kb_doc_chunk_pkey PRIMARY KEY (id);

--

-- kb_doc kb_doc_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.kb_doc
    ADD CONSTRAINT kb_doc_pkey PRIMARY KEY (doc_id);

--

-- kb_extract_audit kb_extract_audit_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.kb_extract_audit
    ADD CONSTRAINT kb_extract_audit_pkey PRIMARY KEY (id);

--

-- kb_extract_candidate kb_extract_candidate_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.kb_extract_candidate
    ADD CONSTRAINT kb_extract_candidate_pkey PRIMARY KEY (id);

--

-- kb_extract_watermark kb_extract_watermark_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.kb_extract_watermark
    ADD CONSTRAINT kb_extract_watermark_pkey PRIMARY KEY (id);

--

-- kb_nav_article_rel kb_nav_article_rel_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.kb_nav_article_rel
    ADD CONSTRAINT kb_nav_article_rel_pkey PRIMARY KEY (id);

--

-- kb_nav_category kb_nav_category_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.kb_nav_category
    ADD CONSTRAINT kb_nav_category_pkey PRIMARY KEY (id);

--

-- kb_nav_tag kb_nav_tag_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.kb_nav_tag
    ADD CONSTRAINT kb_nav_tag_pkey PRIMARY KEY (id);

--

-- kb_scheduled_extract kb_scheduled_extract_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.kb_scheduled_extract
    ADD CONSTRAINT kb_scheduled_extract_pkey PRIMARY KEY (id);

--

-- kb_scheduled_extract kb_scheduled_extract_schedule_id_key (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.kb_scheduled_extract
    ADD CONSTRAINT kb_scheduled_extract_schedule_id_key UNIQUE (schedule_id);

--

-- kg_sync_log kg_sync_log_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.kg_sync_log
    ADD CONSTRAINT kg_sync_log_pkey PRIMARY KEY (id);

--

-- knowledge_article knowledge_article_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.knowledge_article
    ADD CONSTRAINT knowledge_article_pkey PRIMARY KEY (id);

--

-- knowledge_embedding knowledge_embedding_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.knowledge_embedding
    ADD CONSTRAINT knowledge_embedding_pkey PRIMARY KEY (id);

--

-- ecos_kb_access_request pk_ecos_kb_access_request (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_kb_access_request
    ADD CONSTRAINT pk_ecos_kb_access_request PRIMARY KEY (id);

--

-- ecos_kb_assumption pk_ecos_kb_assumption (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_kb_assumption
    ADD CONSTRAINT pk_ecos_kb_assumption PRIMARY KEY (id);

--

-- ecos_kb_document pk_ecos_kb_document (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_kb_document
    ADD CONSTRAINT pk_ecos_kb_document PRIMARY KEY (id);

--

-- ecos_kb_extract_audit pk_ecos_kb_extract_audit (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_kb_extract_audit
    ADD CONSTRAINT pk_ecos_kb_extract_audit PRIMARY KEY (id);

--

-- ecos_kb_extract_candidate pk_ecos_kb_extract_candidate (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_kb_extract_candidate
    ADD CONSTRAINT pk_ecos_kb_extract_candidate PRIMARY KEY (id);

--

-- ecos_kb_extract_watermark pk_ecos_kb_extract_watermark (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_kb_extract_watermark
    ADD CONSTRAINT pk_ecos_kb_extract_watermark PRIMARY KEY (id);

--

-- ecos_kb_graph_edge pk_ecos_kb_graph_edge (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_kb_graph_edge
    ADD CONSTRAINT pk_ecos_kb_graph_edge PRIMARY KEY (id);

--

-- ecos_kb_graph_node pk_ecos_kb_graph_node (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_kb_graph_node
    ADD CONSTRAINT pk_ecos_kb_graph_node PRIMARY KEY (id);

--

-- ecos_kb_lineage_event pk_ecos_kb_lineage_event (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_kb_lineage_event
    ADD CONSTRAINT pk_ecos_kb_lineage_event PRIMARY KEY (id);

--

-- ecos_kb_market_asset pk_ecos_kb_market_asset (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_kb_market_asset
    ADD CONSTRAINT pk_ecos_kb_market_asset PRIMARY KEY (id);

--

-- ecos_kb_ontology_snapshot pk_ecos_kb_ontology_snapshot (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_kb_ontology_snapshot
    ADD CONSTRAINT pk_ecos_kb_ontology_snapshot PRIMARY KEY (id);

--

-- ecos_kb_profile pk_ecos_kb_profile (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_kb_profile
    ADD CONSTRAINT pk_ecos_kb_profile PRIMARY KEY (id);

--

-- ecos_kb_term pk_ecos_kb_term (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_kb_term
    ADD CONSTRAINT pk_ecos_kb_term PRIMARY KEY (id);

--

-- ecos_kg_sync_log pk_ecos_kg_sync_log (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_kg_sync_log
    ADD CONSTRAINT pk_ecos_kg_sync_log PRIMARY KEY (id);

--

-- ecos_kb_extract_watermark uniq_ecos_kb_extract_wm (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_kb_extract_watermark
    ADD CONSTRAINT uniq_ecos_kb_extract_wm UNIQUE (ontology_id, entity_code, resource_id, is_deleted);

--

-- ecos_kb_lineage_event uniq_ecos_kb_lineage_evt (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_kb_lineage_event
    ADD CONSTRAINT uniq_ecos_kb_lineage_evt UNIQUE (event_id, is_deleted);

--

-- ecos_kb_ontology_snapshot uniq_ecos_kb_onto_snap (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_kb_ontology_snapshot
    ADD CONSTRAINT uniq_ecos_kb_onto_snap UNIQUE (ontology_id, snap_version, is_deleted);

--

-- ecos_kb_term uniq_ecos_kb_term_name (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.ecos_kb_term
    ADD CONSTRAINT uniq_ecos_kb_term_name UNIQUE (domain, subject_domain, name, is_deleted);

--

-- kb_extract_watermark uq_kb_extract_watermark (CONSTRAINT)
ALTER TABLE ONLY ecos_knowledge.kb_extract_watermark
    ADD CONSTRAINT uq_kb_extract_watermark UNIQUE (ontology_id, entity_code, resource_id);

--

-- idx_ecos_kb_areq_asset (INDEX)
CREATE INDEX idx_ecos_kb_areq_asset ON ecos_knowledge.ecos_kb_access_request USING btree (asset_id, status, is_deleted);

--

-- idx_ecos_kb_gedge_dst (INDEX)
CREATE INDEX idx_ecos_kb_gedge_dst ON ecos_knowledge.ecos_kb_graph_edge USING btree (target_node_id, edge_type, is_deleted);

--

-- idx_ecos_kb_gedge_src (INDEX)
CREATE INDEX idx_ecos_kb_gedge_src ON ecos_knowledge.ecos_kb_graph_edge USING btree (source_node_id, edge_type, is_deleted);

--

-- idx_ecos_kb_gnode_onto (INDEX)
CREATE INDEX idx_ecos_kb_gnode_onto ON ecos_knowledge.ecos_kb_graph_node USING btree (ontology_id, ontology_version, is_deleted);

--

-- idx_ecos_kb_gnode_type (INDEX)
CREATE INDEX idx_ecos_kb_gnode_type ON ecos_knowledge.ecos_kb_graph_node USING btree (node_type, domain, is_deleted);

--

-- idx_ecos_kb_lineage_parse (INDEX)
CREATE INDEX idx_ecos_kb_lineage_parse ON ecos_knowledge.ecos_kb_lineage_event USING btree (parse_time, lineage_format, is_deleted);

--

-- idx_ecos_kb_mkt_cat (INDEX)
CREATE INDEX idx_ecos_kb_mkt_cat ON ecos_knowledge.ecos_kb_market_asset USING btree (asset_category, status, is_deleted);

--

-- idx_ecos_kb_onto_snap_dom (INDEX)
CREATE INDEX idx_ecos_kb_onto_snap_dom ON ecos_knowledge.ecos_kb_ontology_snapshot USING btree (domain, is_deleted);

--

-- idx_ecos_kb_term_type (INDEX)
CREATE INDEX idx_ecos_kb_term_type ON ecos_knowledge.ecos_kb_term USING btree (term_type, status, is_deleted);

--

-- idx_ecos_kbeat_job (INDEX)
CREATE INDEX idx_ecos_kbeat_job ON ecos_knowledge.ecos_kb_extract_audit USING btree (job_id, create_time);

--

-- idx_ecos_kbeic_doc (INDEX)
CREATE INDEX idx_ecos_kbeic_doc ON ecos_knowledge.ecos_kb_extract_candidate USING btree (doc_id, is_deleted);

--

-- idx_ecos_kbeic_status (INDEX)
CREATE INDEX idx_ecos_kbeic_status ON ecos_knowledge.ecos_kb_extract_candidate USING btree (status, is_deleted);

--

-- idx_ecos_kg_sync_log_dom (INDEX)
CREATE INDEX idx_ecos_kg_sync_log_dom ON ecos_knowledge.ecos_kg_sync_log USING btree (domain, status, is_deleted);

--

-- idx_ecos_kg_sync_log_job (INDEX)
CREATE INDEX idx_ecos_kg_sync_log_job ON ecos_knowledge.ecos_kg_sync_log USING btree (job_id, create_time);

--

-- idx_expert_rule_domain (INDEX)
CREATE INDEX idx_expert_rule_domain ON ecos_knowledge.expert_rule USING btree (domain);

--

-- idx_expert_rule_type (INDEX)
CREATE INDEX idx_expert_rule_type ON ecos_knowledge.expert_rule USING btree (rule_type);

--

-- idx_graph_edge_ontology_version (INDEX)
CREATE INDEX idx_graph_edge_ontology_version ON ecos_knowledge.graph_edge USING btree (ontology_id, ontology_version);

--

-- idx_graph_edge_source (INDEX)
CREATE INDEX idx_graph_edge_source ON ecos_knowledge.graph_edge USING btree (source_id);

--

-- idx_graph_edge_target (INDEX)
CREATE INDEX idx_graph_edge_target ON ecos_knowledge.graph_edge USING btree (target_id);

--

-- idx_graph_edge_type (INDEX)
CREATE INDEX idx_graph_edge_type ON ecos_knowledge.graph_edge USING btree (type);

--

-- idx_graph_node_label (INDEX)
CREATE INDEX idx_graph_node_label ON ecos_knowledge.graph_node USING btree (label);

--

-- idx_graph_node_label_trgm (INDEX)
CREATE INDEX idx_graph_node_label_trgm ON ecos_knowledge.graph_node USING gin (label public.gin_trgm_ops);

--

-- idx_graph_node_node_type (INDEX)
CREATE INDEX idx_graph_node_node_type ON ecos_knowledge.graph_node USING btree (node_type);

--

-- idx_graph_node_ontology_version (INDEX)
CREATE INDEX idx_graph_node_ontology_version ON ecos_knowledge.graph_node USING btree (ontology_id, ontology_version);

--

-- idx_kb_art_domain (INDEX)
CREATE INDEX idx_kb_art_domain ON ecos_knowledge.knowledge_article USING btree (domain);

--

-- idx_kb_art_status (INDEX)
CREATE INDEX idx_kb_art_status ON ecos_knowledge.knowledge_article USING btree (status);

--

-- idx_kb_doc_chunk_doc (INDEX)
CREATE INDEX idx_kb_doc_chunk_doc ON ecos_knowledge.kb_doc_chunk USING btree (doc_id);

--

-- idx_kb_doc_chunk_status (INDEX)
CREATE INDEX idx_kb_doc_chunk_status ON ecos_knowledge.kb_doc_chunk USING btree (status);

--

-- idx_kb_doc_parse_status (INDEX)
CREATE INDEX idx_kb_doc_parse_status ON ecos_knowledge.kb_doc USING btree (parse_status);

--

-- idx_kb_emb_doc (INDEX)
CREATE INDEX idx_kb_emb_doc ON ecos_knowledge.knowledge_embedding USING btree (document_id);

--

-- idx_kb_extract_audit_trace_id (INDEX)
CREATE INDEX idx_kb_extract_audit_trace_id ON ecos_knowledge.kb_extract_audit USING btree (trace_id);

--

-- idx_kb_extract_watermark_ontology (INDEX)
CREATE INDEX idx_kb_extract_watermark_ontology ON ecos_knowledge.kb_extract_watermark USING btree (ontology_id);

--

-- idx_kb_gedge_src (INDEX)
CREATE INDEX idx_kb_gedge_src ON ecos_knowledge.graph_edge USING btree (source_id);

--

-- idx_kb_gedge_tgt (INDEX)
CREATE INDEX idx_kb_gedge_tgt ON ecos_knowledge.graph_edge USING btree (target_id);

--

-- idx_kb_gedge_type (INDEX)
CREATE INDEX idx_kb_gedge_type ON ecos_knowledge.graph_edge USING btree (type);

--

-- idx_kb_gnode_domain (INDEX)
CREATE INDEX idx_kb_gnode_domain ON ecos_knowledge.graph_node USING btree (domain);

--

-- idx_kb_gnode_label (INDEX)
CREATE INDEX idx_kb_gnode_label ON ecos_knowledge.graph_node USING btree (label);

--

-- idx_kb_kge_src (INDEX)
CREATE INDEX idx_kb_kge_src ON ecos_knowledge.ecos_knowledge_graph_edge USING btree (source_node_id);

--

-- idx_kb_kge_tgt (INDEX)
CREATE INDEX idx_kb_kge_tgt ON ecos_knowledge.ecos_knowledge_graph_edge USING btree (target_node_id);

--

-- idx_kb_nav_category_domain_parent (INDEX)
CREATE INDEX idx_kb_nav_category_domain_parent ON ecos_knowledge.kb_nav_category USING btree (domain, parent_id, is_deleted);

--

-- idx_kb_rule_domain (INDEX)
CREATE INDEX idx_kb_rule_domain ON ecos_knowledge.expert_rule USING btree (domain);

--

-- idx_kb_rule_type (INDEX)
CREATE INDEX idx_kb_rule_type ON ecos_knowledge.expert_rule USING btree (rule_type);

--

-- idx_kba_expire (INDEX)
CREATE INDEX idx_kba_expire ON ecos_knowledge.ecos_kb_assumption USING btree (expire_at, status);

--

-- idx_kba_scenario_status (INDEX)
CREATE INDEX idx_kba_scenario_status ON ecos_knowledge.ecos_kb_assumption USING btree (scenario_type, status, is_deleted);

--

-- idx_kbea_job (INDEX)
CREATE INDEX idx_kbea_job ON ecos_knowledge.kb_extract_audit USING btree (job_id);

--

-- idx_kbea_ts (INDEX)
CREATE INDEX idx_kbea_ts ON ecos_knowledge.kb_extract_audit USING btree (created_at DESC);

--

-- idx_kbeic_doc (INDEX)
CREATE INDEX idx_kbeic_doc ON ecos_knowledge.kb_extract_candidate USING btree (doc_id);

--

-- idx_kbeic_status (INDEX)
CREATE INDEX idx_kbeic_status ON ecos_knowledge.kb_extract_candidate USING btree (status);

--

-- idx_kbp_domain_window (INDEX)
CREATE INDEX idx_kbp_domain_window ON ecos_knowledge.ecos_kb_profile USING btree (domain, window_from, window_to);

--

-- idx_kbp_metric_status (INDEX)
CREATE INDEX idx_kbp_metric_status ON ecos_knowledge.ecos_kb_profile USING btree (metric_code, status, is_deleted);

--

-- idx_kbsched_enabled (INDEX)
CREATE INDEX idx_kbsched_enabled ON ecos_knowledge.kb_scheduled_extract USING btree (enabled);

--

-- idx_kg_node_created (INDEX)
CREATE INDEX idx_kg_node_created ON ecos_knowledge.ecos_knowledge_graph_node USING btree (created_at DESC);

--

-- idx_kg_node_type (INDEX)
CREATE INDEX idx_kg_node_type ON ecos_knowledge.ecos_knowledge_graph_node USING btree (node_type);

--

-- idx_kg_sync_log_created_at (INDEX)
CREATE INDEX idx_kg_sync_log_created_at ON ecos_knowledge.kg_sync_log USING btree (created_at DESC);

--

-- idx_kg_sync_log_job_id (INDEX)
CREATE INDEX idx_kg_sync_log_job_id ON ecos_knowledge.kg_sync_log USING btree (job_id);

--

-- idx_kg_sync_log_trace_id (INDEX)
CREATE INDEX idx_kg_sync_log_trace_id ON ecos_knowledge.kg_sync_log USING btree (trace_id);

--

-- idx_knowledge_article_category (INDEX)
CREATE INDEX idx_knowledge_article_category ON ecos_knowledge.knowledge_article USING btree (knowledge_category);

--

-- idx_knowledge_article_rule_ref (INDEX)
CREATE INDEX idx_knowledge_article_rule_ref ON ecos_knowledge.knowledge_article USING btree (rule_ref);

--

-- idx_knowledge_embedding_doc (INDEX)
CREATE INDEX idx_knowledge_embedding_doc ON ecos_knowledge.knowledge_embedding USING btree (document_id);

--

-- idx_knowledge_embedding_vec_hnsw (INDEX)
CREATE INDEX idx_knowledge_embedding_vec_hnsw ON ecos_knowledge.knowledge_embedding USING hnsw (embedding_vec public.vector_cosine_ops);

--

-- idx_nav_cat_domain (INDEX)
CREATE INDEX idx_nav_cat_domain ON ecos_knowledge.kb_nav_category USING btree (domain) WHERE (is_deleted = 0);

--

-- idx_nav_cat_parent (INDEX)
CREATE INDEX idx_nav_cat_parent ON ecos_knowledge.kb_nav_category USING btree (parent_id) WHERE (is_deleted = 0);

--

-- idx_rel_article (INDEX)
CREATE INDEX idx_rel_article ON ecos_knowledge.kb_nav_article_rel USING btree (article_id) WHERE (is_deleted = 0);

--

-- idx_rel_node (INDEX)
CREATE INDEX idx_rel_node ON ecos_knowledge.kb_nav_article_rel USING btree (node_id) WHERE (is_deleted = 0);

--

-- uniq_ecos_kb_document_code (INDEX)
CREATE UNIQUE INDEX uniq_ecos_kb_document_code ON ecos_knowledge.ecos_kb_document USING btree (doc_code, is_deleted);

--

-- uniq_kb_doc_chunk_doc_idx (INDEX)
CREATE UNIQUE INDEX uniq_kb_doc_chunk_doc_idx ON ecos_knowledge.kb_doc_chunk USING btree (doc_id, chunk_index);

--

-- uniq_kb_nav_category (INDEX)
CREATE UNIQUE INDEX uniq_kb_nav_category ON ecos_knowledge.kb_nav_category USING btree (domain, parent_id, name, is_deleted);

--

-- uniq_kba_key_ver (INDEX)
CREATE UNIQUE INDEX uniq_kba_key_ver ON ecos_knowledge.ecos_kb_assumption USING btree (assumption_key, assumption_version, is_deleted);

--

-- uniq_kbp_key_ver (INDEX)
CREATE UNIQUE INDEX uniq_kbp_key_ver ON ecos_knowledge.ecos_kb_profile USING btree (profile_key, profile_version, is_deleted);

--

-- uniq_nav_cat_name_domain (INDEX)
CREATE UNIQUE INDEX uniq_nav_cat_name_domain ON ecos_knowledge.kb_nav_category USING btree (domain, name) WHERE (is_deleted = 0);

--

-- uniq_nav_tag_name_domain (INDEX)
CREATE UNIQUE INDEX uniq_nav_tag_name_domain ON ecos_knowledge.kb_nav_tag USING btree (domain, tag_name) WHERE (is_deleted = 0);

--

-- uniq_rel_article_node_scope (INDEX)
CREATE UNIQUE INDEX uniq_rel_article_node_scope ON ecos_knowledge.kb_nav_article_rel USING btree (article_id, node_id, scope) WHERE (is_deleted = 0);

--

