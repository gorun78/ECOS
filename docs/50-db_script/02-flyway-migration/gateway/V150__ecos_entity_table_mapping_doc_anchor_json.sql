-- V150__ecos_entity_table_mapping_doc_anchor_json.sql
-- W2 修补：列名 doc_anchor → doc_anchor_json，对齐 DR04"JSONB 列带 _json 后缀"
-- 可重入：仅当旧列 doc_anchor 存在时才执行 RENAME（幂等）
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'ecos_entity_table_mapping'
      AND column_name = 'doc_anchor'
  ) THEN
    ALTER TABLE public.ecos_entity_table_mapping RENAME COLUMN doc_anchor TO doc_anchor_json;
  END IF;
END $$;
COMMENT ON COLUMN public.ecos_entity_table_mapping.doc_anchor_json IS '非结构化文档锚点（W2 新增）：JSON {"docId":"...","source":"...","docChunkCount":N}';
COMMENT ON COLUMN public.ecos_entity_table_mapping.doc_anchor_type IS '锚点类型：TABLE(默认)/DOC_ONLY/MIXED，W2 新增';
