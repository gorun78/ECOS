-- ============================================================================
-- ECOS Data Domain — MySQL DDL
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

-- ============================================
-- Table: td_datasource
-- Description: 数据源表
-- ============================================
CREATE TABLE IF NOT EXISTS td_datasource (
    datasource_id     VARCHAR(64)  PRIMARY KEY COMMENT '数据源ID',
    datasource_name   VARCHAR(128) NOT NULL COMMENT '数据源名称',
    datasource_type   VARCHAR(32)  DEFAULT 'JDBC' COMMENT '类型: JDBC/API/FILE/MQ',
    org_id            VARCHAR(64) COMMENT '组织ID',
    node_id           VARCHAR(64) COMMENT '节点ID',
    description       LONGTEXT COMMENT '描述',
    connection_config LONGTEXT COMMENT '连接配置',
    status            VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT '状态',
    tags              VARCHAR(256) COMMENT '标签',
    last_test_time    DATETIME COMMENT '最后测试时间',
    last_test_result  TINYINT(1) COMMENT '最后测试结果',
    last_test_message LONGTEXT COMMENT '最后测试消息',
    tenant_id         VARCHAR(64) COMMENT '租户ID',
    create_by         VARCHAR(64) COMMENT '创建人',
    create_time       DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    update_by         VARCHAR(64) COMMENT '更新人',
    update_time       DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源表';

CREATE INDEX idx_data_ds_org    ON td_datasource(org_id);
CREATE INDEX idx_data_ds_status ON td_datasource(status);
CREATE INDEX idx_data_ds_tenant ON td_datasource(tenant_id);

-- ============================================
-- Table: td_data_resource
-- Description: 数据资源表
-- ============================================
CREATE TABLE IF NOT EXISTS td_data_resource (
    resource_id     VARCHAR(64)  PRIMARY KEY COMMENT '资源ID',
    resource_name   VARCHAR(256) NOT NULL COMMENT '资源名称',
    resource_type   VARCHAR(32)  DEFAULT 'TABLE' COMMENT '资源类型',
    org_id          VARCHAR(64) COMMENT '组织ID',
    org_name        VARCHAR(128) COMMENT '组织名称',
    datasource_id   VARCHAR(64) COMMENT '数据源ID',
    source_path     VARCHAR(512) COMMENT '源路径',
    description     LONGTEXT COMMENT '描述',
    tags            VARCHAR(256) COMMENT '标签',
    status          VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT '状态',
    field_count     INT          DEFAULT 0 COMMENT '字段数',
    record_count    BIGINT       DEFAULT 0 COMMENT '记录数',
    last_sync_time  DATETIME COMMENT '最后同步时间',
    tenant_id       VARCHAR(64) COMMENT '租户ID',
    create_by       VARCHAR(64) COMMENT '创建人',
    create_time     DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    update_by       VARCHAR(64) COMMENT '更新人',
    update_time     DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据资源表';

CREATE INDEX idx_data_res_ds     ON td_data_resource(datasource_id);
CREATE INDEX idx_data_res_org    ON td_data_resource(org_id);
CREATE INDEX idx_data_res_tenant ON td_data_resource(tenant_id);

-- ============================================
-- Table: td_data_field
-- Description: 数据字段表
-- ============================================
CREATE TABLE IF NOT EXISTS td_data_field (
    field_id       VARCHAR(64)  PRIMARY KEY COMMENT '字段ID',
    resource_id    VARCHAR(64)  NOT NULL COMMENT '资源ID',
    field_name     VARCHAR(256) NOT NULL COMMENT '字段名',
    field_alias    VARCHAR(256) COMMENT '字段别名',
    field_type     VARCHAR(64) COMMENT '字段类型',
    field_length   INT COMMENT '字段长度',
    data_precision INT COMMENT '精度',
    nullable       SMALLINT     DEFAULT 1 COMMENT '是否可空',
    is_primary_key SMALLINT     DEFAULT 0 COMMENT '是否主键',
    default_value  VARCHAR(256) COMMENT '默认值',
    description    LONGTEXT COMMENT '描述',
    field_order    INT          DEFAULT 0 COMMENT '字段顺序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据字段表';

CREATE INDEX idx_data_field_res ON td_data_field(resource_id);

-- ============================================
-- Table: td_catalog_item
-- Description: 数据目录项表
-- ============================================
CREATE TABLE IF NOT EXISTS td_catalog_item (
    catalog_id      VARCHAR(64)  PRIMARY KEY COMMENT '目录ID',
    resource_id     VARCHAR(64) COMMENT '资源ID',
    resource_name   VARCHAR(256) COMMENT '资源名称',
    resource_type   VARCHAR(32)  DEFAULT 'TABLE' COMMENT '资源类型',
    org_name        VARCHAR(128) COMMENT '组织名称',
    description     LONGTEXT COMMENT '描述',
    tags            VARCHAR(256) COMMENT '标签',
    category_path   VARCHAR(256) COMMENT '分类路径',
    access_type     VARCHAR(32)  DEFAULT 'READ' COMMENT '访问类型',
    data_format     VARCHAR(32) COMMENT '数据格式',
    field_count     INT          DEFAULT 0 COMMENT '字段数',
    record_count    BIGINT       DEFAULT 0 COMMENT '记录数',
    last_updated    DATETIME COMMENT '最后更新',
    status          VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT '状态',
    tenant_id       VARCHAR(64) COMMENT '租户ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据目录项表';

CREATE INDEX idx_data_cat_res   ON td_catalog_item(resource_id);
CREATE INDEX idx_data_cat_type  ON td_catalog_item(resource_type);
CREATE INDEX idx_data_cat_path  ON td_catalog_item(category_path);

-- ============================================
-- Table: td_data_category
-- Description: 数据分类表
-- ============================================
CREATE TABLE IF NOT EXISTS td_data_category (
    category_id   VARCHAR(64)  PRIMARY KEY COMMENT '分类ID',
    category_name VARCHAR(256) NOT NULL COMMENT '分类名称',
    parent_id     VARCHAR(64) COMMENT '父分类ID',
    path          VARCHAR(512) COMMENT '路径',
    level         INT          DEFAULT 1 COMMENT '层级',
    sort_order    INT          DEFAULT 0 COMMENT '排序',
    description   LONGTEXT COMMENT '描述',
    status        VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT '状态',
    tenant_id     VARCHAR(64) COMMENT '租户ID',
    create_by     VARCHAR(64) COMMENT '创建人',
    create_time   DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    update_by     VARCHAR(64) COMMENT '更新人',
    update_time   DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据分类表';

CREATE INDEX idx_data_cat_parent ON td_data_category(parent_id);

-- ============================================
-- Table: ecos_pipeline_definition
-- Description: 管道定义表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_pipeline_definition (
    id          VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    name        VARCHAR(255) NOT NULL COMMENT '名称',
    description LONGTEXT COMMENT '描述',
    definition  JSON COMMENT '定义',
    status      VARCHAR(32)  DEFAULT 'DRAFT' COMMENT '状态',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管道定义表';

CREATE INDEX idx_data_pdef_status ON ecos_pipeline_definition(status);
CREATE INDEX idx_data_pdef_tenant ON ecos_pipeline_definition(tenant_id);

-- ============================================
-- Table: ecos_pipeline_execution
-- Description: 管道执行表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_pipeline_execution (
    id              VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    pipeline_id     VARCHAR(64)  NOT NULL COMMENT '管道ID',
    status          VARCHAR(32)  DEFAULT 'PENDING' COMMENT '状态',
    started_at      DATETIME COMMENT '开始时间',
    finished_at     DATETIME COMMENT '完成时间',
    error_message   LONGTEXT COMMENT '错误消息',
    rows_processed  BIGINT       DEFAULT 0 COMMENT '处理行数',
    tenant_id       VARCHAR(64) COMMENT '租户ID',
    created_at      DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管道执行表';

CREATE INDEX idx_data_pexec_pipeline ON ecos_pipeline_execution(pipeline_id);
CREATE INDEX idx_data_pexec_status   ON ecos_pipeline_execution(status);
CREATE INDEX idx_data_pexec_tenant   ON ecos_pipeline_execution(tenant_id);

-- ============================================
-- Table: ecos_pipeline_node
-- Description: 管道节点表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_pipeline_node (
    id            VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    definition_id VARCHAR(64)  NOT NULL COMMENT '定义ID',
    node_id       VARCHAR(64)  NOT NULL COMMENT '节点ID',
    type          VARCHAR(64)  DEFAULT 'TRANSFORM_SQL' COMMENT '类型',
    config        JSON COMMENT '配置',
    depends_on    JSON COMMENT '依赖',
    position_x    INT          DEFAULT 0 COMMENT 'X坐标',
    position_y    INT          DEFAULT 0 COMMENT 'Y坐标',
    created_at    DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管道节点表';

CREATE INDEX idx_data_pnode_def ON ecos_pipeline_node(definition_id);

-- ============================================
-- Table: ecos_pipeline_edge
-- Description: 管道边表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_pipeline_edge (
    id            VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    definition_id VARCHAR(64)  NOT NULL COMMENT '定义ID',
    from_node_id  VARCHAR(64)  NOT NULL COMMENT '起始节点ID',
    to_node_id    VARCHAR(64)  NOT NULL COMMENT '目标节点ID',
    created_at    DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管道边表';

CREATE INDEX idx_data_pedge_def ON ecos_pipeline_edge(definition_id);

-- ============================================
-- Table: ecos_dq_rule
-- Description: 数据质量规则表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_dq_rule (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'ID',
    name            VARCHAR(255)  NOT NULL COMMENT '名称',
    description     LONGTEXT COMMENT '描述',
    rule_type       VARCHAR(64)   DEFAULT 'NOT_NULL' COMMENT '规则类型',
    config_json     LONGTEXT COMMENT '配置JSON',
    severity        VARCHAR(32)   DEFAULT 'HIGH' COMMENT '严重级别',
    enabled         TINYINT(1)    DEFAULT 1 COMMENT '是否启用',
    target_entity   VARCHAR(128) COMMENT '目标实体',
    target_field    VARCHAR(128) COMMENT '目标字段',
    rule_expression LONGTEXT COMMENT '规则表达式',
    code            VARCHAR(64) COMMENT '编码',
    status          VARCHAR(32)   DEFAULT 'ACTIVE' COMMENT '状态',
    tenant_id       VARCHAR(64) COMMENT '租户ID',
    created_at      DATETIME      NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at      DATETIME      NOT NULL DEFAULT NOW() COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据质量规则表';

CREATE INDEX idx_data_dq_type   ON ecos_dq_rule(rule_type);
CREATE INDEX idx_data_dq_tenant ON ecos_dq_rule(tenant_id);

-- ============================================
-- Table: ecos_dq_issue
-- Description: 数据质量问题表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_dq_issue (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    rule_id     BIGINT       NOT NULL COMMENT '规则ID',
    asset_id    VARCHAR(255) DEFAULT '' COMMENT '资产ID',
    description LONGTEXT COMMENT '描述',
    status      VARCHAR(32)  DEFAULT 'open' COMMENT '状态',
    severity    VARCHAR(32)  DEFAULT 'HIGH' COMMENT '严重级别',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    resolved_at DATETIME COMMENT '解决时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据质量问题表';

CREATE INDEX idx_data_dqi_rule ON ecos_dq_issue(rule_id);
ALTER TABLE ecos_dq_issue
    ADD CONSTRAINT fk_data_dqi_rule FOREIGN KEY (rule_id) REFERENCES ecos_dq_rule(id) ON DELETE CASCADE;

-- ============================================
-- Table: ecos_dq_execution_result
-- Description: 数据质量执行结果表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_dq_execution_result (
    id            VARCHAR(64) PRIMARY KEY COMMENT 'ID',
    rule_id       VARCHAR(64) COMMENT '规则ID',
    passed        TINYINT(1)  DEFAULT 0 COMMENT '是否通过',
    total_rows    INT         DEFAULT 0 COMMENT '总行数',
    failed_rows   INT         DEFAULT 0 COMMENT '失败行数',
    error_details LONGTEXT COMMENT '错误详情',
    executed_at   DATETIME    NOT NULL DEFAULT NOW() COMMENT '执行时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据质量执行结果表';

CREATE INDEX idx_data_dqr_rule ON ecos_dq_execution_result(rule_id);
CREATE INDEX idx_data_dqr_time ON ecos_dq_execution_result(executed_at DESC);

-- ============================================
-- Table: ecos_cognitive_rule
-- Description: 认知规则表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_cognitive_rule (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'ID',
    rule_name      VARCHAR(128)  NOT NULL COMMENT '规则名称',
    rule_type      VARCHAR(32) COMMENT '规则类型',
    condition_expr LONGTEXT COMMENT '条件表达式',
    action_config  JSON COMMENT '动作配置',
    priority       INT           DEFAULT 0 COMMENT '优先级',
    enabled        TINYINT(1)    DEFAULT 1 COMMENT '是否启用',
    description    LONGTEXT COMMENT '描述',
    tenant_id      VARCHAR(64) COMMENT '租户ID',
    created_by     VARCHAR(64) COMMENT '创建人',
    created_at     DATETIME      NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at     DATETIME      NOT NULL DEFAULT NOW() COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='认知规则表';

CREATE INDEX idx_data_cog_type    ON ecos_cognitive_rule(rule_type);
CREATE INDEX idx_data_cog_enabled ON ecos_cognitive_rule(enabled);

-- ============================================
-- Table: ecos_task
-- Description: 任务表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_task (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'ID',
    task_name     VARCHAR(256)  NOT NULL COMMENT '任务名称',
    task_type     VARCHAR(32) COMMENT '任务类型',
    status        VARCHAR(32)   DEFAULT 'PENDING' COMMENT '状态',
    config        JSON COMMENT '配置',
    runner        VARCHAR(64) COMMENT '执行者',
    priority      INT           DEFAULT 0 COMMENT '优先级',
    retry_count   INT           DEFAULT 0 COMMENT '重试次数',
    max_retries   INT           DEFAULT 3 COMMENT '最大重试次数',
    scheduled_at  DATETIME COMMENT '计划时间',
    started_at    DATETIME COMMENT '开始时间',
    completed_at  DATETIME COMMENT '完成时间',
    result        JSON COMMENT '结果',
    error_message LONGTEXT COMMENT '错误消息',
    tenant_id     VARCHAR(64) COMMENT '租户ID',
    created_by    VARCHAR(64) COMMENT '创建人',
    created_at    DATETIME      NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at    DATETIME      NOT NULL DEFAULT NOW() COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';

CREATE INDEX idx_data_task_status ON ecos_task(status);
CREATE INDEX idx_data_task_type   ON ecos_task(task_type);
CREATE INDEX idx_data_task_time   ON ecos_task(created_at DESC);

-- ============================================
-- Foreign Keys (cross-domain)
-- ============================================
ALTER TABLE td_datasource
    ADD CONSTRAINT fk_data_ds_org FOREIGN KEY (org_id) REFERENCES td_organization(`ORG_ID`);
ALTER TABLE td_data_resource
    ADD CONSTRAINT fk_data_res_ds  FOREIGN KEY (datasource_id) REFERENCES td_datasource(datasource_id);
ALTER TABLE td_data_field
    ADD CONSTRAINT fk_data_field_res FOREIGN KEY (resource_id) REFERENCES td_data_resource(resource_id);
ALTER TABLE ecos_pipeline_execution
    ADD CONSTRAINT fk_data_pexec_def FOREIGN KEY (pipeline_id) REFERENCES ecos_pipeline_definition(id);
ALTER TABLE ecos_pipeline_node
    ADD CONSTRAINT fk_data_pnode_def FOREIGN KEY (definition_id) REFERENCES ecos_pipeline_definition(id);
ALTER TABLE ecos_pipeline_edge
    ADD CONSTRAINT fk_data_pedge_def FOREIGN KEY (definition_id) REFERENCES ecos_pipeline_definition(id);
