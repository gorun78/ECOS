-- V145__ecos_entity_table_mapping_doc_anchor.sql
-- W2：非结构化文档锚点 —— 本体实体可锚定到数据湖 doc/doc_chunk 来源
ALTER TABLE public.ecos_entity_table_mapping ADD COLUMN IF NOT EXISTS doc_anchor JSONB;
ALTER TABLE public.ecos_entity_table_mapping ADD COLUMN IF NOT EXISTS doc_anchor_type VARCHAR(32);
COMMENT ON COLUMN public.ecos_entity_table_mapping.doc_anchor IS '非结构化文档锚点（W2 新增）：JSON {"docId":"...","source":"...","docChunkCount":N}';
COMMENT ON COLUMN public.ecos_entity_table_mapping.doc_anchor_type IS '锚点类型：TABLE(默认)/DOC_ONLY/MIXED，W2 新增';
