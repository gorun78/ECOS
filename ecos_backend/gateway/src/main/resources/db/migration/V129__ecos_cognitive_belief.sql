-- ============================================================
-- V129__ecos_cognitive_belief.sql — 不确定性判断表（PMO-59 Phase 1 / ADR-9）
-- ADR-9 口径: 推理"结果"不落盘(实时计算)；模型"资产"落盘(ADR-8)；
--             认知"心智状态"(证据/假设/不确定性判断)落盘(ADR-9 本表族)
-- 术语: 外部方案原"信念"统一改称"不确定性判断"(业务方口径, 避心理学歧义)
-- 表: ecos_cognitive_belief (心智层 P 库: 不可观测经营变量的有限离散概率分布)
-- 口径: 外部方案层3.2；工程实现=有限离散概率分布 + 新证据加权更新 + 人工覆写
--       snapshot_version 为时间回放(历史决策同参数重算, Phase 3)预留
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_cognitive_belief (
    id                  VARCHAR(64) PRIMARY KEY,
    variable_name       VARCHAR(128) NOT NULL,        -- 不可观测经营变量名(如 competitor_price_cut_prob)
    tenant_scope        VARCHAR(64),                  -- 租户/域隔离(可选, 多租户启用)
    domain              VARCHAR(64) NOT NULL,         -- 业务域(pricing / supply-chain / demand)
    distribution        JSONB NOT NULL DEFAULT '[]'::jsonb, -- 有限离散概率分布 [{"outcome":"high","prob":0.4},...] prob 和=1 约定
    version             INTEGER NOT NULL DEFAULT 1,   -- 分布版本(每次证据加权更新 +1)
    snapshot_version    INTEGER,                      -- 时间回放预留(Phase 3: 历史决策同参数重算引用此版本)
    is_manual_override  BOOLEAN NOT NULL DEFAULT FALSE,  -- 人工覆写标记(专家干预优先于模型更新)
    override_reason     TEXT,                         -- 人工覆写理由
    last_evidence_id    VARCHAR(64),                  -- 触发本次版本更新的证据 id(引用 ecos_cognitive_evidence.id, 可溯源)
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / ARCHIVED
    create_time         TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMP NOT NULL DEFAULT NOW(),
    create_by           VARCHAR(64) DEFAULT 'system',
    update_by           VARCHAR(64) DEFAULT 'system',
    is_deleted          SMALLINT NOT NULL DEFAULT 0
);

-- 唯一约束: 同一变量同一域下版本不重复(时间回放=按 variable+domain+version 可重放)
CREATE UNIQUE INDEX IF NOT EXISTS uniq_ecos_cognitive_belief_variable_ver ON ecos_cognitive_belief(variable_name, domain, version);
-- 热路径索引: 按域查询认知状态 / 按时间回放
CREATE INDEX IF NOT EXISTS idx_ecos_cognitive_belief_domain ON ecos_cognitive_belief(domain);
CREATE INDEX IF NOT EXISTS idx_ecos_cognitive_belief_time   ON ecos_cognitive_belief(update_time DESC);

COMMENT ON TABLE  ecos_cognitive_belief IS '不确定性判断表 — ADR-9 心智层 P 库: 有限离散概率分布+版本+人工覆写(原稿"信念"改称)';
COMMENT ON COLUMN ecos_cognitive_belief.variable_name    IS '不可观测经营变量名';
COMMENT ON COLUMN ecos_cognitive_belief.distribution     IS '有限离散概率分布 [{"outcome","prob"}], prob 和=1';
COMMENT ON COLUMN ecos_cognitive_belief.version          IS '分布版本(新证据加权更新 +1)';
COMMENT ON COLUMN ecos_cognitive_belief.snapshot_version IS '时间回放预留(Phase 3 历史决策同参数重算)';
COMMENT ON COLUMN ecos_cognitive_belief.is_manual_override IS '人工覆写标记(专家干预优先)';
COMMENT ON COLUMN ecos_cognitive_belief.last_evidence_id IS '触发本次版本更新的证据引用(溯源)';

-- 种子: 1 个变量的 2 个版本(演示版本递进 + 证据溯源)
INSERT INTO ecos_cognitive_belief (id, variable_name, tenant_scope, domain, distribution, version, snapshot_version, is_manual_override, last_evidence_id, status, create_by, update_by)
VALUES
    ('cog_blf_seed_001', 'competitor_price_cut_prob', 'default', 'pricing',
     '[{"outcome":"none","prob":0.7},{"outcome":"mild","prob":0.25},{"outcome":"aggressive","prob":0.05}]'::jsonb,
     1, 1, FALSE, NULL, 'ACTIVE', 'system', 'system'),
    ('cog_blf_seed_002', 'competitor_price_cut_prob', 'default', 'pricing',
     '[{"outcome":"none","prob":0.55},{"outcome":"mild","prob":0.35},{"outcome":"aggressive","prob":0.10}]'::jsonb,
     2, 1, FALSE, 'cog_ev_seed_001', 'ACTIVE', 'system', 'system')
ON CONFLICT DO NOTHING;
