-- ============================================================================
-- 02_ecos_data.sql — ecos_data 权威 DDL
-- ============================================================================
-- 由 pg_dump --schema-only 从真实库 sys_man 反向导出后清洗装配（2026-11-17，V349 清理后重导）。
-- 目标 schema = ecos_data；保留对象 244 个，剔分区子表块 0 个，外键 1 条移入 09。
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

-- dq_alert_record (TABLE)
CREATE TABLE ecos_data.dq_alert_record (
    id character varying(36) NOT NULL,
    rule_id character varying(64) NOT NULL,
    alert_level character varying(4) NOT NULL,
    alert_type character varying(32) NOT NULL,
    asset_id character varying(64),
    asset_name character varying(191),
    rule_name character varying(255),
    message text NOT NULL,
    payload jsonb,
    status character varying(16) DEFAULT 'PENDING' NOT NULL,
    escalated_to character varying(4),
    notify_count integer DEFAULT 0 NOT NULL,
    last_notify_at timestamp without time zone,
    notify_channels jsonb,
    ack_by character varying(128),
    ack_at timestamp without time zone,
    resolved_by character varying(128),
    resolved_at timestamp without time zone,
    resolved_note text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE dq_alert_record (COMMENT)
COMMENT ON TABLE ecos_data.dq_alert_record IS '告警事件记录 (P0-P3 分级, 升级链路)';

--

-- COLUMN dq_alert_record.status (COMMENT)
COMMENT ON COLUMN ecos_data.dq_alert_record.status IS '告警状态: PENDING / NOTIFIED / ACKED / RESOLVED / IGNORED / ESCALATED';

--

-- COLUMN dq_alert_record.escalated_to (COMMENT)
COMMENT ON COLUMN ecos_data.dq_alert_record.escalated_to IS '升级到的告警级别 (如 P1)';

--

-- dq_knowledge_entry (TABLE)
CREATE TABLE ecos_data.dq_knowledge_entry (
    id character varying(36) NOT NULL,
    source_type character varying(16) NOT NULL,
    source_id character varying(64) NOT NULL,
    category character varying(32),
    title character varying(191) NOT NULL,
    summary text,
    content_md text,
    entity_json jsonb DEFAULT '{}'::jsonb,
    embedding text,
    score_hint double precision,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE dq_knowledge_entry (COMMENT)
COMMENT ON TABLE ecos_data.dq_knowledge_entry IS 'DQ 知识条目（PMO-48-D T16，规则/告警/工单沉淀，供 RCA + RAG 相似检索）';

--

-- COLUMN dq_knowledge_entry.id (COMMENT)
COMMENT ON COLUMN ecos_data.dq_knowledge_entry.id IS '知识条目 ID (VARCHAR(36))';

--

-- COLUMN dq_knowledge_entry.source_type (COMMENT)
COMMENT ON COLUMN ecos_data.dq_knowledge_entry.source_type IS '来源类型: RULE/ALERT/WORK_ORDER';

--

-- COLUMN dq_knowledge_entry.source_id (COMMENT)
COMMENT ON COLUMN ecos_data.dq_knowledge_entry.source_id IS '来源记录 ID（对应 dq_rule.id / dq_alert_record.id / dq_work_order.id）';

--

-- COLUMN dq_knowledge_entry.category (COMMENT)
COMMENT ON COLUMN ecos_data.dq_knowledge_entry.category IS '分类: rule-hit/alert-pattern/fix-pattern';

--

-- COLUMN dq_knowledge_entry.title (COMMENT)
COMMENT ON COLUMN ecos_data.dq_knowledge_entry.title IS '知识条目标题 (<= 191)';

--

-- COLUMN dq_knowledge_entry.summary (COMMENT)
COMMENT ON COLUMN ecos_data.dq_knowledge_entry.summary IS '摘要（供列表展示）';

--

-- COLUMN dq_knowledge_entry.content_md (COMMENT)
COMMENT ON COLUMN ecos_data.dq_knowledge_entry.content_md IS '知识全文 Markdown';

--

-- COLUMN dq_knowledge_entry.entity_json (COMMENT)
COMMENT ON COLUMN ecos_data.dq_knowledge_entry.entity_json IS '结构化实体 JSONB (ruleName/assetId/ruleType 等)';

--

-- COLUMN dq_knowledge_entry.embedding (COMMENT)
COMMENT ON COLUMN ecos_data.dq_knowledge_entry.embedding IS '向量列（pgvector 未装，本版本降级 TEXT 存 JSON 数组字符串 DIM=1024）';

--

-- COLUMN dq_knowledge_entry.score_hint (COMMENT)
COMMENT ON COLUMN ecos_data.dq_knowledge_entry.score_hint IS '预计算相似分参考 (0.0-1.0)';

--

-- dq_report (TABLE)
CREATE TABLE ecos_data.dq_report (
    id character varying(36) NOT NULL,
    report_type character varying(16) NOT NULL,
    scope character varying(32) DEFAULT 'ALL' NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    payload_html text NOT NULL,
    pdf_object_key character varying(512),
    llm_summary text,
    row_count integer DEFAULT 0,
    alert_count integer DEFAULT 0,
    avg_score double precision,
    created_by character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE dq_report (COMMENT)
COMMENT ON TABLE ecos_data.dq_report IS 'DQ 报告（PMO-48-D T15，日报/周报/月报，HTML+PDF，AI 摘要）';

--

-- COLUMN dq_report.id (COMMENT)
COMMENT ON COLUMN ecos_data.dq_report.id IS '报告 ID (VARCHAR(36) 主键)';

--

-- COLUMN dq_report.report_type (COMMENT)
COMMENT ON COLUMN ecos_data.dq_report.report_type IS '报告类型: DAILY/WEEKLY/MONTHLY';

--

-- COLUMN dq_report.scope (COMMENT)
COMMENT ON COLUMN ecos_data.dq_report.scope IS '报告范围: ALL/NATIVE/OFI/DOMAIN:xxx';

--

-- COLUMN dq_report.start_date (COMMENT)
COMMENT ON COLUMN ecos_data.dq_report.start_date IS '报告起始日期（含）';

--

-- COLUMN dq_report.end_date (COMMENT)
COMMENT ON COLUMN ecos_data.dq_report.end_date IS '报告截止日期（含）';

--

-- COLUMN dq_report.payload_html (COMMENT)
COMMENT ON COLUMN ecos_data.dq_report.payload_html IS '内嵌 HTML（小文件 <= 200KB，直接存 TEXT）';

--

-- COLUMN dq_report.pdf_object_key (COMMENT)
COMMENT ON COLUMN ecos_data.dq_report.pdf_object_key IS 'MinIO bucket=dq-reports 内 object key（PDF > 200KB 走对象存储）';

--

-- COLUMN dq_report.llm_summary (COMMENT)
COMMENT ON COLUMN ecos_data.dq_report.llm_summary IS 'LLM 生成摘要（PMO-48-D Phase 5 接 cognitive-engine 后非空）';

--

-- COLUMN dq_report.row_count (COMMENT)
COMMENT ON COLUMN ecos_data.dq_report.row_count IS '报告覆盖的数据行数';

--

-- COLUMN dq_report.alert_count (COMMENT)
COMMENT ON COLUMN ecos_data.dq_report.alert_count IS '报告覆盖的告警位数';

--

-- COLUMN dq_report.avg_score (COMMENT)
COMMENT ON COLUMN ecos_data.dq_report.avg_score IS '报告覆盖范围内平均分 0.0-1.0';

--

-- dq_rule (TABLE)
CREATE TABLE ecos_data.dq_rule (
    id character varying(64) NOT NULL,
    rule_name character varying(255) NOT NULL,
    rule_code character varying(191),
    category character varying(32) NOT NULL,
    domain character varying(128),
    rule_type character varying(64) NOT NULL,
    severity character varying(16) DEFAULT 'MEDIUM' NOT NULL,
    target_kind character varying(32) NOT NULL,
    target_id character varying(64),
    target_table character varying(191),
    target_field character varying(191),
    target_pipeline_id character varying(64),
    parameters jsonb DEFAULT '{}'::jsonb NOT NULL,
    status character varying(32) DEFAULT 'DRAFT' NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    approved_by character varying(128),
    effective_date bigint,
    expiry_date bigint,
    source_type character varying(32) DEFAULT 'MANUAL' NOT NULL,
    source_ref character varying(64),
    description text DEFAULT ''::text,
    created_by character varying(128),
    updated_by character varying(128),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    deleted_at timestamp without time zone,
    is_deleted boolean DEFAULT false NOT NULL,
    approved_at timestamp without time zone,
    applied_at timestamp without time zone,
    legacy_rule_id character varying(36),
    parameters_json text,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    update_by character varying(100),
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL
);

--

-- TABLE dq_rule (COMMENT)
COMMENT ON TABLE ecos_data.dq_rule IS 'DQ 规则主表 (双轨合并后的统一规则表, PMO-48-A T1 + PMO-48-B V112)';

--

-- COLUMN dq_rule.category (COMMENT)
COMMENT ON COLUMN ecos_data.dq_rule.category IS '规则类别: BUSINESS / TECHNICAL / COMPLIANCE';

--

-- COLUMN dq_rule.status (COMMENT)
COMMENT ON COLUMN ecos_data.dq_rule.status IS '规则状态: DRAFT / IN_REVIEW / ACTIVE / DEPRECATED / SUPERSEDED / REJECTED';

--

-- COLUMN dq_rule.source_type (COMMENT)
COMMENT ON COLUMN ecos_data.dq_rule.source_type IS '规则来源: MANUAL / LEGACY_V2 / LEGACY_QUALITY / KB_SYNC';

--

-- COLUMN dq_rule.source_ref (COMMENT)
COMMENT ON COLUMN ecos_data.dq_rule.source_ref IS '来源追溯 (legacy 表:原始行id)';

--

-- COLUMN dq_rule.approved_at (COMMENT)
COMMENT ON COLUMN ecos_data.dq_rule.approved_at IS '审批通过时间 (submit → approve: IN_REVIEW → ACTIVE 切换)';

--

-- COLUMN dq_rule.applied_at (COMMENT)
COMMENT ON COLUMN ecos_data.dq_rule.applied_at IS '正式生效时间 (approve → ACTIVE 切换时设置, 应用方使用)';

--

-- dq_rule_check (TABLE)
CREATE TABLE ecos_data.dq_rule_check (
    id character varying(36) NOT NULL,
    rule_id character varying(64) NOT NULL,
    schedule_id character varying(64),
    trigger_type character varying(16) NOT NULL,
    executed_at timestamp without time zone DEFAULT now() NOT NULL,
    passed boolean DEFAULT false NOT NULL,
    total_rows bigint DEFAULT 0,
    failed_rows bigint DEFAULT 0,
    pass_rate double precision,
    latency_ms integer,
    error_message text,
    sample_size integer,
    sample_failures jsonb
);

--

-- TABLE dq_rule_check (COMMENT)
COMMENT ON TABLE ecos_data.dq_rule_check IS '规则执行记录 (调度/事件/手动触发)';

--

-- COLUMN dq_rule_check.trigger_type (COMMENT)
COMMENT ON COLUMN ecos_data.dq_rule_check.trigger_type IS '触发类型: SCHEDULE / EVENT / MANUAL';

--

-- dq_rule_version (TABLE)
CREATE TABLE ecos_data.dq_rule_version (
    id character varying(64) NOT NULL,
    rule_id character varying(64) NOT NULL,
    version_number integer NOT NULL,
    snapshot jsonb NOT NULL,
    changed_by character varying(128),
    changed_at timestamp without time zone DEFAULT now() NOT NULL,
    change_note text
);

--

-- TABLE dq_rule_version (COMMENT)
COMMENT ON TABLE ecos_data.dq_rule_version IS '规则版本快照 (每次修改存档, 审计追溯)';

--

-- dq_schedule (TABLE)
CREATE TABLE ecos_data.dq_schedule (
    id character varying(64) NOT NULL,
    name character varying(191) NOT NULL,
    trigger_type character varying(16) NOT NULL,
    cron_expression character varying(64),
    event_type character varying(64),
    rule_ids jsonb DEFAULT '[]'::jsonb NOT NULL,
    scope_type character varying(16) DEFAULT 'DATASOURCE',
    scope_id character varying(64),
    enabled boolean DEFAULT true NOT NULL,
    max_runtime_seconds integer DEFAULT 60,
    created_by character varying(128),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL
);

--

-- TABLE dq_schedule (COMMENT)
COMMENT ON TABLE ecos_data.dq_schedule IS 'DQ 监控调度计划（PMO-48-C T11，接 runtime-task）';

--

-- COLUMN dq_schedule.id (COMMENT)
COMMENT ON COLUMN ecos_data.dq_schedule.id IS '调度 ID (VARCHAR(64) PK)';

--

-- COLUMN dq_schedule.trigger_type (COMMENT)
COMMENT ON COLUMN ecos_data.dq_schedule.trigger_type IS '触发类型: SCHEDULE=定时 / EVENT=Pipeline事件 / MANUAL=仅手动';

--

-- COLUMN dq_schedule.cron_expression (COMMENT)
COMMENT ON COLUMN ecos_data.dq_schedule.cron_expression IS 'cron 表达式 (trigger_type=SCHEDULE 时必填)';

--

-- COLUMN dq_schedule.event_type (COMMENT)
COMMENT ON COLUMN ecos_data.dq_schedule.event_type IS '事件类型常量 (trigger_type=EVENT 时必填, 如 PIPELINE_EXECUTION_SUCCEEDED)';

--

-- COLUMN dq_schedule.rule_ids (COMMENT)
COMMENT ON COLUMN ecos_data.dq_schedule.rule_ids IS '关联规则 ID 列表 JSONB ["ruleId1","ruleId2"]';

--

-- COLUMN dq_schedule.scope_type (COMMENT)
COMMENT ON COLUMN ecos_data.dq_schedule.scope_type IS '限流作用域类型: FIELD/TABLE/DATASOURCE/DOMAIN/SYSTEM (缺省 DATASOURCE)';

--

-- COLUMN dq_schedule.scope_id (COMMENT)
COMMENT ON COLUMN ecos_data.dq_schedule.scope_id IS '限流作用域 ID (与 dq_throttle.scope_id 对齐)';

--

-- COLUMN dq_schedule.enabled (COMMENT)
COMMENT ON COLUMN ecos_data.dq_schedule.enabled IS '启用开关 (FALSE 时调度器/EVENT 监听跳过)';

--

-- COLUMN dq_schedule.max_runtime_seconds (COMMENT)
COMMENT ON COLUMN ecos_data.dq_schedule.max_runtime_seconds IS '单次执行超时秒数 (超时记 TIMEOUT 不中断)';

--

-- dq_score_asset (TABLE)
-- ✅ 权威侧（2026-11-18 用户裁决）：生产代码 5 处真 SQL 全读写本表
--   （DqScoreDao 的 SELECT/INSERT/AVG/COUNT 与 DqReportDao 的 AVG）。
--   同族另有一张 ecos_dq_score_asset（V165.1 建，14 列，0 行 0 引用）见本文件下方，
--   那是未执行完的命名收口计划留下的历史遗留，非权威。
CREATE TABLE ecos_data.dq_score_asset (
    asset_type character varying(16) NOT NULL,
    asset_id character varying(64) NOT NULL,
    overall_score double precision NOT NULL,
    rolled_up_scores jsonb DEFAULT '[]'::jsonb NOT NULL,
    last_evaluated_at timestamp without time zone DEFAULT now() NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL
);

--

-- TABLE dq_score_asset (COMMENT)
COMMENT ON TABLE ecos_data.dq_score_asset IS 'DQ 资产级评分 (每资产 6 维加权汇总, PMO-48-B T8 评分引擎写入)';

--

-- COLUMN dq_score_asset.asset_type (COMMENT)
COMMENT ON COLUMN ecos_data.dq_score_asset.asset_type IS '资产类型: DATASOURCE / TABLE (预留 FIELD/DOMAIN/SYSTEM 扩展)';

--

-- COLUMN dq_score_asset.asset_id (COMMENT)
COMMENT ON COLUMN ecos_data.dq_score_asset.asset_id IS '资产唯一 id (DATASOURCE 时为 datasource_id; TABLE 时为 table_qualified_name)';

--

-- COLUMN dq_score_asset.overall_score (COMMENT)
COMMENT ON COLUMN ecos_data.dq_score_asset.overall_score IS '加权汇总分, 0.0 - 1.0';

--

-- COLUMN dq_score_asset.rolled_up_scores (COMMENT)
COMMENT ON COLUMN ecos_data.dq_score_asset.rolled_up_scores IS '各维度明细 JSON: [{dimension, score, weight}, ...]';

--

-- COLUMN dq_score_asset.last_evaluated_at (COMMENT)
COMMENT ON COLUMN ecos_data.dq_score_asset.last_evaluated_at IS '最后一次评估时间';

--

-- COLUMN dq_score_asset.is_deleted (COMMENT)
COMMENT ON COLUMN ecos_data.dq_score_asset.is_deleted IS '逻辑删除标记 (TRUE=软删, 列表过滤)';

--

-- dq_score_snapshot (TABLE)
CREATE TABLE ecos_data.dq_score_snapshot (
    id bigint NOT NULL,
    dimension character varying(16) NOT NULL,
    scope_type character varying(16) NOT NULL,
    scope_id character varying(64) NOT NULL,
    score_value double precision NOT NULL,
    weight double precision DEFAULT 1.0 NOT NULL,
    sample_size integer,
    distinct_rule_count integer DEFAULT 0 NOT NULL,
    evaluated_at timestamp without time zone DEFAULT now() NOT NULL,
    metadata jsonb
);

--

-- TABLE dq_score_snapshot (COMMENT)
COMMENT ON TABLE ecos_data.dq_score_snapshot IS 'DQ 6 维度评分快照 (PMO-48-B T8 评分引擎写入)';

--

-- COLUMN dq_score_snapshot.dimension (COMMENT)
COMMENT ON COLUMN ecos_data.dq_score_snapshot.dimension IS '评估维度: ACCURACY/CONSISTENCY/FRESHNESS/UNIQUENESS/VALIDITY/COMPLETENESS';

--

-- COLUMN dq_score_snapshot.scope_type (COMMENT)
COMMENT ON COLUMN ecos_data.dq_score_snapshot.scope_type IS '范围类型: FIELD/TABLE/DATASOURCE/DOMAIN/SYSTEM';

--

-- COLUMN dq_score_snapshot.scope_id (COMMENT)
COMMENT ON COLUMN ecos_data.dq_score_snapshot.scope_id IS '范围实体 id (与 scope_type 匹配, TABLE 时为 datasource:table, FIELD 时为 datasource:table:field)';

--

-- COLUMN dq_score_snapshot.score_value (COMMENT)
COMMENT ON COLUMN ecos_data.dq_score_snapshot.score_value IS '该次评估的维度分, 0.0 - 1.0 规范化 (PASS_RATE)';

--

-- COLUMN dq_score_snapshot.weight (COMMENT)
COMMENT ON COLUMN ecos_data.dq_score_snapshot.weight IS '该维度在资产级汇总中的权重 (T8 按目标设置, 默认 1.0)';

--

-- COLUMN dq_score_snapshot.sample_size (COMMENT)
COMMENT ON COLUMN ecos_data.dq_score_snapshot.sample_size IS '本次评估使用的样本量 (T8 用于诊断小样本偏差)';

--

-- COLUMN dq_score_snapshot.distinct_rule_count (COMMENT)
COMMENT ON COLUMN ecos_data.dq_score_snapshot.distinct_rule_count IS '本次评估命中的规则数 (去重统计)';

--

-- COLUMN dq_score_snapshot.metadata (COMMENT)
COMMENT ON COLUMN ecos_data.dq_score_snapshot.metadata IS '评估上下文 (sampleStats / threshold / relatedAlertId 等)';

--

-- dq_score_snapshot_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_data.dq_score_snapshot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- dq_score_snapshot_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_data.dq_score_snapshot_id_seq OWNED BY ecos_data.dq_score_snapshot.id;

--

-- dq_throttle (TABLE)
CREATE TABLE ecos_data.dq_throttle (
    id character varying(64) NOT NULL,
    scope_type character varying(16) NOT NULL,
    scope_id character varying(64) NOT NULL,
    max_checks_per_day integer DEFAULT 100 NOT NULL,
    current_count integer DEFAULT 0 NOT NULL,
    reset_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE dq_throttle (COMMENT)
COMMENT ON TABLE ecos_data.dq_throttle IS 'DQ 监控限流（每资产/天最大检查数，防雪崩, PMO-48-C T11）';

--

-- COLUMN dq_throttle.max_checks_per_day (COMMENT)
COMMENT ON COLUMN ecos_data.dq_throttle.max_checks_per_day IS '每日检查上限 (超过则 runRuleBatch 跳过并记 SKIP 日志)';

--

-- COLUMN dq_throttle.current_count (COMMENT)
COMMENT ON COLUMN ecos_data.dq_throttle.current_count IS '当日内已执行检查数 (跨日自动重置)';

--

-- COLUMN dq_throttle.reset_at (COMMENT)
COMMENT ON COLUMN ecos_data.dq_throttle.reset_at IS 'current_count 重置时点 (跨日判定)';

--

-- dq_work_order (TABLE)
CREATE TABLE ecos_data.dq_work_order (
    id character varying(36) NOT NULL,
    order_no character varying(64) NOT NULL,
    alert_id character varying(36) NOT NULL,
    rule_id character varying(64) NOT NULL,
    asset_id character varying(64),
    title character varying(255) NOT NULL,
    description text,
    status character varying(16) DEFAULT 'PENDING' NOT NULL,
    handling_mode character varying(16) DEFAULT 'MANUAL' NOT NULL,
    severity character varying(16),
    assigned_to character varying(128),
    assigned_at timestamp without time zone,
    repair_action character varying(64),
    repair_status character varying(16),
    repair_log jsonb,
    verified_by character varying(128),
    verified_at timestamp without time zone,
    verify_pass boolean,
    verify_note text,
    resolved_by character varying(128),
    resolved_at timestamp without time zone,
    resolution_note text,
    preventive_actions jsonb,
    rca_result jsonb,
    rca_confidence double precision,
    rca_analyzed_at timestamp without time zone,
    retry_count integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    closed_at timestamp without time zone,
    is_deleted boolean DEFAULT false NOT NULL,
    reject_note text
);

--

-- TABLE dq_work_order (COMMENT)
COMMENT ON TABLE ecos_data.dq_work_order IS '工单 (告警→处理→验证→关闭, 溯源 RCA)';

--

-- COLUMN dq_work_order.status (COMMENT)
COMMENT ON COLUMN ecos_data.dq_work_order.status IS '工单状态: PENDING / ASSIGNED / IN_WORK / RESOLVED / VERIFIED / CLOSED / REJECTED';

--

-- COLUMN dq_work_order.handling_mode (COMMENT)
COMMENT ON COLUMN ecos_data.dq_work_order.handling_mode IS '处理模式: MANUAL / AUTO_REPAIR / EXEMPT';

--

-- COLUMN dq_work_order.reject_note (COMMENT)
COMMENT ON COLUMN ecos_data.dq_work_order.reject_note IS '驳回原因（PMO-48-D T17，Phase 3 原文拼入 description，Phase 4 独立列）';

--

-- ecos_data_asset (TABLE)
CREATE TABLE ecos_data.ecos_data_asset (
    asset_id character varying(36) NOT NULL,
    resource_id character varying(64) NOT NULL,
    asset_name character varying(256) NOT NULL,
    business_desc text,
    owner character varying(100),
    owner_org character varying(128),
    data_grain character varying(64),
    category_id character varying(36),
    sensitivity_level character varying(20) DEFAULT 'L1',
    category_status character varying(20) DEFAULT 'PENDING',
    last_tagged_by character varying(100),
    last_tagged_at timestamp without time zone,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_data_asset (COMMENT)
COMMENT ON TABLE ecos_data.ecos_data_asset IS '数据资产（业务视图）— PMO-data10 资产 CRUD / 分级 / 分类主表';

--

-- ecos_data_asset_field (TABLE)
CREATE TABLE ecos_data.ecos_data_asset_field (
    field_asset_id character varying(36) NOT NULL,
    asset_id character varying(36) NOT NULL,
    field_id character varying(64) NOT NULL,
    field_name character varying(256) NOT NULL,
    field_type character varying(64),
    data_type character varying(64) DEFAULT 'GENERAL',
    field_sensitivity character varying(20) DEFAULT 'L1',
    mask_strategy character varying(50) DEFAULT 'none',
    recommend_level character varying(20),
    recommend_source character varying(50),
    confirmed boolean DEFAULT false,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_data_asset_field (COMMENT)
COMMENT ON TABLE ecos_data.ecos_data_asset_field IS '资产-字段级敏感度 — 参考 td_data_field；mask_strategy: none/middle4/prefix3/suffix4/full';

--

-- ecos_data_category_tree (TABLE)
CREATE TABLE ecos_data.ecos_data_category_tree (
    category_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    parent_id character varying(36),
    level smallint DEFAULT 1,
    description text,
    icon character varying(50),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    sort_order integer DEFAULT 0
);

--

-- TABLE ecos_data_category_tree (COMMENT)
COMMENT ON TABLE ecos_data.ecos_data_category_tree IS '业务分类树（最多 3 级，资产/目录主用）';

--

-- ecos_data_governance_candidate (TABLE)
CREATE TABLE ecos_data.ecos_data_governance_candidate (
    id character varying(36) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    org_id character varying(64),
    candidate_type character varying(30) NOT NULL,
    target_ref character varying(200) NOT NULL,
    proposal_text text NOT NULL,
    evidence_refs_text text,
    confidence_value numeric(6,5),
    confidence_kind character varying(50),
    calibrated smallint,
    assumptions_text text,
    conflicts_text text,
    impact_text text,
    preview_pass_rate numeric(6,5),
    rollback_plan_text text,
    status character varying(24) DEFAULT 'GENERATED' NOT NULL,
    tool_contract_ref character varying(100),
    approval_ref character varying(100),
    source_run_id character varying(36),
    trace_id character varying(64),
    idempotency_key character varying(100),
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL
);

--

-- TABLE ecos_data_governance_candidate (COMMENT)
COMMENT ON TABLE ecos_data.ecos_data_governance_candidate IS '数据治理 AI 候选（补充设计 v1.0 §7）：候选态载体；正式写入仍经 data-engine 写端点/审批（§7.1）；GENERATED/VALIDATED≠APPROVED；预览只读不改变正式资产（SUP-DATA-01/02）';

--

-- ecos_data_level_def (TABLE)
CREATE TABLE ecos_data.ecos_data_level_def (
    level_code character varying(20) NOT NULL,
    level_value integer NOT NULL,
    level_name character varying(100) NOT NULL,
    level_description text,
    mask_strategy_json jsonb DEFAULT '{"type": "none"}'::jsonb,
    rls_strategy_json jsonb DEFAULT '{"type": "no_filter"}'::jsonb,
    sort_order integer DEFAULT 0,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL
);

--

-- TABLE ecos_data_level_def (COMMENT)
COMMENT ON TABLE ecos_data.ecos_data_level_def IS '数据分级字典（L1..L4）— data-engine 唯一权威（替代已 deprecated 的 DataClassificationServiceImpl 内存 4 级）';

--

-- ecos_data_lineage_edge (TABLE)
CREATE TABLE ecos_data.ecos_data_lineage_edge (
    id character varying(64) NOT NULL,
    source_node_id character varying(64) NOT NULL,
    target_node_id character varying(64) NOT NULL,
    edge_type character varying(30) NOT NULL,
    pipeline_task_id character varying(64),
    transformation character varying(500),
    properties jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL
);

--

-- TABLE ecos_data_lineage_edge (COMMENT)
COMMENT ON TABLE ecos_data.ecos_data_lineage_edge IS 'Data lineage edge — data flow between nodes';

--

-- COLUMN ecos_data_lineage_edge.edge_type (COMMENT)
COMMENT ON COLUMN ecos_data.ecos_data_lineage_edge.edge_type IS 'DATA_FLOW / DERIVATION / DEPENDENCY';

--

-- COLUMN ecos_data_lineage_edge.pipeline_task_id (COMMENT)
COMMENT ON COLUMN ecos_data.ecos_data_lineage_edge.pipeline_task_id IS 'Associated pipeline task that creates this data flow';

--

-- ecos_data_lineage_node (TABLE)
CREATE TABLE ecos_data.ecos_data_lineage_node (
    id character varying(64) NOT NULL,
    node_type character varying(20) NOT NULL,
    name character varying(200) NOT NULL,
    schema_name character varying(100),
    table_name character varying(200),
    datasource_id character varying(64),
    layer character varying(20),
    properties jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    last_incremental_sync_at timestamp without time zone,
    pipeline_task_id character varying(64)
);

--

-- TABLE ecos_data_lineage_node (COMMENT)
COMMENT ON TABLE ecos_data.ecos_data_lineage_node IS 'Data lineage node — represents a data source, target, or transform point';

--

-- COLUMN ecos_data_lineage_node.node_type (COMMENT)
COMMENT ON COLUMN ecos_data.ecos_data_lineage_node.node_type IS 'SOURCE / TARGET / TRANSFORM';

--

-- COLUMN ecos_data_lineage_node.layer (COMMENT)
COMMENT ON COLUMN ecos_data.ecos_data_lineage_node.layer IS 'SOURCE / RAW / CURATED / SEMANTIC / APPLICATION';

--

-- COLUMN ecos_data_lineage_node.last_incremental_sync_at (COMMENT)
COMMENT ON COLUMN ecos_data.ecos_data_lineage_node.last_incremental_sync_at IS '增量重建水位线：/api/v1/engine/data/lineage/topology/rebuild 增量模式执行完成后写入';

--

-- COLUMN ecos_data_lineage_node.pipeline_task_id (COMMENT)
COMMENT ON COLUMN ecos_data.ecos_data_lineage_node.pipeline_task_id IS '来源 pipeline definition/task ID';

--

-- ecos_data_period_status (TABLE)
CREATE TABLE ecos_data.ecos_data_period_status (
    id character varying(36) NOT NULL,
    period character varying(7) NOT NULL,
    status character varying(10) DEFAULT 'OPEN' NOT NULL,
    closed_by character varying(64),
    closed_at timestamp without time zone,
    close_reason character varying(500),
    close_detail_json text,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_by character varying(64) DEFAULT 'system' NOT NULL,
    update_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone
);

--

-- TABLE ecos_data_period_status (COMMENT)
COMMENT ON TABLE ecos_data.ecos_data_period_status IS '期间关账状态表（卷09 H-3：控制域真身；关账=OPEN→CLOSED 单向；历史事实行永不回写；降级路径=配置期锁 ecos.data.facts.closed-periods）';

--

-- COLUMN ecos_data_period_status.period (COMMENT)
COMMENT ON COLUMN ecos_data.ecos_data_period_status.period IS '期间键 YYYY-MM（与 PeriodLock 端口入参同形）';

--

-- COLUMN ecos_data_period_status.status (COMMENT)
COMMENT ON COLUMN ecos_data.ecos_data_period_status.status IS 'OPEN/CLOSED（应用侧枚举；CLOSED 后导入链对该期间 409 ECOS-DATA-022）';

--

-- COLUMN ecos_data_period_status.closed_by (COMMENT)
COMMENT ON COLUMN ecos_data.ecos_data_period_status.closed_by IS '关账操作者（security-engine OPA 财务角色裁决通过后写入；禁硬编码角色映射）';

--

-- COLUMN ecos_data_period_status.close_detail_json (COMMENT)
COMMENT ON COLUMN ecos_data.ecos_data_period_status.close_detail_json IS '关账附加上下文序列化 TEXT（受控 JSON，仅描述用，禁 WHERE/JOIN/索引，MC02）';

--

-- ecos_dq_legacy_migration_report (TABLE)
CREATE TABLE ecos_data.ecos_dq_legacy_migration_report (
    id character varying(36) NOT NULL,
    migration_batch character varying(64) DEFAULT 'V166' NOT NULL,
    legacy_rule_id character varying(36) NOT NULL,
    legacy_rule_name character varying(255),
    outcome character varying(32) NOT NULL,
    reason text,
    migrated_rule_id character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL
);

--

-- ecos_dq_rule_v2 (TABLE)
CREATE TABLE ecos_data.ecos_dq_rule_v2 (
    id bigint NOT NULL,
    name character varying(256) NOT NULL,
    description text,
    rule_type character varying(64),
    config_json text,
    severity character varying(32),
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--

-- ecos_dq_rule_v2_id_seq (SEQUENCE)
CREATE SEQUENCE ecos_data.ecos_dq_rule_v2_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--

-- ecos_dq_rule_v2_id_seq (SEQUENCE OWNED BY)
ALTER SEQUENCE ecos_data.ecos_dq_rule_v2_id_seq OWNED BY ecos_data.ecos_dq_rule_v2.id;

--

-- ecos_dq_score_asset (TABLE)
-- ⚠ 历史遗留（2026-11-18 用户裁决「裸名 dq_score_asset 为权威」）：
--   V165.1 依 DR02 前缀规范建了本表（14 列），头部原计划是「消费侧 Mapper 切限定名」，
--   但那批业务改造从未执行 —— 生产代码 5 处真 SQL 全读裸名 ecos_data.dq_score_asset（6 列），
--   本表实测 0 行、零代码引用。保留建表只为「从零建库与当前活库一致」，不是权威表。
--   V165.1:9 明写「如需回滚禁 DROP（IR03）」⇒ 不清退，只停写。
CREATE TABLE ecos_data.ecos_dq_score_asset (
    id character varying(36) NOT NULL,
    asset_type character varying(20) NOT NULL,
    asset_id character varying(64) NOT NULL,
    overall_score numeric(5,2) NOT NULL,
    grade character varying(4) NOT NULL,
    dimensions_json text,
    snapshot_at timestamp without time zone NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    create_by character varying(100),
    update_by character varying(100)
);

--

-- ecos_pipeline_definition (TABLE)
CREATE TABLE ecos_data.ecos_pipeline_definition (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    definition jsonb DEFAULT '{}'::jsonb,
    status character varying(32) DEFAULT 'DRAFT',
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    definition_json text,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20)
);

--

-- TABLE ecos_pipeline_definition (COMMENT)
COMMENT ON TABLE ecos_data.ecos_pipeline_definition IS '管道定义表（JSON DAG 唯一承流模型，R-2 a）— 单源补写 V170';

--

-- ecos_pipeline_execution (TABLE)
CREATE TABLE ecos_data.ecos_pipeline_execution (
    id character varying(64) NOT NULL,
    pipeline_id character varying(64) NOT NULL,
    status character varying(32) DEFAULT 'PENDING',
    started_at timestamp without time zone,
    finished_at timestamp without time zone,
    error_message text,
    rows_processed bigint DEFAULT 0,
    tenant_id character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    create_time timestamp without time zone,
    update_time timestamp without time zone,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20)
);

--

-- TABLE ecos_pipeline_execution (COMMENT)
COMMENT ON TABLE ecos_data.ecos_pipeline_execution IS '管道执行表 — 单源补写 V170（pipeline_id 关联由应用层保证，ST09 不建跨表外键）';

--

-- ecos_pipeline_function (TABLE)
CREATE TABLE ecos_data.ecos_pipeline_function (
    id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    category character varying(50) NOT NULL,
    signature text,
    return_type character varying(50),
    description text,
    example text,
    is_builtin boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

--

-- ecos_pipeline_run (TABLE)
CREATE TABLE ecos_data.ecos_pipeline_run (
    id character varying(36) NOT NULL,
    task_id character varying(36) NOT NULL,
    status character varying(20) DEFAULT 'QUEUED',
    triggered_by character varying(50) DEFAULT 'manual',
    total_steps integer DEFAULT 0,
    completed_steps integer DEFAULT 0,
    started_at timestamp without time zone,
    finished_at timestamp without time zone,
    elapsed_ms integer DEFAULT 0,
    error_msg text,
    log_json jsonb DEFAULT '[]'::jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

--

-- ecos_pipeline_step (TABLE)
CREATE TABLE ecos_data.ecos_pipeline_step (
    id character varying(36) NOT NULL,
    task_id character varying(36) NOT NULL,
    step_order integer NOT NULL,
    node_id character varying(100) NOT NULL,
    node_type character varying(50) NOT NULL,
    config_json jsonb DEFAULT '{}'::jsonb,
    depends_on jsonb DEFAULT '[]'::jsonb,
    position_x double precision DEFAULT 0,
    position_y double precision DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

--

-- ecos_pipeline_step_run (TABLE)
CREATE TABLE ecos_data.ecos_pipeline_step_run (
    id character varying(36) NOT NULL,
    run_id character varying(36) NOT NULL,
    step_id character varying(36) NOT NULL,
    node_id character varying(100) NOT NULL,
    status character varying(20) DEFAULT 'QUEUED',
    rows_input integer DEFAULT 0,
    rows_output integer DEFAULT 0,
    started_at timestamp without time zone,
    finished_at timestamp without time zone,
    elapsed_ms integer DEFAULT 0,
    error_msg text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

--

-- ecos_pipeline_task (TABLE)
CREATE TABLE ecos_data.ecos_pipeline_task (
    id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    yaml_content text NOT NULL,
    git_url character varying(500),
    git_branch character varying(100) DEFAULT 'main',
    git_commit_id character varying(40),
    status character varying(20) DEFAULT 'DRAFT',
    cron_expression character varying(100),
    config_json jsonb DEFAULT '{}'::jsonb,
    enabled boolean DEFAULT true,
    task_type character varying(20) DEFAULT 'TRANSFORM',
    created_by character varying(100),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

--

-- COLUMN ecos_pipeline_task.task_type (COMMENT)
COMMENT ON COLUMN ecos_data.ecos_pipeline_task.task_type IS 'TRANSFORM / SYNC / LAKE_EXPORT';

--

-- ecos_pipeline_udf (TABLE)
CREATE TABLE ecos_data.ecos_pipeline_udf (
    id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    category character varying(50),
    language character varying(20) DEFAULT 'python',
    signature text,
    source_code text NOT NULL,
    compiled_path character varying(500),
    version integer DEFAULT 1,
    author character varying(100),
    is_shared boolean DEFAULT false,
    description text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

--

-- ecos_pipeline_yaml_migration_report (TABLE)
CREATE TABLE ecos_data.ecos_pipeline_yaml_migration_report (
    id character varying(36) NOT NULL,
    migration_batch character varying(64) DEFAULT 'V168' NOT NULL,
    task_id character varying(36) NOT NULL,
    task_name character varying(200),
    outcome character varying(32) NOT NULL,
    reason text,
    migrated_definition_id character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(100),
    update_by character varying(100),
    is_deleted smallint DEFAULT 0 NOT NULL,
    domain character varying(50) DEFAULT 'default' NOT NULL,
    version_no character varying(20) DEFAULT '1' NOT NULL
);

--

-- ecos_quality_evaluation (TABLE)
CREATE TABLE ecos_data.ecos_quality_evaluation (
    id character varying(36) NOT NULL,
    dataset_id character varying(100),
    rule_id character varying(64),
    passed boolean,
    total_rows bigint,
    failed_rows bigint,
    pass_rate double precision,
    sample_size integer,
    sample_failures jsonb,
    severity character varying(10),
    message text,
    evaluated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

--

-- ecos_quality_rule (TABLE)
CREATE TABLE ecos_data.ecos_quality_rule (
    rule_id character varying(64) NOT NULL,
    rule_name character varying(200) NOT NULL,
    rule_type character varying(30) NOT NULL,
    target character varying(200) NOT NULL,
    dataset_id character varying(100),
    parameters jsonb,
    severity character varying(10) DEFAULT 'WARN',
    enabled boolean DEFAULT true,
    description text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

--

-- ecos_query_history (TABLE)
CREATE TABLE ecos_data.ecos_query_history (
    id character varying(36) NOT NULL,
    sql_content text NOT NULL,
    datasource_id character varying(64),
    status character varying(20) DEFAULT 'RUNNING',
    rows_affected integer DEFAULT 0,
    error_msg text,
    execution_time_ms bigint DEFAULT 0,
    started_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    finished_at timestamp without time zone,
    created_by character varying(100)
);

--

-- TABLE ecos_query_history (COMMENT)
COMMENT ON TABLE ecos_data.ecos_query_history IS 'SQL query execution history';

--

-- COLUMN ecos_query_history.status (COMMENT)
COMMENT ON COLUMN ecos_data.ecos_query_history.status IS 'RUNNING / COMPLETED / FAILED / CANCELLED';

--

-- ecos_query_template (TABLE)
CREATE TABLE ecos_data.ecos_query_template (
    id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    sql_content text NOT NULL,
    datasource_id character varying(64),
    is_shared boolean DEFAULT false,
    created_by character varying(100),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

--

-- TABLE ecos_query_template (COMMENT)
COMMENT ON TABLE ecos_data.ecos_query_template IS 'Saved SQL query templates';

--

-- td_data_category (TABLE)
CREATE TABLE ecos_data.td_data_category (
    category_id character varying(64) NOT NULL,
    category_name character varying(256) NOT NULL,
    parent_id character varying(64),
    path character varying(512),
    level integer DEFAULT 1,
    sort_order integer DEFAULT 0,
    description text,
    status character varying(16) DEFAULT 'ACTIVE',
    tenant_id character varying(64),
    create_by character varying(64),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_by character varying(64),
    update_time timestamp without time zone DEFAULT now() NOT NULL
);

--

-- TABLE td_data_category (COMMENT)
COMMENT ON TABLE ecos_data.td_data_category IS 'Data category tree for catalog classification';

--

-- COLUMN td_data_category.level (COMMENT)
COMMENT ON COLUMN ecos_data.td_data_category.level IS '分类层级，根=1，子=父+1；前端按 level=2 取业务分类。与 parent_id 必须自洽（D12）。';

--

-- COLUMN td_data_category.status (COMMENT)
COMMENT ON COLUMN ecos_data.td_data_category.status IS 'ACTIVE / INACTIVE';

--

-- dq_score_snapshot id (DEFAULT)
ALTER TABLE ONLY ecos_data.dq_score_snapshot ALTER COLUMN id SET DEFAULT nextval('ecos_data.dq_score_snapshot_id_seq'::regclass);

--

-- ecos_dq_rule_v2 id (DEFAULT)
ALTER TABLE ONLY ecos_data.ecos_dq_rule_v2 ALTER COLUMN id SET DEFAULT nextval('ecos_data.ecos_dq_rule_v2_id_seq'::regclass);

--

-- dq_alert_record dq_alert_record_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.dq_alert_record
    ADD CONSTRAINT dq_alert_record_pkey PRIMARY KEY (id);

--

-- dq_knowledge_entry dq_knowledge_entry_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.dq_knowledge_entry
    ADD CONSTRAINT dq_knowledge_entry_pkey PRIMARY KEY (id);

--

-- dq_report dq_report_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.dq_report
    ADD CONSTRAINT dq_report_pkey PRIMARY KEY (id);

--

-- dq_rule_check dq_rule_check_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.dq_rule_check
    ADD CONSTRAINT dq_rule_check_pkey PRIMARY KEY (id);

--

-- dq_rule dq_rule_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.dq_rule
    ADD CONSTRAINT dq_rule_pkey PRIMARY KEY (id);

--

-- dq_rule dq_rule_rule_code_key (CONSTRAINT)
ALTER TABLE ONLY ecos_data.dq_rule
    ADD CONSTRAINT dq_rule_rule_code_key UNIQUE (rule_code);

--

-- dq_rule_version dq_rule_version_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.dq_rule_version
    ADD CONSTRAINT dq_rule_version_pkey PRIMARY KEY (id);

--

-- dq_rule_version dq_rule_version_rule_id_version_number_key (CONSTRAINT)
ALTER TABLE ONLY ecos_data.dq_rule_version
    ADD CONSTRAINT dq_rule_version_rule_id_version_number_key UNIQUE (rule_id, version_number);

--

-- dq_schedule dq_schedule_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.dq_schedule
    ADD CONSTRAINT dq_schedule_pkey PRIMARY KEY (id);

--

-- dq_score_asset dq_score_asset_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.dq_score_asset
    ADD CONSTRAINT dq_score_asset_pkey PRIMARY KEY (asset_type, asset_id);

--

-- dq_score_snapshot dq_score_snapshot_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.dq_score_snapshot
    ADD CONSTRAINT dq_score_snapshot_pkey PRIMARY KEY (id);

--

-- dq_throttle dq_throttle_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.dq_throttle
    ADD CONSTRAINT dq_throttle_pkey PRIMARY KEY (id);

--

-- dq_throttle dq_throttle_scope_type_scope_id_key (CONSTRAINT)
ALTER TABLE ONLY ecos_data.dq_throttle
    ADD CONSTRAINT dq_throttle_scope_type_scope_id_key UNIQUE (scope_type, scope_id);

--

-- dq_work_order dq_work_order_order_no_key (CONSTRAINT)
ALTER TABLE ONLY ecos_data.dq_work_order
    ADD CONSTRAINT dq_work_order_order_no_key UNIQUE (order_no);

--

-- dq_work_order dq_work_order_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.dq_work_order
    ADD CONSTRAINT dq_work_order_pkey PRIMARY KEY (id);

--

-- ecos_data_asset_field ecos_data_asset_field_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_data_asset_field
    ADD CONSTRAINT ecos_data_asset_field_pkey PRIMARY KEY (field_asset_id);

--

-- ecos_data_asset ecos_data_asset_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_data_asset
    ADD CONSTRAINT ecos_data_asset_pkey PRIMARY KEY (asset_id);

--

-- ecos_data_category_tree ecos_data_category_tree_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_data_category_tree
    ADD CONSTRAINT ecos_data_category_tree_pkey PRIMARY KEY (category_id);

--

-- ecos_data_governance_candidate ecos_data_governance_candidate_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_data_governance_candidate
    ADD CONSTRAINT ecos_data_governance_candidate_pkey PRIMARY KEY (id);

--

-- ecos_data_level_def ecos_data_level_def_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_data_level_def
    ADD CONSTRAINT ecos_data_level_def_pkey PRIMARY KEY (level_code);

--

-- ecos_data_lineage_edge ecos_data_lineage_edge_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_data_lineage_edge
    ADD CONSTRAINT ecos_data_lineage_edge_pkey PRIMARY KEY (id);

--

-- ecos_data_lineage_node ecos_data_lineage_node_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_data_lineage_node
    ADD CONSTRAINT ecos_data_lineage_node_pkey PRIMARY KEY (id);

--

-- ecos_dq_legacy_migration_report ecos_dq_legacy_migration_report_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_dq_legacy_migration_report
    ADD CONSTRAINT ecos_dq_legacy_migration_report_pkey PRIMARY KEY (id);

--

-- ecos_dq_rule_v2 ecos_dq_rule_v2_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_dq_rule_v2
    ADD CONSTRAINT ecos_dq_rule_v2_pkey PRIMARY KEY (id);

--

-- ecos_dq_score_asset ecos_dq_score_asset_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_dq_score_asset
    ADD CONSTRAINT ecos_dq_score_asset_pkey PRIMARY KEY (id);

--

-- ecos_pipeline_definition ecos_pipeline_definition_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_pipeline_definition
    ADD CONSTRAINT ecos_pipeline_definition_pkey PRIMARY KEY (id);

--

-- ecos_pipeline_execution ecos_pipeline_execution_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_pipeline_execution
    ADD CONSTRAINT ecos_pipeline_execution_pkey PRIMARY KEY (id);

--

-- ecos_pipeline_function ecos_pipeline_function_name_key (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_pipeline_function
    ADD CONSTRAINT ecos_pipeline_function_name_key UNIQUE (name);

--

-- ecos_pipeline_function ecos_pipeline_function_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_pipeline_function
    ADD CONSTRAINT ecos_pipeline_function_pkey PRIMARY KEY (id);

--

-- ecos_pipeline_run ecos_pipeline_run_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_pipeline_run
    ADD CONSTRAINT ecos_pipeline_run_pkey PRIMARY KEY (id);

--

-- ecos_pipeline_step ecos_pipeline_step_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_pipeline_step
    ADD CONSTRAINT ecos_pipeline_step_pkey PRIMARY KEY (id);

--

-- ecos_pipeline_step_run ecos_pipeline_step_run_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_pipeline_step_run
    ADD CONSTRAINT ecos_pipeline_step_run_pkey PRIMARY KEY (id);

--

-- ecos_pipeline_task ecos_pipeline_task_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_pipeline_task
    ADD CONSTRAINT ecos_pipeline_task_pkey PRIMARY KEY (id);

--

-- ecos_pipeline_udf ecos_pipeline_udf_name_key (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_pipeline_udf
    ADD CONSTRAINT ecos_pipeline_udf_name_key UNIQUE (name);

--

-- ecos_pipeline_udf ecos_pipeline_udf_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_pipeline_udf
    ADD CONSTRAINT ecos_pipeline_udf_pkey PRIMARY KEY (id);

--

-- ecos_pipeline_yaml_migration_report ecos_pipeline_yaml_migration_report_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_pipeline_yaml_migration_report
    ADD CONSTRAINT ecos_pipeline_yaml_migration_report_pkey PRIMARY KEY (id);

--

-- ecos_quality_evaluation ecos_quality_evaluation_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_quality_evaluation
    ADD CONSTRAINT ecos_quality_evaluation_pkey PRIMARY KEY (id);

--

-- ecos_quality_rule ecos_quality_rule_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_quality_rule
    ADD CONSTRAINT ecos_quality_rule_pkey PRIMARY KEY (rule_id);

--

-- ecos_query_history ecos_query_history_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_query_history
    ADD CONSTRAINT ecos_query_history_pkey PRIMARY KEY (id);

--

-- ecos_query_template ecos_query_template_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_query_template
    ADD CONSTRAINT ecos_query_template_pkey PRIMARY KEY (id);

--

-- ecos_data_period_status pk_ecos_data_period_status (CONSTRAINT)
ALTER TABLE ONLY ecos_data.ecos_data_period_status
    ADD CONSTRAINT pk_ecos_data_period_status PRIMARY KEY (id);

--

-- td_data_category td_data_category_pkey (CONSTRAINT)
ALTER TABLE ONLY ecos_data.td_data_category
    ADD CONSTRAINT td_data_category_pkey PRIMARY KEY (category_id);

--

-- idx_asset_category (INDEX)
CREATE INDEX idx_asset_category ON ecos_data.ecos_data_asset USING btree (category_id);

--

-- idx_asset_field_asset (INDEX)
CREATE INDEX idx_asset_field_asset ON ecos_data.ecos_data_asset_field USING btree (asset_id);

--

-- idx_asset_field_conf (INDEX)
CREATE INDEX idx_asset_field_conf ON ecos_data.ecos_data_asset_field USING btree (confirmed);

--

-- idx_asset_field_sens (INDEX)
CREATE INDEX idx_asset_field_sens ON ecos_data.ecos_data_asset_field USING btree (field_sensitivity);

--

-- idx_asset_owner (INDEX)
CREATE INDEX idx_asset_owner ON ecos_data.ecos_data_asset USING btree (owner);

--

-- idx_asset_sensitivity (INDEX)
CREATE INDEX idx_asset_sensitivity ON ecos_data.ecos_data_asset USING btree (sensitivity_level);

--

-- idx_category_parent (INDEX)
CREATE INDEX idx_category_parent ON ecos_data.td_data_category USING btree (parent_id);

--

-- idx_category_tree_level (INDEX)
CREATE INDEX idx_category_tree_level ON ecos_data.ecos_data_category_tree USING btree (level);

--

-- idx_category_tree_parent (INDEX)
CREATE INDEX idx_category_tree_parent ON ecos_data.ecos_data_category_tree USING btree (parent_id);

--

-- idx_data_cat_parent (INDEX)
CREATE INDEX idx_data_cat_parent ON ecos_data.td_data_category USING btree (parent_id);

--

-- idx_data_level_code (INDEX)
CREATE INDEX idx_data_level_code ON ecos_data.ecos_data_level_def USING btree (level_code);

--

-- idx_data_pdef_status (INDEX)
CREATE INDEX idx_data_pdef_status ON ecos_data.ecos_pipeline_definition USING btree (status);

--

-- idx_data_pdef_tenant (INDEX)
CREATE INDEX idx_data_pdef_tenant ON ecos_data.ecos_pipeline_definition USING btree (tenant_id);

--

-- idx_data_pexec_pipeline (INDEX)
CREATE INDEX idx_data_pexec_pipeline ON ecos_data.ecos_pipeline_execution USING btree (pipeline_id);

--

-- idx_data_pexec_status (INDEX)
CREATE INDEX idx_data_pexec_status ON ecos_data.ecos_pipeline_execution USING btree (status);

--

-- idx_data_pexec_tenant (INDEX)
CREATE INDEX idx_data_pexec_tenant ON ecos_data.ecos_pipeline_execution USING btree (tenant_id);

--

-- idx_dq_alert_level (INDEX)
CREATE INDEX idx_dq_alert_level ON ecos_data.dq_alert_record USING btree (alert_level);

--

-- idx_dq_alert_rule (INDEX)
CREATE INDEX idx_dq_alert_rule ON ecos_data.dq_alert_record USING btree (rule_id, created_at DESC);

--

-- idx_dq_alert_status (INDEX)
CREATE INDEX idx_dq_alert_status ON ecos_data.dq_alert_record USING btree (status) WHERE ((status)::text = ANY ((ARRAY['PENDING', 'NOTIFIED'])::text[]));

--

-- idx_dq_alert_time (INDEX)
CREATE INDEX idx_dq_alert_time ON ecos_data.dq_alert_record USING btree (created_at DESC);

--

-- idx_dq_check_rule_time (INDEX)
CREATE INDEX idx_dq_check_rule_time ON ecos_data.dq_rule_check USING btree (rule_id, executed_at DESC);

--

-- idx_dq_check_time (INDEX)
CREATE INDEX idx_dq_check_time ON ecos_data.dq_rule_check USING btree (executed_at DESC);

--

-- idx_dq_knowledge_category (INDEX)
CREATE INDEX idx_dq_knowledge_category ON ecos_data.dq_knowledge_entry USING btree (category);

--

-- idx_dq_knowledge_type_source (INDEX)
CREATE INDEX idx_dq_knowledge_type_source ON ecos_data.dq_knowledge_entry USING btree (source_type, source_id);

--

-- idx_dq_mig_report_outcome (INDEX)
CREATE INDEX idx_dq_mig_report_outcome ON ecos_data.ecos_dq_legacy_migration_report USING btree (outcome);

--

-- idx_dq_report_scope (INDEX)
CREATE INDEX idx_dq_report_scope ON ecos_data.dq_report USING btree (scope, end_date DESC);

--

-- idx_dq_report_type_date (INDEX)
CREATE INDEX idx_dq_report_type_date ON ecos_data.dq_report USING btree (report_type, end_date DESC);

--

-- idx_dq_rule_category (INDEX)
CREATE INDEX idx_dq_rule_category ON ecos_data.dq_rule USING btree (category) WHERE (is_deleted = false);

--

-- idx_dq_rule_domain (INDEX)
CREATE INDEX idx_dq_rule_domain ON ecos_data.dq_rule USING btree (domain) WHERE (is_deleted = false);

--

-- idx_dq_rule_legacy (INDEX)
CREATE INDEX idx_dq_rule_legacy ON ecos_data.dq_rule USING btree (legacy_rule_id);

--

-- idx_dq_rule_rule_type (INDEX)
CREATE INDEX idx_dq_rule_rule_type ON ecos_data.dq_rule USING btree (rule_type) WHERE (is_deleted = false);

--

-- idx_dq_rule_status (INDEX)
CREATE INDEX idx_dq_rule_status ON ecos_data.dq_rule USING btree (status) WHERE (is_deleted = false);

--

-- idx_dq_rule_status_active (INDEX)
CREATE INDEX idx_dq_rule_status_active ON ecos_data.dq_rule USING btree (status, is_active);

--

-- idx_dq_rule_target (INDEX)
CREATE INDEX idx_dq_rule_target ON ecos_data.dq_rule USING btree (target_kind, target_id) WHERE (is_deleted = false);

--

-- idx_dq_rule_version_rule (INDEX)
CREATE INDEX idx_dq_rule_version_rule ON ecos_data.dq_rule_version USING btree (rule_id, version_number DESC);

--

-- idx_dq_schedule_enable (INDEX)
CREATE INDEX idx_dq_schedule_enable ON ecos_data.dq_schedule USING btree (trigger_type) WHERE (enabled AND (is_deleted = false));

--

-- idx_dq_score_asset_type (INDEX)
CREATE INDEX idx_dq_score_asset_type ON ecos_data.dq_score_asset USING btree (asset_type, asset_id);

--

-- idx_dq_score_scope_time (INDEX)
CREATE INDEX idx_dq_score_scope_time ON ecos_data.dq_score_snapshot USING btree (scope_type, scope_id, evaluated_at DESC);

--

-- idx_ecos_dq_score_asset_asset (INDEX)
CREATE INDEX idx_ecos_dq_score_asset_asset ON ecos_data.ecos_dq_score_asset USING btree (asset_type, asset_id);

--

-- idx_ecos_dq_score_asset_grade (INDEX)
CREATE INDEX idx_ecos_dq_score_asset_grade ON ecos_data.ecos_dq_score_asset USING btree (grade, snapshot_at);

--

-- idx_ecos_dq_score_asset_time (INDEX)
CREATE INDEX idx_ecos_dq_score_asset_time ON ecos_data.ecos_dq_score_asset USING btree (snapshot_at);

--

-- idx_ecos_pipeline_task_updated_at (INDEX)
CREATE INDEX idx_ecos_pipeline_task_updated_at ON ecos_data.ecos_pipeline_task USING btree (updated_at DESC);

--

-- idx_edps_status (INDEX)
CREATE INDEX idx_edps_status ON ecos_data.ecos_data_period_status USING btree (status, is_deleted);

--

-- idx_lineage_edge_src (INDEX)
CREATE INDEX idx_lineage_edge_src ON ecos_data.ecos_data_lineage_edge USING btree (source_node_id);

--

-- idx_lineage_edge_task (INDEX)
CREATE INDEX idx_lineage_edge_task ON ecos_data.ecos_data_lineage_edge USING btree (pipeline_task_id);

--

-- idx_lineage_edge_tgt (INDEX)
CREATE INDEX idx_lineage_edge_tgt ON ecos_data.ecos_data_lineage_edge USING btree (target_node_id);

--

-- idx_lineage_node_ds (INDEX)
CREATE INDEX idx_lineage_node_ds ON ecos_data.ecos_data_lineage_node USING btree (datasource_id);

--

-- idx_lineage_node_task (INDEX)
CREATE INDEX idx_lineage_node_task ON ecos_data.ecos_data_lineage_node USING btree (pipeline_task_id) WHERE (pipeline_task_id IS NOT NULL);

--

-- idx_lineage_node_type (INDEX)
CREATE INDEX idx_lineage_node_type ON ecos_data.ecos_data_lineage_node USING btree (node_type);

--

-- idx_pipeline_tenant (INDEX)
CREATE INDEX idx_pipeline_tenant ON ecos_data.ecos_pipeline_definition USING btree (tenant_id);

--

-- idx_query_history_ds (INDEX)
CREATE INDEX idx_query_history_ds ON ecos_data.ecos_query_history USING btree (datasource_id);

--

-- idx_query_history_started (INDEX)
CREATE INDEX idx_query_history_started ON ecos_data.ecos_query_history USING btree (started_at DESC);

--

-- idx_v272_cand_target (INDEX)
CREATE INDEX idx_v272_cand_target ON ecos_data.ecos_data_governance_candidate USING btree (target_ref, is_deleted);

--

-- idx_v272_cand_tenant (INDEX)
CREATE INDEX idx_v272_cand_tenant ON ecos_data.ecos_data_governance_candidate USING btree (tenant_id, status, is_deleted);

--

-- idx_wo_alert (INDEX)
CREATE INDEX idx_wo_alert ON ecos_data.dq_work_order USING btree (alert_id);

--

-- idx_wo_asset (INDEX)
CREATE INDEX idx_wo_asset ON ecos_data.dq_work_order USING btree (asset_id) WHERE (is_deleted = false);

--

-- idx_wo_severity (INDEX)
CREATE INDEX idx_wo_severity ON ecos_data.dq_work_order USING btree (severity, status) WHERE (is_deleted = false);

--

-- idx_wo_status (INDEX)
CREATE INDEX idx_wo_status ON ecos_data.dq_work_order USING btree (status) WHERE (is_deleted = false);

--

-- uk_v272_cand_idem (INDEX)
CREATE UNIQUE INDEX uk_v272_cand_idem ON ecos_data.ecos_data_governance_candidate USING btree (tenant_id, idempotency_key, is_deleted);

--

-- uniq_asset_resource_domain (INDEX)
CREATE UNIQUE INDEX uniq_asset_resource_domain ON ecos_data.ecos_data_asset USING btree (resource_id, domain);

--

-- uniq_dq_mig_report (INDEX)
CREATE UNIQUE INDEX uniq_dq_mig_report ON ecos_data.ecos_dq_legacy_migration_report USING btree (migration_batch, legacy_rule_id);

--

-- uniq_edps_period (INDEX)
CREATE UNIQUE INDEX uniq_edps_period ON ecos_data.ecos_data_period_status USING btree (period, is_deleted);

--

-- uniq_ypm_report_task (INDEX)
CREATE UNIQUE INDEX uniq_ypm_report_task ON ecos_data.ecos_pipeline_yaml_migration_report USING btree (migration_batch, task_id);

--

