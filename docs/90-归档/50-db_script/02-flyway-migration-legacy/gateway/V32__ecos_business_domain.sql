-- V32: Sprint 9 — 业务域种子数据 + 本体/术语domain_id

-- ═══ 1. 业务域种子 ═══
INSERT INTO ecos_domain (id, code, name, owner, description, status, sort_order) VALUES
('dom_proc', 'Procurement', '采购域', 'admin', '供应商管理、采购合同、招投标、交货准时率', 'Active', 10),
('dom_fin', 'Finance', '财务域', 'admin', '营收、利润、回款率、成本核算、预算管理', 'Active', 20),
('dom_hr', 'HR', '人资域', 'admin', '组织架构、人员管理、绩效考核', 'Active', 30),
('dom_proj', 'Project', '项目域', 'admin', '工程项目、进度管理、产值统计、合同管理', 'Active', 40),
('dom_asset', 'Asset', '资产域', 'admin', '设备管理、设施巡检、资产管理', 'Active', 50)
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name, status='Active';

-- ═══ 2. 本体实体增加 domain_id ═══
ALTER TABLE ecos_ontology_entity ADD COLUMN IF NOT EXISTS domain_id VARCHAR(50) REFERENCES ecos_domain(id);

-- 将现有本体实体按语义归类到业务域
UPDATE ecos_ontology_entity SET domain_id = 'dom_proj' WHERE code IN ('Project', 'Invoice');
UPDATE ecos_ontology_entity SET domain_id = 'dom_proc' WHERE code IN ('Supplier', 'Order', 'Product');
UPDATE ecos_ontology_entity SET domain_id = 'dom_asset' WHERE code IN ('Equipment', 'Facility', 'Inspection', 'RoadSection');
UPDATE ecos_ontology_entity SET domain_id = 'dom_fin' WHERE code = 'Alert';

-- ═══ 3. 智库术语表增加 domain_id ═══
ALTER TABLE ecos_business_glossary ADD COLUMN IF NOT EXISTS domain_id VARCHAR(50) REFERENCES ecos_domain(id);
ALTER TABLE ecos_glossary_term ADD COLUMN IF NOT EXISTS domain_id VARCHAR(50) REFERENCES ecos_domain(id);
