-- =============================================================================
-- entmgmt_demo_seed_v2_w5_ontology.sql
-- 二期 W5 · 域2 本体工作台「定义面」初始化（企业经营管控场景）
--
-- 覆盖：本体域切分 → 对象/属性/关系 → 术语 → 指标(绑口径) → 规则 → action 定义
--       → 数据映射（指向域 1 CURATED 治理层四表，field_mappings 契约形态）→ 接口登记
--
-- 契约依据（2026-10-17 information_schema / 源码实读，非推定）：
--   * metric 活动表 = ecos_ontology.ecos_metric_definition（CaliberDao.java:179 SELECT）；
--     metric_definition 旧表 R-4 裁决停写（OntologyNoForeignTableAccessArchTest 注释实证）⇒ 不灌。
--   * action_definition(id,code,name,type,config jsonb,created_at) = ActionTypeDao:71 读契约。
--   * graph 活动套 = ecos_knowledge.graph_node/edge（KnowledgeNodeMapper @Insert 实证）⇒ 域3 脚本灌。
--   * field_mappings JSONB = {"datasetId": resource_id, "fieldMappings":[{"source","target"}]}
--     （OntologyMappingService.toTarget:293-317 解析契约；source=属性 code，target=DW 实列）。
--   * glossary id 实列为 bigint（非 VARCHAR36）⇒ 用固定序号 9100xxx，MC01 不适用既有形态。
--   * 一期保留行：ecos_ontology_entity 3 行(ent_project/ent_contract/ent_cost_item) 不删，
--     UPDATE 补 domain_id；ecos_caliber 4 行 + ecos_metric_definition 4 行沿用为一期成果。
--
-- 五层联动：本脚本映射目标 = W4 seed 的 CURATED 四资源
--   res_entmgmt_cur_stage / cur_cost / cur_resource / cur_attribution（td_data_resource 实查）。
--
-- 禁 seed（红线）：action 执行结果(ecos_application_action_outcome)、抽取产物、
--   版本快照流水。本脚本只落「定义面」。
--
-- 幂等：按 ent2_ / entmgmt 命名空间先删后插；一期行豁免。可重复执行。
-- 跑法：docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 -f - < 本文件
-- =============================================================================

BEGIN;

-- ---------- 0. 清理（按本批 seed 标记删除，一期 3 entity/4 caliber/4 metric 与一期属性面不动） ----------
-- 注意：本批属性/映射的 code 不带 ent2_ 前缀（要挂到一期对象上），幂等键只能用 seed 标记/显式 code 清单。
DELETE FROM ecos_ontology.ecos_ontology_property        WHERE domain='entmgmt' AND create_by='seed-entmgmt';
DELETE FROM ecos_ontology.ecos_ontology_relationship    WHERE domain='entmgmt' AND (source_entity_id IN (SELECT id FROM ecos_ontology.ecos_ontology_entity WHERE code LIKE 'ent2_%') OR code LIKE 'ent2_%');
DELETE FROM ecos_ontology.ecos_ontology_rule            WHERE domain='entmgmt' AND code LIKE 'ent2_%';
DELETE FROM ecos_ontology.ecos_metric_definition        WHERE domain='entmgmt' AND code LIKE 'ent2_%';
DELETE FROM ecos_ontology.ecos_glossary_term            WHERE domain='entmgmt' AND created_by='seed-entmgmt';
DELETE FROM ecos_ontology.action_definition             WHERE code LIKE 'ent2_%';
DELETE FROM ecos_ontology.ecos_entity_table_mapping     WHERE domain='entmgmt' AND entity_code IN ('ent2_output_stage','ent2_timesheet','ent_cost_item','ent_project');
DELETE FROM ecos_ontology.ecos_ontology_domain          WHERE code IN ('ont_entmgmt','ent2_contract','ent2_costfund','ent2_capacity','ent2_perf');
DELETE FROM ecos_ontology.ecos_ontology_entity          WHERE domain='entmgmt' AND code LIKE 'ent2_%';
DELETE FROM ecos_control.ecos_interface_ref             WHERE id LIKE 'if_ent2_%';

-- ---------- 1. 本体域：根域 + 4 业务域 ----------
INSERT INTO ecos_ontology.ecos_ontology_domain
    (id, code, name, owner, description, status, sort_order, tenant_id, create_time, update_time, create_by, update_by, is_deleted)
