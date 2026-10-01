-- ============================================================================
-- 企业运营本体 — 演示/测试/开发种子数据（重置 + 播种，可重复执行）
-- ============================================================================
-- 用途：本体工作台（Ontology Workbench）演示、测试、开发用样例数据。
--
-- 范围（本体工作台 canonical 表，public schema）：
--   ecos_ontology / ecos_ontology_entity / ecos_ontology_property /
--   ecos_ontology_relationship / ecos_ontology_version / ecos_ontology_proposals /
--   ecos_ontology_action / ecos_ontology_data / ecos_ontology_rule / ecos_domain
--
-- 执行方式（Flyway 已禁用，本脚本非迁移文件，不会自动执行）：
--   docker cp enterprise_ontology_demo.sql ecos-postgres:/tmp/seed.sql
--   docker exec ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 -f /tmp/seed.sql
--
-- ⚠️ 脚本首段会清空本体工作台全部存量数据（含提案），执行前请先备份。
--
-- 字段口径（与 OntologyRepository 读取语义严格一致）：
--   ontology_id = 'ont001'（前端 DEFAULT_ONTOLOGY_ID）
--   实体的列表查询不过滤 is_deleted → 存量必须物理清除，不能只置逻辑删除位
--   status='ACTIVE' / is_deleted=0；属性的 unique_flag=1 表示主键标识（前端 isPrimaryKey）
--
-- 数据规模：8 个业务域 + 11 个核心实体 + 95 个属性 + 20 条关系 + 1 个已发布版本
--           + 24 条 Wiki 词条（gt_*）+ 30 条词条关系（6 类边）
-- ============================================================================

-- ① 清空本体工作台全部存量数据
--    ecos_domain 被 ecos_business_glossary / ecos_glossary_term / ecos_wm_goal 以 NO ACTION
--    外键引用（属知识工作台/认知引擎数据，不在本次清除范围），TRUNCATE 会整表失败。
--    处理方式：仅删除未被引用的旧域；仍被 ecos_wm_goal 引用的 3 个域 id
--    （dom_proj / dom_proc / dom_fin）保留行，改由下方 ② 的 ON CONFLICT 覆盖为新定义，
--    避免跨越引擎边界去改写认知引擎的表（架构铁律 §3.3）。
TRUNCATE TABLE
    public.ecos_ontology_property,
    public.ecos_ontology_relationship,
    public.ecos_ontology_action,
    public.ecos_ontology_data,
    public.ecos_ontology_rule,
    public.ecos_ontology_proposals,
    public.ecos_ontology_version,
    public.ecos_ontology_entity,
    public.ecos_ontology;

DELETE FROM public.ecos_domain
WHERE id NOT IN ('dom_proj', 'dom_proc', 'dom_fin');

-- ② 分类体系：业务域（sort_order 决定工作台左侧分组顺序）
--    保留的 3 个 id 由 DO UPDATE 覆盖为新定义，保证脚本可重复执行
INSERT INTO public.ecos_domain (id, code, name, owner, description, status, sort_order) VALUES
('dom_org',   'Organization', '组织架构域', 'admin', '部门组织与汇报层级',           'Active', 10),
('dom_hr',    'HR',           '人力资源域', 'admin', '员工主数据与人事信息',         'Active', 20),
('dom_prod',  'Product',      '产品域',     'admin', '产品目录与产品分类层级',       'Active', 30),
('dom_sales', 'Sales',        '销售域',     'admin', '客户、销售机会与订单',         'Active', 40),
('dom_proj',  'Project',      '项目域',     'admin', '项目立项、执行与交付',         'Active', 50),
('dom_proc',  'Procurement',  '采购域',     'admin', '供应商与采购合同',             'Active', 60),
('dom_fin',   'Finance',      '财务域',     'admin', '收入、成本与金额口径',         'Active', 70),
('dom_asset', 'Asset',        '资产域',     'admin', '固定资产与使用归属',           'Active', 80)
ON CONFLICT (id) DO UPDATE SET
    code        = EXCLUDED.code,
    name        = EXCLUDED.name,
    owner       = EXCLUDED.owner,
    description = EXCLUDED.description,
    status      = EXCLUDED.status,
    sort_order  = EXCLUDED.sort_order,
    updated_at  = now();

-- ③ 本体定义（id 固定为 ont001，前端默认加载该本体）
INSERT INTO public.ecos_ontology (id, code, name, version, status, description, is_deleted) VALUES
('ont001', 'enterprise', '企业运营本体', '1.0', 'PUBLISHED',
 '覆盖组织、人力、产品、客户、项目、采购、财务与资产的企业运营核心本体', 0);

-- ④ 核心实体类型（entity_type：MASTER=主数据，TRANSACTION=业务单据）
INSERT INTO public.ecos_ontology_entity
    (id, ontology_id, code, name, description, entity_type, sort_order, domain_id, is_deleted, status)
