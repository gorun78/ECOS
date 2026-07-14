-- ============================================================
-- V12__ecos_sys_dict.sql — 系统数据字典表
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_dict (
    id           VARCHAR(64)  PRIMARY KEY,
    dict_type    VARCHAR(64)  NOT NULL,
    dict_code    VARCHAR(128) NOT NULL,
    dict_label   VARCHAR(255) NOT NULL,
    dict_label_en VARCHAR(255),
    sort_order   INT          DEFAULT 0,
    status       VARCHAR(32)  DEFAULT 'active',
    parent_code  VARCHAR(128),
    ext_value    VARCHAR(255),
    created_at   TIMESTAMP    DEFAULT NOW(),
    updated_at   TIMESTAMP    DEFAULT NOW(),
    UNIQUE(dict_type, dict_code)
);

-- ============================================================
-- Seed data: asset_status
-- ============================================================
INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order) VALUES
('dict-asset-status-draft',     'asset_status', 'draft',     '草稿',   'Draft',     1),
('dict-asset-status-published', 'asset_status', 'published', '已发布', 'Published',  2),
('dict-asset-status-archived',  'asset_status', 'archived',  '已归档', 'Archived',   3)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- ============================================================
-- Seed data: glossary_status
-- ============================================================
INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order) VALUES
('dict-glossary-status-draft',     'glossary_status', 'DRAFT',     '草稿',   'Draft',     1),
('dict-glossary-status-review',    'glossary_status', 'REVIEW',    '审核中', 'In Review',  2),
('dict-glossary-status-published', 'glossary_status', 'PUBLISHED', '已发布', 'Published',  3),
('dict-glossary-status-archived',  'glossary_status', 'ARCHIVED',  '已归档', 'Archived',   4)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- ============================================================
-- Seed data: contract_status
-- ============================================================
INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order) VALUES
('dict-contract-status-draft',     'contract_status', 'draft',      '草稿',   'Draft',      1),
('dict-contract-status-active',    'contract_status', 'active',     '生效中', 'Active',      2),
('dict-contract-status-completed', 'contract_status', 'completed',  '已完成', 'Completed',   3),
('dict-contract-status-terminated','contract_status', 'terminated', '已终止', 'Terminated',  4),
('dict-contract-status-expired',   'contract_status', 'expired',    '已过期', 'Expired',     5)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- ============================================================
-- Seed data: project_status
-- ============================================================
INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order) VALUES
('dict-project-status-planned',    'project_status', 'planned',     '计划',   'Planned',     1),
('dict-project-status-inprogress', 'project_status', 'in_progress', '进行中', 'In Progress', 2),
('dict-project-status-completed',  'project_status', 'completed',   '已完成', 'Completed',   3),
('dict-project-status-paused',     'project_status', 'paused',      '暂停',   'Paused',      4),
('dict-project-status-cancelled',  'project_status', 'cancelled',   '已取消', 'Cancelled',   5)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- ============================================================
-- Seed data: object_status
-- ============================================================
INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order) VALUES
('dict-object-status-draft',    'object_status', 'draft',    '草稿', 'Draft',    1),
('dict-object-status-active',   'object_status', 'active',   '激活', 'Active',   2),
('dict-object-status-archived', 'object_status', 'archived', '归档', 'Archived', 3)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- ============================================================
-- Seed data: marketplace_category
-- ============================================================
INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order) VALUES
('dict-mp-cat-dataset',       'marketplace_category', 'dataset',       '数据集',   'Dataset',       1),
('dict-mp-cat-dataservice',   'marketplace_category', 'data_service',  '数据服务', 'Data Service',  2),
('dict-mp-cat-aimodel',       'marketplace_category', 'ai_model',      'AI模型',  'AI Model',      3),
('dict-mp-cat-api',           'marketplace_category', 'api',           'API',     'API',           4),
('dict-mp-cat-report',        'marketplace_category', 'report',        '报告',    'Report',        5),
('dict-mp-cat-knowledgebase', 'marketplace_category', 'knowledge_base','知识库',  'Knowledge Base',6)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- ============================================================
-- Seed data: data_source_type
-- ============================================================
INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order) VALUES
('dict-ds-type-mysql',      'data_source_type', 'mysql',      'MySQL',      'MySQL',      1),
('dict-ds-type-postgresql', 'data_source_type', 'postgresql', 'PostgreSQL', 'PostgreSQL', 2),
('dict-ds-type-oracle',     'data_source_type', 'oracle',     'Oracle',     'Oracle',     3),
('dict-ds-type-api',        'data_source_type', 'api',        'API',        'API',        4),
('dict-ds-type-file',       'data_source_type', 'file',       '文件',       'File',       5),
('dict-ds-type-stream',     'data_source_type', 'stream',     '流数据',     'Stream',     6)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- ============================================================
-- Seed data: dq_rule_type
-- ============================================================
INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order) VALUES
('dict-dq-type-completeness', 'dq_rule_type', 'completeness', '完整性', 'Completeness', 1),
('dict-dq-type-accuracy',     'dq_rule_type', 'accuracy',     '准确性', 'Accuracy',     2),
('dict-dq-type-uniqueness',   'dq_rule_type', 'uniqueness',   '唯一性', 'Uniqueness',   3),
('dict-dq-type-consistency',  'dq_rule_type', 'consistency',  '一致性', 'Consistency',  4),
('dict-dq-type-timeliness',   'dq_rule_type', 'timeliness',   '时效性', 'Timeliness',   5)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- ============================================================
-- Seed data: clearance_level
-- ============================================================
INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order, ext_value) VALUES
('dict-cl-1', 'clearance_level', '1', '公开',     'Public',            1, '#52c41a'),
('dict-cl-2', 'clearance_level', '2', '内部',     'Internal',          2, '#1890ff'),
('dict-cl-3', 'clearance_level', '3', '机密',     'Confidential',      3, '#faad14'),
('dict-cl-4', 'clearance_level', '4', '绝密',     'Top Secret',        4, '#ff7a45'),
('dict-cl-5', 'clearance_level', '5', '最高机密', 'Highest Secret',    5, '#f5222d')
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- ============================================================
-- Seed data: audit_mode
-- ============================================================
INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order) VALUES
('dict-audit-mode-basic',        'audit_mode', 'basic',        '基础', 'Basic',         1),
('dict-audit-mode-detailed',     'audit_mode', 'detailed',     '详细', 'Detailed',      2),
('dict-audit-mode-comprehensive','audit_mode', 'comprehensive','全面', 'Comprehensive', 3)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- ============================================================
-- Seed data: agent_type
-- ============================================================
INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order) VALUES
('dict-agent-type-llm',       'agent_type', 'llm',       '大语言模型', 'LLM',       1),
('dict-agent-type-retrieval', 'agent_type', 'retrieval', '检索',      'Retrieval', 2),
('dict-agent-type-tool',      'agent_type', 'tool',      '工具',      'Tool',      3),
('dict-agent-type-workflow',  'agent_type', 'workflow',  '工作流',    'Workflow',  4)
ON CONFLICT (dict_type, dict_code) DO NOTHING;
