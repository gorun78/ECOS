-- ============================================================
-- V8__ecos_ontology_action.sql — 本体动作定义表
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_ontology_action (
    id              VARCHAR(64) PRIMARY KEY,
    entity_id       VARCHAR(64) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    action_type     VARCHAR(64) NOT NULL DEFAULT 'CUSTOM',
    rule_json       TEXT DEFAULT '',
    strategy        VARCHAR(255) DEFAULT '',
    status          VARCHAR(32) DEFAULT 'ACTIVE',
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- Seed action data
INSERT INTO ecos_ontology_action (id, entity_id, name, action_type, rule_json, strategy, status, created_at, updated_at)
SELECT * FROM (VALUES
    ('act001', 'ent001', '用户同步', 'SYNC', '{"trigger":"on_update","target":"ldap"}', 'BATCH', 'ACTIVE', NOW(), NOW()),
    ('act002', 'ent002', '组织发布', 'PUBLISH', '{"trigger":"on_create","target":"portal"}', 'REALTIME', 'ACTIVE', NOW(), NOW()),
    ('act003', 'ent003', '审批通知', 'NOTIFY', '{"trigger":"on_status_change","target":"wecom"}', 'QUEUE', 'ACTIVE', NOW(), NOW())
) AS t
WHERE NOT EXISTS (SELECT 1 FROM ecos_ontology_action);
