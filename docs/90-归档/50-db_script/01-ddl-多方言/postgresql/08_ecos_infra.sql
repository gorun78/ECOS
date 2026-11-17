-- ============================================================================
-- ECOS Infrastructure Domain — PostgreSQL DDL
-- Schema: ecos_infra
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS ecos_infra;

-- ============================================
-- Table: outbox_event
-- Description: Outbox事件表(统一,按月分区)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_infra.outbox_event (
    id             BIGSERIAL,
    event_type     VARCHAR(128)   NOT NULL,
    aggregate_type VARCHAR(128)   NOT NULL,
    aggregate_id   VARCHAR(64)    NOT NULL,
    payload        JSONB          DEFAULT '{}',
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    published      BOOLEAN        DEFAULT FALSE,
    published_at   TIMESTAMPTZ,
    kafka_topic    VARCHAR(128),
    kafka_key      VARCHAR(128),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE  ecos_infra.outbox_event IS 'Outbox事件表(统一,按月分区)';
COMMENT ON COLUMN ecos_infra.outbox_event.event_type IS '事件类型';
COMMENT ON COLUMN ecos_infra.outbox_event.aggregate_type IS '聚合类型';
COMMENT ON COLUMN ecos_infra.outbox_event.aggregate_id IS '聚合ID';
COMMENT ON COLUMN ecos_infra.outbox_event.published IS '是否已发布';
COMMENT ON COLUMN ecos_infra.outbox_event.kafka_topic IS 'Kafka主题';

CREATE INDEX IF NOT EXISTS idx_infra_outbox_type      ON ecos_infra.outbox_event(event_type);
CREATE INDEX IF NOT EXISTS idx_infra_outbox_published  ON ecos_infra.outbox_event(published) WHERE NOT published;
CREATE INDEX IF NOT EXISTS idx_infra_outbox_aggregate  ON ecos_infra.outbox_event(aggregate_type, aggregate_id);

-- Monthly partitions 2025-2027
DO $$
BEGIN
    FOR y IN 2025..2027 LOOP
        FOR m IN 1..12 LOOP
            EXECUTE format(
                'CREATE TABLE IF NOT EXISTS ecos_infra.outbox_event_%s_%s PARTITION OF ecos_infra.outbox_event FOR VALUES FROM (%L) TO (%L)',
                y, lpad(m::text, 2, '0'),
                make_date(y, m, 1),
                make_date(y, m, 1) + interval '1 month'
            );
        END LOOP;
    END LOOP;
    EXECUTE 'CREATE TABLE IF NOT EXISTS ecos_infra.outbox_event_default PARTITION OF ecos_infra.outbox_event DEFAULT';
END $$;

-- ============================================
-- Table: saga_instance
-- Description: Saga实例表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_infra.saga_instance (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_type         VARCHAR(128) NOT NULL,
    status            VARCHAR(32)  DEFAULT 'STARTED',
    current_step      INT          DEFAULT 0,
    total_steps       INT,
    input_data        JSONB        DEFAULT '{}',
    compensation_data JSONB        DEFAULT '{}',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMPTZ,
    error_message     TEXT
);

COMMENT ON TABLE  ecos_infra.saga_instance IS 'Saga实例表';
COMMENT ON COLUMN ecos_infra.saga_instance.saga_type IS 'Saga类型';
COMMENT ON COLUMN ecos_infra.saga_instance.status IS '状态: STARTED/COMPENSATING/COMPLETED/FAILED';
CREATE INDEX IF NOT EXISTS idx_infra_saga_type   ON ecos_infra.saga_instance(saga_type);
CREATE INDEX IF NOT EXISTS idx_infra_saga_status ON ecos_infra.saga_instance(status);

-- ============================================
-- Table: schema_version
-- Description: Schema版本追踪表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_infra.schema_version (
    id              VARCHAR(64)  PRIMARY KEY,
    schema_name     VARCHAR(64)  NOT NULL,
    version         VARCHAR(32)  NOT NULL,
    description     TEXT,
    applied_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    applied_by      VARCHAR(64),
    checksum        VARCHAR(64),
    execution_time_ms INT
);

COMMENT ON TABLE  ecos_infra.schema_version IS 'Schema版本追踪表';
CREATE UNIQUE INDEX IF NOT EXISTS idx_infra_schemaver_name_ver ON ecos_infra.schema_version(schema_name, version);
