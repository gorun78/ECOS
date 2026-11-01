-- =============================================================================
-- V301 — R11 / F-KB-18 知识对象七分类（#147H）
-- 单源目录：docs/50-db_script/02-flyway-migration/gateway/（铁律 v2.2 §3.1）
-- 执行：psql -f（Flyway 永久禁用）
--
-- 变更：ecos_knowledge.knowledge_article 新增两列（additive，不删不改既有列）
--   knowledge_category  七类封闭枚举（Fact/Concept/Procedure/Rule/Case/Evidence/Pattern）
--   rule_ref            Rule 类知识的语义根（ecos_ontology_rule.id；不复制第二份规则内容）
-- 约束：CHECK 闭集允许 NULL（存量无分类数据不被判违例）；Rule 类须带 rule_ref 由应用侧
--       KnowledgeCategoryPolicy 强校验（DB 仅保证值域）。
-- =============================================================================

ALTER TABLE ecos_knowledge.knowledge_article
    ADD COLUMN IF NOT EXISTS knowledge_category VARCHAR(32);

ALTER TABLE ecos_knowledge.knowledge_article
    ADD COLUMN IF NOT EXISTS rule_ref VARCHAR(64);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_knowledge_article_category'
    ) THEN
        ALTER TABLE ecos_knowledge.knowledge_article
            ADD CONSTRAINT ck_knowledge_article_category
            CHECK (knowledge_category IS NULL OR knowledge_category IN
                ('Fact', 'Concept', 'Procedure', 'Rule', 'Case', 'Evidence', 'Pattern'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_knowledge_article_category
    ON ecos_knowledge.knowledge_article (knowledge_category);

CREATE INDEX IF NOT EXISTS idx_knowledge_article_rule_ref
    ON ecos_knowledge.knowledge_article (rule_ref);

COMMENT ON COLUMN ecos_knowledge.knowledge_article.knowledge_category IS
    'R11/F-KB-18 知识对象七分类封闭枚举：Fact/Concept/Procedure/Rule/Case/Evidence/Pattern（受控新增须改 CHECK + 枚举）';
COMMENT ON COLUMN ecos_knowledge.knowledge_article.rule_ref IS
    'Rule 类知识语义根：ecos_ontology_rule.id（rule_carrier=KNOWLEDGE_ASSET）；非 Rule 类必须为空';
