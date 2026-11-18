-- Sprint 3: Knowledge Graph and Vector tables
CREATE TABLE IF NOT EXISTS ecos_knowledge.knowledge_article (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255),
    content TEXT,
    source VARCHAR(512),
    domain VARCHAR(64),
    tags JSONB DEFAULT '[]',
    status VARCHAR(16) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_knowledge.knowledge_embedding (
    id VARCHAR(64) PRIMARY KEY,
    document_id VARCHAR(64),
    chunk_index INTEGER,
    content TEXT,
    embedding JSONB,
    embedding_model VARCHAR(128) DEFAULT 'bge-small-zh-v1.5',
    token_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_knowledge.graph_node (
    id VARCHAR(64) PRIMARY KEY,
    label VARCHAR(64),
    properties JSONB DEFAULT '{}',
    domain VARCHAR(64),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_knowledge.graph_edge (
    id VARCHAR(64) PRIMARY KEY,
    source_id VARCHAR(64),
    target_id VARCHAR(64),
    type VARCHAR(64),
    properties JSONB DEFAULT '{}',
    weight DECIMAL(5,2) DEFAULT 1.0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_knowledge.graph_subgraph (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255),
    description TEXT,
    node_ids JSONB DEFAULT '[]',
    edge_ids JSONB DEFAULT '[]',
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_knowledge_embedding_doc ON ecos_knowledge.knowledge_embedding(document_id);
CREATE INDEX IF NOT EXISTS idx_graph_node_label ON ecos_knowledge.graph_node(label);
CREATE INDEX IF NOT EXISTS idx_graph_edge_source ON ecos_knowledge.graph_edge(source_id);
CREATE INDEX IF NOT EXISTS idx_graph_edge_target ON ecos_knowledge.graph_edge(target_id);
CREATE INDEX IF NOT EXISTS idx_graph_edge_type ON ecos_knowledge.graph_edge(type);
