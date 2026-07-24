-- V58: Data Workbench Refine — lineage tables, query tables, sync support, category table
-- Schema: ecos_data (per V47 schema isolation)

-- 1. Data Lineage Node
CREATE TABLE IF NOT EXISTS ecos_data.ecos_data_lineage_node (
    id VARCHAR(64) PRIMARY KEY,
    node_type VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    schema_name VARCHAR(100),
    table_name VARCHAR(200),
    datasource_id VARCHAR(64),
    layer VARCHAR(20),
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ecos_data.ecos_data_lineage_node IS 'Data lineage node — represents a data source, target, or transform point';
COMMENT ON COLUMN ecos_data.ecos_data_lineage_node.node_type IS 'SOURCE / TARGET / TRANSFORM';
COMMENT ON COLUMN ecos_data.ecos_data_lineage_node.layer IS 'SOURCE / RAW / CURATED / SEMANTIC / APPLICATION';

CREATE INDEX IF NOT EXISTS idx_lineage_node_type ON ecos_data.ecos_data_lineage_node(node_type);
CREATE INDEX IF NOT EXISTS idx_lineage_node_ds ON ecos_data.ecos_data_lineage_node(datasource_id);

-- 2. Data Lineage Edge
CREATE TABLE IF NOT EXISTS ecos_data.ecos_data_lineage_edge (
    id VARCHAR(64) PRIMARY KEY,
    source_node_id VARCHAR(64) NOT NULL,
    target_node_id VARCHAR(64) NOT NULL,
    edge_type VARCHAR(30) NOT NULL,
    pipeline_task_id VARCHAR(64),
    transformation VARCHAR(500),
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ecos_data.ecos_data_lineage_edge IS 'Data lineage edge — data flow between nodes';
COMMENT ON COLUMN ecos_data.ecos_data_lineage_edge.edge_type IS 'DATA_FLOW / DERIVATION / DEPENDENCY';
COMMENT ON COLUMN ecos_data.ecos_data_lineage_edge.pipeline_task_id IS 'Associated pipeline task that creates this data flow';

CREATE INDEX IF NOT EXISTS idx_lineage_edge_src ON ecos_data.ecos_data_lineage_edge(source_node_id);
CREATE INDEX IF NOT EXISTS idx_lineage_edge_tgt ON ecos_data.ecos_data_lineage_edge(target_node_id);
CREATE INDEX IF NOT EXISTS idx_lineage_edge_task ON ecos_data.ecos_data_lineage_edge(pipeline_task_id);

-- 3. SQL Query Template
CREATE TABLE IF NOT EXISTS ecos_data.ecos_query_template (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    sql_content TEXT NOT NULL,
    datasource_id VARCHAR(64),
    is_shared BOOLEAN DEFAULT false,
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ecos_data.ecos_query_template IS 'Saved SQL query templates';

-- 4. SQL Query History
CREATE TABLE IF NOT EXISTS ecos_data.ecos_query_history (
    id VARCHAR(36) PRIMARY KEY,
    sql_content TEXT NOT NULL,
    datasource_id VARCHAR(64),
    status VARCHAR(20) DEFAULT 'RUNNING',
    rows_affected INT DEFAULT 0,
    error_msg TEXT,
    execution_time_ms BIGINT DEFAULT 0,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    created_by VARCHAR(100)
);

COMMENT ON TABLE ecos_data.ecos_query_history IS 'SQL query execution history';
COMMENT ON COLUMN ecos_data.ecos_query_history.status IS 'RUNNING / COMPLETED / FAILED / CANCELLED';

CREATE INDEX IF NOT EXISTS idx_query_history_ds ON ecos_data.ecos_query_history(datasource_id);
CREATE INDEX IF NOT EXISTS idx_query_history_started ON ecos_data.ecos_query_history(started_at DESC);

-- 5. Data Category
CREATE TABLE IF NOT EXISTS ecos_data.td_data_category (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    parent_id VARCHAR(36),
    description TEXT,
    icon VARCHAR(50),
    sort_order INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ecos_data.td_data_category IS 'Data category tree for catalog classification';
COMMENT ON COLUMN ecos_data.td_data_category.status IS 'ACTIVE / INACTIVE';

CREATE INDEX IF NOT EXISTS idx_category_parent ON ecos_data.td_data_category(parent_id);

-- 6. Pipeline Task: add task_type column (table is in public schema, not ecos_data)
ALTER TABLE public.ecos_pipeline_task ADD COLUMN IF NOT EXISTS task_type VARCHAR(20) DEFAULT 'TRANSFORM';
COMMENT ON COLUMN public.ecos_pipeline_task.task_type IS 'TRANSFORM / SYNC / LAKE_EXPORT';

-- 7. Seed data: root categories (using existing column names: category_id, category_name)
INSERT INTO ecos_data.td_data_category (category_id, category_name, parent_id, description, sort_order, status)
VALUES
    ('cat_root', 'Data Assets', NULL, 'Root category for all data assets', 0, 'ACTIVE'),
    ('cat_raw', 'Raw Data', 'cat_root', 'Raw ingested data', 1, 'ACTIVE'),
    ('cat_curated', 'Curated Data', 'cat_root', 'Curated and cleansed data', 2, 'ACTIVE'),
    ('cat_semantic', 'Semantic Data', 'cat_root', 'Semantic and business-ready data', 3, 'ACTIVE'),
    ('cat_app', 'Application Data', 'cat_root', 'Application-level data marts', 4, 'ACTIVE')
ON CONFLICT (category_id) DO NOTHING;

-- 8. Seed data: sample lineage for demo
INSERT INTO ecos_data.ecos_data_lineage_node (id, node_type, name, schema_name, table_name, datasource_id, layer)
VALUES
    ('ln_pg_source', 'SOURCE', 'PostgreSQL Source', 'public', 'td_datasource', NULL, 'SOURCE'),
    ('ln_pg_raw', 'TARGET', 'Raw Data Layer', 'ecos_data', 'td_data_resource', NULL, 'RAW'),
    ('ln_pg_curated', 'TARGET', 'Curated Data Layer', 'ecos_data', 'ecos_quality_rule', NULL, 'CURATED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ecos_data.ecos_data_lineage_edge (id, source_node_id, target_node_id, edge_type, pipeline_task_id, transformation)
VALUES
    ('le_ingest', 'ln_pg_source', 'ln_pg_raw', 'DATA_FLOW', NULL, 'Metadata Collection'),
    ('le_curate', 'ln_pg_raw', 'ln_pg_curated', 'DERIVATION', NULL, 'Quality Check + Transform')
ON CONFLICT (id) DO NOTHING;
