-- Sprint 3: Agent Runtime tables
-- 【W248/R-22 订正 2026-10-10】ST07 五枚举权威下 Agent 族落 ecos_ai，禁建 ecos_agent schema（X-4 库内本无）；
-- 本文件 ecos_agent schema 限定 15 处逐处订正为 ecos_ai.（同名表实存 ecos_ai，IF NOT EXISTS 语义安全）；本脚本从未实跑
-- （登记见 docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md §一），原语句作废保留可审计。
-- 【W261/R-35 注记 2026-10-10】agent_cost 本文件为**非分区基础表**形态（合规）；库内 25 个分区子表的分区语句
--   出处 = docs/50-db_script/01-ddl-多方言/postgresql/06_ecos_ai.sql:222-250（第二源），禁原生分区，
--   冷存归档经 runtime-monitor；存量 25 子表属库内实况，停写不迁移待裁（定性登记见 DDL迁移脚本落地登记）。
CREATE TABLE IF NOT EXISTS ecos_ai.agent_definition (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32),
    role VARCHAR(128),
    description TEXT,
    capability JSONB DEFAULT '{}',
    config JSONB DEFAULT '{}',
    status VARCHAR(32) DEFAULT 'ACTIVE',
    version INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ai.agent_execution (
    id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64) REFERENCES ecos_ai.agent_definition(id),
    goal TEXT,
    plan JSONB,
    status VARCHAR(32) DEFAULT 'CREATED',
    result JSONB,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ai.agent_execution_step (
    id VARCHAR(64) PRIMARY KEY,
    execution_id VARCHAR(64) REFERENCES ecos_ai.agent_execution(id),
    step_order INTEGER,
    instruction TEXT,
    tool_type VARCHAR(16),
    tool_params JSONB,
    status VARCHAR(32) DEFAULT 'PENDING',
    output TEXT,
    metrics JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ai.agent_memory (
    id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64),
    session_id VARCHAR(64),
    layer VARCHAR(16),
    content TEXT,
    embedding JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ai.agent_cost (
    id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64),
    execution_id VARCHAR(64),
    prompt_tokens INTEGER DEFAULT 0,
    completion_tokens INTEGER DEFAULT 0,
    total_cost DECIMAL(10,4) DEFAULT 0,
    currency VARCHAR(8) DEFAULT 'CNY',
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ai.agent_evaluation (
    id VARCHAR(64) PRIMARY KEY,
    execution_id VARCHAR(64),
    correctness DECIMAL(5,2),
    completeness DECIMAL(5,2),
    safety DECIMAL(5,2),
    efficiency DECIMAL(5,2),
    overall DECIMAL(5,2),
    feedback TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ai.agent_governance_policy (
    id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64),
    type VARCHAR(16),
    rule JSONB DEFAULT '{}',
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ai.agent_approval (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64),
    risk_level VARCHAR(4),
    status VARCHAR(16) DEFAULT 'PENDING',
    requested_at TIMESTAMP DEFAULT NOW(),
    processed_at TIMESTAMP,
    approved_by VARCHAR(64),
    comment TEXT
);

CREATE TABLE IF NOT EXISTS ecos_ai.agent_registry (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(128),
    capability JSONB DEFAULT '{}',
    status VARCHAR(32) DEFAULT 'ACTIVE',
    endpoint VARCHAR(512),
    metadata JSONB DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_mission (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255),
    goal TEXT,
    mode VARCHAR(16) DEFAULT 'SUPERVISOR',
    status VARCHAR(32) DEFAULT 'PENDING',
    plan JSONB,
    result JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_mission_task (
    id VARCHAR(64) PRIMARY KEY,
    mission_id VARCHAR(64) REFERENCES ecos_ai.ecos_mission(id),
    agent_id VARCHAR(64),
    instruction TEXT,
    status VARCHAR(32) DEFAULT 'PENDING',
    result JSONB,
    depends_on VARCHAR(256),
    created_at TIMESTAMP DEFAULT NOW()
);

-- Pre-registered agents
INSERT INTO ecos_ai.agent_registry (id, name, role, capability) VALUES
('ag-coordinator', 'Coordinator Agent', 'supervisor', '{"capabilities": ["task_decomposition", "dispatch", "aggregation"]}'),
('ag-data', 'Data Agent', 'specialist', '{"capabilities": ["object_query", "ontology_explore"]}'),
('ag-knowledge', 'Knowledge Agent', 'specialist', '{"capabilities": ["knowledge_search", "rag"]}'),
('ag-compliance', 'Compliance Agent', 'specialist', '{"capabilities": ["object_query", "knowledge_search", "ml_rating"]}');
