-- ============================================================================
-- 08_ecos_dw.sql — ecos_dw 权威 DDL
-- ============================================================================
-- 由 pg_dump --schema-only 从真实库 sys_man 反向导出后清洗装配（2026-11-17，V349 清理后重导）。
-- 目标 schema = ecos_dw；保留对象 144 个，剔分区子表块 0 个，外键 0 条移入 09。
--
-- 清洗口径:
--   · 剥离 pg_dump 噪音（\restrict / SET / set_config / CREATE SCHEMA / OWNER）
--   · 分区表只保父表 + DEFAULT 分区，60 张按月日期子表由运行时创建
--   · 冗余 ::character varying cast 去除（MC03 口径）
--   · 外键全部延后到 09_foreign_keys.sql（跨 schema 依赖，禁按文件顺序内联）
--   · public 归位表已由 V345/V346 实库搬入归属 schema，本册直接反映搬后落点
--   · 平行误会表 users 已由 V347 统一登录权威到 td_user、V349 实库清退，本册不含
--
-- 前置: 空库 + 已执行 00_create_schemas.sql。本文件不含 IF NOT EXISTS，
--       结构不符即报错（刻意 fail-loud，禁静默跳过）。
-- ============================================================================

-- SCHEMA ecos_dw (COMMENT)
COMMENT ON SCHEMA ecos_dw IS 'DW 层（CURATED）业务表 schema — 数据工作台唯一写入，本体/知识只读';

--

-- doc (TABLE)
CREATE TABLE ecos_dw.doc (
    id character varying(64) NOT NULL,
    doc_id character varying(128) NOT NULL,
    source character varying(128),
    original_file_name character varying(512),
    object_key character varying(512),
    content_type character varying(128),
    size_bytes bigint,
    parse_status character varying(16) DEFAULT 'queued' NOT NULL,
    error_message text,
    chunk_count integer DEFAULT 0 NOT NULL,
    parsed_at timestamp without time zone,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64),
    update_by character varying(64),
    is_deleted boolean DEFAULT false NOT NULL
);

--

-- TABLE doc (COMMENT)
COMMENT ON TABLE ecos_dw.doc IS 'V186/R-10① 定性: 演示遗留（legacy）文档表，**只停写**：新文档一律写 ecos_knowledge.kb_doc（唯一权威）。V139 曾按 A1 目标态将其定义为「数据工作台唯一写入」，该口径已被 R-10① 覆写（业务域不承载文档权威）。本批不 DROP、不 ALTER（IR03；R-12 DROP 重建前置件——备份+引用扫描+代码切换——未闭环）';

--

-- COLUMN doc.doc_id (COMMENT)
COMMENT ON COLUMN ecos_dw.doc.doc_id IS 'V186 登记: 停写后本列仅作历史对账键；权威文档业务键为 ecos_knowledge.kb_doc.doc_id（应用层映射，无跨 schema FK）';

--

-- COLUMN doc.object_key (COMMENT)
COMMENT ON COLUMN ecos_dw.doc.object_key IS '近源层对象 key: raw/unstructured/{source}/{docId}/{originalFileName}';

--

-- COLUMN doc.parse_status (COMMENT)
COMMENT ON COLUMN ecos_dw.doc.parse_status IS '解析状态: queued/parsing/extracting/done/failed';

--

-- doc_chunk (TABLE)
CREATE TABLE ecos_dw.doc_chunk (
    id character varying(64) NOT NULL,
    chunk_id character varying(128) NOT NULL,
    doc_id character varying(128) NOT NULL,
    chunk_index integer NOT NULL,
    content text NOT NULL,
    char_start integer,
    char_end integer,
    metadata jsonb DEFAULT '{}'::jsonb,
    status character varying(16) DEFAULT 'active' NOT NULL,
    embedding_id character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(64),
    update_by character varying(64),
    is_deleted boolean DEFAULT false NOT NULL
);

--

-- TABLE doc_chunk (COMMENT)
COMMENT ON TABLE ecos_dw.doc_chunk IS 'V186/R-10① 定性: 演示遗留（legacy）切片表，**只停写**：切片一律写 ecos_knowledge.kb_doc_chunk。切片/向量属 K 层，迁入/落 DW 会与 MC05 冲突（R-10 ② 否决理由）；不 DROP、不 ALTER（IR03）';

--

-- COLUMN doc_chunk.metadata (COMMENT)
COMMENT ON COLUMN ecos_dw.doc_chunk.metadata IS 'V186 登记: 存量 JSONB 列，随本表停写一并冻结（MC02 不为本表补 _text 列——停写载体无读写路径，补列即制造第四形态）';

--

-- COLUMN doc_chunk.status (COMMENT)
COMMENT ON COLUMN ecos_dw.doc_chunk.status IS '分块状态: active/deprecated';

--

-- COLUMN doc_chunk.embedding_id (COMMENT)
COMMENT ON COLUMN ecos_dw.doc_chunk.embedding_id IS '关联知识向量索引 ID（知识工作台消费后回填），可空';

--

-- e2e_target (TABLE)
CREATE TABLE ecos_dw.e2e_target (
    one integer
);

--

-- ecos_application_action_outcome (TABLE)
CREATE TABLE ecos_dw.ecos_application_action_outcome (
    id character varying(36) NOT NULL,
    forecast_run_id character varying(64),
    action_id character varying(64),
    fact_type character varying(32) NOT NULL,
    fact_id character varying(36) NOT NULL,
    period character varying(7),
    outcome character varying(1000) NOT NULL,
    outcome_comment character varying(2000),
    expected_impact numeric(18,2),
    actual_impact numeric(18,2),
    currency character varying(8) DEFAULT 'CNY' NOT NULL,
    batch_id character varying(64),
    idempotency_key character varying(64),
    detail_json text,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);

