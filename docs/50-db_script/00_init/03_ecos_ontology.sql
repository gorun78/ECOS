-- ============================================================================
-- 03_ecos_ontology.sql — ecos_ontology 权威 DDL
-- ============================================================================
-- 由 pg_dump --schema-only 从真实库 sys_man 反向导出后清洗装配（2026-11-17，V349 清理后重导）。
-- 目标 schema = ecos_ontology；保留对象 199 个，剔分区子表块 0 个，外键 2 条移入 09。
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

-- action_definition (TABLE)
CREATE TABLE ecos_ontology.action_definition (
    id character varying(64) NOT NULL,
    code character varying(128),
    name character varying(255),
    type character varying(16) DEFAULT 'WORKFLOW',
    config jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE action_definition (COMMENT)
COMMENT ON TABLE ecos_ontology.action_definition IS '动作定义表(本体编译器)';

--

-- ecos_business_glossary (TABLE)
CREATE TABLE ecos_ontology.ecos_business_glossary (
    id bigint NOT NULL,
    code character varying(64),
    name character varying(255) NOT NULL,
    definition text,
    domain character varying(128),
    domain_id character varying(50),
    owner character varying(128),
    status character varying(32) DEFAULT 'DRAFT',
    tenant_id character varying(64),
    created_by character varying(128),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL
);

--

-- TABLE ecos_business_glossary (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_business_glossary IS 'WAVE_D_T20: V47 搬, 0 行 ghost. Canonical = public.ecos_business_glossary. 未来清理需走 PMO 专项 drop.';

--

-- ecos_business_glossary_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_ontology.ecos_business_glossary_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_business_glossary_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_ontology.ecos_business_glossary_id_seq OWNED BY ecos_ontology.ecos_business_glossary.id;

--

-- ecos_caliber (TABLE)
CREATE TABLE ecos_ontology.ecos_caliber (
    id character varying(36) NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE' NOT NULL,
    unit character varying(32),
    currency character varying(8),
    owner_role character varying(64),
    dimension_json text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_caliber (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_caliber IS '口径主表（REQ-ONTO-02 / F03-01；R-6 ① 口径主权归 ontology；仅 ontology 可写，cognitive/workspace/kb 经 GET /api/v1/ontology/calibers/{code}/current 只读消费）';

--

-- ecos_caliber_version (TABLE)
CREATE TABLE ecos_ontology.ecos_caliber_version (
    id character varying(36) NOT NULL,
    caliber_id character varying(36) NOT NULL,
    version_no character varying(20) NOT NULL,
    formula text,
    additive smallint,
    period_granularity character varying(16),
    status character varying(16) DEFAULT 'DRAFT' NOT NULL,
    approved_by character varying(100),
    approved_at timestamp without time zone,
    git_ref character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_caliber_version (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_caliber_version IS '口径版本表（F03-01；行不可变，一次升版一行；PUBLISHED 指标绑定的 caliber_version 须 status=APPROVED，V1 门禁判据）';

--

-- ecos_domain (TABLE)
CREATE TABLE ecos_ontology.ecos_domain (
    id character varying(50) NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(200) NOT NULL,
    owner character varying(100),
    description text,
    status character varying(50) DEFAULT 'Draft',
    sort_order integer DEFAULT 0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE ecos_domain (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_domain IS 'WAVE_D_T20: V47 搬, 0 行 ghost. Canonical = public.ecos_domain. 未来清理需走 PMO 专项 drop.';

--

-- ecos_entity_table_mapping (TABLE)
CREATE TABLE ecos_ontology.ecos_entity_table_mapping (
    id character varying(36) NOT NULL,
    entity_code character varying(100) NOT NULL,
    entity_name character varying(200),
    domain_code character varying(64),
    datasource_id character varying(64) DEFAULT '' NOT NULL,
    resource_name character varying(200) NOT NULL,
    table_schema character varying(64),
    field_mappings jsonb NOT NULL,
    materialized boolean DEFAULT true NOT NULL,
    doc_anchor_json jsonb,
    doc_anchor_type character varying(32),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_entity_table_mapping (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_entity_table_mapping IS '本体实体 → 物理表映射契约（本体工作台产出、知识工作台实例抽取消费）；列清单单源 = OntologyMappingDao.MAPPING_COLUMNS；唯一约束 (entity_code, datasource_id, resource_name) 是 AutoDiscoverDao.upsertMapping ON CONFLICT 的前置条件';

--

-- COLUMN ecos_entity_table_mapping.field_mappings (COMMENT)
COMMENT ON COLUMN ecos_ontology.ecos_entity_table_mapping.field_mappings IS '映射契约 JSON（DR04/MC02 偏差列 D1/D2 登记对象：无 _json 后缀 + JSONB 形态，理由见脚本头部下方）';

--

-- COLUMN ecos_entity_table_mapping.materialized (COMMENT)
COMMENT ON COLUMN ecos_ontology.ecos_entity_table_mapping.materialized IS '是否参与图谱实例化: TRUE=参与(默认), FALSE=显式关闭（Q2 裁决原文，V135 登记，本脚本内联为建表默认）';

--

-- COLUMN ecos_entity_table_mapping.doc_anchor_json (COMMENT)
COMMENT ON COLUMN ecos_ontology.ecos_entity_table_mapping.doc_anchor_json IS '非结构化文档锚点（W2）：JSON {"docId":"...","source":"...","docChunkCount":N}；V150 改名后的最终列名';

--

-- COLUMN ecos_entity_table_mapping.doc_anchor_type (COMMENT)
COMMENT ON COLUMN ecos_ontology.ecos_entity_table_mapping.doc_anchor_type IS '锚点类型：TABLE(默认)/DOC_ONLY/MIXED（W2，V145 登记）';

--

-- ecos_function_audit (TABLE)
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
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_function_audit (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_function_audit IS 'Function 沙箱执行审计表·合规重建（W82/C65；Kafka ecos.audit FUNCTION_EXEC 的本地兜底落点，双失败=拒收 ECOS-ONTO-061 属代码侧）；旧 public.ecos_function_audit_log 停写保留';

--

-- ecos_glossary_term_relation (TABLE)
CREATE TABLE ecos_ontology.ecos_glossary_term_relation (
    id bigint NOT NULL,
    from_term_id bigint NOT NULL,
    to_term_id bigint NOT NULL,
    relation_type character varying(32) NOT NULL,
    weight integer DEFAULT 100,
    description character varying(512) DEFAULT '',
    created_by character varying(128) DEFAULT '',
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

-- TABLE ecos_glossary_term_relation (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_glossary_term_relation IS '词条关系边 — Wiki 词条间语义关系';

--

-- COLUMN ecos_glossary_term_relation.from_term_id (COMMENT)
COMMENT ON COLUMN ecos_ontology.ecos_glossary_term_relation.from_term_id IS '关系起点词条 id';

--

-- COLUMN ecos_glossary_term_relation.to_term_id (COMMENT)
COMMENT ON COLUMN ecos_ontology.ecos_glossary_term_relation.to_term_id IS '关系终点词条 id';

--

-- COLUMN ecos_glossary_term_relation.relation_type (COMMENT)
COMMENT ON COLUMN ecos_ontology.ecos_glossary_term_relation.relation_type IS '边类型: ISA/SYNONYM/PART_OF/SEE_ALSO/CAUSAL/RELATED';

--

-- COLUMN ecos_glossary_term_relation.weight (COMMENT)
COMMENT ON COLUMN ecos_ontology.ecos_glossary_term_relation.weight IS '关系权重(默认 100)';

--

-- ecos_glossary_term_relation_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_ontology.ecos_glossary_term_relation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_glossary_term_relation_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_ontology.ecos_glossary_term_relation_id_seq OWNED BY ecos_ontology.ecos_glossary_term_relation.id;

--

-- ecos_metric_definition (TABLE)
CREATE TABLE ecos_ontology.ecos_metric_definition (
    id character varying(36) NOT NULL,
    code character varying(128) NOT NULL,
    name character varying(255),
    expression text,
    aggregation character varying(16) DEFAULT 'SUM',
    entity_code character varying(128),
    caliber_id character varying(36),
    formula_version character varying(20),
    caliber_snapshot text,
    status character varying(20) DEFAULT 'DRAFT' NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_metric_definition (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_metric_definition IS '指标定义合规表（V168.1；MC01 主键形态；口径绑定承载表；旧 ecos_ontology.metric_definition 转停写只读过渡）';

--

-- ecos_object_attachment (TABLE)
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

-- TABLE ecos_object_attachment (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_object_attachment IS '对象附件表';

--

-- ecos_object_data (TABLE)
CREATE TABLE ecos_ontology.ecos_object_data (
    id character varying(64) NOT NULL,
    entity_code character varying(128) NOT NULL,
    object_data jsonb,
    status character varying(32) DEFAULT 'ACTIVE',
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_object_data (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_object_data IS '对象数据表(运行时)';

--

-- ecos_object_links (TABLE)
CREATE TABLE ecos_ontology.ecos_object_links (
    id character varying(64) DEFAULT (gen_random_uuid())::text NOT NULL,
    source_id character varying(64),
    target_id character varying(64),
    relation_code character varying(128),
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE ecos_object_links (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_object_links IS '对象链接表';

--

-- ecos_object_relation (TABLE)
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

-- TABLE ecos_object_relation (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_object_relation IS '对象关联表(桥接)';

--

-- ecos_object_relationship (TABLE)
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

-- TABLE ecos_object_relationship (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_object_relationship IS '对象关系表';

--

-- ecos_object_state_machine (TABLE)
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

-- TABLE ecos_object_state_machine (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_object_state_machine IS '对象状态机表';

--

-- ecos_object_timeline (TABLE)
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

-- TABLE ecos_object_timeline (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_object_timeline IS '对象时间线表';

--

-- ecos_object_version (TABLE)
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
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_object_version (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_object_version IS '对象版本表';

--

-- ecos_ontology_candidate (TABLE)
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
    status character varying(24) DEFAULT 'GENERATED' NOT NULL,
    proposal_ref character varying(36),
    tool_contract_ref character varying(100),
    approval_ref character varying(100),
    source_run_id character varying(36),
    trace_id character varying(64),
    idempotency_key character varying(100),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_ontology_candidate (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_ontology_candidate IS '本体 AI 候选（补充设计 v1.0 §7.3）：候选集载体；正式发布只经既有 proposals 审批流（AI 禁直发，SUP-ONTO-02）；候选必带证据+冲突+兼容性（SUP-ONTO-01）';

--

-- ecos_ontology_data (TABLE)
CREATE TABLE ecos_ontology.ecos_ontology_data (
    id character varying(64) NOT NULL,
    ontology_id character varying(64) NOT NULL,
    object_type character varying(32) NOT NULL,
    record_key character varying(255),
    payload text,
    status character varying(32) DEFAULT 'ACTIVE' NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(128),
    update_by character varying(128),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- ecos_ontology_entity (TABLE)
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
    status character varying(32) DEFAULT 'ACTIVE' NOT NULL,
    update_by character varying(128),
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_ontology_entity (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_ontology_entity IS 'WAVE_D_T20: V47 建, 0 行 ghost. Canonical = public.ecos_ontology_entity. 未来清理需走 PMO 专项 drop.';

--

-- ecos_ontology_git_archive_pending (TABLE)
CREATE TABLE ecos_ontology.ecos_ontology_git_archive_pending (
    id character varying(36) NOT NULL,
    ontology_id character varying(64) NOT NULL,
    version_id character varying(64) NOT NULL,
    target_version_no character varying(20) NOT NULL,
    repository_id character varying(64) DEFAULT 'ontology' NOT NULL,
    commit_message character varying(512),
    author_name character varying(100),
    payload text,
    attempts smallint DEFAULT 0 NOT NULL,
    next_retry_at timestamp without time zone NOT NULL,
    last_error character varying(1000),
    status character varying(16) DEFAULT 'pending' NOT NULL,
    last_commit_id character varying(64),
    trace_id character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1.0.0' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- ecos_ontology_proposal (TABLE)
CREATE TABLE ecos_ontology.ecos_ontology_proposal (
    id character varying(36) NOT NULL,
    domain_code character varying(128) NOT NULL,
    proposal_type character varying(32) NOT NULL,
    target_entity character varying(256),
    payload_json text NOT NULL,
    snapshot_json text,
    status character varying(20) DEFAULT 'PENDING' NOT NULL,
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
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_ontology_proposal (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_ontology_proposal IS '本体变更提案合规表（W72/W74；V174 起唯一写入口；旧 public.ecos_ontology_proposals 停写）';

--

-- ecos_ontology_proposals (TABLE)
CREATE TABLE ecos_ontology.ecos_ontology_proposals (
    id bigint NOT NULL,
    domain_code character varying(128) NOT NULL,
    proposal_type character varying(32) NOT NULL,
    target_entity character varying(256),
    payload jsonb NOT NULL,
    snapshot jsonb,
    status character varying(16) DEFAULT 'DRAFT' NOT NULL,
    author character varying(64),
    reviewer character varying(64),
    reviewer_comment character varying(512),
    version_id bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    optimistic_lock_version integer DEFAULT 0 NOT NULL
);

--

-- ecos_ontology_proposals_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_ontology.ecos_ontology_proposals_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_ontology_proposals_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_ontology.ecos_ontology_proposals_id_seq OWNED BY ecos_ontology.ecos_ontology_proposals.id;

--

-- ecos_ontology_rule (TABLE)
CREATE TABLE ecos_ontology.ecos_ontology_rule (
    id character varying(64) NOT NULL,
    entity_id character varying(64) NOT NULL,
    code character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    rule_type character varying(64) DEFAULT 'BINARY',
    expression text DEFAULT ''::text,
    action character varying(255) DEFAULT '',
    priority integer DEFAULT 100,
    enabled smallint DEFAULT 1,
    description text DEFAULT ''::text,
    status character varying(32) DEFAULT 'ACTIVE' NOT NULL,
    create_time timestamp without time zone DEFAULT now(),
    update_time timestamp without time zone DEFAULT now(),
    create_by character varying(128),
    update_by character varying(128),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- ecos_ontology_version (TABLE)
CREATE TABLE ecos_ontology.ecos_ontology_version (
    id character varying(64) NOT NULL,
    ontology_id character varying(64) NOT NULL,
    version_no character varying(32) NOT NULL,
    status character varying(32) DEFAULT 'DRAFT' NOT NULL,
    snapshot text,
    change_log text DEFAULT ''::text,
    publisher character varying(128),
    published_at timestamp without time zone,
    create_time timestamp without time zone DEFAULT now(),
    update_time timestamp without time zone DEFAULT now(),
    create_by character varying(128),
    update_by character varying(128),
    is_deleted smallint DEFAULT 0 NOT NULL,
    snapshot_json text,
    git_ref character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- COLUMN ecos_ontology_version.snapshot (COMMENT)
COMMENT ON COLUMN ecos_ontology.ecos_ontology_version.snapshot IS 'W83/C66 违例存量列（MC02 禁用的 JSON 二进制类型形态）：V170.1 起停写，新写一律 snapshot_json（TEXT）；列保留不删（IR03），历史语义降级为"在用版本"指针，行内容以 git_ref 指向的 Git 归档为准（后端规范 §十一）';

--

-- COLUMN ecos_ontology_version.snapshot_json (COMMENT)
COMMENT ON COLUMN ecos_ontology.ecos_ontology_version.snapshot_json IS '在用版本快照 JSON 文本（MC02 合规形态；只做存取，禁 WHERE/JOIN/索引）';

--

-- COLUMN ecos_ontology_version.git_ref (COMMENT)
COMMENT ON COLUMN ecos_ontology.ecos_ontology_version.git_ref IS 'Git 归档引用（F03-15：发布成功后写 ontology 仓库 ref；归档失败入 git_archive_pending 补偿队列，由 runtime-task 重试——队列表建表属卷03 其他批次，未在本脚本落）';

--

-- ecos_ontology_workbench_object (TABLE)
CREATE TABLE ecos_ontology.ecos_ontology_workbench_object (
    id character varying(64) NOT NULL,
    ontology_id character varying(64) NOT NULL,
    object_type character varying(32) NOT NULL,
    code character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text NOT NULL,
    definition_json text DEFAULT ''::text NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE' NOT NULL,
    create_time timestamp without time zone DEFAULT now(),
    update_time timestamp without time zone DEFAULT now(),
    create_by character varying(128),
    update_by character varying(128),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- ecos_workflow (TABLE)
CREATE TABLE ecos_ontology.ecos_workflow (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text DEFAULT ''::text,
    status character varying(32) DEFAULT 'draft',
    mode character varying(32) DEFAULT 'sequential',
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
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_workflow (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_workflow IS '工作流定义唯一模型表（R-7 归属①+模型 b；旧 public.ecos_workflow(v1,8 行) 与 public.ecos_workflow_v2(11 行) 停写只读，数据收敛本表）';

--

-- ecos_workflow_approval (TABLE)
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
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_workflow_approval (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_workflow_approval IS '工作流审批表（W85/C69 修复载体：与主表同域 ecos_ontology；代码侧必须 schema 限定查询（F03-13），裸名即 SchemaDriftLintTest CI 红）';

--

-- ecos_workflow_edge (TABLE)
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
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_workflow_edge (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_workflow_edge IS '工作流边投影表（F03-12）';

--

-- ecos_workflow_instance (TABLE)
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

-- TABLE ecos_workflow_instance (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_workflow_instance IS '工作流实例表';

--

-- ecos_workflow_log (TABLE)
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

-- TABLE ecos_workflow_log (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_workflow_log IS '工作流日志表';

--

-- ecos_workflow_node (TABLE)
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
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_workflow_node (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_workflow_node IS '工作流节点投影表（F03-12；供血缘与校验检索，主表 nodes_json 不参与 WHERE）';

--

-- ecos_workflow_task (TABLE)
CREATE TABLE ecos_ontology.ecos_workflow_task (
    id character varying(50) NOT NULL,
    instance_id character varying(50),
    node_id character varying(100),
    task_type character varying(50),
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
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE ecos_workflow_task (COMMENT)
COMMENT ON TABLE ecos_ontology.ecos_workflow_task IS '工作流任务表';

--

-- entity_definition (TABLE)
CREATE TABLE ecos_ontology.entity_definition (
    id character varying(64) NOT NULL,
    code character varying(128) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    category character varying(32) DEFAULT 'MASTER',
    properties jsonb DEFAULT '[]'::jsonb,
    lifecycle jsonb DEFAULT '{}'::jsonb,
    version integer DEFAULT 1,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE entity_definition (COMMENT)
COMMENT ON TABLE ecos_ontology.entity_definition IS '实体定义表(本体编译器)';

--

-- event_definition (TABLE)
CREATE TABLE ecos_ontology.event_definition (
    id character varying(64) NOT NULL,
    code character varying(128),
    source character varying(128),
    payload_schema jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE event_definition (COMMENT)
COMMENT ON TABLE ecos_ontology.event_definition IS '事件定义表(本体编译器)';

--

-- metric_definition (TABLE)
CREATE TABLE ecos_ontology.metric_definition (
    id character varying(64) NOT NULL,
    code character varying(128) NOT NULL,
    name character varying(255) NOT NULL,
    expression text,
    aggregation character varying(16) DEFAULT 'SUM',
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
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE metric_definition (COMMENT)
COMMENT ON TABLE ecos_ontology.metric_definition IS '指标定义旧表（V52 建，id VARCHAR(64) 违 MC01、created_at 违 DR06，O-11/W77）：V168.1 起停写，新写一律 ecos_ontology.ecos_metric_definition；只加不删（IR03），清理走 PMO 专项';

--

-- policy_definition (TABLE)
CREATE TABLE ecos_ontology.policy_definition (
    id character varying(64) NOT NULL,
    code character varying(128),
    type character varying(32),
    expression text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE policy_definition (COMMENT)
COMMENT ON TABLE ecos_ontology.policy_definition IS '策略定义表(本体编译器)';

--

-- relationship_definition (TABLE)
CREATE TABLE ecos_ontology.relationship_definition (
    id character varying(64) NOT NULL,
    source_entity character varying(128) NOT NULL,
    target_entity character varying(128) NOT NULL,
    type character varying(32),
    cardinality character varying(16) DEFAULT 'ONE_TO_MANY',
    properties jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE relationship_definition (COMMENT)
COMMENT ON TABLE ecos_ontology.relationship_definition IS '关系定义表(本体编译器)';

--

-- ecos_business_glossary id (DEFAULT)
ALTER TABLE ONLY ecos_ontology.ecos_business_glossary ALTER COLUMN id SET DEFAULT nextval('ecos_ontology.ecos_business_glossary_id_seq'::regclass);

--

-- ecos_glossary_term_relation id (DEFAULT)
ALTER TABLE ONLY ecos_ontology.ecos_glossary_term_relation ALTER COLUMN id SET DEFAULT nextval('ecos_ontology.ecos_glossary_term_relation_id_seq'::regclass);

--

-- ecos_ontology_proposals id (DEFAULT)
ALTER TABLE ONLY ecos_ontology.ecos_ontology_proposals ALTER COLUMN id SET DEFAULT nextval('ecos_ontology.ecos_ontology_proposals_id_seq'::regclass);

--

-- action_definition action_definition_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.action_definition
    ADD CONSTRAINT action_definition_pkey PRIMARY KEY (id);

--

-- ecos_business_glossary ecos_business_glossary_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_business_glossary
    ADD CONSTRAINT ecos_business_glossary_pkey PRIMARY KEY (id);

--

-- ecos_caliber ecos_caliber_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_caliber
    ADD CONSTRAINT ecos_caliber_pkey PRIMARY KEY (id);

--

-- ecos_caliber_version ecos_caliber_version_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_caliber_version
    ADD CONSTRAINT ecos_caliber_version_pkey PRIMARY KEY (id);

--

-- ecos_domain ecos_domain_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_domain
    ADD CONSTRAINT ecos_domain_pkey PRIMARY KEY (id);

--

-- ecos_entity_table_mapping ecos_entity_table_mapping_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_entity_table_mapping
    ADD CONSTRAINT ecos_entity_table_mapping_pkey PRIMARY KEY (id);

--

-- ecos_function_audit ecos_function_audit_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_function_audit
    ADD CONSTRAINT ecos_function_audit_pkey PRIMARY KEY (id);

--

-- ecos_glossary_term_relation ecos_glossary_term_relation_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_glossary_term_relation
    ADD CONSTRAINT ecos_glossary_term_relation_pkey PRIMARY KEY (id);

--

-- ecos_metric_definition ecos_metric_definition_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_metric_definition
    ADD CONSTRAINT ecos_metric_definition_pkey PRIMARY KEY (id);

--

-- ecos_object_attachment ecos_object_attachment_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_object_attachment
    ADD CONSTRAINT ecos_object_attachment_pkey PRIMARY KEY (id);

--

-- ecos_object_data ecos_object_data_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_object_data
    ADD CONSTRAINT ecos_object_data_pkey PRIMARY KEY (id);

--

-- ecos_object_links ecos_object_links_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_object_links
    ADD CONSTRAINT ecos_object_links_pkey PRIMARY KEY (id);

--

-- ecos_object_relation ecos_object_relation_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_object_relation
    ADD CONSTRAINT ecos_object_relation_pkey PRIMARY KEY (id);

--

-- ecos_object_relationship ecos_object_relationship_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_object_relationship
    ADD CONSTRAINT ecos_object_relationship_pkey PRIMARY KEY (id);

--

-- ecos_object_state_machine ecos_object_state_machine_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_object_state_machine
    ADD CONSTRAINT ecos_object_state_machine_pkey PRIMARY KEY (id);

--

-- ecos_object_timeline ecos_object_timeline_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_object_timeline
    ADD CONSTRAINT ecos_object_timeline_pkey PRIMARY KEY (id);

--

-- ecos_object_version ecos_object_version_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_object_version
    ADD CONSTRAINT ecos_object_version_pkey PRIMARY KEY (id);

--

-- ecos_ontology_candidate ecos_ontology_candidate_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_ontology_candidate
    ADD CONSTRAINT ecos_ontology_candidate_pkey PRIMARY KEY (id);

--

-- ecos_ontology_data ecos_ontology_data_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_ontology_data
    ADD CONSTRAINT ecos_ontology_data_pkey PRIMARY KEY (id);

--

-- ecos_ontology_entity ecos_ontology_entity_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_ontology_entity
    ADD CONSTRAINT ecos_ontology_entity_pkey PRIMARY KEY (id);

--

-- ecos_ontology_git_archive_pending ecos_ontology_git_archive_pending_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_ontology_git_archive_pending
    ADD CONSTRAINT ecos_ontology_git_archive_pending_pkey PRIMARY KEY (id);

--

-- ecos_ontology_proposal ecos_ontology_proposal_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_ontology_proposal
    ADD CONSTRAINT ecos_ontology_proposal_pkey PRIMARY KEY (id);

--

-- ecos_ontology_proposals ecos_ontology_proposals_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_ontology_proposals
    ADD CONSTRAINT ecos_ontology_proposals_pkey PRIMARY KEY (id);

--

-- ecos_ontology_rule ecos_ontology_rule_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_ontology_rule
    ADD CONSTRAINT ecos_ontology_rule_pkey PRIMARY KEY (id);

--

-- ecos_ontology_version ecos_ontology_version_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_ontology_version
    ADD CONSTRAINT ecos_ontology_version_pkey PRIMARY KEY (id);

--

-- ecos_ontology_workbench_object ecos_ontology_workbench_object_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_ontology_workbench_object
    ADD CONSTRAINT ecos_ontology_workbench_object_pkey PRIMARY KEY (id);

--

-- ecos_workflow_approval ecos_workflow_approval_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_workflow_approval
    ADD CONSTRAINT ecos_workflow_approval_pkey PRIMARY KEY (id);

--

-- ecos_workflow_edge ecos_workflow_edge_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_workflow_edge
    ADD CONSTRAINT ecos_workflow_edge_pkey PRIMARY KEY (id);

--

-- ecos_workflow_instance ecos_workflow_instance_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_workflow_instance
    ADD CONSTRAINT ecos_workflow_instance_pkey PRIMARY KEY (id);

--

-- ecos_workflow_log ecos_workflow_log_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_workflow_log
    ADD CONSTRAINT ecos_workflow_log_pkey PRIMARY KEY (id);

--

-- ecos_workflow_node ecos_workflow_node_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_workflow_node
    ADD CONSTRAINT ecos_workflow_node_pkey PRIMARY KEY (id);

--

-- ecos_workflow ecos_workflow_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_workflow
    ADD CONSTRAINT ecos_workflow_pkey PRIMARY KEY (id);

--

-- ecos_workflow_task ecos_workflow_task_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_workflow_task
    ADD CONSTRAINT ecos_workflow_task_pkey PRIMARY KEY (id);

--

-- entity_definition entity_definition_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.entity_definition
    ADD CONSTRAINT entity_definition_pkey PRIMARY KEY (id);

--

-- event_definition event_definition_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.event_definition
    ADD CONSTRAINT event_definition_pkey PRIMARY KEY (id);

--

-- metric_definition metric_definition_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.metric_definition
    ADD CONSTRAINT metric_definition_pkey PRIMARY KEY (id);

--

-- policy_definition policy_definition_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.policy_definition
    ADD CONSTRAINT policy_definition_pkey PRIMARY KEY (id);

--

-- relationship_definition relationship_definition_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.relationship_definition
    ADD CONSTRAINT relationship_definition_pkey PRIMARY KEY (id);

--

-- ecos_glossary_term_relation uniq_glossary_relation_edge (CONSTRAINT)
ALTER TABLE ONLY ecos_ontology.ecos_glossary_term_relation
    ADD CONSTRAINT uniq_glossary_relation_edge UNIQUE (from_term_id, to_term_id, relation_type);

--

-- idx_ecos_caliber_domain (INDEX)
CREATE INDEX idx_ecos_caliber_domain ON ecos_ontology.ecos_caliber USING btree (domain);

--

-- idx_ecos_caliber_status (INDEX)
CREATE INDEX idx_ecos_caliber_status ON ecos_ontology.ecos_caliber USING btree (status);

--

-- idx_ecos_domain_tenant (INDEX)
CREATE INDEX idx_ecos_domain_tenant ON ecos_ontology.ecos_domain USING btree (tenant_id);

--

-- idx_ecos_func_audit_caller (INDEX)
CREATE INDEX idx_ecos_func_audit_caller ON ecos_ontology.ecos_function_audit USING btree (caller_id);

--

-- idx_ecos_func_audit_created (INDEX)
CREATE INDEX idx_ecos_func_audit_created ON ecos_ontology.ecos_function_audit USING btree (create_time);

--

-- idx_ecos_func_audit_domain (INDEX)
CREATE INDEX idx_ecos_func_audit_domain ON ecos_ontology.ecos_function_audit USING btree (domain);

--

-- idx_ecos_func_audit_status (INDEX)
CREATE INDEX idx_ecos_func_audit_status ON ecos_ontology.ecos_function_audit USING btree (status);

--

-- idx_ecos_func_audit_trace (INDEX)
CREATE INDEX idx_ecos_func_audit_trace ON ecos_ontology.ecos_function_audit USING btree (trace_id);

--

-- idx_ecos_metric_definition_domain (INDEX)
CREATE INDEX idx_ecos_metric_definition_domain ON ecos_ontology.ecos_metric_definition USING btree (domain);

--

-- idx_ecos_metric_definition_status (INDEX)
CREATE INDEX idx_ecos_metric_definition_status ON ecos_ontology.ecos_metric_definition USING btree (status);

--

-- idx_ecos_ontology_data_ontology (INDEX)
CREATE INDEX idx_ecos_ontology_data_ontology ON ecos_ontology.ecos_ontology_data USING btree (ontology_id);

--

-- idx_ecos_ontology_entity_ontology (INDEX)
CREATE INDEX idx_ecos_ontology_entity_ontology ON ecos_ontology.ecos_ontology_entity USING btree (ontology_id);

--

-- idx_ecos_ontology_git_archive_due (INDEX)
CREATE INDEX idx_ecos_ontology_git_archive_due ON ecos_ontology.ecos_ontology_git_archive_pending USING btree (status, next_retry_at);

--

-- idx_ecos_ontology_git_archive_onto (INDEX)
CREATE INDEX idx_ecos_ontology_git_archive_onto ON ecos_ontology.ecos_ontology_git_archive_pending USING btree (ontology_id, version_id, is_deleted);

--

-- idx_ecos_ontology_workbench_object_ontology_type (INDEX)
CREATE INDEX idx_ecos_ontology_workbench_object_ontology_type ON ecos_ontology.ecos_ontology_workbench_object USING btree (ontology_id, object_type);

--

-- idx_ecos_ontology_workbench_object_type (INDEX)
CREATE INDEX idx_ecos_ontology_workbench_object_type ON ecos_ontology.ecos_ontology_workbench_object USING btree (object_type, is_deleted);

--

-- idx_ecos_proposal_author (INDEX)
CREATE INDEX idx_ecos_proposal_author ON ecos_ontology.ecos_ontology_proposal USING btree (author);

--

-- idx_ecos_proposal_domain (INDEX)
CREATE INDEX idx_ecos_proposal_domain ON ecos_ontology.ecos_ontology_proposal USING btree (domain);

--

-- idx_ecos_proposal_domain_status (INDEX)
CREATE INDEX idx_ecos_proposal_domain_status ON ecos_ontology.ecos_ontology_proposal USING btree (domain_code, status);

--

-- idx_ecos_wf_appr_instance (INDEX)
CREATE INDEX idx_ecos_wf_appr_instance ON ecos_ontology.ecos_workflow_approval USING btree (instance_id);

--

-- idx_ecos_wf_appr_task (INDEX)
CREATE INDEX idx_ecos_wf_appr_task ON ecos_ontology.ecos_workflow_approval USING btree (task_id);

--

-- idx_ecos_workflow_domain (INDEX)
CREATE INDEX idx_ecos_workflow_domain ON ecos_ontology.ecos_workflow USING btree (domain);

--

-- idx_ecos_workflow_status (INDEX)
CREATE INDEX idx_ecos_workflow_status ON ecos_ontology.ecos_workflow USING btree (status);

--

-- idx_entity_def_code (INDEX)
CREATE INDEX idx_entity_def_code ON ecos_ontology.entity_definition USING btree (code);

--

-- idx_glossary_relation_from (INDEX)
CREATE INDEX idx_glossary_relation_from ON ecos_ontology.ecos_glossary_term_relation USING btree (from_term_id);

--

-- idx_glossary_relation_to (INDEX)
CREATE INDEX idx_glossary_relation_to ON ecos_ontology.ecos_glossary_term_relation USING btree (to_term_id);

--

-- idx_glossary_relation_type (INDEX)
CREATE INDEX idx_glossary_relation_type ON ecos_ontology.ecos_glossary_term_relation USING btree (relation_type);

--

-- idx_metric_def_code (INDEX)
CREATE INDEX idx_metric_def_code ON ecos_ontology.metric_definition USING btree (code);

--

-- idx_object_data_tenant (INDEX)
CREATE INDEX idx_object_data_tenant ON ecos_ontology.ecos_object_data USING btree (tenant_id);

--

-- idx_onto_domain_code (INDEX)
CREATE UNIQUE INDEX idx_onto_domain_code ON ecos_ontology.ecos_domain USING btree (code);

--

-- idx_onto_entdef_code (INDEX)
CREATE UNIQUE INDEX idx_onto_entdef_code ON ecos_ontology.entity_definition USING btree (code);

--

-- idx_onto_gloss_domain (INDEX)
CREATE INDEX idx_onto_gloss_domain ON ecos_ontology.ecos_business_glossary USING btree (domain);

--

-- idx_onto_gloss_status (INDEX)
CREATE INDEX idx_onto_gloss_status ON ecos_ontology.ecos_business_glossary USING btree (status);

--

-- idx_onto_links_src (INDEX)
CREATE INDEX idx_onto_links_src ON ecos_ontology.ecos_object_links USING btree (source_id);

--

-- idx_onto_links_tgt (INDEX)
CREATE INDEX idx_onto_links_tgt ON ecos_ontology.ecos_object_links USING btree (target_id);

--

-- idx_onto_metric_code (INDEX)
CREATE UNIQUE INDEX idx_onto_metric_code ON ecos_ontology.metric_definition USING btree (code);

--

-- idx_onto_objdata_entity (INDEX)
CREATE INDEX idx_onto_objdata_entity ON ecos_ontology.ecos_object_data USING btree (entity_code);

--

-- idx_onto_objdata_tenant (INDEX)
CREATE INDEX idx_onto_objdata_tenant ON ecos_ontology.ecos_object_data USING btree (tenant_id);

--

-- idx_onto_objrel_src (INDEX)
CREATE INDEX idx_onto_objrel_src ON ecos_ontology.ecos_object_relationship USING btree (source_object_id);

--

-- idx_onto_objrel_tenant (INDEX)
CREATE INDEX idx_onto_objrel_tenant ON ecos_ontology.ecos_object_relation USING btree (tenant_id);

--

-- idx_onto_objrel_tgt (INDEX)
CREATE INDEX idx_onto_objrel_tgt ON ecos_ontology.ecos_object_relationship USING btree (target_object_id);

--

-- idx_onto_objver_uniq (INDEX)
CREATE UNIQUE INDEX idx_onto_objver_uniq ON ecos_ontology.ecos_object_version USING btree (object_id, version_no);

--

-- idx_onto_sm_uniq (INDEX)
CREATE UNIQUE INDEX idx_onto_sm_uniq ON ecos_ontology.ecos_object_state_machine USING btree (entity_code, from_status, transition_code);

--

-- idx_onto_timeline_obj (INDEX)
CREATE INDEX idx_onto_timeline_obj ON ecos_ontology.ecos_object_timeline USING btree (object_id, created_at DESC);

--

-- idx_onto_wfappr_inst (INDEX)
CREATE INDEX idx_onto_wfappr_inst ON ecos_ontology.ecos_workflow_approval USING btree (instance_id);

--

-- idx_onto_wfappr_task (INDEX)
CREATE INDEX idx_onto_wfappr_task ON ecos_ontology.ecos_workflow_approval USING btree (task_id);

--

-- idx_onto_wfinst_status (INDEX)
CREATE INDEX idx_onto_wfinst_status ON ecos_ontology.ecos_workflow_instance USING btree (status);

--

-- idx_onto_wfinst_tenant (INDEX)
CREATE INDEX idx_onto_wfinst_tenant ON ecos_ontology.ecos_workflow_instance USING btree (tenant_id);

--

-- idx_onto_wfinst_wf (INDEX)
CREATE INDEX idx_onto_wfinst_wf ON ecos_ontology.ecos_workflow_instance USING btree (workflow_id);

--

-- idx_onto_wflog_inst (INDEX)
CREATE INDEX idx_onto_wflog_inst ON ecos_ontology.ecos_workflow_log USING btree (instance_id, created_at);

--

-- idx_onto_wftask_assign (INDEX)
CREATE INDEX idx_onto_wftask_assign ON ecos_ontology.ecos_workflow_task USING btree (assignee);

--

-- idx_onto_wftask_inst (INDEX)
CREATE INDEX idx_onto_wftask_inst ON ecos_ontology.ecos_workflow_task USING btree (instance_id);

--

-- idx_onto_wftask_status (INDEX)
CREATE INDEX idx_onto_wftask_status ON ecos_ontology.ecos_workflow_task USING btree (status);

--

-- idx_proposals_author (INDEX)
CREATE INDEX idx_proposals_author ON ecos_ontology.ecos_ontology_proposals USING btree (author);

--

-- idx_proposals_domain (INDEX)
CREATE INDEX idx_proposals_domain ON ecos_ontology.ecos_ontology_proposals USING btree (domain_code, status);

--

-- idx_proposals_olock (INDEX)
CREATE INDEX idx_proposals_olock ON ecos_ontology.ecos_ontology_proposals USING btree (optimistic_lock_version);

--

-- idx_rel_def_source (INDEX)
CREATE INDEX idx_rel_def_source ON ecos_ontology.relationship_definition USING btree (source_entity);

--

-- idx_rel_def_target (INDEX)
CREATE INDEX idx_rel_def_target ON ecos_ontology.relationship_definition USING btree (target_entity);

--

-- idx_v273_onto_ref (INDEX)
CREATE INDEX idx_v273_onto_ref ON ecos_ontology.ecos_ontology_candidate USING btree (proposal_ref, is_deleted);

--

-- idx_v273_onto_tenant (INDEX)
CREATE INDEX idx_v273_onto_tenant ON ecos_ontology.ecos_ontology_candidate USING btree (tenant_id, status, is_deleted);

--

-- idx_v281_etm_domain_code (INDEX)
CREATE INDEX idx_v281_etm_domain_code ON ecos_ontology.ecos_entity_table_mapping USING btree (domain_code);

--

-- uk_v273_onto_idem (INDEX)
CREATE UNIQUE INDEX uk_v273_onto_idem ON ecos_ontology.ecos_ontology_candidate USING btree (tenant_id, idempotency_key, is_deleted);

--

-- uk_v281_etm_entity_ds_resource (INDEX)
CREATE UNIQUE INDEX uk_v281_etm_entity_ds_resource ON ecos_ontology.ecos_entity_table_mapping USING btree (entity_code, datasource_id, resource_name);

--

-- uniq_ecos_caliber_code (INDEX)
CREATE UNIQUE INDEX uniq_ecos_caliber_code ON ecos_ontology.ecos_caliber USING btree (code, is_deleted);

--

-- uniq_ecos_caliber_ver (INDEX)
CREATE UNIQUE INDEX uniq_ecos_caliber_ver ON ecos_ontology.ecos_caliber_version USING btree (caliber_id, version_no);

--

-- uniq_ecos_metric_definition_code (INDEX)
CREATE UNIQUE INDEX uniq_ecos_metric_definition_code ON ecos_ontology.ecos_metric_definition USING btree (code, is_deleted);

--

-- uniq_ecos_ontology_data_key (INDEX)
CREATE UNIQUE INDEX uniq_ecos_ontology_data_key ON ecos_ontology.ecos_ontology_data USING btree (ontology_id, object_type, record_key);

--

-- uniq_ecos_wf_edge (INDEX)
CREATE UNIQUE INDEX uniq_ecos_wf_edge ON ecos_ontology.ecos_workflow_edge USING btree (workflow_id, edge_key, is_deleted);

--

-- uniq_ecos_wf_node (INDEX)
CREATE UNIQUE INDEX uniq_ecos_wf_node ON ecos_ontology.ecos_workflow_node USING btree (workflow_id, node_key, is_deleted);

--

-- uniq_ecos_workflow_name (INDEX)
CREATE UNIQUE INDEX uniq_ecos_workflow_name ON ecos_ontology.ecos_workflow USING btree (name, is_deleted);

--

