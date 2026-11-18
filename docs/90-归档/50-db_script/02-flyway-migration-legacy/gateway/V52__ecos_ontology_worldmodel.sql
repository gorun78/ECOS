-- Sprint 4: World Model and Ontology Compiler tables
-- 【W248/R-22 订正 2026-10-10】ST07 五枚举权威下 Agent 族落 ecos_ai，禁建 ecos_agent schema（X-4 库内本无）；
-- 本文件原 ecos_agent schema 限定 15 处逐处订正为 ecos_ai（同名表实存 ecos_ai，IF NOT EXISTS 语义安全）；
-- 本脚本从未实跑（登记见 docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md §一），原语句作废保留可审计。

-- Ontology tables
CREATE TABLE IF NOT EXISTS ecos_ontology.entity_definition (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(255),
    description TEXT,
    category VARCHAR(32) DEFAULT 'MASTER',
    properties JSONB DEFAULT '[]',
    lifecycle JSONB DEFAULT '{}',
    version INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ontology.relationship_definition (
    id VARCHAR(64) PRIMARY KEY,
    source_entity VARCHAR(128),
    target_entity VARCHAR(128),
    type VARCHAR(32),
    cardinality VARCHAR(16) DEFAULT 'ONE_TO_MANY',
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ontology.metric_definition (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(255),
    expression TEXT,
    aggregation VARCHAR(16) DEFAULT 'SUM',
    entity_code VARCHAR(128),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ontology.action_definition (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(128),
    name VARCHAR(255),
    type VARCHAR(16) DEFAULT 'WORKFLOW',
    config JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ontology.policy_definition (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(128),
    type VARCHAR(32),
    expression TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ontology.event_definition (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(128),
    source VARCHAR(128),
    payload_schema JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT NOW()
);

-- World Model tables
CREATE TABLE IF NOT EXISTS ecos_ai.world_state (
    id VARCHAR(64) PRIMARY KEY,
    timestamp TIMESTAMP DEFAULT NOW(),
    state_data JSONB DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS ecos_ai.world_snapshot (
    id VARCHAR(64) PRIMARY KEY,
    state_id VARCHAR(64),
    snapshot_type VARCHAR(32),
    data JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ai.scenario (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255),
    type VARCHAR(32) DEFAULT 'CUSTOM',
    assumptions JSONB DEFAULT '{}',
    description TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ai.simulation (
    id VARCHAR(64) PRIMARY KEY,
    scenario_id VARCHAR(64) REFERENCES ecos_ai.scenario(id),
    status VARCHAR(32) DEFAULT 'CREATED',
    config JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ai.simulation_result (
    id VARCHAR(64) PRIMARY KEY,
    simulation_id VARCHAR(64) REFERENCES ecos_ai.simulation(id),
    output_state JSONB DEFAULT '{}',
    predictions JSONB DEFAULT '{}',
    confidence DECIMAL(5,4) DEFAULT 0,
    summary TEXT,
    completed_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ai.forecast (
    id VARCHAR(64) PRIMARY KEY,
    target_entity VARCHAR(128),
    target_metric VARCHAR(128),
    horizon VARCHAR(32),
    values JSONB DEFAULT '[]',
    model VARCHAR(64),
    confidence DECIMAL(5,4) DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ai.optimization_job (
    id VARCHAR(64) PRIMARY KEY,
    objective TEXT,
    constraints JSONB DEFAULT '[]',
    status VARCHAR(32) DEFAULT 'CREATED',
    result JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ai.strategy_recommendation (
    id VARCHAR(64) PRIMARY KEY,
    goal TEXT,
    actions JSONB DEFAULT '[]',
    expected_impact DECIMAL(5,4) DEFAULT 0,
    risk_level DECIMAL(5,4) DEFAULT 0,
    reasoning TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ai.causal_edge (
    id VARCHAR(64) PRIMARY KEY,
    source_node VARCHAR(128),
    target_node VARCHAR(128),
    weight DECIMAL(5,4) DEFAULT 0.5,
    description TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Ontology indexes
CREATE INDEX IF NOT EXISTS idx_entity_def_code ON ecos_ontology.entity_definition(code);
CREATE INDEX IF NOT EXISTS idx_rel_def_source ON ecos_ontology.relationship_definition(source_entity);
CREATE INDEX IF NOT EXISTS idx_rel_def_target ON ecos_ontology.relationship_definition(target_entity);
CREATE INDEX IF NOT EXISTS idx_metric_def_code ON ecos_ontology.metric_definition(code);

-- World Model indexes
CREATE INDEX IF NOT EXISTS idx_scenario_type ON ecos_ai.scenario(type);
CREATE INDEX IF NOT EXISTS idx_simulation_status ON ecos_ai.simulation(status);
CREATE INDEX IF NOT EXISTS idx_causal_source ON ecos_ai.causal_edge(source_node);
CREATE INDEX IF NOT EXISTS idx_causal_target ON ecos_ai.causal_edge(target_node);
