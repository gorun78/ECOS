-- PG版 Agent Mesh 建表 + 种子数据

CREATE TABLE IF NOT EXISTS ecos_agent_registry (
    id          VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    role        VARCHAR(64)  NOT NULL,
    capability  JSONB        DEFAULT '{}',
    status      VARCHAR(32)  DEFAULT 'ACTIVE',
    endpoint    VARCHAR(512),
    metadata    JSONB        DEFAULT '{}',
    created_at  TIMESTAMP    DEFAULT NOW(),
    updated_at  TIMESTAMP    DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_mission (
    id          VARCHAR(64)  PRIMARY KEY,
    title       VARCHAR(256) NOT NULL,
    goal        TEXT,
    mode        VARCHAR(32)  DEFAULT 'SUPERVISOR',
    status      VARCHAR(32)  DEFAULT 'PENDING',
    plan        JSONB        DEFAULT '{}',
    result      JSONB        DEFAULT '{}',
    created_by  VARCHAR(64),
    created_at  TIMESTAMP    DEFAULT NOW(),
    finished_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ecos_mission_task (
    id          VARCHAR(64)  PRIMARY KEY,
    mission_id  VARCHAR(64)  NOT NULL REFERENCES ecos_mission(id),
    agent_id    VARCHAR(64)  NOT NULL,
    instruction TEXT,
    status      VARCHAR(32)  DEFAULT 'PENDING',
    result      JSONB        DEFAULT '{}',
    depends_on  VARCHAR(64),
    started_at  TIMESTAMP,
    finished_at TIMESTAMP
);

-- 种子Agent（用metadata JSONB存扩展字段）
INSERT INTO ecos_agent_registry (id, name, role, capability, endpoint, metadata) VALUES
('ag-data', '数据分析Agent', 'data',
 '{"tools":["object_query","ontology_explore"],"model":"deepseek-chat"}',
 'http://localhost:8081/sys-man/api/agent-mesh/agents/ag-data',
 '{"systemPrompt":"你是一个数据分析专家。从数据库提取记录，检查数据完整性，输出结构化分析结果。","maxIterations":8,"description":"从数据源提取、清洗、分析数据"}'),
('ag-knowledge', '知识检索Agent', 'knowledge',
 '{"tools":["knowledge_search"],"model":"deepseek-chat"}',
 'http://localhost:8081/sys-man/api/agent-mesh/agents/ag-knowledge',
 '{"systemPrompt":"你是一个知识检索专家。检索政策法规、行业标准和历史案例。","maxIterations":8,"description":"检索政策、标准、历史案例"}'),
('ag-compliance', '合规审查Agent', 'compliance',
 '{"tools":["object_query","knowledge_search","workflow_start"],"model":"deepseek-chat"}',
 'http://localhost:8081/sys-man/api/agent-mesh/agents/ag-compliance',
 '{"systemPrompt":"你是一个企业合规审查专家。检查：1)资质有效性 2)注册资本 3)行政处罚 4)信用评级。逐项给出合规结论和风险等级。","maxIterations":10,"description":"检查资质、合同合规性，识别风险点"}')
ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name;