VALUES
    (md5('seed:entmgmt-v2:dom:ont_entmgmt'), 'ont_entmgmt',   '企业经营管控（根域）', 'role_ent2_exec',
     '一期 3 实体悬挂的根域补位（规划册 域2 D 项）：集团经营管控本体总域', 'ACTIVE', 0, 'tenant-a', now(), now(), 'seed-entmgmt', 'seed-entmgmt', 0),
    (md5('seed:entmgmt-v2:dom:ent2_contract'), 'ent2_contract', '合同履约域', 'role_ent2_proj_mgr',
     '项目-合同-客户-分包的履约链对象与口径', 'ACTIVE', 1, 'tenant-a', now(), now(), 'seed-entmgmt', 'seed-entmgmt', 0),
    (md5('seed:entmgmt-v2:dom:ent2_costfund'), 'ent2_costfund', '成本资金域', 'role_ent2_group_fin',
     '成本归集、分摊规则、资金计划与回款', 'ACTIVE', 2, 'tenant-a', now(), now(), 'seed-entmgmt', 'seed-entmgmt', 0),
    (md5('seed:entmgmt-v2:dom:ent2_capacity'), 'ent2_capacity', '资源产能域', 'role_ent2_seg_gm',
     '人员、工时与产能投入', 'ACTIVE', 3, 'tenant-a', now(), now(), 'seed-entmgmt', 'seed-entmgmt', 0),
    (md5('seed:entmgmt-v2:dom:ent2_perf'),     'ent2_perf',     '经营绩效域', 'role_ent2_exec',
     '产值确认、期间关账与绩效指标', 'ACTIVE', 4, 'tenant-a', now(), now(), 'seed-entmgmt', 'seed-entmgmt', 0);

-- ---------- 2. 对象：一期 3 行补 domain_id，新增 9 行（共 12 对象） ----------
UPDATE ecos_ontology.ecos_ontology_entity e
   SET domain_id = d.id, update_by = 'seed-entmgmt', update_time = now()
  FROM ecos_ontology.ecos_ontology_domain d
 WHERE d.code = 'ont_entmgmt' AND e.ontology_id = 'ont_entmgmt' AND e.domain='entmgmt';

INSERT INTO ecos_ontology.ecos_ontology_entity
    (id, ontology_id, code, name, description, entity_type, sort_order, created_at, updated_at,
     domain_id, tenant_id, is_deleted, status, create_time, update_time, create_by, version_no, domain)
SELECT md5('seed:entmgmt-v2:ent:'||v.code), 'ont_entmgmt', v.code, v.name, v.descr, 'BUSINESS', v.ord, now(), now(),
       d.id, 'tenant-a', 0, 'ACTIVE', now(), now(), 'seed-entmgmt', 'v1', 'entmgmt'
  FROM (VALUES
    ('ent2_customer',    '客户',        '业主客户主数据，履约与回款的对方主体', 10),
    ('ent2_subcontract', '分包合同',    '对下游分包方的采购/分包合约', 11),
    ('ent2_fund_plan',   '资金计划',    '按期间的计划回款与计划支出', 12),
    ('ent2_collection',  '回款单',      '实际到账流水（收付实现制载体）', 13),
    ('ent2_staff',       '人员',        '集团在册经营人员，对应域0 IAM 用户', 14),
    ('ent2_timesheet',   '工时填报',    '人员-项目-期间的投入工时与费率', 15),
    ('ent2_output_stage','产值确认阶段','权责发生制的阶段验收确认记录', 16),
    ('ent2_period',      '会计期间',    '关账状态的期间维度', 17),
    ('ent2_org_unit',    '组织单元',    '板块公司/部门/项目部三级归口', 18)
  ) AS v(code, name, descr, ord)
  JOIN ecos_ontology.ecos_ontology_domain d ON d.code = 'ont_entmgmt';

-- ---------- 3. 属性（41 条；function_type 属性带 EXPRESSION/AGGREGATION） ----------
INSERT INTO ecos_ontology.ecos_ontology_property
    (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order, created_at,
     function_type, function_expression, is_deleted, status, create_time, update_time, create_by, version_no, domain)
