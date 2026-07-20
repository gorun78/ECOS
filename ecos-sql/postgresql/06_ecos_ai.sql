-- ============================================================================
-- ECOS AI Domain — PostgreSQL DDL
-- Schema: ecos_ai
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS ecos_ai;

-- ============================================
-- Table: ecos_agent
-- Description: Agent配置表(旧版)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.ecos_agent (
    id             VARCHAR(64)  PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    model_provider VARCHAR(64)  DEFAULT 'deepseek',
    model_name     VARCHAR(128) DEFAULT 'deepseek-v4-flash',
    system_prompt  TEXT,
    tools          TEXT         DEFAULT '[]',
    knowledge      TEXT         DEFAULT '[]',
    status         VARCHAR(32)  DEFAULT 'draft',
    tenant_id      VARCHAR(64),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.ecos_agent IS 'Agent配置表(旧版)';
CREATE INDEX IF NOT EXISTS idx_ai_agent_status ON ecos_ai.ecos_agent(status);

-- ============================================
-- Table: ecos_agent_registry
-- Description: Agent注册表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.ecos_agent_registry (
    id          VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    role        VARCHAR(128),
    capability  JSONB        DEFAULT '{}',
    status      VARCHAR(32)  DEFAULT 'ACTIVE',
    endpoint    VARCHAR(512),
    metadata    JSONB        DEFAULT '{}',
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.ecos_agent_registry IS 'Agent注册表';

-- ============================================
-- Table: ecos_mission
-- Description: Mission表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.ecos_mission (
    id          VARCHAR(64)  PRIMARY KEY,
    title       VARCHAR(256) NOT NULL,
    goal        TEXT,
    mode        VARCHAR(16)  DEFAULT 'SUPERVISOR',
    status      VARCHAR(32)  DEFAULT 'PENDING',
    plan        JSONB,
    result      JSONB,
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.ecos_mission IS 'Mission表';

-- ============================================
-- Table: ecos_mission_task
-- Description: Mission任务表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.ecos_mission_task (
    id          VARCHAR(64)  PRIMARY KEY,
    mission_id  VARCHAR(64)  NOT NULL,
    agent_id    VARCHAR(64),
    instruction TEXT,
    status      VARCHAR(32)  DEFAULT 'PENDING',
    result      JSONB,
    depends_on  VARCHAR(256),
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.ecos_mission_task IS 'Mission任务表';
CREATE INDEX IF NOT EXISTS idx_ai_mtask_mission ON ecos_ai.ecos_mission_task(mission_id);
ALTER TABLE ecos_ai.ecos_mission_task
    ADD CONSTRAINT fk_ai_mtask_mission FOREIGN KEY (mission_id) REFERENCES ecos_ai.ecos_mission(id);

-- ============================================
-- Table: ecos_tool_definition
-- Description: 工具定义表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.ecos_tool_definition (
    id           VARCHAR(64)  PRIMARY KEY,
    code         VARCHAR(128) NOT NULL,
    name         VARCHAR(255) NOT NULL,
    description  TEXT,
    tool_type    VARCHAR(32),
    endpoint_url VARCHAR(512),
    http_method  VARCHAR(16),
    schema_json  JSONB,
    status       VARCHAR(32)  DEFAULT 'ACTIVE',
    tenant_id    VARCHAR(64),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.ecos_tool_definition IS '工具定义表';
CREATE UNIQUE INDEX IF NOT EXISTS idx_ai_tool_code ON ecos_ai.ecos_tool_definition(code);

-- ============================================
-- Table: ecos_decision_case
-- Description: 决策案例表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.ecos_decision_case (
    id          BIGSERIAL     PRIMARY KEY,
    title       VARCHAR(256)  NOT NULL,
    scenario    TEXT,
    tags        TEXT[],
    decision    JSONB         DEFAULT '{}',
    result      JSONB         DEFAULT '{}',
    feedback    VARCHAR(32)   DEFAULT 'pending',
    source      VARCHAR(64),
    created_by  VARCHAR(128),
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.ecos_decision_case IS '决策案例表';
CREATE INDEX IF NOT EXISTS idx_ai_dcase_tags ON ecos_ai.ecos_decision_case USING GIN(tags);

-- ============================================
-- Table: agent_definition
-- Description: Agent定义表(V50)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.agent_definition (
    id          VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    type        VARCHAR(32),
    role        VARCHAR(128),
    description TEXT,
    capability  JSONB        DEFAULT '{}',
    config      JSONB        DEFAULT '{}',
    status      VARCHAR(32)  DEFAULT 'ACTIVE',
    version     INTEGER      DEFAULT 1,
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.agent_definition IS 'Agent定义表(V50)';

-- ============================================
-- Table: agent_execution
-- Description: Agent执行表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.agent_execution (
    id            VARCHAR(64)  PRIMARY KEY,
    agent_id      VARCHAR(64)  NOT NULL,
    goal          TEXT,
    plan          JSONB,
    status        VARCHAR(32)  DEFAULT 'CREATED',
    result        JSONB,
    started_at    TIMESTAMP,
    completed_at  TIMESTAMP,
    tenant_id     VARCHAR(64),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.agent_execution IS 'Agent执行表';
CREATE INDEX IF NOT EXISTS idx_ai_exec_agent  ON ecos_ai.agent_execution(agent_id);
CREATE INDEX IF NOT EXISTS idx_ai_exec_status ON ecos_ai.agent_execution(status);
ALTER TABLE ecos_ai.agent_execution
    ADD CONSTRAINT fk_ai_exec_agent FOREIGN KEY (agent_id) REFERENCES ecos_ai.agent_definition(id);

-- ============================================
-- Table: agent_execution_step
-- Description: Agent执行步骤表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.agent_execution_step (
    id            VARCHAR(64)  PRIMARY KEY,
    execution_id  VARCHAR(64)  NOT NULL,
    step_order    INTEGER,
    instruction   TEXT,
    tool_type     VARCHAR(16),
    tool_params   JSONB,
    status        VARCHAR(32)  DEFAULT 'PENDING',
    output        TEXT,
    metrics       JSONB,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.agent_execution_step IS 'Agent执行步骤表';
CREATE INDEX IF NOT EXISTS idx_ai_step_exec ON ecos_ai.agent_execution_step(execution_id);
ALTER TABLE ecos_ai.agent_execution_step
    ADD CONSTRAINT fk_ai_step_exec FOREIGN KEY (execution_id) REFERENCES ecos_ai.agent_execution(id);

-- ============================================
-- Table: agent_memory
-- Description: Agent记忆表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.agent_memory (
    id          VARCHAR(64)  PRIMARY KEY,
    agent_id    VARCHAR(64),
    session_id  VARCHAR(64),
    layer       VARCHAR(16),
    content     TEXT,
    embedding   JSONB,
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.agent_memory IS 'Agent记忆表';
CREATE INDEX IF NOT EXISTS idx_ai_mem_agent  ON ecos_ai.agent_memory(agent_id);
CREATE INDEX IF NOT EXISTS idx_ai_mem_session ON ecos_ai.agent_memory(session_id);

-- ============================================
-- Table: agent_cost
-- Description: Agent成本表(按月分区)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.agent_cost (
    id                VARCHAR(64)   NOT NULL,
    agent_id          VARCHAR(64),
    execution_id      VARCHAR(64),
    prompt_tokens     INTEGER       DEFAULT 0,
    completion_tokens INTEGER       DEFAULT 0,
    total_cost        DECIMAL(10,4) DEFAULT 0,
    currency          VARCHAR(8)    DEFAULT 'CNY',
    tenant_id         VARCHAR(64),
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE  ecos_ai.agent_cost IS 'Agent成本表(按月分区)';

DO $$
BEGIN
    FOR y IN 2025..2026 LOOP
        FOR m IN 1..12 LOOP
            EXECUTE format(
                'CREATE TABLE IF NOT EXISTS ecos_ai.agent_cost_%s_%s PARTITION OF ecos_ai.agent_cost FOR VALUES FROM (%L) TO (%L)',
                y, lpad(m::text, 2, '0'),
                make_date(y, m, 1),
                make_date(y, m, 1) + interval '1 month'
            );
        END LOOP;
    END LOOP;
    EXECUTE 'CREATE TABLE IF NOT EXISTS ecos_ai.agent_cost_default PARTITION OF ecos_ai.agent_cost DEFAULT';
END $$;

-- ============================================
-- Table: agent_evaluation
-- Description: Agent评估表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.agent_evaluation (
    id            VARCHAR(64)  PRIMARY KEY,
    execution_id  VARCHAR(64)  NOT NULL,
    correctness   DECIMAL(5,2),
    completeness  DECIMAL(5,2),
    safety        DECIMAL(5,2),
    efficiency    DECIMAL(5,2),
    overall       DECIMAL(5,2),
    feedback      TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.agent_evaluation IS 'Agent评估表';

-- ============================================
-- Table: agent_governance_policy
-- Description: Agent治理策略表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.agent_governance_policy (
    id          VARCHAR(64) PRIMARY KEY,
    agent_id    VARCHAR(64),
    type        VARCHAR(16),
    rule        JSONB       DEFAULT '{}',
    enabled     BOOLEAN     DEFAULT TRUE,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.agent_governance_policy IS 'Agent治理策略表';

-- ============================================
-- Table: agent_approval
-- Description: Agent审批表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.agent_approval (
    id           VARCHAR(64) PRIMARY KEY,
    task_id      VARCHAR(64),
    risk_level   VARCHAR(4),
    status       VARCHAR(16) DEFAULT 'PENDING',
    requested_at TIMESTAMP,
    processed_at TIMESTAMP,
    approved_by  VARCHAR(64),
    comment      TEXT
);

COMMENT ON TABLE  ecos_ai.agent_approval IS 'Agent审批表';

-- ============================================
-- Table: sys_agent_profile
-- Description: Agent配置档案表(Hermes)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.sys_agent_profile (
    id                   VARCHAR(64)  PRIMARY KEY,
    profile_name         VARCHAR(128) NOT NULL,
    subsystem            VARCHAR(64),
    enabled              BOOLEAN      DEFAULT TRUE,
    description          VARCHAR(512),
    provider             VARCHAR(64),
    model                VARCHAR(128),
    base_url             VARCHAR(512),
    api_key_ref          VARCHAR(256),
    temperature          DOUBLE PRECISION,
    max_tokens           INT,
    system_prompt        TEXT,
    max_iterations       INT          DEFAULT 10,
    session_timeout_sec  INT          DEFAULT 300,
    tools_enabled        BOOLEAN      DEFAULT TRUE,
    auto_approve         BOOLEAN      DEFAULT FALSE,
    allowed_tools        TEXT,
    concurrency          INT          DEFAULT 5,
    priority             INT          DEFAULT 0,
    tenant_id            VARCHAR(64),
    created_by           VARCHAR(64),
    created_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by           VARCHAR(64),
    updated_time         TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.sys_agent_profile IS 'Agent配置档案表(Hermes)';

-- ============================================
-- Table: sys_agent_call_log
-- Description: Agent调用日志表(Hermes)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.sys_agent_call_log (
    id            VARCHAR(64)  PRIMARY KEY,
    subsystem     VARCHAR(64),
    profile_name  VARCHAR(128),
    session_id    VARCHAR(64),
    user_message  TEXT,
    tokens_input  INT          DEFAULT 0,
    tokens_output INT          DEFAULT 0,
    duration_ms   INT          DEFAULT 0,
    status        VARCHAR(16)  DEFAULT 'success',
    error_msg     TEXT,
    tenant_id     VARCHAR(64),
    created_time  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.sys_agent_call_log IS 'Agent调用日志表(Hermes)';
CREATE INDEX IF NOT EXISTS idx_ai_calllog_profile ON ecos_ai.sys_agent_call_log(profile_name);
CREATE INDEX IF NOT EXISTS idx_ai_calllog_time    ON ecos_ai.sys_agent_call_log(created_time DESC);

-- ============================================
-- Table: world_state
-- Description: 世界状态表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.world_state (
    id          VARCHAR(64) PRIMARY KEY,
    timestamp   TIMESTAMP,
    state_data  JSONB       DEFAULT '{}'
);

COMMENT ON TABLE  ecos_ai.world_state IS '世界状态表';

-- ============================================
-- Table: world_snapshot
-- Description: 世界快照表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.world_snapshot (
    id             VARCHAR(64) PRIMARY KEY,
    state_id       VARCHAR(64),
    snapshot_type  VARCHAR(32),
    data           JSONB       DEFAULT '{}',
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.world_snapshot IS '世界快照表';

-- ============================================
-- Table: scenario
-- Description: 场景表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.scenario (
    id          VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    type        VARCHAR(32)  DEFAULT 'CUSTOM',
    assumptions JSONB        DEFAULT '{}',
    description TEXT,
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.scenario IS '场景表';
CREATE INDEX IF NOT EXISTS idx_ai_scenario_type ON ecos_ai.scenario(type);

-- ============================================
-- Table: simulation
-- Description: 仿真表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.simulation (
    id          VARCHAR(64)  PRIMARY KEY,
    scenario_id VARCHAR(64),
    status      VARCHAR(32)  DEFAULT 'CREATED',
    config      JSONB        DEFAULT '{}',
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.simulation IS '仿真表';
CREATE INDEX IF NOT EXISTS idx_ai_sim_status ON ecos_ai.simulation(status);
ALTER TABLE ecos_ai.simulation
    ADD CONSTRAINT fk_ai_sim_scenario FOREIGN KEY (scenario_id) REFERENCES ecos_ai.scenario(id);

-- ============================================
-- Table: simulation_result
-- Description: 仿真结果表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.simulation_result (
    id            VARCHAR(64)  PRIMARY KEY,
    simulation_id VARCHAR(64),
    output_state  JSONB        DEFAULT '{}',
    predictions   JSONB        DEFAULT '{}',
    confidence    DECIMAL(5,4) DEFAULT 0,
    summary       TEXT,
    completed_at  TIMESTAMP
);

COMMENT ON TABLE  ecos_ai.simulation_result IS '仿真结果表';
ALTER TABLE ecos_ai.simulation_result
    ADD CONSTRAINT fk_ai_simres_sim FOREIGN KEY (simulation_id) REFERENCES ecos_ai.simulation(id);

-- ============================================
-- Table: forecast
-- Description: 预测表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.forecast (
    id             VARCHAR(64)  PRIMARY KEY,
    target_entity  VARCHAR(128),
    target_metric  VARCHAR(128),
    horizon        VARCHAR(32),
    values         JSONB        DEFAULT '[]',
    model          VARCHAR(64),
    confidence     DECIMAL(5,4) DEFAULT 0,
    tenant_id      VARCHAR(64),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.forecast IS '预测表';

-- ============================================
-- Table: optimization_job
-- Description: 优化任务表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.optimization_job (
    id          VARCHAR(64)  PRIMARY KEY,
    objective   TEXT,
    constraints JSONB        DEFAULT '[]',
    status      VARCHAR(32)  DEFAULT 'CREATED',
    result      JSONB        DEFAULT '{}',
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.optimization_job IS '优化任务表';

-- ============================================
-- Table: strategy_recommendation
-- Description: 策略推荐表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.strategy_recommendation (
    id              VARCHAR(64)  PRIMARY KEY,
    goal            TEXT,
    actions         JSONB        DEFAULT '[]',
    expected_impact DECIMAL(5,4) DEFAULT 0,
    risk_level      DECIMAL(5,4) DEFAULT 0,
    reasoning       TEXT,
    tenant_id       VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.strategy_recommendation IS '策略推荐表';

-- ============================================
-- Table: causal_edge
-- Description: 因果边表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_ai.causal_edge (
    id           VARCHAR(64)  PRIMARY KEY,
    source_node  VARCHAR(128),
    target_node  VARCHAR(128),
    weight       DECIMAL(5,4) DEFAULT 0.5,
    description  TEXT,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_ai.causal_edge IS '因果边表';
CREATE INDEX IF NOT EXISTS idx_ai_causal_src ON ecos_ai.causal_edge(source_node);
CREATE INDEX IF NOT EXISTS idx_ai_causal_tgt ON ecos_ai.causal_edge(target_node);
