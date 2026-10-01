-- ============================================================
-- V148__ecos_security_policy.sql — 安全策略真表（PMO-60 v2.0，替代 sys_dict 兜底）
-- 场景安全策略从字典表兜底升级为独立真表，ABAC 表达式 + 优先级路由。
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_security_policy (
    id            VARCHAR(64) PRIMARY KEY,          -- sp_xxxxxxxx
    name          VARCHAR(255) NOT NULL,
    domain        VARCHAR(128),                     -- 适用域：data|knowledge|agent|interface|all
    policy_expr   TEXT NOT NULL,                    -- ABAC 表达式（OPA/SpEL）
    priority      INT NOT NULL DEFAULT 100,
    create_time   TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP NOT NULL DEFAULT NOW(),
    create_by     VARCHAR(64) NOT NULL DEFAULT 'system',
    update_by     VARCHAR(64) NOT NULL DEFAULT 'system',
    is_deleted    SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_sec_policy_domain_name UNIQUE (domain, name)
);

CREATE INDEX IF NOT EXISTS idx_sec_policy_domain ON ecos_security_policy (domain, is_deleted);

COMMENT ON TABLE ecos_security_policy IS '场景绑定的安全策略真表';
