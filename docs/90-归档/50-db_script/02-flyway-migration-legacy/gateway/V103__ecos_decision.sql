-- V103__ecos_decision.sql
-- ECOS 决策智能层 — 6 张表（PMO-32）
-- 五元组: Decision / Policy / Exception / Precedent / ApprovalChain + Provenance

CREATE TABLE IF NOT EXISTS ecos_decision (
    id              VARCHAR(64) PRIMARY KEY,
    category        VARCHAR(64)  NOT NULL,
    scenario        TEXT,
    reasoning       TEXT,
    outcome         VARCHAR(128),
    confidence      DECIMAL(5,4),
    decision_maker  VARCHAR(64),
    valid_from      TIMESTAMP,
    valid_until     TIMESTAMP,
    metadata        JSONB,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_decision_causal_link (
    id                  VARCHAR(64) PRIMARY KEY,
    source_decision_id  VARCHAR(64) NOT NULL,
    target_decision_id  VARCHAR(64) NOT NULL,
    relationship        VARCHAR(32) NOT NULL,
    weight              DECIMAL(5,4) DEFAULT 0.5,
    created_at          TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_decision_policy (
    id          VARCHAR(64) PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    category    VARCHAR(64),
    rules       JSONB,
    version     INTEGER DEFAULT 1,
    status      VARCHAR(16) DEFAULT 'active',
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_decision_exception (
    id          VARCHAR(64) PRIMARY KEY,
    decision_id VARCHAR(64) NOT NULL,
    reason      TEXT,
    approver    VARCHAR(64),
    status      VARCHAR(16) DEFAULT 'pending',
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_decision_approval (
    id          VARCHAR(64) PRIMARY KEY,
    decision_id VARCHAR(64) NOT NULL,
    approver    VARCHAR(64),
    level       INTEGER DEFAULT 1,
    status      VARCHAR(16) DEFAULT 'pending',
    comment     TEXT,
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_decision_precedent (
    id                  VARCHAR(64) PRIMARY KEY,
    decision_id         VARCHAR(64) NOT NULL,
    similar_decision_id VARCHAR(64) NOT NULL,
    similarity          DECIMAL(5,4) DEFAULT 0,
    note                TEXT,
    created_at          TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_provenance_entry (
    id          VARCHAR(64) PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL,
    entity_id   VARCHAR(64) NOT NULL,
    source_type VARCHAR(32),
    source_ref  TEXT,
    agent       VARCHAR(64),
    activity    VARCHAR(64),
    timestamp   TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_decision_category ON ecos_decision(category);
CREATE INDEX IF NOT EXISTS idx_causal_source ON ecos_decision_causal_link(source_decision_id);
CREATE INDEX IF NOT EXISTS idx_causal_target ON ecos_decision_causal_link(target_decision_id);
CREATE INDEX IF NOT EXISTS idx_provenance_entity ON ecos_provenance_entry(entity_type, entity_id);
