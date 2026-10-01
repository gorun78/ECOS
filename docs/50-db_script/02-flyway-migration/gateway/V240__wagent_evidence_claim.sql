-- V240 (卷10 §6.2): 证据三表 ecos_ai.ecos_wagent_evidence / _claim / _claim_evidence——引用型证据与断言绑定（一脚本三表）
-- 追溯: W245~W277 ↔ C227~C259（卷 10 §七）；需求依据 REQ-WAG-21~23（证据/断言/数字一致性面，F10-24 同源）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-33=①、R-35=①；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST06 / ST07 / ST09 / IR02 / IR03(只加不删)
-- 边界（§6.3）: evidence **无内容字段**（只存引用与哈希，禁存业务明细副本/内容正文）；写方唯一 = `ai-engine.wagent`，全链只读。
-- 与 cognitive V127 `ecos_cognitive_evidence` 关系: 心智层证据属认知域，本表为编排域运行证据，**互不互写**（ST09 无跨 schema FK；对齐口径待卷 10 F10 批次，不重复立号）。
-- 金额/数值: claim.numeric_value NUMERIC(18,6) 为断言数值（非金额列本体，不进 ST03-A 豁免登记；若后续落金额语义需逐列再登记）。

-- ── 1. 证据（引用型，无内容字段）────────────────────────────
CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_evidence (
    id                  VARCHAR(36) PRIMARY KEY,              -- MC01 应用侧 UUID
    tenant_id           VARCHAR(64) NOT NULL,
    org_id              VARCHAR(64),
    evidence_type       VARCHAR(16),                          -- 事实行/口径快照/统计/文档锚点…
    source_engine       VARCHAR(32),                          -- 来源引擎（data/ontology/kb/cognitive）
    source_object_type  VARCHAR(32),
    source_object_id    VARCHAR(64),                          -- 外部业务键→VARCHAR(64)
    source_version      VARCHAR(20),
    snapshot_ref_json   TEXT,                                 -- 快照引用（JSON 语义 TEXT）
    locator_text        TEXT,                                 -- 定位符（行指针/URI 引用，非正文）
    content_hash        VARCHAR(64),                          -- 内容哈希（正文不入库，完整性以哈希锚定）
    classify_level      VARCHAR(16),                          -- 密级（CLS 列过滤联动）
    is_valid            SMALLINT NOT NULL DEFAULT 1,          -- 有效性位（DR05：is_ 前缀；文档字面 valid 已收口，登记默认随"新证据即有效"）
    domain              VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20) NOT NULL,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_ai.ecos_wagent_evidence IS 'W Agent 运行证据（引用型，无内容字段，§7.1/§6.3）';
CREATE INDEX IF NOT EXISTS idx_wagent_evidence_src ON ecos_ai.ecos_wagent_evidence(source_engine, source_object_type);

-- ── 2. 断言（数字一致性校验对象，F10-24）─────────────────────
CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_claim (
    id                  VARCHAR(36) PRIMARY KEY,              -- MC01 应用侧 UUID
    tenant_id           VARCHAR(64) NOT NULL,
    org_id              VARCHAR(64),
    run_id              VARCHAR(36) NOT NULL,                 -- V233 逻辑引用
    step_id             VARCHAR(36),                          -- V234 逻辑引用
    claim_text          TEXT,                                 -- 断言原文
    claim_type          VARCHAR(12),
    numeric_value       NUMERIC(18,6),                        -- 文档明载精度
    numeric_unit        VARCHAR(16),
    value_source        VARCHAR(24),                          -- 数值来源（金额唯一事实源纪律：禁 AI 生成金额，卷 09 §4.2 同源）
    evidence_grade      CHAR(1),
    verify_status       VARCHAR(16),                          -- 校验状态（E-WA-NARRATIVE-NUM 门禁）
    domain              VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20) NOT NULL,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_ai.ecos_wagent_claim IS 'W Agent 断言（数字一致性校验承载，§7.1）';
CREATE INDEX IF NOT EXISTS idx_wagent_claim_run ON ecos_ai.ecos_wagent_claim(run_id, verify_status);

-- ── 3. 断言×证据关联（复合主键，无独立 id）───────────────────
CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_claim_evidence (
    claim_id            VARCHAR(36) NOT NULL,                 -- PK①（复合主键替代单列 id：卷 10 §6.2 V240 明载）
    evidence_id         VARCHAR(36) NOT NULL,                 -- PK②
    relation            VARCHAR(8),                           -- SUPPORTS/CONTRADICTS/DERIVES 等
    cover_ratio         NUMERIC(5,4),                         -- 覆盖率→NUMERIC(5,4)
    tenant_id           VARCHAR(64) NOT NULL,
    org_id              VARCHAR(64),
    domain              VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20) NOT NULL,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_wagent_claim_evidence PRIMARY KEY (claim_id, evidence_id)
);
COMMENT ON TABLE ecos_ai.ecos_wagent_claim_evidence IS '断言×证据关联（PK(claim_id, evidence_id) 复合主键；证据主权在 V240 evidence 表）';
CREATE INDEX IF NOT EXISTS idx_wagent_claim_ev_evidence ON ecos_ai.ecos_wagent_claim_evidence(evidence_id);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai. 三表限定 ✓ [DR02] ecos_ ✓ [DR05/06/07/08] 通用列齐（rel 表无 id，主键=复合 PK 按文档）✓
-- [MC01] UUID 无默认函数 ✓ [MC02] JSON 全 TEXT、无数组列；numeric_value NUMERIC(18,6)、cover_ratio NUMERIC(5,4) ✓
-- [ST03-A] 无金额列新增（numeric_value=断言数值非金额本体，注释已登记判定）✓
-- [ST07] ecos_ai ✓ [ST09] 无跨 schema FK（引用关系由应用层维护）✓ [IR03] 只加 ✓ [§14.4] 未实跑 ✓