--

-- TABLE ecos_application_action_outcome (COMMENT)
COMMENT ON TABLE ecos_dw.ecos_application_action_outcome IS '行动结果回灌载体（卷09 H-3：预期影响 vs 实际影响可查询物理表；append-only 只增不改；写路径=data-engine 唯一写通道（ADR-14/ST08 cognitive 禁直连本表））';

--

-- COLUMN ecos_application_action_outcome.expected_impact (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_application_action_outcome.expected_impact IS '预期影响金额 NUMERIC(18,2)（ST03-A 豁免登记：业务域 CURATED 层明文存储；RLS+CLS+审计承接；导出/AI 通道经 security-engine mask。见登记表文本 H-3-①）';

--

-- COLUMN ecos_application_action_outcome.actual_impact (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_application_action_outcome.actual_impact IS '实际影响金额 NUMERIC(18,2)（ST03-A 豁免登记同上口径，H-3-②；"实际"为回灌真值，与 expected_impact 成对呈现，差值即行动复盘读数）';

--

-- COLUMN ecos_application_action_outcome.idempotency_key (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_application_action_outcome.idempotency_key IS '回灌幂等键（应用侧先查 uniq_eaao_idem 后 INSERT，重复提交返回首写 outcome id，不产生第二行）';

--

-- COLUMN ecos_application_action_outcome.detail_json (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_application_action_outcome.detail_json IS '扩展上下文序列化 TEXT（受控 JSON，仅描述用，禁 WHERE/JOIN/索引，MC02）';

--

-- ecos_application_forecast_run (TABLE)
CREATE TABLE ecos_dw.ecos_application_forecast_run (
    id character varying(36) NOT NULL,
    forecast_run_id character varying(64) NOT NULL,
    scenario_id character varying(64) NOT NULL,
    run_type character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'RUNNING' NOT NULL,
    metric character varying(255),
    deviation numeric(9,6),
    diagnosis_result_json text,
    forecast_result_json text,
    simulation_result_json text,
    strategy_result_json text,
    decision_id character varying(64),
    is_degraded smallint DEFAULT 0 NOT NULL,
    caliber_id character varying(64),
    caliber_version character varying(20),
    as_of_time timestamp without time zone,
    project_id character varying(64),
    department_id character varying(64),
    baseline_run_id character varying(64),
    is_exploratory smallint DEFAULT 0 NOT NULL,
    overrides_json text,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone,
    snapshot_id character varying(64),
    formula_version character varying(20),
    reused_from_run_id character varying(64),
    task_id character varying(64),
    idempotency_key character varying(128),
    content_hash character varying(64)
);

--

-- TABLE ecos_application_forecast_run (COMMENT)
COMMENT ON TABLE ecos_dw.ecos_application_forecast_run IS '确定性计算产物·运行与结果快照表（R-17 ①：业务域 APPLICATION 层落 ecos_dw，经 data-engine 写通道；旧 public.ecos_scenario_run 停写保留；cognitive 只读 forecastRunId 引用，不直写本表）';

--

-- COLUMN ecos_application_forecast_run.deviation (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.deviation IS '偏差百分比（ST03-A 比率形态 NUMERIC(9,6)；派生统计量，非个人数据）';

--

-- COLUMN ecos_application_forecast_run.baseline_run_id (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.baseline_run_id IS '反事实/情景重算基准运行键（F05-10：baselineRunId+overrides 重算得新 forecast_run_id）';

--

-- COLUMN ecos_application_forecast_run.is_exploratory (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.is_exploratory IS '1=探索式通道产物（ScenarioSimulatorServiceImpl 强制 exploratory=true），响应/UI 不得作金额来源';

--

-- COLUMN ecos_application_forecast_run.snapshot_id (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.snapshot_id IS '六要素之输入快照标识（SNAPSHOT_FROZEN 后非空，指向 PRD-02 DATA-01 表五 ecos_forecast_input_snapshot；缺列期由计算层内存承载的历史行留 NULL，禁伪造回填）';

--

-- COLUMN ecos_application_forecast_run.formula_version (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.formula_version IS '六要素之公式版本（与 caliber_version 同形态 VARCHAR(20) 版本号，非自增；APPROVED 口径公式的不可变版本引用，历史 run 公式文本由此列 + caliber_id 快照重建）';

--

-- COLUMN ecos_application_forecast_run.reused_from_run_id (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.reused_from_run_id IS '幂等复用来源运行键（PRD-09 §2.1：命中同 caliberVersion+asOfTime+scopeHash+scenarioSet+overridesHash 的既有 SUCCEEDED run 时响应 reused=true；force=true 新建时本列指向被复用的基准 forecast_run_id，供 diff=0 对账锚定）';

--

-- COLUMN ecos_application_forecast_run.task_id (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.task_id IS '异步运行任务键（铁律 v2.0 §1.6 经 runtime-task ITaskManagementService.submitTask 返回的真实 taskId；禁自造 jobId；同步小样本运行为 NULL）';

--

-- COLUMN ecos_application_forecast_run.idempotency_key (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.idempotency_key IS '幂等键（详设-09 H-5：与唯一写通道 V260 台账 idempotency_key 同值，本表侧供 findByIdempotencyKey 直接定位既有 run；历史搬运行为 NULL，禁伪造回填）';

--

-- COLUMN ecos_application_forecast_run.content_hash (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.content_hash IS '内容指纹（六要素+范围规范化后的确定性摘要；同键异指纹 ⇒ 409 冲突判定依据，ForecastRunStateMachine.resolveIdempotentReuse 读回比对，禁事后改写）';

--

-- ecos_application_wagent_option_amount (TABLE)
CREATE TABLE ecos_dw.ecos_application_wagent_option_amount (
    option_id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    profit_delta numeric(18,2),
    revenue_delta numeric(18,2),
    cost_delta numeric(18,2),
    cash_impact numeric(18,2),
    id character varying(36) NOT NULL,
    domain character varying(50) DEFAULT 'entmgmt' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    trace_id character varying(64)
);

--

-- TABLE ecos_application_wagent_option_amount (COMMENT)
COMMENT ON TABLE ecos_dw.ecos_application_wagent_option_amount IS 'W 域备选方案的金额影响实列载体（V286；自 V283 控制域搬出，用户 2026-10-14 裁决）。四列均明文存储，豁免依据见《列级加密豁免登记表》§二·续·补五；写通道 = data-engine 唯一写通道';

--

-- COLUMN ecos_application_wagent_option_amount.option_id (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_application_wagent_option_amount.option_id IS '逻辑引用 ecos_ai.ecos_wagent_decision_option.id（V283）；反向引用位 = 该表 amount_ref 列。两列同值，禁第三处存金额真相（防同列双权威）';

--

-- COLUMN ecos_application_wagent_option_amount.profit_delta (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_application_wagent_option_amount.profit_delta IS '利润影响额（方案 vs 基准差额，可负）：明文 NUMERIC(18,2)，受 RLS+CLS+审计三重保护 + 导出/AI 通道经 security-engine 脱敏裁决；豁免登记 = 登记表 §二·续·补五，批准人 = 用户 2026-10-14 裁决';

--

-- COLUMN ecos_application_wagent_option_amount.revenue_delta (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_application_wagent_option_amount.revenue_delta IS '收入影响额（同上口径与豁免条件；登记表 §二·续·补五逐列行）';

--

-- COLUMN ecos_application_wagent_option_amount.cost_delta (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_application_wagent_option_amount.cost_delta IS '成本影响额（同上口径与豁免条件；登记表 §二·续·补五逐列行）';

--

-- COLUMN ecos_application_wagent_option_amount.cash_impact (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_application_wagent_option_amount.cash_impact IS '现金影响额（同上口径与豁免条件；登记表 §二·续·补五逐列行）';

--

-- ecos_biz_cost_fact (TABLE)
CREATE TABLE ecos_dw.ecos_biz_cost_fact (
    id character varying(36) NOT NULL,
    project_id character varying(36),
    is_pool smallint DEFAULT 0 NOT NULL,
    department_id character varying(36) NOT NULL,
    period character varying(7) NOT NULL,
    cost_category character varying(20),
    direct_or_allocated character varying(10),
    allocation_rule_ref character varying(36),
    amount numeric(18,2),
    currency character varying(3),
    fact_type character varying(10) NOT NULL,
    dq_status character varying(20) DEFAULT 'PENDING' NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    batch_id character varying(36),
    source_ref character varying(255),
    evidence_ref character varying(255),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    CONSTRAINT ck_bcf_is_pool CHECK ((is_pool = ANY (ARRAY[0, 1])))
);

--

-- ecos_biz_project_attribution (TABLE)
CREATE TABLE ecos_dw.ecos_biz_project_attribution (
    id character varying(36) NOT NULL,
    project_id character varying(36) NOT NULL,
    contract_id character varying(36) NOT NULL,
    department_id character varying(36) NOT NULL,
    attribution_ratio numeric(5,4) NOT NULL,
    attribution_type character varying(20) DEFAULT 'PRIMARY' NOT NULL,
    effective_from character varying(7) NOT NULL,
    effective_to character varying(7),
    source_evidence character varying(255),
    dq_status character varying(20) DEFAULT 'PENDING' NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    batch_id character varying(36),
    source_ref character varying(255),
    evidence_ref character varying(255),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    CONSTRAINT ck_bpa_ratio CHECK (((attribution_ratio >= (0)::numeric) AND (attribution_ratio <= (1)::numeric)))
);

--

-- ecos_biz_resource_fact (TABLE)
CREATE TABLE ecos_dw.ecos_biz_resource_fact (
    id character varying(36) NOT NULL,
    project_id character varying(36) NOT NULL,
    department_id character varying(36) NOT NULL,
    period character varying(7) NOT NULL,
    staff_ref_hash character varying(64) NOT NULL,
    staff_hash_algo character varying(20) DEFAULT 'SHA-256' NOT NULL,
    fte numeric(5,2),
    work_hours numeric(8,1),
    hourly_rate numeric(18,2),
    cost_category character varying(20),
    fact_type character varying(10) NOT NULL,
    dq_status character varying(20) DEFAULT 'PENDING' NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    batch_id character varying(36),
    source_ref character varying(255),
    evidence_ref character varying(255),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100)
);

--

-- ecos_biz_stage_fact (TABLE)
CREATE TABLE ecos_dw.ecos_biz_stage_fact (
    id character varying(36) NOT NULL,
    project_id character varying(36) NOT NULL,
    department_id character varying(36) NOT NULL,
    period character varying(7) NOT NULL,
    stage character varying(20) NOT NULL,
    fact_type character varying(10) NOT NULL,
    contract_base numeric(18,2),
    attribution_ratio numeric(5,4),
    realization_rate numeric(5,4),
    amount numeric(18,2),
    currency character varying(3),
    source_type character varying(20),
    dq_status character varying(20) DEFAULT 'PENDING' NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    batch_id character varying(36),
    source_ref character varying(255),
    evidence_ref character varying(255),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    CONSTRAINT ck_bsf_attr_ratio CHECK (((attribution_ratio IS NULL) OR ((attribution_ratio >= (0)::numeric) AND (attribution_ratio <= (1)::numeric)))),
    CONSTRAINT ck_bsf_real_rate CHECK (((realization_rate IS NULL) OR ((realization_rate >= (0)::numeric) AND (realization_rate <= (1)::numeric))))
);

--

-- ecos_fc_backtest (TABLE)
CREATE TABLE ecos_dw.ecos_fc_backtest (
    id character varying(36) NOT NULL,
    forecast_run_id character varying(36) NOT NULL,
    period character varying(16) NOT NULL,
    granularity character varying(16) NOT NULL,
    project_id character varying(36),
    department_id character varying(36),
    stage character varying(32),
    metric_id character varying(36) NOT NULL,
    mae numeric(8,4),
    mape numeric(8,4),
    bias_direction character varying(16),
    coverage numeric(5,4),
    data_coverage numeric(5,4),
    zero_actual_count integer,
    sample_count integer,
    sample_flag character varying(16),
    review_task_id character varying(64),
    computed_time timestamp without time zone,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_fc_backtest (COMMENT)
COMMENT ON TABLE ecos_dw.ecos_fc_backtest IS '回测五指标结果（业务域 APPLICATION，data-engine 写通道 ADR-14）；仅对 CLOSED 期间计算（§3.4），阈值判定源在 sysman 配置单源（R-66①）不入 DDL';

--

-- ecos_fc_result_detail (TABLE)
CREATE TABLE ecos_dw.ecos_fc_result_detail (
    id character varying(36) NOT NULL,
    forecast_run_id character varying(36) NOT NULL,
    caliber_version character varying(20) NOT NULL,
    as_of_time timestamp without time zone NOT NULL,
    snapshot_id character varying(36) NOT NULL,
    formula_version character varying(20) NOT NULL,
    source_ref_json text NOT NULL,
    metric_id character varying(36) NOT NULL,
    metric_code character varying(64),
    period character varying(16) NOT NULL,
    granularity character varying(16) DEFAULT 'TOTAL' NOT NULL,
    project_id character varying(36),
    department_id character varying(36),
    stage character varying(32),
    amount numeric(18,2),
    amount_p10 numeric(18,2),
    amount_p50 numeric(18,2),
    amount_p90 numeric(18,2),
    rate_value numeric(5,4),
    source_type character varying(20) NOT NULL,
    parent_detail_id character varying(36),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_fc_result_detail (COMMENT)
COMMENT ON TABLE ecos_dw.ecos_fc_result_detail IS '预测结果明细（格×指标；业务域 APPLICATION，data-engine 唯一写通道 ADR-14）；行级六要素 NOT NULL（C209），SUCCEEDED 后只读（C210 应用层）';

--

-- COLUMN ecos_fc_result_detail.amount (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_fc_result_detail.amount IS '预测金额主值（P50 同值存储冗余展示由后端保证，禁前端插值——R-62①）；明文+CLS 读取裁决，ST03-A 待登记';

--

-- COLUMN ecos_fc_result_detail.parent_detail_id (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_fc_result_detail.parent_detail_id IS '构成树父明细行（COMPUTED 逐层展开；导出/AI 回答必须复用本行集，禁旁路 §4.2）';

--

-- ecos_fc_run (TABLE)
CREATE TABLE ecos_dw.ecos_fc_run (
    id character varying(36) NOT NULL,
    caliber_id character varying(36) NOT NULL,
    caliber_version character varying(20) NOT NULL,
    formula_version character varying(20) NOT NULL,
    as_of_time timestamp without time zone NOT NULL,
    snapshot_id character varying(36) NOT NULL,
    snapshot_checksum character varying(64),
    scope_hash character varying(64),
    overrides_hash character varying(64),
    scenario_set_hash character varying(64),
    overrides_json text,
    caliber_snapshot_json text,
    status character varying(20) DEFAULT 'CREATED' NOT NULL,
    reused_from_run_id character varying(36),
    task_id character varying(64),
    error_code character varying(24),
    error_text text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_fc_run (COMMENT)
COMMENT ON TABLE ecos_dw.ecos_fc_run IS '预测运行主记录（业务域 APPLICATION，data-engine 唯一写通道，R-17①/R-59①/ADR-14）；SUCCEEDED 后只读（应用层约束 C210）';

--

-- COLUMN ecos_fc_run.as_of_time (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_fc_run.as_of_time IS '六要素·快照可见性时刻：事实行按 create_time<=asOfTime AND (is_deleted=0 OR deleted_at>asOfTime) 裁剪（§3.2）';

--

-- COLUMN ecos_fc_run.task_id (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_fc_run.task_id IS 'runtime-task 任务真值（禁自造）；异步阈值见 §3.1/C218';

--

-- ecos_forecast_input_snapshot (TABLE)
CREATE TABLE ecos_dw.ecos_forecast_input_snapshot (
    id character varying(36) NOT NULL,
    forecast_run_id character varying(36) NOT NULL,
    as_of_time timestamp without time zone NOT NULL,
    scope_hash character varying(64) NOT NULL,
    fact_refs_json text,
    metric_versions_json text,
    profile_versions_json text,
    assumption_versions_json text,
    caliber_id character varying(36),
    caliber_version character varying(20),
    checksum character varying(64),
    dq_status character varying(20) DEFAULT 'PENDING' NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    batch_id character varying(36),
    source_ref character varying(255),
    evidence_ref character varying(255),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100)
);

--

-- ecos_kb_assumption_value (TABLE)
CREATE TABLE ecos_dw.ecos_kb_assumption_value (
    id character varying(36) NOT NULL,
    assumption_id character varying(36) NOT NULL,
    assumption_key character varying(255) NOT NULL,
    value_type character varying(10) NOT NULL,
    value numeric(18,4) NOT NULL,
    value_semantic character varying(20) DEFAULT 'ABSOLUTE' NOT NULL,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--

-- ecos_kb_profile_stats (TABLE)
CREATE TABLE ecos_dw.ecos_kb_profile_stats (
    id character varying(36) NOT NULL,
    profile_id character varying(36) NOT NULL,
    profile_key character varying(255) NOT NULL,
    sample_count integer NOT NULL,
    missing_rate numeric(5,4) DEFAULT 0 NOT NULL,
    p10 numeric(18,4),
    p50 numeric(18,4),
    p90 numeric(18,4),
    mean_value numeric(18,4),
    ci_low numeric(18,4),
    ci_high numeric(18,4),
    window_from character varying(7) NOT NULL,
    window_to character varying(7) NOT NULL,
    stats_batch_id character varying(64),
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) NOT NULL,
    update_by character varying(64) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--

-- TABLE ecos_kb_profile_stats (COMMENT)
COMMENT ON TABLE ecos_dw.ecos_kb_profile_stats IS '历史画像·数值统计事实（业务域 ecos_dw；写方=data-engine 代 kb 写(ADR-14)，kb 只读组装 profiles/resolve）';

--

-- COLUMN ecos_kb_profile_stats.missing_rate (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.missing_rate IS '缺失率（概率形态 NUMERIC(5,4)；ST03-A 逐列登记）';

--

-- COLUMN ecos_kb_profile_stats.p10 (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.p10 IS '派生统计量 P10（ST03-A 说明行逐列登记，R-9 ①，不加密不豁免业务金额列规则）';

--

-- COLUMN ecos_kb_profile_stats.p50 (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.p50 IS '派生统计量 P50（ST03-A 逐列登记）';

--

-- COLUMN ecos_kb_profile_stats.p90 (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.p90 IS '派生统计量 P90（ST03-A 逐列登记）';

--

-- COLUMN ecos_kb_profile_stats.mean_value (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.mean_value IS '派生统计量 均值（ST03-A 逐列登记）';

--

-- COLUMN ecos_kb_profile_stats.ci_low (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.ci_low IS '置信区间下界（ST03-A 逐列登记）';

--

-- COLUMN ecos_kb_profile_stats.ci_high (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.ci_high IS '置信区间上界（ST03-A 逐列登记）';

--

-- ecos_kb_vector_fallback (TABLE)
CREATE TABLE ecos_dw.ecos_kb_vector_fallback (
    id character varying(36) NOT NULL,
    embedding_ref character varying(120) NOT NULL,
    document_ref character varying(64),
    chunk_index integer,
    embedding_model character varying(128) NOT NULL,
    embedding_dim integer NOT NULL,
    embedding_text text NOT NULL,
    degraded_kind character varying(20) DEFAULT 'NO_PGVector' NOT NULL,
    trace_id character varying(64),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(100) DEFAULT 'system' NOT NULL,
    update_by character varying(100) DEFAULT 'system' NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--

-- TABLE ecos_kb_vector_fallback (COMMENT)
COMMENT ON TABLE ecos_dw.ecos_kb_vector_fallback IS '形态B(V185/MC05): 向量 TEXT 降级载体（业务域 ecos_dw；pgvector 未启用档由应用写入，vectorDegraded 显式标记，非权威链）';

--

-- COLUMN ecos_kb_vector_fallback.embedding_text (COMMENT)
COMMENT ON COLUMN ecos_dw.ecos_kb_vector_fallback.embedding_text IS 'JSON 数值数组文本（如 "[0.013,-0.2,...]"，1536/512 维由 embedding_dim 自证）；MC02 合规形态：TEXT，禁 JSONB';

--

-- fc_cost_allocation_fact (TABLE)
CREATE TABLE ecos_dw.fc_cost_allocation_fact (
    id character varying(36) NOT NULL,
    project_id character varying(36) NOT NULL,
    department_id character varying(36) NOT NULL,
    period character varying(7) NOT NULL,
    hourly_rate numeric(18,2),
    amount numeric(18,2),
    allocated_amount numeric(18,2),
    currency character varying(3),
    source_type character varying(20),
    dq_status character varying(20) DEFAULT 'PENDING' NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    batch_id character varying(36),
    source_ref character varying(255),
    evidence_ref character varying(255),
    version_no character varying(20) NOT NULL,
    domain character varying(50) DEFAULT 'forecast' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100)
);

--

-- fc_project_period_fact (TABLE)
CREATE TABLE ecos_dw.fc_project_period_fact (
    id character varying(36) NOT NULL,
    project_id character varying(36) NOT NULL,
    department_id character varying(36) NOT NULL,
    period character varying(7) NOT NULL,
    plan_amount numeric(18,2),
    actual_amount numeric(18,2),
    currency character varying(3),
    source_type character varying(20),
    dq_status character varying(20) DEFAULT 'PENDING' NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    batch_id character varying(36),
    source_ref character varying(255),
    evidence_ref character varying(255),
    version_no character varying(20) NOT NULL,
    domain character varying(50) DEFAULT 'forecast' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100)
);

--

-- fc_resource_input_fact (TABLE)
CREATE TABLE ecos_dw.fc_resource_input_fact (
    id character varying(36) NOT NULL,
    project_id character varying(36) NOT NULL,
    department_id character varying(36) NOT NULL,
    period character varying(7) NOT NULL,
    staff_ref character varying(64) NOT NULL,
    hours numeric(8,1),
    hourly_rate numeric(18,2),
    cost_category character varying(20),
    dq_status character varying(20) DEFAULT 'PENDING' NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    batch_id character varying(36),
    source_ref character varying(255),
    evidence_ref character varying(255),
    version_no character varying(20) NOT NULL,
    domain character varying(50) DEFAULT 'forecast' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100)
);

--

-- doc_chunk doc_chunk_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.doc_chunk
    ADD CONSTRAINT doc_chunk_pkey PRIMARY KEY (id);

--

-- doc doc_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.doc
    ADD CONSTRAINT doc_pkey PRIMARY KEY (id);

--

-- ecos_application_wagent_option_amount ecos_application_wagent_option_amount_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.ecos_application_wagent_option_amount
    ADD CONSTRAINT ecos_application_wagent_option_amount_pkey PRIMARY KEY (id);

--

-- ecos_biz_cost_fact ecos_biz_cost_fact_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.ecos_biz_cost_fact
    ADD CONSTRAINT ecos_biz_cost_fact_pkey PRIMARY KEY (id);

--

-- ecos_biz_project_attribution ecos_biz_project_attribution_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.ecos_biz_project_attribution
    ADD CONSTRAINT ecos_biz_project_attribution_pkey PRIMARY KEY (id);

--

-- ecos_biz_resource_fact ecos_biz_resource_fact_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.ecos_biz_resource_fact
    ADD CONSTRAINT ecos_biz_resource_fact_pkey PRIMARY KEY (id);

--

-- ecos_biz_stage_fact ecos_biz_stage_fact_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.ecos_biz_stage_fact
    ADD CONSTRAINT ecos_biz_stage_fact_pkey PRIMARY KEY (id);

--

-- ecos_fc_backtest ecos_fc_backtest_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.ecos_fc_backtest
    ADD CONSTRAINT ecos_fc_backtest_pkey PRIMARY KEY (id);

--

-- ecos_fc_result_detail ecos_fc_result_detail_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.ecos_fc_result_detail
    ADD CONSTRAINT ecos_fc_result_detail_pkey PRIMARY KEY (id);

--

-- ecos_fc_run ecos_fc_run_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.ecos_fc_run
    ADD CONSTRAINT ecos_fc_run_pkey PRIMARY KEY (id);

--

-- ecos_forecast_input_snapshot ecos_forecast_input_snapshot_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.ecos_forecast_input_snapshot
    ADD CONSTRAINT ecos_forecast_input_snapshot_pkey PRIMARY KEY (id);

--

-- fc_cost_allocation_fact fc_cost_allocation_fact_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.fc_cost_allocation_fact
    ADD CONSTRAINT fc_cost_allocation_fact_pkey PRIMARY KEY (id);

--

-- fc_project_period_fact fc_project_period_fact_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.fc_project_period_fact
    ADD CONSTRAINT fc_project_period_fact_pkey PRIMARY KEY (id);

--

-- fc_resource_input_fact fc_resource_input_fact_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.fc_resource_input_fact
    ADD CONSTRAINT fc_resource_input_fact_pkey PRIMARY KEY (id);

--

-- ecos_application_action_outcome pk_ecos_application_action_outcome (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.ecos_application_action_outcome
    ADD CONSTRAINT pk_ecos_application_action_outcome PRIMARY KEY (id);

--

-- ecos_application_forecast_run pk_ecos_application_forecast_run (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.ecos_application_forecast_run
    ADD CONSTRAINT pk_ecos_application_forecast_run PRIMARY KEY (id);

--

-- ecos_kb_assumption_value pk_ecos_kb_assumption_value (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.ecos_kb_assumption_value
    ADD CONSTRAINT pk_ecos_kb_assumption_value PRIMARY KEY (id);

--

-- ecos_kb_profile_stats pk_ecos_kb_profile_stats (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.ecos_kb_profile_stats
    ADD CONSTRAINT pk_ecos_kb_profile_stats PRIMARY KEY (id);

--

-- ecos_kb_vector_fallback pk_ecos_kb_vector_fallback (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.ecos_kb_vector_fallback
    ADD CONSTRAINT pk_ecos_kb_vector_fallback PRIMARY KEY (id);

--

-- ecos_application_wagent_option_amount uk_app_wagent_option_amount (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.ecos_application_wagent_option_amount
    ADD CONSTRAINT uk_app_wagent_option_amount UNIQUE (option_id);

--

-- ecos_kb_vector_fallback uniq_ecos_kb_vec_fb_ref (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.ecos_kb_vector_fallback
    ADD CONSTRAINT uniq_ecos_kb_vec_fb_ref UNIQUE (embedding_ref, is_deleted);

--

-- ecos_kb_assumption_value uniq_kbau_assumption (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.ecos_kb_assumption_value
    ADD CONSTRAINT uniq_kbau_assumption UNIQUE (assumption_id, is_deleted);

--

-- ecos_kb_profile_stats uniq_kbps_profile (CONSTRAINT)
ALTER TABLE ONLY ecos_dw.ecos_kb_profile_stats
    ADD CONSTRAINT uniq_kbps_profile UNIQUE (profile_id, is_deleted);

--

-- idx_afcr_create (INDEX)
CREATE INDEX idx_afcr_create ON ecos_dw.ecos_application_forecast_run USING btree (create_time);

--

-- idx_afcr_idem (INDEX)
CREATE INDEX idx_afcr_idem ON ecos_dw.ecos_application_forecast_run USING btree (idempotency_key, is_deleted);

--

-- idx_afcr_reused (INDEX)
CREATE INDEX idx_afcr_reused ON ecos_dw.ecos_application_forecast_run USING btree (reused_from_run_id);

--

-- idx_afcr_scenario (INDEX)
CREATE INDEX idx_afcr_scenario ON ecos_dw.ecos_application_forecast_run USING btree (scenario_id);

--

-- idx_afcr_status (INDEX)
CREATE INDEX idx_afcr_status ON ecos_dw.ecos_application_forecast_run USING btree (status);

--

-- idx_afcr_task (INDEX)
CREATE INDEX idx_afcr_task ON ecos_dw.ecos_application_forecast_run USING btree (task_id);

--

-- idx_app_wagent_option_amount_tenant (INDEX)
CREATE INDEX idx_app_wagent_option_amount_tenant ON ecos_dw.ecos_application_wagent_option_amount USING btree (tenant_id, is_deleted);

--

-- idx_bcf_dept (INDEX)
CREATE INDEX idx_bcf_dept ON ecos_dw.ecos_biz_cost_fact USING btree (department_id, period);

--

-- idx_bcf_project (INDEX)
CREATE INDEX idx_bcf_project ON ecos_dw.ecos_biz_cost_fact USING btree (project_id, period);

--

-- idx_bpa_dept (INDEX)
CREATE INDEX idx_bpa_dept ON ecos_dw.ecos_biz_project_attribution USING btree (department_id);

--

-- idx_bpa_project (INDEX)
CREATE INDEX idx_bpa_project ON ecos_dw.ecos_biz_project_attribution USING btree (project_id);

--

-- idx_brf_hash (INDEX)
CREATE INDEX idx_brf_hash ON ecos_dw.ecos_biz_resource_fact USING btree (staff_ref_hash);

--

-- idx_brf_project (INDEX)
CREATE INDEX idx_brf_project ON ecos_dw.ecos_biz_resource_fact USING btree (project_id, period);

--

-- idx_bsf_dept (INDEX)
CREATE INDEX idx_bsf_dept ON ecos_dw.ecos_biz_stage_fact USING btree (department_id);

--

-- idx_bsf_project (INDEX)
CREATE INDEX idx_bsf_project ON ecos_dw.ecos_biz_stage_fact USING btree (project_id, period);

--

-- idx_caf_dept (INDEX)
CREATE INDEX idx_caf_dept ON ecos_dw.fc_cost_allocation_fact USING btree (department_id);

--

-- idx_caf_project (INDEX)
CREATE INDEX idx_caf_project ON ecos_dw.fc_cost_allocation_fact USING btree (project_id, period);

--

-- idx_dw_doc_chunk_doc (INDEX)
CREATE INDEX idx_dw_doc_chunk_doc ON ecos_dw.doc_chunk USING btree (doc_id);

--

-- idx_dw_doc_parse_status (INDEX)
CREATE INDEX idx_dw_doc_parse_status ON ecos_dw.doc USING btree (parse_status);

--

-- idx_eaao_act (INDEX)
CREATE INDEX idx_eaao_act ON ecos_dw.ecos_application_action_outcome USING btree (action_id, is_deleted);

--

-- idx_eaao_fact (INDEX)
CREATE INDEX idx_eaao_fact ON ecos_dw.ecos_application_action_outcome USING btree (fact_id, is_deleted);

--

-- idx_eaao_run (INDEX)
CREATE INDEX idx_eaao_run ON ecos_dw.ecos_application_action_outcome USING btree (forecast_run_id, is_deleted);

--

-- idx_ecos_kb_vec_fb_model (INDEX)
CREATE INDEX idx_ecos_kb_vec_fb_model ON ecos_dw.ecos_kb_vector_fallback USING btree (embedding_model, domain, is_deleted);

--

-- idx_fc_backtest_period (INDEX)
CREATE INDEX idx_fc_backtest_period ON ecos_dw.ecos_fc_backtest USING btree (period, granularity);

--

-- idx_fc_detail_parent (INDEX)
CREATE INDEX idx_fc_detail_parent ON ecos_dw.ecos_fc_result_detail USING btree (parent_detail_id);

--

-- idx_fc_detail_project (INDEX)
CREATE INDEX idx_fc_detail_project ON ecos_dw.ecos_fc_result_detail USING btree (project_id, period);

--

-- idx_fc_detail_run (INDEX)
CREATE INDEX idx_fc_detail_run ON ecos_dw.ecos_fc_result_detail USING btree (forecast_run_id);

--

-- idx_fc_detail_run_cell (INDEX)
CREATE INDEX idx_fc_detail_run_cell ON ecos_dw.ecos_fc_result_detail USING btree (forecast_run_id, metric_id, period, granularity);

--

-- idx_fc_run_caliber (INDEX)
CREATE INDEX idx_fc_run_caliber ON ecos_dw.ecos_fc_run USING btree (caliber_id, caliber_version);

--

-- idx_fc_run_domain_time (INDEX)
CREATE INDEX idx_fc_run_domain_time ON ecos_dw.ecos_fc_run USING btree (domain, as_of_time);

--

-- idx_fc_run_scope_hash (INDEX)
CREATE INDEX idx_fc_run_scope_hash ON ecos_dw.ecos_fc_run USING btree (scope_hash);

--

-- idx_fc_run_status (INDEX)
CREATE INDEX idx_fc_run_status ON ecos_dw.ecos_fc_run USING btree (status);

--

-- idx_fis_asof (INDEX)
CREATE INDEX idx_fis_asof ON ecos_dw.ecos_forecast_input_snapshot USING btree (as_of_time);

--

-- idx_kbav_key (INDEX)
CREATE INDEX idx_kbav_key ON ecos_dw.ecos_kb_assumption_value USING btree (assumption_key, domain);

--

-- idx_kbps_key_window (INDEX)
CREATE INDEX idx_kbps_key_window ON ecos_dw.ecos_kb_profile_stats USING btree (profile_key, window_from, window_to);

--

-- idx_kbps_metric_domain (INDEX)
CREATE INDEX idx_kbps_metric_domain ON ecos_dw.ecos_kb_profile_stats USING btree (domain, trace_id);

--

-- idx_pcf_dept (INDEX)
CREATE INDEX idx_pcf_dept ON ecos_dw.fc_project_period_fact USING btree (department_id);

--

-- idx_pcf_project (INDEX)
CREATE INDEX idx_pcf_project ON ecos_dw.fc_project_period_fact USING btree (project_id, period);

--

-- idx_rii_dept (INDEX)
CREATE INDEX idx_rii_dept ON ecos_dw.fc_resource_input_fact USING btree (department_id);

--

-- idx_rii_project (INDEX)
CREATE INDEX idx_rii_project ON ecos_dw.fc_resource_input_fact USING btree (project_id, period);

--

-- uniq_afcr_run_id (INDEX)
CREATE UNIQUE INDEX uniq_afcr_run_id ON ecos_dw.ecos_application_forecast_run USING btree (forecast_run_id, is_deleted);

--

-- uniq_bcf_active (INDEX)
CREATE UNIQUE INDEX uniq_bcf_active ON ecos_dw.ecos_biz_cost_fact USING btree (project_id, is_pool, department_id, period, cost_category, direct_or_allocated, fact_type, is_active);

--

-- uniq_bpa_active (INDEX)
CREATE UNIQUE INDEX uniq_bpa_active ON ecos_dw.ecos_biz_project_attribution USING btree (project_id, contract_id, department_id, effective_from, is_active);

--

-- uniq_brf_active (INDEX)
CREATE UNIQUE INDEX uniq_brf_active ON ecos_dw.ecos_biz_resource_fact USING btree (project_id, department_id, period, staff_ref_hash, fact_type, is_active);

--

-- uniq_bsf_active (INDEX)
CREATE UNIQUE INDEX uniq_bsf_active ON ecos_dw.ecos_biz_stage_fact USING btree (project_id, department_id, period, stage, fact_type, is_active);

--

-- uniq_caf_active (INDEX)
CREATE UNIQUE INDEX uniq_caf_active ON ecos_dw.fc_cost_allocation_fact USING btree (project_id, department_id, period, is_active);

--

-- uniq_dw_doc_chunk_chunk_id (INDEX)
CREATE UNIQUE INDEX uniq_dw_doc_chunk_chunk_id ON ecos_dw.doc_chunk USING btree (chunk_id);

--

-- uniq_dw_doc_chunk_doc_idx (INDEX)
CREATE UNIQUE INDEX uniq_dw_doc_chunk_doc_idx ON ecos_dw.doc_chunk USING btree (doc_id, chunk_index);

--

-- uniq_dw_doc_doc_id (INDEX)
CREATE UNIQUE INDEX uniq_dw_doc_doc_id ON ecos_dw.doc USING btree (doc_id);

--

-- uniq_eaao_idem (INDEX)
CREATE UNIQUE INDEX uniq_eaao_idem ON ecos_dw.ecos_application_action_outcome USING btree (idempotency_key, is_deleted);

--

-- uniq_fc_backtest_cell (INDEX)
CREATE UNIQUE INDEX uniq_fc_backtest_cell ON ecos_dw.ecos_fc_backtest USING btree (forecast_run_id, period, granularity, metric_id, is_deleted);

--

-- uniq_fis_run (INDEX)
CREATE UNIQUE INDEX uniq_fis_run ON ecos_dw.ecos_forecast_input_snapshot USING btree (forecast_run_id);

--

-- uniq_pcf_active (INDEX)
CREATE UNIQUE INDEX uniq_pcf_active ON ecos_dw.fc_project_period_fact USING btree (project_id, department_id, period, is_active);

--

-- uniq_rii_active (INDEX)
CREATE UNIQUE INDEX uniq_rii_active ON ecos_dw.fc_resource_input_fact USING btree (project_id, department_id, period, staff_ref, is_active);

--
-- PostgreSQL database dump complete
--