SELECT md5('seed:entmgmt-v2:prop:'||v.ecode||':'||v.pcode), e.id, v.pcode, v.pname, v.ptype,
       v.req, 1, v.ord, now(), v.ftype, v.fexpr, 0, 'ACTIVE', now(), now(), 'seed-entmgmt', 'v1', 'entmgmt'
  FROM (VALUES
    -- 一期三对象补属性挂现有 id（code 前缀仍 ent2_ 之外的命名，豁免清理仅按 code LIKE 'ent2_%'，此处用 legacy 前缀 prop_）
    ('ent_project','project_code','项目编号','STRING',1,0,NULL,NULL,1),
    ('ent_project','project_name','项目名称','STRING',1,0,NULL,NULL,2),
    ('ent_project','segment','所属板块','STRING',1,0,NULL,NULL,3),
    ('ent_project','lifecycle_status','生命周期状态','STRING',1,0,NULL,NULL,4),
    ('ent_contract','contract_no','合同编号','STRING',1,0,NULL,NULL,1),
    ('ent_contract','contract_amount','合同额','DECIMAL',1,0,NULL,NULL,2),
    ('ent_contract','sign_date','签约日期','DATE',1,0,NULL,NULL,3),
    ('ent_contract','counterparty','合同对方','STRING',0,0,NULL,NULL,4),
    ('ent_cost_item','cost_category','成本科目','STRING',1,0,NULL,NULL,1),
    ('ent_cost_item','direct_or_allocated','直接/分摊','STRING',1,0,NULL,NULL,2),
    ('ent_cost_item','allocation_rule_ref','分摊规则引用','STRING',0,0,NULL,NULL,3),
    ('ent2_customer','customer_code','客户编码','STRING',1,0,NULL,NULL,1),
    ('ent2_customer','customer_name','客户名称','STRING',1,0,NULL,NULL,2),
    ('ent2_customer','rating','客户评级','STRING',0,0,NULL,NULL,3),
    ('ent2_subcontract','sub_contract_no','分包合同号','STRING',1,0,NULL,NULL,1),
    ('ent2_subcontract','sub_amount','分包金额','DECIMAL',1,0,NULL,NULL,2),
    ('ent2_subcontract','vendor_name','分包方','STRING',1,0,NULL,NULL,3),
    ('ent2_fund_plan','plan_period','计划期间','STRING',1,0,NULL,NULL,1),
    ('ent2_fund_plan','planned_collection','计划回款','DECIMAL',1,0,NULL,NULL,2),
    ('ent2_fund_plan','planned_payment','计划支出','DECIMAL',1,0,NULL,NULL,3),
    ('ent2_collection','flow_no','回款流水号','STRING',1,0,NULL,NULL,1),
    ('ent2_collection','collection_amount','回款金额','DECIMAL',1,0,NULL,NULL,2),
    ('ent2_collection','collection_date','到账日期','DATE',1,0,NULL,NULL,3),
    ('ent2_staff','staff_id','人员编号','STRING',1,0,NULL,NULL,1),
    ('ent2_staff','staff_name','姓名','STRING',1,0,NULL,NULL,2),
    ('ent2_staff','org_unit','归口组织','STRING',1,0,NULL,NULL,3),
    ('ent2_staff','job_role','岗位角色','STRING',0,0,NULL,NULL,4),
    ('ent2_timesheet','report_period','填报期间','STRING',1,0,NULL,NULL,1),
    ('ent2_timesheet','fte','FTE 投入','DECIMAL',1,0,NULL,NULL,2),
    ('ent2_timesheet','work_hours','工时','DECIMAL',1,0,NULL,NULL,3),
    ('ent2_timesheet','hourly_rate','小时费率','DECIMAL',1,0,NULL,NULL,4),
    ('ent2_output_stage','stage_type','阶段类型','STRING',1,0,NULL,NULL,1),
    ('ent2_output_stage','realization_rate','实现率','DECIMAL',1,1,'EXPRESSION','IF(contract_base > 0, amount / contract_base, 0)',2),
    ('ent2_output_stage','confirmed_amount','确认金额','DECIMAL',1,0,NULL,NULL,3),
    ('ent2_output_stage','period','所属期间','STRING',1,0,NULL,NULL,4),
    ('ent2_period','period_key','期间键(YYYY-MM)','STRING',1,0,NULL,NULL,1),
    ('ent2_period','close_status','关账状态','STRING',1,0,NULL,NULL,2),
    ('ent2_org_unit','org_code','组织编码','STRING',1,0,NULL,NULL,1),
    ('ent2_org_unit','org_name','组织名称','STRING',1,0,NULL,NULL,2),
    ('ent2_org_unit','org_level','组织层级','STRING',1,0,NULL,NULL,3),
    ('ent2_org_unit','parent_org_code','上级组织','STRING',0,0,NULL,NULL,4)
  ) AS v(ecode, pcode, pname, ptype, req, has_func, ftype, fexpr, ord)
  JOIN ecos_ontology.ecos_ontology_entity e ON e.code = v.ecode AND e.domain='entmgmt';

-- ---------- 3b. 映射侧属性（13）+ 函数属性（4）：让 §9 每条 fieldMappings 的 source 都是本对象真实属性 ----------
-- source 形态契约 = <本映射 entity_code>.<本对象 property_code>（跨对象/跨关系引用属悬挂，OntologyMappingService 不解析）
INSERT INTO ecos_ontology.ecos_ontology_property
    (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order, created_at,
     function_type, function_expression, is_deleted, status, create_time, update_time, create_by, version_no, domain)
