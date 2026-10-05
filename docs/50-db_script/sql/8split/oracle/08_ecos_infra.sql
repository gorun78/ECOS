-- ============================================================================
-- ECOS Infrastructure Domain — Oracle DDL
-- Schema: ecos_infra
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

BEGIN
   EXECUTE IMMEDIATE 'CREATE USER ecos_infra IDENTIFIED BY ecos_infra DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE INDEX TO ecos_infra;

-- ============================================
-- Sequence for outbox_event
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'CREATE SEQUENCE ecos_infra.seq_outbox_event START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: outbox_event
-- Description: Outbox事件表(统一,按月分区)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_infra.outbox_event (
    id             NUMBER(19)      PRIMARY KEY,
    event_type     VARCHAR2(128)   NOT NULL,
    aggregate_type VARCHAR2(128)   NOT NULL,
    aggregate_id   VARCHAR2(64)    NOT NULL,
    payload        CLOB            DEFAULT ''{}'',
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    published      NUMBER(1)       DEFAULT 0,
    published_at   TIMESTAMP WITH TIME ZONE,
    kafka_topic    VARCHAR2(128),
    kafka_key      VARCHAR2(128)
)
PARTITION BY RANGE (created_at)
(
    PARTITION outbox_event_2025_01 VALUES LESS THAN (TO_DATE(''2025-02-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2025_02 VALUES LESS THAN (TO_DATE(''2025-03-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2025_03 VALUES LESS THAN (TO_DATE(''2025-04-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2025_04 VALUES LESS THAN (TO_DATE(''2025-05-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2025_05 VALUES LESS THAN (TO_DATE(''2025-06-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2025_06 VALUES LESS THAN (TO_DATE(''2025-07-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2025_07 VALUES LESS THAN (TO_DATE(''2025-08-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2025_08 VALUES LESS THAN (TO_DATE(''2025-09-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2025_09 VALUES LESS THAN (TO_DATE(''2025-10-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2025_10 VALUES LESS THAN (TO_DATE(''2025-11-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2025_11 VALUES LESS THAN (TO_DATE(''2025-12-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2025_12 VALUES LESS THAN (TO_DATE(''2026-01-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2026_01 VALUES LESS THAN (TO_DATE(''2026-02-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2026_02 VALUES LESS THAN (TO_DATE(''2026-03-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2026_03 VALUES LESS THAN (TO_DATE(''2026-04-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2026_04 VALUES LESS THAN (TO_DATE(''2026-05-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2026_05 VALUES LESS THAN (TO_DATE(''2026-06-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2026_06 VALUES LESS THAN (TO_DATE(''2026-07-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2026_07 VALUES LESS THAN (TO_DATE(''2026-08-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2026_08 VALUES LESS THAN (TO_DATE(''2026-09-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2026_09 VALUES LESS THAN (TO_DATE(''2026-10-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2026_10 VALUES LESS THAN (TO_DATE(''2026-11-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2026_11 VALUES LESS THAN (TO_DATE(''2026-12-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2026_12 VALUES LESS THAN (TO_DATE(''2027-01-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2027_01 VALUES LESS THAN (TO_DATE(''2027-02-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2027_02 VALUES LESS THAN (TO_DATE(''2027-03-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2027_03 VALUES LESS THAN (TO_DATE(''2027-04-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2027_04 VALUES LESS THAN (TO_DATE(''2027-05-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2027_05 VALUES LESS THAN (TO_DATE(''2027-06-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2027_06 VALUES LESS THAN (TO_DATE(''2027-07-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2027_07 VALUES LESS THAN (TO_DATE(''2027-08-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2027_08 VALUES LESS THAN (TO_DATE(''2027-09-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2027_09 VALUES LESS THAN (TO_DATE(''2027-10-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2027_10 VALUES LESS THAN (TO_DATE(''2027-11-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2027_11 VALUES LESS THAN (TO_DATE(''2027-12-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_2027_12 VALUES LESS THAN (TO_DATE(''2028-01-01'', ''YYYY-MM-DD'')),
    PARTITION outbox_event_default VALUES LESS THAN (MAXVALUE)
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_infra.outbox_event IS 'Outbox事件表(统一,按月分区)';
COMMENT ON COLUMN ecos_infra.outbox_event.event_type IS '事件类型';
COMMENT ON COLUMN ecos_infra.outbox_event.aggregate_type IS '聚合类型';
COMMENT ON COLUMN ecos_infra.outbox_event.aggregate_id IS '聚合ID';
COMMENT ON COLUMN ecos_infra.outbox_event.published IS '是否已发布';
COMMENT ON COLUMN ecos_infra.outbox_event.kafka_topic IS 'Kafka主题';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_infra_outbox_type ON ecos_infra.outbox_event(event_type)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_infra_outbox_unpublished ON ecos_infra.outbox_event(published)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_infra_outbox_aggregate ON ecos_infra.outbox_event(aggregate_type, aggregate_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

CREATE OR REPLACE TRIGGER ecos_infra.trg_outbox_event_id
BEFORE INSERT ON ecos_infra.outbox_event
FOR EACH ROW
BEGIN
   IF :NEW.id IS NULL THEN
      :NEW.id := ecos_infra.seq_outbox_event.NEXTVAL;
   END IF;
END;
/

-- ============================================
-- Table: saga_instance
-- Description: Saga实例表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_infra.saga_instance (
    id                RAW(16)       PRIMARY KEY DEFAULT SYS_GUID(),
    saga_type         VARCHAR2(128) NOT NULL,
    status            VARCHAR2(32)  DEFAULT ''STARTED'',
    current_step      NUMBER(10)    DEFAULT 0,
    total_steps       NUMBER(10),
    input_data        CLOB          DEFAULT ''{}'',
    compensation_data CLOB          DEFAULT ''{}'',
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    completed_at      TIMESTAMP WITH TIME ZONE,
    error_message     CLOB
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_infra.saga_instance IS 'Saga实例表';
COMMENT ON COLUMN ecos_infra.saga_instance.saga_type IS 'Saga类型';
COMMENT ON COLUMN ecos_infra.saga_instance.status IS '状态: STARTED/COMPENSATING/COMPLETED/FAILED';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_infra_saga_type ON ecos_infra.saga_instance(saga_type)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_infra_saga_status ON ecos_infra.saga_instance(status)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: schema_version
-- Description: Schema版本追踪表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_infra.schema_version (
    id              VARCHAR2(64)  PRIMARY KEY,
    schema_name     VARCHAR2(64)  NOT NULL,
    version         VARCHAR2(32)  NOT NULL,
    description     CLOB,
    applied_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    applied_by      VARCHAR2(64),
    checksum        VARCHAR2(64),
    execution_time_ms NUMBER(10)
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_infra.schema_version IS 'Schema版本追踪表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX idx_infra_schemaver_name_ver ON ecos_infra.schema_version(schema_name, version)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
