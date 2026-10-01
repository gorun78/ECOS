-- V238 (卷10 §6.2): 知识候选 ecos_ai.ecos_wagent_candidate——四元校验与发布治理（R-40① 统一治理）
-- 追溯: W245~W277 ↔ C227~C259（卷 10 §七）；需求依据 REQ-WAG-18/19（候选生成与校验面）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-33=①、R-35=①、R-40=①（Candidate 统一治理 + 发布接 Git 归档唯一出口）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST06 / ST07 / ST09 / IR02 / IR03(只加不删)
-- 收编定性: `ecos_knowledge.kb_extract_candidate`（KB 侧只读适配）、`ecos_ai.agent_approval`（V239 凭证来源之一）均为存量动作（待 R-34/R-40/R-41），本脚本只建新表。
-- 命名口径: 未列类型者按 §6.1 通用列 + ECOS 类型映射展开（target_engine→VARCHAR(32)、batch_id→VARCHAR(64) 外部业务键、generated_by_*→VARCHAR(36)）

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_candidate (
    id                      VARCHAR(36) PRIMARY KEY,          -- MC01 应用侧 UUID
    tenant_id               VARCHAR(64) NOT NULL,
    org_id                  VARCHAR(64),
    candidate_type          VARCHAR(24),                      -- 候选类型（对象/关系/规则/口径…）
    target_engine           VARCHAR(32),                      -- 属主 Engine（写回经其端口，禁 Engine 侧回写 Agent 表 §6.3）
    target_object_ref_json  TEXT,                             -- 目标对象引用（JSON 语义 TEXT）
    payload_json            TEXT,                             -- 候选载荷（引用型/序列化，MC02 TEXT）
    payload_hash            VARCHAR(64),                      -- *_hash→VARCHAR(64)
    generated_by_run        VARCHAR(36),                      -- V233 逻辑引用
    generated_by_step       VARCHAR(36),                      -- V234 逻辑引用
    confidence              NUMERIC(5,4),                     -- 概率→NUMERIC(5,4)
    basis_json              TEXT,                             -- 生成依据（证据/口径引用集）
    validation_structure    VARCHAR(12),                      -- 四元校验①结构
    validation_reference    VARCHAR(12),                      -- 四元校验②引用闭合
    validation_conflict     VARCHAR(12),                      -- 四元校验③冲突
    validation_impact       VARCHAR(12),                      -- 四元校验④影响
    validation_regression   VARCHAR(12),                      -- 回归（第五道，若启用）
    status                  VARCHAR(16),                      -- 候选状态机（过期 E-WA-STATE 409，§5.4）
    batch_id                VARCHAR(64),                      -- 批次外部键
    engine_draft_ref_text   TEXT,                             -- Engine 侧草稿引用（只引用不复制）
    published_ref_text      TEXT,                             -- 发布产物引用
    published_git_ref       VARCHAR(120),                     -- 发布走 Git 归档唯一出口（R-40①）
    expire_time             TIMESTAMP,
    domain                  VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no              VARCHAR(20) NOT NULL,
    create_time             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by               VARCHAR(100),
    update_by               VARCHAR(100),
    is_deleted              SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_ai.ecos_wagent_candidate IS 'W Agent 知识/资产候选（附件 agt_candidate 重写；R-40① 统一治理，kb_extract_candidate/agent_approval 收编口径见卷 10 §6.2）';
CREATE INDEX IF NOT EXISTS idx_wagent_cand_status ON ecos_ai.ecos_wagent_candidate(status, expire_time);
CREATE INDEX IF NOT EXISTS idx_wagent_cand_run    ON ecos_ai.ecos_wagent_candidate(generated_by_run);
CREATE INDEX IF NOT EXISTS idx_wagent_cand_batch  ON ecos_ai.ecos_wagent_candidate(batch_id);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai. ✓ [DR02] ecos_ ✓ [DR05/06/07/08] 通用列齐 ✓ [MC01] UUID ✓ [MC02] payload 一律 TEXT
-- [ST07] ecos_ai ✓ [ST09] 无跨 schema FK ✓ [IR03] 只加、存量不动 ✓ [§14.4] 未实跑 ✓
