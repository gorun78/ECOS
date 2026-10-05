-- ============================================================================
-- ECOS Ontology Domain — MySQL DDL
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

-- ============================================
-- Table: ecos_domain
-- Description: 业务域表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_domain (
    id          VARCHAR(50)  PRIMARY KEY COMMENT 'ID',
    code        VARCHAR(100) NOT NULL COMMENT '编码',
    name        VARCHAR(200) NOT NULL COMMENT '名称',
    owner       VARCHAR(100) COMMENT '负责人',
    description LONGTEXT COMMENT '描述',
    status      VARCHAR(50)  DEFAULT 'Draft' COMMENT '状态',
    sort_order  INT          DEFAULT 0 COMMENT '排序',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务域表';

CREATE UNIQUE INDEX idx_onto_domain_code ON ecos_domain(code);

-- ============================================
-- Table: ecos_ontology_entity
-- Description: 本体实体表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology_entity (
    id          VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    ontology_id VARCHAR(64) COMMENT '本体ID',
    code        VARCHAR(255) NOT NULL COMMENT '编码',
    name        VARCHAR(255) NOT NULL COMMENT '名称',
    description LONGTEXT COMMENT '描述',
    entity_type VARCHAR(64)  DEFAULT 'MASTER' COMMENT '实体类型',
    sort_order  INT          DEFAULT 1 COMMENT '排序',
    status      VARCHAR(32)  DEFAULT 'ACTIVE' COMMENT '状态',
    table_name  VARCHAR(128) COMMENT '表名',
    domain_id   VARCHAR(50) COMMENT '域ID',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本体实体表';

CREATE INDEX idx_onto_ent_onto    ON ecos_ontology_entity(ontology_id);
CREATE INDEX idx_onto_ent_code    ON ecos_ontology_entity(code);
CREATE INDEX idx_onto_ent_domain  ON ecos_ontology_entity(domain_id);
CREATE INDEX idx_onto_ent_tenant  ON ecos_ontology_entity(tenant_id);
ALTER TABLE ecos_ontology_entity
    ADD CONSTRAINT fk_onto_ent_domain FOREIGN KEY (domain_id) REFERENCES ecos_domain(id);

-- ============================================
-- Table: ecos_ontology_property
-- Description: 本体属性表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology_property (
    id               VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    entity_id        VARCHAR(64)  NOT NULL COMMENT '实体ID',
    code             VARCHAR(255) NOT NULL COMMENT '编码',
    name             VARCHAR(255) NOT NULL COMMENT '名称',
    property_type    VARCHAR(64)  DEFAULT 'STRING' COMMENT '属性类型',
    required_flag    INT          DEFAULT 0 COMMENT '是否必填',
    searchable_flag  INT          DEFAULT 0 COMMENT '是否可搜索',
    sort_order       INT          DEFAULT 1 COMMENT '排序',
    enum_values      LONGTEXT COMMENT '枚举值',
    default_value    VARCHAR(500) COMMENT '默认值',
    validation_rule  LONGTEXT COMMENT '校验规则',
    unique_flag      INT          DEFAULT 0 COMMENT '是否唯一',
    ref_entity_code  VARCHAR(100) COMMENT '引用实体编码',
    max_length       INT COMMENT '最大长度',
    min_value        DECIMAL(20,6) COMMENT '最小值',
    max_value        DECIMAL(20,6) COMMENT '最大值',
    function_type    VARCHAR(32) COMMENT '函数类型',
    function_expression LONGTEXT COMMENT '函数表达式',
    created_at       DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本体属性表';

CREATE INDEX idx_onto_prop_ent ON ecos_ontology_property(entity_id);
ALTER TABLE ecos_ontology_property
    ADD CONSTRAINT fk_onto_prop_ent FOREIGN KEY (entity_id) REFERENCES ecos_ontology_entity(id);

-- ============================================
-- Table: ecos_ontology_relationship
-- Description: 本体关系表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology_relationship (
    id                VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    source_entity_id  VARCHAR(64)  NOT NULL COMMENT '源实体ID',
    target_entity_id  VARCHAR(64)  NOT NULL COMMENT '目标实体ID',
    code              VARCHAR(255) COMMENT '编码',
    name              VARCHAR(255) COMMENT '名称',
    relationship_type VARCHAR(64)  DEFAULT 'ONE_TO_MANY' COMMENT '关系类型',
    created_at        DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本体关系表';

CREATE INDEX idx_onto_rel_src ON ecos_ontology_relationship(source_entity_id);
CREATE INDEX idx_onto_rel_tgt ON ecos_ontology_relationship(target_entity_id);

-- ============================================
-- Table: ecos_ontology_action
-- Description: 本体动作表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology_action (
    id            VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    entity_id     VARCHAR(64)  NOT NULL COMMENT '实体ID',
    name          VARCHAR(255) NOT NULL COMMENT '名称',
    action_type   VARCHAR(64)  DEFAULT 'CUSTOM' COMMENT '动作类型',
    rule_json     LONGTEXT COMMENT '规则JSON',
    strategy      VARCHAR(255) COMMENT '策略',
    status        VARCHAR(32)  DEFAULT 'ACTIVE' COMMENT '状态',
    code          VARCHAR(100) COMMENT '编码',
    description   LONGTEXT COMMENT '描述',
    preconditions LONGTEXT COMMENT '前置条件',
    effects       LONGTEXT COMMENT '效果',
    created_at    DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本体动作表';

CREATE INDEX idx_onto_act_ent ON ecos_ontology_action(entity_id);

-- ============================================
-- Table: ecos_ontology_rule
-- Description: 本体规则表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology_rule (
    id          VARCHAR(50)  PRIMARY KEY COMMENT 'ID',
    entity_id   VARCHAR(50)  NOT NULL COMMENT '实体ID',
    code        VARCHAR(100) NOT NULL COMMENT '编码',
    name        VARCHAR(200) COMMENT '名称',
    rule_type   VARCHAR(50) COMMENT '规则类型',
    expression  LONGTEXT COMMENT '表达式',
    action      LONGTEXT COMMENT '动作',
    priority    INT          DEFAULT 0 COMMENT '优先级',
    enabled     INT          DEFAULT 1 COMMENT '是否启用',
    description LONGTEXT COMMENT '描述',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本体规则表';

CREATE UNIQUE INDEX idx_onto_rule_ent_code ON ecos_ontology_rule(entity_id, code);

-- ============================================
-- Table: ecos_ontology_version
-- Description: 本体版本表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ontology_version (
    id           VARCHAR(50) PRIMARY KEY COMMENT 'ID',
    ontology_id  VARCHAR(50) NOT NULL COMMENT '本体ID',
    version_no   VARCHAR(20) NOT NULL COMMENT '版本号',
    status       VARCHAR(50) DEFAULT 'Draft' COMMENT '状态',
    snapshot     JSON COMMENT '快照',
    change_log   LONGTEXT COMMENT '变更日志',
    publisher    VARCHAR(100) COMMENT '发布人',
    published_at DATETIME COMMENT '发布时间',
    created_at   DATETIME    NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本体版本表';

CREATE UNIQUE INDEX idx_onto_ver_onto_vno ON ecos_ontology_version(ontology_id, version_no);

-- ============================================
-- Table: ecos_business_glossary
-- Description: 业务术语表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_business_glossary (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'ID',
    code        VARCHAR(64) COMMENT '编码',
    name        VARCHAR(255)  NOT NULL COMMENT '名称',
    definition  LONGTEXT COMMENT '定义',
    domain      VARCHAR(128) COMMENT '域',
    domain_id   VARCHAR(50) COMMENT '域ID',
    owner       VARCHAR(128) COMMENT '负责人',
    status      VARCHAR(32)   DEFAULT 'DRAFT' COMMENT '状态',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_by  VARCHAR(128) COMMENT '创建人',
    created_at  DATETIME      NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at  DATETIME      NOT NULL DEFAULT NOW() COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务术语表';

CREATE INDEX idx_onto_gloss_domain ON ecos_business_glossary(domain);
CREATE INDEX idx_onto_gloss_status ON ecos_business_glossary(status);
ALTER TABLE ecos_business_glossary
    ADD CONSTRAINT fk_onto_gloss_domain FOREIGN KEY (domain_id) REFERENCES ecos_domain(id);

-- ============================================
-- Table: entity_definition
-- Description: 实体定义表(本体编译器)
-- ============================================
CREATE TABLE IF NOT EXISTS entity_definition (
    id          VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    code        VARCHAR(128) NOT NULL COMMENT '编码',
    name        VARCHAR(255) NOT NULL COMMENT '名称',
    description LONGTEXT COMMENT '描述',
    category    VARCHAR(32)  DEFAULT 'MASTER' COMMENT '分类',
    properties  JSON COMMENT '属性',
    lifecycle   JSON COMMENT '生命周期',
    version     INT          DEFAULT 1 COMMENT '版本',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体定义表(本体编译器)';

CREATE UNIQUE INDEX idx_onto_entdef_code ON entity_definition(code);

-- ============================================
-- Table: relationship_definition
-- Description: 关系定义表(本体编译器)
-- ============================================
CREATE TABLE IF NOT EXISTS relationship_definition (
    id             VARCHAR(64) PRIMARY KEY COMMENT 'ID',
    source_entity  VARCHAR(128) NOT NULL COMMENT '源实体',
    target_entity  VARCHAR(128) NOT NULL COMMENT '目标实体',
    type           VARCHAR(32) COMMENT '类型',
    cardinality    VARCHAR(16)  DEFAULT 'ONE_TO_MANY' COMMENT '基数',
    properties     JSON COMMENT '属性',
    created_at     DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关系定义表(本体编译器)';

-- ============================================
-- Table: metric_definition
-- Description: 指标定义表(本体编译器)
-- ============================================
CREATE TABLE IF NOT EXISTS metric_definition (
    id          VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    code        VARCHAR(128) NOT NULL COMMENT '编码',
    name        VARCHAR(255) NOT NULL COMMENT '名称',
    expression  LONGTEXT COMMENT '表达式',
    aggregation VARCHAR(16)  DEFAULT 'SUM' COMMENT '聚合方式',
    entity_code VARCHAR(128) COMMENT '实体编码',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指标定义表(本体编译器)';

CREATE UNIQUE INDEX idx_onto_metric_code ON metric_definition(code);

-- ============================================
-- Table: action_definition
-- Description: 动作定义表(本体编译器)
-- ============================================
CREATE TABLE IF NOT EXISTS action_definition (
    id          VARCHAR(64) PRIMARY KEY COMMENT 'ID',
    code        VARCHAR(128) COMMENT '编码',
    name        VARCHAR(255) COMMENT '名称',
    type        VARCHAR(16)  DEFAULT 'WORKFLOW' COMMENT '类型',
    config      JSON COMMENT '配置',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作定义表(本体编译器)';

-- ============================================
-- Table: policy_definition
-- Description: 策略定义表(本体编译器)
-- ============================================
CREATE TABLE IF NOT EXISTS policy_definition (
    id          VARCHAR(64) PRIMARY KEY COMMENT 'ID',
    code        VARCHAR(128) COMMENT '编码',
    type        VARCHAR(32) COMMENT '类型',
    expression  LONGTEXT COMMENT '表达式',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='策略定义表(本体编译器)';

-- ============================================
-- Table: event_definition
-- Description: 事件定义表(本体编译器)
-- ============================================
CREATE TABLE IF NOT EXISTS event_definition (
    id             VARCHAR(64) PRIMARY KEY COMMENT 'ID',
    code           VARCHAR(128) COMMENT '编码',
    source         VARCHAR(128) COMMENT '来源',
    payload_schema JSON COMMENT '载荷Schema',
    created_at     DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件定义表(本体编译器)';

-- ============================================
-- Table: ecos_object_state_machine
-- Description: 对象状态机表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_object_state_machine (
    id              VARCHAR(50)  PRIMARY KEY COMMENT 'ID',
    entity_code     VARCHAR(100) NOT NULL COMMENT '实体编码',
    from_status     VARCHAR(50) COMMENT '源状态',
    to_status       VARCHAR(50) COMMENT '目标状态',
    transition_code VARCHAR(100) COMMENT '转换编码',
    transition_name VARCHAR(200) COMMENT '转换名称',
    require_role    VARCHAR(200) COMMENT '所需角色',
    guard_rule      LONGTEXT COMMENT '守卫规则',
    side_effect     LONGTEXT COMMENT '副作用',
    sort_order      INT          DEFAULT 0 COMMENT '排序',
    tenant_id       VARCHAR(64) COMMENT '租户ID',
    created_at      DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对象状态机表';

CREATE UNIQUE INDEX idx_onto_sm_uniq ON ecos_object_state_machine(entity_code, from_status, transition_code);

-- ============================================
-- Table: ecos_object_timeline
-- Description: 对象时间线表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_object_timeline (
    id            VARCHAR(50) PRIMARY KEY COMMENT 'ID',
    object_id     VARCHAR(100) NOT NULL COMMENT '对象ID',
    entity_code   VARCHAR(100) COMMENT '实体编码',
    event_type    VARCHAR(100) COMMENT '事件类型',
    event_summary VARCHAR(500) COMMENT '事件摘要',
    actor         VARCHAR(100) COMMENT '执行者',
    details       JSON COMMENT '详情',
    tenant_id     VARCHAR(64) COMMENT '租户ID',
    created_at    DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对象时间线表';

CREATE INDEX idx_onto_timeline_obj ON ecos_object_timeline(object_id, created_at DESC);

-- ============================================
-- Table: ecos_object_version
-- Description: 对象版本表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_object_version (
    id             VARCHAR(50) PRIMARY KEY COMMENT 'ID',
    object_id      VARCHAR(100) NOT NULL COMMENT '对象ID',
    entity_code    VARCHAR(100) COMMENT '实体编码',
    version_no     INT COMMENT '版本号',
    snapshot       JSON COMMENT '快照',
    change_summary VARCHAR(500) COMMENT '变更摘要',
    created_by     VARCHAR(100) COMMENT '创建人',
    tenant_id      VARCHAR(64) COMMENT '租户ID',
    created_at     DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对象版本表';

CREATE UNIQUE INDEX idx_onto_objver_uniq ON ecos_object_version(object_id, version_no);

-- ============================================
-- Table: ecos_object_relationship
-- Description: 对象关系表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_object_relationship (
    id                  VARCHAR(50) PRIMARY KEY COMMENT 'ID',
    source_object_id    VARCHAR(100) COMMENT '源对象ID',
    target_object_id    VARCHAR(100) COMMENT '目标对象ID',
    source_entity_code  VARCHAR(100) COMMENT '源实体编码',
    target_entity_code  VARCHAR(100) COMMENT '目标实体编码',
    relationship_code   VARCHAR(100) COMMENT '关系编码',
    relationship_type   VARCHAR(50) COMMENT '关系类型',
    properties          JSON COMMENT '属性',
    tenant_id           VARCHAR(64) COMMENT '租户ID',
    created_at          DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对象关系表';

CREATE INDEX idx_onto_objrel_src ON ecos_object_relationship(source_object_id);
CREATE INDEX idx_onto_objrel_tgt ON ecos_object_relationship(target_object_id);

-- ============================================
-- Table: ecos_object_attachment
-- Description: 对象附件表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_object_attachment (
    id          VARCHAR(50)  PRIMARY KEY COMMENT 'ID',
    object_id   VARCHAR(100) NOT NULL COMMENT '对象ID',
    entity_code VARCHAR(100) COMMENT '实体编码',
    file_name   VARCHAR(500) COMMENT '文件名',
    file_path   VARCHAR(1000) COMMENT '文件路径',
    file_size   BIGINT COMMENT '文件大小',
    mime_type   VARCHAR(200) COMMENT 'MIME类型',
    version_no  INT          DEFAULT 1 COMMENT '版本号',
    uploaded_by VARCHAR(100) COMMENT '上传人',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对象附件表';

-- ============================================
-- Table: ecos_object_links
-- Description: 对象链接表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_object_links (
    id            VARCHAR(64) PRIMARY KEY COMMENT 'ID',
    source_id     VARCHAR(64) COMMENT '源ID',
    target_id     VARCHAR(64) COMMENT '目标ID',
    relation_code VARCHAR(128) COMMENT '关系编码',
    tenant_id     VARCHAR(64) COMMENT '租户ID',
    created_at    DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对象链接表';

CREATE INDEX idx_onto_links_src ON ecos_object_links(source_id);
CREATE INDEX idx_onto_links_tgt ON ecos_object_links(target_id);

-- ============================================
-- Table: ecos_object_data
-- Description: 对象数据表(运行时)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_object_data (
    id          VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    entity_code VARCHAR(128) NOT NULL COMMENT '实体编码',
    object_data JSON COMMENT '对象数据',
    status      VARCHAR(32)  DEFAULT 'ACTIVE' COMMENT '状态',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对象数据表(运行时)';

CREATE INDEX idx_onto_objdata_entity ON ecos_object_data(entity_code);
CREATE INDEX idx_onto_objdata_tenant ON ecos_object_data(tenant_id);

-- ============================================
-- Table: ecos_object_relation
-- Description: 对象关联表(桥接)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_object_relation (
    id                  VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    source_entity_code  VARCHAR(128) COMMENT '源实体编码',
    target_entity_code  VARCHAR(128) COMMENT '目标实体编码',
    relation_type       VARCHAR(64) COMMENT '关系类型',
    description         LONGTEXT COMMENT '描述',
    tenant_id           VARCHAR(64) COMMENT '租户ID',
    created_at          DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对象关联表(桥接)';

CREATE INDEX idx_onto_objrel_tenant ON ecos_object_relation(tenant_id);

-- ============================================
-- Table: ecos_workflow
-- Description: 工作流定义表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_workflow (
    id           VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    name         VARCHAR(255) NOT NULL COMMENT '名称',
    description  LONGTEXT COMMENT '描述',
    status       VARCHAR(32)  DEFAULT 'draft' COMMENT '状态',
    mode         VARCHAR(32)  DEFAULT 'sequential' COMMENT '模式',
    nodes        LONGTEXT COMMENT '节点',
    edges        LONGTEXT COMMENT '边',
    published_at DATETIME COMMENT '发布时间',
    tenant_id    VARCHAR(64) COMMENT '租户ID',
    created_at   DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at   DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流定义表';

-- ============================================
-- Table: ecos_workflow_instance
-- Description: 工作流实例表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_workflow_instance (
    id                  VARCHAR(50) PRIMARY KEY COMMENT 'ID',
    workflow_id         VARCHAR(50) COMMENT '工作流ID',
    workflow_name       VARCHAR(200) COMMENT '工作流名称',
    version_no          VARCHAR(20) COMMENT '版本号',
    status              VARCHAR(50) COMMENT '状态',
    trigger_type        VARCHAR(50) COMMENT '触发类型',
    triggered_by        VARCHAR(100) COMMENT '触发人',
    triggered_object_id VARCHAR(100) COMMENT '触发对象ID',
    trigger_event       VARCHAR(200) COMMENT '触发事件',
    variables           JSON COMMENT '变量',
    context             JSON COMMENT '上下文',
    current_node_ids    JSON COMMENT '当前节点ID',
    started_at          DATETIME COMMENT '开始时间',
    completed_at        DATETIME COMMENT '完成时间',
    error_message       LONGTEXT COMMENT '错误消息',
    retry_count         INT         DEFAULT 0 COMMENT '重试次数',
    tenant_id           VARCHAR(64) COMMENT '租户ID',
    created_at          DATETIME    NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at          DATETIME    NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流实例表';

CREATE INDEX idx_onto_wfinst_status ON ecos_workflow_instance(status);
CREATE INDEX idx_onto_wfinst_wf     ON ecos_workflow_instance(workflow_id);
CREATE INDEX idx_onto_wfinst_tenant ON ecos_workflow_instance(tenant_id);

-- ============================================
-- Table: ecos_workflow_task
-- Description: 工作流任务表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_workflow_task (
    id              VARCHAR(50) PRIMARY KEY COMMENT 'ID',
    instance_id     VARCHAR(50) COMMENT '实例ID',
    node_id         VARCHAR(100) COMMENT '节点ID',
    task_type       VARCHAR(50) COMMENT '任务类型',
    title           VARCHAR(500) COMMENT '标题',
    assignee        VARCHAR(100) COMMENT '执行人',
    candidate_users JSON COMMENT '候选用户',
    candidate_roles JSON COMMENT '候选角色',
    status          VARCHAR(50) DEFAULT 'New' COMMENT '状态',
    priority        VARCHAR(20) DEFAULT 'NORMAL' COMMENT '优先级',
    form_schema     JSON COMMENT '表单Schema',
    form_data       JSON COMMENT '表单数据',
    result          JSON COMMENT '结果',
    agent_result    JSON COMMENT 'Agent结果',
    due_date        DATETIME COMMENT '截止日期',
    completed_at    DATETIME COMMENT '完成时间',
    completed_by    VARCHAR(100) COMMENT '完成人',
    tenant_id       VARCHAR(64) COMMENT '租户ID',
    created_at      DATETIME    NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at      DATETIME    NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流任务表';

CREATE INDEX idx_onto_wftask_status ON ecos_workflow_task(status);
CREATE INDEX idx_onto_wftask_assign ON ecos_workflow_task(assignee);
CREATE INDEX idx_onto_wftask_inst   ON ecos_workflow_task(instance_id);

-- ============================================
-- Table: ecos_workflow_approval
-- Description: 工作流审批表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_workflow_approval (
    id          VARCHAR(50) PRIMARY KEY COMMENT 'ID',
    task_id     VARCHAR(50) COMMENT '任务ID',
    instance_id VARCHAR(50) COMMENT '实例ID',
    approver    VARCHAR(100) COMMENT '审批人',
    decision    VARCHAR(50) COMMENT '决定',
    opinion     LONGTEXT COMMENT '意见',
    form_data   JSON COMMENT '表单数据',
    created_at  DATETIME    NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流审批表';

CREATE INDEX idx_onto_wfappr_task ON ecos_workflow_approval(task_id);
CREATE INDEX idx_onto_wfappr_inst ON ecos_workflow_approval(instance_id);

-- ============================================
-- Table: ecos_workflow_log
-- Description: 工作流日志表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_workflow_log (
    id          VARCHAR(50) PRIMARY KEY COMMENT 'ID',
    instance_id VARCHAR(50) COMMENT '实例ID',
    node_id     VARCHAR(100) COMMENT '节点ID',
    node_type   VARCHAR(50) COMMENT '节点类型',
    event_type  VARCHAR(100) COMMENT '事件类型',
    message     LONGTEXT COMMENT '消息',
    details     JSON COMMENT '详情',
    duration_ms BIGINT COMMENT '耗时(ms)',
    trace_id    VARCHAR(100) COMMENT 'Trace ID',
    created_at  DATETIME    NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流日志表';

CREATE INDEX idx_onto_wflog_inst ON ecos_workflow_log(instance_id, created_at);
