-- V236 (卷10 §6.2): 工具契约 ecos_ai.ecos_wagent_tool_contract——注册/发现/披露/治理四要件（§5.1+§5.4）
-- 追溯: W245~W277 ↔ C227~C259（卷 10 §七）；需求依据 REQ-WAG-14~17（工具面 F10 族，§5.1）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-33=①、R-35=①、R-43=推荐项批准（端点前缀单源）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST06 / ST07 / ST09 / IR02 / IR03(只加不删)
-- 命名口径: 未列类型者按 §6.1 通用列 + ECOS 类型映射展开（name→VARCHAR(255)、engine→VARCHAR(32)、endpoint_url→VARCHAR(500)、endpoint_method→VARCHAR(10)、owner→VARCHAR(100)、deprecated_by→VARCHAR(100)（*_by 映射，后继工具标识））
-- CHECK 说明: commit 类工具必须登记回滚预案（卷 10 §5.4），CHECK 为多库兼容标准 SQL 约束

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_tool_contract (
    id                  VARCHAR(36) PRIMARY KEY,              -- MC01 应用侧 UUID
    tenant_id           VARCHAR(64) NOT NULL,
    org_id              VARCHAR(64),
    name                VARCHAR(255) NOT NULL,
    version             VARCHAR(20) NOT NULL,
    engine              VARCHAR(32),                          -- 属主引擎代号（data/ontology/kb/cognitive/ai/security/workspace）
    category            VARCHAR(12),                          -- READ/WRITE/COMMIT 等
    side_effect         VARCHAR(12),
    min_level           SMALLINT,                             -- 最低自动化档位
    required_permission VARCHAR(80),
    input_schema_json   TEXT,                                 -- JSON Schema 语义一律 TEXT（MC02）
    output_schema_json  TEXT,
    timeout_ms          INTEGER,                              -- *_ms→INTEGER
    retry_text          TEXT,
    is_idempotent       SMALLINT NOT NULL DEFAULT 0,          -- DR05：is_ 前缀（文档字面 idempotent 已收口）
    cost_class          VARCHAR(8),
    is_evidence_output  SMALLINT NOT NULL DEFAULT 0,          -- 是否产出证据（V240 evidence 联动；DR05 is_ 前缀）
    data_classification_text TEXT,                            -- 数据密级声明（CLS 联动）
    rollback_plan_text  TEXT,
    endpoint_kind       VARCHAR(8),
    endpoint_url        VARCHAR(500),
    endpoint_method     VARCHAR(10),
    owner               VARCHAR(100),
    sla_text            TEXT,
    status              VARCHAR(12),
    deprecated_by       VARCHAR(100),                         -- 后继工具标识（*_by→VARCHAR(100) 映射）
    toolset             VARCHAR(32),
    summary             VARCHAR(120),                         -- 文档明载 VARCHAR(120)（映射表 summary→200，取文档字面优先）
    disclosure          VARCHAR(12),                          -- 披露形态（§5.4）
    capability_deps_text TEXT,
    domain              VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20) NOT NULL,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_wagent_tool_rollback CHECK (category <> 'commit' OR rollback_plan_text IS NOT NULL)
);
COMMENT ON TABLE ecos_ai.ecos_wagent_tool_contract IS 'W Agent 工具契约（附件 §5.1/§5.4 重写；UNIQUE(name,version)；commit 类必带回滚预案）';

-- UNIQUE(name, version)（红线：禁 partial ⇒ 含 is_deleted；下架工具再注册需递增 version）
CREATE UNIQUE INDEX IF NOT EXISTS uniq_wagent_tool_name_ver
    ON ecos_ai.ecos_wagent_tool_contract(name, version, is_deleted);
CREATE INDEX IF NOT EXISTS idx_wagent_tool_engine   ON ecos_ai.ecos_wagent_tool_contract(engine, status);
CREATE INDEX IF NOT EXISTS idx_wagent_tool_toolset  ON ecos_ai.ecos_wagent_tool_contract(toolset);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai. ✓ [DR02] ecos_ ✓ [DR05/06/07/08] 通用列齐 ✓ [MC01] UUID ✓ [MC02] schema 一律 TEXT
-- [唯一索引] 非 partial、含 is_deleted ✓  [ST07] ecos_ai ✓  [ST09] 无 FK ✓  [IR03] 只加 ✓ [§14.4] 未实跑 ✓
