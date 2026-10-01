-- ============================================================================
-- ECOS AI Domain — MySQL DDL
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

-- ============================================
-- Table: ecos_agent
-- Description: Agent配置表(旧版)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_agent (
    id             VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    name           VARCHAR(255) NOT NULL COMMENT '名称',
    model_provider VARCHAR(64)  DEFAULT 'deepseek' COMMENT '模型供应商',
    model_name     VARCHAR(128) DEFAULT 'deepseek-v4-flash' COMMENT '模型名称',
    system_prompt  LONGTEXT COMMENT '系统提示',
    tools          LONGTEXT COMMENT '工具',
    knowledge      LONGTEXT COMMENT '知识',
    status         VARCHAR(32)  DEFAULT 'draft' COMMENT '状态',
    tenant_id      VARCHAR(64) COMMENT '租户ID',
    created_at     DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at     DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent配置表(旧版)';

CREATE INDEX idx_ai_agent_status ON ecos_agent(status);

-- ============================================
-- Table: ecos_agent_registry
-- Description: Agent注册表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_agent_registry (
    id          VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    name        VARCHAR(255) NOT NULL COMMENT '名称',
    role        VARCHAR(128) COMMENT '角色',
    capability  JSON COMMENT '能力',
    status      VARCHAR(32)  DEFAULT 'ACTIVE' COMMENT '状态',
    endpoint    VARCHAR(512) COMMENT '端点',
    metadata    JSON COMMENT '元数据',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent注册表';

-- ============================================
-- Table: ecos_mission
-- Description: Mission表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_mission (
    id          VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    title       VARCHAR(256) NOT NULL COMMENT '标题',
    goal        LONGTEXT COMMENT '目标',
    mode        VARCHAR(16)  DEFAULT 'SUPERVISOR' COMMENT '模式',
    status      VARCHAR(32)  DEFAULT 'PENDING' COMMENT '状态',
    plan        JSON COMMENT '计划',
    result      JSON COMMENT '结果',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Mission表';

-- ============================================
-- Table: ecos_mission_task
-- Description: Mission任务表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_mission_task (
    id          VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    mission_id  VARCHAR(64)  NOT NULL COMMENT 'Mission ID',
    agent_id    VARCHAR(64) COMMENT 'Agent ID',
    instruction LONGTEXT COMMENT '指令',
    status      VARCHAR(32)  DEFAULT 'PENDING' COMMENT '状态',
    result      JSON COMMENT '结果',
    depends_on  VARCHAR(256) COMMENT '依赖',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Mission任务表';

CREATE INDEX idx_ai_mtask_mission ON ecos_mission_task(mission_id);
ALTER TABLE ecos_mission_task
    ADD CONSTRAINT fk_ai_mtask_mission FOREIGN KEY (mission_id) REFERENCES ecos_mission(id);

-- ============================================
-- Table: ecos_tool_definition
-- Description: 工具定义表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_tool_definition (
    id           VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    code         VARCHAR(128) NOT NULL COMMENT '编码',
    name         VARCHAR(255) NOT NULL COMMENT '名称',
    description  LONGTEXT COMMENT '描述',
    tool_type    VARCHAR(32) COMMENT '工具类型',
    endpoint_url VARCHAR(512) COMMENT '端点URL',
    http_method  VARCHAR(16) COMMENT 'HTTP方法',
    schema_json  JSON COMMENT 'Schema',
    status       VARCHAR(32)  DEFAULT 'ACTIVE' COMMENT '状态',
    tenant_id    VARCHAR(64) COMMENT '租户ID',
    created_at   DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at   DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具定义表';

CREATE UNIQUE INDEX idx_ai_tool_code ON ecos_tool_definition(code);

-- ============================================
-- Table: ecos_decision_case
-- Description: 决策案例表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_decision_case (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'ID',
    title       VARCHAR(256)  NOT NULL COMMENT '标题',
    scenario    LONGTEXT COMMENT '场景',
    tags        JSON COMMENT '标签',
    decision    JSON COMMENT '决策',
    result      JSON COMMENT '结果',
    feedback    VARCHAR(32)   DEFAULT 'pending' COMMENT '反馈',
    source      VARCHAR(64) COMMENT '来源',
    created_by  VARCHAR(128) COMMENT '创建人',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_at  DATETIME      NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at  DATETIME      NOT NULL DEFAULT NOW() COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='决策案例表';

CREATE INDEX idx_ai_dcase_tags ON ecos_decision_case((CAST(tags AS CHAR(255))));

-- ============================================
-- Table: agent_definition
-- Description: Agent定义表(V50)
-- ============================================
CREATE TABLE IF NOT EXISTS agent_definition (
    id          VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    name        VARCHAR(255) NOT NULL COMMENT '名称',
    type        VARCHAR(32) COMMENT '类型',
    role        VARCHAR(128) COMMENT '角色',
    description LONGTEXT COMMENT '描述',
    capability  JSON COMMENT '能力',
    config      JSON COMMENT '配置',
    status      VARCHAR(32)  DEFAULT 'ACTIVE' COMMENT '状态',
    version     INT          DEFAULT 1 COMMENT '版本',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent定义表(V50)';

-- ============================================
-- Table: agent_execution
-- Description: Agent执行表
-- ============================================
CREATE TABLE IF NOT EXISTS agent_execution (
    id            VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    agent_id      VARCHAR(64)  NOT NULL COMMENT 'Agent ID',
    goal          LONGTEXT COMMENT '目标',
    plan          JSON COMMENT '计划',
    status        VARCHAR(32)  DEFAULT 'CREATED' COMMENT '状态',
    result        JSON COMMENT '结果',
    started_at    DATETIME COMMENT '开始时间',
    completed_at  DATETIME COMMENT '完成时间',
    tenant_id     VARCHAR(64) COMMENT '租户ID',
    created_at    DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent执行表';

CREATE INDEX idx_ai_exec_agent  ON agent_execution(agent_id);
CREATE INDEX idx_ai_exec_status ON agent_execution(status);
ALTER TABLE agent_execution
    ADD CONSTRAINT fk_ai_exec_agent FOREIGN KEY (agent_id) REFERENCES agent_definition(id);

-- ============================================
-- Table: agent_execution_step
-- Description: Agent执行步骤表
-- ============================================
CREATE TABLE IF NOT EXISTS agent_execution_step (
    id            VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    execution_id  VARCHAR(64)  NOT NULL COMMENT '执行ID',
    step_order    INT COMMENT '步骤顺序',
    instruction   LONGTEXT COMMENT '指令',
    tool_type     VARCHAR(16) COMMENT '工具类型',
    tool_params   JSON COMMENT '工具参数',
    status        VARCHAR(32)  DEFAULT 'PENDING' COMMENT '状态',
    output        LONGTEXT COMMENT '输出',
    metrics       JSON COMMENT '指标',
    created_at    DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent执行步骤表';

CREATE INDEX idx_ai_step_exec ON agent_execution_step(execution_id);
ALTER TABLE agent_execution_step
    ADD CONSTRAINT fk_ai_step_exec FOREIGN KEY (execution_id) REFERENCES agent_execution(id);

-- ============================================
-- Table: agent_memory
-- Description: Agent记忆表
-- ============================================
CREATE TABLE IF NOT EXISTS agent_memory (
    id          VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    agent_id    VARCHAR(64) COMMENT 'Agent ID',
    session_id  VARCHAR(64) COMMENT '会话ID',
    layer       VARCHAR(16) COMMENT '层',
    content     LONGTEXT COMMENT '内容',
    embedding   JSON COMMENT '嵌入向量',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent记忆表';

CREATE INDEX idx_ai_mem_agent   ON agent_memory(agent_id);
CREATE INDEX idx_ai_mem_session ON agent_memory(session_id);

-- ============================================
-- Table: agent_cost
-- Description: Agent成本表(按季度分区)
-- Note: MySQL partitioned tables require partition column in PK
-- ============================================
CREATE TABLE IF NOT EXISTS agent_cost (
    id                VARCHAR(64)   NOT NULL COMMENT 'ID',
    agent_id          VARCHAR(64) COMMENT 'Agent ID',
    execution_id      VARCHAR(64) COMMENT '执行ID',
    prompt_tokens     INT           DEFAULT 0 COMMENT '输入Token数',
    completion_tokens INT           DEFAULT 0 COMMENT '输出Token数',
    total_cost        DECIMAL(10,4) DEFAULT 0 COMMENT '总成本',
    currency          VARCHAR(8)    DEFAULT 'CNY' COMMENT '货币',
    tenant_id         VARCHAR(64) COMMENT '租户ID',
    created_at        DATETIME      NOT NULL DEFAULT NOW() COMMENT '创建时间',
    PRIMARY KEY (id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent成本表(按季度分区)'
PARTITION BY RANGE COLUMNS(created_at) (
    PARTITION p2025_q1 VALUES LESS THAN ('2025-04-01 00:00:00'),
    PARTITION p2025_q2 VALUES LESS THAN ('2025-07-01 00:00:00'),
    PARTITION p2025_q3 VALUES LESS THAN ('2025-10-01 00:00:00'),
    PARTITION p2025_q4 VALUES LESS THAN ('2026-01-01 00:00:00'),
    PARTITION p2026_q1 VALUES LESS THAN ('2026-04-01 00:00:00'),
    PARTITION p2026_q2 VALUES LESS THAN ('2026-07-01 00:00:00'),
    PARTITION p2026_q3 VALUES LESS THAN ('2026-10-01 00:00:00'),
    PARTITION p2026_q4 VALUES LESS THAN ('2027-01-01 00:00:00'),
    PARTITION p_future  VALUES LESS THAN MAXVALUE
);

-- ============================================
-- Table: agent_evaluation
-- Description: Agent评估表
-- ============================================
CREATE TABLE IF NOT EXISTS agent_evaluation (
    id            VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    execution_id  VARCHAR(64)  NOT NULL COMMENT '执行ID',
    correctness   DECIMAL(5,2) COMMENT '正确性',
    completeness  DECIMAL(5,2) COMMENT '完整性',
    safety        DECIMAL(5,2) COMMENT '安全性',
    efficiency    DECIMAL(5,2) COMMENT '效率',
    overall       DECIMAL(5,2) COMMENT '总体',
    feedback      LONGTEXT COMMENT '反馈',
    created_at    DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent评估表';

-- ============================================
-- Table: agent_governance_policy
-- Description: Agent治理策略表
-- ============================================
CREATE TABLE IF NOT EXISTS agent_governance_policy (
    id          VARCHAR(64) PRIMARY KEY COMMENT 'ID',
    agent_id    VARCHAR(64) COMMENT 'Agent ID',
    type        VARCHAR(16) COMMENT '类型',
    rule        JSON COMMENT '规则',
    enabled     TINYINT(1)  DEFAULT 1 COMMENT '是否启用',
    created_at  DATETIME    NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent治理策略表';

-- ============================================
-- Table: agent_approval
-- Description: Agent审批表
-- ============================================
CREATE TABLE IF NOT EXISTS agent_approval (
    id           VARCHAR(64) PRIMARY KEY COMMENT 'ID',
    task_id      VARCHAR(64) COMMENT '任务ID',
    risk_level   VARCHAR(4) COMMENT '风险级别',
    status       VARCHAR(16) DEFAULT 'PENDING' COMMENT '状态',
    requested_at DATETIME COMMENT '请求时间',
    processed_at DATETIME COMMENT '处理时间',
    approved_by  VARCHAR(64) COMMENT '审批人',
    comment      LONGTEXT COMMENT '评论'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent审批表';

-- ============================================
-- Table: sys_agent_profile
-- Description: Agent配置档案表(Hermes)
-- ============================================
CREATE TABLE IF NOT EXISTS sys_agent_profile (
    id                   VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    profile_name         VARCHAR(128) NOT NULL COMMENT '档案名称',
    subsystem            VARCHAR(64) COMMENT '子系统',
    enabled              TINYINT(1)   DEFAULT 1 COMMENT '是否启用',
    description          VARCHAR(512) COMMENT '描述',
    provider             VARCHAR(64) COMMENT '供应商',
    model                VARCHAR(128) COMMENT '模型',
    base_url             VARCHAR(512) COMMENT '基础URL',
    api_key_ref          VARCHAR(256) COMMENT 'API密钥引用',
    temperature          DOUBLE COMMENT '温度',
    max_tokens           INT COMMENT '最大Token数',
    system_prompt        LONGTEXT COMMENT '系统提示',
    max_iterations       INT          DEFAULT 10 COMMENT '最大迭代次数',
    session_timeout_sec  INT          DEFAULT 300 COMMENT '会话超时(秒)',
    tools_enabled        TINYINT(1)   DEFAULT 1 COMMENT '是否启用工具',
    auto_approve         TINYINT(1)   DEFAULT 0 COMMENT '是否自动审批',
    allowed_tools        LONGTEXT COMMENT '允许的工具',
    concurrency          INT          DEFAULT 5 COMMENT '并发数',
    priority             INT          DEFAULT 0 COMMENT '优先级',
    tenant_id            VARCHAR(64) COMMENT '租户ID',
    created_by           VARCHAR(64) COMMENT '创建人',
    created_time         DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_by           VARCHAR(64) COMMENT '更新人',
    updated_time         DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent配置档案表(Hermes)';

-- ============================================
-- Table: sys_agent_call_log
-- Description: Agent调用日志表(Hermes)
-- ============================================
CREATE TABLE IF NOT EXISTS sys_agent_call_log (
    id            VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    subsystem     VARCHAR(64) COMMENT '子系统',
    profile_name  VARCHAR(128) COMMENT '档案名称',
    session_id    VARCHAR(64) COMMENT '会话ID',
    user_message  LONGTEXT COMMENT '用户消息',
    tokens_input  INT          DEFAULT 0 COMMENT '输入Token数',
    tokens_output INT          DEFAULT 0 COMMENT '输出Token数',
    duration_ms   INT          DEFAULT 0 COMMENT '耗时(ms)',
    status        VARCHAR(16)  DEFAULT 'success' COMMENT '状态',
    error_msg     LONGTEXT COMMENT '错误消息',
    tenant_id     VARCHAR(64) COMMENT '租户ID',
    created_time  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent调用日志表(Hermes)';

CREATE INDEX idx_ai_calllog_profile ON sys_agent_call_log(profile_name);
CREATE INDEX idx_ai_calllog_time    ON sys_agent_call_log(created_time DESC);

-- ============================================
-- Table: world_state
-- Description: 世界状态表
-- ============================================
CREATE TABLE IF NOT EXISTS world_state (
    id          VARCHAR(64) PRIMARY KEY COMMENT 'ID',
    timestamp   DATETIME COMMENT '时间戳',
    state_data  JSON COMMENT '状态数据'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='世界状态表';

-- ============================================
-- Table: world_snapshot
-- Description: 世界快照表
-- ============================================
CREATE TABLE IF NOT EXISTS world_snapshot (
    id             VARCHAR(64) PRIMARY KEY COMMENT 'ID',
    state_id       VARCHAR(64) COMMENT '状态ID',
    snapshot_type  VARCHAR(32) COMMENT '快照类型',
    data           JSON COMMENT '数据',
    created_at     DATETIME    NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='世界快照表';

-- ============================================
-- Table: scenario
-- Description: 场景表
-- ============================================
CREATE TABLE IF NOT EXISTS scenario (
    id          VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    name        VARCHAR(255) NOT NULL COMMENT '名称',
    type        VARCHAR(32)  DEFAULT 'CUSTOM' COMMENT '类型',
    assumptions JSON COMMENT '假设',
    description LONGTEXT COMMENT '描述',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场景表';

CREATE INDEX idx_ai_scenario_type ON scenario(type);

-- ============================================
-- Table: simulation
-- Description: 仿真表
-- ============================================
CREATE TABLE IF NOT EXISTS simulation (
    id          VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    scenario_id VARCHAR(64) COMMENT '场景ID',
    status      VARCHAR(32)  DEFAULT 'CREATED' COMMENT '状态',
    config      JSON COMMENT '配置',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='仿真表';

CREATE INDEX idx_ai_sim_status ON simulation(status);
ALTER TABLE simulation
    ADD CONSTRAINT fk_ai_sim_scenario FOREIGN KEY (scenario_id) REFERENCES scenario(id);

-- ============================================
-- Table: simulation_result
-- Description: 仿真结果表
-- ============================================
CREATE TABLE IF NOT EXISTS simulation_result (
    id            VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    simulation_id VARCHAR(64) COMMENT '仿真ID',
    output_state  JSON COMMENT '输出状态',
    predictions   JSON COMMENT '预测',
    confidence    DECIMAL(5,4) DEFAULT 0 COMMENT '置信度',
    summary       LONGTEXT COMMENT '摘要',
    completed_at  DATETIME COMMENT '完成时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='仿真结果表';

ALTER TABLE simulation_result
    ADD CONSTRAINT fk_ai_simres_sim FOREIGN KEY (simulation_id) REFERENCES simulation(id);

-- ============================================
-- Table: forecast
-- Description: 预测表
-- ============================================
CREATE TABLE IF NOT EXISTS forecast (
    id             VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    target_entity  VARCHAR(128) COMMENT '目标实体',
    target_metric  VARCHAR(128) COMMENT '目标指标',
    horizon        VARCHAR(32) COMMENT '时间范围',
    values         JSON COMMENT '值',
    model          VARCHAR(64) COMMENT '模型',
    confidence     DECIMAL(5,4) DEFAULT 0 COMMENT '置信度',
    tenant_id      VARCHAR(64) COMMENT '租户ID',
    created_at     DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预测表';

-- ============================================
-- Table: optimization_job
-- Description: 优化任务表
-- ============================================
CREATE TABLE IF NOT EXISTS optimization_job (
    id          VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    objective   LONGTEXT COMMENT '目标',
    constraints JSON COMMENT '约束',
    status      VARCHAR(32)  DEFAULT 'CREATED' COMMENT '状态',
    result      JSON COMMENT '结果',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优化任务表';

-- ============================================
-- Table: strategy_recommendation
-- Description: 策略推荐表
-- ============================================
CREATE TABLE IF NOT EXISTS strategy_recommendation (
    id              VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    goal            LONGTEXT COMMENT '目标',
    actions         JSON COMMENT '动作',
    expected_impact DECIMAL(5,4) DEFAULT 0 COMMENT '预期影响',
    risk_level      DECIMAL(5,4) DEFAULT 0 COMMENT '风险级别',
    reasoning       LONGTEXT COMMENT '推理',
    tenant_id       VARCHAR(64) COMMENT '租户ID',
    created_at      DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='策略推荐表';

-- ============================================
-- Table: causal_edge
-- Description: 因果边表
-- ============================================
CREATE TABLE IF NOT EXISTS causal_edge (
    id           VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    source_node  VARCHAR(128) COMMENT '源节点',
    target_node  VARCHAR(128) COMMENT '目标节点',
    weight       DECIMAL(5,4) DEFAULT 0.5 COMMENT '权重',
    description  LONGTEXT COMMENT '描述',
    created_at   DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='因果边表';

CREATE INDEX idx_ai_causal_src ON causal_edge(source_node);
CREATE INDEX idx_ai_causal_tgt ON causal_edge(target_node);
