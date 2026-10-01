-- V132: 本体属性表补齐「唯一/主键标识」与「描述」列
--
-- 背景（本体工作台功能审查）：
--   1) `OntologyProperty.uniqueFlag` 在 model 中已存在，但物理表无 `unique_flag` 列，
--      且 OntologyRepository.insertProperty / updateProperty 均未写入 —— 属性无法持久化主键标识；
--   2) 属性描述（description）在属性编辑表单中可编辑，但物理表无对应列，保存即丢失。
--
-- 影响范围：public.ecos_ontology_property（canonical 表，V122 收敛后的唯一权威源）
-- 迁移方式：Flyway 已禁用（spring.flyway.enabled: false），本脚本手工执行。
-- 兼容性：仅增列 + 回填默认值，不改结构、不删列（遵守「schema 只加不删」）。

ALTER TABLE public.ecos_ontology_property
    ADD COLUMN IF NOT EXISTS unique_flag integer DEFAULT 0;

ALTER TABLE public.ecos_ontology_property
    ADD COLUMN IF NOT EXISTS description character varying(500);

COMMENT ON COLUMN public.ecos_ontology_property.unique_flag IS '唯一/主键标识：1=主键(唯一)，0=普通属性';
COMMENT ON COLUMN public.ecos_ontology_property.description IS '属性描述（本体工作台属性编辑器）';

-- 回填 NULL 至 0，避免下游 getInt 语义歧义
UPDATE public.ecos_ontology_property SET unique_flag = 0 WHERE unique_flag IS NULL;