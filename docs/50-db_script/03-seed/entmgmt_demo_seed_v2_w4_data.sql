-- =============================================================================
-- entmgmt_demo_seed_v2_w4_data.sql
-- 二期 W4 · 域1 数据工作台「定义面」初始化（企业经营管控场景）
--
-- 覆盖：数据源登记 → 近源层入湖管道（DAG） → 源层/治理层资源 → 字段清单
--       → 数据目录镜像 → 数据资产登记 → DQ 规则与质量调度
--
-- 五层落点（依 docs/30-设计/企业经营管控演示数据集二期-六域联动规划-2026-10-16.md §域1）：
--   SOURCE   = 9 份 CSV 台账（本脚本登记资源与字段）
--   RAW      = 管道 pl_entmgmt_ledger_raw 真实执行时由 SINK_MINIO 自动登记
--              （dataLakeResourceService.markNearSourceStructured → td_data_resource
--                layer=RAW, zone=STRUCTURED）⇒ 本脚本禁止 seed RAW 行
--   CURATED  = ecos_dw.ecos_biz_*（唯一写通道 = POST /api/v1/datanet/facts/{factType}/import）
--   SEMANTIC = 域2 本体映射（W5）
--   APPLICATION = 域5 场景（W6）
--
-- 质量处理说明（平台实况，非本脚本可选项）：
--   管道节点类型宇宙（PipelineNodeTypesCatalog）12 种中无 DQ 质量节点；
--   入 DW 的行级质量门禁由 fact import 通道的 DqRowGateImpl（DQ-F01..F11）执行；
--   dq_rule / dq_schedule 供 DQ 子系统评分与调度面使用。本脚本按此实况登记规则定义，
--   不伪造「管道内含质量节点」的假制品。
--
-- 机器值注入（禁止把本机绝对路径写入本文件）：
--   ledger_root = CSV 台账目录的**绝对路径**（SOURCE_CSV 节点契约要求 filePath 为绝对路径）
--   跑法（由仓库根现场推导，跨机可用）：
--     LEDGER="$(git rev-parse --show-toplevel)/ecos_backend/database/fixtures/entmgmt"
--     docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 \
--       -v ledger_root="$LEDGER" -f - < 本文件
--
-- 幂等：按 entmgmt 命名空间先删后插，可重复执行。
-- =============================================================================

\set ON_ERROR_STOP on

BEGIN;

-- ─── 0. 清理本命名空间（幂等重跑） ──────────────────────────────────────────
DELETE FROM ecos_data.td_data_field      WHERE field_id     LIKE 'fd_entmgmt_%';
DELETE FROM ecos_data.ecos_data_asset    WHERE asset_id     LIKE 'asset_entmgmt_%';
DELETE FROM ecos_data.td_catalog_item    WHERE catalog_id   LIKE 'cat_entmgmt_%';
DELETE FROM ecos_data.dq_schedule        WHERE id           LIKE 'dqsch_entmgmt_%';
DELETE FROM ecos_data.dq_rule            WHERE id           LIKE 'dq_entmgmt_%';
DELETE FROM ecos_data.ecos_pipeline_edge WHERE id           LIKE 'pe_entmgmt_%';
DELETE FROM ecos_data.ecos_pipeline_node WHERE id           LIKE 'pn_entmgmt_%';
DELETE FROM ecos_data.ecos_pipeline_definition WHERE id     LIKE 'pl_entmgmt_%';
DELETE FROM ecos_data.td_data_resource   WHERE resource_id  LIKE 'res_entmgmt_%';
DELETE FROM ecos_data.td_datasource      WHERE datasource_id LIKE 'ds_entmgmt_%';

-- ─── 1. 原始数据源登记（FILESYSTEM · CSV 台账根目录） ─────────────────────────
-- connection_config 键 rootPath 为数据源类型 FILESYSTEM 的必填项（DataSourceTypeCatalog）。
INSERT INTO ecos_data.td_datasource (
    datasource_id, datasource_name, datasource_type, org_id, description,
    connection_config, status, tags, metadata_config, is_default, remark,
    create_by, create_time, update_by, update_time
) VALUES (
    'ds_entmgmt_ledger',
    '企业经营管控台账文件源',
    'FILESYSTEM',
    'org_ent_group_hq',
    '以集团公司组织结构为背景的项目-成本-工时-回款台账，9 份 CSV 文件，经管道入湖至近源层',
    jsonb_build_object(
        'rootPath', :'ledger_root',
        'format', 'csv',
        'encoding', 'UTF-8',
        'delimiter', ','
    )::text,
    'ACTIVE',
    'entmgmt,ledger,csv,source-layer',
    jsonb_build_object('fileCount', 9, 'domain', 'entmgmt', 'delivery', 'CSV'),
    0,
    '二期 W4 域1 定义面 · 交付形态 CSV（xlsx 写入能力缺口见规划册 §域1 能力缺口登记）',
    'seed_entmgmt_v2', NOW(), 'seed_entmgmt_v2', NOW()
);

