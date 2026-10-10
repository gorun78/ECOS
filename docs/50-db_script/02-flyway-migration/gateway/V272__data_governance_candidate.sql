-- V272 (补充设计-认知域与智能域 v1.0 §7.1/7.2): 数据治理 AI 候选载体
-- 追溯: 八项建议 #4「AI 辅助数据治理」；§7.2 七能力（DATA_PROFILE 等 SUGGEST 类 + 两项 WRITE 级）；
--       §7.4 候选通用状态机；§7.5 安全与幂等；REQ-AI-01（候选面纳入统一护栏链）
-- 归属裁定: data-engine（土 D）控制 schema ecos_data（ST07；与 DQ/数据源族同域）。
--           **不写业务域 ecos_dw**（候选是控制域治理资产，非业务事实）。
-- 红线:
--   * 候选 != 正式资产：AI 工具只能创建/推进候选状态，正式写入一律经 data-engine 既有写通道
--     （DQ rules 的 submit/approve 族 / 数据源变更审批），本表不承载正式生效配置（§7.1 首段）；
--   * GENERATED/VALIDATED ≠ APPROVED（§7.4 末段：三态禁混淆，生成与申请必须两个动作/两个审计事件）；
--   * preview_pass_rate 等预览产物只来自**只读预览**，禁改变正式资产/版本/水位线（SUP-DATA-01 断言面）；
--   * 幂等：同 idempotency_key（租户内唯一约束）重试不得重复创建（§7.5）。
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**
-- 纪律: DR01~DR08 / MC01~MC03 / ST07 / IR02 / IR03(只加不删)
-- 类型映射: proposal/evidence/confidence/impact/assumptions/conflicts/review_log → JSON-TEXT；
--           preview_pass_rate = NUMERIC(6,5)（比率非金额，MC02 带精度同族）

CREATE TABLE IF NOT EXISTS ecos_data.ecos_data_governance_candidate (
    id                     VARCHAR(36) PRIMARY KEY,          -- MC01 应用侧 UUID（= candidateId）
    tenant_id              VARCHAR(64) NOT NULL,
    org_id                 VARCHAR(64),
    candidate_type         VARCHAR(30) NOT NULL,             -- FIELD_SEMANTIC/FIELD_MAPPING/DQ_RULE/CONVERSION/OTHER（词表单源 data 域 DO constants）
    target_ref             VARCHAR(200) NOT NULL,            -- 受影响资产/字段引用（asset-001.field-17 形态）
    proposal_text          TEXT NOT NULL,                    -- JSON：候选正文（§7.2 candidate.proposal 对象）
    evidence_refs_text     TEXT,                             -- JSON 数组：evidenceRefs（profile/ontology-ref/kb-chunk 引用 ID）
    confidence_value       NUMERIC(6,5),                     -- §7.2 confidence.value（0..1 同族精度）
    confidence_kind        VARCHAR(50),                      -- MODEL_ESTIMATE / UNCALIBRATED_MODEL_ESTIMATE / STATISTICAL（§7.2 置信规则 + §8.3 同词表纪律）
    calibrated             SMALLINT,                         -- NULL=未声明；LLM 自评必 FALSE（§7.2：禁当已校准概率）
    assumptions_text       TEXT,                             -- JSON 数组：assumptions
    conflicts_text         TEXT,                             -- JSON 数组：conflicts（含现有权威资产重叠声明）
    impact_text            TEXT,                             -- JSON：affectedAssets/breakingChange（§7.2 impact）
    preview_pass_rate      NUMERIC(6,5),                     -- DQ_PREVIEW 只读预览通过率（§7.2；SUP-DATA-01：预览前后正式版本/水位线无变化）
    rollback_plan_text     TEXT,                             -- 回滚计划（§7.2 DATA_CHANGE_PROPOSAL 必带；无回滚计划禁进 PENDING_APPROVAL，应用侧门禁）
    status                 VARCHAR(24) NOT NULL DEFAULT 'GENERATED',
        -- §7.4 状态机：GENERATED/EVIDENCE_CHECKED/VALIDATED/PENDING_APPROVAL/APPROVED/APPLIED
        --  + 分支 REJECTED/NEEDS_EVIDENCE/CONFLICTED/EXPIRED/ROLLED_BACK（词表单源，不得把 GENERATED/VALIDATED 当 APPROVED）
    tool_contract_ref      VARCHAR(100),                     -- 生成该候选的 Tool Contract 引用（operationClass=SUGGEST 单源 V270）
    approval_ref           VARCHAR(100),                     -- 审批记录引用（PENDING_APPROVAL→APPROVED 必填语义，应用侧门禁）
    source_run_id          VARCHAR(36),                      -- §7.5：触发候选的 W Agent Run 引用
    trace_id               VARCHAR(64),
    idempotency_key        VARCHAR(100),                     -- §7.5 幂等键（租户内唯一，重试幂等）
    domain                 VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no             VARCHAR(20) NOT NULL,
    create_time            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by              VARCHAR(100),
    update_by              VARCHAR(100),
    is_deleted             SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_data.ecos_data_governance_candidate IS
    '数据治理 AI 候选（补充设计 v1.0 §7）：候选态载体；正式写入仍经 data-engine 写端点/审批（§7.1）；GENERATED/VALIDATED≠APPROVED；预览只读不改变正式资产（SUP-DATA-01/02）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_v272_cand_idem ON ecos_data.ecos_data_governance_candidate (tenant_id, idempotency_key, is_deleted);
CREATE INDEX IF NOT EXISTS idx_v272_cand_tenant     ON ecos_data.ecos_data_governance_candidate (tenant_id, status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_v272_cand_target     ON ecos_data.ecos_data_governance_candidate (target_ref, is_deleted);

-- ── 回滚说明 ──────────────────────────────────────────────
-- 数据回滚：仅新增表。回滚 = 应用侧停用 /api/v1/data/governance/candidates 端点；删表只允许人工裁决（IR03）。
-- 幂等性：CREATE TABLE/INDEX IF NOT EXISTS ⇒ 重复执行零报错。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] schema 限定（ecos_data.）✓ [DR02/03] 新表单数 ✓ [DR04] JSON 全 TEXT ✓
-- [DR05] calibrated SMALLINT（0/1/NULL）✓ [DR06~08] 七基线列齐 ✓
-- [MC01] UUID 主键 ✓ [MC02] 数值列带精度 ✓ [MC03] 无条件索引 / 无 RLS / TIMESTAMP 无时区 ✓
-- [ST07] 控制域数据引擎 schema ✓ [IR02] 仅文件 ✓ [IR03] 只加 ✓
