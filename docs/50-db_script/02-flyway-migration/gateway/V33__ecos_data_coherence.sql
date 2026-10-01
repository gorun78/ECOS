-- V33: Sprint 9 — 种子数据逻辑连贯性加固

-- ═══ 1. ecos_biz_metric 增加 goal_id FK ═══
ALTER TABLE ecos_biz_metric ADD COLUMN IF NOT EXISTS goal_id BIGINT REFERENCES ecos_wm_goal(id);

-- 将指标关联到目标: 营收指标→G24, 利润指标→G27, 准时率→G26
UPDATE ecos_biz_metric SET goal_id = 24 WHERE metric_type = 'revenue';
UPDATE ecos_biz_metric SET goal_id = 27 WHERE metric_type = 'profit';
UPDATE ecos_biz_metric SET goal_id = 26 WHERE metric_type LIKE 'supplier_ontime%';

-- ═══ 2. ecos_biz_target 增加 goal_id FK ═══
ALTER TABLE ecos_biz_target ADD COLUMN IF NOT EXISTS goal_id BIGINT REFERENCES ecos_wm_goal(id);

UPDATE ecos_biz_target SET goal_id = 24 WHERE target_type = 'revenue';
UPDATE ecos_biz_target SET goal_id = 27 WHERE target_type = 'profit';
UPDATE ecos_biz_target SET goal_id = 26 WHERE target_type LIKE '%supplier%' OR target_type = 'collection_rate';

-- ═══ 3. ecos_wm_goal 增加 domain_id ═══
ALTER TABLE ecos_wm_goal ADD COLUMN IF NOT EXISTS domain_id VARCHAR(50) REFERENCES ecos_domain(id);

-- CEO场景目标归类
UPDATE ecos_wm_goal SET domain_id = 'dom_fin' WHERE id IN (24, 27);   -- 营收+利润→财务域
UPDATE ecos_wm_goal SET domain_id = 'dom_proj' WHERE id = 25;          -- 进度→项目域
UPDATE ecos_wm_goal SET domain_id = 'dom_proc' WHERE id = 26;          -- 供应商→采购域

-- ═══ 4. ecos_biz_project 增加 goal_id 关联 ═══
ALTER TABLE ecos_biz_project ADD COLUMN IF NOT EXISTS goal_id BIGINT REFERENCES ecos_wm_goal(id);

UPDATE ecos_biz_project SET goal_id = 25 WHERE id = 'proj_zb';  -- 浙北路桥→进度目标
