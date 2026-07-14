-- ============================================================
-- P3-6 多租户配额与用量表
-- ============================================================

-- 租户配额定义表
CREATE TABLE IF NOT EXISTS ecos_tenant_quota (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       VARCHAR(64)     NOT NULL,
    quota_type      VARCHAR(32)     NOT NULL,   -- API_CALLS, STORAGE_MB, etc.
    daily_limit     BIGINT          DEFAULT 0,
    monthly_limit   BIGINT          DEFAULT 0,
    created_at      TIMESTAMP       DEFAULT NOW(),
    updated_at      TIMESTAMP       DEFAULT NOW()
);

-- 唯一约束：每个租户每种配额类型只有一条记录
CREATE UNIQUE INDEX IF NOT EXISTS idx_tenant_quota_type
    ON ecos_tenant_quota (tenant_id, quota_type);

-- 种子数据
INSERT INTO ecos_tenant_quota (tenant_id, quota_type, daily_limit, monthly_limit)
VALUES
    ('tenant-a', 'API_CALLS',  100,  3000),
    ('tenant-a', 'STORAGE_MB', 500, 15000)
ON CONFLICT (tenant_id, quota_type) DO NOTHING;

-- 租户用量记录表
CREATE TABLE IF NOT EXISTS ecos_tenant_usage (
    tenant_id   VARCHAR(64)     NOT NULL,
    usage_date  DATE            NOT NULL,
    quota_type  VARCHAR(32)     NOT NULL,
    used_count  BIGINT          DEFAULT 0,
    updated_at  TIMESTAMP       DEFAULT NOW(),
    PRIMARY KEY (tenant_id, usage_date, quota_type)
);

-- 用量查询索引
CREATE INDEX IF NOT EXISTS idx_tenant_usage_date
    ON ecos_tenant_usage (tenant_id, usage_date DESC);
