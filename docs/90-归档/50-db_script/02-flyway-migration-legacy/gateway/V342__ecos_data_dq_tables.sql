-- ============================================================
-- V342__ecos_data_dq_tables.sql — 数据质量 11 张表的建表迁移
-- 追溯: D123 第二面（2026-11-17 活体取证 + 单源核对）
--
-- 现象: V111~V113 建的是 ecos_dq schema 下的 dq_*（实测 ecos_dq 表数 = 0），
--       而 DqRuleMapper / DqRuleSqlProvider 等 DAO 全部查 ecos_data.dq_*（dev 实存 11 表）。
--       ⇒ 全新库跑完单源迁移后 DAO 找不到表；dev 库的表是历史手建、从未进过单源。
--
-- 处置: 按 dev 真实结构在 ecos_data 建这 11 张表（IF NOT EXISTS，幂等）。
--       不动 V111~V113（历史脚本不追改），不动 ecos_dq 空 schema（R9 禁 DROP）。
--       结构来源 = pg_dump --schema-only ecos_data.dq_*（2026-11-17 实库导出），
--       已剥离 pg_dump 噪音（\restrict / SET / OWNER / COMMENT）。
--
-- 纪律: DR01 全 schema 限定 / DR02 无前缀表 / IR03 只建表不删 / MC01 金额列 NUMERIC。
-- 号段: 接 V341；落地后下一号 V343（须同批核对 MigrationVersionUniquenessTest 与三枚 SCRIPT_FLOOR）。
-- ============================================================

\restrict 83EhjJ61vjpY8X6tCNyXKpDQDbkEsnDNZkE0qdfr7ZIJbSIhFR6Z4Rli5NdUHrx

