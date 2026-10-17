-- =============================================================================
-- V290__kb_nav_code_contract_columns.sql
-- 二期 W5 · 知识导航三表「代码读面契约列」补齐（增量·只加不删）
--
-- 追溯: 规划册 docs/30-设计/企业经营管控演示数据集二期-六域联动规划-2026-10-16.md
--       §三 域3 验收项「/api/v1/knowledge/nav/categories 现 code:-1，需先定位是缺行还是缺陷」
--       ⇒ 2026-10-17 实测定位 = **缺列缺陷（非缺行）**，本脚本收口 DDL 面，seed 另文。
--
-- 实证依据（2026-10-17 本机 information_schema 实查 + 源码实读，非推定）：
--   * 库内现形态 = V175__kb_nav_tables_conform.sql（卷04 §E.2 合规形态）：
--       kb_nav_category(id,parent_id,name,description,sort_order,color_token,domain,...)
--       kb_nav_tag(id,category_id,name,description,color_token,domain,...)
--       kb_nav_article_rel(id,article_id,tag_id,category_id,relation_type CHECK('TAG','CATEGORY'),...)
--   * 活代码读/写形态 = kb-engine-impl NavTaxonDao（ecos_knowledge 内联 SQL，实读行号）：
--       :34-36  listCategories  SELECT c.path, c.level + ORDER BY c.level
--       :43-45                  rel JOIN kb_nav_category sub ON sub.id = rel.node_id AND rel.scope='category'
--       :61-63  insertCategory  INSERT (id,domain,parent_id,name,path,level,sort_order,create_by,update_by)
--       :124-147 countArticlesByPath / countDistinctTagsByPath / listTagNamesByPath → r.node_id, r.scope, t.tag_name
--       :205-243 insert/update article rel → (article_id, node_id, scope)
--     ⇒ 代码引用的 path/level/tag_name/node_id/scope 五列在现库不存在 ⇒ 导航面 SQL grammar 失败 ⇒ code:-1。
--   * V155/V156/V157 虽有这五列，但全文为 CREATE TABLE IF NOT EXISTS ⇒ 对同名已存在表恒 no-op，
--     不可能补齐（规划册 §五「优先实跑既有脚本」在本项上不适用，故按号段纪律新建本增量脚本）。
--
-- 纪律：IR03/ST 只加不删（零 DROP、零 ALTER TYPE、零改名）；DR02 不改表名（代码硬编码 ecos_knowledge.kb_nav_*）；
--       新列全部可空或带默认，既有行/既有写路径不受影响。
--
-- ⚠ 双形态债登记（本脚本不解，须专项裁决）：
--   补列后同一张表同时存在两套等价列（V175 的 category_id/tag_id/relation_type ↔ 代码的 node_id/scope，
--   name ↔ tag_name）。本批 seed 两套同写保持一致；长期必须由「改代码切 V175 形态」或「表名单点重建」收口其一。
--
-- 跑法（Flyway 禁用，人工 psql，IR02）：
--   docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 -f - < 本文件
-- =============================================================================

BEGIN;

-- ── 1. kb_nav_category：补代码读的树路径两列 ────────────────────────────
ALTER TABLE ecos_knowledge.kb_nav_category ADD COLUMN IF NOT EXISTS path  VARCHAR(512);
ALTER TABLE ecos_knowledge.kb_nav_category ADD COLUMN IF NOT EXISTS level SMALLINT NOT NULL DEFAULT 1;
COMMENT ON COLUMN ecos_knowledge.kb_nav_category.path  IS '代码契约列（NavTaxonDao 子树 LIKE 查询）：/<域>/<一级>/<本节点名>；与 V175 形态并存，双形态债见脚本头';
COMMENT ON COLUMN ecos_knowledge.kb_nav_category.level IS '代码契约列（NavTaxonDao ORDER BY level）：1/2/3，域下一级=1';

-- ── 2. kb_nav_tag：补代码读的标签名列（与 V175 name 同义）────────────────
ALTER TABLE ecos_knowledge.kb_nav_tag ADD COLUMN IF NOT EXISTS tag_name VARCHAR(128);
COMMENT ON COLUMN ecos_knowledge.kb_nav_tag.tag_name IS '代码契约列（NavTaxonDao SELECT t.tag_name）：与 name 同义，seed/写入两套同值';

-- ── 3. kb_nav_article_rel：补代码读的节点两列（与 category_id/tag_id/relation_type 同义）──
ALTER TABLE ecos_knowledge.kb_nav_article_rel ADD COLUMN IF NOT EXISTS node_id VARCHAR(64);
ALTER TABLE ecos_knowledge.kb_nav_article_rel ADD COLUMN IF NOT EXISTS scope  VARCHAR(16);
COMMENT ON COLUMN ecos_knowledge.kb_nav_article_rel.node_id IS '代码契约列：scope=category→kb_nav_category.id，scope=tag→kb_nav_tag.id；与 category_id/tag_id 同值双写';
COMMENT ON COLUMN ecos_knowledge.kb_nav_article_rel.scope   IS '代码契约列：category|tag（代码内联字面量，不加 CHECK 以容 V175 relation_type 词表并存）';

-- ── 4. 存量行回填（实扫行数：category=0 / tag=0 / rel=0 ⇒ 预期全为 0 行受影响，回填语句为将来存量兜底）──
UPDATE ecos_knowledge.kb_nav_tag SET tag_name = name WHERE tag_name IS NULL AND name IS NOT NULL;
UPDATE ecos_knowledge.kb_nav_article_rel
   SET node_id = COALESCE(category_id, tag_id),
       scope   = CASE WHEN relation_type = 'CATEGORY' THEN 'category'
                       WHEN relation_type = 'TAG'      THEN 'tag'
                       ELSE lower(COALESCE(relation_type, 'tag')) END
 WHERE node_id IS NULL;
UPDATE ecos_knowledge.kb_nav_category
   SET path = '/'||name, level = 1
 WHERE path IS NULL;

-- ── 5. 索引（代码按 node_id+scope、path 前缀 LIKE 查询）─────────────────
CREATE INDEX IF NOT EXISTS idx_kb_nav_rel_node_scope
    ON ecos_knowledge.kb_nav_article_rel(node_id, scope) WHERE is_deleted = 0;
CREATE INDEX IF NOT EXISTS idx_kb_nav_category_path
    ON ecos_knowledge.kb_nav_category(domain, path);

-- ── 6. 对账：五列必须存在 ──────────────────────────────────────────────
SELECT 'category_path'  k, count(*) v FROM information_schema.columns WHERE table_schema='ecos_knowledge' AND table_name='kb_nav_category' AND column_name='path'
UNION ALL SELECT 'category_level', count(*) FROM information_schema.columns WHERE table_schema='ecos_knowledge' AND table_name='kb_nav_category' AND column_name='level'
UNION ALL SELECT 'tag_tag_name',   count(*) FROM information_schema.columns WHERE table_schema='ecos_knowledge' AND table_name='kb_nav_tag' AND column_name='tag_name'
UNION ALL SELECT 'rel_node_id',    count(*) FROM information_schema.columns WHERE table_schema='ecos_knowledge' AND table_name='kb_nav_article_rel' AND column_name='node_id'
UNION ALL SELECT 'rel_scope',      count(*) FROM information_schema.columns WHERE table_schema='ecos_knowledge' AND table_name='kb_nav_article_rel' AND column_name='scope';

COMMIT;
