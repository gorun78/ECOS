-- ============================================================
-- V17__ecos_goal_hierarchy.sql
-- 目标金字塔 — 扩展 ecos_wm_goal + 目标变更日志表
-- 5 层金字塔模型: 战略(STRATEGIC) → OKR → KPI → 工作流(WORKFLOW) → Agent
-- ============================================================

-- 1. 扩展 ecos_wm_goal 新字段 (幂等: 使用 ADD COLUMN IF NOT EXISTS)
ALTER TABLE ecos_wm_goal ADD COLUMN IF NOT EXISTS goal_type        VARCHAR(32)  DEFAULT 'STRATEGIC';
ALTER TABLE ecos_wm_goal ADD COLUMN IF NOT EXISTS weight            INTEGER      DEFAULT 50;
ALTER TABLE ecos_wm_goal ADD COLUMN IF NOT EXISTS org_id            VARCHAR(64);
ALTER TABLE ecos_wm_goal ADD COLUMN IF NOT EXISTS owner_user_id     VARCHAR(64);
ALTER TABLE ecos_wm_goal ADD COLUMN IF NOT EXISTS start_date        DATE;
ALTER TABLE ecos_wm_goal ADD COLUMN IF NOT EXISTS end_date          DATE;
ALTER TABLE ecos_wm_goal ADD COLUMN IF NOT EXISTS target_value      DECIMAL(18,2);
ALTER TABLE ecos_wm_goal ADD COLUMN IF NOT EXISTS current_value     DECIMAL(18,2);
ALTER TABLE ecos_wm_goal ADD COLUMN IF NOT EXISTS unit              VARCHAR(32);
ALTER TABLE ecos_wm_goal ADD COLUMN IF NOT EXISTS linked_workflow_id VARCHAR(64);

-- 2. 目标变更日志表
CREATE TABLE IF NOT EXISTS ecos_wm_goal_log (
    id              BIGSERIAL PRIMARY KEY,
    goal_id         BIGINT NOT NULL REFERENCES ecos_wm_goal(id) ON DELETE CASCADE,
    change_type     VARCHAR(32)  NOT NULL,
    old_value       TEXT,
    new_value       TEXT,
    changed_by      VARCHAR(64),
    changed_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_goal_log_goal  ON ecos_wm_goal_log(goal_id);
CREATE INDEX IF NOT EXISTS idx_goal_log_time  ON ecos_wm_goal_log(changed_at);

-- 3. 现有目标统一升级为 STRATEGIC 类型
UPDATE ecos_wm_goal SET goal_type = 'STRATEGIC', weight = 100
 WHERE goal_type IS NULL;