-- ─── 2. 入湖管道定义（SOURCE_CSV → SINK_MINIO，9 对 18 节点） ─────────────────
INSERT INTO ecos_data.ecos_pipeline_definition (
    id, name, description, definition, status, tenant_id,
    created_at, updated_at, create_time, update_time, create_by, update_by,
    is_deleted, domain, version_no
) VALUES (
    'pl_entmgmt_ledger_raw',
    '企业经营管控台账入湖管道（源层→近源层）',
    '读取 9 份台账 CSV，按数据湖存储分层规范 §三 组装 raw/structured/... 对象键写入 MinIO；执行成功后由 SINK_MINIO 自动登记近源层资源（layer=RAW, zone=STRUCTURED）',
    jsonb_build_object(
        'name', '企业经营管控台账入湖管道（源层→近源层）',
        'model', 'JSON_DAG',
        'nodeCount', 18,
        'sourceType', 'SOURCE_CSV',
        'sinkType', 'SINK_MINIO',
        'lakeZone', 'STRUCTURED',
        'schedule', jsonb_build_object('cron', '', 'enabled', false)
    ),
    'PUBLISHED',
    'ent_group',
    NOW(), NOW(), NOW(), NOW(), 'seed_entmgmt_v2', 'seed_entmgmt_v2',
    0, 'entmgmt', '1'
);

-- 台账表元数据：表名 / 中文名 / 层段 / 列序（供节点与字段两处复用）
CREATE TEMP TABLE tmp_entmgmt_ledger ON COMMIT DROP AS
SELECT * FROM (VALUES
    ('dim_project',          '项目主数据',     'master', ARRAY['project_id','project_code','project_name','company_org_id','dept_org_id','project_type','contract_amount_total','monthly_contract_base','currency','sign_date','planned_start','planned_finish','project_status']),
    ('dim_contract',         '合同主数据',     'master', ARRAY['contract_id','contract_code','project_id','customer_name','contract_amount','currency','sign_date','settlement_mode','contract_status']),
    ('dim_attribution',      '项目归属规则',   'master', ARRAY['project_id','contract_id','dept_org_id','attribution_type','attribution_ratio','effective_from','effective_to','source_evidence']),
    ('dim_staff',            '人员主数据',     'master', ARRAY['staff_code','user_id','dept_org_id','position_name','hourly_rate','currency','staff_status']),
    ('cfg_allocation_rule',  '成本分摊规则',   'config', ARRAY['rule_code','rule_name','allocation_basis','applies_to_category','pool_dept_org_id','effective_from','is_active']),
    ('fact_cost_voucher',    '成本凭证流水',   'fact',   ARRAY['voucher_no','period','project_id','dept_org_id','cost_category','direct_or_allocated','allocation_rule_code','amount','currency','entry_date','remark']),
    ('fact_timesheet',       '工时填报流水',   'fact',   ARRAY['period','staff_code','dept_org_id','project_id','fte','work_hours','hourly_rate','currency','remark']),
    ('fact_output_measure',  '产值计量流水',   'fact',   ARRAY['measure_no','period','project_id','dept_org_id','measure_type','monthly_contract_base','cumulative_measured_output','period_measured_output','currency','measure_date','remark']),
    ('fact_collection_flow', '回款流水',       'fact',   ARRAY['receipt_no','period','project_id','contract_id','dept_org_id','collected_amount','currency','receipt_date','payer_name','remark'])
) AS t(tbl, cn_name, layer_seg, cols);

-- 2.1 SOURCE_CSV 节点（filePath 必填绝对路径 — CsvConnector 契约）
INSERT INTO ecos_data.ecos_pipeline_node (
    id, definition_id, node_id, type, config, depends_on, position_x, position_y,
    created_at, updated_at
)
SELECT
    'pn_entmgmt_lr_' || tbl || '_src',
    'pl_entmgmt_ledger_raw',
    'src_' || tbl,
    'SOURCE_CSV',
    jsonb_build_object(
        'filePath', :'ledger_root' || '/' || tbl || '.csv',
        'delimiter', ',',
        'header', true,
        'encoding', 'UTF-8',
        'fetchSize', 1000
    ),
    '[]'::jsonb,
    (row_number() OVER (ORDER BY layer_seg, tbl))::int * 120,
    60,
    NOW(), NOW()
FROM tmp_entmgmt_ledger;

