-- ============================================================================
-- ECOS Knowledge Domain — PostgreSQL DDL
-- Schema: ecos_knowledge
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS ecos_knowledge;

-- ============================================
-- Table: graph_node
-- Description: 知识图谱节点表(kb-engine)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_knowledge.graph_node (
    id          VARCHAR(64)  PRIMARY KEY,
    label       VARCHAR(64),
    node_type   VARCHAR(64)  DEFAULT 'Concept',
    description TEXT,
    properties  JSONB        DEFAULT '{}',
    domain      VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_knowledge.graph_node IS '知识图谱节点表(kb-engine)';
COMMENT ON COLUMN ecos_knowledge.graph_node.label IS '标签/名称';
COMMENT ON COLUMN ecos_knowledge.graph_node.node_type IS '节点类型: Concept/Entity/Event';
COMMENT ON COLUMN ecos_knowledge.graph_node.properties IS '属性JSON';
CREATE INDEX IF NOT EXISTS idx_kb_gnode_label ON ecos_knowledge.graph_node(label);
CREATE INDEX IF NOT EXISTS idx_kb_gnode_domain ON ecos_knowledge.graph_node(domain);

-- ============================================
-- Table: graph_edge
-- Description: 知识图谱边表(kb-engine)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_knowledge.graph_edge (
    id          VARCHAR(64)  PRIMARY KEY,
    source_id   VARCHAR(64)  NOT NULL,
    target_id   VARCHAR(64)  NOT NULL,
    type        VARCHAR(64),
    properties  JSONB        DEFAULT '{}',
    weight      DECIMAL(5,2) DEFAULT 1.0,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_knowledge.graph_edge IS '知识图谱边表(kb-engine)';
COMMENT ON COLUMN ecos_knowledge.graph_edge.type IS '关系类型';
COMMENT ON COLUMN ecos_knowledge.graph_edge.weight IS '关系权重';
CREATE INDEX IF NOT EXISTS idx_kb_gedge_src ON ecos_knowledge.graph_edge(source_id);
CREATE INDEX IF NOT EXISTS idx_kb_gedge_tgt ON ecos_knowledge.graph_edge(target_id);
CREATE INDEX IF NOT EXISTS idx_kb_gedge_type ON ecos_knowledge.graph_edge(type);
ALTER TABLE ecos_knowledge.graph_edge
    ADD CONSTRAINT fk_kb_gedge_src FOREIGN KEY (source_id) REFERENCES ecos_knowledge.graph_node(id);
ALTER TABLE ecos_knowledge.graph_edge
    ADD CONSTRAINT fk_kb_gedge_tgt FOREIGN KEY (target_id) REFERENCES ecos_knowledge.graph_node(id);

-- ============================================
-- Table: graph_subgraph
-- Description: 知识图谱子图表(kb-engine)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_knowledge.graph_subgraph (
    id          VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    node_ids    JSONB        DEFAULT '[]',
    edge_ids    JSONB        DEFAULT '[]',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_knowledge.graph_subgraph IS '知识图谱子图表(kb-engine)';

-- ============================================
-- Table: knowledge_article
-- Description: 知识文章表(kb-engine)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_knowledge.knowledge_article (
    id          VARCHAR(64)  PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    content     TEXT,
    source      VARCHAR(512),
    source_type VARCHAR(64),
    domain      VARCHAR(64),
    category    VARCHAR(64),
    tags        JSONB        DEFAULT '[]',
    status      VARCHAR(16)  DEFAULT 'ACTIVE',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_knowledge.knowledge_article IS '知识文章表(kb-engine)';
CREATE INDEX IF NOT EXISTS idx_kb_art_domain ON ecos_knowledge.knowledge_article(domain);
CREATE INDEX IF NOT EXISTS idx_kb_art_status ON ecos_knowledge.knowledge_article(status);

-- ============================================
-- Table: knowledge_embedding
-- Description: 知识向量嵌入表(kb-engine)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_knowledge.knowledge_embedding (
    id             VARCHAR(64)  PRIMARY KEY,
    document_id    VARCHAR(64),
    article_id     VARCHAR(64),
    chunk_index    INTEGER,
    chunk_text     TEXT,
    content        TEXT,
    embedding      JSONB,
    model          VARCHAR(128),
    embedding_model VARCHAR(128) DEFAULT 'bge-small-zh-v1.5',
    token_count    INTEGER      DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_knowledge.knowledge_embedding IS '知识向量嵌入表(kb-engine)';
CREATE INDEX IF NOT EXISTS idx_kb_emb_doc ON ecos_knowledge.knowledge_embedding(document_id);

-- ============================================
-- Table: expert_rule
-- Description: 专家规则表(kb-engine)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_knowledge.expert_rule (
    id              VARCHAR(64)  PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    domain          VARCHAR(64),
    rule_type       VARCHAR(32)  DEFAULT 'IF-THEN',
    condition_expr  TEXT,
    action_expr     TEXT,
    priority        INTEGER      DEFAULT 0,
    enabled         BOOLEAN      DEFAULT TRUE,
    description     TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_knowledge.expert_rule IS '专家规则表(kb-engine)';
CREATE INDEX IF NOT EXISTS idx_kb_rule_domain ON ecos_knowledge.expert_rule(domain);
CREATE INDEX IF NOT EXISTS idx_kb_rule_type   ON ecos_knowledge.expert_rule(rule_type);

-- ============================================
-- Table: ecos_knowledge_graph_node
-- Description: 知识图谱节点表(runtime-core兼容)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_knowledge_graph_node (
    id              VARCHAR(64)  PRIMARY KEY,
    label           VARCHAR(128),
    node_type       VARCHAR(64)  DEFAULT 'Concept',
    description     TEXT,
    properties_json JSONB,
    source_node_id  VARCHAR(64),
    domain          VARCHAR(64),
    tenant_id       VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_knowledge.ecos_knowledge_graph_node IS '知识图谱节点表(runtime-core兼容)';
CREATE INDEX IF NOT EXISTS idx_kb_kgn_label ON ecos_knowledge.ecos_knowledge_graph_node(label);

-- ============================================
-- Table: ecos_knowledge_graph_edge
-- Description: 知识图谱边表(runtime-core兼容)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_knowledge_graph_edge (
    id              VARCHAR(64)  PRIMARY KEY,
    source_node_id  VARCHAR(64)  NOT NULL,
    target_node_id  VARCHAR(64)  NOT NULL,
    relationship    VARCHAR(64),
    properties_json JSONB,
    weight          DECIMAL(5,2) DEFAULT 1.0,
    tenant_id       VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_knowledge.ecos_knowledge_graph_edge IS '知识图谱边表(runtime-core兼容)';
CREATE INDEX IF NOT EXISTS idx_kb_kge_src ON ecos_knowledge.ecos_knowledge_graph_edge(source_node_id);
CREATE INDEX IF NOT EXISTS idx_kb_kge_tgt ON ecos_knowledge.ecos_knowledge_graph_edge(target_node_id);

-- ============================================
-- Table: ecos_glossary_term
-- Description: 术语表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_glossary_term (
    id          BIGSERIAL     PRIMARY KEY,
    code        VARCHAR(64),
    name        VARCHAR(255)  NOT NULL,
    definition  TEXT,
    domain      VARCHAR(128),
    domain_id   VARCHAR(50),
    owner       VARCHAR(128),
    status      VARCHAR(32)   DEFAULT 'DRAFT',
    tenant_id   VARCHAR(64),
    created_by  VARCHAR(128),
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_knowledge.ecos_glossary_term IS '术语表';
CREATE INDEX IF NOT EXISTS idx_kb_gloss_domain ON ecos_knowledge.ecos_glossary_term(domain);
CREATE INDEX IF NOT EXISTS idx_kb_gloss_status ON ecos_knowledge.ecos_glossary_term(status);

-- ============================================
-- Table: ecos_knowledge_document
-- Description: 知识文档表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_knowledge_document (
    id            VARCHAR(64)  PRIMARY KEY,
    title         VARCHAR(255) NOT NULL,
    content       TEXT,
    doc_type      VARCHAR(32),
    tags          VARCHAR(256),
    entity_types  VARCHAR(512),
    tenant_id     VARCHAR(64),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_knowledge.ecos_knowledge_document IS '知识文档表';

-- ============================================
-- Table: ecos_marketplace_asset
-- Description: 市场资产表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_marketplace_asset (
    id                  BIGSERIAL     PRIMARY KEY,
    name                VARCHAR(255)  NOT NULL,
    description         TEXT,
    category            VARCHAR(64),
    owner               VARCHAR(128),
    rating              NUMERIC(3,2)  DEFAULT 0.0,
    popularity          INTEGER       DEFAULT 0,
    status              VARCHAR(32)   DEFAULT 'PUBLISHED',
    ontology_entity_id  VARCHAR(128),
    tenant_id           VARCHAR(64),
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_knowledge.ecos_marketplace_asset IS '市场资产表';
CREATE INDEX IF NOT EXISTS idx_kb_mkt_cat   ON ecos_knowledge.ecos_marketplace_asset(category);
CREATE INDEX IF NOT EXISTS idx_kb_mkt_stat  ON ecos_knowledge.ecos_marketplace_asset(status);
CREATE INDEX IF NOT EXISTS idx_kb_mkt_pop   ON ecos_knowledge.ecos_marketplace_asset(popularity DESC);

-- ============================================
-- Table: ecos_marketplace_access_request
-- Description: 市场访问请求表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_marketplace_access_request (
    id         BIGSERIAL   PRIMARY KEY,
    asset_id   BIGINT      NOT NULL,
    reason     TEXT,
    applicant  VARCHAR(128),
    status     VARCHAR(32) DEFAULT 'PENDING',
    created_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ecos_knowledge.ecos_marketplace_access_request IS '市场访问请求表';
CREATE INDEX IF NOT EXISTS idx_kb_mktreq_asset  ON ecos_knowledge.ecos_marketplace_access_request(asset_id);
CREATE INDEX IF NOT EXISTS idx_kb_mktreq_status ON ecos_knowledge.ecos_marketplace_access_request(status);
ALTER TABLE ecos_knowledge.ecos_marketplace_access_request
    ADD CONSTRAINT fk_kb_mktreq_asset FOREIGN KEY (asset_id) REFERENCES ecos_knowledge.ecos_marketplace_asset(id);
