-- V162 (PMO-74.8 H8-T1, L1 + lint IR04 扩项扫出的其余运行时隐式建表点):
-- 运行时代码内嵌 DDL 全部收编迁移单源。
-- 涉及代码点（本批同步删除其 jdbc.execute("CREATE/ALTER ...") 隐式建表调用）：
--   data-engine : DataLineageService.tryCreateLineageTablesSafely（表已由 V58/V155 单源承载，本脚本补审计列）
--                 SchemaChangeDetector.ensureTables / PipelineFunctionServiceImpl.ensureSchema
--                 PipelineTaskServiceImpl.ensureSchema（含 2 条 ADD COLUMN IF NOT EXISTS）
--                 QualityServiceImpl.ensureSchema / StubCategoryService.ensureSchema / UdfServiceImpl.ensureSchema
--   ai-engine   : DataInitializer.ensureTables（CronJob/CronJobExecution/SkillRepository.ensureTable）
--                 AgentMetricsCollector.ensureTables
--   sysman      : SysConfigService.ensureSchema（sys_config 3 列 ALTER + pipeline 两表）
--                 DictTableService.ensureSchema / DbBlacklistStore.init
-- 规范: DR06 审计 5 字段 + DR07 version_no + DR08 domain；IR03 只加不删；全部幂等。
-- 说明: 这批表历史落 public（主控制 schema），本次仅收编 DDL 单源不改落点（迁引擎
--       schema 属 H8 后续子指令）；存量表旧列 created_at/updated_at 保留（R9），
--       审计 5 字段以 ADD COLUMN 补齐，应用侧新写路径按 DR06 列名使用。

-- ── 0. Schema guard ──────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS ecos_data;

-- ── 1. 血缘表补审计列（表体 V58 + V155 已单源，此处只加列）────────
ALTER TABLE ecos_data.ecos_data_lineage_node ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE ecos_data.ecos_data_lineage_node ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE ecos_data.ecos_data_lineage_node ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100);
ALTER TABLE ecos_data.ecos_data_lineage_node ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100);
ALTER TABLE ecos_data.ecos_data_lineage_node ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE ecos_data.ecos_data_lineage_node ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';
ALTER TABLE ecos_data.ecos_data_lineage_node ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1';

ALTER TABLE ecos_data.ecos_data_lineage_edge ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE ecos_data.ecos_data_lineage_edge ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE ecos_data.ecos_data_lineage_edge ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100);
ALTER TABLE ecos_data.ecos_data_lineage_edge ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100);
ALTER TABLE ecos_data.ecos_data_lineage_edge ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE ecos_data.ecos_data_lineage_edge ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';
ALTER TABLE ecos_data.ecos_data_lineage_edge ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1';

