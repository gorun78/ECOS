-- V239 (卷10 §6.2): 候选评审 ecos_ai.ecos_wagent_candidate_review——评审凭证与决定留痕
-- 追溯: W245~W277 ↔ C227~C259（卷 10 §七）；需求依据 REQ-WAG-19/20（候选评审面，F10-20 评审中心同族）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-33=①、R-35=①、R-40=①；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST06 / ST07 / ST09 / IR02 / IR03(只加不删)
-- 凭证来源: `public.ecos_decision_approval` / `public.ecos_workflow_approval`（存量，作为 approval_token 的凭证链来源之一，卷 10 §6.2 收编段；本表不建 FK）
-- 审计: 评审动作事件走 Kafka `ecos.audit`（ST06），本表只存评审事实。

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_candidate_review (
    id              VARCHAR(36) PRIMARY KEY,                  -- MC01 应用侧 UUID
    tenant_id       VARCHAR(64) NOT NULL,
    org_id          VARCHAR(64),
    candidate_id    VARCHAR(36) NOT NULL,                     -- V238 逻辑引用（同 schema）
    reviewer_id     VARCHAR(100),
    decision        VARCHAR(16),                              -- APPROVE/REJECT/REVISE 等
    comment_text    TEXT,
    changes_json    TEXT,                                     -- 修改意见（JSON 语义 TEXT，MC02）
    approval_token  VARCHAR(64),                              -- 审批凭证令牌（来源含存量审批表；禁回显凭据）
    review_time     TIMESTAMP,
    domain          VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no      VARCHAR(20) NOT NULL,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by       VARCHAR(100),
    update_by       VARCHAR(100),
    is_deleted      SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_ai.ecos_wagent_candidate_review IS 'W Agent 候选评审记录（评审中心消费，卷 08；凭证链关联存量审批表，不建 FK）';
CREATE INDEX IF NOT EXISTS idx_wagent_cand_rev_cand ON ecos_ai.ecos_wagent_candidate_review(candidate_id, review_time);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai. ✓ [DR02] ecos_ ✓ [DR05/06/07/08] 通用列齐 ✓ [MC01] UUID ✓ [MC02] JSON 全 TEXT
-- [ST06] 审计走 Kafka 不建事件表 ✓ [ST07] ecos_ai ✓ [ST09] 无跨 schema FK ✓ [IR03] 只加 ✓ [§14.4] 未实跑 ✓
