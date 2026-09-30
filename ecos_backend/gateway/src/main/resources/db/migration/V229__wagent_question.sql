-- V229 (卷10 §6.2): 业务问题 ecos_ai.ecos_wagent_question——意图识别与槽位承载
-- 追溯: W245~W277 ↔ C227~C259（卷 10 §七）；需求依据 REQ-WAG-05/06（F10-05 对象链与意图识别 / F10-06 槽位来源与澄清配额）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-33=①、R-35=①；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST06 / ST07 / ST09 / IR02 / IR03(只加不删)
-- 命名口径: 未列类型者按 §6.1 通用列 + ECOS 类型映射展开；JSON 语义一律 *_json TEXT（【2026-09-30 DR04 收口】卷 10 §6.1 原字面 `*_text` 违 DR04（JSON 语义列必加 `_json` 后缀），以红线为准；分册 §6.1 映射行同步勘误，逐列改名见 docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md §五）

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_question (
    id                  VARCHAR(36) PRIMARY KEY,              -- MC01 应用侧 UUID
    tenant_id           VARCHAR(64) NOT NULL,
    org_id              VARCHAR(64),
    mission_id          VARCHAR(36) NOT NULL,                 -- 指向 ecos_wagent_mission.id（逻辑引用）
    raw_text            TEXT,                                 -- 原始提问文本
    intent              VARCHAR(20),
    intent_confidence   NUMERIC(5,4),                         -- 概率→NUMERIC(5,4)
    slots_json          TEXT,                                 -- 槽位集（JSON 语义 TEXT，MC02）
    depends_on_json     TEXT,                                 -- 问题间依赖（JSON 语义 TEXT）
    status              VARCHAR(20),
    latest_run_id       VARCHAR(36),                          -- 指向 ecos_wagent_run.id（V233）
    latest_grade        CHAR(1),                              -- 最新 Readiness 定级 A~D（V230 冗余投影）
    answer_summary_text TEXT,
    domain              VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20) NOT NULL,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_ai.ecos_wagent_question IS 'W Agent 业务问题（附件 agt_business_question 重写）';
CREATE INDEX IF NOT EXISTS idx_wagent_question_mission ON ecos_ai.ecos_wagent_question(mission_id);
CREATE INDEX IF NOT EXISTS idx_wagent_question_tenant  ON ecos_ai.ecos_wagent_question(tenant_id, status);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai. ✓ [DR02] ecos_ ✓ [DR05/06/07/08] 通用列齐 ✓ [MC01] UUID ✓ [MC02] JSON 全 TEXT
-- [ST07] ecos_ai ✓ [ST09] 无跨 schema FK ✓ [IR03] 只加 ✓ [§14.4] 未实跑 ✓
