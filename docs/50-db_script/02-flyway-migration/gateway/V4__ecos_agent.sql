-- ============================================================
-- V4__ecos_agent.sql — Agent 配置持久化表
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_agent (
    id              VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    model_provider  VARCHAR(64) DEFAULT 'deepseek',
    model_name      VARCHAR(128) DEFAULT 'deepseek-v4-flash',
    system_prompt   TEXT DEFAULT '',
    tools           TEXT DEFAULT '[]',   -- JSON array: ["search_knowledge", "query_object"]
    knowledge       TEXT DEFAULT '[]',   -- JSON array: ["kb-supplier"]
    status          VARCHAR(32) DEFAULT 'draft',
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- Seed data (only insert if empty)
INSERT INTO ecos_agent (id, name, model_provider, model_name, system_prompt, tools, knowledge, status, created_at, updated_at)
SELECT * FROM (VALUES
    ('agent-001', '供应商分析助手', 'deepseek', 'deepseek-v4-flash',
     '你是一个供应商分析专家，负责分析供应商资信状况、履约能力和历史表现。请基于知识库中的信息给出客观评估。',
     '["search_knowledge", "query_object"]', '["kb-supplier"]', 'published', NOW(), NOW()),
    ('agent-002', '数据质量巡检员', 'deepseek', 'deepseek-v4-flash',
     '你是一个数据质量分析师，负责定期检查数据完整性、一致性和准确性。发现异常时生成质量问题报告。',
     '["query_object", "execute_sql"]', '[]', 'draft', NOW(), NOW()),
    ('agent-003', '合规审查助手', 'deepseek', 'deepseek-v4-flash',
     '你是一个合规审查专家，负责审核业务操作是否符合法律法规和内部制度要求。请引用具体条款。',
     '["search_knowledge", "query_object"]', '["kb-compliance", "kb-regulation"]', 'published', NOW(), NOW())
) AS t
WHERE NOT EXISTS (SELECT 1 FROM ecos_agent);
