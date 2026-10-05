-- ============================================================
-- V11__ecos_marketplace_ontology.sql — 资产-本体关联字段
-- ============================================================

ALTER TABLE ecos_marketplace_asset
    ADD COLUMN IF NOT EXISTS ontology_entity_id VARCHAR(128) DEFAULT NULL;

COMMENT ON COLUMN ecos_marketplace_asset.ontology_entity_id IS '关联的本体实体ID';

CREATE INDEX IF NOT EXISTS idx_marketplace_asset_ontology
    ON ecos_marketplace_asset(ontology_entity_id);
