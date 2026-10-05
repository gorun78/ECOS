-- ============================================================================
-- ECOS Ontology Domain — PostgreSQL DDL
-- Schema: ecos_ontology
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS ecos_ontology;

-- ============================================
-- Table: ecos_domain
-- Description: 业务域表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_domain (
    id          VARCHAR(50)  PRIMARY KEY,
    code        VARCHAR(100) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    owner       VARCHAR(100),
    description TEXT,
    status      VARCHAR(50)  DEFAULT 'Draft',
    sort_order  INT          DEFAULT 0,
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_domain IS '业务域表';
CREATE UNIQUE INDEX IF NOT EXISTS idx_onto_domain_code ON ecos_ontology.ecos_domain(code);

-- ============================================
-- Table: ecos_ontology_entity
-- Description: 本体实体表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_ontology_entity (
    id          VARCHAR(64)  PRIMARY KEY,
    ontology_id VARCHAR(64),
    code        VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT         DEFAULT '',
    entity_type VARCHAR(64)  DEFAULT 'MASTER',
    sort_order  INTEGER      DEFAULT 1,
    status      VARCHAR(32)  DEFAULT 'ACTIVE',
    table_name  VARCHAR(128),
    domain_id   VARCHAR(50),
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_ontology_entity IS '本体实体表';
CREATE INDEX IF NOT EXISTS idx_onto_ent_onto    ON ecos_ontology.ecos_ontology_entity(ontology_id);
CREATE INDEX IF NOT EXISTS idx_onto_ent_code    ON ecos_ontology.ecos_ontology_entity(code);
CREATE INDEX IF NOT EXISTS idx_onto_ent_domain  ON ecos_ontology.ecos_ontology_entity(domain_id);
CREATE INDEX IF NOT EXISTS idx_onto_ent_tenant  ON ecos_ontology.ecos_ontology_entity(tenant_id);
ALTER TABLE ecos_ontology.ecos_ontology_entity
    ADD CONSTRAINT fk_onto_ent_domain FOREIGN KEY (domain_id) REFERENCES ecos_ontology.ecos_domain(id);

-- ============================================
-- Table: ecos_ontology_property
-- Description: 本体属性表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_ontology_property (
    id               VARCHAR(64)  PRIMARY KEY,
    entity_id        VARCHAR(64)  NOT NULL,
    code             VARCHAR(255) NOT NULL,
    name             VARCHAR(255) NOT NULL,
    property_type    VARCHAR(64)  DEFAULT 'STRING',
    required_flag    INTEGER      DEFAULT 0,
    searchable_flag  INTEGER      DEFAULT 0,
    sort_order       INTEGER      DEFAULT 1,
    enum_values      TEXT,
    default_value    VARCHAR(500),
    validation_rule  TEXT,
    unique_flag      INT          DEFAULT 0,
    ref_entity_code  VARCHAR(100),
    max_length       INT,
    min_value        NUMERIC,
    max_value        NUMERIC,
    function_type    VARCHAR(32),
    function_expression TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_ontology_property IS '本体属性表';
CREATE INDEX IF NOT EXISTS idx_onto_prop_ent ON ecos_ontology.ecos_ontology_property(entity_id);
ALTER TABLE ecos_ontology.ecos_ontology_property
    ADD CONSTRAINT fk_onto_prop_ent FOREIGN KEY (entity_id) REFERENCES ecos_ontology.ecos_ontology_entity(id);

-- ============================================
-- Table: ecos_ontology_relationship
-- Description: 本体关系表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_ontology_relationship (
    id               VARCHAR(64)  PRIMARY KEY,
    source_entity_id VARCHAR(64)  NOT NULL,
    target_entity_id VARCHAR(64)  NOT NULL,
    code             VARCHAR(255),
    name             VARCHAR(255),
    relationship_type VARCHAR(64) DEFAULT 'ONE_TO_MANY',
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_ontology_relationship IS '本体关系表';
CREATE INDEX IF NOT EXISTS idx_onto_rel_src ON ecos_ontology.ecos_ontology_relationship(source_entity_id);
CREATE INDEX IF NOT EXISTS idx_onto_rel_tgt ON ecos_ontology.ecos_ontology_relationship(target_entity_id);

-- ============================================
-- Table: ecos_ontology_action
-- Description: 本体动作表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_ontology_action (
    id          VARCHAR(64)  PRIMARY KEY,
    entity_id   VARCHAR(64)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    action_type VARCHAR(64)  DEFAULT 'CUSTOM',
    rule_json   TEXT,
    strategy    VARCHAR(255),
    status      VARCHAR(32)  DEFAULT 'ACTIVE',
    code        VARCHAR(100),
    description TEXT,
    preconditions TEXT,
    effects     TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_ontology_action IS '本体动作表';
CREATE INDEX IF NOT EXISTS idx_onto_act_ent ON ecos_ontology.ecos_ontology_action(entity_id);

-- ============================================
-- Table: ecos_ontology_rule
-- Description: 本体规则表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_ontology_rule (
    id          VARCHAR(50)  PRIMARY KEY,
    entity_id   VARCHAR(50)  NOT NULL,
    code        VARCHAR(100) NOT NULL,
    name        VARCHAR(200),
    rule_type   VARCHAR(50),
    expression  TEXT,
    action      TEXT,
    priority    INT          DEFAULT 0,
    enabled     INT          DEFAULT 1,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_ontology_rule IS '本体规则表';
CREATE UNIQUE INDEX IF NOT EXISTS idx_onto_rule_ent_code ON ecos_ontology.ecos_ontology_rule(entity_id, code);

-- ============================================
-- Table: ecos_ontology_version
-- Description: 本体版本表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_ontology_version (
    id           VARCHAR(50) PRIMARY KEY,
    ontology_id  VARCHAR(50) NOT NULL,
    version_no   VARCHAR(20) NOT NULL,
    status       VARCHAR(50) DEFAULT 'Draft',
    snapshot     JSONB,
    change_log   TEXT,
    publisher    VARCHAR(100),
    published_at TIMESTAMP,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_ontology_version IS '本体版本表';
CREATE UNIQUE INDEX IF NOT EXISTS idx_onto_ver_onto_vno ON ecos_ontology.ecos_ontology_version(ontology_id, version_no);

-- ============================================
-- Table: ecos_business_glossary
-- Description: 业务术语表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_business_glossary (
    id          BIGSERIAL     PRIMARY KEY,
    code        VARCHAR(64),
    name        VARCHAR(255)  NOT NULL,
    definition  TEXT,
    domain      VARCHAR(128),
    domain_id   VARCHAR(50),
    owner       VARCHAR(128),
    status      VARCHAR(32)   DEFAULT 'DRAFT',
    tenant_id   VARCHAR(64),
    created_by  VARCHAR(128),
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_business_glossary IS '业务术语表';
CREATE INDEX IF NOT EXISTS idx_onto_gloss_domain ON ecos_ontology.ecos_business_glossary(domain);
CREATE INDEX IF NOT EXISTS idx_onto_gloss_status ON ecos_ontology.ecos_business_glossary(status);
ALTER TABLE ecos_ontology.ecos_business_glossary
    ADD CONSTRAINT fk_onto_gloss_domain FOREIGN KEY (domain_id) REFERENCES ecos_ontology.ecos_domain(id);

-- ============================================
-- Table: entity_definition
-- Description: 实体定义表(V52本体编译器)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.entity_definition (
    id          VARCHAR(64)  PRIMARY KEY,
    code        VARCHAR(128) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    category    VARCHAR(32)  DEFAULT 'MASTER',
    properties  JSONB        DEFAULT '[]',
    lifecycle   JSONB        DEFAULT '{}',
    version     INTEGER      DEFAULT 1,
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.entity_definition IS '实体定义表(本体编译器)';
CREATE UNIQUE INDEX IF NOT EXISTS idx_onto_entdef_code ON ecos_ontology.entity_definition(code);

-- ============================================
-- Table: relationship_definition
-- Description: 关系定义表(V52本体编译器)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.relationship_definition (
    id             VARCHAR(64) PRIMARY KEY,
    source_entity  VARCHAR(128) NOT NULL,
    target_entity  VARCHAR(128) NOT NULL,
    type           VARCHAR(32),
    cardinality    VARCHAR(16)  DEFAULT 'ONE_TO_MANY',
    properties     JSONB        DEFAULT '{}',
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.relationship_definition IS '关系定义表(本体编译器)';

-- ============================================
-- Table: metric_definition
-- Description: 指标定义表(V52本体编译器)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.metric_definition (
    id          VARCHAR(64)  PRIMARY KEY,
    code        VARCHAR(128) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    expression  TEXT,
    aggregation VARCHAR(16)  DEFAULT 'SUM',
    entity_code VARCHAR(128),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.metric_definition IS '指标定义表(本体编译器)';
CREATE UNIQUE INDEX IF NOT EXISTS idx_onto_metric_code ON ecos_ontology.metric_definition(code);

-- ============================================
-- Table: action_definition
-- Description: 动作定义表(V52本体编译器)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.action_definition (
    id          VARCHAR(64)  PRIMARY KEY,
    code        VARCHAR(128),
    name        VARCHAR(255),
    type        VARCHAR(16)  DEFAULT 'WORKFLOW',
    config      JSONB        DEFAULT '{}',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.action_definition IS '动作定义表(本体编译器)';

-- ============================================
-- Table: policy_definition
-- Description: 策略定义表(V52本体编译器)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.policy_definition (
    id          VARCHAR(64)  PRIMARY KEY,
    code        VARCHAR(128),
    type        VARCHAR(32),
    expression  TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.policy_definition IS '策略定义表(本体编译器)';

-- ============================================
-- Table: event_definition
-- Description: 事件定义表(V52本体编译器)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.event_definition (
    id             VARCHAR(64) PRIMARY KEY,
    code           VARCHAR(128),
    source         VARCHAR(128),
    payload_schema JSONB       DEFAULT '{}',
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.event_definition IS '事件定义表(本体编译器)';

-- ============================================
-- Table: ecos_object_state_machine
-- Description: 对象状态机表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_object_state_machine (
    id              VARCHAR(50)  PRIMARY KEY,
    entity_code     VARCHAR(100) NOT NULL,
    from_status     VARCHAR(50),
    to_status       VARCHAR(50),
    transition_code VARCHAR(100),
    transition_name VARCHAR(200),
    require_role    VARCHAR(200),
    guard_rule      TEXT,
    side_effect     TEXT,
    sort_order      INT          DEFAULT 0,
    tenant_id       VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_object_state_machine IS '对象状态机表';
CREATE UNIQUE INDEX IF NOT EXISTS idx_onto_sm_uniq ON ecos_ontology.ecos_object_state_machine(entity_code, from_status, transition_code);

-- ============================================
-- Table: ecos_object_timeline
-- Description: 对象时间线表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_object_timeline (
    id            VARCHAR(50) PRIMARY KEY,
    object_id     VARCHAR(100) NOT NULL,
    entity_code   VARCHAR(100),
    event_type    VARCHAR(100),
    event_summary VARCHAR(500),
    actor         VARCHAR(100),
    details       JSONB,
    tenant_id     VARCHAR(64),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_object_timeline IS '对象时间线表';
CREATE INDEX IF NOT EXISTS idx_onto_timeline_obj ON ecos_ontology.ecos_object_timeline(object_id, created_at DESC);

-- ============================================
-- Table: ecos_object_version
-- Description: 对象版本表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_object_version (
    id             VARCHAR(50) PRIMARY KEY,
    object_id      VARCHAR(100) NOT NULL,
    entity_code    VARCHAR(100),
    version_no     INT,
    snapshot       JSONB,
    change_summary VARCHAR(500),
    created_by     VARCHAR(100),
    tenant_id      VARCHAR(64),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_object_version IS '对象版本表';
CREATE UNIQUE INDEX IF NOT EXISTS idx_onto_objver_uniq ON ecos_ontology.ecos_object_version(object_id, version_no);

-- ============================================
-- Table: ecos_object_relationship
-- Description: 对象关系表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_object_relationship (
    id                  VARCHAR(50) PRIMARY KEY,
    source_object_id    VARCHAR(100),
    target_object_id    VARCHAR(100),
    source_entity_code  VARCHAR(100),
    target_entity_code  VARCHAR(100),
    relationship_code   VARCHAR(100),
    relationship_type   VARCHAR(50),
    properties          JSONB,
    tenant_id           VARCHAR(64),
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_object_relationship IS '对象关系表';
CREATE INDEX IF NOT EXISTS idx_onto_objrel_src ON ecos_ontology.ecos_object_relationship(source_object_id);
CREATE INDEX IF NOT EXISTS idx_onto_objrel_tgt ON ecos_ontology.ecos_object_relationship(target_object_id);

-- ============================================
-- Table: ecos_object_attachment
-- Description: 对象附件表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_object_attachment (
    id          VARCHAR(50)  PRIMARY KEY,
    object_id   VARCHAR(100) NOT NULL,
    entity_code VARCHAR(100),
    file_name   VARCHAR(500),
    file_path   VARCHAR(1000),
    file_size   BIGINT,
    mime_type   VARCHAR(200),
    version_no  INT          DEFAULT 1,
    uploaded_by VARCHAR(100),
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_object_attachment IS '对象附件表';

-- ============================================
-- Table: ecos_object_links
-- Description: 对象链接表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_object_links (
    id            VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    source_id     VARCHAR(64),
    target_id     VARCHAR(64),
    relation_code VARCHAR(128),
    tenant_id     VARCHAR(64),
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_object_links IS '对象链接表';
CREATE INDEX IF NOT EXISTS idx_onto_links_src ON ecos_ontology.ecos_object_links(source_id);
CREATE INDEX IF NOT EXISTS idx_onto_links_tgt ON ecos_ontology.ecos_object_links(target_id);

-- ============================================
-- Table: ecos_object_data
-- Description: 对象数据表(运行时)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_object_data (
    id          VARCHAR(64)  PRIMARY KEY,
    entity_code VARCHAR(128) NOT NULL,
    object_data JSONB,
    status      VARCHAR(32)  DEFAULT 'ACTIVE',
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_object_data IS '对象数据表(运行时)';
CREATE INDEX IF NOT EXISTS idx_onto_objdata_entity ON ecos_ontology.ecos_object_data(entity_code);
CREATE INDEX IF NOT EXISTS idx_onto_objdata_tenant ON ecos_ontology.ecos_object_data(tenant_id);

-- ============================================
-- Table: ecos_object_relation
-- Description: 对象关联表(桥接)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_object_relation (
    id                  VARCHAR(64)  PRIMARY KEY,
    source_entity_code  VARCHAR(128),
    target_entity_code  VARCHAR(128),
    relation_type       VARCHAR(64),
    description         TEXT,
    tenant_id           VARCHAR(64),
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_object_relation IS '对象关联表(桥接)';
CREATE INDEX IF NOT EXISTS idx_onto_objrel_tenant ON ecos_ontology.ecos_object_relation(tenant_id);

-- ============================================
-- Table: ecos_workflow
-- Description: 工作流定义表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_workflow (
    id           VARCHAR(64)  PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    description  TEXT         DEFAULT '',
    status       VARCHAR(32)  DEFAULT 'draft',
    mode         VARCHAR(32)  DEFAULT 'sequential',
    nodes        TEXT         DEFAULT '[]',
    edges        TEXT         DEFAULT '[]',
    published_at TIMESTAMP,
    tenant_id    VARCHAR(64),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_workflow IS '工作流定义表';

-- ============================================
-- Table: ecos_workflow_instance
-- Description: 工作流实例表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_workflow_instance (
    id                  VARCHAR(50) PRIMARY KEY,
    workflow_id         VARCHAR(50),
    workflow_name       VARCHAR(200),
    version_no          VARCHAR(20),
    status              VARCHAR(50),
    trigger_type        VARCHAR(50),
    triggered_by        VARCHAR(100),
    triggered_object_id VARCHAR(100),
    trigger_event       VARCHAR(200),
    variables           JSONB       DEFAULT '{}',
    context             JSONB,
    current_node_ids    JSONB,
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    error_message       TEXT,
    retry_count         INT         DEFAULT 0,
    tenant_id           VARCHAR(64),
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_workflow_instance IS '工作流实例表';
CREATE INDEX IF NOT EXISTS idx_onto_wfinst_status   ON ecos_ontology.ecos_workflow_instance(status);
CREATE INDEX IF NOT EXISTS idx_onto_wfinst_wf       ON ecos_ontology.ecos_workflow_instance(workflow_id);
CREATE INDEX IF NOT EXISTS idx_onto_wfinst_tenant   ON ecos_ontology.ecos_workflow_instance(tenant_id);

-- ============================================
-- Table: ecos_workflow_task
-- Description: 工作流任务表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_workflow_task (
    id              VARCHAR(50) PRIMARY KEY,
    instance_id     VARCHAR(50),
    node_id         VARCHAR(100),
    task_type       VARCHAR(50),
    title           VARCHAR(500),
    assignee        VARCHAR(100),
    candidate_users JSONB,
    candidate_roles JSONB,
    status          VARCHAR(50) DEFAULT 'New',
    priority        VARCHAR(20) DEFAULT 'NORMAL',
    form_schema     JSONB,
    form_data       JSONB,
    result          JSONB,
    agent_result    JSONB,
    due_date        TIMESTAMP,
    completed_at    TIMESTAMP,
    completed_by    VARCHAR(100),
    tenant_id       VARCHAR(64),
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_workflow_task IS '工作流任务表';
CREATE INDEX IF NOT EXISTS idx_onto_wftask_status ON ecos_ontology.ecos_workflow_task(status);
CREATE INDEX IF NOT EXISTS idx_onto_wftask_assign ON ecos_ontology.ecos_workflow_task(assignee);
CREATE INDEX IF NOT EXISTS idx_onto_wftask_inst   ON ecos_ontology.ecos_workflow_task(instance_id);

-- ============================================
-- Table: ecos_workflow_approval
-- Description: 工作流审批表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_workflow_approval (
    id          VARCHAR(50) PRIMARY KEY,
    task_id     VARCHAR(50),
    instance_id VARCHAR(50),
    approver    VARCHAR(100),
    decision    VARCHAR(50),
    opinion     TEXT,
    form_data   JSONB,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_workflow_approval IS '工作流审批表';
CREATE INDEX IF NOT EXISTS idx_onto_wfappr_task ON ecos_ontology.ecos_workflow_approval(task_id);
CREATE INDEX IF NOT EXISTS idx_onto_wfappr_inst ON ecos_ontology.ecos_workflow_approval(instance_id);

-- ============================================
-- Table: ecos_workflow_log
-- Description: 工作流日志表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_workflow_log (
    id          VARCHAR(50) PRIMARY KEY,
    instance_id VARCHAR(50),
    node_id     VARCHAR(100),
    node_type   VARCHAR(50),
    event_type  VARCHAR(100),
    message     TEXT,
    details     JSONB,
    duration_ms BIGINT,
    trace_id    VARCHAR(100),
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ontology.ecos_workflow_log IS '工作流日志表';
CREATE INDEX IF NOT EXISTS idx_onto_wflog_inst ON ecos_ontology.ecos_workflow_log(instance_id, created_at);
