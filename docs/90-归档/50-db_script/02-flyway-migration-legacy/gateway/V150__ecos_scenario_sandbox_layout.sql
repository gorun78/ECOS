-- ============================================================
-- V150__ecos_scenario_sandbox_layout.sql — 沙盘画布布局持久化（PMO-60 v2.0）
-- React Flow { nodes[], edges[], viewport } 序列化落库，
-- layout_version 乐观锁防止并发编辑互相覆盖。
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_scenario_sandbox_layout (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scenario_id   VARCHAR(64) NOT NULL UNIQUE,       -- 1:1 场景
    layout_jsonb  JSONB NOT NULL DEFAULT '{}'::jsonb, -- React Flow { nodes[], edges[], viewport }
    layout_version INT NOT NULL DEFAULT 1,            -- 乐观锁
    create_time   TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP NOT NULL DEFAULT NOW(),
    create_by     VARCHAR(64) NOT NULL DEFAULT 'system',
    update_by     VARCHAR(64) NOT NULL DEFAULT 'system',
    is_deleted    SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE ecos_scenario_sandbox_layout IS '业务场景沙盘画布布局（React Flow 序列化，乐观锁 layout_version）';