CREATE TABLE IF NOT EXISTS ecos_data.dq_alert_record (
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

-- Name: TABLE dq_alert_record; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_alert_record.status; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_alert_record.escalated_to; Type: COMMENT; Schema: ecos_data; Owner: postgres

CREATE TABLE IF NOT EXISTS ecos_data.dq_knowledge_entry (
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

-- Name: TABLE dq_knowledge_entry; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_knowledge_entry.id; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_knowledge_entry.source_type; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_knowledge_entry.source_id; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_knowledge_entry.category; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_knowledge_entry.title; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_knowledge_entry.summary; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_knowledge_entry.content_md; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_knowledge_entry.entity_json; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_knowledge_entry.embedding; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_knowledge_entry.score_hint; Type: COMMENT; Schema: ecos_data; Owner: postgres

CREATE TABLE IF NOT EXISTS ecos_data.dq_report (
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

-- Name: TABLE dq_report; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_report.id; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_report.report_type; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_report.scope; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_report.start_date; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_report.end_date; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_report.payload_html; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_report.pdf_object_key; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_report.llm_summary; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_report.row_count; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_report.alert_count; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_report.avg_score; Type: COMMENT; Schema: ecos_data; Owner: postgres

CREATE TABLE IF NOT EXISTS ecos_data.dq_rule (
    id character varying(64) NOT NULL,
    rule_name character varying(255) NOT NULL,
    rule_code character varying(191),
    category character varying(32) NOT NULL,
    domain VARCHAR(50),
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
    version_no VARCHAR(20) DEFAULT '1' NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL
);

-- Name: TABLE dq_rule; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_rule.category; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_rule.status; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_rule.source_type; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_rule.source_ref; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_rule.approved_at; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_rule.applied_at; Type: COMMENT; Schema: ecos_data; Owner: postgres

CREATE TABLE IF NOT EXISTS ecos_data.dq_rule_check (
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

-- Name: TABLE dq_rule_check; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_rule_check.trigger_type; Type: COMMENT; Schema: ecos_data; Owner: postgres

CREATE TABLE IF NOT EXISTS ecos_data.dq_rule_version (
    id character varying(64) NOT NULL,
    rule_id character varying(64) NOT NULL,
    version_number integer NOT NULL,
    snapshot jsonb NOT NULL,
    changed_by character varying(128),
    changed_at timestamp without time zone DEFAULT now() NOT NULL,
    change_note text
);

-- Name: TABLE dq_rule_version; Type: COMMENT; Schema: ecos_data; Owner: postgres

CREATE TABLE IF NOT EXISTS ecos_data.dq_schedule (
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

-- Name: TABLE dq_schedule; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_schedule.id; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_schedule.trigger_type; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_schedule.cron_expression; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_schedule.event_type; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_schedule.rule_ids; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_schedule.scope_type; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_schedule.scope_id; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_schedule.enabled; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_schedule.max_runtime_seconds; Type: COMMENT; Schema: ecos_data; Owner: postgres

CREATE TABLE IF NOT EXISTS ecos_data.dq_score_asset (
    asset_type character varying(16) NOT NULL,
    asset_id character varying(64) NOT NULL,
    overall_score double precision NOT NULL,
    rolled_up_scores jsonb DEFAULT '[]'::jsonb NOT NULL,
    last_evaluated_at timestamp without time zone DEFAULT now() NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL
);

-- Name: TABLE dq_score_asset; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_score_asset.asset_type; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_score_asset.asset_id; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_score_asset.overall_score; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_score_asset.rolled_up_scores; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_score_asset.last_evaluated_at; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_score_asset.is_deleted; Type: COMMENT; Schema: ecos_data; Owner: postgres

CREATE TABLE IF NOT EXISTS ecos_data.dq_score_snapshot (
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

-- Name: TABLE dq_score_snapshot; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_score_snapshot.dimension; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_score_snapshot.scope_type; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_score_snapshot.scope_id; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_score_snapshot.score_value; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_score_snapshot.weight; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_score_snapshot.sample_size; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_score_snapshot.distinct_rule_count; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_score_snapshot.metadata; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: dq_score_snapshot_id_seq; Type: SEQUENCE; Schema: ecos_data; Owner: postgres

CREATE SEQUENCE IF NOT EXISTS ecos_data.dq_score_snapshot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: dq_score_snapshot_id_seq; Type: SEQUENCE OWNED BY; Schema: ecos_data; Owner: postgres

ALTER SEQUENCE ecos_data.dq_score_snapshot_id_seq OWNED BY ecos_data.dq_score_snapshot.id;

CREATE TABLE IF NOT EXISTS ecos_data.dq_throttle (
    id character varying(64) NOT NULL,
    scope_type character varying(16) NOT NULL,
    scope_id character varying(64) NOT NULL,
    max_checks_per_day integer DEFAULT 100 NOT NULL,
    current_count integer DEFAULT 0 NOT NULL,
    reset_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

-- Name: TABLE dq_throttle; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_throttle.max_checks_per_day; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_throttle.current_count; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_throttle.reset_at; Type: COMMENT; Schema: ecos_data; Owner: postgres

CREATE TABLE IF NOT EXISTS ecos_data.dq_work_order (
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

-- Name: TABLE dq_work_order; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_work_order.status; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_work_order.handling_mode; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: COLUMN dq_work_order.reject_note; Type: COMMENT; Schema: ecos_data; Owner: postgres

-- Name: dq_score_snapshot id; Type: DEFAULT; Schema: ecos_data; Owner: postgres

ALTER TABLE ONLY ecos_data.dq_score_snapshot ALTER COLUMN id SET DEFAULT nextval('ecos_data.dq_score_snapshot_id_seq'::regclass);

-- Name: dq_alert_record dq_alert_record_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'dq_alert_record_pkey' AND conrelid = 'ecos_data.dq_alert_record'::regclass) THEN
        ALTER TABLE ecos_data.dq_alert_record ADD CONSTRAINT dq_alert_record_pkey PRIMARY KEY (id);
    END IF;
END $$;

-- Name: dq_knowledge_entry dq_knowledge_entry_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'dq_knowledge_entry_pkey' AND conrelid = 'ecos_data.dq_knowledge_entry'::regclass) THEN
        ALTER TABLE ecos_data.dq_knowledge_entry ADD CONSTRAINT dq_knowledge_entry_pkey PRIMARY KEY (id);
    END IF;
END $$;

-- Name: dq_report dq_report_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'dq_report_pkey' AND conrelid = 'ecos_data.dq_report'::regclass) THEN
        ALTER TABLE ecos_data.dq_report ADD CONSTRAINT dq_report_pkey PRIMARY KEY (id);
    END IF;
END $$;

-- Name: dq_rule_check dq_rule_check_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'dq_rule_check_pkey' AND conrelid = 'ecos_data.dq_rule_check'::regclass) THEN
        ALTER TABLE ecos_data.dq_rule_check ADD CONSTRAINT dq_rule_check_pkey PRIMARY KEY (id);
    END IF;
END $$;

-- Name: dq_rule dq_rule_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'dq_rule_pkey' AND conrelid = 'ecos_data.dq_rule'::regclass) THEN
        ALTER TABLE ecos_data.dq_rule ADD CONSTRAINT dq_rule_pkey PRIMARY KEY (id);
    END IF;
END $$;

-- Name: dq_rule dq_rule_rule_code_key; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'dq_rule_rule_code_key' AND conrelid = 'ecos_data.dq_rule'::regclass) THEN
        ALTER TABLE ecos_data.dq_rule ADD CONSTRAINT dq_rule_rule_code_key UNIQUE (rule_code);
    END IF;
END $$;

-- Name: dq_rule_version dq_rule_version_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'dq_rule_version_pkey' AND conrelid = 'ecos_data.dq_rule_version'::regclass) THEN
        ALTER TABLE ecos_data.dq_rule_version ADD CONSTRAINT dq_rule_version_pkey PRIMARY KEY (id);
    END IF;
END $$;

-- Name: dq_rule_version dq_rule_version_rule_id_version_number_key; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'dq_rule_version_rule_id_version_number_key' AND conrelid = 'ecos_data.dq_rule_version'::regclass) THEN
        ALTER TABLE ecos_data.dq_rule_version ADD CONSTRAINT dq_rule_version_rule_id_version_number_key UNIQUE (rule_id, version_number);
    END IF;
