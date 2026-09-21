-- ============================================================
-- V149__ecos_interface_ref.sql — 场景接口引用真表（PMO-60 v2.0）
-- 场景绑定的外部接口引用（HTTP/KAFKA/REST/MQ/AMQP）独立成表。
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_interface_ref (
    id             VARCHAR(64) PRIMARY KEY,          -- ifc_xxxxxxxx
    name           VARCHAR(255) NOT NULL UNIQUE,
    interface_type VARCHAR(32) NOT NULL,             -- HTTP|KAFKA|REST|MQ|AMQP
    endpoint       VARCHAR(512) NOT NULL,            -- host:port/path 或 topic
    method         VARCHAR(16),
    timeout_ms     INT NOT NULL DEFAULT 3000,
    create_time    TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time    TIMESTAMP NOT NULL DEFAULT NOW(),
    create_by      VARCHAR(64) NOT NULL DEFAULT 'system',
    update_by      VARCHAR(64) NOT NULL DEFAULT 'system',
    is_deleted     SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ifc_ref_type ON ecos_interface_ref (interface_type, is_deleted);

COMMENT ON TABLE ecos_interface_ref IS '场景绑定的外部接口引用真表';
