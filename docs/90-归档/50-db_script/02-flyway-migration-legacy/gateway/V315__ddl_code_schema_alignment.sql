-- V315 (DoD DD-4 / #147Z-6): DDL 单源 ↔ 代码读端 schema 对齐（补建 + 孤儿副本清退）
--
-- 背景：按「代码 main 面 <schema>.<table> 字面量 × DDL 单源 CREATE TABLE」全量对扫，实测 5 张表的
--       代码读端 schema 在单源里从未被建出（DdlCodeSchemaAlignmentGateTest 钉住）。dev 库因历史手工
--       建表而"看起来能用"，从零建库按单源跑完则读端必断。
-- 授权：2026-11-06 用户明确授权本条 DELETE 类动作（DROP 撞 R9「只加不删」，属一次性裁决，不构成先例）；
--       执行前已对 public. 侧三张表做 pg_dump 备份，备份路径见本次执行记录。
-- 纪律：DR01~DR08 / MC01~MC03 / ST07 / IR02(手动 psql) / IR03(本批经授权例外，仅限下列四张孤儿副本)
-- 号段：接 V314；本脚本落地后下一号 V316（须同批改 MigrationVersionUniquenessTest 钉值）

-- ── 1. 控制域：审计兜底重试（代码读 ecos_control.，见 runtime-task AuditRetryDao）──
CREATE TABLE IF NOT EXISTS ecos_control.ecos_runtime_audit_retry (
    id            VARCHAR(36) PRIMARY KEY,
    event_type    VARCHAR(64)  NOT NULL,
    payload       TEXT         NOT NULL,
    attempts      SMALLINT     NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP    NOT NULL,
    last_error    VARCHAR(1000),
    status        VARCHAR(16)  NOT NULL DEFAULT 'pending',
    trace_id      VARCHAR(64),
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    version_no    VARCHAR(20)  NOT NULL,
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    domain        VARCHAR(50)  NOT NULL DEFAULT 'default',
    tenant_id     VARCHAR(36),
    create_by     VARCHAR(100),
    update_by     VARCHAR(100)
);
-- 幂等补列：V315 首跑时两表按 legacy 形态建出（无 create_by/update_by，撞 DR06），此处补齐
ALTER TABLE ecos_control.ecos_runtime_audit_retry ADD COLUMN IF NOT EXISTS create_by VARCHAR(100);
ALTER TABLE ecos_control.ecos_runtime_audit_retry ADD COLUMN IF NOT EXISTS update_by VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_ecos_control_audit_retry_due
    ON ecos_control.ecos_runtime_audit_retry (status, next_retry_at);

-- ── 2. 控制域：事件 DLQ（代码读 ecos_control.；gateway RuntimeAlertDao 走 currentSchema 解析，
--       currentSchema 首位即 ecos_control；本表此前 dev 两处都没有 ⇒ 告警重放三端点恒 500）──
CREATE TABLE IF NOT EXISTS ecos_control.ecos_runtime_event_dlq (
    id            VARCHAR(36) PRIMARY KEY,
    topic         VARCHAR(128) NOT NULL,
    dlt_topic     VARCHAR(160) NOT NULL,
    payload       TEXT         NOT NULL,
    error_message VARCHAR(1000),
    attempts      SMALLINT     NOT NULL DEFAULT 0,
    first_seen_at TIMESTAMP    NOT NULL,
    replayed_at   TIMESTAMP,
    status        VARCHAR(16)  NOT NULL DEFAULT 'pending',
    trace_id      VARCHAR(64),
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    version_no    VARCHAR(20)  NOT NULL,
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    domain        VARCHAR(50)  NOT NULL DEFAULT 'default',
    tenant_id     VARCHAR(36),
    create_by     VARCHAR(100),
    update_by     VARCHAR(100)
);
ALTER TABLE ecos_control.ecos_runtime_event_dlq ADD COLUMN IF NOT EXISTS create_by VARCHAR(100);
ALTER TABLE ecos_control.ecos_runtime_event_dlq ADD COLUMN IF NOT EXISTS update_by VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_ecos_control_dlq_status
    ON ecos_control.ecos_runtime_event_dlq (status, first_seen_at);

-- ── 3. 控制域：安全资产目录（代码读 ecos_control.，见 SecurityAssetCatalogDao / SecurityDecisionDao）──
CREATE TABLE IF NOT EXISTS ecos_control.ecos_security_asset (
    id             VARCHAR(36) PRIMARY KEY,
    asset_key      VARCHAR(192) NOT NULL,
    kind           VARCHAR(16)  NOT NULL,
    sensitivity    VARCHAR(8)   NOT NULL,
    guard_combo    VARCHAR(64)  NOT NULL,
    exemption_ref  VARCHAR(64),
    owner_role     VARCHAR(64),
    description    VARCHAR(500),
    version_no     VARCHAR(20)  NOT NULL,
    is_deleted     SMALLINT     NOT NULL DEFAULT 0,
    domain         VARCHAR(50)  NOT NULL DEFAULT 'default',
    tenant_id      VARCHAR(36),
    create_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    create_by      VARCHAR(100),
    update_by      VARCHAR(100)
);
-- 与 V164 同形态（跨库等价由 W38 镜像 + 应用侧软删判定承接）；仅对本表新 schema 侧补建
CREATE UNIQUE INDEX IF NOT EXISTS uniq_ecos_control_sec_asset_key
    ON ecos_control.ecos_security_asset (tenant_id, asset_key) WHERE is_deleted = 0;

-- ── 4. 本体域：Git 归档补偿队列（代码读 ecos_ontology.，见 ontology-engine 与 runtime-task 两处 DAO）──
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_ontology_git_archive_pending (
    id                  VARCHAR(36)   PRIMARY KEY,
    ontology_id         VARCHAR(64)   NOT NULL,
    version_id          VARCHAR(64)   NOT NULL,
    target_version_no   VARCHAR(20)   NOT NULL,
    repository_id       VARCHAR(64)   NOT NULL DEFAULT 'ontology',
    commit_message      VARCHAR(512),
    author_name         VARCHAR(100),
    payload             TEXT,
    attempts            SMALLINT      NOT NULL DEFAULT 0,
    next_retry_at       TIMESTAMP     NOT NULL,
    last_error          VARCHAR(1000),
    status              VARCHAR(16)   NOT NULL DEFAULT 'pending',
    last_commit_id      VARCHAR(64),
    trace_id            VARCHAR(64),
    create_time         TIMESTAMP     NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMP     NOT NULL DEFAULT NOW(),
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT      NOT NULL DEFAULT 0,
    version_no          VARCHAR(20)   NOT NULL DEFAULT '1.0.0',
    domain              VARCHAR(50)   NOT NULL DEFAULT 'default'
);
CREATE INDEX IF NOT EXISTS idx_ecos_ontology_git_archive_due
    ON ecos_ontology.ecos_ontology_git_archive_pending (status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_ecos_ontology_git_archive_onto
    ON ecos_ontology.ecos_ontology_git_archive_pending (ontology_id, version_id, is_deleted);

-- ── 5. 业务域：场景主表（代码读 ecos_ai.，见 cognitive2 ScenarioDao；live 亦仅 ecos_ai. 一侧）──
--   V123 建在 ecos_cognitive. 的是"错放镜像"（R-13 ① 只停写），保留不动；本处按代码读端补建 ecos_ai. 侧。
--   新表用规范形态（metrics_json TEXT，MC02 禁新建 JSONB）；dev 存量 ecos_ai 表为 legacy jsonb 列，
--   CREATE IF NOT EXISTS 在 dev 为 no-op，两库形态差异已在 DD-4 登记。
CREATE TABLE IF NOT EXISTS ecos_ai.ecos_business_scenario (
    id                  VARCHAR(36)  NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         TEXT         DEFAULT '',
    business_goal       TEXT         DEFAULT '',
    department          VARCHAR(128),
    priority            VARCHAR(16)  DEFAULT 'MEDIUM',
    status              VARCHAR(32)  DEFAULT 'DRAFT',
    budget              VARCHAR(64),
    safety_index_target NUMERIC(8,4),
    actual_safety_index NUMERIC(8,4),
    metrics_json        TEXT,
    domain              VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted          SMALLINT     NOT NULL DEFAULT 0,
    create_by           VARCHAR(64)  NOT NULL DEFAULT 'system',
    update_by           VARCHAR(64),
    create_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP,
    CONSTRAINT pk_ecos_ai_business_scenario PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_ecos_ai_bizscn_status
    ON ecos_ai.ecos_business_scenario (status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_ecos_ai_bizscn_dept
    ON ecos_ai.ecos_business_scenario (department, is_deleted);

-- ── 6. 孤儿副本清退（用户 2026-11-06 授权；DROP 前已备份）──
--   判据：代码读端已全部指向目标 schema（第 1~5 节），public. 侧无任何 main 面读引用。
DROP TABLE IF EXISTS public.ecos_ontology_git_archive_pending;
DROP TABLE IF EXISTS public.ecos_runtime_audit_retry;
DROP TABLE IF EXISTS public.ecos_runtime_event_dlq;
DROP TABLE IF EXISTS public.ecos_security_asset;

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] 全部 schema 限定 ✓  [DR02] 新表 ecos_ 前缀 ✓  [DR03] 单数无 _v2 ✓
-- [DR04] 未新增 JSON 语义列（metrics_json 为 TEXT，沿 V123 口径）✓
-- [DR05~DR08] is_deleted / create_time / update_time / version_no / domain 齐 ✓
-- [MC01] PK VARCHAR(36) 应用侧 UUID，无 SERIAL/gen_random_uuid() ✓
-- [MC02] 无 JSON/JSONB 列 ✓  [MC03] 无 ::cast / ON CONFLICT / RETURNING ✓
-- [ST07] ecos_control（主控制目标 schema）/ ecos_ontology / ecos_ai 均为 ST07 权威落点 ✓
-- [IR03] 第 6 节四条 DROP 为**授权例外**，只删孤儿副本，不删任何在用表 ✓