-- 2.2 SINK_MINIO 节点（table 必填；objectName 显式给规范前缀 raw/structured/...）
INSERT INTO ecos_data.ecos_pipeline_node (
    id, definition_id, node_id, type, config, depends_on, position_x, position_y,
    created_at, updated_at
)
SELECT
    'pn_entmgmt_lr_' || tbl || '_lake',
    'pl_entmgmt_ledger_raw',
    'lake_' || tbl,
    'SINK_MINIO',
    jsonb_build_object(
        'table', tbl,
        'objectName', 'raw/structured/entmgmt-ledger/' || tbl || '/dt=2026-01-01/' || tbl || '_ledger.csv',
        'format', 'csv',
        'columns', cols
    ),
    jsonb_build_array('src_' || tbl),
    (row_number() OVER (ORDER BY layer_seg, tbl))::int * 120,
    200,
    NOW(), NOW()
FROM tmp_entmgmt_ledger;

-- 2.3 管道边（与节点 depends_on 双写，供画布与血缘一致性）
INSERT INTO ecos_data.ecos_pipeline_edge (id, definition_id, from_node_id, to_node_id, created_at)
SELECT
    'pe_entmgmt_lr_' || tbl,
    'pl_entmgmt_ledger_raw',
    'src_' || tbl,
    'lake_' || tbl,
    NOW()
FROM tmp_entmgmt_ledger;

-- ─── 3. 源层资源（layer=SOURCE，9 行） ────────────────────────────────────────
INSERT INTO ecos_data.td_data_resource (
    resource_id, resource_name, resource_type, org_id, org_name, datasource_id,
    source_path, description, tags, status, field_count, record_count,
    create_by, create_time, update_by, update_time, layer
)
SELECT
    'res_entmgmt_src_' || tbl,
    cn_name || '（CSV 台账）',
    'FILE',
    'org_ent_group_hq',
    '集团公司总部',
    'ds_entmgmt_ledger',
    tbl || '.csv',
    '企业经营管控场景源层台账文件，域段=' || layer_seg || '，经管道 pl_entmgmt_ledger_raw 入近源层',
    'entmgmt,source,' || layer_seg,
    'ACTIVE',
    array_length(cols, 1),
    NULL,
    'seed_entmgmt_v2', NOW(), 'seed_entmgmt_v2', NOW(),
    'SOURCE'
FROM tmp_entmgmt_ledger;

-- ─── 4. 治理层资源（layer=CURATED，4 行 · 对应 DW 四张业务事实表） ─────────────
INSERT INTO ecos_data.td_data_resource (
    resource_id, resource_name, resource_type, org_id, org_name, datasource_id,
    source_path, description, tags, status, field_count, record_count,
    create_by, create_time, update_by, update_time, layer
)
SELECT
    'res_entmgmt_cur_' || t.sh,
    t.cn || '（治理层）',
    'TABLE',
    'org_ent_group_hq',
    '集团公司总部',
    d.datasource_id,
    'ecos_dw.' || t.tbl,
    '治理层业务事实表，唯一写通道 = 事实导入接口 POST /api/v1/datanet/facts/' || t.ft || '/import（行级 DQ 门禁 DQ-F01..F11）',
    'entmgmt,curated,' || t.sh,
    'ACTIVE',
    array_length(t.cols, 1),
    NULL,
    'seed_entmgmt_v2', NOW(), 'seed_entmgmt_v2', NOW(),
    'CURATED'
FROM (VALUES
    ('attribution', 'ecos_dw.ecos_biz_project_attribution', '项目归属分摊事实', 'ATTRIBUTION',
        ARRAY['project_id','contract_id','department_id','attribution_ratio','attribution_type','effective_from','effective_to','source_evidence']),
    ('stage',       'ecos_dw.ecos_biz_stage_fact',          '项目阶段产值事实', 'STAGE',
        ARRAY['project_id','department_id','period','stage','fact_type','contract_base','attribution_ratio','realization_rate','amount','currency','source_type']),
    ('resource',    'ecos_dw.ecos_biz_resource_fact',       '人员资源投入事实', 'RESOURCE',
        ARRAY['project_id','department_id','period','staff_ref_hash','staff_hash_algo','fte','work_hours','hourly_rate','cost_category','fact_type']),
    ('cost',        'ecos_dw.ecos_biz_cost_fact',           '项目成本归集事实', 'COST',
        ARRAY['project_id','is_pool','department_id','period','cost_category','direct_or_allocated','allocation_rule_ref','amount','currency','fact_type'])
) AS t(sh, tbl, cn, ft, cols)
CROSS JOIN LATERAL (
    -- 治理层落在平台主库（控制域数据源），无 ACTIVE 数据源时本段失败即暴露配置缺失
    SELECT datasource_id FROM ecos_data.td_datasource
     WHERE datasource_type = 'POSTGRESQL' AND status = 'ACTIVE'
     ORDER BY is_default DESC, create_time ASC LIMIT 1
) d;

