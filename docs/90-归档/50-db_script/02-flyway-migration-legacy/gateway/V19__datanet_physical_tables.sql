-- =====================================================
-- V19: datanet 物理表注册 — 数据源+资源+字段+目录
-- 支持 Oracle/MySQL/PostgreSQL/SQLServer/达梦/金仓 JDBC 元数据采集
-- =====================================================

-- 1. 数据源注册表
CREATE TABLE IF NOT EXISTS td_datasource (
    datasource_id      VARCHAR(64)  PRIMARY KEY,
    datasource_name    VARCHAR(128) NOT NULL,
    datasource_type    VARCHAR(32)  NOT NULL DEFAULT 'JDBC',
    org_id             VARCHAR(64),
    node_id            VARCHAR(64),
    description        TEXT,
    connection_config  TEXT         NOT NULL,
    status             VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    tags               VARCHAR(256),
    last_test_time     TIMESTAMP,
    last_test_result   BOOLEAN,
    last_test_message  TEXT,
    create_by          VARCHAR(64),
    create_time        TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_by          VARCHAR(64),
    update_time        TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE td_datasource IS '数据源注册表 — 支持 Oracle/MySQL/PG/SQLServer/达梦/金仓';

-- 2. 数据资源表 (发现的表/视图)
CREATE TABLE IF NOT EXISTS td_data_resource (
    resource_id        VARCHAR(64)  PRIMARY KEY,
    resource_name      VARCHAR(256) NOT NULL,
    resource_type      VARCHAR(32)  NOT NULL DEFAULT 'TABLE',
    org_id             VARCHAR(64),
    org_name           VARCHAR(128),
    datasource_id      VARCHAR(64)  NOT NULL,
    source_path        VARCHAR(512),
    description        TEXT,
    tags               VARCHAR(256),
    status             VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    field_count        INTEGER      DEFAULT 0,
    record_count       BIGINT       DEFAULT 0,
    last_sync_time     TIMESTAMP,
    create_by          VARCHAR(64),
    create_time        TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_by          VARCHAR(64),
    update_time        TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_data_res_ds ON td_data_resource(datasource_id);
COMMENT ON TABLE td_data_resource IS '数据资源表 — JDBC 发现的 TABLE/VIEW';

-- 3. 数据字段表 (列信息)
CREATE TABLE IF NOT EXISTS td_data_field (
    field_id           VARCHAR(64)  PRIMARY KEY,
    resource_id        VARCHAR(64)  NOT NULL,
    field_name         VARCHAR(256) NOT NULL,
    field_alias        VARCHAR(256),
    field_type         VARCHAR(64),
    field_length       INTEGER,
    data_precision     INTEGER,
    nullable           SMALLINT     DEFAULT 1,
    is_primary_key     SMALLINT     DEFAULT 0,
    default_value      VARCHAR(256),
    description        TEXT,
    field_order        INTEGER      DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_data_field_res ON td_data_field(resource_id);
COMMENT ON TABLE td_data_field IS '数据字段表 — 表的列级元数据';

-- 4. 目录索引表
CREATE TABLE IF NOT EXISTS td_catalog_item (
    catalog_id         VARCHAR(64)  PRIMARY KEY,
    resource_id        VARCHAR(64)  NOT NULL,
    resource_name      VARCHAR(256) NOT NULL,
    resource_type      VARCHAR(32)  NOT NULL DEFAULT 'TABLE',
    org_name           VARCHAR(128),
    description        TEXT,
    tags               VARCHAR(256),
    category_path      VARCHAR(256),
    access_type        VARCHAR(32)  DEFAULT 'READ',
    data_format        VARCHAR(32),
    field_count        INTEGER      DEFAULT 0,
    record_count       BIGINT       DEFAULT 0,
    last_updated       TIMESTAMP    NOT NULL DEFAULT NOW(),
    status             VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
);
CREATE INDEX IF NOT EXISTS idx_catalog_res ON td_catalog_item(resource_id);
CREATE INDEX IF NOT EXISTS idx_catalog_type ON td_catalog_item(resource_type);
COMMENT ON TABLE td_catalog_item IS '数据目录索引表 — 全局搜索入口';
