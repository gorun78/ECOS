-- ============================================================================
-- ecos_demo —— 本体工作台 DW 层（CURATED）演示 schema
-- ============================================================================
-- 用途：按已初始化的企业运营本体（ont001，11 实体 / 95 属性）落地 DW 层表，
--       供本体工作台「数据映射」从 DW 层取数（数据湖分层规范 §四：本体只读 DW 层）。
--
-- 分层口径（数据湖存储分层规范 §五）：
--   本 schema 内表均为 DW 层 → 登记 td_data_resource 时 layer='CURATED'、zone=NULL
--   （合法性矩阵：非 RAW 层 zone 必须为 NULL）
--
-- 执行方式（Flyway 已禁用，本脚本非迁移文件）：
--   Get-Content ecos_demo_dw_schema.sql -Raw | docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1
--
-- 表名规则：实体 code 的 snake_case 加 dw_ 前缀 —— 统一标识 DW 层，且规避 order 等 SQL 保留字。
-- 列名规则：属性 code 原样；unique_flag=1 的属性作主键（PRIMARY KEY）。
-- 类型映射：STRING→varchar / NUMBER→integer / DOUBLE→numeric(18,2) / DATE→date。
--
-- 数据规模：11 表 / 约 92 行，含部门上下级、员工隶属、客户订单、项目归口等自洽引用。
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS ecos_demo;

-- ① 部门（自关联：parent_dept_code）
DROP TABLE IF EXISTS ecos_demo.dw_department CASCADE;
CREATE TABLE ecos_demo.dw_department (
    dept_code        varchar(64)  PRIMARY KEY,
    dept_name        varchar(200) NOT NULL,
    dept_level       integer,
    parent_dept_code varchar(64),
    manager_emp_no   varchar(64),
    dept_type        varchar(50),
    founded_date     date,
    dept_status      varchar(20)
);
INSERT INTO ecos_demo.dw_department VALUES
('D100', '集团总部',   1, NULL,   'E1001', '职能部门', '2010-03-01', 'ACTIVE'),
('D110', '人力资源部', 2, 'D100', 'E1002', '职能部门', '2010-03-15', 'ACTIVE'),
('D120', '财务部',     2, 'D100', 'E1003', '职能部门', '2010-03-15', 'ACTIVE'),
('D130', '采购部',     2, 'D100', 'E1004', '支持部门', '2011-06-01', 'ACTIVE'),
('D140', '研发中心',   2, 'D100', 'E1005', '业务部门', '2012-01-10', 'ACTIVE'),
('D150', '销售部',     2, 'D100', 'E1006', '业务部门', '2010-09-01', 'ACTIVE'),
('D160', '生产制造部', 2, 'D100', 'E1007', '业务部门', '2013-04-20', 'ACTIVE');

-- ② 员工（dept_code → dw_department）
DROP TABLE IF EXISTS ecos_demo.dw_employee CASCADE;
CREATE TABLE ecos_demo.dw_employee (
    emp_no     varchar(64)  PRIMARY KEY,
    emp_name   varchar(100) NOT NULL,
    gender     varchar(10),
    position   varchar(100),
    hire_date  date         NOT NULL,
    phone      varchar(30),
    email      varchar(120),
    salary     numeric(18,2),
    dept_code  varchar(64),
    emp_status varchar(20),
    education  varchar(30)
);
INSERT INTO ecos_demo.dw_employee VALUES
('E1001', '陈建国', 'M', '集团总经理',   '2010-03-01', '13800001001', 'chenjg@ecos-demo.cn',  68000.00, 'D100', 'ACTIVE', '硕士'),
('E1002', '李慧敏', 'F', '人力资源总监', '2010-03-15', '13800001002', 'lihm@ecos-demo.cn',    38000.00, 'D110', 'ACTIVE', '硕士'),
('E1003', '王振华', 'M', '财务总监',     '2010-03-20', '13800001003', 'wangzh@ecos-demo.cn',  39000.00, 'D120', 'ACTIVE', '硕士'),
('E1004', '赵晓峰', 'M', '采购经理',     '2011-06-01', '13800001004', 'zhaoxf@ecos-demo.cn',  26000.00, 'D130', 'ACTIVE', '本科'),
('E1005', '刘志强', 'M', '研发总监',     '2012-01-10', '13800001005', 'liuzq@ecos-demo.cn',   45000.00, 'D140', 'ACTIVE', '博士'),
('E1006', '孙丽娟', 'F', '销售总监',     '2010-09-01', '13800001006', 'sunlj@ecos-demo.cn',   42000.00, 'D150', 'ACTIVE', '硕士'),
('E1007', '周大勇', 'M', '生产总监',     '2013-04-20', '13800001007', 'zhoudy@ecos-demo.cn',  40000.00, 'D160', 'ACTIVE', '本科'),
('E2001', '吴文博', 'M', '高级工程师',   '2016-07-01', '13800002001', 'wuwb@ecos-demo.cn',    28000.00, 'D140', 'ACTIVE', '硕士'),
('E2002', '郑小云', 'F', '产品经理',     '2018-03-12', '13800002002', 'zhengxy@ecos-demo.cn', 24000.00, 'D140', 'ACTIVE', '本科'),
('E2003', '黄志明', 'M', '大客户经理',   '2015-05-08', '13800002003', 'huangzm@ecos-demo.cn', 27000.00, 'D150', 'ACTIVE', '本科'),
('E2004', '林巧云', 'F', '会计主管',     '2017-09-01', '13800002004', 'linqy@ecos-demo.cn',   19000.00, 'D120', 'LEAVE',  '本科'),
('E2005', '冯建华', 'M', '供应链专员',   '2019-11-04', '13800002005', 'fengjh@ecos-demo.cn',  16000.00, 'D130', 'ACTIVE', '本科');