SELECT md5('seed:entmgmt-v2:prop:'||v.ecode||':'||v.pcode), e.id, v.pcode, v.pname, v.ptype,
       v.req, 1, v.ord, now(), v.ftype, v.fexpr, 0, 'ACTIVE', now(), now(), 'seed-entmgmt', 'v1', 'entmgmt'
  FROM (VALUES
    ('ent2_output_stage','project_ref',      '项目标识',     'STRING', 1,0,NULL,NULL,5),
    ('ent2_output_stage','contract_base',    '合同基数',     'DECIMAL',1,0,NULL,NULL,6),
    ('ent2_output_stage','attribution_ratio','归属比例',     'DECIMAL',0,0,NULL,NULL,7),
    ('ent_cost_item',    'cost_amount',      '归集金额',     'DECIMAL',1,0,NULL,NULL,4),
    ('ent_cost_item',    'period',           '所属期间',     'STRING', 1,0,NULL,NULL,5),
    ('ent_cost_item',    'project_ref',      '项目标识',     'STRING', 1,0,NULL,NULL,6),
    ('ent_cost_item',    'is_pool',          '是否公共成本池','STRING', 0,0,NULL,NULL,7),
    ('ent2_timesheet',   'project_ref',      '项目标识',     'STRING', 1,0,NULL,NULL,5),
    ('ent2_timesheet',   'staff_ref',        '人员标识',     'STRING', 1,0,NULL,NULL,6),
    ('ent_project',      'department_ref',   '归口部门',     'STRING', 1,0,NULL,NULL,5),
    ('ent_project',      'attribution_ratio','归属比例',     'DECIMAL',1,0,NULL,NULL,6),
    ('ent_project',      'attribution_type', '归属类型',     'STRING', 1,0,NULL,NULL,7),
    ('ent_project',      'contract_ref',     '主合同标识',   'STRING', 0,0,NULL,NULL,8),
    -- 函数属性：表达式只用已验白名单 SUM/ROUND/MIN/MAX/IF（CaliberDslParser 文法）
    ('ent_project',      'output_total',     '产值合计(函数)', 'DECIMAL',0,1,'AGGREGATION','SUM(confirmed_amount)',9),
    ('ent_project',      'cost_total',       '成本合计(函数)', 'DECIMAL',0,1,'AGGREGATION','SUM(cost_amount)',10),
    ('ent_project',      'profit_amount',    '经营毛利(函数)', 'DECIMAL',0,1,'EXPRESSION','IF(output_total > 0, output_total - cost_total, 0)',11),
    ('ent2_fund_plan',   'planned_gap',      '计划资金缺口(函数)','DECIMAL',0,1,'EXPRESSION','IF(planned_collection > planned_payment, planned_collection - planned_payment, 0)',4)
  ) AS v(ecode, pcode, pname, ptype, req, has_func, ftype, fexpr, ord)
  JOIN ecos_ontology.ecos_ontology_entity e ON e.code = v.ecode AND e.domain='entmgmt';

-- ---------- 4. 关系（10 条） ----------
INSERT INTO ecos_ontology.ecos_ontology_relationship
    (id, source_entity_id, target_entity_id, code, name, relationship_type, created_at,
     is_deleted, status, create_time, update_time, create_by, version_no, domain)
SELECT md5('seed:entmgmt-v2:rel:'||v.code), s.id, t.id, v.code, v.name, v.rtype, now(),
       0, 'ACTIVE', now(), now(), 'seed-entmgmt', 'v1', 'entmgmt'
  FROM (VALUES
    ('ent2_rel_proj_contract',   'ent_project',       'ent_contract',     '签订合同', 'ASSOCIATION'),
    ('ent2_rel_proj_cost',       'ent_project',       'ent_cost_item',    '归集成本', 'ASSOCIATION'),
    ('ent2_rel_proj_customer',   'ent_project',       'ent2_customer',    '服务业主', 'ASSOCIATION'),
    ('ent2_rel_proj_subcontract','ent_project',       'ent2_subcontract', '分包给',   'ASSOCIATION'),
    ('ent2_rel_proj_stage',      'ent_project',       'ent2_output_stage','确认产值', 'ASSOCIATION'),
    ('ent2_rel_proj_timesheet',  'ent_project',       'ent2_timesheet',   '消耗工时', 'ASSOCIATION'),
    ('ent2_rel_collection_contract','ent2_collection','ent_contract',     '回款对应', 'ASSOCIATION'),
    ('ent2_rel_fundplan_period', 'ent2_fund_plan',    'ent2_period',      '计划落于', 'ASSOCIATION'),
    ('ent2_rel_cost_org',        'ent_cost_item',     'ent2_org_unit',    '成本归口', 'ASSOCIATION'),
    ('ent2_rel_proj_org',        'ent_project',       'ent2_org_unit',    '项目归属', 'ASSOCIATION')
  ) AS v(code, scode, tcode, name, rtype)
  JOIN ecos_ontology.ecos_ontology_entity s ON s.code = v.scode AND s.domain='entmgmt'
  JOIN ecos_ontology.ecos_ontology_entity t ON t.code = v.tcode AND t.domain='entmgmt';

-- ---------- 5. 术语（24 条；id=bigint 固定序号） ----------
INSERT INTO ecos_ontology.ecos_glossary_term
    (id, code, name, definition, domain, owner, status, created_by, created_at, updated_at, tenant_id, is_deleted, version_no)