VALUES
('ent001', 'ont001', 'Department',       '部门',     '企业组织架构中的部门节点，支持上下级层级', 'MASTER',       1,  'dom_org',   0, 'ACTIVE'),
('ent002', 'ont001', 'Employee',         '员工',     '企业员工主数据，含职位与入职信息',         'MASTER',       2,  'dom_hr',    0, 'ACTIVE'),
('ent003', 'ont001', 'Product',          '产品',     '可销售或交付的产品主数据',                 'MASTER',       3,  'dom_prod',  0, 'ACTIVE'),
('ent004', 'ont001', 'Customer',         '客户',     '企业客户主数据与信用信息',                 'MASTER',       4,  'dom_sales', 0, 'ACTIVE'),
('ent005', 'ont001', 'Project',          '项目',     '项目立项、预算与执行信息',                 'MASTER',       5,  'dom_proj',  0, 'ACTIVE'),
('ent006', 'ont001', 'Order',            '订单',     '客户订单交易单据',                         'TRANSACTION',  6,  'dom_sales', 0, 'ACTIVE'),
('ent007', 'ont001', 'Supplier',         '供应商',   '采购供应商主数据与评级',                   'MASTER',       7,  'dom_proc',  0, 'ACTIVE'),
('ent008', 'ont001', 'Contract',         '合同',     '采购与销售合同单据',                       'TRANSACTION',  8,  'dom_proc',  0, 'ACTIVE'),
('ent009', 'ont001', 'Asset',            '资产',     '固定资产及其价值信息',                     'MASTER',       9,  'dom_asset', 0, 'ACTIVE'),
('ent010', 'ont001', 'SalesOpportunity', '销售机会', '销售线索与商机推进阶段',                   'TRANSACTION', 10,  'dom_sales', 0, 'ACTIVE'),
('ent011', 'ont001', 'ProductCategory',  '产品分类', '产品分类层级，支持父子分类',               'MASTER',       11,  'dom_prod',  0, 'ACTIVE');

-- ⑤ 实体属性（unique_flag=1 为该实体的主键标识，required_flag=1 为必填）
INSERT INTO public.ecos_ontology_property
    (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order, is_deleted, status, unique_flag, description)
