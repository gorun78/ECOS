-- V274 (补充设计-认知域与智能域 v1.0 §9.2 步骤 10 / §9.4): W→D 结果评估表补列
-- 追溯: §9.2 步骤 10「闭环记录：保留输入/实际/误差/建议/审批/新版本的引用链」；
--       §9.4 七种禁自更新情形必须留痕（fail-loud，禁只在响应体里出现）；
--       §7.5 幂等纪律（与 V272 data_governance_candidate / V273 ontology_candidate 同族）
-- 归属: 控制域认知引擎 schema ecos_cognitive（ST07；V267 建表，本脚本只加列不改列）
-- 补列理由（V267 缺三处，缺则应用侧只能伪造或丢证据）:
--   * idempotency_key —— V267 无幂等键，同 run 重试会重复建评估记录（§9.4 防错误学习的前提是
--     "同一次评估只算一次"）；V272/V273 均有此列，本表补齐同族纪律。
--   * blockers_text —— §9.4 命中的七情形枚举清单。V267 只有 warnings_text（自由文本），
--     无法机器判读"为什么不许自动学"，SUP-OUT-01 验收点无锚。
--   * caliber_alignment —— §9.2 步骤 2 口径对齐结论。V267 的 caliber_id 只存"对齐后采用的口径"，
--     对齐失败时该列为空即丢失"失败在哪一维"的证据。
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（IR02；实跑属未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST06 / ST07 / ST09 / IR02 / IR03(只加不删)

ALTER TABLE ecos_cognitive.ecos_cognitive_outcome_evaluation
    ADD COLUMN IF NOT EXISTS idempotency_key   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS blockers_text     TEXT,
    ADD COLUMN IF NOT EXISTS caliber_alignment VARCHAR(200);

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_outcome_evaluation.idempotency_key IS
    '幂等键（§7.5）：同键重试返回既有评估记录，禁重复计算/重复建记录';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_outcome_evaluation.blockers_text IS
    'JSON 数组：§9.4 命中的禁自更新情形枚举名（ACTUALS_NOT_CLOSED 等七项词表）；空数组=未命中，非空 ⇒ status 必为 NEEDS_RECONCILE';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_outcome_evaluation.caliber_alignment IS
    '§9.2 步骤 2 口径对齐结论：ALIGNED / MISMATCH:{维度} / UNCONFIRMED:{缺失侧}';

CREATE UNIQUE INDEX IF NOT EXISTS uk_cv274_outcome_idem
    ON ecos_cognitive.ecos_cognitive_outcome_evaluation (tenant_id, idempotency_key, is_deleted);
CREATE INDEX IF NOT EXISTS idx_cv274_outcome_trace
    ON ecos_cognitive.ecos_cognitive_outcome_evaluation (trace_id, is_deleted);

-- ── 回滚说明 ──────────────────────────────────────────────
-- 数据回滚：仅新增可空列 + 新增索引，既有行零影响，既有读写路径零影响（IR03 只加不删）。
--           回滚 = 应用侧停用 /outcomes/evaluate 端点；删列只允许人工裁决（IR03 禁脚本内 DROP）。
-- 幂等性：ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS ⇒ 重复执行零报错。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] schema 限定（ecos_cognitive.）+ 小写下划线 ✓ [DR02/DR03] 无新表 ✓
-- [DR04] blockers_text = JSON 存 TEXT ✓ [DR05] 无布尔列（新增三列均非布尔）✓
-- [DR06~DR08] 七基线列 V267 已齐，本脚本不动 ✓
-- [MC01] 无新主键/无序列 ✓ [MC02] JSON 全 TEXT，无金额列 ✓
-- [MC03] 无裸 cast / 无条件索引 / 无 RLS / 无时区戳列 ✓
-- [ST07] 控制域认知引擎 schema ✓ [ST09] 无跨 schema FK ✓ [IR02] 仅文件 ✓ [IR03] 只加 ✓
