-- ============================================================================
-- ECOS Data Domain — PostgreSQL DDL
-- Schema: ecos_data
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS ecos_data;

-- ============================================
-- Table: td_datasource
-- Description: 数据源表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_data.td_datasource (
    datasource_id     VARCHAR(64)  PRIMARY KEY,
    datasource_name   VARCHAR(128) NOT NULL,
    datasource_type   VARCHAR(32)  DEFAULT 'JDBC',
    org_id            VARCHAR(64),
    node_id           VARCHAR(64),
    description       TEXT,
    connection_config TEXT,
    status            VARCHAR(16)  DEFAULT 'ACTIVE',
    tags              VARCHAR(256),
    last_test_time    TIMESTAMP,
    last_test_result  BOOLEAN,
    last_test_message TEXT,
    tenant_id         VARCHAR(64),
    create_by         VARCHAR(64),
    create_time       TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_by         VARCHAR(64),
    update_time       TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_data.td_datasource IS '数据源表';
COMMENT ON COLUMN ecos_data.td_datasource.datasource_type IS '类型: JDBC/API/FILE/MQ';
CREATE INDEX IF NOT EXISTS idx_data_ds_org    ON ecos_data.td_datasource(org_id);
CREATE INDEX IF NOT EXISTS idx_data_ds_status ON ecos_data.td_datasource(status);
CREATE INDEX IF NOT EXISTS idx_data_ds_tenant ON ecos_data.td_datasource(tenant_id);

-- ============================================
-- Table: td_data_resource
-- Description: 数据资源表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_data.td_data_resource (
    resource_id     VARCHAR(64)  PRIMARY KEY,
    resource_name   VARCHAR(256) NOT NULL,
    resource_type   VARCHAR(32)  DEFAULT 'TABLE',
    org_id          VARCHAR(64),
    org_name        VARCHAR(128),
    datasource_id   VARCHAR(64),
    source_path     VARCHAR(512),
    description     TEXT,
    tags            VARCHAR(256),
    status          VARCHAR(16)  DEFAULT 'ACTIVE',
    field_count     INTEGER      DEFAULT 0,
    record_count    BIGINT       DEFAULT 0,
    last_sync_time  TIMESTAMP,
    tenant_id       VARCHAR(64),
    create_by       VARCHAR(64),
    create_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_by       VARCHAR(64),
    update_time     TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_data.td_data_resource IS '数据资源表';
CREATE INDEX IF NOT EXISTS idx_data_res_ds     ON ecos_data.td_data_resource(datasource_id);
CREATE INDEX IF NOT EXISTS idx_data_res_org    ON ecos_data.td_data_resource(org_id);
CREATE INDEX IF NOT EXISTS idx_data_res_tenant ON ecos_data.td_data_resource(tenant_id);

-- ============================================
-- Table: td_data_field
-- Description: 数据字段表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_data.td_data_field (
    field_id      VARCHAR(64)  PRIMARY KEY,
    resource_id   VARCHAR(64)  NOT NULL,
    field_name    VARCHAR(256) NOT NULL,
    field_alias   VARCHAR(256),
    field_type    VARCHAR(64),
    field_length  INTEGER,
    data_precision INTEGER,
    nullable      SMALLINT     DEFAULT 1,
    is_primary_key SMALLINT    DEFAULT 0,
    default_value VARCHAR(256),
    description   TEXT,
    field_order   INTEGER      DEFAULT 0
);

COMMENT ON TABLE  ecos_data.td_data_field IS '数据字段表';
CREATE INDEX IF NOT EXISTS idx_data_field_res ON ecos_data.td_data_field(resource_id);

-- ============================================
-- Table: td_catalog_item
-- Description: 数据目录项表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_data.td_catalog_item (
    catalog_id      VARCHAR(64)  PRIMARY KEY,
    resource_id     VARCHAR(64),
    resource_name   VARCHAR(256),
    resource_type   VARCHAR(32)  DEFAULT 'TABLE',
    org_name        VARCHAR(128),
    description     TEXT,
    tags            VARCHAR(256),
    category_path   VARCHAR(256),
    access_type     VARCHAR(32)  DEFAULT 'READ',
    data_format     VARCHAR(32),
    field_count     INTEGER      DEFAULT 0,
    record_count    BIGINT       DEFAULT 0,
    last_updated    TIMESTAMP,
    status          VARCHAR(16)  DEFAULT 'ACTIVE',
    tenant_id       VARCHAR(64)
);

COMMENT ON TABLE  ecos_data.td_catalog_item IS '数据目录项表';
CREATE INDEX IF NOT EXISTS idx_data_cat_res   ON ecos_data.td_catalog_item(resource_id);
CREATE INDEX IF NOT EXISTS idx_data_cat_type  ON ecos_data.td_catalog_item(resource_type);
CREATE INDEX IF NOT EXISTS idx_data_cat_path  ON ecos_data.td_catalog_item(category_path);

-- ============================================
-- Table: td_data_category
-- Description: 数据分类表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_data.td_data_category (
    category_id   VARCHAR(64)  PRIMARY KEY,
    category_name VARCHAR(256) NOT NULL,
    parent_id     VARCHAR(64),
    path          VARCHAR(512),
    level         INT          DEFAULT 1,
    sort_order    INT          DEFAULT 0,
    description   TEXT,
    status        VARCHAR(16)  DEFAULT 'ACTIVE',
    tenant_id     VARCHAR(64),
    create_by     VARCHAR(64),
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_by     VARCHAR(64),
    update_time   TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_data.td_data_category IS '数据分类表';
CREATE INDEX IF NOT EXISTS idx_data_cat_parent ON ecos_data.td_data_category(parent_id);

-- ============================================
-- Table: ecos_pipeline_definition
-- Description: 管道定义表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_data.ecos_pipeline_definition (
    id          VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    definition  JSONB        DEFAULT '{}',
    status      VARCHAR(32)  DEFAULT 'DRAFT',
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_data.ecos_pipeline_definition IS '管道定义表';
CREATE INDEX IF NOT EXISTS idx_data_pdef_status ON ecos_data.ecos_pipeline_definition(status);
CREATE INDEX IF NOT EXISTS idx_data_pdef_tenant ON ecos_data.ecos_pipeline_definition(tenant_id);

-- ============================================
-- Table: ecos_pipeline_execution
-- Description: 管道执行表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_data.ecos_pipeline_execution (
    id              VARCHAR(64)  PRIMARY KEY,
    pipeline_id     VARCHAR(64)  NOT NULL,
    status          VARCHAR(32)  DEFAULT 'PENDING',
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    error_message   TEXT,
    rows_processed  BIGINT       DEFAULT 0,
    tenant_id       VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_data.ecos_pipeline_execution IS '管道执行表';
CREATE INDEX IF NOT EXISTS idx_data_pexec_pipeline ON ecos_data.ecos_pipeline_execution(pipeline_id);
CREATE INDEX IF NOT EXISTS idx_data_pexec_status   ON ecos_data.ecos_pipeline_execution(status);
CREATE INDEX IF NOT EXISTS idx_data_pexec_tenant   ON ecos_data.ecos_pipeline_execution(tenant_id);

-- ============================================
-- Table: ecos_pipeline_node
-- Description: 管道节点表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_data.ecos_pipeline_node (
    id            VARCHAR(64)  PRIMARY KEY,
    definition_id VARCHAR(64)  NOT NULL,
    node_id       VARCHAR(64)  NOT NULL,
    type          VARCHAR(64)  DEFAULT 'TRANSFORM_SQL',
    config        JSONB        DEFAULT '{}',
    depends_on    JSONB        DEFAULT '[]',
    position_x    INTEGER      DEFAULT 0,
    position_y    INTEGER      DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_data.ecos_pipeline_node IS '管道节点表';
CREATE INDEX IF NOT EXISTS idx_data_pnode_def ON ecos_data.ecos_pipeline_node(definition_id);

-- ============================================
-- Table: ecos_pipeline_edge
-- Description: 管道边表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_data.ecos_pipeline_edge (
    id            VARCHAR(64)  PRIMARY KEY,
    definition_id VARCHAR(64)  NOT NULL,
    from_node_id  VARCHAR(64)  NOT NULL,
    to_node_id    VARCHAR(64)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_data.ecos_pipeline_edge IS '管道边表';
CREATE INDEX IF NOT EXISTS idx_data_pedge_def ON ecos_data.ecos_pipeline_edge(definition_id);

-- ============================================
-- Table: ecos_dq_rule
-- Description: 数据质量规则表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_data.ecos_dq_rule (
    id              BIGSERIAL     PRIMARY KEY,
    name            VARCHAR(255)  NOT NULL,
    description     TEXT,
    rule_type       VARCHAR(64)   DEFAULT 'NOT_NULL',
    config_json     TEXT,
    severity        VARCHAR(32)   DEFAULT 'HIGH',
    enabled         BOOLEAN       DEFAULT TRUE,
    target_entity   VARCHAR(128),
    target_field    VARCHAR(128),
    rule_expression TEXT,
    code            VARCHAR(64),
    status          VARCHAR(32)   DEFAULT 'ACTIVE',
    tenant_id       VARCHAR(64),
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_data.ecos_dq_rule IS '数据质量规则表';
CREATE INDEX IF NOT EXISTS idx_data_dq_type   ON ecos_data.ecos_dq_rule(rule_type);
CREATE INDEX IF NOT EXISTS idx_data_dq_tenant ON ecos_data.ecos_dq_rule(tenant_id);

-- ============================================
-- Table: ecos_dq_issue
-- Description: 数据质量问题表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_data.ecos_dq_issue (
    id          BIGSERIAL    PRIMARY KEY,
    rule_id     BIGINT       NOT NULL,
    asset_id    VARCHAR(255) DEFAULT '',
    description TEXT,
    status      VARCHAR(32)  DEFAULT 'open',
    severity    VARCHAR(32)  DEFAULT 'HIGH',
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP
);

COMMENT ON TABLE  ecos_data.ecos_dq_issue IS '数据质量问题表';
CREATE INDEX IF NOT EXISTS idx_data_dqi_rule ON ecos_data.ecos_dq_issue(rule_id);
ALTER TABLE ecos_data.ecos_dq_issue
    ADD CONSTRAINT fk_data_dqi_rule FOREIGN KEY (rule_id) REFERENCES ecos_data.ecos_dq_rule(id) ON DELETE CASCADE;

-- ============================================
-- Table: ecos_dq_execution_result
-- Description: 数据质量执行结果表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_data.ecos_dq_execution_result (
    id            VARCHAR(64) PRIMARY KEY,
    rule_id       VARCHAR(64),
    passed        BOOLEAN     DEFAULT FALSE,
    total_rows    INTEGER     DEFAULT 0,
    failed_rows   INTEGER     DEFAULT 0,
    error_details TEXT,
    executed_at   TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_data.ecos_dq_execution_result IS '数据质量执行结果表';
CREATE INDEX IF NOT EXISTS idx_data_dqr_rule ON ecos_data.ecos_dq_execution_result(rule_id);
CREATE INDEX IF NOT EXISTS idx_data_dqr_time ON ecos_data.ecos_dq_execution_result(executed_at DESC);

-- ============================================
-- Table: ecos_cognitive_rule
-- Description: 认知规则表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_data.ecos_cognitive_rule (
    id             BIGSERIAL     PRIMARY KEY,
    rule_name      VARCHAR(128)  NOT NULL,
    rule_type      VARCHAR(32),
    condition_expr TEXT,
    action_config  JSONB,
    priority       INT           DEFAULT 0,
    enabled        BOOLEAN       DEFAULT TRUE,
    description    TEXT,
    tenant_id      VARCHAR(64),
    created_by     VARCHAR(64),
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_data.ecos_cognitive_rule IS '认知规则表';
CREATE INDEX IF NOT EXISTS idx_data_cog_type    ON ecos_data.ecos_cognitive_rule(rule_type);
CREATE INDEX IF NOT EXISTS idx_data_cog_enabled ON ecos_data.ecos_cognitive_rule(enabled);

-- ============================================
-- Table: ecos_task
-- Description: 任务表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_data.ecos_task (
    id            BIGSERIAL     PRIMARY KEY,
    task_name     VARCHAR(256)  NOT NULL,
    task_type     VARCHAR(32),
    status        VARCHAR(32)   DEFAULT 'PENDING',
    config        JSONB,
    runner        VARCHAR(64),
    priority      INT           DEFAULT 0,
    retry_count   INT           DEFAULT 0,
    max_retries   INT           DEFAULT 3,
    scheduled_at  TIMESTAMP,
    started_at    TIMESTAMP,
    completed_at  TIMESTAMP,
    result        JSONB,
    error_message TEXT,
    tenant_id     VARCHAR(64),
    created_by    VARCHAR(64),
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_data.ecos_task IS '任务表';
CREATE INDEX IF NOT EXISTS idx_data_task_status ON ecos_data.ecos_task(status);
CREATE INDEX IF NOT EXISTS idx_data_task_type   ON ecos_data.ecos_task(task_type);
CREATE INDEX IF NOT EXISTS idx_data_task_time   ON ecos_data.ecos_task(created_at DESC);

-- ============================================
-- Foreign Keys (cross-domain)
-- ============================================
ALTER TABLE ecos_data.td_datasource
    ADD CONSTRAINT fk_data_ds_org FOREIGN KEY (org_id) REFERENCES ecos_sysman.td_organization("ORG_ID");
ALTER TABLE ecos_data.td_data_resource
    ADD CONSTRAINT fk_data_res_ds  FOREIGN KEY (datasource_id) REFERENCES ecos_data.td_datasource(datasource_id);
ALTER TABLE ecos_data.td_data_field
    ADD CONSTRAINT fk_data_field_res FOREIGN KEY (resource_id) REFERENCES ecos_data.td_data_resource(resource_id);
ALTER TABLE ecos_data.ecos_pipeline_execution
    ADD CONSTRAINT fk_data_pexec_def FOREIGN KEY (pipeline_id) REFERENCES ecos_data.ecos_pipeline_definition(id);
ALTER TABLE ecos_data.ecos_pipeline_node
    ADD CONSTRAINT fk_data_pnode_def FOREIGN KEY (definition_id) REFERENCES ecos_data.ecos_pipeline_definition(id);
ALTER TABLE ecos_data.ecos_pipeline_edge
    ADD CONSTRAINT fk_data_pedge_def FOREIGN KEY (definition_id) REFERENCES ecos_data.ecos_pipeline_definition(id);