VALUES
-- 部门 Department
('prop1',  'ent001', 'dept_code',           '部门编码',     'STRING', 1, 1, 1,  0, 'ACTIVE', 1, '部门唯一编码（主键）'),
('prop2',  'ent001', 'dept_name',           '部门名称',     'STRING', 1, 1, 2,  0, 'ACTIVE', 0, '部门显示名称'),
('prop3',  'ent001', 'dept_level',          '部门层级',     'NUMBER', 0, 0, 3,  0, 'ACTIVE', 0, '层级：1=一级部门，2=二级部门'),
('prop4',  'ent001', 'parent_dept_code',    '上级部门编码', 'STRING', 0, 1, 4,  0, 'ACTIVE', 0, '上级部门编码（自关联，构建组织层级）'),
('prop5',  'ent001', 'manager_emp_no',      '部门负责人',   'STRING', 0, 1, 5,  0, 'ACTIVE', 0, '部门负责人员工工号'),
('prop6',  'ent001', 'dept_type',           '部门类型',     'STRING', 0, 0, 6,  0, 'ACTIVE', 0, '业务部门/职能部门/支持部门'),
('prop7',  'ent001', 'founded_date',        '成立日期',     'DATE',   0, 0, 7,  0, 'ACTIVE', 0, '部门成立日期'),
('prop8',  'ent001', 'dept_status',         '部门状态',     'STRING', 0, 0, 8,  0, 'ACTIVE', 0, 'ACTIVE=在编，INACTIVE=撤销'),
-- 员工 Employee
('prop9',  'ent002', 'emp_no',              '员工工号',     'STRING', 1, 1, 1,  0, 'ACTIVE', 1, '员工唯一工号（主键）'),
('prop10', 'ent002', 'emp_name',            '姓名',         'STRING', 1, 1, 2,  0, 'ACTIVE', 0, '员工姓名'),
('prop11', 'ent002', 'gender',              '性别',         'STRING', 0, 0, 3,  0, 'ACTIVE', 0, 'M=男，F=女'),
('prop12', 'ent002', 'position',            '职位',         'STRING', 0, 1, 4,  0, 'ACTIVE', 0, '岗位名称，如部门经理/工程师'),
('prop13', 'ent002', 'hire_date',           '入职日期',     'DATE',   1, 0, 5,  0, 'ACTIVE', 0, '员工入职日期'),
('prop14', 'ent002', 'phone',               '联系电话',     'STRING', 0, 0, 6,  0, 'ACTIVE', 0, '手机号码'),
('prop15', 'ent002', 'email',               '企业邮箱',     'STRING', 0, 1, 7,  0, 'ACTIVE', 0, '企业邮箱地址'),
('prop16', 'ent002', 'salary',              '月薪',         'DOUBLE', 0, 0, 8,  0, 'ACTIVE', 0, '月薪（元）'),
('prop17', 'ent002', 'dept_code',           '所属部门编码', 'STRING', 0, 1, 9,  0, 'ACTIVE', 0, '所属部门编码（关联部门主键）'),
('prop18', 'ent002', 'emp_status',          '在职状态',     'STRING', 0, 0, 10, 0, 'ACTIVE', 0, 'ACTIVE=在职，LEAVE=休假，LEFT=离职'),
('prop19', 'ent002', 'education',           '最高学历',     'STRING', 0, 0, 11, 0, 'ACTIVE', 0, '本科/硕士/博士等'),
-- 产品 Product
('prop20', 'ent003', 'product_code',        '产品编码',     'STRING', 1, 1, 1,  0, 'ACTIVE', 1, '产品唯一编码（主键）'),
('prop21', 'ent003', 'product_name',        '产品名称',     'STRING', 1, 1, 2,  0, 'ACTIVE', 0, '产品显示名称'),
('prop22', 'ent003', 'product_model',       '规格型号',     'STRING', 0, 1, 3,  0, 'ACTIVE', 0, '产品规格型号'),
('prop23', 'ent003', 'unit_price',          '销售单价',     'DOUBLE', 1, 0, 4,  0, 'ACTIVE', 0, '标准销售单价（元）'),
('prop24', 'ent003', 'cost_price',          '成本价',       'DOUBLE', 0, 0, 5,  0, 'ACTIVE', 0, '产品成本价（元）'),
('prop25', 'ent003', 'category_code',       '所属分类编码', 'STRING', 0, 1, 6,  0, 'ACTIVE', 0, '所属产品分类编码'),
('prop26', 'ent003', 'unit',                '计量单位',     'STRING', 0, 0, 7,  0, 'ACTIVE', 0, '台/套/件等'),
('prop27', 'ent003', 'launch_date',         '上市日期',     'DATE',   0, 0, 8,  0, 'ACTIVE', 0, '产品上市日期'),
('prop28', 'ent003', 'product_status',      '产品状态',     'STRING', 0, 0, 9,  0, 'ACTIVE', 0, 'ON_SALE=在售，OFF_SALE=停售'),
-- 客户 Customer
('prop29', 'ent004', 'customer_code',       '客户编码',     'STRING', 1, 1, 1,  0, 'ACTIVE', 1, '客户唯一编码（主键）'),
('prop30', 'ent004', 'customer_name',       '客户名称',     'STRING', 1, 1, 2,  0, 'ACTIVE', 0, '客户企业名称'),
('prop31', 'ent004', 'industry',            '所属行业',     'STRING', 0, 1, 3,  0, 'ACTIVE', 0, '客户所属行业'),
('prop32', 'ent004', 'customer_level',      '客户等级',     'STRING', 0, 1, 4,  0, 'ACTIVE', 0, 'A/B/C 类客户'),
('prop33', 'ent004', 'contact_name',        '联系人',       'STRING', 0, 0, 5,  0, 'ACTIVE', 0, '客户联系人姓名'),
('prop34', 'ent004', 'contact_phone',       '联系电话',     'STRING', 0, 0, 6,  0, 'ACTIVE', 0, '客户联系电话'),
('prop35', 'ent004', 'credit_rating',       '信用评级',     'STRING', 0, 0, 7,  0, 'ACTIVE', 0, 'AAA/AA/A/B 等信用等级'),
('prop36', 'ent004', 'region',              '所属区域',     'STRING', 0, 1, 8,  0, 'ACTIVE', 0, '客户所属销售区域'),
('prop37', 'ent004', 'sign_date',           '签约日期',     'DATE',   0, 0, 9,  0, 'ACTIVE', 0, '首次签约日期'),
('prop38', 'ent004', 'customer_status',     '客户状态',     'STRING', 0, 0, 10, 0, 'ACTIVE', 0, 'ACTIVE=合作中，LOST=已流失'),
-- 项目 Project
('prop39', 'ent005', 'project_code',        '项目编码',     'STRING', 1, 1, 1,  0, 'ACTIVE', 1, '项目唯一编码（主键）'),
('prop40', 'ent005', 'project_name',        '项目名称',     'STRING', 1, 1, 2,  0, 'ACTIVE', 0, '项目名称'),
('prop41', 'ent005', 'start_date',          '开始日期',     'DATE',   1, 0, 3,  0, 'ACTIVE', 0, '项目计划开始日期'),
('prop42', 'ent005', 'end_date',            '结束日期',     'DATE',   0, 0, 4,  0, 'ACTIVE', 0, '项目计划结束日期'),
('prop43', 'ent005', 'budget',              '项目预算',     'DOUBLE', 0, 0, 5,  0, 'ACTIVE', 0, '项目预算金额（元）'),
('prop44', 'ent005', 'actual_cost',         '实际成本',     'DOUBLE', 0, 0, 6,  0, 'ACTIVE', 0, '项目实际发生成本（元）'),
('prop45', 'ent005', 'progress',            '完成进度',     'NUMBER', 0, 0, 7,  0, 'ACTIVE', 0, '完成进度百分比 0-100'),
('prop46', 'ent005', 'project_status',      '项目状态',     'STRING', 0, 1, 8,  0, 'ACTIVE', 0, 'INIT/RUNNING/DELIVERED/CLOSED'),
('prop47', 'ent005', 'owner_emp_no',        '项目负责人',   'STRING', 0, 1, 9,  0, 'ACTIVE', 0, '项目负责人员工工号'),
('prop48', 'ent005', 'dept_code',           '归口部门编码', 'STRING', 0, 1, 10, 0, 'ACTIVE', 0, '项目归口部门编码'),
-- 订单 Order
('prop49', 'ent006', 'order_no',            '订单编号',     'STRING', 1, 1, 1,  0, 'ACTIVE', 1, '订单唯一编号（主键）'),
('prop50', 'ent006', 'order_date',          '下单日期',     'DATE',   1, 0, 2,  0, 'ACTIVE', 0, '客户下单日期'),
('prop51', 'ent006', 'customer_code',       '客户编码',     'STRING', 1, 1, 3,  0, 'ACTIVE', 0, '下单客户编码'),
('prop52', 'ent006', 'total_amount',        '订单金额',     'DOUBLE', 1, 0, 4,  0, 'ACTIVE', 0, '订单总金额（元）'),
('prop53', 'ent006', 'discount_amount',     '优惠金额',     'DOUBLE', 0, 0, 5,  0, 'ACTIVE', 0, '折扣优惠金额（元）'),
('prop54', 'ent006', 'payment_status',      '付款状态',     'STRING', 0, 1, 6,  0, 'ACTIVE', 0, 'UNPAID/PARTIAL/PAID'),
('prop55', 'ent006', 'delivery_status',     '交付状态',     'STRING', 0, 0, 7,  0, 'ACTIVE', 0, 'PENDING/DELIVERING/DONE'),
('prop56', 'ent006', 'sales_emp_no',        '销售负责人',   'STRING', 0, 1, 8,  0, 'ACTIVE', 0, '跟进销售的员工工号'),
-- 供应商 Supplier
('prop57', 'ent007', 'supplier_code',       '供应商编码',   'STRING', 1, 1, 1,  0, 'ACTIVE', 1, '供应商唯一编码（主键）'),
('prop58', 'ent007', 'supplier_name',       '供应商名称',   'STRING', 1, 1, 2,  0, 'ACTIVE', 0, '供应商企业名称'),
('prop59', 'ent007', 'supplier_type',       '供应商类型',   'STRING', 0, 1, 3,  0, 'ACTIVE', 0, '原材料/设备/服务供应商'),
('prop60', 'ent007', 'contact_person',      '联系人',       'STRING', 0, 0, 4,  0, 'ACTIVE', 0, '供应商联系人姓名'),
('prop61', 'ent007', 'contact_phone',       '联系电话',     'STRING', 0, 0, 5,  0, 'ACTIVE', 0, '供应商联系电话'),
('prop62', 'ent007', 'cooperation_since',   '合作起始日期', 'DATE',   0, 0, 6,  0, 'ACTIVE', 0, '开始合作日期'),
('prop63', 'ent007', 'supplier_rating',     '供应商评级',   'STRING', 0, 1, 7,  0, 'ACTIVE', 0, 'A/B/C 级供应商'),
('prop64', 'ent007', 'supplier_status',     '供应商状态',   'STRING', 0, 0, 8,  0, 'ACTIVE', 0, 'ACTIVE=合作中，FROZEN=冻结'),
-- 合同 Contract
('prop65', 'ent008', 'contract_no',         '合同编号',     'STRING', 1, 1, 1,  0, 'ACTIVE', 1, '合同唯一编号（主键）'),
('prop66', 'ent008', 'contract_name',       '合同名称',     'STRING', 1, 1, 2,  0, 'ACTIVE', 0, '合同名称'),
('prop67', 'ent008', 'contract_type',       '合同类型',     'STRING', 0, 1, 3,  0, 'ACTIVE', 0, 'PURCHASE/SALES/SERVICE'),
('prop68', 'ent008', 'sign_date',           '签订日期',     'DATE',   1, 0, 4,  0, 'ACTIVE', 0, '合同签订日期'),
('prop69', 'ent008', 'expire_date',         '到期日期',     'DATE',   0, 0, 5,  0, 'ACTIVE', 0, '合同到期日期'),
('prop70', 'ent008', 'amount',              '合同金额',     'DOUBLE', 1, 0, 6,  0, 'ACTIVE', 0, '合同金额（元）'),
('prop71', 'ent008', 'party_a',             '甲方',         'STRING', 0, 1, 7,  0, 'ACTIVE', 0, '合同甲方名称'),
('prop72', 'ent008', 'party_b',             '乙方',         'STRING', 0, 1, 8,  0, 'ACTIVE', 0, '合同乙方名称'),
('prop73', 'ent008', 'contract_status',     '合同状态',     'STRING', 0, 1, 9,  0, 'ACTIVE', 0, 'DRAFT/EFFECTIVE/PERFORMING/TERMINATED'),
-- 资产 Asset
('prop74', 'ent009', 'asset_code',          '资产编码',     'STRING', 1, 1, 1,  0, 'ACTIVE', 1, '资产唯一编码（主键）'),
('prop75', 'ent009', 'asset_name',          '资产名称',     'STRING', 1, 1, 2,  0, 'ACTIVE', 0, '资产名称'),
('prop76', 'ent009', 'asset_type',          '资产类型',     'STRING', 0, 1, 3,  0, 'ACTIVE', 0, '办公设备/生产设备/车辆/房产'),
('prop77', 'ent009', 'purchase_date',       '购置日期',     'DATE',   0, 0, 4,  0, 'ACTIVE', 0, '资产购置日期'),
('prop78', 'ent009', 'original_value',      '资产原值',     'DOUBLE', 0, 0, 5,  0, 'ACTIVE', 0, '资产原值（元）'),
('prop79', 'ent009', 'net_value',           '资产净值',     'DOUBLE', 0, 0, 6,  0, 'ACTIVE', 0, '资产净值（元）'),
('prop80', 'ent009', 'dept_code',           '使用部门编码', 'STRING', 0, 1, 7,  0, 'ACTIVE', 0, '资产使用部门编码'),
('prop81', 'ent009', 'custodian_emp_no',    '保管人工号',   'STRING', 0, 1, 8,  0, 'ACTIVE', 0, '资产保管人员工工号'),
('prop82', 'ent009', 'asset_status',        '资产状态',     'STRING', 0, 0, 9,  0, 'ACTIVE', 0, 'IN_USE=在用，IDLE=闲置，SCRAPPED=报废'),
-- 销售机会 SalesOpportunity
('prop83', 'ent010', 'opp_no',              '机会编号',     'STRING', 1, 1, 1,  0, 'ACTIVE', 1, '销售机会唯一编号（主键）'),
('prop84', 'ent010', 'opp_name',            '机会名称',     'STRING', 1, 1, 2,  0, 'ACTIVE', 0, '销售机会名称'),
('prop85', 'ent010', 'customer_code',       '客户编码',     'STRING', 1, 1, 3,  0, 'ACTIVE', 0, '关联客户编码'),
('prop86', 'ent010', 'expected_amount',     '预计金额',     'DOUBLE', 0, 0, 4,  0, 'ACTIVE', 0, '预计成交金额（元）'),
('prop87', 'ent010', 'win_rate',            '赢单概率',     'NUMBER', 0, 0, 5,  0, 'ACTIVE', 0, '赢单概率百分比 0-100'),
('prop88', 'ent010', 'stage',               '机会阶段',     'STRING', 0, 1, 6,  0, 'ACTIVE', 0, '初步接触/方案/报价/谈判/赢单/输单'),
('prop89', 'ent010', 'expected_close_date', '预计成交日期', 'DATE',   0, 0, 7,  0, 'ACTIVE', 0, '预计成交日期'),
('prop90', 'ent010', 'owner_emp_no',        '负责人工号',   'STRING', 0, 1, 8,  0, 'ACTIVE', 0, '机会负责人员工工号'),
-- 产品分类 ProductCategory
('prop91', 'ent011', 'category_code',       '分类编码',     'STRING', 1, 1, 1,  0, 'ACTIVE', 1, '产品分类唯一编码（主键）'),
('prop92', 'ent011', 'category_name',       '分类名称',     'STRING', 1, 1, 2,  0, 'ACTIVE', 0, '产品分类名称'),
('prop93', 'ent011', 'parent_category_code','上级分类编码', 'STRING', 0, 1, 3,  0, 'ACTIVE', 0, '上级分类编码（自关联，构建分类层级）'),
('prop94', 'ent011', 'category_level',      '分类层级',     'NUMBER', 0, 0, 4,  0, 'ACTIVE', 0, '层级：1=一级分类'),
('prop95', 'ent011', 'category_status',     '分类状态',     'STRING', 0, 0, 5,  0, 'ACTIVE', 0, 'ACTIVE/INACTIVE');

