-- D15：ecos_glossary_term 双副本列漂移导致术语列表恒 500
-- 现象：GET /api/v1/ontology/glossary/terms → HTTP 500 / ECOS-DATA-031
--       日志 BadSqlGrammarException，SQL 选 18 列（含 term_type/aliases/…/is_primary）
-- 根因：同名表存在两份副本——
--       public.ecos_glossary_term     27 列（V143/V144 语义列已补）
--       ecos_control.ecos_glossary_term 13 列（V143/V144 只改了 public，未覆盖此副本）
--       运行期 TenantAwareJdbcTemplate 的 search_path 解析到 ecos_control 副本 ⇒ 6 个语义列缺失
-- 处置：把 V143/V144 的语义列等价补齐到 ecos_control 副本（只加不删，幂等）。
-- 注：两副本并存属 ST07 schema 收敛遗留，本次不做去重（去重须单独立项 + 数据迁移）。

ALTER TABLE ecos_control.ecos_glossary_term
    ADD COLUMN IF NOT EXISTS term_type      VARCHAR(32) DEFAULT 'CONCEPT';

ALTER TABLE ecos_control.ecos_glossary_term
    ADD COLUMN IF NOT EXISTS aliases        TEXT[] DEFAULT '{}';

ALTER TABLE ecos_control.ecos_glossary_term
    ADD COLUMN IF NOT EXISTS object_type_id VARCHAR(64);

ALTER TABLE ecos_control.ecos_glossary_term
    ADD COLUMN IF NOT EXISTS parent_term_id BIGINT;

ALTER TABLE ecos_control.ecos_glossary_term
    ADD COLUMN IF NOT EXISTS version        INTEGER DEFAULT 1;

ALTER TABLE ecos_control.ecos_glossary_term
    ADD COLUMN IF NOT EXISTS examples       TEXT[] DEFAULT '{}';

ALTER TABLE ecos_control.ecos_glossary_term
    ADD COLUMN IF NOT EXISTS tags           TEXT[] DEFAULT '{}';

ALTER TABLE ecos_control.ecos_glossary_term
    ADD COLUMN IF NOT EXISTS is_primary     BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ecos_control.ecos_glossary_term.term_type IS
  '词条分类 ENTITY/RELATION/METRIC/FUNCTION/CONCEPT（与 public 副本同义，D15 补齐）';
COMMENT ON COLUMN ecos_control.ecos_glossary_term.is_primary IS
  '是否实体主术语（与 public 副本同义，D15 补齐）';
