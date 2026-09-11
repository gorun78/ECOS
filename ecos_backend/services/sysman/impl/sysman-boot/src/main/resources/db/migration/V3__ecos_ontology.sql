-- ============================================================
-- V3__ecos_ontology.sql — 本体持久化表
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_ontology_entity (
    id              VARCHAR(64) PRIMARY KEY,
    ontology_id     VARCHAR(64) NOT NULL,
    code            VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT DEFAULT '',
    entity_type     VARCHAR(64) DEFAULT 'MASTER',
    sort_order      INTEGER DEFAULT 1,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ontology_property (
    id              VARCHAR(64) PRIMARY KEY,
    entity_id       VARCHAR(64) NOT NULL,
    code            VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    property_type   VARCHAR(64) DEFAULT 'STRING',
    required_flag   INTEGER DEFAULT 0,
    searchable_flag INTEGER DEFAULT 0,
    sort_order      INTEGER DEFAULT 1,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_ontology_relationship (
    id                  VARCHAR(64) PRIMARY KEY,
    source_entity_id    VARCHAR(64) NOT NULL,
    target_entity_id    VARCHAR(64) NOT NULL,
    code                VARCHAR(255) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    relationship_type   VARCHAR(64) DEFAULT 'ONE_TO_MANY',
    created_at          TIMESTAMP DEFAULT NOW()
);

-- Seed entity data
INSERT INTO ecos_ontology_entity (id, ontology_id, code, name, description, entity_type, sort_order, created_at, updated_at)
SELECT * FROM (VALUES
    ('ent001', 'ont001', 'ENT_USER', '用户', '系统用户主数据实体', 'MASTER', 1, NOW(), NOW()),
    ('ent002', 'ont001', 'ENT_ORG', '组织机构', '组织架构与部门主数据', 'MASTER', 1, NOW(), NOW()),
    ('ent003', 'ont001', 'ENT_CASE', '审批案例', '行政审批事项记录', 'TRANSACTION', 1, NOW(), NOW())
) AS t
WHERE NOT EXISTS (SELECT 1 FROM ecos_ontology_entity);

-- Seed property data
INSERT INTO ecos_ontology_property (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order, created_at, updated_at)
SELECT * FROM (VALUES
    ('prop001', 'ent001', 'user_name', '用户名', 'STRING', 1, 1, 1, NOW(), NOW()),
    ('prop002', 'ent001', 'user_email', '邮箱', 'STRING', 0, 1, 1, NOW(), NOW()),
    ('prop003', 'ent001', 'user_phone', '手机号', 'STRING', 0, 0, 1, NOW(), NOW()),
    ('prop004', 'ent002', 'org_code', '机构编码', 'STRING', 1, 1, 1, NOW(), NOW()),
    ('prop005', 'ent002', 'org_name', '机构名称', 'STRING', 1, 1, 1, NOW(), NOW()),
    ('prop006', 'ent003', 'case_no', '案件编号', 'STRING', 1, 1, 1, NOW(), NOW()),
    ('prop007', 'ent003', 'case_status', '案件状态', 'STRING', 1, 0, 1, NOW(), NOW())
) AS t
WHERE NOT EXISTS (SELECT 1 FROM ecos_ontology_property);

-- Seed relationship data
INSERT INTO ecos_ontology_relationship (id, source_entity_id, target_entity_id, code, name, relationship_type, created_at)
SELECT * FROM (VALUES
    ('rel001', 'ent003', 'ent001', 'BELONGS_TO', '所属用户', 'MANY_TO_ONE', NOW()),
    ('rel002', 'ent002', 'ent001', 'MANAGES', '管理用户', 'ONE_TO_MANY', NOW())
) AS t
WHERE NOT EXISTS (SELECT 1 FROM ecos_ontology_relationship);
