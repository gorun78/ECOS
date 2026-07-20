-- ============================================================================
-- ECOS Data Domain — Oracle DDL
-- Schema: ecos_data
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

BEGIN
   EXECUTE IMMEDIATE 'CREATE USER ecos_data IDENTIFIED BY ecos_data DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE INDEX TO ecos_data;

-- ============================================
-- Table: td_datasource
-- Description: 数据源表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_data.td_datasource (
    datasource_id     VARCHAR2(64)  PRIMARY KEY,
    datasource_name   VARCHAR2(128) NOT NULL,
    datasource_type   VARCHAR2(32)  DEFAULT ''JDBC'',
    org_id            VARCHAR2(64),
    node_id           VARCHAR2(64),
    description       CLOB,
    connection_config CLOB,
    status            VARCHAR2(16)  DEFAULT ''ACTIVE'',
    tags              VARCHAR2(256),
    last_test_time    TIMESTAMP,
    last_test_result  NUMBER(1),
    last_test_message CLOB,
    tenant_id         VARCHAR2(64),
    create_by         VARCHAR2(64),
    create_time       TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    update_by         VARCHAR2(64),
    update_time       TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_data.td_datasource IS '数据源表';
COMMENT ON COLUMN ecos_data.td_datasource.datasource_type IS '类型: JDBC/API/FILE/MQ';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_ds_org ON ecos_data.td_datasource(org_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_ds_status ON ecos_data.td_datasource(status)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_ds_tenant ON ecos_data.td_datasource(tenant_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: td_data_resource
-- Description: 数据资源表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_data.td_data_resource (
    resource_id     VARCHAR2(64)  PRIMARY KEY,
    resource_name   VARCHAR2(256) NOT NULL,
    resource_type   VARCHAR2(32)  DEFAULT ''TABLE'',
    org_id          VARCHAR2(64),
    org_name        VARCHAR2(128),
    datasource_id   VARCHAR2(64),
    source_path     VARCHAR2(512),
    description     CLOB,
    tags            VARCHAR2(256),
    status          VARCHAR2(16)  DEFAULT ''ACTIVE'',
    field_count     NUMBER(10)    DEFAULT 0,
    record_count    NUMBER(19)    DEFAULT 0,
    last_sync_time  TIMESTAMP,
    tenant_id       VARCHAR2(64),
    create_by       VARCHAR2(64),
    create_time     TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    update_by       VARCHAR2(64),
    update_time     TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_data.td_data_resource IS '数据资源表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_res_ds ON ecos_data.td_data_resource(datasource_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_res_org ON ecos_data.td_data_resource(org_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_res_tenant ON ecos_data.td_data_resource(tenant_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: td_data_field
-- Description: 数据字段表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_data.td_data_field (
    field_id      VARCHAR2(64)  PRIMARY KEY,
    resource_id   VARCHAR2(64)  NOT NULL,
    field_name    VARCHAR2(256) NOT NULL,
    field_alias   VARCHAR2(256),
    field_type    VARCHAR2(64),
    field_length  NUMBER(10),
    data_precision NUMBER(10),
    nullable      NUMBER(5)     DEFAULT 1,
    is_primary_key NUMBER(5)    DEFAULT 0,
    default_value VARCHAR2(256),
    description   CLOB,
    field_order   NUMBER(10)    DEFAULT 0
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_data.td_data_field IS '数据字段表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_field_res ON ecos_data.td_data_field(resource_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: td_catalog_item
-- Description: 数据目录项表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_data.td_catalog_item (
    catalog_id      VARCHAR2(64)  PRIMARY KEY,
    resource_id     VARCHAR2(64),
    resource_name   VARCHAR2(256),
    resource_type   VARCHAR2(32)  DEFAULT ''TABLE'',
    org_name        VARCHAR2(128),
    description     CLOB,
    tags            VARCHAR2(256),
    category_path   VARCHAR2(256),
    access_type     VARCHAR2(32)  DEFAULT ''READ'',
    data_format     VARCHAR2(32),
    field_count     NUMBER(10)    DEFAULT 0,
    record_count    NUMBER(19)    DEFAULT 0,
    last_updated    TIMESTAMP,
    status          VARCHAR2(16)  DEFAULT ''ACTIVE'',
    tenant_id       VARCHAR2(64)
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_data.td_catalog_item IS '数据目录项表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_cat_res ON ecos_data.td_catalog_item(resource_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_cat_type ON ecos_data.td_catalog_item(resource_type)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_cat_path ON ecos_data.td_catalog_item(category_path)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: td_data_category
-- Description: 数据分类表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_data.td_data_category (
    category_id   VARCHAR2(64)  PRIMARY KEY,
    category_name VARCHAR2(256) NOT NULL,
    parent_id     VARCHAR2(64),
    path          VARCHAR2(512),
    level         NUMBER(10)    DEFAULT 1,
    sort_order    NUMBER(10)    DEFAULT 0,
    description   CLOB,
    status        VARCHAR2(16)  DEFAULT ''ACTIVE'',
    tenant_id     VARCHAR2(64),
    create_by     VARCHAR2(64),
    create_time   TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    update_by     VARCHAR2(64),
    update_time   TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_data.td_data_category IS '数据分类表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_cat_parent ON ecos_data.td_data_category(parent_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_pipeline_definition
-- Description: 管道定义表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_data.ecos_pipeline_definition (
    id          VARCHAR2(64)  PRIMARY KEY,
    name        VARCHAR2(255) NOT NULL,
    description CLOB,
    definition  CLOB          DEFAULT ''{}'',
    status      VARCHAR2(32)  DEFAULT ''DRAFT'',
    tenant_id   VARCHAR2(64),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_data.ecos_pipeline_definition IS '管道定义表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_pdef_status ON ecos_data.ecos_pipeline_definition(status)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_pdef_tenant ON ecos_data.ecos_pipeline_definition(tenant_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_pipeline_execution
-- Description: 管道执行表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_data.ecos_pipeline_execution (
    id              VARCHAR2(64)  PRIMARY KEY,
    pipeline_id     VARCHAR2(64)  NOT NULL,
    status          VARCHAR2(32)  DEFAULT ''PENDING'',
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    error_message   CLOB,
    rows_processed  NUMBER(19)    DEFAULT 0,
    tenant_id       VARCHAR2(64),
    created_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_data.ecos_pipeline_execution IS '管道执行表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_pexec_pipeline ON ecos_data.ecos_pipeline_execution(pipeline_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_pexec_status ON ecos_data.ecos_pipeline_execution(status)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_pexec_tenant ON ecos_data.ecos_pipeline_execution(tenant_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_pipeline_node
-- Description: 管道节点表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_data.ecos_pipeline_node (
    id            VARCHAR2(64)  PRIMARY KEY,
    definition_id VARCHAR2(64)  NOT NULL,
    node_id       VARCHAR2(64)  NOT NULL,
    type          VARCHAR2(64)  DEFAULT ''TRANSFORM_SQL'',
    config        CLOB          DEFAULT ''{}'',
    depends_on    CLOB          DEFAULT ''[]'',
    position_x    NUMBER(10)    DEFAULT 0,
    position_y    NUMBER(10)    DEFAULT 0,
    created_at    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_data.ecos_pipeline_node IS '管道节点表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_pnode_def ON ecos_data.ecos_pipeline_node(definition_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_pipeline_edge
-- Description: 管道边表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_data.ecos_pipeline_edge (
    id            VARCHAR2(64)  PRIMARY KEY,
    definition_id VARCHAR2(64)  NOT NULL,
    from_node_id  VARCHAR2(64)  NOT NULL,
    to_node_id    VARCHAR2(64)  NOT NULL,
    created_at    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_data.ecos_pipeline_edge IS '管道边表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_pedge_def ON ecos_data.ecos_pipeline_edge(definition_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Sequence for ecos_dq_rule
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'CREATE SEQUENCE ecos_data.seq_dq_rule START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_dq_rule
-- Description: 数据质量规则表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_data.ecos_dq_rule (
    id              NUMBER(19)     PRIMARY KEY,
    name            VARCHAR2(255)  NOT NULL,
    description     CLOB,
    rule_type       VARCHAR2(64)   DEFAULT ''NOT_NULL'',
    config_json     CLOB,
    severity        VARCHAR2(32)   DEFAULT ''HIGH'',
    enabled         NUMBER(1)      DEFAULT 1,
    target_entity   VARCHAR2(128),
    target_field    VARCHAR2(128),
    rule_expression CLOB,
    code            VARCHAR2(64),
    status          VARCHAR2(32)   DEFAULT ''ACTIVE'',
    tenant_id       VARCHAR2(64),
    created_at      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_data.ecos_dq_rule IS '数据质量规则表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_dq_type ON ecos_data.ecos_dq_rule(rule_type)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_dq_tenant ON ecos_data.ecos_dq_rule(tenant_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

CREATE OR REPLACE TRIGGER ecos_data.trg_dq_rule_id
BEFORE INSERT ON ecos_data.ecos_dq_rule
FOR EACH ROW
BEGIN
   IF :NEW.id IS NULL THEN
      :NEW.id := ecos_data.seq_dq_rule.NEXTVAL;
   END IF;
END;
/

-- ============================================
-- Sequence for ecos_dq_issue
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'CREATE SEQUENCE ecos_data.seq_dq_issue START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_dq_issue
-- Description: 数据质量问题表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_data.ecos_dq_issue (
    id          NUMBER(19)    PRIMARY KEY,
    rule_id     NUMBER(19)    NOT NULL,
    asset_id    VARCHAR2(255) DEFAULT '''',
    description CLOB,
    status      VARCHAR2(32)  DEFAULT ''open'',
    severity    VARCHAR2(32)  DEFAULT ''HIGH'',
    tenant_id   VARCHAR2(64),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    resolved_at TIMESTAMP
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_data.ecos_dq_issue IS '数据质量问题表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_dqi_rule ON ecos_data.ecos_dq_issue(rule_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_data.ecos_dq_issue ADD CONSTRAINT fk_data_dqi_rule FOREIGN KEY (rule_id) REFERENCES ecos_data.ecos_dq_rule(id) ON DELETE CASCADE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

CREATE OR REPLACE TRIGGER ecos_data.trg_dq_issue_id
BEFORE INSERT ON ecos_data.ecos_dq_issue
FOR EACH ROW
BEGIN
   IF :NEW.id IS NULL THEN
      :NEW.id := ecos_data.seq_dq_issue.NEXTVAL;
   END IF;
END;
/

-- ============================================
-- Table: ecos_dq_execution_result
-- Description: 数据质量执行结果表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_data.ecos_dq_execution_result (
    id            VARCHAR2(64) PRIMARY KEY,
    rule_id       VARCHAR2(64),
    passed        NUMBER(1)    DEFAULT 0,
    total_rows    NUMBER(10)   DEFAULT 0,
    failed_rows   NUMBER(10)   DEFAULT 0,
    error_details CLOB,
    executed_at   TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_data.ecos_dq_execution_result IS '数据质量执行结果表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_dqr_rule ON ecos_data.ecos_dq_execution_result(rule_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_dqr_time ON ecos_data.ecos_dq_execution_result(executed_at DESC)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Sequence for ecos_cognitive_rule
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'CREATE SEQUENCE ecos_data.seq_cognitive_rule START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_cognitive_rule
-- Description: 认知规则表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_data.ecos_cognitive_rule (
    id             NUMBER(19)     PRIMARY KEY,
    rule_name      VARCHAR2(128)  NOT NULL,
    rule_type      VARCHAR2(32),
    condition_expr CLOB,
    action_config  CLOB,
    priority       NUMBER(10)     DEFAULT 0,
    enabled        NUMBER(1)      DEFAULT 1,
    description    CLOB,
    tenant_id      VARCHAR2(64),
    created_by     VARCHAR2(64),
    created_at     TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at     TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_data.ecos_cognitive_rule IS '认知规则表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_cog_type ON ecos_data.ecos_cognitive_rule(rule_type)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_cog_enabled ON ecos_data.ecos_cognitive_rule(enabled)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

CREATE OR REPLACE TRIGGER ecos_data.trg_cognitive_rule_id
BEFORE INSERT ON ecos_data.ecos_cognitive_rule
FOR EACH ROW
BEGIN
   IF :NEW.id IS NULL THEN
      :NEW.id := ecos_data.seq_cognitive_rule.NEXTVAL;
   END IF;
END;
/

-- ============================================
-- Sequence for ecos_task
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'CREATE SEQUENCE ecos_data.seq_task START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_task
-- Description: 任务表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_data.ecos_task (
    id            NUMBER(19)     PRIMARY KEY,
    task_name     VARCHAR2(256)  NOT NULL,
    task_type     VARCHAR2(32),
    status        VARCHAR2(32)   DEFAULT ''PENDING'',
    config        CLOB,
    runner        VARCHAR2(64),
    priority      NUMBER(10)     DEFAULT 0,
    retry_count   NUMBER(10)     DEFAULT 0,
    max_retries   NUMBER(10)     DEFAULT 3,
    scheduled_at  TIMESTAMP,
    started_at    TIMESTAMP,
    completed_at  TIMESTAMP,
    result        CLOB,
    error_message CLOB,
    tenant_id     VARCHAR2(64),
    created_by    VARCHAR2(64),
    created_at    TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at    TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_data.ecos_task IS '任务表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_task_status ON ecos_data.ecos_task(status)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_task_type ON ecos_data.ecos_task(task_type)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_data_task_time ON ecos_data.ecos_task(created_at DESC)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

CREATE OR REPLACE TRIGGER ecos_data.trg_task_id
BEFORE INSERT ON ecos_data.ecos_task
FOR EACH ROW
BEGIN
   IF :NEW.id IS NULL THEN
      :NEW.id := ecos_data.seq_task.NEXTVAL;
   END IF;
END;
/

-- ============================================
-- Foreign Keys (cross-domain)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_data.td_datasource ADD CONSTRAINT fk_data_ds_org FOREIGN KEY (org_id) REFERENCES ecos_sysman.td_organization(ORG_ID)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_data.td_data_resource ADD CONSTRAINT fk_data_res_ds FOREIGN KEY (datasource_id) REFERENCES ecos_data.td_datasource(datasource_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_data.td_data_field ADD CONSTRAINT fk_data_field_res FOREIGN KEY (resource_id) REFERENCES ecos_data.td_data_resource(resource_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_data.ecos_pipeline_execution ADD CONSTRAINT fk_data_pexec_def FOREIGN KEY (pipeline_id) REFERENCES ecos_data.ecos_pipeline_definition(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_data.ecos_pipeline_node ADD CONSTRAINT fk_data_pnode_def FOREIGN KEY (definition_id) REFERENCES ecos_data.ecos_pipeline_definition(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_data.ecos_pipeline_edge ADD CONSTRAINT fk_data_pedge_def FOREIGN KEY (definition_id) REFERENCES ecos_data.ecos_pipeline_definition(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
