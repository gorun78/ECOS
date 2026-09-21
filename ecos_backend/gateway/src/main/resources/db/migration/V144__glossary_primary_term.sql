-- ============================================================================
-- V144 — Wiki 词条「主术语」标记（T1 本体消费词条）
--
-- 目的：本体实体（ecos_ontology_entity）可绑定多个词条（经
--       ecos_glossary_term.object_type_id），需标记其中一个为「主术语」，
--       供本体模型消费时取「实体 → 首选词条」的稳定映射（primaryTermCode）。
--
-- 设计：
--   ① ecos_glossary_term 加 is_primary BOOLEAN NOT NULL DEFAULT FALSE；
--   ② 部分唯一索引保证「一个实体至多一个主术语」（object_type_id 非空且 is_primary 为真时唯一）；
--   ③ 存量数据零回填（默认全非主），避免擅自把某条既有词条认定为权威口径。
--
-- 幂等：ADD COLUMN IF NOT EXISTS + CREATE UNIQUE INDEX IF NOT EXISTS，可重复执行。
-- ============================================================================

ALTER TABLE public.ecos_glossary_term
    ADD COLUMN IF NOT EXISTS is_primary BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN public.ecos_glossary_term.is_primary
    IS '是否为其所属本体实体(object_type_id)的主术语；每实体至多一条';

-- 同一本体实体至多一个主术语（共享行 tenant_id IS NULL 与租户行同受约束）
CREATE UNIQUE INDEX IF NOT EXISTS uniq_glossary_term_primary
    ON public.ecos_glossary_term(object_type_id)
    WHERE is_primary AND object_type_id IS NOT NULL;