-- ③ 产品分类（自关联：parent_category_code）
DROP TABLE IF EXISTS ecos_demo.dw_product_category CASCADE;
CREATE TABLE ecos_demo.dw_product_category (
    category_code        varchar(64)  PRIMARY KEY,
    category_name        varchar(200) NOT NULL,
    parent_category_code varchar(64),
    category_level       integer,
    category_status      varchar(20)
);
INSERT INTO ecos_demo.dw_product_category
    (category_code, category_name, parent_category_code, category_level, category_status) VALUES
('C10', '智能硬件',   NULL,  1, 'ACTIVE'),
('C11', '移动终端',   'C10', 2, 'ACTIVE'),
('C12', '穿戴设备',   'C10', 2, 'ACTIVE'),
('C20', '工业装备',   NULL,  1, 'ACTIVE'),
('C21', '数控设备',   'C20', 2, 'ACTIVE'),
('C22', '检测仪器',   'C20', 2, 'ACTIVE'),
('C30', '软件与平台', NULL,  1, 'ACTIVE'),
('C31', '数据平台',   'C30', 2, 'ACTIVE');

-- ④ 产品（category_code → dw_product_category）
DROP TABLE IF EXISTS ecos_demo.dw_product CASCADE;
CREATE TABLE ecos_demo.dw_product (
    product_code  varchar(64)  PRIMARY KEY,
    product_name  varchar(200) NOT NULL,
    product_model varchar(120),
    unit_price    numeric(18,2) NOT NULL,
    cost_price    numeric(18,2),
    category_code varchar(64),
    unit          varchar(20),
    launch_date   date,
    product_status varchar(20)
);
INSERT INTO ecos_demo.dw_product VALUES
('P1001', '智能巡检终端 X1',   'X1-2024A', 12800.00,  8600.00, 'C11', '台', '2024-03-15', 'ON_SALE'),
('P1002', '工业平板 T7',       'T7-Pro',    9800.00,  6300.00, 'C11', '台', '2023-09-01', 'ON_SALE'),
('P1003', '智能安全帽 H2',     'H2-Lite',   2600.00,  1500.00, 'C12', '顶', '2024-06-20', 'ON_SALE'),
('P1004', '数控加工中心 CNC-5', 'CNC-580', 680000.00, 520000.00, 'C21', '台', '2022-11-10', 'ON_SALE'),
('P1005', '激光检测仪 L3',     'L3-300',   156000.00, 108000.00, 'C22', '台', '2023-04-18', 'ON_SALE'),
('P1006', '红外测温仪 IR-8',   'IR-8B',      8600.00,  5200.00, 'C22', '台', '2021-08-05', 'OFF_SALE'),
('P1007', '数据中台平台套件',  'DP-Std',   380000.00, 210000.00, 'C31', '套', '2024-01-08', 'ON_SALE'),
('P1008', '设备物联采集网关',  'GW-200',    16800.00,  9900.00, 'C31', '台', '2023-12-01', 'ON_SALE'),
('P1009', '智能腕表 W5',       'W5-Sport',   1980.00,  1080.00, 'C12', '只', '2024-09-12', 'ON_SALE'),
('P1010', '车载调度终端 V3',   'V3-4G',     11200.00,  7400.00, 'C11', '台', '2022-06-30', 'OFF_SALE');

