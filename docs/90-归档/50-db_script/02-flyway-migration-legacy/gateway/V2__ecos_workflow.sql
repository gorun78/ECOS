-- ============================================================
-- V2__ecos_workflow.sql — 工作流持久化表
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_workflow (
    id              VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     TEXT DEFAULT '',
    status          VARCHAR(32) DEFAULT 'draft',
    mode            VARCHAR(32) DEFAULT 'sequential',
    nodes           TEXT DEFAULT '[]',
    edges           TEXT DEFAULT '[]',
    published_at    TIMESTAMP NULL,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- Seed data (only insert if empty)
INSERT INTO ecos_workflow (id, name, description, status, mode, nodes, edges, created_at, updated_at)
SELECT * FROM (VALUES
    ('wf001', '供应商准入审批', '供应商资质审查与准入管理流程', 'active', 'sequential',
     '[{"id":"t1","name":"资质提交","type":"start"},{"id":"t2","name":"初审","type":"task"},{"id":"t3","name":"终审","type":"task"},{"id":"t4","name":"准入完成","type":"end"}]',
     '[{"id":"e1","source":"t1","target":"t2"},{"id":"e2","source":"t2","target":"t3"},{"id":"e3","source":"t3","target":"t4"}]',
     NOW(), NOW()),
    ('wf002', '数据质量巡检', '定期数据质量检查与问题修复', 'draft', 'parallel',
     '[{"id":"t1","name":"开始巡检","type":"start"},{"id":"t2","name":"完整性检查","type":"task"},{"id":"t3","name":"一致性检查","type":"task"},{"id":"t4","name":"生成报告","type":"end"}]',
     '[{"id":"e1","source":"t1","target":"t2"},{"id":"e2","source":"t1","target":"t3"},{"id":"e3","source":"t2","target":"t4"},{"id":"e4","source":"t3","target":"t4"}]',
     NOW(), NOW()),
    ('wf003', '智能客服问答', 'AI驱动的客户咨询与解答流程', 'active', 'sequential',
     '[{"id":"t1","name":"接收问题","type":"start"},{"id":"t2","name":"意图识别","type":"ai"},{"id":"t3","name":"知识检索","type":"ai"},{"id":"t4","name":"生成回复","type":"ai"},{"id":"t5","name":"人工兜底","type":"task"},{"id":"t6","name":"结束","type":"end"}]',
     '[{"id":"e1","source":"t1","target":"t2"},{"id":"e2","source":"t2","target":"t3"},{"id":"e3","source":"t3","target":"t4"},{"id":"e4","source":"t4","target":"t6"},{"id":"e5","source":"t2","target":"t5"},{"id":"e6","source":"t5","target":"t6"}]',
     NOW(), NOW())
) AS t
WHERE NOT EXISTS (SELECT 1 FROM ecos_workflow);
