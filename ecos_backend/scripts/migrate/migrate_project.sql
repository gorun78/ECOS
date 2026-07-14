-- ============================================================
-- migrate_project.sql — 项目型企业迁移模板
-- 场景: PM → ECOS (项目/合同/进度/产值)
-- 用法: 数据工程师根据客户实际表结构调整
-- ============================================================

-- ============================================================
-- Part 1: Ontology 实体定义
-- ============================================================

-- 1.1 项目实体
INSERT INTO ecos_ontology_entity (id, ontology_id, code, name, description, entity_type, sort_order, created_at, updated_at)
VALUES ('ent_prj_project', 'ont_prj', 'PRJ_PROJECT', '项目', '工程项目主数据', 'MASTER', 1, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 1.2 项目合同实体
INSERT INTO ecos_ontology_entity (id, ontology_id, code, name, description, entity_type, sort_order, created_at, updated_at)
VALUES ('ent_prj_contract', 'ont_prj', 'PRJ_CONTRACT', '项目合同', '工程承包合同', 'TRANSACTION', 2, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 1.3 进度实体
INSERT INTO ecos_ontology_entity (id, ontology_id, code, name, description, entity_type, sort_order, created_at, updated_at)
VALUES ('ent_prj_progress', 'ont_prj', 'PRJ_PROGRESS', '项目进度', '里程碑/进度报告', 'TRANSACTION', 3, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 1.4 产值实体
INSERT INTO ecos_ontology_entity (id, ontology_id, code, name, description, entity_type, sort_order, created_at, updated_at)
VALUES ('ent_prj_output', 'ont_prj', 'PRJ_OUTPUT', '产值确认', '月度产值/完工量确认', 'TRANSACTION', 4, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- Part 2: Ontology 属性定义
-- ============================================================

-- 项目属性
INSERT INTO ecos_ontology_property (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order, created_at, updated_at) VALUES
('prop_prj_proj_code',     'ent_prj_project', 'project_code',   '项目编码',   'STRING',  1, 1, 1, NOW(), NOW()),
('prop_prj_proj_name',     'ent_prj_project', 'project_name',   '项目名称',   'STRING',  1, 1, 2, NOW(), NOW()),
('prop_prj_proj_type',     'ent_prj_project', 'project_type',   '项目类型',   'STRING',  1, 1, 3, NOW(), NOW()),
('prop_prj_proj_budget',   'ent_prj_project', 'budget_amount',  '预算金额',   'NUMBER',  1, 0, 4, NOW(), NOW()),
('prop_prj_proj_start',    'ent_prj_project', 'start_date',     '开始日期',   'DATE',    1, 0, 5, NOW(), NOW()),
('prop_prj_proj_end',      'ent_prj_project', 'end_date',       '结束日期',   'DATE',    1, 0, 6, NOW(), NOW()),
('prop_prj_proj_manager',  'ent_prj_project', 'project_manager','项目经理',   'STRING',  1, 1, 7, NOW(), NOW()),
('prop_prj_proj_status',   'ent_prj_project', 'project_status', '项目状态',   'STRING',  1, 1, 8, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 项目合同属性
INSERT INTO ecos_ontology_property (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order, created_at, updated_at) VALUES
('prop_prj_cont_no',        'ent_prj_contract', 'contract_no',       '合同编号',   'STRING',  1, 1, 1, NOW(), NOW()),
('prop_prj_cont_proj',      'ent_prj_contract', 'project_code',      '所属项目',   'STRING',  1, 1, 2, NOW(), NOW()),
('prop_prj_cont_party',     'ent_prj_contract', 'contract_party',    '合同方',     'STRING',  1, 1, 3, NOW(), NOW()),
('prop_prj_cont_amount',    'ent_prj_contract', 'contract_amount',   '合同金额',   'NUMBER',  1, 0, 4, NOW(), NOW()),
('prop_prj_cont_type',      'ent_prj_contract', 'contract_type',     '合同类型',   'STRING',  1, 0, 5, NOW(), NOW()),
('prop_prj_cont_sign_date', 'ent_prj_contract', 'sign_date',         '签订日期',   'DATE',    1, 0, 6, NOW(), NOW()),
('prop_prj_cont_status',    'ent_prj_contract', 'contract_status',   '合同状态',   'STRING',  1, 1, 7, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 进度属性
INSERT INTO ecos_ontology_property (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order, created_at, updated_at) VALUES
('prop_prj_prog_id',        'ent_prj_progress', 'progress_id',      '进度编号',   'STRING',  1, 1, 1, NOW(), NOW()),
('prop_prj_prog_proj',      'ent_prj_progress', 'project_code',     '所属项目',   'STRING',  1, 1, 2, NOW(), NOW()),
('prop_prj_prog_pct',       'ent_prj_progress', 'progress_pct',     '进度百分比', 'NUMBER',  1, 0, 3, NOW(), NOW()),
('prop_prj_prog_milestone', 'ent_prj_progress', 'milestone',        '里程碑',     'STRING',  1, 1, 4, NOW(), NOW()),
('prop_prj_prog_date',      'ent_prj_progress', 'report_date',      '报告日期',   'DATE',    1, 0, 5, NOW(), NOW()),
('prop_prj_prog_plan_pct',  'ent_prj_progress', 'planned_pct',      '计划进度',   'NUMBER',  0, 0, 6, NOW(), NOW()),
('prop_prj_prog_delay',     'ent_prj_progress', 'delay_days',       '延期天数',   'NUMBER',  0, 0, 7, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 产值属性
INSERT INTO ecos_ontology_property (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order, created_at, updated_at) VALUES
('prop_prj_out_no',      'ent_prj_output', 'output_no',       '产值单号',   'STRING',  1, 1, 1, NOW(), NOW()),
('prop_prj_out_proj',    'ent_prj_output', 'project_code',    '所属项目',   'STRING',  1, 1, 2, NOW(), NOW()),
('prop_prj_out_month',   'ent_prj_output', 'output_month',    '产值月份',   'STRING',  1, 1, 3, NOW(), NOW()),
('prop_prj_out_amount',  'ent_prj_output', 'output_amount',   '产值金额',   'NUMBER',  1, 0, 4, NOW(), NOW()),
('prop_prj_out_cum',     'ent_prj_output', 'cumulative_amount','累计产值',  'NUMBER',  0, 0, 5, NOW(), NOW()),
('prop_prj_out_status',  'ent_prj_output', 'confirm_status',  '确认状态',   'STRING',  1, 1, 6, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- Part 3: Ontology 关系定义
-- ============================================================

INSERT INTO ecos_ontology_relationship (id, source_entity_id, target_entity_id, code, name, relationship_type, created_at) VALUES
('rel_prj_001', 'ent_prj_contract',  'ent_prj_project',  'GOVERNS',     '合同管辖项目', 'MANY_TO_ONE', NOW()),
('rel_prj_002', 'ent_prj_progress',  'ent_prj_project',  'TRACKS',      '跟踪项目进度', 'MANY_TO_ONE', NOW()),
('rel_prj_003', 'ent_prj_output',    'ent_prj_project',  'MEASURES',    '度量项目产值', 'MANY_TO_ONE', NOW()),
('rel_prj_004', 'ent_prj_output',    'ent_prj_contract', 'DERIVED_FROM','来源于合同',   'MANY_TO_ONE', NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- Part 4: DDL 物理表建表
-- ============================================================

-- 项目物理表
CREATE TABLE IF NOT EXISTS prj_project (
    id              VARCHAR(64)  PRIMARY KEY,
    project_code    VARCHAR(64)  NOT NULL UNIQUE,
    project_name    VARCHAR(256) NOT NULL,
    project_type    VARCHAR(32)  DEFAULT 'CONSTRUCTION',
    budget_amount   NUMERIC(18,2) NOT NULL DEFAULT 0,
    start_date      DATE,
    end_date        DATE,
    project_manager VARCHAR(64),
    department      VARCHAR(128),
    project_status  VARCHAR(32)  DEFAULT 'PLANNING',
    description     TEXT,
    create_time     TIMESTAMP    DEFAULT NOW(),
    update_time     TIMESTAMP    DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_prj_status ON prj_project(project_status);
CREATE INDEX IF NOT EXISTS idx_prj_manager ON prj_project(project_manager);
COMMENT ON TABLE prj_project IS '项目主数据 — 从PM系统迁移';

-- 项目合同物理表
CREATE TABLE IF NOT EXISTS prj_contract (
    id              VARCHAR(64)  PRIMARY KEY,
    contract_no     VARCHAR(64)  NOT NULL UNIQUE,
    project_code    VARCHAR(64)  NOT NULL,
    contract_party  VARCHAR(256) NOT NULL,
    contract_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    contract_type   VARCHAR(32)  DEFAULT 'GENERAL_CONTRACTING',
    sign_date       DATE,
    start_date      DATE,
    end_date        DATE,
    contract_status VARCHAR(32)  DEFAULT 'DRAFT',
    payment_terms   TEXT,
    create_time     TIMESTAMP    DEFAULT NOW(),
    update_time     TIMESTAMP    DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_prj_cont_proj ON prj_contract(project_code);
COMMENT ON TABLE prj_contract IS '项目合同 — 从PM系统迁移';

-- 进度物理表
CREATE TABLE IF NOT EXISTS prj_progress (
    id              VARCHAR(64)  PRIMARY KEY,
    progress_id     VARCHAR(64)  NOT NULL UNIQUE,
    project_code    VARCHAR(64)  NOT NULL,
    milestone       VARCHAR(256) NOT NULL,
    progress_pct    NUMERIC(5,2) NOT NULL DEFAULT 0,
    planned_pct     NUMERIC(5,2) DEFAULT 0,
    delay_days      INTEGER      DEFAULT 0,
    report_date     DATE         NOT NULL,
    reporter        VARCHAR(64),
    risk_level      VARCHAR(16)  DEFAULT 'LOW',
    remark          TEXT,
    create_time     TIMESTAMP    DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_prj_prog_proj ON prj_progress(project_code);
CREATE INDEX IF NOT EXISTS idx_prj_prog_date ON prj_progress(report_date);
COMMENT ON TABLE prj_progress IS '项目进度 — 从PM系统迁移';

-- 产值物理表
CREATE TABLE IF NOT EXISTS prj_output_value (
    id              VARCHAR(64)  PRIMARY KEY,
    output_no       VARCHAR(64)  NOT NULL UNIQUE,
    project_code    VARCHAR(64)  NOT NULL,
    output_month    VARCHAR(7)   NOT NULL,
    output_amount   NUMERIC(18,2) NOT NULL DEFAULT 0,
    cumulative_amount NUMERIC(18,2) DEFAULT 0,
    contract_no     VARCHAR(64),
    confirm_status  VARCHAR(32)  DEFAULT 'PENDING',
    confirmed_by    VARCHAR(64),
    confirmed_time  TIMESTAMP,
    create_time     TIMESTAMP    DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_prj_out_proj ON prj_output_value(project_code);
CREATE INDEX IF NOT EXISTS idx_prj_out_month ON prj_output_value(output_month);
COMMENT ON TABLE prj_output_value IS '产值确认 — 从PM/财务系统迁移';

-- ============================================================
-- Part 5: Pipeline 定义 (CSV导入 → ECOS)
-- ============================================================

-- Pipeline 定义: 项目数据导入
INSERT INTO ecos_pipeline_definition (id, name, description, status, create_time, update_time)
VALUES ('pipe_prj_001', '项目数据导入-PM迁移', '从PM系统导入项目/合同/进度/产值数据', 'DRAFT', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Pipeline 节点: CSV源 (项目型企业常用CSV导出)
INSERT INTO ecos_pipeline_node (id, definition_id, node_id, type, config, position_x, position_y, created_at, updated_at)
VALUES
('node_prj_src_proj', 'pipe_prj_001', 'n1', 'SOURCE_CSV',
 '{"filePath":"/data/migrate/prj_project.csv","delimiter":",","hasHeader":true}',
 100, 100, NOW(), NOW()),
('node_prj_src_cont', 'pipe_prj_001', 'n2', 'SOURCE_CSV',
 '{"filePath":"/data/migrate/prj_contract.csv","delimiter":",","hasHeader":true}',
 100, 300, NOW(), NOW()),
('node_prj_src_prog', 'pipe_prj_001', 'n3', 'SOURCE_CSV',
 '{"filePath":"/data/migrate/prj_progress.csv","delimiter":",","hasHeader":true}',
 100, 500, NOW(), NOW()),
('node_prj_src_out',  'pipe_prj_001', 'n4', 'SOURCE_CSV',
 '{"filePath":"/data/migrate/prj_output_value.csv","delimiter":",","hasHeader":true}',
 100, 700, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Pipeline 节点: 输出目标
INSERT INTO ecos_pipeline_node (id, definition_id, node_id, type, config, position_x, position_y, created_at, updated_at)
VALUES
('node_prj_out_proj', 'pipe_prj_001', 'n5', 'OUTPUT_OBJECT',
 '{"entityCode":"PRJ_PROJECT","tableName":"prj_project","mode":"UPSERT"}',
 500, 100, NOW(), NOW()),
('node_prj_out_cont', 'pipe_prj_001', 'n6', 'OUTPUT_OBJECT',
 '{"entityCode":"PRJ_CONTRACT","tableName":"prj_contract","mode":"UPSERT"}',
 500, 300, NOW(), NOW()),
('node_prj_out_prog', 'pipe_prj_001', 'n7', 'OUTPUT_OBJECT',
 '{"entityCode":"PRJ_PROGRESS","tableName":"prj_progress","mode":"UPSERT"}',
 500, 500, NOW(), NOW()),
('node_prj_out_out',  'pipe_prj_001', 'n8', 'OUTPUT_OBJECT',
 '{"entityCode":"PRJ_OUTPUT","tableName":"prj_output_value","mode":"UPSERT"}',
 500, 700, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Pipeline 边
INSERT INTO ecos_pipeline_edge (id, definition_id, from_node_id, to_node_id, created_at) VALUES
('edge_prj_001', 'pipe_prj_001', 'n1', 'n5', NOW()),
('edge_prj_002', 'pipe_prj_001', 'n2', 'n6', NOW()),
('edge_prj_003', 'pipe_prj_001', 'n3', 'n7', NOW()),
('edge_prj_004', 'pipe_prj_001', 'n4', 'n8', NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- Part 6: 种子数据
-- ============================================================

-- 项目种子数据
INSERT INTO prj_project (id, project_code, project_name, project_type, budget_amount, start_date, end_date, project_manager, department, project_status) VALUES
('proj_001', 'PRJ-2026-001', '城市综合体A地块一期',      'CONSTRUCTION', 85000000.00,  '2026-01-01', '2027-06-30', '张工', '工程一部', 'IN_PROGRESS'),
('proj_002', 'PRJ-2026-002', '高速公路B段改扩建',        'INFRASTRUCTURE',120000000.00,'2026-03-15', '2028-12-31', '李总', '基础设施部','IN_PROGRESS'),
('proj_003', 'PRJ-2026-003', '智慧园区C区弱电智能化',    'MEP',          15000000.00,  '2026-05-01', '2027-03-31', '王经理','智能化部',  'PLANNING'),
('proj_004', 'PRJ-2026-004', '污水处理厂D升级改造',      'MUNICIPAL',    32000000.00,  '2026-02-01', '2026-12-31', '赵总', '市政部',    'IN_PROGRESS')
ON CONFLICT (id) DO NOTHING;

-- 项目合同种子数据
INSERT INTO prj_contract (id, contract_no, project_code, contract_party, contract_amount, contract_type, sign_date, start_date, end_date, contract_status, payment_terms) VALUES
('pcont_001', 'PC-2026-001', 'PRJ-2026-001', '城市建设投资集团',   85000000.00,'GENERAL_CONTRACTING','2026-01-15','2026-01-15','2027-06-30','EXECUTING','按进度节点支付'),
('pcont_002', 'PC-2026-002', 'PRJ-2026-002', '省交通建设管理局',   120000000.00,'GENERAL_CONTRACTING','2026-04-01','2026-04-01','2028-12-31','EXECUTING','季度计量支付'),
('pcont_003', 'PC-2026-003', 'PRJ-2026-003', '科技园区管委会',     15000000.00,'SUB_CONTRACTING',   '2026-05-15','2026-05-15','2027-03-31','DRAFT',    '里程碑付款'),
('pcont_004', 'PC-2026-004', 'PRJ-2026-004', '市水务集团',         32000000.00,'GENERAL_CONTRACTING','2026-02-15','2026-02-15','2026-12-31','EXECUTING','月度进度支付')
ON CONFLICT (id) DO NOTHING;

-- 进度种子数据
INSERT INTO prj_progress (id, progress_id, project_code, milestone, progress_pct, planned_pct, delay_days, report_date, reporter, risk_level, remark) VALUES
('prog_001', 'PROG-202606-001', 'PRJ-2026-001', '地基工程完成',     30.00, 30.00, 0,  '2026-06-30', '张工', 'LOW',    '按期完成'),
('prog_002', 'PROG-202606-002', 'PRJ-2026-002', '桥梁桩基完成50%',  18.00, 20.00, -5, '2026-06-30', '李总', 'MEDIUM', '因雨季略有延期'),
('prog_003', 'PROG-202606-003', 'PRJ-2026-003', '深化设计完成',     10.00, 10.00, 0,  '2026-06-30', '王经理','LOW',    '设计已通过评审'),
('prog_004', 'PROG-202606-004', 'PRJ-2026-004', '设备基础浇筑',     45.00, 40.00, 3,  '2026-06-30', '赵总', 'LOW',    '提前完成设备基础')
ON CONFLICT (id) DO NOTHING;

-- 产值种子数据
INSERT INTO prj_output_value (id, output_no, project_code, output_month, output_amount, cumulative_amount, contract_no, confirm_status, confirmed_by, confirmed_time) VALUES
('out_001', 'OV-2026-001', 'PRJ-2026-001', '2026-06', 3500000.00,  21000000.00, 'PC-2026-001', 'CONFIRMED', '业主代表A', '2026-07-01'),
('out_002', 'OV-2026-002', 'PRJ-2026-002', '2026-06', 5800000.00,  18000000.00, 'PC-2026-002', 'CONFIRMED', '监理B',     '2026-07-02'),
('out_003', 'OV-2026-003', 'PRJ-2026-004', '2026-06', 4200000.00,  14800000.00, 'PC-2026-004', 'CONFIRMED', '业主代表C', '2026-07-01'),
('out_004', 'OV-2026-004', 'PRJ-2026-001', '2026-07', 4800000.00,  25800000.00, 'PC-2026-001', 'PENDING',   NULL,        NULL)
ON CONFLICT (id) DO NOTHING;
