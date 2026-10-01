-- ============================================================
-- V127__ecos_cognitive_evidence.sql — 认知证据表（PMO-59 Phase 1 / ADR-9）
-- ADR-9 口径: 推理"结果"不落盘(实时计算)；模型"资产"落盘(ADR-8)；
--             认知"心智状态"(证据/假设/不确定性判断)落盘(ADR-9 本表族)
-- 表: ecos_cognitive_evidence (感知层采集的结构化证据: 来源/可信度/冲突标记)
-- 口径: 外部方案层1"感知接入层"输出物；可信度对齐原稿打分口径
--       系统数据 0.95~1.0 / 权威新闻官宣 ~0.8 / 小道消息 0.3~0.5
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_cognitive_evidence (
    id                  VARCHAR(64) PRIMARY KEY,
    evidence_code       VARCHAR(128) NOT NULL,        -- 业务唯一键(幂等登记)
    tenant_scope        VARCHAR(64),                  -- 租户/域隔离(可选, 多租户启用)
    source_type         VARCHAR(32) NOT NULL,         -- SYSTEM_DATA / NEWS / EXPERT / PIPELINE
    source_ref          VARCHAR(256),                 -- 来源定位(管道ID/URL/人工录入ID)
    blob                JSONB NOT NULL DEFAULT '{}'::jsonb,  -- 结构化事实载荷 {fact, metric, value, context...}
    confidence          DECIMAL(5,4) NOT NULL DEFAULT 0.5,   -- 可信度 0~1
    is_conflict         BOOLEAN NOT NULL DEFAULT FALSE,      -- 同事实多证据不一致 → 冲突待修正标记
    refuting_evidence_ids JSONB NOT NULL DEFAULT '[]'::jsonb, -- 冲突对方证据 id 列表
    effective_time      TIMESTAMP,                    -- 事实生效时间
    expire_time         TIMESTAMP,                    -- 事实失效时间(可选)
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / CONFLICTED / ARCHIVED
    create_time         TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMP NOT NULL DEFAULT NOW(),
    create_by           VARCHAR(64) DEFAULT 'system',
    update_by           VARCHAR(64) DEFAULT 'system',
    is_deleted          SMALLINT NOT NULL DEFAULT 0
);

-- 唯一约束: 同一业务证据不重复登记
CREATE UNIQUE INDEX IF NOT EXISTS uniq_ecos_cognitive_evidence_code ON ecos_cognitive_evidence(evidence_code);
-- 热路径索引: 按来源类型/状态/时间回放
CREATE INDEX IF NOT EXISTS idx_ecos_cognitive_evidence_source ON ecos_cognitive_evidence(source_type);
CREATE INDEX IF NOT EXISTS idx_ecos_cognitive_evidence_status ON ecos_cognitive_evidence(status);
CREATE INDEX IF NOT EXISTS idx_ecos_cognitive_evidence_time   ON ecos_cognitive_evidence(create_time DESC);

COMMENT ON TABLE  ecos_cognitive_evidence IS '认知证据表 — ADR-9 心智层: 结构化证据(来源/可信度/冲突标记)';
COMMENT ON COLUMN ecos_cognitive_evidence.source_type IS '来源类型 SYSTEM_DATA/NEWS/EXPERT/PIPELINE';
COMMENT ON COLUMN ecos_cognitive_evidence.confidence  IS '可信度 0~1 (系统数据0.95+ / 权威新闻~0.8 / 小道消息0.3~0.5)';
COMMENT ON COLUMN ecos_cognitive_evidence.is_conflict IS '同事实多证据不一致→冲突待修正';
COMMENT ON COLUMN ecos_cognitive_evidence.blob        IS '结构化事实载荷(事实/指标/数值/上下文)';

-- 种子: 2 条测试证据(1 条系统数据 + 1 条与前者冲突的新闻证据)
INSERT INTO ecos_cognitive_evidence (id, evidence_code, tenant_scope, source_type, source_ref, blob, confidence, is_conflict, refuting_evidence_ids, effective_time, status, create_by, update_by)
VALUES
    ('cog_ev_seed_001', 'EV-20260913-001', 'default', 'SYSTEM_DATA', 'datanet.pipeline:inv-daily',
     '{"fact":"inventory_turnover","metric":"inv_turnover_days","value":23.5,"period":"2026-09"}'::jsonb,
     0.98, FALSE, '[]'::jsonb, NOW(), 'ACTIVE', 'system', 'system'),
    ('cog_ev_seed_002', 'EV-20260913-002', 'default', 'NEWS', 'supply-chain-watch-20260913',
     '{"fact":"inventory_turnover","metric":"inv_turnover_days","value":24.8,"period":"2026-09","note":"口径差异待核"}'::jsonb,
     0.80, TRUE, '["cog_ev_seed_001"]'::jsonb, NOW(), 'CONFLICTED', 'system', 'system')
ON CONFLICT DO NOTHING;