-- ─── 5. 字段清单（td_data_field） ─────────────────────────────────────────────
-- 5.1 源层 9 表 × 各自列
INSERT INTO ecos_data.td_data_field (
    field_id, resource_id, field_name, field_alias, field_type, nullable,
    is_primary_key, description, field_order
)
SELECT
    'fd_entmgmt_src_' || x.tbl || '_' || lpad(x.ord::text, 2, '0'),
    'res_entmgmt_src_' || x.tbl,
    x.col,
    NULL,
    CASE
        WHEN x.col IN ('contract_amount_total','monthly_contract_base','contract_amount','amount','collected_amount','hourly_rate','work_hours','fte','attribution_ratio','realization_rate','period_measured_output','cumulative_measured_output') THEN 'NUMERIC'
        WHEN x.col IN ('is_active') THEN 'BOOLEAN'
        WHEN x.col ~ '_(date|from|finish)$' OR x.col IN ('sign_date','planned_start','effective_to') THEN 'DATE'
        ELSE 'VARCHAR'
    END,
    CASE WHEN x.col ~ '^(project_id|contract_id|voucher_no|measure_no|receipt_no|rule_code|staff_code)$'
              OR (x.col = 'period') THEN 0 ELSE 1 END,
    CASE WHEN x.col ~ '^(project_id|contract_id|voucher_no|measure_no|receipt_no|rule_code)$' THEN 1 ELSE 0 END,
    NULL,
    x.ord
FROM (
    SELECT l.tbl, l.cols, c.col, c.ord
    FROM tmp_entmgmt_ledger l, unnest(l.cols) WITH ORDINALITY AS c(col, ord)
) x;

-- 5.2 治理层 4 表 × 业务列
INSERT INTO ecos_data.td_data_field (
    field_id, resource_id, field_name, field_alias, field_type, nullable,
    is_primary_key, description, field_order
)
SELECT
    'fd_entmgmt_cur_' || t.sh || '_' || lpad(c.ord::text, 2, '0'),
    'res_entmgmt_cur_' || t.sh,
    c.col,
    NULL,
    CASE
        WHEN c.col IN ('attribution_ratio','realization_rate','amount','contract_base','fte','work_hours','hourly_rate') THEN 'NUMERIC'
        WHEN c.col IN ('is_pool') THEN 'BOOLEAN'
        WHEN c.col IN ('effective_from','effective_to','period') THEN 'VARCHAR'
        ELSE 'VARCHAR'
    END,
    CASE WHEN c.col ~ '^(project_id|period)$' THEN 0 ELSE 1 END,
    0,
    NULL,
    c.ord
FROM (VALUES
    ('attribution', ARRAY['project_id','contract_id','department_id','attribution_ratio','attribution_type','effective_from','effective_to','source_evidence']),
    ('stage',       ARRAY['project_id','department_id','period','stage','fact_type','contract_base','attribution_ratio','realization_rate','amount','currency','source_type']),
    ('resource',    ARRAY['project_id','department_id','period','staff_ref_hash','staff_hash_algo','fte','work_hours','hourly_rate','cost_category','fact_type']),
    ('cost',        ARRAY['project_id','is_pool','department_id','period','cost_category','direct_or_allocated','allocation_rule_ref','amount','currency','fact_type'])
) AS t(sh, cols),
unnest(t.cols) WITH ORDINALITY AS c(col, ord);

-- ─── 6. 数据目录镜像（td_catalog_item，13 行 = 9 SOURCE + 4 CURATED） ──────────
INSERT INTO ecos_data.td_catalog_item (
    catalog_id, resource_id, resource_name, resource_type, org_name, description,
    tags, category_path, access_type, data_format, field_count, record_count,
    last_updated, status, tenant_id
)
SELECT
    'cat_entmgmt_src_' || tbl,
    'res_entmgmt_src_' || tbl,
    cn_name || '（CSV 台账）',
    'FILE',
    '集团公司总部',
    '源层台账文件：' || cn_name || '（域段 ' || layer_seg || '）',
    'entmgmt,source,' || layer_seg,
    '企业经营管控/源层台账/' || layer_seg,
    'READ',
    'CSV',
    array_length(cols, 1),
    0,
    NOW(),
    'ACTIVE',
    'ent_group'
FROM tmp_entmgmt_ledger
UNION ALL
SELECT
    'cat_entmgmt_cur_' || t.sh,
    'res_entmgmt_cur_' || t.sh,
    t.cn,
    'TABLE',
    '集团公司总部',
    '治理层业务事实：' || t.cn || '（经事实导入 DQ 门禁写入）',
    'entmgmt,curated,' || t.sh,
    '企业经营管控/治理层事实',
    'READ',
    'TABLE',
    array_length(t.cols, 1),
    0,
    NOW(),
    'ACTIVE',
    'ent_group'
