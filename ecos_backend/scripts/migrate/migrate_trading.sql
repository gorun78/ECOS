-- ============================================================
-- migrate_trading.sql — 贸易型企业迁移模板
-- 场景: 进销存 → ECOS (供应商/合同/库存/回款)
-- 用法: 数据工程师根据客户实际表结构调整
-- ============================================================

-- ============================================================
-- Part 1: Ontology 实体定义
-- ============================================================

-- 1.1 供应商实体
INSERT INTO ecos_ontology_entity (id, ontology_id, code, name, description, entity_type, sort_order, created_at, updated_at)
VALUES ('ent_trd_supplier', 'ont_trd', 'TRD_SUPPLIER', '供应商', '供应商主数据', 'MASTER', 1, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 1.2 采购合同实体
INSERT INTO ecos_ontology_entity (id, ontology_id, code, name, description, entity_type, sort_order, created_at, updated_at)
VALUES ('ent_trd_contract', 'ont_trd', 'TRD_CONTRACT', '采购合同', '采购合同/订单', 'TRANSACTION', 2, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 1.3 库存实体
INSERT INTO ecos_ontology_entity (id, ontology_id, code, name, description, entity_type, sort_order, created_at, updated_at)
VALUES ('ent_trd_inventory', 'ont_trd', 'TRD_INVENTORY', '库存', '商品库存流水', 'TRANSACTION', 3, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 1.4 回款实体
INSERT INTO ecos_ontology_entity (id, ontology_id, code, name, description, entity_type, sort_order, created_at, updated_at)
VALUES ('ent_trd_payment', 'ont_trd', 'TRD_PAYMENT', '回款记录', '销售回款/应收账款', 'TRANSACTION', 4, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- Part 2: Ontology 属性定义
-- ============================================================

-- 供应商属性
INSERT INTO ecos_ontology_property (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order, created_at, updated_at) VALUES
('prop_trd_sup_code',     'ent_trd_supplier', 'supplier_code', '供应商编码', 'STRING',  1, 1, 1, NOW(), NOW()),
('prop_trd_sup_name',     'ent_trd_supplier', 'supplier_name', '供应商名称', 'STRING',  1, 1, 2, NOW(), NOW()),
('prop_trd_sup_contact',  'ent_trd_supplier', 'contact_person','联系人',     'STRING',  0, 0, 3, NOW(), NOW()),
('prop_trd_sup_phone',    'ent_trd_supplier', 'contact_phone', '联系电话',   'STRING',  0, 0, 4, NOW(), NOW()),
('prop_trd_sup_credit',   'ent_trd_supplier', 'credit_level',  '信用等级',   'STRING',  0, 1, 5, NOW(), NOW()),
('prop_trd_sup_status',   'ent_trd_supplier', 'status',        '合作状态',   'STRING',  1, 1, 6, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 采购合同属性
INSERT INTO ecos_ontology_property (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order, created_at, updated_at) VALUES
('prop_trd_cont_no',       'ent_trd_contract', 'contract_no',   '合同编号',   'STRING',  1, 1, 1, NOW(), NOW()),
('prop_trd_cont_sup',      'ent_trd_contract', 'supplier_code', '供应商编码', 'STRING',  1, 1, 2, NOW(), NOW()),
('prop_trd_cont_amount',   'ent_trd_contract', 'contract_amount','合同金额',  'NUMBER',  1, 0, 3, NOW(), NOW()),
('prop_trd_cont_currency', 'ent_trd_contract', 'currency',      '币种',       'STRING',  1, 0, 4, NOW(), NOW()),
('prop_trd_cont_sign_date','ent_trd_contract', 'sign_date',     '签订日期',   'DATE',    1, 0, 5, NOW(), NOW()),
('prop_trd_cont_delivery', 'ent_trd_contract', 'delivery_date', '交货日期',   'DATE',    1, 0, 6, NOW(), NOW()),
('prop_trd_cont_status',   'ent_trd_contract', 'contract_status','合同状态',  'STRING',  1, 1, 7, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 库存属性
INSERT INTO ecos_ontology_property (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order, created_at, updated_at) VALUES
('prop_trd_inv_sku',      'ent_trd_inventory', 'sku_code',     'SKU编码',    'STRING',  1, 1, 1, NOW(), NOW()),
('prop_trd_inv_name',     'ent_trd_inventory', 'product_name', '商品名称',   'STRING',  1, 1, 2, NOW(), NOW()),
('prop_trd_inv_wh',       'ent_trd_inventory', 'warehouse',    '仓库',       'STRING',  1, 1, 3, NOW(), NOW()),
('prop_trd_inv_qty',      'ent_trd_inventory', 'quantity',     '库存数量',   'NUMBER',  1, 0, 4, NOW(), NOW()),
('prop_trd_inv_unit_cost', 'ent_trd_inventory','unit_cost',    '单位成本',   'NUMBER',  0, 0, 5, NOW(), NOW()),
('prop_trd_inv_total',     'ent_trd_inventory','total_value',  '库存总值',   'NUMBER',  0, 0, 6, NOW(), NOW()),
('prop_trd_inv_update',    'ent_trd_inventory','last_update',  '最后更新',   'DATETIME',1, 0, 7, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 回款属性
INSERT INTO ecos_ontology_property (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order, created_at, updated_at) VALUES
('prop_trd_pay_no',       'ent_trd_payment', 'payment_no',    '回款单号',   'STRING',  1, 1, 1, NOW(), NOW()),
('prop_trd_pay_contract', 'ent_trd_payment', 'contract_no',   '关联合同',   'STRING',  1, 1, 2, NOW(), NOW()),
('prop_trd_pay_amount',   'ent_trd_payment', 'payment_amount','回款金额',   'NUMBER',  1, 0, 3, NOW(), NOW()),
('prop_trd_pay_date',     'ent_trd_payment', 'payment_date',  '回款日期',   'DATE',    1, 0, 4, NOW(), NOW()),
('prop_trd_pay_method',   'ent_trd_payment', 'payment_method','回款方式',   'STRING',  0, 0, 5, NOW(), NOW()),
('prop_trd_pay_status',   'ent_trd_payment', 'payment_status','回款状态',   'STRING',  1, 1, 6, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- Part 3: Ontology 关系定义
-- ============================================================

INSERT INTO ecos_ontology_relationship (id, source_entity_id, target_entity_id, code, name, relationship_type, created_at) VALUES
('rel_trd_001', 'ent_trd_contract',  'ent_trd_supplier', 'SIGNED_WITH',  '与供应商签订', 'MANY_TO_ONE', NOW()),
('rel_trd_002', 'ent_trd_inventory', 'ent_trd_contract', 'BELONGS_TO',   '所属合同',     'MANY_TO_ONE', NOW()),
('rel_trd_003', 'ent_trd_payment',   'ent_trd_contract', 'PAYS_FOR',     '回款对应合同', 'MANY_TO_ONE', NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- Part 4: DDL 物理表建表
-- ============================================================

-- 供应商物理表
CREATE TABLE IF NOT EXISTS trd_supplier (
    id              VARCHAR(64)  PRIMARY KEY,
    supplier_code   VARCHAR(64)  NOT NULL UNIQUE,
    supplier_name   VARCHAR(256) NOT NULL,
    contact_person  VARCHAR(64),
    contact_phone   VARCHAR(32),
    email           VARCHAR(128),
    address         TEXT,
    credit_level    VARCHAR(16)  DEFAULT 'B',
    status          VARCHAR(16)  DEFAULT 'ACTIVE',
    create_time     TIMESTAMP    DEFAULT NOW(),
    update_time     TIMESTAMP    DEFAULT NOW()
);
COMMENT ON TABLE trd_supplier IS '供应商主数据 — 从进销存系统迁移';

-- 采购合同物理表
CREATE TABLE IF NOT EXISTS trd_contract (
    id              VARCHAR(64)  PRIMARY KEY,
    contract_no     VARCHAR(64)  NOT NULL UNIQUE,
    supplier_code   VARCHAR(64)  NOT NULL,
    contract_name   VARCHAR(256),
    contract_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    currency        VARCHAR(8)   DEFAULT 'CNY',
    sign_date       DATE,
    delivery_date   DATE,
    contract_status VARCHAR(32)  DEFAULT 'DRAFT',
    payment_terms   VARCHAR(128),
    create_by       VARCHAR(64),
    create_time     TIMESTAMP    DEFAULT NOW(),
    update_time     TIMESTAMP    DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_trd_cont_sup ON trd_contract(supplier_code);
CREATE INDEX IF NOT EXISTS idx_trd_cont_status ON trd_contract(contract_status);
COMMENT ON TABLE trd_contract IS '采购合同 — 从进销存系统迁移';

-- 库存物理表
CREATE TABLE IF NOT EXISTS trd_inventory (
    id              VARCHAR(64)  PRIMARY KEY,
    sku_code        VARCHAR(64)  NOT NULL,
    product_name    VARCHAR(256) NOT NULL,
    category        VARCHAR(64),
    warehouse       VARCHAR(64)  NOT NULL,
    quantity        NUMERIC(18,4) NOT NULL DEFAULT 0,
    unit            VARCHAR(16)  DEFAULT '个',
    unit_cost       NUMERIC(18,4) DEFAULT 0,
    total_value     NUMERIC(18,2) DEFAULT 0,
    last_update     TIMESTAMP    DEFAULT NOW(),
    create_time     TIMESTAMP    DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_trd_inv_sku_wh ON trd_inventory(sku_code, warehouse);
COMMENT ON TABLE trd_inventory IS '库存流水 — 从进销存系统迁移';

-- 回款物理表
CREATE TABLE IF NOT EXISTS trd_payment (
    id              VARCHAR(64)  PRIMARY KEY,
    payment_no      VARCHAR(64)  NOT NULL UNIQUE,
    contract_no     VARCHAR(64)  NOT NULL,
    customer_name   VARCHAR(256),
    payment_amount  NUMERIC(18,2) NOT NULL,
    payment_date    DATE         NOT NULL,
    payment_method  VARCHAR(32)  DEFAULT 'BANK_TRANSFER',
    payment_status  VARCHAR(32)  DEFAULT 'PENDING',
    remark          TEXT,
    create_time     TIMESTAMP    DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_trd_pay_contract ON trd_payment(contract_no);
COMMENT ON TABLE trd_payment IS '回款记录 — 从财务系统迁移';

-- ============================================================
-- Part 5: Pipeline 定义 (CSV导入 + REST API → ECOS)
-- ============================================================

-- Pipeline 定义: 贸易数据导入
INSERT INTO ecos_pipeline_definition (id, name, description, status, create_time, update_time)
VALUES ('pipe_trd_001', '贸易数据导入-进销存迁移', '从进销存系统导入供应商/合同/库存/回款数据', 'DRAFT', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Pipeline 节点: CSV源 - 供应商
INSERT INTO ecos_pipeline_node (id, definition_id, node_id, type, config, position_x, position_y, created_at, updated_at)
VALUES
('node_trd_src_sup',  'pipe_trd_001', 'n1', 'SOURCE_CSV',
 '{"filePath":"/data/migrate/trd_supplier.csv","delimiter":",","hasHeader":true}',
 100, 100, NOW(), NOW()),
('node_trd_src_cont', 'pipe_trd_001', 'n2', 'SOURCE_CSV',
 '{"filePath":"/data/migrate/trd_contract.csv","delimiter":",","hasHeader":true}',
 100, 300, NOW(), NOW()),
('node_trd_src_inv',  'pipe_trd_001', 'n3', 'SOURCE_CSV',
 '{"filePath":"/data/migrate/trd_inventory.csv","delimiter":",","hasHeader":true}',
 100, 500, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Pipeline 节点: REST API源 - 回款数据（老财务系统API）
INSERT INTO ecos_pipeline_node (id, definition_id, node_id, type, config, position_x, position_y, created_at, updated_at)
VALUES
('node_trd_src_pay',  'pipe_trd_001', 'n4', 'SOURCE_REST',
 '{"baseUrl":"http://legacy-finance:8080/api/payments","authType":"TOKEN","authValue":"Bearer {{FINANCE_TOKEN}}","timeout":30}',
 100, 700, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Pipeline 节点: 输出目标
INSERT INTO ecos_pipeline_node (id, definition_id, node_id, type, config, position_x, position_y, created_at, updated_at)
VALUES
('node_trd_out_sup',  'pipe_trd_001', 'n5', 'OUTPUT_OBJECT',
 '{"entityCode":"TRD_SUPPLIER","tableName":"trd_supplier","mode":"UPSERT"}',
 500, 100, NOW(), NOW()),
('node_trd_out_cont', 'pipe_trd_001', 'n6', 'OUTPUT_OBJECT',
 '{"entityCode":"TRD_CONTRACT","tableName":"trd_contract","mode":"UPSERT"}',
 500, 300, NOW(), NOW()),
('node_trd_out_inv',  'pipe_trd_001', 'n7', 'OUTPUT_OBJECT',
 '{"entityCode":"TRD_INVENTORY","tableName":"trd_inventory","mode":"UPSERT"}',
 500, 500, NOW(), NOW()),
('node_trd_out_pay',  'pipe_trd_001', 'n8', 'OUTPUT_OBJECT',
 '{"entityCode":"TRD_PAYMENT","tableName":"trd_payment","mode":"UPSERT"}',
 500, 700, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Pipeline 边
INSERT INTO ecos_pipeline_edge (id, definition_id, from_node_id, to_node_id, created_at) VALUES
('edge_trd_001', 'pipe_trd_001', 'n1', 'n5', NOW()),
('edge_trd_002', 'pipe_trd_001', 'n2', 'n6', NOW()),
('edge_trd_003', 'pipe_trd_001', 'n3', 'n7', NOW()),
('edge_trd_004', 'pipe_trd_001', 'n4', 'n8', NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- Part 6: 种子数据
-- ============================================================

-- 供应商种子数据
INSERT INTO trd_supplier (id, supplier_code, supplier_name, contact_person, contact_phone, email, address, credit_level, status) VALUES
('sup_001', 'SUP001', '华东精密制造有限公司', '王经理', '13800001001', 'wang@hd-precision.cn', '苏州市工业园区星湖街328号', 'A', 'ACTIVE'),
('sup_002', 'SUP002', '华南电子元器件集团',     '陈总监', '13800001002', 'chen@sc-elec.cn',    '深圳市南山区科技园路66号', 'A', 'ACTIVE'),
('sup_003', 'SUP003', '北方包装材料厂',         '赵厂长', '13800001003', 'zhao@bf-pack.cn',    '天津市滨海新区临港路12号', 'B', 'ACTIVE'),
('sup_004', 'SUP004', '西部物流供应链公司',     '刘总',   '13800001004', 'liu@west-logistics.cn','成都市高新区天府大道88号', 'B', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- 采购合同种子数据
INSERT INTO trd_contract (id, contract_no, supplier_code, contract_name, contract_amount, currency, sign_date, delivery_date, contract_status, payment_terms) VALUES
('cont_001', 'CT-2026-001', 'SUP001', '精密零部件采购合同', 500000.00, 'CNY', '2026-01-15', '2026-07-15', 'EXECUTING', '30%预付+70%验收'),
('cont_002', 'CT-2026-002', 'SUP002', '电子元器件年度框架', 1200000.00,'CNY', '2026-03-01', '2026-12-31', 'EXECUTING', '月结60天'),
('cont_003', 'CT-2026-003', 'SUP003', '包装材料季度采购',   80000.00,  'CNY', '2026-06-01', '2026-08-31', 'DRAFT',     '货到付款')
ON CONFLICT (id) DO NOTHING;

-- 库存种子数据
INSERT INTO trd_inventory (id, sku_code, product_name, category, warehouse, quantity, unit, unit_cost, total_value) VALUES
('inv_001', 'SKU001', '精密齿轮D20',    '机械零件', '上海仓', 5000,   '个', 25.00,  125000.00),
('inv_002', 'SKU002', 'IC芯片STM32',    '电子元件', '深圳仓', 12000,  '片', 8.50,   102000.00),
('inv_003', 'SKU003', '防静电包装袋A4', '包装材料', '上海仓', 50000,  '个', 0.30,   15000.00),
('inv_004', 'SKU004', '传感器模块V3',   '电子模块', '深圳仓', 3000,   '个', 45.00,  135000.00),
('inv_005', 'SKU005', '轴承6205-2RS',   '机械零件', '上海仓', 8000,   '个', 12.00,  96000.00)
ON CONFLICT (id) DO NOTHING;

-- 回款种子数据
INSERT INTO trd_payment (id, payment_no, contract_no, customer_name, payment_amount, payment_date, payment_method, payment_status) VALUES
('pay_001', 'PAY-2026001', 'CT-2026-001', '华东制造客户A', 150000.00, '2026-03-15', 'BANK_TRANSFER', 'RECEIVED'),
('pay_002', 'PAY-2026002', 'CT-2026-001', '华东制造客户A', 200000.00, '2026-06-15', 'BANK_TRANSFER', 'RECEIVED'),
('pay_003', 'PAY-2026003', 'CT-2026-002', '华南电子客户B', 300000.00, '2026-04-30', 'BANK_TRANSFER', 'RECEIVED'),
('pay_004', 'PAY-2026004', 'CT-2026-003', '北方包装客户C', 80000.00,  '2026-07-15', 'BANK_TRANSFER', 'PENDING')
ON CONFLICT (id) DO NOTHING;
