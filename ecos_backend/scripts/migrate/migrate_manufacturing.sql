-- ============================================================
-- migrate_manufacturing.sql — 制造型企业迁移模板
-- 场景: MES/ERP → ECOS (产线/工单/物料/质检)
-- 用法: 数据工程师根据客户实际表结构调整
-- ============================================================

-- ============================================================
-- Part 1: Ontology 实体定义
-- ============================================================

-- 1.1 产线实体
INSERT INTO ecos_ontology_entity (id, ontology_id, code, name, description, entity_type, sort_order, created_at, updated_at)
VALUES ('ent_mfg_line', 'ont_mfg', 'MFG_LINE', '产线', '制造产线主数据', 'MASTER', 1, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 1.2 工单实体
INSERT INTO ecos_ontology_entity (id, ontology_id, code, name, description, entity_type, sort_order, created_at, updated_at)
VALUES ('ent_mfg_order', 'ont_mfg', 'MFG_ORDER', '工单', '生产工单事务', 'TRANSACTION', 2, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 1.3 物料实体
INSERT INTO ecos_ontology_entity (id, ontology_id, code, name, description, entity_type, sort_order, created_at, updated_at)
VALUES ('ent_mfg_material', 'ont_mfg', 'MFG_MATERIAL', '物料', '原材料/半成品/成品', 'MASTER', 3, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 1.4 质检实体
INSERT INTO ecos_ontology_entity (id, ontology_id, code, name, description, entity_type, sort_order, created_at, updated_at)
VALUES ('ent_mfg_qc', 'ont_mfg', 'MFG_QC', '质检记录', '质量检验结果', 'TRANSACTION', 4, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- Part 2: Ontology 属性定义
-- ============================================================

-- 产线属性
INSERT INTO ecos_ontology_property (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order, created_at, updated_at) VALUES
('prop_mfg_line_code',   'ent_mfg_line', 'line_code',   '产线编码', 'STRING',  1, 1, 1, NOW(), NOW()),
('prop_mfg_line_name',   'ent_mfg_line', 'line_name',   '产线名称', 'STRING',  1, 1, 2, NOW(), NOW()),
('prop_mfg_line_workshop','ent_mfg_line','workshop',    '所属车间', 'STRING',  0, 1, 3, NOW(), NOW()),
('prop_mfg_line_status', 'ent_mfg_line', 'line_status', '产线状态', 'STRING',  1, 0, 4, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 工单属性
INSERT INTO ecos_ontology_property (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order, created_at, updated_at) VALUES
('prop_mfg_order_no',        'ent_mfg_order', 'order_no',        '工单号',       'STRING',  1, 1, 1, NOW(), NOW()),
('prop_mfg_order_line_code', 'ent_mfg_order', 'line_code',       '产线编码',     'STRING',  1, 1, 2, NOW(), NOW()),
('prop_mfg_order_product',   'ent_mfg_order', 'product_code',    '产品编码',     'STRING',  1, 1, 3, NOW(), NOW()),
('prop_mfg_order_qty',       'ent_mfg_order', 'plan_qty',        '计划数量',     'NUMBER',  1, 0, 4, NOW(), NOW()),
('prop_mfg_order_actual',    'ent_mfg_order', 'actual_qty',      '实际产量',     'NUMBER',  0, 0, 5, NOW(), NOW()),
('prop_mfg_order_start',     'ent_mfg_order', 'start_time',      '计划开始',     'DATETIME',1, 0, 6, NOW(), NOW()),
('prop_mfg_order_end',       'ent_mfg_order', 'end_time',        '计划结束',     'DATETIME',1, 0, 7, NOW(), NOW()),
('prop_mfg_order_status',    'ent_mfg_order', 'order_status',    '工单状态',     'STRING',  1, 1, 8, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 物料属性
INSERT INTO ecos_ontology_property (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order, created_at, updated_at) VALUES
('prop_mfg_mat_code',   'ent_mfg_material', 'material_code', '物料编码', 'STRING',  1, 1, 1, NOW(), NOW()),
('prop_mfg_mat_name',   'ent_mfg_material', 'material_name', '物料名称', 'STRING',  1, 1, 2, NOW(), NOW()),
('prop_mfg_mat_spec',   'ent_mfg_material', 'spec',          '规格型号', 'STRING',  0, 0, 3, NOW(), NOW()),
('prop_mfg_mat_unit',   'ent_mfg_material', 'unit',          '单位',     'STRING',  1, 0, 4, NOW(), NOW()),
('prop_mfg_mat_qty',    'ent_mfg_material', 'stock_qty',     '库存数量', 'NUMBER',  0, 0, 5, NOW(), NOW()),
('prop_mfg_mat_safe',   'ent_mfg_material', 'safety_stock',  '安全库存', 'NUMBER',  0, 0, 6, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 质检属性
INSERT INTO ecos_ontology_property (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order, created_at, updated_at) VALUES
('prop_mfg_qc_no',       'ent_mfg_qc', 'qc_no',          '检验单号', 'STRING',  1, 1, 1, NOW(), NOW()),
('prop_mfg_qc_order',    'ent_mfg_qc', 'order_no',       '关联工单', 'STRING',  1, 1, 2, NOW(), NOW()),
('prop_mfg_qc_type',     'ent_mfg_qc', 'qc_type',        '检验类型', 'STRING',  1, 0, 3, NOW(), NOW()),
('prop_mfg_qc_result',   'ent_mfg_qc', 'qc_result',      '检验结果', 'STRING',  1, 1, 4, NOW(), NOW()),
('prop_mfg_qc_pass_rate','ent_mfg_qc', 'pass_rate',      '合格率',   'NUMBER',  0, 0, 5, NOW(), NOW()),
('prop_mfg_qc_time',     'ent_mfg_qc', 'qc_time',        '检验时间', 'DATETIME',1, 0, 6, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- Part 3: Ontology 关系定义
-- ============================================================

INSERT INTO ecos_ontology_relationship (id, source_entity_id, target_entity_id, code, name, relationship_type, created_at) VALUES
('rel_mfg_001', 'ent_mfg_order',    'ent_mfg_line',     'RUNS_ON',      '在产线上执行',   'MANY_TO_ONE', NOW()),
('rel_mfg_002', 'ent_mfg_order',    'ent_mfg_material', 'PRODUCES',     '生产物料',       'MANY_TO_ONE', NOW()),
('rel_mfg_003', 'ent_mfg_qc',       'ent_mfg_order',    'INSPECTS',     '检验工单',       'MANY_TO_ONE', NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- Part 4: DDL 物理表建表
-- ============================================================

-- 产线物理表
CREATE TABLE IF NOT EXISTS mfg_production_line (
    id              VARCHAR(64)  PRIMARY KEY,
    line_code       VARCHAR(64)  NOT NULL UNIQUE,
    line_name       VARCHAR(128) NOT NULL,
    workshop        VARCHAR(128),
    line_status     VARCHAR(32)  DEFAULT 'ACTIVE',
    capacity_per_hour INTEGER    DEFAULT 0,
    create_time     TIMESTAMP    DEFAULT NOW(),
    update_time     TIMESTAMP    DEFAULT NOW()
);
COMMENT ON TABLE mfg_production_line IS '产线主数据 — 从MES迁移';

-- 工单物理表
CREATE TABLE IF NOT EXISTS mfg_work_order (
    id              VARCHAR(64)  PRIMARY KEY,
    order_no        VARCHAR(64)  NOT NULL UNIQUE,
    line_code       VARCHAR(64)  NOT NULL,
    product_code    VARCHAR(64)  NOT NULL,
    product_name    VARCHAR(128),
    plan_qty        INTEGER      NOT NULL DEFAULT 0,
    actual_qty      INTEGER      DEFAULT 0,
    defect_qty      INTEGER      DEFAULT 0,
    start_time      TIMESTAMP,
    end_time        TIMESTAMP,
    order_status    VARCHAR(32)  DEFAULT 'PLANNED',
    priority        INTEGER      DEFAULT 1,
    create_time     TIMESTAMP    DEFAULT NOW(),
    update_time     TIMESTAMP    DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_mfg_wo_line ON mfg_work_order(line_code);
CREATE INDEX IF NOT EXISTS idx_mfg_wo_status ON mfg_work_order(order_status);
COMMENT ON TABLE mfg_work_order IS '生产工单 — 从MES迁移';

-- 物料物理表
CREATE TABLE IF NOT EXISTS mfg_material (
    id              VARCHAR(64)  PRIMARY KEY,
    material_code   VARCHAR(64)  NOT NULL UNIQUE,
    material_name   VARCHAR(128) NOT NULL,
    material_type   VARCHAR(32)  DEFAULT 'RAW',
    spec            VARCHAR(256),
    unit            VARCHAR(16)  NOT NULL DEFAULT '个',
    stock_qty       NUMERIC(18,4) DEFAULT 0,
    safety_stock    NUMERIC(18,4) DEFAULT 0,
    warehouse_code  VARCHAR(64),
    create_time     TIMESTAMP    DEFAULT NOW(),
    update_time     TIMESTAMP    DEFAULT NOW()
);
COMMENT ON TABLE mfg_material IS '物料主数据 — 从ERP迁移';

-- 质检物理表
CREATE TABLE IF NOT EXISTS mfg_quality_check (
    id              VARCHAR(64)  PRIMARY KEY,
    qc_no           VARCHAR(64)  NOT NULL UNIQUE,
    order_no        VARCHAR(64)  NOT NULL,
    qc_type         VARCHAR(32)  NOT NULL DEFAULT 'INCOMING',
    sample_qty      INTEGER      DEFAULT 0,
    defect_qty      INTEGER      DEFAULT 0,
    pass_rate       NUMERIC(5,2) DEFAULT 100.00,
    qc_result       VARCHAR(32)  DEFAULT 'PENDING',
    inspector       VARCHAR(64),
    qc_time         TIMESTAMP,
    remark          TEXT,
    create_time     TIMESTAMP    DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_mfg_qc_order ON mfg_quality_check(order_no);
COMMENT ON TABLE mfg_quality_check IS '质检记录 — 从MES迁移';

-- ============================================================
-- Part 5: Pipeline 定义 (CSV导入 → ECOS)
-- ============================================================

-- Pipeline 定义: 制造数据导入
INSERT INTO ecos_pipeline_definition (id, name, description, status, create_time, update_time)
VALUES ('pipe_mfg_001', '制造数据导入-MES迁移', '从MES导出的CSV文件批量导入产线/工单/物料/质检数据', 'DRAFT', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Pipeline 节点: CSV源 - 产线
INSERT INTO ecos_pipeline_node (id, definition_id, node_id, type, config, position_x, position_y, created_at, updated_at)
VALUES
('node_mfg_src_line',  'pipe_mfg_001', 'n1', 'SOURCE_CSV',
 '{"filePath":"/data/migrate/mfg_production_line.csv","delimiter":",","hasHeader":true}',
 100, 100, NOW(), NOW()),
('node_mfg_src_order', 'pipe_mfg_001', 'n2', 'SOURCE_CSV',
 '{"filePath":"/data/migrate/mfg_work_order.csv","delimiter":",","hasHeader":true}',
 100, 300, NOW(), NOW()),
('node_mfg_src_mat',   'pipe_mfg_001', 'n3', 'SOURCE_CSV',
 '{"filePath":"/data/migrate/mfg_material.csv","delimiter":",","hasHeader":true}',
 100, 500, NOW(), NOW()),
('node_mfg_src_qc',    'pipe_mfg_001', 'n4', 'SOURCE_CSV',
 '{"filePath":"/data/migrate/mfg_quality_check.csv","delimiter":",","hasHeader":true}',
 100, 700, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Pipeline 节点: 输出目标
INSERT INTO ecos_pipeline_node (id, definition_id, node_id, type, config, position_x, position_y, created_at, updated_at)
VALUES
('node_mfg_out_line',  'pipe_mfg_001', 'n5', 'OUTPUT_OBJECT',
 '{"entityCode":"MFG_LINE","tableName":"mfg_production_line","mode":"UPSERT"}',
 500, 100, NOW(), NOW()),
('node_mfg_out_order', 'pipe_mfg_001', 'n6', 'OUTPUT_OBJECT',
 '{"entityCode":"MFG_ORDER","tableName":"mfg_work_order","mode":"UPSERT"}',
 500, 300, NOW(), NOW()),
('node_mfg_out_mat',   'pipe_mfg_001', 'n7', 'OUTPUT_OBJECT',
 '{"entityCode":"MFG_MATERIAL","tableName":"mfg_material","mode":"UPSERT"}',
 500, 500, NOW(), NOW()),
('node_mfg_out_qc',    'pipe_mfg_001', 'n8', 'OUTPUT_OBJECT',
 '{"entityCode":"MFG_QC","tableName":"mfg_quality_check","mode":"UPSERT"}',
 500, 700, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Pipeline 边
INSERT INTO ecos_pipeline_edge (id, definition_id, from_node_id, to_node_id, created_at) VALUES
('edge_mfg_001', 'pipe_mfg_001', 'n1', 'n5', NOW()),
('edge_mfg_002', 'pipe_mfg_001', 'n2', 'n6', NOW()),
('edge_mfg_003', 'pipe_mfg_001', 'n3', 'n7', NOW()),
('edge_mfg_004', 'pipe_mfg_001', 'n4', 'n8', NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- Part 6: 种子数据
-- ============================================================

-- 产线种子数据
INSERT INTO mfg_production_line (id, line_code, line_name, workshop, line_status, capacity_per_hour) VALUES
('line_001', 'L001', '总装一线', '总装车间', 'ACTIVE', 120),
('line_002', 'L002', '总装二线', '总装车间', 'ACTIVE', 100),
('line_003', 'L003', 'SMT贴片线', '电子车间', 'ACTIVE', 500),
('line_004', 'L004', '注塑线A',   '注塑车间', 'ACTIVE', 300)
ON CONFLICT (id) DO NOTHING;

-- 物料种子数据
INSERT INTO mfg_material (id, material_code, material_name, material_type, spec, unit, stock_qty, safety_stock, warehouse_code) VALUES
('mat_001', 'MC001', 'PCB主板',      'SEMI', 'V2.0 四层板', '块', 5000, 1000, 'WH-A'),
('mat_002', 'MC002', 'ABS塑料粒子',  'RAW',  'PA-757',       'kg', 2000, 500,  'WH-B'),
('mat_003', 'MC003', 'M3x12螺丝',    'RAW',  '不锈钢304',    '个', 50000, 10000,'WH-A'),
('mat_004', 'MC004', '电源适配器12V','SEMI', '12V/2A',       '个', 3000, 500,  'WH-C')
ON CONFLICT (id) DO NOTHING;

-- 工单种子数据
INSERT INTO mfg_work_order (id, order_no, line_code, product_code, product_name, plan_qty, actual_qty, start_time, end_time, order_status, priority) VALUES
('wo_001', 'WO-20260629-001', 'L001', 'P001', '智能控制器A型', 1000, 850, '2026-06-29 08:00', '2026-06-30 18:00', 'IN_PROGRESS', 1),
('wo_002', 'WO-20260629-002', 'L003', 'P002', '传感器模块',    2000, 2000,'2026-06-29 08:00', '2026-06-29 20:00', 'COMPLETED',   2)
ON CONFLICT (id) DO NOTHING;

-- 质检种子数据
INSERT INTO mfg_quality_check (id, qc_no, order_no, qc_type, sample_qty, defect_qty, pass_rate, qc_result, inspector, qc_time) VALUES
('qc_001', 'QC-20260629-001', 'WO-20260629-001', 'IN_PROCESS', 100, 2, 98.00, 'PASS', '张三', '2026-06-29 14:00'),
('qc_002', 'QC-20260629-002', 'WO-20260629-002', 'FINAL',      200, 0, 100.00,'PASS', '李四', '2026-06-29 21:00')
ON CONFLICT (id) DO NOTHING;