VALUES
    (9100001,'ent2_term_output','产值确认','权责发生制下按阶段验收口径确认的工程产出金额（对应指标 accrual_revenue_amount）','entmgmt','role_ent2_exec','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100002,'ent2_term_accrual','权责发生制','以权利义务发生期间归集收入/成本的核算基准，区别于到账口径','entmgmt','role_ent2_group_fin','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100003,'ent2_term_cash','收付实现制','以现金实际收付期间归集收入/成本的核算基准','entmgmt','role_ent2_group_fin','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100004,'ent2_term_realization','实现率','阶段确认金额与合同基数之比，衡量履约转化进度','entmgmt','role_ent2_exec','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100005,'ent2_term_collection','回款','业主按合同节点实际支付的到账金额','entmgmt','role_ent2_group_fin','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100006,'ent2_term_costpool','成本池','不能直接归属单一项目、需按规则分摊的公共成本集合','entmgmt','role_ent2_group_fin','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100007,'ent2_term_pubpool','公共成本池','总部职能与共享服务的公共成本，按归口/工时规则二次分摊','entmgmt','role_ent2_group_fin','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100008,'ent2_term_guikou','归口','成本、项目或事项对应的责任组织单元','entmgmt','role_ent2_seg_gm','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100009,'ent2_term_alloc','分摊','把共享资源成本按规则分配到项目/板块的过程','entmgmt','role_ent2_group_fin','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100010,'ent2_term_fte','FTE','全时当量：以标准工时为 1 的人力投入折算单位','entmgmt','role_ent2_proj_mgr','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100011,'ent2_term_rate','小时费率','人员单位工时的内部核算价格','entmgmt','role_ent2_proj_mgr','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100012,'ent2_term_lvyue','履约','按合同节点完成交付义务的过程与状态','entmgmt','role_ent2_proj_mgr','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100013,'ent2_term_period','账期','会计核算的自然月期间键（YYYY-MM）','entmgmt','role_ent2_group_fin','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100014,'ent2_term_close','关账','锁定某期间事实数据、停止追加的操作（平台端点 period-close）','entmgmt','role_ent2_group_fin','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100015,'ent2_term_budget','预算','期间计划收入/支出基线，经营偏差分析的对照面','entmgmt','role_ent2_exec','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100016,'ent2_term_profit','经营毛利','验收确认额与归集成本之差（口径 operating_profit_amount）','entmgmt','role_ent2_exec','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100017,'ent2_term_segment','板块','集团下按业务线划分的事业部公司（基础设施/能源/房建）','entmgmt','role_ent2_seg_gm','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100018,'ent2_term_bu','事业部','板块公司的经营单元称谓，对应组织树 COMPANY 层','entmgmt','role_ent2_seg_gm','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100019,'ent2_term_pm','项目部','最小交付与成本归口单元，对应组织树 DEPARTMENT/PMO','entmgmt','role_ent2_proj_mgr','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100020,'ent2_term_master','主数据','项目/合同/人员等跨域共享的基准实体记录','entmgmt','role_ent2_data_gov','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100021,'ent2_term_nearsource','近源层','数据湖 RAW 层：源文件原样入湖的结构化载体','entmgmt','role_ent2_data_gov','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100022,'ent2_term_curated','治理层','数据湖 CURATED 层：经 DQ 门禁的事实表（ecos_dw.ecos_biz_*）','entmgmt','role_ent2_data_gov','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100023,'ent2_term_caliber','口径','指标的精确计算定义（表达式+维度+限定符），经 DSL 四关审批','entmgmt','role_ent2_onto_modeler','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1'),
    (9100024,'ent2_term_asset','数据资产','经目录登记、可授权消费的数据产品（域1 asset 四行）','entmgmt','role_ent2_data_gov','ACTIVE','seed-entmgmt',now(),now(),'tenant-a',0,'v1');

-- ---------- 6. 指标（新增 8：4 条 PUBLISHED 绑一期已过四关口径，4 条 DRAFT） ----------
INSERT INTO ecos_ontology.ecos_metric_definition
    (id, code, name, expression, aggregation, entity_code, caliber_id, formula_version, caliber_snapshot,
     status, create_time, update_time, create_by, update_by, is_deleted, version_no, domain)
