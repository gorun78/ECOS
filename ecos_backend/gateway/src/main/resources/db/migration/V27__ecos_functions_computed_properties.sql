-- ============================================================
-- V27__ecos_functions_computed_properties.sql — 计算属性 (Functions)
-- ============================================================
-- 为 ecos_ontology_property 添加函数计算能力:
--   function_type       — EXPRESSION / AGGREGATION / LOOKUP
--   function_expression — 表达式文本

ALTER TABLE ecos_ontology_property ADD COLUMN IF NOT EXISTS function_type VARCHAR(32);
ALTER TABLE ecos_ontology_property ADD COLUMN IF NOT EXISTS function_expression TEXT;

COMMENT ON COLUMN ecos_ontology_property.function_type       IS '计算属性类型: EXPRESSION(同实体运算) / AGGREGATION(跨link聚合) / LOOKUP(简单link取值)';
COMMENT ON COLUMN ecos_ontology_property.function_expression IS '计算表达式文本';