FROM (VALUES
    ('attribution', '项目归属分摊事实', ARRAY['project_id','contract_id','department_id','attribution_ratio','attribution_type','effective_from','effective_to','source_evidence']),
    ('stage',       '项目阶段产值事实', ARRAY['project_id','department_id','period','stage','fact_type','contract_base','attribution_ratio','realization_rate','amount','currency','source_type']),
    ('resource',    '人员资源投入事实', ARRAY['project_id','department_id','period','staff_ref_hash','staff_hash_algo','fte','work_hours','hourly_rate','cost_category','fact_type']),
    ('cost',        '项目成本归集事实', ARRAY['project_id','is_pool','department_id','period','cost_category','direct_or_allocated','allocation_rule_ref','amount','currency','fact_type'])
) AS t(sh, cn, cols);

-- ─── 7. 数据资产登记（ecos_data_asset，4 行 · 指向 CURATED 资源） ───────────────
-- 资产页读 ecos_data_asset 且要求 resource_id 已存在于 td_data_resource（§7 校验）。
INSERT INTO ecos_data.ecos_data_asset (
    asset_id, resource_id, asset_name, business_desc, owner, owner_org, data_grain,
    category_status, sensitivity_level, create_by, create_time, update_by, update_time,
    is_deleted, version_no, domain
) VALUES
    ('asset_entmgmt_attribution', 'res_entmgmt_cur_attribution', '项目归属分摊资产',
     '集团公司→事业部→项目的归属比例主数据，支撑成本与产值按归属分摊',
     'admin', '集团公司总部', '项目×部门×生效期', 'TAGGED', 'L2',
     'seed_entmgmt_v2', NOW(), 'seed_entmgmt_v2', NOW(), 0, '1', 'entmgmt'),
    ('asset_entmgmt_stage', 'res_entmgmt_cur_stage', '项目阶段产值资产',
     '项目阶段口径的签约基数、归属比例、实现率与确认金额，支撑产值达成分析',
     'admin', '集团公司总部', '项目×部门×月', 'TAGGED', 'L2',
     'seed_entmgmt_v2', NOW(), 'seed_entmgmt_v2', NOW(), 0, '1', 'entmgmt'),
    ('asset_entmgmt_resource', 'res_entmgmt_cur_resource', '人员资源投入资产',
     '人员（工号脱敏哈希）投入 FTE 与工时成本，支撑资源负荷与人力成本分析',
     'admin', '集团公司总部', '项目×部门×月×人员', 'TAGGED', 'L3',
     'seed_entmgmt_v2', NOW(), 'seed_entmgmt_v2', NOW(), 0, '1', 'entmgmt'),
    ('asset_entmgmt_cost', 'res_entmgmt_cur_cost', '项目成本归集资产',
     '直接成本与公摊池分摊成本凭证级归集，支撑项目毛利与成本结构分析',
     'admin', '集团公司总部', '项目×部门×月×成本类别', 'TAGGED', 'L3',
     'seed_entmgmt_v2', NOW(), 'seed_entmgmt_v2', NOW(), 0, '1', 'entmgmt');