SELECT md5('seed:entmgmt-v2:met:'||v.code), v.code, v.name, v.expr, v.agg,
       v.ecode, c.id, CASE WHEN v.status='PUBLISHED' THEN 'v1' ELSE NULL END,
       CASE WHEN v.status='PUBLISHED' THEN c.name || '（一期四关口径引用）' ELSE NULL END,
       v.status, now(), now(), 'seed-entmgmt', 'seed-entmgmt', 0, 'v1', 'entmgmt'
  FROM (VALUES
    ('ent2_m_stage_output_sum',   '阶段确认额合计',   'SUM(confirmed_amount)',      'SUM',   'ent2_output_stage', 'accrual_revenue_amount', 'PUBLISHED'),
    ('ent2_m_collection_sum',     '期间回款合计',     'SUM(collection_amount)',     'SUM',   'ent2_collection',   'cash_collection_amount', 'PUBLISHED'),
    ('ent2_m_profit_by_segment',  '板块经营毛利',     'accrual_revenue_amount - allocated_cost', 'DERIVED', 'ent_project', 'operating_profit_amount', 'PUBLISHED'),
    ('ent2_m_realization_by_proj','项目综合实现率',   'ent_realization_ratio',      'RATIO', 'ent_project',       'ent_realization_ratio',  'PUBLISHED'),
    ('ent2_m_cost_to_output',     '成本产值比',       'allocated_cost / confirmed_output', 'RATIO', 'ent_project', NULL, 'DRAFT'),
    ('ent2_m_fte_utilization',    '人力利用率',       'SUM(fte) / staff_count',     'RATIO', 'ent2_staff',        NULL, 'DRAFT'),
    ('ent2_m_collection_aging',   '回款账龄天数',     'AVG(collection_date - node_due_date)', 'AVG', 'ent2_collection', NULL, 'DRAFT'),
    ('ent2_m_overdue_ratio',      '逾期回款占比',     'overdue_amount / receivable_amount',   'RATIO', 'ent2_collection', NULL, 'DRAFT')
  ) AS v(code, name, expr, agg, ecode, calcode, status)
  LEFT JOIN ecos_ontology.ecos_caliber c ON c.code = v.calcode AND c.domain='entmgmt';

-- ---------- 6b. 一期 4 指标补关联对象（一期 seed 遗留 entity_code=NULL，本批只做加法补齐对接） ----------
UPDATE ecos_ontology.ecos_metric_definition SET entity_code = 'ent2_output_stage', update_by='seed-entmgmt', update_time=now()
 WHERE domain='entmgmt' AND code='accrual_revenue_amount' AND entity_code IS NULL;
UPDATE ecos_ontology.ecos_metric_definition SET entity_code = 'ent2_collection', update_by='seed-entmgmt', update_time=now()
 WHERE domain='entmgmt' AND code='cash_collection_amount' AND entity_code IS NULL;
UPDATE ecos_ontology.ecos_metric_definition SET entity_code = 'ent_project', update_by='seed-entmgmt', update_time=now()
 WHERE domain='entmgmt' AND code IN ('operating_profit_amount','ent_realization_ratio') AND entity_code IS NULL;

-- ---------- 7. 规则（8 条，挂真实 entity id） ----------
INSERT INTO ecos_ontology.ecos_ontology_rule
    (id, entity_id, code, name, rule_type, expression, action, priority, enabled, description,
     status, create_time, update_time, create_by, update_by, is_deleted, version_no, domain)
SELECT md5('seed:entmgmt-v2:rule:'||v.code), e.id, v.code, v.name, v.rtype, v.expr, v.act, v.pri, 1, v.descr,
       'ACTIVE', now(), now(), 'seed-entmgmt', 'seed-entmgmt', 0, 'v1', 'entmgmt'
  FROM (VALUES
    ('ent2_r_stage_range',     'ent2_output_stage','VALIDATION','阶段确认额区间校验','confirmed_amount >= 0 AND confirmed_amount <= contract_amount','REJECT',1,'确认额不得超过合同基数'),
    ('ent2_r_rate_range',      'ent2_output_stage','VALIDATION','实现率区间校验','0 <= realization_rate AND realization_rate <= 1','REJECT',2,'实现率必须在 [0,1]'),
    ('ent2_r_period_fmt',      'ent2_period',      'VALIDATION','期间格式校验','regexp_matches(period_key, ''^[0-9]{4}-(0[1-9]|1[0-2])$'')','REJECT',1,'期间键必须 YYYY-MM'),
    ('ent2_r_close_immutable', 'ent2_period',      'POLICY',    '关账后禁改','close_status = ''CLOSED'' => facts immutable','BLOCK_WRITE',1,'CLOSED 期间事实行不可追加/修改'),
    ('ent2_r_ratio_sum',       'ent2_org_unit',    'VALIDATION','归属比例合计校验','SUM(attribution_ratio) per project <= 1.000001','WARN',3,'项目归属比例合计应为 1'),
    ('ent2_r_cost_enum',       'ent_cost_item',    'VALIDATION','成本科目枚举校验','cost_category IN (人工,分包,材料,机械,间接,其他)','REJECT',2,'科目必须落在词表'),
    ('ent2_r_fte_range',       'ent2_timesheet',   'VALIDATION','FTE 上限校验','0 <= fte AND fte <= 1.0','REJECT',1,'单人单期 FTE 不超 1'),
    ('ent2_r_derive_realization','ent2_output_stage','DERIVATION','实现率推导','realization_rate = confirmed_amount / contract_base','COMPUTE',4,'按公式属性推导')
  ) AS v(code, ecode, rtype, name, expr, act, pri, descr)
  JOIN ecos_ontology.ecos_ontology_entity e ON e.code = v.ecode AND e.domain='entmgmt';

