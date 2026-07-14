-- V29: CEO周一晨会场景 — 项目型企业因果链种子数据
-- 4个目标节点 + 3条因果链 + 2个应对场景

-- ═══ 目标节点 ═══
INSERT INTO ecos_wm_goal (name, description, parent_id, target_value, current_value, unit, goal_type, status) VALUES
('年度营收10亿', '2026年度项目型业务营收目标10亿元', NULL, 1000000000.00, 620000000.00, '元', 'FINANCIAL', 'AT_RISK'),
('项目进度达成率≥95%', '所有在建项目整体进度偏差控制在5%以内', NULL, 95.00, 88.00, '%', 'OPERATIONAL', 'AT_RISK'),
('供应商交货准时率≥90%', '核心供应商（华强钢构/建通建材）交货准时率', NULL, 90.00, 67.00, '%', 'SUPPLY_CHAIN', 'CRITICAL'),
('年度利润8000万', '2026年度净利润目标8000万元', NULL, 80000000.00, 46500000.00, '元', 'FINANCIAL', 'AT_RISK');

-- ═══ 因果链 (使用子查询引用目标ID) ═══
-- G3(供应商准时率) → G2(项目进度)
INSERT INTO ecos_wm_causal_link (source_goal_id, target_goal_id, description, relationship_type)
SELECT g3.id, g2.id, '供应商交货准时率影响项目进度偏差：华强钢构准时率降至67%导致浙北路桥滞后12%', 'NEGATIVE'
FROM ecos_wm_goal g3, ecos_wm_goal g2
WHERE g3.name = '供应商交货准时率≥90%' AND g2.name = '项目进度达成率≥95%';

-- G2(项目进度) → G1(营收)
INSERT INTO ecos_wm_causal_link (source_goal_id, target_goal_id, description, relationship_type)
SELECT g2.id, g1.id, '项目进度滞后导致营收确认延迟：浙北路桥少确认1.2亿季度营收', 'POSITIVE'
FROM ecos_wm_goal g2, ecos_wm_goal g1
WHERE g2.name = '项目进度达成率≥95%' AND g1.name = '年度营收10亿';

-- G1(营收) → G4(利润)
INSERT INTO ecos_wm_causal_link (source_goal_id, target_goal_id, description, relationship_type)
SELECT g1.id, g4.id, '营收完成率直接影响利润：当前营收完成62%，利润完成58%', 'POSITIVE'
FROM ecos_wm_goal g1, ecos_wm_goal g4
WHERE g1.name = '年度营收10亿' AND g4.name = '年度利润8000万';

-- ═══ 应对场景 ═══
INSERT INTO ecos_wm_scenario (name, description, config_json) VALUES
('Scenario A: 更换供应商',
 '将浙北路桥钢材供应从华强钢构切换至中联重科，采购成本增加5%（约425万），预计项目进度恢复正常，全年追回损失',
 '{"action":"switch_supplier","cost_impact":4250000,"progress_recovery_pct":12,"risk":"新供应商磨合期1个月"}'),
('Scenario B: 谈判催货',
 '与华强钢构高层谈判，派驻质量监理驻厂，承诺增加次年订单量。成本不变，预计进度部分恢复（+7%），剩余5%延迟',
 '{"action":"negotiate","cost_impact":0,"progress_recovery_pct":7,"risk":"华强钢构产能瓶颈短期难解决"}');