-- ⑥ 实体关系（relationship_type：ONE_TO_ONE / ONE_TO_MANY / MANY_TO_MANY）
INSERT INTO public.ecos_ontology_relationship
    (id, source_entity_id, target_entity_id, code, name, relationship_type, is_deleted, status)
VALUES
('rel1',  'ent002', 'ent001', 'belongs_to',       '隶属',       'ONE_TO_MANY',  0, 'ACTIVE'),
('rel2',  'ent001', 'ent001', 'parent_dept',      '上级部门',   'ONE_TO_MANY',  0, 'ACTIVE'),
('rel3',  'ent001', 'ent002', 'managed_by',       '部门负责人', 'ONE_TO_ONE',   0, 'ACTIVE'),
('rel4',  'ent002', 'ent002', 'reports_to',       '汇报关系',   'ONE_TO_MANY',  0, 'ACTIVE'),
('rel5',  'ent002', 'ent005', 'responsible_for',  '负责项目',   'ONE_TO_MANY',  0, 'ACTIVE'),
('rel6',  'ent005', 'ent002', 'project_member',   '项目成员',   'MANY_TO_MANY', 0, 'ACTIVE'),
('rel7',  'ent004', 'ent006', 'places_order',     '下达订单',   'ONE_TO_MANY',  0, 'ACTIVE'),
('rel8',  'ent006', 'ent003', 'contains_product', '包含产品',   'MANY_TO_MANY', 0, 'ACTIVE'),
('rel9',  'ent002', 'ent004', 'serves_customer',  '负责客户',   'ONE_TO_MANY',  0, 'ACTIVE'),
('rel10', 'ent004', 'ent010', 'generates_opp',    '产生销售机会','ONE_TO_MANY', 0, 'ACTIVE'),
('rel11', 'ent010', 'ent006', 'converts_to',      '转化为订单', 'ONE_TO_ONE',   0, 'ACTIVE'),
('rel12', 'ent003', 'ent011', 'classified_as',    '归属分类',   'ONE_TO_MANY',  0, 'ACTIVE'),
('rel13', 'ent011', 'ent011', 'parent_category',  '上级分类',   'ONE_TO_MANY',  0, 'ACTIVE'),
('rel14', 'ent007', 'ent003', 'supplies_product', '供应产品',   'MANY_TO_MANY', 0, 'ACTIVE'),
('rel15', 'ent008', 'ent007', 'signed_with',      '签约供应商', 'ONE_TO_MANY',  0, 'ACTIVE'),
('rel16', 'ent004', 'ent008', 'signs_contract',   '签订合同',   'ONE_TO_MANY',  0, 'ACTIVE'),
('rel17', 'ent001', 'ent009', 'uses_asset',       '使用资产',   'ONE_TO_MANY',  0, 'ACTIVE'),
('rel18', 'ent002', 'ent009', 'custodian_of',     '保管资产',   'ONE_TO_ONE',   0, 'ACTIVE'),
('rel19', 'ent005', 'ent008', 'related_contract', '关联合同',   'MANY_TO_MANY', 0, 'ACTIVE'),
('rel20', 'ent001', 'ent005', 'owns_project',     '归口项目',   'ONE_TO_MANY',  0, 'ACTIVE');

