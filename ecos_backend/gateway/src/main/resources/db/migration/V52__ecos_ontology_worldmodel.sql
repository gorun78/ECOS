-- Sprint 4: World Model and Ontology Compiler tables

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
CREATE TABLE IF NOT EXISTS ecos_agent.world_state (
    id VARCHAR(64) PRIMARY KEY,
    timestamp TIMESTAMP DEFAULT NOW(),
    state_data JSONB DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS ecos_agent.world_snapshot (
    id VARCHAR(64) PRIMARY KEY,
    state_id VARCHAR(64),
    snapshot_type VARCHAR(32),
    data JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_agent.scenario (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255),
    type VARCHAR(32) DEFAULT 'CUSTOM',
    assumptions JSONB DEFAULT '{}',
    description TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_agent.simulation (
    id VARCHAR(64) PRIMARY KEY,
    scenario_id VARCHAR(64) REFERENCES ecos_agent.scenario(id),
    status VARCHAR(32) DEFAULT 'CREATED',
    config JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_agent.simulation_result (
    id VARCHAR(64) PRIMARY KEY,
    simulation_id VARCHAR(64) REFERENCES ecos_agent.simulation(id),
    output_state JSONB DEFAULT '{}',
    predictions JSONB DEFAULT '{}',
    confidence DECIMAL(5,4) DEFAULT 0,
    summary TEXT,
    completed_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_agent.forecast (
    id VARCHAR(64) PRIMARY KEY,
    target_entity VARCHAR(128),
    target_metric VARCHAR(128),
    horizon VARCHAR(32),
    values JSONB DEFAULT '[]',
    model VARCHAR(64),
    confidence DECIMAL(5,4) DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_agent.optimization_job (
    id VARCHAR(64) PRIMARY KEY,
    objective TEXT,
    constraints JSONB DEFAULT '[]',
    status VARCHAR(32) DEFAULT 'CREATED',
    result JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_agent.strategy_recommendation (
    id VARCHAR(64) PRIMARY KEY,
    goal TEXT,
    actions JSONB DEFAULT '[]',
    expected_impact DECIMAL(5,4) DEFAULT 0,
    risk_level DECIMAL(5,4) DEFAULT 0,
    reasoning TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_agent.causal_edge (
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
CREATE INDEX IF NOT EXISTS idx_scenario_type ON ecos_agent.scenario(type);
CREATE INDEX IF NOT EXISTS idx_simulation_status ON ecos_agent.simulation(status);
CREATE INDEX IF NOT EXISTS idx_causal_source ON ecos_agent.causal_edge(source_node);
CREATE INDEX IF NOT EXISTS idx_causal_target ON ecos_agent.causal_edge(target_node);
