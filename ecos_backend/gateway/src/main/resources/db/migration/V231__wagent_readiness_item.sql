-- V231 (卷10 §6.2): 就绪评估检查项 ecos_ai.ecos_wagent_readiness_item——逐 check_code 明细
-- 追溯: W245~W277 ↔ C227~C259（卷 10 §七）；需求依据 REQ-WAG-08（F10-08）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-33=①、R-35=①；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST06 / ST07 / ST09 / IR02 / IR03(只加不删)
-- 命名口径: 未列类型者按 §6.1 通用列 + ECOS 类型映射展开；JSON 语义一律 *_json TEXT（2026-09-30 DR04 收口，卷 10 §6.1 同步勘误）

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_readiness_item (
    id                  VARCHAR(36) PRIMARY KEY,              -- MC01 应用侧 UUID
    tenant_id           VARCHAR(64) NOT NULL,
    org_id              VARCHAR(64),
    assessment_id       VARCHAR(36) NOT NULL,                 -- 指向 ecos_wagent_readiness.id（同 schema 逻辑引用）
    layer               CHAR(1),                              -- D/I/K/C
    check_code          VARCHAR(40),                          -- 检查码（阈值字典 wagent.readiness.threshold，经 sysman 门面）
    status              VARCHAR(10),
    measured_json       TEXT,                                 -- 实测值（JSON 语义 TEXT）
    threshold_json      TEXT,                                 -- 阈值快照（来源 sysman 配置，非硬编码，R-66 同族纪律）
    evidence_ids_json   TEXT,                                 -- 证据 ID 集（JSON 语义 TEXT；指向 V240 evidence）
    domain              VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20) NOT NULL,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_ai.ecos_wagent_readiness_item IS '就绪评估检查项明细（附件 §3.1 item 分表）';
CREATE INDEX IF NOT EXISTS idx_wagent_ready_item_assess ON ecos_ai.ecos_wagent_readiness_item(assessment_id, layer);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai. ✓ [DR02] ecos_ ✓ [DR05/06/07/08] 通用列齐 ✓ [MC01] UUID ✓ [MC02] JSON 全 TEXT
-- [ST07] ecos_ai ✓ [ST09] 无跨 schema FK ✓ [IR03] 只加 ✓ [§14.4] 未实跑 ✓
