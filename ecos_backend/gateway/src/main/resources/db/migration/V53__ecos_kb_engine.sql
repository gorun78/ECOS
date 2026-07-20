-- Sprint 7: KB-Engine schema additions
-- Add missing columns to graph_node
ALTER TABLE ecos_knowledge.graph_node ADD COLUMN IF NOT EXISTS node_type VARCHAR(64) DEFAULT 'Concept';
ALTER TABLE ecos_knowledge.graph_node ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE ecos_knowledge.graph_node ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- Rename properties -> properties_json for consistency with Java model
-- (Kept as-is; mapper uses properties_json column alias via @Result mapping)

-- Add missing columns to graph_edge
ALTER TABLE ecos_knowledge.graph_edge ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- Add missing columns to knowledge_article
ALTER TABLE ecos_knowledge.knowledge_article ADD COLUMN IF NOT EXISTS category VARCHAR(64);
ALTER TABLE ecos_knowledge.knowledge_article ADD COLUMN IF NOT EXISTS source_type VARCHAR(64);

-- Rename document_id -> article_id in knowledge_embedding for consistency
ALTER TABLE ecos_knowledge.knowledge_embedding ADD COLUMN IF NOT EXISTS article_id VARCHAR(64);
UPDATE ecos_knowledge.knowledge_embedding SET article_id = document_id WHERE article_id IS NULL;

-- Rename content -> chunk_text in knowledge_embedding
ALTER TABLE ecos_knowledge.knowledge_embedding ADD COLUMN IF NOT EXISTS chunk_text TEXT;
UPDATE ecos_knowledge.knowledge_embedding SET chunk_text = content WHERE chunk_text IS NULL;

-- Rename embedding_model -> model in knowledge_embedding
ALTER TABLE ecos_knowledge.knowledge_embedding ADD COLUMN IF NOT EXISTS model VARCHAR(128);
UPDATE ecos_knowledge.knowledge_embedding SET model = embedding_model WHERE model IS NULL;

-- Expert rule table
CREATE TABLE IF NOT EXISTS ecos_knowledge.expert_rule (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    domain VARCHAR(64),
    rule_type VARCHAR(32) DEFAULT 'IF-THEN',
    condition_expr TEXT,
    action_expr TEXT,
    priority INTEGER DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    description TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_expert_rule_domain ON ecos_knowledge.expert_rule(domain);
CREATE INDEX IF NOT EXISTS idx_expert_rule_type ON ecos_knowledge.expert_rule(rule_type);
