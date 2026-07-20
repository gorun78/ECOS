ALTER TABLE td_data_resource ADD COLUMN IF NOT EXISTS layer VARCHAR(16) DEFAULT 'RAW';
COMMENT ON COLUMN td_data_resource.layer IS 'Data layer: SOURCE/RAW/CURATED/SEMANTIC/APPLICATION';

-- Backfill existing data based on resource_type
UPDATE td_data_resource SET layer = 'SOURCE' WHERE resource_type = 'TABLE' AND source_path LIKE '%_source';
UPDATE td_data_resource SET layer = 'RAW' WHERE layer = 'RAW' AND resource_type IN ('TABLE','VIEW');
UPDATE td_data_resource SET layer = 'CURATED' WHERE resource_type = 'VIEW' AND source_path LIKE '%_curated';
UPDATE td_data_resource SET layer = 'APPLICATION' WHERE resource_type = 'MATERIALIZED_VIEW';
