-- PMO-30 P1-1 本体多租户 RLS
-- 1) ecos_ontology_entity 加 tenant_id 列（幂等：V38 已加过）+ 补缺失
-- 2) 已有数据回填 default tenant（保留 NULL 兜底：未分配 Tenant 的数据保持共享可见）
-- 3) 二级索引

ALTER TABLE ecos_ontology_entity
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);

-- 回填默认租户（保留 NULL 行兼容旧数据，但新插入打 tenantId）
UPDATE ecos_ontology_entity SET tenant_id = 'tenant-a' WHERE tenant_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_ontology_entity_tenant ON ecos_ontology_entity(tenant_id);

-- ecos_ontology 表加 tenant_id，确保 ontology 主表能做到租户隔离（schema 只加不删铁律）
ALTER TABLE ecos_ontology
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
-- 已有数据回填
UPDATE ecos_ontology SET tenant_id = 'tenant-a' WHERE tenant_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_ontology_tenant ON ecos_ontology(tenant_id);

-- ecos_ontology-domain 表 tenant_id 幂等（如果该表存在且无 tenant_id 列则添加）-- 软约束
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables
             WHERE table_schema='public' AND table_name='ecos_domain')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                     WHERE table_schema='public' AND table_name='ecos_domain' AND column_name='tenant_id') THEN
    EXECUTE 'ALTER TABLE ecos_domain ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) '
            'DEFAULT ''tenant-a''';
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_ontology_domain_tenant ON ecos_domain(tenant_id)';
  END IF;
END
$$;