-- ─── 8. DQ 规则（13 条 · target_id 指向 CURATED 资源，与资产评分口径一致） ──────
-- 契约依据：
--   DqScoreDao.loadRulesForAsset → WHERE target_kind='TABLE' AND target_id=? AND status='ACTIVE' AND is_deleted=FALSE
--   DqRuleMapper 读 parameters::text AS parametersJson（jsonb 列为权威载体；物理 parameters_json 列系 legacy 死列）
--   DqDimension.ofRuleType 词表：NOT_NULL/PRESENCE→完整性 FORMAT/RANGE/REGEX→准确性
--                              CONSISTENCY/STATISTICAL→一致性 UNIQUE→唯一性
--                              VALIDITY/ENUM→有效性 FRESHNESS/TIMELINESS→时效性
--   仅 DqFreshnessEvaluator 消费参数键 freshness_minutes / stale_minutes（其余维度参数为展示口径）
-- 规则语义与 fact import 行门禁 DqRowGateImpl（DQ-F01..F11）逐条对齐，不新增代码不执行的规则。
-- 实况登记（据 FactTemplateService COST 段 :58-70 + DqRowGateImpl businessKey :308-321 / buildRequiredFields :350-361）：
--   1) COST 必填列 = departmentId/period/costCategory/directOrAllocated/amount/factType，**不含 projectId**。
--      ⇒ 台账缺陷 D-3（project_id 空）在当前代码下不会被行门禁拒绝；is_pool 也未参与一致性校验，
--        「project_id 空且 is_pool=0」的无主成本行可直接入库。本批规则不冒充该判据，缺项已登记入册。
--   2) COST 业务键 = projectId|departmentId|period|costCategory|directOrAllocated|factType，**不含 voucher_no**
--      ⇒ D-5 由 DQ-F01 拒绝的实际依据是六元组重复，不是凭证号重复。
--   3) DQ-F09 对 type=COST 恒要求 evidenceRef ∨ sourceRef ⇒ 台账 CSV 无 evidence_ref 列，
--      导入 payload 必须补该列（正常行给引用，缺陷行留空以证明 F09 真触发）。
INSERT INTO ecos_data.dq_rule (
    id, rule_name, rule_code, category, domain, rule_type, severity,
    target_kind, target_id, target_table, target_field, parameters, status, version,
    source_type, source_ref, description,
    created_by, updated_by, created_at, updated_at, is_deleted, version_no, is_active
) VALUES
    -- 项目归属分摊
    ('dq_entmgmt_attr_notnull', '归属记录项目/部门/比例必填', 'ENT-ATTR-001', 'TECHNICAL', 'entmgmt', 'NOT_NULL', 'CRITICAL',
     'TABLE', 'res_entmgmt_cur_attribution', 'ecos_dw.ecos_biz_project_attribution', 'project_id',
     '{"threshold":100.0,"passRate":100.0,"gateRef":"DQ-F07"}', 'ACTIVE', 1,
     'MANUAL', 'pl_entmgmt_ledger_raw', '归属事实必填列缺失即整行拒绝（对应导入门禁 DQ-F07）',
     'seed_entmgmt_v2', 'seed_entmgmt_v2', NOW(), NOW(), FALSE, '1', 1),
    ('dq_entmgmt_attr_ratio_range', '归属比例值域 0~1', 'ENT-ATTR-002', 'TECHNICAL', 'entmgmt', 'RANGE', 'HIGH',
     'TABLE', 'res_entmgmt_cur_attribution', 'ecos_dw.ecos_biz_project_attribution', 'attribution_ratio',
     '{"min":0,"max":1,"threshold":100.0,"passRate":100.0,"gateRef":"DQ-F02"}', 'ACTIVE', 1,
     'MANUAL', 'pl_entmgmt_ledger_raw', '归属比例必须落在 [0,1]（对应导入门禁 DQ-F02）',
     'seed_entmgmt_v2', 'seed_entmgmt_v2', NOW(), NOW(), FALSE, '1', 1),
    ('dq_entmgmt_attr_unique', '同项目同部门归属记录批内唯一', 'ENT-ATTR-003', 'TECHNICAL', 'entmgmt', 'UNIQUE', 'CRITICAL',
     'TABLE', 'res_entmgmt_cur_attribution', 'ecos_dw.ecos_biz_project_attribution', 'project_id',
     '{"keys":["projectId","departmentId","effectiveFrom"],"threshold":100.0,"passRate":100.0,"gateRef":"DQ-F01"}', 'ACTIVE', 1,
     'MANUAL', 'pl_entmgmt_ledger_raw', '导入批次内业务键不得重复（对应导入门禁 DQ-F01）',
     'seed_entmgmt_v2', 'seed_entmgmt_v2', NOW(), NOW(), FALSE, '1', 1),
    ('dq_entmgmt_attr_sum_le_1', '单项目归属比例合计不超过 1', 'ENT-ATTR-004', 'BUSINESS', 'entmgmt', 'CONSISTENCY', 'HIGH',
     'TABLE', 'res_entmgmt_cur_attribution', 'ecos_dw.ecos_biz_project_attribution', 'attribution_ratio',
     '{"max":1.0,"threshold":100.0,"passRate":100.0,"gateRef":"DQ-F06"}', 'ACTIVE', 1,
     'MANUAL', 'pl_entmgmt_ledger_raw', '同一项目各部门归属比例之和必须 ≤ 1（对应导入门禁 DQ-F06）',
     'seed_entmgmt_v2', 'seed_entmgmt_v2', NOW(), NOW(), FALSE, '1', 1),
    -- 阶段产值
    ('dq_entmgmt_stage_period_fmt', '期间格式 YYYY-MM', 'ENT-STG-001', 'TECHNICAL', 'entmgmt', 'FORMAT', 'HIGH',
     'TABLE', 'res_entmgmt_cur_stage', 'ecos_dw.ecos_biz_stage_fact', 'period',
     '{"pattern":"^2[0-9]{3}-(0[1-9]|1[0-2])$","threshold":100.0,"passRate":100.0,"gateRef":"DQ-F04"}', 'ACTIVE', 1,
     'MANUAL', 'pl_entmgmt_ledger_raw', '期间必须为 YYYY-MM（对应导入门禁 DQ-F04）',
     'seed_entmgmt_v2', 'seed_entmgmt_v2', NOW(), NOW(), FALSE, '1', 1),
    ('dq_entmgmt_stage_amount_range', '确认金额非负且精度不超过 2 位', 'ENT-STG-002', 'TECHNICAL', 'entmgmt', 'RANGE', 'HIGH',
     'TABLE', 'res_entmgmt_cur_stage', 'ecos_dw.ecos_biz_stage_fact', 'amount',
     '{"min":0,"maxScale":2,"threshold":100.0,"passRate":100.0,"gateRef":"DQ-F03"}', 'ACTIVE', 1,
     'MANUAL', 'pl_entmgmt_ledger_raw', '金额列非负、小数位 ≤ 2（对应导入门禁 DQ-F03）',
     'seed_entmgmt_v2', 'seed_entmgmt_v2', NOW(), NOW(), FALSE, '1', 1),
    ('dq_entmgmt_stage_rate_range', '实现率值域 0~1', 'ENT-STG-003', 'TECHNICAL', 'entmgmt', 'RANGE', 'MEDIUM',
     'TABLE', 'res_entmgmt_cur_stage', 'ecos_dw.ecos_biz_stage_fact', 'realization_rate',
     '{"min":0,"max":1,"threshold":100.0,"passRate":100.0,"gateRef":"DQ-F02"}', 'ACTIVE', 1,
     'MANUAL', 'pl_entmgmt_ledger_raw', '产值实现率必须落在 [0,1]（对应导入门禁 DQ-F02）',
     'seed_entmgmt_v2', 'seed_entmgmt_v2', NOW(), NOW(), FALSE, '1', 1),
    -- 资源投入
    ('dq_entmgmt_res_staff_hash_notnull', '人员工号脱敏哈希必填', 'ENT-RES-001', 'TECHNICAL', 'entmgmt', 'PRESENCE', 'CRITICAL',
     'TABLE', 'res_entmgmt_cur_resource', 'ecos_dw.ecos_biz_resource_fact', 'staff_ref_hash',
     '{"threshold":100.0,"passRate":100.0,"gateRef":"DQ-F07"}', 'ACTIVE', 1,
     'MANUAL', 'pl_entmgmt_ledger_raw', '资源事实必须落 staff_ref_hash + staff_hash_algo（工号不得明文入湖）',
     'seed_entmgmt_v2', 'seed_entmgmt_v2', NOW(), NOW(), FALSE, '1', 1),
    ('dq_entmgmt_res_fte_range', 'FTE 值域 0~1', 'ENT-RES-002', 'TECHNICAL', 'entmgmt', 'RANGE', 'MEDIUM',
     'TABLE', 'res_entmgmt_cur_resource', 'ecos_dw.ecos_biz_resource_fact', 'fte',
     '{"min":0,"max":1,"threshold":100.0,"passRate":100.0,"gateRef":"DQ-F02"}', 'ACTIVE', 1,
     'MANUAL', 'pl_entmgmt_ledger_raw', '单月单项目 FTE 不得超过 1（对应导入门禁 DQ-F02）',
     'seed_entmgmt_v2', 'seed_entmgmt_v2', NOW(), NOW(), FALSE, '1', 1),
    -- 成本归集
    ('dq_entmgmt_cost_category_enum', '成本类别取值有效', 'ENT-CST-001', 'TECHNICAL', 'entmgmt', 'ENUM', 'MEDIUM',
     'TABLE', 'res_entmgmt_cur_cost', 'ecos_dw.ecos_biz_cost_fact', 'cost_category',
     '{"values":["SALARY","MANAGEMENT","MARKETING"],"threshold":100.0,"passRate":100.0}', 'ACTIVE', 1,
     'MANUAL', 'pl_entmgmt_ledger_raw', '成本类别必须命中 COST 模板词表（FactTemplateService 权威枚举 SALARY/MANAGEMENT/MARKETING；台账缺陷样本 CB-DEFECT-007 即小写 salary）',
     'seed_entmgmt_v2', 'seed_entmgmt_v2', NOW(), NOW(), FALSE, '1', 1),
    ('dq_entmgmt_cost_alloc_rule_ref', '分摊成本必须引用分摊规则', 'ENT-CST-002', 'BUSINESS', 'entmgmt', 'VALIDITY', 'HIGH',
     'TABLE', 'res_entmgmt_cur_cost', 'ecos_dw.ecos_biz_cost_fact', 'allocation_rule_ref',
     '{"when":"direct_or_allocated=ALLOCATED","require":"allocation_rule_ref","threshold":100.0,"passRate":100.0,"gateRef":"DQ-F08"}', 'ACTIVE', 1,
     'MANUAL', 'pl_entmgmt_ledger_raw', 'ALLOCATED 行必须带分摊规则引用（对应导入门禁 DQ-F08）。注意：原始台账该列取值为 ALLOCATION，平台词表为 ALLOCATED，须在导入 payload 构造时归一，否则 DQ-F08 恒不触发',
     'seed_entmgmt_v2', 'seed_entmgmt_v2', NOW(), NOW(), FALSE, '1', 1),
    ('dq_entmgmt_cost_evidence_ref', '实际成本必须有凭证或来源引用', 'ENT-CST-003', 'BUSINESS', 'entmgmt', 'PRESENCE', 'HIGH',
     'TABLE', 'res_entmgmt_cur_cost', 'ecos_dw.ecos_biz_cost_fact', 'evidence_ref',
     '{"when":"fact_type=ACTUAL","requireAnyOf":["evidence_ref","source_ref"],"threshold":100.0,"passRate":100.0,"gateRef":"DQ-F09"}', 'ACTIVE', 1,
     'MANUAL', 'pl_entmgmt_ledger_raw', 'ACTUAL 成本行必须有凭证引用或来源引用（对应导入门禁 DQ-F09）',
     'seed_entmgmt_v2', 'seed_entmgmt_v2', NOW(), NOW(), FALSE, '1', 1),
    -- 时效（跨资产，挂成本资产做代表）
    ('dq_entmgmt_dw_freshness', '治理层事实到达时效', 'ENT-FRS-001', 'TECHNICAL', 'entmgmt', 'FRESHNESS', 'MEDIUM',
     'TABLE', 'res_entmgmt_cur_cost', 'ecos_dw.ecos_biz_cost_fact', 'update_time',
     '{"freshness_minutes":1440,"stale_minutes":10080,"threshold":100.0,"passRate":95.0}', 'ACTIVE', 1,
     'MANUAL', 'pl_entmgmt_ledger_raw', '以最近一次规则执行时间衡量新鲜度（阈值由 DqFreshnessEvaluator 消费）',
     'seed_entmgmt_v2', 'seed_entmgmt_v2', NOW(), NOW(), FALSE, '1', 1);

