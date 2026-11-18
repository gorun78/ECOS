-- ============================================================
-- V22__ecos_cognitive_rule.sql — 认知规则引擎表
-- ============================================================

-- ── ecos_cognitive_rule: 认知规则 ──────────────────────────
CREATE TABLE IF NOT EXISTS ecos_cognitive_rule (
    id              BIGSERIAL PRIMARY KEY,
    rule_name       VARCHAR(128) NOT NULL,
    rule_type       VARCHAR(32)  NOT NULL,    -- THRESHOLD / CAUSAL / OPTIMIZATION
    condition_expr  TEXT         NOT NULL,    -- 规则条件表达式
    action_config   JSONB,                    -- 动作配置
    priority        INT          DEFAULT 0,
    enabled         BOOLEAN      DEFAULT true,
    description     TEXT,
    created_by      VARCHAR(64),
    created_at      TIMESTAMP    DEFAULT NOW(),
    updated_at      TIMESTAMP    DEFAULT NOW()
);

COMMENT ON TABLE  ecos_cognitive_rule                IS '认知规则引擎表 — 阈值/因果/优化规则定义';
COMMENT ON COLUMN ecos_cognitive_rule.id              IS '主键，自增';
COMMENT ON COLUMN ecos_cognitive_rule.rule_name       IS '规则名称';
COMMENT ON COLUMN ecos_cognitive_rule.rule_type       IS '规则类型: THRESHOLD/CAUSAL/OPTIMIZATION';
COMMENT ON COLUMN ecos_cognitive_rule.condition_expr  IS '规则条件表达式';
COMMENT ON COLUMN ecos_cognitive_rule.action_config   IS '动作配置 (JSONB)';
COMMENT ON COLUMN ecos_cognitive_rule.priority        IS '优先级，数值越大优先级越高';
COMMENT ON COLUMN ecos_cognitive_rule.enabled         IS '是否启用';
COMMENT ON COLUMN ecos_cognitive_rule.description     IS '规则描述';
COMMENT ON COLUMN ecos_cognitive_rule.created_by      IS '创建人';
COMMENT ON COLUMN ecos_cognitive_rule.created_at      IS '创建时间';
COMMENT ON COLUMN ecos_cognitive_rule.updated_at      IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_cog_rule_type    ON ecos_cognitive_rule(rule_type);
CREATE INDEX IF NOT EXISTS idx_cog_rule_enabled ON ecos_cognitive_rule(enabled);
