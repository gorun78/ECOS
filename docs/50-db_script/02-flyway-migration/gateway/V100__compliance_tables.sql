-- V100: 合规模块（知识抽取 + 规则引擎）建表
-- 引擎分工: kb-engine (规则CRUD) + ontology-engine (模型)
-- 库: sys_man (PostgreSQL)

-- 1. 合规规则表（扩展 ExpertRule）
CREATE TABLE IF NOT EXISTS sys_compliance_rule (
    id              VARCHAR(64)   NOT NULL PRIMARY KEY,
    name            VARCHAR(255)  NOT NULL,
    domain          VARCHAR(128),
    rule_type       VARCHAR(64),
    condition       TEXT,                       -- SpEL 条件表达式
    action          TEXT,                       -- 规则结论
    priority        INT           DEFAULT 0,
    enabled         BOOLEAN       DEFAULT TRUE,
    description     TEXT,
    status          VARCHAR(32)   DEFAULT 'DRAFT',  -- DRAFT/IN_REVIEW/ACTIVE/DEPRECATED/SUPERSEDED
    required_fact_list TEXT,                    -- JSON 数组: 需要的前件事实
    extracted_rule_id VARCHAR(64),              -- 关联抽取规则ID
    approved_by     VARCHAR(128),
    effective_date  BIGINT,
    expiry_date     BIGINT,
    version         INT           DEFAULT 1,
    created_at      BIGINT,
    updated_at      BIGINT
);

-- 2. 规则版本快照
CREATE TABLE IF NOT EXISTS sys_rule_version (
    id              VARCHAR(64)   NOT NULL PRIMARY KEY,
    rule_id         VARCHAR(64)   NOT NULL,
    version_number  INT           NOT NULL,
    snapshot        TEXT,                       -- JSON 格式的规则快照
    changed_by      VARCHAR(128),
    changed_at      BIGINT,
    change_note     TEXT,
    CONSTRAINT fk_rule_version_rule FOREIGN KEY (rule_id) REFERENCES sys_compliance_rule(id)
);

-- 3. 知识抽取来源
CREATE TABLE IF NOT EXISTS sys_extraction_source (
    id              VARCHAR(64)   NOT NULL PRIMARY KEY,
    source_type     VARCHAR(32),                -- MANUAL / KB_ARTICLE / DOCUMENT / KG_ENTITY / STRUCTURED
    source_id       VARCHAR(255),
    source_title    VARCHAR(512),
    source_excerpt  TEXT,
    extracted_at    BIGINT,
    extractor_version VARCHAR(32)
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_compliance_rule_domain   ON sys_compliance_rule(domain);
CREATE INDEX IF NOT EXISTS idx_compliance_rule_status   ON sys_compliance_rule(status);
CREATE INDEX IF NOT EXISTS idx_rule_version_rule_id     ON sys_rule_version(rule_id);
CREATE INDEX IF NOT EXISTS idx_extraction_source_type   ON sys_extraction_source(source_type);