-- ─── 9. DQ 质量调度（1 条 · MANUAL，由 POST /api/v1/dq/schedules/{id}/trigger 触发） ─
INSERT INTO ecos_data.dq_schedule (
    id, name, trigger_type, cron_expression, event_type, rule_ids,
    scope_type, scope_id, enabled, max_runtime_seconds,
    created_by, created_at, updated_at, is_deleted
) VALUES (
    'dqsch_entmgmt_dw',
    '企业经营管控治理层资产质量批次',
    'MANUAL',
    NULL,
    NULL,
    (SELECT jsonb_agg(id ORDER BY id) FROM ecos_data.dq_rule WHERE id LIKE 'dq_entmgmt_%'),
    'DATASOURCE',
    '*',
    TRUE,
    60,
    'seed_entmgmt_v2',
    NOW(), NOW(), FALSE
);

-- ─── 10. 落库自检（行数与五层分布） ───────────────────────────────────────────
SELECT 'datasource' AS obj, count(*) AS n FROM ecos_data.td_datasource        WHERE datasource_id LIKE 'ds_entmgmt_%'
UNION ALL SELECT 'pipeline_def',  count(*) FROM ecos_data.ecos_pipeline_definition WHERE id LIKE 'pl_entmgmt_%'
UNION ALL SELECT 'pipeline_node', count(*) FROM ecos_data.ecos_pipeline_node       WHERE definition_id = 'pl_entmgmt_ledger_raw'
UNION ALL SELECT 'pipeline_edge', count(*) FROM ecos_data.ecos_pipeline_edge       WHERE definition_id = 'pl_entmgmt_ledger_raw'
UNION ALL SELECT 'resource_source',   count(*) FROM ecos_data.td_data_resource WHERE layer = 'SOURCE'  AND resource_id LIKE 'res_entmgmt_%'
UNION ALL SELECT 'resource_curated',  count(*) FROM ecos_data.td_data_resource WHERE layer = 'CURATED' AND resource_id LIKE 'res_entmgmt_%'
UNION ALL SELECT 'resource_raw(expect 0)', count(*) FROM ecos_data.td_data_resource WHERE layer = 'RAW'
UNION ALL SELECT 'data_field', count(*) FROM ecos_data.td_data_field      WHERE field_id   LIKE 'fd_entmgmt_%'
UNION ALL SELECT 'catalog',    count(*) FROM ecos_data.td_catalog_item    WHERE catalog_id LIKE 'cat_entmgmt_%'
UNION ALL SELECT 'asset',      count(*) FROM ecos_data.ecos_data_asset    WHERE asset_id   LIKE 'asset_entmgmt_%'
UNION ALL SELECT 'dq_rule',    count(*) FROM ecos_data.dq_rule            WHERE id LIKE 'dq_entmgmt_%'
UNION ALL SELECT 'dq_schedule',count(*) FROM ecos_data.dq_schedule        WHERE id LIKE 'dqsch_entmgmt_%';

COMMIT;