-- ⑤ 客户
DROP TABLE IF EXISTS ecos_demo.dw_customer CASCADE;
CREATE TABLE ecos_demo.dw_customer (
    customer_code  varchar(64)  PRIMARY KEY,
    customer_name  varchar(200) NOT NULL,
    industry       varchar(100),
    customer_level varchar(10),
    contact_name   varchar(100),
    contact_phone  varchar(30),
    credit_rating  varchar(10),
    region         varchar(50),
    sign_date      date,
    customer_status varchar(20)
);
INSERT INTO ecos_demo.dw_customer VALUES
('CU001', '华东能源集团有限公司',   '能源',     'A', '徐立诚', '13900002001', 'AAA', '华东', '2019-04-12', 'ACTIVE'),
('CU002', '南方轨道交通股份公司',   '交通',     'A', '何雅琴', '13900002002', 'AAA', '华南', '2020-07-01', 'ACTIVE'),
('CU003', '西南矿业开发有限公司',   '矿业',     'B', '罗建国', '13900002003', 'AA',  '西南', '2021-03-18', 'ACTIVE'),
('CU004', '北方重工装备有限公司',   '制造业',   'B', '董雪梅', '13900002004', 'AA',  '华北', '2020-11-25', 'ACTIVE'),
('CU005', '中部智慧城市科技公司',   '信息技术', 'A', '邵文彬', '13900002005', 'AAA', '华中', '2022-01-20', 'ACTIVE'),
('CU006', '东南建材集团有限公司',   '建筑材料', 'C', '钱志远', '13900002006', 'A',   '华东', '2023-05-09', 'ACTIVE'),
('CU007', '西北电力工程有限公司',   '电力',     'B', '马晓东', '13900002007', 'AA',  '西北', '2021-09-30', 'ACTIVE'),
('CU008', '沿海物流服务有限公司',   '物流',     'C', '沈丽丽', '13900002008', 'A',   '华南', '2023-08-14', 'LOST');

-- ⑥ 供应商
DROP TABLE IF EXISTS ecos_demo.dw_supplier CASCADE;
CREATE TABLE ecos_demo.dw_supplier (
    supplier_code     varchar(64)  PRIMARY KEY,
    supplier_name     varchar(200) NOT NULL,
    supplier_type     varchar(50),
    contact_person    varchar(100),
    contact_phone     varchar(30),
    cooperation_since date,
    supplier_rating   varchar(10),
    supplier_status   varchar(20)
);
INSERT INTO ecos_demo.dw_supplier VALUES
('SU001', '精锐电子元件有限公司',   '原材料供应商', '刘成钢', '13700007001', '2016-03-01', 'A', 'ACTIVE'),
('SU002', '恒信精密机械有限公司',   '设备供应商',   '王海涛', '13700007002', '2017-08-15', 'A', 'ACTIVE'),
('SU003', '广东智造传感科技有限公司', '原材料供应商', '陈美玲', '13700007003', '2019-01-20', 'A', 'ACTIVE'),
('SU004', '联合工业服务有限公司',   '服务供应商',   '张伟民', '13700007004', '2020-06-10', 'B', 'ACTIVE'),
('SU005', '瑞驰包装材料有限公司',   '原材料供应商', '李国强', '13700007005', '2018-11-05', 'B', 'ACTIVE'),
('SU006', '天成检测认证有限公司',   '服务供应商',   '赵倩雯', '13700007006', '2021-04-22', 'C', 'FROZEN');