-- ⑦ 初始版本（Published，使工作台版本视图有基线）
INSERT INTO public.ecos_ontology_version
    (id, ontology_id, version_no, status, snapshot, change_log, publisher, published_at)
VALUES
('ver1', 'ont001', '1.0.0', 'Published',
 '{"note":"enterprise ontology demo seed","entities":11,"properties":95,"relationships":20}'::jsonb,
 '企业运营本体初始版本（演示种子数据）', 'admin', now());

-- ⑧ 提案 / 动作 / 业务数据 / 规则：保持为空（已由 ① 清空，演示数据不播种流程与实例数据）

-- ============================================================================
-- ⑨ 操作类型（ecos_action_type）— 企业运营场景 demo
--
-- object_type_id 存**本体实体主键**（ent001..ent011）：前端 ObjectTypeDetail 的
-- 「操作」Tab 按 `param.objectTypeId === objectType.id` 匹配，故必须用实体主键而非实体 code。
-- preconditions / post_actions 为 JSON 数组文本（前端映射为规则展示）。
--
-- 清理：ecos_action_type 存量为历史测试数据（approve_order / test_action），
-- 与本本体无关，整表清空后重播。
-- ============================================================================
DELETE FROM public.ecos_action_type;

INSERT INTO public.ecos_action_type
    (id, name, description, object_type_id, preconditions, post_actions, audit_required, enabled, created_at, updated_at)
VALUES
('act_emp_transfer', '员工调岗', '将员工调动至目标部门并更新所属部门编码',
 'ent002', '[{"field":"emp_status","op":"eq","value":"ACTIVE"}]',
 '[{"type":"update_field","field":"dept_code","value":"${targetDeptCode}"}]', true, true, now(), now()),
('act_emp_leave', '员工离职', '办理离职并置在职状态为已离职',
 'ent002', '[{"field":"emp_status","op":"eq","value":"ACTIVE"}]',
 '[{"type":"update_field","field":"emp_status","value":"LEFT"}]', true, true, now(), now()),
