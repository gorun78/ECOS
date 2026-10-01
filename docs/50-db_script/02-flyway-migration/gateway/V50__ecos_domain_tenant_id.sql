-- P0-5 (Wave-4.2): ecos_domain 表补 tenant_id 列 (Wave-3.1 RLS 重写依赖此列)
-- 不破坏现有数据：IF NOT EXISTS + tenant-a 回填
-- 铁律: schema 只加不删

ALTER TABLE ecos_domain ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_ecos_domain_tenant ON ecos_domain(tenant_id);
UPDATE ecos_domain SET tenant_id = 'tenant-a' WHERE tenant_id IS NULL;
