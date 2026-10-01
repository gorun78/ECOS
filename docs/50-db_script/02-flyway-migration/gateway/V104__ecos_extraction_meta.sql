-- V104__ecos_extraction_meta.sql (PMO-34)
-- extraction_drafts 表加文档解析元数据列（只加不删）

ALTER TABLE extraction_drafts ADD COLUMN IF NOT EXISTS file_type VARCHAR(16);
ALTER TABLE extraction_drafts ADD COLUMN IF NOT EXISTS page_count INTEGER DEFAULT 1;
ALTER TABLE extraction_drafts ADD COLUMN IF NOT EXISTS char_count INTEGER DEFAULT 0;
