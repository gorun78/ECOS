-- ============================================================
-- V9: ECOS Object Runtime 全链路 — P0 核心表
-- ============================================================

-- 4.1.1 状态机定义表
CREATE TABLE IF NOT EXISTS ecos_object_state_machine (
    id              VARCHAR(50) PRIMARY KEY,
    entity_code     VARCHAR(100) NOT NULL,
    from_status     VARCHAR(50) NOT NULL,
    to_status       VARCHAR(50) NOT NULL,
    transition_code VARCHAR(100) NOT NULL,
    transition_name VARCHAR(200),
    require_role    VARCHAR(200),
    guard_rule      TEXT,
    side_effect     TEXT,
    sort_order      INT DEFAULT 0,
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(entity_code, from_status, transition_code)
);

-- 4.1.2 结构化时间线表
CREATE TABLE IF NOT EXISTS ecos_object_timeline (
    id              VARCHAR(50) PRIMARY KEY,
    object_id       VARCHAR(100) NOT NULL,
    entity_code     VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    event_summary   VARCHAR(500),
    actor           VARCHAR(100),
    details         JSONB,
    created_at      TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_timeline_object ON ecos_object_timeline(object_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_timeline_entity ON ecos_object_timeline(entity_code, created_at DESC);

-- 4.1.3 对象版本表
CREATE TABLE IF NOT EXISTS ecos_object_version (
    id              VARCHAR(50) PRIMARY KEY,
    object_id       VARCHAR(100) NOT NULL,
    entity_code     VARCHAR(100) NOT NULL,
    version_no      INT NOT NULL,
    snapshot        JSONB NOT NULL,
    change_summary  VARCHAR(500),
    created_by      VARCHAR(100),
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(object_id, version_no)
);

-- 4.1.4 对象实例关系表
CREATE TABLE IF NOT EXISTS ecos_object_relationship (
    id                  VARCHAR(50) PRIMARY KEY,
    source_object_id    VARCHAR(100) NOT NULL,
    target_object_id    VARCHAR(100) NOT NULL,
    source_entity_code  VARCHAR(100) NOT NULL,
    target_entity_code  VARCHAR(100) NOT NULL,
    relationship_code   VARCHAR(100) NOT NULL,
    relationship_type   VARCHAR(50) NOT NULL,
    properties          JSONB,
    created_at          TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_obj_rel_source ON ecos_object_relationship(source_object_id);
CREATE INDEX IF NOT EXISTS idx_obj_rel_target ON ecos_object_relationship(target_object_id);

-- 4.1.5 附件表
CREATE TABLE IF NOT EXISTS ecos_object_attachment (
    id              VARCHAR(50) PRIMARY KEY,
    object_id       VARCHAR(100) NOT NULL,
    entity_code     VARCHAR(100) NOT NULL,
    file_name       VARCHAR(500) NOT NULL,
    file_path       VARCHAR(1000) NOT NULL,
    file_size       BIGINT,
    mime_type       VARCHAR(200),
    version_no      INT DEFAULT 1,
    uploaded_by     VARCHAR(100),
    created_at      TIMESTAMP DEFAULT NOW()
);

-- ============================================================
-- 种子数据: 默认状态机转换 (适用于 Customer/Supplier/Invoice)
-- ============================================================
INSERT INTO ecos_object_state_machine (id, entity_code, from_status, to_status, transition_code, transition_name, require_role, sort_order) VALUES
('sm-001', 'Customer', 'Draft',   'Active',   'activate',   '激活',   NULL, 1),
('sm-002', 'Customer', 'Active',  'Archived', 'archive',    '归档',   'Admin', 2),
('sm-003', 'Customer', 'Active',  'Suspended','suspend',    '暂停',   'Admin', 3),
('sm-004', 'Customer', 'Suspended','Active',  'resume',     '恢复',   'Admin', 4),
('sm-005', 'Customer', 'Draft',   'Archived', 'quick_archive','快速归档','Admin', 5),
('sm-006', 'Supplier', 'Draft',   'Active',   'activate',   '激活',   NULL, 1),
('sm-007', 'Supplier', 'Active',  'Archived', 'archive',    '归档',   'Admin', 2),
('sm-008', 'Supplier', 'Active',  'Suspended','suspend',    '暂停',   'Admin', 3),
('sm-009', 'Supplier', 'Suspended','Active',  'resume',     '恢复',   'Admin', 4),
('sm-010', 'Supplier', 'Draft',   'Archived', 'quick_archive','快速归档','Admin', 5),
('sm-011', 'Invoice',  'Draft',   'Active',   'activate',   '激活',   NULL, 1),
('sm-012', 'Invoice',  'Active',  'Archived', 'archive',    '归档',   'Admin', 2),
('sm-013', 'Invoice',  'Draft',   'Archived', 'quick_archive','快速归档','Admin', 5)
ON CONFLICT (entity_code, from_status, transition_code) DO NOTHING;
