-- V28: CEO周一晨会场景 — 项目型企业种子数据
-- 补充到已有ecos_biz表中，ON CONFLICT确保不重复
-- 3部门(工程/采购/财务) + 3项目(浙北路桥/湘江新城/赣深高铁) + 5合同 + 12月指标 + 年度目标

-- ═══ 部门 ═══
INSERT INTO ecos_biz_department (id, name, manager, parent_id) VALUES
('dept_eng', '工程部', '赵刚', NULL),
('dept_proc', '采购部', '钱敏', NULL),
('dept_fin', '财务部', '陈明', NULL)
ON CONFLICT (id) DO NOTHING;

-- ═══ 项目 ═══
-- 浙北路桥: 进度滞后12%，供应商华强钢构
-- 湘江新城: 正常进展
-- 赣深高铁: 超前8%
INSERT INTO ecos_biz_project (id, name, project_type, dept_id, customer_name, contract_amount, status, start_date, end_date, manager) VALUES
('proj_zb', '浙北路桥', 'production', 'dept_eng', '浙江交通集团', 450000000.00, 'in_progress', '2025-03-01', '2027-06-30', '周杰'),
('proj_xx', '湘江新城', 'production', 'dept_eng', '长沙城投', 280000000.00, 'in_progress', '2025-06-01', '2027-12-31', '吴磊'),
('proj_gs', '赣深高铁', 'production', 'dept_eng', '中铁建', 620000000.00, 'in_progress', '2024-09-01', '2027-03-31', '郑凯')
ON CONFLICT (id) DO NOTHING;

-- ═══ 合同 ═══
-- 3份收入合同 + 2份采购合同
INSERT INTO ecos_biz_contract (id, contract_no, contract_type, project_id, party_name, amount, signed_date, status) VALUES
('ctr_zb_01', 'ZB-2025-001', 'income', 'proj_zb', '浙江交通集团', 450000000.00, '2025-02-15', 'active'),
('ctr_xx_01', 'XX-2025-001', 'income', 'proj_xx', '长沙城投', 280000000.00, '2025-05-20', 'active'),
('ctr_gs_01', 'GS-2024-001', 'income', 'proj_gs', '中铁建', 620000000.00, '2024-08-10', 'active'),
('ctr_zb_p01', 'ZB-PROC-2025-001', 'procurement', 'proj_zb', '华强钢构', 85000000.00, '2025-03-01', 'active'),
('ctr_gs_p01', 'GS-PROC-2025-001', 'procurement', 'proj_gs', '建通建材', 120000000.00, '2025-01-15', 'active')
ON CONFLICT (id) DO NOTHING;

-- ═══ 经营指标 2026.01-2026.12 ═══
-- 3类指标: revenue(营收)/profit(利润)/collection_rate(回款率)/supplier_ontime(供应商准时率)
INSERT INTO ecos_biz_metric (id, dept_id, metric_type, metric_value, target_value, metric_month) VALUES
-- 营收 (前6月实际 + 后6月预测)
('m_rev_01','dept_eng','revenue',62000000.00,80000000.00,'2026-01'),
('m_rev_02','dept_eng','revenue',58000000.00,80000000.00,'2026-02'),
('m_rev_03','dept_eng','revenue',71000000.00,80000000.00,'2026-03'),
('m_rev_04','dept_eng','revenue',55000000.00,80000000.00,'2026-04'),
('m_rev_05','dept_eng','revenue',68000000.00,80000000.00,'2026-05'),
('m_rev_06','dept_eng','revenue',49000000.00,80000000.00,'2026-06'),
('m_rev_07','dept_eng','revenue',72000000.00,80000000.00,'2026-07'),
('m_rev_08','dept_eng','revenue',78000000.00,80000000.00,'2026-08'),
('m_rev_09','dept_eng','revenue',85000000.00,80000000.00,'2026-09'),
('m_rev_10','dept_eng','revenue',90000000.00,80000000.00,'2026-10'),
('m_rev_11','dept_eng','revenue',95000000.00,80000000.00,'2026-11'),
('m_rev_12','dept_eng','revenue',100000000.00,80000000.00,'2026-12'),
-- 利润
('m_prf_01','dept_eng','profit',4800000.00,6500000.00,'2026-01'),
('m_prf_02','dept_eng','profit',4200000.00,6500000.00,'2026-02'),
('m_prf_03','dept_eng','profit',5500000.00,6500000.00,'2026-03'),
('m_prf_04','dept_eng','profit',3900000.00,6500000.00,'2026-04'),
('m_prf_05','dept_eng','profit',5100000.00,6500000.00,'2026-05'),
('m_prf_06','dept_eng','profit',3600000.00,6500000.00,'2026-06'),
-- 回款率
('m_clr_01','dept_fin','collection_rate',82.00,85.00,'2026-01'),
('m_clr_02','dept_fin','collection_rate',78.00,85.00,'2026-02'),
('m_clr_03','dept_fin','collection_rate',84.00,85.00,'2026-03'),
('m_clr_04','dept_fin','collection_rate',76.00,85.00,'2026-04'),
('m_clr_05','dept_fin','collection_rate',80.00,85.00,'2026-05'),
('m_clr_06','dept_fin','collection_rate',73.00,85.00,'2026-06'),
-- 华强钢构交货准时率 (近3个月急剧下降: 67%/71%/63%)
('m_sup_04','dept_proc','supplier_ontime_rate_hq',67.00,90.00,'2026-04'),
('m_sup_05','dept_proc','supplier_ontime_rate_hq',71.00,90.00,'2026-05'),
('m_sup_06','dept_proc','supplier_ontime_rate_hq',63.00,90.00,'2026-06'),
-- 建通建材准时率 (正常)
('m_sup_04_jt','dept_proc','supplier_ontime_rate_jt',91.00,90.00,'2026-04'),
('m_sup_05_jt','dept_proc','supplier_ontime_rate_jt',93.00,90.00,'2026-05'),
('m_sup_06_jt','dept_proc','supplier_ontime_rate_jt',89.00,90.00,'2026-06')
ON CONFLICT (id) DO NOTHING;

-- ═══ 年度目标 ═══
INSERT INTO ecos_biz_target (id, dept_id, target_type, target_value, target_year) VALUES
('tgt_rev_2026','dept_eng','revenue',1000000000.00,2026),
('tgt_prf_2026','dept_eng','profit',80000000.00,2026),
('tgt_clr_2026','dept_fin','collection_rate',85.00,2026)
ON CONFLICT (id) DO NOTHING;
