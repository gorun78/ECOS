-- ============================================================
-- V146__ecos_scenario_mind.sql — 场景多心智变体表（PMO-60 v2.0）
-- 多心智变体 1:N：同一场景可挂多个心智（base/adverse/乐观...），
-- active_mind 哨兵保证同一场景仅有一个激活心智（partial unique index）。
-- cognitive 三表（evidence/hypothesis/belief）仅以 id 引用本表，不 ALTER。
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_scenario_mind (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scenario_id           VARCHAR(64) NOT NULL,              -- 1:N，不再 UNIQUE
    mind_label            VARCHAR(64) NOT NULL DEFAULT 'base',
    active_mind           SMALLINT NOT NULL DEFAULT 0,
    initial_belief_jsonb  JSONB NOT NULL DEFAULT '{}'::jsonb,
    evidence_refs         JSONB NOT NULL DEFAULT '[]'::jsonb,
    hypothesis_refs       JSONB NOT NULL DEFAULT '[]'::jsonb,
    model_refs            JSONB NOT NULL DEFAULT '[]'::jsonb,
    cognitive_endpoints   JSONB NOT NULL DEFAULT '{}'::jsonb,
    initial_confidence    DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    create_time           TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time           TIMESTAMP NOT NULL DEFAULT NOW(),
    create_by             VARCHAR(64) NOT NULL DEFAULT 'system',
    update_by             VARCHAR(64) NOT NULL DEFAULT 'system',
    is_deleted            SMALLINT NOT NULL DEFAULT 0
);

-- 热路径索引: 按场景查未删心智
CREATE INDEX IF NOT EXISTS idx_mind_scenario ON ecos_scenario_mind (scenario_id, is_deleted);
-- 唯一: 同一场景下心智标签不重复（逻辑删除豁免）
CREATE UNIQUE INDEX IF NOT EXISTS uq_mind_scenario_label
    ON ecos_scenario_mind (scenario_id, mind_label)
    WHERE is_deleted = 0;
-- 哨兵: 同一场景至多一个激活心智
CREATE UNIQUE INDEX IF NOT EXISTS uq_mind_active
    ON ecos_scenario_mind (scenario_id)
    WHERE is_deleted = 0 AND active_mind = 1;

COMMENT ON TABLE ecos_scenario_mind IS '场景授权心智变体（1:N，active_mind 哨兵唯一激活）';