END $$;

-- Name: dq_schedule dq_schedule_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'dq_schedule_pkey' AND conrelid = 'ecos_data.dq_schedule'::regclass) THEN
        ALTER TABLE ecos_data.dq_schedule ADD CONSTRAINT dq_schedule_pkey PRIMARY KEY (id);
    END IF;
END $$;

-- Name: dq_score_asset dq_score_asset_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'dq_score_asset_pkey' AND conrelid = 'ecos_data.dq_score_asset'::regclass) THEN
        ALTER TABLE ecos_data.dq_score_asset ADD CONSTRAINT dq_score_asset_pkey PRIMARY KEY (asset_type, asset_id);
    END IF;
END $$;

-- Name: dq_score_snapshot dq_score_snapshot_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'dq_score_snapshot_pkey' AND conrelid = 'ecos_data.dq_score_snapshot'::regclass) THEN
        ALTER TABLE ecos_data.dq_score_snapshot ADD CONSTRAINT dq_score_snapshot_pkey PRIMARY KEY (id);
    END IF;
END $$;

-- Name: dq_throttle dq_throttle_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'dq_throttle_pkey' AND conrelid = 'ecos_data.dq_throttle'::regclass) THEN
        ALTER TABLE ecos_data.dq_throttle ADD CONSTRAINT dq_throttle_pkey PRIMARY KEY (id);
    END IF;
END $$;

-- Name: dq_throttle dq_throttle_scope_type_scope_id_key; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'dq_throttle_scope_type_scope_id_key' AND conrelid = 'ecos_data.dq_throttle'::regclass) THEN
        ALTER TABLE ecos_data.dq_throttle ADD CONSTRAINT dq_throttle_scope_type_scope_id_key UNIQUE (scope_type, scope_id);
    END IF;
END $$;

-- Name: dq_work_order dq_work_order_order_no_key; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'dq_work_order_order_no_key' AND conrelid = 'ecos_data.dq_work_order'::regclass) THEN
        ALTER TABLE ecos_data.dq_work_order ADD CONSTRAINT dq_work_order_order_no_key UNIQUE (order_no);
    END IF;
END $$;