('act_customer_upgrade', '客户等级调整', '按年度贡献度调整客户等级',
 'ent004', '[{"field":"customer_status","op":"eq","value":"ACTIVE"}]',
 '[{"type":"update_field","field":"customer_level","value":"${targetLevel}"}]', false, true, now(), now()),
('act_customer_lost', '客户流失标记', '将客户标记为已流失',
 'ent004', '[{"field":"customer_status","op":"eq","value":"ACTIVE"}]',
 '[{"type":"update_field","field":"customer_status","value":"LOST"}]', true, true, now(), now()),
('act_order_approve', '订单审批', '审批通过待处理订单',
 'ent006', '[{"field":"payment_status","op":"eq","value":"UNPAID"}]',
 '[{"type":"update_field","field":"payment_status","value":"PARTIAL"}]', true, true, now(), now()),
('act_order_deliver', '订单发货', '将订单交付状态推进为已交付',
 'ent006', '[{"field":"delivery_status","op":"eq","value":"PENDING"}]',
 '[{"type":"update_field","field":"delivery_status","value":"DELIVERING"}]', false, true, now(), now()),
('act_project_close', '项目结项', '完成项目结项并归档',
 'ent005', '[{"field":"project_status","op":"eq","value":"DELIVERED"}]',
 '[{"type":"update_field","field":"project_status","value":"CLOSED"}]', true, true, now(), now()),
('act_project_budget_adjust', '项目预算调整', '调整项目预算金额',
 'ent005', '[{"field":"project_status","op":"in","value":"INIT,RUNNING"}]',
 '[{"type":"update_field","field":"budget","value":"${newBudget}"}]', true, true, now(), now()),
('act_supplier_freeze', '供应商冻结', '因质量或交付问题冻结供应商',
 'ent007', '[{"field":"supplier_status","op":"eq","value":"ACTIVE"}]',
 '[{"type":"update_field","field":"supplier_status","value":"FROZEN"}]', true, true, now(), now()),
('act_contract_effective', '合同生效', '将已签署合同置为生效中',
 'ent008', '[{"field":"contract_status","op":"eq","value":"DRAFT"}]',
 '[{"type":"update_field","field":"contract_status","value":"EFFECTIVE"}]', true, true, now(), now()),
('act_contract_terminate', '合同终止', '提前终止执行中的合同',
 'ent008', '[{"field":"contract_status","op":"eq","value":"PERFORMING"}]',
 '[{"type":"update_field","field":"contract_status","value":"TERMINATED"}]', true, true, now(), now()),
('act_asset_scrap', '资产报废', '将已达报废条件资产置为已报废',
 'ent009', '[{"field":"asset_status","op":"in","value":"IN_USE,IDLE"}]',
 '[{"type":"update_field","field":"asset_status","value":"SCRAPPED"}]', true, true, now(), now()),
('act_opp_advance', '销售机会推进', '推进销售机会阶段并更新赢单概率',
 'ent010', '[{"field":"stage","op":"in","value":"初步接触,方案,报价"}]',
 '[{"type":"update_field","field":"stage","value":"${nextStage}"}]', false, true, now(), now()),
('act_dept_merge', '部门合并', '将下级部门并入目标部门',
 'ent001', '[{"field":"dept_status","op":"eq","value":"ACTIVE"},{"field":"dept_level","op":"gt","value":"1"}]',
 '[{"type":"update_field","field":"parent_dept_code","value":"${targetDeptCode}"}]', true, true, now(), now()),
('act_product_offsale', '产品停售', '将产品置为停售',
 'ent003', '[{"field":"product_status","op":"eq","value":"ON_SALE"}]',
 '[{"type":"update_field","field":"product_status","value":"OFF_SALE"}]', false, true, now(), now()),
('act_category_disable', '产品分类停用', '停用不再使用的产品分类',
 'ent011', '[{"field":"category_status","op":"eq","value":"ACTIVE"}]',
 '[{"type":"update_field","field":"category_status","value":"INACTIVE"}]', false, true, now(), now());

-- ============================================================================
-- ⑥ Wiki 词条与词条关系（V143 语义扩展后的演示数据）
--    词条以 code='gt_*' 为稳定业务键，可重复执行（先按 code 前缀清理）。
--    11 条实体类词条经 object_type_id 挂接到本体实体（ent001~ent011），
--    其余为指标/概念/关系/函数类词条；关系覆盖 6 类边类型共 30 条。
-- ============================================================================
DELETE FROM public.ecos_glossary_term WHERE code LIKE 'gt\_%';

INSERT INTO public.ecos_glossary_term
    (code, name, definition, domain, status, term_type, aliases, examples, tags,
     version, object_type_id, created_by, created_at, updated_at)
VALUES
-- ── 实体类词条（挂接本体实体）──────────────────────────────
('gt_department', '部门', '企业内部承担特定职能的组织单元，具有层级归属关系', 'BUSINESS', 'PUBLISHED', 'ENTITY',
 ARRAY['组织单元','Org Unit'], ARRAY['销售部','研发中心'], ARRAY['组织架构','主数据'], 1, 'ent001', 'demo', now(), now()),
('gt_employee', '员工', '与企业建立劳动关系的自然人，隶属唯一部门', 'BUSINESS', 'PUBLISHED', 'ENTITY',
 ARRAY['职工','人员'], ARRAY['张三','李四'], ARRAY['组织架构','人力资源'], 1, 'ent002', 'demo', now(), now()),
('gt_product', '产品', '企业对外销售或交付的标准化商品单元', 'BUSINESS', 'PUBLISHED', 'ENTITY',
 ARRAY['商品','Product'], ARRAY['智能网关','边缘服务器'], ARRAY['商品域','主数据'], 1, 'ent003', 'demo', now(), now()),
