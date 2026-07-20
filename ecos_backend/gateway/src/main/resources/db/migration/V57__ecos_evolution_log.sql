CREATE TABLE IF NOT EXISTS ecos_ai.ecos_evolution_log (
    id VARCHAR(64) PRIMARY KEY,
    mission_id VARCHAR(64),
    stage VARCHAR(32) NOT NULL,
    agent_id VARCHAR(64),
    input_context JSONB,
    output_result JSONB,
    status VARCHAR(32) DEFAULT 'STARTED',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    tenant_id VARCHAR(64),
    created_at TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE ecos_ai.ecos_evolution_log IS '自治进化日志表';
COMMENT ON COLUMN ecos_ai.ecos_evolution_log.stage IS '阶段: DIAGNOSIS/ONTOLOGY_EVOLVE/KNOWLEDGE_REBUILD/SECURITY_HEAL/DEPLOYMENT';
COMMENT ON COLUMN ecos_ai.ecos_evolution_log.status IS '状态: STARTED/COMPLETED/FAILED';
