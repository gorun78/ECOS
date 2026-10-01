-- ============================================================================
-- ECOS Infrastructure Domain — MySQL DDL
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

-- ============================================
-- Table: outbox_event
-- Description: Outbox事件表(统一,按月分区)
-- ============================================
CREATE TABLE IF NOT EXISTS outbox_event (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    event_type     VARCHAR(128)  NOT NULL COMMENT '事件类型',
    aggregate_type VARCHAR(128)  NOT NULL COMMENT '聚合类型',
    aggregate_id   VARCHAR(64)   NOT NULL COMMENT '聚合ID',
    payload        JSON          COMMENT '事件载荷',
    created_at     DATETIME(6)   NOT NULL DEFAULT NOW(6) COMMENT '创建时间',
    published      TINYINT(1)    DEFAULT 0 COMMENT '是否已发布',
    published_at   DATETIME(6)   COMMENT '发布时间',
    kafka_topic    VARCHAR(128)  COMMENT 'Kafka主题',
    kafka_key      VARCHAR(128)  COMMENT 'Kafka键',
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE COLUMNS(created_at) (
    PARTITION p2025_01 VALUES LESS THAN ('2025-02-01'),
    PARTITION p2025_02 VALUES LESS THAN ('2025-03-01'),
    PARTITION p2025_03 VALUES LESS THAN ('2025-04-01'),
    PARTITION p2025_04 VALUES LESS THAN ('2025-05-01'),
    PARTITION p2025_05 VALUES LESS THAN ('2025-06-01'),
    PARTITION p2025_06 VALUES LESS THAN ('2025-07-01'),
    PARTITION p2025_07 VALUES LESS THAN ('2025-08-01'),
    PARTITION p2025_08 VALUES LESS THAN ('2025-09-01'),
    PARTITION p2025_09 VALUES LESS THAN ('2025-10-01'),
    PARTITION p2025_10 VALUES LESS THAN ('2025-11-01'),
    PARTITION p2025_11 VALUES LESS THAN ('2025-12-01'),
    PARTITION p2025_12 VALUES LESS THAN ('2026-01-01'),
    PARTITION p2026_01 VALUES LESS THAN ('2026-02-01'),
    PARTITION p2026_02 VALUES LESS THAN ('2026-03-01'),
    PARTITION p2026_03 VALUES LESS THAN ('2026-04-01'),
    PARTITION p2026_04 VALUES LESS THAN ('2026-05-01'),
    PARTITION p2026_05 VALUES LESS THAN ('2026-06-01'),
    PARTITION p2026_06 VALUES LESS THAN ('2026-07-01'),
    PARTITION p2026_07 VALUES LESS THAN ('2026-08-01'),
    PARTITION p2026_08 VALUES LESS THAN ('2026-09-01'),
    PARTITION p2026_09 VALUES LESS THAN ('2026-10-01'),
    PARTITION p2026_10 VALUES LESS THAN ('2026-11-01'),
    PARTITION p2026_11 VALUES LESS THAN ('2026-12-01'),
    PARTITION p2026_12 VALUES LESS THAN ('2027-01-01'),
    PARTITION pmax VALUES LESS THAN MAXVALUE
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox事件表(统一,按月分区)';

CREATE INDEX idx_infra_outbox_type     ON outbox_event(event_type);
CREATE INDEX idx_infra_outbox_aggregate ON outbox_event(aggregate_type, aggregate_id);

-- ============================================
-- Table: saga_instance
-- Description: Saga实例表
-- ============================================
CREATE TABLE IF NOT EXISTS saga_instance (
    id                CHAR(36)      NOT NULL DEFAULT (UUID()),
    saga_type         VARCHAR(128)  NOT NULL COMMENT 'Saga类型',
    status            VARCHAR(32)   DEFAULT 'STARTED' COMMENT '状态: STARTED/COMPENSATING/COMPLETED/FAILED',
    current_step      INT           DEFAULT 0,
    total_steps       INT,
    input_data        JSON          COMMENT '输入数据',
    compensation_data JSON          COMMENT '补偿数据',
    created_at        DATETIME(6)   NOT NULL DEFAULT NOW(6),
    updated_at        DATETIME(6)   NOT NULL DEFAULT NOW(6),
    completed_at      DATETIME(6),
    error_message     LONGTEXT,
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Saga实例表';

CREATE INDEX idx_infra_saga_type   ON saga_instance(saga_type);
CREATE INDEX idx_infra_saga_status ON saga_instance(status);

-- ============================================
-- Table: schema_version
-- Description: Schema版本追踪表
-- ============================================
CREATE TABLE IF NOT EXISTS schema_version (
    id              VARCHAR(64)   NOT NULL,
    schema_name     VARCHAR(64)   NOT NULL,
    version         VARCHAR(32)   NOT NULL,
    description     LONGTEXT,
    applied_at      DATETIME      NOT NULL DEFAULT NOW(),
    applied_by      VARCHAR(64),
    checksum        VARCHAR(64),
    execution_time_ms INT,
    PRIMARY KEY (id),
    UNIQUE KEY uk_schemaver_name_ver (schema_name, version)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Schema版本追踪表';
