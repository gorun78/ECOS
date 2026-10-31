-- V296 (#146ay 差距分析 §8.4.2 行 432「关键表字段」余项 / 详设 §8.4.2 逐列口径):
--   ecos_cognitive.ecos_cognitive_model 补 R4 九类分类面五枚缺口列
--     model_key / phase / compute_owner / io_contract_json / limits_json / description +
--     model_category（九值分类权威列，字面 CAUSAL…BEHAVIORAL）；
--   ecos_cognitive.ecos_cognitive_hypothesis 补承参与信心面缺口列
--     subject_type / subject_id / reason / confidence NUMERIC(9,6) / evidence_ids_json TEXT。
--
-- 批准口径: /goal「完整补齐差距、不漏项、无假绿」批内 DDL 只加不删（IR03）；
--          既有列零改动——model 的 model_type（旧四值）/cognitive_model_type（CM-01~07 V261）
--          /features_json（V192/V276）与 hypothesis 的 evidence_ids（V128 JSONB, knownLegacy 形态）
--          全部保留停读，API 只增不改。
-- 状态: 2026-10-31 dev PG（sys_man）已实跑零错；同日幂等复跑一次终稿全量
--          （12 枚 ALTER + 12 枚 COMMENT 零错，12 枚 "already exists, skipping" NOTICE 零副作用）。
--          information_schema 实测列形态与设计逐列一致（phase SMALLINT DEFAULT 1 /
--          confidence NUMERIC(9,6) / reason TEXT DEFAULT '' / model_category VARCHAR(32) /
--          余 VARCHAR(…)、TEXT）。
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
--
-- ── 设计依据（唯一真源 docs/30-设计/ECOS详细设计说明书-从零实施版-2026-10-12.md §8.4.2）──────────
-- model：`model_key` 稳定业务键 / `phase SMALLINT` 1/2/3 三期分期 / `compute_owner` 计算执行归属
--        / `io_contract_json` 输入→输出契约 / `limits_json` 适用限制 / `description`。
--        九值分类（CAUSAL|DIAGNOSTIC|...|BEHAVIORAL）权威承载列 = model_category（本脚本）：
--        与旧 model_type 四值、cognitive_model_type CM-01~07（V261）同先例多词表并存，各守其域，
--        互不合并改写；沿用 V261 头注同纪律：CHECK 延后，词表守护由应用层属主单源枚举
--        （CognitiveR4ModelCategory）+ 静态门禁把守，本脚本不建 CHECK（防先加约束后改代码
--        造成线上拒写，与 V292 三轴同纪律）。
-- hypothesis：`subject_type`+`subject_id` 承参维度 / `reason` 立据（§8.4.2：statement 与 reason
--        均 NOT NULL——新列给 DEFAULT 不回填存量属越权裁决，故 DEFAULT '' 兜底保持存量可写不变，
--        应用层把关真必填）/ `confidence NUMERIC(9,6)`（详设精度 9,6，∈[0,1] 应用层守护）/
--        `evidence_ids_json TEXT`——V128 既有 `evidence_ids JSONB` 是 knownLegacy 形态
--        （DR04 命名缺 _json 后缀 + MC02 白名单外），按台账 #146h 确立的 knownLegacy 处置口径
--        只停再跑、不 DROP 不改建；设计列以正确命名 `evidence_ids_json` 补建为新权威面。
--
-- ── 1. model 加列（全 ADD COLUMN IF NOT EXISTS，可重复执行）──────────────────────────────────
ALTER TABLE ecos_cognitive.ecos_cognitive_model
    ADD COLUMN IF NOT EXISTS model_key        VARCHAR(120);
ALTER TABLE ecos_cognitive.ecos_cognitive_model
    ADD COLUMN IF NOT EXISTS description      TEXT;
ALTER TABLE ecos_cognitive.ecos_cognitive_model
    ADD COLUMN IF NOT EXISTS phase            SMALLINT      NOT NULL DEFAULT 1;
ALTER TABLE ecos_cognitive.ecos_cognitive_model
    ADD COLUMN IF NOT EXISTS compute_owner    VARCHAR(64);
ALTER TABLE ecos_cognitive.ecos_cognitive_model
    ADD COLUMN IF NOT EXISTS io_contract_json TEXT;
ALTER TABLE ecos_cognitive.ecos_cognitive_model
    ADD COLUMN IF NOT EXISTS limits_json      TEXT;
ALTER TABLE ecos_cognitive.ecos_cognitive_model
    ADD COLUMN IF NOT EXISTS model_category   VARCHAR(32);

-- ── 2. hypothesis 加列（同幂等形态）─────────────────────────────────────────────────────────
ALTER TABLE ecos_cognitive.ecos_cognitive_hypothesis
    ADD COLUMN IF NOT EXISTS subject_type      VARCHAR(20);
ALTER TABLE ecos_cognitive.ecos_cognitive_hypothesis
    ADD COLUMN IF NOT EXISTS subject_id        VARCHAR(36);
ALTER TABLE ecos_cognitive.ecos_cognitive_hypothesis
    ADD COLUMN IF NOT EXISTS reason            TEXT          NOT NULL DEFAULT '';
ALTER TABLE ecos_cognitive.ecos_cognitive_hypothesis
    ADD COLUMN IF NOT EXISTS confidence        NUMERIC(9,6);
ALTER TABLE ecos_cognitive.ecos_cognitive_hypothesis
    ADD COLUMN IF NOT EXISTS evidence_ids_json TEXT;

-- ── 3. 注释登记 ─────────────────────────────────────────────────────────────────────────────
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.model_key IS 'R4 稳定业务键（详设 §8.4.2）；V296 前该行 NULL=未挂键，应用侧注册端点补挂';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.description IS 'R4 模型资产说明';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.phase IS 'R4 三期路线分期 1/2/3（首期因果/诊断/假设检验，二期预测/风险/仿真/关系，三期优化/行为/信念更新）；CHECK 延后，应用层守护（V292 同纪律）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.compute_owner IS 'R4 计算执行归属（认知模型≠AI模型≠计算引擎）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.io_contract_json IS 'R4 输入→输出契约 TEXT JSON（DR04 _json 后缀）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.limits_json IS 'R4 适用限制 TEXT JSON';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.model_category IS 'R4 九类分类权威列 CAUSAL|DIAGNOSTIC|HYPOTHESIS_TESTING|PREDICTIVE|SIMULATION|RISK|RELATIONAL|OPTIMIZATION|BEHAVIORAL（详设 §8.4.2）；与旧 model_type 四值、cognitive_model_type CM-01~07 并存各守其域；CHECK 延后，应用层单源 CognitiveR4ModelCategory 守护';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.subject_type IS 'R4 承参维度主体类型（禁场景专有列；详设 §8.4.2：E3 按 subject 承参）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.subject_id IS 'R4 承参维度主体标识（与 subject_type 成对）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.reason IS 'R4 立据/理由（详设 §8.4.2 与 statement 并列必需；存量 DEFAULT 空串不伪造历史，应用层把关真必填）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.confidence IS 'R4 信心度 NUMERIC(9,6) ∈[0,1]（详设精度；越界即数据缺陷，应用层守护；列 NULL=存量未评信心）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.evidence_ids_json IS 'R4 证据引用数组 TEXT JSON；V128 旧 evidence_ids JSONB 列停跑保留（knownLegacy），本列为新权威读写面';

-- ── 4. 幂等与回滚说明 ────────────────────────────────────────────────────────────────────────
-- 幂等: 全部 ADD COLUMN IF NOT EXISTS，重复执行无副作用。
-- 回滚: IR03 禁 DROP 列；回退即停写新列（NULL/默认值不破坏存量读面）。

-- ── DDL Lint Self-audit ─────────────────────────────────────────────────────────────────────
-- [DR01] 全列 schema 限定 + 小写下划线 ✓  [DR02] 无新表  [DR03] n/a
-- [DR04] 新增 JSON 语义列 io_contract_json/limits_json/evidence_ids_json 均 TEXT ✓
-- [DR05] 无新增布尔列  [DR06] 存量表 ADD COLUMN 不触碰审计五列 ✓
-- [DR07] 存量表既有 version_no/版本列不动  [DR08] n/a
-- [MC01] 无新表/无主键改动  [MC02] confidence 显式精度 NUMERIC(9,6)、TEXT/VARCHAR/SMALLINT 全在白名单 ✓
-- [MC03] 无新建索引、无时区变体、无 partial/RLS/分区 ✓  [ST03-A] 无敏感/金额列 ✓
-- [ST07] 落 ecos_cognitive（控制域五引擎白名单）✓  [IR02] 手动 psql（Flyway 禁用）✓
-- [IR03] 只加不删：零 DROP/零改既有列形态 ✓
