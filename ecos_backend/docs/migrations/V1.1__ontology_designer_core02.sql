-- S1-CORE02: Ontology Designer DB Migration
-- Domain / Rule / Version tables + Property & Action enhancements

-- ── 5.1.1 ecos_domain ──────
CREATE TABLE IF NOT EXISTS ecos_domain (
    id              VARCHAR(50) PRIMARY KEY,
    code            VARCHAR(100) NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    owner           VARCHAR(100),
    description     TEXT,
    status          VARCHAR(50) DEFAULT 'Draft',
    sort_order      INT DEFAULT 0,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- ── 5.1.2 ecos_ontology_property Enhancement ──────
ALTER TABLE ecos_ontology_property ADD COLUMN IF NOT EXISTS enum_values TEXT;
ALTER TABLE ecos_ontology_property ADD COLUMN IF NOT EXISTS default_value VARCHAR(500);
ALTER TABLE ecos_ontology_property ADD COLUMN IF NOT EXISTS validation_rule TEXT;
ALTER TABLE ecos_ontology_property ADD COLUMN IF NOT EXISTS unique_flag INT DEFAULT 0;
ALTER TABLE ecos_ontology_property ADD COLUMN IF NOT EXISTS ref_entity_code VARCHAR(100);
ALTER TABLE ecos_ontology_property ADD COLUMN IF NOT EXISTS max_length INT;
ALTER TABLE ecos_ontology_property ADD COLUMN IF NOT EXISTS min_value NUMERIC;
ALTER TABLE ecos_ontology_property ADD COLUMN IF NOT EXISTS max_value NUMERIC;

-- ── 5.1.2b ecos_ontology_action Enhancement ──────
ALTER TABLE ecos_ontology_action ADD COLUMN IF NOT EXISTS code VARCHAR(100);
ALTER TABLE ecos_ontology_action ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE ecos_ontology_action ADD COLUMN IF NOT EXISTS preconditions TEXT;
ALTER TABLE ecos_ontology_action ADD COLUMN IF NOT EXISTS effects TEXT;

-- ── 5.1.3 ecos_ontology_rule ──────
CREATE TABLE IF NOT EXISTS ecos_ontology_rule (
    id              VARCHAR(50) PRIMARY KEY,
    entity_id       VARCHAR(50) NOT NULL,
    code            VARCHAR(100) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    rule_type       VARCHAR(50) NOT NULL,
    expression      TEXT NOT NULL,
    action          TEXT,
    priority        INT DEFAULT 0,
    enabled         INT DEFAULT 1,
    description     TEXT,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_rule_code ON ecos_ontology_rule(entity_id, code);

-- ── 5.1.4 ecos_ontology_version ──────
CREATE TABLE IF NOT EXISTS ecos_ontology_version (
    id              VARCHAR(50) PRIMARY KEY,
    ontology_id     VARCHAR(50) NOT NULL,
    version_no      VARCHAR(20) NOT NULL,
    status          VARCHAR(50) DEFAULT 'Draft',
    snapshot        JSONB NOT NULL,
    change_log      TEXT,
    publisher       VARCHAR(100),
    published_at    TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(ontology_id, version_no)
);