-- ⑦ 销售机会（customer_code → dw_customer，owner_emp_no → dw_employee）
DROP TABLE IF EXISTS ecos_demo.dw_sales_opportunity CASCADE;
CREATE TABLE ecos_demo.dw_sales_opportunity (
    opp_no              varchar(64)  PRIMARY KEY,
    opp_name            varchar(200) NOT NULL,
    customer_code       varchar(64)  NOT NULL,
    expected_amount     numeric(18,2),
    win_rate            integer,
    stage               varchar(50),
    expected_close_date date,
    owner_emp_no        varchar(64)
);
INSERT INTO ecos_demo.dw_sales_opportunity VALUES
('OP2024001', '华东能源巡检终端采购',   'CU001',  680000.00, 80, '报价',   '2025-02-28', 'E2003'),
('OP2024002', '南方轨道数据中台建设',   'CU002', 3800000.00, 60, '方案',   '2025-04-15', 'E2003'),
('OP2024003', '西南矿业数控设备采购',   'CU003', 1360000.00, 45, '谈判',   '2025-03-20', 'E2003'),
('OP2024004', '北方重工检测仪器采购',   'CU004',  312000.00, 70, '报价',   '2025-01-30', 'E1006'),
('OP2024005', '中部智慧城市物联网关',   'CU005',  504000.00, 55, '初步接触','2025-05-10', 'E1006'),
('OP2024006', '西北电力智能腕表采购',   'CU007',   39600.00, 35, '初步接触','2025-06-01', 'E2003'),
('OP2024007', '华东能源安全帽续采',     'CU001',   78000.00, 90, '谈判',   '2025-01-15', 'E2003'),
('OP2024008', '东南建材车载终端采购',   'CU006',  112000.00, 25, '方案',   '2025-07-20', 'E1006');

-- ⑧ 订单（customer_code → dw_customer，sales_emp_no → dw_employee）
DROP TABLE IF EXISTS ecos_demo.dw_sales_order CASCADE;
CREATE TABLE ecos_demo.dw_sales_order (
    order_no       varchar(64)  PRIMARY KEY,
    order_date     date         NOT NULL,
    customer_code  varchar(64)  NOT NULL,
    total_amount   numeric(18,2) NOT NULL,
    discount_amount numeric(18,2),
    payment_status varchar(20),
    delivery_status varchar(20),
    sales_emp_no   varchar(64)
);
INSERT INTO ecos_demo.dw_sales_order VALUES
('SO20240001', '2024-03-18', 'CU001',  640000.00, 40000.00, 'PAID',    'DONE',       'E2003'),
('SO20240002', '2024-04-02', 'CU002', 1900000.00, 0.00,     'PARTIAL', 'DELIVERING', 'E2003'),
('SO20240003', '2024-05-11', 'CU004',  196000.00, 6000.00,  'PAID',    'DONE',       'E1006'),
('SO20240004', '2024-06-25', 'CU005',  336000.00, 0.00,     'PARTIAL', 'DELIVERING', 'E1006'),
('SO20240005', '2024-07-08', 'CU003',  680000.00, 20000.00, 'UNPAID',  'PENDING',    'E2003'),
('SO20240006', '2024-08-19', 'CU001',   78000.00, 0.00,     'PAID',    'DONE',       'E2003'),
('SO20240007', '2024-09-27', 'CU007',   39600.00, 0.00,     'PAID',    'DONE',       'E2003'),
('SO20240008', '2024-10-15', 'CU002',  760000.00, 10000.00, 'PARTIAL', 'DELIVERING', 'E2003'),
('SO20240009', '2024-11-06', 'CU006',  112000.00, 0.00,     'UNPAID',  'PENDING',    'E1006'),
('SO20240010', '2024-12-02', 'CU005',  168000.00, 0.00,     'PAID',    'DONE',       'E1006');

-- ⑨ 项目（owner_emp_no → dw_employee，dept_code → dw_department）
DROP TABLE IF EXISTS ecos_demo.dw_project CASCADE;
CREATE TABLE ecos_demo.dw_project (
    project_code   varchar(64)  PRIMARY KEY,
    project_name   varchar(200) NOT NULL,
    start_date     date         NOT NULL,
    end_date       date,
    budget         numeric(18,2),
    actual_cost    numeric(18,2),
    progress       integer,
    project_status varchar(20),
    owner_emp_no   varchar(64),
    dept_code      varchar(64)
);
INSERT INTO ecos_demo.dw_project VALUES
('PRJ2024001', '智能巡检终端 X1 研发',     '2024-01-08', '2024-09-30', 2800000.00, 2650000.00, 100, 'DELIVERED', 'E2001', 'D140'),
('PRJ2024002', '数据中台平台套件 v2',      '2024-02-20', '2025-03-31', 5200000.00, 3100000.00,  62, 'RUNNING',   'E2002', 'D140'),
('PRJ2024003', '数控加工中心产线升级',     '2024-03-05', '2025-01-20', 8600000.00, 7200000.00,  88, 'RUNNING',   'E1007', 'D160'),
('PRJ2024004', '华东能源智慧巡检试点',     '2024-05-15', '2024-12-20', 1200000.00, 1080000.00, 100, 'DELIVERED', 'E2003', 'D150'),
('PRJ2024005', '供应链协同平台建设',       '2024-07-01', '2025-06-30', 1800000.00,  560000.00,  31, 'RUNNING',   'E2005', 'D130'),
('PRJ2024006', '智能腕表 W5 量产导入',     '2024-09-01', '2025-02-28',  900000.00,  410000.00,  45, 'RUNNING',   'E1007', 'D160'),
('PRJ2024007', '集团数据治理体系规划',     '2024-06-10', '2024-11-30',  600000.00,   580000.00, 100, 'CLOSED',    'E2002', 'D100');

