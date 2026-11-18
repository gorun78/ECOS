-- ============================================================================
-- 07_ecos_infra.sql — ecos_infra 权威 DDL
-- ============================================================================
-- 由 pg_dump --schema-only 从真实库 sys_man 反向导出后清洗装配（2026-11-17，V349 清理后重导）。
-- 目标 schema = ecos_infra；保留对象 35 个，剔分区子表块 360 个，外键 0 条移入 09。
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

-- outbox_event (TABLE)
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

-- TABLE outbox_event (COMMENT)
COMMENT ON TABLE ecos_infra.outbox_event IS 'Outbox事件表(统一,按月分区)';

--

-- COLUMN outbox_event.event_type (COMMENT)
COMMENT ON COLUMN ecos_infra.outbox_event.event_type IS '事件类型';

--

-- COLUMN outbox_event.aggregate_type (COMMENT)
COMMENT ON COLUMN ecos_infra.outbox_event.aggregate_type IS '聚合类型';

--

-- COLUMN outbox_event.aggregate_id (COMMENT)
COMMENT ON COLUMN ecos_infra.outbox_event.aggregate_id IS '聚合ID';

--

-- COLUMN outbox_event.published (COMMENT)
COMMENT ON COLUMN ecos_infra.outbox_event.published IS '是否已发布';

--

-- COLUMN outbox_event.kafka_topic (COMMENT)
COMMENT ON COLUMN ecos_infra.outbox_event.kafka_topic IS 'Kafka主题';

--

-- outbox_event_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_infra.outbox_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- outbox_event_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_infra.outbox_event_id_seq OWNED BY ecos_infra.outbox_event.id;

--

-- outbox_event_default (TABLE)
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

-- saga_instance (TABLE)
CREATE TABLE ecos_infra.saga_instance (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    saga_type character varying(128) NOT NULL,
    status character varying(32) DEFAULT 'STARTED',
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

-- TABLE saga_instance (COMMENT)
COMMENT ON TABLE ecos_infra.saga_instance IS 'Saga实例表';

--

-- COLUMN saga_instance.saga_type (COMMENT)
COMMENT ON COLUMN ecos_infra.saga_instance.saga_type IS 'Saga类型';

--

-- COLUMN saga_instance.status (COMMENT)
COMMENT ON COLUMN ecos_infra.saga_instance.status IS '状态: STARTED/COMPENSATING/COMPLETED/FAILED';

--

-- schema_version (TABLE)
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

-- TABLE schema_version (COMMENT)
COMMENT ON TABLE ecos_infra.schema_version IS 'Schema版本追踪表';

--

-- outbox_event_default (TABLE ATTACH)
ALTER TABLE ONLY ecos_infra.outbox_event ATTACH PARTITION ecos_infra.outbox_event_default DEFAULT;

--

-- outbox_event id (DEFAULT)
ALTER TABLE ONLY ecos_infra.outbox_event ALTER COLUMN id SET DEFAULT nextval('ecos_infra.outbox_event_id_seq'::regclass);

--

-- outbox_event outbox_event_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_infra.outbox_event
    ADD CONSTRAINT outbox_event_pkey PRIMARY KEY (id, created_at);

--

-- outbox_event_default outbox_event_default_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_infra.outbox_event_default
    ADD CONSTRAINT outbox_event_default_pkey PRIMARY KEY (id, created_at);

--

-- saga_instance saga_instance_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_infra.saga_instance
    ADD CONSTRAINT saga_instance_pkey PRIMARY KEY (id);

--

-- schema_version schema_version_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_infra.schema_version
    ADD CONSTRAINT schema_version_pkey PRIMARY KEY (id);

--

-- idx_infra_outbox_aggregate (INDEX)
CREATE INDEX idx_infra_outbox_aggregate ON ONLY ecos_infra.outbox_event USING btree (aggregate_type, aggregate_id);

--

-- idx_infra_outbox_published (INDEX)
CREATE INDEX idx_infra_outbox_published ON ONLY ecos_infra.outbox_event USING btree (published) WHERE (NOT published);

--

-- idx_infra_outbox_type (INDEX)
CREATE INDEX idx_infra_outbox_type ON ONLY ecos_infra.outbox_event USING btree (event_type);

--

-- idx_infra_saga_status (INDEX)
CREATE INDEX idx_infra_saga_status ON ecos_infra.saga_instance USING btree (status);

--

-- idx_infra_saga_type (INDEX)
CREATE INDEX idx_infra_saga_type ON ecos_infra.saga_instance USING btree (saga_type);

--

-- idx_infra_schemaver_name_ver (INDEX)
CREATE UNIQUE INDEX idx_infra_schemaver_name_ver ON ecos_infra.schema_version USING btree (schema_name, version);

--

-- outbox_event_default_aggregate_type_aggregate_id_idx (INDEX)
CREATE INDEX outbox_event_default_aggregate_type_aggregate_id_idx ON ecos_infra.outbox_event_default USING btree (aggregate_type, aggregate_id);

--

-- outbox_event_default_event_type_idx (INDEX)
CREATE INDEX outbox_event_default_event_type_idx ON ecos_infra.outbox_event_default USING btree (event_type);

--

-- outbox_event_default_published_idx (INDEX)
CREATE INDEX outbox_event_default_published_idx ON ecos_infra.outbox_event_default USING btree (published) WHERE (NOT published);

--

-- outbox_event_default_aggregate_type_aggregate_id_idx (INDEX ATTACH)
ALTER INDEX ecos_infra.idx_infra_outbox_aggregate ATTACH PARTITION ecos_infra.outbox_event_default_aggregate_type_aggregate_id_idx;

--

-- outbox_event_default_event_type_idx (INDEX ATTACH)
ALTER INDEX ecos_infra.idx_infra_outbox_type ATTACH PARTITION ecos_infra.outbox_event_default_event_type_idx;

--

-- outbox_event_default_pkey (INDEX ATTACH)
ALTER INDEX ecos_infra.outbox_event_pkey ATTACH PARTITION ecos_infra.outbox_event_default_pkey;

--

-- outbox_event_default_published_idx (INDEX ATTACH)
ALTER INDEX ecos_infra.idx_infra_outbox_published ATTACH PARTITION ecos_infra.outbox_event_default_published_idx;

--
-- PostgreSQL database dump complete
--

