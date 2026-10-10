-- V273 (补充设计-认知域与智能域 v1.0 §7.3/7.4/7.5): 本体 AI 候选载体
-- 追溯: 八项建议 #5「证据驱动的本体候选建模」；§7.3 五能力（GAP_ANALYZE/GENERATE/VALIDATE/PROPOSAL_CREATE/DIFF_PREVIEW）；
--       REQ-ONTO（AI 禁直发正式本体，SUP-ONTO-02）
-- 归属裁定: ontology-engine 控制 schema ecos_ontology（ST07）。**与既有 ecos_ontology_proposals（V4.1/V174）分工**：
--   * 本表 = AI/Agent 侧候选集（GENERATED…VALIDATED 段）：证据、冲突、兼容性、差异预览；
--   * 既有 proposals 表 = 正式提案流（DRAFT/PENDING/APPROVED…，V174 合规化后状态机）；
--   * 桥接：ONTOLOGY_PROPOSAL_CREATE 容量时把已 VALIDATED 候选投影到提案流，
--     本表记 proposal_ref（逻辑引用，ST09 禁跨表 FK），本表不承载正式发布（§7.3：候选禁直发，SUP-ONTO-01/02）；
--   * 状态词表与 §7.4 统一候选状态机一致（与 data 候选同构，语义单源 = 本体域 DO constants）。
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**
-- 纪律: DR01~DR08 / MC01~MC03 / ST07 / IR02 / IR03(只加不删)

CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_ontology_candidate (
    id                     VARCHAR(36) PRIMARY KEY,          -- MC01 应用侧 UUID（= candidateId）
    tenant_id              VARCHAR(64) NOT NULL,
    org_id                 VARCHAR(64),
    candidate_type         VARCHAR(30) NOT NULL,             -- ENTITY/PROPERTY/RELATION/CONSTRAINT（§7.3 对象类型/属性/关系/约束候选）
    subject_type           VARCHAR(100),                     -- §7.3 候选示例主端类型（如 Project）
    predicate              VARCHAR(100),                     -- 关系谓词（如 HAS_ACCEPTANCE_MILESTONE）
    object_type            VARCHAR(100),                     -- 宾语端类型（如 AcceptanceMilestone）
    target_ref             VARCHAR(200),                     -- 关联本体版本/对象引用（ontology-version-N 形态）
    proposal_text          TEXT NOT NULL,                    -- JSON：候选正文（properties/constraints 全量，§7.3）
    evidence_refs_text     TEXT,                             -- JSON 数组：evidenceRefs（data-profile/kb-chunk/decision-basis 引用）
    confidence_value       NUMERIC(6,5),                     -- 同 V272 同族精度纪律
    confidence_kind        VARCHAR(50),                      -- MODEL_ESTIMATE / UNCALIBRATED_MODEL_ESTIMATE / STATISTICAL（§7.2 置信规则复用）
    calibrated             SMALLINT,
    conflicts_text         TEXT,                             -- JSON 数组：与现有本体语义重叠/冲突项（SUP-ONTO-01：未处理禁发布）
    compatibility_text     TEXT,                             -- JSON：breakingChange/requiresMigration（§7.3 compatibility）
    diff_preview_text      TEXT,                             -- JSON：ONTOLOGY_DIFF_PREVIEW 差异预览产物（只读快照，禁直接应用）
    validation_text        TEXT,                             -- JSON：自检结果（命名/类型/基数/端点/兼容性，ONTOLOGY_CANDIDATE_VALIDATE 产物）
    status                 VARCHAR(24) NOT NULL DEFAULT 'GENERATED',
        -- §7.4 同 V272 词表：GENERATED/EVIDENCE_CHECKED/VALIDATED/PENDING_APPROVAL/APPROVED/APPLIED
        --  + REJECTED/NEEDS_EVIDENCE/CONFLICTED/EXPIRED/ROLLED_BACK
    proposal_ref           VARCHAR(36),                      -- 投影到既有 ecos_ontology_proposals 的 ID（逻辑引用；NULL=未提交提案流）
    tool_contract_ref      VARCHAR(100),
    approval_ref           VARCHAR(100),
    source_run_id          VARCHAR(36),
    trace_id               VARCHAR(64),
    idempotency_key        VARCHAR(100),
    domain                 VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no             VARCHAR(20) NOT NULL,
    create_time            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by              VARCHAR(100),
    update_by              VARCHAR(100),
    is_deleted             SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_ontology.ecos_ontology_candidate IS
    '本体 AI 候选（补充设计 v1.0 §7.3）：候选集载体；正式发布只经既有 proposals 审批流（AI 禁直发，SUP-ONTO-02）；候选必带证据+冲突+兼容性（SUP-ONTO-01）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_v273_onto_idem ON ecos_ontology.ecos_ontology_candidate (tenant_id, idempotency_key, is_deleted);
CREATE INDEX IF NOT EXISTS idx_v273_onto_tenant    ON ecos_ontology.ecos_ontology_candidate (tenant_id, status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_v273_onto_ref       ON ecos_ontology.ecos_ontology_candidate (proposal_ref, is_deleted);

-- ── 回滚说明 ──────────────────────────────────────────────
-- 数据回滚：仅新增表。回滚 = 应用侧停用 /api/v1/ontology/candidates 端点族；删表只允许人工裁决（IR03）。
-- 幂等性：CREATE TABLE/INDEX IF NOT EXISTS ⇒ 重复执行零报错。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] schema 限定（ecos_ontology.）✓ [DR02/03] 新表单数 ✓ [DR04] JSON 全 TEXT ✓
-- [DR05] calibrated SMALLINT ✓ [DR06~08] 七基线列齐 ✓
-- [MC01] UUID 主键 ✓ [MC02] 数值列带精度 ✓ [MC03] 无条件索引 / 无 RLS / TIMESTAMP 无时区 ✓
-- [ST07] 控制域本体 schema ✓ [ST09] 无跨 schema FK（proposal_ref 逻辑引用）✓ [IR02] 仅文件 ✓ [IR03] 只加 ✓
