-- V268 (补充设计-认知域与智能域 v1.0 §8): 认知资产统一生命周期 + Belief 置信语义补列
-- 追溯: §8.1~8.5（资产三层 A/B/C + 统一逻辑状态机 + probabilityKind）；SUP-COG-01~04
-- 需求依据: REQ-COG-05/06/07/08；§8.3 置信三分（禁 LLM 置信直写 belief.probability）
-- 批准口径: §14.1 新增表/新增列条件六条已逐条核对（对象目录定性 B 层治理资产 ∈ R-13 ①）
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST07 / IR02 / IR03(只加不删，禁改存量列语义)
--
-- ── 落点选择 ──────────────────────────────────────────────
--   ① hypothesis/belief/model 三表权威载体均在 ecos_cognitive（V191/V192/V261），按 R-13 ① 补列；
--   ② lifecycle_state 为 §8.4 统一逻辑状态的**唯一权威列**（词表单源 = CognitiveAssetStatus 枚举，
--      DDL 不建 CHECK，R-15 同纪律）；**存量 status 列（legacy 运营态）不改语义、停写**——
--      双列并存期由应用侧保证：读以 lifecycle_state 为准（NULL=未接管，回落按 legacy status 语义展示，
--      禁伪造新状态，§8.3 同 UNKNOWN_LEGACY 纪律）；
--   ③ Belief 补 prob_kind/calibration_ref/model_ref/scope_ref/as_of_time 五列，V262 probability 列的
--      语义配套（§8.3 建议 API/DTO 语义字段只增不改）：
--      probability 有无值 × prob_kind 有无值 四态：
--        probability=NULL                        → 未标定（V262 既有语义，E2 剔除+missingProbability）
--        probability 有 + prob_kind=STATISTICAL_PROBABILITY/CALIBRATED_MODEL_PROBABILITY → 可入算
--        probability 有 + prob_kind=UNCALIBRATED_CONFIDENCE/UNCALIBRATED_MODEL_ESTIMATE → 禁入金额/审批（§16.5 红线 4）
--        存量 probability 有 + prob_kind=NULL    → 应用侧读为 UNKNOWN_LEGACY 并纳入治理清单（§8.3 末段，禁伪造补齐）
--      与 distribution_json 关系同 V262 头注：点概率是「已标定标量」，禁由分布反算冒充标定值。
--   ④ model 表 memora_state（详设-07 E12/E13 裁判态，LIVE/PAUSED/FAILED）与 lifecycle_state（治理态）
--      正交不合并：一列管「合约上下线」，一列管「治理生命周期」。

-- ── 1. 三表补 lifecycle_state（幂等）──────────────────────
ALTER TABLE ecos_cognitive.ecos_cognitive_hypothesis ADD COLUMN IF NOT EXISTS lifecycle_state VARCHAR(30);
ALTER TABLE ecos_cognitive.ecos_cognitive_belief     ADD COLUMN IF NOT EXISTS lifecycle_state VARCHAR(30);
ALTER TABLE ecos_cognitive.ecos_cognitive_model      ADD COLUMN IF NOT EXISTS lifecycle_state VARCHAR(30);

-- ── 2. belief 置信语义四列 + 时点（幂等）──────────────────
ALTER TABLE ecos_cognitive.ecos_cognitive_belief ADD COLUMN IF NOT EXISTS prob_kind        VARCHAR(30);
ALTER TABLE ecos_cognitive.ecos_cognitive_belief ADD COLUMN IF NOT EXISTS calibration_ref  VARCHAR(100);
ALTER TABLE ecos_cognitive.ecos_cognitive_belief ADD COLUMN IF NOT EXISTS model_ref        VARCHAR(100);
ALTER TABLE ecos_cognitive.ecos_cognitive_belief ADD COLUMN IF NOT EXISTS scope_ref        VARCHAR(100);
ALTER TABLE ecos_cognitive.ecos_cognitive_belief ADD COLUMN IF NOT EXISTS as_of_time       TIMESTAMP;   -- TIMESTAMP 无时区（MC 列口径，与 create_time 同族）

