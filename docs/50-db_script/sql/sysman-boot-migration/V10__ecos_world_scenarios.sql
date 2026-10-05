-- ============================================================
-- V10__ecos_world_scenarios.sql — World Model 场景表 (P1-3)
-- 设计: ECOS Phase 1 P1-3 World Model
-- 表: ecos_world_scenarios (VARCHAR PK 版本，供内存模式对照)
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_world_scenarios (
    id              VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     TEXT DEFAULT '',
    goal_ids        TEXT DEFAULT '[]',        -- JSON array: ["goal-id-1", "goal-id-2"]
    status          VARCHAR(32) DEFAULT 'draft',
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- ── 索引 ────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_ws_status ON ecos_world_scenarios(status);
