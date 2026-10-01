-- ============================================================================
-- ECOS Knowledge Domain — MySQL DDL
-- Architecture Constraint: ADD-ONLY — never DROP column/table
-- ============================================================================

-- ============================================
-- Table: graph_node
-- Description: 知识图谱节点表(kb-engine)
-- ============================================
CREATE TABLE IF NOT EXISTS graph_node (
    id          VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    label       VARCHAR(64) COMMENT '标签/名称',
    node_type   VARCHAR(64)  DEFAULT 'Concept' COMMENT '节点类型: Concept/Entity/Event',
    description LONGTEXT COMMENT '描述',
    properties  JSON COMMENT '属性JSON',
    domain      VARCHAR(64) COMMENT '域',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识图谱节点表(kb-engine)';

CREATE INDEX idx_kb_gnode_label  ON graph_node(label);
CREATE INDEX idx_kb_gnode_domain ON graph_node(domain);

-- ============================================
-- Table: graph_edge
-- Description: 知识图谱边表(kb-engine)
-- ============================================
CREATE TABLE IF NOT EXISTS graph_edge (
    id          VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    source_id   VARCHAR(64)  NOT NULL COMMENT '源节点ID',
    target_id   VARCHAR(64)  NOT NULL COMMENT '目标节点ID',
    type        VARCHAR(64) COMMENT '关系类型',
    properties  JSON COMMENT '属性',
    weight      DECIMAL(5,2) DEFAULT 1.0 COMMENT '关系权重',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识图谱边表(kb-engine)';

CREATE INDEX idx_kb_gedge_src  ON graph_edge(source_id);
CREATE INDEX idx_kb_gedge_tgt  ON graph_edge(target_id);
CREATE INDEX idx_kb_gedge_type ON graph_edge(type);
ALTER TABLE graph_edge
    ADD CONSTRAINT fk_kb_gedge_src FOREIGN KEY (source_id) REFERENCES graph_node(id);
ALTER TABLE graph_edge
    ADD CONSTRAINT fk_kb_gedge_tgt FOREIGN KEY (target_id) REFERENCES graph_node(id);

-- ============================================
-- Table: graph_subgraph
-- Description: 知识图谱子图表(kb-engine)
-- ============================================
CREATE TABLE IF NOT EXISTS graph_subgraph (
    id          VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    name        VARCHAR(255) NOT NULL COMMENT '名称',
    description LONGTEXT COMMENT '描述',
    node_ids    JSON COMMENT '节点ID列表',
    edge_ids    JSON COMMENT '边ID列表',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识图谱子图表(kb-engine)';

-- ============================================
-- Table: knowledge_article
-- Description: 知识文章表(kb-engine)
-- ============================================
CREATE TABLE IF NOT EXISTS knowledge_article (
    id          VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    title       VARCHAR(255) NOT NULL COMMENT '标题',
    content     LONGTEXT COMMENT '内容',
    source      VARCHAR(512) COMMENT '来源',
    source_type VARCHAR(64) COMMENT '来源类型',
    domain      VARCHAR(64) COMMENT '域',
    category    VARCHAR(64) COMMENT '分类',
    tags        JSON COMMENT '标签',
    status      VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT '状态',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文章表(kb-engine)';

CREATE INDEX idx_kb_art_domain ON knowledge_article(domain);
CREATE INDEX idx_kb_art_status ON knowledge_article(status);

-- ============================================
-- Table: knowledge_embedding
-- Description: 知识向量嵌入表(kb-engine)
-- ============================================
CREATE TABLE IF NOT EXISTS knowledge_embedding (
    id              VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    document_id     VARCHAR(64) COMMENT '文档ID',
    article_id      VARCHAR(64) COMMENT '文章ID',
    chunk_index     INT COMMENT '块索引',
    chunk_text      LONGTEXT COMMENT '块文本',
    content         LONGTEXT COMMENT '内容',
    embedding       JSON COMMENT '嵌入向量',
    model           VARCHAR(128) COMMENT '模型',
    embedding_model VARCHAR(128) DEFAULT 'bge-small-zh-v1.5' COMMENT '嵌入模型',
    token_count     INT          DEFAULT 0 COMMENT 'Token数',
    created_at      DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识向量嵌入表(kb-engine)';

CREATE INDEX idx_kb_emb_doc ON knowledge_embedding(document_id);

-- ============================================
-- Table: expert_rule
-- Description: 专家规则表(kb-engine)
-- ============================================
CREATE TABLE IF NOT EXISTS expert_rule (
    id              VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    name            VARCHAR(255) NOT NULL COMMENT '名称',
    domain          VARCHAR(64) COMMENT '域',
    rule_type       VARCHAR(32)  DEFAULT 'IF-THEN' COMMENT '规则类型',
    condition_expr  LONGTEXT COMMENT '条件表达式',
    action_expr     LONGTEXT COMMENT '动作表达式',
    priority        INT          DEFAULT 0 COMMENT '优先级',
    enabled         TINYINT(1)   DEFAULT 1 COMMENT '是否启用',
    description     LONGTEXT COMMENT '描述',
    created_at      DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专家规则表(kb-engine)';

CREATE INDEX idx_kb_rule_domain ON expert_rule(domain);
CREATE INDEX idx_kb_rule_type   ON expert_rule(rule_type);

-- ============================================
-- Table: ecos_knowledge_graph_node
-- Description: 知识图谱节点表(runtime-core兼容)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_knowledge_graph_node (
    id              VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    label           VARCHAR(128) COMMENT '标签',
    node_type       VARCHAR(64)  DEFAULT 'Concept' COMMENT '节点类型',
    description     LONGTEXT COMMENT '描述',
    properties_json JSON COMMENT '属性JSON',
    source_node_id  VARCHAR(64) COMMENT '源节点ID',
    domain          VARCHAR(64) COMMENT '域',
    tenant_id       VARCHAR(64) COMMENT '租户ID',
    created_at      DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识图谱节点表(runtime-core兼容)';

CREATE INDEX idx_kb_kgn_label ON ecos_knowledge_graph_node(label);

-- ============================================
-- Table: ecos_knowledge_graph_edge
-- Description: 知识图谱边表(runtime-core兼容)
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_knowledge_graph_edge (
    id              VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    source_node_id  VARCHAR(64)  NOT NULL COMMENT '源节点ID',
    target_node_id  VARCHAR(64)  NOT NULL COMMENT '目标节点ID',
    relationship    VARCHAR(64) COMMENT '关系',
    properties_json JSON COMMENT '属性JSON',
    weight          DECIMAL(5,2) DEFAULT 1.0 COMMENT '权重',
    tenant_id       VARCHAR(64) COMMENT '租户ID',
    created_at      DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识图谱边表(runtime-core兼容)';

CREATE INDEX idx_kb_kge_src ON ecos_knowledge_graph_edge(source_node_id);
CREATE INDEX idx_kb_kge_tgt ON ecos_knowledge_graph_edge(target_node_id);

-- ============================================
-- Table: ecos_glossary_term
-- Description: 术语表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_glossary_term (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'ID',
    code        VARCHAR(64) COMMENT '编码',
    name        VARCHAR(255)  NOT NULL COMMENT '名称',
    definition  LONGTEXT COMMENT '定义',
    domain      VARCHAR(128) COMMENT '域',
    domain_id   VARCHAR(50) COMMENT '域ID',
    owner       VARCHAR(128) COMMENT '负责人',
    status      VARCHAR(32)   DEFAULT 'DRAFT' COMMENT '状态',
    tenant_id   VARCHAR(64) COMMENT '租户ID',
    created_by  VARCHAR(128) COMMENT '创建人',
    created_at  DATETIME      NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at  DATETIME      NOT NULL DEFAULT NOW() COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='术语表';

CREATE INDEX idx_kb_gloss_domain ON ecos_glossary_term(domain);
CREATE INDEX idx_kb_gloss_status ON ecos_glossary_term(status);

-- ============================================
-- Table: ecos_knowledge_document
-- Description: 知识文档表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_knowledge_document (
    id            VARCHAR(64)  PRIMARY KEY COMMENT 'ID',
    title         VARCHAR(255) NOT NULL COMMENT '标题',
    content       LONGTEXT COMMENT '内容',
    doc_type      VARCHAR(32) COMMENT '文档类型',
    tags          VARCHAR(256) COMMENT '标签',
    entity_types  VARCHAR(512) COMMENT '实体类型',
    tenant_id     VARCHAR(64) COMMENT '租户ID',
    created_at    DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档表';

-- ============================================
-- Table: ecos_marketplace_asset
-- Description: 市场资产表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_marketplace_asset (
    id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'ID',
    name                VARCHAR(255)  NOT NULL COMMENT '名称',
    description         LONGTEXT COMMENT '描述',
    category            VARCHAR(64) COMMENT '分类',
    owner               VARCHAR(128) COMMENT '负责人',
    rating              DECIMAL(3,2)  DEFAULT 0.0 COMMENT '评分',
    popularity          INT           DEFAULT 0 COMMENT '人气',
    status              VARCHAR(32)   DEFAULT 'PUBLISHED' COMMENT '状态',
    ontology_entity_id  VARCHAR(128) COMMENT '本体实体ID',
    tenant_id           VARCHAR(64) COMMENT '租户ID',
    created_at          DATETIME      NOT NULL DEFAULT NOW() COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='市场资产表';

CREATE INDEX idx_kb_mkt_cat   ON ecos_marketplace_asset(category);
CREATE INDEX idx_kb_mkt_stat  ON ecos_marketplace_asset(status);
CREATE INDEX idx_kb_mkt_pop   ON ecos_marketplace_asset(popularity DESC);

-- ============================================
-- Table: ecos_marketplace_access_request
-- Description: 市场访问请求表
-- ============================================
CREATE TABLE IF NOT EXISTS ecos_marketplace_access_request (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    asset_id   BIGINT       NOT NULL COMMENT '资产ID',
    reason     LONGTEXT COMMENT '原因',
    applicant  VARCHAR(128) COMMENT '申请人',
    status     VARCHAR(32)  DEFAULT 'PENDING' COMMENT '状态',
    created_at DATETIME     NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT NOW() COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='市场访问请求表';

CREATE INDEX idx_kb_mktreq_asset  ON ecos_marketplace_access_request(asset_id);
CREATE INDEX idx_kb_mktreq_status ON ecos_marketplace_access_request(status);
ALTER TABLE ecos_marketplace_access_request
    ADD CONSTRAINT fk_kb_mktreq_asset FOREIGN KEY (asset_id) REFERENCES ecos_marketplace_asset(id);