-- ── 3. 索引（is_deleted 并入键代偿，MC03 禁条件索引）────────
CREATE INDEX IF NOT EXISTS idx_cv268_hypo_lc  ON ecos_cognitive.ecos_cognitive_hypothesis (lifecycle_state, is_deleted);
CREATE INDEX IF NOT EXISTS idx_cv268_belief_lc ON ecos_cognitive.ecos_cognitive_belief (lifecycle_state, is_deleted);
CREATE INDEX IF NOT EXISTS idx_cv268_model_lc  ON ecos_cognitive.ecos_cognitive_model (lifecycle_state, is_deleted);
CREATE INDEX IF NOT EXISTS idx_cv268_belief_kd ON ecos_cognitive.ecos_cognitive_belief (prob_kind, is_deleted);

-- ── 4. 注释登记 ──────────────────────────────────────────────
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.lifecycle_state IS
    '<补充设计 v1.0 §8.4>统一认知资产生命周期唯一权威列（词表单源 CognitiveAssetStatus；DRAFT/CANDIDATE/EVIDENCE_CHECKED/VALIDATED/APPROVED/ACTIVE/SUPERSEDED/CONTRADICTED/EXPIRED/REJECTED/DEPRECATED/SUSPENDED）；存量 status 列停写不改语义；NULL=未接管（回落 legacy 展示，禁伪造）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.lifecycle_state IS
    '<补充设计 v1.0 §8.4>Belief 治理生命周期（同 hypothesis 词表）；level-evidence 型生命周期 EVIDENCE_CHECKED=last_evidence_id 链完整；CONTRADICTED≠删除，保留反证链（§8.5）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.lifecycle_state IS
    '<补充设计 v1.0 §8.4>CM 治理生命周期；与 registry_state（门禁态 PUBLISHED/REJECTED）、memora_state（合约上下线态）正交三列并存，各管一段禁合并';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.prob_kind IS
    '<补充设计 v1.0 §8.3/§16.5 红线>point probability 语义三分：STATISTICAL_PROBABILITY / CALIBRATED_MODEL_PROBABILITY / UNCALIBRATED_CONFIDENCE / UNCALIBRATED_MODEL_ESTIMATE；NULL+probability 非 NULL=UNKNOWN_LEGACY（应用侧回落，禁伪造补齐）；UNCALIBRATED 两类禁入金额计算/自动审批（SUP-COG-02/SUP-MODEL-03）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.calibration_ref IS
    '<补充设计 v1.0 §8.3>校准方法/数据集/有效范围的引用声明（CALIBRATED_MODEL_PROBABILITY 必填语义，应用侧校验，DDL 不建 CHECK 约束）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.model_ref IS '<补充设计 v1.0 §8.3>产生该判断的 CM 版本引用（如 cm-07:v3，CP 前缀式）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.scope_ref IS '<补充设计 v1.0 §8.3>适用范围引用（scenario/tenant-scope 键）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.as_of_time IS '<补充设计 v1.0 §5.3/§12.2>判断时点（T1S 轨；与 V261 model 的 as_of 同口径）';

-- ── 回滚说明 ──────────────────────────────────────────────
-- 数据回滚：纯增量列。回滚 = 应用侧不再读写新列（读回落 legacy status/probability 原语义）；
--           如需清列，只允许人工裁决（IR03 禁脚本内 DROP COLUMN）。
-- 幂等性：ADD COLUMN IF NOT EXISTS + CREATE INDEX IF NOT EXISTS ⇒ 重复执行零报错。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] schema 限定 ✓ [DR02] 不改表名 ✓ [DR04] 无新增 JSON 列（引用键=VARCHAR）✓
-- [DR05] 无布尔列 ✓ [DR06~DR08] 通用列各表既有，本批不重复 ✓
-- [MC01] 无新主键 ✓ [MC02] 无二进制浮点 ✓ [MC03] 无条件索引 / 无时区戳列（as_of_time=TIMESTAMP 家族口径）✓
-- [ST07] 全部落 ecos_cognitive ✓ [IR02] 仅文件 ✓ [IR03] 只加不删，不改存量列 ✓
