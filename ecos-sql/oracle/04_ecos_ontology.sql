-- ============================================================================
-- ECOS Ontology Domain — Oracle DDL
-- Schema: ecos_ontology
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

BEGIN
   EXECUTE IMMEDIATE 'CREATE USER ecos_ontology IDENTIFIED BY ecos_ontology DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE INDEX TO ecos_ontology;

-- ============================================
-- Table: ecos_domain
-- Description: 业务域表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_domain (
    id          VARCHAR2(50)  PRIMARY KEY,
    code        VARCHAR2(100) NOT NULL,
    name        VARCHAR2(200) NOT NULL,
    owner       VARCHAR2(100),
    description CLOB,
    status      VARCHAR2(50)  DEFAULT ''Draft'',
    sort_order  NUMBER(10)    DEFAULT 0,
    tenant_id   VARCHAR2(64),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_domain IS '业务域表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX idx_onto_domain_code ON ecos_ontology.ecos_domain(code)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_ontology_entity
-- Description: 本体实体表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_ontology_entity (
    id          VARCHAR2(64)  PRIMARY KEY,
    ontology_id VARCHAR2(64),
    code        VARCHAR2(255) NOT NULL,
    name        VARCHAR2(255) NOT NULL,
    description CLOB,
    entity_type VARCHAR2(64)  DEFAULT ''MASTER'',
    sort_order  NUMBER(10)    DEFAULT 1,
    status      VARCHAR2(32)  DEFAULT ''ACTIVE'',
    table_name  VARCHAR2(128),
    domain_id   VARCHAR2(50),
    tenant_id   VARCHAR2(64),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_ontology_entity IS '本体实体表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_ent_onto ON ecos_ontology.ecos_ontology_entity(ontology_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_ent_code ON ecos_ontology.ecos_ontology_entity(code)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_ent_domain ON ecos_ontology.ecos_ontology_entity(domain_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_ent_tenant ON ecos_ontology.ecos_ontology_entity(tenant_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_ontology.ecos_ontology_entity ADD CONSTRAINT fk_onto_ent_domain FOREIGN KEY (domain_id) REFERENCES ecos_ontology.ecos_domain(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_ontology_property
-- Description: 本体属性表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_ontology_property (
    id               VARCHAR2(64)  PRIMARY KEY,
    entity_id        VARCHAR2(64)  NOT NULL,
    code             VARCHAR2(255) NOT NULL,
    name             VARCHAR2(255) NOT NULL,
    property_type    VARCHAR2(64)  DEFAULT ''STRING'',
    required_flag    NUMBER(10)    DEFAULT 0,
    searchable_flag  NUMBER(10)    DEFAULT 0,
    sort_order       NUMBER(10)    DEFAULT 1,
    enum_values      CLOB,
    default_value    VARCHAR2(500),
    validation_rule  CLOB,
    unique_flag      NUMBER(10)    DEFAULT 0,
    ref_entity_code  VARCHAR2(100),
    max_length       NUMBER(10),
    min_value        NUMBER,
    max_value        NUMBER,
    function_type    VARCHAR2(32),
    function_expression CLOB,
    created_at       TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at       TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_ontology_property IS '本体属性表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_prop_ent ON ecos_ontology.ecos_ontology_property(entity_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_ontology.ecos_ontology_property ADD CONSTRAINT fk_onto_prop_ent FOREIGN KEY (entity_id) REFERENCES ecos_ontology.ecos_ontology_entity(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_ontology_relationship
-- Description: 本体关系表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_ontology_relationship (
    id               VARCHAR2(64)  PRIMARY KEY,
    source_entity_id VARCHAR2(64)  NOT NULL,
    target_entity_id VARCHAR2(64)  NOT NULL,
    code             VARCHAR2(255),
    name             VARCHAR2(255),
    relationship_type VARCHAR2(64) DEFAULT ''ONE_TO_MANY'',
    created_at       TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_ontology_relationship IS '本体关系表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_rel_src ON ecos_ontology.ecos_ontology_relationship(source_entity_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_rel_tgt ON ecos_ontology.ecos_ontology_relationship(target_entity_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_ontology_action
-- Description: 本体动作表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_ontology_action (
    id          VARCHAR2(64)  PRIMARY KEY,
    entity_id   VARCHAR2(64)  NOT NULL,
    name        VARCHAR2(255) NOT NULL,
    action_type VARCHAR2(64)  DEFAULT ''CUSTOM'',
    rule_json   CLOB,
    strategy    VARCHAR2(255),
    status      VARCHAR2(32)  DEFAULT ''ACTIVE'',
    code        VARCHAR2(100),
    description CLOB,
    preconditions CLOB,
    effects     CLOB,
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_ontology_action IS '本体动作表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_act_ent ON ecos_ontology.ecos_ontology_action(entity_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_ontology_rule
-- Description: 本体规则表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_ontology_rule (
    id          VARCHAR2(50)  PRIMARY KEY,
    entity_id   VARCHAR2(50)  NOT NULL,
    code        VARCHAR2(100) NOT NULL,
    name        VARCHAR2(200),
    rule_type   VARCHAR2(50),
    expression  CLOB,
    action      CLOB,
    priority    NUMBER(10)    DEFAULT 0,
    enabled     NUMBER(10)    DEFAULT 1,
    description CLOB,
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_ontology_rule IS '本体规则表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX idx_onto_rule_ent_code ON ecos_ontology.ecos_ontology_rule(entity_id, code)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_ontology_version
-- Description: 本体版本表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_ontology_version (
    id           VARCHAR2(50) PRIMARY KEY,
    ontology_id  VARCHAR2(50) NOT NULL,
    version_no   VARCHAR2(20) NOT NULL,
    status       VARCHAR2(50) DEFAULT ''Draft'',
    snapshot     CLOB,
    change_log   CLOB,
    publisher    VARCHAR2(100),
    published_at TIMESTAMP,
    created_at   TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_ontology_version IS '本体版本表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX idx_onto_ver_onto_vno ON ecos_ontology.ecos_ontology_version(ontology_id, version_no)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Sequence for ecos_business_glossary
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'CREATE SEQUENCE ecos_ontology.seq_business_glossary START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_business_glossary
-- Description: 业务术语表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_business_glossary (
    id          NUMBER(19)     PRIMARY KEY,
    code        VARCHAR2(64),
    name        VARCHAR2(255)  NOT NULL,
    definition  CLOB,
    domain      VARCHAR2(128),
    domain_id   VARCHAR2(50),
    owner       VARCHAR2(128),
    status      VARCHAR2(32)   DEFAULT ''DRAFT'',
    tenant_id   VARCHAR2(64),
    created_by  VARCHAR2(128),
    created_at  TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_business_glossary IS '业务术语表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_gloss_domain ON ecos_ontology.ecos_business_glossary(domain)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_gloss_status ON ecos_ontology.ecos_business_glossary(status)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_ontology.ecos_business_glossary ADD CONSTRAINT fk_onto_gloss_domain FOREIGN KEY (domain_id) REFERENCES ecos_ontology.ecos_domain(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

CREATE OR REPLACE TRIGGER ecos_ontology.trg_business_glossary_id
BEFORE INSERT ON ecos_ontology.ecos_business_glossary
FOR EACH ROW
BEGIN
   IF :NEW.id IS NULL THEN
      :NEW.id := ecos_ontology.seq_business_glossary.NEXTVAL;
   END IF;
END;
/

-- ============================================
-- Table: entity_definition
-- Description: 实体定义表(本体编译器)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.entity_definition (
    id          VARCHAR2(64)  PRIMARY KEY,
    code        VARCHAR2(128) NOT NULL,
    name        VARCHAR2(255) NOT NULL,
    description CLOB,
    category    VARCHAR2(32)  DEFAULT ''MASTER'',
    properties  CLOB          DEFAULT ''[]'',
    lifecycle   CLOB          DEFAULT ''{}'',
    version     NUMBER(10)    DEFAULT 1,
    tenant_id   VARCHAR2(64),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.entity_definition IS '实体定义表(本体编译器)';

BEGIN
   EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX idx_onto_entdef_code ON ecos_ontology.entity_definition(code)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: relationship_definition
-- Description: 关系定义表(本体编译器)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.relationship_definition (
    id             VARCHAR2(64) PRIMARY KEY,
    source_entity  VARCHAR2(128) NOT NULL,
    target_entity  VARCHAR2(128) NOT NULL,
    type           VARCHAR2(32),
    cardinality    VARCHAR2(16)  DEFAULT ''ONE_TO_MANY'',
    properties     CLOB          DEFAULT ''{}'',
    created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.relationship_definition IS '关系定义表(本体编译器)';

-- ============================================
-- Table: metric_definition
-- Description: 指标定义表(本体编译器)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.metric_definition (
    id          VARCHAR2(64)  PRIMARY KEY,
    code        VARCHAR2(128) NOT NULL,
    name        VARCHAR2(255) NOT NULL,
    expression  CLOB,
    aggregation VARCHAR2(16)  DEFAULT ''SUM'',
    entity_code VARCHAR2(128),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.metric_definition IS '指标定义表(本体编译器)';

BEGIN
   EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX idx_onto_metric_code ON ecos_ontology.metric_definition(code)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: action_definition
-- Description: 动作定义表(本体编译器)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.action_definition (
    id          VARCHAR2(64)  PRIMARY KEY,
    code        VARCHAR2(128),
    name        VARCHAR2(255),
    type        VARCHAR2(16)  DEFAULT ''WORKFLOW'',
    config      CLOB          DEFAULT ''{}'',
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.action_definition IS '动作定义表(本体编译器)';

-- ============================================
-- Table: policy_definition
-- Description: 策略定义表(本体编译器)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.policy_definition (
    id          VARCHAR2(64)  PRIMARY KEY,
    code        VARCHAR2(128),
    type        VARCHAR2(32),
    expression  CLOB,
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.policy_definition IS '策略定义表(本体编译器)';

-- ============================================
-- Table: event_definition
-- Description: 事件定义表(本体编译器)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.event_definition (
    id             VARCHAR2(64) PRIMARY KEY,
    code           VARCHAR2(128),
    source         VARCHAR2(128),
    payload_schema CLOB         DEFAULT ''{}'',
    created_at     TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.event_definition IS '事件定义表(本体编译器)';

-- ============================================
-- Table: ecos_object_state_machine
-- Description: 对象状态机表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_object_state_machine (
    id              VARCHAR2(50)  PRIMARY KEY,
    entity_code     VARCHAR2(100) NOT NULL,
    from_status     VARCHAR2(50),
    to_status       VARCHAR2(50),
    transition_code VARCHAR2(100),
    transition_name VARCHAR2(200),
    require_role    VARCHAR2(200),
    guard_rule      CLOB,
    side_effect     CLOB,
    sort_order      NUMBER(10)    DEFAULT 0,
    tenant_id       VARCHAR2(64),
    created_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_object_state_machine IS '对象状态机表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX idx_onto_sm_uniq ON ecos_ontology.ecos_object_state_machine(entity_code, from_status, transition_code)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_object_timeline
-- Description: 对象时间线表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_object_timeline (
    id            VARCHAR2(50) PRIMARY KEY,
    object_id     VARCHAR2(100) NOT NULL,
    entity_code   VARCHAR2(100),
    event_type    VARCHAR2(100),
    event_summary VARCHAR2(500),
    actor         VARCHAR2(100),
    details       CLOB,
    tenant_id     VARCHAR2(64),
    created_at    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_object_timeline IS '对象时间线表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_timeline_obj ON ecos_ontology.ecos_object_timeline(object_id, created_at DESC)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_object_version
-- Description: 对象版本表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_object_version (
    id             VARCHAR2(50) PRIMARY KEY,
    object_id      VARCHAR2(100) NOT NULL,
    entity_code    VARCHAR2(100),
    version_no     NUMBER(10),
    snapshot       CLOB,
    change_summary VARCHAR2(500),
    created_by     VARCHAR2(100),
    tenant_id      VARCHAR2(64),
    created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_object_version IS '对象版本表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX idx_onto_objver_uniq ON ecos_ontology.ecos_object_version(object_id, version_no)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_object_relationship
-- Description: 对象关系表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_object_relationship (
    id                  VARCHAR2(50) PRIMARY KEY,
    source_object_id    VARCHAR2(100),
    target_object_id    VARCHAR2(100),
    source_entity_code  VARCHAR2(100),
    target_entity_code  VARCHAR2(100),
    relationship_code   VARCHAR2(100),
    relationship_type   VARCHAR2(50),
    properties          CLOB,
    tenant_id           VARCHAR2(64),
    created_at          TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_object_relationship IS '对象关系表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_objrel_src ON ecos_ontology.ecos_object_relationship(source_object_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_objrel_tgt ON ecos_ontology.ecos_object_relationship(target_object_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_object_attachment
-- Description: 对象附件表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_object_attachment (
    id          VARCHAR2(50)  PRIMARY KEY,
    object_id   VARCHAR2(100) NOT NULL,
    entity_code VARCHAR2(100),
    file_name   VARCHAR2(500),
    file_path   VARCHAR2(1000),
    file_size   NUMBER(19),
    mime_type   VARCHAR2(200),
    version_no  NUMBER(10)    DEFAULT 1,
    uploaded_by VARCHAR2(100),
    tenant_id   VARCHAR2(64),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_object_attachment IS '对象附件表';

-- ============================================
-- Table: ecos_object_links
-- Description: 对象链接表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_object_links (
    id            VARCHAR2(64) PRIMARY KEY DEFAULT SYS_GUID(),
    source_id     VARCHAR2(64),
    target_id     VARCHAR2(64),
    relation_code VARCHAR2(128),
    tenant_id     VARCHAR2(64),
    created_at    TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_object_links IS '对象链接表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_links_src ON ecos_ontology.ecos_object_links(source_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_links_tgt ON ecos_ontology.ecos_object_links(target_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_object_data
-- Description: 对象数据表(运行时)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_object_data (
    id          VARCHAR2(64)  PRIMARY KEY,
    entity_code VARCHAR2(128) NOT NULL,
    object_data CLOB,
    status      VARCHAR2(32)  DEFAULT ''ACTIVE'',
    tenant_id   VARCHAR2(64),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_object_data IS '对象数据表(运行时)';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_objdata_entity ON ecos_ontology.ecos_object_data(entity_code)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_objdata_tenant ON ecos_ontology.ecos_object_data(tenant_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_object_relation
-- Description: 对象关联表(桥接)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_object_relation (
    id                  VARCHAR2(64)  PRIMARY KEY,
    source_entity_code  VARCHAR2(128),
    target_entity_code  VARCHAR2(128),
    relation_type       VARCHAR2(64),
    description         CLOB,
    tenant_id           VARCHAR2(64),
    created_at          TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_object_relation IS '对象关联表(桥接)';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_objrel_tenant ON ecos_ontology.ecos_object_relation(tenant_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_workflow
-- Description: 工作流定义表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_workflow (
    id           VARCHAR2(64)  PRIMARY KEY,
    name         VARCHAR2(255) NOT NULL,
    description  CLOB,
    status       VARCHAR2(32)  DEFAULT ''draft'',
    mode         VARCHAR2(32)  DEFAULT ''sequential'',
    nodes        CLOB          DEFAULT ''[]'',
    edges        CLOB          DEFAULT ''[]'',
    published_at TIMESTAMP,
    tenant_id    VARCHAR2(64),
    created_at   TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at   TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_workflow IS '工作流定义表';

-- ============================================
-- Table: ecos_workflow_instance
-- Description: 工作流实例表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_workflow_instance (
    id                  VARCHAR2(50) PRIMARY KEY,
    workflow_id         VARCHAR2(50),
    workflow_name       VARCHAR2(200),
    version_no          VARCHAR2(20),
    status              VARCHAR2(50),
    trigger_type        VARCHAR2(50),
    triggered_by        VARCHAR2(100),
    triggered_object_id VARCHAR2(100),
    trigger_event       VARCHAR2(200),
    variables           CLOB         DEFAULT ''{}'',
    context             CLOB,
    current_node_ids    CLOB,
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    error_message       CLOB,
    retry_count         NUMBER(10)   DEFAULT 0,
    tenant_id           VARCHAR2(64),
    created_at          TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at          TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_workflow_instance IS '工作流实例表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_wfinst_status ON ecos_ontology.ecos_workflow_instance(status)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_wfinst_wf ON ecos_ontology.ecos_workflow_instance(workflow_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_wfinst_tenant ON ecos_ontology.ecos_workflow_instance(tenant_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_workflow_task
-- Description: 工作流任务表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_workflow_task (
    id              VARCHAR2(50) PRIMARY KEY,
    instance_id     VARCHAR2(50),
    node_id         VARCHAR2(100),
    task_type       VARCHAR2(50),
    title           VARCHAR2(500),
    assignee        VARCHAR2(100),
    candidate_users CLOB,
    candidate_roles CLOB,
    status          VARCHAR2(50) DEFAULT ''New'',
    priority        VARCHAR2(20) DEFAULT ''NORMAL'',
    form_schema     CLOB,
    form_data       CLOB,
    result          CLOB,
    agent_result    CLOB,
    due_date        TIMESTAMP,
    completed_at    TIMESTAMP,
    completed_by    VARCHAR2(100),
    tenant_id       VARCHAR2(64),
    created_at      TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_workflow_task IS '工作流任务表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_wftask_status ON ecos_ontology.ecos_workflow_task(status)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_wftask_assign ON ecos_ontology.ecos_workflow_task(assignee)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_wftask_inst ON ecos_ontology.ecos_workflow_task(instance_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_workflow_approval
-- Description: 工作流审批表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_workflow_approval (
    id          VARCHAR2(50) PRIMARY KEY,
    task_id     VARCHAR2(50),
    instance_id VARCHAR2(50),
    approver    VARCHAR2(100),
    decision    VARCHAR2(50),
    opinion     CLOB,
    form_data   CLOB,
    created_at  TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_workflow_approval IS '工作流审批表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_wfappr_task ON ecos_ontology.ecos_workflow_approval(task_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_wfappr_inst ON ecos_ontology.ecos_workflow_approval(instance_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_workflow_log
-- Description: 工作流日志表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ontology.ecos_workflow_log (
    id          VARCHAR2(50) PRIMARY KEY,
    instance_id VARCHAR2(50),
    node_id     VARCHAR2(100),
    node_type   VARCHAR2(50),
    event_type  VARCHAR2(100),
    message     CLOB,
    details     CLOB,
    duration_ms NUMBER(19),
    trace_id    VARCHAR2(100),
    created_at  TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ontology.ecos_workflow_log IS '工作流日志表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_onto_wflog_inst ON ecos_ontology.ecos_workflow_log(instance_id, created_at)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