('gt_customer', '客户', '与企业建立交易关系的法人或自然人主体', 'BUSINESS', 'PUBLISHED', 'ENTITY',
 ARRAY['客户主数据','Customer'], ARRAY['某交投集团','某科技公司'], ARRAY['客户域','主数据'], 1, 'ent004', 'demo', now(), now()),
('gt_project', '项目', '有明确起止时间与交付目标的临时性工作集合', 'BUSINESS', 'PUBLISHED', 'ENTITY',
 ARRAY['工程','Project'], ARRAY['ETC 改造项目'], ARRAY['项目域','交付'], 1, 'ent005', 'demo', now(), now()),
('gt_order', '订单', '客户下达的采购请求，含商品明细与交付约定', 'BUSINESS', 'PUBLISHED', 'ENTITY',
 ARRAY['销售订单','Order'], ARRAY['SO-2026-001'], ARRAY['交易域'], 1, 'ent006', 'demo', now(), now()),
('gt_supplier', '供应商', '为企业提供物料或服务的合作方', 'BUSINESS', 'PUBLISHED', 'ENTITY',
 ARRAY['厂商','Vendor'], ARRAY['某机电设备公司'], ARRAY['供应链','主数据'], 1, 'ent007', 'demo', now(), now()),
('gt_contract', '合同', '约定双方权利义务并具法律效力的书面协议', 'BUSINESS', 'PUBLISHED', 'ENTITY',
 ARRAY['协议','Contract'], ARRAY['HT-2026-018'], ARRAY['法务','交易域'], 1, 'ent008', 'demo', now(), now()),
('gt_asset', '资产', '企业持有并预期带来经济利益的资源实体', 'BUSINESS', 'PUBLISHED', 'ENTITY',
 ARRAY['固定资产','Asset'], ARRAY['收费岗亭','检测设备'], ARRAY['资产域'], 1, 'ent009', 'demo', now(), now()),
('gt_sales_opportunity', '销售机会', '尚未成交但有明确意向的潜在销售线索', 'BUSINESS', 'PUBLISHED', 'ENTITY',
 ARRAY['商机','Opportunity'], ARRAY['某市交投二期'], ARRAY['销售域'], 1, 'ent010', 'demo', now(), now()),
('gt_product_category', '产品分类', '按业务属性对产品进行归类的层级目录', 'BUSINESS', 'PUBLISHED', 'ENTITY',
 ARRAY['品类','Category'], ARRAY['硬件','软件'], ARRAY['商品域'], 1, 'ent011', 'demo', now(), now()),
-- ── 指标类词条 ────────────────────────────────────────────
('gt_kpi', '关键绩效指标', 'KPI，用于量化衡量组织或业务目标达成度的核心指标', 'OPERATION', 'PUBLISHED', 'METRIC',
 ARRAY['KPI','绩效指标'], ARRAY['客户流失率','订单履约率'], ARRAY['运营评价','指标体系'], 1, NULL, 'demo', now(), now()),
('gt_churn', '客户流失率', '统计周期内流失客户数占期初客户总数的比例', 'OPERATION', 'PUBLISHED', 'METRIC',
 ARRAY['流失率','Churn Rate'], ARRAY['3.2%'], ARRAY['客户域','运营评价'], 1, NULL, 'demo', now(), now()),
('gt_supplier_rating', '供应商评级', '基于质量、交付、价格三维度对供应商的综合评分', 'OPERATION', 'PUBLISHED', 'METRIC',
 ARRAY['供应商评分','Vendor Rating'], ARRAY['A 级','B 级'], ARRAY['供应链','运营评价'], 1, NULL, 'demo', now(), now()),
('gt_asset_depreciation', '资产折旧', '资产在使用周期内按规则分摊至成本的价值损耗', 'OPERATION', 'PUBLISHED', 'METRIC',
 ARRAY['折旧率','Depreciation'], ARRAY['直线法 5 年'], ARRAY['资产域'], 1, NULL, 'demo', now(), now()),
-- ── 概念类词条 ────────────────────────────────────────────
('gt_ce_360', '客户360视图', '整合交易、服务、行为数据形成的客户统一全景视图', 'BUSINESS', 'PUBLISHED', 'CONCEPT',
 ARRAY['360视图','Customer 360'], ARRAY['客户画像看板'], ARRAY['客户域','数据治理'], 1, NULL, 'demo', now(), now()),
('gt_sla', '服务等级协议', 'SLA，约定服务可用性、响应时效与违约责任的协议条款', 'OPERATION', 'PUBLISHED', 'CONCEPT',
 ARRAY['SLA','服务水平协议'], ARRAY['可用性 99.9%'], ARRAY['法务','运维'], 1, NULL, 'demo', now(), now()),
('gt_cmdb', '配置管理数据库', 'CMDB，记录 IT 配置项及其相互关系的管理数据库', 'TECH_ARCH', 'PUBLISHED', 'CONCEPT',
 ARRAY['CMDB','配置库'], ARRAY['服务器配置项'], ARRAY['资产域','运维'], 1, NULL, 'demo', now(), now()),
('gt_data_lineage', '数据血缘', '描述数据从源头到消费端的全链路流转与转换依赖关系', 'DATA_MGMT', 'PUBLISHED', 'CONCEPT',
 ARRAY['血缘','Lineage'], ARRAY['ODS→DW→指标'], ARRAY['数据治理','元数据'], 1, NULL, 'demo', now(), now()),
('gt_master_data', '主数据', '跨业务系统共享、相对稳定且需统一治理的核心实体数据', 'DATA_MGMT', 'PUBLISHED', 'CONCEPT',
 ARRAY['MDM','基准数据'], ARRAY['客户主数据','产品主数据'], ARRAY['数据治理','主数据'], 1, NULL, 'demo', now(), now()),
