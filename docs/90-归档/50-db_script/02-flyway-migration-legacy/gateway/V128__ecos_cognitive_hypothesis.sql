-- ============================================================
-- V128__ecos_cognitive_hypothesis.sql — 认知假设表（PMO-59 Phase 1 / ADR-9）
-- ADR-9 口径: 推理"结果"不落盘(实时计算)；模型"资产"落盘(ADR-8)；
--             认知"心智状态"(证据/假设/不确定性判断)落盘(ADR-9 本表族)
-- 表: ecos_cognitive_hypothesis (心智层 H 库: 假设/证据引用/valid/失效时间)
-- 口径: 外部方案层3.1"假设管理模块"；核心能力=监测证据触发"假设失效"
--       假设失效 → 自动下游推演作废 + 告警(Phase 2 实现)
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_cognitive_hypothesis (
    id                  VARCHAR(64) PRIMARY KEY,
    hypothesis_code     VARCHAR(128) NOT NULL,        -- 业务唯一键(幂等登记)
    tenant_scope        VARCHAR(64),                  -- 租户/域隔离(可选, 多租户启用)
    statement           TEXT NOT NULL,                -- 假设陈述(业务语言)
    domain              VARCHAR(64),                  -- 业务域(如 supply-chain / pricing)
    metric_ref          VARCHAR(128),                 -- 关联经营变量(对齐 belief.variable_name)
    evidence_ids        JSONB NOT NULL DEFAULT '[]'::jsonb, -- 支撑证据 id 列表(引用 ecos_cognitive_evidence.id)
    is_valid            BOOLEAN NOT NULL DEFAULT TRUE,       -- 当前是否有效
    invalid_at          TIMESTAMP,                    -- 失效时间(监测命中时写)
    invalid_reason      TEXT,                         -- 失效原因(触发证据/冲突说明)
    status              VARCHAR(16) NOT NULL DEFAULT 'VALID', -- VALID / INVALIDATED / ARCHIVED
    create_time         TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMP NOT NULL DEFAULT NOW(),
    create_by           VARCHAR(64) DEFAULT 'system',
    update_by           VARCHAR(64) DEFAULT 'system',
    is_deleted          SMALLINT NOT NULL DEFAULT 0
);

-- 唯一约束: 同一业务假设不重复登记
CREATE UNIQUE INDEX IF NOT EXISTS uniq_ecos_cognitive_hypothesis_code ON ecos_cognitive_hypothesis(hypothesis_code);
-- 热路径索引: 失效检测按域/失效时间扫描
CREATE INDEX IF NOT EXISTS idx_ecos_cognitive_hypothesis_domain   ON ecos_cognitive_hypothesis(domain);
CREATE INDEX IF NOT EXISTS idx_ecos_cognitive_hypothesis_status   ON ecos_cognitive_hypothesis(status);
CREATE INDEX IF NOT EXISTS idx_ecos_cognitive_hypothesis_time     ON ecos_cognitive_hypothesis(update_time DESC);

COMMENT ON TABLE  ecos_cognitive_hypothesis IS '认知假设表 — ADR-9 心智层 H 库: 假设/证据引用/失效监测';
COMMENT ON COLUMN ecos_cognitive_hypothesis.statement    IS '假设陈述(业务语言)';
COMMENT ON COLUMN ecos_cognitive_hypothesis.metric_ref   IS '关联经营变量(与 belief.variable_name 对齐)';
COMMENT ON COLUMN ecos_cognitive_hypothesis.evidence_ids IS '支撑证据 id 列表(引用 ecos_cognitive_evidence.id)';
COMMENT ON COLUMN ecos_cognitive_hypothesis.is_valid     IS '当前是否有效(监测命中失效→FALSE)';

-- 种子: 1 条有效假设(引用 seed 证据)
INSERT INTO ecos_cognitive_hypothesis (id, hypothesis_code, tenant_scope, statement, domain, metric_ref, evidence_ids, is_valid, status, create_by, update_by)
VALUES
    ('cog_hyp_seed_001', 'HYP-20260913-001', 'default',
     '库存周转天数维持在 24 天内可控(无结构性恶化)', 'supply-chain', 'inv_turnover_days',
     '["cog_ev_seed_001"]'::jsonb,
     TRUE, 'VALID', 'system', 'system')
ON CONFLICT DO NOTHING;