-- ⑩ 合同（party_a / party_b 存名称）
DROP TABLE IF EXISTS ecos_demo.dw_contract CASCADE;
CREATE TABLE ecos_demo.dw_contract (
    contract_no    varchar(64)  PRIMARY KEY,
    contract_name  varchar(200) NOT NULL,
    contract_type  varchar(20),
    sign_date      date         NOT NULL,
    expire_date    date,
    amount         numeric(18,2) NOT NULL,
    party_a        varchar(200),
    party_b        varchar(200),
    contract_status varchar(20)
);
INSERT INTO ecos_demo.dw_contract VALUES
('CT2024001', '电子元件年度框架采购合同', 'PURCHASE', '2024-01-05', '2024-12-31', 3600000.00, 'ECOS 演示科技有限公司', '精锐电子元件有限公司',   'EFFECTIVE'),
('CT2024002', '精密机械长期采购合同',     'PURCHASE', '2024-02-18', '2026-02-17', 9800000.00, 'ECOS 演示科技有限公司', '恒信精密机械有限公司',   'PERFORMING'),
('CT2024003', '华东能源设备销售合同',     'SALES',    '2024-03-18', '2025-03-17',  680000.00, 'ECOS 演示科技有限公司', '华东能源集团有限公司',   'PERFORMING'),
('CT2024004', '南方轨道数据平台服务合同', 'SERVICE',  '2024-04-02', '2025-09-30', 1900000.00, 'ECOS 演示科技有限公司', '南方轨道交通股份公司',   'PERFORMING'),
('CT2024005', '传感元件采购合同',         'PURCHASE', '2024-05-20', '2025-05-19', 1450000.00, 'ECOS 演示科技有限公司', '广东智造传感科技有限公司','EFFECTIVE'),
('CT2024006', '西部矿业设备销售合同',     'SALES',    '2024-07-08', '2025-07-07',  680000.00, 'ECOS 演示科技有限公司', '西南矿业开发有限公司',   'PERFORMING'),
('CT2024007', '检测认证服务合同',         'SERVICE',  '2024-08-12', '2025-08-11',   180000.00, 'ECOS 演示科技有限公司', '天成检测认证有限公司',   'TERMINATED');

-- ⑪ 资产（dept_code → dw_department，custodian_emp_no → dw_employee）
DROP TABLE IF EXISTS ecos_demo.dw_asset CASCADE;
CREATE TABLE ecos_demo.dw_asset (
    asset_code       varchar(64)  PRIMARY KEY,
    asset_name       varchar(200) NOT NULL,
    asset_type       varchar(50),
    purchase_date    date,
    original_value   numeric(18,2),
    net_value        numeric(18,2),
    dept_code        varchar(64),
    custodian_emp_no varchar(64),
    asset_status     varchar(20)
);
INSERT INTO ecos_demo.dw_asset VALUES
('AS001', '研发中心服务器集群', '生产设备', '2022-05-10', 1280000.00, 640000.00, 'D140', 'E2001', 'IN_USE'),
('AS002', '数控加工中心 CNC-5', '生产设备', '2022-11-10',  680000.00, 408000.00, 'D160', 'E1007', 'IN_USE'),
('AS003', '激光检测仪 L3',      '生产设备', '2023-04-18',  156000.00, 109200.00, 'D160', 'E1007', 'IN_USE'),
('AS004', '办公楼 A 座 12 层',  '房产',     '2018-09-01', 8600000.00, 6020000.00, 'D100', 'E1001', 'IN_USE'),
('AS005', '商务公务车辆',       '车辆',     '2021-06-15',   380000.00, 152000.00, 'D100', 'E1001', 'IN_USE'),
('AS006', '财务专用工作站',     '办公设备', '2023-02-08',    46000.00,  27600.00, 'D120', 'E2004', 'IN_USE'),
('AS007', '人力资源信息系统',   '办公设备', '2021-10-20',   128000.00,  38400.00, 'D110', 'E1002', 'IDLE'),
('AS008', '旧款检测仪器 IR-8',  '生产设备', '2019-03-12',    86000.00,   8600.00, 'D160', 'E1007', 'SCRAPPED'),
('AS009', '采购部会议终端',     '办公设备', '2023-08-30',    32000.00,  19200.00, 'D130', 'E2005', 'IN_USE');