-- ---------- 8. Action 定义（6 条；结果面禁 seed，只落定义） ----------
INSERT INTO ecos_ontology.action_definition (id, code, name, type, config, created_at)
SELECT md5('seed:entmgmt-v2:act:'||v.code), v.code, v.name, v.atype,
       ('{"endpoint":"'||v.ep||'","method":"POST","permission":"perm_ent2_act_x","requiresConfirm":'||v.confirm||'}')::jsonb,
       now()
  FROM (VALUES
    ('ent2_a_run_forecast','发起经营预测','API','/api/v1/workspace/scenarios/sc_entmgmt/forecast-runs/submit','true'),
    ('ent2_a_close_period','执行期间关账','API','/api/v1/data/periods/close','true'),
    ('ent2_a_run_dq_batch','触发质量批次','API','/api/v1/ecos/dq/schedules/dqsch_entmgmt_dw/run','false'),
    ('ent2_a_export_ledger','导出台账报表','REPORT','/api/v1/data/facts/export','false'),
    ('ent2_a_publish_mapping','发布数据映射','WORKFLOW','/api/v1/ontology/mappings/publish','true'),
    ('ent2_a_create_workorder','创建 DQ 问题工单','WORKFLOW','/api/v1/ecos/dq/work-orders','false')
  ) AS v(code, name, atype, ep, confirm);

-- ---------- 9. 数据映射（4 条 → 域1 CURATED 四表；field_mappings 按 OntologyMappingService 契约） ----------
INSERT INTO ecos_ontology.ecos_entity_table_mapping
    (id, entity_code, entity_name, domain_code, datasource_id, resource_name, table_schema,
     field_mappings, materialized, created_at, updated_at, version_no, domain, is_deleted)
VALUES
    (md5('seed:entmgmt-v2:map:ent2_output_stage'), 'ent2_output_stage', '产值确认阶段', 'ent2_perf', 'ds-pg-demo',
     '项目阶段产值事实（治理层）', 'ecos_dw',
     '{"datasetId":"res_entmgmt_cur_stage","fieldMappings":[{"source":"ent2_output_stage.period","target":"period"},{"source":"ent2_output_stage.stage_type","target":"stage"},{"source":"ent2_output_stage.confirmed_amount","target":"amount"},{"source":"ent2_output_stage.realization_rate","target":"realization_rate"},{"source":"ent2_output_stage.project_ref","target":"project_id"},{"source":"ent2_output_stage.contract_base","target":"contract_base"},{"source":"ent2_output_stage.attribution_ratio","target":"attribution_ratio"}]}',
     true, now(), now(), 'v1', 'entmgmt', 0),
    (md5('seed:entmgmt-v2:map:ent_cost_item'), 'ent_cost_item', '成本科目', 'ent2_costfund', 'ds-pg-demo',
     '项目成本归集事实（治理层）', 'ecos_dw',
     '{"datasetId":"res_entmgmt_cur_cost","fieldMappings":[{"source":"ent_cost_item.cost_category","target":"cost_category"},{"source":"ent_cost_item.direct_or_allocated","target":"direct_or_allocated"},{"source":"ent_cost_item.allocation_rule_ref","target":"allocation_rule_ref"},{"source":"ent_cost_item.cost_amount","target":"amount"},{"source":"ent_cost_item.period","target":"period"},{"source":"ent_cost_item.project_ref","target":"project_id"},{"source":"ent_cost_item.is_pool","target":"is_pool"}]}',
     true, now(), now(), 'v1', 'entmgmt', 0),
    (md5('seed:entmgmt-v2:map:ent2_timesheet'), 'ent2_timesheet', '工时填报', 'ent2_capacity', 'ds-pg-demo',
     '人员资源投入事实（治理层）', 'ecos_dw',
     '{"datasetId":"res_entmgmt_cur_resource","fieldMappings":[{"source":"ent2_timesheet.report_period","target":"period"},{"source":"ent2_timesheet.fte","target":"fte"},{"source":"ent2_timesheet.work_hours","target":"work_hours"},{"source":"ent2_timesheet.hourly_rate","target":"hourly_rate"},{"source":"ent2_timesheet.project_ref","target":"project_id"},{"source":"ent2_timesheet.staff_ref","target":"staff_ref_hash"}]}',
     true, now(), now(), 'v1', 'entmgmt', 0),
    (md5('seed:entmgmt-v2:map:ent_project'), 'ent_project', '经营项目', 'ent2_contract', 'ds-pg-demo',
     '项目归属分摊事实（治理层）', 'ecos_dw',
     '{"datasetId":"res_entmgmt_cur_attribution","fieldMappings":[{"source":"ent_project.project_code","target":"project_id"},{"source":"ent_project.department_ref","target":"department_id"},{"source":"ent_project.attribution_ratio","target":"attribution_ratio"},{"source":"ent_project.attribution_type","target":"attribution_type"},{"source":"ent_project.contract_ref","target":"contract_id"}]}',
     true, now(), now(), 'v1', 'entmgmt', 0);