-- Name: dq_work_order dq_work_order_pkey; Type: CONSTRAINT; Schema: ecos_data; Owner: postgres

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'dq_work_order_pkey' AND conrelid = 'ecos_data.dq_work_order'::regclass) THEN
        ALTER TABLE ecos_data.dq_work_order ADD CONSTRAINT dq_work_order_pkey PRIMARY KEY (id);
    END IF;
END $$;

-- Name: idx_dq_alert_level; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_alert_level ON ecos_data.dq_alert_record USING btree (alert_level);

-- Name: idx_dq_alert_rule; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_alert_rule ON ecos_data.dq_alert_record USING btree (rule_id, created_at DESC);

-- Name: idx_dq_alert_status; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_alert_status ON ecos_data.dq_alert_record USING btree (status) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'NOTIFIED'::character varying])::text[]));

-- Name: idx_dq_alert_time; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_alert_time ON ecos_data.dq_alert_record USING btree (created_at DESC);

-- Name: idx_dq_check_rule_time; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_check_rule_time ON ecos_data.dq_rule_check USING btree (rule_id, executed_at DESC);

-- Name: idx_dq_check_time; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_check_time ON ecos_data.dq_rule_check USING btree (executed_at DESC);

-- Name: idx_dq_knowledge_category; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_knowledge_category ON ecos_data.dq_knowledge_entry USING btree (category);

-- Name: idx_dq_knowledge_type_source; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_knowledge_type_source ON ecos_data.dq_knowledge_entry USING btree (source_type, source_id);

-- Name: idx_dq_report_scope; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_report_scope ON ecos_data.dq_report USING btree (scope, end_date DESC);

-- Name: idx_dq_report_type_date; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_report_type_date ON ecos_data.dq_report USING btree (report_type, end_date DESC);

-- Name: idx_dq_rule_category; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_rule_category ON ecos_data.dq_rule USING btree (category) WHERE (is_deleted = false);

-- Name: idx_dq_rule_domain; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_rule_domain ON ecos_data.dq_rule USING btree (domain) WHERE (is_deleted = false);

-- Name: idx_dq_rule_legacy; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_rule_legacy ON ecos_data.dq_rule USING btree (legacy_rule_id);

-- Name: idx_dq_rule_rule_type; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_rule_rule_type ON ecos_data.dq_rule USING btree (rule_type) WHERE (is_deleted = false);

-- Name: idx_dq_rule_status; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_rule_status ON ecos_data.dq_rule USING btree (status) WHERE (is_deleted = false);

-- Name: idx_dq_rule_status_active; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_rule_status_active ON ecos_data.dq_rule USING btree (status, is_active);

-- Name: idx_dq_rule_target; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_rule_target ON ecos_data.dq_rule USING btree (target_kind, target_id) WHERE (is_deleted = false);

-- Name: idx_dq_rule_version_rule; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_rule_version_rule ON ecos_data.dq_rule_version USING btree (rule_id, version_number DESC);

-- Name: idx_dq_schedule_enable; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_schedule_enable ON ecos_data.dq_schedule USING btree (trigger_type) WHERE (enabled AND (is_deleted = false));

-- Name: idx_dq_score_asset_type; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_score_asset_type ON ecos_data.dq_score_asset USING btree (asset_type, asset_id);

-- Name: idx_dq_score_scope_time; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_dq_score_scope_time ON ecos_data.dq_score_snapshot USING btree (scope_type, scope_id, evaluated_at DESC);

-- Name: idx_wo_alert; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_wo_alert ON ecos_data.dq_work_order USING btree (alert_id);

-- Name: idx_wo_asset; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_wo_asset ON ecos_data.dq_work_order USING btree (asset_id) WHERE (is_deleted = false);

-- Name: idx_wo_severity; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_wo_severity ON ecos_data.dq_work_order USING btree (severity, status) WHERE (is_deleted = false);

-- Name: idx_wo_status; Type: INDEX; Schema: ecos_data; Owner: postgres

CREATE INDEX IF NOT EXISTS idx_wo_status ON ecos_data.dq_work_order USING btree (status) WHERE (is_deleted = false);

\unrestrict 83EhjJ61vjpY8X6tCNyXKpDQDbkEsnDNZkE0qdfr7ZIJbSIhFR6Z4Rli5NdUHrx

