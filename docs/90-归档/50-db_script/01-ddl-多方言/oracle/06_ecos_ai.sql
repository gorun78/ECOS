-- ============================================================================
-- ECOS AI Domain — Oracle DDL
-- Schema: ecos_ai
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

BEGIN
   EXECUTE IMMEDIATE 'CREATE USER ecos_ai IDENTIFIED BY ecos_ai DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE INDEX TO ecos_ai;

-- ============================================
-- Table: ecos_agent
-- Description: Agent配置表(旧版)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.ecos_agent (
    id             VARCHAR2(64)  PRIMARY KEY,
    name           VARCHAR2(255) NOT NULL,
    model_provider VARCHAR2(64)  DEFAULT ''deepseek'',
    model_name     VARCHAR2(128) DEFAULT ''deepseek-v4-flash'',
    system_prompt  CLOB,
    tools          CLOB          DEFAULT ''[]'',
    knowledge      CLOB          DEFAULT ''[]'',
    status         VARCHAR2(32)  DEFAULT ''draft'',
    tenant_id      VARCHAR2(64),
    created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at     TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.ecos_agent IS 'Agent配置表(旧版)';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_ai_agent_status ON ecos_ai.ecos_agent(status)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_agent_registry
-- Description: Agent注册表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.ecos_agent_registry (
    id          VARCHAR2(64)  PRIMARY KEY,
    name        VARCHAR2(255) NOT NULL,
    role        VARCHAR2(128),
    capability  CLOB          DEFAULT ''{}'',
    status      VARCHAR2(32)  DEFAULT ''ACTIVE'',
    endpoint    VARCHAR2(512),
    metadata    CLOB          DEFAULT ''{}'',
    tenant_id   VARCHAR2(64),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.ecos_agent_registry IS 'Agent注册表';

-- ============================================
-- Table: ecos_mission
-- Description: Mission表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.ecos_mission (
    id          VARCHAR2(64)  PRIMARY KEY,
    title       VARCHAR2(256) NOT NULL,
    goal        CLOB,
    mode        VARCHAR2(16)  DEFAULT ''SUPERVISOR'',
    status      VARCHAR2(32)  DEFAULT ''PENDING'',
    plan        CLOB,
    result      CLOB,
    tenant_id   VARCHAR2(64),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.ecos_mission IS 'Mission表';

-- ============================================
-- Table: ecos_mission_task
-- Description: Mission任务表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.ecos_mission_task (
    id          VARCHAR2(64)  PRIMARY KEY,
    mission_id  VARCHAR2(64)  NOT NULL,
    agent_id    VARCHAR2(64),
    instruction CLOB,
    status      VARCHAR2(32)  DEFAULT ''PENDING'',
    result      CLOB,
    depends_on  VARCHAR2(256),
    tenant_id   VARCHAR2(64),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.ecos_mission_task IS 'Mission任务表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_ai_mtask_mission ON ecos_ai.ecos_mission_task(mission_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_ai.ecos_mission_task ADD CONSTRAINT fk_ai_mtask_mission FOREIGN KEY (mission_id) REFERENCES ecos_ai.ecos_mission(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_tool_definition
-- Description: 工具定义表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.ecos_tool_definition (
    id           VARCHAR2(64)  PRIMARY KEY,
    code         VARCHAR2(128) NOT NULL,
    name         VARCHAR2(255) NOT NULL,
    description  CLOB,
    tool_type    VARCHAR2(32),
    endpoint_url VARCHAR2(512),
    http_method  VARCHAR2(16),
    schema_json  CLOB,
    status       VARCHAR2(32)  DEFAULT ''ACTIVE'',
    tenant_id    VARCHAR2(64),
    created_at   TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at   TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.ecos_tool_definition IS '工具定义表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX idx_ai_tool_code ON ecos_ai.ecos_tool_definition(code)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Sequence for ecos_decision_case
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'CREATE SEQUENCE ecos_ai.seq_decision_case START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_decision_case
-- Description: 决策案例表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.ecos_decision_case (
    id          NUMBER(19)     PRIMARY KEY,
    title       VARCHAR2(256)  NOT NULL,
    scenario    CLOB,
    tags        CLOB,
    decision    CLOB           DEFAULT ''{}'',
    result      CLOB           DEFAULT ''{}'',
    feedback    VARCHAR2(32)   DEFAULT ''pending'',
    source      VARCHAR2(64),
    created_by  VARCHAR2(128),
    tenant_id   VARCHAR2(64),
    created_at  TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.ecos_decision_case IS '决策案例表';

CREATE OR REPLACE TRIGGER ecos_ai.trg_decision_case_id
BEFORE INSERT ON ecos_ai.ecos_decision_case
FOR EACH ROW
BEGIN
   IF :NEW.id IS NULL THEN
      :NEW.id := ecos_ai.seq_decision_case.NEXTVAL;
   END IF;
END;
/

-- ============================================
-- Table: agent_definition
-- Description: Agent定义表(V50)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.agent_definition (
    id          VARCHAR2(64)  PRIMARY KEY,
    name        VARCHAR2(255) NOT NULL,
    type        VARCHAR2(32),
    role        VARCHAR2(128),
    description CLOB,
    capability  CLOB          DEFAULT ''{}'',
    config      CLOB          DEFAULT ''{}'',
    status      VARCHAR2(32)  DEFAULT ''ACTIVE'',
    version     NUMBER(10)    DEFAULT 1,
    tenant_id   VARCHAR2(64),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.agent_definition IS 'Agent定义表(V50)';

-- ============================================
-- Table: agent_execution
-- Description: Agent执行表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.agent_execution (
    id            VARCHAR2(64)  PRIMARY KEY,
    agent_id      VARCHAR2(64)  NOT NULL,
    goal          CLOB,
    plan          CLOB,
    status        VARCHAR2(32)  DEFAULT ''CREATED'',
    result        CLOB,
    started_at    TIMESTAMP,
    completed_at  TIMESTAMP,
    tenant_id     VARCHAR2(64),
    created_at    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.agent_execution IS 'Agent执行表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_ai_exec_agent ON ecos_ai.agent_execution(agent_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_ai_exec_status ON ecos_ai.agent_execution(status)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_ai.agent_execution ADD CONSTRAINT fk_ai_exec_agent FOREIGN KEY (agent_id) REFERENCES ecos_ai.agent_definition(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: agent_execution_step
-- Description: Agent执行步骤表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.agent_execution_step (
    id            VARCHAR2(64)  PRIMARY KEY,
    execution_id  VARCHAR2(64)  NOT NULL,
    step_order    NUMBER(10),
    instruction   CLOB,
    tool_type     VARCHAR2(16),
    tool_params   CLOB,
    status        VARCHAR2(32)  DEFAULT ''PENDING'',
    output        CLOB,
    metrics       CLOB,
    created_at    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.agent_execution_step IS 'Agent执行步骤表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_ai_step_exec ON ecos_ai.agent_execution_step(execution_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_ai.agent_execution_step ADD CONSTRAINT fk_ai_step_exec FOREIGN KEY (execution_id) REFERENCES ecos_ai.agent_execution(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: agent_memory
-- Description: Agent记忆表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.agent_memory (
    id          VARCHAR2(64)  PRIMARY KEY,
    agent_id    VARCHAR2(64),
    session_id  VARCHAR2(64),
    layer       VARCHAR2(16),
    content     CLOB,
    embedding   CLOB,
    tenant_id   VARCHAR2(64),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.agent_memory IS 'Agent记忆表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_ai_mem_agent ON ecos_ai.agent_memory(agent_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_ai_mem_session ON ecos_ai.agent_memory(session_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: agent_cost
-- Description: Agent成本表(按月分区)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.agent_cost (
    id                VARCHAR2(64)   PRIMARY KEY,
    agent_id          VARCHAR2(64),
    execution_id      VARCHAR2(64),
    prompt_tokens     NUMBER(10)     DEFAULT 0,
    completion_tokens NUMBER(10)     DEFAULT 0,
    total_cost        NUMBER(10,4)   DEFAULT 0,
    currency          VARCHAR2(8)    DEFAULT ''CNY'',
    tenant_id         VARCHAR2(64),
    created_at        TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
)
PARTITION BY RANGE (created_at)
(
    PARTITION agent_cost_2025_01 VALUES LESS THAN (TO_DATE(''2025-02-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2025_02 VALUES LESS THAN (TO_DATE(''2025-03-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2025_03 VALUES LESS THAN (TO_DATE(''2025-04-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2025_04 VALUES LESS THAN (TO_DATE(''2025-05-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2025_05 VALUES LESS THAN (TO_DATE(''2025-06-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2025_06 VALUES LESS THAN (TO_DATE(''2025-07-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2025_07 VALUES LESS THAN (TO_DATE(''2025-08-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2025_08 VALUES LESS THAN (TO_DATE(''2025-09-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2025_09 VALUES LESS THAN (TO_DATE(''2025-10-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2025_10 VALUES LESS THAN (TO_DATE(''2025-11-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2025_11 VALUES LESS THAN (TO_DATE(''2025-12-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2025_12 VALUES LESS THAN (TO_DATE(''2026-01-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2026_01 VALUES LESS THAN (TO_DATE(''2026-02-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2026_02 VALUES LESS THAN (TO_DATE(''2026-03-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2026_03 VALUES LESS THAN (TO_DATE(''2026-04-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2026_04 VALUES LESS THAN (TO_DATE(''2026-05-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2026_05 VALUES LESS THAN (TO_DATE(''2026-06-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2026_06 VALUES LESS THAN (TO_DATE(''2026-07-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2026_07 VALUES LESS THAN (TO_DATE(''2026-08-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2026_08 VALUES LESS THAN (TO_DATE(''2026-09-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2026_09 VALUES LESS THAN (TO_DATE(''2026-10-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2026_10 VALUES LESS THAN (TO_DATE(''2026-11-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2026_11 VALUES LESS THAN (TO_DATE(''2026-12-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_2026_12 VALUES LESS THAN (TO_DATE(''2027-01-01'', ''YYYY-MM-DD'')),
    PARTITION agent_cost_default VALUES LESS THAN (MAXVALUE)
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.agent_cost IS 'Agent成本表(按月分区)';

-- ============================================
-- Table: agent_evaluation
-- Description: Agent评估表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.agent_evaluation (
    id            VARCHAR2(64)  PRIMARY KEY,
    execution_id  VARCHAR2(64)  NOT NULL,
    correctness   NUMBER(5,2),
    completeness  NUMBER(5,2),
    safety        NUMBER(5,2),
    efficiency    NUMBER(5,2),
    overall       NUMBER(5,2),
    feedback      CLOB,
    created_at    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.agent_evaluation IS 'Agent评估表';

-- ============================================
-- Table: agent_governance_policy
-- Description: Agent治理策略表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.agent_governance_policy (
    id          VARCHAR2(64) PRIMARY KEY,
    agent_id    VARCHAR2(64),
    type        VARCHAR2(16),
    rule        CLOB         DEFAULT ''{}'',
    enabled     NUMBER(1)    DEFAULT 1,
    created_at  TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.agent_governance_policy IS 'Agent治理策略表';

-- ============================================
-- Table: agent_approval
-- Description: Agent审批表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.agent_approval (
    id           VARCHAR2(64) PRIMARY KEY,
    task_id      VARCHAR2(64),
    risk_level   VARCHAR2(4),
    status       VARCHAR2(16) DEFAULT ''PENDING'',
    requested_at TIMESTAMP,
    processed_at TIMESTAMP,
    approved_by  VARCHAR2(64),
    comment      CLOB
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.agent_approval IS 'Agent审批表';

-- ============================================
-- Table: sys_agent_profile
-- Description: Agent配置档案表(Hermes)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.sys_agent_profile (
    id                   VARCHAR2(64)  PRIMARY KEY,
    profile_name         VARCHAR2(128) NOT NULL,
    subsystem            VARCHAR2(64),
    enabled              NUMBER(1)     DEFAULT 1,
    description          VARCHAR2(512),
    provider             VARCHAR2(64),
    model                VARCHAR2(128),
    base_url             VARCHAR2(512),
    api_key_ref          VARCHAR2(256),
    temperature          BINARY_DOUBLE,
    max_tokens           NUMBER(10),
    system_prompt        CLOB,
    max_iterations       NUMBER(10)    DEFAULT 10,
    session_timeout_sec  NUMBER(10)    DEFAULT 300,
    tools_enabled        NUMBER(1)     DEFAULT 1,
    auto_approve         NUMBER(1)     DEFAULT 0,
    allowed_tools        CLOB,
    concurrency          NUMBER(10)    DEFAULT 5,
    priority             NUMBER(10)    DEFAULT 0,
    tenant_id            VARCHAR2(64),
    created_by           VARCHAR2(64),
    created_time         TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by           VARCHAR2(64),
    updated_time         TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.sys_agent_profile IS 'Agent配置档案表(Hermes)';

-- ============================================
-- Table: sys_agent_call_log
-- Description: Agent调用日志表(Hermes)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.sys_agent_call_log (
    id            VARCHAR2(64)  PRIMARY KEY,
    subsystem     VARCHAR2(64),
    profile_name  VARCHAR2(128),
    session_id    VARCHAR2(64),
    user_message  CLOB,
    tokens_input  NUMBER(10)    DEFAULT 0,
    tokens_output NUMBER(10)    DEFAULT 0,
    duration_ms   NUMBER(10)    DEFAULT 0,
    status        VARCHAR2(16)  DEFAULT ''success'',
    error_msg     CLOB,
    tenant_id     VARCHAR2(64),
    created_time  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.sys_agent_call_log IS 'Agent调用日志表(Hermes)';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_ai_calllog_profile ON ecos_ai.sys_agent_call_log(profile_name)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_ai_calllog_time ON ecos_ai.sys_agent_call_log(created_time DESC)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: world_state
-- Description: 世界状态表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.world_state (
    id          VARCHAR2(64) PRIMARY KEY,
    timestamp   TIMESTAMP,
    state_data  CLOB         DEFAULT ''{}''
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.world_state IS '世界状态表';

-- ============================================
-- Table: world_snapshot
-- Description: 世界快照表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.world_snapshot (
    id             VARCHAR2(64) PRIMARY KEY,
    state_id       VARCHAR2(64),
    snapshot_type  VARCHAR2(32),
    data           CLOB         DEFAULT ''{}'',
    created_at     TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.world_snapshot IS '世界快照表';

-- ============================================
-- Table: scenario
-- Description: 场景表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.scenario (
    id          VARCHAR2(64)  PRIMARY KEY,
    name        VARCHAR2(255) NOT NULL,
    type        VARCHAR2(32)  DEFAULT ''CUSTOM'',
    assumptions CLOB          DEFAULT ''{}'',
    description CLOB,
    tenant_id   VARCHAR2(64),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.scenario IS '场景表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_ai_scenario_type ON ecos_ai.scenario(type)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: simulation
-- Description: 仿真表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.simulation (
    id          VARCHAR2(64)  PRIMARY KEY,
    scenario_id VARCHAR2(64),
    status      VARCHAR2(32)  DEFAULT ''CREATED'',
    config      CLOB          DEFAULT ''{}'',
    tenant_id   VARCHAR2(64),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.simulation IS '仿真表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_ai_sim_status ON ecos_ai.simulation(status)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_ai.simulation ADD CONSTRAINT fk_ai_sim_scenario FOREIGN KEY (scenario_id) REFERENCES ecos_ai.scenario(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: simulation_result
-- Description: 仿真结果表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.simulation_result (
    id            VARCHAR2(64)  PRIMARY KEY,
    simulation_id VARCHAR2(64),
    output_state  CLOB          DEFAULT ''{}'',
    predictions   CLOB          DEFAULT ''{}'',
    confidence    NUMBER(5,4)   DEFAULT 0,
    summary       CLOB,
    completed_at  TIMESTAMP
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.simulation_result IS '仿真结果表';

BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_ai.simulation_result ADD CONSTRAINT fk_ai_simres_sim FOREIGN KEY (simulation_id) REFERENCES ecos_ai.simulation(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: forecast
-- Description: 预测表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.forecast (
    id             VARCHAR2(64)  PRIMARY KEY,
    target_entity  VARCHAR2(128),
    target_metric  VARCHAR2(128),
    horizon        VARCHAR2(32),
    values         CLOB          DEFAULT ''[]'',
    model          VARCHAR2(64),
    confidence     NUMBER(5,4)   DEFAULT 0,
    tenant_id      VARCHAR2(64),
    created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.forecast IS '预测表';

-- ============================================
-- Table: optimization_job
-- Description: 优化任务表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.optimization_job (
    id          VARCHAR2(64)  PRIMARY KEY,
    objective   CLOB,
    constraints CLOB          DEFAULT ''[]'',
    status      VARCHAR2(32)  DEFAULT ''CREATED'',
    result      CLOB          DEFAULT ''{}'',
    tenant_id   VARCHAR2(64),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.optimization_job IS '优化任务表';

-- ============================================
-- Table: strategy_recommendation
-- Description: 策略推荐表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.strategy_recommendation (
    id              VARCHAR2(64)  PRIMARY KEY,
    goal            CLOB,
    actions         CLOB          DEFAULT ''[]'',
    expected_impact NUMBER(5,4)   DEFAULT 0,
    risk_level      NUMBER(5,4)   DEFAULT 0,
    reasoning       CLOB,
    tenant_id       VARCHAR2(64),
    created_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.strategy_recommendation IS '策略推荐表';

-- ============================================
-- Table: causal_edge
-- Description: 因果边表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_ai.causal_edge (
    id           VARCHAR2(64)  PRIMARY KEY,
    source_node  VARCHAR2(128),
    target_node  VARCHAR2(128),
    weight       NUMBER(5,4)   DEFAULT 0.5,
    description  CLOB,
    created_at   TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_ai.causal_edge IS '因果边表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_ai_causal_src ON ecos_ai.causal_edge(source_node)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_ai_causal_tgt ON ecos_ai.causal_edge(target_node)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
