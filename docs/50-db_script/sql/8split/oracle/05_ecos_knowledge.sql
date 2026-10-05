-- ============================================================================
-- ECOS Knowledge Domain — Oracle DDL
-- Schema: ecos_knowledge
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

BEGIN
   EXECUTE IMMEDIATE 'CREATE USER ecos_knowledge IDENTIFIED BY ecos_knowledge DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE INDEX TO ecos_knowledge;

-- ============================================
-- Table: graph_node
-- Description: 知识图谱节点表(kb-engine)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_knowledge.graph_node (
    id          VARCHAR2(64)  PRIMARY KEY,
    label       VARCHAR2(64),
    node_type   VARCHAR2(64)  DEFAULT ''Concept'',
    description CLOB,
    properties  CLOB          DEFAULT ''{}'',
    domain      VARCHAR2(64),
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_knowledge.graph_node IS '知识图谱节点表(kb-engine)';
COMMENT ON COLUMN ecos_knowledge.graph_node.label IS '标签/名称';
COMMENT ON COLUMN ecos_knowledge.graph_node.node_type IS '节点类型: Concept/Entity/Event';
COMMENT ON COLUMN ecos_knowledge.graph_node.properties IS '属性JSON';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_gnode_label ON ecos_knowledge.graph_node(label)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_gnode_domain ON ecos_knowledge.graph_node(domain)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: graph_edge
-- Description: 知识图谱边表(kb-engine)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_knowledge.graph_edge (
    id          VARCHAR2(64)  PRIMARY KEY,
    source_id   VARCHAR2(64)  NOT NULL,
    target_id   VARCHAR2(64)  NOT NULL,
    type        VARCHAR2(64),
    properties  CLOB          DEFAULT ''{}'',
    weight      NUMBER(5,2)   DEFAULT 1.0,
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_knowledge.graph_edge IS '知识图谱边表(kb-engine)';
COMMENT ON COLUMN ecos_knowledge.graph_edge.type IS '关系类型';
COMMENT ON COLUMN ecos_knowledge.graph_edge.weight IS '关系权重';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_gedge_src ON ecos_knowledge.graph_edge(source_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_gedge_tgt ON ecos_knowledge.graph_edge(target_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_gedge_type ON ecos_knowledge.graph_edge(type)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_knowledge.graph_edge ADD CONSTRAINT fk_kb_gedge_src FOREIGN KEY (source_id) REFERENCES ecos_knowledge.graph_node(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_knowledge.graph_edge ADD CONSTRAINT fk_kb_gedge_tgt FOREIGN KEY (target_id) REFERENCES ecos_knowledge.graph_node(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: graph_subgraph
-- Description: 知识图谱子图表(kb-engine)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_knowledge.graph_subgraph (
    id          VARCHAR2(64)  PRIMARY KEY,
    name        VARCHAR2(255) NOT NULL,
    description CLOB,
    node_ids    CLOB          DEFAULT ''[]'',
    edge_ids    CLOB          DEFAULT ''[]'',
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_knowledge.graph_subgraph IS '知识图谱子图表(kb-engine)';

-- ============================================
-- Table: knowledge_article
-- Description: 知识文章表(kb-engine)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_knowledge.knowledge_article (
    id          VARCHAR2(64)  PRIMARY KEY,
    title       VARCHAR2(255) NOT NULL,
    content     CLOB,
    source      VARCHAR2(512),
    source_type VARCHAR2(64),
    domain      VARCHAR2(64),
    category    VARCHAR2(64),
    tags        CLOB          DEFAULT ''[]'',
    status      VARCHAR2(16)  DEFAULT ''ACTIVE'',
    created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_knowledge.knowledge_article IS '知识文章表(kb-engine)';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_art_domain ON ecos_knowledge.knowledge_article(domain)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_art_status ON ecos_knowledge.knowledge_article(status)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: knowledge_embedding
-- Description: 知识向量嵌入表(kb-engine)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_knowledge.knowledge_embedding (
    id             VARCHAR2(64)  PRIMARY KEY,
    document_id    VARCHAR2(64),
    article_id     VARCHAR2(64),
    chunk_index    NUMBER(10),
    chunk_text     CLOB,
    content        CLOB,
    embedding      CLOB,
    model          VARCHAR2(128),
    embedding_model VARCHAR2(128) DEFAULT ''bge-small-zh-v1.5'',
    token_count    NUMBER(10)     DEFAULT 0,
    created_at     TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_knowledge.knowledge_embedding IS '知识向量嵌入表(kb-engine)';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_emb_doc ON ecos_knowledge.knowledge_embedding(document_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: expert_rule
-- Description: 专家规则表(kb-engine)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_knowledge.expert_rule (
    id              VARCHAR2(64)  PRIMARY KEY,
    name            VARCHAR2(255) NOT NULL,
    domain          VARCHAR2(64),
    rule_type       VARCHAR2(32)  DEFAULT ''IF-THEN'',
    condition_expr  CLOB,
    action_expr     CLOB,
    priority        NUMBER(10)    DEFAULT 0,
    enabled         NUMBER(1)     DEFAULT 1,
    description     CLOB,
    created_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_knowledge.expert_rule IS '专家规则表(kb-engine)';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_rule_domain ON ecos_knowledge.expert_rule(domain)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_rule_type ON ecos_knowledge.expert_rule(rule_type)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_knowledge_graph_node
-- Description: 知识图谱节点表(runtime-core兼容)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_knowledge.ecos_knowledge_graph_node (
    id              VARCHAR2(64)  PRIMARY KEY,
    label           VARCHAR2(128),
    node_type       VARCHAR2(64)  DEFAULT ''Concept'',
    description     CLOB,
    properties_json CLOB,
    source_node_id  VARCHAR2(64),
    domain          VARCHAR2(64),
    tenant_id       VARCHAR2(64),
    created_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_knowledge.ecos_knowledge_graph_node IS '知识图谱节点表(runtime-core兼容)';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_kgn_label ON ecos_knowledge.ecos_knowledge_graph_node(label)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_knowledge_graph_edge
-- Description: 知识图谱边表(runtime-core兼容)
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_knowledge.ecos_knowledge_graph_edge (
    id              VARCHAR2(64)  PRIMARY KEY,
    source_node_id  VARCHAR2(64)  NOT NULL,
    target_node_id  VARCHAR2(64)  NOT NULL,
    relationship    VARCHAR2(64),
    properties_json CLOB,
    weight          NUMBER(5,2)   DEFAULT 1.0,
    tenant_id       VARCHAR2(64),
    created_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_knowledge.ecos_knowledge_graph_edge IS '知识图谱边表(runtime-core兼容)';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_kge_src ON ecos_knowledge.ecos_knowledge_graph_edge(source_node_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_kge_tgt ON ecos_knowledge.ecos_knowledge_graph_edge(target_node_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Sequence for ecos_glossary_term
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'CREATE SEQUENCE ecos_knowledge.seq_glossary_term START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_glossary_term
-- Description: 术语表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_knowledge.ecos_glossary_term (
    id          NUMBER(19)     PRIMARY KEY,
    code        VARCHAR2(64),
    name        VARCHAR2(255)  NOT NULL,
    definition  CLOB,
    domain      VARCHAR2(128),
    domain_id   VARCHAR2(50),
    owner       VARCHAR2(128),
    status      VARCHAR2(32)   DEFAULT ''DRAFT'',
    tenant_id   VARCHAR2(64),
    created_by  VARCHAR2(128),
    created_at  TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_knowledge.ecos_glossary_term IS '术语表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_gloss_domain ON ecos_knowledge.ecos_glossary_term(domain)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_gloss_status ON ecos_knowledge.ecos_glossary_term(status)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

CREATE OR REPLACE TRIGGER ecos_knowledge.trg_glossary_term_id
BEFORE INSERT ON ecos_knowledge.ecos_glossary_term
FOR EACH ROW
BEGIN
   IF :NEW.id IS NULL THEN
      :NEW.id := ecos_knowledge.seq_glossary_term.NEXTVAL;
   END IF;
END;
/

-- ============================================
-- Table: ecos_knowledge_document
-- Description: 知识文档表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_knowledge.ecos_knowledge_document (
    id            VARCHAR2(64)  PRIMARY KEY,
    title         VARCHAR2(255) NOT NULL,
    content       CLOB,
    doc_type      VARCHAR2(32),
    tags          VARCHAR2(256),
    entity_types  VARCHAR2(512),
    tenant_id     VARCHAR2(64),
    created_at    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_knowledge.ecos_knowledge_document IS '知识文档表';

-- ============================================
-- Sequence for ecos_marketplace_asset
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'CREATE SEQUENCE ecos_knowledge.seq_marketplace_asset START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_marketplace_asset
-- Description: 市场资产表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_knowledge.ecos_marketplace_asset (
    id                  NUMBER(19)     PRIMARY KEY,
    name                VARCHAR2(255)  NOT NULL,
    description         CLOB,
    category            VARCHAR2(64),
    owner               VARCHAR2(128),
    rating              NUMBER(3,2)    DEFAULT 0.0,
    popularity          NUMBER(10)     DEFAULT 0,
    status              VARCHAR2(32)   DEFAULT ''PUBLISHED'',
    ontology_entity_id  VARCHAR2(128),
    tenant_id           VARCHAR2(64),
    created_at          TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_knowledge.ecos_marketplace_asset IS '市场资产表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_mkt_cat ON ecos_knowledge.ecos_marketplace_asset(category)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_mkt_stat ON ecos_knowledge.ecos_marketplace_asset(status)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_mkt_pop ON ecos_knowledge.ecos_marketplace_asset(popularity DESC)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

CREATE OR REPLACE TRIGGER ecos_knowledge.trg_marketplace_asset_id
BEFORE INSERT ON ecos_knowledge.ecos_marketplace_asset
FOR EACH ROW
BEGIN
   IF :NEW.id IS NULL THEN
      :NEW.id := ecos_knowledge.seq_marketplace_asset.NEXTVAL;
   END IF;
END;
/

-- ============================================
-- Sequence for ecos_marketplace_access_request
-- ============================================
BEGIN
   EXECUTE IMMEDIATE 'CREATE SEQUENCE ecos_knowledge.seq_marketplace_access_req START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- ============================================
-- Table: ecos_marketplace_access_request
-- Description: 市场访问请求表
-- ============================================
BEGIN
   EXECUTE IMMEDIATE '
CREATE TABLE ecos_knowledge.ecos_marketplace_access_request (
    id         NUMBER(19)    PRIMARY KEY,
    asset_id   NUMBER(19)    NOT NULL,
    reason     CLOB,
    applicant  VARCHAR2(128),
    status     VARCHAR2(32)  DEFAULT ''PENDING'',
    created_at TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)';
EXCEPTION
   WHEN OTHERS THEN NULL;
END;
/

COMMENT ON TABLE  ecos_knowledge.ecos_marketplace_access_request IS '市场访问请求表';

BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_mktreq_asset ON ecos_knowledge.ecos_marketplace_access_request(asset_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'CREATE INDEX idx_kb_mktreq_status ON ecos_knowledge.ecos_marketplace_access_request(status)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'ALTER TABLE ecos_knowledge.ecos_marketplace_access_request ADD CONSTRAINT fk_kb_mktreq_asset FOREIGN KEY (asset_id) REFERENCES ecos_knowledge.ecos_marketplace_asset(id)';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

CREATE OR REPLACE TRIGGER ecos_knowledge.trg_marketplace_access_req_id
BEFORE INSERT ON ecos_knowledge.ecos_marketplace_access_request
FOR EACH ROW
BEGIN
   IF :NEW.id IS NULL THEN
      :NEW.id := ecos_knowledge.seq_marketplace_access_req.NEXTVAL;
   END IF;
END;
/