-- ── 关系类词条 ────────────────────────────────────────────
('gt_contract_lifecycle', '合同生命周期', '合同从拟稿、签署、履行到终止的完整状态流转过程', 'BUSINESS', 'PUBLISHED', 'RELATION',
 ARRAY['合同流转'], ARRAY['DRAFT→EFFECTIVE→PERFORMING'], ARRAY['法务','流程'], 1, NULL, 'demo', now(), now()),
('gt_order_fulfillment', '订单履约', '订单自确认至交付完成的全过程执行链路', 'BUSINESS', 'PUBLISHED', 'RELATION',
 ARRAY['履约','Fulfillment'], ARRAY['备货→发货→签收'], ARRAY['交易域','交付'], 1, NULL, 'demo', now(), now()),
('gt_sales_funnel', '销售漏斗', '从线索到成交各阶段转化率的漏斗式度量模型', 'BUSINESS', 'PUBLISHED', 'RELATION',
 ARRAY['漏斗','Funnel'], ARRAY['线索→商机→签约'], ARRAY['销售域'], 1, NULL, 'demo', now(), now()),
-- ── 函数类词条 ────────────────────────────────────────────
('gt_dept_headcount', '部门编制人数', '按部门统计在职员工数量的计算口径', 'BUSINESS', 'PUBLISHED', 'FUNCTION',
 ARRAY['部门人数','Headcount'], ARRAY['销售部 32 人'], ARRAY['组织架构','指标口径'], 1, NULL, 'demo', now(), now());

-- ISA 边对应的上位词条内联绑定（parent_term_id）
UPDATE public.ecos_glossary_term c SET parent_term_id = p.id
FROM public.ecos_glossary_term p
WHERE p.code = 'gt_kpi'
  AND c.code IN ('gt_churn', 'gt_supplier_rating', 'gt_asset_depreciation');

UPDATE public.ecos_glossary_term c SET parent_term_id = p.id
FROM public.ecos_glossary_term p
WHERE p.code = 'gt_master_data'
  AND c.code IN ('gt_product_category', 'gt_customer', 'gt_supplier');

-- 30 条词条关系（6 类边，按 code 关联以保持幂等）
INSERT INTO public.ecos_glossary_term_relation
    (from_term_id, to_term_id, relation_type, weight, description, created_by, created_at)
SELECT f.id, t.id, v.rel, 100, v.descr, 'demo', now()
FROM (VALUES
    ('gt_churn',              'gt_kpi',                 'ISA',      '客户流失率属于关键绩效指标'),
    ('gt_supplier_rating',    'gt_kpi',                 'ISA',      '供应商评级属于关键绩效指标'),
    ('gt_asset_depreciation', 'gt_kpi',                 'ISA',      '资产折旧属于关键绩效指标'),
    ('gt_product_category',   'gt_master_data',         'ISA',      '产品分类属于主数据'),
    ('gt_customer',           'gt_master_data',         'ISA',      '客户属于主数据'),
    ('gt_supplier',           'gt_master_data',         'ISA',      '供应商属于主数据'),
    ('gt_ce_360',             'gt_customer',            'SYNONYM',  '客户360视图为客户统一视图的别名'),
    ('gt_cmdb',               'gt_asset',               'SYNONYM',  'CMDB 在配置项口径上与资产同义'),
    ('gt_sla',                'gt_contract',            'SYNONYM',  'SLA 以合同条款形式落地'),
    ('gt_employee',           'gt_department',          'PART_OF',  '员工隶属于部门'),
    ('gt_product',            'gt_product_category',    'PART_OF',  '产品归属于产品分类'),
    ('gt_contract',           'gt_project',             'PART_OF',  '合同归属于项目'),
    ('gt_asset',              'gt_department',          'PART_OF',  '资产由部门持有'),
    ('gt_asset_depreciation', 'gt_asset',               'PART_OF',  '资产折旧是资产的价值构成'),
    ('gt_sales_funnel',       'gt_sales_opportunity',   'PART_OF',  '销售漏斗包含销售机会'),
    ('gt_churn',              'gt_ce_360',              'PART_OF',  '客户流失率是客户360视图的组成指标'),
    ('gt_contract_lifecycle', 'gt_contract',            'SEE_ALSO', '合同生命周期描述合同的状态流转'),
    ('gt_order_fulfillment',  'gt_order',               'SEE_ALSO', '订单履约描述订单的执行链路'),
    ('gt_sla',                'gt_order_fulfillment',   'SEE_ALSO', 'SLA 约束订单履约时效'),
    ('gt_cmdb',               'gt_data_lineage',        'SEE_ALSO', 'CMDB 与数据血缘共同支撑配置追溯'),
    ('gt_master_data',        'gt_data_lineage',        'SEE_ALSO', '主数据治理依赖数据血缘'),
    ('gt_dept_headcount',     'gt_department',          'SEE_ALSO', '部门编制人数统计部门在职员工'),
    ('gt_data_lineage',       'gt_master_data',         'CAUSAL',   '数据血缘完备性影响主数据质量'),
    ('gt_data_lineage',       'gt_ce_360',              'CAUSAL',   '数据血缘支撑客户360视图整合'),
    ('gt_sales_funnel',       'gt_sales_opportunity',   'CAUSAL',   '销售漏斗转化率影响商机推进'),
    ('gt_supplier_rating',    'gt_contract',            'CAUSAL',   '供应商评级影响合同授予'),
    ('gt_project',            'gt_contract',            'RELATED',  '项目与合同相互关联'),
    ('gt_project',            'gt_asset',               'RELATED',  '项目交付形成资产'),
    ('gt_customer',           'gt_sales_opportunity',   'RELATED',  '客户产生销售机会'),
    ('gt_supplier',           'gt_product',             'RELATED',  '供应商供应产品')
) AS v(fc, tc, rel, descr)
JOIN public.ecos_glossary_term f ON f.code = v.fc
JOIN public.ecos_glossary_term t ON t.code = v.tc;