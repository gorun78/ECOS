-- ============================================================
-- P0-1 统一租户数据表 ecos_tenant
-- 合并 td_tenant 与 ecos_tenant_quota 概念，统一为单表
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_tenant (
    id              VARCHAR(32) PRIMARY KEY,
    tenant_name     VARCHAR(64) NOT NULL,
    tenant_code     VARCHAR(32) UNIQUE,
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    max_users       INT DEFAULT 0,
    max_storage_mb  BIGINT DEFAULT 0,
    max_api_per_day BIGINT DEFAULT 0,
    isolation_mode  VARCHAR(16) DEFAULT 'ROW_FILTER',
    schema_name     VARCHAR(64),
    database_url    VARCHAR(256),
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- 种子数据
INSERT INTO ecos_tenant (id, tenant_name, tenant_code, status, max_users, max_storage_mb, max_api_per_day)
VALUES
('tenant-a', '默认租户A', 'tenant-a', 'ACTIVE', 0, 0, 0),
('tenant-b', '测试租户B', 'tenant-b', 'ACTIVE', 50, 5120, 10000)
ON CONFLICT (id) DO NOTHING;
