-- V31: Sprint 9 — 目标追踪表 + 配置激活 + 业务域数据

-- ═══ 1. 激活所有僵尸配置项 ═══
UPDATE sys_config SET status = 'active', config_group = 
  CASE 
    WHEN config_key LIKE 'agent_%' THEN 'agent'
    WHEN config_key LIKE 'db_%' THEN 'database'
    WHEN config_key LIKE 'dq_%' THEN 'data_quality'
    WHEN config_key LIKE 'audit_%' THEN 'audit'
    WHEN config_key LIKE 'lockout_%' OR config_key LIKE 'login_%' THEN 'auth'
    WHEN config_key LIKE 'default_%' THEN 'frontend'
    WHEN config_key LIKE 'cache_%' THEN 'cache'
    WHEN config_key LIKE 'glossary_%' THEN 'glossary'
    WHEN config_key LIKE 'marketplace_%' THEN 'marketplace'
    WHEN config_key LIKE 'kg_%' THEN 'knowledge_graph'
    WHEN config_key LIKE 'causal_%' THEN 'causal'
    WHEN config_key LIKE 'alert_%' OR config_key LIKE 'data_%' THEN 'monitoring'
    ELSE 'general'
  END
WHERE status IS NULL OR status != 'active';

-- ═══ 2. 目标追踪表 — 时序进度快照 ═══
CREATE TABLE IF NOT EXISTS ecos_goal_tracking (
    id          VARCHAR(36) PRIMARY KEY,
    goal_id     BIGINT NOT NULL REFERENCES ecos_wm_goal(id) ON DELETE CASCADE,
    progress    INTEGER NOT NULL CHECK (progress >= 0 AND progress <= 100),
    actual_value NUMERIC(18,2),
    note        TEXT,
    recorded_at DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_goal_tracking_goal ON ecos_goal_tracking(goal_id, recorded_at);

-- 种子追踪数据: CEO场景4个目标过去6个月进度
-- G24 年度营收10亿 (target=10亿, 按月累计)
INSERT INTO ecos_goal_tracking (id, goal_id, progress, actual_value, note, recorded_at) VALUES
('gt_rev_01', 24, 8, 80000000.00, '1月营收8000万', '2026-01-31'),
('gt_rev_02', 24, 16, 160000000.00, '2月累计1.6亿', '2026-02-28'),
('gt_rev_03', 24, 25, 250000000.00, '3月累计2.5亿', '2026-03-31'),
('gt_rev_04', 24, 33, 330000000.00, '4月累计3.3亿', '2026-04-30'),
('gt_rev_05', 24, 45, 450000000.00, '5月累计4.5亿', '2026-05-31'),
('gt_rev_06', 24, 62, 620000000.00, '6月累计6.2亿(完成率62%)', '2026-06-30');

-- G25 项目进度达成率≥95% (target=95%, actual=88%)
INSERT INTO ecos_goal_tracking (id, goal_id, progress, actual_value, note, recorded_at) VALUES
('gt_prog_01', 25, 93, 93.00, '1月进度正常', '2026-01-31'),
('gt_prog_02', 25, 91, 91.00, '2月略有滞后', '2026-02-28'),
('gt_prog_03', 25, 90, 90.00, '3月华强钢构首次延迟', '2026-03-31'),
('gt_prog_04', 25, 89, 89.00, '4月浙北路桥滞后加剧', '2026-04-30'),
('gt_prog_05', 25, 88, 88.00, '5月持续恶化', '2026-05-31'),
('gt_prog_06', 25, 88, 88.00, '6月进度偏差12%', '2026-06-30');

-- G26 供应商交货准时率≥90% (target=90%, actual=67%)
INSERT INTO ecos_goal_tracking (id, goal_id, progress, actual_value, note, recorded_at) VALUES
('gt_sup_01', 26, 92, 92.00, '1月准时率92%', '2026-01-31'),
('gt_sup_02', 26, 91, 91.00, '2月准时率91%', '2026-02-28'),
('gt_sup_03', 26, 85, 85.00, '3月首次跌破90%', '2026-03-31'),
('gt_sup_04', 26, 67, 67.00, '4月华强钢构产能瓶颈', '2026-04-30'),
('gt_sup_05', 26, 71, 71.00, '5月短暂回升', '2026-05-31'),
('gt_sup_06', 26, 63, 63.00, '6月创新低,供应商风险', '2026-06-30');

-- G27 年度利润8000万 (target=8000万, 按月累计)
INSERT INTO ecos_goal_tracking (id, goal_id, progress, actual_value, note, recorded_at) VALUES
('gt_prf_01', 27, 7, 5600000.00, '1月利润560万', '2026-01-31'),
('gt_prf_02', 27, 13, 10400000.00, '2月累计1040万', '2026-02-28'),
('gt_prf_03', 27, 21, 16800000.00, '3月累计1680万', '2026-03-31'),
('gt_prf_04', 27, 28, 22400000.00, '4月累计2240万', '2026-04-30'),
('gt_prf_05', 27, 39, 31200000.00, '5月累计3120万', '2026-05-31'),
('gt_prf_06', 27, 58, 46500000.00, '6月累计4650万(完成率58%)', '2026-06-30');
