-- ============================================================
-- V5__ecos_world_model.sql — World Model 持久化表
-- 设计: 10214 World Model Platform
-- 表: ecos_wm_goal / ecos_wm_causal_link / ecos_wm_scenario
-- ============================================================

-- ── 目标表 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ecos_wm_goal (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     TEXT DEFAULT '',
    parent_id       BIGINT REFERENCES ecos_wm_goal(id) ON DELETE SET NULL,
    progress        INTEGER DEFAULT 0 CHECK (progress >= 0 AND progress <= 100),
    status          VARCHAR(32) DEFAULT 'PLANNED',
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- ── 因果链关系表 ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ecos_wm_causal_link (
    id                  BIGSERIAL PRIMARY KEY,
    source_goal_id      BIGINT NOT NULL REFERENCES ecos_wm_goal(id) ON DELETE CASCADE,
    target_goal_id      BIGINT NOT NULL REFERENCES ecos_wm_goal(id) ON DELETE CASCADE,
    relationship_type   VARCHAR(32) NOT NULL DEFAULT 'POSITIVE',
    description         TEXT DEFAULT '',
    created_at          TIMESTAMP DEFAULT NOW()
);

-- ── 场景表 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ecos_wm_scenario (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     TEXT DEFAULT '',
    config_json     TEXT DEFAULT '{}',       -- JSON: 自定义配置(score/cost/risk 等)
    status          VARCHAR(32) DEFAULT 'DRAFT',
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- ── 索引 ────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_wm_goal_parent   ON ecos_wm_goal(parent_id);
CREATE INDEX IF NOT EXISTS idx_wm_goal_status   ON ecos_wm_goal(status);
CREATE INDEX IF NOT EXISTS idx_wm_causal_source ON ecos_wm_causal_link(source_goal_id);
CREATE INDEX IF NOT EXISTS idx_wm_causal_target ON ecos_wm_causal_link(target_goal_id);
CREATE INDEX IF NOT EXISTS idx_wm_scenario_status ON ecos_wm_scenario(status);