-- ── 2. ai-engine：cron job / skill / agent metrics 收编 ──────────
CREATE TABLE IF NOT EXISTS public.ecos_cron_job (
    id              BIGSERIAL    PRIMARY KEY,         -- knownLegacy 自增技术键（原运行时自建形态收编，R9 不改）
    name            VARCHAR(255) NOT NULL,
    cron_expression VARCHAR(100),
    description     TEXT,
    enabled         BOOLEAN      DEFAULT TRUE,
    last_run_at     TIMESTAMP,
    next_run_at     TIMESTAMP,
    status          VARCHAR(50)  DEFAULT 'IDLE',
    created_by      VARCHAR(100),
    created_at      TIMESTAMP    DEFAULT NOW(),
    updated_at      TIMESTAMP    DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS public.ecos_cron_job_execution (
    id            BIGSERIAL   PRIMARY KEY,
    cron_job_id   BIGINT      NOT NULL,
    started_at    TIMESTAMP,
    finished_at   TIMESTAMP,
    status        VARCHAR(50) DEFAULT 'RUNNING',
    result        TEXT,
    error_message TEXT,
    created_at    TIMESTAMP   DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS public.ecos_skill (
    id           BIGSERIAL    PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    description  TEXT,
    version      VARCHAR(50)  DEFAULT '1.0.0',
    enabled      BOOLEAN      DEFAULT TRUE,
    category     VARCHAR(100),
    package_info TEXT,
    created_by   VARCHAR(100),
    created_at   TIMESTAMP    DEFAULT NOW(),
    updated_at   TIMESTAMP    DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS public.ecos_agent_metrics (
    id         BIGSERIAL PRIMARY KEY,
    agent_id   VARCHAR(64),
    action     VARCHAR(32),
    success    BOOLEAN,
    elapsed_ms BIGINT,
    tokens_in  INT DEFAULT 0,
    tokens_out INT DEFAULT 0,
    trace_id   VARCHAR(16),
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS public.ecos_agent_alert (
    id         BIGSERIAL PRIMARY KEY,
    trace_id   VARCHAR(16),
    agent_id   VARCHAR(64),
    alert_type VARCHAR(32),
    message    TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_agent_metrics_agent ON public.ecos_agent_metrics(agent_id, created_at DESC);

-- ── 3. data-engine：schema 快照 / 管道函数 / UDF / 管道任务四表 / 质量两表 ──
CREATE TABLE IF NOT EXISTS public.schema_snapshots (
    id            BIGSERIAL PRIMARY KEY,
    datasource_id VARCHAR(128) NOT NULL,
    table_name    VARCHAR(256) NOT NULL,
    column_hash   VARCHAR(64)  NOT NULL,
    col_sig       TEXT,
    snapshot_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS public.schema_changes (
    id           BIGSERIAL PRIMARY KEY,
    datasource_id VARCHAR(128) NOT NULL,
    table_name   VARCHAR(256) NOT NULL,
    change_type  VARCHAR(32)  NOT NULL,
    detail_json  TEXT,
    detected_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    acknowledged BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_schema_changes_ack ON public.schema_changes (acknowledged, detected_at);

CREATE TABLE IF NOT EXISTS public.ecos_pipeline_function (
    id          VARCHAR(36) PRIMARY KEY,             -- MC01: 应用侧 UUID，DDL 无默认值
    name        VARCHAR(100) NOT NULL UNIQUE,
    category    VARCHAR(50)  NOT NULL,
    signature   TEXT,
    return_type VARCHAR(50),
    description TEXT,
    example     TEXT,
    is_builtin  BOOLEAN DEFAULT true,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.ecos_pipeline_udf (
    id            VARCHAR(36) PRIMARY KEY,
    name          VARCHAR(200) NOT NULL UNIQUE,
    category      VARCHAR(50),
    language      VARCHAR(20) DEFAULT 'python',
    signature     TEXT,
    source_code   TEXT NOT NULL,
    compiled_path VARCHAR(500),
    version       INTEGER DEFAULT 1,
    author        VARCHAR(100),
    is_shared     BOOLEAN DEFAULT false,
    description   TEXT,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.ecos_pipeline_task (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    yaml_content    TEXT NOT NULL,
    git_url         VARCHAR(500),
    git_branch      VARCHAR(100) DEFAULT 'main',
    git_commit_id   VARCHAR(40),
    status          VARCHAR(20) DEFAULT 'DRAFT',
    cron_expression VARCHAR(100),
    config_json     JSONB DEFAULT '{}',
    enabled         BOOLEAN DEFAULT true,
    task_type       VARCHAR(20) DEFAULT 'TRANSFORM',
    created_by      VARCHAR(100),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.ecos_pipeline_step (
    id          VARCHAR(36) PRIMARY KEY,
    task_id     VARCHAR(36) NOT NULL,
    step_order  INTEGER NOT NULL,
    node_id     VARCHAR(100) NOT NULL,
    node_type   VARCHAR(50) NOT NULL,
    config_json JSONB DEFAULT '{}',
    depends_on  JSONB DEFAULT '[]',
    position_x  FLOAT DEFAULT 0,
    position_y  FLOAT DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.ecos_pipeline_run (
    id              VARCHAR(36) PRIMARY KEY,
    task_id         VARCHAR(36) NOT NULL,
    status          VARCHAR(20) DEFAULT 'QUEUED',
    triggered_by    VARCHAR(50) DEFAULT 'manual',
    total_steps     INTEGER DEFAULT 0,
    completed_steps INTEGER DEFAULT 0,
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    elapsed_ms      INTEGER DEFAULT 0,
    error_msg       TEXT,
    log_json        JSONB DEFAULT '[]',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.ecos_pipeline_step_run (
    id          VARCHAR(36) PRIMARY KEY,
    run_id      VARCHAR(36) NOT NULL,
    step_id     VARCHAR(36) NOT NULL,
    node_id     VARCHAR(100) NOT NULL,
    status      VARCHAR(20) DEFAULT 'QUEUED',
    rows_input  INTEGER DEFAULT 0,
    rows_output INTEGER DEFAULT 0,
    started_at  TIMESTAMP,
    finished_at TIMESTAMP,
    elapsed_ms  INTEGER DEFAULT 0,
    error_msg   TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.ecos_quality_rule (
    rule_id    VARCHAR(64) PRIMARY KEY,
    rule_name  VARCHAR(200) NOT NULL,
    rule_type  VARCHAR(30)  NOT NULL,
    target     VARCHAR(200) NOT NULL,
    dataset_id VARCHAR(100),
    parameters JSONB,
    severity   VARCHAR(10) DEFAULT 'WARN',
    enabled    BOOLEAN DEFAULT true,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.ecos_quality_evaluation (
    id            VARCHAR(36) PRIMARY KEY,
    dataset_id    VARCHAR(100),
    rule_id       VARCHAR(64),
    passed        BOOLEAN,
    total_rows    BIGINT,
    failed_rows   BIGINT,
    pass_rate     DOUBLE PRECISION,
    sample_size   INTEGER,
    sample_failures JSONB,
    severity      VARCHAR(10),
    message       TEXT,
    evaluated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ── 4. sysman：dict_table/dict_column / sys_token_blacklist 收编 ──
CREATE TABLE IF NOT EXISTS public.dict_table (
    id           VARCHAR(36) PRIMARY KEY,
    code         VARCHAR(200),
    name         VARCHAR(200) NOT NULL,
    name_zh      VARCHAR(200),
    schema_name  VARCHAR(200),
    description  TEXT,
    status       VARCHAR(32) DEFAULT 'DRAFT',
    source       VARCHAR(100),
    row_count    BIGINT,
    storage_size VARCHAR(50),
    owner        VARCHAR(100),
    tags         TEXT,
    created_by   VARCHAR(100),
    created_at   TIMESTAMP DEFAULT NOW(),
    updated_at   TIMESTAMP DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS public.dict_column (
    id            VARCHAR(36) PRIMARY KEY,
    table_id      VARCHAR(36) NOT NULL REFERENCES public.dict_table(id) ON DELETE CASCADE,
    name          VARCHAR(200) NOT NULL,
    type          VARCHAR(100) NOT NULL,
    length        INT,
    precision_val INT,
    scale         INT,
    nullable      BOOLEAN DEFAULT true,
    primary_key   BOOLEAN DEFAULT false,
    default_value VARCHAR(500),
    description   TEXT,
    sort_order    INT DEFAULT 0,
    created_at    TIMESTAMP DEFAULT NOW(),
    updated_at    TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_dict_column_table_id ON public.dict_column(table_id);
CREATE INDEX IF NOT EXISTS idx_dict_table_status    ON public.dict_table(status);

CREATE TABLE IF NOT EXISTS public.sys_token_blacklist (
    token     TEXT PRIMARY KEY,                        -- knownLegacy: token 摘要即自然键
    expire_at BIGINT NOT NULL
);

-- ── 5. sys_config 扩展列收编（原 SysConfigService.ensureSchema 运行时 ALTER）──
ALTER TABLE public.sys_config ADD COLUMN IF NOT EXISTS config_group  VARCHAR(50) DEFAULT 'general';
ALTER TABLE public.sys_config ADD COLUMN IF NOT EXISTS description   TEXT;
ALTER TABLE public.sys_config ADD COLUMN IF NOT EXISTS config_type   VARCHAR(20) DEFAULT 'string';
-- config_label / sort_order / edition / status（CognitiveConfigController 原直写列，
-- 收编为迁移单源；读写改走 sysman api 门面 = H8-T2）
ALTER TABLE public.sys_config ADD COLUMN IF NOT EXISTS config_label  VARCHAR(200);
ALTER TABLE public.sys_config ADD COLUMN IF NOT EXISTS sort_order    INTEGER DEFAULT 100;
ALTER TABLE public.sys_config ADD COLUMN IF NOT EXISTS edition       VARCHAR(50) DEFAULT 'all';
ALTER TABLE public.sys_config ADD COLUMN IF NOT EXISTS status        VARCHAR(20) DEFAULT 'active';

-- ── 6. td_data_category 审计补齐（表体 V58 已单源；原 StubCategoryService 运行时建表删除）──
ALTER TABLE ecos_data.td_data_category ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE ecos_data.td_data_category ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE ecos_data.td_data_category ADD COLUMN IF NOT EXISTS domain     VARCHAR(50) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_category_parent ON ecos_data.td_data_category(parent_id);

-- ── 7. 本批新收编表统一补审计 5 字段 + domain + version_no（DR06/07/08）──
-- （对上面所有 public.ecos_*/dict_*/schema_* 循环加列；存量已有同名列因 IF NOT EXISTS 跳过）
ALTER TABLE public.ecos_cron_job             ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_by VARCHAR(100), ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default', ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE public.ecos_cron_job_execution   ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS create_by VARCHAR(100), ADD COLUMN IF NOT EXISTS update_by VARCHAR(100), ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default', ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE public.ecos_skill                ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_by VARCHAR(100), ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default', ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE public.ecos_agent_metrics        ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS create_by VARCHAR(100), ADD COLUMN IF NOT EXISTS update_by VARCHAR(100), ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default', ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE public.ecos_agent_alert          ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS create_by VARCHAR(100), ADD COLUMN IF NOT EXISTS update_by VARCHAR(100), ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default', ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE public.schema_snapshots          ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS create_by VARCHAR(100), ADD COLUMN IF NOT EXISTS update_by VARCHAR(100), ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default', ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE public.schema_changes            ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS create_by VARCHAR(100), ADD COLUMN IF NOT EXISTS update_by VARCHAR(100), ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default', ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE public.ecos_pipeline_function    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS create_by VARCHAR(100), ADD COLUMN IF NOT EXISTS update_by VARCHAR(100), ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default', ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE public.ecos_pipeline_udf         ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS create_by VARCHAR(100), ADD COLUMN IF NOT EXISTS update_by VARCHAR(100), ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default', ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE public.ecos_pipeline_task        ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_by VARCHAR(100), ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default', ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE public.ecos_pipeline_step        ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS create_by VARCHAR(100), ADD COLUMN IF NOT EXISTS update_by VARCHAR(100), ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default', ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE public.ecos_pipeline_run         ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS create_by VARCHAR(100), ADD COLUMN IF NOT EXISTS update_by VARCHAR(100), ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default', ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE public.ecos_pipeline_step_run    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS create_by VARCHAR(100), ADD COLUMN IF NOT EXISTS update_by VARCHAR(100), ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default', ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE public.ecos_quality_rule         ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS create_by VARCHAR(100), ADD COLUMN IF NOT EXISTS update_by VARCHAR(100), ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default', ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE public.ecos_quality_evaluation   ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS create_by VARCHAR(100), ADD COLUMN IF NOT EXISTS update_by VARCHAR(100), ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default', ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE public.dict_table                ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_by VARCHAR(100), ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default', ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE public.dict_column               ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT NOW(), ADD COLUMN IF NOT EXISTS create_by VARCHAR(100), ADD COLUMN IF NOT EXISTS update_by VARCHAR(100), ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS domain VARCHAR(50) NOT NULL DEFAULT 'default', ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';

-- ── 8. sys_dict / extraction_drafts 运行时 ALTER 收编（原 DictService.ensureSchema、
--       KnowledgeExtractionService.ensureColumns 删除；现网两列已存在，幂等补齐）──
ALTER TABLE public.sys_dict ADD COLUMN IF NOT EXISTS subsystem VARCHAR(20);
ALTER TABLE public.extraction_drafts ADD COLUMN IF NOT EXISTS extracted_links_json TEXT;
ALTER TABLE public.extraction_drafts ADD COLUMN IF NOT EXISTS rejected_reason TEXT;

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR06/07/08] 全部收编表补审计 5 字段 + version_no + domain ✓（存量旧列 created_at/updated_at 按 R9 保留双轨）
-- [DR01/03] 表名收编不改名（R9）；dict_table/dict_column 无 ecos_ 前缀 = knownLegacy 白名单（DR02 存量豁免）
-- [IR03] 零 DROP / 零改列 ✓
-- [ST07] public = 主控制 schema（现网代码非限定名解析落点）；schema 归位改造 = H8 后续子指令
-- [MC01] 新建业务表主键 VARCHAR(36) 应用侧 UUID；BIGSERIAL 存量技术键标 knownLegacy
