-- ECOS Schema 彻底治理 · Batch B · 2026-10-05
-- Two divergent lineage tables merge · control = 真身 (user-authorized C)
-- 流程: ADD alignment cols to control → INSERT home rows (only shared cols) → DROP home
-- PRECHECK elbowed: home ids unique · home/control id overlap=0

\set ON_ERROR_STOP on
BEGIN;

-- ===== 1. lineage_node: ADD home-only cols to control side (only add, no drop/rename) =====
ALTER TABLE ecos_control.ecos_data_lineage_node
  ADD COLUMN IF NOT EXISTS name             character varying(200),
  ADD COLUMN IF NOT EXISTS schema_name      character varying(100),
  ADD COLUMN IF NOT EXISTS table_name       character varying(200),
  ADD COLUMN IF NOT EXISTS datasource_id    character varying(64),
  ADD COLUMN IF NOT EXISTS layer            character varying(20),
  ADD COLUMN IF NOT EXISTS properties       jsonb,
  ADD COLUMN IF NOT EXISTS updated_at       timestamp,
  ADD COLUMN IF NOT EXISTS pipeline_task_id character varying(64);

-- 5 columns in control but NOT in home (label, entity_code, description) remain untouched (control-only data)

-- ===== 2. lineage_edge: ADD home-only cols to control side =====
ALTER TABLE ecos_control.ecos_data_lineage_edge
  ADD COLUMN IF NOT EXISTS pipeline_task_id character varying(64),
  ADD COLUMN IF NOT EXISTS transformation   character varying(500),
  ADD COLUMN IF NOT EXISTS properties       jsonb;

-- `description` and `transform_rule` are control-only (not in home); kept untouched.

-- ===== 3. Merge home rows INTO control =====
-- mapping: control-side `label` (NOT NULL, human-readable) ← home-side `name` (same semantic role)
INSERT INTO ecos_control.ecos_data_lineage_node
  (id, label, node_type,
   name, schema_name, table_name, datasource_id, layer,
   properties, created_at, updated_at, pipeline_task_id)
SELECT h.id, h.name, h.node_type,
       h.name, h.schema_name, h.table_name, h.datasource_id, h.layer,
       h.properties, h.created_at, h.updated_at, h.pipeline_task_id
FROM ecos_data.ecos_data_lineage_node h
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_data_lineage_node c WHERE c.id = h.id);

INSERT INTO ecos_control.ecos_data_lineage_edge
  (id, source_node_id, target_node_id, edge_type,
   pipeline_task_id, transformation, properties, created_at)
SELECT h.id, h.source_node_id, h.target_node_id, h.edge_type,
       h.pipeline_task_id, h.transformation, h.properties, h.created_at
FROM ecos_data.ecos_data_lineage_edge h
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_data_lineage_edge c WHERE c.id = h.id);

-- ===== 4. counts post-merge: node=20, edge=9 =====
DO $$
DECLARE n_node BIGINT; n_edge BIGINT;
BEGIN
  SELECT count(*) INTO n_node FROM ecos_control.ecos_data_lineage_node;
  SELECT count(*) INTO n_edge FROM ecos_control.ecos_data_lineage_edge;
  IF n_node <> 20 THEN RAISE EXCEPTION 'node count: expected 20, got %', n_node; END IF;
  IF n_edge <> 9  THEN RAISE EXCEPTION 'edge count: expected 9, got %', n_edge; END IF;
  RAISE NOTICE 'MERGE COUNTS PASS: node=20 edge=9';
END $$;

-- ===== 5. DROP home =====
DROP TABLE ecos_data.ecos_data_lineage_edge;
DROP TABLE ecos_data.ecos_data_lineage_node;

-- ===== 6. final object count assertion: 423 - 2 = 421 =====
DO $$
DECLARE n BIGINT;
BEGIN
  SELECT count(*) INTO n FROM pg_class c JOIN pg_namespace ns ON ns.oid=c.relnamespace
   WHERE c.relkind IN ('r','v','p') AND ns.nspname NOT IN ('pg_catalog','information_schema');
  IF n <> 421 THEN RAISE EXCEPTION 'FINAL object count: expected 421, got %', n; END IF;
  RAISE NOTICE 'FINAL ASSET PASS: 421 objects retained';
END $$;

COMMIT;