-- ---------- 10. 接口登记（6 条，ecos_control.ecos_interface_ref） ----------
INSERT INTO ecos_control.ecos_interface_ref (id, name, interface_type, endpoint, method, timeout_ms, create_time, update_time, create_by, update_by, is_deleted)
VALUES
    ('if_ent2_forecast_submit','经营预测提交','REST','/api/v1/workspace/scenarios/sc_entmgmt/forecast-runs/submit','POST',30000,now(),now(),'seed-entmgmt','seed-entmgmt',0),
    ('if_ent2_forecast_list', '经营预测列表','REST','/api/v1/workspace/scenarios/sc_entmgmt/forecast-runs','GET',10000,now(),now(),'seed-entmgmt','seed-entmgmt',0),
    ('if_ent2_fact_import',   '业务事实导入','REST','/api/v1/data/facts/{factType}/import','POST',60000,now(),now(),'seed-entmgmt','seed-entmgmt',0),
    ('if_ent2_dq_run',        'DQ 批次执行','REST','/api/v1/ecos/dq/schedules/dqsch_entmgmt_dw/run','POST',120000,now(),now(),'seed-entmgmt','seed-entmgmt',0),
    ('if_ent2_graph_query',   '知识图谱查询','REST','/api/v1/knowledge/graph/query','GET',15000,now(),now(),'seed-entmgmt','seed-entmgmt',0),
    ('if_ent2_mapping_validate','数据映射校验','REST','/api/v1/ontology/mappings/validate','POST',30000,now(),now(),'seed-entmgmt','seed-entmgmt',0);

-- ---------- 对账 ----------
SELECT 'domain' t, count(*) FROM ecos_ontology.ecos_ontology_domain WHERE code LIKE 'ont_%' OR code LIKE 'ent2_%'
UNION ALL SELECT 'entity_total(entmgmt)', count(*) FROM ecos_ontology.ecos_ontology_entity WHERE domain='entmgmt'
UNION ALL SELECT 'property', count(*) FROM ecos_ontology.ecos_ontology_property WHERE domain='entmgmt'
UNION ALL SELECT 'relationship', count(*) FROM ecos_ontology.ecos_ontology_relationship WHERE domain='entmgmt'
UNION ALL SELECT 'glossary', count(*) FROM ecos_ontology.ecos_glossary_term WHERE domain='entmgmt'
UNION ALL SELECT 'metric_total', count(*) FROM ecos_ontology.ecos_metric_definition WHERE domain='entmgmt'
UNION ALL SELECT 'metric_published_with_caliber', count(*) FROM ecos_ontology.ecos_metric_definition WHERE domain='entmgmt' AND status='PUBLISHED' AND caliber_id IS NOT NULL
UNION ALL SELECT 'rule', count(*) FROM ecos_ontology.ecos_ontology_rule WHERE domain='entmgmt'
UNION ALL SELECT 'action', count(*) FROM ecos_ontology.action_definition WHERE code LIKE 'ent2_%'
UNION ALL SELECT 'mapping', count(*) FROM ecos_ontology.ecos_entity_table_mapping WHERE domain='entmgmt'
UNION ALL SELECT 'property_function_typed', count(*) FROM ecos_ontology.ecos_ontology_property WHERE domain='entmgmt' AND function_type IS NOT NULL
UNION ALL SELECT 'mapping_pairs', count(*) FROM ecos_ontology.ecos_entity_table_mapping mp, jsonb_array_elements(mp.field_mappings->'fieldMappings') fm WHERE mp.domain='entmgmt'
UNION ALL SELECT 'mapping_source_dangling', count(*) FROM ecos_ontology.ecos_entity_table_mapping mp, jsonb_array_elements(mp.field_mappings->'fieldMappings') fm
  WHERE mp.domain='entmgmt' AND NOT EXISTS (SELECT 1 FROM ecos_ontology.ecos_ontology_entity e JOIN ecos_ontology.ecos_ontology_property p ON p.entity_id=e.id
        WHERE e.code = split_part(fm->>'source','.',1) AND p.code = split_part(fm->>'source','.',2) AND p.domain='entmgmt')
UNION ALL SELECT 'mapping_target_dangling', count(*) FROM ecos_ontology.ecos_entity_table_mapping mp, jsonb_array_elements(mp.field_mappings->'fieldMappings') fm
  WHERE mp.domain='entmgmt' AND NOT EXISTS (SELECT 1 FROM ecos_data.td_data_field f WHERE f.resource_id = mp.field_mappings->>'datasetId' AND f.field_name = fm->>'target')
UNION ALL SELECT 'metric_entitycode_dangling', count(*) FROM ecos_ontology.ecos_metric_definition m WHERE m.domain='entmgmt' AND m.entity_code IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM ecos_ontology.ecos_ontology_entity e WHERE e.code=m.entity_code)
UNION ALL SELECT 'interface', count(*) FROM ecos_control.ecos_interface_ref WHERE id LIKE 'if_ent2_%';

COMMIT;
