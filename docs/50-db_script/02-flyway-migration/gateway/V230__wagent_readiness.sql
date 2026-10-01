-- V230 (卷10 §6.2): DIKC 就绪评估 ecos_ai.ecos_wagent_readiness——§3.1 三表合并主表（item/gap 见 V231/V232）
-- 追溯: W245~W277 ↔ C227~C259（卷 10 §七）；需求依据 REQ-WAG-08（F10-08 Readiness 评估与定级）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-33=①、R-35=①；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST06 / ST07 / ST09 / IR02 / IR03(只加不删)
-- 命名口径: 未列类型者按 §6.1 通用列 + ECOS 类型映射展开；JSON 语义一律 *_json TEXT（2026-09-30 DR04 收口，卷 10 §6.1 同步勘误）

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_readiness (
    id                  VARCHAR(36) PRIMARY KEY,              -- MC01 应用侧 UUID
    tenant_id           VARCHAR(64) NOT NULL,
    org_id              VARCHAR(64),
    question_id         VARCHAR(36) NOT NULL,                 -- V229 逻辑引用
    run_id              VARCHAR(36),                          -- V233 逻辑引用
    profile_id          VARCHAR(36),                          -- 画像引用（卷 04 知识域对象；ST09 不建跨 schema FK）
    profile_version     VARCHAR(20),
    grade               CHAR(1),                              -- 综合定级 A/B/C/D
    layer_d             VARCHAR(10),                          -- D 层（数据）档位
    layer_i             VARCHAR(10),                          -- I 层（本体）档位
    layer_k             VARCHAR(10),                          -- K 层（知识）档位
    layer_c             VARCHAR(10),                          -- C 层（认知）档位
    layer_summary_json  TEXT,
    degradations_json   TEXT,                                 -- 命中的预登记降级项（F10-10，JSON 语义 TEXT）
    computed_time       TIMESTAMP,
    valid_until         TIMESTAMP,                            -- 评估有效期（过期即失效，is_pinned 除外）
    superseded_by       VARCHAR(36),                          -- 被新评估取代（同表逻辑引用）
    is_pinned           SMALLINT NOT NULL DEFAULT 0,          -- 人工钉住（DR05：is_ 前缀；文档字面 pinned 已收口）
    domain              VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20) NOT NULL,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_ai.ecos_wagent_readiness IS 'DIKC 就绪评估主表（附件 §3.1 三表合并；item/gap 子表见 V231/V232）';
CREATE INDEX IF NOT EXISTS idx_wagent_ready_question ON ecos_ai.ecos_wagent_readiness(question_id, valid_until);
CREATE INDEX IF NOT EXISTS idx_wagent_ready_tenant   ON ecos_ai.ecos_wagent_readiness(tenant_id, grade);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai. ✓ [DR02] ecos_ ✓ [DR05/06/07/08] 通用列齐 ✓ [MC01] UUID ✓ [MC02] JSON 全 TEXT、时间列 TIMESTAMP（无时区形态）
-- [ST07] ecos_ai ✓ [ST09] 无跨 schema FK ✓ [IR03] 只加 ✓ [§14.4] 未实跑 ✓
