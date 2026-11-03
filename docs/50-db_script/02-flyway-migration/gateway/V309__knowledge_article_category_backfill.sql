-- V309 (#147U 数据面：12 行知识资产回填七分类)
--
-- 背景：V301 只给 `ecos_knowledge.knowledge_article` 加了 `knowledge_category`/`rule_ref`
-- 两列，**14 行存量（本批实查 12 行在架）全部为空** ⇒ 七分类筛选任何分类都返回空集，
-- 筛选面形同摆设。本脚本按裁决「请按建议归类」逐行回填。
--
-- 归类口径（人工判定，非脚本猜值；可复核）：
--   * 「办法 / 规范 / 制度 / 手册 / 指引 / 流程」⇒ **Procedure**（可执行步骤与作业规程）
--   * 「口径 / 说明 / 对照表」⇒ **Concept**（定义、术语与映射关系）
--   * **不标 Rule**：七分类要求 Rule 必带 `rule_ref` 指向 `ecos_ontology_rule` 语义根，
--     而这 12 行正文与元数据均未绑定具体规则行；为守「Rule 不复制第二份规则内容」，
--     全部按 Concept/Procedure 归类，待业务完成规则绑定后再单独改判（禁凭空写 rule_ref）。
--   * 本批无 Fact / Case / Evidence / Pattern 形态的条目（无单一事实陈述、无复盘案例、
--     无审计留痕、无可复用范式）——**不为了"七类都有"而虚构分类**。
--
-- 纪律：只改 `knowledge_category`（可空列），不动正文/标题/domain；幂等（`IS NULL` 守卫，
--   不覆盖此后的人工改判）；IR02 手动 psql -f，Flyway 永久禁用。

-- Procedure（办法/规范/制度/手册/指引）——7 行
UPDATE ecos_knowledge.knowledge_article SET knowledge_category = 'Procedure'
 WHERE id = '04ae1d2b3aeda85e70264762548a518d' AND knowledge_category IS NULL; -- 近源层装载规范
UPDATE ecos_knowledge.knowledge_article SET knowledge_category = 'Procedure'
 WHERE id = '052d250f96b789d6a853555eb408a940' AND knowledge_category IS NULL; -- 主数据管理办法
UPDATE ecos_knowledge.knowledge_article SET knowledge_category = 'Procedure'
 WHERE id = '40dbdb9ff4c6825ac055a559f16f6f12' AND knowledge_category IS NULL; -- 数据质量门禁手册
UPDATE ecos_knowledge.knowledge_article SET knowledge_category = 'Procedure'
 WHERE id = '61dfa0f90014b5a4e57a0cada9d2a40c' AND knowledge_category IS NULL; -- 数据资产上架流程
UPDATE ecos_knowledge.knowledge_article SET knowledge_category = 'Procedure'
 WHERE id = '9794ba4a41a61ae001b8766b163b7a75' AND knowledge_category IS NULL; -- 期间关账管理制度
UPDATE ecos_knowledge.knowledge_article SET knowledge_category = 'Procedure'
 WHERE id = '99c138eac26a01f9c6d06ae832433d78' AND knowledge_category IS NULL; -- 经营预测作业指引
UPDATE ecos_knowledge.knowledge_article SET knowledge_category = 'Procedure'
 WHERE id = 'a99a05ab45031b15471d9ab4e4ed72b7' AND knowledge_category IS NULL; -- 权责发生制产值确认办法

-- Concept（口径/说明/对照表）——5 行
UPDATE ecos_knowledge.knowledge_article SET knowledge_category = 'Concept'
 WHERE id = '1cfec75b3f79367c47cf40aa67436d6b' AND knowledge_category IS NULL; -- 收付实现制回款归属细则（口径）
UPDATE ecos_knowledge.knowledge_article SET knowledge_category = 'Concept'
 WHERE id = '561ab8292260753b29b2e967e20e2ae6' AND knowledge_category IS NULL; -- 归属分摊规则说明（口径）
UPDATE ecos_knowledge.knowledge_article SET knowledge_category = 'Concept'
 WHERE id = '7d1c916df0095522d32295785db076d1' AND knowledge_category IS NULL; -- 本体与数据映射对照表
UPDATE ecos_knowledge.knowledge_article SET knowledge_category = 'Concept'
 WHERE id = 'cf94feccd199e5bc1e2b9aaf66eec662' AND knowledge_category IS NULL; -- 经营毛利口径说明
UPDATE ecos_knowledge.knowledge_article SET knowledge_category = 'Concept'
 WHERE id = 'd7c8406072fee7a9ced5a3e93240016e' AND knowledge_category IS NULL; -- 人力投入与费率规则（口径）
