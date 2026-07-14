-- 为所有业务表追加 tenant_id 列
ALTER TABLE ecos_object_data ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
ALTER TABLE ecos_object_relation ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
ALTER TABLE ecos_dq_rule ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
ALTER TABLE ecos_pipeline_definition ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
ALTER TABLE ecos_pipeline_execution ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
ALTER TABLE ecos_workflow_instance ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
ALTER TABLE ecos_ontology_entity ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
ALTER TABLE ecos_glossary_term ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);

-- 已有数据填入默认租户
UPDATE ecos_object_data SET tenant_id = 'tenant-a' WHERE tenant_id IS NULL;
UPDATE ecos_object_relation SET tenant_id = 'tenant-a' WHERE tenant_id IS NULL;
UPDATE ecos_dq_rule SET tenant_id = 'tenant-a' WHERE tenant_id IS NULL;
UPDATE ecos_pipeline_definition SET tenant_id = 'tenant-a' WHERE tenant_id IS NULL;
UPDATE ecos_pipeline_execution SET tenant_id = 'tenant-a' WHERE tenant_id IS NULL;
UPDATE ecos_workflow_instance SET tenant_id = 'tenant-a' WHERE tenant_id IS NULL;
UPDATE ecos_ontology_entity SET tenant_id = 'tenant-a' WHERE tenant_id IS NULL;
UPDATE ecos_glossary_term SET tenant_id = 'tenant-a' WHERE tenant_id IS NULL;

-- 建索引
CREATE INDEX IF NOT EXISTS idx_object_data_tenant ON ecos_object_data(tenant_id);
CREATE INDEX IF NOT EXISTS idx_dq_rule_tenant ON ecos_dq_rule(tenant_id);
CREATE INDEX IF NOT EXISTS idx_pipeline_tenant ON ecos_pipeline_definition(tenant_id);