-- ⑫ 清理：本 schema 仅含 DW 层表，无 zone（合法性矩阵）
COMMENT ON SCHEMA ecos_demo IS 'DW 层（CURATED）演示 schema — 本体工作台数据映射只读取数来源';

-- ============================================================================
-- ⑬ DW 层标记（在「注册数据源 + 触发元数据采集」之后执行）
--
-- 依据《数据湖存储分层规范》§六「写入与标记责任」：写入 DW 层表后应标记
--   layer = 'CURATED'、zone = NULL（合法性矩阵：非 RAW 层 zone 必须为 NULL）。
-- 生产链路由数据管道在写入后标记（标记失败仅记 warn，不使管道失败）；
-- 本次为无管道的手工模拟，故在采集后按 source_path 前缀补标记。
--
-- 前置操作（本脚本之外，见 ecos_backend/AGENTS.md 数据源注册流程）：
--   1. POST /api/v1/datanet/datasource     注册数据源（connectionConfig.schema = ecos_demo）
--   2. POST /api/v1/datanet/metadata/collect/{datasourceId}   采集表级资源（列见 ⑭）
-- 注意：数据源密码经 security-engine 加密只存 password_enc，且密钥不跨进程持久化，
--       重启后需重新提交一次密码（PUT /api/v1/datanet/datasource/{id}）方能再次建连。
-- ============================================================================
UPDATE public.td_data_resource
SET layer = 'CURATED', zone = NULL
WHERE source_path LIKE 'ecos_demo.%';

-- ============================================================================
-- ⑭ 列元数据补充（td_data_field）
--
-- 背景：当前后端没有任何写入 td_data_field 的代码路径 —— 元数据采集
-- （MetadataServiceImpl.collectAll / MetadataCollectTaskExecutor）只登记表级资源
-- （ResourceSyncService.syncResource），Connector 接口也没有 listFields 能力，
-- 故 td_data_field 对新建数据源恒为空，数据映射左栏将显示 0 列。
--
-- 为满足「数据映射展示 DW 数据对象列」的测试需要，此处按本体定义直接从
-- information_schema 回填列元数据。这是测试环境准备手段，非采集能力的替代；
-- 「采集链路补 listFields」属平台缺口，另行立项。
--
-- 注：public.td_data_field 的 nullable / is_primary_key 实际类型为 boolean
--     （V19 声明 smallint，线上已被改为 boolean），故此处写布尔值。
-- ============================================================================
DELETE FROM public.td_data_field f
USING public.td_data_resource r
WHERE f.resource_id = r.resource_id
  AND r.source_path LIKE 'ecos_demo.%';

INSERT INTO public.td_data_field
    (field_id, resource_id, field_name, field_type, field_length, data_precision,
     nullable, is_primary_key, field_order)
SELECT
    md5(r.resource_id || '.' || c.column_name),
    r.resource_id,
    c.column_name,
    c.data_type,
    c.character_maximum_length,
    c.numeric_precision,
    (c.is_nullable = 'YES'),
    (pk.column_name IS NOT NULL),
    c.ordinal_position
FROM information_schema.columns c
JOIN public.td_data_resource r
  ON r.source_path = c.table_schema || '.' || c.table_name
LEFT JOIN (
    SELECT kcu.table_schema, kcu.table_name, kcu.column_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON kcu.constraint_name = tc.constraint_name
     AND kcu.table_schema = tc.table_schema
    WHERE tc.constraint_type = 'PRIMARY KEY'
) pk
  ON pk.table_schema = c.table_schema
 AND pk.table_name = c.table_name
 AND pk.column_name = c.column_name
WHERE c.table_schema = 'ecos_demo'
ORDER BY r.resource_id, c.ordinal_position;
